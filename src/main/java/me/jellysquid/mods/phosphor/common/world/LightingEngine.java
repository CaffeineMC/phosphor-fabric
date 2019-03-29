package me.jellysquid.mods.phosphor.common.world;

import me.jellysquid.mods.phosphor.api.IChunkLighting;
import me.jellysquid.mods.phosphor.api.ILightingEngine;
import me.jellysquid.mods.phosphor.common.PhosphorMod;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;

@SuppressWarnings("unused")
public class LightingEngine implements ILightingEngine {
    private static final int MAX_SCHEDULED_COUNT = 1 << 22;

    private static final int MAX_LIGHT = 15;

    private static final Logger logger = LogManager.getLogger();

    private final World world;
    private final Profiler profiler;

    //Layout of longs: [padding(4)] [y(8)] [x(26)] [z(26)]
    private final PooledLongQueue[] queuedLightUpdates = new PooledLongQueue[EnumSkyBlock.values().length];

    //Layout of longs: see above
    private final PooledLongQueue[] queuedDarkenings = new PooledLongQueue[MAX_LIGHT + 1];
    private final PooledLongQueue[] queuedBrightenings = new PooledLongQueue[MAX_LIGHT + 1];

    //Layout of longs: [newLight(4)] [pos(60)]
    private final PooledLongQueue initialBrightenings = new PooledLongQueue();
    //Layout of longs: [padding(4)] [pos(60)]
    private final PooledLongQueue initialDarkenings = new PooledLongQueue();

    private boolean updating = false;

    //Layout parameters
    //Length of bit segments
    private static final int
            lX = 26,
            lY = 8,
            lZ = 26,
            lL = 4;

    //Bit segment shifts/positions
    private static final int
            sZ = 0,
            sX = sZ + lZ,
            sY = sX + lX,
            sL = sY + lY;

    //Bit segment masks
    private static final long
            mX = (1L << lX) - 1,
            mY = (1L << lY) - 1,
            mZ = (1L << lZ) - 1,
            mL = (1L << lL) - 1,
            mPos = (mY << sY) | (mX << sX) | (mZ << sZ);

    //Bit to check whether y had overflow
    private static final long yCheck = 1L << (sY + lY);

    private static final long[] neighborShifts = new long[6];

    static {
        for (int i = 0; i < 6; ++i) {
            final Vec3i offset = EnumFacing.VALUES[i].getDirectionVec();
            neighborShifts[i] = ((long) offset.getY() << sY) | ((long) offset.getX() << sX) | ((long) offset.getZ() << sZ);
        }
    }

    //Mask to extract chunk idenitfier
    private static final long mChunk = ((mX >> 4) << (4 + sX)) | ((mZ >> 4) << (4 + sZ));

    //Stored light type to reduce amount of method parameters
    private EnumSkyBlock lightType;

    //Iteration state data
    //Cache position to avoid allocation of new object each time
    private final BlockPos.MutableBlockPos curPos = new BlockPos.MutableBlockPos();
    private PooledLongQueue curQueue;
    private Chunk curChunk;
    private long curChunkIdentifier;
    private long curData;

    //Cached data about neighboring blocks (of tempPos)
    private boolean isNeighborDataValid = false;

    private final NeighborInfo[] neighborInfos = new NeighborInfo[6];

    public LightingEngine(final World world) {
        this.world = world;
        this.profiler = world.profiler;

        for (int i = 0; i < EnumSkyBlock.values().length; ++i) {
            this.queuedLightUpdates[i] = new PooledLongQueue();
        }

        for (int i = 0; i < this.queuedDarkenings.length; ++i) {
            this.queuedDarkenings[i] = new PooledLongQueue();
        }

        for (int i = 0; i < this.queuedBrightenings.length; ++i) {
            this.queuedBrightenings[i] = new PooledLongQueue();
        }

        for (int i = 0; i < this.neighborInfos.length; ++i) {
            this.neighborInfos[i] = new NeighborInfo();
        }
    }

    /**
     * Schedules a light update for the specified light type and position to be processed later by {@link #procLightUpdates(EnumSkyBlock)}
     */
    @Override
    public void scheduleLightUpdate(final EnumSkyBlock lightType, final BlockPos pos) {
        this.scheduleLightUpdate(lightType, posToLong(pos));
    }

    /**
     * Schedules a light update for the specified light type and position to be processed later by {@link #procLightUpdates()}
     */
    private void scheduleLightUpdate(final EnumSkyBlock lightType, final long pos) {
        final PooledLongQueue queue = this.queuedLightUpdates[lightType.ordinal()];

        queue.add(pos);

        //make sure there are not too many queued light updates
        if (queue.size() >= MAX_SCHEDULED_COUNT) {
            this.procLightUpdates(lightType);
        }
    }

    /**
     * Calls {@link #procLightUpdates(EnumSkyBlock)} for both light types
     */
    @Override
    public void procLightUpdates() {
        this.procLightUpdates(EnumSkyBlock.SKY);
        this.procLightUpdates(EnumSkyBlock.BLOCK);
    }

    /**
     * Processes light updates of the given light type
     */
    @Override
    public void procLightUpdates(final EnumSkyBlock lightType) {
        final PooledLongQueue queue = this.queuedLightUpdates[lightType.ordinal()];

        if (queue.isEmpty()) {
            return;
        }

        //renderer accesses world unsynchronized, don't modify anything in that case
        if (this.world.isRemote && !PhosphorMod.PROXY.getMinecraftThread().isCallingFromMinecraftThread()) {
            return;
        }

        //avoid nested calls
        if (this.updating) {
            logger.warn("Trying to access light values during relighting");
            return;
        }

        this.updating = true;
        this.curChunkIdentifier = -1; //reset chunk cache

        this.profiler.startSection("lighting");

        this.lightType = lightType;

        this.profiler.startSection("checking");

        //process the queued updates and enqueue them for further processing
        for (this.curQueue = queue; this.nextItem(); ) {
            if (this.curChunk == null) {
                continue;
            }

            final int oldLight = this.curToCachedLight();
            final int newLight = this.calcNewLightFromCur();

            if (oldLight < newLight) {
                //don't enqueue directly for brightening in order to avoid duplicate scheduling
                this.initialBrightenings.add(((long) newLight << sL) | this.curData);
            } else if (oldLight > newLight) {
                //don't enqueue directly for darkening in order to avoid duplicate scheduling
                this.initialDarkenings.add(this.curData);
            }
        }

        for (this.curQueue = this.initialBrightenings; this.nextItem(); ) {
            final int newLight = (int) (this.curData >> sL & mL);

            if (newLight > this.curToCachedLight()) {
                //Sets the light to newLight to only schedule once. Clear leading bits of curData for later
                this.enqueueBrightening(this.curPos, this.curData & mPos, newLight, this.curChunk);
            }
        }

        for (this.curQueue = this.initialDarkenings; this.nextItem(); ) {
            final int oldLight = this.curToCachedLight();

            if (oldLight != 0) {
                //Sets the light to 0 to only schedule once
                this.enqueueDarkening(this.curPos, this.curData, oldLight, this.curChunk);
            }
        }

        this.profiler.endSection();

        //Iterate through enqueued updates (brightening and darkening in parallel) from brightest to darkest so that we only need to iterate once
        for (int curLight = MAX_LIGHT; curLight >= 0; --curLight) {
            this.profiler.startSection("darkening");

            for (this.curQueue = this.queuedDarkenings[curLight]; this.nextItem(); ) {
                if (this.curToCachedLight() >= curLight) //don't darken if we got brighter due to some other change
                {
                    continue;
                }

                final IBlockState state = this.curToState();
                final int luminosity = this.curToLuminosity(state);
                final int opacity = luminosity >= MAX_LIGHT - 1 ? 1 : this.curToOpac(state); //if luminosity is high enough, opacity is irrelevant

                //only darken neighbors if we indeed became darker
                if (this.calcNewLightFromCur(luminosity, opacity) < curLight) {
                    //need to calculate new light value from neighbors IGNORING neighbors which are scheduled for darkening
                    int newLight = luminosity;

                    this.fetchNeighborDataFromCur();

                    for (NeighborInfo info : this.neighborInfos) {
                        final Chunk nChunk = info.chunk;

                        if (nChunk == null) {
                            continue;
                        }

                        final int nLight = info.light;

                        if (nLight == 0) {
                            continue;
                        }

                        final BlockPos.MutableBlockPos nPos = info.pos;

                        if (curLight - this.posToOpac(nPos, posToState(nPos, info.section)) >= nLight) //schedule neighbor for darkening if we possibly light it
                        {
                            this.enqueueDarkening(nPos, info.key, nLight, nChunk);
                        } else //only use for new light calculation if not
                        {
                            //if we can't darken the neighbor, no one else can (because of processing order) -> safe to let us be illuminated by it
                            newLight = Math.max(newLight, nLight - opacity);
                        }
                    }

                    //schedule brightening since light level was set to 0
                    this.enqueueBrighteningFromCur(newLight);
                } else //we didn't become darker, so we need to re-set our initial light value (was set to 0) and notify neighbors
                {
                    this.enqueueBrighteningFromCur(curLight); //do not spread to neighbors immediately to avoid scheduling multiple times
                }
            }

            this.profiler.endStartSection("brightening");

            for (this.curQueue = this.queuedBrightenings[curLight]; this.nextItem(); ) {
                final int oldLight = this.curToCachedLight();

                if (oldLight == curLight) //only process this if nothing else has happened at this position since scheduling
                {
                    this.world.notifyLightSet(this.curPos);

                    if (curLight > 1) {
                        this.spreadLightFromCur(curLight);
                    }
                }
            }

            this.profiler.endSection();
        }

        this.profiler.endSection();

        this.updating = false;
    }

    /**
     * Gets data for neighbors of <code>curPos</code> and saves the results into neighbor state data members. If a neighbor can't be accessed/doesn't exist, the corresponding entry in <code>neighborChunks</code> is <code>null</code> - others are not reset
     */
    private void fetchNeighborDataFromCur() {
        //only update if curPos was changed
        if (this.isNeighborDataValid) {
            return;
        }

        this.isNeighborDataValid = true;

        for (int i = 0; i < this.neighborInfos.length; ++i) {
            NeighborInfo info = this.neighborInfos[i];

            final long nLongPos = info.key = this.curData + neighborShifts[i];

            if ((nLongPos & yCheck) != 0) {
                info.chunk = null;
                info.section = null;
                continue;
            }

            final BlockPos.MutableBlockPos nPos = longToPos(info.pos, nLongPos);

            final Chunk nChunk;

            if ((nLongPos & mChunk) == this.curChunkIdentifier) {
                nChunk = info.chunk = this.curChunk;
            } else {
                nChunk = info.chunk = this.posToChunk(nPos);
            }

            if (nChunk != null) {
                info.light = this.posToCachedLight(nPos, nChunk);
                info.section = nChunk.getBlockStorageArray()[nPos.getY() >> 4];
            }
        }
    }

    private int calcNewLightFromCur() {
        final IBlockState state = this.curToState();
        final int luminosity = this.curToLuminosity(state);

        return this.calcNewLightFromCur(luminosity, luminosity >= MAX_LIGHT - 1 ? 1 : this.curToOpac(state));
    }

    private int calcNewLightFromCur(final int luminosity, final int opacity) {
        if (luminosity >= MAX_LIGHT - opacity) {
            return luminosity;
        }

        int newLight = luminosity;
        this.fetchNeighborDataFromCur();

        for (NeighborInfo info : this.neighborInfos) {
            if (info.chunk == null) {
                continue;
            }

            final int nLight = info.light;

            newLight = Math.max(nLight - opacity, newLight);
        }

        return newLight;
    }

    private void spreadLightFromCur(final int curLight) {
        this.fetchNeighborDataFromCur();

        for (NeighborInfo info : this.neighborInfos) {
            final Chunk nChunk = info.chunk;

            if (nChunk == null) {
                continue;
            }

            final int newLight = curLight - this.posToOpac(info.pos, posToState(info.pos, info.section));

            if (newLight > info.light) {
                this.enqueueBrightening(info.pos, info.key, newLight, nChunk);
            }
        }
    }

    private void enqueueBrighteningFromCur(final int newLight) {
        this.enqueueBrightening(this.curPos, this.curData, newLight, this.curChunk);
    }

    /**
     * Enqueues the pos for brightening and sets its light value to <code>newLight</code>
     */
    private void enqueueBrightening(final BlockPos pos, final long longPos, final int newLight, final Chunk chunk) {
        this.queuedBrightenings[newLight].add(longPos);
        chunk.setLightFor(this.lightType, pos, newLight);
    }

    /**
     * Enqueues the pos for darkening and sets its light value to 0
     */
    private void enqueueDarkening(final BlockPos pos, final long longPos, final int oldLight, final Chunk chunk) {
        this.queuedDarkenings[oldLight].add(longPos);
        chunk.setLightFor(this.lightType, pos, 0);
    }

    private static BlockPos.MutableBlockPos longToPos(final BlockPos.MutableBlockPos pos, final long longPos) {
        final int posX = (int) (longPos >> sX & mX) - (1 << lX - 1);
        final int posY = (int) (longPos >> sY & mY);
        final int posZ = (int) (longPos >> sZ & mZ) - (1 << lZ - 1);
        return pos.setPos(posX, posY, posZ);
    }

    private static long posToLong(final BlockPos pos) {
        return posToLong(pos.getX(), pos.getY(), pos.getZ());
    }

    private static long posToLong(final long x, final long y, final long z) {
        return (y << sY) | (x + (1 << lX - 1) << sX) | (z + (1 << lZ - 1) << sZ);
    }

    /**
     * Polls a new item from <code>curQueue</code> and fills in state data members
     *
     * @return If there was an item to poll
     */
    private boolean nextItem() {
        if (this.curQueue.isEmpty()) {
            return false;
        }

        this.curData = this.curQueue.poll();
        this.isNeighborDataValid = false;
        longToPos(this.curPos, this.curData);

        final long chunkIdentifier = this.curData & mChunk;

        if (this.curChunkIdentifier != chunkIdentifier) {
            this.curChunk = this.curToChunk();
            this.curChunkIdentifier = chunkIdentifier;
        }

        return true;
    }

    private int posToCachedLight(final BlockPos.MutableBlockPos pos, final Chunk chunk) {
        return ((IChunkLighting) chunk).getCachedLightFor(this.lightType, pos);
    }

    private int curToCachedLight() {
        return this.posToCachedLight(this.curPos, this.curChunk);
    }

    /**
     * Calculates the luminosity for <code>curPos</code>, taking into account <code>lightType</code>
     */
    private int curToLuminosity(final IBlockState state) {
        if (this.lightType == EnumSkyBlock.SKY) {
            return this.curChunk.canSeeSky(this.curPos) ? EnumSkyBlock.SKY.defaultLightValue : 0;
        }

        return MathHelper.clamp(state.getLightValue(this.world, this.curPos), 0, MAX_LIGHT);
    }

    private int curToOpac(final IBlockState state) {
        return this.posToOpac(this.curPos, state);
    }

    private int posToOpac(final BlockPos pos, final IBlockState state) {
        return MathHelper.clamp(state.getLightOpacity(this.world, pos), 1, MAX_LIGHT);
    }

    private IBlockState curToState() {
        return posToState(this.curPos, this.curChunk);
    }

    private static final IBlockState DEFAULT_BLOCK_STATE = Blocks.AIR.getDefaultState();

    // Avoids some additional logic in Chunk#getBlockState... 0 is always air
    private static IBlockState posToState(final BlockPos pos, final Chunk chunk) {
        return posToState(pos, chunk.getBlockStorageArray()[pos.getY() >> 4]);
    }

    private static IBlockState posToState(final BlockPos pos, final ExtendedBlockStorage section) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        if (section != Chunk.NULL_BLOCK_STORAGE)
        {
            int i = section.data.storage.getAt((y & 15) << 8 | (z & 15) << 4 | x & 15);

            if (i != 0) {
                IBlockState state = section.data.palette.getBlockState(i);

                if (state != null) {
                    return state;
                }
            }
        }

        return DEFAULT_BLOCK_STATE;
    }

    private Chunk posToChunk(final BlockPos pos) {
        return this.world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private Chunk curToChunk() {
        return this.posToChunk(this.curPos);
    }

    //PooledLongQueue code
    //Implement own queue with pooled segments to reduce allocation costs and reduce idle memory footprint

    private static final int CACHED_QUEUE_SEGMENTS_COUNT = 1 << 12;
    private static final int QUEUE_SEGMENT_SIZE = 1 << 10;

    private final Deque<PooledLongQueueSegment> segmentPool = new ArrayDeque<>();

    private PooledLongQueueSegment getLongQueueSegment() {
        if (this.segmentPool.isEmpty()) {
            return new PooledLongQueueSegment();
        }

        return this.segmentPool.pop();
    }

    private class PooledLongQueueSegment {
        private final long[] longArray = new long[QUEUE_SEGMENT_SIZE];
        private int index = 0;
        private PooledLongQueueSegment next;

        private void release() {
            this.index = 0;
            this.next = null;

            if (LightingEngine.this.segmentPool.size() < CACHED_QUEUE_SEGMENTS_COUNT) {
                LightingEngine.this.segmentPool.push(this);
            }
        }

        public PooledLongQueueSegment add(final long val) {
            PooledLongQueueSegment ret = this;

            if (this.index == QUEUE_SEGMENT_SIZE) {
                ret = this.next = LightingEngine.this.getLongQueueSegment();
            }

            ret.longArray[ret.index++] = val;
            return ret;
        }
    }

    private class PooledLongQueue {
        private PooledLongQueueSegment cur, last;
        private int size = 0;

        private int index = 0;

        public int size() {
            return this.size;
        }

        public boolean isEmpty() {
            return this.cur == null;
        }

        public void add(final long val) {
            if (this.cur == null) {
                this.cur = this.last = LightingEngine.this.getLongQueueSegment();
            }

            this.last = this.last.add(val);
            ++this.size;
        }

        public long poll() {
            final long ret = this.cur.longArray[this.index++];
            --this.size;

            if (this.index == this.cur.index) {
                this.index = 0;
                final PooledLongQueueSegment next = this.cur.next;
                this.cur.release();
                this.cur = next;
            }

            return ret;
        }
    }

    private static class NeighborInfo {
        Chunk chunk;
        ExtendedBlockStorage section;

        int light;

        long key;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    }
}


package me.jellysquid.mods.phosphor.common.util.cache;

class AbstractCachedAccess {
    static class CachedEntry<T> {
        T obj;
        int x, y, z;

        CachedEntry() {
            this.reset();
        }

        void reset() {
            this.obj = null;
            this.x = Integer.MIN_VALUE;
            this.y = Integer.MIN_VALUE;
            this.z = Integer.MIN_VALUE;
        }
    }
}

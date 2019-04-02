package me.jellysquid.mods.phosphor.common.util;

import me.jellysquid.mods.phosphor.common.PhosphorMod;

public final class ThreadUtil {
    public static boolean VALIDATE_THREAD_ACCESS = true;

    /**
     * Checks whether or not the current thread matches the {@param expected} thread. A warning will be logged
     * if the current thread does not match {@param expected} and {@link ThreadUtil#VALIDATE_THREAD_ACCESS} is true.
     * <p>
     * This method can be slow, and should only be used as a debugging tool when we suspect invalid thread accesses are
     * happening.
     *
     * @param expected The expected thread to be accessing this method.
     */
    public static void validateThread(Thread expected) {
        if (!VALIDATE_THREAD_ACCESS) {
            return;
        }

        Thread current = Thread.currentThread();

        if (current != expected) {
            IllegalAccessException e = new IllegalAccessException(String.format("Illegal thread access! World is owned by '%s' (ID: %s)," +
                            " but was accessed illegally from thread '%s' (ID: %s)",
                    expected.getName(), expected.getId(), current.getName(), current.getId()));

            PhosphorMod.LOGGER.error("Something (likely another mod) attempted to modify the world's state from the wrong thread!" +
                    " In a previous release, this would've caused severe errors, but we will handle this as gracefully as we can (which might hurt performance)." +
                    " Please report this issue! (You can disable this warning in the config by setting `enable_illegal_thread_access_warnings` to false.)", e);
        }
    }
}

package me.jellysquid.mods.phosphor.common.util;

import me.jellysquid.mods.phosphor.common.PhosphorMod;

public final class ThreadUtil {
    public static boolean VALIDATE_THREAD_ACCESS = true;

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

package io.github.baba983.nativedo.core;

import android.os.Build;

/**
 * Build variant flags matching the fields Telegram's PhotoViewer and ported UI code
 * read from {@code org/telegram/messenger/BuildVars}.
 *
 * <p>Skeleton — add fields as ported subsystems need them.
 */
public final class BuildVars {

    /** Mirrors upstream {@code BuildVars.DEBUG_VERSION}. */
    // public static boolean DEBUG_VERSION = BuildConfig.DEBUG;
    public static boolean DEBUG_VERSION = false;

    /** Mirrors upstream {@code BuildVars.LOGS_ENABLED}. */
    public static boolean LOGS_ENABLED = false;

    /**
     * When {@code true} the app uses the legacy file-picker / WRITE_EXTERNAL_STORAGE path
     * instead of the MediaStore scoped-storage API. Upstream gates this on SDK ≤ 29.
     */
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;

    private BuildVars() {
    }
}

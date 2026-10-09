package io.github.baba983.nativedo.settings;

import java.util.ArrayList;

import org.telegram.ui.Components.UItem;

/**
 * The debug-only dev-tools section of {@link SettingsFragment}.
 *
 * <p>Android's build-type source sets cannot replace a class that also exists in {@code main} (the
 * merger reports a duplicate class), and {@code SettingsFragment} is a shipping screen, so the
 * implementation cannot live in {@code main} and be swapped out. Instead this seam lives in
 * {@code main} and the real menu lives in {@code src/debug}: {@link #load()} answers the debug
 * implementation reflectively, and only while {@code BuildConfig.DEBUG}. A release build never
 * loads it (the class is not on its classpath, so the lookup fails and this answers {@code null}),
 * which is why no debug row can ever appear in a release build.</p>
 *
 * <p>The implementation adds its rows with {@link #fillItems} and handles their taps with
 * {@link #onItemClick}; both are no-ops here because there is no implementation to call.</p>
 */
public interface DebugTools {

    /**
     * The debug menu for this process, or {@code null} when there is none (a release build). Called
     * once per {@link SettingsFragment}; a lookup that fails is treated as "no debug tools".
     */
    static DebugTools load() {
        if (!io.github.baba983.nativedo.BuildConfig.DEBUG) {
            return null;
        }
        try {
            Class<?> implementation = Class.forName("io.github.baba983.nativedo.debug.DebugToolsImpl");
            return (DebugTools) implementation.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Whether the tools have anything to draw. */
    boolean isAvailable();

    /** Appends the debug section's rows to the settings list. */
    void fillItems(ArrayList<UItem> items);

    /**
     * Handles a tap on one of the debug rows. Answers {@code true} when the id was the debug
     * section's, so {@link SettingsFragment} stops looking for it among its own rows.
     */
    boolean onItemClick(int id, SettingsFragment fragment);
}

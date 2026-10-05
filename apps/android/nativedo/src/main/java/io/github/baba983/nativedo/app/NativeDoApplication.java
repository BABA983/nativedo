package io.github.baba983.nativedo.app;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;

import io.github.baba983.nativedo.BuildConfig;
import io.github.baba983.nativedo.settings.LinkOpenSettings;

/**
 * The app's Application.
 *
 * <p>Upstream's loader answers "what is this app's id" with null unless the module that ships the
 * APK says otherwise, and its own app module says so with a subclass of its own. The id is not
 * cosmetic: {@code AndroidUtilities.openForView} builds the FileProvider authority from it, so a
 * null id means a tapped attachment throws instead of opening.</p>
 */
public class NativeDoApplication extends ApplicationLoader {

    private static boolean runtimeConfigLogged;

    @Override
    public void onCreate() {
        super.onCreate();
        loadRuntimeConfig();
        // The reader's link choice is process-wide and the browser hook is static: it is read back
        // here, before any screen can hand a pressed link to the browser.
        LinkOpenSettings.INSTANCE.load(this);
    }

    /**
     * The part of upstream's startup that everything downstream reads from: the measured device
     * performance class and the LiteMode preset derived from it. Upstream loads both from
     * {@code ApplicationLoader.postInitApplication()} (its {@code SharedConfig.loadConfig()} at
     * ApplicationLoader.java:247, {@code LiteMode.getValue()} at first use); this shell does not
     * run that method - it starts Telegram's network, storage and services - so it asks for
     * exactly these two, once, at the top of the process.
     *
     * <p>Without it the two classes fall back to their field defaults when their static
     * initializer runs before {@code applicationContext} exists: {@code loadConfig()} returns
     * early and is never retried, {@code overrideDevicePerformanceClass} stays at its zero value,
     * which {@code getDevicePerformanceClass()} reads as {@code PERFORMANCE_CLASS_LOW}, and the
     * LiteMode preset is computed from that same zero - so a flagship phone silently takes the
     * opaque glass branch forever, and nothing on screen says why. The log line is that answer:
     * the measured class versus the class in use, and whether the chat blur is enabled.</p>
     */
    public static void loadRuntimeConfig() {
        SharedConfig.loadConfig();
        // Recompute the preset now that the class is loaded: a process that read LiteMode too
        // early cached the preset that followed the uninitialized class, and its value (not the
        // preference) is what isEnabled() answers with.
        LiteMode.loadPreference();
        if (!runtimeConfigLogged) {
            runtimeConfigLogged = true;
            FileLog.d("nativedo runtime config: performance class = "
                    + SharedConfig.performanceClassName(SharedConfig.getDevicePerformanceClass())
                    + ", measured = " + SharedConfig.performanceClassName(SharedConfig.measureDevicePerformanceClass())
                    + ", chat blur = " + SharedConfig.chatBlurEnabled());
        }
    }

    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }
}


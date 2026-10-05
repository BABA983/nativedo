package io.github.baba983.nativedo.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SharedPrefsHelper;

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
        loadShellRuntime();
        // The reader's link choice is process-wide and the browser hook is static: it is read back
        // here, before any screen can hand a pressed link to the browser.
        LinkOpenSettings.INSTANCE.load(this);
    }

    /**
     * The rest of upstream's {@code postInitApplication()} that this client's screens read and
     * that does not start Telegram: the locale singleton, the WebView-bot preference file, and the
     * screen on/off state upstream keeps in {@code ApplicationLoader.isScreenOn} (upstream's own
     * {@code ScreenReceiver}, minus the two tgnet calls it makes - pausing and resuming Telegram's
     * connections - which the hooks keep from ever running a network).
     *
     * <p>{@code LocaleController.getInstance()} is what every cell formats its numbers and dates
     * with; upstream builds it at this point, before the first screen can draw. {@code
     * SharedPrefsHelper.init} is the file {@code ChatActivityEnterView} reads a WebView-confirm
     * flag from - never null once this ran. {@code isScreenOn} is read by upstream's own theme and
     * media code; without the receiver it would stay at its field default {@code false} for the
     * life of the process, which silently disables every screen-on branch.</p>
     *
     * <p>What stays out, and why: the connectivity receiver's only work is refreshing {@code
     * currentNetworkInfo} and telling tgnet and {@code FileLoader} about a network change; every
     * read of that info in this client goes through {@code ApplicationLoader.ensureCurrentNetworkGet},
     * which fetches it lazily and is forced on foreground by upstream's own {@code ForegroundDetector}
     * (created in {@code ApplicationLoader.onCreate}). The per-account loop (UserConfig,
     * MessagesController, per-account ConnectionsManager), {@code initPushServices}, MediaController's
     * eager build, ContactsController/DownloadController and BillingController all start Telegram
     * account, network, push, media or billing work that this client does not have - they are the
     * part of that method the hooks exist to keep off.</p>
     */
    private void loadShellRuntime() {
        try {
            LocaleController.getInstance();
        } catch (Exception e) {
            FileLog.e(e);
        }
        SharedPrefsHelper.init(this);
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                ApplicationLoader.isScreenOn = powerManager.isScreenOn();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screenStateReceiver, filter);
        } catch (Exception e) {
            FileLog.e(e);
        }
        FileLog.d("nativedo shell runtime: screen on = " + ApplicationLoader.isScreenOn);
    }

    /**
     * Upstream's {@code ScreenReceiver} without its tgnet half: the flag the rest of upstream
     * reads ({@code ApplicationLoader.isScreenOn}) and the global notification it posts
     * ({@code NotificationCenter.screenStateChanged}). Pausing Telegram's connections on screen
     * off is exactly what the hooks keep off, so only these two state changes are ported.
     */
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                ApplicationLoader.isScreenOn = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                ApplicationLoader.isScreenOn = true;
            } else {
                return;
            }
            NotificationCenter.getGlobalInstance()
                    .postNotificationName(NotificationCenter.screenStateChanged);
        }
    };

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


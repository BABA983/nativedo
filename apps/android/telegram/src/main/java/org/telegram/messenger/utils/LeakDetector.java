package org.telegram.messenger.utils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * No-op host stub of Telegram's LeakDetector.
 *
 * BottomSheet (and other DEBUG_PRIVATE_VERSION paths) call
 * {@code LeakDetector.getInstance().add(this)}. NativeDo keeps
 * {@code BuildConfig.DEBUG_PRIVATE_VERSION=false}, so this is rarely entered;
 * the stub still compiles those call sites without pulling X-Core
 * ReferenceMap / NotificationCenter leak plumbing.
 */
public final class LeakDetector {

    private static final LeakDetector INSTANCE = new LeakDetector();

    @NonNull
    public static LeakDetector getInstance() {
        return INSTANCE;
    }

    private LeakDetector() {
    }

    @MainThread
    public void start() {
        // no-op
    }

    @MainThread
    public void stop() {
        // no-op
    }

    @MainThread
    public void add(@NonNull Object instance) {
        // no-op — NativeDo does not track Telegram-style object leaks
    }
}

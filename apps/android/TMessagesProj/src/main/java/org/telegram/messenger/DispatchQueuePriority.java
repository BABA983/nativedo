/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

/**
 * NativeDo stub of org.telegram.messenger.DispatchQueuePriority.
 * Upstream selects a queue from a priority pool. NativeDo doesn't need queue
 * prioritisation — every submission lands on a single shared dispatcher.
 */
public class DispatchQueuePriority {

    public DispatchQueuePriority(String threadName) {
    }

    public DispatchQueue getQueue(int priority) {
        return Utilities.globalQueue;
    }

    public Runnable postRunnable(Runnable runnable) {
        Utilities.globalQueue.postRunnable(runnable);
        return runnable;
    }

    public Runnable postRunnable(Runnable runnable, int priority) {
        Utilities.globalQueue.postRunnable(runnable);
        return runnable;
    }

    public void cancelRunnable(Runnable runnable) {
        Utilities.globalQueue.cancelRunnable(runnable);
    }

    public void pause() {
        // NativeDo shares a global queue and cannot suspend it for one owner.
    }

    public void resume() {
        // Paired host API for Telegram callers; see pause().
    }
}

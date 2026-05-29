/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/NotificationCenter.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * Telegram-shaped event bus. Each notification is an {@code int} constant; observers
 * implement {@link NotificationCenterDelegate} and register via
 * {@link #addObserver(NotificationCenterDelegate, int)}.
 */
public final class NotificationCenter {

    private static final long EXPIRE_NOTIFICATIONS_TIME = 5017;
    private static int totalEvents = 1;

    // ── Notification constants ──────────────────────────────────────────
    //
    // Constants are added on demand as Telegram subsystems are ported.
    // The totalEvents counter ensures every constant gets a unique int ID
    // regardless of insertion order, matching upstream's auto-increment pattern.

    // Theme / UI
    public static final int didSetNewTheme = totalEvents++;
    public static final int updateInterfaces = totalEvents++;
    public static final int needCheckSystemBarColors = totalEvents++;
    public static final int invalidateMotionBackground = totalEvents++;
    public static final int didReplacedPhotoInMemCache = totalEvents++;

    // File loading
    public static final int fileLoaded = totalEvents++;
    public static final int fileLoadFailed = totalEvents++;
    public static final int fileLoadProgressChanged = totalEvents++;
    public static final int filePreparingFailed = totalEvents++;
    public static final int fileNewChunkAvailable = totalEvents++;
    public static final int fileUploaded = totalEvents++;
    public static final int fileUploadFailed = totalEvents++;
    public static final int fileUploadProgressChanged = totalEvents++;
    public static final int videoLoadingStateChanged = totalEvents++;
    public static final int httpFileDidLoad = totalEvents++;
    public static final int httpFileDidFailedLoad = totalEvents++;
    public static final int onDownloadingFilesChanged = totalEvents++;
    public static final int messageReceivedByServer = totalEvents++;
    public static final int messageReceivedByAck = totalEvents++;
    public static final int messageSendError = totalEvents++;
    public static final int didReceiveCall = totalEvents++;

    // Chat / dialog lifecycle
    public static final int closeChats = totalEvents++;
    public static final int messagesDeleted = totalEvents++;
    public static final int dialogDeleted = totalEvents++;
    public static final int replaceMessagesObjects = totalEvents++;
    public static final int dialogsNeedReload = totalEvents++;
    public static final int bookmarkAdded = totalEvents++;
    public static final int articleClosed = totalEvents++;

    // Media
    public static final int mediaDidLoad = totalEvents++;
    public static final int mediaCountDidLoad = totalEvents++;
    public static final int dialogPhotosUpdate = totalEvents++;
    public static final int reloadDialogPhotos = totalEvents++;

    // User
    public static final int mainUserInfoChanged = totalEvents++;
    public static final int webViewResolved = totalEvents++;
    public static final int messagePlayingDidStart = totalEvents++;
    public static final int messagePlayingDidReset = totalEvents++;
    public static final int messagePlayingPlayStateChanged = totalEvents++;
    public static final int messagePlayingProgressDidChanged = totalEvents++;

    // Emoji / sticker (compile-only until ported)
    public static final int emojiLoaded = totalEvents++;
    public static final int customStickerCreated = totalEvents++;

    // Animation deferral (used by the event bus itself)
    public static final int startAllHeavyOperations = totalEvents++;
    public static final int stopAllHeavyOperations = totalEvents++;

    // Message ack
    public static final int messageReceivedByServer2 = totalEvents++;

    // ── Event bus state ─────────────────────────────────────────────────

    static boolean alreadyLogged;

    private final SparseArray<ArrayList<NotificationCenterDelegate>> observers = new SparseArray<>();
    private final SparseArray<ArrayList<NotificationCenterDelegate>> removeAfterBroadcast = new SparseArray<>();
    private final SparseArray<ArrayList<NotificationCenterDelegate>> addAfterBroadcast = new SparseArray<>();
    private final ArrayList<DelayedPost> delayedPosts = new ArrayList<>(10);
    private final ArrayList<Runnable> delayedRunnables = new ArrayList<>(10);
    private final ArrayList<Runnable> delayedRunnablesTmp = new ArrayList<>(10);
    private final ArrayList<DelayedPost> delayedPostsTmp = new ArrayList<>(10);
    private final ArrayList<PostponeNotificationCallback> postponeCallbackList = new ArrayList<>(10);

    private Runnable checkForExpiredNotifications;
    private int broadcasting;

    private int animationInProgressCount;
    private int animationInProgressPointer = 1;
    final HashSet<Integer> heavyOperationsCounter = new HashSet<>();
    private final SparseArray<AllowedNotifications> allowedNotifications = new SparseArray<>();

    private int currentAccount;
    private int currentHeavyOperationFlags;
    private static volatile NotificationCenter Instance;
    private static volatile NotificationCenter globalInstance;
    private static final int SINGLE_ACCOUNT = 0;

    // ── Public types ────────────────────────────────────────────────────

    public interface NotificationCenterDelegate {
        void didReceivedNotification(int id, int account, Object... args);
    }

    public interface PostponeNotificationCallback {
        boolean needPostpone(int id, int currentAccount, Object[] args);
    }

    // ── Instance access ─────────────────────────────────────────────────

    public static NotificationCenter getInstance(int num) {
        return getGlobalInstance();
    }

    public static NotificationCenter getGlobalInstance() {
        NotificationCenter localInstance = globalInstance;
        if (localInstance == null) {
            synchronized (NotificationCenter.class) {
                localInstance = globalInstance;
                if (localInstance == null) {
                    globalInstance = localInstance = new NotificationCenter(SINGLE_ACCOUNT);
                }
            }
        }
        return localInstance;
    }

    // ── Construction ────────────────────────────────────────────────────

    private NotificationCenter(int account) {
        currentAccount = account;
    }

    // ── Animation delivery deferral ─────────────────────────────────────

    public int setAnimationInProgress(int oldIndex, int[] allowedNotifications) {
        return setAnimationInProgress(oldIndex, allowedNotifications, true);
    }

    public int setAnimationInProgress(int oldIndex, int[] allowedNotifications, boolean stopHeavyOperations) {
        onAnimationFinish(oldIndex);
        if (heavyOperationsCounter.isEmpty() && stopHeavyOperations) {
            getGlobalInstance().postNotificationName(stopAllHeavyOperations, 512);
        }
        animationInProgressCount++;
        animationInProgressPointer++;
        if (stopHeavyOperations) {
            heavyOperationsCounter.add(animationInProgressPointer);
        }
        AllowedNotifications notifications = new AllowedNotifications();
        notifications.allowedIds = allowedNotifications;
        this.allowedNotifications.put(animationInProgressPointer, notifications);
        if (checkForExpiredNotifications == null) {
            AndroidUtilities.runOnUIThread(checkForExpiredNotifications = this::checkForExpiredNotifications, EXPIRE_NOTIFICATIONS_TIME);
        }
        return animationInProgressPointer;
    }

    private void checkForExpiredNotifications() {
        checkForExpiredNotifications = null;
        if (this.allowedNotifications.size() == 0) {
            return;
        }
        long minTime = Long.MAX_VALUE;
        long currentTime = SystemClock.elapsedRealtime();
        ArrayList<Integer> expiredIndices = null;
        for (int i = 0; i < allowedNotifications.size(); i++) {
            AllowedNotifications allowedNotification = allowedNotifications.valueAt(i);
            if (currentTime - allowedNotification.time > 1000) {
                if (expiredIndices == null) {
                    expiredIndices = new ArrayList<>();
                }
                expiredIndices.add(allowedNotifications.keyAt(i));
            } else {
                minTime = Math.min(allowedNotification.time, minTime);
            }
        }
        if (expiredIndices != null) {
            for (int i = 0; i < expiredIndices.size(); i++) {
                onAnimationFinish(expiredIndices.get(i));
            }
        }
        if (minTime != Long.MAX_VALUE) {
            long time = EXPIRE_NOTIFICATIONS_TIME - (currentTime - minTime);
            AndroidUtilities.runOnUIThread(() -> checkForExpiredNotifications = this::checkForExpiredNotifications, Math.max(17, time));
        }
    }

    public void updateAllowedNotifications(int transitionAnimationIndex, int[] allowedNotifications) {
        AllowedNotifications notifications = this.allowedNotifications.get(transitionAnimationIndex);
        if (notifications != null) {
            notifications.allowedIds = allowedNotifications;
        }
    }

    public void onAnimationFinish(int index) {
        AllowedNotifications allowed = allowedNotifications.get(index);
        allowedNotifications.delete(index);
        if (allowed != null) {
            animationInProgressCount--;
            if (!heavyOperationsCounter.isEmpty()) {
                heavyOperationsCounter.remove(index);
                if (heavyOperationsCounter.isEmpty()) {
                    getGlobalInstance().postNotificationName(startAllHeavyOperations, 512);
                }
            }
            if (animationInProgressCount == 0) {
                runDelayedNotifications();
            }
        }
        if (checkForExpiredNotifications != null && allowedNotifications.size() == 0) {
            AndroidUtilities.cancelRunOnUIThread(checkForExpiredNotifications);
            checkForExpiredNotifications = null;
        }
    }

    public void runDelayedNotifications() {
        if (!delayedPosts.isEmpty()) {
            delayedPostsTmp.clear();
            delayedPostsTmp.addAll(delayedPosts);
            delayedPosts.clear();
            for (int a = 0; a < delayedPostsTmp.size(); a++) {
                DelayedPost delayedPost = delayedPostsTmp.get(a);
                postNotificationNameInternal(delayedPost.id, true, delayedPost.args);
            }
            delayedPostsTmp.clear();
        }
        if (!delayedRunnables.isEmpty()) {
            delayedRunnablesTmp.clear();
            delayedRunnablesTmp.addAll(delayedRunnables);
            delayedRunnables.clear();
            for (int a = 0; a < delayedRunnablesTmp.size(); a++) {
                AndroidUtilities.runOnUIThread(delayedRunnablesTmp.get(a));
            }
            delayedRunnablesTmp.clear();
        }
    }

    public boolean isAnimationInProgress() {
        return animationInProgressCount > 0;
    }

    public int getCurrentHeavyOperationFlags() {
        return currentHeavyOperationFlags;
    }

    // ── Post notifications ──────────────────────────────────────────────

    public ArrayList<NotificationCenterDelegate> getObservers(int id) {
        return observers.get(id);
    }

    public void postNotificationNameOnUIThread(final int id, final Object... args) {
        AndroidUtilities.runOnUIThread(() -> postNotificationName(id, args));
    }

    public void postNotificationName(final int id, Object... args) {
        boolean allowDuringAnimation = id == startAllHeavyOperations
                || id == stopAllHeavyOperations
                || id == didReplacedPhotoInMemCache
                || id == closeChats
                || id == invalidateMotionBackground
                || id == needCheckSystemBarColors
                || id == messageReceivedByServer2;
        ArrayList<Integer> expiredIndices = null;
        if (!allowDuringAnimation && allowedNotifications.size() > 0) {
            int size = allowedNotifications.size();
            int allowedCount = 0;
            long currentTime = SystemClock.elapsedRealtime();
            for (int i = 0; i < allowedNotifications.size(); i++) {
                AllowedNotifications allowedNotification = allowedNotifications.valueAt(i);
                if (currentTime - allowedNotification.time > EXPIRE_NOTIFICATIONS_TIME) {
                    if (expiredIndices == null) {
                        expiredIndices = new ArrayList<>();
                    }
                    expiredIndices.add(allowedNotifications.keyAt(i));
                }
                int[] allowed = allowedNotification.allowedIds;
                if (allowed != null) {
                    for (int a = 0; a < allowed.length; a++) {
                        if (allowed[a] == id) {
                            allowedCount++;
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
            allowDuringAnimation = size == allowedCount;
        }
        if (id == startAllHeavyOperations) {
            Integer flags = (Integer) args[0];
            currentHeavyOperationFlags &= ~flags;
        } else if (id == stopAllHeavyOperations) {
            Integer flags = (Integer) args[0];
            currentHeavyOperationFlags |= flags;
        }
        postNotificationNameInternal(id, allowDuringAnimation, args);

        if (expiredIndices != null) {
            for (int i = 0; i < expiredIndices.size(); i++) {
                onAnimationFinish(expiredIndices.get(i));
            }
        }
    }

    private void postNotificationNameInternal(int id, boolean allowDuringAnimation, Object... args) {
        if (!allowDuringAnimation && isAnimationInProgress()) {
            delayedPosts.add(new DelayedPost(id, args));
            return;
        }
        if (!postponeCallbackList.isEmpty()) {
            for (int i = 0; i < postponeCallbackList.size(); i++) {
                if (postponeCallbackList.get(i).needPostpone(id, currentAccount, args)) {
                    delayedPosts.add(new DelayedPost(id, args));
                    return;
                }
            }
        }
        broadcasting++;
        ArrayList<NotificationCenterDelegate> objects = observers.get(id);
        if (objects != null && !objects.isEmpty()) {
            for (int a = 0; a < objects.size(); a++) {
                objects.get(a).didReceivedNotification(id, currentAccount, args);
            }
        }
        broadcasting--;
        if (broadcasting == 0) {
            if (removeAfterBroadcast.size() != 0) {
                for (int a = 0; a < removeAfterBroadcast.size(); a++) {
                    int key = removeAfterBroadcast.keyAt(a);
                    ArrayList<NotificationCenterDelegate> arrayList = removeAfterBroadcast.get(key);
                    for (int b = 0; b < arrayList.size(); b++) {
                        removeObserver(arrayList.get(b), key);
                    }
                }
                removeAfterBroadcast.clear();
            }
            if (addAfterBroadcast.size() != 0) {
                for (int a = 0; a < addAfterBroadcast.size(); a++) {
                    int key = addAfterBroadcast.keyAt(a);
                    ArrayList<NotificationCenterDelegate> arrayList = addAfterBroadcast.get(key);
                    for (int b = 0; b < arrayList.size(); b++) {
                        addObserver(arrayList.get(b), key);
                    }
                }
                addAfterBroadcast.clear();
            }
        }
    }

    // ── Observer management ─────────────────────────────────────────────

    public void updateObserver(boolean add, NotificationCenterDelegate observer, int id) {
        if (add) {
            addObserver(observer, id);
        } else {
            removeObserver(observer, id);
        }
    }

    public void addObserver(NotificationCenterDelegate observer, int id) {
        if (broadcasting != 0) {
            ArrayList<NotificationCenterDelegate> arrayList = addAfterBroadcast.get(id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                addAfterBroadcast.put(id, arrayList);
            }
            arrayList.add(observer);
            return;
        }
        ArrayList<NotificationCenterDelegate> objects = observers.get(id);
        if (objects == null) {
            observers.put(id, (objects = createArrayForId(id)));
        }
        if (objects.contains(observer)) {
            return;
        }
        objects.add(observer);
        if (!alreadyLogged && objects.size() > 1000) {
            alreadyLogged = true;
            Log.e("NotificationCenter", "Total observers > 1000, possible leak. id=" + id);
        }
    }

    private ArrayList<NotificationCenterDelegate> createArrayForId(int id) {
        if (id == didReplacedPhotoInMemCache || id == stopAllHeavyOperations || id == startAllHeavyOperations) {
            return new UniqArrayList<>();
        }
        return new ArrayList<>();
    }

    public void removeObserver(NotificationCenterDelegate observer, int id) {
        if (broadcasting != 0) {
            ArrayList<NotificationCenterDelegate> arrayList = removeAfterBroadcast.get(id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                removeAfterBroadcast.put(id, arrayList);
            }
            arrayList.add(observer);
            return;
        }
        ArrayList<NotificationCenterDelegate> objects = observers.get(id);
        if (objects != null) {
            objects.remove(observer);
        }
    }

    public boolean hasObservers(int id) {
        return observers.indexOfKey(id) >= 0;
    }

    // ── Postpone callbacks ──────────────────────────────────────────────

    public void addPostponeNotificationsCallback(PostponeNotificationCallback callback) {
        if (!postponeCallbackList.contains(callback)) {
            postponeCallbackList.add(callback);
        }
    }

    public void removePostponeNotificationsCallback(PostponeNotificationCallback callback) {
        if (postponeCallbackList.remove(callback)) {
            runDelayedNotifications();
        }
    }

    // ── Do-on-idle helpers ──────────────────────────────────────────────

    public void doOnIdle(Runnable runnable) {
        if (isAnimationInProgress()) {
            delayedRunnables.add(runnable);
        } else {
            runnable.run();
        }
    }

    public void removeDelayed(Runnable runnable) {
        delayedRunnables.remove(runnable);
    }

    // ── View-scoped listeners ───────────────────────────────────────────

    public Runnable listenGlobal(View view, final int id, final Utilities.Callback<Object[]> callback) {
        if (view == null || callback == null) {
            return () -> { };
        }
        final NotificationCenterDelegate delegate = (_id, account, args) -> {
            if (_id == id) {
                callback.run(args);
            }
        };
        final View.OnAttachStateChangeListener viewListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                getGlobalInstance().addObserver(delegate, id);
            }
            @Override
            public void onViewDetachedFromWindow(View v) {
                getGlobalInstance().removeObserver(delegate, id);
            }
        };
        view.addOnAttachStateChangeListener(viewListener);
        return () -> {
            view.removeOnAttachStateChangeListener(viewListener);
            getGlobalInstance().removeObserver(delegate, id);
        };
    }

    public Runnable listen(View view, final int id, final Utilities.Callback<Object[]> callback) {
        if (view == null || callback == null) {
            return () -> { };
        }
        final NotificationCenterDelegate delegate = (_id, account, args) -> {
            if (_id == id) {
                callback.run(args);
            }
        };
        final View.OnAttachStateChangeListener viewListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                addObserver(delegate, id);
            }
            @Override
            public void onViewDetachedFromWindow(View v) {
                removeObserver(delegate, id);
            }
        };
        view.addOnAttachStateChangeListener(viewListener);
        return () -> {
            view.removeOnAttachStateChangeListener(viewListener);
            removeObserver(delegate, id);
        };
    }

    public static void listenEmojiLoading(View view) {
        getGlobalInstance().listenGlobal(view, emojiLoaded, args -> view.invalidate());
    }

    public void listenOnce(int id, Runnable callback) {
        final NotificationCenterDelegate[] observer = new NotificationCenterDelegate[1];
        observer[0] = (nid, account, args) -> {
            if (nid == id && observer[0] != null) {
                if (callback != null) {
                    callback.run();
                }
                removeObserver(observer[0], id);
                observer[0] = null;
            }
        };
        addObserver(observer[0], id);
    }

    public void listenOnce(int id, Utilities.Callback3<Integer, Object[], Runnable> callback) {
        final NotificationCenterDelegate[] observer = new NotificationCenterDelegate[1];
        observer[0] = (nid, account, args) -> {
            if (nid == id && observer[0] != null) {
                if (callback != null) {
                    callback.run(account, args, () -> {
                        removeObserver(observer[0], id);
                        observer[0] = null;
                    });
                }
            }
        };
        addObserver(observer[0], id);
    }

    // ── Internal types ──────────────────────────────────────────────────

    private static class DelayedPost {
        final int id;
        final Object[] args;
        DelayedPost(int id, Object[] args) {
            this.id = id;
            this.args = args;
        }
    }

    private static class AllowedNotifications {
        int[] allowedIds;
        final long time;
        AllowedNotifications() {
            time = SystemClock.elapsedRealtime();
        }
    }

    // ── UniqArrayList ───────────────────────────────────────────────────

    private static class UniqArrayList<T> extends ArrayList<T> {
        private final HashSet<T> set = new HashSet<>();

        @Override
        public boolean add(T t) {
            if (set.add(t)) {
                return super.add(t);
            }
            return false;
        }

        @Override
        public void add(int index, T element) {
            if (set.add(element)) {
                super.add(index, element);
            }
        }

        @Override
        public boolean addAll(@NonNull Collection<? extends T> c) {
            boolean modified = false;
            for (T t : c) {
                if (add(t)) {
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean addAll(int index, @NonNull Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T remove(int index) {
            T t = super.remove(index);
            if (t != null) {
                set.remove(t);
            }
            return t;
        }

        @Override
        public boolean remove(@Nullable Object o) {
            if (set.remove(o)) {
                return super.remove(o);
            }
            return false;
        }

        @Override
        public boolean removeAll(@NonNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return set.contains(o);
        }

        @Override
        public void clear() {
            set.clear();
            super.clear();
        }
    }
}

/*
 * Stub for compile compatibility.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * Minimal shim matching Telegram's {@code MessagesController} API surface
 * used by ported animation code.
 */
public final class MessagesController {

    private static final MessagesController[] Instance = new MessagesController[1];

    public int mapProvider = 0;
    public boolean premiumLocked = true;
    public int maxGroupCount = 200;
    public int maxBroadcastCount = 100;

    private MessagesController() {
    }

    /** Matches upstream {@code MessagesController.getInstance(int)}. */
    public static MessagesController getInstance(int account) {
        MessagesController local = Instance[0];
        if (local == null) {
            synchronized (MessagesController.class) {
                local = Instance[0];
                if (local == null) {
                    Instance[0] = local = new MessagesController();
                }
            }
        }
        return local;
    }

    /**
     * Returns a SharedPreferences instance that returns default values.
     * Matches upstream {@code MessagesController.getGlobalMainSettings()}.
     */
    public static SharedPreferences getGlobalMainSettings() {
        return ApplicationLoader.applicationContext.getSharedPreferences("global", 0);
    }

    public SharedPreferences getMainSettings() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainConfig", 0);
    }

    /** LinuxDo has no Telegram-premium concept. */
    public boolean isPremiumUser(TLRPC.User user) {
        return false;
    }

    public TLRPC.UserFull getUserFull(long userId) {
        return null;
    }

    public TLRPC.User getUser(Long userId) {
        return null;
    }

    public TLRPC.Chat getChat(Long chatId) {
        return null;
    }

    public TLObject getUserOrChat(String username) {
        return null;
    }

    public TLObject getUserOrChat(long dialogId) {
        return dialogId > 0 ? getUser(dialogId) : getChat(-dialogId);
    }

    public void openByUserName(String username, org.telegram.ui.ActionBar.BaseFragment fragment, int type) {
    }

    public static TLRPC.InputPeer getInputChannel(TLRPC.Chat chat) {
        return new TLRPC.TL_inputPeerChannel();
    }

    public void processUpdates(TLRPC.Updates updates, boolean fromQueue) {
    }

    public void generateJoinMessage(long chatId, boolean force) {
    }

    public void loadFullChat(long chatId, int classGuid, boolean force) {
    }

    public TLRPC.InputPeer getInputPeer(long id) {
        return null;
    }

    public void putUser(TLRPC.User user, boolean fromCache) {
    }

    public void putChat(TLRPC.Chat chat, boolean fromCache) {
    }

    public void putUsers(java.util.ArrayList<TLRPC.User> users, boolean fromCache) {
    }

    public void putChats(java.util.ArrayList<TLRPC.Chat> chats, boolean fromCache) {
    }

    public void deleteMessages(java.util.ArrayList<Integer> ids, Object scheduled, Object updates, long dialogId, int topicId, boolean revoke, int mode) {
    }

    public boolean shouldEncryptPhotoOrVideo(int account, TLRPC.Message message) {
        return false;
    }

    public boolean shouldEncryptPhotoOrVideo(TLRPC.Message message) {
        return false;
    }

    public boolean isPeerNoForwards(long dialogId) {
        return false;
    }

    public void loadFullUser(TLRPC.User user, int classGuid, boolean force) {
    }

    public TLRPC.UserFull getUserFull(Long userId) {
        return null;
    }

    // Caption length / paid-message constants
    public int captionLengthLimitDefault = 1024;
    public int captionLengthLimitPremium = 2048;
    public int revokeTimePmLimit = Integer.MAX_VALUE;
    public int revokeTimeLimit = Integer.MAX_VALUE;
    public String linkPrefix = "t.me";
    public boolean sponsoredLinksInappAllow = false;

    public boolean premiumFeaturesBlocked() {
        return false;
    }

    public TranslateController getTranslateController() {
        return TranslateController.getInstance(UserConfig.selectedAccount);
    }

    public DialogPhotos getDialogPhotos(long dialogId) {
        return new DialogPhotos();
    }

    public long getSendPaidMessagesStars(long dialogId) {
        return 0L;
    }

    public void disableAds(boolean disable) {
    }

    public static class DialogPhotos {
        public boolean isLoading() { return false; }
        public java.util.List<TLRPC.Photo> getPhotos() { return java.util.Collections.emptyList(); }
        public void loadCache() {}
        public void load() {}
    }
}

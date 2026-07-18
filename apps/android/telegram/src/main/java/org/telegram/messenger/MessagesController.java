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

    public final BlurConfig config = new BlurConfig();

    public static class BlurConfig {
        public final ConfigBoolean disableBlurInLightTheme = new ConfigBoolean(false);
        public final ConfigBoolean disableBlurInDarkTheme = new ConfigBoolean(false);

        public static class ConfigBoolean {
            private final boolean value;
            public ConfigBoolean(boolean defaultValue) { this.value = defaultValue; }
            public boolean get() { return value; }
        }
    }

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

    // ── Single-account browser settings façade (pin WebBrowserSettings @ 12.9.0) ──
    // Maps Telegram MessagesController browser policy APIs onto device-local
    // SharedConfig + RestrictedDomainsList. One account only; no MTProto sync.

    public boolean isWebBrowserInAppEnabled() {
        SharedConfig.loadBrowserConfig();
        return SharedConfig.inappBrowser;
    }

    public void toggleWebBrowserInAppEnabled() {
        SharedConfig.toggleInappBrowser();
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.webBrowserSettingsUpdate);
    }

    public boolean isWebBrowserUseCustomTabs() {
        SharedConfig.loadBrowserConfig();
        return SharedConfig.customTabs;
    }

    public void toggleWebBrowserUseCustomTabs(boolean enabled) {
        SharedConfig.toggleCustomTabs(enabled);
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.webBrowserSettingsUpdate);
    }

    /**
     * Pin-shaped: whether this URL should open in the in-app browser.
     * Host: {@link SharedConfig#inappBrowser} AND domain not on
     * {@link org.telegram.ui.web.RestrictedDomainsList}.
     */
    public boolean isWebBrowserOpenInApp(String url) {
        SharedConfig.loadBrowserConfig();
        if (!SharedConfig.inappBrowser || url == null) {
            return false;
        }
        return !org.telegram.ui.web.RestrictedDomainsList.getInstance().isRestrictedUrl(url);
    }

    /**
     * Pin {@code getWebBrowserExceptionsList(externalExceptions)}:
     * {@code true} = never-open-in-app list (RestrictedDomainsList);
     * {@code false} = always-open-in-app dual list (not ported — empty).
     */
    public java.util.List<org.telegram.tgnet.tl.TL_account.WebDomainException> getWebBrowserExceptionsList(boolean externalExceptions) {
        if (!externalExceptions) {
            return java.util.Collections.emptyList();
        }
        org.telegram.ui.web.RestrictedDomainsList list =
                org.telegram.ui.web.RestrictedDomainsList.getInstance();
        list.load();
        java.util.ArrayList<org.telegram.tgnet.tl.TL_account.WebDomainException> out =
                new java.util.ArrayList<>();
        for (String domain : list.getRestrictedDomainsList()) {
            org.telegram.tgnet.tl.TL_account.WebDomainException e =
                    new org.telegram.tgnet.tl.TL_account.WebDomainException();
            e.domain = domain;
            e.url = "https://" + domain;
            org.telegram.ui.web.WebMetadataCache.WebMetadata meta =
                    org.telegram.ui.web.WebMetadataCache.getInstance().get(domain);
            if (meta != null && !android.text.TextUtils.isEmpty(meta.sitename)) {
                e.title = meta.sitename;
            } else if (meta != null && !android.text.TextUtils.isEmpty(meta.title)) {
                e.title = meta.title;
            } else {
                e.title = domain;
            }
            e.favicon = 0; // host: Bitmap favicons not document ids; letter placeholder
            out.add(e);
        }
        return out;
    }

    /**
     * @param externalException {@code true}: never open in-app (RestrictedDomainsList).
     *                          {@code false}: always-open-in-app dual list (no-op locally).
     */
    public void addWebBrowserException(String domain, boolean externalException) {
        if (!externalException) {
            return;
        }
        org.telegram.ui.web.RestrictedDomainsList.getInstance().addRestrictedDomain(domain);
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.webBrowserSettingsUpdate);
    }

    public void removeWebBrowserException(String domain) {
        org.telegram.ui.web.RestrictedDomainsList.getInstance().removeRestrictedDomain(domain);
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.webBrowserSettingsUpdate);
    }

    public void clearAllWebBrowserExceptions() {
        org.telegram.ui.web.RestrictedDomainsList.getInstance().clearRestrictedDomains();
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.webBrowserSettingsUpdate);
    }

    public boolean isWebBrowserExceptionsLimitReached(boolean externalExceptions) {
        if (!externalExceptions) {
            return false;
        }
        return org.telegram.ui.web.RestrictedDomainsList.getInstance()
                .getRestrictedDomainsList().size() >= 200;
    }

}

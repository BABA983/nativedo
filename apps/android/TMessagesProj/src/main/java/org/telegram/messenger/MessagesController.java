/*
 * Stub for compile compatibility.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

/**
 * Minimal shim matching Telegram's {@code MessagesController} API surface
 * used by ported animation code.
 */
public final class MessagesController {

    private static final MessagesController[] Instance = new MessagesController[1];
    private volatile static long lastThemeCheckTime;
    private final Runnable themeCheckRunnable = Theme::checkAutoNightThemeConditions;

    public int mapProvider = 0;
    public boolean premiumLocked = true;
    public int maxGroupCount = 200;
    public int maxBroadcastCount = 100;
    public int largeQueueMaxActiveOperations = 2;
    public int smallQueueMaxActiveOperations = 5;

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

    public void updateTimerProc() {
        long currentTime = System.currentTimeMillis();
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED && Math.abs(currentTime - lastThemeCheckTime) >= 60) {
            AndroidUtilities.runOnUIThread(themeCheckRunnable);
            lastThemeCheckTime = currentTime;
        }
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
    // Telegram stores this model in the account database and syncs it through
    // account.getWebBrowserSettings. NativeDo has no MTProto backend, so the
    // same state machine is persisted locally as JSON instead.

    private static final String WEB_BROWSER_SETTINGS_KEY = "web_browser_settings_v2";
    private org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings webBrowserSettings;
    private boolean webBrowserSettingsLoaded;

    private org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings newWebBrowserSettings() {
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings =
                new org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings();
        settings.open_external_browser = false;
        settings.display_close_button = true;
        return settings;
    }

    private static String normalizeWebDomain(String value) {
        String text = value == null ? "" : value.trim();
        if (android.text.TextUtils.isEmpty(text)) {
            return "";
        }
        try {
            android.net.Uri uri = android.net.Uri.parse(text);
            if (uri.getHost() == null) {
                uri = android.net.Uri.parse("https://" + text);
            }
            String host = uri.getHost();
            if (android.text.TextUtils.isEmpty(host)) {
                return "";
            }
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JSONObject exceptionToJson(
            org.telegram.tgnet.tl.TL_account.WebDomainException exception) {
        JSONObject object = new JSONObject();
        try {
            object.put("domain", exception.domain);
            object.put("url", exception.url);
            object.put("title", exception.title);
            object.put("favicon", exception.favicon);
        } catch (Exception ignored) {
        }
        return object;
    }

    private static void readExceptionList(
            JSONArray array,
            java.util.ArrayList<org.telegram.tgnet.tl.TL_account.WebDomainException> target) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String domain = normalizeWebDomain(object.optString("domain"));
            if (android.text.TextUtils.isEmpty(domain)) {
                continue;
            }
            org.telegram.tgnet.tl.TL_account.WebDomainException exception =
                    new org.telegram.tgnet.tl.TL_account.WebDomainException();
            exception.domain = domain;
            exception.url = object.optString("url", "https://" + domain);
            exception.title = object.optString("title", domain);
            exception.favicon = object.optLong("favicon", 0L);
            target.add(exception);
        }
    }

    private org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings readWebBrowserSettings() {
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = newWebBrowserSettings();
        try {
            String serialized = getGlobalMainSettings().getString(WEB_BROWSER_SETTINGS_KEY, null);
            if (android.text.TextUtils.isEmpty(serialized)) {
                return settings;
            }
            JSONObject object = new JSONObject(serialized);
            settings.open_external_browser = object.optBoolean("open_external_browser", false);
            settings.display_close_button = object.optBoolean("display_close_button", true);
            readExceptionList(object.optJSONArray("external_exceptions") == null
                    ? new JSONArray() : object.optJSONArray("external_exceptions"), settings.external_exceptions);
            readExceptionList(object.optJSONArray("inapp_exceptions") == null
                    ? new JSONArray() : object.optJSONArray("inapp_exceptions"), settings.inapp_exceptions);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return settings;
    }

    private void saveWebBrowserSettings() {
        if (webBrowserSettings == null) {
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("open_external_browser", webBrowserSettings.open_external_browser);
            object.put("display_close_button", webBrowserSettings.display_close_button);
            JSONArray external = new JSONArray();
            for (org.telegram.tgnet.tl.TL_account.WebDomainException exception : webBrowserSettings.external_exceptions) {
                external.put(exceptionToJson(exception));
            }
            JSONArray inApp = new JSONArray();
            for (org.telegram.tgnet.tl.TL_account.WebDomainException exception : webBrowserSettings.inapp_exceptions) {
                inApp.put(exceptionToJson(exception));
            }
            object.put("external_exceptions", external);
            object.put("inapp_exceptions", inApp);
            getGlobalMainSettings().edit().putString(WEB_BROWSER_SETTINGS_KEY, object.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void loadWebBrowserConfig() {
        loadWebBrowserConfig(false);
    }

    public void loadWebBrowserConfig(boolean force) {
        if (webBrowserSettingsLoaded && !force) {
            return;
        }
        webBrowserSettings = readWebBrowserSettings();
        webBrowserSettingsLoaded = true;
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.webBrowserSettingsUpdate);
    }

    private org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings getWebBrowserSettings() {
        loadWebBrowserConfig(false);
        return webBrowserSettings;
    }

    private void setWebBrowserSettings(
            org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings) {
        webBrowserSettings = settings;
        webBrowserSettingsLoaded = true;
        saveWebBrowserSettings();
        NotificationCenter.getInstance(UserConfig.selectedAccount)
                .postNotificationName(NotificationCenter.webBrowserSettingsUpdate);
    }

    public boolean isWebBrowserInAppEnabled() {
        return !getWebBrowserSettings().open_external_browser;
    }

    public boolean isWebBrowserUseCustomTabs() {
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = getWebBrowserSettings();
        return settings.open_external_browser && settings.display_close_button;
    }

    public boolean isWebBrowserOpenInApp(String url) {
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = getWebBrowserSettings();
        return url != null && !isWebBrowserOpenInExternal(settings, url);
    }

    public java.util.List<org.telegram.tgnet.tl.TL_account.WebDomainException> getWebBrowserExceptionsList(boolean externalExceptions) {
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = getWebBrowserSettings();
        return externalExceptions ? settings.external_exceptions : settings.inapp_exceptions;
    }

    public void toggleWebBrowserInAppEnabled() {
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = getWebBrowserSettings();
        settings.open_external_browser = !settings.open_external_browser;
        setWebBrowserSettings(settings);
    }

    public void toggleWebBrowserUseCustomTabs(boolean useCustomTabs) {
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = getWebBrowserSettings();
        settings.display_close_button = useCustomTabs;
        setWebBrowserSettings(settings);
    }

    public boolean isWebBrowserExceptionsLimitReached(boolean externalExceptions) {
        return getWebBrowserExceptionsList(externalExceptions).size() >= 100;
    }

    public void addWebBrowserException(String url, boolean openExternalBrowser) {
        String domain = normalizeWebDomain(url);
        if (android.text.TextUtils.isEmpty(domain)) {
            return;
        }
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = getWebBrowserSettings();
        org.telegram.tgnet.tl.TL_account.WebDomainException exception =
                new org.telegram.tgnet.tl.TL_account.WebDomainException();
        exception.domain = domain;
        exception.url = "https://" + domain;
        org.telegram.ui.web.WebMetadataCache.WebMetadata meta =
                org.telegram.ui.web.WebMetadataCache.getInstance().get(domain);
        exception.title = meta != null && !android.text.TextUtils.isEmpty(meta.sitename)
                ? meta.sitename : meta != null && !android.text.TextUtils.isEmpty(meta.title)
                ? meta.title : domain;
        exception.favicon = 0;
        java.util.ArrayList<org.telegram.tgnet.tl.TL_account.WebDomainException> target =
                openExternalBrowser ? settings.external_exceptions : settings.inapp_exceptions;
        java.util.ArrayList<org.telegram.tgnet.tl.TL_account.WebDomainException> other =
                openExternalBrowser ? settings.inapp_exceptions : settings.external_exceptions;
        addWebBrowserExceptionToList(target, exception);
        deleteWebBrowserExceptionFromList(other, exception);
        setWebBrowserSettings(settings);
    }

    public void removeWebBrowserException(String url) {
        String domain = normalizeWebDomain(url);
        if (android.text.TextUtils.isEmpty(domain)) {
            return;
        }
        org.telegram.tgnet.tl.TL_account.WebDomainException exception =
                new org.telegram.tgnet.tl.TL_account.WebDomainException();
        exception.domain = domain;
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = getWebBrowserSettings();
        deleteWebBrowserExceptionFromList(settings.external_exceptions, exception);
        deleteWebBrowserExceptionFromList(settings.inapp_exceptions, exception);
        setWebBrowserSettings(settings);
    }

    public void clearAllWebBrowserExceptions() {
        org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings = getWebBrowserSettings();
        settings.open_external_browser = true;
        settings.display_close_button = true;
        settings.external_exceptions.clear();
        settings.inapp_exceptions.clear();
        setWebBrowserSettings(settings);
    }

    private static void deleteWebBrowserExceptionFromList(
            java.util.ArrayList<org.telegram.tgnet.tl.TL_account.WebDomainException> list,
            org.telegram.tgnet.tl.TL_account.WebDomainException exception) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (org.telegram.tgnet.tl.TL_account.WebDomainException.equalsByDomain(list.get(i), exception)) {
                list.remove(i);
            }
        }
    }

    private static void addWebBrowserExceptionToList(
            java.util.ArrayList<org.telegram.tgnet.tl.TL_account.WebDomainException> list,
            org.telegram.tgnet.tl.TL_account.WebDomainException exception) {
        deleteWebBrowserExceptionFromList(list, exception);
        list.add(exception);
    }

    private static boolean isWebBrowserOpenInExternal(
            org.telegram.tgnet.tl.TL_account.TL_webBrowserSettings settings, String url) {
        String domain = normalizeWebDomain(url);
        if (android.text.TextUtils.isEmpty(domain)) {
            return false;
        }
        boolean openExternal = settings.open_external_browser;
        if (openExternal) {
            return !hasWebBrowserException(settings.inapp_exceptions, domain);
        }
        return hasWebBrowserException(settings.external_exceptions, domain);
    }

    private static boolean hasWebBrowserException(
            java.util.ArrayList<org.telegram.tgnet.tl.TL_account.WebDomainException> list,
            String domain) {
        String normalized = domain.toLowerCase(java.util.Locale.ROOT);
        for (org.telegram.tgnet.tl.TL_account.WebDomainException exception : list) {
            String exceptionDomain = exception.domain == null ? "" : exception.domain.toLowerCase(java.util.Locale.ROOT);
            if (normalized.equals(exceptionDomain) || normalized.endsWith("." + exceptionDomain)) {
                return true;
            }
        }
        return false;
    }

}

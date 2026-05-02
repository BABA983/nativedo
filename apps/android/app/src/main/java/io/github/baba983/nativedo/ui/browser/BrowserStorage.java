// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/web/BrowserHistory.java; org/telegram/ui/web/BookmarksFragment.java; org/telegram/ui/web/WebBrowserSettings.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Persists browser feature state from NativeDo's browser package.
// ADAPTED-BACKEND:
//   - Uses Android SharedPreferences instead of Telegram FileLoader/SerializedData/Saved Messages.
//   - Keeps Telegram-style local browser history semantics with id/url/title/favicon/time metadata.
//   - Replaces Telegram Saved Messages-backed bookmarks with explicit NativeDo local bookmark storage.
//   - Stores browser settings needed by the current NativeDo browser slice.
// OMITTED:
//   - Telegram account-scoped history/bookmark sync, WebMetadataCache, and restricted-domain counters.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/web/BrowserHistory.java, org/telegram/ui/web/BookmarksFragment.java,
 * and org/telegram/ui/web/WebBrowserSettings.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
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
package io.github.baba983.nativedo.ui.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class BrowserStorage {

    private static final String PREFS = "nativedo_browser";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_SEARCH_ENGINE = "search_engine";
    private static final String KEY_OPEN_IN_APP = "open_in_app";
    private static final String KEY_RESTRICTED_DOMAINS = "restricted_domains";
    private static final String KEY_INSTANT_VIEW_FONT_SCALE = "instant_view_font_scale";
    private static final int MAX_HISTORY_ENTRIES = 200;

    static final float[] INSTANT_VIEW_FONT_SCALES = new float[] { 0.85f, 1.0f, 1.15f, 1.30f };

    static final class Entry {
        long id;
        long time;
        String url;
        String title;
        String subtitle;
        @Nullable Bitmap favicon;

        String displayTitle() {
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
            String host = hostOf(url);
            return TextUtils.isEmpty(host) ? "about:blank" : host;
        }

        String displaySubtitle() {
            if (!TextUtils.isEmpty(subtitle)) {
                return subtitle;
            }
            return TextUtils.isEmpty(url) ? "about:blank" : url;
        }
    }

    enum SearchEngine {
        LINUX_DO("linux_do"),
        GOOGLE("google"),
        BING("bing");

        final String value;

        SearchEngine(String value) {
            this.value = value;
        }

        static SearchEngine fromValue(@Nullable String value) {
            for (SearchEngine engine : values()) {
                if (engine.value.equals(value)) {
                    return engine;
                }
            }
            return GOOGLE;
        }
    }

    private BrowserStorage() {
    }

    static ArrayList<Entry> getHistory(Context context) {
        return readEntries(context, KEY_HISTORY);
    }

    static void pushHistory(Context context, String url, String title, @Nullable Bitmap favicon) {
        if (context == null || TextUtils.isEmpty(url)) {
            return;
        }
        ArrayList<Entry> history = readEntries(context, KEY_HISTORY);
        long id = idFromUrl(url);
        int existingIndex = -1;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).id == id) {
                existingIndex = i;
                break;
            }
        }
        Entry entry = existingIndex >= 0 ? history.remove(existingIndex) : new Entry();
        entry.id = id;
        entry.time = System.currentTimeMillis();
        entry.url = url;
        entry.title = title;
        entry.subtitle = url;
        entry.favicon = favicon;
        history.add(entry);
        while (history.size() > MAX_HISTORY_ENTRIES) {
            history.remove(0);
        }
        writeEntries(context, KEY_HISTORY, history);
    }

    static void clearHistory(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().remove(KEY_HISTORY).apply();
    }

    static ArrayList<Entry> getBookmarks(Context context) {
        ArrayList<Entry> bookmarks = readEntries(context, KEY_BOOKMARKS);
        Collections.sort(bookmarks, (left, right) -> Long.compare(right.time, left.time));
        return bookmarks;
    }

    static boolean isBookmarked(Context context, String url) {
        if (context == null || TextUtils.isEmpty(url)) {
            return false;
        }
        ArrayList<Entry> bookmarks = readEntries(context, KEY_BOOKMARKS);
        long id = idFromUrl(url);
        for (Entry entry : bookmarks) {
            if (entry.id == id) {
                return true;
            }
        }
        return false;
    }

    static boolean toggleBookmark(Context context, String url, String title, @Nullable Bitmap favicon) {
        if (context == null || TextUtils.isEmpty(url)) {
            return false;
        }
        ArrayList<Entry> bookmarks = readEntries(context, KEY_BOOKMARKS);
        long id = idFromUrl(url);
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).id == id) {
                bookmarks.remove(i);
                writeEntries(context, KEY_BOOKMARKS, bookmarks);
                return false;
            }
        }
        Entry entry = new Entry();
        entry.id = id;
        entry.time = System.currentTimeMillis();
        entry.url = url;
        entry.title = title;
        entry.subtitle = url;
        entry.favicon = favicon;
        bookmarks.add(entry);
        writeEntries(context, KEY_BOOKMARKS, bookmarks);
        return true;
    }

    static void clearBookmarks(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().remove(KEY_BOOKMARKS).apply();
    }

    static SearchEngine getSearchEngine(Context context) {
        return SearchEngine.fromValue(prefs(context).getString(KEY_SEARCH_ENGINE, SearchEngine.GOOGLE.value));
    }

    static void setSearchEngine(Context context, SearchEngine engine) {
        if (context == null || engine == null) {
            return;
        }
        prefs(context).edit().putString(KEY_SEARCH_ENGINE, engine.value).apply();
    }

    static boolean openExternalLinksInApp(Context context) {
        return prefs(context).getBoolean(KEY_OPEN_IN_APP, true);
    }

    static void setOpenExternalLinksInApp(Context context, boolean value) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putBoolean(KEY_OPEN_IN_APP, value).apply();
    }

    static ArrayList<String> getRestrictedDomains(Context context) {
        return new ArrayList<>(readRestrictedDomains(context));
    }

    static void addRestrictedDomain(Context context, String domain) {
        String normalized = normalizeRestrictedDomain(domain);
        if (context == null || TextUtils.isEmpty(normalized)) {
            return;
        }
        LinkedHashSet<String> domains = readRestrictedDomains(context);
        domains.add(normalized);
        prefs(context).edit().putStringSet(KEY_RESTRICTED_DOMAINS, domains).apply();
    }

    static void removeRestrictedDomain(Context context, String domain) {
        String normalized = normalizeRestrictedDomain(domain);
        if (context == null || TextUtils.isEmpty(normalized)) {
            return;
        }
        LinkedHashSet<String> domains = readRestrictedDomains(context);
        domains.remove(normalized);
        prefs(context).edit().putStringSet(KEY_RESTRICTED_DOMAINS, domains).apply();
    }

    static void clearRestrictedDomains(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().remove(KEY_RESTRICTED_DOMAINS).apply();
    }

    static boolean isRestricted(Context context, String url) {
        String host = hostOf(url);
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        List<String> domains = getRestrictedDomains(context);
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    static String normalizeRestrictedDomain(String value) {
        String text = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        try {
            Uri uri = Uri.parse(text);
            if (uri.getHost() == null) {
                uri = Uri.parse("https://" + text);
            }
            String host = uri.getHost();
            if (TextUtils.isEmpty(host)) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static LinkedHashSet<String> readRestrictedDomains(Context context) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        if (context == null) {
            return domains;
        }
        for (String domain : prefs(context).getStringSet(KEY_RESTRICTED_DOMAINS, Collections.emptySet())) {
            String normalized = normalizeRestrictedDomain(domain);
            if (!TextUtils.isEmpty(normalized)) {
                domains.add(normalized);
            }
        }
        return domains;
    }

    static float getInstantViewFontScale(Context context) {
        if (context == null) {
            return 1.0f;
        }
        float value = prefs(context).getFloat(KEY_INSTANT_VIEW_FONT_SCALE, 1.0f);
        return clampFontScale(value);
    }

    static void setInstantViewFontScale(Context context, float scale) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putFloat(KEY_INSTANT_VIEW_FONT_SCALE, clampFontScale(scale)).apply();
    }

    static int instantViewFontScaleIndex(float scale) {
        float clamped = clampFontScale(scale);
        int bestIndex = 1;
        float bestDiff = Float.MAX_VALUE;
        for (int i = 0; i < INSTANT_VIEW_FONT_SCALES.length; i++) {
            float diff = Math.abs(INSTANT_VIEW_FONT_SCALES[i] - clamped);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static float clampFontScale(float scale) {
        if (scale < INSTANT_VIEW_FONT_SCALES[0]) {
            return INSTANT_VIEW_FONT_SCALES[0];
        }
        if (scale > INSTANT_VIEW_FONT_SCALES[INSTANT_VIEW_FONT_SCALES.length - 1]) {
            return INSTANT_VIEW_FONT_SCALES[INSTANT_VIEW_FONT_SCALES.length - 1];
        }
        return scale;
    }

    static String buildSearchUrl(Context context, String query) {
        SearchEngine engine = getSearchEngine(context);
        String encoded = Uri.encode(query == null ? "" : query);
        switch (engine) {
            case LINUX_DO:
                return "https://linux.do/search?q=" + encoded;
            case BING:
                return "https://www.bing.com/search?q=" + encoded;
            case GOOGLE:
            default:
                return "https://www.google.com/search?q=" + encoded;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    private static ArrayList<Entry> readEntries(Context context, String key) {
        ArrayList<Entry> result = new ArrayList<>();
        if (context == null) {
            return result;
        }
        String json = prefs(context).getString(key, null);
        if (TextUtils.isEmpty(json)) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                Entry entry = new Entry();
                entry.id = object.optLong("id");
                entry.time = object.optLong("time");
                entry.url = object.optString("url", "");
                entry.title = object.optString("title", "");
                entry.subtitle = object.optString("subtitle", "");
                entry.favicon = decodeBitmap(object.optString("favicon", null));
                if (!TextUtils.isEmpty(entry.url)) {
                    result.add(entry);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void writeEntries(Context context, String key, ArrayList<Entry> entries) {
        if (context == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            if (entry == null || TextUtils.isEmpty(entry.url)) {
                continue;
            }
            JSONObject object = new JSONObject();
            try {
                object.put("id", entry.id);
                object.put("time", entry.time);
                object.put("url", entry.url);
                object.put("title", entry.title == null ? "" : entry.title);
                object.put("subtitle", entry.subtitle == null ? "" : entry.subtitle);
                String favicon = encodeBitmap(entry.favicon);
                if (favicon != null) {
                    object.put("favicon", favicon);
                }
            } catch (Exception ignored) {
            }
            array.put(object);
        }
        prefs(context).edit().putString(key, array.toString()).apply();
    }

    private static long idFromUrl(String url) {
        return url == null ? 0L : url.hashCode();
    }

    private static String hostOf(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (Exception ignored) {
            return "";
        }
    }

    @Nullable
    private static String encodeBitmap(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Bitmap decodeBitmap(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(value, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }
}

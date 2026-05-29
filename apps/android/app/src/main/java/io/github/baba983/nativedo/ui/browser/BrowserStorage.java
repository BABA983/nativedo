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

import org.telegram.ui.web.SearchEngine;

final class BrowserStorage {

    private static final String PREFS = "nativedo_browser";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_SEARCH_ENGINE = "search_engine";
    private static final String KEY_RECENT_SEARCHES = "recent_searches";
    private static final String KEY_OPEN_IN_APP = "open_in_app";
    private static final String KEY_INSTANT_VIEW_FONT_SCALE = "instant_view_font_scale";
    private static final int MAX_HISTORY_ENTRIES = 200;
    private static final int MAX_RECENT_SEARCHES = 20;

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

    /** Predefined search engines for NativeDo. Uses upstream {@link SearchEngine} data class. */
    static final SearchEngine GOOGLE = new SearchEngine("Google", "https://www.google.com/search?q=", "https://suggestqueries.google.com/complete/search?client=chrome&q=", "https://policies.google.com/privacy");
    static final SearchEngine DUCKDUCKGO = new SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=", "https://duckduckgo.com/ac/?q=", "https://duckduckgo.com/privacy");
    static final SearchEngine BING = new SearchEngine("Bing", "https://www.bing.com/search?q=", "https://api.bing.com/osjson.aspx?query=", "https://privacy.microsoft.com/en-us/privacystatement");
    static final SearchEngine YAHOO = new SearchEngine("Yahoo", "https://search.yahoo.com/search?p=", "https://sugg.search.yahoo.net/sg/?output=json&nresults=10&command=", "https://legal.yahoo.com/us/en/yahoo/privacy/index.html");
    static final SearchEngine BRAVE = new SearchEngine("Brave Search", "https://search.brave.com/search?q=", "https://search.brave.com/api/suggest?q=", "https://search.brave.com/help/privacy-policy");

    private static final SearchEngine[] NATIVEDO_ENGINES = { GOOGLE, DUCKDUCKGO, BING, YAHOO, BRAVE };

    /** Engine key stored in preferences; matches the index in {@link #NATIVEDO_ENGINES}. */
    private static final String KEY_SEARCH_ENGINE_INDEX = "search_engine_index";

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
        // Migrate from old string-based pref to index-based pref
        SharedPreferences p = prefs(context);
        if (p.contains(KEY_SEARCH_ENGINE) && !p.contains(KEY_SEARCH_ENGINE_INDEX)) {
            String oldValue = p.getString(KEY_SEARCH_ENGINE, null);
            int index = 0;
            String[] oldKeys = {"google", "duckduckgo", "bing", "yahoo", "brave"};
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldKeys[i].equals(oldValue)) {
                    index = i;
                    break;
                }
            }
            p.edit().putInt(KEY_SEARCH_ENGINE_INDEX, index).remove(KEY_SEARCH_ENGINE).apply();
        }
        int index = p.getInt(KEY_SEARCH_ENGINE_INDEX, 0);
        return NATIVEDO_ENGINES[Math.max(0, Math.min(index, NATIVEDO_ENGINES.length - 1))];
    }

    static void setSearchEngine(Context context, SearchEngine engine) {
        if (context == null || engine == null) {
            return;
        }
        for (int i = 0; i < NATIVEDO_ENGINES.length; i++) {
            if (NATIVEDO_ENGINES[i] == engine) {
                prefs(context).edit().putInt(KEY_SEARCH_ENGINE_INDEX, i).apply();
                return;
            }
        }
    }

    static SearchEngine[] getSearchEngines() {
        return NATIVEDO_ENGINES;
    }

    static ArrayList<String> getRecentSearches(Context context) {
        ArrayList<String> result = new ArrayList<>();
        if (context == null) {
            return result;
        }
        String json = prefs(context).getString(KEY_RECENT_SEARCHES, null);
        if (TextUtils.isEmpty(json)) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i);
                if (!TextUtils.isEmpty(value)) {
                    result.add(value);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    static void pushRecentSearch(Context context, String query) {
        if (context == null || TextUtils.isEmpty(query)) {
            return;
        }
        String normalized = query.trim();
        if (TextUtils.isEmpty(normalized)) {
            return;
        }
        ArrayList<String> searches = getRecentSearches(context);
        for (int i = searches.size() - 1; i >= 0; i--) {
            if (normalized.equalsIgnoreCase(searches.get(i))) {
                searches.remove(i);
            }
        }
        searches.add(0, normalized);
        while (searches.size() > MAX_RECENT_SEARCHES) {
            searches.remove(searches.size() - 1);
        }
        writeRecentSearches(context, searches);
    }

    static void clearRecentSearches(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().remove(KEY_RECENT_SEARCHES).apply();
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
        return getSearchEngine(context).getSearchURL(query);
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

    private static void writeRecentSearches(Context context, ArrayList<String> searches) {
        if (context == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (String search : searches) {
            if (!TextUtils.isEmpty(search)) {
                array.put(search);
            }
        }
        prefs(context).edit().putString(KEY_RECENT_SEARCHES, array.toString()).apply();
    }

    @Nullable
    static String encodeBitmap(@Nullable Bitmap bitmap) {
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
    static Bitmap decodeBitmap(@Nullable String value) {
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

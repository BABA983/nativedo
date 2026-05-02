// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/web/WebMetadataCache.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Uses SharedPreferences JSON instead of Telegram FileLoader/TLObject serialization.
// ADAPTED-BACKEND:
//   - Stores NativeDo browser metadata by domain for address/history/bookmark/restricted-domain rows.
// OMITTED:
//   - Hidden WebView favicon/sitename retrieval; live BrowserWebViewContainer metadata is saved instead.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/ui/web/WebMetadataCache.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

final class BrowserMetadataCache {

    private static final String PREFS = "nativedo_browser_metadata";
    private static final String KEY_METADATA = "metadata";
    private static final int MAX_COUNT = 100;
    private static final long EXPIRATION = 1000L * 60L * 60L * 24L * 7L;

    private BrowserMetadataCache() {
    }

    static final class Metadata {
        long time = System.currentTimeMillis();
        String domain;
        String title;
        String siteName;
        int actionBarColor;
        int backgroundColor;
        @Nullable Bitmap favicon;

        String displayTitle() {
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
            if (!TextUtils.isEmpty(siteName)) {
                return siteName;
            }
            return TextUtils.isEmpty(domain) ? "about:blank" : domain;
        }
    }

    @Nullable
    static Metadata get(Context context, @Nullable String domain) {
        String normalized = normalizeDomain(domain);
        if (context == null || TextUtils.isEmpty(normalized)) {
            return null;
        }
        ArrayList<Metadata> metadata = readMetadata(context);
        for (Metadata item : metadata) {
            if (normalized.equals(item.domain)) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    static Metadata getForUrl(Context context, @Nullable String url) {
        return get(context, domainFromUrl(url));
    }

    static void save(
            Context context,
            @Nullable String url,
            @Nullable String title,
            @Nullable String siteName,
            @Nullable Bitmap favicon,
            int actionBarColor,
            int backgroundColor
    ) {
        if (context == null) {
            return;
        }
        String domain = domainFromUrl(url);
        if (TextUtils.isEmpty(domain)) {
            return;
        }
        ArrayList<Metadata> metadata = readMetadata(context);
        Metadata target = null;
        for (Metadata item : metadata) {
            if (domain.equals(item.domain)) {
                target = item;
                break;
            }
        }
        if (target == null) {
            target = new Metadata();
            target.domain = domain;
            metadata.add(target);
        }
        target.time = System.currentTimeMillis();
        if (!TextUtils.isEmpty(title)) {
            target.title = title;
        }
        if (siteName != null) {
            target.siteName = siteName;
        }
        if (favicon != null) {
            target.favicon = favicon;
        }
        if (actionBarColor != 0) {
            target.actionBarColor = actionBarColor;
        }
        if (backgroundColor != 0) {
            target.backgroundColor = backgroundColor;
        }
        writeMetadata(context, metadata);
    }

    static void clear(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().remove(KEY_METADATA).apply();
    }

    static String domainFromUrl(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            Uri uri = Uri.parse(url);
            return normalizeDomain(uri.getHost());
        } catch (Exception ignored) {
            return "";
        }
    }

    static String normalizeDomain(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String domain = value.trim().toLowerCase(Locale.ROOT);
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    }

    private static ArrayList<Metadata> readMetadata(Context context) {
        ArrayList<Metadata> result = new ArrayList<>();
        String json = prefs(context).getString(KEY_METADATA, null);
        if (TextUtils.isEmpty(json)) {
            return result;
        }
        long now = System.currentTimeMillis();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                Metadata item = new Metadata();
                item.time = object.optLong("time");
                item.domain = normalizeDomain(object.optString("domain"));
                item.title = object.optString("title", "");
                item.siteName = object.optString("siteName", "");
                item.actionBarColor = object.optInt("actionBarColor");
                item.backgroundColor = object.optInt("backgroundColor");
                item.favicon = BrowserStorage.decodeBitmap(object.optString("favicon", null));
                if (!TextUtils.isEmpty(item.domain) && now - item.time <= EXPIRATION) {
                    result.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void writeMetadata(Context context, ArrayList<Metadata> metadata) {
        Collections.sort(metadata, new Comparator<Metadata>() {
            @Override
            public int compare(Metadata left, Metadata right) {
                return Long.compare(right.time, left.time);
            }
        });
        JSONArray array = new JSONArray();
        int count = 0;
        long now = System.currentTimeMillis();
        for (Metadata item : metadata) {
            if (item == null || TextUtils.isEmpty(item.domain) || now - item.time > EXPIRATION) {
                continue;
            }
            JSONObject object = new JSONObject();
            try {
                object.put("time", item.time);
                object.put("domain", item.domain);
                object.put("title", item.title == null ? "" : item.title);
                object.put("siteName", item.siteName == null ? "" : item.siteName);
                object.put("actionBarColor", item.actionBarColor);
                object.put("backgroundColor", item.backgroundColor);
                String favicon = BrowserStorage.encodeBitmap(item.favicon);
                if (favicon != null) {
                    object.put("favicon", favicon);
                }
            } catch (Exception ignored) {
            }
            array.put(object);
            count++;
            if (count >= MAX_COUNT) {
                break;
            }
        }
        prefs(context).edit().putString(KEY_METADATA, array.toString()).apply();
    }
}

/*
 * This file is part of NativeDo.
 *
 * Provides a compile-compatible shim for Telegram's LocaleController so that
 * ported UI code can call LocaleController.isRTL and LocaleController.getString()
 * without pulling in Telegram's entire i18n pipeline.
 *
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Minimal shim matching Telegram's {@code LocaleController} API surface.
 * Ported code calls {@code LocaleController.isRTL} and
 * {@code LocaleController.getString(key, resId)}; this class satisfies those
 * calls without the full Telegram i18n system.
 */
public final class LocaleController {

    /** NativeDo does not support RTL; always {@code false}. */
    public static boolean isRTL = false;

    private static Context applicationContext;

    private LocaleController() {
    }

    /** Stub of upstream {@code LocaleController.formatNumber(int, char)}. */
    public static String formatNumber(int count, char digitGroupingSeparator) {
        if (count < 1000) {
            return Integer.toString(count);
        }
        StringBuilder sb = new StringBuilder();
        String s = Integer.toString(count);
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(digitGroupingSeparator);
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    public static String formatShortNumber(int number, int[] rounded) {
        StringBuilder suffix = new StringBuilder();
        int lastDecimal = 0;
        while (number / 1000 > 0) {
            suffix.append("K");
            lastDecimal = (number % 1000) / 100;
            number /= 1000;
        }
        if (rounded != null) {
            double value = number + lastDecimal / 10.0;
            for (int i = 0; i < suffix.length(); i++) {
                value *= 1000;
            }
            rounded[0] = (int) value;
        }
        if (lastDecimal != 0 && suffix.length() > 0) {
            if (suffix.length() == 2) {
                return String.format(Locale.US, "%d.%dM", number, lastDecimal);
            }
            return String.format(Locale.US, "%d.%d%s", number, lastDecimal, suffix);
        }
        if (suffix.length() == 2) {
            return String.format(Locale.US, "%dM", number);
        }
        return String.format(Locale.US, "%d%s", number, suffix);
    }

    public static String formatDuration(int duration) {
        return AndroidUtilities.formatShortDuration(duration);
    }

    public static void init(Context context) {
        applicationContext = context.getApplicationContext();
    }

    /**
     * Returns the string for the given resource ID.
     * Matches upstream {@code LocaleController.getString(String key, int resId)}.
     */
    public static String getString(String key, int resId) {
        if (applicationContext == null) {
            return key;
        }
        try {
            return applicationContext.getString(resId);
        } catch (Exception e) {
            return key;
        }
    }

    public static String getString(String key) {
        if (applicationContext == null) {
            return "LOC_ERR:" + key;
        }
        try {
            int resId = applicationContext.getResources().getIdentifier(key, "string", applicationContext.getPackageName());
            return resId == 0 ? "LOC_ERR:" + key : applicationContext.getString(resId);
        } catch (Exception e) {
            return "LOC_ERR:" + key;
        }
    }

    public static String getString(int resId) {
        if (applicationContext == null) {
            return "";
        }
        try {
            return applicationContext.getString(resId);
        } catch (Exception e) {
            return "";
        }
    }

    public static String formatPluralStringComma(String key, int quantity) {
        return formatPluralString(key, quantity);
    }

    public static String formatPluralStringComma(String key, int quantity, char separator) {
        return formatPluralString(key, quantity);
    }

    public static String getPluralString(String key, int quantity) {
        return formatPluralString(key, quantity);
    }

    /**
     * Returns the formatted string for the given resource ID.
     * Matches upstream {@code LocaleController.formatString(String key, int resId, Object... args)}.
     */
    public static String formatString(String key, int resId, Object... args) {
        if (applicationContext == null) {
            return key;
        }
        try {
            return applicationContext.getString(resId, args);
        } catch (Exception e) {
            return key;
        }
    }

    public static String formatString(int resId, Object... args) {
        if (applicationContext == null) {
            return "";
        }
        try {
            return applicationContext.getString(resId, args);
        } catch (Exception e) {
            return "";
        }
    }

    public static LocaleController getInstance() {
        return new LocaleController();
    }

    public LocaleInfo getCurrentLocaleInfo() {
        return new LocaleInfo(Locale.getDefault().getLanguage());
    }

    public Locale getCurrentLocale() {
        return Locale.getDefault();
    }

    /** NativeDo locale shim: no transliteration table is loaded, so preserve the source. */
    public String getTranslitString(String src) {
        return src;
    }

    public static final class LocaleInfo {
        public final String shortName;

        public LocaleInfo(String shortName) {
            this.shortName = shortName == null ? "en" : shortName;
        }
    }

    public SimpleDateFormat getChatFullDate() {
        return new SimpleDateFormat(getDatePattern("chatFullDate", R.string.chatFullDate, "MMMM d, yyyy"), Locale.getDefault());
    }

    public SimpleDateFormat getChatDate() {
        return new SimpleDateFormat(getDatePattern("chatDate", R.string.chatDate, "MMMM d"), Locale.getDefault());
    }

    public SimpleDateFormat getFormatterDay() {
        return new SimpleDateFormat("MMM d", Locale.US);
    }

    public static String formatDateChat(long value) {
        return formatDateChat(value, false);
    }

    public static String formatDateChat(long value, boolean checkYear) {
        try {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            int currentYear = calendar.get(java.util.Calendar.YEAR);
            long date = value * 1000L;

            calendar.setTimeInMillis(date);
            if (checkYear && currentYear == calendar.get(java.util.Calendar.YEAR)
                    || !checkYear && Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return getInstance().getChatDate().format(date);
            }
            return getInstance().getChatFullDate().format(date);
        } catch (Exception ignore) {
            return "LOC_ERR: formatDateChat";
        }
    }

    private static String getDatePattern(String key, int resId, String fallback) {
        String pattern = getString(key, resId);
        if (pattern == null || pattern.isEmpty() || pattern.equals(key) || pattern.startsWith("LOC_ERR:")) {
            return fallback;
        }
        return pattern;
    }

    /**
     * Returns a quantity-correct string for the given resource ID.
     * Matches upstream {@code LocaleController.formatPluralString(String key, int quantity)}.
     */
    public static String formatPluralString(String key, int quantity) {
        if (applicationContext == null) {
            return key;
        }
        try {
            return applicationContext.getResources().getQuantityString(
                    applicationContext.getResources().getIdentifier(key, "plurals", applicationContext.getPackageName()),
                    quantity, quantity);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Returns a quantity-correct formatted string.
     * Matches upstream {@code LocaleController.formatPluralString(String key, int quantity, Object... args)}.
     */
    public static String formatPluralString(String key, int quantity, Object... args) {
        if (applicationContext == null) {
            return key;
        }
        try {
            return applicationContext.getResources().getQuantityString(
                    applicationContext.getResources().getIdentifier(key, "plurals", applicationContext.getPackageName()),
                    quantity, args);
        } catch (Exception e) {
            return key;
        }
    }
}

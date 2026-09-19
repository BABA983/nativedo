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

import org.telegram.tgnet.TLRPC;
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

    /**
     * Host version of the pin's entity-date formatter. The pin composes relative week/day/long/short
     * variants from its own formatter set; this shim returns a readable date and the rich pattern lands
     * with the formatted-date feature (our composer wires the date action out).
     */
    public static String formatEntityFormattedDate(TLRPC.TL_messageEntityFormattedDate entity) {
        if (entity == null) {
            return "";
        }
        return java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(entity.date * 1000L));
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

    public SimpleDateFormat getFormatterDayMonth() {
        return new SimpleDateFormat(getDatePattern("formatterMonth", R.string.formatterMonth, "dd MMM"), Locale.getDefault());
    }

    public SimpleDateFormat getFormatterYear() {
        return new SimpleDateFormat(getDatePattern("formatterYear", R.string.formatterYear, "dd.MM.yy"), Locale.getDefault());
    }

    public static String formatDate(long date) {
        return formatDate(date, false);
    }

    /** Pin implementation; the patterns fall back to upstream's when the string resources are absent. */
    public static String formatDate(long date, boolean withToday) {
        try {
            date *= 1000;
            java.util.Calendar rightNow = java.util.Calendar.getInstance();
            int day = rightNow.get(java.util.Calendar.DAY_OF_YEAR);
            int year = rightNow.get(java.util.Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(java.util.Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(java.util.Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                if (withToday) {
                    return getString(R.string.ShortToday);
                }
                return getInstance().getFormatterDay().format(new java.util.Date(date));
            } else if (dateDay + 1 == day && year == dateYear) {
                return getString("Yesterday", R.string.Yesterday);
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return getInstance().getFormatterDayMonth().format(new java.util.Date(date));
            } else {
                return getInstance().getFormatterYear().format(new java.util.Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR: formatDate";
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

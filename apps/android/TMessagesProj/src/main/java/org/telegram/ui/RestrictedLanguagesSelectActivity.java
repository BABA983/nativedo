/*
 * This file is part of NativeDo.
 *
 * Native shim for Telegram for Android's
 * org/telegram/ui/RestrictedLanguagesSelectActivity.java. Upstream lets the user exclude
 * languages from the auto-translate menu; NativeDo ships no translation settings screen and
 * translates through the in-app browser, so the restriction set is always empty.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui;

import java.util.HashSet;

public class RestrictedLanguagesSelectActivity {

    public static HashSet<String> getRestrictedLanguages() {
        return new HashSet<>();
    }

    public static void invalidateRestrictedLanguages() {
    }

    public static void updateRestrictedLanguages(HashSet<String> value, Boolean changed) {
    }
}

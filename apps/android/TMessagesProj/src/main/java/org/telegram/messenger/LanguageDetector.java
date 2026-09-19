/*
 * This file is part of NativeDo.
 *
 * Native shim for Telegram for Android's org/telegram/messenger/LanguageDetector.java.
 * Upstream wraps ML Kit's on-device language identification to pick the source language of
 * the translate menu; NativeDo translates through the in-app browser, so there is nothing to
 * detect and the selection helper's translate item is offered directly.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

public class LanguageDetector {

    public interface StringCallback {
        void run(String str);
    }

    public interface ExceptionCallback {
        void run(Exception e);
    }

    public static boolean hasSupport() {
        return false;
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail) {
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail, boolean initializeFirst) {
    }
}

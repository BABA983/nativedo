/*
 * Host stub for the pin's org/telegram/messenger/AppGlobalConfig.java. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

/**
 * NativeDo: the pin's class is the server-config registry (410 lines: every value is a
 * {@code Config*} holder filled from {@code help.getAppConfig}, with a SharedPreferences cache and
 * the {@code TL_json*} types this tree does not ship). This port carries the values the tree reads
 * today, as plain defaults: the rich-message limits the editor measures against and the blur
 * switches the blur3 drawables ask for. A value that arrives from the server would need the config
 * fetch path, which is not wired here.
 */
public class AppGlobalConfig {

    public static class ConfigInt {
        private final int value;
        public ConfigInt(int defaultValue) { this.value = defaultValue; }
        public int get() { return value; }
    }

    public static class ConfigBoolean {
        private final boolean value;
        public ConfigBoolean(boolean defaultValue) { this.value = defaultValue; }
        public boolean get() { return value; }
    }

    // rich-message editor limits (pin defaults, AppGlobalConfig.richMessage*)
    public final ConfigInt richMessageLengthLimit = new ConfigInt(32768);
    public final ConfigInt richMessageMaxBlocks = new ConfigInt(500);
    public final ConfigInt richMessageMaxDepth = new ConfigInt(16);
    public final ConfigInt richMessageMaxMedia = new ConfigInt(50);
    public final ConfigInt richMessageMaxTableCols = new ConfigInt(20);

    // blur switches the blur3 drawables read from MessagesController.config
    public final ConfigBoolean disableBlurInLightTheme = new ConfigBoolean(false);
    public final ConfigBoolean disableBlurInDarkTheme = new ConfigBoolean(false);
}

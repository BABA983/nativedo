// PORT-MODE: Native Feature
// UPSTREAM-SCOPE: org/telegram/messenger/MessagesController.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - None.
// ADAPTED-BACKEND:
//   - getGlobalMainSettings() returns a default SharedPreferences that always returns defaults.
// OMITTED:
//   - Telegram's full MessagesController (message processing, dialog management, etc.).
/*
 * Stub for compile compatibility.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.core;

import android.content.SharedPreferences;

/**
 * Minimal shim matching Telegram's {@code MessagesController} API surface
 * used by ported animation code.
 */
public final class MessagesController {

    private MessagesController() {
    }

    /**
     * Returns a SharedPreferences instance that returns default values.
     * Matches upstream {@code MessagesController.getGlobalMainSettings()}.
     */
    public static SharedPreferences getGlobalMainSettings() {
        return ApplicationLoader.applicationContext.getSharedPreferences("global", 0);
    }
}

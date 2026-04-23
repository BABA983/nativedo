/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/FileLog.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/FileLog.java
 *
 * NativeDo milestone slice: compile-only logging shim for Telegram-derived code;
 * omits Telegram's file logging pipeline.
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
package io.github.baba983.nativedo.core;

import android.util.Log;

public final class FileLog {

    private FileLog() {
    }

    public static void e(Throwable throwable) {
        Log.e("NativeDo", "Telegram-derived failure", throwable);
    }
}

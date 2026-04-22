/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.messenger.Utilities.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/Utilities.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 as published by
 * Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-only
 */
package io.github.baba983.nativedo.core.util

object Utilities {
    fun clamp(value: Int, max: Int, min: Int): Int {
        return value.coerceAtMost(max).coerceAtLeast(min)
    }

    fun clamp(value: Float, max: Float, min: Float): Float {
        if (value.isNaN()) {
            return min
        }
        if (value.isInfinite()) {
            return max
        }
        return value.coerceAtMost(max).coerceAtLeast(min)
    }

    fun clamp01(value: Float): Float = clamp(value = value, max = 1f, min = 0f)
}

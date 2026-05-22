// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/Components/LayoutHelper.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: layout-helper=L1
// HOST-WIRING:
//   - AndroidUtilities.dp import rewritten to NativeDo.
// ADAPTED:
//   - Keeps the LinearLayout helper overload currently needed by ItemOptions.
// OMITTED:
//   - Helper overloads for RelativeLayout, RecyclerView, ScrollView, and other ViewGroup types not used by current ports.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/LayoutHelper.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/LayoutHelper.java
 *
 * NativeDo milestone slice: keeps FrameLayout helpers used by the navigation shell and
 * the LinearLayout helper used by ItemOptions; omits helper overloads for out-of-scope
 * ViewGroup types.
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
package io.github.baba983.nativedo.ui.components;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import io.github.baba983.nativedo.telegram.messenger.AndroidUtilities;

@SuppressWarnings({"WeakerAccess"})
public final class LayoutHelper {

    public static final int MATCH_PARENT = -1;
    public static final int WRAP_CONTENT = -2;

    private LayoutHelper() {
    }

    private static int getSize(float size) {
        return (int) (size < 0 ? size : AndroidUtilities.dp(size));
    }

    public static FrameLayout.LayoutParams createFrame(int width, int height) {
        return new FrameLayout.LayoutParams(getSize(width), getSize(height));
    }

    public static FrameLayout.LayoutParams createFrame(int width, int height, int gravity) {
        return new FrameLayout.LayoutParams(getSize(width), getSize(height), gravity);
    }

    public static FrameLayout.LayoutParams createFrame(float width, float height) {
        return new FrameLayout.LayoutParams(getSize(width), getSize(height));
    }

    public static FrameLayout.LayoutParams createFrame(float width, float height, int gravity) {
        return new FrameLayout.LayoutParams(getSize(width), getSize(height), gravity);
    }

    public static FrameLayout.LayoutParams createFrame(float width, float height, int gravity, float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(getSize(width), getSize(height), gravity);
        params.leftMargin = AndroidUtilities.dp(leftMargin);
        params.topMargin = AndroidUtilities.dp(topMargin);
        params.rightMargin = AndroidUtilities.dp(rightMargin);
        params.bottomMargin = AndroidUtilities.dp(bottomMargin);
        return params;
    }

    public static LinearLayout.LayoutParams createLinear(int width, int height) {
        return new LinearLayout.LayoutParams(getSize(width), getSize(height));
    }
}

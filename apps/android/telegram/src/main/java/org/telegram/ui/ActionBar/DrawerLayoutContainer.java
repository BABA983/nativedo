/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/DrawerLayoutContainer.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/DrawerLayoutContainer.java
 *
 * NativeDo milestone slice: keeps Telegram's draw-content gate used by
 * fullscreen viewers; omits drawer gestures, previews, and menu behavior.
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
package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

public class DrawerLayoutContainer extends FrameLayout {

    public boolean allowDrawContent = true;

    public DrawerLayoutContainer(Context context) {
        super(context);
    }

    public void setAllowDrawContent(boolean value) {
        if (allowDrawContent != value) {
            allowDrawContent = value;
            invalidate();
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (!allowDrawContent) {
            return false;
        }
        return super.drawChild(canvas, child, drawingTime);
    }
}

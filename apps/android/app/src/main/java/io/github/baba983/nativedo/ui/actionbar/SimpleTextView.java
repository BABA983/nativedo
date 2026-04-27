// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/ActionBar/SimpleTextView.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: title-view=L2
// ADAPTED:
//   - Uses AppCompatTextView for the single-line ActionBar title role.
// OMITTED:
//   - Telegram custom View text layout, drawable callbacks, manual measuring, and custom drawing.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/SimpleTextView.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/SimpleTextView.java
 *
 * NativeDo milestone slice: keeps the single-line title text role used by ActionBar;
 * omits Telegram's custom View text layout and drawing features.
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
package io.github.baba983.nativedo.ui.actionbar;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;

import io.github.baba983.nativedo.core.AndroidUtilities;

public class SimpleTextView extends AppCompatTextView {

    public SimpleTextView(Context context) {
        super(context);
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        setEllipsize(TextUtils.TruncateAt.END);
        setSingleLine(true);
        setTextSize(18);
        setPadding(0, 0, AndroidUtilities.dp(8), 0);
    }
}

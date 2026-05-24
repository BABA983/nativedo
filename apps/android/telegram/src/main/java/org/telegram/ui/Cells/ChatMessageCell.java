/*
 * Stub for compile compatibility. Full port deferred to Topic Detail phase.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.FrameLayout;

public class ChatMessageCell extends FrameLayout {
    public ChatMessageCell(Context context) {
        super(context);
    }

    /** Stub matching upstream ChatMessageCell.setAnimationRunning. No-op until full port. */
    public void setAnimationRunning(boolean running, boolean start) {
    }

    /** Stub of upstream sizing helper used by ImageLoader to compute message-photo dimensions. */
    public static org.telegram.ui.Components.Point getMessageSize(int w, int h) {
        return new org.telegram.ui.Components.Point(w, h);
    }
}

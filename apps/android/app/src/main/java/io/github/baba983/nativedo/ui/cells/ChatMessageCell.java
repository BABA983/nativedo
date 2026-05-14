// PORT-MODE: Exact Component Port (stub — full port deferred to Topic Detail phase)
// UPSTREAM-SCOPE: org/telegram/ui/Cells/ChatMessageCell.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: (none yet — stub for instanceof checks only)
// HOST-WIRING:
//   - None.
// ADAPTED-BACKEND:
//   - None.
// ADAPTED-PRODUCT:
//   - None.
// OMITTED:
//   - Full ChatMessageCell implementation (deferred to Topic Detail phase).
/*
 * Stub for compile compatibility. Full port deferred to Topic Detail phase.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.cells;

import android.content.Context;
import android.widget.FrameLayout;

public class ChatMessageCell extends FrameLayout {
    public ChatMessageCell(Context context) {
        super(context);
    }

    /** Stub matching upstream ChatMessageCell.setAnimationRunning. No-op until full port. */
    public void setAnimationRunning(boolean running, boolean start) {
    }
}

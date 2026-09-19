/*
 * Host stub for the pin's org/telegram/ui/Components/ChatAttachAlert.java. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

import android.content.Context;

/**
 * NativeDo: the pin's class is the chat attach sheet (4k+ lines: the media/emoji/poll panels, the
 * drag-and-drop sheet, the permission glue). This tree has no chat, so nothing of that sheet exists;
 * what the rich editor's block list does read is the layout-type table - its "attach" entry points
 * are indices into the sheet's pages - so those constants are carried verbatim and nothing else.
 * When the editor gets its own host, that host answers `onOpenAttachRequest` with its own pages and
 * this stub goes.
 */
public class ChatAttachAlert {

    /** Kept from the earlier stub: the type exists, but no screen of ours opens a chat attach sheet. */
    public ChatAttachAlert(Context context) {}

    public void dismiss() {}

    public boolean isShowing() {
        return false;
    }

    public static final int LAYOUT_TYPE_PHOTO = 1;
    public static final int LAYOUT_TYPE_MUSIC = 3;
    public static final int LAYOUT_TYPE_DOCUMENTS = 4;
    public static final int LAYOUT_TYPE_CONTACTS = 5;
    public static final int LAYOUT_TYPE_LOCATION = 6;
    public static final int LAYOUT_TYPE_POLL = 9;
    public static final int LAYOUT_TYPE_REPLIES = 11;
    public static final int LAYOUT_TYPE_TODO = 12;
    public static final int LAYOUT_TYPE_STICKERS = 13;
    public static final int LAYOUT_TYPE_EMOJI = 14;
    public static final int LAYOUT_TYPE_LINK = 15;
}

/*
 * Host stub for the pin's org/telegram/ui/iv/ChatAttachAlertRichLayout.java. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.iv;

import android.content.Context;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * The attach-alert host of the rich editor, of which this tree needs one entry point: the LaTeX
 * source sheet, which {@code RichEditText} and {@code RichEditorListView} open from their math
 * spans. The pin's sheet is ~150 lines of {@code BottomSheet} + {@code EditTextCell} +
 * {@code ButtonWithCounterView} preview, i.e. the editor's own UI, so it lands with the rest of that
 * host rather than as a copy of one method ({@code docs/render-model-alignment.md}, "The ui/iv
 * project"). Until then the entry point exists and does nothing: no sheet opens and the math span
 * keeps the source it had, which is the same visible behaviour as a sheet that never gets a new
 * source (the sheet only reports a change when the text differs).
 */
public final class ChatAttachAlertRichLayout {

    private ChatAttachAlertRichLayout() {}

    public static void showEditLatexSheet(Context context, String initialSource, Utilities.Callback<String> whenDone, Theme.ResourcesProvider resourcesProvider) {
        // NativeDo: wire-out - the sheet's UI (BottomSheet, EditTextCell, ButtonWithCounterView) comes
        // with the attach-alert editor host.
    }
}

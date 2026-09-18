/*
 * Host stub for the pin's org/telegram/ui/iv/RichHtml.java. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.iv;

import android.text.Spannable;

import org.telegram.tgnet.tl.TL_iv;

/**
 * The pin's rich-model-to-HTML helpers, as the three entry points the rich-message renderer calls
 * when it exports a selection (its copy/quote path). The real conversion - and the rest of the
 * 1,427-line file, which needs the editor half of {@code ui/iv} - lands with the editor slice
 * ({@code docs/render-model-alignment.md}, "The ui/iv project"), so these answer with an empty
 * document and the renderer's own "nothing to export" handling takes over.
 */
public final class RichHtml {

    private RichHtml() {}

    public static String tableToHtml(TL_iv.pageBlockTable table) {
        return "";
    }

    public static String inlineToHtml(Spannable text) {
        return "";
    }

    public static String preToHtml(Spannable text, String language) {
        return "";
    }
}

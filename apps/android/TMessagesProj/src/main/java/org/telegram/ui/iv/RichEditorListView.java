/*
 * Host stub for the pin's org/telegram/ui/iv/RichEditorListView.java. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.iv;

import org.telegram.tgnet.tl.TL_iv;

import java.util.ArrayList;

/**
 * NativeDo: the pin's file is the editor's 7,764-line RecyclerView (the block list, the drag
 * reordering, the toolbar glue, the media inserters). This tree has no host for it yet - the editor
 * lands with chat, see {@code docs/render-model-alignment.md}, "The ui/iv project" - but the cells
 * that are already the pin's files call a few of its static predicates, so this stub carries those
 * exactly as the pin writes them. When the real file lands, it replaces this one and the overlay's
 * {@code native} entry goes.
 */
public final class RichEditorListView {

    private RichEditorListView() {}

    static boolean isHeading(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockHeading1
            || b instanceof TL_iv.pageBlockHeading2
            || b instanceof TL_iv.pageBlockHeading3
            || b instanceof TL_iv.pageBlockHeading4
            || b instanceof TL_iv.pageBlockHeading5
            || b instanceof TL_iv.pageBlockHeading6;
    }

    static boolean isDetailsHeader(BlockRow row) {
        return row != null && row.block instanceof TL_iv.pageBlockDetails;
    }

    static boolean isGallery(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockCollage || b instanceof TL_iv.pageBlockSlideshow;
    }

    static ArrayList<TL_iv.PageBlock> galleryItems(TL_iv.PageBlock b) {
        if (b instanceof TL_iv.pageBlockCollage) return ((TL_iv.pageBlockCollage) b).items;
        if (b instanceof TL_iv.pageBlockSlideshow) return ((TL_iv.pageBlockSlideshow) b).items;
        return null;
    }
}

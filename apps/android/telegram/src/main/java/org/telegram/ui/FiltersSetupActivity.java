package org.telegram.ui;

import android.content.Context;
import android.widget.FrameLayout;

/**
 * Minimal host stub: only {@link HintInnerCell} is needed by
 * {@link org.telegram.ui.Components.RecyclerListView} section predicates.
 * Full FiltersSetupActivity (chat filters UI) is not ported.
 */
public final class FiltersSetupActivity {
    private FiltersSetupActivity() {}

    /** Marker view type excluded from section backgrounds (matches upstream). */
    public static class HintInnerCell extends FrameLayout {
        public HintInnerCell(Context context) {
            super(context);
        }
    }
}

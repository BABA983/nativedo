package org.telegram.ui.recyclerview;

import android.animation.TimeInterpolator;
import android.view.animation.Interpolator;

import androidx.recyclerview.widget.DefaultItemAnimator;

import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Minimal ChatListItemAnimator surface for pin AdjustPanLayoutHelper.
 * Full chat list item animator is not ported.
 */
public class ChatListItemAnimator extends DefaultItemAnimator {
    public static final Interpolator DEFAULT_INTERPOLATOR = new CubicBezierInterpolator(
            0.19919472913616398,
            0.010644531250000006,
            0.27920937042459737,
            0.91025390625
    );
}

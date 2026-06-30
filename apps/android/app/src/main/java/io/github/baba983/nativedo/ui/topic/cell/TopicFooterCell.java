/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Cells/TextInfoPrivacyCell.java footer row pattern.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.cell;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

public final class TopicFooterCell extends TextInfoPrivacyCell implements Theme.Colorable {

    public TopicFooterCell(Context context) {
        super(context, 18);
        setTextGravity(Gravity.CENTER);
        setTextColor(Theme.getColor(Theme.key_chat_inTimeText));
    }

    public void bind(String text, Runnable onClick) {
        setText(text);
        setOnClickListener(onClick == null ? null : (View view) -> onClick.run());
        setClickable(onClick != null);
    }

    @Override
    public void updateColors() {
        setTextColor(Theme.getColor(Theme.key_chat_inTimeText));
    }
}

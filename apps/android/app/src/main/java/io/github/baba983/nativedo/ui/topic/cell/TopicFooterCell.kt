/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Cells/TextInfoPrivacyCell.java footer row pattern.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.cell

import android.content.Context
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

internal class TopicFooterCell(context: Context) : AppCompatTextView(context), Theme.Colorable {
    init {
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), AndroidUtilities.dp(18f))
    }

    fun bind(text: String, onClick: (() -> Unit)?) {
        this.text = text
        setOnClickListener(if (onClick == null) null else View.OnClickListener { onClick() })
        isClickable = onClick != null
    }

    override fun updateColors() {
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
    }
}

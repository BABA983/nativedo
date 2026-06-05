/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ArticleViewer.java block header cells.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.cell

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper

internal class TopicHeaderCell(context: Context) : FrameLayout(context), Theme.Colorable {
    private val titleView = TextView(context).apply {
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        textSize = 22f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val metaView = TextView(context).apply {
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        textSize = 13f
    }

    init {
        setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(14f), AndroidUtilities.dp(18f), AndroidUtilities.dp(12f))
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        addView(metaView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP, 0f, 36f, 0f, 0f))
    }

    fun bind(title: String, totalPosts: Int, viewCount: Int, tags: List<String>) {
        titleView.text = title
        metaView.text = buildString {
            append(totalPosts)
            append(" posts")
            if (viewCount > 0) append(" · ").append(viewCount).append(" views")
            if (tags.isNotEmpty()) append(" · ").append(tags.joinToString(" / "))
        }
    }

    override fun updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        metaView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
    }
}

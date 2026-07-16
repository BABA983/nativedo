/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java.
 * It keeps the floating-date visibility, delay, animation, and separator push-off subsystem.
 */
package io.github.baba983.nativedo.ui.topic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.feature.topic.ui.TopicDateHeader
import io.github.baba983.nativedo.ui.topic.cell.TopicDateCell
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.RecyclerListView

internal enum class TopicFloatingDateRowKind {
    POST,
    DATE,
    OTHER,
}

internal data class TopicFloatingDateRow(
    val kind: TopicFloatingDateRowKind,
    val date: TopicDateHeader?,
)

internal class TopicFloatingDateController(
    private val dateView: TopicDateCell,
    private val listView: RecyclerListView,
    private val topInsetView: View,
    private val rowAt: (Int) -> TopicFloatingDateRow?,
    private val interactionsBlocked: () -> Boolean,
) {
    private var animation: AnimatorSet? = null
    private var viewOffset = 0
    private var currentDateOnScreen = false
    private var currentTopIsNotMessage = false
    private var scrolling = false
    private var hideDelay = HIDE_DELAY_MS
    private val parentLocation = IntArray(2)
    private val insetLocation = IntArray(2)

    fun onScrollStateChanged(state: Int) {
        if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
            scrolling = true
            update()
            if (!currentTopIsNotMessage) {
                show(scroll = true)
            }
        } else if (state == RecyclerView.SCROLL_STATE_IDLE) {
            scrolling = false
            update()
            hide(animated = true)
        }
    }

    fun onScrolled(dy: Int) {
        update()
        if (dy != 0 && scrolling && !currentTopIsNotMessage) {
            show(scroll = true)
        }
    }

    fun showForProgrammaticUpdate() {
        update()
        show(scroll = false)
        hide(animated = true)
    }

    fun update() {
        if (listView.childCount == 0) {
            viewOffset = 0
            currentDateOnScreen = false
            hide(animated = false, force = true)
            return
        }

        val listLocation = IntArray(2)
        val insetLocation = IntArray(2)
        listView.getLocationInWindow(listLocation)
        topInsetView.getLocationInWindow(insetLocation)
        val clipTop = (insetLocation[1] + topInsetView.measuredHeight - listLocation[1]).coerceAtLeast(0)

        var topRow: TopicFloatingDateRow? = null
        var topRowBottom = Int.MAX_VALUE
        var topDate: TopicDateHeader? = null
        var topDateBottom = Int.MAX_VALUE
        var approachingDateCell: TopicDateCell? = null
        var approachingDateTop = Int.MAX_VALUE

        for (index in 0 until listView.childCount) {
            val child = listView.getChildAt(index)
            val position = listView.getChildAdapterPosition(child)
            val row = rowAt(position) ?: continue
            if (row.kind == TopicFloatingDateRowKind.DATE) {
                child.alpha = 1f
                if (child.top < approachingDateTop && child.bottom > clipTop) {
                    approachingDateTop = child.top
                    approachingDateCell = child as? TopicDateCell
                }
            }
            if (child.bottom <= clipTop) continue
            if (child.bottom < topRowBottom) {
                topRowBottom = child.bottom
                topRow = row
            }
            if (row.date != null && child.bottom < topDateBottom) {
                topDateBottom = child.bottom
                topDate = row.date
            }
        }

        val date = topDate
        if (date == null) {
            viewOffset = 0
            currentDateOnScreen = false
            currentTopIsNotMessage = true
            updatePosition()
            hide(animated = true)
            return
        }
        dateView.setCustomDate(date.epochSeconds)
        currentTopIsNotMessage = topRow?.kind == TopicFloatingDateRowKind.OTHER

        val separator = approachingDateCell
        val separatorTop = separator?.top ?: Int.MAX_VALUE
        val height = dateView.measuredHeight.takeIf { it > 0 } ?: AndroidUtilities.dp(28f)
        currentDateOnScreen = separator != null && separatorTop <= clipTop + height
        viewOffset = if (separator != null) {
            topicFloatingDatePushOffset(separator.bottom, clipTop, height)
        } else {
            0
        }
        if (separator != null && separator.top <= clipTop) {
            separator.alpha = 0f
        }
        updatePosition()
        if (currentDateOnScreen) {
            forceVisible()
        }
    }

    fun hide(animated: Boolean, force: Boolean = false) {
        if (dateView.tag == null || (!force && (currentDateOnScreen || (scrolling && !currentTopIsNotMessage)))) {
            return
        }
        dateView.tag = null
        animation?.cancel()
        animation = if (animated) {
            AnimatorSet().apply {
                playTogether(ObjectAnimator.ofFloat(dateView, View.ALPHA, 0f))
                duration = ANIMATION_DURATION_MS
                startDelay = hideDelay
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) {
                        if (dateView.tag == null) dateView.visibility = View.INVISIBLE
                        if (animation === this@apply) animation = null
                    }
                })
                start()
            }
        } else {
            dateView.alpha = 0f
            dateView.visibility = View.INVISIBLE
            null
        }
        hideDelay = HIDE_DELAY_MS
    }

    fun release() {
        animation?.cancel()
        animation = null
        scrolling = false
        currentDateOnScreen = false
        dateView.tag = null
        dateView.alpha = 0f
        dateView.visibility = View.INVISIBLE
    }

    private fun show(scroll: Boolean) {
        if (dateView.getCustomDate() == 0L || interactionsBlocked() || dateView.tag != null) return
        animation?.cancel()
        dateView.tag = 1
        dateView.visibility = View.VISIBLE
        animation = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(dateView, View.ALPHA, 1f))
            duration = ANIMATION_DURATION_MS
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    if (animation === this@apply) animation = null
                }
            })
            start()
        }
        if (!scroll) hideDelay = LONG_HIDE_DELAY_MS
    }

    private fun forceVisible() {
        if (dateView.getCustomDate() == 0L || interactionsBlocked()) return
        animation?.cancel()
        animation = null
        dateView.tag = 1
        dateView.visibility = View.VISIBLE
        dateView.alpha = 1f
    }

    private fun updatePosition() {
        val parentView = dateView.parent as? View ?: return
        parentView.getLocationInWindow(parentLocation)
        topInsetView.getLocationInWindow(insetLocation)
        dateView.translationY = topicFloatingDateTranslationY(
            viewTop = dateView.top,
            parentWindowTop = parentLocation[1],
            insetWindowTop = insetLocation[1],
            insetHeight = topInsetView.measuredHeight,
            viewOffset = viewOffset,
        )
    }

    private companion object {
        private const val ANIMATION_DURATION_MS = 150L
        private const val HIDE_DELAY_MS = 500L
        private const val LONG_HIDE_DELAY_MS = 1_000L
    }
}

internal fun topicFloatingDatePushOffset(separatorBottom: Int, clipTop: Int, floatingHeight: Int): Int {
    val offset = separatorBottom - clipTop
    return if (offset > floatingHeight && offset < 2 * floatingHeight) {
        -2 * floatingHeight + offset
    } else {
        0
    }
}

internal fun topicFloatingDateTranslationY(
    viewTop: Int,
    parentWindowTop: Int,
    insetWindowTop: Int,
    insetHeight: Int,
    viewOffset: Int,
): Float {
    val desiredTopInParent = insetWindowTop - parentWindowTop + insetHeight + viewOffset - AndroidUtilities.dp(4f)
    return desiredTopInParent - viewTop.toFloat()
}

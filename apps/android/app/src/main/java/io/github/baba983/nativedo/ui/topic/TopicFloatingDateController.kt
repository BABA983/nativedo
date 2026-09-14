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
    val isTerminalDateSeparator: Boolean = false,
)

internal class TopicFloatingDateController(
    private val dateView: TopicDateCell,
    private val listView: RecyclerListView,
    private val rowAt: (Int) -> TopicFloatingDateRow?,
    private val interactionsBlocked: () -> Boolean,
) {
    private var animation: AnimatorSet? = null
    private var viewOffset = 0
    private var currentDateOnScreen = false
    private var currentTopIsNotMessage = false
    private var scrolling = false
    private var hideDelay = HIDE_DELAY_MS

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

        val clipTop = listView.paddingTop.toFloat()

        var topRow: TopicFloatingDateRow? = null
        var topRowBottom = Float.MAX_VALUE
        var topDate: TopicDateHeader? = null
        var topDateBottom = Float.MAX_VALUE
        var approachingDateCell: TopicDateCell? = null
        var approachingDateRow: TopicFloatingDateRow? = null
        var approachingDateBottom = Float.MAX_VALUE

        for (index in 0 until listView.childCount) {
            val child = listView.getChildAt(index)
            val position = listView.getChildAdapterPosition(child)
            val row = rowAt(position) ?: continue
            val childTop = child.y
            val childBottom = childTop + child.measuredHeight
            if (row.kind == TopicFloatingDateRowKind.DATE) {
                if (childBottom <= clipTop) {
                    child.alpha = 0f
                } else {
                    child.alpha = 1f
                    if (childBottom < approachingDateBottom) {
                        approachingDateBottom = childBottom
                        approachingDateCell = child as? TopicDateCell
                        approachingDateRow = row
                    }
                }
            }
            if (childBottom <= clipTop) continue
            if (childBottom < topRowBottom) {
                topRowBottom = childBottom
                topRow = row
            }
            if (row.date != null && childBottom < topDateBottom) {
                topDateBottom = childBottom
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
        currentDateOnScreen = false
        viewOffset = 0

        val separator = approachingDateCell
        var showFloatingView = false
        if (separator != null) {
            val separatorTop = separator.y
            val terminalSeparator = approachingDateRow?.isTerminalDateSeparator == true
            if (separatorTop > clipTop || currentTopIsNotMessage) {
                separator.alpha = 1f
                if (terminalSeparator) {
                    hide(animated = false, force = true)
                } else {
                    hide(animated = !currentTopIsNotMessage)
                }
            } else {
                separator.alpha = 0f
                showFloatingView = true
            }

            val floatingHeight = dateView.measuredHeight.takeIf { it > 0 } ?: AndroidUtilities.dp(28f)
            val separatorBottom = separatorTop + separator.measuredHeight
            val offset = separatorBottom - clipTop
            if (offset > floatingHeight && offset < floatingHeight * 2) {
                if (terminalSeparator) {
                    showFloatingView = false
                    separator.alpha = 1f
                    hide(animated = false, force = true)
                } else {
                    viewOffset = -floatingHeight * 2 + offset.toInt()
                }
            }
        }
        updatePosition()
        if (showFloatingView) {
            currentDateOnScreen = true
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
        dateView.translationY = topicFloatingDateTranslationY(
            listTop = listView.y,
            listTranslationY = listView.translationY,
            listPaddingTop = listView.paddingTop,
            viewOffset = viewOffset,
        )
        dateView.setFloatingTopClip(
            topicFloatingDateTopClip(
                listTop = listView.y,
                listTranslationY = listView.translationY,
                listPaddingTop = listView.paddingTop,
                floatingViewY = dateView.y,
            ),
        )
    }

    private companion object {
        private const val ANIMATION_DURATION_MS = 150L
        private const val HIDE_DELAY_MS = 500L
        private const val LONG_HIDE_DELAY_MS = 1_000L
    }
}

internal fun topicFloatingDatePushOffset(
    separatorBottom: Float,
    clipTop: Float,
    floatingHeight: Int,
    isTerminalSeparator: Boolean = false,
): Int {
    if (isTerminalSeparator) return 0
    val offset = separatorBottom - clipTop
    return if (offset > floatingHeight && offset < 2 * floatingHeight) {
        -2 * floatingHeight + offset.toInt()
    } else {
        0
    }
}

internal fun topicFloatingDateTranslationY(
    listTop: Float,
    listTranslationY: Float,
    listPaddingTop: Int,
    viewOffset: Int,
): Float {
    return listTop + listTranslationY + listPaddingTop + viewOffset - AndroidUtilities.dp(4f)
}

internal fun topicFloatingDateTopClip(
    listTop: Float,
    listTranslationY: Float,
    listPaddingTop: Int,
    floatingViewY: Float,
): Float = (
    listTop + listTranslationY + listPaddingTop - floatingViewY - AndroidUtilities.dp(4f)
).coerceAtLeast(0f)

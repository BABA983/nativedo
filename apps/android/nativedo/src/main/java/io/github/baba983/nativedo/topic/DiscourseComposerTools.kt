// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.topic

import android.view.MotionEvent
import android.widget.FrameLayout
import io.github.baba983.nativedo.app.GlassPanelHost
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.MentionCandidate
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.feature.topic.data.TopicMentionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
import org.telegram.ui.Components.chat.ChatInputViewsContainer
import org.telegram.ui.Components.inset.WindowInsetsStateHolder

/** Site input tools shared by reply and new-topic screens; their owner forwards lifecycle/insets. */
class DiscourseComposerTools(
    fragment: BaseFragment,
    container: AppContainer,
    private val composer: ChatActivityEnterView,
    private val inputViews: ChatInputViewsContainer,
    inputInsets: WindowInsetsStateHolder,
    glassHost: GlassPanelHost,
    topicId: Long?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val picker = DiscourseEmojiPicker(composer.context, container, fragment.resourceProvider)
    internal val mentions = DiscourseMentions.install(
        composer, inputViews, fragment, fragment.resourceProvider, container.baseUrl, topicId,
        TopicMentionRepository(container.discourseApiClient), scope, container.imageOkHttpClient,
    )!!

    init {
        mentions.panel().setBackgroundDrawable(glassHost.createPanel(
            mentions.panel(), BlurredBackgroundProviderImpl.bottomPanelChatActivity(fragment.resourceProvider),
        ))
        picker.setInsertionTarget(composer.editField)
        picker.setInsetsController(inputInsets)
        picker.setOnDismissListener { updateEmojiIcon() }
        inputViews.inAppKeyboardBubbleContainer.addView(picker, LayoutHelper.createFrameMatchParent())
        composer.emojiButton.contentDescription = "站点表情"
        composer.emojiButton.setOnClickListener {
            if (picker.isOpen()) {
                picker.dismiss()
                composer.setFieldFocused()
                AndroidUtilities.showKeyboard(composer.editField)
            } else {
                mentions.dismiss()
                composer.hidePopup(false)
                picker.show()
                updateEmojiIcon()
            }
        }
        // The field has no upstream OnTouchListener. Preserve its own touch handling and IME open.
        composer.editField!!.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && picker.isOpen()) picker.dismiss()
            false
        }
    }

    fun setLocalCandidates(participants: List<MentionCandidate>, posts: List<Post>) {
        mentions.setLocalCandidates(participants, posts)
    }

    /**
     * The panel follows the actual island geometry, including animated height and bottom inset.
     *
     * <p>A long candidate list must not fill the screen: upstream hosts this panel at the whole
     * content height, and its own band math then paints the glass over that whole host whenever the
     * list's padding row leaves the window (the reader scrolling the candidates), which covers the
     * posts. So the host is capped to a compact band - tall enough for the first candidate to sit
     * at the band's top (the padding row's own height), never tall enough to cover the screen.</p>
     */
    fun updatePosition(extraHeight: Int) {
        val panel = mentions.panel()
        val params = panel.layoutParams as FrameLayout.LayoutParams
        val bottom = Math.round(inputViews.height - inputViews.inputBubbleTop) + extraHeight + AndroidUtilities.dp(4f)
        // Upstream lets the panel reach the container's top and trusts the list's padding row to
        // place the round-rect band at the first candidate (ChatActivity.onMeasure forces the host
        // to heightSize). Measured against this screen's much shorter composer, the candidate band
        // is a few rows; capping the host keeps the reader the posts above it while still being tall
        // enough that the padding row's own height lands on the first candidate's top.
        val full = Math.max(inputViews.height - bottom, 0)
        val height = Math.min(full, AndroidUtilities.dp(DiscourseMentions.MENTION_BAND_HEIGHT_DP.toFloat()))
        if (params.bottomMargin != bottom || params.height != height) {
            params.bottomMargin = bottom
            params.height = height
            panel.layoutParams = params
        }
        updateEmojiIcon()
    }

    /** Returns true when this input owns Back; predictive probes never mutate UI. */
    fun onBackPressed(invoked: Boolean): Boolean {
        if (mentions.panel().isShowing) {
            if (invoked) mentions.dismiss()
            return true
        }
        if (picker.isOpen()) {
            if (invoked) picker.dismiss()
            return true
        }
        return false
    }

    fun onResume() = mentions.onResume()

    fun onPause() {
        mentions.onPause()
        if (picker.isOpen()) picker.dismiss()
    }

    fun onDestroy() {
        mentions.onDestroy()
        picker.destroy()
        scope.cancel()
    }

    private fun updateEmojiIcon() {
        (composer.emojiButton as ChatActivityEnterViewAnimatedIconView).setState(
            if (picker.isOpen()) ChatActivityEnterViewAnimatedIconView.State.KEYBOARD
            else ChatActivityEnterViewAnimatedIconView.State.SMILE, true,
        )
    }
}

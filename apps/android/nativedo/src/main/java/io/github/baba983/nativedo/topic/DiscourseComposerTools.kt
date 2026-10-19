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

/**
 * Site input tools shared by reply and new-topic screens; their owner forwards lifecycle/insets.
 *
 * <p>The same tools own this client's hold-to-record microphone when the screen hands in the
 * composer's own delegate (see [ComposerVoiceController]): a forum post records and uploads a voice
 * attachment the way it attaches a file, and the screen only makes its empty microphone visible.
 * A screen that hands in no delegate - the chat binds its own host directly - gets no voice here.</p>
 */
class DiscourseComposerTools @JvmOverloads constructor(
    fragment: BaseFragment,
    container: AppContainer,
    private val composer: ChatActivityEnterView,
    private val inputViews: ChatInputViewsContainer,
    inputInsets: WindowInsetsStateHolder,
    glassHost: GlassPanelHost,
    topicId: Long?,
    recordDelegate: DiscourseComposerDelegate? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The composer's own voice band, or null when the screen offered no recording delegate. Its
     * microphone is the composer's empty mic; a screen reads [isVoiceReady] to decide whether that
     * microphone is offered at all.
     */
    val voice: ComposerVoiceController? = recordDelegate?.let { delegate ->
        fragment.parentActivity?.let { activity ->
            ComposerVoiceController(
                activity, container, composer, delegate, inputViews, fragment.resourceProvider,
            )
        }
    }
    private val picker = DiscourseEmojiPicker(composer.context, container, fragment.resourceProvider)
    private val gifPicker = DiscourseGifPicker(composer.context, container, fragment.resourceProvider)
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
        picker.setOnGifRequested {
            picker.dismiss()
            mentions.dismiss()
            composer.hidePopup(false)
            gifPicker.show()
            updateEmojiIcon()
        }
        gifPicker.setInsertionTarget(composer.editField)
        gifPicker.setInsetsController(inputInsets)
        gifPicker.setOnDismissListener { updateEmojiIcon() }
        gifPicker.setOnBackToEmoji {
            gifPicker.dismiss()
            picker.show()
            updateEmojiIcon()
        }
        gifPicker.setOnGifSelected { _ ->
            composer.setFieldFocused()
            AndroidUtilities.showKeyboard(composer.editField)
        }
        inputViews.inAppKeyboardBubbleContainer.addView(picker, LayoutHelper.createFrameMatchParent())
        inputViews.inAppKeyboardBubbleContainer.addView(gifPicker, LayoutHelper.createFrameMatchParent())
        composer.emojiButton.contentDescription = "站点表情"
        composer.emojiButton.setOnClickListener {
            if (picker.isOpen() || gifPicker.isOpen()) {
                picker.dismiss()
                gifPicker.dismiss()
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
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                if (picker.isOpen()) picker.dismiss()
                if (gifPicker.isOpen()) gifPicker.dismiss()
            }
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

    /** Whether this screen's composer offers its own microphone (a recording host is bound). */
    fun isVoiceReady(): Boolean = voice != null

    /** The microphone permission answer; forwarded to this screen's recording host. */
    fun onPermissionResult(requestCode: Int) = voice?.onPermissionResult(requestCode)

    /** Returns true when this input owns Back; predictive probes never mutate UI. */
    fun onBackPressed(invoked: Boolean): Boolean {
        if (mentions.panel().isShowing) {
            if (invoked) mentions.dismiss()
            return true
        }
        if (gifPicker.isOpen()) {
            if (invoked) gifPicker.dismiss()
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
        voice?.onPause()
        mentions.onPause()
        if (picker.isOpen()) picker.dismiss()
        if (gifPicker.isOpen()) gifPicker.dismiss()
    }

    fun onDestroy() {
        voice?.onDestroy()
        mentions.onDestroy()
        picker.destroy()
        gifPicker.destroy()
        scope.cancel()
    }

    private fun updateEmojiIcon() {
        (composer.emojiButton as ChatActivityEnterViewAnimatedIconView).setState(
            if (picker.isOpen() || gifPicker.isOpen()) ChatActivityEnterViewAnimatedIconView.State.KEYBOARD
            else ChatActivityEnterViewAnimatedIconView.State.SMILE, true,
        )
    }
}

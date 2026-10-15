// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.topic

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.media.MediaPlayerVoiceRecordingPreview
import io.github.baba983.nativedo.core.media.PickedMedia
import io.github.baba983.nativedo.core.media.VoiceRecording
import io.github.baba983.nativedo.core.media.VoiceRecordingFeedback
import io.github.baba983.nativedo.core.media.insertReplyMarkup
import io.github.baba983.nativedo.hooks.NativeDoHooks
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.chat.ChatInputViewsContainer

/**
 * The forum screens' hold-to-record microphone, take review and upload, bound to one composer.
 *
 * <p>A reply, a new topic and a private message all write a post, so they all offer the same voice
 * affordance the chat does - a hold records a private local `.m4a`, a review band lets the reader
 * check it, and Send uploads it to the site and puts the site's own `![name|audio](...)` markdown
 * into the field. Only the field it lands in differs, which is why this lives once behind
 * {@link DiscourseComposerTools} rather than in each screen. Nothing here posts: the field's own
 * send button is the reader's.</p>
 *
 * <p>What makes the flow safe, none of which needs a device microphone:</p>
 * <ul>
 *   <li><b>The microphone is this client's.</b> The host is bound to the composer's own
 *       {@link DiscourseComposerDelegate}, so upstream's single touch call site hands the live
 *       delegate in and Telegram's recorder (and its video note) is never reached. See
 *       {@link ComposerVoiceInput}.</li>
 *   <li><b>The site's markdown, not a download link.</b> The upload runs on the shared
 *       {@code uploadReplyMedia} path, which builds the answer's own `|audio` embed; a `.m4a`
 *       recorded here is embedded and playable, never offered as a generic attachment.</li>
 *   <li><b>A failed upload keeps the take.</b> The private file survives a refusal and the band
 *       offers the retry; only an accepted upload or a cancel deletes it.</li>
 *   <li><b>A stale take is dropped.</b> The take carries the session it was recorded under; a
 *       different account or a logout-and-login drops it rather than uploading a clip for the wrong
 *       reader or inserting it into another draft.</li>
 * </ul>
 */
class ComposerVoiceController internal constructor(
    private val activity: Activity,
    private val container: AppContainer,
    private val composer: ChatActivityEnterView,
    private val delegate: DiscourseComposerDelegate,
    private val inputViews: ChatInputViewsContainer,
    provider: Theme.ResourcesProvider?,
) : VoiceRecordingFeedback {

    private val preview = MediaPlayerVoiceRecordingPreview()
    private val panel = ComposerVoicePanel(activity, provider)

    /** The finished take awaiting upload, or null. Kept across a failed upload for a retry. */
    private var pending: VoiceRecording? = null
    /** True while the take is on its way to the site, so a second capture cannot race it. */
    private var uploading = false
    private var disposed = false
    /** The session a take was recorded under; a take whose session is gone is never inserted. */
    private var takeGeneration = sessionGeneration()
    private var takeAccount = sessionUsername()

    private val input = ComposerVoiceInput.bind(
        activity = activity,
        composer = composer,
        delegate = delegate,
        feedback = this,
        onRecorded = { onRecorded(it) },
        captureAllowed = { canRecord() },
    )

    init {
        panel.visibility = View.GONE
        inputViews.addView(
            panel,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM, 0f, 0f, 0f, 0f,
            ),
        )
        panel.onPlayPause = Runnable { togglePreview() }
        panel.onSend = Runnable { send() }
        panel.onCancel = Runnable { cancel() }
        panel.onRetry = Runnable { send() }
        installRecordAccessibility()
    }

    // ---------------------------------------------------------------------------------------------
    // Screen contract
    // ---------------------------------------------------------------------------------------------

    /** The microphone permission answer; the recording host waits for it before a second request. */
    fun onPermissionResult(requestCode: Int) {
        if (requestCode == ComposerVoiceInput.RECORD_PERMISSION_REQUEST_CODE) {
            input.onPermissionResult()
        }
    }

    /** The owning screen going to the background: a live capture is discarded, a take is kept. */
    fun onPause() {
        input.onPause()
        stopPreview()
        if (panel.state == ComposerVoicePanel.State.Recording) panel.hide()
    }

    /** The owning screen going away: nothing survives, and the host releases its own binding. */
    fun onDestroy() {
        disposed = true
        input.onDestroy()
        cancel()
        (panel.parent as? android.view.ViewGroup)?.removeView(panel)
    }

    /** The composer's geometry changed (an island height, an inset): the band follows it. */
    fun onComposerGeometryChanged() {
        if (panel.visibility == View.VISIBLE) {
            positionPanel()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Recording feedback (the input drives, the band draws)
    // ---------------------------------------------------------------------------------------------

    override fun onHoldHint() {
        showPanel()
        panel.showHoldHint()
        panel.postDelayed({
            if (panel.state == ComposerVoicePanel.State.Recording && !input.isRecording) {
                panel.hide()
            }
        }, HOLD_HINT_MILLIS)
    }

    override fun onRecordingStarted() {
        takeGeneration = sessionGeneration()
        takeAccount = sessionUsername()
        showPanel()
        panel.showRecording()
    }

    override fun onRecordingTick(durationMillis: Long) {
        panel.updateRecording(durationMillis)
    }

    override fun onRecordingFinished() {
        panel.hide()
    }

    override fun onRecordingDiscarded() {
        panel.hide()
    }

    override fun onRecordingFailed(message: String?) {
        panel.showFailure(message ?: "无法开始录音")
        showPanel()
    }

    override fun onPermissionRequired() {
        // The permission prompt is the feedback; the band says nothing extra.
    }

    // ---------------------------------------------------------------------------------------------
    // Take lifecycle
    // ---------------------------------------------------------------------------------------------

    /** A finished local take: it becomes the review band, never a post. */
    private fun onRecorded(recording: VoiceRecording) {
        if (!stillOwned(takeGeneration, takeAccount)) {
            dropStale(recording)
            return
        }
        // A second take replaces the first; the replaced private file goes with it.
        pending?.file?.delete()
        pending = recording
        uploading = false
        panel.showReview(recording.durationMillis)
        showPanel()
    }

    /** Uploads the finished take and inserts the site's own audio markdown. Never posts. */
    fun send() {
        val recording = pending ?: return
        if (uploading || disposed) {
            return
        }
        if (!stillOwned(takeGeneration, takeAccount)) {
            // The account/draft this take was recorded under is gone: the take belongs to no live
            // draft, so it is dropped rather than uploaded for the wrong reader.
            cancel()
            return
        }
        uploading = true
        stopPreview()
        panel.showBusy("正在上传语音…")
        showPanel()
        val generation = takeGeneration
        val account = takeAccount
        val onUploaded: (String) -> Unit = { markup ->
            if (stillOwned(generation, account) && pending === recording) {
                uploading = false
                recording.file.delete()
                pending = null
                insertReplyMarkup(composer.editField, markup)
                panel.hide()
            } else {
                dropStale(recording)
            }
        }
        val onFailed: (Throwable) -> Unit = { _ ->
            if (stillOwned(generation, account) && pending === recording) {
                uploading = false
                panel.showError("语音上传失败")
                showPanel()
            } else {
                dropStale(recording)
            }
        }
        DiscourseLoader.uploadReplyMedia(
            container,
            PickedMedia(recording.file, recording.name, recording.mimeType),
            onUploaded,
            onFailed,
        )
    }

    /** The reader threw the take away: the private file goes with it. */
    fun cancel() {
        stopPreview()
        pending?.file?.delete()
        pending = null
        uploading = false
        panel.hide()
    }

    /** An answer that outlived its session: the take belongs to nobody now, so it is deleted. */
    private fun dropStale(recording: VoiceRecording) {
        uploading = false
        recording.file.delete()
        if (pending === recording) {
            pending = null
        }
        panel.hide()
    }

    private fun togglePreview() {
        val recording = pending ?: return
        if (preview.playing != null) {
            preview.stop()
            panel.setPlaying(false)
            return
        }
        preview.play(recording.file)
        panel.setPlaying(preview.playing != null)
    }

    private fun stopPreview() {
        preview.stop()
        panel.setPlaying(false)
    }

    // ---------------------------------------------------------------------------------------------
    // Session fence
    // ---------------------------------------------------------------------------------------------

    private fun sessionGeneration(): Int =
        container.sessionCoordinator.currentRequestContext().generation

    private fun sessionUsername(): String =
        container.sessionStore.snapshot()?.username.orEmpty()

    /**
     * Whether a write's answer still belongs to the reader who recorded it: the same session
     * generation and the same account, and this host is still alive. A screen that has left, a
     * different account, and a logout-and-login of the same name are all answered false.
     */
    private fun stillOwned(generation: Int, account: String): Boolean =
        !disposed && generation == sessionGeneration() && account == sessionUsername()

    private fun canRecord(): Boolean =
        !uploading && !disposed && sessionUsername().isNotEmpty()

    // ---------------------------------------------------------------------------------------------
    // The band
    // ---------------------------------------------------------------------------------------------

    private fun showPanel() {
        panel.visibility = View.VISIBLE
        positionPanel()
    }

    /**
     * The band floats just above the composer island, over the message the reader is answering,
     * and never covers the composer it answers to. The island's own top is what says where that is,
     * so the band follows an animated height and a bottom inset without a fixed strip.
     */
    private fun positionPanel() {
        val params = panel.layoutParams as? FrameLayout.LayoutParams ?: return
        if (inputViews.height <= 0) {
            return
        }
        val bottom = Math.round(inputViews.height - inputViews.inputBubbleTop)
        if (params.bottomMargin != bottom) {
            params.bottomMargin = bottom
            panel.layoutParams = params
        }
    }

    /**
     * The microphone is a touch-only affordance; a screen reader needs an alternative. The mic's
     * own click action toggles a capture (one activation starts, the next finishes and delivers),
     * the same flow without the hold.
     */
    private fun installRecordAccessibility() {
        val mic = composer.audioVideoButtonContainer ?: return
        mic.contentDescription = "按住录音"
        mic.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    input.toggleFromAccessibility()
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Test hooks
    // ---------------------------------------------------------------------------------------------

    internal fun attachPendingRecordingForTest(recording: VoiceRecording) = onRecorded(recording)

    internal fun sendForTest() = send()

    internal fun cancelForTest() = cancel()

    internal fun pendingForTest(): VoiceRecording? = pending

    internal fun isUploadingForTest(): Boolean = uploading

    internal fun panelForTest(): ComposerVoicePanel = panel

    internal fun boundRecordHostForTest(): NativeDoHooks.ComposerRecordHost? = delegate.recordHost

    private companion object {
        /** How long a short-tap hold hint stays up before the band puts itself away. */
        const val HOLD_HINT_MILLIS = 1_200L
    }
}

// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.baba983.nativedo.topic

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.media.AndroidVoiceRecorder
import io.github.baba983.nativedo.core.media.PickedMedia
import io.github.baba983.nativedo.core.media.VoiceRecorder
import io.github.baba983.nativedo.core.media.VoiceRecording
import io.github.baba983.nativedo.core.media.VoiceRecordingEditor
import io.github.baba983.nativedo.core.media.VoiceRecordingFeedback
import io.github.baba983.nativedo.core.media.insertReplyMarkup
import io.github.baba983.nativedo.hooks.NativeDoHooks
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ChatActivityEnterView
import org.telegram.ui.Components.RecordedAudioPlayerView
import org.telegram.ui.Components.chat.ChatInputViewsContainer

/** Telegram's voice editor backed by local AAC segments and Discourse uploads, never a post. */
class ComposerVoiceController internal constructor(
    activity: Activity,
    private val container: AppContainer,
    private val composer: ChatActivityEnterView,
    delegate: DiscourseComposerDelegate,
    private val inputViews: ChatInputViewsContainer,
    provider: Theme.ResourcesProvider?,
) : VoiceRecordingFeedback {
    private val bulletins = BulletinFactory.of(inputViews, provider)
    private val files = VoiceRecordingEditor()
    private var editor: RecordedAudioPlayerView? = null
    private var clearEditor: Runnable? = null
    private var pending: VoiceRecording? = null
    private var extraSegment: VoiceRecording? = null
    private var uploading = false
    private var editing = false
    private var operation = 0
    private var disposed = false
    private var takeGeneration = sessionGeneration()
    private var takeAccount = sessionUsername()

    internal var recorderFactoryForTest: ((Context) -> VoiceRecorder)? = null

    private val input = ComposerVoiceInput(
        activity, composer, delegate, this,
        { recorderFactoryForTest?.invoke(it) ?: AndroidVoiceRecorder(it) },
        { onRecorded(it) }, { !uploading && !editing && !disposed && sessionUsername().isNotEmpty() },
        onCaptureOpened = {
            takeGeneration = sessionGeneration()
            takeAccount = sessionUsername()
        },
        onEditorCreated = { view, clear -> editor = view; clearEditor = clear },
        onSendRequested = { send() },
        onResumeRequested = { resume() },
        onTrimRequested = { _, _, done -> trimSelection { done.run() } },
    )

    init {
        Bulletin.addDelegate(inputViews, object : Bulletin.Delegate {
            override fun getBottomOffset(tag: Int): Int {
                val controls = composer.controlsView
                val top = if (controls != null && controls.visibility == View.VISIBLE) controls.top
                    else inputViews.inputBubbleTop.toInt()
                return inputViews.height - top + AndroidUtilities.dp(7f)
            }
        })
        val mic = composer.audioVideoButtonContainer
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

    fun onPermissionResult(requestCode: Int) {
        if (requestCode == ComposerVoiceInput.RECORD_PERMISSION_REQUEST_CODE) input.onPermissionResult()
    }

    fun onPause() {
        input.onPause()
        editor?.setPlaying(false)
    }

    fun onDestroy() {
        disposed = true
        cancel()
        input.onDestroy()
        Bulletin.removeDelegate(inputViews)
    }

    override fun onHoldHint() = Unit
    override fun onRecordingStarted() = Unit
    override fun onRecordingTick(durationMillis: Long) = Unit
    override fun onRecordingFinished() = Unit
    override fun onPermissionRequired() = Unit
    override fun onRecordingFailed(message: String?) {
        bulletins.createErrorBulletin(message ?: "无法开始录音").show()
        pending?.let { showReview(it) }
    }
    override fun onRecordingDiscarded() { discardFiles() }

    private fun onRecorded(recording: VoiceRecording) {
        if (!stillOwned()) {
            recording.file.delete()
            cancel()
            return
        }
        if (pending == null) {
            pending = recording
            showReview(recording)
        } else {
            extraSegment = recording
            mergeSegments { pending?.let { showReview(it) } }
        }
    }

    private fun showReview(recording: VoiceRecording) {
        val fromDraft = !composer.isRecordingAudioVideo
        val document = TLRPC.TL_document().apply {
            mime_type = recording.mimeType
            size = recording.file.length()
            file_reference = byteArrayOf()
            attributes.add(TLRPC.TL_documentAttributeAudio().apply {
                voice = true
                duration = recording.durationMillis / 1000.0
                // An empty native waveform would invoke Telegram's Opus decoder on an AAC file.
                waveform = recording.waveform.takeIf { it.isNotEmpty() } ?: byteArrayOf(0)
                flags = flags or 4
            })
        }
        composer.didReceivedNotification(NotificationCenter.recordPaused, 0)
        composer.didReceivedNotification(NotificationCenter.audioDidSent, 0,
            composer.recordingGuid, document, recording.file.absolutePath, fromDraft)
    }

    private fun resume() {
        if (uploading || editing || disposed) return
        if (!stillOwned()) { cancel(); return }
        mergeSegments {
            trimSelection {
                editor?.setPlaying(false)
                input.resumeCapture()
            }
        }
    }

    private fun mergeSegments(done: () -> Unit) {
        val source = pending ?: return
        val next = extraSegment ?: return done()
        edit(source, { files.join(source, next) }) {
            next.file.delete()
            extraSegment = null
            done()
        }
    }

    private fun trimSelection(done: () -> Unit) {
        val source = pending ?: return
        val view = editor
        if (view == null || !view.needsCut()) return done()
        view.setPlaying(false)
        // Fractions are upstream's public selection. Use our file's duration even before the
        // asynchronous player has discovered its duration (TIME_UNSET must never become a crop).
        val start = (view.audioLeft * source.durationMillis).toLong()
        val end = (view.audioRight * source.durationMillis).toLong()
        edit(source, { files.trim(source, start, end) }) {
            pending?.let { showReview(it) }
            done()
        }
    }

    /** File work never blocks a frame. Cancels/account switches invalidate and clean late outputs. */
    private fun edit(source: VoiceRecording, work: () -> VoiceRecording, done: () -> Unit) {
        if (editing || uploading || !stillOwned()) return
        editing = true
        val ticket = ++operation
        Utilities.globalQueue.postRunnable {
            val result = runCatching(work)
            AndroidUtilities.runOnUIThread {
                if (ticket != operation || pending !== source || !stillOwned()) {
                    result.getOrNull()?.file?.delete()
                    if (ticket == operation) cancel()
                } else {
                    editing = false
                    result.fold({ edited ->
                        source.file.delete()
                        pending = edited
                        done()
                    }, {
                        // Keep both segments after a join refusal; retry must never upload only
                        // the first half while silently losing the continuation.
                        if (composer.isRecordingAudioVideo) showReview(source)
                        bulletins.createSimpleBulletin(R.raw.error, "语音编辑失败，录音已保留", "重试", Runnable { edit(source, work, done) }).show()
                    })
                }
            }
        }
    }

    fun send() {
        if (pending == null || uploading || editing || disposed) return
        if (!stillOwned()) { cancel(); return }
        mergeSegments { trimSelection { upload() } }
    }

    private fun upload() {
        val recording = pending ?: return
        if (!stillOwned()) { cancel(); return }
        uploading = true
        editor?.setPlaying(false)
        val ticket = ++operation
        DiscourseLoader.uploadReplyMedia(container,
            PickedMedia(recording.file, recording.name, recording.mimeType),
            { markup ->
                if (ticket == operation && pending === recording && stillOwned()) {
                    discardFiles()
                    clearEditor?.run()
                    insertReplyMarkup(composer.editField, markup)
                } else if (ticket == operation) cancel()
            },
            {
                if (ticket == operation && pending === recording && stillOwned()) {
                    uploading = false
                    bulletins.createSimpleBulletin(R.raw.error, "语音上传失败，录音已保留", "重试", Runnable { send() }).show()
                } else if (ticket == operation) cancel()
            })
    }

    fun cancel() {
        input.cancelCapture()
        discardFiles()
        clearEditor?.run()
    }

    private fun discardFiles() {
        ++operation
        editor?.setPlaying(false)
        pending?.file?.delete()
        extraSegment?.file?.delete()
        pending = null
        extraSegment = null
        uploading = false
        editing = false
        input.finishReview()
    }

    private fun sessionGeneration() = container.sessionCoordinator.currentRequestContext().generation
    private fun sessionUsername() = container.sessionStore.snapshot()?.username.orEmpty()
    private fun stillOwned() = !disposed && takeGeneration == sessionGeneration() && takeAccount == sessionUsername()

    internal fun attachPendingRecordingForTest(recording: VoiceRecording) = onRecorded(recording)
    internal fun sendForTest() = send()
    internal fun cancelForTest() = cancel()
    internal fun pendingForTest(): VoiceRecording? = pending
    internal fun isUploadingForTest(): Boolean = uploading
    internal fun boundRecordHostForTest(): NativeDoHooks.ComposerRecordHost = input
}

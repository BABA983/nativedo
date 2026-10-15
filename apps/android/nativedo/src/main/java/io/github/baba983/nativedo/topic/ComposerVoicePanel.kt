package io.github.baba983.nativedo.topic

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

/**
 * The composer's own voice band: the Telegram-shaped feedback for a hold-to-record microphone,
 * drawn from this client's theme rather than Telegram's account-backed recorder.
 *
 * <p>It has three faces and never more than one at a time:</p>
 * <ul>
 *   <li><b>Recording</b> - a live dot, the elapsed time, and the two things the gesture can do
 *       ("release to review, slide left to cancel"); a short tap that recorded nothing shows the same
 *       band as a hold hint.</li>
 *   <li><b>Review</b> - a finished take: play/pause, its duration, send and cancel. This is what a
 *       local recording becomes before it is uploaded, so a take can be checked and thrown away
 *       without ever reaching the site.</li>
 *   <li><b>Busy/Error</b> - the take is being uploaded or sent (a progress line), or the write
 *       failed (the site's refusal, and a retry that keeps the take).</li>
 * </ul>
 *
 * <p>The panel owns no state a screen needs to reason about: it draws what it is told and reports
 * the presses it gets. A screen binds [onPlayPause]/[onSend]/[onCancel]/[onRetry] and drives it.</p>
 */
class ComposerVoicePanel(
    context: Context,
    private val provider: Theme.ResourcesProvider?,
) : FrameLayout(context) {

    enum class State { Hidden, Recording, Review, Busy, Error }

    var onPlayPause: Runnable? = null
    var onSend: Runnable? = null
    var onCancel: Runnable? = null
    var onRetry: Runnable? = null

    private val dot = View(context)
    private val timer = label("")
    private val holdHint = label("按住录音，松开预览")
    private val recordingRow = row(dot, timer, holdHint)

    private val playButton = label("播放").apply { contentDescription = "播放录音" }
    private val duration = label("")
    private val sendButton = label("发送").apply { contentDescription = "发送录音" }
    private val cancelButton = label("取消").apply { contentDescription = "取消录音" }
    private val reviewRow = row(playButton, duration, sendButton, cancelButton)

    private val status = label("")
    private val retryButton = label("重试").apply { contentDescription = "重试发送录音" }
    private val errorRow = row(status, retryButton)

    var state: State = State.Hidden
        private set

    /** Whether the error row offers its retry; a capture that never started has none. */
    private var retryVisible = false

    init {
        setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground, provider))
        val pad = AndroidUtilities.dp(10f)
        setPadding(pad, pad, pad, pad)
        dot.layoutParams = LinearLayout.LayoutParams(AndroidUtilities.dp(10f), AndroidUtilities.dp(10f))
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Theme.getColor(Theme.key_chat_recordedVoiceDot, provider))
        }
        // The dot is a child of recordingRow (see its own construction above); adding it to this
        // FrameLayout as well would throw - a view has exactly one parent - so only the rows are
        // added here.
        addView(recordingRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        addView(reviewRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        addView(errorRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        playButton.setOnClickListener { onPlayPause?.run() }
        sendButton.setOnClickListener { onSend?.run() }
        cancelButton.setOnClickListener { onCancel?.run() }
        retryButton.setOnClickListener { onRetry?.run() }
        showOnly(State.Hidden)
    }

    /** The hold passed its threshold and the recorder opened: a live dot and a running timer. */
    fun showRecording() {
        timer.text = formatDuration(0L)
        holdHint.text = "松开预览 · 左滑取消"
        showOnly(State.Recording)
    }

    /** A short tap that recorded nothing: the same band says how to record. */
    fun showHoldHint() {
        timer.text = ""
        holdHint.text = "按住录音，松开预览"
        showOnly(State.Recording)
    }

    /** The live duration of the current capture. */
    fun updateRecording(elapsedMillis: Long) {
        if (state == State.Recording) {
            timer.text = formatDuration(elapsedMillis)
        }
    }

    /** A finished take, ready to check and send. */
    fun showReview(durationMillis: Long) {
        duration.text = formatDuration(durationMillis)
        setPlaying(false)
        showOnly(State.Review)
    }

    /** Whether the take is playing back, so the control reads play or pause. */
    fun setPlaying(playing: Boolean) {
        playButton.text = if (playing) "暂停" else "播放"
        playButton.contentDescription = if (playing) "暂停录音" else "播放录音"
    }

    /** The take is on its way to the site; nothing else is pressable until it lands or fails. */
    fun showBusy(message: String) {
        status.text = message
        showOnly(State.Busy)
    }

    /** The write failed and the take is kept: say so, and offer the retry. */
    fun showError(message: String?) {
        status.text = message ?: "发送失败"
        retryVisible = true
        showOnly(State.Error)
    }

    /**
     * The capture could not start at all (the microphone was busy, permission was refused between
     * the check and the start). There is no take to retry, so no retry control is offered - a dead
     * button would promise something this state cannot do.
     */
    fun showFailure(message: String?) {
        status.text = message ?: "无法开始录音"
        retryVisible = false
        showOnly(State.Error)
    }

    /** Nothing to show: the band is gone. */
    fun hide() = showOnly(State.Hidden)

    private fun showOnly(next: State) {
        state = next
        visibility = if (next == State.Hidden) View.GONE else View.VISIBLE
        recordingRow.visibility = if (next == State.Recording) View.VISIBLE else View.GONE
        reviewRow.visibility = if (next == State.Review) View.VISIBLE else View.GONE
        errorRow.visibility = if (next == State.Busy || next == State.Error) View.VISIBLE else View.GONE
        dot.visibility = if (next == State.Recording) View.VISIBLE else View.GONE
        retryButton.visibility = if (next == State.Error && retryVisible) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------------------------------------
    // Test hooks
    // ---------------------------------------------------------------------------------------------

    fun timerTextForTest(): CharSequence = timer.text

    fun durationTextForTest(): CharSequence = duration.text

    fun statusTextForTest(): CharSequence = status.text

    fun playLabelForTest(): CharSequence = playButton.text

    fun sendButtonForTest(): View = sendButton

    fun cancelButtonForTest(): View = cancelButton

    fun retryButtonForTest(): View = retryButton

    fun playButtonForTest(): View = playButton

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(AndroidUtilities.dp(8f), AndroidUtilities.dp(6f), AndroidUtilities.dp(8f), AndroidUtilities.dp(6f))
        isFocusable = true
        isClickable = true
    }

    private fun row(vararg children: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (child in children) addView(child)
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}

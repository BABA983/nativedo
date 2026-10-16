package io.github.baba983.nativedo.topic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.PlayPauseDrawable

/**
 * The composer's own voice band, drawn from this client's theme.
 *
 * <p>Since upstream `ChatActivityEnterView` owns the recording itself - the RecordCircle, the
 * slide-to-cancel text, the lock and the timer are its own views - this band no longer draws a
 * recording face. What is left for it is the part upstream cannot do for a linux.do post: the
 * finished take, played back locally and either uploaded to the site or thrown away.</p>
 *
 * <p>It has these faces and never more than one at a time:</p>
 * <ul>
 *   <li><b>Review</b> - a finished take: upstream's own play/pause morph, the take's duration, a
 *       position line fed from the real local player, and send and cancel. A local playback
 *       control, not a row of text buttons, so the reader can check the take the way the site's
 *       own composer lets them.</li>
 *   <li><b>Busy/Error</b> - the take is on its way to the site (a status line), or the write
 *       failed (the site's refusal, and a retry that keeps the take).</li>
 *   <li><b>Recording/HoldHint</b> - kept for the screens that still drive their own band; the
 *       linux.do screens let upstream's own recording interface show instead.</li>
 * </ul>
 *
 * <p>The panel owns no state a screen reasons about: it draws what it is told and reports the
 * presses it gets. A screen binds [onPlayPause]/[onSend]/[onCancel]/[onRetry] and drives it.</p>
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

    /** Upstream's own play/pause morph, so the transport reads as Telegram's voice playback. */
    private val playDrawable = PlayPauseDrawable(PLAY_SIZE_DP).apply {
        setColor(Theme.getColor(Theme.key_chat_messagePanelSend, provider))
    }
    private val playButton = ImageView(context).apply {
        contentDescription = "播放录音"
        setImageDrawable(playDrawable)
        scaleType = ImageView.ScaleType.CENTER
        isClickable = true
        isFocusable = true
        setPadding(AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(6f))
    }
    /** The take's real position, fed by the driving screen's own player; blank with no player. */
    private val playbackProgress = ProgressView(context, provider)
    private val duration = label("")
    private val sendButton = ImageView(context).apply {
        contentDescription = "发送录音"
        setImageResource(R.drawable.send_plane_24)
        setColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend, provider))
        scaleType = ImageView.ScaleType.CENTER
        isClickable = true
        isFocusable = true
        setPadding(AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(6f))
    }
    private val cancelButton = ImageView(context).apply {
        contentDescription = "取消录音"
        setImageResource(R.drawable.msg_delete)
        setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider))
        scaleType = ImageView.ScaleType.CENTER
        isClickable = true
        isFocusable = true
        setPadding(AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(6f), AndroidUtilities.dp(6f))
    }
    private val reviewRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(playButton, LinearLayout.LayoutParams(AndroidUtilities.dp(36f), AndroidUtilities.dp(36f)))
        addView(playbackProgress, LinearLayout.LayoutParams(0, AndroidUtilities.dp(36f), 1f))
        addView(duration)
        addView(sendButton, LinearLayout.LayoutParams(AndroidUtilities.dp(36f), AndroidUtilities.dp(36f)))
        addView(cancelButton, LinearLayout.LayoutParams(AndroidUtilities.dp(36f), AndroidUtilities.dp(36f)))
    }

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
        addView(recordingRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        addView(reviewRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        addView(errorRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        playButton.setOnClickListener { onPlayPause?.run() }
        sendButton.setOnClickListener { onSend?.run() }
        cancelButton.setOnClickListener { onCancel?.run() }
        retryButton.setOnClickListener { onRetry?.run() }
        // The morph invalidates this view while it animates, so it cannot be left unparented.
        playDrawable.setParent(playButton)
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
        duration.text = formatDuration(durationMillis.coerceAtLeast(0L))
        playbackProgress.reset()
        setPlaying(false)
        showOnly(State.Review)
    }

    /**
     * Whether the take is playing back: upstream's own play/pause morph flips and the position line
     * is cleared when playback ends, so the review returns to its start.
     */
    fun setPlaying(playing: Boolean) {
        playDrawable.setPause(playing, true)
        playButton.contentDescription = if (playing) "暂停录音" else "播放录音"
        if (!playing) {
            playbackProgress.reset()
        }
    }

    /**
     * The take's real playback position, a fraction in `[0, 1]`, read from the local player by the
     * driving screen. There is no wall clock here: a screen with no local player (a received
     * message's audio) never calls this, and the line stays blank.
     */
    fun setPlaybackProgress(progress: Float) {
        playbackProgress.setProgress(progress)
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
    fun hide() {
        setPlaying(false)
        showOnly(State.Hidden)
    }

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

    /**
     * The review's playback position: a thin line that fills only from the take's own player. It is
     * drawn once a real position has been reported ([setProgress]); a driver with no local player
     * leaves it blank. Nothing here advances on a wall clock.
     */
    private class ProgressView(context: Context, provider: Theme.ResourcesProvider?) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val trackColor = Theme.getColor(Theme.key_chat_messagePanelVoiceBackground, provider)
        private val fillColor = Theme.getColor(Theme.key_chat_messagePanelSend, provider)
        private var progress = 0f
        private var hasProgress = false

        fun setProgress(value: Float) {
            progress = value.coerceIn(0f, 1f)
            hasProgress = true
            invalidate()
        }

        fun reset() {
            progress = 0f
            hasProgress = false
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (!hasProgress) {
                return
            }
            val height = AndroidUtilities.dp(2f).toFloat()
            val cy = this.height / 2f
            paint.color = trackColor
            canvas.drawRect(0f, cy - height / 2f, this.width.toFloat(), cy + height / 2f, paint)
            paint.color = fillColor
            canvas.drawRect(0f, cy - height / 2f, this.width * progress, cy + height / 2f, paint)
        }
    }

    private companion object {
        /** The play/pause glyph, in dp; upstream's own composer badge uses a similar size. */
        const val PLAY_SIZE_DP = 20
    }
}

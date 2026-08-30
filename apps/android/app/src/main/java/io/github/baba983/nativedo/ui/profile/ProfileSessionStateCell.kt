package io.github.baba983.nativedo.ui.profile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ScaleStateListAnimator
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.voip.CellFlickerDrawable

internal data class SessionPresentation(
    val titleRes: Int,
    val subtitleRes: Int,
    val actionRes: Int,
    val action: SessionAction,
    val danger: Boolean,
) {
    companion object {
        fun signIn(): SessionPresentation = SessionPresentation(
            titleRes = R.string.profile_login_title,
            subtitleRes = R.string.profile_login_subtitle,
            actionRes = R.string.profile_login_action,
            action = SessionAction.OpenLogin,
            danger = false,
        )
    }
}

internal enum class SessionAction {
    OpenLogin,
    RetryRestore,
}

internal fun sessionPresentation(reason: SessionExpiredReason): SessionPresentation =
    when (reason) {
        SessionExpiredReason.ServerLoggedOut,
        SessionExpiredReason.CookieMissing,
        -> SessionPresentation(
            titleRes = R.string.profile_session_expired_title,
            subtitleRes = R.string.profile_session_expired_subtitle,
            actionRes = R.string.profile_login_again_action,
            action = SessionAction.OpenLogin,
            danger = true,
        )

        SessionExpiredReason.ChallengeRequired -> SessionPresentation(
            titleRes = R.string.profile_session_challenge_title,
            subtitleRes = R.string.profile_session_challenge_subtitle,
            actionRes = R.string.profile_session_challenge_action,
            action = SessionAction.OpenLogin,
            danger = false,
        )

        SessionExpiredReason.RecoveryFailed -> SessionPresentation(
            titleRes = R.string.profile_session_recovery_title,
            subtitleRes = R.string.profile_session_recovery_subtitle,
            actionRes = R.string.profile_session_recovery_action,
            action = SessionAction.RetryRestore,
            danger = false,
        )
    }

/**
 * Full-viewport sign-in / recovery cell shown when the account has no usable
 * profile: icon, explainer text, and a flicker-highlighted action button.
 */
internal class ProfileSessionStateCell(context: Context) : StickerEmptyView(
    context,
    null,
    STICKER_TYPE_SEARCH,
), Theme.Colorable {
    private val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(R.drawable.settings_login)
        setPadding(
            AndroidUtilities.dp(14f),
            AndroidUtilities.dp(14f),
            AndroidUtilities.dp(14f),
            AndroidUtilities.dp(14f),
        )
    }
    private val actionBackground = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null)
    private val actionView = object : TextView(context) {
        private val flickerDrawable = CellFlickerDrawable().apply {
            drawFrame = false
            repeatProgress = 2f
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            actionBackground.setBounds(0, 0, width, height)
            actionBackground.cornerRadius = minOf(width, height) / 2f
            flickerDrawable.setParentWidth(width)
        }

        override fun draw(canvas: Canvas) {
            actionBackground.draw(canvas)
            super.draw(canvas)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            AndroidUtilities.rectTmp.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
            flickerDrawable.draw(canvas, AndroidUtilities.rectTmp, measuredHeight / 2f, null)
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = View.MeasureSpec.getSize(widthMeasureSpec)
            if (width > AndroidUtilities.dp(260f)) {
                val measuredWidth = minOf(width, AndroidUtilities.dp(320f))
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY),
                    heightMeasureSpec,
                )
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }.apply {
        gravity = Gravity.CENTER
        typeface = AndroidUtilities.bold()
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
        ScaleStateListAnimator.apply(this, 0.02f, 1.2f)
    }
    private var danger = false
    internal var bottomContentInset: Int = 0

    init {
        subtitle.maxWidth = AndroidUtilities.dp(290f)
        showProgress(false, false)
        (linearLayout.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            leftMargin = AndroidUtilities.dp(16f)
            rightMargin = AndroidUtilities.dp(16f)
        }
        linearLayout.clipChildren = false
        linearLayout.addView(
            iconView,
            0,
            LayoutHelper.createLinear(52, 52, Gravity.CENTER_HORIZONTAL),
        )
        linearLayout.addView(
            actionView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                48,
                Gravity.CENTER_HORIZONTAL,
                0,
                28,
                0,
                4,
            ),
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val recycler = parent as? RecyclerView
        val availableHeight = if (recycler != null && recycler.measuredHeight > 0) {
            recycler.measuredHeight - recycler.paddingTop -
                maxOf(recycler.paddingBottom, bottomContentInset)
        } else {
            View.MeasureSpec.getSize(heightMeasureSpec)
        }
        super.onMeasure(
            widthMeasureSpec,
            View.MeasureSpec.makeMeasureSpec(
                availableHeight.coerceAtLeast(AndroidUtilities.dp(280f)),
                View.MeasureSpec.EXACTLY,
            ),
        )
    }

    fun bind(
        titleText: CharSequence?,
        subtitleText: CharSequence?,
        actionText: CharSequence?,
        danger: Boolean,
        onAction: () -> Unit,
    ) {
        this.danger = danger
        title(titleText ?: "")
        subtitle(subtitleText ?: "")
        actionView.text = actionText ?: ""
        actionView.contentDescription = actionText
        actionView.setOnClickListener { onAction() }
        updateColors()
    }

    override fun updateColors() {
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        val accent = Theme.getColor(
            if (danger) Theme.key_text_RedBold else Theme.key_featuredStickers_addButton,
        )
        iconView.setColorFilter(accent)
        iconView.background = Theme.createCircleDrawable(
            AndroidUtilities.dp(52f),
            Theme.multAlpha(accent, 0.12f),
        )
        actionBackground.colors = intArrayOf(
            Theme.getColor(Theme.key_featuredStickers_addButton),
            Theme.getColor(Theme.key_featuredStickers_addButton2),
        )
        actionView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
        actionView.background = Theme.createSimpleSelectorRoundRectDrawable(
            AndroidUtilities.dp(24f),
            Color.TRANSPARENT,
            Theme.getColor(Theme.key_featuredStickers_addButtonPressed),
        )
    }
}

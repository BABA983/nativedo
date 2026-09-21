package org.telegram.ui.iv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ChatActivityEnterView

/**
 * The block editor's toolbar and its seams: the pin's [RichEditorToolbar] is byte-exact, and the two
 * chat-side classes it mounts come from native seams - [org.telegram.ui.Components.ChatActivityEnterView.SendButton]
 * (here) and [org.telegram.ui.Components.AiButtonDrawable] (a pin copy). The tests pin what our host
 * will rely on: the send button reports to the delegate and follows `setSendEditing`/`setSendEnabled`,
 * draws the pin's plane at its own size inside the pin's circle, follows the toolbar's loading state,
 * and the toolbar measures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IvEditorToolbarContractTest {

    private val provider = object : Theme.ResourcesProvider {
        override fun getColor(key: Int): Int = 0xff000000.toInt()
        override fun getColorOrNull(key: Int): Int = 0xff000000.toInt()
    }

    private class Session : RichEditorToolbar.Delegate {
        var sends = 0
        var longClicks = 0
        var back = 0

        override fun getResourcesProvider(): Theme.ResourcesProvider? = null
        override fun onBack() { back++ }
        override fun onUndo() {}
        override fun onRedo() {}
        override fun onEmoji() {}
        override fun onAi() {}
        override fun onAttach() {}
        override fun onSend() { sends++ }
        override fun onSendLongClick(anchor: View): Boolean { longClicks++; return true }
        override fun onBlockButton(flag: Int, anchor: View) {}
        override fun onFormatting(styleFlag: Int) {}
        override fun onLink() {}
        override fun onButton(anchor: View) {}
        override fun onDate() {}
        override fun onMath() {}
        override fun onQuote() {}
        override fun onAiStyle() {}
    }

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun toolbar(session: Session): RichEditorToolbar =
        RichEditorToolbar(RuntimeEnvironment.getApplication(), session)

    @Test
    fun theSendButtonReportsToTheDelegate() {
        val session = Session()
        val toolbar = toolbar(session)

        val send = toolbar.sendButton
        assertThat(send).isNotNull()
        assertThat(send!!.resId).isEqualTo(R.drawable.send_plane_24)

        send.performClick()
        assertThat(session.sends).isEqualTo(1)

        send.performLongClick()
        assertThat(session.longClicks).isEqualTo(1)
    }

    @Test
    fun theSendButtonFollowsEditingAndEnabledState() {
        val toolbar = toolbar(Session())
        val send = toolbar.sendButton!!

        toolbar.setSendEditing(true)
        assertThat(send.resId).isEqualTo(R.drawable.input_done)
        toolbar.setSendEditing(false)
        assertThat(send.resId).isEqualTo(R.drawable.send_plane_24)

        toolbar.setSendEnabled(false)
        assertThat(send.isEnabled).isFalse()
        toolbar.setSendEnabled(true)
        assertThat(send.isEnabled).isTrue()
    }

    @Test
    fun theToolbarMeasuresItsPanels() {
        val toolbar = toolbar(Session())
        toolbar.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        assertThat(toolbar.measuredHeight).isGreaterThan(0)
        assertThat(toolbar.measuredWidth).isEqualTo(1080)
    }

    @Test
    fun blockAndFormattingButtonsCarryTheirAccessibilityNames() {
        assertThat(RichEditor.blockButtonContentDescription(RichEditorToolbar.BLOCK_TABLE)).isNotEmpty()
        assertThat(RichEditor.blockButtonContentDescription(RichEditorToolbar.BLOCK_MATH)).isNotEmpty()
        assertThat(RichEditor.formattingButtonContentDescription(RichTextStyle.BOLD)).isNotEmpty()
        assertThat(RichEditor.formattingButtonContentDescription(RichTextStyle.SPOILER)).isNotEmpty()
    }

    @Test
    fun theSendPlaneKeepsThePinsIntrinsicSizeInsteadOfTheMount() {
        val toolbar = toolbar(Session())
        val send = toolbar.sendButton!!
        val size = AndroidUtilities.dp(44f)
        send.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
        )
        send.layout(0, 0, size, size)
        send.draw(Canvas(Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)))

        // the pin lays the drawable out at its own intrinsic size, centred in the circle the button
        // paints (38dp inside the toolbar's 44dp mount): the mount must not stretch the plane
        val plane = planeBounds(send)
        assertThat(plane.width()).isEqualTo(AndroidUtilities.dp(24f))
        assertThat(plane.height()).isEqualTo(AndroidUtilities.dp(24f))
        assertThat(Math.abs(plane.exactCenterX() - size / 2f)).isAtMost(1f)
        assertThat(Math.abs(plane.exactCenterY() - size / 2f)).isAtMost(1f)
    }

    @Test
    fun theSendButtonFollowsTheToolbarsLoadingState() {
        val toolbar = toolbar(Session())
        val send = toolbar.sendButton!!

        // the pin's `INFINITE_LOADING`, and the toolbar's own `isOpen()` override following it
        assertThat(ChatActivityEnterView.SendButton.INFINITE_LOADING).isEqualTo(-3f)
        assertThat(send.isOpen).isFalse()
        toolbar.setSendLoading(true)
        assertThat(send.isOpen).isTrue()
        toolbar.setSendLoading(false)
        assertThat(send.isOpen).isFalse()

        // the host drives the pin's loading arc and the open state; every state has to draw
        val bitmap = Bitmap.createBitmap(AndroidUtilities.dp(44f), AndroidUtilities.dp(44f), Bitmap.Config.ARGB_8888)
        toolbar.setSendLoading(true)
        send.setLoading(true, ChatActivityEnterView.SendButton.INFINITE_LOADING)
        send.open.force(1f)
        send.draw(Canvas(bitmap))
        send.setLoading(false, 0f)
        toolbar.setSendLoading(false)
        send.open.force(0f)

        // the lock badge is the same drawing path (the host leaves it unused: the premium gate is a
        // chat wire-out, so this only pins that the state is carried and draws)
        send.setLocked(true)
        send.draw(Canvas(bitmap))
        send.setLocked(false)
    }

    /** The bounds the pin's `onDraw` gave the plane: its own intrinsic size, centred. */
    private fun planeBounds(send: ChatActivityEnterView.SendButton): Rect {
        val field = ChatActivityEnterView.SendButton::class.java.getDeclaredField("drawable")
        field.isAccessible = true
        return (field.get(send) as android.graphics.drawable.Drawable).bounds
    }
}

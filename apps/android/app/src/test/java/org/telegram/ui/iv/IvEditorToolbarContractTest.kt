package org.telegram.ui.iv

import android.graphics.Bitmap
import android.graphics.Canvas
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
 * will rely on: the send button reports to the delegate and follows `setSendEditing`/`setSendEnabled`
 * and the toolbar's loading state, and the block and formatting keys carry their accessibility names.
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
    fun blockAndFormattingButtonsCarryTheirAccessibilityNames() {
        assertThat(RichEditor.blockButtonContentDescription(RichEditorToolbar.BLOCK_TABLE)).isNotEmpty()
        assertThat(RichEditor.blockButtonContentDescription(RichEditorToolbar.BLOCK_MATH)).isNotEmpty()
        assertThat(RichEditor.formattingButtonContentDescription(RichTextStyle.BOLD)).isNotEmpty()
        assertThat(RichEditor.formattingButtonContentDescription(RichTextStyle.SPOILER)).isNotEmpty()
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
}

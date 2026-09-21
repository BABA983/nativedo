/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * The app-side host of the rich editor. The page itself is upstream's shape
 * (org/telegram/ui/iv/RichEditorHostLayout.java, the pin's ChatAttachAlertRichLayout with the
 * chat glue dropped); this class is what our product contributes: the app's sheet idiom around
 * it, and the two ends the page leaves open - the file picker and the send.
 */
package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.baba983.nativedo.core.ui.RichMessageMarkdown
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.UserConfig
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.iv.RichEditorHostLayout

/**
 * The block editor, opened from the reply composer: Telegram's editor page mounted in a
 * full-height sheet. The document is Discourse markdown, the same `raw` the composer's own
 * [TopicReplyComposerMarkdown] bridge writes, so opening the editor with the composer's draft and
 * sending its result back is a plain string round trip:
 *
 *  - [loadMarkdown] parses the draft into the editor's blocks (via [RichMessageMarkdown]),
 *  - [markdown] writes the blocks back,
 *  - [onSend] gets the markdown when the toolbar's send button is pressed (the page's own empty /
 *    pending-upload / limit guards already ran), and
 *  - [onDraft] gets it when the sheet is dismissed instead, so the composer keeps the edits.
 *
 * What the page cannot do itself is left to the caller: [onAttachRequested] is the app's file
 * picker (the caller hands the picked file back with `RichEditorHostLayout.attachMedia` /
 * `attachExternalMedia`), and emoji is not wired yet (the composer's emoji panel belongs to its
 * input island, not to this sheet).
 */
internal class RichEditorSheet(
    context: Context,
    resourcesProvider: Theme.ResourcesProvider,
    currentAccount: Int = UserConfig.selectedAccount,
    private val onSend: (String) -> Unit,
    private val onDraft: (String) -> Unit,
) : BottomSheet(context, false, resourcesProvider) {

    /** The app's file picker; left unset, the toolbar's attach button and media rows do nothing. */
    var onAttachRequested: (() -> Unit)? = null

    private val host = RichEditorHostLayout(context, currentAccount, resourcesProvider)
    private var sending = false
    private var unresizedWindowHeight = 0

    init {
        host.setDelegate(object : RichEditorHostLayout.Delegate {
            override fun onBackPressed() {
                dismiss()
            }

            override fun onAttachRequested() {
                onAttachRequested?.invoke()
            }

            override fun onEmojiRequested() {
                // NativeDo wire-out: the composer's emoji panel is anchored to its input island.
            }

            override fun onSendPressed() {
                val markdown = markdown()
                sending = true
                onSend(markdown)
                dismiss()
            }
        })
        // The page fills the sheet: the container measures it against the height the sheet has.
        // A `BottomSheet` only prepares its own container when it creates one, so a container
        // handed in has to paint the sheet background itself - the same shape the app's other
        // sheets use (`FixedHeaderRecyclerBottomSheet`) - and carry the padding `BottomSheet`
        // gives its own container (`BottomSheet.java`: `setPadding(backgroundPaddingLeft,
        // (applyTopPadding ? dp(8) : 0) + backgroundPaddingTop - 1, backgroundPaddingLeft,
        // (applyBottomPadding ? dp(8) : 0))`), the bottom `dp(8)` included.
        containerView = object : FrameLayout(context) {
            override fun onDraw(canvas: Canvas) {
                shadowDrawable.setBounds(0, 0, measuredWidth, measuredHeight)
                shadowDrawable.draw(canvas)
            }
        }.apply {
            setWillNotDraw(false)
            setPadding(
                backgroundPaddingLeft,
                AndroidUtilities.dp(8f) + backgroundPaddingTop - 1,
                backgroundPaddingLeft,
                AndroidUtilities.dp(8f),
            )
            addView(
                host,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        fixNavigationBar(getThemedColor(Theme.key_dialogBackground))
        setOnDismissListener(DialogInterface.OnDismissListener { reportDraftIfNotSent() })
        // The page's own top is part of its insets; the sheet's handle gap is the base it keeps.
        host.viewTreeObserver.addOnGlobalLayoutListener { applyWindowInsets(ViewCompat.getRootWindowInsets(host)) }
    }

    /**
     * The keyboard is an insets problem, not a fixed number. [BottomSheet]'s own container consumes
     * the system insets before the page sees them, so the sheet reads them from the dialog's decor
     * (which sees them first) and feeds the page the part of its bottom edge the system actually
     * covers - the navigation bar when the keyboard is down, the keyboard's own overlap when it is
     * up, and nothing when the window was resized for the IME and already rides above it. See
     * [RichEditorSheetInsets] for the arithmetic.
     */
    override fun show() {
        super.show()
        window?.decorView?.let { decor ->
            ViewCompat.setOnApplyWindowInsetsListener(decor) { _, insets ->
                applyWindowInsets(insets)
                insets
            }
        }
        applyWindowInsets(ViewCompat.getRootWindowInsets(host))
    }

    private fun applyWindowInsets(insets: WindowInsetsCompat?) {
        if (insets == null || !host.isAttachedToWindow) return
        val currentWindowHeight = host.rootView?.height ?: return
        if (currentWindowHeight <= 0) return
        val location = IntArray(2)
        host.getLocationInWindow(location)
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        // A resized decor is shorter while the IME is visible. Keep its keyboard-down height as
        // the reference; using the shortened height here would lift an already-resized page again.
        if (ime == 0) unresizedWindowHeight = currentWindowHeight
        val windowHeight = maxOf(unresizedWindowHeight, currentWindowHeight)
        val values = RichEditorSheetInsets.compute(
            hostTop = location[1],
            hostBottom = location[1] + host.height,
            windowHeight = windowHeight,
            systemBarsTop = bars.top,
            systemBarsBottom = bars.bottom,
            imeBottom = ime,
            minTopInset = AndroidUtilities.dp(8f),
        )
        host.setTopInset(values.top)
        host.setBottomInsets(values.bottom, 0)
        // the page's own keyboard seam: the pin's `measureKeyboardHeight() > dp(20)`, read from the
        // window's real IME inset instead of the size-notifier view the attach sheet had
        host.setImeInset(ime)
    }

    /** The composer's draft (Discourse markdown) as the editor's starting document. */
    fun loadMarkdown(markdown: String) {
        host.loadRichMessage(RichMessageMarkdown.parse(markdown))
    }

    /** The editor's document as Discourse markdown. */
    fun markdown(): String = RichMessageMarkdown.blocksToMarkdown(host.collectBlocks())

    /** The page, for the caller's file picker (`attachMedia` / `attachExternalMedia`). */
    fun editorHost(): RichEditorHostLayout = host

    /**
     * What a dismissal does: keep the editor's markdown as the caller's draft - unless the sheet
     * already sent it, or the page writes nothing (an untouched editor is not a draft) - and
     * release the page.
     */
    internal fun reportDraftIfNotSent() {
        if (!sending) {
            val markdown = markdown()
            if (markdown.isNotEmpty()) onDraft(markdown)
        }
        host.destroy()
    }
}

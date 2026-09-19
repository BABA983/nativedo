package io.github.baba983.nativedo.ui.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Components.RecyclerListView
import kotlin.math.min

/**
 * The post body's selection engine: upstream's `ArticleTextSelectionHelper` (the pin's file) with
 * the pieces this product adds, wired through upstream's own extension points the way
 * `ChatActivity` and `MessagePreviewView` do it:
 *
 * - the Quote action, through `canShowQuote()` / `onQuoteClick(...)` (upstream's hooks; the pin's
 *   `quoteText()` only fetches a `MessageObject` for a chat cell, so the base file carries one
 *   small `// NativeDo:` line that lets a non-chat view reach this override);
 * - [destroy], which tears down the overlay the fragment mounts over the list.
 */
internal class TopicPostTextSelectionHelper : TextSelectionHelper.ArticleTextSelectionHelper() {

    var onQuote: ((TopicPostCell, CharSequence) -> Unit)? = null

    private var observedAdapter: RecyclerView.Adapter<*>? = null

    /**
     * Our old port observed the list adapter so a selection never pointed at stale positions while
     * the topic streamed; the pin's file leaves that to its host, so the rule lives here. A change
     * at or before the selection's end invalidates it, a change after it does not
     * (`getEndCell()` is the pin's normalized end).
     */
    private val adapterObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            clearIfSelecting(Int.MAX_VALUE)
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            clearIfSelecting(positionStart)
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            clearIfSelecting(positionStart)
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            clearIfSelecting(positionStart)
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            clearIfSelecting(min(fromPosition, toPosition))
        }
    }

    override fun setParentView(view: ViewGroup?) {
        super.setParentView(view)
        val listView = view as? RecyclerListView ?: return
        val adapter = listView.adapter ?: return
        if (observedAdapter === adapter) {
            return
        }
        observedAdapter?.unregisterAdapterDataObserver(adapterObserver)
        observedAdapter = adapter
        adapter.registerAdapterDataObserver(adapterObserver)
    }

    private fun clearIfSelecting(positionStart: Int) {
        if (isInSelectionMode && positionStart <= endCell) {
            clear(false)
        }
    }

    protected override fun canShowQuote(): Boolean = onQuote != null && selectedView is TopicPostCell

    override fun onQuoteClick(messageObject: MessageObject?, start: Int, end: Int, text: CharSequence?) {
        val cell = selectedView as? TopicPostCell ?: return
        if (text.isNullOrEmpty()) {
            return
        }
        onQuote?.invoke(cell, text)
    }

    /** The fragment adds [getOverlayView] to its root, so teardown has to remove it again. */
    fun destroy() {
        clear(true)
        hideActions()
        val overlay = getOverlayView(ApplicationLoader.applicationContext)
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }
}

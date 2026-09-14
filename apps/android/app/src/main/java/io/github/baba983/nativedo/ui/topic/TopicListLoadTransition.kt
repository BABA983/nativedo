package io.github.baba983.nativedo.ui.topic

import android.view.View
import android.view.ViewTreeObserver
import org.telegram.ui.Components.DialogsItemAnimator
import org.telegram.ui.Components.RecyclerListView

internal class TopicListLoadTransition(
    private val listView: RecyclerListView,
    private val loadingView: View,
    private val onContentReady: () -> Unit,
) : View.OnAttachStateChangeListener {
    private val itemAnimator = DialogsItemAnimator(listView)
    private var hasContent = false
    private var observedTree: ViewTreeObserver? = null
    var isContentReady = false
        private set

    private val contentPreDraw = ViewTreeObserver.OnPreDrawListener {
        if (hasContent && listView.childCount > 0 &&
            !listView.hasPendingAdapterUpdates() && !listView.isLayoutRequested
        ) {
            stopObserving()
            loadingView.visibility = View.GONE
            isContentReady = true
            listView.itemAnimator = itemAnimator
            onContentReady()
        }
        true
    }

    init {
        listView.itemAnimator = null
        listView.addOnAttachStateChangeListener(this)
    }

    fun reset() {
        stopObserving()
        hasContent = false
        isContentReady = false
        listView.itemAnimator = null
    }

    fun submit(hasContent: Boolean, showLoading: Boolean, updateItems: () -> Unit) {
        if (!hasContent) reset()
        this.hasContent = hasContent
        // Initial rows and a recycled page's replacement are a single presentation.
        // DialogsItemAnimator is only attached after that content has been laid out.
        if (!isContentReady) listView.itemAnimator = null
        updateItems()
        if (showLoading) {
            loadingView.visibility = View.VISIBLE
        } else if (!hasContent || isContentReady) {
            loadingView.visibility = View.GONE
        }
        if (hasContent && !isContentReady) observeContentLayout()
    }

    private fun observeContentLayout() {
        if (observedTree != null || !listView.isAttachedToWindow) return
        observedTree = listView.viewTreeObserver.also { it.addOnPreDrawListener(contentPreDraw) }
    }

    private fun stopObserving() {
        observedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(contentPreDraw)
        observedTree = null
    }

    override fun onViewAttachedToWindow(view: View) {
        if (hasContent && !isContentReady) observeContentLayout()
    }

    override fun onViewDetachedFromWindow(view: View) {
        stopObserving()
    }
}

package io.github.baba983.nativedo.harness

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import org.telegram.ui.Components.FilterTabsView
import org.telegram.ui.Components.RecyclerListView

/**
 * The screen's own list, wherever it sits under [activity]'s content view.
 *
 * <p>A screen can hold more than one list: the topic list's category strip is upstream's
 * [FilterTabsView], which carries a list of tabs of its own, and a walk that stops at the first
 * list finds that strip instead of the list the screen is about. The strip's list is inside the
 * strip - a tab itself is a view, not a row - so the screen's list is the first one that is not.
 * A screen can also hold several lists that are real (the topic list's category pages are a pair
 * that slides, the other pages of the root pager sit behind it), so the list the reader is looking
 * at is the first one outside the strip that is actually shown; a screen with no shown list (a
 * screen that is still being built) falls back to its first list, which is what a caller that only
 * needs "the list that exists" gets.</p>
 */
fun contentListOf(activity: Activity): RecyclerListView? {
    val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
    val lists = contentListsIn(content)
    return lists.firstOrNull { it.isShown } ?: lists.firstOrNull()
}

private fun contentListsIn(view: View): List<RecyclerListView> = when (view) {
    is RecyclerListView -> if (view.parent is FilterTabsView) emptyList() else listOf(view)
    is ViewGroup -> (0 until view.childCount).flatMap { contentListsIn(view.getChildAt(it)) }
    else -> emptyList()
}

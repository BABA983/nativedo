package io.github.baba983.nativedo.harness

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import org.telegram.ui.Components.FilterTabsView
import org.telegram.ui.Components.RecyclerListView

/**
 * The screen's own list, wherever it sits under [activity]'s content view.
 *
 * A screen can hold more than one list: the topic list's category strip is upstream's
 * [FilterTabsView], which carries a list of tabs of its own, and a walk that stops at the first list
 * finds that strip instead of the list the screen is about. The strip's list is inside the strip -
 * a tab itself is a view, not a row - so the screen's list is the first one that is not.
 */
fun contentListOf(activity: Activity): RecyclerListView? =
    activity.findViewById<ViewGroup>(android.R.id.content)?.let { findContentList(it) }

private fun findContentList(view: View): RecyclerListView? {
    if (view is RecyclerListView && view.parent !is FilterTabsView) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findContentList(view.getChildAt(index))?.let { return it }
        }
    }
    return null
}

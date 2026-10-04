package io.github.baba983.nativedo.harness

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import org.telegram.ui.ActionBar.ActionBarMenuSubItem

/**
 * The post menu a long press opened — upstream's `ItemOptions` scrim popup — as the rows the
 * reader taps.
 *
 * The popup is its own window, so it is not under the activity's content view: Robolectric records
 * the views the window manager was handed, and the newest one that carries menu rows is the menu
 * on screen.
 */
fun latestItemOptionsMenu(activity: Activity): List<ActionBarMenuSubItem> {
    val windowManager = org.robolectric.shadow.api.Shadow.extract(activity.windowManager)
        as org.robolectric.shadows.ShadowWindowManagerImpl
    val views = windowManager.views
    for (view in views.asReversed()) {
        if (view === activity.window.decorView) {
            continue
        }
        val rows = mutableListOf<ActionBarMenuSubItem>()
        collectMenuRows(view, rows)
        if (rows.isNotEmpty()) {
            return rows
        }
    }
    return emptyList()
}

/** The labels the menu shows, in the order it shows them. */
fun menuItemLabels(rows: List<ActionBarMenuSubItem>): List<String> =
    rows.map { row -> row.textView.text.toString() }

/** Taps the row labelled [label], the way the reader taps it. The click closes the menu too. */
fun clickMenuItem(rows: List<ActionBarMenuSubItem>, label: String) {
    val row = rows.firstOrNull { row -> menuItemLabels(listOf(row)).first() == label }
        ?: throw AssertionError("no menu row labelled \"$label\" among " + menuItemLabels(rows))
    row.performClick()
}

private fun collectMenuRows(view: View?, out: MutableList<ActionBarMenuSubItem>) {
    when (view) {
        is ActionBarMenuSubItem -> out += view
        is ViewGroup -> for (index in 0 until view.childCount) collectMenuRows(view.getChildAt(index), out)
    }
}

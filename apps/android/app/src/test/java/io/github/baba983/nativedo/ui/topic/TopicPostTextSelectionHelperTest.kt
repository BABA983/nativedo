package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RecyclerListView
import java.lang.reflect.Field

/**
 * The helper resolves the selected cell through the list's layout manager, and a host that does not hand
 * it one gets the wrong cell: `pickStartView()`/`pickEndView()` then fall back to
 * `parentView.getChildAt(adapterPosition)`, which indexes *attached children* with an *adapter* position.
 *
 * That is not a shape detail - a device pass read its consequences on the action bar (a selection in a
 * later post lost the Quote item, gained a Select all, and the bar anchored at the top of the screen,
 * because `onGetContentRect` answers y=1 when `selectedView` is null). The test drives the resolution the
 * bar depends on: with the wiring, a selection in a cell whose adapter position is beyond the attached
 * children resolves to that cell; without it, to null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TopicPostTextSelectionHelperTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(context)
        AndroidUtilities.init(context)
        LocaleController.init(context)
        Theme.getActiveTheme()
        Theme.createDialogsResources(context)
    }

    @Test
    fun aSelectionResolvesToItsOwnCellWhenTheAdapterPositionIsPastTheChildCount() {
        val list = scrolledList()
        val helper = TopicPostTextSelectionHelper()
        helper.setParentView(list)

        val target = visibleCells(list).maxByOrNull { list.getChildAdapterPosition(it) }!!
        assertThat(list.getChildAdapterPosition(target)).isGreaterThan(list.childCount)
        assertThat(helper.selectRangeOf(target, 0, 1, 6)).isTrue()

        pickStartView(helper)
        assertThat(field(helper, "selectedView")).isSameInstanceAs(target)
    }

    @Test
    fun withoutTheLayoutManagerTheSameSelectionResolvesToNothing() {
        val list = scrolledList()
        val helper = TopicPostTextSelectionHelper()
        // What a host that only calls setParentView used to hand the helper: no layout manager, so the
        // pick falls back to a child index that cannot exist.
        helper.setParentView(list)
        field(helper, "layoutManager")
        helper.layoutManager = null

        val target = visibleCells(list).maxByOrNull { list.getChildAdapterPosition(it) }!!
        assertThat(helper.selectRangeOf(target, 0, 1, 6)).isTrue()

        pickStartView(helper)
        assertThat(field(helper, "selectedView")).isNull()
    }

    /** A list scrolled past its start, so its adapter positions no longer index its attached children. */
    private fun scrolledList(): RecyclerListView {
        val list = RecyclerListView(context)
        list.layoutManager = LinearLayoutManager(context)
        val cells = ArrayList<TopicPostCell>()
        list.adapter = object : RecyclerListView.SelectionAdapter() {
            override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean = true
            override fun getItemCount(): Int = 10
            override fun getItemViewType(position: Int): Int = if (position == 0) 0 else 1
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view: View = if (viewType == 0) {
                    TextView(context).apply {
                        height = 300
                        text = "topic header"
                    }
                } else {
                    cell(80 + cells.size * 200).also { cells.add(it) }
                }
                view.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (viewType == 0) 300 else 200,
                )
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }
        val container = FrameLayout(context)
        container.addView(list, FrameLayout.LayoutParams(945, 500))
        layout(container)
        list.scrollBy(0, 1400)
        layout(container)
        return list
    }

    private fun layout(container: FrameLayout) {
        container.measure(
            View.MeasureSpec.makeMeasureSpec(945, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
        )
        container.layout(0, 0, 945, 500)
    }

    private fun visibleCells(list: RecyclerListView): List<TopicPostCell> =
        (0 until list.childCount).map { list.getChildAt(it) }.filterIsInstance<TopicPostCell>()

    private fun pickStartView(helper: TopicPostTextSelectionHelper) {
        var type: Class<*>? = helper.javaClass
        while (type != null) {
            try {
                val method = type.getDeclaredMethod("pickStartView")
                method.isAccessible = true
                method.invoke(helper)
                return
            } catch (_: NoSuchMethodException) {
                type = type.superclass
            }
        }
        error("pickStartView not found")
    }

    private fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val f: Field = type.getDeclaredField(name)
                f.isAccessible = true
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    private fun cell(postNumber: Int): TopicPostCell {
        val html = "<p>Paragraph number $postNumber, long enough to select a word from it.</p>"
        return TopicPostCell(context).apply {
            setPost(
                Post(
                    id = postNumber.toLong(),
                    postNumber = postNumber,
                    authorUsername = "alice",
                    authorDisplayName = "Alice Chen",
                    cookedHtml = html,
                ),
                null, null, true, true, 2566145L, "https://linux.do/",
                emptyMap(), emptyMap(), emptyList(), 0, emptyList(),
                parseCookedPostBody(html, "https://linux.do/"), PostBodyBlockState(), 0, null,
            )
        }
    }
}

package org.telegram.ui.Cells

import android.content.Context
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TextSelectionHelperTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ApplicationLoader.init(context)
    }

    @Test
    fun parentScroll_detachesTransientViewWithoutClearingEndpointSelection() {
        val helper = TextSelectionHelper.ArticleTextSelectionHelper()
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = CountingAdapter(2)
        }
        helper.setParentView(recyclerView)
        helper.setRecyclerView(recyclerView)
        helper.installSelectionForTest(position = 1, selectedView = SelectableTestView(context))

        helper.onParentScrolled()

        assertThat(helper.isInSelectionMode).isTrue()
        assertThat(helper.privateField("selectedView")).isNull()
    }

    @Test
    fun adapterAppendAfterSelectionPreservesButEarlierInsertClears() {
        val adapter = CountingAdapter(2)
        val helper = TextSelectionHelper.ArticleTextSelectionHelper()
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        helper.setParentView(recyclerView)
        helper.setRecyclerView(recyclerView)
        helper.installSelectionForTest(position = 1, selectedView = null)

        adapter.insert(position = 2)
        assertThat(helper.isInSelectionMode).isTrue()

        adapter.insert(position = 0)
        assertThat(helper.isInSelectionMode).isFalse()
    }

    @Test
    fun adapterChangeInsideSelectionClearsCachedPositionOwnership() {
        val adapter = CountingAdapter(3)
        val helper = TextSelectionHelper.ArticleTextSelectionHelper()
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        helper.setParentView(recyclerView)
        helper.setRecyclerView(recyclerView)
        helper.installSelectionForTest(position = 1, selectedView = null)

        adapter.notifyItemChanged(1)

        assertThat(helper.isInSelectionMode).isFalse()
    }

    @Test
    fun stopScrolling_reprojectsActiveEndpointWhenItsCellReturns() {
        val endpointView = SelectableTestView(context, withText = true)
        val helper = TextSelectionHelper.ArticleTextSelectionHelper().apply {
            layoutManager = object : LinearLayoutManager(context) {
                override fun findViewByPosition(position: Int): View? =
                    endpointView.takeIf { position == 1 }
            }
        }
        helper.installSelectionForTest(position = 1, selectedView = null)

        helper.stopScrolling()

        assertThat(helper.privateField("selectedView")).isSameInstanceAs(endpointView)
        assertThat(helper.isInSelectionMode).isTrue()
    }

    private class SelectableTestView(
        context: Context,
        private val withText: Boolean = false,
    ) : View(context), TextSelectionHelper.ArticleSelectableView {
        override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
            if (!withText) return
            val layout = StaticLayout.Builder.obtain("hello", 0, 5, TextPaint(), 200).build()
            blocks += object : TextSelectionHelper.TextLayoutBlock {
                override fun getLayout(): StaticLayout = layout
                override fun getX(): Int = 0
                override fun getY(): Int = 0
                override fun getRow(): Int = 0
                override fun getPrefix(): CharSequence = ""
            }
        }
    }

    private class CountingAdapter(initialCount: Int) : RecyclerView.Adapter<CountingHolder>() {
        private var count = initialCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountingHolder =
            CountingHolder(View(parent.context))

        override fun onBindViewHolder(holder: CountingHolder, position: Int) = Unit

        override fun getItemCount(): Int = count

        fun insert(position: Int) {
            count++
            notifyItemInserted(position)
        }
    }

    private class CountingHolder(view: View) : RecyclerView.ViewHolder(view)
}

private fun TextSelectionHelper.ArticleTextSelectionHelper.installSelectionForTest(
    position: Int,
    selectedView: TextSelectionHelper.ArticleSelectableView?,
) {
    setPrivateField("selectedView", selectedView)
    setPrivateField("selectedIndex", 0)
    setPrivateField("selectionStart", 0)
    setPrivateField("selectionEnd", 1)
    setPrivateField("startViewPosition", position)
    setPrivateField("startViewChildPosition", 0)
    setPrivateField("startViewOffset", 0)
    setPrivateField("endViewPosition", position)
    setPrivateField("endViewChildPosition", 0)
    setPrivateField("endViewOffset", 1)
}

private fun Any.setPrivateField(name: String, value: Any?) {
    javaClass.getDeclaredField(name).apply {
        isAccessible = true
        set(this@setPrivateField, value)
    }
}

private fun Any.privateField(name: String): Any? =
    javaClass.getDeclaredField(name).run {
        isAccessible = true
        get(this@privateField)
    }

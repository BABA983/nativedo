package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.NoCopySpan
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpanWatcher
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.ui.topic.TopicPostTextSelectionHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.RecyclerListView

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
    fun parentScrollAndStopScrollingKeepTheSelection() {
        val helper = TopicPostTextSelectionHelper()
        val recyclerView = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = CountingAdapter(2)
        }
        helper.setParentView(recyclerView)
        // The pin's stopScrolling() invalidates the overlay, so it has to exist.
        helper.getOverlayView(context)
        helper.installSelectionForTest(position = 1, selectedView = SelectableTestView(context))

        helper.onParentScrolled()
        helper.stopScrolling()

        assertThat(helper.isInSelectionMode).isTrue()
    }

    @Test
    fun adapterAppendAfterSelectionPreservesButEarlierInsertClears() {
        val adapter = CountingAdapter(2)
        val helper = TopicPostTextSelectionHelper()
        val recyclerView = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        helper.setParentView(recyclerView)
        helper.installSelectionForTest(position = 1, selectedView = null)

        adapter.insert(position = 2)
        assertThat(helper.isInSelectionMode).isTrue()

        adapter.insert(position = 0)
        assertThat(helper.isInSelectionMode).isFalse()
    }

    @Test
    fun adapterChangeInsideSelectionClearsCachedPositionOwnership() {
        val adapter = CountingAdapter(3)
        val helper = TopicPostTextSelectionHelper()
        val recyclerView = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        helper.setParentView(recyclerView)
        helper.installSelectionForTest(position = 1, selectedView = null)

        adapter.notifyItemChanged(1)

        assertThat(helper.isInSelectionMode).isFalse()
    }


    @Test
    fun cachedTextDetachesFromTheLiveLayoutAndDropsWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
        }
        val spanWatcher = object : SpanWatcher {
            override fun onSpanAdded(text: Spannable?, what: Any?, start: Int, end: Int) = Unit
            override fun onSpanRemoved(text: Spannable?, what: Any?, start: Int, end: Int) = Unit
            override fun onSpanChanged(text: Spannable?, what: Any?, ostart: Int, oend: Int, nstart: Int, nend: Int) = Unit
        }
        val noCopySpan = object : NoCopySpan {}
        val text = SpannableStringBuilder("hello").apply {
            setSpan(textWatcher, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(spanWatcher, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(noCopySpan, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val prefix = SpannableStringBuilder("1.").apply {
            setSpan(textWatcher, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val helper = TextSelectionHelper.ArticleTextSelectionHelper()
        helper.cacheTextForViewForTest(
            SelectableTestView(context, withText = true, sourceText = text, prefixText = prefix),
            position = 0,
        )

        val cached = requireNotNull(helper.cachedText(position = 0, child = 0))
        assertThat(cached).isNotSameInstanceAs(text)
        assertThat(cached.toString()).isEqualTo("hello")
        assertThat(cached.getSpans(0, cached.length, TextWatcher::class.java)).isEmpty()
        assertThat(cached.getSpans(0, cached.length, SpanWatcher::class.java)).isEmpty()
        assertThat(cached.getSpans(0, cached.length, NoCopySpan::class.java)).isEmpty()
        assertThat(cached.getSpans(0, cached.length, StyleSpan::class.java)).hasLength(1)

        val cachedPrefix = requireNotNull(helper.cachedPrefix(position = 0, child = 0))
        assertThat(cachedPrefix).isNotSameInstanceAs(prefix)
        assertThat(cachedPrefix.toString()).isEqualTo("1.")
        assertThat(cachedPrefix.getSpans(0, cachedPrefix.length, TextWatcher::class.java)).isEmpty()
    }

    @Test
    fun nullPrefixClearsTheCachedEntry() {
        val helper = TextSelectionHelper.ArticleTextSelectionHelper()
        helper.cacheTextForViewForTest(
            SelectableTestView(context, withText = true, sourceText = SpannableStringBuilder("hello"), prefixText = null),
            position = 0,
        )

        assertThat(helper.cachedText(position = 0, child = 0).toString()).isEqualTo("hello")
        assertThat(helper.cachedPrefixKeyPresent(position = 0, child = 0)).isFalse()
    }

    private class SelectableTestView(
        context: Context,
        private val withText: Boolean = false,
        private val sourceText: CharSequence = "hello",
        private val prefixText: CharSequence? = "",
    ) : View(context), TextSelectionHelper.ArticleSelectableView {
        override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
            if (!withText) return
            val layout = StaticLayout.Builder.obtain(sourceText, 0, sourceText.length, TextPaint(), 200).build()
            blocks += object : TextSelectionHelper.TextLayoutBlock {
                override fun getLayout(): StaticLayout = layout
                override fun getX(): Int = 0
                override fun getY(): Int = 0
                override fun getRow(): Int = 0
                override fun getPrefix(): CharSequence? = prefixText
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
    declaredField(name).apply {
        isAccessible = true
        set(this@setPrivateField, value)
    }
}

private fun Any.privateField(name: String): Any? =
    declaredField(name).run {
        isAccessible = true
        get(this@privateField)
    }

/** The engine's state lives on the article helper (or its base), not on the subclass under test. */
private fun Any.declaredField(name: String): java.lang.reflect.Field {
    var type: Class<*>? = javaClass
    while (type != null) {
        try {
            return type.getDeclaredField(name)
        } catch (ignored: NoSuchFieldException) {
            type = type.superclass
        }
    }
    throw NoSuchFieldException(name)
}

private fun TextSelectionHelper.ArticleTextSelectionHelper.cacheTextForViewForTest(
    view: TextSelectionHelper.ArticleSelectableView,
    position: Int,
) {
    // The pin caches a view's text (and its prefix) through this private filler.
    declaredMethod(
        "populateTextCacheForView",
        TextSelectionHelper.ArticleSelectableView::class.java,
        Int::class.javaPrimitiveType!!,
    ).apply { isAccessible = true }.invoke(this, view, position)
}

private fun Any.declaredMethod(name: String, vararg parameterTypes: Class<*>): java.lang.reflect.Method {
    var type: Class<*>? = javaClass
    while (type != null) {
        try {
            return type.getDeclaredMethod(name, *parameterTypes)
        } catch (ignored: NoSuchMethodException) {
            type = type.superclass
        }
    }
    throw NoSuchMethodException(name)
}

private fun TextSelectionHelper.ArticleTextSelectionHelper.cachedText(position: Int, child: Int): Spanned? =
    cachedArray("textByPosition").get(cacheKey(position, child)) as? Spanned

private fun TextSelectionHelper.ArticleTextSelectionHelper.cachedPrefix(position: Int, child: Int): Spanned? =
    cachedArray("prefixTextByPosition").get(cacheKey(position, child)) as? Spanned

private fun TextSelectionHelper.ArticleTextSelectionHelper.cachedPrefixKeyPresent(position: Int, child: Int): Boolean =
    cachedArray("prefixTextByPosition").indexOfKey(cacheKey(position, child)) >= 0

private fun cacheKey(position: Int, child: Int): Int = position + (child shl 16)

@Suppress("UNCHECKED_CAST")
private fun TextSelectionHelper.ArticleTextSelectionHelper.cachedArray(name: String): SparseArray<CharSequence> {
    val field = TextSelectionHelper.ArticleTextSelectionHelper::class.java.getDeclaredField(name)
    field.isAccessible = true
    return field.get(this) as SparseArray<CharSequence>
}

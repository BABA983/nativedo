package io.github.baba983.nativedo.debug

import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.topic.TopicDetailFragment
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import kotlin.math.max
import kotlin.math.min

class DebugTopicFixtureSheet(
    context: Context,
    private val fragment: BaseFragment,
    private val onDismissed: () -> Unit = {},
) : BottomSheet(context, false, null) {
    private val adapter = FixtureAdapter(fragment)
    private val recyclerListView = RecyclerListView(context)
    private val fixedHeaderView = FixedHeaderView(context)
    private val rect = RectF()
    private var scrollOffsetY = 0
    private var ignoreLayout = false
    private var fullHeight = false
    private var contentRowsHeight = estimateContentHeight(DebugTopicFixtures.entries)
    private var statusBarOpen: Boolean? = null

    init {
        setUseLightStatusBar(fragment.isLightStatusBar)
        containerView = object : FrameLayout(context) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.y < scrollOffsetY) {
                    dismiss()
                    return true
                }
                return super.onInterceptTouchEvent(ev)
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val height = MeasureSpec.getSize(heightMeasureSpec)
                ignoreLayout = true
                setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0)
                ignoreLayout = false
                fixedHeaderView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                val contentSize = fixedHeaderView.measuredHeight + contentRowsHeight + backgroundPaddingTop + AndroidUtilities.statusBarHeight
                val recyclerParams = recyclerListView.layoutParams as? FrameLayout.LayoutParams
                if (recyclerParams != null && recyclerParams.topMargin != fixedHeaderView.measuredHeight) {
                    recyclerParams.topMargin = fixedHeaderView.measuredHeight
                    recyclerListView.layoutParams = recyclerParams
                }
                var padding = if (contentSize < height / 5f * 3.2f) 0 else (height / 5f * 2).toInt()
                if (padding != 0 && contentSize < height) {
                    padding -= height - contentSize
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop
                }
                if (recyclerListView.paddingTop != padding) {
                    ignoreLayout = true
                    recyclerListView.setPadding(0, padding, 0, AndroidUtilities.dp(8f))
                    ignoreLayout = false
                }
                fullHeight = contentSize >= height
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(minOf(contentSize, height), MeasureSpec.EXACTLY))
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                updateLayout()
            }

            override fun requestLayout() {
                if (ignoreLayout) return
                super.requestLayout()
            }

            override fun onDraw(canvas: Canvas) {
                var y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6f)
                var top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13f)
                var statusBarHeight = 0
                var radProgress = 1f
                top += AndroidUtilities.statusBarHeight
                y += AndroidUtilities.statusBarHeight
                if (fullHeight) {
                    if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
                        val diff = min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop)
                        top -= diff
                        radProgress = 1f - min(1f, diff * 2f / AndroidUtilities.statusBarHeight)
                    }
                    if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                        statusBarHeight = min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop)
                    }
                }
                shadowDrawable.setBounds(0, top, measuredWidth, measuredHeight)
                shadowDrawable.draw(canvas)
                if (radProgress != 1f) {
                    Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_dialogBackground)
                    rect.set(
                        backgroundPaddingLeft.toFloat(),
                        (backgroundPaddingTop + top).toFloat(),
                        (measuredWidth - backgroundPaddingLeft).toFloat(),
                        (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat(),
                    )
                    val radius = AndroidUtilities.dp(12f) * radProgress
                    canvas.drawRoundRect(rect, radius, radius, Theme.dialogs_onlineCirclePaint)
                }
                val handleAlpha = max(0f, min(1f, (y - AndroidUtilities.statusBarHeight).toFloat() / AndroidUtilities.dp(16f)))
                if (handleAlpha > 0f) {
                    val width = AndroidUtilities.dp(36f)
                    rect.set(
                        (measuredWidth - width) / 2f,
                        y.toFloat(),
                        (measuredWidth + width) / 2f,
                        (y + AndroidUtilities.dp(4f)).toFloat(),
                    )
                    Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_sheet_scrollUp)
                    Theme.dialogs_onlineCirclePaint.alpha = (Theme.dialogs_onlineCirclePaint.alpha * handleAlpha).toInt()
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.dialogs_onlineCirclePaint)
                }
                if (statusBarHeight > 0) {
                    Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_dialogBackground)
                    canvas.drawRect(
                        backgroundPaddingLeft.toFloat(),
                        (AndroidUtilities.statusBarHeight - statusBarHeight).toFloat(),
                        (measuredWidth - backgroundPaddingLeft).toFloat(),
                        AndroidUtilities.statusBarHeight.toFloat(),
                        Theme.dialogs_onlineCirclePaint,
                    )
                }
                updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2)
            }
        }
        containerView.setWillNotDraw(false)
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground))
        setOnDismissListener(DialogInterface.OnDismissListener { onDismissed() })

        recyclerListView.itemAnimator = null
        recyclerListView.clipToPadding = false
        recyclerListView.layoutManager = LinearLayoutManager(context)
        recyclerListView.adapter = adapter
        recyclerListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateLayout()
            }
        })
        recyclerListView.setOnItemClickListener(RecyclerListView.OnItemClickListener { _, position ->
            val fixture = adapter.fixtureAt(position) ?: return@OnItemClickListener
            dismiss()
            fragment.presentFragment(TopicDetailFragment(fixture.topicId, fixture.label))
        })

        containerView.addView(
            recyclerListView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP),
        )
        containerView.addView(
            fixedHeaderView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP),
        )
        fixedHeaderView.bringToFront()
        adapter.setItems(DebugTopicFixtures.entries)
    }

    override fun canDismissWithSwipe(): Boolean = false

    private fun updateLightStatusBar(open: Boolean) {
        if (statusBarOpen == open) return
        val openBgLight = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_dialogBackground)) > 0.721f
        statusBarOpen = open
        AndroidUtilities.setLightStatusBar(window, if (open) openBgLight else fragment.isLightStatusBar)
    }

    private fun updateLayout() {
        if (recyclerListView.childCount <= 0) {
            setScrollOffsetY(recyclerListView.paddingTop)
            return
        }
        var firstChild: View? = null
        var firstChildPosition = Int.MAX_VALUE
        for (index in 0 until recyclerListView.childCount) {
            val child = recyclerListView.getChildAt(index)
            val position = recyclerListView.getChildAdapterPosition(child)
            if (position in 0 until firstChildPosition) {
                firstChildPosition = position
                firstChild = child
            }
        }
        val newOffset = if (firstChild != null && firstChild.top >= 0) firstChild.top else 0
        if (scrollOffsetY != newOffset) {
            setScrollOffsetY(newOffset)
        }
    }

    private fun setScrollOffsetY(newOffset: Int) {
        scrollOffsetY = newOffset
        fixedHeaderView.translationY = newOffset.toFloat()
        fixedHeaderView.bringToFront()
        containerView.invalidate()
    }

    private class FixedHeaderView(
        context: Context,
    ) : FrameLayout(context) {
        private val titleView = TextView(context)

        init {
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground))
            titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
            titleView.textSize = 20f
            titleView.typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)
            titleView.text = context.getString(R.string.profile_topic_fixture_sheet_title)
            titleView.setPadding(AndroidUtilities.dp(21f), AndroidUtilities.dp(16f), AndroidUtilities.dp(21f), AndroidUtilities.dp(12f))
            addView(
                titleView,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP),
            )
            setOnClickListener(null)
        }
    }

    private companion object {
        fun estimateContentHeight(fixtures: List<DebugTopicFixture>): Int {
            val categoryCount = fixtures.map { it.category }.distinct().size
            val rowCount = fixtures.size
            return categoryCount * AndroidUtilities.dp(40f) + rowCount * AndroidUtilities.dp(48f) + AndroidUtilities.dp(16f)
        }
    }
}

private sealed class FixtureListItem {
    data class Header(val title: String) : FixtureListItem()
    data class Fixture(val fixture: DebugTopicFixture) : FixtureListItem()
}

private class FixtureAdapter(
    private val fragment: BaseFragment,
) : RecyclerListView.SelectionAdapter() {
    private val items = ArrayList<FixtureListItem>()

    fun setItems(fixtures: List<DebugTopicFixture>) {
        items.clear()
        var lastCategory: String? = null
        for (fixture in fixtures) {
            if (fixture.category != lastCategory) {
                items.add(FixtureListItem.Header(fixture.category))
                lastCategory = fixture.category
            }
            items.add(FixtureListItem.Fixture(fixture))
        }
        notifyDataSetChanged()
    }

    fun fixtureAt(position: Int): DebugTopicFixture? {
        return (items.getOrNull(position) as? FixtureListItem.Fixture)?.fixture
    }

    override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
        return items.getOrNull(holder.bindingAdapterPosition) is FixtureListItem.Fixture
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FixtureListItem.Header -> VIEW_TYPE_HEADER
            is FixtureListItem.Fixture -> VIEW_TYPE_FIXTURE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val view = when (viewType) {
            VIEW_TYPE_HEADER -> HeaderCell(context, fragment.getResourceProvider())
            else -> TextCell(context, fragment.getResourceProvider())
        }
        return RecyclerListView.Holder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FixtureListItem.Header -> (holder.itemView as HeaderCell).setText(item.title)
            is FixtureListItem.Fixture -> {
                val cell = holder.itemView as TextCell
                cell.setTextAndValue(item.fixture.label, "#${item.fixture.topicId}", position != items.lastIndex)
            }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_FIXTURE = 1
    }
}

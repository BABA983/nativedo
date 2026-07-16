package io.github.baba983.nativedo.debug

import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.components.FixedHeaderRecyclerBottomSheet
import io.github.baba983.nativedo.ui.topic.TopicDetailFragment
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView

class DebugTopicFixtureSheet(
    context: Context,
    private val fragment: BaseFragment,
    private val onDismissed: () -> Unit = {},
) : FixedHeaderRecyclerBottomSheet(context, fragment) {
    private val adapter = FixtureAdapter(fragment)
    private val fixedHeaderView = FixedHeaderView(context)

    init {
        setOnDismissListener(DialogInterface.OnDismissListener { onDismissed() })
        setRecyclerAdapter(adapter)
        setFixedHeaderView(fixedHeaderView)
        setContentHeightPx(estimateContentHeight(DebugTopicFixtures.entries))
        recyclerListView.setOnItemClickListener(RecyclerListView.OnItemClickListener { _, position ->
            val fixture = adapter.fixtureAt(position) ?: return@OnItemClickListener
            dismiss()
            fragment.presentFragment(TopicDetailFragment(fixture.topicId, fixture.label))
        })
        adapter.setItems(DebugTopicFixtures.entries)
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

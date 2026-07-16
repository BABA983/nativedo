package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.model.SearchUserSummary
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.ui.components.DiscourseUserCell
import io.github.baba983.nativedo.ui.topic.cell.TopicFooterCell
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.RecyclerListView

internal class SearchUsersAdapter(
    private val context: Context,
    private val baseUrl: String,
) : RecyclerListView.SelectionAdapter() {
    private var users: List<SearchUserSummary> = emptyList()
    private var loading = false
    private var stateMessage: String? = null
    private var retry: (() -> Unit)? = null

    fun submit(
        users: List<SearchUserSummary>,
        loading: Boolean,
        stateMessage: String? = null,
        retry: (() -> Unit)? = null,
    ) {
        this.users = if (loading) emptyList() else users
        this.loading = loading
        this.stateMessage = stateMessage
        this.retry = retry
        notifyDataSetChanged()
    }

    fun userAt(position: Int): SearchUserSummary? {
        return users.getOrNull(position)
    }

    override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
        return holder.itemViewType == VIEW_USER || holder.itemViewType == VIEW_STATE && retry != null
    }

    override fun getItemCount(): Int {
        return when {
            loading -> LOADING_ROWS
            users.isNotEmpty() -> users.size
            stateMessage != null -> 1
            else -> 0
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            loading -> VIEW_LOADING
            users.isNotEmpty() -> VIEW_USER
            else -> VIEW_STATE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = when (viewType) {
            VIEW_USER -> DiscourseUserCell(parent.context)
            VIEW_LOADING -> FlickerLoadingView(context).apply {
                setIsSingleCell(true)
                setItemsCount(1)
                setViewType(FlickerLoadingView.REACTED_TYPE)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(DiscourseUserCell.ITEM_HEIGHT_DP.toFloat()),
                )
            }
            else -> TopicFooterCell(parent.context)
        }
        return RecyclerListView.Holder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_USER -> {
                val user = users[position]
                val avatarUrl = resolveAvatarUrl(baseUrl, user.avatarTemplate, size = 96)
                (holder.itemView as DiscourseUserCell).bind(
                    user.name,
                    user.username,
                    avatarUrl,
                    avatarImageFilter("", 50),
                    false,
                    user,
                )
            }
            VIEW_STATE -> (holder.itemView as TopicFooterCell).bind(
                stateMessage.orEmpty(),
                retry?.let { action -> Runnable { action() } },
            )
        }
    }

    private companion object {
        private const val VIEW_USER = 0
        private const val VIEW_LOADING = 1
        private const val VIEW_STATE = 2
        private const val LOADING_ROWS = 7
    }
}

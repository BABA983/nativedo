package io.github.baba983.nativedo.topic

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.PostPoll
import io.github.baba983.nativedo.core.model.PostReactionUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.Cells.GraySectionCell
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView

/**
 * Who voted, option by option: the site's own `/polls/voters.json`.
 *
 * Upstream's poll cell asks its host for this when the reader presses "View Votes"; Telegram answers
 * with its own voters sheet. linux.do's payload is the same shape - voters grouped by the option
 * digest - so the sheet is built here instead: one section per option that has votes, and a row per
 * voter whose tap opens that reader's profile.
 */
object DiscoursePollVotersUi {

    @JvmStatic
    fun voters(
        fragment: BaseFragment,
        container: AppContainer,
        postId: Long,
        poll: PostPoll,
        onUser: (String, String) -> Unit,
    ): BottomSheet {
        val activity = fragment.parentActivity!!
        val sheet = BottomSheet(activity, false)
        sheet.setTitle(poll.title.ifBlank { "投票" })
        val body = PollVotersView(activity, container, postId, poll, sheet) { user ->
            sheet.dismiss()
            onUser(user.username, user.displayName)
        }
        sheet.setCustomView(body)
        sheet.setOnDismissListener(Runnable { body.destroy() })
        return sheet
    }
}

private class PollVotersView(
    context: Context,
    private val container: AppContainer,
    private val postId: Long,
    private val poll: PostPoll,
    private val sheet: BottomSheet,
    private val onUser: (PostReactionUser) -> Unit,
) : FrameLayout(context) {

    private sealed interface Row {
        data class Section(val label: String, val votes: Int) : Row
        data class Voter(val user: PostReactionUser) : Row
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val list = RecyclerListView(context)
    private val state = EmptyTextProgressView(context)
    private var rows = emptyList<Row>()
    private var destroyed = false

    private val adapter = object : RecyclerListView.SelectionAdapter() {
        override fun isEnabled(holder: RecyclerView.ViewHolder) = holder.itemViewType == 1
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Section) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            RecyclerListView.Holder(
                if (viewType == 0) {
                    GraySectionCell(parent.context)
                } else {
                    UserCell(parent.context, 0, 0, false).apply {
                        layoutParams = RecyclerView.LayoutParams(
                            LayoutHelper.MATCH_PARENT,
                            AndroidUtilities.dp(64f),
                        )
                    }
                },
            )

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Section -> (holder.itemView as GraySectionCell).setText(row.label, "${row.votes} 票", null)
                is Row.Voter -> {
                    val user = row.user
                    val peer = DiscourseAvatars.user(
                        user.username, user.displayName, user.avatarTemplate,
                        container.baseUrl, user.animatedAvatarUrl,
                    )
                    (holder.itemView as UserCell).apply {
                        setData(peer, user.displayName.ifBlank { user.username }, "@${user.username}", 0)
                        DiscourseAvatars.imageLocation(peer)?.let { location ->
                            avatarImageView.setImage(
                                location, "50_50", avatarImageView.imageReceiver.getDrawable(), peer,
                            )
                        }
                    }
                }
            }
        }
    }

    init {
        minimumHeight = AndroidUtilities.dp(180f)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.setOnItemClickListener { _, position ->
            (rows.getOrNull(position) as? Row.Voter)?.let { row -> onUser(row.user) }
        }
        addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 180f))
        addView(state, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 180f))
        state.setOnClickListener { load() }
        load()
    }

    fun destroy() {
        destroyed = true
        scope.cancel()
    }

    private fun load() {
        if (destroyed) return
        state.visibility = View.VISIBLE
        state.showProgress()
        scope.launch {
            runCatching { container.discourseApiClient.fetchPollVoters(postId, poll.name) }
                .onSuccess { groups ->
                    if (destroyed) return@onSuccess
                    // The site answers per option digest; the poll's own order and labels are what
                    // the reader saw in the bubble, so the sheet follows that order, not the map's.
                    val byOption = groups.associateBy { group -> group.id }
                    val built = ArrayList<Row>()
                    for (option in poll.options) {
                        val group = byOption[option.id] ?: continue
                        if (group.users.isEmpty()) continue
                        built += Row.Section(option.label, group.count.coerceAtLeast(group.users.size))
                        group.users.forEach { user -> built += Row.Voter(user) }
                    }
                    rows = built
                    adapter.notifyDataSetChanged()
                    val height = built.sumOf { row ->
                        if (row is Row.Section) AndroidUtilities.dp(32f) else AndroidUtilities.dp(64f)
                    }.coerceIn(AndroidUtilities.dp(180f), AndroidUtilities.dp(6 * 64f))
                    list.layoutParams.height = height
                    state.layoutParams.height = height
                    minimumHeight = height
                    requestLayout()
                    if (built.isEmpty()) {
                        state.setText("还没有人投票")
                        state.showTextView()
                    } else {
                        state.visibility = View.GONE
                        cacheAvatars(built)
                    }
                }
                .onFailure {
                    if (!destroyed) {
                        state.setText("加载失败，点击重试")
                        state.showTextView()
                    }
                }
        }
    }

    private fun cacheAvatars(built: List<Row>) {
        for (row in built) {
            if (row !is Row.Voter) continue
            val user = row.user
            scope.launch {
                val url = DiscourseAvatars.url(user.avatarTemplate, container.baseUrl) ?: return@launch
                val peer = DiscourseAvatars.user(
                    user.username, user.displayName, user.avatarTemplate,
                    container.baseUrl, user.animatedAvatarUrl,
                )
                val file = DiscourseAvatars.cache(container.imageOkHttpClient, url, peer)
                if (!destroyed && file != null) adapter.notifyDataSetChanged()
            }
        }
    }
}

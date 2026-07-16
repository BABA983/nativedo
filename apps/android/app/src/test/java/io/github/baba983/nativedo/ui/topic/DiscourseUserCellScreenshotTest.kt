package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import io.github.baba983.nativedo.ui.components.DiscourseUserCell
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RecyclerListView

internal class DiscourseUserCellScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun singleUserKeepsSixRowViewport() {
        paparazziRule.snapshot { context ->
            stableUserList(context, USERS.take(1))
        }
    }

    @Test
    fun sixUsersFillStableViewport() {
        paparazziRule.snapshot { context ->
            stableUserList(context, USERS)
        }
    }

    @Test
    fun pollVoterUsesBoldNameAndSingleAtUsername() {
        paparazziRule.snapshot { context ->
            RecyclerListView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(DiscourseUserCell.ITEM_HEIGHT_DP.toFloat()),
                )
                setBackgroundColor(Theme.getColor(Theme.key_dialogBackground))
                layoutManager = LinearLayoutManager(context)
                adapter = UserAdapter(
                    users = listOf(UserFixture("Alice Chen", "@alice")),
                    style = DiscourseUserCell.STYLE_POLL,
                )
                itemAnimator = null
            }
        }
    }

    private fun stableUserList(
        context: Context,
        users: List<UserFixture>,
    ): RecyclerListView {
        val rowHeight = AndroidUtilities.dp(DiscourseUserCell.ITEM_HEIGHT_DP.toFloat())
        val viewportHeight = StableUserListSheetSizePolicy(rowHeight).minimumHeight()
        return RecyclerListView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                viewportHeight,
            )
            setBackgroundColor(Theme.getColor(Theme.key_dialogBackground))
            layoutManager = LinearLayoutManager(context)
            adapter = UserAdapter(users)
            itemAnimator = null
            overScrollMode = RecyclerListView.OVER_SCROLL_NEVER
            setHasFixedSize(true)
        }
    }

    private data class UserFixture(
        val displayName: String,
        val username: String,
    )

    private class UserAdapter(
        private val users: List<UserFixture>,
        private val style: Int = DiscourseUserCell.STYLE_DEFAULT,
    ) : RecyclerView.Adapter<UserHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserHolder {
            return UserHolder(DiscourseUserCell(parent.context, style))
        }

        override fun onBindViewHolder(holder: UserHolder, position: Int) {
            val user = users[position]
            holder.cell.bind(
                user.displayName,
                user.username,
                "",
                "50_50",
                false,
                user,
            )
        }

        override fun getItemCount(): Int = users.size
    }

    private class UserHolder(
        val cell: DiscourseUserCell,
    ) : RecyclerView.ViewHolder(cell)

    private companion object {
        val USERS = listOf(
            UserFixture("Alice Chen", "alice"),
            UserFixture("", "bob"),
            UserFixture("A Very Long Display Name Used To Verify Truncation", "charlie"),
            UserFixture("Dana", "dana"),
            UserFixture("Evan", "evan"),
            UserFixture("Faye", "faye"),
        )
    }
}

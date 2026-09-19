package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.feature.topic.ui.PollOptionRef
import io.github.baba983.nativedo.feature.topic.ui.PollVotersSheetArgs
import io.github.baba983.nativedo.feature.topic.ui.TopicReactionUsersUiState
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.messenger.nativedo.PollVotesBridge
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.Components.PollVotesAlert

internal class TopicUserSheetsScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun pollVotesAlertRendersVoterSections() {
        paparazziRule.snapshot { context ->
            val args = PollVotersSheetArgs(
                pollName = "favorite",
                pollTitle = "Which option do you prefer?",
                options = listOf(
                    PollOptionRef(id = "first", label = "First option", votes = USERS.size),
                    PollOptionRef(id = "second", label = "Second option", votes = 2),
                ),
                initialSelectedOptionId = "first",
                totalVoters = USERS.size + 2,
            )
            PollVotesBridge.setProvider { request, callback ->
                val optionId = String(request.option ?: ByteArray(0), Charsets.UTF_8)
                callback.onVotes(
                    TopicPollVotesAlert.buildVotesList(BASE_URL, USERS, optionId, USERS.size, 1),
                )
                true
            }
            try {
                val alert = PollVotesAlert(context, 0, TopicPollVotesAlert.buildMessage(43L, args), null)
                sheetViewport(context, alert)
            } finally {
                PollVotesBridge.setProvider(null)
            }
        }
    }

    @Test
    fun reactionUsersSheetRendersIdentityRowsInRealPanel() {
        paparazziRule.snapshot { context ->
            val sheetClass = Class.forName(
                "io.github.baba983.nativedo.ui.topic.TopicDetailFragment\$TopicReactionUsersSheet",
            )
            val sheet = sheetClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(
                context,
                testFragment(),
                BASE_URL,
                emptyMap<String, String>(),
                { _: String -> Unit },
                { Unit },
                { _: String -> Unit },
                { Unit },
            ) as BottomSheet
            sheetClass.getDeclaredMethod(
                "submit",
                List::class.java,
                Int::class.javaPrimitiveType,
                TopicReactionUsersUiState::class.java,
            ).apply { isAccessible = true }.invoke(
                sheet,
                listOf(PostReaction(id = "heart", count = USERS.size)),
                USERS.size,
                TopicReactionUsersUiState(
                    groups = listOf(PostReactionUsersGroup(id = "heart", count = USERS.size, users = USERS)),
                    selectedReactionId = "heart",
                    hasLoaded = true,
                ),
            )
            sheetViewport(context, sheet)
        }
    }

    private fun sheetViewport(context: Context, sheet: BottomSheet): View {
        val width = AndroidUtilities.dp(360f)
        val height = AndroidUtilities.dp(500f)
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height,
            )
            addView(
                sheet.sheetContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.BOTTOM,
                ),
            )
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, width, height)
        }
    }

    private fun testFragment(): BaseFragment = object : BaseFragment() {}

    private companion object {
        const val BASE_URL = "https://linux.do/"

        val USERS = listOf(
            PostReactionUser(username = "alice", displayName = "Alice Chen"),
            PostReactionUser(username = "bob", displayName = "Bob Wang"),
            PostReactionUser(username = "charlie", displayName = "Charlie Zhang"),
            PostReactionUser(username = "dana", displayName = "Dana Liu"),
            PostReactionUser(username = "evan", displayName = "Evan Zhao"),
            PostReactionUser(username = "faye", displayName = "Faye Sun"),
        )
    }
}

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
import io.github.baba983.nativedo.feature.topic.ui.TopicPollVoterOptionPageUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicPollVotersSheetUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicPollVotersUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicReactionUsersUiState
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet

internal class TopicUserSheetsScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun pollVotersSheetRendersIdentityRowsInRealPanel() {
        paparazziRule.snapshot { context ->
            val sheet = TopicPollVotersSheet(
                context = context,
                fragment = testFragment(),
                baseUrl = BASE_URL,
                onOptionSelected = {},
                onLoadMore = {},
                onRetry = {},
                onUserSelected = {},
                onDismissed = {},
            )
            sheet.submit(
                TopicPollVotersSheetUiState(
                    postId = 43L,
                    pollName = "favorite",
                    pollTitle = "Which option do you prefer?",
                    options = listOf(PollOptionRef(id = "first", label = "First", votes = USERS.size)),
                ),
                TopicPollVotersUiState(
                    groups = listOf(PostReactionUsersGroup(id = "first", count = USERS.size, users = USERS)),
                    selectedOptionId = "first",
                    optionPages = mapOf(
                        "first" to TopicPollVoterOptionPageUiState(hasLoaded = true, endReached = true),
                    ),
                ),
            )
            sheetViewport(context, sheet)
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

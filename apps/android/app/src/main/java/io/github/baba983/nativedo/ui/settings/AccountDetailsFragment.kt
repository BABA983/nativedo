/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Discourse account metadata kept outside the content-first profile screen.
 */
package io.github.baba983.nativedo.ui.settings

import android.content.Context
import android.view.View
import androidx.lifecycle.ViewModelProvider
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.feature.profile.ui.ProfileViewModel
import io.github.baba983.nativedo.ui.profile.ProfileViewModelFactory
import io.github.baba983.nativedo.ui.profile.profileFormatDate
import io.github.baba983.nativedo.ui.profile.profileFormatRelativeActivity
import io.github.baba983.nativedo.ui.profile.profileFormatTimeRead
import java.util.ArrayList
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

private const val ACCOUNT_ROW_USERNAME = 1
private const val ACCOUNT_ROW_ROLE = 2
private const val ACCOUNT_ROW_TRUST_LEVEL = 3
private const val ACCOUNT_ROW_TITLE = 4
private const val ACCOUNT_ROW_FLAIR = 5
private const val ACCOUNT_ROW_PRIMARY_GROUP = 6
private const val ACCOUNT_ROW_JOINED = 7
private const val ACCOUNT_ROW_GAMIFICATION = 20
private const val ACCOUNT_ROW_ACCEPTED_ANSWERS = 21
private const val ACCOUNT_ROW_FOLLOWERS = 22
private const val ACCOUNT_ROW_FOLLOWING = 23
private const val ACCOUNT_ROW_PROFILE_VIEWS = 24
private const val ACCOUNT_ROW_BADGES = 25
private const val ACCOUNT_ROW_LAST_SEEN = 40
private const val ACCOUNT_ROW_LAST_POSTED = 41
private const val ACCOUNT_ROW_TIME_READ = 42
private const val ACCOUNT_ROW_RECENT_TIME_READ = 43
private const val ACCOUNT_ROW_CAKE_DATE = 44

class AccountDetailsFragment : SessionSettingsFragment() {
    private var profile: UserProfile? = null

    override fun createView(context: Context): View {
        val view = super.createView(context)
        val viewModel = ViewModelProvider(
            this,
            ProfileViewModelFactory(appContainer.profileRepository),
        )[ProfileViewModel::class.java]
        viewCollectors += fragmentScope.launch {
            appContainer.sessionCoordinator.sessionState.collectLatest(viewModel::onSessionStateChanged)
        }
        viewCollectors += fragmentScope.launch {
            viewModel.uiState.collectLatest { state ->
                profile = state.profile
                listView.adapter?.update(false)
            }
        }
        return view
    }

    override fun getTitle(): CharSequence =
        context?.getString(R.string.profile_settings_account_title).orEmpty()

    override fun isSupportEdgeToEdge(): Boolean = true

    override fun onInsets(left: Int, top: Int, right: Int, bottom: Int) {
        listView.setPadding(0, 0, 0, bottom)
        listView.clipToPadding = false
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val profile = profile
        if (profile == null) {
            items.add(UItem.asCenterShadow(string(R.string.profile_about_loading)))
            return
        }
        addIdentityItems(items, profile)
        addStatisticsItems(items, profile)
        addActivityItems(items, profile)
    }

    private fun addIdentityItems(items: ArrayList<UItem>, profile: UserProfile) {
        val start = items.size
        items.add(UItem.asHeader(string(R.string.profile_identity_title)))
        addValue(items, ACCOUNT_ROW_USERNAME, "@${profile.username}", R.string.profile_username_label)
        profileRoleLabelRes(profile)?.let {
            addValue(items, ACCOUNT_ROW_ROLE, string(it), R.string.profile_role_label)
        }
        profileTrustLevelLabelRes(profile.trustLevel)?.let {
            addValue(items, ACCOUNT_ROW_TRUST_LEVEL, string(it), R.string.profile_trust_level_label)
        }
        addValueIfNotBlank(items, ACCOUNT_ROW_TITLE, profile.title, R.string.profile_title_label)
        addValueIfNotBlank(items, ACCOUNT_ROW_FLAIR, profile.flairName, R.string.profile_flair_label)
        addValueIfNotBlank(items, ACCOUNT_ROW_PRIMARY_GROUP, profile.primaryGroupName, R.string.profile_group_label)
        addValueIfNotBlank(items, ACCOUNT_ROW_JOINED, profileFormatDate(profile.createdAt), R.string.profile_joined_label)
        finishSection(items, start)
    }

    private fun addStatisticsItems(items: ArrayList<UItem>, profile: UserProfile) {
        val start = items.size
        items.add(UItem.asHeader(string(R.string.profile_stats_title)))
        addPositiveValue(items, ACCOUNT_ROW_GAMIFICATION, profile.gamificationScore, R.string.profile_gamification_label)
        addPositiveValue(items, ACCOUNT_ROW_ACCEPTED_ANSWERS, profile.acceptedAnswers, R.string.profile_accepted_answers_label)
        addPositiveValue(items, ACCOUNT_ROW_FOLLOWERS, profile.totalFollowers, R.string.profile_followers_label)
        addPositiveValue(items, ACCOUNT_ROW_FOLLOWING, profile.totalFollowing, R.string.profile_following_label)
        addPositiveValue(items, ACCOUNT_ROW_PROFILE_VIEWS, profile.profileViewCount, R.string.profile_profile_views_label)
        addPositiveValue(items, ACCOUNT_ROW_BADGES, profile.badgeCount, R.string.profile_badges_label)
        finishSection(items, start)
    }

    private fun addActivityItems(items: ArrayList<UItem>, profile: UserProfile) {
        val start = items.size
        items.add(UItem.asHeader(string(R.string.profile_activity_title)))
        addValueIfNotBlank(items, ACCOUNT_ROW_LAST_SEEN, profileFormatRelativeActivity(profile.lastSeenAt), R.string.profile_last_seen_label)
        addValueIfNotBlank(items, ACCOUNT_ROW_LAST_POSTED, profileFormatRelativeActivity(profile.lastPostedAt), R.string.profile_last_posted_label)
        if (profile.timeRead > 0) {
            addValue(items, ACCOUNT_ROW_TIME_READ, profileFormatTimeRead(profile.timeRead), R.string.profile_time_read_label)
        }
        if (profile.recentTimeRead > 0) {
            addValue(items, ACCOUNT_ROW_RECENT_TIME_READ, profileFormatTimeRead(profile.recentTimeRead), R.string.profile_recent_time_read_label)
        }
        addValueIfNotBlank(items, ACCOUNT_ROW_CAKE_DATE, profileFormatDate(profile.cakeDate), R.string.profile_cake_date_label)
        finishSection(items, start)
    }

    private fun addPositiveValue(items: ArrayList<UItem>, id: Int, value: Int, labelRes: Int) {
        if (value > 0) addValue(items, id, value.toString(), labelRes)
    }

    private fun addValueIfNotBlank(items: ArrayList<UItem>, id: Int, value: String, labelRes: Int) {
        value.trim().takeIf(String::isNotEmpty)?.let { addValue(items, id, it, labelRes) }
    }

    private fun addValue(items: ArrayList<UItem>, id: Int, value: String, labelRes: Int) {
        items.add(UItem.asSettingsCell(id, string(labelRes), value))
    }

    private fun finishSection(items: ArrayList<UItem>, sectionStart: Int) {
        if (items.size == sectionStart + 1) {
            items.removeAt(sectionStart)
        } else {
            items.add(UItem.asShadow(null))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) = Unit

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val value = item.subtext?.toString()?.trim().orEmpty()
        if (value.isEmpty() || item.id <= 0) return false
        AndroidUtilities.addToClipboard(value)
        BulletinFactory.of(this)
            .createCopyBulletin(string(R.string.profile_value_copied))
            .show()
        return true
    }

    override fun onFragmentDestroy() {
        fragmentScope.cancel()
        super.onFragmentDestroy()
    }
}

internal fun profileRoleLabelRes(profile: UserProfile): Int? = when {
    profile.admin -> R.string.profile_role_admin
    profile.moderator -> R.string.profile_role_moderator
    else -> null
}

internal fun profileTrustLevelLabelRes(level: Int): Int? = when (level) {
    0 -> R.string.profile_trust_level_0
    1 -> R.string.profile_trust_level_1
    2 -> R.string.profile_trust_level_2
    3 -> R.string.profile_trust_level_3
    4 -> R.string.profile_trust_level_4
    else -> null
}

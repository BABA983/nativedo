package io.github.baba983.nativedo.profile

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.Bookmark
import io.github.baba983.nativedo.core.model.UserBadge
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.model.UserSummary
import io.github.baba983.nativedo.feature.profile.data.ProfileExtrasRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the profile's data layer to the Java fragment, the way
 * [io.github.baba983.nativedo.topic.DiscourseLoader] bridges the topic screens: a suspend call made
 * from Java needs a continuation by hand, so the launch lives here and reports back through
 * callbacks.
 */
object ProfileLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun extras(container: AppContainer) = ProfileExtrasRepository(container.discourseApiClient)

    /** Reads one profile, from the cache when it is there and from the site when it is not. */
    fun load(
        container: AppContainer,
        username: String,
        onLoaded: (UserProfile) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.profileRepository.load(username).getOrThrow() }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** Re-reads the profile, bypassing the cache: what a write just changed. */
    fun refresh(
        container: AppContainer,
        username: String,
        onLoaded: (UserProfile) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.profileRepository.refresh(username).getOrThrow() }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** `/u/{username}/summary.json`: the stats block, when the site shows it to this reader. */
    fun loadSummary(
        container: AppContainer,
        username: String,
        onLoaded: (UserSummary) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            extras(container).loadSummary(username)
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** `/user-badges/{username}.json`: the badges the site granted the reader. */
    fun loadBadges(
        container: AppContainer,
        username: String,
        onLoaded: (List<UserBadge>) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            extras(container).loadBadges(username)
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** One page of the reader's own bookmarks (`/u/{username}/bookmarks.json`). */
    fun loadBookmarks(
        container: AppContainer,
        username: String,
        page: Int,
        onLoaded: (List<Bookmark>) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.bookmarkRepository.load(username, page).getOrThrow() }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** Follows or unfollows with the discourse-follow plugin's routes. */
    fun setFollow(
        container: AppContainer,
        username: String,
        follow: Boolean,
        onDone: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            extras(container).setFollow(username, follow)
                .onSuccess { onDone() }
                .onFailure(onFailed)
        }
    }

    /** Mutes or unmutes: `PUT /u/{username}/notification_level.json`. */
    fun setMuted(
        container: AppContainer,
        username: String,
        muted: Boolean,
        onDone: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            extras(container).setMuted(username, muted)
                .onSuccess { onDone() }
                .onFailure(onFailed)
        }
    }
}

package io.github.baba983.nativedo.bookmarks

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.Bookmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the bookmarks repository to the Java fragment, the way
 * [io.github.baba983.nativedo.topic.DiscourseLoader] bridges the topic screens: a suspend call made
 * from Java needs a continuation by hand, so the launch lives here and reports back through
 * callbacks.
 */
object BookmarksLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun page(
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
}

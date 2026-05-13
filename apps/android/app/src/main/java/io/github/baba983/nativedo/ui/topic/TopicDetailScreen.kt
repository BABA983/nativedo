package io.github.baba983.nativedo.ui.topic

import android.graphics.Bitmap
import android.graphics.RectF
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SvgSpriteStore
import io.github.baba983.nativedo.core.ui.GalleryImage
import io.github.baba983.nativedo.feature.topic.ui.TopicDetailContent
import io.github.baba983.nativedo.feature.topic.ui.TopicViewModel

@Composable
fun TopicDetailScreen(
    viewModel: TopicViewModel,
    topicId: Long,
    baseUrl: String,
    cookieStore: CookieStore,
    discourseApiClient: DiscourseApiClient,
    svgSpriteStore: SvgSpriteStore,
    hiddenImageUrl: String?,
    onTitleChanged: (String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
    onImageBoundsChanged: (String, RectF?) -> Unit,
    onImageViewChanged: (String, View?) -> Unit,
    onOpenImage: (String, List<GalleryImage>, RectF?, Bitmap?, View?) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val svgSpriteMarkup by svgSpriteStore.sprite.collectAsState()

    TopicDetailContent(
        topicId = topicId,
        uiState = uiState,
        baseUrl = baseUrl,
        cookieStore = cookieStore,
        svgSpriteMarkup = svgSpriteMarkup,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onPostUpdated = viewModel::replacePost,
        fetchPostByNumber = { sourceTopicId, sourcePostNumber ->
            discourseApiClient.fetchPostByNumber(sourceTopicId, sourcePostNumber).cookedHtml
        },
        fetchReactionUsers = discourseApiClient::fetchReactionUsers,
        toggleReaction = discourseApiClient::toggleReaction,
        onCollapsedTitleChanged = onTitleChanged,
        onTopicClick = onTopicClick,
        onOpenLink = onOpenLink,
        hiddenImageUrl = hiddenImageUrl,
        onImageBoundsChanged = onImageBoundsChanged,
        onImageViewChanged = onImageViewChanged,
        onOpenImage = onOpenImage,
    )
}

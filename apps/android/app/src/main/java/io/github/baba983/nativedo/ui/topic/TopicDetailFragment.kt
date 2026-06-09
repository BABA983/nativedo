package io.github.baba983.nativedo.ui.topic

import android.animation.ValueAnimator
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.ui.TopicReplySheetItem
import io.github.baba983.nativedo.feature.topic.ui.TopicReplyTargetResolver
import io.github.baba983.nativedo.feature.topic.ui.TopicReactionUsersUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicViewModel
import io.github.baba983.nativedo.feature.topic.ui.topicPostDisplayName
import io.github.baba983.nativedo.feature.topic.ui.topicPostTimeLabel
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.cell.TopicFooterCell
import io.github.baba983.nativedo.ui.topic.cell.TopicHeaderCell
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCellDelegate
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessageObject
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.PhotoViewer
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BottomSheetWithRecyclerListView
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.PullForegroundDrawable
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import java.net.URLEncoder

class TopicDetailFragment(
    private val topicId: Long,
    private val initialTitle: String? = null,
) : BaseFragment() {
    private lateinit var viewModel: TopicViewModel
    private lateinit var adapter: TopicDetailAdapter
    private lateinit var listView: RecyclerListView
    private lateinit var pullRefreshLayout: PullRefreshLayout
    private lateinit var baseUrl: String
    private val collectorScope = MainScope()
    private var reactionUsersSheet: TopicReactionUsersSheet? = null
    private var reactionPickerSheet: TopicReactionPickerSheet? = null
    private var repliesSheet: TopicRepliesSheet? = null
    private var replySheetEntriesByPostNumber: Map<Int, List<TopicReplySheetItem>> = emptyMap()
    private var validReactionIds: List<String> = emptyList()
    private var emojiCatalog: Map<String, String> = emptyMap()
    private var topicTitle: String = initialTitle.orEmpty()
    private var collapsedTitleVisible: Boolean = false
    private val textSelectionHelper = TextSelectionHelper.ArticleTextSelectionHelper()

    override fun createView(context: Context): View {
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setTitle(initialTitle ?: "")
        actionBar.setCastShadows(false)

        val appContainer = (context.applicationContext as NativeDoApp).appContainer
        baseUrl = appContainer.baseUrl
        viewModel = ViewModelProvider(
            this,
            TopicViewModelFactory(
                repository = TopicRepository(appContainer.discourseApiClient),
                topicId = topicId,
            ),
        )[TopicViewModel::class.java]

        val root = SizeNotifierFrameLayout(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        textSelectionHelper.setParentView(root)
        textSelectionHelper.setOnTranslate { text, _, toLang, onAlertDismiss ->
            parentActivity?.let { activity ->
                val encoded = URLEncoder.encode(text.toString(), Charsets.UTF_8.name())
                val targetLanguage = toLang?.takeIf { it.isNotBlank() } ?: "zh-CN"
                Browser.openUrl(activity, "https://translate.google.com/?sl=auto&tl=$targetLanguage&op=translate&text=$encoded")
            }
            onAlertDismiss.run()
        }
        textSelectionHelper.setOnQuote { text ->
            AndroidUtilities.addToClipboard(text.lines().joinToString("\n") { line -> "> $line" })
        }
        root.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        adapter = TopicDetailAdapter(context)
        listView = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            clipToPadding = false
            setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(24f))
            adapter = this@TopicDetailFragment.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    textSelectionHelper.onParentScrolled()
                    updateCollapsedTitle()
                    if (dy <= 0) return
                    val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (manager.findLastVisibleItemPosition() >= manager.itemCount - 4) {
                        viewModel.loadMore()
                    }
                }
            })
        }
        textSelectionHelper.setRecyclerView(listView)
        pullRefreshLayout = PullRefreshLayout(
            context = context,
            listView = listView,
            onRefresh = viewModel::refresh,
        )
        root.addView(
            pullRefreshLayout,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP, 0f, 0f, 0f, 0f),
        )
        root.addView(
            textSelectionHelper.getOverlayView(context),
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP, 0f, 0f, 0f, 0f),
        )
        actionBar.bringToFront()

        collectorScope.launch {
            viewModel.uiState.collectLatest { state ->
                topicTitle = state.title.ifBlank { initialTitle.orEmpty() }
                pullRefreshLayout.isBusy = state.isLoading || state.isRefreshing
                adapter.submit(state)
                updateCollapsedTitle()
                updateReactionUsersSheet(context, state)
                handleJumpEvent(state)
            }
        }

        fragmentView = root
        return root
    }

    override fun onFragmentDestroy() {
        textSelectionHelper.destroy()
        collectorScope.cancel()
        PhotoViewer.getInstance().closePhoto(false, false)
        reactionUsersSheet?.dismiss()
        reactionUsersSheet = null
        reactionPickerSheet?.dismiss()
        reactionPickerSheet = null
        repliesSheet?.dismiss()
        repliesSheet = null
        super.onFragmentDestroy()
    }

    override fun onBackPressed(): Boolean {
        return super.onBackPressed()
    }

    private fun updateCollapsedTitle() {
        val manager = listView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = manager.findFirstVisibleItemPosition()
        val firstPostView = manager.findViewByPosition(1)
        val shouldShow = firstVisible > 1 || (firstPostView != null && firstPostView.bottom <= actionBar.measuredHeight)
        if (collapsedTitleVisible != shouldShow) {
            collapsedTitleVisible = shouldShow
            actionBar.setTitle(if (shouldShow) topicTitle else "")
        } else if (shouldShow) {
            actionBar.setTitle(topicTitle)
        }
    }

    private fun openImageViewer(
        post: Post,
        url: String,
        galleryImages: List<GalleryImage>,
        tappedSourceView: View?,
        sourceBoundsProvider: (String) -> RectF?,
        sourceImageReceiverProvider: (String) -> ImageReceiver?,
        sourceBitmapProvider: (String) -> ImageReceiver.BitmapHolder?,
        sourceRadiusProvider: (String) -> IntArray?,
    ) {
        val urls = galleryImages.map { it.url }.ifEmpty { listOf(url) }
        val sourceCell = tappedSourceView as? TopicPostCell
        val setHiddenImageUrl: (String?) -> Unit = { hiddenUrl -> sourceCell?.setHiddenImageUrl(hiddenUrl) }
        val viewer = PhotoViewer.getInstance()
        viewer.setParentActivity(this)
        val provider = object : PhotoViewer.EmptyPhotoViewerProvider() {
            private fun sourceBitmap(imageUrl: String): ImageReceiver.BitmapHolder? {
                return sourceCell?.imageBitmapForUrl(imageUrl) ?: sourceBitmapProvider(imageUrl)
            }

            override fun getPlaceForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                index: Int,
                needPreview: Boolean,
                closing: Boolean,
            ): PhotoViewer.PlaceProviderObject? {
                val imageUrl = urls.getOrNull(index) ?: return null
                val cellPlace = sourceCell?.getPlaceForPhoto(imageUrl, needPreview, closing)
                if (cellPlace != null) {
                    return cellPlace
                }
                val bounds = sourceBoundsProvider(imageUrl) ?: return null
                val imageReceiver = sourceImageReceiverProvider(imageUrl)
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = (bounds.left - (imageReceiver?.imageX ?: 0f)).toInt()
                    viewY = (bounds.top - (imageReceiver?.imageY ?: 0f)).toInt()
                    viewWidth = (imageReceiver?.imageWidth ?: bounds.width()).toInt()
                    viewHeight = (imageReceiver?.imageHeight ?: bounds.height()).toInt()
                    parentView = tappedSourceView ?: getPhotoViewerScaleView()
                    this.imageReceiver = imageReceiver
                    thumb = if (needPreview) sourceBitmapProvider(imageUrl) else null
                    radius = if (needPreview) sourceRadiusProvider(imageUrl) else null
                }
            }

            override fun getThumbForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                index: Int,
            ): ImageReceiver.BitmapHolder? {
                val imageUrl = urls.getOrNull(index) ?: return null
                return sourceBitmap(imageUrl)
            }

            override fun willSwitchFromPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                index: Int,
            ) {
                setHiddenImageUrl(null)
            }

            override fun onPhotoChanged(photoIndex: Int) {
                setHiddenImageUrl(urls.getOrNull(photoIndex))
            }

            override fun onClose() {
                setHiddenImageUrl(null)
            }
        }
        var selectedIndex = 0
        val viewerTitle = topicPostDisplayName(post.authorDisplayName, post.authorUsername).ifBlank { topicTitle }
        val viewerSubtitle = topicPostTimeLabel(post.createdAt)
        val opened = if (galleryImages.isNotEmpty()) {
            val items = galleryImages.map { image ->
                PhotoViewer.UrlPhotoItem(image.url, image.thumbUrl, image.widthPx, image.heightPx, image.caption, viewerTitle, viewerSubtitle)
            }
            selectedIndex = items.indexOfFirst { it.url == url }.coerceAtLeast(0)
            viewer.openPhotoItems(items, selectedIndex, provider)
        } else {
            val items = urls.map { imageUrl ->
                PhotoViewer.UrlPhotoItem(imageUrl, null, 0, 0, null, viewerTitle, viewerSubtitle)
            }
            selectedIndex = items.indexOfFirst { it.url == url }.coerceAtLeast(0)
            viewer.openPhotoItems(items, selectedIndex, provider)
        }
        if (!opened) {
            setHiddenImageUrl(null)
        } else {
            setHiddenImageUrl(urls.getOrNull(selectedIndex))
        }
    }

    private fun updateReactionUsersSheet(context: Context, state: TopicUiState) {
        val sheetState = state.reactionSheet
        if (sheetState == null) {
            reactionUsersSheet?.dismiss()
            reactionUsersSheet = null
            return
        }
        val sheet = reactionUsersSheet
        if (sheet == null) {
            val created = TopicReactionUsersSheet(
                context = context,
                fragment = this,
                baseUrl = baseUrl,
                emojiCatalog = emojiCatalog,
                onReactionSelected = viewModel::selectReactionInSheet,
                onDismissed = viewModel::dismissReactionSheet,
            )
            reactionUsersSheet = created
            created.submit(sheetState.reactions, state.reactionUsers)
            created.show()
        } else {
            sheet.submit(sheetState.reactions, state.reactionUsers)
        }
    }

    private fun showRepliesSheet(sourcePost: Post, replies: List<TopicReplySheetItem>) {
        if (replies.isEmpty()) return
        val context = fragmentView?.context ?: return
        repliesSheet?.dismiss()
        repliesSheet = TopicRepliesSheet(
            context = context,
            fragment = this,
            baseUrl = baseUrl,
            sourcePost = sourcePost,
            replies = replies,
            onReplySelected = { reply ->
                repliesSheet?.dismiss()
                repliesSheet = null
                reply.postNumber?.let(viewModel::requestJumpToReplyTarget)
            },
            onDismissed = { repliesSheet = null },
        ).also { sheet ->
            sheet.show()
        }
    }

    private fun showReactionPickerSheet(post: Post, reactions: List<PostReaction>, initialReactionId: String?) {
        val context = fragmentView?.context ?: return
        val currentIds = reactions.map { it.id }
        val ids = buildList {
            initialReactionId?.let { add(it) }
            addAll(validReactionIds)
            addAll(currentIds)
            if (isEmpty()) addAll(DEFAULT_REACTION_IDS)
        }.distinct()
        if (ids.isEmpty()) return
        reactionPickerSheet?.dismiss()
        reactionPickerSheet = TopicReactionPickerSheet(
            context = context,
            fragment = this,
            reactionIds = ids,
            selectedReactionId = post.currentUserReaction ?: initialReactionId,
            emojiCatalog = emojiCatalog,
            baseUrl = baseUrl,
            onReactionSelected = { reactionId ->
                reactionPickerSheet?.dismiss()
                reactionPickerSheet = null
                viewModel.onReactionSelected(post.id, reactionId)
            },
            onDismissed = { reactionPickerSheet = null },
        ).also { sheet -> sheet.show() }
    }

    private fun handleJumpEvent(state: TopicUiState) {
        val event = state.jumpEvent ?: return
        val manager = listView.layoutManager as? LinearLayoutManager ?: return
        manager.scrollToPositionWithOffset(event.itemIndex, AndroidUtilities.dp(8f))
        viewModel.consumeJumpEvent(event.nonce)
    }

    override fun getNavigationBarColor(): Int {
        return Theme.getColor(Theme.key_windowBackgroundGray)
    }

    override fun isSupportEdgeToEdge(): Boolean {
        return true
    }

    private sealed interface TopicDetailRow {
        val stableId: Long

        data object InitialSkeleton : TopicDetailRow {
            override val stableId: Long = -1L
        }

        data class InitialError(val text: String) : TopicDetailRow {
            override val stableId: Long = -2L
        }

        data object Empty : TopicDetailRow {
            override val stableId: Long = -3L
        }

        data class Header(
            val title: String,
            val totalPosts: Int,
            val viewCount: Int,
            val tags: List<String>,
        ) : TopicDetailRow {
            override val stableId: Long = -4L
        }

        data class PostRow(
            val post: Post,
            val replyCount: Int,
            val replyParticipants: List<Post>,
            val jumpHighlightNonce: Int,
        ) : TopicDetailRow {
            override val stableId: Long = post.id
        }

        data object AppendLoading : TopicDetailRow {
            override val stableId: Long = -5L
        }

        data class AppendError(val text: String) : TopicDetailRow {
            override val stableId: Long = -6L
        }

        data object EndOfList : TopicDetailRow {
            override val stableId: Long = -7L
        }
    }

    private data class PostDiffKey(
        val id: Long,
        val updatedAt: String,
        val editCount: Int,
        val cookedHtmlHash: Int,
        val currentUserReaction: String?,
        val reactionUsersCount: Int,
        val reactionsSignature: Int,
        val authorAvatarTemplate: String,
        val authorTitle: String,
    )

    private data class ParsedBodyCacheKey(
        val postId: Long,
        val htmlHash: Int,
    )

    private data class BodyStateCacheKey(
        val postId: Long,
        val htmlHash: Int,
        val editCount: Int,
    )

    private class ParsedBodyCache(
        private val maxSize: Int = 64,
    ) : LinkedHashMap<ParsedBodyCacheKey, List<CookedPostBlock>>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ParsedBodyCacheKey, List<CookedPostBlock>>): Boolean {
            return size > maxSize
        }
    }

    private class BodyStateCache(
        private val maxSize: Int = 64,
    ) : LinkedHashMap<BodyStateCacheKey, PostBodyBlockState>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BodyStateCacheKey, PostBodyBlockState>): Boolean {
            return size > maxSize
        }
    }

    private inner class TopicDetailAdapter(
        private val context: Context,
    ) : RecyclerListView.SelectionAdapter() {
        private val parsedBodyCache = ParsedBodyCache()
        private val bodyStateCache = BodyStateCache()
        private var rows: List<TopicDetailRow> = emptyList()
        private var localPostsByNumber: Map<Int, String> = emptyMap()

        init {
            setHasStableIds(true)
        }

        fun submit(state: TopicUiState) {
            validReactionIds = state.validReactions
            emojiCatalog = state.emojiCatalog
            replySheetEntriesByPostNumber = TopicReplyTargetResolver.replySheetEntriesByPostNumber(state.posts)
            localPostsByNumber = state.posts.mapNotNull { post ->
                post.postNumber?.let { number -> number to post.cookedHtml }
            }.toMap()
            val oldRows = rows
            val newRows = buildRows(state)
            val diff = DiffUtil.calculateDiff(TopicRowsDiffCallback(oldRows, newRows), false)
            rows = newRows
            diff.dispatchUpdatesTo(this)
        }

        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
            return when (rows.getOrNull(holder.bindingAdapterPosition)) {
                is TopicDetailRow.PostRow,
                is TopicDetailRow.InitialError,
                is TopicDetailRow.AppendError -> true
                else -> false
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemId(position: Int): Long = rows[position].stableId

        override fun getItemViewType(position: Int): Int {
            return when (rows[position]) {
                TopicDetailRow.InitialSkeleton -> VIEW_TYPE_SKELETON
                TopicDetailRow.AppendLoading -> VIEW_TYPE_SKELETON
                is TopicDetailRow.InitialError,
                TopicDetailRow.Empty,
                is TopicDetailRow.AppendError,
                TopicDetailRow.EndOfList -> VIEW_TYPE_FOOTER
                is TopicDetailRow.Header -> VIEW_TYPE_HEADER
                is TopicDetailRow.PostRow -> VIEW_TYPE_POST
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = when (viewType) {
                VIEW_TYPE_POST -> TopicPostCell(context).apply {
                    delegate = postCellDelegate
                    textSelectionHelper = this@TopicDetailFragment.textSelectionHelper
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                VIEW_TYPE_SKELETON -> TopicSkeletonView(context)
                VIEW_TYPE_LOADING -> LoadingCell(context)
                VIEW_TYPE_FOOTER -> TopicFooterCell(context)
                else -> TopicHeaderCell(context)
            }
            return RecyclerListView.Holder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                TopicDetailRow.InitialSkeleton -> (holder.itemView as TopicSkeletonView).setRows(3)
                TopicDetailRow.AppendLoading -> (holder.itemView as TopicSkeletonView).setRows(1)
                is TopicDetailRow.InitialError -> (holder.itemView as TopicFooterCell).bind(row.text, viewModel::refresh)
                TopicDetailRow.Empty -> (holder.itemView as TopicFooterCell).bind("No posts", null)
                is TopicDetailRow.AppendError -> (holder.itemView as TopicFooterCell).bind(row.text, viewModel::loadMore)
                TopicDetailRow.EndOfList -> (holder.itemView as TopicFooterCell).bind("End of topic", null)
                is TopicDetailRow.Header -> (holder.itemView as TopicHeaderCell).bind(row.title, row.totalPosts, row.viewCount, row.tags)
                is TopicDetailRow.PostRow -> {
                    val post = row.post
                    val htmlHash = post.cookedHtml.hashCode()
                    val parsedBlocks = parsedBodyCache.getOrPut(ParsedBodyCacheKey(post.id, htmlHash)) {
                        parseCookedPostBody(post.cookedHtml.ifBlank { "<p>（空内容）</p>" }, baseUrl)
                    }
                    val blockState = bodyStateCache.getOrPut(BodyStateCacheKey(post.id, htmlHash, post.editCount)) {
                        PostBodyBlockState()
                    }
                    (holder.itemView as TopicPostCell).setPost(
                        post,
                        rows.take(position).lastOrNull { it is TopicDetailRow.PostRow }?.let { (it as TopicDetailRow.PostRow).post },
                        rows.drop(position + 1).firstOrNull { it is TopicDetailRow.PostRow }?.let { (it as TopicDetailRow.PostRow).post },
                        rows.take(position).none { it is TopicDetailRow.PostRow },
                        rows.drop(position + 1).none { it is TopicDetailRow.PostRow },
                        topicId,
                        baseUrl,
                        localPostsByNumber,
                        emojiCatalog,
                        row.replyCount,
                        row.replyParticipants,
                        parsedBlocks,
                        blockState,
                        row.jumpHighlightNonce,
                        { viewModel.clearJumpHighlight(row.jumpHighlightNonce) },
                    )
                }
            }
        }

        private fun buildRows(state: TopicUiState): List<TopicDetailRow> {
            if (state.isLoading && state.posts.isEmpty()) {
                return listOf(TopicDetailRow.InitialSkeleton)
            }
            if (state.errorMessage != null && state.posts.isEmpty()) {
                return listOf(TopicDetailRow.InitialError(state.errorMessage))
            }
            if (state.posts.isEmpty()) {
                return listOf(TopicDetailRow.Empty)
            }
            val rows = mutableListOf<TopicDetailRow>()
            rows += TopicDetailRow.Header(
                title = state.title,
                totalPosts = state.totalPosts,
                viewCount = state.viewCount,
                tags = state.tags,
            )
            val repliesByNumber = state.posts.groupBy { post -> post.replyToPostNumber }
            state.posts.forEach { post ->
                val replies = post.postNumber?.let { repliesByNumber[it] }.orEmpty()
                rows += TopicDetailRow.PostRow(
                    post = post,
                    replyCount = replies.size,
                    replyParticipants = replies,
                    jumpHighlightNonce = state.jumpHighlight
                        ?.takeIf { highlight -> highlight.postId == post.id }
                        ?.nonce
                        ?: 0,
                )
            }
            if (state.isLoadingMore) {
                rows += TopicDetailRow.AppendLoading
            } else if (state.loadMoreErrorMessage != null) {
                rows += TopicDetailRow.AppendError(state.loadMoreErrorMessage)
            } else if (!state.hasMore) {
                rows += TopicDetailRow.EndOfList
            }
            return rows
        }
    }

    private class TopicRowsDiffCallback(
        private val oldRows: List<TopicDetailRow>,
        private val newRows: List<TopicDetailRow>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldRows.size

        override fun getNewListSize(): Int = newRows.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldRows[oldItemPosition].stableId == newRows[newItemPosition].stableId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return contentKey(oldRows[oldItemPosition]) == contentKey(newRows[newItemPosition])
        }

        private fun contentKey(row: TopicDetailRow): Any {
            return when (row) {
                TopicDetailRow.InitialSkeleton,
                TopicDetailRow.Empty,
                TopicDetailRow.AppendLoading,
                TopicDetailRow.EndOfList -> row
                is TopicDetailRow.InitialError -> row.text
                is TopicDetailRow.AppendError -> row.text
                is TopicDetailRow.Header -> row
                is TopicDetailRow.PostRow -> listOf(
                    postDiffKey(row.post),
                    row.replyCount,
                    row.replyParticipants.map { reply ->
                        listOf(reply.id, reply.authorUsername, reply.authorDisplayName, reply.authorAvatarTemplate, reply.authorAnimatedAvatarUrl)
                    },
                    row.jumpHighlightNonce,
                )
            }
        }

        private fun postDiffKey(post: Post): PostDiffKey {
            return PostDiffKey(
                id = post.id,
                updatedAt = post.updatedAt,
                editCount = post.editCount,
                cookedHtmlHash = post.cookedHtml.hashCode(),
                currentUserReaction = post.currentUserReaction,
                reactionUsersCount = post.reactionUsersCount,
                reactionsSignature = post.reactions.joinToString(separator = "|") { reaction ->
                    "${reaction.id}:${reaction.count}:${reaction.iconUrl}:${reaction.reacted}"
                }.hashCode(),
                authorAvatarTemplate = post.authorAvatarTemplate,
                authorTitle = post.authorTitle,
            )
        }
    }

    private val postCellDelegate = object : TopicPostCellDelegate {
        override fun didPressLink(url: String) {
            Browser.openUrl(parentActivity ?: return, url)
        }

        override fun didPressUser(username: String) {
            Browser.openUrl(parentActivity ?: return, "$baseUrl/u/$username")
        }

        override fun didPressTopic(topicId: Long) {
            presentFragment(TopicDetailFragment(topicId))
        }

        override fun didLongPressLink(url: String) {
            AndroidUtilities.addToClipboard(url)
        }

        override fun didPressReaction(post: Post, reaction: PostReaction) {
            viewModel.openReactionSheet(post.id, post.reactions, reaction.id)
        }

        override fun didPressMoreReactions(post: Post, reactions: List<PostReaction>, initialReactionId: String?) {
            showReactionPickerSheet(post, reactions, initialReactionId)
        }

        override fun didPressReply(post: Post) {
            val postNumber = post.postNumber ?: return
            val replies = replySheetEntriesByPostNumber[postNumber].orEmpty()
            if (replies.isEmpty()) return
            showRepliesSheet(post, replies)
        }

        override fun didExpandQuote(topicId: Long, postNumber: Int, onResult: (String?) -> Unit) {
            collectorScope.launch {
                val html = viewModel.expandQuote(topicId, postNumber).getOrNull()
                onResult(html)
            }
        }

        override fun didPressPollOption(post: Post, pollName: String, optionId: String) {
            viewModel.onPollOptionSelected(post.id, post.postNumber, pollName, optionId)
        }

        override fun didPressImage(
            post: Post,
            url: String,
            gallery: List<GalleryImage>,
            sourceBoundsInWindow: RectF?,
            hostView: View,
            sourceBoundsProvider: (String) -> RectF?,
            sourceImageReceiverProvider: (String) -> ImageReceiver?,
            sourceBitmapProvider: (String) -> ImageReceiver.BitmapHolder?,
            sourceRadiusProvider: (String) -> IntArray?,
        ) {
            openImageViewer(
                post = post,
                url = url,
                galleryImages = gallery,
                tappedSourceView = hostView,
                sourceBoundsProvider = { imageUrl -> sourceBoundsProvider(imageUrl) ?: if (imageUrl == url) sourceBoundsInWindow else null },
                sourceImageReceiverProvider = sourceImageReceiverProvider,
                sourceBitmapProvider = sourceBitmapProvider,
                sourceRadiusProvider = sourceRadiusProvider,
            )
        }
    }

    private class TopicSkeletonView(context: Context) : FlickerLoadingView(context) {
        private val rect = RectF()
        private val horizontalPadding = AndroidUtilities.dp(18f)
        private val verticalPadding = AndroidUtilities.dp(12f)
        private val avatarSize = AndroidUtilities.dp(42f)
        private val avatarGap = AndroidUtilities.dp(10f)
        private val blockSpacing = AndroidUtilities.dp(10f)
        private val rowHeight = AndroidUtilities.dp(236f)
        private var rows = 3

        init {
            setWillNotDraw(false)
            setIsSingleCell(false)
        }

        fun setRows(count: Int) {
            rows = count.coerceAtLeast(1)
            requestLayout()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, rowHeight * rows)
        }

        override fun onDraw(canvas: Canvas) {
            updateColors()
            updateGradient()
            val paint = getPaint()
            var top = 0
            repeat(rows) {
                drawPostSkeleton(canvas, top)
                top += rowHeight
            }
            invalidate()
        }

        private fun drawPostSkeleton(canvas: Canvas, top: Int) {
            val headerLeft = horizontalPadding + avatarSize + avatarGap
            val headerTop = top + verticalPadding
            canvas.drawCircle(
                horizontalPadding + avatarSize / 2f,
                headerTop + avatarSize / 2f,
                avatarSize / 2f,
                paint,
            )
            drawRound(canvas, headerLeft, headerTop + AndroidUtilities.dp(4f), headerLeft + AndroidUtilities.dp(122f), headerTop + AndroidUtilities.dp(14f), 5f)
            drawRound(canvas, headerLeft, headerTop + AndroidUtilities.dp(26f), headerLeft + AndroidUtilities.dp(238f), headerTop + AndroidUtilities.dp(34f), 4f)

            var y = top + verticalPadding + avatarSize + AndroidUtilities.dp(20f)
            val contentRight = width - horizontalPadding
            drawRound(canvas, horizontalPadding, y, contentRight, y + AndroidUtilities.dp(11f), 4f)
            y += AndroidUtilities.dp(22f)
            drawRound(canvas, horizontalPadding, y, contentRight - AndroidUtilities.dp(32f), y + AndroidUtilities.dp(11f), 4f)
            y += AndroidUtilities.dp(22f)
            drawRound(canvas, horizontalPadding, y, contentRight - AndroidUtilities.dp(86f), y + AndroidUtilities.dp(11f), 4f)
            y += AndroidUtilities.dp(28f)
            drawRound(canvas, horizontalPadding, y, contentRight, y + AndroidUtilities.dp(52f), 6f)
            y += AndroidUtilities.dp(52f) + blockSpacing
            drawRound(canvas, horizontalPadding, y, horizontalPadding + AndroidUtilities.dp(92f), y + AndroidUtilities.dp(26f), 13f)
            drawRound(canvas, horizontalPadding + AndroidUtilities.dp(104f), y, horizontalPadding + AndroidUtilities.dp(158f), y + AndroidUtilities.dp(26f), 13f)
        }

        private fun drawRound(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int, radiusDp: Float) {
            rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            canvas.drawRoundRect(rect, AndroidUtilities.dp(radiusDp).toFloat(), AndroidUtilities.dp(radiusDp).toFloat(), getPaint())
        }
    }

    private class TopicReactionUsersSheet(
        context: Context,
        private val fragment: BaseFragment,
        private val baseUrl: String,
        private val emojiCatalog: Map<String, String>,
        private val onReactionSelected: (String) -> Unit,
        private val onDismissed: () -> Unit,
    ) : BottomSheet(context, false, null) {
        private val adapter = Adapter()
        private val recyclerListView = RecyclerListView(context)
        private val fixedHeaderView = ReactionFixedHeaderView(context, baseUrl, emojiCatalog, onReactionSelected, backgroundPaddingLeft)
        private val rect = RectF()
        private var scrollOffsetY = 0
        private var ignoreLayout = false
        private var fullHeight = false
        private var contentRowsHeight = AndroidUtilities.dp(280f)
        private var statusBarOpen: Boolean? = null

        init {
            setUseLightStatusBar(fragment.isLightStatusBar)
            containerView = object : FrameLayout(context) {
                override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                    if (ev.action == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.y < scrollOffsetY) {
                        dismiss()
                        return true
                    }
                    return super.onInterceptTouchEvent(ev)
                }

                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val height = MeasureSpec.getSize(heightMeasureSpec)
                    ignoreLayout = true
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0)
                    ignoreLayout = false
                    fixedHeaderView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                    val contentSize = fixedHeaderView.measuredHeight + contentRowsHeight + backgroundPaddingTop + AndroidUtilities.statusBarHeight
                    val recyclerParams = recyclerListView.layoutParams as? FrameLayout.LayoutParams
                    if (recyclerParams != null && recyclerParams.topMargin != fixedHeaderView.measuredHeight) {
                        recyclerParams.topMargin = fixedHeaderView.measuredHeight
                        recyclerListView.layoutParams = recyclerParams
                    }
                    var padding = if (contentSize < height / 5f * 3.2f) 0 else (height / 5f * 2).toInt()
                    if (padding != 0 && contentSize < height) {
                        padding -= height - contentSize
                    }
                    if (padding == 0) {
                        padding = backgroundPaddingTop
                    }
                    if (recyclerListView.paddingTop != padding) {
                        ignoreLayout = true
                        recyclerListView.setPadding(0, padding, 0, AndroidUtilities.dp(8f))
                        ignoreLayout = false
                    }
                    fullHeight = contentSize >= height
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(minOf(contentSize, height), MeasureSpec.EXACTLY))
                }

                override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                    super.onLayout(changed, left, top, right, bottom)
                    updateLayout()
                }

                override fun requestLayout() {
                    if (ignoreLayout) return
                    super.requestLayout()
                }

                override fun onDraw(canvas: Canvas) {
                    var y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6f)
                    var top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13f)
                    var statusBarHeight = 0
                    var radProgress = 1f
                    top += AndroidUtilities.statusBarHeight
                    y += AndroidUtilities.statusBarHeight
                    if (fullHeight) {
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
                            val diff = minOf(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop)
                            top -= diff
                            radProgress = 1f - minOf(1f, diff * 2f / AndroidUtilities.statusBarHeight)
                        }
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                            statusBarHeight = minOf(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop)
                        }
                    }
                    shadowDrawable.setBounds(0, top, measuredWidth, measuredHeight)
                    shadowDrawable.draw(canvas)
                    if (radProgress != 1f) {
                        Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_dialogBackground)
                        rect.set(backgroundPaddingLeft.toFloat(), (backgroundPaddingTop + top).toFloat(), (measuredWidth - backgroundPaddingLeft).toFloat(), (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat())
                        val radius = AndroidUtilities.dp(12f) * radProgress
                        canvas.drawRoundRect(rect, radius, radius, Theme.dialogs_onlineCirclePaint)
                    }
                    val handleAlpha = maxOf(0f, minOf(1f, (y - AndroidUtilities.statusBarHeight).toFloat() / AndroidUtilities.dp(16f)))
                    if (handleAlpha > 0f) {
                        val width = AndroidUtilities.dp(36f)
                        rect.set((measuredWidth - width) / 2f, y.toFloat(), (measuredWidth + width) / 2f, (y + AndroidUtilities.dp(4f)).toFloat())
                        Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_sheet_scrollUp)
                        Theme.dialogs_onlineCirclePaint.alpha = (Theme.dialogs_onlineCirclePaint.alpha * handleAlpha).toInt()
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.dialogs_onlineCirclePaint)
                    }
                    if (statusBarHeight > 0) {
                        Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_dialogBackground)
                        canvas.drawRect(backgroundPaddingLeft.toFloat(), (AndroidUtilities.statusBarHeight - statusBarHeight).toFloat(), (measuredWidth - backgroundPaddingLeft).toFloat(), AndroidUtilities.statusBarHeight.toFloat(), Theme.dialogs_onlineCirclePaint)
                    }
                    updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2)
                }
            }
            containerView.setWillNotDraw(false)
            containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)
            fixNavigationBar(Theme.getColor(Theme.key_dialogBackground))
            adapter.configure(baseUrl)
            adapter.setTopSpacerHeight(0)
            setOnDismissListener(DialogInterface.OnDismissListener { onDismissed() })
            recyclerListView.itemAnimator = null
            recyclerListView.clipToPadding = false
            recyclerListView.layoutManager = LinearLayoutManager(context)
            recyclerListView.adapter = adapter
            recyclerListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateLayout()
                }
            })
            containerView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))
            containerView.addView(fixedHeaderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT))
            fixedHeaderView.bringToFront()
        }

        fun submit(reactions: List<PostReaction>, usersState: TopicReactionUsersUiState) {
            val tabReactions = buildList {
                addAll(reactions)
                usersState.groups.forEach { group ->
                    if (none { reaction -> reaction.id == group.id }) {
                        add(PostReaction(group.id, group.count.takeIf { it > 0 } ?: group.users.size, iconUrl = emojiCatalog[group.id]))
                    }
                }
            }
            val selected = usersState.selectedReactionId ?: usersState.groups.firstOrNull()?.id ?: tabReactions.firstOrNull()?.id
            val selectedGroup = usersState.groups.firstOrNull { group -> group.id == selected }
            val visibleSelected = selectedGroup?.id
            val selectedUsers = selectedGroup?.users ?: usersState.groups
                .flatMap { group -> group.users }
                .distinctBy { user -> user.username }
            fixedHeaderView.bind(tabReactions, visibleSelected)
            contentRowsHeight = when {
                usersState.isLoading -> AndroidUtilities.dp(280f)
                selectedUsers.isEmpty() -> AndroidUtilities.dp(60f)
                else -> selectedUsers.size * AndroidUtilities.dp(56f)
            }
            adapter.submit(usersState, selectedUsers)
            containerView.requestLayout()
        }

        override fun canDismissWithSwipe(): Boolean {
            return false
        }

        private fun updateLightStatusBar(open: Boolean) {
            if (statusBarOpen == open) return
            val openBgLight = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_dialogBackground)) > 0.721f
            statusBarOpen = open
            AndroidUtilities.setLightStatusBar(window, if (open) openBgLight else fragment.isLightStatusBar)
        }

        private fun updateLayout() {
            if (recyclerListView.childCount <= 0) {
                setScrollOffsetY(recyclerListView.paddingTop)
                return
            }
            var firstChild: View? = null
            var firstChildPosition = -1
            for (index in 0 until recyclerListView.childCount) {
                val child = recyclerListView.getChildAt(index)
                val position = recyclerListView.getChildAdapterPosition(child)
                if (firstChildPosition == -1 || firstChildPosition > position) {
                    firstChild = child
                    firstChildPosition = position
                }
            }
            val newOffset = if (firstChild != null && firstChild.top >= 0) firstChild.top else 0
            fixedHeaderView.setShadowVisible(newOffset == 0 && recyclerListView.canScrollVertically(-1))
            if (scrollOffsetY != newOffset) {
                setScrollOffsetY(newOffset)
            }
        }

        private fun setScrollOffsetY(newOffset: Int) {
            scrollOffsetY = newOffset
            fixedHeaderView.translationY = newOffset.toFloat()
            fixedHeaderView.bringToFront()
            containerView.invalidate()
        }

        private sealed interface Row {
            data class TopSpacer(val height: Int) : Row
            data object Loading : Row
            data object Empty : Row
            data class User(val user: PostReactionUser, val divider: Boolean) : Row
        }

        private class Adapter : RecyclerListView.SelectionAdapter() {
            private var rows: List<Row> = emptyList()
            private var baseUrl: String = ""
            private var topSpacerHeight: Int = AndroidUtilities.dp(140f)

            fun configure(baseUrl: String) {
                this.baseUrl = baseUrl
            }

            fun setTopSpacerHeight(height: Int) {
                if (topSpacerHeight == height) return
                topSpacerHeight = height
                rows = rows.map { row -> if (row is Row.TopSpacer) Row.TopSpacer(height) else row }
                notifyDataSetChanged()
            }

            fun submit(usersState: TopicReactionUsersUiState, selectedUsers: List<PostReactionUser>) {
                rows = buildList {
                    add(Row.TopSpacer(topSpacerHeight))
                    if (usersState.isLoading) {
                        add(Row.Loading)
                    } else if (selectedUsers.isEmpty()) {
                        add(Row.Empty)
                    } else {
                        selectedUsers.forEachIndexed { index, user ->
                            add(Row.User(user, divider = index != selectedUsers.lastIndex))
                        }
                    }
                }
                notifyDataSetChanged()
            }

            override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
                return rows.getOrNull(holder.bindingAdapterPosition) is Row.User
            }

            override fun getItemCount(): Int = rows.size

            override fun getItemViewType(position: Int): Int {
                return when (rows[position]) {
                    is Row.TopSpacer -> SHEET_ROW_REACTION_USERS_TOP_SPACER
                    Row.Loading -> SHEET_ROW_LOADING
                    Row.Empty -> SHEET_ROW_EMPTY
                    is Row.User -> SHEET_ROW_USER
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = when (viewType) {
                    SHEET_ROW_REACTION_USERS_TOP_SPACER -> TopSpacerView(parent.context)
                    SHEET_ROW_LOADING -> ReactionUsersSkeletonView(parent.context)
                    SHEET_ROW_USER -> ReactionUserRowView(parent.context, baseUrl)
                    else -> TopicFooterCell(parent.context)
                }
                return RecyclerListView.Holder(view)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (val row = rows[position]) {
                    is Row.TopSpacer -> (holder.itemView as TopSpacerView).setHeight(row.height)
                    Row.Loading -> Unit
                    Row.Empty -> (holder.itemView as TopicFooterCell).bind("No users", null)
                    is Row.User -> (holder.itemView as ReactionUserRowView).bind(row.user, row.divider)
                }
            }
        }

        private class TopSpacerView(context: Context) : View(context) {
            private var height = 0

            fun setHeight(height: Int) {
                if (this.height == height) return
                this.height = height
                requestLayout()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
            }
        }

        private class ReactionUsersSkeletonView(context: Context) : View(context) {
            private val rect = RectF()
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val rowHeight = AndroidUtilities.dp(56f)
            private val rowCount = 5
            private val avatarSize = AndroidUtilities.dp(36f)
            private val horizontalInset = AndroidUtilities.dp(18f)

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), rowHeight * rowCount)
            }

            override fun onDraw(canvas: Canvas) {
                paint.color = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.12f)
                repeat(rowCount) { index ->
                    val top = index * rowHeight
                    val centerY = top + rowHeight / 2f
                    canvas.drawCircle(horizontalInset + avatarSize / 2f, centerY, avatarSize / 2f, paint)
                    val textLeft = horizontalInset + avatarSize + AndroidUtilities.dp(12f)
                    drawRound(canvas, textLeft, top + AndroidUtilities.dp(13f), textLeft + AndroidUtilities.dp(128f), top + AndroidUtilities.dp(24f), 5f)
                    drawRound(canvas, textLeft, top + AndroidUtilities.dp(34f), textLeft + AndroidUtilities.dp(84f), top + AndroidUtilities.dp(42f), 4f)
                }
            }

            private fun drawRound(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int, radiusDp: Float) {
                rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                val radius = AndroidUtilities.dp(radiusDp).toFloat()
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        private class ReactionFixedHeaderView(
            context: Context,
            baseUrl: String,
            emojiCatalog: Map<String, String>,
            onReactionSelected: (String) -> Unit,
            private val horizontalInset: Int,
        ) : FrameLayout(context) {
            private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val backgroundRect = RectF()
            private val titleView = TextView(context).apply {
                text = "Reactions"
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                textSize = 20f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(6f), AndroidUtilities.dp(18f), 0)
                gravity = Gravity.CENTER_VERTICAL
            }
            private val tabsView = ReactionTabsView(context, baseUrl, emojiCatalog, onReactionSelected)
            private val shadowView = View(context).apply {
                alpha = 0f
                setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine))
            }

            init {
                setWillNotDraw(false)
                addView(titleView)
                addView(tabsView)
                addView(shadowView)
            }

            fun bind(reactions: List<PostReaction>, selectedId: String?) {
                tabsView.bind(reactions, selectedId)
            }

            fun setShadowVisible(visible: Boolean) {
                val target = if (visible) 1f else 0f
                if (shadowView.alpha == target) return
                shadowView.animate().cancel()
                shadowView.animate().alpha(target).setDuration(150L).start()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val contentWidth = (width - horizontalInset * 2).coerceAtLeast(0)
                val titleHeight = AndroidUtilities.dp(50f)
                titleView.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(titleHeight, MeasureSpec.EXACTLY))
                tabsView.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                shadowView.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY))
                setMeasuredDimension(width, titleHeight + tabsView.measuredHeight)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val contentLeft = horizontalInset
                val contentRight = right - left - horizontalInset
                titleView.layout(contentLeft, 0, contentRight, titleView.measuredHeight)
                tabsView.layout(contentLeft, titleView.measuredHeight, contentRight, titleView.measuredHeight + tabsView.measuredHeight)
                shadowView.layout(contentLeft, bottom - top - 1, contentRight, bottom - top)
            }

            override fun onDraw(canvas: Canvas) {
                backgroundPaint.color = Theme.getColor(Theme.key_dialogBackground)
                backgroundRect.set(horizontalInset.toFloat(), 0f, (measuredWidth - horizontalInset).toFloat(), measuredHeight.toFloat())
                canvas.drawRect(backgroundRect, backgroundPaint)
                super.onDraw(canvas)
            }
        }

        private class ReactionTabsView(
            context: Context,
            private val baseUrl: String,
            private val emojiCatalog: Map<String, String>,
            private val onReactionSelected: (String) -> Unit,
        ) : FrameLayout(context) {
            private val chips = mutableListOf<ReactionTabChipView>()

            init {
                setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(10f), AndroidUtilities.dp(12f), AndroidUtilities.dp(8f))
            }

            fun bind(reactions: List<PostReaction>, selectedId: String?) {
                removeAllViews()
                chips.clear()
                reactions.forEach { reaction ->
                    val chip = ReactionTabChipView(context).apply {
                        bind(reaction, reaction.iconUrl ?: emojiCatalog[reaction.id], baseUrl, selected = reaction.id == selectedId)
                        setOnClickListener { onReactionSelected(reaction.id) }
                    }
                    chips += chip
                    addView(chip)
                }
                requestLayout()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val chipHeight = AndroidUtilities.dp(32f)
                val gap = AndroidUtilities.dp(8f)
                val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
                var x = 0
                var y = paddingTop
                var rowHeight = chipHeight
                chips.forEach { chip ->
                    chip.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(chipHeight, MeasureSpec.EXACTLY))
                    if (x > 0 && x + chip.measuredWidth > available) {
                        x = 0
                        y += rowHeight + gap
                    }
                    x += chip.measuredWidth + gap
                }
                setMeasuredDimension(width, y + rowHeight + paddingBottom)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val gap = AndroidUtilities.dp(8f)
                val available = (right - left - paddingLeft - paddingRight).coerceAtLeast(0)
                var x = paddingLeft
                var y = paddingTop
                chips.forEach { chip ->
                    if (x > paddingLeft && x - paddingLeft + chip.measuredWidth > available) {
                        x = paddingLeft
                        y += chip.measuredHeight + gap
                    }
                    chip.layout(x, y, x + chip.measuredWidth, y + chip.measuredHeight)
                    x += chip.measuredWidth + gap
                }
            }
        }

        private class ReactionTabChipView(context: Context) : View(context) {
            private val iconSize = AndroidUtilities.dp(18f)
            private val iconReceiver = ImageReceiver(this).apply {
                setAllowLoadingOnAttachedOnly(true)
            }
            private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = AndroidUtilities.dp(15f).toFloat()
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = AndroidUtilities.dp(13f).toFloat()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            private var id = ""
            private var count = 0
            private var selected = false
            private var hasIcon = false

            fun bind(reaction: PostReaction, rawIconUrl: String?, baseUrl: String, selected: Boolean) {
                this.id = reaction.id
                this.count = reaction.count
                this.selected = selected
                val iconUrl = rawIconUrl?.let { resolvePostImageUrl(baseUrl, it) }
                hasIcon = !iconUrl.isNullOrBlank()
                if (hasIcon) {
                    iconReceiver.setImage(ImageLocation.getForPath(iconUrl), null, null, null, iconUrl, 0)
                } else {
                    iconReceiver.setImage(null, null, null, null, reaction.id, 0)
                }
                requestLayout()
                invalidate()
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                iconReceiver.onAttachedToWindow()
            }

            override fun onDetachedFromWindow() {
                iconReceiver.onDetachedFromWindow()
                super.onDetachedFromWindow()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val textWidth = textPaint.measureText(count.toString()).toInt()
                val fallbackWidth = if (hasIcon) 0 else fallbackPaint.measureText(reactionLabel(id)).toInt().coerceAtLeast(iconSize)
                val desired = AndroidUtilities.dp(18f) + maxOf(iconSize, fallbackWidth) + AndroidUtilities.dp(5f) + textWidth
                setMeasuredDimension(desired.coerceAtLeast(AndroidUtilities.dp(52f)), MeasureSpec.getSize(heightMeasureSpec))
            }

            override fun onDraw(canvas: Canvas) {
                val accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
                val secondaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
                backgroundPaint.color = Theme.multAlpha(accentColor, if (selected) 0.16f else 0.08f)
                val radius = height / 2f
                canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, backgroundPaint)

                val iconLeft = AndroidUtilities.dp(9f)
                val iconTop = (height - iconSize) / 2
                if (hasIcon) {
                    iconReceiver.setImageCoords(iconLeft.toFloat(), iconTop.toFloat(), iconSize.toFloat(), iconSize.toFloat())
                    iconReceiver.draw(canvas)
                } else {
                    fallbackPaint.color = if (selected) accentColor else secondaryColor
                    val fallback = reactionLabel(id)
                    canvas.drawText(
                        fallback,
                        iconLeft + iconSize / 2f,
                        height / 2f - (fallbackPaint.ascent() + fallbackPaint.descent()) / 2f,
                        fallbackPaint,
                    )
                }

                textPaint.color = if (selected) accentColor else secondaryColor
                val text = count.toString()
                canvas.drawText(
                    text,
                    (iconLeft + iconSize + AndroidUtilities.dp(5f)).toFloat(),
                    height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
                    textPaint,
                )
            }
        }

        private class ReactionUserRowView(context: Context, private val baseUrl: String) : FrameLayout(context) {
            private val avatarSize = AndroidUtilities.dp(36f)
            private val avatarDrawable = AvatarDrawable()
            private val avatarImage = ImageReceiver(this).apply {
                setRoundRadius(avatarSize / 2)
                setAllowLoadingOnAttachedOnly(true)
            }
            private val nameView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                textSize = 15f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val usernameView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val divider = View(context).apply {
                setBackgroundColor(Theme.getColor(Theme.key_divider))
            }

            init {
                setWillNotDraw(false)
                addView(nameView)
                addView(usernameView)
                addView(divider)
                setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(8f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))
            }

            fun bind(user: PostReactionUser, showDivider: Boolean) {
                val name = user.displayName.ifBlank { user.username }
                nameView.text = name
                usernameView.text = if (user.username.isBlank()) "" else "@${user.username}"
                avatarDrawable.setInfo(user.username.hashCode().toLong(), name)
                val avatarUrl = resolveAvatarUrl(baseUrl, user.avatarTemplate, "", 96)
                if (avatarUrl.isBlank()) {
                    avatarImage.setImage(null, null, avatarDrawable, null, user, 0)
                } else {
                    avatarImage.setImage(ImageLocation.getForPath(avatarUrl), "50_50", avatarDrawable, null, user, 0)
                }
                divider.visibility = if (showDivider) View.VISIBLE else View.GONE
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                avatarImage.onAttachedToWindow()
            }

            override fun onDetachedFromWindow() {
                avatarImage.onDetachedFromWindow()
                super.onDetachedFromWindow()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val textWidth = (width - paddingLeft - paddingRight - avatarSize - AndroidUtilities.dp(12f)).coerceAtLeast(0)
                nameView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22f), MeasureSpec.EXACTLY))
                usernameView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(18f), MeasureSpec.EXACTLY))
                divider.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY))
                setMeasuredDimension(width, AndroidUtilities.dp(56f))
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val avatarTop = (height - avatarSize) / 2
                avatarImage.setImageCoords(paddingLeft.toFloat(), avatarTop.toFloat(), avatarSize.toFloat(), avatarSize.toFloat())
                val textLeft = paddingLeft + avatarSize + AndroidUtilities.dp(12f)
                val nameTop = AndroidUtilities.dp(8f)
                nameView.layout(textLeft, nameTop, width - paddingRight, nameTop + nameView.measuredHeight)
                usernameView.layout(textLeft, nameTop + nameView.measuredHeight, width - paddingRight, nameTop + nameView.measuredHeight + usernameView.measuredHeight)
                divider.layout(textLeft, height - 1, width - paddingRight, height)
            }

            override fun dispatchDraw(canvas: Canvas) {
                avatarImage.draw(canvas)
                super.dispatchDraw(canvas)
            }
        }
    }

    private class PullRefreshLayout(
        context: Context,
        private val listView: RecyclerListView,
        private val onRefresh: () -> Unit,
    ) : FrameLayout(context) {
        private val maxPull = PullForegroundDrawable.getMaxOverscroll().toFloat()
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val pullDrawable = PullForegroundDrawable(
            context.getString(io.github.baba983.nativedo.R.string.topic_detail_pull_refresh),
            context.getString(io.github.baba983.nativedo.R.string.topic_detail_release_refresh),
        )
        private val headerView = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                pullDrawable.draw(canvas)
            }
        }
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var pullDistance = 0f
        private var thresholdReached = false
        private var settleAnimator: ValueAnimator? = null
        var isBusy: Boolean = false

        init {
            pullDrawable.setCell(headerView)
            pullDrawable.setListView(listView)
            pullDrawable.doNotShow()
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
            addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, PullForegroundDrawable.getMaxOverscroll(), Gravity.TOP))
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    dragging = false
                    settleAnimator?.cancel()
                    pullDrawable.resetText()
                    thresholdReached = false
                    pullDrawable.colorize(false)
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(ev.x - downX)
                    val dy = ev.y - downY
                    if (dy > touchSlop && dy > dx && !listView.canScrollVertically(-1)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    settleAnimator?.cancel()
                    pullDrawable.resetText()
                    thresholdReached = false
                    pullDrawable.colorize(false)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - downY
                    if (dy > 0 && !listView.canScrollVertically(-1)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        setPullDistance(dampenPull(dy))
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging || pullDistance > 0f) {
                        if (pullDrawable.pullProgress >= PullForegroundDrawable.SNAP_HEIGHT && !isBusy) {
                            onRefresh()
                        }
                        pullDrawable.doNotShow()
                        pullDrawable.colorize(false)
                        thresholdReached = false
                        animatePullBack()
                        dragging = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }

        private fun dampenPull(distance: Float): Float {
            return distance.coerceAtMost(maxPull * 2.0f) * 0.5f
        }

        private fun setPullDistance(distance: Float) {
            pullDistance = distance.coerceIn(0f, maxPull)
            listView.translationY = pullDistance
            val progress = (pullDistance / maxPull).coerceIn(0f, 1f)
            val reached = progress >= PullForegroundDrawable.SNAP_HEIGHT
            if (thresholdReached != reached) {
                thresholdReached = reached
                pullDrawable.colorize(reached)
            }
            pullDrawable.showHidden()
            pullDrawable.setWillDraw(pullDistance > 0f)
            pullDrawable.setPullProgress(progress)
            headerView.invalidate()
        }

        private fun animatePullBack() {
            settleAnimator?.cancel()
            settleAnimator = ValueAnimator.ofFloat(pullDistance, 0f).apply {
                interpolator = DecelerateInterpolator()
                duration = 180L
                addUpdateListener { animator -> setPullDistance(animator.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        pullDrawable.doNotShow()
                        pullDrawable.resetText()
                        pullDrawable.colorize(false)
                        thresholdReached = false
                        headerView.invalidate()
                    }
                })
                start()
            }
        }
    }

    private class TopicRepliesSheet(
        context: Context,
        fragment: BaseFragment,
        private val baseUrl: String,
        sourcePost: Post,
        replies: List<TopicReplySheetItem>,
        private val onReplySelected: (TopicReplySheetItem) -> Unit,
        private val onDismissed: () -> Unit,
    ) : BottomSheetWithRecyclerListView(context, fragment, false, true, false, null) {
        private lateinit var adapter: Adapter

        init {
            adapter.configure(baseUrl, onReplySelected)
            setOnDismissListener(DialogInterface.OnDismissListener { onDismissed() })
            recyclerListView.layoutParams = recyclerListView.layoutParams.apply {
                height = AndroidUtilities.dp(460f)
            }
            adapter.submit(sourcePost, replies)
        }

        override fun getTitle(): CharSequence = "Replies"

        override fun createAdapter(recyclerListView: RecyclerListView): RecyclerListView.SelectionAdapter {
            return Adapter().also { adapter = it }
        }

        private sealed interface Row {
            data class Header(val sourcePost: Post, val count: Int) : Row
            data class Reply(val item: TopicReplySheetItem, val divider: Boolean) : Row
        }

        private class Adapter : RecyclerListView.SelectionAdapter() {
            private var rows: List<Row> = emptyList()
            private var baseUrl: String = ""
            private var onReplySelected: (TopicReplySheetItem) -> Unit = {}

            fun configure(baseUrl: String, onReplySelected: (TopicReplySheetItem) -> Unit) {
                this.baseUrl = baseUrl
                this.onReplySelected = onReplySelected
            }

            fun submit(sourcePost: Post, replies: List<TopicReplySheetItem>) {
                rows = buildList {
                    add(Row.Header(sourcePost, replies.size))
                    replies.forEachIndexed { index, reply ->
                        add(Row.Reply(reply, divider = index != replies.lastIndex))
                    }
                }
                notifyDataSetChanged()
            }

            override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
                return rows.getOrNull(holder.bindingAdapterPosition) is Row.Reply
            }

            override fun getItemCount(): Int = rows.size

            override fun getItemViewType(position: Int): Int {
                return when (rows[position]) {
                    is Row.Header -> SHEET_ROW_REPLY_HEADER
                    is Row.Reply -> SHEET_ROW_REPLY
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = if (viewType == SHEET_ROW_REPLY_HEADER) {
                    ReplySheetHeaderView(parent.context)
                } else {
                    ReplySheetRowView(parent.context, baseUrl)
                }
                return RecyclerListView.Holder(view)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (val row = rows[position]) {
                    is Row.Header -> (holder.itemView as ReplySheetHeaderView).bind(row.sourcePost, row.count)
                    is Row.Reply -> {
                        (holder.itemView as ReplySheetRowView).bind(row.item, row.divider)
                        holder.itemView.setOnClickListener { onReplySelected(row.item) }
                    }
                }
            }
        }

        private class ReplySheetHeaderView(context: Context) : AppCompatTextView(context) {
            init {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                textSize = 20f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))
            }

            fun bind(sourcePost: Post, count: Int) {
                val floor = sourcePost.postNumber?.let { "#${it}" }.orEmpty()
                text = if (floor.isBlank()) "$count replies" else "$count replies to $floor"
            }
        }

        private class ReplySheetRowView(context: Context, private val baseUrl: String) : FrameLayout(context) {
            private val avatarSize = AndroidUtilities.dp(36f)
            private val avatarDrawable = AvatarDrawable()
            private val avatarImage = ImageReceiver(this).apply {
                setRoundRadius(avatarSize / 2)
                setAllowLoadingOnAttachedOnly(true)
            }
            private val nameView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                textSize = 15f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val metaView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val previewView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                textSize = 13f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val divider = View(context).apply {
                setBackgroundColor(Theme.getColor(Theme.key_divider))
            }

            init {
                setWillNotDraw(false)
                setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(8f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))
                addView(nameView)
                addView(metaView)
                addView(previewView)
                addView(divider)
            }

            fun bind(reply: TopicReplySheetItem, showDivider: Boolean) {
                val name = topicPostDisplayName(reply.authorDisplayName, reply.authorUsername)
                nameView.text = name
                metaView.text = buildString {
                    reply.postNumber?.let { append('#').append(it) }
                    val time = topicPostTimeLabel(reply.createdAt)
                    if (!time.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(time)
                    }
                    if (reply.authorUsername.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append('@').append(reply.authorUsername)
                    }
                }
                previewView.text = reply.previewText.ifBlank { "View original reply" }
                divider.visibility = if (showDivider) View.VISIBLE else View.GONE
                avatarDrawable.setInfo(reply.authorUsername.hashCode().toLong(), name)
                val avatarUrl = resolveAvatarUrl(baseUrl, reply.authorAvatarTemplate, reply.authorAnimatedAvatarUrl, 96)
                if (avatarUrl.isBlank()) {
                    avatarImage.setImage(null, null, avatarDrawable, null, reply, 0)
                } else {
                    avatarImage.setImage(ImageLocation.getForPath(avatarUrl), "50_50", avatarDrawable, null, reply, 0)
                }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                avatarImage.onAttachedToWindow()
            }

            override fun onDetachedFromWindow() {
                avatarImage.onDetachedFromWindow()
                super.onDetachedFromWindow()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val contentWidth = (width - paddingLeft - paddingRight - avatarSize - AndroidUtilities.dp(12f)).coerceAtLeast(0)
                nameView.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.EXACTLY))
                metaView.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(18f), MeasureSpec.EXACTLY))
                previewView.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(38f), MeasureSpec.AT_MOST))
                divider.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY))
                setMeasuredDimension(width, maxOf(AndroidUtilities.dp(72f), paddingTop + nameView.measuredHeight + metaView.measuredHeight + previewView.measuredHeight + paddingBottom + 1))
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                var y = paddingTop
                val avatarTop = (height - avatarSize) / 2
                avatarImage.setImageCoords(paddingLeft.toFloat(), avatarTop.toFloat(), avatarSize.toFloat(), avatarSize.toFloat())
                val contentLeft = paddingLeft + avatarSize + AndroidUtilities.dp(12f)
                val contentRight = right - left - paddingRight
                nameView.layout(contentLeft, y, contentRight, y + nameView.measuredHeight)
                y += nameView.measuredHeight
                metaView.layout(contentLeft, y, contentRight, y + metaView.measuredHeight)
                y += metaView.measuredHeight
                previewView.layout(contentLeft, y, contentRight, y + previewView.measuredHeight)
                divider.layout(contentLeft, bottom - top - 1, contentRight, bottom - top)
            }

            override fun dispatchDraw(canvas: Canvas) {
                avatarImage.draw(canvas)
                super.dispatchDraw(canvas)
            }
        }
    }

    private class TopicReactionPickerSheet(
        context: Context,
        fragment: BaseFragment,
        reactionIds: List<String>,
        selectedReactionId: String?,
        emojiCatalog: Map<String, String>,
        baseUrl: String,
        private val onReactionSelected: (String) -> Unit,
        private val onDismissed: () -> Unit,
    ) : BottomSheetWithRecyclerListView(context, fragment, false, true, false, null) {
        private lateinit var adapter: Adapter

        init {
            adapter.configure(emojiCatalog, baseUrl, onReactionSelected)
            setOnDismissListener(DialogInterface.OnDismissListener { onDismissed() })
            recyclerListView.layoutParams = recyclerListView.layoutParams.apply {
                height = AndroidUtilities.dp(360f)
            }
            adapter.submit(reactionIds, selectedReactionId)
        }

        override fun getTitle(): CharSequence = "Choose reaction"

        override fun createAdapter(recyclerListView: RecyclerListView): RecyclerListView.SelectionAdapter {
            return Adapter().also { adapter = it }
        }

        private sealed interface Row {
            data object Header : Row
            data class Reaction(val id: String, val selected: Boolean, val divider: Boolean) : Row
        }

        private class Adapter : RecyclerListView.SelectionAdapter() {
            private var rows: List<Row> = emptyList()
            private var emojiCatalog: Map<String, String> = emptyMap()
            private var baseUrl: String = ""
            private var onReactionSelected: (String) -> Unit = {}

            fun configure(
                emojiCatalog: Map<String, String>,
                baseUrl: String,
                onReactionSelected: (String) -> Unit,
            ) {
                this.emojiCatalog = emojiCatalog
                this.baseUrl = baseUrl
                this.onReactionSelected = onReactionSelected
            }

            fun submit(reactionIds: List<String>, selectedReactionId: String?) {
                rows = buildList {
                    add(Row.Header)
                    reactionIds.forEachIndexed { index, id ->
                        add(Row.Reaction(id, selected = id == selectedReactionId, divider = index != reactionIds.lastIndex))
                    }
                }
                notifyDataSetChanged()
            }

            override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
                return rows.getOrNull(holder.bindingAdapterPosition) is Row.Reaction
            }

            override fun getItemCount(): Int = rows.size

            override fun getItemViewType(position: Int): Int {
                return when (rows[position]) {
                    Row.Header -> SHEET_ROW_REACTION_PICKER_HEADER
                    is Row.Reaction -> SHEET_ROW_REACTION_PICKER_ITEM
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = if (viewType == SHEET_ROW_REACTION_PICKER_HEADER) {
                    PickerHeaderView(parent.context)
                } else {
                    ReactionPickerRowView(parent.context)
                }
                return RecyclerListView.Holder(view)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (val row = rows[position]) {
                    Row.Header -> Unit
                    is Row.Reaction -> {
                        (holder.itemView as ReactionPickerRowView).bind(
                            id = row.id,
                            iconUrl = emojiCatalog[row.id]?.let { resolvePostImageUrl(baseUrl, it) },
                            selected = row.selected,
                            divider = row.divider,
                        )
                        holder.itemView.setOnClickListener { onReactionSelected(row.id) }
                    }
                }
            }
        }

        private class PickerHeaderView(context: Context) : AppCompatTextView(context) {
            init {
                text = "Choose reaction"
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                textSize = 20f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))
            }
        }

        private class ReactionPickerRowView(context: Context) : FrameLayout(context) {
            private val iconSize = AndroidUtilities.dp(32f)
            private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = AndroidUtilities.dp(2f).toFloat()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val iconReceiver = ImageReceiver(this).apply {
                setAllowLoadingOnAttachedOnly(true)
            }
            private val labelView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val valueView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val divider = View(context).apply {
                setBackgroundColor(Theme.getColor(Theme.key_divider))
            }
            private var hasIcon = false
            private var selected = false

            init {
                setWillNotDraw(false)
                setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(8f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))
                addView(labelView)
                addView(valueView)
                addView(divider)
            }

            fun bind(id: String, iconUrl: String?, selected: Boolean, divider: Boolean) {
                this.selected = selected
                hasIcon = !iconUrl.isNullOrBlank()
                labelView.text = reactionLabel(id)
                valueView.text = if (selected) "Selected" else id.replace('_', ' ')
                if (iconUrl.isNullOrBlank()) {
                    iconReceiver.setImage(null, null, null, null, id, 0)
                } else {
                    iconReceiver.setImage(ImageLocation.getForPath(iconUrl), null, null, null, iconUrl, 0)
                }
                this.divider.visibility = if (divider) View.VISIBLE else View.GONE
                invalidate()
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                iconReceiver.onAttachedToWindow()
            }

            override fun onDetachedFromWindow() {
                iconReceiver.onDetachedFromWindow()
                super.onDetachedFromWindow()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                val textWidth = (width - paddingLeft - paddingRight - iconSize - AndroidUtilities.dp(48f)).coerceAtLeast(0)
                labelView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f), MeasureSpec.EXACTLY))
                valueView.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(18f), MeasureSpec.EXACTLY))
                divider.measure(MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY))
                setMeasuredDimension(width, AndroidUtilities.dp(60f))
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                val iconTop = (height - iconSize) / 2
                iconReceiver.setImageCoords(paddingLeft.toFloat(), iconTop.toFloat(), iconSize.toFloat(), iconSize.toFloat())
                val textLeft = paddingLeft + iconSize + AndroidUtilities.dp(14f)
                val textRight = right - left - paddingRight - AndroidUtilities.dp(28f)
                val textTop = AndroidUtilities.dp(8f)
                labelView.layout(textLeft, textTop, textRight, textTop + labelView.measuredHeight)
                valueView.layout(textLeft, textTop + labelView.measuredHeight, textRight, textTop + labelView.measuredHeight + valueView.measuredHeight)
                divider.layout(textLeft, height - 1, textRight, height)
            }

            override fun dispatchDraw(canvas: Canvas) {
                if (hasIcon) {
                    iconReceiver.draw(canvas)
                } else {
                    val cx = paddingLeft + iconSize / 2f
                    val cy = height / 2f
                    circlePaint.color = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.12f)
                    canvas.drawCircle(cx, cy, iconSize / 2f, circlePaint)
                    val label = labelView.text.toString().take(2)
                    val textPaint = labelView.paint
                    val oldSize = textPaint.textSize
                    val oldColor = textPaint.color
                    textPaint.textSize = AndroidUtilities.dp(18f).toFloat()
                    textPaint.color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
                    canvas.drawText(label, cx - textPaint.measureText(label) / 2f, cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
                    textPaint.textSize = oldSize
                    textPaint.color = oldColor
                }
                super.dispatchDraw(canvas)
                if (selected) {
                    checkPaint.color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
                    val right = width - paddingRight - AndroidUtilities.dp(2f)
                    val centerY = height / 2f
                    canvas.drawLine((right - AndroidUtilities.dp(14f)).toFloat(), centerY, (right - AndroidUtilities.dp(8f)).toFloat(), centerY + AndroidUtilities.dp(6f), checkPaint)
                    canvas.drawLine((right - AndroidUtilities.dp(8f)).toFloat(), centerY + AndroidUtilities.dp(6f), right.toFloat(), centerY - AndroidUtilities.dp(7f), checkPaint)
                }
            }
        }
    }

    private companion object {
        private val DEFAULT_REACTION_IDS = listOf("heart", "+1", "laughing", "clap", "confetti_ball")
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_POST = 1
        private const val VIEW_TYPE_FOOTER = 2
        private const val VIEW_TYPE_LOADING = 3
        private const val VIEW_TYPE_SKELETON = 4
        private const val SHEET_ROW_HEADER = 9
        private const val SHEET_ROW_REACTION_USERS_TOP_SPACER = 10
        private const val SHEET_ROW_LOADING = 11
        private const val SHEET_ROW_EMPTY = 12
        private const val SHEET_ROW_USER = 13
        private const val SHEET_ROW_REPLY_HEADER = 20
        private const val SHEET_ROW_REPLY = 21
        private const val SHEET_ROW_REACTION_PICKER_HEADER = 30
        private const val SHEET_ROW_REACTION_PICKER_ITEM = 31

        private fun reactionLabel(id: String): String {
            return id
                .replace("+1", "👍")
                .replace("heart", "♡")
                .replace("laughing", "😄")
                .replace("clap", "👏")
                .replace("confetti_ball", "🎉")
                .replace('_', ' ')
        }
    }
}

private class TopicViewModelFactory(
    private val repository: TopicRepository,
    private val topicId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return TopicViewModel(repository = repository, topicId = topicId) as T
    }
}

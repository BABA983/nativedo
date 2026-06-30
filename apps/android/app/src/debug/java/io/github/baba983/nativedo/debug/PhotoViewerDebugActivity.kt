package io.github.baba983.nativedo.debug

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.ui.topic.TopicDetailFragment
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCellDelegate
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.INavigationLayout
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.PhotoViewer
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import java.io.File

class PhotoViewerDebugActivity : ComponentActivity() {
    private lateinit var hostFragment: BaseFragment
    private lateinit var sourceView: SourcePreviewView
    private lateinit var statusView: TextView
    private var lastStatus: String = "pending"
    private var lastPlaceSourceInWindow: RectF? = null
    private var lastTopicCellSourceInWindow: RectF? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidUtilities.init(this)

        val urls = intent.getStringExtra(EXTRA_URLS)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: createDebugImageFiles()
        val index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, urls.lastIndex)
        val scenario = intent.getStringExtra(EXTRA_SCENARIO)

        val root = DebugRootLayout(this).apply {
            setBackgroundColor(0xff101010.toInt())
            keepScreenOn = true
        }
        statusView = TextView(this).apply {
            setTextColor(0xffffffff.toInt())
            textSize = 14f
            setPadding(AndroidUtilities.dp(16f), AndroidUtilities.dp(10f), AndroidUtilities.dp(16f), AndroidUtilities.dp(10f))
            text = "PhotoViewer debug: pending"
        }
        root.addView(
            statusView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        setContentView(root)

        hostFragment = object : BaseFragment() {}
        hostFragment.setFragmentView(root)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val viewer = PhotoViewer.getInstance()
                    if (viewer.isVisibleOrAnimating()) {
                        viewer.closePhoto(true, false)
                    } else {
                        finish()
                    }
                }
            },
        )

        if (scenario == SCENARIO_TOPIC_DETAIL_INTERACTION) {
            setupTopicDetailInteractionScenario(root)
            return
        }

        val topicCellFixture = when (scenario) {
            SCENARIO_TOPIC_CELL_TAP -> TopicCellFixture.SingleImage
            SCENARIO_TOPIC_CELL_NESTED_IMAGE_TAP -> TopicCellFixture.NestedImage
            SCENARIO_TOPIC_CELL_COLLAGE_TAP -> TopicCellFixture.Collage
            SCENARIO_TOPIC_CELL_ONEBOX_THUMBNAIL_TAP -> TopicCellFixture.OneboxThumbnail
            SCENARIO_TOPIC_CELL_POLL_VOTE -> TopicCellFixture.PollVote
            SCENARIO_TOPIC_CELL_INLINE_SPOILER_TAP -> TopicCellFixture.InlineSpoiler
            SCENARIO_TOPIC_CELL_REPLY_FOOTER_TAP -> TopicCellFixture.ReplyFooter
            SCENARIO_TOPIC_CELL_REACTION_TAP -> TopicCellFixture.Reaction
            SCENARIO_TOPIC_CELL_MORE_REACTION_TAP -> TopicCellFixture.MoreReaction
            SCENARIO_TOPIC_CELL_POST_LONG_PRESS -> TopicCellFixture.PostLongPress
            SCENARIO_TOPIC_CELL_REPLY_FOOTER_LONG_PRESS -> TopicCellFixture.ReplyFooterLongPress
            SCENARIO_TOPIC_CELL_REACTION_LONG_PRESS -> TopicCellFixture.ReactionLongPress
            else -> null
        }
        if (topicCellFixture != null) {
            setupTopicCellScenario(
                root,
                urls,
                index,
                intent.getLongExtra(EXTRA_CLOSE_AFTER_MS, 0L),
                fixture = topicCellFixture,
                scenarioName = scenario ?: SCENARIO_TOPIC_CELL_TAP,
            )
        } else {
            sourceView = SourcePreviewView(this).apply {
                setImageUrl(urls[index])
            }
            root.addView(
                sourceView,
                FrameLayout.LayoutParams(AndroidUtilities.dp(260f), AndroidUtilities.dp(180f), Gravity.CENTER),
            )
            root.post {
                openViewer(urls, index)
                runScenario(scenario)
                val closeAfterMs = intent.getLongExtra(EXTRA_CLOSE_AFTER_MS, 0L)
                if (closeAfterMs > 0L) {
                    root.postDelayed({ PhotoViewer.getInstance().closePhoto(true, false) }, closeAfterMs)
                }
            }
        }
    }

    private fun setupTopicDetailInteractionScenario(root: DebugRootLayout) {
        val fragment = TopicDetailFragment(1L, "Debug Topic")
        fragment.setParentLayout(root)
        val fragmentRoot = SizeNotifierFrameLayout(this).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        val actionBar = fragment.actionBar
        fragmentRoot.addView(
            actionBar,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )
        val listView = RecyclerListView(this).apply {
            setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(24f))
        }
        fragmentRoot.addView(
            listView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP),
        )
        val anchor = View(this).apply {
            setBackgroundColor(0x2200aaff)
        }
        fragmentRoot.addView(
            anchor,
            FrameLayout.LayoutParams(AndroidUtilities.dp(220f), AndroidUtilities.dp(96f), Gravity.CENTER),
        )
        root.addView(
            fragmentRoot,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        statusView.bringToFront()
        fragment.setFragmentView(fragmentRoot)
        setReflectedField(fragment, "baseUrl", DEBUG_BASE_URL)
        setReflectedField(fragment, "validReactionIds", listOf("heart", "rocket", "eyes", "tada"))
        setReflectedField(fragment, "emojiCatalog", emptyMap<String, String>())
        setReflectedField(fragment, "listView", listView)
        setReflectedField(fragment, "listBottomBasePadding", AndroidUtilities.dp(24f))

        val post = Post(
            id = 1001L,
            postNumber = 1,
            authorUsername = "debug",
            authorDisplayName = "Topic Detail Debug",
            cookedHtml = "<p>Debug topic detail interaction</p>",
            currentUserReaction = "heart",
            reactions = listOf(
                PostReaction("heart", 3, reacted = true),
                PostReaction("rocket", 2),
            ),
            isBookmarked = false,
        )
        fragmentRoot.postDelayed({
            invokeReflectedMethod(
                fragment,
                "showPostOptions",
                arrayOf(Post::class.java, View::class.java, Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!),
                post,
                anchor,
                anchor.width * 0.55f,
                anchor.height * 0.4f,
            )
            val postPopup = reflectedField(fragment, "postOptions") as? ActionBarPopupWindow
            val postPopupContent = postPopup?.contentView
            val postRailCount = countDescendants(postPopupContent) { it.javaClass.simpleName == "ReactionPickerRailView" }
            val postActionCount = countDescendants(postPopupContent) { it.javaClass.simpleName == "ActionBarMenuSubItem" }
            val replyItem = findDescendant(postPopupContent) { it.javaClass.simpleName == "ActionBarMenuSubItem" }
            val postPopupShownBeforeReply = postPopup?.isShowing == true
            val replyClicked = replyItem?.performClick() == true
            anchor.postDelayed({
                val replyPanel = reflectedField(fragment, "replyComposerPanel") as? View
                val replyAttached = replyPanel?.parent === fragmentRoot
                val bottomPaddingAfterReply = listView.paddingBottom
                dismissReflectedReplyPanel(replyPanel)
                invokeReflectedMethod(
                    fragment,
                    "showReactionPickerOptions",
                    arrayOf(
                        Post::class.java,
                        List::class.java,
                        String::class.java,
                        View::class.java,
                        Float::class.javaPrimitiveType!!,
                        Float::class.javaPrimitiveType!!,
                    ),
                    post,
                    post.reactions,
                    "heart",
                    anchor,
                    anchor.width * 0.5f,
                    anchor.height * 0.45f,
                )
                val reactionPopup = reflectedField(fragment, "reactionRailPopup") as? ActionBarPopupWindow
                val reactionPopupContent = reactionPopup?.contentView
                val reactionRailCount = countDescendants(reactionPopupContent) { it.javaClass.simpleName == "ReactionPickerRailView" }
                val checks = listOf(
                    "postPopupShown" to postPopupShownBeforeReply,
                    "postRail" to (postRailCount > 0),
                    "postActions" to (postActionCount == 3),
                    "replyClicked" to replyClicked,
                    "replyAttached" to replyAttached,
                    "replyBottomPadding" to (bottomPaddingAfterReply >= AndroidUtilities.dp(24f)),
                    "reactionPopupShown" to (reactionPopup?.isShowing == true),
                    "reactionRail" to (reactionRailCount > 0),
                )
                val text = scenarioStatus(
                    SCENARIO_TOPIC_DETAIL_INTERACTION,
                    checks,
                    "postPopupShown=$postPopupShownBeforeReply postRailCount=$postRailCount postActionCount=$postActionCount replyClicked=$replyClicked replyAttached=$replyAttached bottomPaddingAfterReply=$bottomPaddingAfterReply reactionPopupShown=${reactionPopup?.isShowing == true} reactionRailCount=$reactionRailCount",
                )
                logScenarioStatus(text, checks)
                setStatus(text)
                anchor.postDelayed({
                    postPopup?.dismiss()
                    reactionPopup?.dismiss()
                    dismissReflectedReplyPanel(reflectedField(fragment, "replyComposerPanel") as? View)
                    fragment.onFragmentDestroy()
                }, 900L)
            }, 260L)
        }, 700L)
    }

    override fun onResume() {
        super.onResume()
        PhotoViewer.getInstance().onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AndroidUtilities.init(this)
        PhotoViewer.getInstance().onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        PhotoViewer.getInstance().destroyPhotoViewer()
        super.onDestroy()
    }

    private fun openViewer(urls: List<String>, index: Int) {
        val viewer = PhotoViewer.getInstance()
        viewer.setParentActivity(hostFragment)
        val provider = object : PhotoViewer.EmptyPhotoViewerProvider() {
            override fun getPlaceForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                photoIndex: Int,
                needPreview: Boolean,
                closing: Boolean,
            ): PhotoViewer.PlaceProviderObject? {
                if (photoIndex != index) return null
                sourceView.ensureReceiverBounds()
                val receiver = if (needPreview) sourceView.imageReceiver else null
                val sourceBounds = sourceView.imageBoundsInWindow()
                if (needPreview) {
                    lastPlaceSourceInWindow = sourceView.imageHandoffRectInWindow()
                }
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = (sourceBounds.left - (receiver?.imageX ?: 0f)).toInt()
                    viewY = (sourceBounds.top - (receiver?.imageY ?: 0f)).toInt()
                    viewWidth = (receiver?.imageWidth ?: sourceBounds.width()).toInt()
                    viewHeight = (receiver?.imageHeight ?: sourceBounds.height()).toInt()
                    parentView = sourceView
                    imageReceiver = receiver
                    thumb = if (needPreview) sourceView.bitmapHolder() else null
                    radius = if (needPreview) sourceView.radius() else null
                }
            }

            override fun getThumbForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                photoIndex: Int,
            ): ImageReceiver.BitmapHolder? {
                return if (photoIndex == index) sourceView.bitmapHolder() else null
            }

            override fun onPhotoChanged(photoIndex: Int) {
                Log.d(TAG, "onPhotoChanged index=$photoIndex")
                setStatus("onPhotoChanged index=$photoIndex")
            }

            override fun onClose() {
                Log.d(TAG, "onClose")
                showStatus("onClose visible=${PhotoViewer.getInstance().isVisibleOrAnimating()} previous=$lastStatus")
            }
        }
        val items = urls.mapIndexed { itemIndex, url ->
            PhotoViewer.UrlPhotoItem(url, null, 1600, 1000, "Photo ${itemIndex + 1}")
        }
        val opened = viewer.openPhotoItems(items, index, provider)
        setStatus("opened=$opened index=${currentPhotoIndex()} visible=${viewer.isVisibleOrAnimating()}")
    }

    private fun setupTopicCellScenario(root: FrameLayout, urls: List<String>, index: Int, closeAfterMs: Long, fixture: TopicCellFixture, scenarioName: String) {
        val topicUrls = urls.map { "thumb://0:$it" }
        val targetUrl = topicUrls[index]
        val cookedHtml = when (fixture) {
            TopicCellFixture.SingleImage -> """
                <p><img src="$targetUrl" width="1600" height="1000" alt="Debug topic image"></p>
            """.trimIndent()
            TopicCellFixture.NestedImage -> nestedTopicCellHtml(targetUrl)
            TopicCellFixture.Collage -> collageTopicCellHtml(topicUrls)
            TopicCellFixture.OneboxThumbnail -> oneboxTopicCellHtml(targetUrl)
            TopicCellFixture.PollVote -> pollTopicCellHtml()
            TopicCellFixture.InlineSpoiler -> inlineSpoilerTopicCellHtml()
            TopicCellFixture.PostLongPress,
            TopicCellFixture.ReplyFooter,
            TopicCellFixture.ReplyFooterLongPress,
            TopicCellFixture.Reaction,
            TopicCellFixture.MoreReaction,
            TopicCellFixture.ReactionLongPress -> """<p>Topic cell chrome smoke fixture</p>"""
        }
        val parsedBlocks = parseCookedPostBody(cookedHtml, DEBUG_BASE_URL)
        val blockState = PostBodyBlockState().apply {
            if (fixture == TopicCellFixture.NestedImage) {
                openedDetailsByIndex.add(0)
            }
        }
        var lastPollName: String? = null
        var lastPollOptionIds: List<String> = emptyList()
        var lastReplyPostNumber: Int? = null
        var lastReactionId: String? = null
        var lastMoreReactionCount: Int? = null
        var lastMoreReactionInitialId: String? = null
        var lastLongPressPostNumber: Int? = null
        val post = debugTopicCellPost(fixture, cookedHtml, topicUrls)
        val cell = TopicPostCell(this).apply {
            setDelegate(object : TopicPostCellDelegate {
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
                    lastTopicCellSourceInWindow = sourceBoundsInWindow?.let(::RectF)
                    openViewerFromTopicCell(
                        post = post,
                        url = url,
                        galleryImages = gallery,
                        tappedSourceView = hostView,
                        sourceBoundsProvider = sourceBoundsProvider,
                        sourceImageReceiverProvider = sourceImageReceiverProvider,
                        sourceBitmapProvider = sourceBitmapProvider,
                        sourceRadiusProvider = sourceRadiusProvider,
                    )
                }

                override fun didPressPollOptions(post: Post, pollName: String, optionIds: List<String>) {
                    lastPollName = pollName
                    lastPollOptionIds = optionIds
                    Log.d(TAG, "didPressPollOptions pollName=$pollName optionIds=$optionIds")
                }

                override fun didPressReply(post: Post) {
                    lastReplyPostNumber = post.postNumber
                    Log.d(TAG, "didPressReply postNumber=${post.postNumber}")
                }

                override fun didPressReaction(post: Post, reaction: PostReaction, anchorView: View, x: Float, y: Float) {
                    lastReactionId = reaction.id
                    Log.d(TAG, "didPressReaction reactionId=${reaction.id}")
                }

                override fun didPressMoreReactions(post: Post, reactions: List<PostReaction>, initialReactionId: String?, anchorView: View, x: Float, y: Float) {
                    lastMoreReactionCount = reactions.size
                    lastMoreReactionInitialId = initialReactionId
                    Log.d(TAG, "didPressMoreReactions count=${reactions.size} initialReactionId=$initialReactionId")
                }

                override fun didLongPressPost(post: Post, anchorView: View, x: Float, y: Float) {
                    lastLongPressPostNumber = post.postNumber
                    Log.d(TAG, "didLongPressPost postNumber=${post.postNumber} x=$x y=$y")
                }
            })
            setPost(
                post,
                null,
                null,
                true,
                true,
                1L,
                DEBUG_BASE_URL,
                emptyMap(),
                emptyMap(),
                validReactionsForFixture(fixture),
                replyCountForFixture(fixture),
                recentReplyParticipantsForFixture(fixture),
                parsedBlocks,
                blockState,
                0,
                null,
            )
        }
        root.addView(
            cell,
            FrameLayout.LayoutParams(topicCellDebugWidth(), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = AndroidUtilities.dp(64f)
            },
        )
        root.postDelayed({
            when (fixture) {
                TopicCellFixture.SingleImage,
                TopicCellFixture.NestedImage,
                TopicCellFixture.Collage,
                TopicCellFixture.OneboxThumbnail -> {
                    val dispatched = dispatchTopicCellImageTap(cell, targetUrl)
                    cell.postDelayed({
                        val start = viewerRect("startImageRect")
                        val startAbs = start?.let(::RectF)?.apply {
                            val location = photoViewerContainerLocationInWindow()
                            offset(location[0].toFloat(), location[1].toFloat())
                        }
                        val text = "scenario=$scenarioName dispatched=$dispatched currentIndex=${currentPhotoIndex()} source=${lastTopicCellSourceInWindow?.toShortString()} handoff=${lastPlaceSourceInWindow?.toShortString()} startAbs=${startAbs?.toShortString()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                        Log.i(TAG, text)
                        setStatus(text)
                    }, 650L)
                    if (closeAfterMs > 0L) {
                        cell.postDelayed({ PhotoViewer.getInstance().closePhoto(true, false) }, closeAfterMs)
                    }
                }
                TopicCellFixture.PollVote -> {
                    val dispatched = dispatchTopicCellPollVoteTap(cell)
                    cell.postDelayed({
                        val text = "scenario=$scenarioName dispatched=$dispatched pollName=$lastPollName optionIds=$lastPollOptionIds submitted=${blockState.submittedPollIndexes} selected=${blockState.selectedPollOptionIndexesByIndex}"
                        Log.i(TAG, text)
                        setStatus(text)
                    }, 300L)
                }
                TopicCellFixture.InlineSpoiler -> {
                    val before = blockState.revealedInlineSpoilersByKey.size
                    val dispatched = dispatchTopicCellInlineSpoilerTap(cell)
                    cell.postDelayed({
                        val text = "scenario=$scenarioName dispatched=$dispatched revealedBefore=$before revealedAfter=${blockState.revealedInlineSpoilersByKey.size} revealedKeys=${blockState.revealedInlineSpoilersByKey}"
                        Log.i(TAG, text)
                        setStatus(text)
                    }, 900L)
                }
                TopicCellFixture.ReplyFooter -> {
                    val dispatched = dispatchTopicCellReplyFooterTap(cell)
                    cell.postDelayed({
                        val text = "scenario=$scenarioName dispatched=$dispatched replyPostNumber=$lastReplyPostNumber"
                        Log.i(TAG, text)
                        setStatus(text)
                    }, 300L)
                }
                TopicCellFixture.Reaction -> {
                    val dispatched = dispatchTopicCellReactionTap(cell, more = false)
                    cell.postDelayed({
                        val text = "scenario=$scenarioName dispatched=$dispatched reactionId=$lastReactionId"
                        Log.i(TAG, text)
                        setStatus(text)
                    }, 300L)
                }
                TopicCellFixture.MoreReaction -> {
                    val dispatched = dispatchTopicCellReactionTap(cell, more = true)
                    cell.postDelayed({
                        val text = "scenario=$scenarioName dispatched=$dispatched moreReactionCount=$lastMoreReactionCount"
                        Log.i(TAG, text)
                        setStatus(text)
                    }, 300L)
                }
                TopicCellFixture.PostLongPress -> {
                    val dispatched = dispatchTopicCellPostLongPress(cell)
                    cell.postDelayed({
                        val checks = listOf(
                            "dispatched" to dispatched,
                            "longPressPostNumber" to (lastLongPressPostNumber == post.postNumber),
                        )
                        val text = scenarioStatus(
                            scenarioName,
                            checks,
                            "dispatched=$dispatched longPressPostNumber=$lastLongPressPostNumber",
                        )
                        logScenarioStatus(text, checks)
                        setStatus(text)
                    }, longPressResultDelayMs())
                }
                TopicCellFixture.ReplyFooterLongPress -> {
                    val dispatched = dispatchTopicCellReplyFooterLongPress(cell)
                    cell.postDelayed({
                        val checks = listOf(
                            "dispatched" to dispatched,
                            "longPressPostNumber" to (lastLongPressPostNumber == post.postNumber),
                            "replyNotTapped" to (lastReplyPostNumber == null),
                        )
                        val text = scenarioStatus(
                            scenarioName,
                            checks,
                            "dispatched=$dispatched longPressPostNumber=$lastLongPressPostNumber replyPostNumber=$lastReplyPostNumber",
                        )
                        logScenarioStatus(text, checks)
                        setStatus(text)
                    }, longPressResultDelayMs())
                }
                TopicCellFixture.ReactionLongPress -> {
                    val dispatched = dispatchTopicCellReactionLongPress(cell)
                    cell.postDelayed({
                        val checks = listOf(
                            "dispatched" to dispatched,
                            "moreReactionsOpened" to (lastMoreReactionCount == post.reactions.size),
                            "initialReactionId" to (lastMoreReactionInitialId == post.currentUserReaction),
                            "reactionUsersNotOpened" to (lastReactionId == null),
                        )
                        val text = scenarioStatus(
                            scenarioName,
                            checks,
                            "dispatched=$dispatched moreReactionCount=$lastMoreReactionCount initialReactionId=$lastMoreReactionInitialId reactionId=$lastReactionId",
                        )
                        logScenarioStatus(text, checks)
                        setStatus(text)
                    }, longPressResultDelayMs())
                }
            }
        }, 700L)
    }

    private fun debugTopicCellPost(fixture: TopicCellFixture, cookedHtml: String, topicUrls: List<String>): Post {
        val reactions = reactionsForFixture(fixture)
        return Post(
            id = 1L,
            postNumber = 1,
            authorUsername = "debug",
            authorDisplayName = "PhotoViewer Debug",
            createdAt = "2026-06-06T00:00:00Z",
            currentUserReaction = reactions.firstOrNull { it.reacted }?.id,
            reactionUsersCount = reactions.sumOf { it.count },
            reactions = reactions,
            cookedHtml = cookedHtml,
            imageUrls = topicUrls,
        )
    }

    private fun reactionsForFixture(fixture: TopicCellFixture): List<PostReaction> {
        return when (fixture) {
            TopicCellFixture.Reaction,
            TopicCellFixture.ReactionLongPress -> listOf(
                PostReaction("heart", 3, reacted = true),
                PostReaction("rocket", 2),
                PostReaction("eyes", 1),
            )
            TopicCellFixture.MoreReaction -> listOf(
                PostReaction("heart", 5, reacted = true),
                PostReaction("rocket", 4),
                PostReaction("eyes", 3),
                PostReaction("tada", 2),
            )
            else -> emptyList()
        }
    }

    private fun replyCountForFixture(fixture: TopicCellFixture): Int {
        return if (fixture == TopicCellFixture.ReplyFooter || fixture == TopicCellFixture.ReplyFooterLongPress) 3 else 0
    }

    private fun recentReplyParticipantsForFixture(fixture: TopicCellFixture): List<Post> {
        if (fixture != TopicCellFixture.ReplyFooter && fixture != TopicCellFixture.ReplyFooterLongPress) {
            return emptyList()
        }
        return listOf(
            Post(id = 11L, postNumber = 2, authorUsername = "alice", authorDisplayName = "Alice", cookedHtml = "<p>Reply A</p>"),
            Post(id = 12L, postNumber = 3, authorUsername = "bob", authorDisplayName = "Bob", cookedHtml = "<p>Reply B</p>"),
            Post(id = 13L, postNumber = 4, authorUsername = "carol", authorDisplayName = "Carol", cookedHtml = "<p>Reply C</p>"),
        )
    }

    private fun validReactionsForFixture(fixture: TopicCellFixture): List<String> {
        return if (fixture == TopicCellFixture.MoreReaction || fixture == TopicCellFixture.ReactionLongPress) {
            listOf("heart", "rocket", "eyes", "tada")
        } else {
            emptyList()
        }
    }

    private fun nestedTopicCellHtml(targetUrl: String): String {
        return """
            <details open>
              <summary>Nested media handoff</summary>
              <ul>
                <li>
                  <table>
                    <tbody>
                      <tr>
                        <td><img src="$targetUrl" width="1600" height="1000" alt="Nested debug image"></td>
                      </tr>
                    </tbody>
                  </table>
                </li>
              </ul>
            </details>
        """.trimIndent()
    }

    private fun collageTopicCellHtml(topicUrls: List<String>): String {
        return topicUrls.joinToString(separator = "\n") { url ->
            """<p><img src="$url" width="1600" height="1000" alt="Debug collage image"></p>"""
        }
    }

    private fun oneboxTopicCellHtml(targetUrl: String): String {
        return """
            <aside class="onebox allowlistedgeneric" data-onebox-src="https://example.dev/debug-onebox">
              <header class="source"><a href="https://example.dev/debug-onebox">example.dev</a></header>
              <article class="onebox-body">
                <img class="thumbnail" src="$targetUrl" width="1600" height="1000" alt="Debug onebox thumbnail">
                <h3><a href="https://example.dev/debug-onebox">Debug onebox thumbnail</a></h3>
                <p>Link preview handoff smoke fixture</p>
              </article>
            </aside>
        """.trimIndent()
    }

    private fun pollTopicCellHtml(): String {
        return """
            <div class="poll" data-poll-name="debug-poll" data-poll-status="open" data-poll-type="regular">
              <div class="poll-container">
                <ul>
                  <li data-poll-option-id="option-a"><span class="poll-option-label">Option A</span></li>
                  <li data-poll-option-id="option-b"><span class="poll-option-label">Option B</span></li>
                </ul>
              </div>
            </div>
        """.trimIndent()
    }

    private fun inlineSpoilerTopicCellHtml(): String {
        return """<p>Visible <span class="spoiler">hidden debug text</span> tail</p>"""
    }

    private fun topicCellDebugWidth(): Int {
        val maxWidth = resources.displayMetrics.widthPixels - AndroidUtilities.dp(48f)
        return AndroidUtilities.dp(420f)
            .coerceAtMost(maxWidth)
            .coerceAtLeast(AndroidUtilities.dp(280f))
    }

    private fun openViewerFromTopicCell(
        post: Post,
        url: String,
        galleryImages: List<GalleryImage>,
        tappedSourceView: View,
        sourceBoundsProvider: (String) -> RectF?,
        sourceImageReceiverProvider: (String) -> ImageReceiver?,
        sourceBitmapProvider: (String) -> ImageReceiver.BitmapHolder?,
        sourceRadiusProvider: (String) -> IntArray?,
    ) {
        val urls = galleryImages.map { it.url }.ifEmpty { listOf(url) }
        val sourceCell = tappedSourceView as? TopicPostCell
        val viewer = PhotoViewer.getInstance()
        viewer.setParentActivity(hostFragment)
        val provider = object : PhotoViewer.EmptyPhotoViewerProvider() {
            override fun getPlaceForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                photoIndex: Int,
                needPreview: Boolean,
                closing: Boolean,
            ): PhotoViewer.PlaceProviderObject? {
                val imageUrl = urls.getOrNull(photoIndex) ?: return null
                val bounds = sourceBoundsProvider(imageUrl) ?: return null
                val imageReceiver = sourceImageReceiverProvider(imageUrl)
                if (needPreview) {
                    lastPlaceSourceInWindow = handoffRectInWindow(bounds, imageReceiver)
                }
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = (bounds.left - (imageReceiver?.imageX ?: 0f)).toInt()
                    viewY = (bounds.top - (imageReceiver?.imageY ?: 0f)).toInt()
                    viewWidth = (imageReceiver?.imageWidth ?: bounds.width()).toInt()
                    viewHeight = (imageReceiver?.imageHeight ?: bounds.height()).toInt()
                    parentView = tappedSourceView
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
                return sourceBitmapProvider(imageUrl)
            }

            override fun onClose() {
                sourceCell?.setHiddenImageUrl(null)
                Log.d(TAG, "onClose")
                showStatus("onClose visible=${PhotoViewer.getInstance().isVisibleOrAnimating()} previous=$lastStatus")
            }
        }
        val title = post.authorDisplayName.ifBlank { post.authorUsername }.ifBlank { "PhotoViewer Debug" }
        val subtitle = buildString {
            post.postNumber?.let { append('#').append(it) }
            if (post.authorUsername.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append('@').append(post.authorUsername)
            }
        }.ifBlank { null }
        val items = galleryImages.map { image ->
            PhotoViewer.UrlPhotoItem(image.url, image.thumbUrl, image.widthPx, image.heightPx, image.caption, title, subtitle)
        }.ifEmpty {
            listOf(PhotoViewer.UrlPhotoItem(url, null, 1600, 1000, null, title, subtitle))
        }
        val opened = viewer.openPhotoItems(items, items.indexOfFirst { it.url == url }.coerceAtLeast(0), provider)
        if (!opened) {
            sourceCell?.setHiddenImageUrl(null)
        }
        setStatus("topicCellOpened=$opened index=${currentPhotoIndex()} visible=${viewer.isVisibleOrAnimating()}")
    }

    private fun dispatchTopicCellImageTap(cell: TopicPostCell, targetUrl: String): Boolean {
        val boundsInWindow = cell.imageBoundsInWindowForUrl(targetUrl)
        val location = IntArray(2)
        cell.getLocationInWindow(location)
        val fallbackBlockLeft = privateIntArray(cell, "blockLefts").firstOrNull() ?: 0
        val fallbackBlockWidth = privateIntArray(cell, "blockWidths").firstOrNull() ?: cell.width
        val fallbackBlockTop = privateIntArray(cell, "blockTops").firstOrNull() ?: AndroidUtilities.dp(64f)
        val fallbackBlockHeight = privateIntArray(cell, "blockHeights").firstOrNull() ?: AndroidUtilities.dp(160f)
        val x = boundsInWindow?.centerX()?.minus(location[0])
            ?: fallbackBlockLeft + fallbackBlockWidth / 2f
        val y = boundsInWindow?.centerY()?.minus(location[1])
            ?: fallbackBlockTop + (fallbackBlockHeight / 2f).coerceAtMost(AndroidUtilities.dp(120f).toFloat())
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 45L, MotionEvent.ACTION_UP, x, y, 0)
        val downHandled = cell.dispatchTouchEvent(down)
        val upHandled = cell.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        return downHandled || upHandled
    }

    private fun dispatchTopicCellPollVoteTap(cell: TopicPostCell): Boolean {
        val rect = firstPollSelectorRectInCell(cell)
            ?: fallbackFirstBlockRect(cell)
            ?: return false
        return dispatchTapToCell(cell, rect.centerX(), rect.centerY())
    }

    private fun dispatchTopicCellInlineSpoilerTap(cell: TopicPostCell): Boolean {
        val rect = firstInlineSpoilerRectInCell(cell)
            ?: fallbackFirstBlockRect(cell)
            ?: return false
        return dispatchTapToCell(cell, rect.centerX(), rect.centerY())
    }

    private fun dispatchTopicCellReplyFooterTap(cell: TopicPostCell): Boolean {
        val rect = replyFooterRectInCell(cell) ?: return false
        return dispatchTapToCell(cell, rect.centerX(), rect.centerY())
    }

    private fun dispatchTopicCellReactionTap(cell: TopicPostCell, more: Boolean): Boolean {
        val rect = reactionButtonRectInCell(cell, more) ?: return false
        return dispatchTapToCell(cell, rect.centerX(), rect.centerY())
    }

    private fun dispatchTopicCellPostLongPress(cell: TopicPostCell): Boolean {
        val x = AndroidUtilities.dp(18f) + AndroidUtilities.dp(8f)
        val y = AndroidUtilities.dp(12f) + AndroidUtilities.dp(8f)
        return dispatchLongPressToCell(cell, x.toFloat(), y.toFloat())
    }

    private fun dispatchTopicCellReplyFooterLongPress(cell: TopicPostCell): Boolean {
        val rect = replyFooterRectInCell(cell) ?: return false
        return dispatchLongPressToCell(cell, rect.centerX(), rect.centerY())
    }

    private fun dispatchTopicCellReactionLongPress(cell: TopicPostCell): Boolean {
        val rect = reactionButtonRectInCell(cell, more = false) ?: return false
        return dispatchLongPressToCell(cell, rect.centerX(), rect.centerY())
    }

    private fun dispatchTapToCell(cell: TopicPostCell, x: Float, y: Float): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 45L, MotionEvent.ACTION_UP, x, y, 0)
        val downHandled = cell.dispatchTouchEvent(down)
        val upHandled = cell.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        return downHandled || upHandled
    }

    private fun dispatchLongPressToCell(cell: TopicPostCell, x: Float, y: Float): Boolean {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val downHandled = cell.dispatchTouchEvent(down)
        down.recycle()
        cell.postDelayed({
            val upTime = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)
            cell.dispatchTouchEvent(up)
            up.recycle()
        }, ViewConfiguration.getLongPressTimeout().toLong() + 80L)
        return downHandled
    }

    private fun longPressResultDelayMs(): Long {
        return ViewConfiguration.getLongPressTimeout().toLong() + 260L
    }

    private fun firstPollSelectorRectInCell(cell: TopicPostCell): RectF? {
        val blocks = privateList(cell, "bodyBlocks")
        val index = blocks.indexOfFirst { it.javaClass.simpleName == "PollBlock" }
        if (index < 0) return null
        val buttons = privateList(blocks[index], "pollButtons")
        val selector = buttons.firstOrNull()?.let { reflectedField(it, "selectorBounds") as? RectF } ?: return null
        return RectF(selector).apply {
            offset(blockLeft(cell, index).toFloat(), blockTop(cell, index).toFloat())
        }
    }

    private fun firstInlineSpoilerRectInCell(cell: TopicPostCell): RectF? {
        val blocks = privateList(cell, "bodyBlocks")
        val index = blocks.indexOfFirst { it.javaClass.simpleName == "ParagraphBlock" }
        if (index < 0) return null
        val spoilers = privateList(blocks[index], "spoilers")
        val bounds = spoilers.firstOrNull()?.let { spoiler ->
            runCatching { spoiler.javaClass.getMethod("getBounds").invoke(spoiler) as? android.graphics.Rect }.getOrNull()
        } ?: return null
        val topPadding = reflectedIntMethod(blocks[index], "topPaddingPx") ?: AndroidUtilities.dp(8f)
        return RectF(bounds).apply {
            offset(blockLeft(cell, index).toFloat(), blockTop(cell, index) + topPadding.toFloat())
        }
    }

    private fun replyFooterRectInCell(cell: TopicPostCell): RectF? {
        val chromeLeft = reflectedField(cell, "chromeLeft") as? Int ?: 0
        val top = reflectedField(cell, "replyFooterTop") as? Int ?: return null
        val rect = reflectedField(cell, "replyFooterSelectorRect") as? RectF
        if (rect != null && rect.width() > 0f && rect.height() > 0f) {
            return RectF(rect).apply {
                offset(chromeLeft.toFloat(), top.toFloat())
            }
        }
        val height = reflectedField(cell, "measuredReplyFooterHeight") as? Int ?: return null
        if (height <= 0) return null
        return RectF(
            chromeLeft.toFloat(),
            top.toFloat(),
            (chromeLeft + AndroidUtilities.dp(120f)).toFloat().coerceAtMost(cell.width.toFloat()),
            (top + height).toFloat(),
        )
    }

    private fun reactionButtonRectInCell(cell: TopicPostCell, more: Boolean): RectF? {
        val buttons = privateList(cell, "reactionButtons")
        val button = buttons.firstOrNull { candidate ->
            (reflectedField(candidate, "postReaction") == null) == more
        } ?: return null
        val chromeLeft = reflectedField(cell, "chromeLeft") as? Int ?: 0
        val rowTop = reflectedField(cell, "reactionRowTop") as? Int ?: return null
        val x = reflectedField(button, "x") as? Int ?: return null
        val y = reflectedField(button, "y") as? Int ?: return null
        val width = reflectedField(button, "width") as? Int ?: return null
        val height = reflectedField(button, "height") as? Int ?: return null
        if (width <= 0 || height <= 0) return null
        return RectF(
            (chromeLeft + x).toFloat(),
            (rowTop + y).toFloat(),
            (chromeLeft + x + width).toFloat(),
            (rowTop + y + height).toFloat(),
        )
    }

    private fun fallbackFirstBlockRect(cell: TopicPostCell): RectF? {
        val left = privateIntArray(cell, "blockLefts").firstOrNull() ?: return null
        val top = privateIntArray(cell, "blockTops").firstOrNull() ?: return null
        val width = privateIntArray(cell, "blockWidths").firstOrNull() ?: return null
        val height = privateIntArray(cell, "blockHeights").firstOrNull() ?: return null
        return RectF(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat())
    }

    private fun blockLeft(cell: TopicPostCell, index: Int): Int {
        return privateIntArray(cell, "blockLefts").getOrNull(index) ?: 0
    }

    private fun blockTop(cell: TopicPostCell, index: Int): Int {
        return privateIntArray(cell, "blockTops").getOrNull(index) ?: 0
    }

    private fun handoffRectInWindow(bounds: RectF, imageReceiver: ImageReceiver?): RectF {
        if (imageReceiver == null) return RectF(bounds)
        val drawRegion = imageReceiver.drawRegion
        val receiverLeft: Float
        val receiverTop: Float
        val receiverRight: Float
        val receiverBottom: Float
        if (!drawRegion.isEmpty) {
            receiverLeft = drawRegion.left
            receiverTop = drawRegion.top
            receiverRight = drawRegion.right
            receiverBottom = drawRegion.bottom
        } else {
            receiverLeft = imageReceiver.imageX
            receiverTop = imageReceiver.imageY
            receiverRight = imageReceiver.imageX2
            receiverBottom = imageReceiver.imageY2
        }
        val viewX = bounds.left - imageReceiver.imageX
        val viewY = bounds.top - imageReceiver.imageY
        return RectF(viewX + receiverLeft, viewY + receiverTop, viewX + receiverRight, viewY + receiverBottom)
    }

    private fun privateIntArray(target: Any, fieldName: String): IntArray {
        return reflectedField(target, fieldName) as IntArray
    }

    @Suppress("UNCHECKED_CAST")
    private fun privateList(target: Any, fieldName: String): List<Any> {
        return reflectedField(target, fieldName) as? List<Any> ?: emptyList()
    }

    private fun runScenario(scenario: String?) {
        when (scenario) {
            SCENARIO_SWIPE_NEXT -> sourceView.postDelayed({
                val dispatched = dispatchSwipe(0.82f, 0.5f, 0.16f, 0.5f, 360L)
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched currentIndex=${currentPhotoIndex()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 500L)
            }, 700L)
            SCENARIO_DRAG_DISMISS -> sourceView.postDelayed({
                val dispatched = dispatchSwipe(0.5f, 0.46f, 0.5f, 0.82f, 360L)
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched currentIndex=${currentPhotoIndex()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 500L)
            }, 700L)
            SCENARIO_DOUBLE_TAP_ZOOM -> sourceView.postDelayed({
                val dispatched = dispatchDoubleTap(0.5f, 0.5f)
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched scale=${viewerScale()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 650L)
            }, 700L)
            SCENARIO_PINCH_ZOOM -> sourceView.postDelayed({
                val dispatched = dispatchPinch(0.5f, 0.5f, 0.12f, 0.54f, 760L)
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched scale=${viewerScale()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 650L)
            }, 700L)
            SCENARIO_HANDOFF_RECT -> sourceView.postDelayed({
                val source = sourceView.imageBoundsInWindow()
                val handoff = lastPlaceSourceInWindow
                val start = viewerRect("startImageRect")
                val startAbs = start?.let(::RectF)?.apply {
                    val location = photoViewerContainerLocationInWindow()
                    offset(location[0].toFloat(), location[1].toFloat())
                }
                val text = "scenario=$scenario source=${source.toShortString()} handoff=${handoff?.toShortString()} startAbs=${startAbs?.toShortString()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                Log.i(TAG, text)
                setStatus(text)
            }, 650L)
            SCENARIO_BACK_CLOSE -> sourceView.postDelayed({
                val dispatched = dispatchBack()
                sourceView.postDelayed({
                    val text = "scenario=$scenario dispatched=$dispatched currentIndex=${currentPhotoIndex()} visible=${PhotoViewer.getInstance().isVisibleOrAnimating()}"
                    Log.i(TAG, text)
                    setStatus(text)
                }, 500L)
            }, 700L)
        }
    }

    private fun dispatchDoubleTap(x: Float, y: Float): Boolean {
        val first = dispatchTap(x, y)
        val second = sourceView.postDelayed({ dispatchTap(x, y) }, 90L)
        return first && second
    }

    private fun dispatchTap(x: Float, y: Float): Boolean {
        val target = photoViewerWindowView() ?: return false
        val width = target.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = target.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, width * x, height * y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 45L, MotionEvent.ACTION_UP, width * x, height * y, 0)
        val downHandled = target.dispatchTouchEvent(down)
        val upHandled = target.dispatchTouchEvent(up)
        return downHandled || upHandled
    }

    private fun dispatchSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): Boolean {
        val target = photoViewerWindowView() ?: return false
        val width = target.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = target.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val start = SystemClock.uptimeMillis()
        val steps = 8
        fun event(action: Int, step: Int): MotionEvent {
            val progress = step / steps.toFloat()
            return MotionEvent.obtain(
                start,
                start + (durationMs * progress).toLong(),
                action,
                width * (fromX + (toX - fromX) * progress),
                height * (fromY + (toY - fromY) * progress),
                0,
            )
        }
        var handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 0))
        for (step in 1 until steps) {
            handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, step)) || handled
        }
        handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_UP, steps)) || handled
        return handled
    }

    private fun dispatchPinch(centerX: Float, centerY: Float, startSpan: Float, endSpan: Float, durationMs: Long): Boolean {
        val target = photoViewerWindowView() ?: return false
        val width = target.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = target.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val downTime = SystemClock.uptimeMillis()
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER },
        )
        fun coords(span: Float): Array<MotionEvent.PointerCoords> {
            val half = width * span / 2f
            val y = height * centerY
            return arrayOf(
                MotionEvent.PointerCoords().apply { x = width * centerX - half; this.y = y; pressure = 1f; size = 1f },
                MotionEvent.PointerCoords().apply { x = width * centerX + half; this.y = y; pressure = 1f; size = 1f },
            )
        }
        val steps = 16
        fun event(action: Int, pointerCount: Int, pointerCoords: Array<MotionEvent.PointerCoords>, step: Int): MotionEvent {
            return MotionEvent.obtain(
                downTime,
                downTime + durationMs * step / steps,
                action,
                pointerCount,
                properties,
                pointerCoords,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        }
        var handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 1, coords(startSpan), 0))
        handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, coords(startSpan), 1)) || handled
        for (step in 2 until steps) {
            val progress = (step - 1) / (steps - 1).toFloat()
            val span = startSpan + (endSpan - startSpan) * progress
            handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 2, coords(span), step)) || handled
        }
        handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, coords(endSpan), steps)) || handled
        handled = target.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 1, coords(endSpan), steps)) || handled
        return handled
    }

    private fun dispatchBack(): Boolean {
        val target = photoViewerWindowView() ?: return false
        val eventTime = SystemClock.uptimeMillis()
        val down = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0)
        val up = KeyEvent(eventTime, eventTime + 40L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
        val downHandled = target.dispatchKeyEvent(down)
        val upHandled = target.dispatchKeyEvent(up)
        return downHandled || upHandled
    }

    private fun scenarioStatus(scenario: String, checks: List<Pair<String, Boolean>>, details: String): String {
        val failures = checks.filterNot { it.second }.joinToString(",") { it.first }.ifBlank { "none" }
        return "scenario=$scenario passed=${failures == "none"} failures=$failures $details"
    }

    private fun logScenarioStatus(text: String, checks: List<Pair<String, Boolean>>) {
        if (checks.all { it.second }) {
            Log.i(TAG, text)
        } else {
            Log.e(TAG, text)
        }
    }

    private fun setStatus(text: String) {
        lastStatus = text
        showStatus(text)
    }

    private fun showStatus(text: String) {
        statusView.text = "PhotoViewer debug: $text"
    }

    private fun photoViewerWindowView(): View? {
        val window = photoViewerWindow() ?: return null
        val viewField = window.javaClass.getDeclaredField("windowView").apply { isAccessible = true }
        return viewField.get(window) as? View
    }

    private fun photoViewerContainerView(): View? {
        val window = photoViewerWindow() ?: return null
        val viewField = window.javaClass.getDeclaredField("containerView").apply { isAccessible = true }
        return viewField.get(window) as? View
    }

    private fun currentPhotoIndex(): Int {
        val field = PhotoViewer::class.java.getDeclaredField("currentIndex").apply { isAccessible = true }
        return field.getInt(PhotoViewer.getInstance())
    }

    private fun viewerScale(): Float {
        val window = photoViewerWindow() ?: return -1f
        (reflectedField(window, "scale") as? Float)?.let { return it }
        val container = reflectedField(window, "containerView") ?: return -1f
        return reflectedField(container, "scale") as? Float ?: -1f
    }

    private fun viewerRect(fieldName: String): RectF? {
        val window = photoViewerWindow() ?: return null
        val container = reflectedField(window, "containerView")
        val rect = reflectedField(window, fieldName) as? RectF
            ?: container?.let { reflectedField(it, fieldName) as? RectF }
        return rect?.let(::RectF)
    }

    private fun reflectedField(target: Any, fieldName: String): Any? {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val value = runCatching {
                current.getDeclaredField(fieldName).apply { isAccessible = true }.get(target)
            }.getOrNull()
            if (value != null) {
                return value
            }
            current = current.superclass
        }
        return null
    }

    private fun setReflectedField(target: Any, fieldName: String, value: Any?) {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val field = runCatching {
                current.getDeclaredField(fieldName).apply { isAccessible = true }
            }.getOrNull()
            if (field != null) {
                field.set(target, value)
                return
            }
            current = current.superclass
        }
    }

    private fun invokeReflectedMethod(target: Any, methodName: String, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val method = runCatching {
                current.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            }.getOrNull()
            if (method != null) {
                return method.invoke(target, *args)
            }
            current = current.superclass
        }
        return null
    }

    private fun countDescendants(view: View?, predicate: (View) -> Boolean): Int {
        if (view == null) {
            return 0
        }
        var count = if (predicate(view)) 1 else 0
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                count += countDescendants(view.getChildAt(i), predicate)
            }
        }
        return count
    }

    private fun findDescendant(view: View?, predicate: (View) -> Boolean): View? {
        if (view == null) {
            return null
        }
        if (predicate(view)) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findDescendant(view.getChildAt(i), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun dismissReflectedReplyPanel(panel: View?) {
        if (panel == null) {
            return
        }
        runCatching {
            panel.javaClass.getDeclaredMethod("dismiss", Boolean::class.javaPrimitiveType).apply {
                isAccessible = true
            }.invoke(panel, false)
        }
    }

    private fun reflectedIntMethod(target: Any, methodName: String): Int? {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val value = runCatching {
                current.getDeclaredMethod(methodName).apply { isAccessible = true }.invoke(target) as? Int
            }.getOrNull()
            if (value != null) {
                return value
            }
            current = current.superclass
        }
        return null
    }

    private fun photoViewerContainerLocationInWindow(): IntArray {
        val location = IntArray(2)
        photoViewerContainerView()?.getLocationInWindow(location)
        return location
    }

    private fun photoViewerWindow(): Any? {
        return PhotoViewer.getInstance()
    }

    private fun createDebugImageFiles(): List<String> {
        return DEBUG_COLORS.mapIndexed { index, colors ->
            val file = File(cacheDir, "photo-viewer-debug-${index + 1}.jpg")
            val bitmap = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f,
                0f,
                bitmap.width.toFloat(),
                bitmap.height.toFloat(),
                colors.first,
                colors.second,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
            paint.shader = null
            paint.color = 0x33ffffff
            canvas.drawCircle(bitmap.width * 0.72f, bitmap.height * 0.32f, 180f, paint)
            paint.color = 0x44000000
            canvas.drawRoundRect(120f, 650f, 1480f, 860f, 64f, 64f, paint)
            paint.color = 0xffffffff.toInt()
            paint.textSize = 92f
            paint.isFakeBoldText = true
            canvas.drawText("PhotoViewer ${index + 1}", 180f, 780f, paint)
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
            bitmap.recycle()
            file.absolutePath
        }
    }

    private class SourcePreviewView(context: android.content.Context) : View(context) {
        val imageReceiver = ImageReceiver(this).apply {
            setAllowLoadingOnAttachedOnly(false)
            setRoundRadius(AndroidUtilities.dp(14f))
        }
        private val bounds = RectF()
        private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setWillNotDraw(false)
        }

        fun setImageUrl(url: String) {
            imageReceiver.setImage(ImageLocation.getForPath(url), null, null, null, url, 0)
        }

        fun ensureReceiverBounds() {
            bounds.set(AndroidUtilities.dp(24f).toFloat(), AndroidUtilities.dp(18f).toFloat(), width - AndroidUtilities.dp(18f).toFloat(), height - AndroidUtilities.dp(22f).toFloat())
            imageReceiver.setImageCoords(bounds)
        }

        fun imageBoundsInWindow(): RectF {
            ensureReceiverBounds()
            val location = IntArray(2)
            getLocationInWindow(location)
            return RectF(bounds).apply { offset(location[0].toFloat(), location[1].toFloat()) }
        }

        fun imageHandoffRectInWindow(): RectF {
            ensureReceiverBounds()
            val location = IntArray(2)
            getLocationInWindow(location)
            val drawRegion = imageReceiver.drawRegion
            val rect = if (!drawRegion.isEmpty) {
                RectF(drawRegion)
            } else {
                RectF(imageReceiver.imageX, imageReceiver.imageY, imageReceiver.imageX2, imageReceiver.imageY2)
            }
            return rect.apply { offset(location[0].toFloat(), location[1].toFloat()) }
        }

        fun bitmapHolder(): ImageReceiver.BitmapHolder {
            return imageReceiver.getBitmapSafe() ?: ImageReceiver.BitmapHolder(createFallbackBitmap())
        }

        fun radius(): IntArray = imageReceiver.getRoundRadius(true).copyOf()

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            imageReceiver.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            imageReceiver.onDetachedFromWindow()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            ensureReceiverBounds()
            placeholderPaint.shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                0xff355c7d.toInt(),
                0xffc06c84.toInt(),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(bounds, AndroidUtilities.dp(14f).toFloat(), AndroidUtilities.dp(14f).toFloat(), placeholderPaint)
            placeholderPaint.shader = null
            imageReceiver.draw(canvas)
        }

        private fun createFallbackBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            draw(canvas)
            return bitmap
        }
    }

    private class DebugRootLayout(context: android.content.Context) : FrameLayout(context), INavigationLayout {
        override fun presentFragment(params: INavigationLayout.NavigationParams): Boolean {
            return false
        }

        override fun addFragmentToStack(fragment: BaseFragment, position: Int): Boolean {
            return false
        }

        override fun closeLastFragment(animated: Boolean, forceNoAnimation: Boolean) = Unit

        override fun getFragmentStack(): List<BaseFragment> {
            return emptyList()
        }

        override fun getOverlayContainerView(): FrameLayout {
            return this
        }
    }

    private enum class TopicCellFixture {
        SingleImage,
        NestedImage,
        Collage,
        OneboxThumbnail,
        PollVote,
        InlineSpoiler,
        ReplyFooter,
        Reaction,
        MoreReaction,
        PostLongPress,
        ReplyFooterLongPress,
        ReactionLongPress,
    }

    private companion object {
        private const val EXTRA_URLS = "urls"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_CLOSE_AFTER_MS = "closeAfterMs"
        private const val EXTRA_SCENARIO = "scenario"
        private const val SCENARIO_SWIPE_NEXT = "swipeNext"
        private const val SCENARIO_DRAG_DISMISS = "dragDismiss"
        private const val SCENARIO_DOUBLE_TAP_ZOOM = "doubleTapZoom"
        private const val SCENARIO_PINCH_ZOOM = "pinchZoom"
        private const val SCENARIO_HANDOFF_RECT = "handoffRect"
        private const val SCENARIO_TOPIC_CELL_TAP = "topicCellTap"
        private const val SCENARIO_TOPIC_CELL_NESTED_IMAGE_TAP = "topicCellNestedImageTap"
        private const val SCENARIO_TOPIC_CELL_COLLAGE_TAP = "topicCellCollageTap"
        private const val SCENARIO_TOPIC_CELL_ONEBOX_THUMBNAIL_TAP = "topicCellOneboxThumbnailTap"
        private const val SCENARIO_TOPIC_CELL_POLL_VOTE = "topicCellPollVote"
        private const val SCENARIO_TOPIC_CELL_INLINE_SPOILER_TAP = "topicCellInlineSpoilerTap"
        private const val SCENARIO_TOPIC_CELL_REPLY_FOOTER_TAP = "topicCellReplyFooterTap"
        private const val SCENARIO_TOPIC_CELL_REACTION_TAP = "topicCellReactionTap"
        private const val SCENARIO_TOPIC_CELL_MORE_REACTION_TAP = "topicCellMoreReactionTap"
        private const val SCENARIO_TOPIC_CELL_POST_LONG_PRESS = "topicCellPostLongPress"
        private const val SCENARIO_TOPIC_CELL_REPLY_FOOTER_LONG_PRESS = "topicCellReplyFooterLongPress"
        private const val SCENARIO_TOPIC_CELL_REACTION_LONG_PRESS = "topicCellReactionLongPress"
        private const val SCENARIO_TOPIC_DETAIL_INTERACTION = "topicDetailInteraction"
        private const val SCENARIO_BACK_CLOSE = "backClose"
        private const val DEBUG_BASE_URL = "https://linux.do/"
        private const val TAG = "PhotoViewerDebugActivity"
        private val DEBUG_COLORS = listOf(
            0xff1c7c54.toInt() to 0xff73e2a7.toInt(),
            0xff214f9c.toInt() to 0xfff0b429.toInt(),
            0xff8f2d56.toInt() to 0xffff7a59.toInt(),
        )
    }
}

package io.github.baba983.nativedo.ui.topic

import android.animation.ValueAnimator
import android.content.Context
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MotionEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.findCookedPoll
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import io.github.baba983.nativedo.core.model.DiscourseEmoji
import io.github.baba983.nativedo.core.model.DiscourseEmojiCategory
import io.github.baba983.nativedo.core.media.PickedMediaResolver
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionRendering
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.postBodyAccessibilityText
import io.github.baba983.nativedo.core.ui.isAnimatedDiscourseImageUrl
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.data.TopicSearchRepository
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraft
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftKey
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionKey
import io.github.baba983.nativedo.feature.topic.data.TopicMentionRepository
import io.github.baba983.nativedo.feature.topic.ui.TopicReplySheetItem
import io.github.baba983.nativedo.feature.topic.ui.TopicReplyTargetResolver
import io.github.baba983.nativedo.feature.topic.ui.TopicPostRepliesUiState
import io.github.baba983.nativedo.feature.topic.ui.PollVotersSheetArgs
import io.github.baba983.nativedo.feature.topic.ui.TopicReactionUsersUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicSearchNavigator
import io.github.baba983.nativedo.feature.topic.ui.TopicSearchRequest
import io.github.baba983.nativedo.feature.topic.ui.TopicSearchSession
import io.github.baba983.nativedo.feature.topic.ui.topicScopedSearchQuery
import io.github.baba983.nativedo.feature.topic.ui.TOPIC_REACTION_ALL_FILTER_ID
import io.github.baba983.nativedo.feature.topic.ui.TopicUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicViewModel
import io.github.baba983.nativedo.feature.topic.ui.isReadPositionOnlyUpdateFrom
import io.github.baba983.nativedo.feature.topic.ui.TopicDateHeader
import io.github.baba983.nativedo.feature.topic.ui.topicFloorLabelForPostNumber
import io.github.baba983.nativedo.feature.topic.ui.topicPostDateEntries
import io.github.baba983.nativedo.feature.topic.ui.topicPostDisplayName
import io.github.baba983.nativedo.feature.topic.ui.topicPostTimeLabel
import io.github.baba983.nativedo.feature.topic.ui.topicReplySheetItem
import io.github.baba983.nativedo.ui.components.DiscourseUserCell
import io.github.baba983.nativedo.ui.components.FixedHeaderRecyclerBottomSheet
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.cell.TopicFooterCell
import io.github.baba983.nativedo.ui.topic.cell.TopicDateCell
import io.github.baba983.nativedo.ui.topic.cell.TopicHeaderCell
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCellDelegate
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LiteMode
import org.telegram.messenger.MessageObject
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.PhotoViewer
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.AnimatedTextView
import org.telegram.ui.Components.AnimatedFloat
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.BottomSheetWithRecyclerListView
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ButtonBounce
import org.telegram.ui.Components.ChatReplyContainer
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView
import org.telegram.ui.Components.ChatActivityEnterTopView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EditTextCaption
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerAnimationScrollHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode
import org.telegram.ui.Components.chat.layouts.ChatActivitySideControlsButtonsLayout
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView
import org.telegram.ui.Components.chat.ChatActivityBottomViewsVisibilityController
import org.telegram.ui.Components.chat.ChatInputViewsContainer
import org.telegram.ui.Components.chat.ViewPositionWatcher
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider
import org.telegram.ui.recyclerview.LinearSmoothScrollerCustom
import org.telegram.ui.Components.inset.WindowInsetsStateHolder
import org.telegram.messenger.utils.RectFMergeBounding
import org.telegram.messenger.R as TelegramR
import java.net.URLEncoder
import kotlin.math.roundToInt

private const val TOPIC_SEARCH_HIGHLIGHT_PAYLOAD = "topic_search_highlight"

/**
 * The global theme as a [Theme.ResourcesProvider]: upstream's `getColor(key, null)` path, spelled
 * as the interface so a themed sheet can take it. `BaseFragment.getResourceProvider()` returns
 * null, and the sheets this screen mounts require one.
 */
private object AppThemeResourcesProvider : Theme.ResourcesProvider {
    override fun getColor(key: Int): Int = Theme.getColor(key)
}

data class TopicInitialIdentity(
    val username: String,
    val avatarTemplate: String,
    val animatedAvatarUrl: String,
)

internal fun TopicSummary.toTopicInitialIdentity(): TopicInitialIdentity = TopicInitialIdentity(
    username = authorUsername,
    avatarTemplate = authorAvatarTemplate,
    animatedAvatarUrl = authorAnimatedAvatarUrl,
)

class TopicDetailFragment @JvmOverloads constructor(
    private val topicId: Long,
    private val initialTitle: String? = null,
    private val initialPostNumber: Int? = null,
    private val initialPostId: Long? = null,
    private val initialIdentity: TopicInitialIdentity? = null,
    private val debugFromTop: Boolean = false,
) : BaseFragment(), NotificationCenter.NotificationCenterDelegate {
    private var viewportStore: io.github.baba983.nativedo.feature.topic.data.TopicViewportStore? = null
    private var viewportKey: TopicReadPositionKey? = null
    private var initialViewport: io.github.baba983.nativedo.feature.topic.data.TopicViewport? = null
    private var initialAnchorApplied = false
    private lateinit var viewModel: TopicViewModel
    private lateinit var adapter: TopicDetailAdapter
    private lateinit var listView: TopicSwipeReplyRecyclerView
    private lateinit var topicSearchRepository: TopicSearchRepository
    private lateinit var baseUrl: String
    private var collectorScope = MainScope()
    private var reactionUsersSheet: TopicReactionUsersSheet? = null
    private var postOptions: ActionBarPopupWindow? = null
    private var reactionRailPopup: ActionBarPopupWindow? = null
    private var expandedReactionPickerPopup: ActionBarPopupWindow? = null
    private var expandedReactionPickerPanel: TopicReactionPickerExpandedPanel? = null
    private var reactionEffectOverlay: TopicReactionEffectOverlay? = null
    private var repliesSheet: TopicRepliesSheet? = null
    private var replyComposerPanel: TopicReplyComposerPanel? = null
    private var replyComposerEmojiPanel: TopicReplyComposerEmojiPanel? = null
    private var topicInputViewsContainer: ChatInputViewsContainer? = null
    private var topicInsetsStateHolder: WindowInsetsStateHolder? = null
    private var topicAnimatedInsetsProvider: WindowAnimatedInsetsProvider? = null
    private val topicBottomViewsVisibilityController =
        ChatActivityBottomViewsVisibilityController(::onTopicBottomItemsVisibilityChanged)
    private var mentionSuggestionsView: TopicMentionSuggestionsView? = null
    private lateinit var mentionRepository: TopicMentionRepository
    private var mentionBlurSourceColor: BlurredBackgroundSourceColor? = null
    private var mentionBlurSource: BlurredBackgroundSourceRenderNode? = null
    private var mentionBlurSourceFrosted: BlurredBackgroundSourceRenderNode? = null
    private var mentionBlurNoiseSuppressor: DownscaleScrollableNoiseSuppressor? = null
    private var mentionBlurCapture: ViewGroupPartRenderer? = null
    private val mentionBlurPositions = ArrayList<RectF>()
    private val mentionBlurPositionsMerged = ArrayList<RectF>()
    private var mentionBlurWatcher: ViewPositionWatcher? = null
    private var mentionBackground: BlurredBackgroundDrawable? = null
    private var topicTopFadeView: ChatActivityFadeView? = null
    private var topicContentPanTranslation = 0f
    private var topicContentPanTranslationT = 0f
    private var topicFragmentPaused = false
    private var replyComposerEmojiPanelHeight: Int = 0
    private var replySheetEntriesByPostNumber: Map<Int, List<TopicReplySheetItem>> = emptyMap()
    private var rootFrameLayout: TopicDetailHostLayout? = null
    private var replyComposerKeyboardHeight: Int = 0
    private var replyComposerLastKeyboardHeight: Int = AndroidUtilities.dp(200f)
    private var topicLastImeVisible = false
    private var topicInitialInsetsSyncScheduled = false
    private val topicAdjustResizeOwnerClassGuid = System.identityHashCode(this)
    private var replyComposerPopupContentType = TopicReplyComposerPopupContentType.NONE
    private var replyComposerWaitingForKeyboardOpenAfterEmoji = false
    private val clearReplyComposerWaitingForKeyboardOpenRunnable = Runnable {
        replyComposerWaitingForKeyboardOpenAfterEmoji = false
    }
    private var postOptionsListScrollLocked = false
    private var validReactionIds: List<String> = emptyList()
    private var emojiCatalog: Map<String, String> = emptyMap()
    private var emojiCategories: List<DiscourseEmojiCategory> = emptyList()
    private var topicTitle: String = initialTitle.orEmpty()
    private var svgSpriteMarkup: String = ""
    private var collapsedTitleVisible: Boolean = false
    private var listBottomBasePadding: Int = 0
    private var topicListUserScrollActive: Boolean = false
    private var topicListUserHasScrolled: Boolean = false
    private var topicInitialContentAnchored: Boolean = false
    private var initialPostJumpRequested: Boolean = false
    private var latestTopicState: TopicUiState = TopicUiState()
    private var pageDownControls: ChatActivitySideControlsButtonsLayout? = null
    private var pageDownReachedLatest: Boolean = false
    private var pageDownInitialized: Boolean = false
    private var pageDownLastKnownBelowPosts: Int = 0
    private var pageDownLastKnownAbovePosts: Int = 0
    private var pageDownSubmittedCount: Int = -1

    // Telegram ChatActivity's page-down state machine: canShowPagedownButton,
    // pagedownButtonShowedByScroll, totalDy, and scrollValue.
    private val pageDownScrollThresholdPx: Int get() = AndroidUtilities.dp(100f)
    private var pageDownCanShowFromScroll: Boolean = true
    private var pageDownShownByScroll: Boolean = false
    private var pageDownScrollAccumPx: Int = 0
    private var floatingDateController: TopicFloatingDateController? = null
    private var floatingDateView: TopicDateCell? = null
    private var topicSearchItem: ActionBarMenuItem? = null
    private var topicHeaderItem: ActionBarMenuItem? = null
    private var topicIdentityContainer: TopicIdentityContainer? = null
    private var topicSearchPanel: FrameLayout? = null
    private var topicSearchCountText: AnimatedTextView? = null
    private var topicSearchJob: Job? = null
    private val topicSearchSession = TopicSearchSession()
    private val topicSearchNavigator = TopicSearchNavigator(topicId)
    private var topicSearchOpen = false
    private var topicSearchQuery = ""
    private var topicSearchPendingNext = false
    private val textSelectionHelper = TopicPostTextSelectionHelper()

    override fun onFragmentCreate(): Boolean {
        if (!super.onFragmentCreate()) {
            return false
        }
        NotificationCenter.getGlobalInstance().addObserver(
            this,
            NotificationCenter.didSetNewWallpapper,
        )
        return true
    }

    override fun createView(context: Context): View {
        actionBar.setBackButtonDrawable(BackDrawable(false))
        // Identity container owns the visible title; keep ActionBar title empty.
        actionBar.setTitle("")
        actionBar.setCastShadows(false)
        // Chat: actionBar.setAddToContainer(false) so the bar lives inside
        // contentView and the list draws under the glass chrome.
        actionBar.setAddToContainer(false)
        AndroidUtilities.requestAdjustResize(parentActivity, topicAdjustResizeOwnerClassGuid)

        val appContainer = (context.applicationContext as NativeDoApp).appContainer
        baseUrl = appContainer.baseUrl
        topicSearchRepository = TopicSearchRepository(appContainer.discourseApiClient)
        mentionRepository = TopicMentionRepository(appContainer.discourseApiClient)
        svgSpriteMarkup = appContainer.svgSpriteStore.sprite.value
        createTopicActionBarMenu(context)
        val sessionSnapshot = appContainer.sessionStore.snapshot()
        val sessionUsername = sessionSnapshot?.username?.takeIf { it.isNotBlank() }
        viewportStore = appContainer.topicViewportStore
        viewportKey = TopicReadPositionKey(baseUrl, sessionUsername.orEmpty(), topicId)
        initialViewport = if (initialPostNumber == null && initialPostId == null && !debugFromTop) {
            viewportKey?.let { viewportStore?.load(it) }
        } else null
        viewModel = ViewModelProvider(
            this,
            TopicViewModelFactory(
                repository = TopicRepository(appContainer.discourseApiClient),
                topicId = topicId,
                replyDraftStore = appContainer.topicReplyDraftStore,
                replyDraftKey = sessionUsername?.let { username ->
                    TopicReplyDraftKey(baseUrl = appContainer.baseUrl, username = username, topicId = topicId)
                },
                readPositionStore = appContainer.topicReadPositionStore,
                readPositionKey = sessionUsername?.let { username ->
                    TopicReadPositionKey(baseUrl = appContainer.baseUrl, username = username, topicId = topicId)
                },
                readReportScope = appContainer.applicationScope,
                canTrackReadPosition = sessionSnapshot != null,
                initialPostNumber = if (debugFromTop) 1 else initialPostNumber ?: initialViewport?.postNumber,
            ),
        )[TopicViewModel::class.java]

        val root = object : TopicDetailHostLayout(context) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                val selectionOverlay = textSelectionHelper.getOverlayView(context)
                val textSelectionEvent = MotionEvent.obtain(ev)
                try {
                    val overlayHandled = textSelectionHelper.isInSelectionMode &&
                        selectionOverlay.onTouchEvent(textSelectionEvent)
                    val dispatch = if (overlayHandled) {
                        TopicSelectionTouchDispatch.CONSUME
                    } else {
                        topicSelectionTouchDispatch(false, selectionOverlay.checkOnTap(ev))
                    }
                    when (dispatch) {
                        TopicSelectionTouchDispatch.CONSUME -> return true
                        TopicSelectionTouchDispatch.DISPATCH_CANCEL -> {
                            val originalAction = ev.action
                            ev.action = MotionEvent.ACTION_CANCEL
                            val handled = super.dispatchTouchEvent(ev)
                            ev.action = originalAction
                            return handled
                        }
                        TopicSelectionTouchDispatch.DISPATCH_ORIGINAL -> Unit
                    }
                } finally {
                    textSelectionEvent.recycle()
                }
                return super.dispatchTouchEvent(ev)
            }

            override fun dispatchDraw(canvas: Canvas) {
                updateRootBlurSource(width, height)
                super.dispatchDraw(canvas)
            }
        }.apply {
            setBackgroundColor(topicDetailListBackgroundColor())
            setBackgroundImage(
                Theme.getCachedWallpaperNonBlocking(),
                Theme.isWallpaperMotion(),
            )
            replyComposerLastKeyboardHeight = topicReplyComposerRememberedKeyboardHeight(context)
        }
        rootFrameLayout = root
        root.setPanTransitionDelegate(object : TopicDetailHostLayout.PanTransitionDelegate {
            override fun onPanTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {
                if (::listView.isInitialized) {
                    listView.cancelSwipeReply(true)
                }
                if (keyboardVisible) {
                    floatingDateController?.hide(animated = false, force = true)
                }
                // Match Chat: keep island insets live for the whole pan transition.
                updateReplyComposerBottomInset()
                replyComposerPanel?.onAdjustPanTransitionStart(keyboardVisible, contentHeight)
            }

            override fun onPanTranslationUpdate(y: Float, progress: Float, keyboardVisible: Boolean) {
                // Chat stores contentPanTranslation for reversed-list top padding and
                // service shaders. TopicDetail is a normal top-down list: folding the
                // pan amount into list top padding cancels the root translation and
                // kills the whole-page motion. Keep the value for overlays/debug, but
                // let AdjustPan's parent.setTranslationY carry every child — including
                // bubbles and the composer island — as one unit.
                val panChanged = topicContentPanTranslation != y || topicContentPanTranslationT != progress
                topicContentPanTranslation = y
                topicContentPanTranslationT = progress
                if (panChanged) {
                    mentionBlurSource?.invalidateDisplayListForDrawables()
                    mentionBackground?.invalidateSelf()
                    topicInputViewsContainer?.invalidate()
                }
                replyComposerPanel?.onAdjustPanTransitionUpdate(y, progress, keyboardVisible)
                if (::listView.isInitialized) {
                    listView.invalidate()
                }
                mentionSuggestionsView?.invalidate()
                pageDownControls?.invalidate()
                floatingDateController?.update()
                root.invalidate()
            }

            override fun onPanTransitionEnd() {
                if (topicContentPanTranslation != 0f || topicContentPanTranslationT != 0f) {
                    topicContentPanTranslation = 0f
                    topicContentPanTranslationT = 0f
                    mentionBlurSource?.invalidateDisplayListForDrawables()
                    mentionBackground?.invalidateSelf()
                    topicInputViewsContainer?.invalidate()
                }
                // Re-sync island + list padding after pan settles, same as Chat checkInsets.
                updateReplyComposerBottomInset()
                replyComposerPanel?.onAdjustPanTransitionEnd()
                if (::listView.isInitialized) {
                    listView.invalidate()
                }
                root.invalidate()
            }

            override fun getPanStartOffset(): Int {
                return topicDetailPanStartOffset(
                    keyboardHeight = replyComposerKeyboardHeight,
                    popupShowing = isReplyComposerPopupShowing(),
                    popupHeight = replyComposerEmojiPanelHeight,
                )
            }

            override fun isPanHeightAnimationEnabled(): Boolean {
                val layout = parentLayout as? org.telegram.ui.ActionBar.ActionBarLayout
                // Keep pan on while BottomSheetTabs are merely visible (AdjustPan folds
                // getExpandedHeight into freeze height). But block while tabs height is
                // mid-animation or a browser sheet is attached — those change host height
                // via margin snap + progress ease; running AdjustPan on top causes the
                // "finishes then shakes once" settle.
                val tabsAnimating = layout?.bottomSheetTabs?.isAnimatingHeight == true
                return topicDetailPanHeightAnimationEnabled(
                    hasParentActivity = parentActivity != null,
                    fragmentPaused = topicFragmentPaused,
                    hasBlockingPopup = postOptions != null ||
                        reactionRailPopup != null ||
                        expandedReactionPickerPopup != null,
                    hasShownAttachedSheet = hasShownSheet(),
                    bottomTabsAnimating = tabsAnimating,
                    isLastFragment = layout?.getLastFragment() === this@TopicDetailFragment ||
                        (layout == null && parentLayout == null),
                )
            }
        })
        createMentionBlurSource(root)
        createTopicInputViewsContainer(context, root)
        textSelectionHelper.setOnTranslate { text, _, toLang, onAlertDismiss ->
            parentActivity?.let { activity ->
                val encoded = URLEncoder.encode(text.toString(), Charsets.UTF_8.name())
                val targetLanguage = toLang?.takeIf { it.isNotBlank() } ?: "zh-CN"
                Browser.openUrl(activity, "https://translate.google.com/?sl=auto&tl=$targetLanguage&op=translate&text=$encoded")
            }
            onAlertDismiss.run()
        }
        // Upstream's extension point: the helper subclass answers canShowQuote()/onQuoteClick()
        // (ChatActivity and MessagePreviewView do the same).
        textSelectionHelper.onQuote = { cell, text ->
            val sourcePost = cell.boundPost
            showReplyComposer(
                initialText = discourseQuoteMarkup(text, sourcePost, topicId),
                replyToPost = sourcePost,
            )
        }
        textSelectionHelper.setCallback(object : TextSelectionHelper.Callback() {
            override fun onStateChanged(isSelected: Boolean) {
                if (isSelected && ::listView.isInitialized) {
                    listView.cancelSwipeReply(true)
                    floatingDateController?.hide(animated = false, force = true)
                }
                updatePageDownControls(animated = true)
            }

            override fun onTextCopied() {
                if (AndroidUtilities.shouldShowClipboardToast()) {
                    BulletinFactory.of(this@TopicDetailFragment)
                        .createCopyBulletin(LocaleController.getString(selectionCopyFeedbackMessageRes()))
                        .show()
                }
            }
        })
        root.setFixedTopView(actionBar)
        root.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        createTopicIdentityContainer(context)

        adapter = TopicDetailAdapter(context)
        listView = TopicSwipeReplyRecyclerView(context).apply {
            layoutManager = TopicSwipeReplyRecyclerView.SwipeReplyLayoutManager(context)
            itemAnimator = null
            clipToPadding = false
            // Telegram draws the wallpaper on the parent content view. Keeping the
            // recycler transparent lets the wallpaper remain visible between posts.
            setBackgroundColor(Color.TRANSPARENT)
            listBottomBasePadding = AndroidUtilities.dp(24f)
            // Apply ActionBar-height top padding immediately so the first layout already
            // matches chrome. Deferring until post{applyTopicListChromePadding} starts the
            // list at paddingTop=0 then grows it, which looks like an automatic scroll jump.
            val estimatedTop = topicDetailListContentTopPaddingPx(
                AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight(),
            )
            setPadding(0, estimatedTop, 0, listBottomBasePadding)
            adapter = this@TopicDetailFragment.adapter
            setSwipeReplyDelegate(object : TopicSwipeReplyRecyclerView.Delegate {
                override fun canStartSwipeReply(): Boolean {
                    return topicDetailCanStartSwipeReply(
                        isBusy = latestTopicState.isLoading || latestTopicState.isRefreshing || latestTopicState.isLoadingMore,
                        selectionActive = textSelectionHelper.isInSelectionMode,
                        popupActive = postOptions != null || reactionRailPopup != null ||
                            expandedReactionPickerPopup != null || root.isScrimActive,
                        listScrollLocked = postOptionsListScrollLocked,
                    )
                }

                override fun onSwipeReply(cell: TopicPostCell, post: Post) {
                    showReplyComposer(initialText = "", replyToPost = post)
                }
            })
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            topicListUserScrollActive = true
                            topicListUserHasScrolled = true
                            floatingDateController?.onScrollStateChanged(newState)
                        }
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            topicListUserScrollActive = false
                            saveViewport()
                            textSelectionHelper.stopScrolling()
                            floatingDateController?.onScrollStateChanged(newState)
                            val manager = recyclerView.layoutManager as? LinearLayoutManager
                            if (manager != null) {
                                updatePageDownScrollState(manager, 0)
                            }
                        }
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    textSelectionHelper.onParentScrolled()
                    updateCollapsedTitle()
                    floatingDateController?.onScrolled(dy)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        mentionBlurNoiseSuppressor?.onScrolled(dx.toFloat(), dy.toFloat())
                        root.invalidate()
                    }
                    val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (dy != 0 && !topicListUserScrollActive) {
                        val first = manager.findFirstVisibleItemPosition()
                        val child = manager.findViewByPosition(first)
                        topicScrollLog("layout_or_programmatic_scroll", this@TopicDetailFragment.adapter.postNumberAtAdapterPosition(first) ?: 0,
                            (child?.top ?: listView.paddingTop) - listView.paddingTop)
                    }
                    updatePageDownScrollState(manager, dy)
                    if (topicDetailShouldLoadMoreFromScroll(
                            dy = dy,
                            userScrollActive = topicListUserScrollActive,
                            lastVisibleItemPosition = manager.findLastVisibleItemPosition(),
                            itemCount = manager.itemCount,
                        )
                    ) {
                        viewModel.loadMore()
                    }
                    if (topicDetailShouldLoadPreviousFromScroll(
                            dy = dy,
                            userScrollActive = topicListUserScrollActive,
                            firstVisibleItemPosition = manager.findFirstVisibleItemPosition(),
                        )
                    ) {
                        viewModel.loadPrevious()
                    }
                }
            })
        }
        textSelectionHelper.setParentView(listView)
        root.addView(
            listView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP, 0f, 0f, 0f, 0f),
        )
        mentionBlurCapture = ViewGroupPartRenderer(listView, root, listView::drawChildForBlur)
        createTopicTopFade(context, root)
        val floatingDateView = TopicDateCell(context, true).apply {
            alpha = 0f
            visibility = View.INVISIBLE
        }
        this.floatingDateView = floatingDateView
        root.addView(
            floatingDateView,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                0f,
                4f,
                0f,
                0f,
            ),
        )
        floatingDateController = TopicFloatingDateController(
            dateView = floatingDateView,
            listView = listView,
            rowAt = adapter::floatingDateRowAt,
            interactionsBlocked = ::floatingDateInteractionBlocked,
        )
        root.addView(
            textSelectionHelper.getOverlayView(context),
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP, 0f, 0f, 0f, 0f),
        )
        val inputBubbleContainer = topicInputViewsContainer?.inputIslandBubbleContainer ?: root
        createReplyComposer(context, inputBubbleContainer)
        createTopicSearchPanel(context, inputBubbleContainer)
        topicInputViewsContainer?.bringToFront()
        topicBottomViewsVisibilityController.setViewVisible(TOPIC_BOTTOM_INPUT_CONTAINER, true, false)
        topicBottomViewsVisibilityController.setViewVisible(TOPIC_BOTTOM_SEARCH_CONTAINER, false, false)
        createMentionSuggestions(context, root)
        root.setScrimViewport(listView, actionBar, replyComposerPanel)
        root.setOnScrimStateChanged {
            updatePageDownControls(animated = pageDownInitialized)
        }
        root.setScrimProgressListener { progress ->
            pageDownControls?.alpha = 1f - progress
            if (progress > 0f) {
                floatingDateController?.hide(animated = false, force = true)
            }
        }
        createPageDownControls(context, root)
        createTopicActionBarGlass(root)
        topicIdentityContainer?.setOccupyStatusBar(actionBar.occupyStatusBar)
        actionBar.bringToFront()
        scheduleInitialTopicInsetsSync(root)
        root.post { applyTopicListChromePadding() }

        collectorScope.launch {
            viewModel.uiState.collectLatest { state ->
                val readPositionOnlyUpdate = state.isReadPositionOnlyUpdateFrom(latestTopicState)
                latestTopicState = state
                if (readPositionOnlyUpdate) {
                    updatePageDownControls(animated = pageDownInitialized)
                    return@collectLatest
                }
                mentionSuggestionsView?.setParticipants(state.participants, state.posts)
                topicTitle = state.title.ifBlank { initialTitle.orEmpty() }
                state.pollActionFailure?.let { failure ->
                    adapter.resetPollInteractionState(failure.postId)
                    viewModel.consumePollActionFailure(failure.nonce)
                }
                adapter.submit(state)
                listView.post { floatingDateController?.update() }
                applyInitialTopicHeaderAnchorIfNeeded(state)
                if (!initialPostJumpRequested && !state.isLoading && state.posts.isNotEmpty()) {
                    initialPostJumpRequested = true
                    if (topicListUserHasScrolled) {
                        topicScrollLog("skip_initial_jump_after_gesture", 0, 0)
                    } else if (initialPostId != null) {
                        viewModel.requestJumpToPostId(initialPostId)
                    } else if (initialPostNumber != null) {
                        viewModel.requestJumpToReplyTarget(initialPostNumber)
                    }
                }
                updateTopicIdentity(state)
                updateCollapsedTitle()
                updateReactionUsersSheet(context, state)
                updatePostRepliesSheet(context, state)
                updateReplyComposerPanel(state)
                handleJumpEvent(state)
                updatePageDownControls(animated = pageDownInitialized)
                listView.post { updatePageDownControls(animated = pageDownInitialized) }
                state.jumpToLatestErrorMessage?.let { message ->
                    BulletinFactory.of(this@TopicDetailFragment).createErrorBulletin(message).show()
                    viewModel.consumeJumpToLatestError()
                }
                pageDownInitialized = true
            }
        }
        collectorScope.launch {
            appContainer.svgSpriteStore.sprite.collectLatest { markup ->
                svgSpriteMarkup = markup
                if (::adapter.isInitialized) {
                    adapter.notifyDataSetChanged()
                }
            }
        }

        fragmentView = root
        return root
    }

    override fun clearViews() {
        collectorScope.cancel()
        collectorScope = MainScope()
        topicSearchJob = null
        super.clearViews()
    }

    override fun onResume() {
        super.onResume()
        topicFragmentPaused = false
        AndroidUtilities.requestAdjustResize(parentActivity, topicAdjustResizeOwnerClassGuid)
        // Match ChatActivity's activityResumeTime < 250 gate via the host delay.
        rootFrameLayout?.delayPanAnimations(250L)
    }

    override fun onPause() {
        topicFragmentPaused = true
        captureAndFlushReadPosition()
        // Chat disables pan while paused; also drop any in-flight transition so a
        // browser-sheet / tabs height change cannot leave a residual translation.
        rootFrameLayout?.stopPanTransition()
        super.onPause()
    }

    /**
     * `BaseFragment` returns null here, and the themed sheets this fragment mounts (the block
     * editor) take a provider. The app draws from the global theme, so the provider below
     * resolves exactly what `Theme.getColor(key)` returns with no provider at all.
     */
    override fun getResourceProvider(): Theme.ResourcesProvider = AppThemeResourcesProvider

    /**
     * Upstream-shaped theme targets (DialogsActivity pattern):
     * - ActionBar / list chrome via FLAG_* ThemeDescriptions
     * - Colorable post cells are refreshed by ActionBarLayout's visible-tree pass
     * - explicit tracks cover every key consumed by topic chrome and post body blocks
     */
    override fun getThemeDescriptions(): java.util.ArrayList<ThemeDescription> {
        val descriptions = java.util.ArrayList<ThemeDescription>()
        val chromeDelegate = ThemeDescription.ThemeDescriptionDelegate {
            actionBar?.applyTheme()
            updateTopicWallpaperBackground()
            parentActivity?.window?.let { window ->
                AndroidUtilities.setLightStatusBar(window, !Theme.isCurrentThemeDark())
            }
        }
        if (actionBar != null) {
            descriptions.add(
                ThemeDescription(
                    actionBar,
                    ThemeDescription.FLAG_BACKGROUND,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_actionBarDefault,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    actionBar,
                    ThemeDescription.FLAG_AB_ITEMSCOLOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_actionBarDefaultIcon,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    actionBar,
                    ThemeDescription.FLAG_AB_TITLECOLOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_actionBarDefaultTitle,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    actionBar,
                    ThemeDescription.FLAG_AB_SELECTORCOLOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_actionBarDefaultSelector,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    actionBar,
                    ThemeDescription.FLAG_AB_SUBTITLECOLOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_actionBarDefaultSubtitle,
                ),
            )
        }
        if (::listView.isInitialized) {
            descriptions.add(
                ThemeDescription(
                    listView,
                    ThemeDescription.FLAG_LISTGLOWCOLOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_actionBarDefault,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    listView,
                    ThemeDescription.FLAG_SELECTOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_listSelector,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    listView,
                    ThemeDescription.FLAG_BACKGROUND,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_chat_wallpaper,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    listView,
                    0,
                    arrayOf(
                        TopicPostCell::class.java,
                        TopicHeaderCell::class.java,
                        TopicFooterCell::class.java,
                        TopicDateCell::class.java,
                    ),
                    null as android.graphics.Paint?,
                    null,
                    chromeDelegate,
                    Theme.key_windowBackgroundWhite,
                ),
            )
        } else {
            descriptions.add(
                ThemeDescription(
                    null,
                    0,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    chromeDelegate,
                    Theme.key_windowBackgroundWhite,
                ),
            )
        }
        intArrayOf(
            Theme.key_actionBarDefaultSubmenuBackground,
            Theme.key_actionBarDefaultSubmenuItem,
            Theme.key_actionBarDefaultSubmenuItemIcon,
            Theme.key_chat_TextSelectionCursor,
            Theme.key_chat_emojiPanelBackground,
            Theme.key_chat_emojiPanelIcon,
            Theme.key_chat_emojiPanelIconSelected,
            Theme.key_chat_emojiPanelShadowLine,
            Theme.key_chat_inReactionButtonBackground,
            Theme.key_chat_inReactionButtonText,
            Theme.key_chat_inReactionButtonTextSelected,
            Theme.key_chat_inAudioSeekbarFill,
            Theme.key_chat_inPreviewInstantText,
            Theme.key_chat_inTextSelectionHighlight,
            Theme.key_chat_messageLinkIn,
            Theme.key_chat_messageLinkOut,
            Theme.key_chat_messagePanelBackground,
            Theme.key_chat_messagePanelCursor,
            Theme.key_chat_messagePanelHint,
            Theme.key_chat_messagePanelSend,
            Theme.key_chat_messagePanelShadow,
            Theme.key_chat_messagePanelText,
            Theme.key_chat_replyPanelClose,
            Theme.key_chat_searchPanelText,
            Theme.key_chat_serviceBackground,
            Theme.key_chat_serviceText,
            Theme.key_checkboxCheck,
            Theme.key_dialogBackground,
            Theme.key_dialogButtonSelector,
            Theme.key_dialogFloatingIcon,
            Theme.key_dialogShadowLine,
            Theme.key_divider,
            Theme.key_emptyListPlaceholder,
            Theme.key_glass_defaultIcon,
            Theme.key_text_RedRegular,
            Theme.key_windowBackgroundGray,
            Theme.key_windowBackgroundWhiteBlackText,
            Theme.key_windowBackgroundWhiteGrayText,
            Theme.key_windowBackgroundWhiteGrayText2,
            Theme.key_chat_messageTextIn,
            Theme.key_chat_inTimeText,
            Theme.key_chat_inReplyLine,
            Theme.key_chat_inBubble,
        ).forEach { key ->
            descriptions.add(
                ThemeDescription(
                    null,
                    0,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    chromeDelegate,
                    key,
                ),
            )
        }
        return descriptions
    }

    override fun onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(
            this,
            NotificationCenter.didSetNewWallpapper,
        )
        captureAndFlushReadPosition()
        topicSearchJob?.cancel()
        topicSearchJob = null
        topicSearchSession.clear()
        topicSearchNavigator.clear()
        topicSearchItem = null
        topicHeaderItem = null
        topicIdentityContainer = null
        topicSearchPanel = null
        topicSearchCountText = null
        textSelectionHelper.destroy()
        collectorScope.cancel()
        PhotoViewer.getInstance().closePhoto(false, false)
        reactionUsersSheet?.dismiss()
        reactionUsersSheet = null
        dismissExpandedReactionPicker(animated = false)
        reactionRailPopup?.dismiss()
        reactionRailPopup = null
        reactionEffectOverlay?.dismiss()
        reactionEffectOverlay = null
        hideReplyComposerEmojiPanel(openKeyboard = false, animated = false)
        mentionSuggestionsView?.release()
        mentionSuggestionsView = null
        mentionBackground = null
        topicTopFadeView = null
        mentionBlurWatcher?.shutdown()
        mentionBlurWatcher = null
        mentionBlurCapture = null
        mentionBlurNoiseSuppressor = null
        mentionBlurSourceFrosted = null
        mentionBlurSource = null
        mentionBlurSourceColor = null
        mentionBlurPositions.clear()
        mentionBlurPositionsMerged.clear()
        clearReplyComposerWaitingForKeyboardOpenAfterEmoji()
        postOptions?.dismiss()
        postOptions = null
        unlockPostOptionsListScroll()
        repliesSheet?.dismiss()
        repliesSheet = null
        richEditorSheet?.dismiss()
        richEditorSheet = null
        replyComposerPanel?.release()
        replyComposerPanel = null
        listView.cancelSwipeReply(false)
        pageDownControls = null
        pageDownReachedLatest = false
        pageDownInitialized = false
        pageDownLastKnownBelowPosts = 0
        pageDownLastKnownAbovePosts = 0
        pageDownSubmittedCount = -1
        pageDownCanShowFromScroll = true
        pageDownShownByScroll = false
        pageDownScrollAccumPx = 0
        floatingDateController?.release()
        floatingDateController = null
        floatingDateView = null
        topicInsetsStateHolder?.let { holder ->
            topicAnimatedInsetsProvider?.unsubscribeFromWindowInsetsAnimation(holder)
        }
        topicAnimatedInsetsProvider = null
        topicInsetsStateHolder = null
        topicInputViewsContainer = null
        topicLastImeVisible = false
        topicInitialInsetsSyncScheduled = false
        rootFrameLayout?.clearScrim()
        rootFrameLayout?.setDelegate(null)
        rootFrameLayout?.setOnScrimStateChanged(null)
        rootFrameLayout?.setScrimProgressListener(null)
        rootFrameLayout?.releasePanLayoutHelper()
        rootFrameLayout = null
        AndroidUtilities.removeAdjustResize(parentActivity, topicAdjustResizeOwnerClassGuid)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id != NotificationCenter.didSetNewWallpapper) {
            return
        }
        updateTopicWallpaperBackground()
        floatingDateView?.invalidate()
        if (::listView.isInitialized) {
            listView.invalidateViews()
        }
    }

    private fun updateTopicWallpaperBackground() {
        val root = rootFrameLayout ?: return
        root.setBackgroundColor(topicDetailListBackgroundColor())
        root.setBackgroundImage(
            Theme.getCachedWallpaperNonBlocking(),
            Theme.isWallpaperMotion(),
        )
        root.invalidate()
    }

    override fun onBackPressed(): Boolean {
        // Prefer topicSearchOpen over isSearchFieldVisible: during collapse the search
        // container stays VISIBLE until the fade ends; a second edge-back would otherwise
        // re-toggle into expand. closeSearch is also tag-guarded in ActionBarMenuItem.
        if (topicSearchOpen) {
            topicSearchItem?.closeSearch(true)
            return false
        }
        if (topicSearchItem?.isSearchFieldVisible == true) {
            topicSearchItem?.closeSearch(true)
            return false
        }
        if (mentionSuggestionsView?.isOpen() == true) {
            mentionSuggestionsView?.dismiss()
            return false
        }
        if (expandedReactionPickerPopup != null || expandedReactionPickerPanel != null) {
            dismissExpandedReactionPicker(animated = true)
            return false
        }
        if (textSelectionHelper.isInSelectionMode()) {
            textSelectionHelper.clear(true)
            return false
        }
        if (replyComposerEmojiPanel != null || replyComposerEmojiPanelHeight > 0) {
            hideReplyComposerEmojiPanel(openKeyboard = false)
            return false
        }
        if (replyComposerKeyboardHeight > 0) {
            replyComposerPanel?.let(::closeReplyComposerKeyboard)
            return false
        }
        captureAndFlushReadPosition()
        return super.onBackPressed()
    }

    private fun getPlaceProviderObjectParent(): View {
        return if (::listView.isInitialized) listView else fragmentView ?: super.getPhotoViewerScaleView() ?: actionBar
    }

    private fun getPlaceProviderObjectClipTop(): Int {
        val parentView = getPlaceProviderObjectParent()
        val parentLocation = IntArray(2)
        val actionBarLocation = IntArray(2)
        parentView.getLocationInWindow(parentLocation)
        actionBar.getLocationInWindow(actionBarLocation)
        return (actionBarLocation[1] + actionBar.measuredHeight - parentLocation[1]).coerceAtLeast(0)
    }

    private fun getPlaceProviderObjectClipBottom(): Int {
        val parentView = getPlaceProviderObjectParent()
        val rootView = fragmentView ?: return 0
        val parentLocation = IntArray(2)
        val rootLocation = IntArray(2)
        parentView.getLocationInWindow(parentLocation)
        rootView.getLocationInWindow(rootLocation)
        val parentBottom = parentLocation[1] + parentView.height
        val rootBottom = rootLocation[1] + rootView.height
        return (parentBottom - rootBottom).coerceAtLeast(0)
    }

    private fun updateCollapsedTitle() {
        // Identity capsule is always-on (Chat avatarContainer). Keep the name so
        // existing scroll listeners still call in; only re-apply visibility for search.
        updateTopicIdentityVisibility()
    }

    private fun floatingDateInteractionBlocked(): Boolean {
        return topicSearchOpen ||
            textSelectionHelper.isInSelectionMode ||
            postOptions != null ||
            reactionRailPopup != null ||
            expandedReactionPickerPopup != null ||
            rootFrameLayout?.isScrimActive == true ||
            replyComposerKeyboardHeight > 0 ||
            replyComposerEmojiPanel != null ||
            replyComposerEmojiPanelHeight > 0
    }

    private fun applyInitialTopicHeaderAnchorIfNeeded(state: TopicUiState) {
        if (topicListUserHasScrolled) {
            topicInitialContentAnchored = true
            return
        }
        if (debugFromTop && !topicInitialContentAnchored && !state.isLoading && state.posts.isNotEmpty()) {
            (listView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
            topicInitialContentAnchored = true
            topicScrollLog("debug_from_top", 1, 0)
            return
        }
        if (topicListUserHasScrolled) {
            topicInitialContentAnchored = true
            return
        }
        if (!initialAnchorApplied && !state.isLoading && state.posts.isNotEmpty() && initialViewport != null) {
            val viewport = initialViewport!!
            val post = state.posts.firstOrNull { it.postNumber == viewport.postNumber }
            val position = post?.id?.let(adapter::adapterPositionForPostId)
            if (position != null) {
                (listView.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(position, viewport.offsetPx)
                topicScrollLog("restore_viewport", viewport.postNumber, viewport.offsetPx)
            }
            initialAnchorApplied = true
            topicInitialContentAnchored = true
            return
        }
        if (initialPostNumber != null || initialPostId != null) {
            topicInitialContentAnchored = true
            return
        }
        if (!topicDetailShouldAnchorInitialContent(
                initialContentAnchored = topicInitialContentAnchored,
                userHasScrolled = topicListUserHasScrolled,
                isLoading = state.isLoading,
                isRefreshing = state.isRefreshing,
                postsEmpty = state.posts.isEmpty(),
                errorMessage = state.errorMessage,
                hasJumpEvent = state.jumpEvent != null,
            )
        ) {
            return
        }
        val targetPostId = topicInitialReadAnchorPostId(
            posts = state.posts,
            lastReadPostNumber = state.lastReadPostNumber,
            lastReadPostId = state.lastReadPostId,
        )
        val adapterPosition = targetPostId?.let(adapter::adapterPositionForPostId)
        val manager = listView.layoutManager as? LinearLayoutManager
        if (adapterPosition != null && manager != null && state.lastReadPostNumber > 1) {
            manager.scrollToPositionWithOffset(adapterPosition, AndroidUtilities.dp(8f))
            topicScrollLog("restore_read_progress", state.lastReadPostNumber, AndroidUtilities.dp(8f))
            listView.post { floatingDateController?.showForProgrammaticUpdate() }
        } else if (state.lastReadPostNumber > 1) {
            viewModel.requestJumpToReadPosition()
        }
        topicInitialContentAnchored = true
    }

    private fun topicScrollLog(reason: String, postNumber: Int, offset: Int) {
        if (io.github.baba983.nativedo.BuildConfig.DEBUG) {
            android.util.Log.d("NativeDoTopicScroll", "reason=$reason topic=$topicId post=$postNumber offset=$offset")
        }
    }

    private fun saveViewport() {
        if (debugFromTop || !::listView.isInitialized || !::adapter.isInitialized || latestTopicState.isLoading ||
            !topicInitialContentAnchored) return
        val manager = listView.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
        val last = manager.findLastVisibleItemPosition()
        if (first < 0 || last < first) return
        for (position in first..last) {
            val number = adapter.postNumberAtAdapterPosition(position) ?: continue
            val view = manager.findViewByPosition(position) ?: continue
            val offset = manager.getDecoratedTop(view) - listView.paddingTop
            viewportKey?.let { viewportStore?.save(it,
                io.github.baba983.nativedo.feature.topic.data.TopicViewport(number, offset)) }
            topicScrollLog("save_viewport", number, offset)
            break
        }
    }

    private fun captureAndFlushReadPosition() {
        saveViewport()
        if (!::viewModel.isInitialized) return
        if (::listView.isInitialized) {
            val manager = listView.layoutManager as? LinearLayoutManager
            if (manager != null) {
                val firstVisible = manager.findFirstVisibleItemPosition()
                val lastVisible = manager.findLastVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION && lastVisible >= firstVisible) {
                    val postNumber = (lastVisible downTo firstVisible)
                        .firstNotNullOfOrNull(adapter::postNumberAtAdapterPosition)
                    if (postNumber != null) {
                        viewModel.advanceReadPositionTo(postNumber)
                    }
                }
            }
        }
        viewModel.flushReadPosition()
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
                    cellPlace.parentView = getPlaceProviderObjectParent()
                    cellPlace.clipTopAddition = getPlaceProviderObjectClipTop()
                    cellPlace.clipBottomAddition = getPlaceProviderObjectClipBottom()
                    return cellPlace
                }
                val bounds = sourceBoundsProvider(imageUrl) ?: return null
                val imageReceiver = sourceImageReceiverProvider(imageUrl)
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = (bounds.left - (imageReceiver?.imageX ?: 0f)).toInt()
                    viewY = (bounds.top - (imageReceiver?.imageY ?: 0f)).toInt()
                    viewWidth = (imageReceiver?.imageWidth ?: bounds.width()).toInt()
                    viewHeight = (imageReceiver?.imageHeight ?: bounds.height()).toInt()
                    parentView = getPlaceProviderObjectParent()
                    this.imageReceiver = imageReceiver
                    thumb = if (needPreview) sourceBitmapProvider(imageUrl) else null
                    radius = if (needPreview) sourceRadiusProvider(imageUrl) else null
                    clipTopAddition = getPlaceProviderObjectClipTop()
                    clipBottomAddition = getPlaceProviderObjectClipBottom()
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

            override fun onClose() {
                setHiddenImageUrl(null)
            }
        }
        var selectedIndex = 0
        val viewerTitle = topicPostDisplayName(post.authorDisplayName, post.authorUsername).ifBlank { topicTitle }
        val viewerSubtitle = topicPostTimeLabel(post.createdAt)
        val opened = if (galleryImages.isNotEmpty()) {
            val items = galleryImages.map { image ->
                val bounds = sourceBoundsProvider(image.url)
                val width = image.widthPx.takeIf { it > 0 } ?: bounds?.width()?.toInt()?.coerceAtLeast(1) ?: 0
                val height = image.heightPx.takeIf { it > 0 } ?: bounds?.height()?.toInt()?.coerceAtLeast(1) ?: 0
                PhotoViewer.UrlPhotoItem(image.url, image.thumbUrl, width, height, image.caption, viewerTitle, viewerSubtitle)
            }
            selectedIndex = items.indexOfFirst { it.url == url }.coerceAtLeast(0)
            viewer.openPhotoItems(items, selectedIndex, provider)
        } else {
            val items = urls.map { imageUrl ->
                val bounds = sourceBoundsProvider(imageUrl)
                val width = bounds?.width()?.toInt()?.coerceAtLeast(1) ?: 0
                val height = bounds?.height()?.toInt()?.coerceAtLeast(1) ?: 0
                PhotoViewer.UrlPhotoItem(imageUrl, null, width, height, null, viewerTitle, viewerSubtitle)
            }
            selectedIndex = items.indexOfFirst { it.url == url }.coerceAtLeast(0)
            viewer.openPhotoItems(items, selectedIndex, provider)
        }
        if (!opened) {
            setHiddenImageUrl(null)
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
                onRetry = viewModel::retryReactionUsers,
                onUserSelected = { username ->
                    reactionUsersSheet?.dismiss()
                    Browser.openUrl(parentActivity ?: return@TopicReactionUsersSheet, "$baseUrl/u/$username")
                },
                onDismissed = viewModel::dismissReactionSheet,
            )
            reactionUsersSheet = created
            created.submit(sheetState.reactions, sheetState.reactionUsersCount, state.reactionUsers)
            created.show()
        } else {
            sheet.submit(sheetState.reactions, sheetState.reactionUsersCount, state.reactionUsers)
        }
    }

    private fun updatePostRepliesSheet(context: Context, state: TopicUiState) {
        val sheetState = state.postRepliesSheet
        if (sheetState == null) {
            repliesSheet?.dismiss()
            repliesSheet = null
            return
        }
        val sheet = repliesSheet
        if (sheet == null) {
            val created = TopicRepliesSheet(
                context = context,
                fragment = this,
                baseUrl = baseUrl,
                onReplySelected = { reply ->
                    repliesSheet?.dismiss()
                    reply.postNumber?.let(viewModel::requestJumpToReplyTarget)
                },
                onLoadMore = viewModel::loadMorePostReplies,
                onRetry = viewModel::retryPostReplies,
                onDismissed = viewModel::dismissPostReplies,
            )
            repliesSheet = created
            created.submit(sheetState.sourcePost, sheetState.totalCount, state.postReplies)
            created.show()
        } else {
            sheet.submit(sheetState.sourcePost, sheetState.totalCount, state.postReplies)
        }
    }

    private fun showReactionPickerOptions(
        post: Post,
        reactions: List<PostReaction>,
        initialReactionId: String?,
        anchorView: View,
        pressX: Float,
        pressY: Float,
    ) {
        val context = anchorView.context
        val ids = postReactionPickerIds(
            initialReactionId = initialReactionId,
            validReactionIds = validReactionIds,
            postReactions = reactions,
            fallbackReactionIds = DEFAULT_REACTION_IDS,
        )
        if (ids.isEmpty()) return
        reactionRailPopup?.dismiss()
        postOptions?.dismiss()
        postOptions = null
        var popup: ActionBarPopupWindow? = null
        val content = ReactionPickerRailView(context, baseUrl, emojiCatalog) { selection ->
            selectReactionFromPicker(post, selection) {
                popup?.dismiss()
                reactionRailPopup = null
            }
        }.apply {
            bind(ids, post.currentUserReaction ?: initialReactionId)
        }
        val scrimContainer = TopicPostScrimPopupContainerLayout(context) {
            popup?.dismiss()
        }.apply {
            addReactionRail(content, reactionPickerRailWidthDp(ids.size))
        }
        popup = object : ActionBarPopupWindow(scrimContainer, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            override fun dismiss() {
                dismiss(true)
            }

            override fun dismiss(animated: Boolean) {
                if (animated) {
                    content.startDismissAnimation()
                } else {
                    content.cancelEnterAnimation()
                }
                rootFrameLayout?.hideScrim(this, animated)
                super.dismiss(animated)
                if (reactionRailPopup === this) {
                    reactionRailPopup = null
                }
                unlockPostOptionsListScroll()
            }
        }.apply {
            setPauseNotifications(true)
            setDismissAnimationDuration(220)
            isOutsideTouchable = true
            isClippingEnabled = true
            isFocusable = true
            animationStyle = 0
            inputMethodMode = ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        val position = measureTopicPostOptionsPopup(
            scrimContainer = scrimContainer,
            anchorView = anchorView,
            pressX = pressX,
            pressY = pressY,
        )
        val window = popup ?: return
        reactionRailPopup = window
        lockPostOptionsListScroll()
        window.showAtLocation(anchorView, Gravity.LEFT or Gravity.TOP, position.x, position.y)
        rootFrameLayout?.showScrim(anchorView, window, true)
        content.startEnterAnimation(animatePopup = true)
    }

    private fun showReplyComposer(
        initialText: String,
        replyToPost: Post? = null,
        replyToPostNumber: Int? = replyToPost?.postNumber,
    ) {
        hideReplyComposerEmojiPanel(openKeyboard = false, animated = false)
        replyComposerPanel?.setEmojiPopupShowing(false)
        viewModel.prepareReplyDraft(initialText = initialText, replyToPostNumber = replyToPostNumber)
        val draft = viewModel.uiState.value.replyDraft
        replyComposerPanel?.bindDraft(draft, replyToPost)
        replyComposerPanel?.focusAndOpenKeyboard()
        replyComposerPanel?.bringToFront()
        actionBar.bringToFront()
    }

    private fun createTopicInputViewsContainer(context: Context, root: TopicDetailHostLayout) {
        if (topicInputViewsContainer != null) return
        val insetsStateHolder = WindowInsetsStateHolder(::onTopicInsetsChanged)
        val container = ChatInputViewsContainer(context).apply {
            clipChildren = false
            clipToPadding = false
            setWindowInsetsProvider(insetsStateHolder)
            setInputIslandBubbleDrawable(
                createTopicInputIslandBackground(
                    view = this,
                    root = root,
                    source = mentionBlurSource,
                    liquidGlassEffectAllowed = true,
                ),
            )
            setUnderKeyboardBackgroundDrawable(
                createTopicInputIslandBackground(
                    view = this,
                    root = root,
                    source = mentionBlurSourceFrosted,
                    liquidGlassEffectAllowed = false,
                ),
            )
        }
        topicInsetsStateHolder = insetsStateHolder
        topicInputViewsContainer = container
        root.addView(
            container,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()),
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            insetsStateHolder.setInsets(insets)
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (topicLastImeVisible != imeVisible) {
                topicLastImeVisible = imeVisible
                root.notifyHeightChanged()
            }
            WindowInsetsCompat.CONSUMED
        }
        topicAnimatedInsetsProvider = WindowAnimatedInsetsProvider(root).also { provider ->
            insetsStateHolder.setupAnimatedInsetsProvider(provider, root)
        }
    }

    /**
     * The first WindowInsets callback can arrive while the input container is
     * already attached but before the list/composer exists. Replay that cached
     * baseline once all three are present, otherwise the first keyboard cycle
     * becomes the first time the composer receives its navigation-bar inset.
     */
    private fun scheduleInitialTopicInsetsSync(root: TopicDetailHostLayout) {
        if (topicInitialInsetsSyncScheduled) {
            return
        }
        topicInitialInsetsSyncScheduled = true
        root.post {
            if (rootFrameLayout !== root || !root.isAttachedToWindow) {
                return@post
            }
            ViewCompat.requestApplyInsets(root)
            topicInputViewsContainer?.checkInsets()
            onTopicInsetsChanged()
            updateReplyComposerBottomInset()
            root.notifyHeightChanged()
            root.requestLayout()
        }
    }

    private fun onTopicInsetsChanged() {
        val insetsStateHolder = topicInsetsStateHolder ?: return
        val systemImeHeight = insetsStateHolder.getInsets(WindowInsetsCompat.Type.ime()).bottom.coerceAtLeast(0)
        // AdjustPan already carries the complete host while the resize is animating.
        // Applying ChatInputViewsContainer's animated IME translation in the same frame
        // stacks both lifts; it is especially visible with BottomSheetTabs because the
        // host is already shortened by the browser strip. Re-apply the final inset from
        // onPanTransitionEnd once AdjustPan releases the host.
        if (topicDetailShouldApplyInputInsets(
                panInProgress = rootFrameLayout?.isPanAnimationInProgress() == true,
                systemImeHeight = systemImeHeight,
            )
        ) {
            topicInputViewsContainer?.checkInsets()
        }
        replyComposerKeyboardHeight = topicReplyComposerKeyboardInsetPx(systemImeHeight)
        val shouldHideEmojiPanel = topicReplyComposerShouldHideEmojiPanelForKeyboard(
            keyboardVisible = replyComposerKeyboardHeight > 0,
            emojiPanelShowing = isReplyComposerPopupShowing(),
            waitingForKeyboardOpenAfterEmoji = replyComposerWaitingForKeyboardOpenAfterEmoji,
        )
        if (replyComposerKeyboardHeight > 0) {
            replyComposerLastKeyboardHeight = replyComposerKeyboardHeight
            rootFrameLayout?.context?.let { context ->
                topicReplyComposerRememberKeyboardHeight(context, replyComposerKeyboardHeight)
            }
            replyComposerPanel?.onKeyboardVisible()
        }
        if (shouldHideEmojiPanel) {
            hideReplyComposerEmojiPanel(openKeyboard = false, animated = false)
        }
        if (replyComposerKeyboardHeight > 0) {
            clearReplyComposerWaitingForKeyboardOpenAfterEmoji()
        }
        updateReplyComposerBottomInset()
    }

    private fun onTopicBottomItemsVisibilityChanged() {
        val replyFactor = topicBottomViewsVisibilityController.getVisibility(TOPIC_BOTTOM_INPUT_CONTAINER)
        val searchFactor = topicBottomViewsVisibilityController.getVisibility(TOPIC_BOTTOM_SEARCH_CONTAINER)
        replyComposerPanel?.let { panel ->
            panel.alpha = replyFactor
            panel.visibility = if (replyFactor > 0f) View.VISIBLE else View.INVISIBLE
        }
        topicSearchPanel?.let { panel ->
            panel.alpha = searchFactor
            panel.visibility = if (searchFactor > 0f) View.VISIBLE else View.INVISIBLE
        }
        updateTopicInputBubbleHeight(replyFactor, searchFactor)
        rootFrameLayout?.setScrimViewport(
            listView,
            actionBar,
            if (topicSearchOpen) topicSearchPanel else replyComposerPanel,
        )
        updateReplyComposerBottomInset()
    }

    private fun updateTopicInputBubbleHeight(
        replyFactor: Float = topicBottomViewsVisibilityController.getVisibility(TOPIC_BOTTOM_INPUT_CONTAINER),
        searchFactor: Float = topicBottomViewsVisibilityController.getVisibility(TOPIC_BOTTOM_SEARCH_CONTAINER),
    ) {
        val replyHeight = replyComposerPanel
            ?.visibleHeightForListPadding()
            ?.toFloat()
            ?.coerceAtLeast(AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat()).toFloat())
            ?: AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat()).toFloat()
        val searchHeight = AndroidUtilities.dp(44f).toFloat()
        val inputHeight = (replyHeight * replyFactor + searchHeight * searchFactor)
            .coerceAtLeast(AndroidUtilities.dp(44f).toFloat())
        topicInputViewsContainer?.setInputBubbleHeight(inputHeight)
    }

    private fun createReplyComposer(context: Context, inputBubbleContainer: ViewGroup) {
        if (replyComposerPanel != null) return
        val draft = viewModel.uiState.value.replyDraft
        val panel = TopicReplyComposerPanel(
            context = context,
            initialDraft = draft,
            replyToPost = viewModel.uiState.value.posts.firstOrNull { it.postNumber == draft.replyToPostNumber },
            onEmojiButtonPressed = ::handleReplyComposerEmojiButton,
            onRichEditorPressed = ::showRichEditor,
            onAttachMediaPressed = ::pickReplyComposerMedia,
            onKeyboardRequested = ::handleReplyComposerKeyboardRequested,
            onReplyContextPressed = { postNumber ->
                viewModel.requestJumpToReplyTarget(postNumber)
            },
            onBackKeyPressed = ::handleReplyComposerEditTextBackKey,
            onMentionKeyPressed = { keyCode, action ->
                mentionSuggestionsView?.handleKey(keyCode, action) == true
            },
            onVisibleHeightChanged = ::updateReplyComposerBottomInset,
            onTextChanged = viewModel::updateReplyDraftText,
            onMentionTokenChanged = { token -> mentionSuggestionsView?.updateQuery(token) },
            onClearReplyTarget = viewModel::clearReplyTarget,
            onSubmit = viewModel::submitReply,
        )
        replyComposerPanel = panel
        inputBubbleContainer.addView(
            panel,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.BOTTOM,
                0f,
                0f,
                0f,
                0f,
            ),
        )
        panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateReplyComposerBottomInset()
        }
        panel.bindDraft(draft, viewModel.uiState.value.posts.firstOrNull { it.postNumber == draft.replyToPostNumber })
        updateReplyComposerBottomInset()
    }

    private var richEditorSheet: RichEditorSheet? = null

    /** Which surface asked for media, while the system picker is up. */
    private var mediaPickTarget: TopicMediaPickTarget? = null

    /**
     * The composer's way into the block editor: the same draft, edited with the block model.
     * Dismissing the sheet keeps the edits as the composer's draft
     * ([TopicViewModel.updateReplyDraftText]), and sending posts them through the same
     * [TopicViewModel.submitReply] the send button uses.
     */
    private fun showRichEditor() {
        val context = context ?: return
        val draft = viewModel.uiState.value.replyDraft
        richEditorSheet?.dismiss()
        val sheet = RichEditorSheet(
            context = context,
            resourcesProvider = resourceProvider,
            currentAccount = currentAccount,
            onSend = { markdown ->
                viewModel.submitReply(
                    raw = markdown,
                    replyToPostNumber = draft.replyToPostNumber,
                )
            },
            onDraft = viewModel::updateReplyDraftText,
        )
        richEditorSheet = sheet
        sheet.onAttachRequested = ::pickEditorMedia
        sheet.loadMarkdown(draft.raw)
        sheet.show()
    }

    /**
     * The editor's attach button (or a media row's add): the page takes the picked file itself -
     * it copies it into the cache and uploads it through the media bridge.
     */
    private fun pickEditorMedia() {
        pickMedia(TopicMediaPickTarget.Editor)
    }

    /**
     * The composer's own attach path (the attach button while the reply is empty, or the send
     * button's menu): upload the picked file to the site and put its markdown into the draft.
     */
    private fun pickReplyComposerMedia() {
        pickMedia(TopicMediaPickTarget.Composer)
    }

    private fun pickMedia(target: TopicMediaPickTarget) {
        val activity = parentActivity ?: return
        if (mediaPickTarget != null) return
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        mediaPickTarget = target
        try {
            activity.startActivityForResult(intent, RICH_EDITOR_MEDIA_REQUEST_CODE)
        } catch (ignored: ActivityNotFoundException) {
            // no picker on the device: the button simply does nothing
            mediaPickTarget = null
        }
    }

    /** The activity's result for [pickMedia]; true when this fragment consumed it. */
    fun handleRichEditorActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != RICH_EDITOR_MEDIA_REQUEST_CODE) return false
        val target = mediaPickTarget
        mediaPickTarget = null
        val uri = data?.data
        if (resultCode == Activity.RESULT_OK && uri != null) {
            when (target) {
                TopicMediaPickTarget.Editor -> richEditorSheet?.editorHost()?.attachExternalMedia(uri)
                TopicMediaPickTarget.Composer -> uploadReplyMedia(uri)
                null -> Unit
            }
        }
        return true
    }

    /** The composer's attach: copy the picked URI, upload it, insert the markdown it yields. */
    private fun uploadReplyMedia(uri: Uri) {
        val context = context ?: return
        val panel = replyComposerPanel ?: return
        val picked = PickedMediaResolver.resolve(context, uri)
        if (picked == null) {
            panel.setError(context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_attach_failed))
            return
        }
        panel.setError(null)
        panel.setAttachUploading(true)
        viewModel.uploadMedia(picked.file, picked.mimeType) { uploaded ->
            panel.setAttachUploading(false)
            if (uploaded == null) {
                panel.setError(context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_attach_failed))
            } else {
                panel.insertMediaMarkdown(alt = uploaded.filename ?: picked.name, url = uploaded.url)
            }
        }
    }

    private fun createTopicActionBarMenu(context: Context) {
        if (topicSearchItem != null || topicHeaderItem != null) return
        val menu = actionBar.createMenu()
        // Hidden search-field host (Chat still has a searchItem, but we only expose Search
        // under ⋯ so the menu pill stays a single icon). Collapse path must re-GONE the
        // host — toggleSearch() forces VISIBLE on close and would otherwise leave a second
        // 🔍 next to ⋯.
        topicSearchItem = menu
            .addItem(TOPIC_MENU_SEARCH_FIELD, TelegramR.drawable.outline_header_search, resourceProvider)
            .setIsSearchField(true)
            .setActionBarMenuItemSearchListener(object : ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                override fun onSearchExpand() {
                    actionBar.visibility = View.VISIBLE
                    actionBar.alpha = 1f
                    topicHeaderItem?.visibility = View.GONE
                    // Drive visibility only via topicSearchOpen (not isSearchFieldVisible):
                    // during collapse the searchContainer is still VISIBLE until the anim ends.
                    enterTopicSearchMode()
                    topicIdentityContainer?.setIdentityVisible(false)
                }

                override fun onSearchCollapse() {
                    topicSearchItem?.visibility = View.GONE
                    topicHeaderItem?.visibility = View.VISIBLE
                    exitTopicSearchMode()
                    // Force-restore identity here. updateTopicIdentityVisibility alone can race
                    // with isSearchFieldVisible still true mid-collapse animation.
                    topicIdentityContainer?.setIdentityVisible(true)
                    topicIdentityContainer?.requestLayout()
                    actionBar.invalidate()
                }

                override fun onTextChanged(editText: EditText) {
                    scheduleTopicSearch(editText.text?.toString().orEmpty())
                }

                override fun onSearchPressed(editText: EditText) {
                    startTopicSearch(editText.text?.toString().orEmpty())
                }
            })
            .apply {
                setSearchPaddingStart(7)
                setSearchFieldHint(context.getString(R.string.topic_search_hint))
                contentDescription = context.getString(R.string.topic_search_hint)
                visibility = View.GONE
            }

        topicHeaderItem = menu.addItem(TOPIC_MENU_OTHER, TelegramR.drawable.ic_ab_other).apply {
            setContentDescription(context.getString(R.string.action_more_options))
            addSubItem(TOPIC_MENU_SEARCH, TelegramR.drawable.msg_search, context.getString(R.string.topic_menu_search))
            addSubItem(TOPIC_MENU_REFRESH, TelegramR.drawable.menu_browser_refresh, context.getString(R.string.topic_menu_refresh))
            if (io.github.baba983.nativedo.BuildConfig.DEBUG) {
                addSubItem(90, TelegramR.drawable.menu_browser_refresh, "调试：从顶部重新打开")
            }
        }

        actionBar.setActionBarMenuOnItemClick(
            object : ActionBar.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    when (id) {
                        // BaseFragment installs a default -1 → finishFragment handler;
                        // we replace it here and must keep the back-button path.
                        -1 -> finishFragment()
                        TOPIC_MENU_SEARCH -> topicSearchItem?.openSearch(true)
                        TOPIC_MENU_REFRESH -> viewModel.refresh()
                        90 -> if (io.github.baba983.nativedo.BuildConfig.DEBUG) {
                            presentFragment(TopicDetailFragment(topicId, initialTitle = topicTitle, debugFromTop = true))
                        }
                    }
                }
            },
        )
    }

    private fun createTopicIdentityContainer(context: Context) {
        if (topicIdentityContainer != null) return
        val container = TopicIdentityContainer(context).apply {
            val initialDisplayName = initialIdentity?.username?.takeIf { it.isNotBlank() }
                ?: topicTitle.ifBlank { initialTitle.orEmpty() }
            bind(
                title = topicTitle.ifBlank { initialTitle.orEmpty() },
                subtitle = "",
                avatarUrl = topicDetailIdentityAvatarUrl(baseUrl, null, initialIdentity),
                displayName = initialDisplayName,
            )
            // Always-on Chat-style identity capsule (not scroll-collapsed).
            setIdentityVisible(true)
            setOnClickListener { openTopicIdentity() }
        }
        topicIdentityContainer = container
        container.setOccupyStatusBar(actionBar.occupyStatusBar)
        // Hide ActionBar's built-in title so glass middle pill tracks the identity
        // strip, not an empty SimpleTextView.
        actionBar.titleTextView.visibility = View.GONE
        // ChatAvatarContainer: WRAP_CONTENT + left 53 / right 52 gutters so the
        // back circle and ⋯ menu stay outside the identity hit target.
        actionBar.addView(
            container,
            0,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.MATCH_PARENT.toFloat(),
                Gravity.TOP or Gravity.LEFT,
                TOPIC_IDENTITY_HOST_LEFT_GUTTER_DP,
                0f,
                TOPIC_IDENTITY_HOST_RIGHT_GUTTER_DP,
                0f,
            ),
        )
        actionBar.backButtonImageView.bringToFront()
        actionBar.createMenu().bringToFront()
    }

    private fun updateTopicIdentity(state: TopicUiState) {
        val firstPost = state.posts.firstOrNull()
        topicIdentityContainer?.bind(
            title = state.title.ifBlank { topicTitle },
            subtitle = topicDetailIdentitySubtitle(
                context = actionBar.context,
                totalPosts = state.totalPosts,
                viewCount = state.viewCount,
                tags = state.tags,
            ),
            avatarUrl = topicDetailIdentityAvatarUrl(
                baseUrl = baseUrl,
                firstPost = firstPost,
                initialIdentity = initialIdentity,
            ),
            displayName = topicDetailIdentityDisplayName(
                firstPost,
                state.title.ifBlank { topicTitle },
                initialIdentity,
            ),
        )
        updateTopicIdentityVisibility()
    }

    private fun openTopicIdentity() {
        val activity = parentActivity ?: return
        val username = latestTopicState.posts.firstOrNull()?.authorUsername?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: initialIdentity?.username?.trim().orEmpty()
        if (username.isBlank()) {
            showTopicInfoSheet()
            return
        }
        // Telegram's ChatAvatarContainer opens the peer profile on a middle-pill tap.
        // NativeDo only has a native profile screen for the signed-in user, so use the
        // in-app browser for arbitrary topic authors until that screen is reusable.
        Browser.openUrl(activity, "${baseUrl.trimEnd('/')}/u/${Uri.encode(username)}")
    }

    private fun updateTopicIdentityVisibility() {
        // Only topicSearchOpen — isSearchFieldVisible stays true until collapse animation ends
        // and would leave the identity strip blank after cancel-without-query until scroll.
        topicIdentityContainer?.setIdentityVisible(!topicSearchOpen)
    }

    private fun showTopicInfoSheet() {
        val activity = parentActivity ?: return
        val state = latestTopicState
        val firstPost = state.posts.firstOrNull()
        val author = topicDetailIdentityDisplayName(firstPost, state.title.ifBlank { topicTitle })
        val empty = activity.getString(R.string.topic_detail_info_empty)
        val tags = state.tags.filter { it.isNotBlank() }.joinToString(" · ").ifBlank { empty }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                AndroidUtilities.dp(20f),
                AndroidUtilities.dp(8f),
                AndroidUtilities.dp(20f),
                AndroidUtilities.dp(12f),
            )
        }
        fun addRow(label: String, value: String) {
            val labelView = TextView(activity).apply {
                text = label
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider))
                textSize = 13f
            }
            val valueView = TextView(activity).apply {
                text = value.ifBlank { empty }
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider))
                textSize = 16f
                setPadding(0, AndroidUtilities.dp(2f), 0, AndroidUtilities.dp(14f))
            }
            content.addView(labelView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            content.addView(valueView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
        }
        addRow(activity.getString(R.string.topic_detail_info_author), author)
        addRow(activity.getString(R.string.topic_detail_info_replies), state.totalPosts.coerceAtLeast(0).toString())
        addRow(activity.getString(R.string.topic_detail_info_views), state.viewCount.coerceAtLeast(0).toString())
        addRow(activity.getString(R.string.topic_detail_info_created), state.createdAt.ifBlank { empty })
        addRow(activity.getString(R.string.topic_detail_info_last_activity), state.lastPostedAt.ifBlank { empty })
        addRow(activity.getString(R.string.topic_detail_info_tags), tags)
        addRow(activity.getString(R.string.topic_detail_info_topic_id), topicId.toString())
        BottomSheet.Builder(activity, false, resourceProvider)
            .setTitle(state.title.ifBlank { activity.getString(R.string.topic_detail_info_title) }, true)
            .setCustomView(content)
            .show()
    }

    private fun createTopicSearchPanel(context: Context, inputBubbleContainer: ViewGroup) {
        if (topicSearchPanel != null) return
        val panel = TopicInputIslandFrameLayout(context).apply {
            visibility = View.GONE
        }
        val content = FrameLayout(context)
        panel.addView(
            content,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT.toFloat(),
                Gravity.LEFT or Gravity.TOP,
                0f,
                0f,
                0f,
                0f,
            ),
        )
        val count = AnimatedTextView(context, true, true, true).apply {
            setAnimationProperties(.25f, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT)
            setTextColor(Theme.getColor(Theme.key_chat_searchPanelText))
            setTextSize(AndroidUtilities.dp(15f).toFloat())
            setTypeface(AndroidUtilities.bold())
            setGravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            getDrawable().setOverrideFullWidth(AndroidUtilities.displaySize.x)
            contentDescription = context.getString(R.string.topic_search_count_description)
        }
        content.addView(
            count,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                30f,
                Gravity.LEFT or Gravity.CENTER_VERTICAL,
                18f,
                -1f,
                97.33f,
                0f,
            ),
        )
        inputBubbleContainer.addView(
            panel,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                44f,
                Gravity.BOTTOM,
                TOPIC_INPUT_ISLAND_HORIZONTAL_INSET_DP,
                0f,
                TOPIC_INPUT_ISLAND_HORIZONTAL_INSET_DP,
                0f,
            ),
        )
        topicSearchPanel = panel
        topicSearchCountText = count
    }

    private fun createTopicInputIslandBackground(
        view: View,
        root: ViewGroup,
        source: BlurredBackgroundSource?,
        liquidGlassEffectAllowed: Boolean,
    ): BlurredBackgroundDrawable {
        val resolvedSource = source ?: mentionBlurSourceColor ?: BlurredBackgroundSourceColor().apply {
            setColor(topicDetailListBackgroundColor())
        }
        val factory = BlurredBackgroundDrawableViewFactory(resolvedSource).apply {
            mentionBlurWatcher?.let { setSourceRootView(it, root) }
            setLiquidGlassEffectAllowed(
                liquidGlassEffectAllowed && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS),
            )
        }
        return factory.create(
            view,
            object : BlurredBackgroundColorProviderThemed(resourceProvider, Theme.key_chat_messagePanelBackground) {
                override fun getBackgroundColor(): Int {
                    if (!BlurredBackgroundProviderImpl.checkBlurEnabled(resourceProvider)) {
                        return ColorUtils.setAlphaComponent(
                            Theme.getColor(Theme.key_chat_messagePanelBackground, resourceProvider),
                            255,
                        )
                    }
                    return if (!super.isDark()) {
                        ColorUtils.setAlphaComponent(super.getBackgroundColor(), 216)
                    } else {
                        super.getBackgroundColor()
                    }
                }
            },
        ).apply {
            setRadius(AndroidUtilities.dp(TOPIC_INPUT_ISLAND_RADIUS_DP).toFloat())
            setPadding(AndroidUtilities.dp(TOPIC_INPUT_ISLAND_HORIZONTAL_INSET_DP))
        }
    }

    private fun enterTopicSearchMode() {
        if (topicSearchOpen) return
        topicSearchOpen = true
        topicSearchQuery = ""
        topicSearchPendingNext = false
        topicSearchSession.clear()
        topicSearchNavigator.clear()
        adapter.setSearchHighlights("", emptyList())
        hideReplyComposerEmojiPanel(openKeyboard = false, animated = false)
        replyComposerPanel?.setEmojiPopupShowing(false)
        topicBottomViewsVisibilityController.setViewVisible(TOPIC_BOTTOM_SEARCH_CONTAINER, true, true)
        topicBottomViewsVisibilityController.setViewVisible(TOPIC_BOTTOM_INPUT_CONTAINER, false, true)
        topicSearchCountText?.setText("", false)
        floatingDateController?.hide(animated = false, force = true)
        updateTopicSearchControls(animated = true)
        updateReplyComposerBottomInset()
    }

    private fun exitTopicSearchMode() {
        if (!topicSearchOpen) return
        topicSearchOpen = false
        topicSearchJob?.cancel()
        topicSearchJob = null
        topicSearchSession.clear()
        topicSearchNavigator.clear()
        topicSearchQuery = ""
        topicSearchPendingNext = false
        adapter.setSearchHighlights("", emptyList())
        topicSearchCountText?.setText("", false)
        topicBottomViewsVisibilityController.setViewVisible(TOPIC_BOTTOM_INPUT_CONTAINER, true, true)
        topicBottomViewsVisibilityController.setViewVisible(TOPIC_BOTTOM_SEARCH_CONTAINER, false, true)
        updateTopicSearchControls(animated = true)
        updatePageDownControls(animated = true)
        updateReplyComposerBottomInset()
        actionBar.visibility = View.VISIBLE
        actionBar.alpha = 1f
        actionBar.bringToFront()
        // Restore middle capsule immediately (collapse anim can leave searchField still "visible").
        topicIdentityContainer?.setIdentityVisible(true)
        topicIdentityContainer?.requestLayout()
        actionBar.invalidate()
        updateCollapsedTitle()
    }

    private fun scheduleTopicSearch(query: String) {
        topicSearchQuery = query
        topicSearchJob?.cancel()
        val normalized = query.trim()
        adapter.setSearchHighlights(normalized, emptyList())
        if (normalized.isEmpty()) {
            topicSearchSession.clear()
            topicSearchNavigator.clear()
            topicSearchCountText?.setText("", true)
            getContext()?.let { context ->
                topicSearchCountText?.contentDescription = context.getString(R.string.topic_search_count_description)
            }
            updateTopicSearchControls(animated = true)
            return
        }
        topicSearchCountText?.setText("", false)
        topicSearchJob = collectorScope.launch {
            delay(TOPIC_SEARCH_DEBOUNCE_MS)
            startTopicSearch(normalized)
        }
    }

    private fun startTopicSearch(query: String) {
        topicSearchQuery = query
        val scopedQuery = topicScopedSearchQuery(query, topicId)
        if (scopedQuery.isBlank() || !topicSearchOpen) return
        topicSearchJob?.cancel()
        topicSearchPendingNext = false
        topicSearchNavigator.clear()
        val request = topicSearchSession.start(scopedQuery)
        renderTopicSearch()
        topicSearchJob = collectorScope.launch { executeTopicSearch(request) }
    }

    private suspend fun executeTopicSearch(request: TopicSearchRequest) {
        val result = topicSearchRepository.search(request.query, request.page, typeFilter = "topic")
        result.fold(
            onSuccess = { searchResult ->
                if (!topicSearchSession.accept(request, searchResult)) return
            },
            onFailure = { error ->
                if (!topicSearchSession.fail(request, error.message ?: error.javaClass.simpleName)) return
            },
        )
        if (!topicSearchOpen) return
        val snapshot = topicSearchSession.snapshot
        val current = topicSearchNavigator.submit(snapshot.result.posts)
        if (topicSearchPendingNext) {
            topicSearchPendingNext = false
            topicSearchNavigator.next()
        }
        renderTopicSearch()
        current?.let { jumpToTopicSearchMatch(topicSearchNavigator.current) }
    }

    private fun loadNextTopicSearchPage() {
        val request = topicSearchSession.nextPage() ?: return
        topicSearchPendingNext = true
        renderTopicSearch()
        topicSearchJob = collectorScope.launch { executeTopicSearch(request) }
    }

    private fun moveTopicSearch(previous: Boolean) {
        if (!topicSearchOpen) return
        val before = topicSearchNavigator.current
        if (previous) {
            topicSearchNavigator.previous()
        } else if (topicSearchNavigator.hasNext) {
            topicSearchNavigator.next()
        } else if (topicSearchSession.snapshot.hasMore) {
            loadNextTopicSearchPage()
            return
        }
        if (before != topicSearchNavigator.current) {
            jumpToTopicSearchMatch(topicSearchNavigator.current)
            renderTopicSearch()
        }
    }

    private fun jumpToTopicSearchMatch(match: io.github.baba983.nativedo.feature.topic.ui.TopicSearchMatch?) {
        match ?: return
        match.postId?.let(viewModel::requestJumpToPostId)
            ?: match.postNumber?.let(viewModel::requestJumpToReplyTarget)
    }

    private fun renderTopicSearch() {
        val countView = topicSearchCountText ?: return
        val context = getContext() ?: return
        val snapshot = topicSearchSession.snapshot
        adapter.setSearchHighlights(topicSearchQuery.trim(), topicSearchNavigator.matches)
        val text = when {
            snapshot.isInitialLoading || snapshot.isLoadingMore -> ""
            snapshot.initialError != null || snapshot.loadMoreError != null -> context.getString(R.string.search_topics_error_title)
            topicSearchNavigator.matches.isEmpty() -> {
                if (snapshot.query.isBlank()) "" else context.getString(TelegramR.string.NoResult)
            }
            else -> context.getString(
                TelegramR.string.Of,
                topicSearchNavigator.selectedIndex + 1,
                topicSearchNavigator.matches.size,
            )
        }
        countView.setText(text, true)
        countView.contentDescription = if (text.isBlank()) {
            context.getString(R.string.topic_search_count_description)
        } else {
            text
        }
        updateTopicSearchControls(animated = true)
    }

    private fun updateTopicSearchControls(animated: Boolean) {
        val controls = pageDownControls ?: return
        val showSearch = topicSearchOpen && topicSearchNavigator.matches.isNotEmpty()
        controls.setButtonContentDescription(
            ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP,
            LocaleController.getString(TelegramR.string.AccDescrSearchPrev),
        )
        controls.setButtonContentDescription(
            ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN,
            LocaleController.getString(TelegramR.string.AccDescrSearchNext),
        )
        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP, showSearch, animated)
        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN, showSearch, animated)
        controls.setButtonEnabled(
            ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP,
            showSearch && topicSearchNavigator.hasPrevious,
            animated,
        )
        controls.setButtonEnabled(
            ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN,
            showSearch && (topicSearchNavigator.hasNext || topicSearchSession.snapshot.hasMore),
            animated,
        )
        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, false, animated)
    }

    private fun createMentionSuggestions(context: Context, root: ViewGroup) {
        if (mentionSuggestionsView != null) return
        val view = TopicMentionSuggestionsView(
            context = context,
            baseUrl = baseUrl,
            topicId = topicId,
            scope = collectorScope,
            repository = mentionRepository,
            onUserSelected = { token, user ->
                replyComposerPanel?.replaceMention(token, user.username)
                mentionSuggestionsView?.dismiss()
            },
        )
        mentionSuggestionsView = view
        val blurSource = mentionBlurSource ?: mentionBlurSourceColor
        val watcher = mentionBlurWatcher
        if (blurSource != null && watcher != null) {
            val factory = BlurredBackgroundDrawableViewFactory(blurSource).apply {
                setSourceRootView(watcher, root)
                setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS))
            }
            mentionBackground = factory.create(
                view,
                object : BlurredBackgroundColorProviderThemed(resourceProvider, Theme.key_windowBackgroundWhite) {
                    override fun getBackgroundColor(): Int {
                        if (!BlurredBackgroundProviderImpl.checkBlurEnabled(resourceProvider)) {
                            return ColorUtils.setAlphaComponent(
                                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                                255,
                            )
                        }
                        return if (!super.isDark()) {
                            ColorUtils.setAlphaComponent(super.getBackgroundColor(), 216)
                        } else {
                            super.getBackgroundColor()
                        }
                    }
                },
            ).apply {
                setRadius(AndroidUtilities.dp(22f).toFloat())
                setPadding(AndroidUtilities.dp(5f))
            }
            view.setBackgroundDrawable(mentionBackground!!)
        }
        root.addView(
            view,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                TOPIC_MENTION_HOST_HEIGHT_DP.toFloat(),
                Gravity.BOTTOM,
                0f,
                0f,
                0f,
                0f,
            ),
        )
        view.setParticipants(latestTopicState.participants, latestTopicState.posts)
        replyComposerPanel?.refreshMentionSuggestions()
        replyComposerPanel?.bringToFront()
        actionBar.bringToFront()
        updateReplyComposerBottomInset()
    }

    private fun createMentionBlurSource(root: ViewGroup) {
        val colorSource = BlurredBackgroundSourceColor().apply {
            setColor(topicDetailListBackgroundColor())
        }
        mentionBlurSourceColor = colorSource
        mentionBlurWatcher = ViewPositionWatcher(root)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val noiseSuppressor = DownscaleScrollableNoiseSuppressor()
        mentionBlurNoiseSuppressor = noiseSuppressor

        val frostedSource = BlurredBackgroundSourceRenderNode(colorSource).apply {
            setOnDrawablesRelativePositionChangeListener(root::invalidate)
            setScrollableNoiseSuppressor(
                noiseSuppressor,
                DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS,
            )
            setUnderSource(colorSource)
        }
        mentionBlurSourceFrosted = frostedSource

        mentionBlurSource = if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
            BlurredBackgroundSourceRenderNode(colorSource).apply {
                setOnDrawablesRelativePositionChangeListener(root::invalidate)
                setScrollableNoiseSuppressor(
                    noiseSuppressor,
                    DownscaleScrollableNoiseSuppressor.DRAW_GLASS,
                )
                setUnderSource(colorSource)
            }
        } else {
            frostedSource
        }
    }

    private fun updateRootBlurSource(width: Int, height: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            width <= 0 || height <= 0
        ) {
            return
        }
        val glassSource = mentionBlurSource ?: return
        val frostedSource = mentionBlurSourceFrosted
        val noiseSuppressor = mentionBlurNoiseSuppressor ?: return
        val capture = mentionBlurCapture ?: return
        if (listView.height <= 0) return

        val actionBarPosition = if (mentionBlurPositions.isEmpty()) {
            RectF().also(mentionBlurPositions::add)
        } else {
            mentionBlurPositions[0]
        }
        actionBarPosition.set(
            0f,
            -AndroidUtilities.dp(48f).toFloat(),
            width.toFloat(),
            actionBar.measuredHeight + AndroidUtilities.dp(48f).toFloat(),
        )
        var positionsCount = 1
        if (frostedSource != null) {
            positionsCount += frostedSource.getVisiblePositions(
                mentionBlurPositions,
                positionsCount,
                AndroidUtilities.dp(48f),
            )
        }
        if (glassSource !== frostedSource) {
            positionsCount += glassSource.getVisiblePositions(
                mentionBlurPositions,
                positionsCount,
                AndroidUtilities.dp(8f),
            )
        }
        val mergedPositionsCount = RectFMergeBounding.mergeOverlapping(
            mentionBlurPositions,
            positionsCount,
            mentionBlurPositionsMerged,
        )
        val maxX = width.toFloat()
        val listTop = listView.y
        val listBottom = listView.y + listView.height
        for (index in 0 until mergedPositionsCount) {
            val position = mentionBlurPositionsMerged[index]
            position.left = position.left.coerceIn(0f, maxX)
            position.top = maxOf(listTop, position.top)
            position.right = position.right.coerceIn(0f, maxX)
            position.bottom = minOf(listBottom, position.bottom)
        }

        noiseSuppressor.setupRenderNodes(mentionBlurPositionsMerged, mergedPositionsCount)
        if (noiseSuppressor.invalidateResultRenderNodes(capture, width, height)) {
            glassSource.invalidateDisplayListForDrawables()
            if (frostedSource !== glassSource) {
                frostedSource?.invalidateDisplayListForDrawables()
            }
        }
    }

    private fun createPageDownControls(context: Context, root: ViewGroup) {
        if (pageDownControls != null) return
        val source = mentionBlurSource ?: mentionBlurSourceColor ?: BlurredBackgroundSourceColor().apply {
            setColor(topicDetailListBackgroundColor())
        }
        val backgroundFactory = BlurredBackgroundDrawableViewFactory(source).apply {
            mentionBlurWatcher?.let { setSourceRootView(it, root) }
            setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS))
        }
        val controls = ChatActivitySideControlsButtonsLayout(
            context,
            resourceProvider,
            BlurredBackgroundColorProviderThemed(resourceProvider, Theme.key_chat_messagePanelBackground),
            backgroundFactory,
        ).apply {
            setOnClickListener { buttonId, _ ->
                when (buttonId) {
                    ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP -> {
                        if (topicSearchOpen) {
                            moveTopicSearch(previous = true)
                        } else {
                            scrollToTopicTop()
                        }
                    }
                    ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_DOWN -> {
                        moveTopicSearch(previous = false)
                    }
                    ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN -> {
                        if (textSelectionHelper.isInSelectionMode) {
                            textSelectionHelper.clear(true)
                        }
                        viewModel.requestJumpToUnread()
                    }
                }
            }
            setButtonContentDescription(
                ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP,
                context.getString(R.string.topic_return_to_top),
            )
            setButtonCount(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, 0, false)
            showButton(ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP, false, false)
            showButton(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, false, false)
        }
        pageDownControls = controls
        root.addView(
            controls,
            LayoutHelper.createFrame(57f, 300f, Gravity.RIGHT or Gravity.BOTTOM),
        )
        updateReplyComposerBottomInset()
    }

    private fun createTopicActionBarGlass(root: ViewGroup) {
        // ChatActivity ActionBar glass pills sample the live list via
        // glassBackgroundSourceRenderNode / Frosted (DownscaleScrollableNoiseSuppressor),
        // falling back to wallpaper color only when blur3 is unavailable.
        val liquidGlass = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)
        val source = when (topicDetailActionBarGlassSourceKind(liquidGlass)) {
            TopicDetailBlurSourceKind.LIST_RENDER_NODE_GLASS,
            TopicDetailBlurSourceKind.LIST_RENDER_NODE_FROSTED,
            -> mentionBlurSource ?: mentionBlurSourceColor
            TopicDetailBlurSourceKind.WALLPAPER_COLOR -> mentionBlurSourceColor
        } ?: return
        val watcher = mentionBlurWatcher ?: return
        val backgroundFactory = BlurredBackgroundDrawableViewFactory(source).apply {
            setSourceRootView(watcher, root)
            setLiquidGlassEffectAllowed(liquidGlass)
        }
        // ChatActivity uses key_chat_messagePanelBackground for glass ActionBar pills.
        actionBar.setupGlass(
            backgroundFactory,
            object : BlurredBackgroundColorProviderThemed(resourceProvider, Theme.key_chat_messagePanelBackground) {
                override fun getBackgroundColor(): Int {
                    // ChatActivity blurredBackgroundColorProvider for glass ActionBar:
                    // light theme uses alpha 216 so posts show through the pills.
                    if (!BlurredBackgroundProviderImpl.checkBlurEnabled(resourceProvider)) {
                        return ColorUtils.setAlphaComponent(
                            Theme.getColor(Theme.key_chat_messagePanelBackground, resourceProvider),
                            255,
                        )
                    }
                    return if (!super.isDark()) {
                        ColorUtils.setAlphaComponent(super.getBackgroundColor(), 216)
                    } else {
                        super.getBackgroundColor()
                    }
                }
            },
        )
        actionBar.setCastShadows(false)
        actionBar.background = null
        // Chat glass ActionBar occupies the status bar; content draws underneath the
        // entire top chrome (not only under the three pills).
        actionBar.setOccupyStatusBar(true)
        actionBar.setClipChildren(false)
        actionBar.clipToPadding = false
    }

    private fun createTopicTopFade(context: Context, root: ViewGroup) {
        // ChatActivity wires FadeView to navbarContentDrawableFactory, which samples
        // navbarContentSourceWallpaper (underlayer), not the frosted/list RenderNode used
        // by glass pills. TopicDetail's wallpaper equivalent is mentionBlurSourceColor.
        // Binding frosted list capture here double-blurs the top band and can shift the
        // color seam that glass capsules then sample against.
        val source = when (topicDetailTopFadeSourceKind()) {
            TopicDetailBlurSourceKind.WALLPAPER_COLOR -> mentionBlurSourceColor
            TopicDetailBlurSourceKind.LIST_RENDER_NODE_FROSTED -> mentionBlurSourceFrosted
            TopicDetailBlurSourceKind.LIST_RENDER_NODE_GLASS -> mentionBlurSource
        } ?: return
        val factory = BlurredBackgroundDrawableViewFactory(source)
        val fadeView = ChatActivityFadeView(context).apply {
            setup(factory)
            setFadeHeightTop(AndroidUtilities.dp(48f))
            setFadeHeightBottom(AndroidUtilities.dp(48f))
            setFadeZoneBottom(0)
        }
        topicTopFadeView = fadeView
        root.addView(
            fadeView,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()),
        )
    }

    private fun updatePageDownScrollState(manager: LinearLayoutManager, dy: Int) {
        val snapshot = pageDownScrollSnapshot(manager)
        if (snapshot == null) {
            updatePageDownControls(animated = pageDownInitialized)
            return
        }
        pageDownLastKnownBelowPosts = snapshot.below
        pageDownLastKnownAbovePosts = snapshot.above
        if (snapshot.below == 0) {
            // Telegram: at the bottom (firstVisibleItem == 0 && forwardEndReached)
            // canShowPagedownButton=false and the pending unread count drains.
            if (!pageDownReachedLatest) {
                pageDownReachedLatest = true
                viewModel.markLoadedPostsRead()
            }
            pageDownCanShowFromScroll = false
            pageDownShownByScroll = false
            pageDownScrollAccumPx = 0
        } else {
            // Scrolling toward the latest passes posts under the bottom of the
            // viewport — advance the read position per scroll, matching the vendor
            // chat's updateMessagesVisiblePart, which marks visible messages read
            // and decrements newUnreadMessageCount on every scroll (not only after
            // the first bottom-reach). Monotonic in the ViewModel.
            if (dy > 0 && topicListUserScrollActive) {
                val lastVisiblePostNumber = snapshot.lastVisiblePostId.let { id ->
                    latestTopicState.posts.firstOrNull { post -> post.id == id }?.postNumber
                        ?.takeIf { number -> number > 0 }
                }
                if (lastVisiblePostNumber != null) {
                    viewModel.advanceReadPositionTo(lastVisiblePostNumber)
                }
            }

            // Telegram's totalDy machine: scrolling away from the latest accumulates
            // toward the reveal threshold; scrolling back while shown by scroll
            // accumulates the other way and hides. Signs are flipped vs upstream
            // because their chat list is inverted, our topic list heads at post 1.
            if (pageDownReachedLatest) {
                val visible = pageDownControls?.isButtonVisible(
                    ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN,
                ) ?: false
                if (dy < 0) {
                    if (!visible) {
                        pageDownScrollAccumPx += -dy
                        if (pageDownScrollAccumPx >= pageDownScrollThresholdPx) {
                            pageDownScrollAccumPx = 0
                            pageDownCanShowFromScroll = true
                            pageDownShownByScroll = true
                        }
                    }
                } else if (dy > 0 && pageDownShownByScroll && visible) {
                    pageDownScrollAccumPx += -dy
                    if (pageDownScrollAccumPx <= -pageDownScrollThresholdPx) {
                        pageDownScrollAccumPx = 0
                        pageDownCanShowFromScroll = false
                        pageDownShownByScroll = false
                    }
                }
            }
        }
        updatePageDownControls(animated = pageDownInitialized)
    }

    private fun updatePageDownControls(animated: Boolean) {
        val controls = pageDownControls ?: return
        if (topicSearchOpen) {
            updateTopicSearchControls(animated)
            return
        }
        val manager = listView.layoutManager as? LinearLayoutManager
        val measuredSnapshot = manager?.let(::pageDownScrollSnapshot)
        if (measuredSnapshot != null) {
            pageDownLastKnownBelowPosts = measuredSnapshot.below
            pageDownLastKnownAbovePosts = measuredSnapshot.above
        }
        val show = topicShouldShowPageDown(
            postsBelowViewport = pageDownLastKnownBelowPosts,
            selectionActive = textSelectionHelper.isInSelectionMode,
            canShowFromScroll = pageDownCanShowFromScroll,
        )
        val showReturnToTop = topicShouldShowReturnToTop(
            postsAboveViewport = pageDownLastKnownAbovePosts,
            selectionActive = textSelectionHelper.isInSelectionMode,
        )
        controls.setButtonContentDescription(
            ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP,
            controls.context.getString(R.string.topic_return_to_top),
        )
        controls.showButton(
            ChatActivitySideControlsButtonsLayout.BUTTON_SEARCH_UP,
            showReturnToTop,
            animated,
        )
        setPageDownCounter(latestTopicState.unreadPosts, animated)
        controls.setButtonLoading(
            ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN,
            latestTopicState.isJumpingToLatest,
            animated,
        )
        controls.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, show, animated)
    }

    private fun setPageDownCounter(count: Int, animated: Boolean) {
        val controls = pageDownControls ?: return
        if (count == pageDownSubmittedCount) return
        pageDownSubmittedCount = count
        controls.setButtonCount(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, count, animated)
    }

    private fun scrollToTopicTop() {
        if (textSelectionHelper.isInSelectionMode) {
            textSelectionHelper.clear(true)
        }
        listView.stopScroll()
        if (latestTopicState.posts.firstOrNull()?.postNumber != 1) {
            viewModel.requestJumpToReplyTarget(1)
            return
        }
        val manager = listView.layoutManager as? LinearLayoutManager ?: return
        manager.scrollToPositionWithOffset(0, 0)
        listView.post {
            floatingDateController?.showForProgrammaticUpdate()
            updatePageDownControls(animated = pageDownInitialized)
        }
    }

    private data class PageDownScrollSnapshot(
        val above: Int,
        val below: Int,
        val lastVisiblePostId: Long,
    )

    private fun pageDownScrollSnapshot(manager: LinearLayoutManager): PageDownScrollSnapshot? {
        val firstVisiblePosition = manager.findFirstVisibleItemPosition()
        val lastVisiblePosition = manager.findLastVisibleItemPosition()
        if (firstVisiblePosition == RecyclerView.NO_POSITION || lastVisiblePosition == RecyclerView.NO_POSITION) {
            return null
        }
        val firstVisiblePostId = (firstVisiblePosition..lastVisiblePosition)
            .firstNotNullOfOrNull(adapter::postIdAtAdapterPosition) ?: return null
        val lastVisiblePostId = (lastVisiblePosition downTo firstVisiblePosition)
            .firstNotNullOfOrNull(adapter::postIdAtAdapterPosition) ?: return null
        val above = topicPostsAboveViewportOrNull(latestTopicState.streamPostIds, firstVisiblePostId)
            ?: return null
        val below = topicPostsBelowViewportOrNull(latestTopicState.streamPostIds, lastVisiblePostId)
            ?: return null
        return PageDownScrollSnapshot(
            above = above,
            below = below,
            lastVisiblePostId = lastVisiblePostId,
        )
    }

    private fun handleReplyComposerKeyboardRequested(keyboardTarget: View) {
        openReplyComposerKeyboardInternal(keyboardTarget)
    }

    private fun handleReplyComposerEditTextBackKey(keyCode: Int, action: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK || action != KeyEvent.ACTION_UP) {
            return false
        }
        if (mentionSuggestionsView?.isOpen() == true) {
            mentionSuggestionsView?.dismiss()
            return true
        }
        if (isReplyComposerPopupShowing()) {
            hideReplyComposerPopup(byBackButton = true, openKeyboard = false)
            return true
        }
        if (replyComposerKeyboardHeight > 0) {
            replyComposerPanel?.let(::closeReplyComposerKeyboard)
            return true
        }
        return false
    }

    private fun handleReplyComposerEmojiButton(
        anchorView: View,
        keyboardTarget: View,
        onEmojiSelected: (String) -> Unit,
    ) {
        if (topicReplyComposerShouldIgnoreEmojiButton(
                keyboardAnimationInProgress = replyComposerPanel?.isKeyboardAnimationInProgress() == true,
                emojiPanelAnimationInProgress = replyComposerEmojiPanel?.isPanelAnimationInProgress() == true,
                waitingForKeyboardOpenAfterAnimation = replyComposerWaitingForKeyboardOpenAfterEmoji,
            )
        ) {
            return
        }
        val panelShowing = isReplyComposerPopupShowing()
        if (topicReplyComposerEmojiButtonAction(panelShowing) == TopicReplyComposerEmojiButtonAction.HIDE_POPUP_AND_OPEN_KEYBOARD) {
            openReplyComposerKeyboardInternal(keyboardTarget)
            return
        }
        showReplyComposerPopup(
            anchorView = anchorView,
            keyboardTarget = keyboardTarget,
            onEmojiSelected = onEmojiSelected,
        )
    }

    private fun showReplyComposerPopup(
        anchorView: View,
        keyboardTarget: View,
        onEmojiSelected: (String) -> Unit,
    ) {
        mentionSuggestionsView?.dismiss()
        val categories = emojiCategories.ifEmpty {
            topicReplyComposerFallbackEmojiCategories(
                reactionIds = validReactionIds.ifEmpty { DEFAULT_REACTION_IDS },
                emojiCatalog = emojiCatalog,
            )
        }
        if (categories.isEmpty()) return
        val context = anchorView.context
        hideReplyComposerPopup(byBackButton = false, openKeyboard = false, animated = false)
        val root = rootFrameLayout ?: fragmentView as? ViewGroup ?: return
        val panelHeight = topicReplyComposerEmojiPanelHeightPx(
            keyboardInset = replyComposerKeyboardHeight,
            fallbackHeight = topicReplyComposerRememberedKeyboardHeight(context, replyComposerLastKeyboardHeight),
            rootHeight = root.height,
        )
        val panel = TopicReplyComposerEmojiPanel(
            context = context,
            heightPx = panelHeight,
            baseUrl = baseUrl,
            categories = categories,
            onEmojiSelected = onEmojiSelected,
        )
        replyComposerEmojiPanel = panel
        replyComposerEmojiPanelHeight = panelHeight
        replyComposerPopupContentType = TopicReplyComposerPopupContentType.EMOJI_KEYBOARD
        topicInsetsStateHolder?.requestInAppKeyboardHeight(panelHeight)
        AndroidUtilities.hideKeyboard(keyboardTarget)
        panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateReplyComposerEmojiPanelContentInset()
        }
        val inAppKeyboardContainer = topicInputViewsContainer?.inAppKeyboardBubbleContainer
        if (inAppKeyboardContainer != null) {
            inAppKeyboardContainer.addView(
                panel,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.BOTTOM,
                ),
            )
        } else {
            root.addView(
                panel,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    panelHeight,
                    Gravity.BOTTOM,
                ),
            )
        }
        replyComposerPanel?.setEmojiPopupShowing(true)
        updateReplyComposerBottomInset()
        panel.show()
        replyComposerPanel?.bringToFront()
        actionBar.bringToFront()
        panel.post { updateReplyComposerEmojiPanelContentInset() }
    }

    private fun hideReplyComposerPopup(
        byBackButton: Boolean,
        openKeyboard: Boolean,
        keyboardTarget: View? = null,
        animated: Boolean = true,
    ) {
        if (openKeyboard && keyboardTarget != null) {
            markReplyComposerWaitingForKeyboardOpenAfterEmoji()
            replyComposerPanel?.requestKeyboardWithRetry(forceWaitForKeyboard = true)
                ?: topicReplyComposerRequestKeyboard(keyboardTarget)
        } else if (!openKeyboard) {
            clearReplyComposerWaitingForKeyboardOpenAfterEmoji()
        }
        replyComposerPanel?.setEmojiPopupShowing(false)
        replyComposerPopupContentType = TopicReplyComposerPopupContentType.NONE
        topicInsetsStateHolder?.resetInAppKeyboardHeight(openKeyboard)
        val panel = replyComposerEmojiPanel
        if (panel == null) {
            replyComposerEmojiPanelHeight = 0
            updateReplyComposerBottomInset()
            return
        }
        replyComposerEmojiPanel = null
        panel.dismiss(animated) {
            (panel.parent as? ViewGroup)?.removeView(panel)
            if (replyComposerEmojiPanel == null) {
                replyComposerEmojiPanelHeight = 0
                updateReplyComposerBottomInset()
            }
        }
    }

    private fun hideReplyComposerEmojiPanel(
        openKeyboard: Boolean,
        keyboardTarget: View? = null,
        animated: Boolean = true,
    ) {
        hideReplyComposerPopup(
            byBackButton = false,
            openKeyboard = openKeyboard,
            keyboardTarget = keyboardTarget,
            animated = animated,
        )
    }

    private fun openReplyComposerKeyboardInternal(keyboardTarget: View) {
        if (isReplyComposerPopupShowing()) {
            hideReplyComposerPopup(
                byBackButton = false,
                openKeyboard = true,
                keyboardTarget = keyboardTarget,
                animated = true,
            )
            return
        }
        replyComposerPanel?.requestKeyboardWithRetry() ?: topicReplyComposerRequestKeyboard(keyboardTarget)
    }

    private fun isReplyComposerPopupShowing(): Boolean {
        return replyComposerPopupContentType != TopicReplyComposerPopupContentType.NONE ||
            replyComposerEmojiPanel != null ||
            replyComposerEmojiPanelHeight > 0
    }

    private fun closeReplyComposerKeyboard(panel: TopicReplyComposerPanel) {
        panel.closeKeyboard()
    }

    private fun markReplyComposerWaitingForKeyboardOpenAfterEmoji() {
        replyComposerWaitingForKeyboardOpenAfterEmoji = true
        AndroidUtilities.cancelRunOnUIThread(clearReplyComposerWaitingForKeyboardOpenRunnable)
        AndroidUtilities.runOnUIThread(
            clearReplyComposerWaitingForKeyboardOpenRunnable,
            topicReplyComposerKeyboardOpenAfterEmojiWaitMs(),
        )
    }

    private fun clearReplyComposerWaitingForKeyboardOpenAfterEmoji() {
        if (!replyComposerWaitingForKeyboardOpenAfterEmoji) {
            AndroidUtilities.cancelRunOnUIThread(clearReplyComposerWaitingForKeyboardOpenRunnable)
            return
        }
        replyComposerWaitingForKeyboardOpenAfterEmoji = false
        AndroidUtilities.cancelRunOnUIThread(clearReplyComposerWaitingForKeyboardOpenRunnable)
    }

    private fun updateReplyComposerPanel(state: TopicUiState) {
        val panel = replyComposerPanel ?: return
        panel.bindDraft(
            state.replyDraft,
            state.posts.firstOrNull { it.postNumber == state.replyDraft.replyToPostNumber },
        )
        panel.setSubmitting(state.isSubmittingReply)
        val error = state.replySubmitErrorMessage
        if (error != null) {
            panel.setError(error)
            return
        }
        panel.setError(null)
    }

    private fun applyTopicListChromePadding() {
        if (!::listView.isInitialized) return
        val actionBarHeight = if (actionBar.measuredHeight > 0) {
            actionBar.measuredHeight
        } else {
            AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight()
        }
        val nextTop = topicDetailListContentTopPaddingPx(actionBarHeight)
        topicTopFadeView?.setFadeZoneTop(actionBarHeight + AndroidUtilities.dp(1f))
        if (listView.paddingTop != nextTop) {
            // Preserve first-visible offset when chrome top pad is corrected after measure
            // so content does not appear to auto-scroll.
            val lm = listView.layoutManager as? LinearLayoutManager
            val firstPos = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            val firstView = if (firstPos != RecyclerView.NO_POSITION) lm?.findViewByPosition(firstPos) else null
            val offsetFromTop = if (firstView != null) firstView.top - listView.paddingTop else 0
            listView.setPadding(listView.paddingLeft, nextTop, listView.paddingRight, listView.paddingBottom)
            if (firstPos != RecyclerView.NO_POSITION && lm != null) {
                topicScrollLog("chrome_padding_changed", adapter.postNumberAtAdapterPosition(firstPos) ?: 0, offsetFromTop)
                lm.scrollToPositionWithOffset(firstPos, offsetFromTop)
            }
        }
    }

    private fun updateReplyComposerBottomInset() {
        if (!::listView.isInitialized) return
        applyTopicListChromePadding()
        updateTopicInputBubbleHeight()
        val inputIslandBottomGap = AndroidUtilities.dp(TOPIC_INPUT_ISLAND_BOTTOM_GAP_DP)
        val inputFactor = topicBottomViewsVisibilityController.getVisibility(TOPIC_BOTTOM_INPUT_CONTAINER)
        val searchFactor = topicBottomViewsVisibilityController.getVisibility(TOPIC_BOTTOM_SEARCH_CONTAINER)
        val bottomPanelAttached = inputFactor > 0f || searchFactor > 0f
        val panelHeight = if (bottomPanelAttached) {
            (topicInputViewsContainer?.inputBubbleHeight?.roundToInt()
                ?: AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat())) + inputIslandBottomGap
        } else {
            0
        }
        val insetsStateHolder = topicInsetsStateHolder
        // ActionBarLayout already dispatches insets.inset(0,0,0, nav+tabs) into the
        // edge-to-edge LayoutContainer, so animatedMaxBottomInset is relative to the
        // shortened host — do not subtract tabs again.
        //
        // Chat keeps checkInsets() running during AdjustPan. Island position is owned
        // solely by ChatInputViewsContainer (-animatedMaxBottomInset - 9dp); the
        // composer panel must not also translate by keyboardInset.
        // Relative to shortened host (nav+tabs already in ActionBarLayout margin).
        val animatedInset = (
            insetsStateHolder?.animatedMaxBottomInset?.roundToInt()
                ?: topicReplyComposerActiveBottomInset(
                    panelAttached = bottomPanelAttached,
                    keyboardInset = replyComposerKeyboardHeight,
                    emojiPanelHeight = replyComposerEmojiPanelHeight,
                )
            ).coerceAtLeast(0)
        // While AdjustPan carries the host for system IME, do not also lift island /
        // pageDown by the same IME amount (would stack pan + inset → jump then settle).
        // Floor is 0 relative to host; emoji panel height still lifts when in-app keyboard.
        val panInProgress = rootFrameLayout?.isPanAnimationInProgress() == true
        val activeInset = when {
            !bottomPanelAttached -> 0
            panInProgress && replyComposerKeyboardHeight > AndroidUtilities.dp(20f) ->
                topicDetailPanSafeBottomInsetPx(
                    navigationBarInset = 0,
                    inAppKeyboardHeight = replyComposerEmojiPanelHeight,
                    systemImeHeight = replyComposerKeyboardHeight,
                )
            else -> animatedInset
        }
        replyComposerPanel?.setKeyboardInset(replyComposerKeyboardHeight)
        val listInset = activeInset
        mentionSuggestionsView?.translationY = -(panelHeight + activeInset).toFloat()
        pageDownControls?.translationY = -topicPageDownControlsBottomOffset(
            panelHeight = panelHeight,
            panelBottomInset = listInset,
        ).toFloat()
        val nextBottom = topicReplyComposerListBottomPadding(
            basePadding = listBottomBasePadding,
            panelHeight = panelHeight,
            keyboardInset = listInset,
        )
        if (listView.paddingBottom != nextBottom) {
            listView.setPadding(listView.paddingLeft, listView.paddingTop, listView.paddingRight, nextBottom)
        }
        // Chat keeps checkInsets during pan, but Chat's reversed list does not also ride a
        // full host translation the same way for the island. When AdjustPan is carrying the
        // host for system IME, skip island re-lift so pan + animatedMaxBottomInset don't stack
        // (composer / pageDown "jump then settle" with Browser bar).
        if (topicDetailShouldApplyInputInsets(
                panInProgress = rootFrameLayout?.isPanAnimationInProgress() == true,
                systemImeHeight = replyComposerKeyboardHeight,
            )
        ) {
            topicInputViewsContainer?.checkInsets()
        }
        updateReplyComposerEmojiPanelContentInset()
    }

    private fun updateReplyComposerEmojiPanelContentInset() {
        val emojiPanel = replyComposerEmojiPanel ?: return
        val composer = replyComposerPanel ?: return
        if (!emojiPanel.isLaidOut || !composer.isLaidOut) {
            emojiPanel.post { updateReplyComposerEmojiPanelContentInset() }
            return
        }
        val panelLocation = IntArray(2)
        val composerLocation = IntArray(2)
        emojiPanel.getLocationInWindow(panelLocation)
        composer.getLocationInWindow(composerLocation)
        val composerBottom = composerLocation[1] + composer.height
        val panelTop = panelLocation[1]
        emojiPanel.setContentTopInset((composerBottom - panelTop).coerceAtLeast(0))
    }

    private fun handleJumpEvent(state: TopicUiState) {
        val event = state.jumpEvent ?: return
        val manager = listView.layoutManager as? LinearLayoutManager ?: return
        val adapterPosition = adapter.adapterPositionForPostId(event.postId) ?: event.itemIndex
        val firstPost = adapter.postNumberAtAdapterPosition(adapterPosition) == 1
        manager.scrollToPositionWithOffset(if (firstPost) 0 else adapterPosition,
            if (firstPost) 0 else AndroidUtilities.dp(8f))
        topicScrollLog("jump_event", adapter.postNumberAtAdapterPosition(adapterPosition) ?: 0, AndroidUtilities.dp(8f))
        listView.post { floatingDateController?.showForProgrammaticUpdate() }
        viewModel.consumeJumpEvent(event.nonce)
    }

    override fun getNavigationBarColor(): Int {
        return Theme.getColor(Theme.key_windowBackgroundGray)
    }

    override fun isSupportEdgeToEdge(): Boolean {
        return true
    }

    override fun drawEdgeNavigationBar(): Boolean {
        return false
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

        data class DateSeparator(val date: TopicDateHeader) : TopicDetailRow {
            override val stableId: Long = date.stableId
        }

        data class PostRow(
            val post: Post,
            val date: TopicDateHeader?,
            val replyCount: Int,
            val replyParticipants: List<Post>,
            val validReactionIds: List<String>,
            val jumpHighlightNonce: Int,
        ) : TopicDetailRow {
            override val stableId: Long = post.id
        }

        data object PrependLoading : TopicDetailRow {
            override val stableId: Long = -8L
        }

        data class PrependError(val text: String) : TopicDetailRow {
            override val stableId: Long = -9L
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
        val createdAt: String,
        val updatedAt: String,
        val editCount: Int,
        val cookedHtmlHash: Int,
        val currentUserReaction: String?,
        val reactionUsersCount: Int,
        val reactionsSignature: Int,
        val isBookmarked: Boolean,
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
        private var terminalDateSeparatorPosition = RecyclerView.NO_POSITION
        private var localPostsByNumber: Map<Int, String> = emptyMap()
        private var searchHighlightText = ""
        private var searchHighlightPostIds: Set<Long> = emptySet()
        private var searchHighlightPostNumbers: Set<Int> = emptySet()

        init {
            setHasStableIds(true)
        }

        fun submit(state: TopicUiState) {
            validReactionIds = state.validReactions
            emojiCatalog = state.emojiCatalog
            emojiCategories = state.emojiCategories
            replySheetEntriesByPostNumber = TopicReplyTargetResolver.replySheetEntriesByPostNumber(state.posts)
            localPostsByNumber = state.posts.mapNotNull { post ->
                post.postNumber?.let { number -> number to post.cookedHtml }
            }.toMap()
            val previousHtmlByPostId = rows.filterIsInstance<TopicDetailRow.PostRow>()
                .associate { row -> row.post.id to row.post.cookedHtml.hashCode() }
            val updatedPostsById = state.posts.associateBy { it.id }
            for ((postId, previousHash) in previousHtmlByPostId) {
                val updated = updatedPostsById[postId] ?: continue
                if (updated.cookedHtml.hashCode() != previousHash) {
                    parsedBodyCache.keys.removeAll { key -> key.postId == postId }
                    bodyStateCache.keys.removeAll { key -> key.postId == postId }
                }
            }
            val oldRows = rows
            val newRows = buildRows(state)
            val diff = DiffUtil.calculateDiff(TopicRowsDiffCallback(oldRows, newRows), false)
            rows = newRows
            terminalDateSeparatorPosition = rows.indexOfLast { row -> row is TopicDetailRow.DateSeparator }
            diff.dispatchUpdatesTo(this)
        }

        fun setSearchHighlights(text: String, matches: List<io.github.baba983.nativedo.feature.topic.ui.TopicSearchMatch>) {
            val nextText = text.trim()
            val nextPostIds = matches.mapNotNullTo(linkedSetOf()) { match -> match.postId }
            val nextPostNumbers = matches.mapNotNullTo(linkedSetOf()) { match -> match.postNumber }
            if (searchHighlightText == nextText &&
                searchHighlightPostIds == nextPostIds &&
                searchHighlightPostNumbers == nextPostNumbers
            ) {
                return
            }
            searchHighlightText = nextText
            searchHighlightPostIds = nextPostIds
            searchHighlightPostNumbers = nextPostNumbers
            rows.forEachIndexed { index, row ->
                if (row is TopicDetailRow.PostRow) {
                    notifyItemChanged(index, TOPIC_SEARCH_HIGHLIGHT_PAYLOAD)
                }
            }
        }

        fun postIdAtAdapterPosition(position: Int): Long? {
            return (rows.getOrNull(position) as? TopicDetailRow.PostRow)?.post?.id
        }

        fun postNumberAtAdapterPosition(position: Int): Int? {
            return (rows.getOrNull(position) as? TopicDetailRow.PostRow)?.post?.postNumber
        }

        fun adapterPositionForPostId(postId: Long): Int? {
            return rows.indexOfFirst { row -> row is TopicDetailRow.PostRow && row.post.id == postId }
                .takeIf { position -> position >= 0 }
        }

        fun firstPostAdapterPosition(): Int? {
            return rows.indexOfFirst { row -> row is TopicDetailRow.PostRow }
                .takeIf { position -> position >= 0 }
        }

        fun floatingDateRowAt(position: Int): TopicFloatingDateRow? {
            return when (val row = rows.getOrNull(position)) {
                is TopicDetailRow.PostRow -> TopicFloatingDateRow(TopicFloatingDateRowKind.POST, row.date)
                is TopicDetailRow.DateSeparator -> TopicFloatingDateRow(
                    kind = TopicFloatingDateRowKind.DATE,
                    date = row.date,
                    isTerminalDateSeparator = position == terminalDateSeparatorPosition,
                )
                null -> null
                else -> TopicFloatingDateRow(TopicFloatingDateRowKind.OTHER, null)
            }
        }

        fun resetPollInteractionState(postId: Long) {
            bodyStateCache.filterKeys { key -> key.postId == postId }.values.forEach { state ->
                state.inFlightPollIndexes.clear()
                state.removedPollIndexes.clear()
                state.submittedPollIndexes.clear()
                state.selectedPollOptionIndexesByIndex.clear()
            }
            val positions = rows.mapIndexedNotNull { index, row ->
                if (row is TopicDetailRow.PostRow && row.post.id == postId) index else null
            }
            positions.forEach(::notifyItemChanged)
        }

        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
            return when (rows.getOrNull(holder.bindingAdapterPosition)) {
                is TopicDetailRow.PostRow,
                is TopicDetailRow.InitialError,
                is TopicDetailRow.PrependError,
                is TopicDetailRow.AppendError -> true
                else -> false
            }
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemId(position: Int): Long = rows[position].stableId

        override fun getItemViewType(position: Int): Int {
            return when (rows[position]) {
                TopicDetailRow.InitialSkeleton -> VIEW_TYPE_SKELETON
                TopicDetailRow.PrependLoading -> VIEW_TYPE_SKELETON
                TopicDetailRow.AppendLoading -> VIEW_TYPE_SKELETON
                is TopicDetailRow.InitialError,
                TopicDetailRow.Empty,
                is TopicDetailRow.PrependError,
                is TopicDetailRow.AppendError,
                TopicDetailRow.EndOfList -> VIEW_TYPE_FOOTER
                is TopicDetailRow.Header -> VIEW_TYPE_HEADER
                is TopicDetailRow.DateSeparator -> VIEW_TYPE_DATE
                is TopicDetailRow.PostRow -> VIEW_TYPE_POST
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = when (viewType) {
                VIEW_TYPE_POST -> TopicPostCell(context).apply {
                    delegate = postCellDelegate
                    textSelectionHelper = this@TopicDetailFragment.textSelectionHelper
                    setSvgSpriteMarkup(this@TopicDetailFragment.svgSpriteMarkup)
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                VIEW_TYPE_SKELETON -> TopicSkeletonView(context)
                VIEW_TYPE_LOADING -> LoadingCell(context)
                VIEW_TYPE_FOOTER -> TopicFooterCell(context)
                VIEW_TYPE_DATE -> TopicDateCell(context, false).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                else -> TopicHeaderCell(context)
            }
            return RecyclerListView.Holder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                TopicDetailRow.InitialSkeleton -> (holder.itemView as TopicSkeletonView).setRows(3)
                TopicDetailRow.PrependLoading -> (holder.itemView as TopicSkeletonView).setRows(1)
                TopicDetailRow.AppendLoading -> (holder.itemView as TopicSkeletonView).setRows(1)
                is TopicDetailRow.InitialError -> (holder.itemView as TopicFooterCell).bind(row.text, viewModel::refresh)
                TopicDetailRow.Empty -> (holder.itemView as TopicFooterCell).bind("No posts", null)
                is TopicDetailRow.PrependError -> (holder.itemView as TopicFooterCell).bind(row.text, viewModel::loadPrevious)
                is TopicDetailRow.AppendError -> (holder.itemView as TopicFooterCell).bind(row.text, viewModel::loadMore)
                TopicDetailRow.EndOfList -> (holder.itemView as TopicFooterCell).bind("End of topic", null)
                is TopicDetailRow.Header -> (holder.itemView as TopicHeaderCell).bind(row.title, row.totalPosts, row.viewCount, row.tags)
                is TopicDetailRow.DateSeparator -> (holder.itemView as TopicDateCell).apply {
                    alpha = 1f
                    setCustomDate(row.date.epochSeconds)
                }
                is TopicDetailRow.PostRow -> {
                    val post = row.post
                    val htmlHash = post.cookedHtml.hashCode()
                    val parsedBlocks = parsedBodyCache.getOrPut(ParsedBodyCacheKey(post.id, htmlHash)) {
                        parseCookedPostBody(post.cookedHtml.ifBlank { "<p>（空内容）</p>" }, baseUrl)
                    }
                    val blockState = bodyStateCache.getOrPut(BodyStateCacheKey(post.id, htmlHash, post.editCount)) {
                        PostBodyBlockState()
                    }
                    (holder.itemView as TopicPostCell).apply {
                        setSvgSpriteMarkup(this@TopicDetailFragment.svgSpriteMarkup)
                        setPost(
                            post,
                            rows.take(position).lastOrNull { it is TopicDetailRow.PostRow }?.let { (it as TopicDetailRow.PostRow).post },
                            rows.drop(position + 1).firstOrNull { it is TopicDetailRow.PostRow }?.let { (it as TopicDetailRow.PostRow).post },
                            rows.take(position).none { it is TopicDetailRow.PostRow },
                            rows.drop(position + 1).none { it is TopicDetailRow.PostRow },
                            topicId,
                            baseUrl,
                            localPostsByNumber,
                            emojiCatalog,
                            row.validReactionIds,
                            row.replyCount,
                            row.replyParticipants,
                            parsedBlocks,
                            blockState,
                            row.jumpHighlightNonce,
                            { viewModel.clearJumpHighlight(row.jumpHighlightNonce) },
                        )
                        setHighlightedText(searchHighlightFor(post))
                        contentDescription = postContentDescription(post, parsedBlocks)
                    }
                }
            }
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
            payloads: MutableList<Any?>,
        ) {
            if (payloads.any { payload -> payload == TOPIC_SEARCH_HIGHLIGHT_PAYLOAD }) {
                val row = rows.getOrNull(position) as? TopicDetailRow.PostRow
                val cell = holder.itemView as? TopicPostCell
                if (row != null && cell != null) {
                    cell.setHighlightedText(searchHighlightFor(row.post))
                    return
                }
            }
            super.onBindViewHolder(holder, position, payloads)
        }

        /** Spoken post: the floor and author, then the body, images included (`postBodyAccessibilityText`). */
        private fun postContentDescription(post: Post, blocks: List<CookedPostBlock>): CharSequence {
            val author = topicPostDisplayName(post.authorDisplayName, post.authorUsername)
            val floor = post.postNumber?.let { number -> topicFloorLabelForPostNumber(number) }
            val header = if (floor != null) "$floor，$author" else author
            val body = postBodyAccessibilityText(blocks)
            return if (body.isEmpty()) header else "$header。$body"
        }

        private fun searchHighlightFor(post: Post): String {
            if (searchHighlightText.isEmpty()) return ""
            val matches = post.id in searchHighlightPostIds ||
                post.postNumber?.let(searchHighlightPostNumbers::contains) == true
            return if (matches) searchHighlightText else ""
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
            if (state.isLoadingPrevious) {
                rows += TopicDetailRow.PrependLoading
            } else if (state.loadPreviousErrorMessage != null) {
                rows += TopicDetailRow.PrependError(state.loadPreviousErrorMessage)
            }
            // Topic chrome (title / meta) lives in the always-on ActionBar identity
            // capsule — do not also render a list Header cell (felt redundant).
            val repliesByNumber = state.posts.groupBy { post -> post.replyToPostNumber }
            topicPostDateEntries(state.posts).forEach { entry ->
                val post = entry.post
                entry.header?.let { date -> rows += TopicDetailRow.DateSeparator(date) }
                val replies = (
                    post.postNumber?.let { repliesByNumber[it] }.orEmpty() +
                        state.replyPreviewsBySourcePostId[post.id].orEmpty()
                    ).distinctBy(Post::id)
                rows += TopicDetailRow.PostRow(
                    post = post,
                    date = entry.date,
                    replyCount = topicReplyFooterCount(post, replies.size),
                    replyParticipants = replies,
                    validReactionIds = state.validReactions.ifEmpty { DEFAULT_REACTION_IDS },
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
                TopicDetailRow.PrependLoading,
                TopicDetailRow.AppendLoading,
                TopicDetailRow.EndOfList -> row
                is TopicDetailRow.InitialError -> row.text
                is TopicDetailRow.PrependError -> row.text
                is TopicDetailRow.AppendError -> row.text
                is TopicDetailRow.Header -> row
                is TopicDetailRow.DateSeparator -> row.date
                is TopicDetailRow.PostRow -> listOf(
                    postDiffKey(row.post),
                    row.replyCount,
                    row.replyParticipants.map { reply ->
                        listOf(reply.id, reply.authorUsername, reply.authorDisplayName, reply.authorAvatarTemplate, reply.authorAnimatedAvatarUrl)
                    },
                    row.validReactionIds,
                    row.jumpHighlightNonce,
                )
            }
        }

        private fun postDiffKey(post: Post): PostDiffKey {
            return PostDiffKey(
                id = post.id,
                createdAt = post.createdAt,
                updatedAt = post.updatedAt,
                editCount = post.editCount,
                cookedHtmlHash = post.cookedHtml.hashCode(),
                currentUserReaction = post.currentUserReaction,
                reactionUsersCount = post.reactionUsersCount,
                reactionsSignature = post.reactions.joinToString(separator = "|") { reaction ->
                    "${reaction.id}:${reaction.count}:${reaction.iconUrl}:${reaction.reacted}"
                }.hashCode(),
                isBookmarked = post.isBookmarked,
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

        override fun didPressReaction(post: Post, reaction: PostReaction, anchorView: View, x: Float, y: Float) {
            viewModel.openReactionSheet(post.id, post.reactions, reaction.id, post.reactionUsersCount)
        }

        override fun didPressMoreReactions(post: Post, reactions: List<PostReaction>, initialReactionId: String?, anchorView: View, x: Float, y: Float) {
            showReactionPickerOptions(post, reactions, initialReactionId, anchorView, x, y)
        }

        override fun didPressReply(post: Post) {
            val previewCount = post.postNumber?.let(replySheetEntriesByPostNumber::get)?.size ?: 0
            viewModel.openPostReplies(post, topicReplyFooterCount(post, previewCount))
        }

        override fun didLongPressPost(post: Post, anchorView: View, x: Float, y: Float) {
            showPostOptions(post, anchorView, x, y)
        }

        override fun didExpandQuote(topicId: Long, postNumber: Int, onResult: (String?) -> Unit) {
            collectorScope.launch {
                val html = viewModel.expandQuote(topicId, postNumber).getOrNull()
                onResult(html)
            }
        }

        override fun didPressPollOptions(post: Post, pollName: String, optionIds: List<String>) {
            viewModel.onPollOptionsSelected(post.id, post.postNumber, pollName, optionIds)
        }

        override fun didPressPollVoteRemoved(post: Post, pollName: String) {
            viewModel.onPollVoteRemoved(post.id, post.postNumber, pollName)
        }

        override fun didPressPollVoters(post: Post, args: PollVotersSheetArgs) {
            val hostContext = context ?: return
            val pollBlock = findCookedPoll(
                parseCookedPostBody(post.cookedHtml.ifBlank { "<p>（空内容）</p>" }, baseUrl),
                args.pollName,
            )
            TopicPollVotesAlert.show(
                context = hostContext,
                baseUrl = baseUrl,
                postId = post.id,
                args = args.copy(
                    totalVoters = pollBlock?.voters ?: args.options.sumOf { option -> option.votes },
                    multiple = pollBlock?.type.equals("multiple", ignoreCase = true),
                ),
                loadVoters = { optionId, page, onResult -> viewModel.loadPollVoters(post.id, args.pollName, optionId, page, onResult) },
                onUserClick = { username ->
                    val activity = parentActivity
                    if (activity != null) {
                        Browser.openUrl(activity, "$baseUrl/u/$username")
                    }
                },
            )
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

    private fun showPostOptions(post: Post, anchorView: View, pressX: Float, pressY: Float) {
        val context = anchorView.context
        postOptions?.dismiss()
        reactionRailPopup?.dismiss()
        reactionRailPopup = null
        val popupLayout = ActionBarPopupWindow.ActionBarPopupWindowLayout(
            context,
            TelegramR.drawable.popup_fixed_alert4,
            null,
        ).apply {
            setMinimumWidth(AndroidUtilities.dp(200f))
            setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground))
            setFitItems(true)
        }
        val reactionIds = postOptionsReactionIds(post.currentUserReaction, validReactionIds, post.reactions, DEFAULT_REACTION_IDS)
        var reactionGrid: ReactionPickerRailView? = null
        var scrimContainer: TopicPostScrimPopupContainerLayout? = null
        if (reactionIds.isNotEmpty()) {
            reactionGrid = ReactionPickerRailView(
                context = context,
                baseUrl = baseUrl,
                emojiCatalog = emojiCatalog,
                showMoreButton = true,
                onMoreReactionsSelected = more@{
                    val rail = reactionGrid ?: return@more
                    val container = scrimContainer ?: return@more
                    showExpandedReactionPicker(
                        post = post,
                        reactionIds = reactionIds,
                        selectedReactionId = post.currentUserReaction,
                        collapsedRail = rail,
                        scrimContainer = container,
                    )
                },
            ) { selection ->
                selectReactionFromPicker(post, selection) {
                    postOptions?.dismiss()
                    postOptions = null
                }
            }.apply {
                bind(reactionIds, post.currentUserReaction)
            }
        }
        val actions = postOptionsActions(post.isBookmarked)
        actions.forEachIndexed { index, action ->
            val item = ActionBarMenuSubItem(context, index == 0 && reactionIds.isEmpty(), index == actions.lastIndex, null).apply {
                setMinimumWidth(AndroidUtilities.dp(200f))
                setColors(
                    Theme.getColor(Theme.key_actionBarDefaultSubmenuItem),
                    Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon),
                )
                setSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector))
                setTextAndIcon(context.getString(action.titleRes), action.iconRes)
                setOnClickListener {
                    postOptions?.dismiss()
                    postOptions = null
                    when (action.kind) {
                        TopicPostActionKind.REPLY -> showReplyComposer(initialText = "", replyToPost = post)
                        TopicPostActionKind.SELECT_TEXT -> {
                            (anchorView as? TopicPostCell)?.startTextSelectionAt(pressX, pressY)
                        }
                        TopicPostActionKind.COPY_LINK -> {
                            AndroidUtilities.addToClipboard(postLink(post))
                            BulletinFactory.of(this@TopicDetailFragment).createCopyLinkBulletin().show(topicCopyLinkBulletinOnTop())
                        }
                        TopicPostActionKind.BOOKMARK -> {
                            val messageRes = postBookmarkFeedbackMessageRes(post.isBookmarked)
                            viewModel.togglePostBookmark(post.id)
                            BulletinFactory.of(this@TopicDetailFragment).createInfoBulletin(context.getString(messageRes)).show()
                        }
                    }
                }
            }
            popupLayout.addView(item)
        }
        var popup: ActionBarPopupWindow? = null
        val container = TopicPostScrimPopupContainerLayout(context) {
            popup?.dismiss()
        }.apply {
            reactionGrid?.let { rail ->
                addReactionRail(
                    rail,
                    reactionPickerRailWidthDp(
                        reactionPickerRailShownItemCount(
                            reactionCount = reactionIds.size,
                            showMoreButton = true,
                        ),
                    ),
                )
            }
            addPopupMenu(popupLayout)
        }
        scrimContainer = container
        popup = object : ActionBarPopupWindow(container, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            override fun dismiss() {
                dismiss(true)
            }

            override fun dismiss(animated: Boolean) {
                dismissExpandedReactionPicker(animated = false)
                if (animated) {
                    reactionGrid?.startDismissAnimation()
                } else {
                    reactionGrid?.cancelEnterAnimation()
                }
                rootFrameLayout?.hideScrim(this, animated)
                super.dismiss(animated)
                if (postOptions === this) {
                    postOptions = null
                }
                unlockPostOptionsListScroll()
            }
        }.apply {
            setPauseNotifications(true)
            setDismissAnimationDuration(220)
            isOutsideTouchable = true
            isClippingEnabled = true
            isFocusable = true
            animationStyle = if (reactionGrid != null) 0 else TelegramR.style.PopupContextAnimation
            inputMethodMode = ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        val position = measureTopicPostOptionsPopup(
            scrimContainer = container,
            anchorView = anchorView,
            pressX = pressX,
            pressY = pressY,
        )
        val window = popup ?: return
        postOptions = window
        lockPostOptionsListScroll()
        window.showAtLocation(anchorView, Gravity.LEFT or Gravity.TOP, position.x, position.y)
        rootFrameLayout?.showScrim(anchorView, window, true)
        reactionGrid?.startEnterAnimation(animatePopup = true)
    }

    private fun showExpandedReactionPicker(
        post: Post,
        reactionIds: List<String>,
        selectedReactionId: String?,
        collapsedRail: View,
        scrimContainer: TopicPostScrimPopupContainerLayout,
    ) {
        val root = rootFrameLayout ?: fragmentView as? ViewGroup ?: return
        if (reactionIds.isEmpty()) return
        dismissExpandedReactionPicker(animated = false)
        var window: ActionBarPopupWindow? = null
        val panel = TopicReactionPickerExpandedPanel(
            context = root.context,
            baseUrl = baseUrl,
            emojiCatalog = emojiCatalog,
            reactionIds = reactionIds,
            selectedReactionId = selectedReactionId,
            parentAlphaUpdater = scrimContainer::setPopupAlpha,
            onDismissed = { dismissedPanel ->
                if (expandedReactionPickerPanel === dismissedPanel) {
                    expandedReactionPickerPanel = null
                }
                val currentWindow = window
                if (currentWindow != null && expandedReactionPickerPopup === currentWindow) {
                    expandedReactionPickerPopup = null
                    currentWindow.dismiss(false)
                }
            },
            onReactionSelected = { selection ->
                selectReactionFromPicker(post, selection) {
                    postOptions?.dismiss()
                    postOptions = null
                }
                expandedReactionPickerPanel?.dismiss(animated = false, restoreParentAlpha = false)
            },
        )
        window = object : ActionBarPopupWindow(panel, LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT) {
            override fun dismiss() {
                dismiss(false)
            }

            override fun dismiss(animated: Boolean) {
                panel.removeImmediately(restoreParentAlpha = animated)
                super.dismiss(false)
                if (expandedReactionPickerPopup === this) {
                    expandedReactionPickerPopup = null
                }
                if (expandedReactionPickerPanel === panel) {
                    expandedReactionPickerPanel = null
                }
            }
        }.apply {
            setPauseNotifications(true)
            setDismissAnimationDuration(0)
            isOutsideTouchable = false
            isClippingEnabled = false
            isFocusable = true
            animationStyle = 0
            inputMethodMode = ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        expandedReactionPickerPanel = panel
        expandedReactionPickerPopup = window
        window.showAtLocation(root, Gravity.LEFT or Gravity.TOP, 0, 0)
        panel.post {
            if (expandedReactionPickerPanel === panel) {
                panel.startEnterAnimation(collapsedRail)
            }
        }
    }

    private fun dismissExpandedReactionPicker(animated: Boolean) {
        val panel = expandedReactionPickerPanel
        if (panel != null) {
            panel.dismiss(animated = animated)
            if (!animated) {
                expandedReactionPickerPopup?.dismiss(false)
                expandedReactionPickerPopup = null
                expandedReactionPickerPanel = null
            }
            return
        }
        expandedReactionPickerPopup?.dismiss(false)
        expandedReactionPickerPopup = null
    }

    private fun selectReactionFromPicker(
        post: Post,
        selection: ReactionPickerRailSelection,
        onSelectionCommitted: () -> Unit,
    ) {
        val overlayCreated = startReactionEffectOverlay(
            postId = post.id,
            reactionId = selection.reactionId,
            sourceView = selection.sourceView,
            sourceCenterX = selection.sourceCenterX,
            sourceCenterY = selection.sourceCenterY,
            beforeStart = {
                viewModel.onReactionSelected(post.id, selection.reactionId)
            },
        )
        if (!overlayCreated) {
            viewModel.onReactionSelected(post.id, selection.reactionId)
            onSelectionCommitted()
            return
        }
        AndroidUtilities.runOnUIThread(
            onSelectionCommitted,
            reactionEffectPopupDismissDelayMs(),
        )
    }

    private fun measureTopicPostOptionsPopup(
        scrimContainer: TopicPostScrimPopupContainerLayout,
        anchorView: View,
        pressX: Float,
        pressY: Float,
    ): TopicPostOptionsPopupPosition {
        val displayWidth = AndroidUtilities.displaySize.x.takeIf { it > 0 } ?: anchorView.resources.displayMetrics.widthPixels
        val displayHeight = AndroidUtilities.displaySize.y.takeIf { it > 0 } ?: anchorView.resources.displayMetrics.heightPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            (displayWidth - AndroidUtilities.dp(12f)).coerceAtLeast(AndroidUtilities.dp(200f)),
            View.MeasureSpec.AT_MOST,
        )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(displayHeight.coerceAtLeast(AndroidUtilities.dp(48f)), View.MeasureSpec.AT_MOST)
        fun measure() {
            scrimContainer.measure(widthSpec, heightSpec)
        }
        measure()
        var position = topicPostOptionsPopupPosition(
            anchorView = anchorView,
            popupWidth = scrimContainer.measuredWidth,
            popupHeight = scrimContainer.measuredHeight,
            pressX = pressX,
            pressY = pressY,
        )
        scrimContainer.setMaxHeight(topicPostScrimPopupMaxHeight(anchorView, position.y))
        measure()
        position = topicPostOptionsPopupPosition(
            anchorView = anchorView,
            popupWidth = scrimContainer.measuredWidth,
            popupHeight = scrimContainer.measuredHeight,
            pressX = pressX,
            pressY = pressY,
        )
        scrimContainer.setMaxHeight(topicPostScrimPopupMaxHeight(anchorView, position.y))
        return position
    }

    private fun startReactionEffectOverlay(
        postId: Long,
        reactionId: String,
        sourceView: View,
        sourceCenterX: Float,
        sourceCenterY: Float,
        beforeStart: () -> Unit,
    ): Boolean {
        val root = rootFrameLayout ?: fragmentView as? ViewGroup ?: return false
        val sourceBounds = reactionEffectSourceBoundsInRoot(root, sourceView, sourceCenterX, sourceCenterY)
        val iconUrl = PostReactionRendering.iconUrl(reactionId, null, emojiCatalog)?.let { resolvePostImageUrl(baseUrl, it) }
        reactionEffectOverlay?.dismiss()
        reactionEffectOverlay = null
        val overlay = TopicReactionEffectOverlay(
            context = root.context,
            reactionId = reactionId,
            iconUrl = iconUrl,
            sourceBounds = sourceBounds,
            targetBoundsProvider = { findReactionIconBoundsInRoot(postId, reactionId) },
            hapticViewProvider = { findVisibleTopicPostCell(postId) ?: sourceView },
            onRemoved = { removed ->
                if (reactionEffectOverlay === removed) {
                    reactionEffectOverlay = null
                }
            },
        )
        reactionEffectOverlay = overlay
        root.addView(
            overlay,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        overlay.bringToFront()
        beforeStart()
        overlay.startWhenTargetReady()
        return true
    }

    private fun reactionEffectSourceBoundsInRoot(root: View, sourceView: View, centerX: Float, centerY: Float): RectF {
        val rootLocation = IntArray(2)
        val sourceLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        sourceView.getLocationOnScreen(sourceLocation)
        val iconSize = AndroidUtilities.dp(reactionEffectOnlyMoveSizeDp().toFloat()).toFloat()
        val rootCenterX = sourceLocation[0] - rootLocation[0] + centerX
        val rootCenterY = sourceLocation[1] - rootLocation[1] + centerY
        return RectF(
            rootCenterX - iconSize / 2f,
            rootCenterY - iconSize / 2f,
            rootCenterX + iconSize / 2f,
            rootCenterY + iconSize / 2f,
        )
    }

    private fun findReactionIconBoundsInRoot(postId: Long, reactionId: String): RectF? {
        if (!::listView.isInitialized) {
            return null
        }
        val root = rootFrameLayout ?: fragmentView as? ViewGroup ?: return null
        val cell = findVisibleTopicPostCell(postId) ?: return null
        val windowBounds = RectF()
        if (!cell.fillReactionIconBoundsInWindow(reactionId, windowBounds)) {
            return null
        }
        val rootLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        windowBounds.offset(-rootLocation[0].toFloat(), -rootLocation[1].toFloat())
        return windowBounds
    }

    private fun findVisibleTopicPostCell(postId: Long): TopicPostCell? {
        for (i in 0 until listView.childCount) {
            val cell = listView.getChildAt(i) as? TopicPostCell ?: continue
            if (cell.boundPost?.id == postId) {
                return cell
            }
        }
        return null
    }

    private fun lockPostOptionsListScroll() {
        if (!::listView.isInitialized || postOptionsListScrollLocked) {
            return
        }
        listView.cancelSwipeReply(true)
        listView.stopScroll()
        listView.setScrollEnabled(false)
        postOptionsListScrollLocked = true
    }

    private fun unlockPostOptionsListScroll() {
        if (!::listView.isInitialized || !postOptionsListScrollLocked) {
            return
        }
        if (postOptions != null || reactionRailPopup != null) {
            return
        }
        listView.setScrollEnabled(true)
        postOptionsListScrollLocked = false
    }

    private fun postLink(post: Post): String {
        val root = baseUrl.trimEnd('/')
        val postNumber = post.postNumber
        return if (postNumber != null) {
            "$root/t/-/$topicId/$postNumber"
        } else {
            "$root/t/-/$topicId"
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
        fragment: BaseFragment,
        private val baseUrl: String,
        private val emojiCatalog: Map<String, String>,
        private val onReactionSelected: (String) -> Unit,
        private val onRetry: () -> Unit,
        private val onUserSelected: (String) -> Unit,
        private val onDismissed: () -> Unit,
    ) : FixedHeaderRecyclerBottomSheet(context, fragment) {
        private val sheetContext = context
        private val pager = ViewPager(context)
        private val pagerAdapter = ReactionPagesAdapter()
        private val pagesByFilterId = linkedMapOf<String, ReactionUsersPage>()
        private var filterIds: List<String> = emptyList()
        private val fixedHeaderView = ReactionFixedHeaderView(
            context,
            baseUrl,
            emojiCatalog,
            onReactionSelected = { reactionId ->
                selectFilter(reactionId, smoothScroll = true, notifySelection = true)
            },
            horizontalInset = backgroundPaddingLeft,
        )
        private val sizePolicy = StableUserListSheetSizePolicy(AndroidUtilities.dp(DiscourseUserCell.ITEM_HEIGHT_DP.toFloat()))
        private var contentHeightAnimator: ValueAnimator? = null
        private var lastSelectedReactionId: String? = null
        private var suppressPageSelectionCallback = false
        private var pagerScrollState = ViewPager.SCROLL_STATE_IDLE

        init {
            setOnDismissListener(DialogInterface.OnDismissListener { onDismissed() })
            pager.adapter = pagerAdapter
            pager.offscreenPageLimit = 1
            pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    val fromId = filterIds.getOrNull(position) ?: return
                    val fromPage = pagesByFilterId[fromId] ?: return
                    val toId = filterIds.getOrNull(position + 1)
                    val toPage = toId?.let(pagesByFilterId::get)
                    if (toId != null && toPage != null && positionOffset > 0f) {
                        contentHeightAnimator?.cancel()
                        fixedHeaderView.setPageProgress(fromId, toId, positionOffset)
                        setContentHeightPx(lerpInt(fromPage.contentHeight, toPage.contentHeight, positionOffset))
                        setRecyclerListTransition(fromPage.listView, toPage.listView, positionOffset)
                    } else {
                        setContentHeightPx(fromPage.contentHeight)
                    }
                }

                override fun onPageSelected(position: Int) {
                    val filterId = filterIds.getOrNull(position) ?: return
                    lastSelectedReactionId = filterId
                    if (!suppressPageSelectionCallback) {
                        onReactionSelected(filterId)
                    }
                }

                override fun onPageScrollStateChanged(state: Int) {
                    pagerScrollState = state
                    if (state == ViewPager.SCROLL_STATE_IDLE) {
                        val filterId = filterIds.getOrNull(pager.currentItem) ?: return
                        val page = pagesByFilterId[filterId] ?: return
                        setActiveRecyclerListView(page.listView)
                        setContentHeightPx(page.contentHeight)
                        fixedHeaderView.setSelectedFilter(filterId)
                    }
                }
            })
            setSheetContentView(pager)
            setFixedHeaderView(fixedHeaderView)
            setHeaderShadowDelegate { visible -> fixedHeaderView.setShadowVisible(visible) }
            setMinimumContentHeightPx(sizePolicy.minimumHeight())
            setContentHeightPx(sizePolicy.rowsHeight())
        }

        fun submit(reactions: List<PostReaction>, reactionUsersCount: Int, usersState: TopicReactionUsersUiState) {
            val tabReactions = buildList {
                addAll(reactions)
                usersState.groups.forEach { group ->
                    if (none { reaction -> reaction.id == group.id }) {
                        add(
                            PostReaction(
                                group.id,
                                group.count.takeIf { it > 0 } ?: group.users.size,
                                iconUrl = PostReactionRendering.iconUrl(group.id, null, emojiCatalog),
                            ),
                        )
                    }
                }
            }
            val showAll = tabReactions.size > 1
            val requestedSelected = usersState.selectedReactionId
                ?: usersState.groups.firstOrNull()?.id
                ?: tabReactions.firstOrNull()?.id
            val requestedGroup = usersState.groups.firstOrNull { group -> group.id == requestedSelected }
            val selected = when {
                requestedSelected == TOPIC_REACTION_ALL_FILTER_ID -> TOPIC_REACTION_ALL_FILTER_ID
                usersState.hasLoaded && requestedGroup == null && usersState.groups.isNotEmpty() && showAll -> TOPIC_REACTION_ALL_FILTER_ID
                tabReactions.any { reaction -> reaction.id == requestedSelected } -> requestedSelected
                showAll -> TOPIC_REACTION_ALL_FILTER_ID
                else -> tabReactions.firstOrNull()?.id
            }
            val allCount = reactionUsersCount.takeIf { it > 0 } ?: tabReactions
                .sumOf { reaction -> reaction.count.toLong() }
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            val selectedChanged = lastSelectedReactionId != selected
            val animateSelectionChange = lastSelectedReactionId != null && selectedChanged
            val nextFilterIds = buildList {
                if (showAll) add(TOPIC_REACTION_ALL_FILTER_ID)
                addAll(tabReactions.map(PostReaction::id))
            }
            reconcilePages(nextFilterIds)
            fixedHeaderView.bind(tabReactions, selected, showAll, allCount)
            filterIds.forEach { filterId ->
                val page = pagesByFilterId.getValue(filterId)
                val pageUsers = topicReactionUsersForFilter(usersState.groups, filterId)
                val pagePredictedCount = if (filterId == TOPIC_REACTION_ALL_FILTER_ID) {
                    allCount
                } else {
                    tabReactions.firstOrNull { reaction -> reaction.id == filterId }?.count
                }
                val partialMessage = if (
                    !usersState.isLoading &&
                    usersState.errorMessage == null &&
                    pageUsers.isNotEmpty() &&
                    pagePredictedCount != null &&
                    pagePredictedCount > pageUsers.size
                ) {
                    sheetContext.getString(R.string.topic_reaction_users_partial, pageUsers.size, pagePredictedCount)
                } else {
                    null
                }
                page.contentHeight = sizePolicy.rowsHeight(
                    userCount = if (usersState.isLoading) pagePredictedCount else pageUsers.size,
                )
                page.adapter.submit(
                    usersState,
                    pageUsers,
                    sizePolicy.skeletonRows(pagePredictedCount),
                    partialMessage,
                )
            }
            val selectedId = selected?.takeIf(filterIds::contains) ?: filterIds.firstOrNull() ?: return
            val selectedPage = pagesByFilterId.getValue(selectedId)
            val selectedIndex = filterIds.indexOf(selectedId)
            if (pager.currentItem != selectedIndex) {
                suppressPageSelectionCallback = true
                pager.setCurrentItem(selectedIndex, false)
                suppressPageSelectionCallback = false
            }
            lastSelectedReactionId = selectedId
            if (pagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
                setActiveRecyclerListView(selectedPage.listView)
                setContentHeight(selectedPage.contentHeight, animateSelectionChange)
            }
        }

        private fun reconcilePages(nextFilterIds: List<String>) {
            if (filterIds == nextFilterIds) return
            filterIds = nextFilterIds
            filterIds.forEach(::pageForFilter)
            pagerAdapter.notifyDataSetChanged()
        }

        private fun pageForFilter(filterId: String): ReactionUsersPage {
            return pagesByFilterId.getOrPut(filterId) {
                val listView = RecyclerListView(sheetContext).apply {
                    itemAnimator = null
                    layoutManager = LinearLayoutManager(sheetContext)
                }
                val pageAdapter = Adapter().apply {
                    configure(
                        baseUrl = baseUrl,
                        emptyMessage = sheetContext.getString(R.string.topic_reaction_users_empty),
                        retryMessage = sheetContext.getString(R.string.topic_reaction_users_retry),
                        onRetry = onRetry,
                    )
                    setTopSpacerHeight(0)
                }
                listView.adapter = pageAdapter
                listView.setOnItemClickListener(RecyclerListView.OnItemClickListener { _, position ->
                    pageAdapter.usernameAt(position)?.let(onUserSelected)
                })
                registerScrollableRecyclerListView(listView)
                ReactionUsersPage(filterId, listView, pageAdapter, sizePolicy.minimumHeight())
            }
        }

        private fun selectFilter(filterId: String, smoothScroll: Boolean, notifySelection: Boolean) {
            val position = filterIds.indexOf(filterId)
            if (position < 0 || pager.currentItem == position) return
            suppressPageSelectionCallback = !notifySelection
            pager.setCurrentItem(position, smoothScroll)
            suppressPageSelectionCallback = false
        }

        private fun setContentHeight(targetHeight: Int, animated: Boolean) {
            contentHeightAnimator?.cancel()
            if (!animated || contentHeightPx == targetHeight) {
                setContentHeightPx(targetHeight)
                return
            }
            contentHeightAnimator = ValueAnimator.ofInt(contentHeightPx, targetHeight).also { animator ->
                animator.duration = 150L
                animator.interpolator = CubicBezierInterpolator.DEFAULT
                animator.addUpdateListener { valueAnimator ->
                    setContentHeightPx(valueAnimator.animatedValue as Int)
                }
                animator.start()
            }
        }

        private data class ReactionUsersPage(
            val filterId: String,
            val listView: RecyclerListView,
            val adapter: Adapter,
            var contentHeight: Int,
        )

        private inner class ReactionPagesAdapter : PagerAdapter() {
            override fun getCount(): Int = filterIds.size

            override fun isViewFromObject(view: View, `object`: Any): Boolean {
                return view === (`object` as ReactionUsersPage).listView
            }

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val page = pageForFilter(filterIds[position])
                (page.listView.parent as? ViewGroup)?.removeView(page.listView)
                container.addView(page.listView)
                return page
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                container.removeView((`object` as ReactionUsersPage).listView)
            }

            override fun getItemPosition(`object`: Any): Int {
                val position = filterIds.indexOf((`object` as ReactionUsersPage).filterId)
                return if (position >= 0) position else POSITION_NONE
            }
        }

        private sealed interface Row {
            data class TopSpacer(val height: Int) : Row
            data class Loading(val rowCount: Int) : Row
            data class Empty(val message: String, val canRetry: Boolean) : Row
            data class User(val user: PostReactionUser) : Row
            data class Info(val message: String) : Row
        }

        private class Adapter : RecyclerListView.SelectionAdapter() {
            private var rows: List<Row> = emptyList()
            private var baseUrl: String = ""
            private var emptyMessage: String = ""
            private var retryMessage: String = ""
            private var onRetry: () -> Unit = {}
            private var topSpacerHeight: Int = AndroidUtilities.dp(140f)

            fun configure(
                baseUrl: String,
                emptyMessage: String,
                retryMessage: String,
                onRetry: () -> Unit,
            ) {
                this.baseUrl = baseUrl
                this.emptyMessage = emptyMessage
                this.retryMessage = retryMessage
                this.onRetry = onRetry
            }

            fun usernameAt(position: Int): String? {
                return (rows.getOrNull(position) as? Row.User)?.user?.username?.takeIf { it.isNotBlank() }
            }

            fun setTopSpacerHeight(height: Int) {
                if (topSpacerHeight == height) return
                topSpacerHeight = height
                rows = rows.map { row -> if (row is Row.TopSpacer) Row.TopSpacer(height) else row }
                notifyDataSetChanged()
            }

            fun submit(
                usersState: TopicReactionUsersUiState,
                selectedUsers: List<PostReactionUser>,
                loadingRowCount: Int,
                partialMessage: String?,
            ) {
                rows = buildList {
                    if (topSpacerHeight > 0) {
                        add(Row.TopSpacer(topSpacerHeight))
                    }
                    if (usersState.isLoading) {
                        add(Row.Loading(loadingRowCount))
                    } else if (selectedUsers.isEmpty()) {
                        add(
                            Row.Empty(
                                message = if (usersState.errorMessage != null) retryMessage else emptyMessage,
                                canRetry = usersState.errorMessage != null,
                            ),
                        )
                    } else {
                        selectedUsers.forEach { user ->
                            add(Row.User(user))
                        }
                        partialMessage?.let { message -> add(Row.Info(message)) }
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
                    is Row.Loading -> SHEET_ROW_LOADING
                    is Row.Empty -> SHEET_ROW_EMPTY
                    is Row.User -> SHEET_ROW_USER
                    is Row.Info -> SHEET_ROW_EMPTY
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = when (viewType) {
                    SHEET_ROW_REACTION_USERS_TOP_SPACER -> TopSpacerView(parent.context)
                    SHEET_ROW_LOADING -> ReactionUsersSkeletonView(parent.context)
                    SHEET_ROW_USER -> DiscourseUserCell(parent.context)
                    else -> TopicFooterCell(parent.context)
                }
                return RecyclerListView.Holder(view)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (val row = rows[position]) {
                    is Row.TopSpacer -> (holder.itemView as TopSpacerView).setHeight(row.height)
                    is Row.Loading -> (holder.itemView as ReactionUsersSkeletonView).setRowCount(row.rowCount)
                    is Row.Empty -> (holder.itemView as TopicFooterCell).bind(
                        row.message,
                        if (row.canRetry) Runnable(onRetry) else null,
                    )
                    is Row.User -> {
                        val avatarUrl = resolveAvatarUrl(baseUrl, row.user.avatarTemplate, row.user.animatedAvatarUrl, 96)
                        (holder.itemView as DiscourseUserCell).bind(
                            row.user.displayName,
                            row.user.username,
                            avatarUrl,
                            avatarImageFilter(row.user.animatedAvatarUrl, 50),
                            isAnimatedDiscourseImageUrl(avatarUrl),
                            row.user,
                        )
                    }
                    is Row.Info -> (holder.itemView as TopicFooterCell).bind(row.message, null)
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

        private class ReactionUsersSkeletonView(context: Context) : FlickerLoadingView(context) {
            private var rowCount = 1

            init {
                setIsSingleCell(true)
                setIgnoreHeightCheck(true)
                setViewType(FlickerLoadingView.REACTED_TYPE)
                setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, -1)
            }

            fun setRowCount(rowCount: Int) {
                val normalized = rowCount.coerceAtLeast(1)
                if (this.rowCount == normalized) return
                this.rowCount = normalized
                setItemsCount(normalized)
                requestLayout()
                invalidate()
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
                text = context.getString(R.string.topic_reaction_users_title)
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

            fun bind(reactions: List<PostReaction>, selectedId: String?, showAll: Boolean, allCount: Int) {
                tabsView.bind(reactions, selectedId, showAll, allCount)
            }

            fun setPageProgress(fromId: String, toId: String, progress: Float) {
                tabsView.setPageProgress(fromId, toId, progress)
            }

            fun setSelectedFilter(filterId: String) {
                tabsView.setSelectedFilter(filterId)
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
        ) : HorizontalScrollView(context) {
            private val tabsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(AndroidUtilities.dp(6f), 0, 0, 0)
            }
            private val chips = mutableListOf<ReactionTabChipView>()
            private var selectedId: String? = null
            private var selectionAnimator: ValueAnimator? = null

            init {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                clipToPadding = false
                addView(tabsContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            }

            fun bind(reactions: List<PostReaction>, selectedId: String?, showAll: Boolean, allCount: Int) {
                val previousSelectedId = this.selectedId
                this.selectedId = selectedId
                selectionAnimator?.cancel()
                tabsContainer.removeAllViews()
                chips.clear()
                if (showAll) {
                    addChip(TOPIC_REACTION_ALL_FILTER_ID) {
                        bindAll(
                            count = allCount,
                            selected = selectedId == TOPIC_REACTION_ALL_FILTER_ID,
                            contentDescription = context.getString(R.string.topic_reaction_users_all),
                        )
                    }
                }
                reactions.forEach { reaction ->
                    addChip(reaction.id) {
                        bind(
                            reaction,
                            PostReactionRendering.iconUrl(reaction.id, reaction.iconUrl, emojiCatalog),
                            baseUrl,
                            selected = reaction.id == selectedId,
                        )
                    }
                }
                requestLayout()
                post {
                    val selectedChip = chips.firstOrNull { chip -> chip.reactionId == selectedId } ?: return@post
                    val targetScroll = (selectedChip.left - (width - selectedChip.width) / 2).coerceAtLeast(0)
                    if (previousSelectedId != null && previousSelectedId != selectedId) {
                        val startScroll = scrollX
                        val previousChip = chips.firstOrNull { chip -> chip.reactionId == previousSelectedId }
                        previousChip?.setSelectionProgress(1f)
                        selectedChip.setSelectionProgress(0f)
                        ValueAnimator.ofFloat(0f, 1f).also { animator ->
                            selectionAnimator = animator
                            animator.duration = 150L
                            animator.interpolator = CubicBezierInterpolator.DEFAULT
                            animator.addUpdateListener { valueAnimator ->
                                val progress = valueAnimator.animatedValue as Float
                                scrollTo((startScroll + (targetScroll - startScroll) * progress).toInt(), 0)
                                previousChip?.setSelectionProgress(1f - progress)
                                selectedChip.setSelectionProgress(progress)
                            }
                            animator.start()
                        }
                    } else {
                        scrollTo(targetScroll, 0)
                    }
                }
            }

            fun setPageProgress(fromId: String, toId: String, progress: Float) {
                val fromChip = chips.firstOrNull { chip -> chip.reactionId == fromId } ?: return
                val toChip = chips.firstOrNull { chip -> chip.reactionId == toId } ?: return
                val normalizedProgress = progress.coerceIn(0f, 1f)
                selectionAnimator?.cancel()
                chips.forEach { chip ->
                    chip.setSelectionProgress(
                        when (chip) {
                            fromChip -> 1f - normalizedProgress
                            toChip -> normalizedProgress
                            else -> 0f
                        },
                    )
                }
                val fromScroll = fromChip.left - (width - fromChip.width) / 2f
                val toScroll = toChip.left - (width - toChip.width) / 2f
                scrollTo((fromScroll + (toScroll - fromScroll) * normalizedProgress).toInt().coerceAtLeast(0), 0)
            }

            fun setSelectedFilter(filterId: String) {
                selectedId = filterId
                selectionAnimator?.cancel()
                chips.forEach { chip ->
                    chip.setSelectionProgress(if (chip.reactionId == filterId) 1f else 0f)
                }
                post {
                    val selectedChip = chips.firstOrNull { chip -> chip.reactionId == filterId } ?: return@post
                    scrollTo((selectedChip.left - (width - selectedChip.width) / 2).coerceAtLeast(0), 0)
                }
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44f), MeasureSpec.EXACTLY))
            }

            private fun addChip(id: String, bind: ReactionTabChipView.() -> Unit) {
                val chip = ReactionTabChipView(context).apply {
                    reactionId = id
                    bind()
                    setOnClickListener {
                        if (this@ReactionTabsView.selectedId != id) {
                            onReactionSelected(id)
                        }
                    }
                }
                chips += chip
                tabsContainer.addView(
                    chip,
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(32f)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginEnd = AndroidUtilities.dp(6f)
                    },
                )
            }
        }

        private class ReactionTabChipView(context: Context) : View(context) {
            private val iconSize = AndroidUtilities.dp(24f)
            private val iconReceiver = ImageReceiver(this).apply {
                setAllowLoadingOnAttachedOnly(true)
            }
            private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = AndroidUtilities.dp(16f).toFloat()
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = AndroidUtilities.dp(14f).toFloat()
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            private val allReactionsDrawable = ContextCompat.getDrawable(context, TelegramR.drawable.msg_reactions_filled)?.mutate()
            var reactionId: String = ""
            private var id = ""
            private var count = 0
            private var selected = false
            private var selectionProgress = 0f
            private var hasIcon = false
            private var isAll = false

            fun bind(reaction: PostReaction, rawIconUrl: String?, baseUrl: String, selected: Boolean) {
                this.id = reaction.id
                this.count = reaction.count
                this.selected = selected
                selectionProgress = if (selected) 1f else 0f
                isAll = false
                isSelected = selected
                contentDescription = "${PostReactionRendering.fallbackLabel(reaction.id)}, ${reaction.count}"
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

            fun bindAll(count: Int, selected: Boolean, contentDescription: String) {
                id = TOPIC_REACTION_ALL_FILTER_ID
                this.count = count
                this.selected = selected
                selectionProgress = if (selected) 1f else 0f
                isAll = true
                hasIcon = true
                isSelected = selected
                this.contentDescription = "$contentDescription, $count"
                iconReceiver.setImage(null, null, null, null, id, 0)
                requestLayout()
                invalidate()
            }

            fun setSelectionProgress(progress: Float) {
                selectionProgress = progress.coerceIn(0f, 1f)
                selected = selectionProgress > 0.5f
                isSelected = selected
                invalidate()
            }

            override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(info)
                info.className = "android.widget.Button"
                info.isClickable = true
                info.isSelected = selected
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
                val countText = LocaleController.formatShortNumber(count, null)
                val textWidth = textPaint.measureText(countText).toInt()
                val fallbackWidth = if (hasIcon) 0 else fallbackPaint.measureText(PostReactionRendering.fallbackLabel(id)).toInt().coerceAtLeast(iconSize)
                val desired = AndroidUtilities.dp(8f) + maxOf(iconSize, fallbackWidth) + AndroidUtilities.dp(8f) + textWidth + AndroidUtilities.dp(8f)
                setMeasuredDimension(desired.coerceAtLeast(AndroidUtilities.dp(52f)), MeasureSpec.getSize(heightMeasureSpec))
            }

            override fun onDraw(canvas: Canvas) {
                val selectedBackgroundColor = Theme.getColor(Theme.key_chat_inReactionButtonBackground)
                val normalBackgroundColor = ColorUtils.setAlphaComponent(selectedBackgroundColor, 0x10)
                val selectedTextColor = Theme.getColor(Theme.key_chat_inReactionButtonTextSelected)
                val normalTextColor = Theme.getColor(Theme.key_chat_inReactionButtonText)
                backgroundPaint.color = ColorUtils.blendARGB(normalBackgroundColor, selectedBackgroundColor, selectionProgress)
                val contentColor = ColorUtils.blendARGB(normalTextColor, selectedTextColor, selectionProgress)
                val radius = AndroidUtilities.dp(32f).toFloat()
                canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, backgroundPaint)

                val iconLeft = AndroidUtilities.dp(8f)
                val iconTop = (height - iconSize) / 2
                if (isAll) {
                    allReactionsDrawable?.let { drawable ->
                        drawable.colorFilter = PorterDuffColorFilter(contentColor, PorterDuff.Mode.MULTIPLY)
                        drawable.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                        drawable.draw(canvas)
                    }
                } else if (hasIcon) {
                    iconReceiver.setImageCoords(iconLeft.toFloat(), iconTop.toFloat(), iconSize.toFloat(), iconSize.toFloat())
                    iconReceiver.draw(canvas)
                } else {
                    fallbackPaint.color = contentColor
                    val fallback = PostReactionRendering.fallbackLabel(id)
                    canvas.drawText(
                        fallback,
                        iconLeft + iconSize / 2f,
                        height / 2f - (fallbackPaint.ascent() + fallbackPaint.descent()) / 2f,
                        fallbackPaint,
                    )
                }

                textPaint.color = contentColor
                val text = LocaleController.formatShortNumber(count, null)
                canvas.drawText(
                    text,
                    (iconLeft + iconSize + AndroidUtilities.dp(8f)).toFloat(),
                    height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
                    textPaint,
                )
            }
        }

    }

    private class TopicRepliesSheet(
        context: Context,
        fragment: BaseFragment,
        private val baseUrl: String,
        private val onReplySelected: (TopicReplySheetItem) -> Unit,
        private val onLoadMore: () -> Unit,
        private val onRetry: () -> Unit,
        private val onDismissed: () -> Unit,
    ) : BottomSheetWithRecyclerListView(
        context,
        fragment,
        false,
        false,
        false,
        BottomSheetWithRecyclerListView.ActionBarType.SLIDING,
        fragment.resourceProvider,
    ) {
        private lateinit var adapter: Adapter
        private var canLoadMore = false

        init {
            adapter.configure(baseUrl, onReplySelected)
            setShowHandle(true)
            setOnDismissListener(DialogInterface.OnDismissListener { onDismissed() })
            recyclerListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val outerItemCount = recyclerView.adapter?.itemCount ?: return
                    if (topicRepliesSheetShouldLoadMore(
                            dy = dy,
                            canLoadMore = canLoadMore,
                            lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition(),
                            outerItemCount = outerItemCount,
                        )
                    ) {
                        onLoadMore()
                    }
                }
            })
        }

        fun submit(sourcePost: Post, totalCount: Int, state: TopicPostRepliesUiState) {
            canLoadMore = state.canLoadMore
            adapter.submit(
                sourcePost = sourcePost,
                totalCount = totalCount,
                replies = state.posts.map(::topicReplySheetItem),
                state = state,
                onRetry = onRetry,
            )
        }

        override fun getTitle(): CharSequence = "Replies"

        override fun createAdapter(recyclerListView: RecyclerListView): RecyclerListView.SelectionAdapter {
            return Adapter().also { adapter = it }
        }

        private sealed interface Row {
            data class Header(val sourcePost: Post, val count: Int) : Row
            data class Reply(val item: TopicReplySheetItem, val divider: Boolean) : Row
            data class Loading(val rowCount: Int) : Row
            data object LoadingMore : Row
            data class State(val message: String, val onRetry: (() -> Unit)?) : Row
        }

        private class Adapter : RecyclerListView.SelectionAdapter() {
            private var rows: List<Row> = emptyList()
            private var baseUrl: String = ""
            private var onReplySelected: (TopicReplySheetItem) -> Unit = {}

            fun configure(baseUrl: String, onReplySelected: (TopicReplySheetItem) -> Unit) {
                this.baseUrl = baseUrl
                this.onReplySelected = onReplySelected
            }

            fun submit(
                sourcePost: Post,
                totalCount: Int,
                replies: List<TopicReplySheetItem>,
                state: TopicPostRepliesUiState,
                onRetry: () -> Unit,
            ) {
                rows = buildList {
                    add(Row.Header(sourcePost, totalCount))
                    if (state.isLoading) {
                        add(Row.Loading(topicReplySkeletonRows(totalCount)))
                        return@buildList
                    }
                    if (replies.isEmpty()) {
                        add(Row.State(state.errorMessage ?: "No replies", state.errorMessage?.let { onRetry }))
                        return@buildList
                    }
                    replies.forEachIndexed { index, reply ->
                        add(Row.Reply(reply, divider = index != replies.lastIndex))
                    }
                    when {
                        state.isLoadingMore -> add(Row.LoadingMore)
                        state.errorMessage != null -> add(Row.State(state.errorMessage, onRetry))
                    }
                }
                notifyDataSetChanged()
            }

            override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
                return holder.itemView is ReplySheetRowView
            }

            override fun getItemCount(): Int = rows.size

            override fun getItemViewType(position: Int): Int {
                return when (rows[position]) {
                    is Row.Header -> SHEET_ROW_REPLY_HEADER
                    is Row.Reply -> SHEET_ROW_REPLY
                    is Row.Loading -> SHEET_ROW_LOADING
                    Row.LoadingMore -> SHEET_ROW_LOADING_MORE
                    is Row.State -> SHEET_ROW_EMPTY
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = when (viewType) {
                    SHEET_ROW_REPLY_HEADER -> ReplySheetHeaderView(parent.context)
                    SHEET_ROW_REPLY -> ReplySheetRowView(parent.context, baseUrl)
                    SHEET_ROW_LOADING -> ReplyRowsSkeletonView(parent.context)
                    SHEET_ROW_LOADING_MORE -> LoadingCell(parent.context)
                    else -> TopicFooterCell(parent.context)
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
                    is Row.Loading -> (holder.itemView as ReplyRowsSkeletonView).setRowCount(row.rowCount)
                    Row.LoadingMore -> Unit
                    is Row.State -> (holder.itemView as TopicFooterCell).bind(
                        row.message,
                        row.onRetry?.let { retry -> Runnable(retry) },
                    )
                }
            }
        }

        private class ReplyRowsSkeletonView(context: Context) : FlickerLoadingView(context) {
            private val rect = RectF()
            private val rowHeight = AndroidUtilities.dp(REPLY_SKELETON_ROW_HEIGHT_DP)
            private var rowCount = 1

            init {
                setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, -1)
            }

            fun setRowCount(rowCount: Int) {
                val normalized = rowCount.coerceAtLeast(1)
                if (this.rowCount == normalized) return
                this.rowCount = normalized
                requestLayout()
                invalidate()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), rowHeight * rowCount)
            }

            override fun onDraw(canvas: Canvas) {
                updateColors()
                updateGradient()
                val paint = getPaint()
                repeat(rowCount) { index ->
                    val top = index * rowHeight
                    canvas.drawCircle(
                        AndroidUtilities.dp(36f).toFloat(),
                        (top + AndroidUtilities.dp(26f)).toFloat(),
                        AndroidUtilities.dp(18f).toFloat(),
                        paint,
                    )
                    drawRound(
                        canvas,
                        paint,
                        AndroidUtilities.dp(66f),
                        top + AndroidUtilities.dp(13f),
                        width - AndroidUtilities.dp(72f),
                        top + AndroidUtilities.dp(21f),
                    )
                    drawRound(
                        canvas,
                        paint,
                        AndroidUtilities.dp(66f),
                        top + AndroidUtilities.dp(31f),
                        AndroidUtilities.dp(160f),
                        top + AndroidUtilities.dp(37f),
                    )
                    drawRound(
                        canvas,
                        paint,
                        AndroidUtilities.dp(66f),
                        top + AndroidUtilities.dp(54f),
                        width - AndroidUtilities.dp(24f),
                        top + AndroidUtilities.dp(62f),
                    )
                    drawRound(
                        canvas,
                        paint,
                        AndroidUtilities.dp(66f),
                        top + AndroidUtilities.dp(70f),
                        width - AndroidUtilities.dp(76f),
                        top + AndroidUtilities.dp(78f),
                    )
                }
                invalidate()
            }

            private fun drawRound(canvas: Canvas, paint: Paint, left: Int, top: Int, right: Int, bottom: Int) {
                rect.set(left.toFloat(), top.toFloat(), right.coerceAtLeast(left).toFloat(), bottom.toFloat())
                val radius = AndroidUtilities.dp(4f).toFloat()
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        private class ReplySheetHeaderView(context: Context) : AppCompatTextView(context), Theme.Colorable {
            init {
                textSize = 20f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))
                updateColors()
            }

            fun bind(sourcePost: Post, count: Int) {
                val floor = sourcePost.postNumber?.let { "#${it}" }.orEmpty()
                text = if (floor.isBlank()) "$count replies" else "$count replies to $floor"
            }

            override fun updateColors() {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                invalidate()
            }
        }

        private class ReplySheetRowView(context: Context, private val baseUrl: String) : FrameLayout(context), Theme.Colorable {
            private val avatarSize = AndroidUtilities.dp(36f)
            private val avatarDrawable = AvatarDrawable()
            private val avatarImage = ImageReceiver(this).apply {
                setRoundRadius(avatarSize / 2)
                setAllowLoadingOnAttachedOnly(true)
                setDelegate { _, _, _, _ -> invalidate() }
                setVisibleInvalidate { invalidate() }
            }
            private val nameView = TextView(context).apply {
                textSize = 15f
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val metaView = TextView(context).apply {
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val previewView = TextView(context).apply {
                textSize = 13f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            private val divider = View(context)

            init {
                setWillNotDraw(false)
                setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(8f), AndroidUtilities.dp(18f), AndroidUtilities.dp(8f))
                addView(nameView)
                addView(metaView)
                addView(previewView)
                addView(divider)
                updateColors()
            }

            fun bind(reply: TopicReplySheetItem, showDivider: Boolean) {
                val name = topicPostDisplayName(reply.authorDisplayName, reply.authorUsername)
                nameView.text = Emoji.replaceEmoji(name, nameView.paint.fontMetricsInt, false)
                val meta = buildString {
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
                metaView.text = Emoji.replaceEmoji(meta, metaView.paint.fontMetricsInt, false)
                val preview = reply.previewText.ifBlank { "View original reply" }
                previewView.text = Emoji.replaceEmoji(preview, previewView.paint.fontMetricsInt, false)
                divider.visibility = if (showDivider) View.VISIBLE else View.GONE
                avatarDrawable.setInfo(reply.authorUsername.hashCode().toLong(), name)
                val avatarUrl = resolveAvatarUrl(baseUrl, reply.authorAvatarTemplate, reply.authorAnimatedAvatarUrl, 96)
                avatarImage.setAllowDecodeSingleFrame(!isAnimatedDiscourseImageUrl(avatarUrl))
                if (avatarUrl.isBlank()) {
                    avatarImage.setImage(null, null, avatarDrawable, null, reply, 0)
                } else {
                    avatarImage.setImage(ImageLocation.getForPath(avatarUrl), avatarImageFilter(reply.authorAnimatedAvatarUrl, 50), avatarDrawable, null, reply, 0)
                }
            }

            override fun updateColors() {
                setBackground(Theme.getSelectorDrawable(false))
                nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                metaView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                previewView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                divider.setBackgroundColor(Theme.getColor(Theme.key_divider))
                invalidate()
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

    private class TopicReplyComposerEmojiPanel(
        context: Context,
        private val heightPx: Int,
        baseUrl: String,
        categories: List<DiscourseEmojiCategory>,
        onEmojiSelected: (String) -> Unit,
    ) : FrameLayout(context) {
        private var panelAnimator: ValueAnimator? = null
        private var selectedCategoryIndex = 0
        private var emojiSmoothScrolling = false
        private var ignoreEmojiScroll = false
        private var contentTopInset = 0
        private val panelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Theme.getColor(Theme.key_chat_emojiPanelBackground)
        }
        private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Theme.getColor(Theme.key_chat_emojiPanelShadowLine)
        }
        private val normalizedCategories = categories.filter { it.emojis.isNotEmpty() }
        private val tabs = EmojiCategoryTabsView(context, baseUrl) { index ->
            selectCategory(index, smoothScroll = true)
        }
        private val adapter = EmojiGridAdapter(baseUrl, onEmojiSelected)
        private val layoutManager = object : GridLayoutManager(context, topicReplyComposerEmojiPanelColumns()) {
            override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
                try {
                    val scroller = object : LinearSmoothScrollerCustom(
                        recyclerView.context,
                        LinearSmoothScrollerCustom.POSITION_TOP,
                    ) {
                        override fun onStart() {
                            emojiSmoothScrolling = true
                        }

                        override fun onEnd() {
                            emojiSmoothScrolling = false
                            ignoreEmojiScroll = false
                            adapter.invalidateAttachedButtons()
                            updateSelectedCategoryFromScroll()
                        }
                    }
                    scroller.targetPosition = position
                    startSmoothScroll(scroller)
                } catch (_: Exception) {
                    emojiSmoothScrolling = false
                    ignoreEmojiScroll = false
                    scrollToPositionWithOffset(position, topicReplyComposerEmojiSectionScrollOffsetPx(contentTopInset))
                }
            }
        }.apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter.isFullSpan(position)) spanCount else 1
                }
            }
        }
        private val grid = RecyclerListView(context).apply {
            overScrollMode = OVER_SCROLL_NEVER
            itemAnimator = null
            setInstantClick(true)
            clipToPadding = false
            setPadding(
                AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_GRID_HORIZONTAL_PADDING_DP.toFloat()),
                topicReplyComposerEmojiGridTopPaddingPx(contentTopInset),
                AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_GRID_HORIZONTAL_PADDING_DP.toFloat()),
                AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_PANEL_BOTTOM_CONTROLS_DP.toFloat()),
            )
            layoutManager = this@TopicReplyComposerEmojiPanel.layoutManager
            adapter = this@TopicReplyComposerEmojiPanel.adapter
            setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground))
            setOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateSelectedCategoryFromScroll()
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        emojiSmoothScrolling = false
                        ignoreEmojiScroll = false
                    }
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        emojiSmoothScrolling = false
                        ignoreEmojiScroll = false
                        this@TopicReplyComposerEmojiPanel.adapter.invalidateAttachedButtons()
                        updateSelectedCategoryFromScroll()
                    }
                }
            })
        }
        private val scrollHelper = RecyclerAnimationScrollHelper(grid, layoutManager).apply {
            setAnimationCallback(object : RecyclerAnimationScrollHelper.AnimationCallback() {
                override fun onStartAnimation() {
                    emojiSmoothScrolling = true
                }

                override fun onPreAnimation() {
                    emojiSmoothScrolling = true
                    adapter.invalidateAttachedButtons()
                }

                override fun onEndAnimation() {
                    emojiSmoothScrolling = false
                    ignoreEmojiScroll = false
                    adapter.invalidateAttachedButtons()
                    updateSelectedCategoryFromScroll()
                }

                override fun ignoreView(view: View?, ignore: Boolean) {
                    (view as? EmojiGridButton)?.ignoring = ignore
                }
            })
        }

        init {
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            tabs.bind(normalizedCategories, selectedIndex = 0)
            adapter.submit(normalizedCategories)
            addView(
                grid,
                LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT.toFloat(),
                    LayoutHelper.MATCH_PARENT.toFloat(),
                    Gravity.TOP,
                    0f,
                    0f,
                    0f,
                    0f,
                ),
            )
            addView(
                tabs,
                LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT.toFloat(),
                    TOPIC_REPLY_COMPOSER_EMOJI_PANEL_TABS_HEIGHT_DP.toFloat(),
                    Gravity.TOP,
                    0f,
                    0f,
                    0f,
                    0f,
                ),
            )
        }

        fun show() {
            panelAnimator?.cancel()
            translationY = heightPx.toFloat()
            animatePanel(
                targetTranslationY = 0f,
                onStart = {
                    adapter.invalidateAttachedButtons()
                    adapter.startEnterAnimation()
                },
                onEnd = null,
            )
        }

        fun dismiss(animated: Boolean, onEnd: () -> Unit) {
            panelAnimator?.cancel()
            adapter.cancelEnterAnimation()
            if (!animated || !isAttachedToWindow) {
                translationY = heightPx.toFloat()
                onEnd()
                return
            }
            animatePanel(targetTranslationY = heightPx.toFloat(), onEnd = onEnd)
        }

        override fun onDetachedFromWindow() {
            panelAnimator?.cancel()
            panelAnimator = null
            adapter.cancelEnterAnimation()
            super.onDetachedFromWindow()
        }

        fun isPanelAnimationInProgress(): Boolean {
            return panelAnimator != null
        }

        fun setContentTopInset(inset: Int) {
            val nextInset = inset.coerceIn(0, heightPx)
            if (contentTopInset == nextInset) {
                return
            }
            contentTopInset = nextInset
            updateChildTopMargins()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, contentTopInset.toFloat(), width.toFloat(), height.toFloat(), panelBackgroundPaint)
            val tabsBottom = topicReplyComposerEmojiTabsBottomY(contentTopInset)
            canvas.drawRect(0f, tabsBottom.toFloat(), width.toFloat(), tabsBottom + 1f, dividerPaint)
        }

        override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
            if (child === grid) {
                val save = canvas.save()
                val top = topicReplyComposerEmojiGridClipTopPx(contentTopInset).toFloat()
                canvas.clipRect(0f, top, width.toFloat(), height.toFloat())
                val result = super.drawChild(canvas, child, drawingTime)
                canvas.restoreToCount(save)
                return result
            }
            return super.drawChild(canvas, child, drawingTime)
        }

        override fun dispatchDraw(canvas: Canvas) {
            val save = canvas.save()
            canvas.clipRect(0f, contentTopInset.toFloat(), width.toFloat(), height.toFloat())
            super.dispatchDraw(canvas)
            canvas.restoreToCount(save)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            return ev.y >= contentTopInset && super.dispatchTouchEvent(ev)
        }

        private fun animatePanel(
            targetTranslationY: Float,
            onStart: (() -> Unit)? = null,
            onEnd: (() -> Unit)?,
        ) {
            val fromTranslationY = translationY
            var cancelled = false
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = AdjustPanLayoutHelper.keyboardDuration
                startDelay = topicReplyComposerEmojiPanelAnimationStartDelayMs()
                interpolator = AdjustPanLayoutHelper.keyboardInterpolator
                addUpdateListener { animation ->
                    val t = animation.animatedValue as Float
                    translationY = lerpFloat(fromTranslationY, targetTranslationY, t)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        if (panelAnimator === animation) {
                            onStart?.invoke()
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (panelAnimator === animation) {
                            cancelled = true
                            panelAnimator = null
                        }
                    }

                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (!cancelled && panelAnimator === animation) {
                            panelAnimator = null
                            translationY = targetTranslationY
                            onEnd?.invoke()
                        }
                    }
                })
            }
            panelAnimator = animator
            animator.start()
        }

        private fun selectCategory(index: Int, smoothScroll: Boolean) {
            if (index !in normalizedCategories.indices) {
                return
            }
            if (selectedCategoryIndex != index) {
                selectedCategoryIndex = index
                tabs.setSelectedIndex(index, scrollToVisible = true)
            }
            if (smoothScroll) {
                val position = adapter.positionForCategory(index)
                grid.stopScroll()
                updateSelectedCategory(index, scrollToVisible = true)
                scrollEmojisToPosition(position, topicReplyComposerEmojiSectionScrollOffsetPx(contentTopInset))
            }
        }

        private fun scrollEmojisToPosition(position: Int, offset: Int) {
            val firstPosition = layoutManager.findFirstVisibleItemPosition()
            val targetView = layoutManager.findViewByPosition(position)
            if (topicReplyComposerEmojiShouldUseHelperScroll(
                    targetAttached = targetView != null,
                    targetPosition = position,
                    firstVisiblePosition = firstPosition,
                    spanCount = layoutManager.spanCount,
                    animationsEnabled = SharedConfig.animationsEnabled(),
                )
            ) {
                emojiSmoothScrolling = true
                ignoreEmojiScroll = true
                scrollHelper.setScrollDirection(
                    if (firstPosition != RecyclerView.NO_POSITION && firstPosition < position) {
                        RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN
                    } else {
                        RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP
                    },
                )
                scrollHelper.scrollToPosition(position, offset, false, true)
            } else {
                ignoreEmojiScroll = true
                emojiSmoothScrolling = true
                val scroller = object : LinearSmoothScrollerCustom(
                    grid.context,
                    LinearSmoothScrollerCustom.POSITION_TOP,
                ) {
                    override fun onStart() {
                        emojiSmoothScrolling = true
                    }

                    override fun onEnd() {
                        emojiSmoothScrolling = false
                        ignoreEmojiScroll = false
                        adapter.invalidateAttachedButtons()
                        updateSelectedCategoryFromScroll()
                    }
                }
                scroller.targetPosition = position
                scroller.setOffset(offset)
                layoutManager.startSmoothScroll(scroller)
            }
        }

        private fun updateChildTopMargins() {
            (tabs.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.topMargin = contentTopInset
                tabs.layoutParams = params
            }
            grid.setPadding(
                grid.paddingLeft,
                topicReplyComposerEmojiGridTopPaddingPx(contentTopInset),
                grid.paddingRight,
                grid.paddingBottom,
            )
            grid.invalidate()
        }

        private fun updateSelectedCategoryFromScroll() {
            if (emojiSmoothScrolling || ignoreEmojiScroll) {
                return
            }
            val firstVisible = firstVisibleSectionPosition()
            val nextIndex = topicReplyComposerEmojiCategoryForPosition(firstVisible, adapter.categoryStartPositions())
            updateSelectedCategory(nextIndex, scrollToVisible = false)
        }

        private fun updateSelectedCategory(index: Int, scrollToVisible: Boolean) {
            val safeIndex = index.coerceIn(0, (normalizedCategories.size - 1).coerceAtLeast(0))
            if (safeIndex == selectedCategoryIndex) {
                return
            }
            selectedCategoryIndex = safeIndex
            tabs.setSelectedIndex(safeIndex, scrollToVisible = scrollToVisible)
        }

        private fun firstVisibleSectionPosition(): Int {
            val completelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
            if (completelyVisible != RecyclerView.NO_POSITION) {
                return completelyVisible
            }
            return layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        }
    }

    private class EmojiCategoryTabsView(
        context: Context,
        private val baseUrl: String,
        private val onCategorySelected: (Int) -> Unit,
    ) : HorizontalScrollView(context) {
        private val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f), 0)
        }
        private var selectedIndex = 0

        init {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground))
            addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }

        fun bind(categories: List<DiscourseEmojiCategory>, selectedIndex: Int) {
            row.removeAllViews()
            this.selectedIndex = selectedIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
            categories.forEachIndexed { index, category ->
                val tab = EmojiCategoryTabButton(
                    context = context,
                    iconRes = topicReplyComposerEmojiTabIconRes(index),
                    iconUrl = topicReplyComposerEmojiCategoryIconUrl(baseUrl, category),
                ) {
                    onCategorySelected(index)
                }.apply {
                    bind(category, selected = index == this@EmojiCategoryTabsView.selectedIndex)
                }
                row.addView(
                    tab,
                    LinearLayout.LayoutParams(
                        AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_TAB_BUTTON_SIZE_DP.toFloat()),
                        AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_TAB_BUTTON_SIZE_DP.toFloat()),
                    ).apply {
                        if (index + 1 < categories.size) {
                            rightMargin = AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_TAB_GAP_DP.toFloat())
                        }
                    },
                )
            }
        }

        fun setSelectedIndex(index: Int, scrollToVisible: Boolean) {
            if (selectedIndex == index) {
                return
            }
            selectedIndex = index
            for (i in 0 until row.childCount) {
                (row.getChildAt(i) as? EmojiCategoryTabButton)?.setSelectedState(i == selectedIndex)
            }
            if (scrollToVisible) {
                val selected = row.getChildAt(selectedIndex) ?: return
                smoothScrollTo((selected.left - width / 2 + selected.width / 2).coerceAtLeast(0), 0)
            }
        }
    }

    private class EmojiCategoryTabButton(
        context: Context,
        iconRes: Int,
        private val iconUrl: String?,
        private val onSelected: () -> Unit,
    ) : View(context) {
        private val iconReceiver = ImageReceiver(this).apply {
            setAllowLoadingOnAttachedOnly(true)
            setInvalidateAll(true)
        }
        private val iconDrawable = context.resources.getDrawable(iconRes).mutate()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val bounce = ButtonBounce(this)
        private var selected = false

        init {
            isFocusable = true
            isClickable = true
            setWillNotDraw(false)
            iconDrawable.callback = this
        }

        fun bind(category: DiscourseEmojiCategory, selected: Boolean) {
            contentDescription = category.title
            setSelectedState(selected)
            if (iconUrl.isNullOrBlank()) {
                iconReceiver.setImage(null, null, null, null, category.id, 0)
            } else {
                iconReceiver.setImage(ImageLocation.getForPath(iconUrl), null, null, null, iconUrl, 0)
            }
            invalidate()
        }

        fun setSelectedState(selected: Boolean) {
            if (this.selected == selected) {
                return
            }
            this.selected = selected
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

        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            bounce.setPressed(pressed)
        }

        override fun performClick(): Boolean {
            super.performClick()
            onSelected()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val scale = bounce.getScale(0.1f)
            val restore = canvas.save()
            canvas.scale(scale, scale, width / 2f, height / 2f)
            val selectorSize = AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_TAB_SELECTOR_SIZE_DP.toFloat()).toFloat()
            rect.set(
                (width - selectorSize) / 2f,
                (height - selectorSize) / 2f,
                (width + selectorSize) / 2f,
                (height + selectorSize) / 2f,
            )
            if (selected) {
                paint.color = Theme.multAlpha(Theme.getColor(Theme.key_chat_emojiPanelIcon), 0.18f)
                canvas.drawRoundRect(rect, selectorSize / 2f, selectorSize / 2f, paint)
            }
            val iconSize = AndroidUtilities.dp(24f)
            val iconLeft = (width - iconSize) / 2
            val iconTop = (height - iconSize) / 2
            if (iconReceiver.hasBitmapImage()) {
                iconReceiver.setImageCoords(iconLeft.toFloat(), iconTop.toFloat(), iconSize.toFloat(), iconSize.toFloat())
                iconReceiver.draw(canvas)
            } else {
                val iconColor = ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_chat_emojiPanelIcon),
                    Theme.getColor(Theme.key_chat_emojiPanelIconSelected),
                    if (selected) 1f else 0f,
                )
                iconDrawable.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                iconDrawable.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                iconDrawable.draw(canvas)
            }
            canvas.restoreToCount(restore)
        }
    }

    private class EmojiGridAdapter(
        private val baseUrl: String,
        private val onEmojiSelected: (String) -> Unit,
    ) : RecyclerListView.SelectionAdapter() {
        private val items = mutableListOf<EmojiGridItem>()
        private val categoryStartPositions = mutableListOf<Int>()
        private val attachedButtons = LinkedHashSet<EmojiGridButton>()

        fun submit(categories: List<DiscourseEmojiCategory>) {
            cancelEnterAnimation()
            items.clear()
            categoryStartPositions.clear()
            categories.forEachIndexed { index, category ->
                val emojis = category.emojis.distinctBy { it.name }
                if (emojis.isEmpty()) {
                    return@forEachIndexed
                }
                categoryStartPositions.add(items.size)
                items.add(EmojiGridItem.Header(category.title, index))
                emojis.forEach { emoji ->
                    items.add(EmojiGridItem.Emoji(emoji.name, emoji.url, index))
                }
            }
            notifyDataSetChanged()
        }

        fun startEnterAnimation() {
            attachedButtons.forEachIndexed { index, button ->
                button.prepareEnterAnimation()
                button.startEnterAnimation(delayMs = (index.coerceAtMost(12) * 12L))
            }
        }

        fun cancelEnterAnimation() {
            attachedButtons.forEach { button ->
                button.cancelEnterAnimation()
            }
        }

        fun invalidateAttachedButtons() {
            attachedButtons.forEach { button ->
                button.invalidate()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                HeaderHolder(EmojiGridHeaderView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        AndroidUtilities.dp(30f),
                    )
                })
            } else {
                val button = EmojiGridButton(parent.context, onEmojiSelected)
                button.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_CELL_SIZE_DP.toFloat()),
                )
                EmojiHolder(button)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is EmojiGridItem.Header -> (holder as HeaderHolder).header.bind(item.title)
                is EmojiGridItem.Emoji -> (holder as EmojiHolder).button.bind(item.name, resolvePostImageUrl(baseUrl, item.url))
            }
        }

        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            if (holder is EmojiHolder) {
                attachedButtons.add(holder.button)
                holder.button.onAttachedToRecycler()
            }
        }

        override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
            if (holder is EmojiHolder) {
                attachedButtons.remove(holder.button)
                holder.button.onDetachedFromRecycler()
            }
        }

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int {
            return if (items.getOrNull(position) is EmojiGridItem.Header) VIEW_TYPE_HEADER else VIEW_TYPE_EMOJI
        }

        fun isFullSpan(position: Int): Boolean {
            return items.getOrNull(position) is EmojiGridItem.Header
        }

        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
            return holder.itemView is EmojiGridButton
        }

        fun positionForCategory(categoryIndex: Int): Int {
            return categoryStartPositions.getOrNull(categoryIndex) ?: 0
        }

        fun categoryStartPositions(): List<Int> = categoryStartPositions.toList()

        class HeaderHolder(val header: EmojiGridHeaderView) : RecyclerView.ViewHolder(header)

        class EmojiHolder(val button: EmojiGridButton) : RecyclerView.ViewHolder(button)

        private companion object {
            private const val VIEW_TYPE_HEADER = 0
            private const val VIEW_TYPE_EMOJI = 1
        }
    }

    private sealed class EmojiGridItem {
        data class Header(val title: String, val categoryIndex: Int) : EmojiGridItem()
        data class Emoji(val name: String, val url: String, val categoryIndex: Int) : EmojiGridItem()
    }

    private class EmojiGridHeaderView(context: Context) : View(context) {
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(14f).toFloat()
            typeface = AndroidUtilities.bold()
        }
        private var title = ""

        init {
            setWillNotDraw(false)
        }

        fun bind(title: String) {
            this.title = title
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (title.isBlank()) {
                return
            }
            textPaint.color = Theme.getColor(Theme.key_chat_emojiPanelIcon)
            val baseline = height - AndroidUtilities.dp(8f) - textPaint.descent()
            canvas.drawText(title, AndroidUtilities.dp(14f).toFloat(), baseline, textPaint)
        }
    }

    private class EmojiGridButton(
        context: Context,
        private val onEmojiSelected: (String) -> Unit,
    ) : View(context) {
        private val iconReceiver = ImageReceiver(this).apply {
            setAllowLoadingOnAttachedOnly(true)
            setInvalidateAll(true)
        }
        private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = AndroidUtilities.bold()
        }
        private val bounce = ButtonBounce(this)
        private var enterAnimator: ValueAnimator? = null
        private var enterScale = 1f
        private var enterAlpha = 1f
        private var name = ""
        private var fallbackLabel = ""
        private var iconUrl: String? = null
        var ignoring: Boolean = false
            set(value) {
                if (field == value) {
                    return
                }
                field = value
                invalidate()
            }

        init {
            isFocusable = true
            isClickable = true
            setWillNotDraw(false)
        }

        fun bind(name: String, iconUrl: String?) {
            this.name = name
            this.iconUrl = iconUrl
            fallbackLabel = PostReactionRendering.fallbackLabel(name)
            contentDescription = name
            if (iconUrl.isNullOrBlank()) {
                iconReceiver.setImage(null, null, null, null, name, 0)
            } else {
                iconReceiver.setImage(ImageLocation.getForPath(iconUrl), null, null, null, iconUrl, 0)
            }
            invalidate()
        }

        fun onAttachedToRecycler() {
            iconReceiver.onAttachedToWindow()
        }

        fun onDetachedFromRecycler() {
            enterAnimator?.cancel()
            enterAnimator = null
            iconReceiver.onDetachedFromWindow()
        }

        fun prepareEnterAnimation() {
            enterAnimator?.cancel()
            enterAnimator = null
            enterScale = 0.82f
            enterAlpha = 0f
            invalidate()
        }

        fun startEnterAnimation(delayMs: Long) {
            enterAnimator?.cancel()
            enterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180L
                startDelay = delayMs
                interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    enterScale = lerpFloat(0.82f, 1f, value)
                    enterAlpha = value
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (enterAnimator === animation) {
                            enterAnimator = null
                            enterScale = 1f
                            enterAlpha = 1f
                            invalidate()
                        }
                    }
                })
            }
            enterAnimator?.start()
        }

        fun cancelEnterAnimation() {
            enterAnimator?.cancel()
            enterAnimator = null
            enterScale = 1f
            enterAlpha = 1f
            invalidate()
        }

        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            bounce.setPressed(pressed)
        }

        override fun performClick(): Boolean {
            super.performClick()
            if (name.isNotBlank()) {
                onEmojiSelected(name)
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            if (enterAlpha <= 0f || enterScale <= 0f) {
                return
            }
            val save = canvas.save()
            if (enterAlpha < 1f) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255f * enterAlpha).toInt().coerceIn(0, 255))
            }
            val scale = bounce.getScale(0.1f) * enterScale
            canvas.scale(scale, scale, width / 2f, height / 2f)
            val iconSize = AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_ICON_SIZE_DP.toFloat())
            val left = (width - iconSize) / 2f
            val top = (height - iconSize) / 2f
            if (iconReceiver.hasBitmapImage()) {
                iconReceiver.setImageCoords(left, top, iconSize.toFloat(), iconSize.toFloat())
                iconReceiver.draw(canvas)
            } else if (!iconUrl.isNullOrBlank()) {
                drawEmojiLoadingPlaceholder(
                    canvas = canvas,
                    paint = placeholderPaint,
                    left = left,
                    top = top,
                    size = iconSize.toFloat(),
                    radius = iconSize / 2f,
                )
            } else {
                textPaint.color = Theme.getColor(Theme.key_chat_inReactionButtonText)
                textPaint.textSize = AndroidUtilities.dp(if (fallbackLabel.length <= 2) 22f else 15f).toFloat()
                val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(fallbackLabel, width / 2f, baseline, textPaint)
            }
            if (enterAlpha < 1f) {
                canvas.restore()
            }
            canvas.restoreToCount(save)
        }
    }

    private data class ReactionPickerRailSelection(
        val reactionId: String,
        val sourceView: View,
        val sourceCenterX: Float,
        val sourceCenterY: Float,
    )

    private class ReactionPickerRailRow(context: Context) : LinearLayout(context) {
        private var pressedButton: View? = null

        init {
            orientation = HORIZONTAL
            setChildrenDrawingOrderEnabled(true)
        }

        fun setPressedButton(button: View?) {
            if (pressedButton === button) {
                return
            }
            pressedButton = button
            invalidate()
        }

        override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
            val pressedIndex = indexOfChild(pressedButton)
            if (pressedIndex < 0 || pressedIndex >= childCount) {
                return drawingPosition
            }
            return when {
                drawingPosition == childCount - 1 -> pressedIndex
                drawingPosition >= pressedIndex -> drawingPosition + 1
                else -> drawingPosition
            }
        }
    }

    private class ReactionPickerRailView(
        context: Context,
        private val baseUrl: String,
        private val emojiCatalog: Map<String, String>,
        private val showMoreButton: Boolean = false,
        private val onMoreReactionsSelected: (() -> Unit)? = null,
        private val onReactionSelected: (ReactionPickerRailSelection) -> Unit,
    ) : HorizontalScrollView(context), TopicPostScrimPopupContainerLayout.ReactionRail {
        private var transitionProgress = 1f
        private var animatePopupWithTransition = true
        private var enterAnimator: ValueAnimator? = null
        private var onTransitionProgressChanged: ((Float) -> Unit)? = null
        private var childEnterAnimationToken = 0
        private var pendingPressedButton: ReactionPickerRailButton? = null
        private var pressedButton: ReactionPickerRailButton? = null
        private var pressedProgress = 0f
        private var pressedHapticSent = false
        private var pressedAnimator: ValueAnimator? = null
        private var expanded = false
        private val leftShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rightShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val row = ReactionPickerRailRow(context).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(
                AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailHorizontalPaddingDp().toFloat()),
                0,
                AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailHorizontalPaddingDp().toFloat()),
                0,
            )
        }
        private val startPressedRunnable = Runnable {
            val button = pendingPressedButton ?: return@Runnable
            pendingPressedButton = null
            if (pressedButton !== button) {
                pressedAnimator?.cancel()
                pressedButton = button
                pressedProgress = 0f
                pressedHapticSent = false
                updatePressedScales()
            }
            performLongPressHaptic(button)
            animatePressedProgress(toProgress = 1f, duration = reactionPickerRailPressedProgressDurationMs(pressedProgress))
        }

        init {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        fun bind(reactionIds: List<String>, selectedReactionId: String?) {
            expanded = false
            bindInternal(reactionIds, selectedReactionId)
        }

        fun expand(reactionIds: List<String>, selectedReactionId: String?) {
            expanded = true
            bindInternal(reactionIds, selectedReactionId)
            smoothScrollTo(0, 0)
        }

        private fun bindInternal(reactionIds: List<String>, selectedReactionId: String?) {
            childEnterAnimationToken++
            cancelChildEnterAnimations()
            row.setPressedButton(null)
            row.removeAllViews()
            val ids = reactionIds.distinct()
            val shouldShowMore = !expanded && showMoreButton && onMoreReactionsSelected != null && ids.size > TopicDetailFragment.reactionPickerRailMaxVisibleReactions()
            val visibleIds = if (shouldShowMore) {
                ids.take(TopicDetailFragment.reactionPickerRailMaxVisibleReactions())
            } else {
                ids
            }
            visibleIds.forEachIndexed { index, id ->
                val iconUrl = PostReactionRendering.iconUrl(id, null, emojiCatalog)?.let { resolvePostImageUrl(baseUrl, it) }
                val button = ReactionPickerRailButton(context, onReactionSelected, ::handleButtonPressStateChanged).apply {
                    bind(id, iconUrl, selected = id == selectedReactionId)
                }
                button.setShouldSelectOnRelease {
                    shouldSelectReactionOnRelease(button)
                }
                row.addView(
                    button,
                    LinearLayout.LayoutParams(
                        AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailButtonWidthDp().toFloat()),
                        AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailHeightDp().toFloat()),
                    ).apply {
                        rightMargin = if (index == visibleIds.lastIndex && !shouldShowMore) 0 else AndroidUtilities.dp(2f)
                    },
                )
            }
            if (shouldShowMore) {
                row.addView(
                    ReactionPickerRailMoreButton(context, onMoreReactionsSelected!!),
                    LinearLayout.LayoutParams(
                        AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailButtonWidthDp().toFloat()),
                        AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailHeightDp().toFloat()),
                    ),
                )
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            invalidateEdgeShadows()
        }

        override fun startEnterAnimation(animatePopup: Boolean) {
            enterAnimator?.cancel()
            animatePopupWithTransition = animatePopup
            val animationToken = ++childEnterAnimationToken
            alpha = 1f
            setTransitionProgress(0f)
            prepareChildEnterAnimations()
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TopicDetailFragment.reactionPickerRailEnterDurationMs()
                interpolator = OvershootInterpolator(TopicDetailFragment.reactionPickerRailEnterOvershootTension())
                addUpdateListener { animation ->
                    setTransitionProgress(animation.animatedValue as Float)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (enterAnimator === animation) {
                            enterAnimator = null
                            setTransitionProgress(1f)
                            animatePopupWithTransition = true
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (enterAnimator === animation) {
                            enterAnimator = null
                            animatePopupWithTransition = true
                        }
                    }
                })
            }
            enterAnimator = animator
            animator.start()
            row.post {
                if (childEnterAnimationToken == animationToken) {
                    startChildEnterAnimations()
                }
            }
        }

        fun cancelEnterAnimation() {
            childEnterAnimationToken++
            enterAnimator?.cancel()
            enterAnimator = null
            animatePopupWithTransition = true
            cancelChildEnterAnimations()
            cancelPendingPressedStart()
            clearPressedState(animated = false)
            setTransitionProgress(1f)
        }

        fun startDismissAnimation() {
            childEnterAnimationToken++
            enterAnimator?.cancel()
            cancelChildEnterAnimations()
            cancelPendingPressedStart()
            clearPressedState(animated = false)
            val fromProgress = transitionProgress.coerceIn(0f, 1f)
            if (fromProgress <= 0f || !isAttachedToWindow) {
                setTransitionProgress(0f)
                return
            }
            val animator = ValueAnimator.ofFloat(fromProgress, 0f).apply {
                duration = TopicDetailFragment.reactionPickerRailDismissDurationMs()
                interpolator = CubicBezierInterpolator.DEFAULT
                addUpdateListener { animation ->
                    setTransitionProgress(animation.animatedValue as Float)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (enterAnimator === animation) {
                            enterAnimator = null
                            setTransitionProgress(0f)
                            animatePopupWithTransition = true
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (enterAnimator === animation) {
                            enterAnimator = null
                            animatePopupWithTransition = true
                        }
                    }
                })
            }
            enterAnimator = animator
            animator.start()
        }

        override fun setTransitionProgressListener(listener: TopicPostScrimPopupContainerLayout.ProgressListener?) {
            onTransitionProgressChanged = listener?.let { progressListener ->
                { progress -> progressListener.onProgress(progress) }
            }
        }

        override fun itemsCount(): Int {
            return row.childCount
        }

        private fun setTransitionProgress(progress: Float) {
            transitionProgress = progress
            onTransitionProgressChanged?.invoke(if (animatePopupWithTransition) progress else 1f)
            invalidate()
        }

        override fun onDetachedFromWindow() {
            childEnterAnimationToken++
            enterAnimator?.cancel()
            enterAnimator = null
            animatePopupWithTransition = true
            cancelChildEnterAnimations()
            cancelPendingPressedStart()
            clearPressedState(animated = false)
            super.onDetachedFromWindow()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(
                    paddingTop + AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailHeightDp().toFloat()) + paddingBottom,
                    View.MeasureSpec.EXACTLY,
                ),
            )
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (transitionProgress == 1f) {
                super.dispatchDraw(canvas)
                return
            }
            val progress = transitionProgress
            val scale = progress
            val direction = if (LocaleController.isRTL) -1f else 1f
            val pivotX = if (LocaleController.isRTL) width * 0.125f else width * 0.875f
            val clipGate = maxOf(
                TopicDetailFragment.reactionPickerRailClipProgress(),
                progress.coerceAtMost(1f),
            )
            val clipLeft = if (LocaleController.isRTL) {
                0f
            } else {
                (width * (1f - clipGate)).coerceIn(0f, width.toFloat())
            }
            val clipRight = if (LocaleController.isRTL) {
                (width * clipGate).coerceIn(0f, width.toFloat())
            } else {
                width.toFloat()
            }
            val contentTranslation = if (LocaleController.isRTL) {
                direction * width * (1f - progress)
            } else {
                direction * width * (1f - progress) - clipLeft
            }
            val restore = canvas.save()
            canvas.scale(scale, scale, pivotX, height / 2f)
            canvas.clipRect(clipLeft, 0f, clipRight, height.toFloat())
            canvas.translate(contentTranslation, 0f)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(restore)
        }

        private fun invalidateEdgeShadows() {
            val shadowWidth = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailEdgeShadowWidthDp().toFloat()).toFloat()
            val color = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground)
            leftShadowPaint.shader = LinearGradient(
                0f,
                height / 2f,
                shadowWidth,
                height / 2f,
                color,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            rightShadowPaint.shader = LinearGradient(
                width.toFloat(),
                height / 2f,
                width - shadowWidth,
                height / 2f,
                color,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }

        private fun drawEdgeShadows(canvas: Canvas) {
            val shadowWidth = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailEdgeShadowWidthDp().toFloat()).toFloat()
            if (shadowWidth <= 0f) {
                return
            }
            if (canScrollHorizontally(-1)) {
                canvas.drawRect(0f, 0f, shadowWidth, height.toFloat(), leftShadowPaint)
            }
            if (canScrollHorizontally(1)) {
                canvas.drawRect(width - shadowWidth, 0f, width.toFloat(), height.toFloat(), rightShadowPaint)
            }
        }

        private fun prepareChildEnterAnimations() {
            forEachReactionButton { button ->
                button.prepareEnterAnimation()
            }
        }

        private fun startChildEnterAnimations() {
            val railWidth = width.takeIf { it > 0 } ?: row.width
            forEachReactionButton { button ->
                val delayMs = reactionPickerRailButtonEnterDelayMs(button, railWidth)
                button.startEnterAnimation(delayMs)
            }
        }

        private fun cancelChildEnterAnimations() {
            forEachReactionButton { button ->
                button.cancelEnterAnimation()
            }
        }

        private fun forEachReactionButton(block: (ReactionPickerRailButton) -> Unit) {
            for (i in 0 until row.childCount) {
                (row.getChildAt(i) as? ReactionPickerRailButton)?.let(block)
            }
        }

        private fun reactionPickerRailButtonEnterDelayMs(button: View, railWidth: Int): Long {
            val measuredRailWidth = railWidth.coerceAtLeast(1)
            val childCenter = button.left + button.measuredWidth / 2f
            val target = if (LocaleController.isRTL) 0.2f else 0.8f
            return (200f * kotlin.math.abs(childCenter / measuredRailWidth - target)).toLong().coerceAtLeast(0L)
        }

        private fun shouldSelectReactionOnRelease(button: ReactionPickerRailButton): Boolean {
            return TopicDetailFragment.reactionPickerRailShouldSelectOnRelease(
                longPressStarted = pressedButton === button,
                pressedProgress = pressedProgress,
            )
        }

        private fun handleButtonPressStateChanged(button: ReactionPickerRailButton, pressed: Boolean) {
            if (pressed) {
                cancelPendingPressedStart()
                if (pressedButton != null && pressedButton !== button) {
                    clearPressedState(animated = false)
                }
                pendingPressedButton = button
                AndroidUtilities.runOnUIThread(startPressedRunnable, TopicDetailFragment.reactionPickerRailPressedStartDelayMs())
                return
            }
            cancelPendingPressedStart(button)
            if (pressedButton === button) {
                clearPressedState(animated = true)
            }
        }

        private fun cancelPendingPressedStart(button: ReactionPickerRailButton? = null) {
            if (button != null && pendingPressedButton !== button) {
                return
            }
            AndroidUtilities.cancelRunOnUIThread(startPressedRunnable)
            pendingPressedButton = null
        }

        private fun animatePressedProgress(toProgress: Float, duration: Long) {
            pressedAnimator?.cancel()
            val fromProgress = pressedProgress
            if (duration <= 0L || fromProgress == toProgress) {
                pressedProgress = toProgress
                updatePressedScales()
                return
            }
            val animator = ValueAnimator.ofFloat(fromProgress, toProgress).apply {
                this.duration = duration
                interpolator = if (toProgress > fromProgress) {
                    android.view.animation.LinearInterpolator()
                } else {
                    CubicBezierInterpolator.DEFAULT
                }
                addUpdateListener { animation ->
                    pressedProgress = animation.animatedValue as Float
                    maybePerformPressedHaptic()
                    updatePressedScales()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (pressedAnimator === animation) {
                            pressedAnimator = null
                            pressedProgress = toProgress
                            maybePerformPressedHaptic()
                            if (toProgress == 0f) {
                                pressedButton = null
                                pressedHapticSent = false
                            }
                            updatePressedScales()
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (pressedAnimator === animation) {
                            pressedAnimator = null
                        }
                    }
                })
            }
            pressedAnimator = animator
            animator.start()
        }

        private fun clearPressedState(animated: Boolean) {
            cancelPendingPressedStart()
            if (!animated || pressedButton == null) {
                pressedAnimator?.cancel()
                pressedAnimator = null
                pressedButton = null
                pressedProgress = 0f
                pressedHapticSent = false
                updatePressedScales()
                return
            }
            animatePressedProgress(toProgress = 0f, duration = TopicDetailFragment.reactionPickerRailPressedCancelDurationMs())
        }

        private fun performLongPressHaptic(button: ReactionPickerRailButton) {
            pressedHapticSent = true
            try {
                button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (_: Exception) {
            }
        }

        private fun maybePerformPressedHaptic() {
            val button = pressedButton ?: return
            if (pressedHapticSent || pressedProgress < 0.8f) {
                return
            }
            pressedHapticSent = true
            try {
                button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (_: Exception) {
            }
        }

        private fun updatePressedScales() {
            val activeButton = pressedButton
            val activePosition = if (activeButton != null) row.indexOfChild(activeButton) else -1
            val pressedScale = 1f + 2f * pressedProgress
            val otherScale = 1f - 0.15f * pressedProgress
            row.setPressedButton(if (activePosition >= 0 && pressedProgress > 0f) activeButton else null)
            for (i in 0 until row.childCount) {
                val button = row.getChildAt(i) as? ReactionPickerRailButton ?: continue
                if (button === activeButton || activePosition < 0) {
                    button.setRailPressTransform(
                        scale = if (button === activeButton) pressedScale else 1f,
                        translationX = 0f,
                    )
                    continue
                }
                var translationX = button.measuredWidth * (pressedScale - 1f) / 3f -
                    button.measuredWidth * (1f - otherScale) * (kotlin.math.abs(activePosition - i) - 1)
                if (i < activePosition) {
                    translationX = -translationX
                }
                button.setRailPressTransform(otherScale, translationX)
            }
        }

        private fun reactionPickerRailPressedProgressDurationMs(fromProgress: Float): Long {
            return (TopicDetailFragment.reactionPickerRailPressedProgressDurationMs() * (1f - fromProgress.coerceIn(0f, 1f))).toLong()
                .coerceAtLeast(0L)
        }

        fun setExpandTransitionProgress(progress: Float, active: Boolean) {
            for (i in 0 until row.childCount) {
                val button = row.getChildAt(i) as? ReactionPickerRailMoreButton ?: continue
                button.setExpandTransitionProgress(progress, active)
            }
        }
    }

    private class ReactionPickerRailButton(
        context: Context,
        private val onReactionSelected: (ReactionPickerRailSelection) -> Unit,
        private val onPressStateChanged: (ReactionPickerRailButton, Boolean) -> Unit,
    ) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = AndroidUtilities.bold()
        }
        private val rect = RectF()
        private val iconReceiver = ImageReceiver(this).apply {
            setAllowLoadingOnAttachedOnly(true)
        }
        private val bounce = ButtonBounce(this)
        private var shouldSelectOnRelease: () -> Boolean = { true }
        private var enterAnimator: ValueAnimator? = null
        private var enterScale = 1f
        private var enterAlpha = 1f
        private var railPressScale = 1f
        private var railPressTranslationX = 0f
        private var id: String = ""
        private var iconUrl: String? = null
        private var selected = false
        private var trackingPress = false
        private var pressedX = 0f
        private var pressedY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        init {
            setWillNotDraw(false)
            isFocusable = true
        }

        fun bind(id: String, iconUrl: String?, selected: Boolean) {
            this.id = id
            this.iconUrl = iconUrl
            this.selected = selected
            contentDescription = PostReactionRendering.fallbackLabel(id)
            if (iconUrl.isNullOrBlank()) {
                iconReceiver.setImage(null, null, null, null, id, 0)
            } else {
                iconReceiver.setImage(ImageLocation.getForPath(iconUrl), null, null, null, iconUrl, 0)
            }
            invalidate()
        }

        fun setShouldSelectOnRelease(block: () -> Boolean) {
            shouldSelectOnRelease = block
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            iconReceiver.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            enterAnimator?.cancel()
            enterAnimator = null
            iconReceiver.onDetachedFromWindow()
            super.onDetachedFromWindow()
        }

        fun prepareEnterAnimation() {
            enterAnimator?.cancel()
            enterAnimator = null
            enterScale = 0f
            enterAlpha = 0f
            invalidate()
        }

        fun startEnterAnimation(delayMs: Long) {
            enterAnimator?.cancel()
            enterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TopicDetailFragment.reactionPickerRailButtonEnterDurationMs()
                startDelay = delayMs
                interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    enterScale = value
                    enterAlpha = value
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (enterAnimator === animation) {
                            enterAnimator = null
                            enterScale = 1f
                            enterAlpha = 1f
                            invalidate()
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (enterAnimator === animation) {
                            enterAnimator = null
                        }
                    }
                })
            }
            enterAnimator?.start()
        }

        fun cancelEnterAnimation() {
            enterAnimator?.cancel()
            enterAnimator = null
            enterScale = 1f
            enterAlpha = 1f
            invalidate()
        }

        fun setRailPressTransform(scale: Float, translationX: Float) {
            val nextScale = scale.coerceAtLeast(0f)
            val nextTranslationX = if (translationX.isFinite()) translationX else 0f
            if (railPressScale == nextScale && railPressTranslationX == nextTranslationX) {
                return
            }
            railPressScale = nextScale
            railPressTranslationX = nextTranslationX
            setTranslationX(nextTranslationX)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled || id.isEmpty()) {
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    trackingPress = true
                    pressedX = event.x
                    pressedY = event.y
                    bounce.setPressed(true)
                    onPressStateChanged(this, true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (trackingPress && shouldCancelPressByMove(event.x, event.y)) {
                        trackingPress = false
                        bounce.setPressed(false)
                        onPressStateChanged(this, false)
                    }
                    return trackingPress
                }
                MotionEvent.ACTION_UP -> {
                    val shouldClick = trackingPress && !shouldCancelPressByMove(event.x, event.y) && shouldSelectOnRelease()
                    trackingPress = false
                    bounce.setPressed(false)
                    onPressStateChanged(this, false)
                    if (shouldClick) {
                        performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    trackingPress = false
                    bounce.setPressed(false)
                    onPressStateChanged(this, false)
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            onReactionSelected(
                ReactionPickerRailSelection(
                    reactionId = id,
                    sourceView = this,
                    sourceCenterX = width / 2f,
                    sourceCenterY = height / 2f,
                ),
            )
            return true
        }

        override fun onDraw(canvas: Canvas) {
            if (enterAlpha <= 0f || enterScale <= 0f) {
                return
            }
            val enterBounceScale = bounce.getScale(0.1f) * enterScale
            val restore = canvas.save()
            if (enterAlpha < 1f) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255f * enterAlpha).toInt().coerceIn(0, 255))
            }
            if (enterBounceScale != 1f) {
                canvas.scale(enterBounceScale, enterBounceScale, width / 2f, height / 2f)
            }
            if (railPressScale != 1f) {
                canvas.scale(railPressScale, railPressScale, width / 2f, railPressPivotY())
            }
            val selectorSize = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailSelectorSizeDp().toFloat())
            val selectorLeft = (width - selectorSize) / 2f
            val selectorTop = (height - selectorSize) / 2f
            if (selected) {
                val radius = selectorSize / 2f
                backgroundPaint.color = Theme.getColor(Theme.key_listSelector)
                rect.set(selectorLeft, selectorTop, selectorLeft + selectorSize, selectorTop + selectorSize)
                canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
            }

            val textColor = if (selected) {
                Theme.getColor(Theme.key_chat_inReactionButtonTextSelected)
            } else {
                Theme.getColor(Theme.key_chat_inReactionButtonText)
            }
            val iconSize = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailIconSizeDp().toFloat())
            val iconLeft = (width - iconSize) / 2f
            val iconTop = (height - iconSize) / 2f
            val currentIconUrl = iconUrl
            val contentScale = if (selected) TopicDetailFragment.reactionPickerRailButtonSelectedScale() else 1f
            val contentRestore = if (contentScale != 1f) {
                val count = canvas.save()
                canvas.scale(contentScale, contentScale, width / 2f, height / 2f)
                count
            } else {
                -1
            }
            if (!currentIconUrl.isNullOrBlank() && iconReceiver.hasBitmapImage()) {
                iconReceiver.setImageCoords(iconLeft, iconTop, iconSize.toFloat(), iconSize.toFloat())
                iconReceiver.draw(canvas)
            } else {
                val label = PostReactionRendering.fallbackLabel(id)
                textPaint.color = textColor
                textPaint.textSize = AndroidUtilities.dp(if (label.length <= 2) 22f else 15f).toFloat()
                val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(label, width / 2f, baseline, textPaint)
                if (!currentIconUrl.isNullOrBlank()) {
                    iconReceiver.setImageCoords(iconLeft, iconTop, iconSize.toFloat(), iconSize.toFloat())
                    iconReceiver.draw(canvas)
                }
            }
            if (contentRestore >= 0) {
                canvas.restoreToCount(contentRestore)
            }
            if (enterAlpha < 1f) {
                canvas.restore()
            }
            canvas.restoreToCount(restore)
        }

        private fun railPressPivotY(): Float {
            val iconSize = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailIconSizeDp().toFloat())
            return (height - iconSize) / 2f + iconSize
        }

        private fun shouldCancelPressByMove(x: Float, y: Float): Boolean {
            return TopicDetailFragment.reactionPickerRailShouldCancelPressByMove(
                dx = x - pressedX,
                dy = y - pressedY,
                touchSlop = touchSlop,
            )
        }
    }

    private class ReactionPickerRailMoreButton(
        context: Context,
        private val onMoreReactionsSelected: () -> Unit,
    ) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val bounce = ButtonBounce(this)
        private var expandTransitionActive = false
        private val icon = context.resources.getDrawable(TelegramR.drawable.msg_reactions_expand).mutate().apply {
            setColorFilter(PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY))
        }

        init {
            setWillNotDraw(false)
            isFocusable = true
            contentDescription = context.getString(TelegramR.string.AccDescrExpandPanel)
        }

        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            bounce.setPressed(pressed)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val inside = event.x >= 0f && event.x <= width && event.y >= 0f && event.y <= height
                    isPressed = false
                    if (inside) {
                        performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            onMoreReactionsSelected()
            return true
        }

        fun setExpandTransitionProgress(progress: Float, active: Boolean) {
            val nextActive = active && progress >= 0f
            if (expandTransitionActive == nextActive) {
                return
            }
            expandTransitionActive = nextActive
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (expandTransitionActive) {
                return
            }
            val scale = bounce.getScale(0.1f)
            val cx = width / 2f
            val cy = height / 2f
            val restore = if (scale != 1f) {
                val count = canvas.save()
                canvas.scale(scale, scale, cx, cy)
                count
            } else {
                -1
            }
            val inset = AndroidUtilities.dp(3f).toFloat()
            rect.set(inset, cy - (width - inset * 2f) / 2f, width - inset, cy + (width - inset * 2f) / 2f)
            backgroundPaint.color = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon),
                Theme.getColor(Theme.key_dialogBackground),
                0.7f,
            )
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, backgroundPaint)

            val iconLeft = Math.round(cx - icon.intrinsicWidth / 2f)
            val iconTop = Math.round(cy - icon.intrinsicHeight / 2f)
            icon.setBounds(iconLeft, iconTop, iconLeft + icon.intrinsicWidth, iconTop + icon.intrinsicHeight)
            icon.draw(canvas)
            if (restore >= 0) {
                canvas.restoreToCount(restore)
            }
        }
    }

    private class TopicReactionPickerExpandedPanel(
        context: Context,
        private val baseUrl: String,
        private val emojiCatalog: Map<String, String>,
        private val reactionIds: List<String>,
        private val selectedReactionId: String?,
        private val parentAlphaUpdater: (Float) -> Unit,
        private val onDismissed: (TopicReactionPickerExpandedPanel) -> Unit,
        private val onReactionSelected: (ReactionPickerRailSelection) -> Unit,
    ) : FrameLayout(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground)
        }
        private val expandButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground)
        }
        private val expandIcon = context.resources.getDrawable(TelegramR.drawable.msg_reactions_expand).mutate().apply {
            setColorFilter(PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY))
        }
        private val shadow = context.resources.getDrawable(TelegramR.drawable.reactions_bubble_shadow).mutate().apply {
            setColorFilter(PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow), PorterDuff.Mode.MULTIPLY))
        }
        private val shadowPad = Rect()
        private val fromRect = RectF()
        private val targetRect = RectF()
        private val drawingRect = RectF()
        private val expandButtonRect = RectF()
        private val clipPath = Path()
        private val contentLayer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        private var transitionProgress = 0f
        private var fromRadius = 0f
        private var drawingRadius = 0f
        private var animator: ValueAnimator? = null
        private var removed = false
        private var enterFinished = false
        private var targetReady = false
        private var sourceRail: ReactionPickerRailView? = null
        private var hasTransitionExpandButton = false
        private var transitionExpandButtonCenterX = 0f
        private var transitionExpandButtonCenterY = 0f

        init {
            setWillNotDraw(false)
            isClickable = true
            isFocusable = true
            clipChildren = false
            clipToPadding = false
            shadowPad.set(
                AndroidUtilities.dp(7f),
                AndroidUtilities.dp(7f),
                AndroidUtilities.dp(7f),
                AndroidUtilities.dp(7f),
            )
            bindButtons()
            addView(contentLayer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        private fun bindButtons() {
            val ids = reactionIds.distinct()
            val columns = TopicDetailFragment.reactionPickerExpandedPanelColumns(ids.size)
            val cellSize = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelCellSizeDp().toFloat())
            val itemGap = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelInterItemGapDp().toFloat())
            val paddingLeft = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelHorizontalPaddingDp().toFloat())
            val paddingTop = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelVerticalPaddingDp().toFloat())
            ids.forEachIndexed { index, id ->
                val iconUrl = PostReactionRendering.iconUrl(id, null, emojiCatalog)?.let { resolvePostImageUrl(baseUrl, it) }
                val button = ReactionPickerExpandedGridButton(context, onReactionSelected).apply {
                    bind(id, iconUrl, selected = id == selectedReactionId)
                }
                contentLayer.addView(
                    button,
                    FrameLayout.LayoutParams(cellSize, cellSize).apply {
                        leftMargin = paddingLeft + index % columns * (cellSize + itemGap)
                        topMargin = paddingTop + index / columns * (cellSize + itemGap)
                    },
                )
            }
        }

        fun startEnterAnimation(collapsedRail: View) {
            if (removed) {
                return
            }
            sourceRail = collapsedRail as? ReactionPickerRailView
            sourceRail?.setExpandTransitionProgress(0f, active = true)
            computeTransitionRects(collapsedRail)
            updateTransitionProgress(0f)
            requestFocus()
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TopicDetailFragment.reactionPickerExpandedPanelEnterDurationMs()
                interpolator = CubicBezierInterpolator.DEFAULT
                addUpdateListener { animation ->
                    updateTransitionProgress(animation.animatedValue as Float)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (animator === animation) {
                            animator = null
                            enterFinished = true
                            updateTransitionProgress(1f)
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (animator === animation) {
                            animator = null
                        }
                    }
                })
            }
            animator?.start()
        }

        fun dismiss(animated: Boolean, restoreParentAlpha: Boolean = true) {
            if (removed) {
                return
            }
            animator?.cancel()
            enterFinished = false
            if (!animated || transitionProgress <= 0f) {
                if (restoreParentAlpha) {
                    updateTransitionProgress(0f)
                } else {
                    parentAlphaUpdater(0f)
                }
                removeImmediately(restoreParentAlpha)
                return
            }
            val fromProgress = transitionProgress.coerceIn(0f, 1f)
            animator = ValueAnimator.ofFloat(fromProgress, 0f).apply {
                duration = TopicDetailFragment.reactionPickerExpandedPanelDismissDurationMs()
                interpolator = CubicBezierInterpolator.DEFAULT
                addUpdateListener { animation ->
                    updateTransitionProgress(animation.animatedValue as Float)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (animator === animation) {
                            animator = null
                            removeImmediately(restoreParentAlpha)
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (animator === animation) {
                            animator = null
                        }
                    }
                })
            }
            animator?.start()
        }

        fun removeImmediately(restoreParentAlpha: Boolean = true) {
            if (removed) {
                return
            }
            removed = true
            animator?.cancel()
            animator = null
            sourceRail?.setExpandTransitionProgress(0f, active = false)
            sourceRail = null
            if (restoreParentAlpha) {
                parentAlphaUpdater(1f)
            }
            onDismissed(this)
        }

        private fun computeTransitionRects(collapsedRail: View) {
            val panelLocation = IntArray(2)
            val railLocation = IntArray(2)
            getLocationOnScreen(panelLocation)
            collapsedRail.getLocationOnScreen(railLocation)
            val margin = AndroidUtilities.dp(16f).toFloat()
            val fromLeft = railLocation[0] - panelLocation[0].toFloat()
            val fromTop = railLocation[1] - panelLocation[1].toFloat()
            val fromWidth = collapsedRail.measuredWidth.toFloat().coerceAtLeast(1f)
            val targetWidth = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelWidthDp(reactionIds.size).toFloat())
                .toFloat()
            val targetHeight = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelHeightDp(reactionIds.size).toFloat()).toFloat()
            fromRect.set(
                fromLeft,
                fromTop,
                fromLeft + fromWidth,
                fromTop + collapsedRail.measuredHeight.toFloat(),
            )
            fromRadius = fromRect.height() / 2f
            computeTransitionExpandButton(targetWidth)

            val rawLeft = fromRect.left
            val rawTop = fromRect.top + AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelTargetTopOffsetDp().toFloat())
            val maxLeft = (width - targetWidth - margin).coerceAtLeast(margin)
            val maxTop = (height - targetHeight - AndroidUtilities.dp(32f)).coerceAtLeast(margin)
            val left = rawLeft.coerceIn(margin, maxLeft)
            val top = rawTop.coerceIn(margin, maxTop)
            targetRect.set(left, top, left + targetWidth, top + targetHeight)
            targetReady = true
        }

        private fun updateTransitionProgress(progress: Float) {
            transitionProgress = progress.coerceIn(0f, 1f)
            if (targetReady) {
                AndroidUtilities.lerp(fromRect, targetRect, transitionProgress, drawingRect)
                drawingRadius = AndroidUtilities.lerp(
                    fromRadius,
                    AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelCornerRadiusDp().toFloat()).toFloat(),
                    transitionProgress,
                )
                contentLayer.translationX = targetRect.left
                contentLayer.translationY = targetRect.top
                contentLayer.pivotX = 0f
                contentLayer.pivotY = 0f
                contentLayer.scaleX = 1f
                contentLayer.scaleY = 1f
                contentLayer.alpha = 1f
                updateButtonAlphas()
            }
            sourceRail?.setExpandTransitionProgress(transitionProgress, active = true)
            parentAlphaUpdater(1f - transitionProgress)
            invalidate()
        }

        private fun computeTransitionExpandButton(targetWidth: Float) {
            val fromWidth = fromRect.width()
            hasTransitionExpandButton = fromWidth > targetWidth + AndroidUtilities.dp(1f)
            if (!hasTransitionExpandButton) {
                transitionExpandButtonCenterX = 0f
                transitionExpandButtonCenterY = 0f
                return
            }
            val buttonWidth = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailButtonWidthDp().toFloat()).toFloat()
            val buttonGap = AndroidUtilities.dp(2f).toFloat()
            val railPadding = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailHorizontalPaddingDp().toFloat()).toFloat()
            val reactionsBeforeExpand = TopicDetailFragment.reactionPickerRailMaxVisibleReactions()
            transitionExpandButtonCenterX = if (LocaleController.isRTL) {
                fromRect.right - railPadding - reactionsBeforeExpand * (buttonWidth + buttonGap) - buttonWidth / 2f
            } else {
                fromRect.left + railPadding + reactionsBeforeExpand * (buttonWidth + buttonGap) + buttonWidth / 2f
            }
            transitionExpandButtonCenterY = fromRect.centerY()
        }

        private fun updateButtonAlphas() {
            val columns = TopicDetailFragment.reactionPickerExpandedPanelColumns(reactionIds.size)
            for (i in 0 until contentLayer.childCount) {
                val child = contentLayer.getChildAt(i)
                val row = i / columns
                child.alpha = if (row == 0) {
                    1f
                } else {
                    val alphaStart = 0.12f
                    ((transitionProgress - alphaStart) / (1f - alphaStart)).coerceIn(0f, 1f)
                }
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK &&
                event.repeatCount == 0 &&
                event.action == KeyEvent.ACTION_UP
            ) {
                dismiss(animated = true)
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
                enterFinished &&
                targetReady &&
                !drawingRect.contains(ev.x, ev.y)
            ) {
                dismiss(animated = true)
                return true
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
            val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(widthSize, heightSize)
            val targetWidth = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelWidthDp(reactionIds.size).toFloat())
            val targetHeight = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelHeightDp(reactionIds.size).toFloat())
            contentLayer.measure(
                View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(targetHeight, View.MeasureSpec.EXACTLY),
            )
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            contentLayer.layout(0, 0, contentLayer.measuredWidth, contentLayer.measuredHeight)
        }

        override fun dispatchDraw(canvas: Canvas) {
            if (!targetReady || drawingRect.isEmpty) {
                super.dispatchDraw(canvas)
                return
            }
            val shadowAlpha = ((transitionProgress / 0.05f).coerceIn(0f, 1f) * 255f).toInt()
            shadow.alpha = shadowAlpha
            shadow.setBounds(
                (drawingRect.left - shadowPad.left).toInt(),
                (drawingRect.top - shadowPad.top).toInt(),
                (drawingRect.right + shadowPad.right).toInt(),
                (drawingRect.bottom + shadowPad.bottom).toInt(),
            )
            shadow.draw(canvas)
            canvas.drawRoundRect(drawingRect, drawingRadius, drawingRadius, backgroundPaint)
            drawTransitionBubbles(canvas)
            clipPath.rewind()
            clipPath.addRoundRect(drawingRect, drawingRadius, drawingRadius, Path.Direction.CW)
            drawTransitionExpandButton(canvas)
            val restore = canvas.save()
            canvas.clipPath(clipPath)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(restore)
        }

        private fun drawTransitionExpandButton(canvas: Canvas) {
            if (!hasTransitionExpandButton || transitionProgress >= 1f) {
                return
            }
            val alphaProgress = (1f - transitionProgress).coerceIn(0f, 1f)
            val alpha = (255f * alphaProgress * alphaProgress).toInt().coerceIn(0, 255)
            if (alpha <= 0) {
                return
            }
            val restore = canvas.save()
            canvas.clipPath(clipPath)
            val scale = AndroidUtilities.lerp(1f, 0.72f, transitionProgress)
            canvas.scale(scale, scale, transitionExpandButtonCenterX, transitionExpandButtonCenterY)
            val buttonWidth = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailButtonWidthDp().toFloat()).toFloat()
            val inset = AndroidUtilities.dp(3f).toFloat()
            val backgroundSize = buttonWidth - inset * 2f
            expandButtonRect.set(
                transitionExpandButtonCenterX - backgroundSize / 2f,
                transitionExpandButtonCenterY - backgroundSize / 2f,
                transitionExpandButtonCenterX + backgroundSize / 2f,
                transitionExpandButtonCenterY + backgroundSize / 2f,
            )
            expandButtonPaint.color = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon),
                Theme.getColor(Theme.key_dialogBackground),
                0.7f,
            )
            expandButtonPaint.alpha = alpha
            canvas.drawRoundRect(expandButtonRect, expandButtonRect.height() / 2f, expandButtonRect.height() / 2f, expandButtonPaint)

            expandIcon.alpha = alpha
            val iconLeft = Math.round(transitionExpandButtonCenterX - expandIcon.intrinsicWidth / 2f)
            val iconTop = Math.round(transitionExpandButtonCenterY - expandIcon.intrinsicHeight / 2f)
            expandIcon.setBounds(iconLeft, iconTop, iconLeft + expandIcon.intrinsicWidth, iconTop + expandIcon.intrinsicHeight)
            expandIcon.draw(canvas)
            expandIcon.alpha = 255
            expandButtonPaint.alpha = 255
            canvas.restoreToCount(restore)
        }

        private fun drawTransitionBubbles(canvas: Canvas) {
            val alphaProgress = (transitionProgress / 0.2f).coerceIn(0f, 1f) * (1f - transitionProgress)
            if (alphaProgress <= 0f) {
                return
            }
            val alpha = (255f * alphaProgress).toInt().coerceIn(0, 255)
            val bigRadius = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailBigBubbleRadiusDp().toFloat()).toFloat()
            val smallRadius = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailSmallBubbleRadiusDp().toFloat()).toFloat()
            val bigOffset = AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailBigBubbleOffsetDp().toFloat()).toFloat()
            val bigCx = if (LocaleController.isRTL) {
                drawingRect.left + bigOffset
            } else {
                drawingRect.right - bigOffset
            }
            val bigCy = drawingRect.bottom
            drawBubbleShadow(canvas, bigCx, bigCy, bigRadius, TopicDetailFragment.reactionPickerRailBigBubbleShadowPadDp(), alpha)
            bubblePaint.alpha = alpha
            canvas.drawCircle(bigCx, bigCy, bigRadius, bubblePaint)

            val smallCx = if (LocaleController.isRTL) {
                bigCx - AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailBigBubbleRadiusDp().toFloat())
            } else {
                bigCx + AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailBigBubbleRadiusDp().toFloat())
            }
            val smallCy = drawingRect.bottom - AndroidUtilities.dp(TopicDetailFragment.reactionPickerRailSmallBubbleRadiusDp().toFloat()) - AndroidUtilities.dp(3f)
            drawBubbleShadow(canvas, smallCx, smallCy, smallRadius, TopicDetailFragment.reactionPickerRailSmallBubbleShadowPadDp(), alpha)
            canvas.drawCircle(smallCx, smallCy, smallRadius, bubblePaint)
            bubblePaint.alpha = 255
        }

        private fun drawBubbleShadow(canvas: Canvas, cx: Float, cy: Float, radius: Float, shadowPadDp: Int, alpha: Int) {
            val pad = AndroidUtilities.dp(shadowPadDp.toFloat()).toFloat()
            shadow.alpha = alpha
            shadow.setBounds(
                (cx - radius - pad).toInt(),
                (cy - radius - pad).toInt(),
                (cx + radius + pad).toInt(),
                (cy + radius + pad).toInt(),
            )
            shadow.draw(canvas)
        }
    }

    private class ReactionPickerExpandedGridButton(
        context: Context,
        private val onReactionSelected: (ReactionPickerRailSelection) -> Unit,
    ) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = AndroidUtilities.bold()
        }
        private val rect = RectF()
        private val iconReceiver = ImageReceiver(this).apply {
            setAllowLoadingOnAttachedOnly(true)
        }
        private val bounce = ButtonBounce(this)
        private var id: String = ""
        private var iconUrl: String? = null
        private var selected = false
        private var trackingPress = false

        init {
            setWillNotDraw(false)
            isFocusable = true
        }

        fun bind(id: String, iconUrl: String?, selected: Boolean) {
            this.id = id
            this.iconUrl = iconUrl
            this.selected = selected
            contentDescription = PostReactionRendering.fallbackLabel(id)
            if (iconUrl.isNullOrBlank()) {
                iconReceiver.setImage(null, null, null, null, id, 0)
            } else {
                iconReceiver.setImage(ImageLocation.getForPath(iconUrl), null, null, null, iconUrl, 0)
            }
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

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled || id.isEmpty()) {
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    trackingPress = true
                    bounce.setPressed(true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val inside = trackingPress && event.x >= 0f && event.x <= width && event.y >= 0f && event.y <= height
                    trackingPress = false
                    bounce.setPressed(false)
                    if (inside) {
                        performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    trackingPress = false
                    bounce.setPressed(false)
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            onReactionSelected(
                ReactionPickerRailSelection(
                    reactionId = id,
                    sourceView = this,
                    sourceCenterX = width / 2f,
                    sourceCenterY = height / 2f,
                ),
            )
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val scale = bounce.getScale(0.08f)
            val restore = canvas.save()
            if (scale != 1f) {
                canvas.scale(scale, scale, width / 2f, height / 2f)
            }
            if (selected) {
                val selectorSize = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelSelectorSizeDp().toFloat())
                val selectorLeft = (width - selectorSize) / 2f
                val selectorTop = (height - selectorSize) / 2f
                backgroundPaint.color = Theme.getColor(Theme.key_listSelector)
                rect.set(selectorLeft, selectorTop, selectorLeft + selectorSize, selectorTop + selectorSize)
                canvas.drawRoundRect(rect, selectorSize / 2f, selectorSize / 2f, backgroundPaint)
            }
            val textColor = if (selected) {
                Theme.getColor(Theme.key_chat_inReactionButtonTextSelected)
            } else {
                Theme.getColor(Theme.key_chat_inReactionButtonText)
            }
            val iconSize = AndroidUtilities.dp(TopicDetailFragment.reactionPickerExpandedPanelIconSizeDp().toFloat())
            val iconLeft = (width - iconSize) / 2f
            val iconTop = (height - iconSize) / 2f
            val currentIconUrl = iconUrl
            if (!currentIconUrl.isNullOrBlank() && iconReceiver.hasBitmapImage()) {
                iconReceiver.setImageCoords(iconLeft, iconTop, iconSize.toFloat(), iconSize.toFloat())
                iconReceiver.draw(canvas)
            } else {
                val label = PostReactionRendering.fallbackLabel(id)
                textPaint.color = textColor
                textPaint.textSize = AndroidUtilities.dp(if (label.length <= 2) 20f else 14f).toFloat()
                val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(label, width / 2f, baseline, textPaint)
                if (!currentIconUrl.isNullOrBlank()) {
                    iconReceiver.setImageCoords(iconLeft, iconTop, iconSize.toFloat(), iconSize.toFloat())
                    iconReceiver.draw(canvas)
                }
            }
            canvas.restoreToCount(restore)
        }
    }

    private class TopicReactionEffectOverlay(
        context: Context,
        private val reactionId: String,
        iconUrl: String?,
        sourceBounds: RectF,
        private val targetBoundsProvider: () -> RectF?,
        private val hapticViewProvider: () -> View?,
        private val onRemoved: (TopicReactionEffectOverlay) -> Unit,
    ) : View(context) {
        private val sourceBounds = RectF(sourceBounds)
        private val targetBounds = RectF()
        private val fallbackTargetBounds = RectF(sourceBounds)
        private val drawRect = RectF()
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = AndroidUtilities.bold()
        }
        private val iconReceiver = ImageReceiver(this).apply {
            setAllowLoadingOnAttachedOnly(true)
            if (iconUrl.isNullOrBlank()) {
                setImage(null, null, null, null, reactionId, 0)
            } else {
                setImage(ImageLocation.getForPath(iconUrl), null, null, null, iconUrl, 0)
            }
        }
        private var animator: ValueAnimator? = null
        private var targetWaitPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
        private var targetWaitFallback: Runnable? = null
        private var progress = 0f
        private var hasTargetBounds = false
        private var started = false
        private var removed = false
        private var targetShortFeedbackStarted = false

        init {
            setWillNotDraw(false)
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            val fallbackInset = AndroidUtilities.dp(4f).toFloat()
            fallbackTargetBounds.inset(-fallbackInset, -fallbackInset)
        }

        fun startWhenTargetReady() {
            animator?.cancel()
            clearTargetWait()
            progress = 0f
            started = false
            resolveTargetBounds()
            if (hasTargetBounds) {
                startMoveAnimation()
                return
            }
            val listener = ViewTreeObserver.OnPreDrawListener {
                resolveTargetBounds()
                if (hasTargetBounds) {
                    clearTargetWait()
                    startMoveAnimation()
                }
                true
            }
            targetWaitPreDrawListener = listener
            viewTreeObserver.addOnPreDrawListener(listener)
            val fallback = Runnable {
                clearTargetWait()
                startMoveAnimation()
            }
            targetWaitFallback = fallback
            postDelayed(fallback, TopicDetailFragment.reactionEffectTargetWaitMs())
            invalidate()
        }

        private fun startMoveAnimation() {
            if (started || removed) {
                return
            }
            started = true
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TopicDetailFragment.reactionEffectMoveDurationMs()
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { animation ->
                    progress = animation.animatedValue as Float
                    resolveTargetBounds()
                    maybeStartTargetShortFeedback(progress)
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (animator === animation) {
                            animator = null
                            removeSelf()
                        }
                    }

                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        if (animator === animation) {
                            animator = null
                        }
                    }
                })
            }
            animator?.start()
        }

        fun dismiss() {
            removeSelf()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            iconReceiver.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            clearTargetWait()
            animator?.cancel()
            animator = null
            iconReceiver.onDetachedFromWindow()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            resolveTargetBounds()
            if (!started) {
                invalidate()
                return
            }
            val target = if (hasTargetBounds) targetBounds else fallbackTargetBounds
            val t = progress.coerceIn(0f, 1f)
            val moveX = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t)
            val moveY = CubicBezierInterpolator.DEFAULT.getInterpolation(t)
            val targetSize = target.width().coerceAtLeast(AndroidUtilities.dp(20f).toFloat())
            val size = lerpFloat(sourceBounds.width(), targetSize, moveX)
            val centerX = lerpFloat(sourceBounds.centerX(), target.centerX(), moveX)
            val centerY = lerpFloat(sourceBounds.centerY(), target.centerY(), moveY)
            if (hasTargetBounds) {
                drawTargetShortEffect(canvas, target, t)
            }
            drawRect.set(centerX - size / 2f, centerY - size / 2f, centerX + size / 2f, centerY + size / 2f)
            val alpha = if (hasTargetBounds) {
                if (t < 0.82f) 1f else 1f - ((t - 0.82f) / 0.18f).coerceIn(0f, 1f)
            } else {
                1f - t
            }
            if (alpha > 0.01f && drawRect.width() > 0f && drawRect.height() > 0f) {
                drawReaction(canvas, alpha)
            }
            if (hasTargetBounds) {
                val staticAlpha = reactionEffectTargetStaticAlpha(t)
                if (staticAlpha > 0.01f) {
                    drawRect.set(target)
                    drawReaction(canvas, staticAlpha)
                }
            }
        }

        private fun drawTargetShortEffect(canvas: Canvas, target: RectF, progress: Float) {
            val shortStart = TopicDetailFragment.reactionEffectShortStartProgress()
            val shortProgress = ((progress - shortStart) / (1f - shortStart)).coerceIn(0f, 1f)
            if (shortProgress <= 0f) {
                return
            }
            val alpha = (1f - shortProgress) * 0.42f
            if (alpha <= 0.01f) {
                return
            }
            val baseSize = AndroidUtilities.dp(TopicDetailFragment.reactionEffectShortSizeDp().toFloat()).toFloat()
            val size = baseSize * (1f + 0.12f * CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(shortProgress))
            drawRect.set(
                target.centerX() - size / 2f,
                target.centerY() - size / 2f,
                target.centerX() + size / 2f,
                target.centerY() + size / 2f,
            )
            drawReaction(canvas, alpha)
        }

        private fun resolveTargetBounds() {
            val next = targetBoundsProvider()
            if (next != null && !next.isEmpty && next.width() > 0f && next.height() > 0f) {
                targetBounds.set(next)
                hasTargetBounds = true
            }
        }

        private fun clearTargetWait() {
            targetWaitPreDrawListener?.let { listener ->
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.removeOnPreDrawListener(listener)
                }
            }
            targetWaitPreDrawListener = null
            targetWaitFallback?.let { removeCallbacks(it) }
            targetWaitFallback = null
        }

        private fun drawReaction(canvas: Canvas, alpha: Float) {
            val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
            val currentIconUrlDrawn = iconReceiver.hasBitmapImage()
            if (currentIconUrlDrawn) {
                iconReceiver.setAlpha(alpha)
                iconReceiver.setImageCoords(drawRect.left, drawRect.top, drawRect.width(), drawRect.height())
                iconReceiver.draw(canvas)
                iconReceiver.setAlpha(1f)
                return
            }
            val label = PostReactionRendering.fallbackLabel(reactionId)
            textPaint.alpha = alphaInt
            textPaint.textSize = AndroidUtilities.dp(if (label.length <= 2) 24f else 15f).toFloat()
            textPaint.color = Theme.getColor(Theme.key_chat_inReactionButtonText)
            val baseline = drawRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, drawRect.centerX(), baseline, textPaint)
            iconReceiver.setAlpha(alpha)
            iconReceiver.setImageCoords(drawRect.left, drawRect.top, drawRect.width(), drawRect.height())
            iconReceiver.draw(canvas)
            iconReceiver.setAlpha(1f)
        }

        private fun maybeStartTargetShortFeedback(progress: Float) {
            if (targetShortFeedbackStarted || !hasTargetBounds || progress < TopicDetailFragment.reactionEffectShortStartProgress()) {
                return
            }
            targetShortFeedbackStarted = true
            try {
                hapticViewProvider()?.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                )
            } catch (_: Exception) {
            }
        }

        private fun reactionEffectTargetStaticAlpha(progress: Float): Float {
            val shortStart = TopicDetailFragment.reactionEffectShortStartProgress()
            return ((progress - shortStart) / (1f - shortStart)).coerceIn(0f, 1f)
        }

        private fun removeSelf() {
            if (removed) {
                return
            }
            removed = true
            clearTargetWait()
            animator?.cancel()
            animator = null
            (parent as? ViewGroup)?.removeView(this)
            onRemoved(this)
        }
    }

    internal companion object {
        @JvmStatic
        fun topicDetailListBackgroundColor(): Int {
            return Theme.getColor(Theme.key_chat_wallpaper)
        }

        private val DEFAULT_REACTION_IDS = listOf(
            "heart",
            "+1",
            "laughing",
            "open_mouth",
            "clap",
            "confetti_ball",
            "hugs",
            "distorted_face",
            "tieba_087",
            "bili_057",
        )
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_POST = 1
        private const val VIEW_TYPE_FOOTER = 2
        private const val VIEW_TYPE_LOADING = 3
        private const val VIEW_TYPE_SKELETON = 4
        private const val VIEW_TYPE_DATE = 5
        private const val SHEET_ROW_HEADER = 9
        private const val SHEET_ROW_REACTION_USERS_TOP_SPACER = 10
        private const val SHEET_ROW_LOADING = 11
        private const val SHEET_ROW_EMPTY = 12
        private const val SHEET_ROW_USER = 13
        private const val SHEET_ROW_REPLY_HEADER = 20
        private const val SHEET_ROW_REPLY = 21
        private const val SHEET_ROW_LOADING_MORE = 22
        private const val SHEET_ROW_REACTION_PICKER_HEADER = 30
        private const val SHEET_ROW_REACTION_PICKER_ITEM = 31
        private const val REACTION_PICKER_RAIL_ENTER_DURATION_MS = 250L
        private const val REACTION_PICKER_RAIL_DISMISS_DURATION_MS = 220L
        private const val REACTION_PICKER_RAIL_ENTER_OVERSHOOT_TENSION = 0.5f
        private const val REACTION_PICKER_RAIL_CLIP_PROGRESS = 0.25f
        private const val REACTION_PICKER_RAIL_BUTTON_ENTER_DURATION_MS = 150L
        private const val REACTION_PICKER_RAIL_BUTTON_SELECTED_SCALE = 0.76f
        private const val REACTION_PICKER_RAIL_LONGPRESS_SELECT_PROGRESS = 0.8f
        private const val REACTION_PICKER_RAIL_PRESSED_PROGRESS_DURATION_MS = 1500L
        private const val REACTION_PICKER_RAIL_PRESSED_CANCEL_DURATION_MS = 150L
        private const val REACTION_PICKER_RAIL_MAX_VISIBLE_REACTIONS = 5
        private const val REACTION_PICKER_RAIL_ICON_SIZE_DP = 30
        private const val REACTION_PICKER_RAIL_SELECTOR_SIZE_DP = 30
        private const val REACTION_PICKER_RAIL_BIG_BUBBLE_RADIUS_DP = 8
        private const val REACTION_PICKER_RAIL_SMALL_BUBBLE_RADIUS_DP = 4
        private const val REACTION_PICKER_RAIL_BIG_BUBBLE_OFFSET_DP = 36
        private const val REACTION_PICKER_RAIL_BIG_BUBBLE_SHADOW_PAD_DP = 3
        private const val REACTION_PICKER_RAIL_SMALL_BUBBLE_SHADOW_PAD_DP = -1
        private const val REACTION_PICKER_EXPANDED_PANEL_MAX_COLUMNS = 5
        private const val REACTION_PICKER_EXPANDED_PANEL_CELL_SIZE_DP = 36
        private const val REACTION_PICKER_EXPANDED_PANEL_INTER_ITEM_GAP_DP = 2
        private const val REACTION_PICKER_EXPANDED_PANEL_HORIZONTAL_PADDING_DP = 8
        private const val REACTION_PICKER_EXPANDED_PANEL_VERTICAL_PADDING_DP = 4
        private const val REACTION_PICKER_EXPANDED_PANEL_CORNER_RADIUS_DP = 8
        private const val REACTION_PICKER_EXPANDED_PANEL_ICON_SIZE_DP = 30
        private const val REACTION_PICKER_EXPANDED_PANEL_SELECTOR_SIZE_DP = 30
        private const val REACTION_PICKER_EXPANDED_PANEL_ENTER_DURATION_MS = 350L
        private const val REACTION_PICKER_EXPANDED_PANEL_DISMISS_DURATION_MS = 220L
        private const val REACTION_EFFECT_ONLY_MOVE_SIZE_DP = 34
        private const val REACTION_EFFECT_SHORT_SIZE_DP = 80
        private const val REACTION_EFFECT_MOVE_DURATION_MS = 350L
        private const val REACTION_EFFECT_TARGET_WAIT_MS = 80L
        private const val REACTION_EFFECT_SHORT_START_PROGRESS = 0.7f
        private const val REACTION_EFFECT_POPUP_DISMISS_DELAY_MS = 50L

        fun reactionPickerRailWidthDp(reactionCount: Int): Int {
            val count = reactionCount.coerceAtLeast(0)
            if (count == 0) {
                return 200
            }
            val contentWidth = reactionPickerRailButtonWidthDp() * count +
                2 * (count - 1) +
                16
            return contentWidth.coerceIn(200, 318)
        }

        fun defaultReactionIds(): List<String> {
            return DEFAULT_REACTION_IDS
        }

        fun reactionPickerRailMaxVisibleReactions(): Int {
            return REACTION_PICKER_RAIL_MAX_VISIBLE_REACTIONS
        }

        fun reactionPickerRailShownItemCount(reactionCount: Int, showMoreButton: Boolean): Int {
            val count = reactionCount.coerceAtLeast(0)
            return if (showMoreButton && count > REACTION_PICKER_RAIL_MAX_VISIBLE_REACTIONS) {
                REACTION_PICKER_RAIL_MAX_VISIBLE_REACTIONS + 1
            } else {
                count
            }
        }

        fun reactionPickerRailIconSizeDp(): Int {
            return REACTION_PICKER_RAIL_ICON_SIZE_DP
        }

        fun reactionPickerRailSelectorSizeDp(): Int {
            return REACTION_PICKER_RAIL_SELECTOR_SIZE_DP
        }

        fun reactionPickerRailHeightDp(): Int {
            return 48
        }

        fun reactionPickerRailButtonWidthDp(): Int {
            return 36
        }

        fun reactionPickerRailHorizontalPaddingDp(): Int {
            return 8
        }

        fun reactionPickerRailEdgeShadowWidthDp(): Int {
            return 24
        }

        fun reactionPickerRailEnterDurationMs(): Long {
            return REACTION_PICKER_RAIL_ENTER_DURATION_MS
        }

        fun reactionPickerRailDismissDurationMs(): Long {
            return REACTION_PICKER_RAIL_DISMISS_DURATION_MS
        }

        fun reactionPickerRailEnterOvershootTension(): Float {
            return REACTION_PICKER_RAIL_ENTER_OVERSHOOT_TENSION
        }

        fun reactionPickerRailClipProgress(): Float {
            return REACTION_PICKER_RAIL_CLIP_PROGRESS
        }

        fun reactionPickerRailBubbleProgress(progress: Float): Float {
            return ((progress.coerceIn(0f, 1f) - REACTION_PICKER_RAIL_CLIP_PROGRESS) /
                (1f - REACTION_PICKER_RAIL_CLIP_PROGRESS)).coerceIn(0f, 1f)
        }

        fun reactionPickerRailBigBubbleRadiusDp(): Int {
            return REACTION_PICKER_RAIL_BIG_BUBBLE_RADIUS_DP
        }

        fun reactionPickerRailSmallBubbleRadiusDp(): Int {
            return REACTION_PICKER_RAIL_SMALL_BUBBLE_RADIUS_DP
        }

        fun reactionPickerRailBigBubbleOffsetDp(): Int {
            return REACTION_PICKER_RAIL_BIG_BUBBLE_OFFSET_DP
        }

        fun reactionPickerRailBigBubbleShadowPadDp(): Int {
            return REACTION_PICKER_RAIL_BIG_BUBBLE_SHADOW_PAD_DP
        }

        fun reactionPickerRailSmallBubbleShadowPadDp(): Int {
            return REACTION_PICKER_RAIL_SMALL_BUBBLE_SHADOW_PAD_DP
        }

        fun reactionPickerRailButtonEnterDurationMs(): Long {
            return REACTION_PICKER_RAIL_BUTTON_ENTER_DURATION_MS
        }

        fun reactionPickerRailButtonSelectedScale(): Float {
            return REACTION_PICKER_RAIL_BUTTON_SELECTED_SCALE
        }

        fun reactionPickerRailPressedStartDelayMs(): Long {
            return ViewConfiguration.getLongPressTimeout().toLong()
        }

        fun reactionPickerRailLongPressSelectProgress(): Float {
            return REACTION_PICKER_RAIL_LONGPRESS_SELECT_PROGRESS
        }

        fun reactionPickerRailShouldSelectOnRelease(longPressStarted: Boolean, pressedProgress: Float): Boolean {
            return !longPressStarted || pressedProgress > REACTION_PICKER_RAIL_LONGPRESS_SELECT_PROGRESS
        }

        fun reactionPickerRailShouldCancelPressByMove(dx: Float, dy: Float, touchSlop: Float): Boolean {
            val limit = touchSlop.coerceAtLeast(0f) * 2f
            return kotlin.math.abs(dx) > limit || kotlin.math.abs(dy) > limit
        }

        fun reactionPickerRailPressedProgressDurationMs(): Long {
            return REACTION_PICKER_RAIL_PRESSED_PROGRESS_DURATION_MS
        }

        fun reactionPickerRailPressedCancelDurationMs(): Long {
            return REACTION_PICKER_RAIL_PRESSED_CANCEL_DURATION_MS
        }

        fun reactionPickerExpandedPanelColumns(reactionCount: Int): Int {
            return reactionCount.coerceAtLeast(1).coerceAtMost(REACTION_PICKER_EXPANDED_PANEL_MAX_COLUMNS)
        }

        fun reactionPickerExpandedPanelWidthDp(reactionCount: Int): Int {
            val columns = reactionPickerExpandedPanelColumns(reactionCount)
            return columns * REACTION_PICKER_EXPANDED_PANEL_CELL_SIZE_DP +
                (columns - 1).coerceAtLeast(0) * REACTION_PICKER_EXPANDED_PANEL_INTER_ITEM_GAP_DP +
                REACTION_PICKER_EXPANDED_PANEL_HORIZONTAL_PADDING_DP * 2
        }

        fun reactionPickerExpandedPanelHeightDp(reactionCount: Int): Int {
            val count = reactionCount.coerceAtLeast(1)
            val columns = reactionPickerExpandedPanelColumns(count)
            val rows = (count + columns - 1) / columns
            return rows * REACTION_PICKER_EXPANDED_PANEL_CELL_SIZE_DP +
                (rows - 1).coerceAtLeast(0) * REACTION_PICKER_EXPANDED_PANEL_INTER_ITEM_GAP_DP +
                REACTION_PICKER_EXPANDED_PANEL_VERTICAL_PADDING_DP * 2
        }

        fun reactionPickerExpandedPanelCellSizeDp(): Int {
            return REACTION_PICKER_EXPANDED_PANEL_CELL_SIZE_DP
        }

        fun reactionPickerExpandedPanelHorizontalPaddingDp(): Int {
            return REACTION_PICKER_EXPANDED_PANEL_HORIZONTAL_PADDING_DP
        }

        fun reactionPickerExpandedPanelInterItemGapDp(): Int {
            return REACTION_PICKER_EXPANDED_PANEL_INTER_ITEM_GAP_DP
        }

        fun reactionPickerExpandedPanelVerticalPaddingDp(): Int {
            return REACTION_PICKER_EXPANDED_PANEL_VERTICAL_PADDING_DP
        }

        fun reactionPickerExpandedPanelCornerRadiusDp(): Int {
            return REACTION_PICKER_EXPANDED_PANEL_CORNER_RADIUS_DP
        }

        fun reactionPickerExpandedPanelIconSizeDp(): Int {
            return REACTION_PICKER_EXPANDED_PANEL_ICON_SIZE_DP
        }

        fun reactionPickerExpandedPanelSelectorSizeDp(): Int {
            return REACTION_PICKER_EXPANDED_PANEL_SELECTOR_SIZE_DP
        }

        fun reactionPickerExpandedPanelTargetTopOffsetDp(): Int {
            return reactionPickerRailHeightDp() / 2 -
                reactionPickerExpandedPanelVerticalPaddingDp() -
                reactionPickerExpandedPanelCellSizeDp() / 2
        }

        fun reactionPickerExpandedPanelEnterDurationMs(): Long {
            return REACTION_PICKER_EXPANDED_PANEL_ENTER_DURATION_MS
        }

        fun reactionPickerExpandedPanelDismissDurationMs(): Long {
            return REACTION_PICKER_EXPANDED_PANEL_DISMISS_DURATION_MS
        }

        fun reactionEffectMoveDurationMs(): Long {
            return REACTION_EFFECT_MOVE_DURATION_MS
        }

        fun reactionEffectOnlyMoveSizeDp(): Int {
            return REACTION_EFFECT_ONLY_MOVE_SIZE_DP
        }

        fun reactionEffectShortSizeDp(): Int {
            return REACTION_EFFECT_SHORT_SIZE_DP
        }

        fun reactionEffectTargetWaitMs(): Long {
            return REACTION_EFFECT_TARGET_WAIT_MS
        }

        fun reactionEffectShortStartProgress(): Float {
            return REACTION_EFFECT_SHORT_START_PROGRESS
        }

        fun reactionEffectPopupDismissDelayMs(): Long {
            return REACTION_EFFECT_POPUP_DISMISS_DELAY_MS
        }

        fun postOptionsActions(isBookmarked: Boolean): List<TopicPostAction> {
            return listOf(
                TopicPostAction(
                    kind = TopicPostActionKind.REPLY,
                    iconRes = postReplyIconRes(),
                    titleRes = io.github.baba983.nativedo.R.string.topic_reader_action_reply,
                ),
                TopicPostAction(
                    kind = TopicPostActionKind.SELECT_TEXT,
                    iconRes = postSelectTextIconRes(),
                    titleRes = io.github.baba983.nativedo.R.string.topic_reader_action_select_text,
                ),
                TopicPostAction(
                    kind = TopicPostActionKind.COPY_LINK,
                    iconRes = postCopyLinkIconRes(),
                    titleRes = io.github.baba983.nativedo.R.string.app_browser_copy_link,
                ),
                TopicPostAction(
                    kind = TopicPostActionKind.BOOKMARK,
                    iconRes = postBookmarkIconRes(isBookmarked),
                    titleRes = if (isBookmarked) {
                        io.github.baba983.nativedo.R.string.app_browser_remove_bookmark
                    } else {
                        io.github.baba983.nativedo.R.string.app_browser_add_bookmark
                    },
                ),
            )
        }

        fun postOptionsReactionIds(
            currentUserReaction: String?,
            validReactionIds: List<String>,
            postReactions: List<PostReaction>,
            fallbackReactionIds: List<String>,
        ): List<String> {
            return postReactionPickerIds(
                initialReactionId = currentUserReaction,
                validReactionIds = validReactionIds,
                postReactions = postReactions,
                fallbackReactionIds = fallbackReactionIds,
            )
        }

        fun postReactionPickerIds(
            initialReactionId: String?,
            validReactionIds: List<String>,
            postReactions: List<PostReaction>,
            fallbackReactionIds: List<String>,
        ): List<String> {
            val availableReactionIds = validReactionIds
                .filter { it.isNotBlank() }
                .ifEmpty { fallbackReactionIds.filter { it.isNotBlank() } }
            return buildList {
                initialReactionId?.let { add(it) }
                addAll(availableReactionIds)
                addAll(postReactions.map { reaction -> reaction.id })
            }.filter { it.isNotBlank() }.distinct()
        }

        fun postBookmarkFeedbackMessageRes(wasBookmarked: Boolean): Int {
            return if (wasBookmarked) {
                io.github.baba983.nativedo.R.string.app_browser_bookmark_removed_message
            } else {
                io.github.baba983.nativedo.R.string.app_browser_bookmark_saved_message
            }
        }

        fun postBookmarkIconRes(wasBookmarked: Boolean): Int {
            return if (wasBookmarked) {
                TelegramR.drawable.menu_browser_bookmarks
            } else {
                TelegramR.drawable.msg_saved
            }
        }

        fun postReplyIconRes(): Int {
            return TelegramR.drawable.menu_reply
        }

        fun postCopyLinkIconRes(): Int {
            return TelegramR.drawable.msg_link
        }

        fun postSelectTextIconRes(): Int {
            return TelegramR.drawable.msg_select
        }

        fun selectionCopyFeedbackMessageRes(): Int {
            return TelegramR.string.TextCopied
        }

        fun topicCopyLinkBulletinOnTop(): Boolean {
            return true
        }

    }
}

/**
 * Mirrors ChatActivity's blur-source split:
 * - wallpaper underlayer for top FadeView (`navbarContentSourceWallpaper`)
 * - live list RenderNode + noise suppressor for glass capsules
 */
internal enum class TopicDetailBlurSourceKind {
    WALLPAPER_COLOR,
    LIST_RENDER_NODE_FROSTED,
    LIST_RENDER_NODE_GLASS,
}

/** ChatActivity: FadeView uses wallpaper, never frosted list capture. */
internal fun topicDetailTopFadeSourceKind(): TopicDetailBlurSourceKind =
    TopicDetailBlurSourceKind.WALLPAPER_COLOR

/** ChatActivity: ActionBar glass uses list RenderNode (glass or frosted). */
internal fun topicDetailActionBarGlassSourceKind(
    liquidGlassEnabled: Boolean,
): TopicDetailBlurSourceKind =
    if (liquidGlassEnabled) {
        TopicDetailBlurSourceKind.LIST_RENDER_NODE_GLASS
    } else {
        TopicDetailBlurSourceKind.LIST_RENDER_NODE_FROSTED
    }


internal fun topicDetailCanStartSwipeReply(
    isBusy: Boolean,
    selectionActive: Boolean,
    popupActive: Boolean,
    listScrollLocked: Boolean,
): Boolean {
    return !isBusy && !selectionActive && !popupActive && !listScrollLocked
}

internal fun topicDetailShouldLoadMoreFromScroll(
    dy: Int,
    userScrollActive: Boolean,
    lastVisibleItemPosition: Int,
    itemCount: Int,
    threshold: Int = 4,
): Boolean {
    if (!userScrollActive || dy <= 0 || itemCount <= 0 || lastVisibleItemPosition < 0) {
        return false
    }
    return lastVisibleItemPosition >= (itemCount - threshold.coerceAtLeast(1)).coerceAtLeast(0)
}

internal fun topicDetailShouldLoadPreviousFromScroll(
    dy: Int,
    userScrollActive: Boolean,
    firstVisibleItemPosition: Int,
    threshold: Int = 4,
): Boolean {
    if (!userScrollActive || dy >= 0 || firstVisibleItemPosition < 0) {
        return false
    }
    return firstVisibleItemPosition < threshold.coerceAtLeast(1)
}

internal fun topicRepliesSheetShouldLoadMore(
    dy: Int,
    canLoadMore: Boolean,
    lastVisibleItemPosition: Int,
    outerItemCount: Int,
    threshold: Int = 3,
): Boolean {
    if (!canLoadMore || dy <= 0 || lastVisibleItemPosition < 0 || outerItemCount <= 1) {
        return false
    }
    return lastVisibleItemPosition >= (outerItemCount - threshold.coerceAtLeast(1)).coerceAtLeast(1)
}

internal fun topicDetailShouldAnchorInitialContent(
    initialContentAnchored: Boolean,
    userHasScrolled: Boolean,
    isLoading: Boolean,
    isRefreshing: Boolean,
    postsEmpty: Boolean,
    errorMessage: String?,
    hasJumpEvent: Boolean,
): Boolean {
    return !initialContentAnchored &&
        !userHasScrolled &&
        topicDetailListShowsTopicHeader(
            isLoading = isLoading,
            postsEmpty = postsEmpty,
            errorMessage = errorMessage,
        ) &&
        !isRefreshing &&
        !hasJumpEvent
}

internal fun topicInitialReadAnchorPostId(
    posts: List<Post>,
    lastReadPostNumber: Int,
    lastReadPostId: Long?,
): Long? {
    if (lastReadPostNumber <= 0) return null
    return posts.firstOrNull { post -> post.postNumber == lastReadPostNumber }?.id
        ?: lastReadPostId?.takeIf { postId -> posts.any { post -> post.id == postId } }
}

internal fun topicDetailListShowsTopicHeader(
    isLoading: Boolean,
    postsEmpty: Boolean,
    errorMessage: String?,
): Boolean {
    return !isLoading && postsEmpty.not() && errorMessage == null
}

internal enum class TopicPostActionKind {
    REPLY,
    SELECT_TEXT,
    COPY_LINK,
    BOOKMARK,
}

internal data class TopicPostAction(
    val kind: TopicPostActionKind,
    val iconRes: Int,
    val titleRes: Int,
)

internal data class TopicPostOptionsPopupPosition(
    val x: Int,
    val y: Int,
)

internal fun topicPostOptionsPopupPosition(
    anchorView: View,
    popupWidth: Int,
    popupHeight: Int,
    pressX: Float,
    pressY: Float,
): TopicPostOptionsPopupPosition {
    val anchorLocation = IntArray(2)
    anchorView.getLocationInWindow(anchorLocation)
    val displayWidth = AndroidUtilities.displaySize.x.takeIf { it > 0 } ?: anchorView.resources.displayMetrics.widthPixels
    val displayHeight = AndroidUtilities.displaySize.y.takeIf { it > 0 } ?: anchorView.resources.displayMetrics.heightPixels
    val touchX = anchorLocation[0] + pressX.coerceIn(0f, anchorView.width.toFloat().coerceAtLeast(0f))
    val touchY = anchorLocation[1] + pressY.coerceIn(0f, anchorView.height.toFloat().coerceAtLeast(0f))
    val margin = AndroidUtilities.dp(6f)
    val width = popupWidth.coerceAtLeast(AndroidUtilities.dp(200f))
    val height = topicPostOptionsPopupHeightForClamp(popupHeight)
    val rawX = touchX - width - AndroidUtilities.dp(28f)
    val maxX = (displayWidth - width - margin).coerceAtLeast(margin)
    val highMenuOffset = if (height > AndroidUtilities.dp(240f)) {
        AndroidUtilities.dp(240f) - height
    } else {
        0
    }
    val rawY = touchY + highMenuOffset
    val minY = (AndroidUtilities.statusBarHeight + AndroidUtilities.dp(6f)).coerceAtLeast(margin)
    val maxY = (displayHeight - height - AndroidUtilities.dp(8f)).coerceAtLeast(minY)
    return TopicPostOptionsPopupPosition(
        x = rawX.toInt().coerceIn(margin, maxX),
        y = rawY.toInt().coerceIn(minY, maxY),
    )
}

internal fun topicPostOptionsPopupHeightForClamp(popupHeight: Int): Int {
    return popupHeight.coerceAtLeast(AndroidUtilities.dp(48f)) + AndroidUtilities.dp(48f)
}

internal fun topicPostScrimPopupMaxHeight(anchorView: View, popupY: Int): Int {
    val displayHeight = AndroidUtilities.displaySize.y.takeIf { it > 0 } ?: anchorView.resources.displayMetrics.heightPixels
    return (displayHeight - popupY - AndroidUtilities.dp(8f)).coerceAtLeast(AndroidUtilities.dp(48f))
}

/** The activity's result code for the media picker (see `pickMedia`). */
internal const val RICH_EDITOR_MEDIA_REQUEST_CODE = 4711

/** Who asked for media: the reply composer's own attach, or the block editor's page. */
private enum class TopicMediaPickTarget { Composer, Editor }

private const val TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP = 44
private const val TOPIC_BOTTOM_INPUT_CONTAINER = 1
private const val TOPIC_BOTTOM_SEARCH_CONTAINER = 4
private const val TOPIC_MENU_SEARCH_FIELD = 1
private const val TOPIC_MENU_OTHER = 2
private const val TOPIC_MENU_SEARCH = 3
private const val TOPIC_MENU_REFRESH = 4
private const val TOPIC_IDENTITY_AVATAR_SIZE_DP = 42
private const val TOPIC_AVATAR_REQUEST_SIZE = 96
private const val TOPIC_AVATAR_FILTER_SIZE = 50
private const val REPLY_SKELETON_ROW_HEIGHT_DP = 88f
private const val REPLY_SKELETON_MAX_VISIBLE_ROWS = 6
/** ChatActivity avatarContainer left margin (back button zone). */
private const val TOPIC_IDENTITY_HOST_LEFT_GUTTER_DP = 53f
/**
 * Chat uses 52 (single ⋯) / 92 (call icon). Topic only shows ⋯ in the menu pill.
 */
private const val TOPIC_IDENTITY_HOST_RIGHT_GUTTER_DP = 52f
private const val TOPIC_IDENTITY_LEFT_PADDING_DP = 8f
/** Trailing reserve inside middle pill (Chat avatarContainer width - 54 - 16). */
private const val TOPIC_IDENTITY_TEXT_TRAILING_RESERVE_DP = 16
/** SimpleTextView gradient fade width at the long-title edge (Chat-like soft cut). */
private const val TOPIC_IDENTITY_TEXT_FADE_DP = 16
/** Right padding so marquee/ellipsize fade sits inside the pill, not on the glass rim. */
private const val TOPIC_IDENTITY_TEXT_RIGHT_PADDING_DP = 8
private const val TOPIC_IDENTITY_TITLE_TOP_OFFSET_DP = 1.3f
private const val TOPIC_IDENTITY_SUBTITLE_TOP_OFFSET_DP = 24f
private const val TOPIC_IDENTITY_TITLE_SIZE_DP = 17
private const val TOPIC_IDENTITY_SUBTITLE_SIZE_DP = 14
private const val TOPIC_SEARCH_DEBOUNCE_MS = 300L
private const val TOPIC_SEARCH_ACTION_BAR_ANIMATION_DURATION_MS = 150L
private const val TOPIC_REPLY_COMPOSER_SEND_BUTTON_WIDTH_DP = 100
private const val TOPIC_INPUT_ISLAND_RADIUS_DP = 22f
private const val TOPIC_INPUT_ISLAND_HORIZONTAL_INSET_DP = 7f
private const val TOPIC_INPUT_ISLAND_BOTTOM_GAP_DP = 9f
private const val TOPIC_REPLY_COMPOSER_PANEL_HORIZONTAL_INSET_DP = 0f
private const val TOPIC_REPLY_COMPOSER_PANEL_TOP_INSET_DP = 0f
private const val TOPIC_REPLY_COMPOSER_PANEL_BOTTOM_INSET_DP = 0f
private const val TOPIC_REPLY_COMPOSER_PANEL_RADIUS_DP = 0f
private const val TOPIC_REPLY_COMPOSER_ICON_BUTTON_PADDING_DP = 7.5f
private const val TOPIC_REPLY_COMPOSER_RICH_EDITOR_BUTTON_LEFT_MARGIN_DP = 46f
private const val TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_RADIUS_DP = 19f
private const val TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_W_DP = 1f
private const val TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_H_DP = 3f
private const val TOPIC_REPLY_COMPOSER_TEXT_FIELD_BOTTOM_PADDING_DP = 0f
private const val TOPIC_REPLY_COMPOSER_EDIT_TEXT_LEFT_MARGIN_DP = 96f
private const val TOPIC_REPLY_COMPOSER_EDIT_TEXT_RIGHT_MARGIN_DP = 50f
private const val TOPIC_REPLY_COMPOSER_SEND_BUTTON_LOADING_RADIUS_DP = 8.66f
private const val TOPIC_REPLY_COMPOSER_TOP_VIEW_HEIGHT_DP = 48
private const val TOPIC_REPLY_COMPOSER_REPLY_HEADER_HEIGHT_DP = 46
private const val TOPIC_REPLY_COMPOSER_REPLY_HEADER_TEXT_HEIGHT_DP = 18f
private const val TOPIC_REPLY_COMPOSER_REPLY_HEADER_TITLE_TOP_DP = 6f
private const val TOPIC_REPLY_COMPOSER_REPLY_HEADER_SUBTITLE_TOP_DP = 24f
private const val TOPIC_REPLY_COMPOSER_ICON_SWITCH_DURATION_MS = 200L
private const val TOPIC_REPLY_COMPOSER_TOP_VIEW_SHOW_DELAY_MS = 200L
private const val TOPIC_REPLY_COMPOSER_KEYBOARD_OPEN_RETRY_DELAY_MS = 100L
private const val TOPIC_REPLY_COMPOSER_KEYBOARD_OPEN_AFTER_EMOJI_WAIT_MS = 200L
private const val TOPIC_REPLY_COMPOSER_KEYBOARD_PREFS = "topic_reply_composer_keyboard"
private const val TOPIC_REPLY_COMPOSER_KEYBOARD_HEIGHT_KEY = "kbd_height"
private const val TOPIC_REPLY_COMPOSER_KEYBOARD_HEIGHT_LAND_KEY = "kbd_height_land3"
private const val TOPIC_REPLY_COMPOSER_TOP_VIEW_ENTER_DURATION_MS = 250L
private const val TOPIC_REPLY_COMPOSER_TOP_VIEW_HIDE_DURATION_MS = 250L
private const val TOPIC_REPLY_COMPOSER_EDIT_TEXT_BOUNDS_ANIMATION_DURATION_MS = 250L
private const val TOPIC_REPLY_COMPOSER_EMOJI_PANEL_ANIMATION_START_DELAY_MS = 0L
private const val TOPIC_REPLY_COMPOSER_SEND_BUTTON_LOADING_REPEAT_DELAY_MS = 650L
private const val TOPIC_REPLY_COMPOSER_EMOJI_PANEL_TABS_HEIGHT_DP = 36
private const val TOPIC_REPLY_COMPOSER_EMOJI_TAB_BUTTON_SIZE_DP = 30
private const val TOPIC_REPLY_COMPOSER_EMOJI_TAB_SELECTOR_SIZE_DP = 30
private const val TOPIC_REPLY_COMPOSER_EMOJI_TAB_GAP_DP = 3
private const val TOPIC_REPLY_COMPOSER_EMOJI_PANEL_SEARCH_HEIGHT_DP = 0
private const val TOPIC_REPLY_COMPOSER_EMOJI_PANEL_BOTTOM_CONTROLS_DP = 44
private const val TOPIC_REPLY_COMPOSER_EMOJI_GRID_HORIZONTAL_PADDING_DP = 5
private const val TOPIC_REPLY_COMPOSER_EMOJI_SECTION_SCROLL_OFFSET_DP = -9
private const val TOPIC_REPLY_COMPOSER_EMOJI_HELPER_SCROLL_ROWS = 9
private const val TOPIC_REPLY_COMPOSER_EMOJI_PANEL_COLUMNS = 8
private const val TOPIC_REPLY_COMPOSER_EMOJI_CELL_SIZE_DP = 42
private const val TOPIC_REPLY_COMPOSER_EMOJI_ICON_SIZE_DP = 28
private const val TOPIC_REPLY_COMPOSER_EMOJI_PANEL_MAX_HEIGHT_DP = 360
private const val TOPIC_REPLY_COMPOSER_EMOJI_PANEL_MAX_SCREEN_FRACTION = 0.46f
private val TOPIC_REPLY_COMPOSER_EMOJI_TAB_ICON_RES = intArrayOf(
    TelegramR.drawable.msg_emoji_smiles,
    TelegramR.drawable.msg_emoji_cat,
    TelegramR.drawable.msg_emoji_food,
    TelegramR.drawable.msg_emoji_activities,
    TelegramR.drawable.msg_emoji_travel,
    TelegramR.drawable.msg_emoji_objects,
    TelegramR.drawable.msg_emoji_other,
    TelegramR.drawable.msg_emoji_flags,
)
private val TOPIC_REPLY_COMPOSER_EDIT_TEXT_BOUNDS_INTERPOLATOR by lazy {
    CubicBezierInterpolator(
        0.19919472913616398,
        0.010644531250000006,
        0.27920937042459737,
        0.91025390625,
    )
}

private class TopicReplyComposerPanel(
    context: Context,
    initialDraft: TopicReplyDraft,
    replyToPost: Post?,
    private val onEmojiButtonPressed: (View, View, (String) -> Unit) -> Unit,
    private val onRichEditorPressed: () -> Unit,
    private val onAttachMediaPressed: () -> Unit,
    private val onKeyboardRequested: (View) -> Unit,
    private val onReplyContextPressed: (Int) -> Unit,
    private val onBackKeyPressed: (Int, Int) -> Boolean,
    private val onMentionKeyPressed: (Int, Int) -> Boolean,
    private val onVisibleHeightChanged: () -> Unit,
    private val onTextChanged: (String) -> Unit,
    private val onMentionTokenChanged: (TopicMentionToken?) -> Unit,
    private val onClearReplyTarget: () -> Unit,
    private val onSubmit: () -> Unit,
) : TopicInputIslandFrameLayout(context) {
    private var activeReplyToPostNumber = initialDraft.replyToPostNumber
    private var bindingDraft = false
    private var lastReplyHeaderKey: String? = null
    private var submitting = false
    private var sendButtonPopup: ActionBarPopupWindow? = null
    private var panelAnimator: ValueAnimator? = null
    private var keyboardInsetAnimator: ValueAnimator? = null
    private var inputFieldHeightAnimator: ValueAnimator? = null
    private var editTextBoundsAnimator: ValueAnimator? = null
    private var editTextBoundsPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var shouldAnimateEditTextWithBounds = false
    private var editTextPredrawHeight = 0
    private var editTextPredrawScrollY = 0
    private var editTextPredrawLineCount = 1
    private var dismissed = false
    private var processEmojiChange = false
    private var panelOffsetY = 0f
    private var keyboardInset = 0
    private var keyboardInsetTarget = 0
    private var inputFieldHeightInitialized = false
    private var inputFieldHeightTarget = 0
    private var animatedInputFieldHeight = 0f
    private var replyHeaderShowPending = false
    private var waitingForKeyboardOpen = false
    private var waitingForKeyboardOpenFromPopup = false
    private val showReplyHeaderRunnable = Runnable {
        replyHeaderShowPending = false
        replyHeader.startEnterAnimation()
    }
    private val replyHeader = ReplyContextView(context)
    private val errorView = TextView(context).apply {
        visibility = View.GONE
        textSize = 13f
        setTextColor(Theme.getColor(Theme.key_text_RedRegular))
        setPadding(AndroidUtilities.dp(52f), AndroidUtilities.dp(2f), AndroidUtilities.dp(52f), 0)
    }
    private var editTextReady = false
    /** The markdown the composer last handed to the draft store; `bindDraft` only re-parses when the store changes elsewhere. */
    private var lastReportedDraftMarkdown = initialDraft.raw
    private val editText = object : EditTextCaption(context, null) {
        override fun onSelectionChanged(selStart: Int, selEnd: Int) {
            super.onSelectionChanged(selStart, selEnd)
            if (editTextReady) {
                dispatchMentionToken()
            }
        }

        // The item set and its order mirror upstream's `ChatActivity.fillActionModeMenu`; the
        // actions are upstream's too (`EditTextCaption.performMenuAction` applies style runs),
        // and TopicReplyComposerMarkdown serializes those runs on the way out.
        override fun extendActionMode(actionMode: ActionMode?, menu: Menu) {
            if (selectionStart < 0 || selectionEnd <= selectionStart) return
            if (menu.findItem(TelegramR.id.menu_bold) != null) return
            if (menu.findItem(android.R.id.copy) == null) return
            var order = 6
            menu.add(TelegramR.id.menu_groupbolditalic, TelegramR.id.menu_quote, order++, LocaleController.getString(TelegramR.string.Quote))
            menu.add(TelegramR.id.menu_groupbolditalic, TelegramR.id.menu_spoiler, order++, LocaleController.getString(TelegramR.string.Spoiler))
            menu.add(
                TelegramR.id.menu_groupbolditalic,
                TelegramR.id.menu_bold,
                order++,
                styleTitle(TelegramR.string.Bold, TypefaceSpan(AndroidUtilities.bold())),
            )
            menu.add(
                TelegramR.id.menu_groupbolditalic,
                TelegramR.id.menu_italic,
                order++,
                styleTitle(TelegramR.string.Italic, TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf") ?: Typeface.DEFAULT)),
            )
            menu.add(
                TelegramR.id.menu_groupbolditalic,
                TelegramR.id.menu_mono,
                order++,
                styleTitle(TelegramR.string.Mono, TypefaceSpan(Typeface.MONOSPACE)),
            )
            menu.add(
                TelegramR.id.menu_groupbolditalic,
                TelegramR.id.menu_strike,
                order++,
                styleTitle(TelegramR.string.Strike, styleRunSpan(TextStyleSpan.FLAG_STYLE_STRIKE)),
            )
            menu.add(
                TelegramR.id.menu_groupbolditalic,
                TelegramR.id.menu_underline,
                order++,
                styleTitle(TelegramR.string.Underline, styleRunSpan(TextStyleSpan.FLAG_STYLE_UNDERLINE)),
            )
            menu.add(TelegramR.id.menu_groupbolditalic, TelegramR.id.menu_link, order++, LocaleController.getString(TelegramR.string.CreateLink))
            menu.add(TelegramR.id.menu_groupbolditalic, TelegramR.id.menu_regular, order++, LocaleController.getString(TelegramR.string.Regular))
        }

        private fun styleTitle(stringRes: Int, span: Any): CharSequence {
            val title = SpannableStringBuilder(LocaleController.getString(stringRes))
            title.setSpan(span, 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return title
        }

        private fun styleRunSpan(flag: Int): TextStyleSpan {
            val run = TextStyleSpan.TextStyleRun()
            run.flags = flag
            return TextStyleSpan(run)
        }
    }.apply {
        // Span-only edits (bold, a link, Regular) leave the text untouched, so no TextWatcher
        // runs for them; the delegate is where `EditTextCaption` reports those.
        setDelegate { reportDraftText(text) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setFallbackLineSpacing(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setLocalePreferredLineHeightForMinimumUsed(false)
        }
        setText(TopicReplyComposerMarkdown.parse(initialDraft.raw))
        setSelection(text.length)
        setSingleLine(false)
        maxLines = 6
        minLines = 1
        gravity = Gravity.BOTTOM or Gravity.START
        includeFontPadding = false
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
        setTextColor(Theme.getColor(Theme.key_chat_messagePanelText))
        setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkOut))
        highlightColor = Theme.getColor(Theme.key_chat_inTextSelectionHighlight)
        val hintColor = Theme.getColor(Theme.key_chat_messagePanelHint)
        setHintColor(hintColor)
        setHintTextColor(hintColor)
        setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor))
        setCursorWidth(1.5f)
        setCursorSize(AndroidUtilities.dp(24f))
        setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor))
        hint = context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_hint)
        setPadding(0, AndroidUtilities.dp(9f), 0, AndroidUtilities.dp(10f))
        background = null
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                setWindowView(rootView)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
    private val sendButton = ComposerCircleButton(context, topicReplyComposerSendButtonIconRes(), shown = false).apply {
        setSoundEffectsEnabled(false)
    }
    /**
     * The composer's attach path: while there is nothing to send, the send slot offers media
     * instead (Telegram's empty-input slot). Once there is text the send button takes the slot
     * back, and the same action stays reachable from the send button's long-press menu.
     */
    private val attachButton = ComposerCircleButton(context, TelegramR.drawable.outline_poll_attach_24, shown = true).apply {
        contentDescription = context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_attach)
        setOnClickListener { onAttachMediaPressed() }
    }
    private val emojiButton = ChatActivityEnterViewAnimatedIconView(context).apply {
        val padding = AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_PADDING_DP)
        setPadding(padding, padding, padding, padding)
        setColorFilter(PorterDuffColorFilter(
            Theme.getColor(Theme.key_glass_defaultIcon),
            PorterDuff.Mode.SRC_IN,
        ))
        background = Theme.createInsetRoundRectDrawable(
            Theme.getColor(Theme.key_listSelector),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_RADIUS_DP).toFloat(),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_W_DP),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_H_DP),
        )
        isFocusable = true
        setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false)
        contentDescription = context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_emoji)
        setOnClickListener {
            onEmojiButtonPressed(this, editText, ::insertEmoji)
        }
    }
    /**
     * The composer's way into the block editor, the button upstream's `ChatActivityEnterView`
     * shows for the article editor (`R.string.ArticleEditor`): the composer stays the simple
     * surface, and this opens the full one with the same draft in it.
     */
    private val richEditorButton = ImageView(context).apply {
        val padding = AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_PADDING_DP)
        setPadding(padding, padding, padding, padding)
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(TelegramR.drawable.large_article)
        setColorFilter(PorterDuffColorFilter(
            Theme.getColor(Theme.key_glass_defaultIcon),
            PorterDuff.Mode.SRC_IN,
        ))
        background = Theme.createInsetRoundRectDrawable(
            Theme.getColor(Theme.key_listSelector),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_RADIUS_DP).toFloat(),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_W_DP),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_H_DP),
        )
        isFocusable = true
        contentDescription = LocaleController.getString(TelegramR.string.ArticleEditor)
        setOnClickListener { onRichEditorPressed() }
    }
    private val openKeyboardRunnable: Runnable = Runnable {
        if (visibility != View.VISIBLE) {
            waitingForKeyboardOpen = false
            waitingForKeyboardOpenFromPopup = false
            return@Runnable
        }
        val shouldRetry = if (waitingForKeyboardOpenFromPopup) {
            !dismissed &&
                waitingForKeyboardOpen &&
                topicReplyComposerShouldWaitForKeyboardOpenAfterPopup(
                    usingHardwareInput = AndroidUtilities.usingHardwareInput,
                    isInMultiwindow = AndroidUtilities.isInMultiwindow,
                )
        } else {
            topicReplyComposerShouldRetryOpenKeyboard(
                dismissed = dismissed,
                waitingForKeyboardOpen = waitingForKeyboardOpen,
                keyboardInset = keyboardInset,
                usingHardwareInput = AndroidUtilities.usingHardwareInput,
                isInMultiwindow = AndroidUtilities.isInMultiwindow,
            )
        }
        if (!shouldRetry) {
            waitingForKeyboardOpen = false
            waitingForKeyboardOpenFromPopup = false
            return@Runnable
        }
        topicReplyComposerRequestKeyboard(editText)
        AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
        AndroidUtilities.runOnUIThread(openKeyboardRunnable, topicReplyComposerKeyboardOpenRetryDelayMs())
    }

    init {
        val content = ComposerBackgroundLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false

        val headerSubtitle = replyHeaderSubtitle(initialDraft.raw, replyToPost)
        val hasReplyHeader = activeReplyToPostNumber != null
        if (hasReplyHeader) {
            replyHeader.bind(
                title = replyHeaderTitle(context, replyToPost, activeReplyToPostNumber),
                subtitle = headerSubtitle,
                onClose = ::clearReplyContext,
                canOpen = activeReplyToPostNumber != null,
                onOpen = ::openReplyContext,
            )
        } else {
            replyHeader.visibility = View.GONE
        }
        content.addView(replyHeader, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            if (hasReplyHeader) {
                AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_TOP_VIEW_HEIGHT_DP.toFloat())
            } else {
                LinearLayout.LayoutParams.WRAP_CONTENT
            },
        ))

        content.addView(errorView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))

        val textFieldContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(0, AndroidUtilities.dp(1f), 0, AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_TEXT_FIELD_BOTTOM_PADDING_DP))
        }
        val messageEditTextContainer = object : FrameLayout(context) {
            private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            private val clipGradient = LinearGradient(
                0f,
                0f,
                0f,
                16f,
                intArrayOf(0xffffffff.toInt(), 0x00ffffff),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            private val clipMatrix = Matrix()
            private val fadeRect = RectF()
            private val topGradientAlpha = AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT)
            private val bottomGradientAlpha = AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT)

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                val minHeight = AndroidUtilities.dp(44f)
                if (measuredHeight < minHeight) {
                    setMeasuredDimension(measuredWidth, minHeight)
                }
                updateInputFieldHeight(measuredHeight)
            }

            override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
                if (child !== editText) {
                    return super.drawChild(canvas, child, drawingTime)
                }
                return drawMessageEditText(canvas, drawingTime)
            }

            private fun drawMessageEditText(canvas: Canvas, drawingTime: Long): Boolean {
                val topAlpha = topGradientAlpha.set(if (editText.canScrollVertically(-1)) 1f else 0f)
                val bottomAlpha = bottomGradientAlpha.set(if (editText.canScrollVertically(1)) 1f else 0f)
                if (topAlpha <= 0f && bottomAlpha <= 0f) {
                    return super.drawChild(canvas, editText, drawingTime)
                }
                val layer = canvas.saveLayerAlpha(
                    0f,
                    0f,
                    editText.x + editText.measuredWidth + AndroidUtilities.dp(5f),
                    editText.y + editText.measuredHeight + AndroidUtilities.dp(2f),
                    0xff,
                )
                val result = super.drawChild(canvas, editText, drawingTime)
                if (topAlpha > 0f) {
                    drawEditTextFade(canvas, top = true, alpha = topAlpha)
                }
                if (bottomAlpha > 0f) {
                    drawEditTextFade(canvas, top = false, alpha = bottomAlpha)
                }
                canvas.restoreToCount(layer)
                return result
            }

            private fun drawEditTextFade(canvas: Canvas, top: Boolean, alpha: Float) {
                val gradientHeight = AndroidUtilities.dp(13f).toFloat()
                val left = editText.x - AndroidUtilities.dp(5f)
                val right = editText.x + editText.measuredWidth + AndroidUtilities.dp(5f)
                val topY = if (top) {
                    editText.y - 1f
                } else {
                    editText.y + editText.measuredHeight - gradientHeight - AndroidUtilities.dp(2f)
                }
                val bottomY = if (top) {
                    editText.y + gradientHeight
                } else {
                    editText.y + editText.measuredHeight + AndroidUtilities.dp(3f)
                }
                fadeRect.set(left, topY, right, bottomY)
                clipMatrix.reset()
                clipMatrix.postScale(1f, fadeRect.height() / 16f)
                if (top) {
                    clipMatrix.postTranslate(fadeRect.left, fadeRect.top)
                } else {
                    clipMatrix.postRotate(180f)
                    clipMatrix.postTranslate(fadeRect.left, fadeRect.bottom)
                }
                clipGradient.setLocalMatrix(clipMatrix)
                gradientPaint.shader = clipGradient
                gradientPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
                canvas.drawRect(fadeRect, gradientPaint)
            }
        }.apply {
            clipChildren = false
            setWillNotDraw(false)
        }

        messageEditTextContainer.addView(emojiButton, LayoutHelper.createFrame(
            TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP,
            TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat(),
            Gravity.BOTTOM or Gravity.LEFT,
            2f,
            0f,
            0f,
            0f,
        ))
        messageEditTextContainer.addView(richEditorButton, LayoutHelper.createFrame(
            TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP,
            TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat(),
            Gravity.BOTTOM or Gravity.LEFT,
            TOPIC_REPLY_COMPOSER_RICH_EDITOR_BUTTON_LEFT_MARGIN_DP,
            0f,
            0f,
            0f,
        ))
        messageEditTextContainer.addView(editText, LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT,
            LayoutHelper.WRAP_CONTENT.toFloat(),
            Gravity.BOTTOM,
            TOPIC_REPLY_COMPOSER_EDIT_TEXT_LEFT_MARGIN_DP,
            0f,
            TOPIC_REPLY_COMPOSER_EDIT_TEXT_RIGHT_MARGIN_DP,
            1.5f,
        ))
        textFieldContainer.addView(messageEditTextContainer, LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT,
            LayoutHelper.WRAP_CONTENT.toFloat(),
            Gravity.BOTTOM,
            0f,
            0f,
            TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat(),
            0f,
        ))

        sendButton.contentDescription = context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_send)
        sendButton.setOnClickListener { submit() }
        sendButton.setOnLongClickListener {
            showSendButtonMenu(sendButton)
            true
        }
        val actionContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        actionContainer.addView(sendButton, LayoutHelper.createFrame(
            TOPIC_REPLY_COMPOSER_SEND_BUTTON_WIDTH_DP.toFloat(),
            TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat(),
            Gravity.BOTTOM or Gravity.RIGHT,
        ))
        actionContainer.addView(attachButton, LayoutHelper.createFrame(
            TOPIC_REPLY_COMPOSER_SEND_BUTTON_WIDTH_DP.toFloat(),
            TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat(),
            Gravity.BOTTOM or Gravity.RIGHT,
        ))
        actionContainer.setOnLongClickListener {
            showSendButtonMenu(sendButton)
            true
        }
        textFieldContainer.addView(actionContainer, LayoutHelper.createFrame(
            TOPIC_REPLY_COMPOSER_SEND_BUTTON_WIDTH_DP.toFloat(),
            TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat(),
            Gravity.BOTTOM or Gravity.RIGHT,
        ))
        content.addView(textFieldContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        content.setEnterViewClipTargets(
            topView = replyHeader,
            textFieldContainer = textFieldContainer,
            inputFieldHeightProvider = {
                animatedInputFieldHeight.takeIf { it > 0f }
                    ?: AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat()).toFloat()
            },
        )

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                prepareEditTextBoundsAnimation()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count - before > 1) {
                    processEmojiChange = true
                }
            }
            override fun afterTextChanged(s: Editable?) {
                if (processEmojiChange) {
                    processEmojiChange = false
                    if (s != null) {
                        Emoji.replaceEmoji(s, editText.paint.fontMetricsInt, false, null as IntArray?)
                    }
                }
                if (errorView.visibility == View.VISIBLE) {
                    setError(null)
                }
                if (!bindingDraft) {
                    reportDraftText(s ?: return)
                }
                dispatchMentionToken()
                updateSendButtonState()
            }
        })
        editText.setOnKeyListener { _, keyCode, event ->
            if (event != null && onBackKeyPressed(keyCode, event.action)) {
                return@setOnKeyListener true
            }
            if (event != null && onMentionKeyPressed(keyCode, event.action)) {
                return@setOnKeyListener true
            }
            if (event != null && topicReplyComposerShouldSubmitFromKey(
                    keyCode = keyCode,
                    action = event.action,
                    shiftPressed = event.isShiftPressed,
                    ctrlPressed = event.isCtrlPressed,
                )
            ) {
                submit()
                true
            } else {
                false
            }
        }
        editText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                event != null && actionId == EditorInfo.IME_NULL && topicReplyComposerShouldSubmitFromKey(
                    keyCode = event.keyCode,
                    action = event.action,
                    shiftPressed = event.isShiftPressed,
                    ctrlPressed = event.isCtrlPressed,
                )
            ) {
                submit()
                true
            } else {
                false
            }
        }
        editText.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                editText.setHandlesColor(Theme.getColor(Theme.key_chat_TextSelectionCursor))
                onKeyboardRequested(editText)
            }
            false
        }
        editTextReady = true
        updateSendButtonState()

        addView(
            content,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.BOTTOM,
                TOPIC_INPUT_ISLAND_HORIZONTAL_INSET_DP,
                0f,
                TOPIC_INPUT_ISLAND_HORIZONTAL_INSET_DP,
                0f,
            ),
        )
    }

    fun focusAndOpenKeyboard() {
        panelAnimator?.cancel()
        panelAnimator = null
        alpha = 1f
        panelOffsetY = 0f
        updatePanelTranslation()
        AndroidUtilities.cancelRunOnUIThread(showReplyHeaderRunnable)
        val delayMs = if (topicReplyComposerShouldDelayTopViewForKeyboard(
                keyboardInset = keyboardInset,
                emojiPopupShowing = false,
            ) && activeReplyToPostNumber != null
        ) topicReplyComposerTopViewShowDelayMs() else 0L
        if (delayMs > 0L) {
            replyHeaderShowPending = true
            AndroidUtilities.runOnUIThread(showReplyHeaderRunnable, delayMs)
        } else {
            replyHeaderShowPending = false
            showReplyHeaderRunnable.run()
        }
        editText.post {
            requestKeyboardWithRetry()
        }
    }

    /** Hands the composer's markdown to the draft store; the store is the payload, so this is what a reply carries. */
    private fun reportDraftText(editable: Editable) {
        val markdown = TopicReplyComposerMarkdown.serialize(editable)
        if (markdown == lastReportedDraftMarkdown) {
            return
        }
        lastReportedDraftMarkdown = markdown
        onTextChanged(markdown)
    }

    fun bindDraft(draft: TopicReplyDraft, replyToPost: Post?) {
        val hadReplyTarget = activeReplyToPostNumber != null && replyHeader.visibility == View.VISIBLE
        activeReplyToPostNumber = draft.replyToPostNumber
        if (draft.raw != lastReportedDraftMarkdown) {
            bindingDraft = true
            val styled = TopicReplyComposerMarkdown.parse(draft.raw)
            editText.setText(styled)
            editText.setSelection(styled.length)
            bindingDraft = false
            lastReportedDraftMarkdown = draft.raw
        }
        if (draft.replyToPostNumber != null) {
            val title = replyHeaderTitle(context, replyToPost, draft.replyToPostNumber)
            val subtitle = replyHeaderSubtitle(draft.raw, replyToPost)
            val headerKey = "${draft.replyToPostNumber}|$title|$subtitle"
            if (lastReplyHeaderKey != headerKey) {
                replyHeader.bind(
                    title = title,
                    subtitle = subtitle,
                    onClose = ::clearReplyContext,
                    canOpen = true,
                    onOpen = ::openReplyContext,
                )
                lastReplyHeaderKey = headerKey
            }
            if (!hadReplyTarget) {
                replyHeader.prepareEnterAnimation()
                replyHeader.startEnterAnimation()
            }
        } else if (replyHeader.visibility == View.VISIBLE) {
            lastReplyHeaderKey = null
            replyHeader.dismiss(animated = isAttachedToWindow)
        }
        dispatchMentionToken()
        updateSendButtonState()
    }

    fun replaceMention(token: TopicMentionToken, username: String) {
        if (submitting) return
        val editable = editText.text ?: return
        val replacement = topicMentionReplacement(editable, token, username) ?: return
        val insertion = replacement.text.substring(token.start, replacement.cursor)
        editable.replace(token.start, token.end, insertion)
        editText.setSelection(replacement.cursor.coerceAtMost(editable.length))
        editText.requestFocus()
        dispatchMentionToken()
    }

    fun refreshMentionSuggestions() {
        dispatchMentionToken()
    }

    private fun dispatchMentionToken() {
        val editable = editText.text
        val cursor = editText.selectionStart
        val selectionEnd = editText.selectionEnd
        onMentionTokenChanged(
            if (editable == null || cursor < 0 || cursor != selectionEnd) null else topicMentionTokenAt(editable, cursor),
        )
    }

    fun requestKeyboardWithRetry(forceWaitForKeyboard: Boolean = false) {
        topicReplyComposerRequestKeyboard(editText)
        AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
        val shouldWaitForKeyboard = if (forceWaitForKeyboard) {
            topicReplyComposerShouldWaitForKeyboardOpenAfterPopup(
                usingHardwareInput = AndroidUtilities.usingHardwareInput,
                isInMultiwindow = AndroidUtilities.isInMultiwindow,
            )
        } else {
            topicReplyComposerShouldWaitForKeyboardOpen(
                keyboardInset = keyboardInset,
                usingHardwareInput = AndroidUtilities.usingHardwareInput,
                isInMultiwindow = AndroidUtilities.isInMultiwindow,
            )
        }
        if (!shouldWaitForKeyboard) {
            waitingForKeyboardOpen = false
            waitingForKeyboardOpenFromPopup = false
            return
        }
        waitingForKeyboardOpen = true
        waitingForKeyboardOpenFromPopup = forceWaitForKeyboard
        AndroidUtilities.runOnUIThread(openKeyboardRunnable, topicReplyComposerKeyboardOpenRetryDelayMs())
    }

    fun closeKeyboard() {
        cancelKeyboardRequest()
        AndroidUtilities.hideKeyboard(editText)
        editText.clearFocus()
    }

    fun cancelKeyboardRequest() {
        AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
        waitingForKeyboardOpen = false
        waitingForKeyboardOpenFromPopup = false
    }

    fun dismiss(animated: Boolean = true) {
        if (dismissed) return
        dismissed = true
        sendButtonPopup?.dismiss()
        sendButtonPopup = null
        keyboardInsetAnimator?.cancel()
        keyboardInsetAnimator = null
        inputFieldHeightAnimator?.cancel()
        inputFieldHeightAnimator = null
        clearEditTextBoundsAnimation(resetOffset = true)
        AndroidUtilities.cancelRunOnUIThread(showReplyHeaderRunnable)
        replyHeaderShowPending = false
        AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
        waitingForKeyboardOpen = false
        waitingForKeyboardOpenFromPopup = false
        AndroidUtilities.hideKeyboard(editText)
        val parentView = parent as? ViewGroup
        val remove: () -> Unit = {
            parentView?.removeView(this)
            Unit
        }
        if (animated && isAttachedToWindow) {
            animatePanel(
                targetOffsetY = AndroidUtilities.dp(16f).toFloat(),
                targetAlpha = 0f,
                duration = 160L,
                interpolator = CubicBezierInterpolator.DEFAULT,
                onEnd = remove,
            )
        } else {
            panelAnimator?.cancel()
            panelAnimator = null
            remove()
        }
    }

    override fun onDetachedFromWindow() {
        onMentionTokenChanged(null)
        sendButtonPopup?.dismiss()
        sendButtonPopup = null
        keyboardInsetAnimator?.cancel()
        keyboardInsetAnimator = null
        inputFieldHeightAnimator?.cancel()
        inputFieldHeightAnimator = null
        clearEditTextBoundsAnimation(resetOffset = true)
        AndroidUtilities.cancelRunOnUIThread(showReplyHeaderRunnable)
        replyHeaderShowPending = false
        AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
        waitingForKeyboardOpen = false
        waitingForKeyboardOpenFromPopup = false
        super.onDetachedFromWindow()
    }

    fun release() {
        dismiss(animated = false)
    }

    fun onKeyboardVisible() {
        if (!topicReplyComposerShouldFlushDelayedTopView(
                keyboardVisible = true,
                showTopViewPending = replyHeaderShowPending,
            )
        ) {
            return
        }
        AndroidUtilities.cancelRunOnUIThread(showReplyHeaderRunnable)
        showReplyHeaderRunnable.run()
    }

    fun onAdjustPanTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {
        if (keyboardVisible) {
            onKeyboardVisible()
        }
    }

    fun onAdjustPanTransitionUpdate(y: Float, progress: Float, keyboardVisible: Boolean) {
        // Kept as the upstream-shaped host hook. The current NativeDo composer
        // has no bot-webview child that needs a per-frame counter-translation.
    }

    fun onAdjustPanTransitionEnd() {
        // Insets own the final keyboard state; AdjustPan only bridges the resize.
    }

    fun setKeyboardInset(inset: Int) {
        val nextInset = topicReplyComposerPanelKeyboardInsetPx(inset)
        if (nextInset > 0) {
            waitingForKeyboardOpen = false
            waitingForKeyboardOpenFromPopup = false
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
        }
        if (keyboardInsetTarget == nextInset && keyboardInset == nextInset) {
            return
        }
        keyboardInsetTarget = nextInset
        keyboardInsetAnimator?.cancel()
        keyboardInsetAnimator = null
        // Island lift is owned by ChatInputViewsContainer.checkInsets(). Keep keyboard
        // inset as state for emoji/retry only — do not translate the panel by IME.
        keyboardInset = nextInset
    }

    fun isKeyboardAnimationInProgress(): Boolean {
        return keyboardInsetAnimator != null
    }

    fun visibleHeightForListPadding(): Int {
        val panelHeight = height.takeIf { it > 0 } ?: measuredHeight
        return topicReplyComposerVisiblePanelHeightForListPadding(
            panelHeight = panelHeight,
            animatedInputFieldHeight = animatedInputFieldHeight,
            targetInputFieldHeight = inputFieldHeightTarget,
        )
    }

    fun bottomInsetForListPadding(targetInset: Int): Int {
        return topicReplyComposerPanelBottomInsetForListPadding(
            targetInset = targetInset,
            animatedInset = keyboardInset,
            animationInProgress = keyboardInsetAnimator != null,
        )
    }

    fun setEmojiPopupShowing(showing: Boolean) {
        if (showing) {
            waitingForKeyboardOpen = false
            waitingForKeyboardOpenFromPopup = false
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
        }
        emojiButton.setState(
            if (showing) ChatActivityEnterViewAnimatedIconView.State.KEYBOARD else ChatActivityEnterViewAnimatedIconView.State.SMILE,
            true,
        )
    }

    fun setSubmitting(submitting: Boolean) {
        this.submitting = submitting
        if (submitting) {
            onMentionTokenChanged(null)
        }
        sendButton.busy = submitting
        updateSendButtonState()
    }

    fun setError(message: String?) {
        errorView.text = message.orEmpty()
        errorView.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun showSendButtonMenu(anchor: View) {
        if (submitting || editText.text?.toString()?.trim().isNullOrEmpty()) {
            return
        }
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        sendButtonPopup?.dismiss()
        val popupLayout = ActionBarPopupWindow.ActionBarPopupWindowLayout(
            context,
            TelegramR.drawable.popup_fixed_alert4,
            null,
        ).apply {
            setMinimumWidth(AndroidUtilities.dp(184f))
            setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground))
            setFitItems(true)
        }
        // The attach action lives in the send slot only while the reply is empty; once there is
        // text the slot belongs to send, so the same action is offered here.
        val attachItem = ActionBarMenuSubItem(context, true, false, null).apply {
            setMinimumWidth(AndroidUtilities.dp(184f))
            setColors(
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItem),
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon),
            )
            setSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector))
            setTextAndIcon(
                context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_attach),
                TelegramR.drawable.outline_poll_attach_24,
            )
            setOnClickListener {
                sendButtonPopup?.dismiss()
                sendButtonPopup = null
                onAttachMediaPressed()
            }
        }
        val sendItem = ActionBarMenuSubItem(context, false, true, null).apply {
            setMinimumWidth(AndroidUtilities.dp(184f))
            setColors(
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItem),
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon),
            )
            setSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector))
            setTextAndIcon(context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_send), topicReplyComposerSendButtonIconRes())
            setOnClickListener {
                sendButtonPopup?.dismiss()
                sendButtonPopup = null
                submit()
            }
        }
        popupLayout.addView(attachItem)
        popupLayout.addView(sendItem)
        popupLayout.measure(
            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(220f), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(160f), MeasureSpec.AT_MOST),
        )
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val popup = ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT).apply {
            setPauseNotifications(true)
            setDismissAnimationDuration(180)
            isOutsideTouchable = true
            isClippingEnabled = true
            isFocusable = true
            animationStyle = TelegramR.style.PopupContextAnimation
            inputMethodMode = ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        sendButtonPopup = popup
        val x = (location[0] + anchor.width - popupLayout.measuredWidth).coerceAtLeast(AndroidUtilities.dp(4f))
        val y = (location[1] - popupLayout.measuredHeight - AndroidUtilities.dp(6f)).coerceAtLeast(AndroidUtilities.dp(4f))
        popup.showAtLocation(anchor, Gravity.LEFT or Gravity.TOP, x, y)
    }

    private fun submit() {
        if (submitting) {
            return
        }
        val raw = editText.text?.toString()?.trim().orEmpty()
        if (raw.isBlank()) {
            setError(null)
            topicReplyComposerRequestKeyboard(editText)
            return
        }
        setError(null)
        setSubmitting(true)
        onSubmit()
    }

    private fun insertEmoji(reactionId: String) {
        if (submitting) return
        val editable = editText.text ?: return
        val selectionStart = editText.selectionStart.takeIf { it >= 0 } ?: editable.length
        val selectionEnd = editText.selectionEnd.takeIf { it >= 0 } ?: selectionStart
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
        val insertion = topicReplyComposerEmojiInsertionText(editable, start, end, reactionId)
        if (insertion.isBlank()) return
        editable.replace(start, end, insertion)
        editText.setSelection((start + insertion.length).coerceAtMost(editable.length))
        editText.requestFocus()
        updateSendButtonState()
    }

    private fun prepareEditTextBoundsAnimation() {
        if (!editText.isLaidOut || editText.measuredHeight <= 0) {
            return
        }
        if (!shouldAnimateEditTextWithBounds) {
            shouldAnimateEditTextWithBounds = true
            editTextPredrawHeight = editText.measuredHeight
            editTextPredrawScrollY = editText.scrollY
            editTextPredrawLineCount = topicReplyComposerEditTextLineCount(editText.lineCount)
        }
        if (editTextBoundsPreDrawListener == null) {
            val listener = ViewTreeObserver.OnPreDrawListener {
                runEditTextBoundsAnimationIfNeeded()
                true
            }
            editTextBoundsPreDrawListener = listener
            editText.viewTreeObserver.addOnPreDrawListener(listener)
        }
    }

    private fun runEditTextBoundsAnimationIfNeeded() {
        removeEditTextBoundsPreDrawListener()
        if (!shouldAnimateEditTextWithBounds) {
            return
        }
        shouldAnimateEditTextWithBounds = false
        val newHeight = editText.measuredHeight
        val newScrollY = editText.scrollY
        val newLineCount = topicReplyComposerEditTextLineCount(editText.lineCount)
        if (!topicReplyComposerShouldAnimateEditTextBounds(
                oldLineCount = editTextPredrawLineCount,
                newLineCount = newLineCount,
                oldHeight = editTextPredrawHeight,
                newHeight = newHeight,
                oldScrollY = editTextPredrawScrollY,
                newScrollY = newScrollY,
            )
        ) {
            return
        }
        val delta = topicReplyComposerEditTextOffsetDelta(
            oldHeight = editTextPredrawHeight,
            newHeight = newHeight,
            oldScrollY = editTextPredrawScrollY,
            newScrollY = newScrollY,
        )
        if (delta == 0f) {
            return
        }
        editTextBoundsAnimator?.cancel()
        editText.setOffsetY(editText.getOffsetY() - delta)
        if (!isAttachedToWindow) {
            editText.setOffsetY(0f)
            return
        }
        val animator = ValueAnimator.ofFloat(editText.getOffsetY(), 0f).apply {
            duration = topicReplyComposerEditTextBoundsAnimationDurationMs()
            interpolator = TOPIC_REPLY_COMPOSER_EDIT_TEXT_BOUNDS_INTERPOLATOR
            addUpdateListener { animation ->
                editText.setOffsetY(animation.animatedValue as Float)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (editTextBoundsAnimator === animation) {
                        editTextBoundsAnimator = null
                        editText.setOffsetY(0f)
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (editTextBoundsAnimator === animation) {
                        editTextBoundsAnimator = null
                    }
                }
            })
        }
        editTextBoundsAnimator = animator
        animator.start()
    }

    private fun clearEditTextBoundsAnimation(resetOffset: Boolean) {
        removeEditTextBoundsPreDrawListener()
        editTextBoundsAnimator?.cancel()
        editTextBoundsAnimator = null
        shouldAnimateEditTextWithBounds = false
        if (resetOffset) {
            editText.setOffsetY(0f)
        }
    }

    private fun removeEditTextBoundsPreDrawListener() {
        val listener = editTextBoundsPreDrawListener ?: return
        val observer = editText.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnPreDrawListener(listener)
        }
        editTextBoundsPreDrawListener = null
    }

    private fun updateInputFieldHeight(height: Int) {
        val nextTarget = height.coerceAtLeast(AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat()))
        if (!inputFieldHeightInitialized) {
            inputFieldHeightInitialized = true
            inputFieldHeightTarget = nextTarget
            animatedInputFieldHeight = nextTarget.toFloat()
            return
        }
        if (inputFieldHeightTarget == nextTarget) {
            return
        }
        inputFieldHeightTarget = nextTarget
        inputFieldHeightAnimator?.cancel()
        inputFieldHeightAnimator = null
        if (!isAttachedToWindow || dismissed) {
            animatedInputFieldHeight = nextTarget.toFloat()
            onVisibleHeightChanged()
            return
        }
        val fromHeight = animatedInputFieldHeight.takeIf { it > 0f } ?: nextTarget.toFloat()
        val animator = ValueAnimator.ofFloat(fromHeight, nextTarget.toFloat()).apply {
            duration = topicReplyComposerEditTextBoundsAnimationDurationMs()
            interpolator = TOPIC_REPLY_COMPOSER_EDIT_TEXT_BOUNDS_INTERPOLATOR
            addUpdateListener { animation ->
                animatedInputFieldHeight = animation.animatedValue as Float
                onVisibleHeightChanged()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (inputFieldHeightAnimator === animation) {
                        inputFieldHeightAnimator = null
                        animatedInputFieldHeight = nextTarget.toFloat()
                        onVisibleHeightChanged()
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (inputFieldHeightAnimator === animation) {
                        inputFieldHeightAnimator = null
                    }
                }
            })
        }
        inputFieldHeightAnimator = animator
        animator.start()
    }

    private fun animatePanel(
        targetOffsetY: Float,
        targetAlpha: Float,
        duration: Long,
        interpolator: android.animation.TimeInterpolator,
        onEnd: (() -> Unit)? = null,
    ) {
        panelAnimator?.cancel()
        val fromOffsetY = panelOffsetY
        val fromAlpha = alpha
        var cancelled = false
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                panelOffsetY = lerpFloat(fromOffsetY, targetOffsetY, t)
                alpha = lerpFloat(fromAlpha, targetAlpha, t)
                updatePanelTranslation()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (panelAnimator === animation) {
                        cancelled = true
                        panelAnimator = null
                    }
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled && panelAnimator === animation) {
                        panelAnimator = null
                        onEnd?.invoke()
                    }
                }
            })
        }
        panelAnimator = animator
        animator.start()
    }

    private fun updatePanelTranslation() {
        translationY = panelOffsetY
    }

    private fun updateSendButtonState() {
        val hasText = editText.text?.toString()?.trim()?.isNotEmpty() == true
        val state = topicReplyComposerSendButtonState(hasText = hasText, isSubmitting = submitting)
        sendButton.isEnabled = state.enabled
        setSendButtonVisible(state.visible, animated = sendButton.isAttachedToWindow)
    }

    /** The attach slot spins (and ignores taps) while a picked file is on its way up. */
    fun setAttachUploading(uploading: Boolean) {
        attachButton.busy = uploading
        attachButton.isEnabled = !uploading
    }

    /** Puts an uploaded file into the draft at the caret, the way the web composer does. */
    fun insertMediaMarkdown(alt: String, url: String) {
        if (submitting) return
        val markup = topicReplyComposerMediaMarkup(alt = alt, url = url)
        if (markup.isEmpty()) return
        val editable = editText.text ?: return
        val selectionStart = editText.selectionStart.takeIf { it >= 0 } ?: editable.length
        val selectionEnd = editText.selectionEnd.takeIf { it >= 0 } ?: selectionStart
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
        val insertion = topicReplyComposerMediaInsertionText(editable, start, end, markup)
        if (insertion.isEmpty()) return
        editable.replace(start, end, insertion)
        editText.setSelection((start + insertion.length).coerceAtMost(editable.length))
        editText.requestFocus()
        updateSendButtonState()
    }

    private fun setSendButtonVisible(visible: Boolean, animated: Boolean) {
        sendButton.setShown(visible, animated)
        // the two share the slot: exactly one of them is on screen
        attachButton.setShown(!visible, animated)
    }

    private fun clearReplyContext() {
        onClearReplyTarget()
        activeReplyToPostNumber = null
        lastReplyHeaderKey = null
        replyHeader.dismiss(animated = true)
    }

    private fun openReplyContext(): Boolean {
        val postNumber = activeReplyToPostNumber ?: return false
        onReplyContextPressed(postNumber)
        return true
    }

}

private enum class TopicReplyComposerPopupContentType {
    NONE,
    EMOJI_KEYBOARD,
}

internal enum class TopicReplyComposerEmojiButtonAction {
    SHOW_EMOJI_POPUP,
    HIDE_POPUP_AND_OPEN_KEYBOARD,
}

internal fun topicReplyComposerEmojiButtonAction(isEmojiPopupShowing: Boolean): TopicReplyComposerEmojiButtonAction {
    return if (isEmojiPopupShowing) {
        TopicReplyComposerEmojiButtonAction.HIDE_POPUP_AND_OPEN_KEYBOARD
    } else {
        TopicReplyComposerEmojiButtonAction.SHOW_EMOJI_POPUP
    }
}

internal fun topicReplyComposerEmojiIconKind(isEmojiPopupShowing: Boolean): ComposerIconKind {
    return if (isEmojiPopupShowing) ComposerIconKind.Keyboard else ComposerIconKind.Smile
}

internal fun topicReplyComposerDefaultHeightDp(): Int = TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP

internal fun topicReplyComposerSendButtonWidthDp(): Int = TOPIC_REPLY_COMPOSER_SEND_BUTTON_WIDTH_DP


internal fun topicReplyComposerPanelHorizontalInsetDp(): Float = TOPIC_REPLY_COMPOSER_PANEL_HORIZONTAL_INSET_DP

internal fun topicReplyComposerPanelBottomInsetDp(): Float = TOPIC_REPLY_COMPOSER_PANEL_BOTTOM_INSET_DP

internal fun topicInputIslandBottomGapDp(): Float = TOPIC_INPUT_ISLAND_BOTTOM_GAP_DP

internal fun topicReplyComposerPanelRadiusDp(): Float = TOPIC_REPLY_COMPOSER_PANEL_RADIUS_DP

internal fun topicReplyComposerSendButtonIconRes(): Int = TelegramR.drawable.send_plane_24

internal fun topicReplyComposerSendButtonLoadingRadiusDp(): Float = TOPIC_REPLY_COMPOSER_SEND_BUTTON_LOADING_RADIUS_DP

internal fun topicReplyComposerSendButtonShouldHandleDown(
    x: Float,
    y: Float,
    viewWidth: Int,
    viewHeight: Int,
    activeWidth: Int,
    activeHeight: Int,
): Boolean {
    if (!x.isFinite() || !y.isFinite() || viewWidth <= 0 || viewHeight <= 0) {
        return false
    }
    val width = activeWidth.coerceIn(0, viewWidth)
    val height = activeHeight.coerceIn(0, viewHeight)
    if (width <= 0 || height <= 0) {
        return false
    }
    return x >= viewWidth - width && y >= viewHeight - height
}

internal fun topicReplyComposerInitialSendButtonVisibility(): Int = View.INVISIBLE

internal fun topicReplyComposerIconButtonPaddingDp(): Float = TOPIC_REPLY_COMPOSER_ICON_BUTTON_PADDING_DP

internal fun topicReplyComposerIconButtonSelectorRadiusDp(): Float = TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_RADIUS_DP

internal fun topicReplyComposerIconButtonSelectorInsetWDp(): Float = TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_W_DP

internal fun topicReplyComposerIconButtonSelectorInsetHDp(): Float = TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_H_DP

internal fun topicReplyComposerTextFieldBottomPaddingDp(): Float = TOPIC_REPLY_COMPOSER_TEXT_FIELD_BOTTOM_PADDING_DP

internal fun topicReplyComposerEditTextLeftMarginDp(): Float = TOPIC_REPLY_COMPOSER_EDIT_TEXT_LEFT_MARGIN_DP

internal fun topicReplyComposerRichEditorButtonLeftMarginDp(): Float = TOPIC_REPLY_COMPOSER_RICH_EDITOR_BUTTON_LEFT_MARGIN_DP

internal fun topicReplyComposerEditTextRightMarginDp(): Float = TOPIC_REPLY_COMPOSER_EDIT_TEXT_RIGHT_MARGIN_DP

internal fun topicReplyComposerEnterViewSeparatorY(containerHeight: Int, inputFieldHeight: Float): Float {
    if (containerHeight <= 0) {
        return 0f
    }
    val inputHeight = inputFieldHeight.takeIf { it > 0f }
        ?: AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat()).toFloat()
    return (containerHeight - inputHeight).coerceIn(0f, containerHeight.toFloat())
}

internal fun topicReplyComposerTopViewHeightDp(): Int = TOPIC_REPLY_COMPOSER_TOP_VIEW_HEIGHT_DP

internal fun topicReplyComposerReplyHeaderHeightDp(): Int = TOPIC_REPLY_COMPOSER_REPLY_HEADER_HEIGHT_DP

internal fun topicReplyComposerIconSwitchDurationMs(): Long = TOPIC_REPLY_COMPOSER_ICON_SWITCH_DURATION_MS

internal fun topicReplyComposerTopViewShowDelayMs(): Long = TOPIC_REPLY_COMPOSER_TOP_VIEW_SHOW_DELAY_MS

internal fun topicReplyComposerKeyboardOpenRetryDelayMs(): Long = TOPIC_REPLY_COMPOSER_KEYBOARD_OPEN_RETRY_DELAY_MS

internal fun topicReplyComposerKeyboardOpenAfterEmojiWaitMs(): Long = TOPIC_REPLY_COMPOSER_KEYBOARD_OPEN_AFTER_EMOJI_WAIT_MS

internal fun topicReplyComposerEmojiPanelAnimationStartDelayMs(): Long = TOPIC_REPLY_COMPOSER_EMOJI_PANEL_ANIMATION_START_DELAY_MS

internal fun topicReplyComposerRememberedKeyboardHeight(context: Context, fallbackHeight: Int = AndroidUtilities.dp(200f)): Int {
    val prefs = context.getSharedPreferences(TOPIC_REPLY_COMPOSER_KEYBOARD_PREFS, Context.MODE_PRIVATE)
    val key = topicReplyComposerKeyboardHeightPreferenceKey()
    val fallback = fallbackHeight.takeIf { it > 0 } ?: AndroidUtilities.dp(200f)
    return prefs.getInt(key, fallback).coerceAtLeast(AndroidUtilities.dp(120f))
}

internal fun topicReplyComposerRememberKeyboardHeight(context: Context, keyboardHeight: Int) {
    val height = topicReplyComposerKeyboardInsetPx(keyboardHeight)
    if (height <= 0) {
        return
    }
    context.getSharedPreferences(TOPIC_REPLY_COMPOSER_KEYBOARD_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(topicReplyComposerKeyboardHeightPreferenceKey(), height)
        .apply()
}

internal fun topicReplyComposerKeyboardHeightPreferenceKey(): String {
    return if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
        TOPIC_REPLY_COMPOSER_KEYBOARD_HEIGHT_LAND_KEY
    } else {
        TOPIC_REPLY_COMPOSER_KEYBOARD_HEIGHT_KEY
    }
}

internal fun topicReplyComposerEmojiPanelTabsHeightDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_PANEL_TABS_HEIGHT_DP

internal fun topicReplyComposerEmojiTabButtonSizeDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_TAB_BUTTON_SIZE_DP

internal fun topicReplyComposerEmojiTabSelectorSizeDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_TAB_SELECTOR_SIZE_DP

internal fun topicReplyComposerEmojiTabGapDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_TAB_GAP_DP

internal fun topicReplyComposerEmojiPanelSearchHeightDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_PANEL_SEARCH_HEIGHT_DP

internal fun topicReplyComposerEmojiPanelBottomControlsDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_PANEL_BOTTOM_CONTROLS_DP

internal fun topicReplyComposerEmojiGridHorizontalPaddingDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_GRID_HORIZONTAL_PADDING_DP

internal fun topicReplyComposerEmojiPanelColumns(): Int = TOPIC_REPLY_COMPOSER_EMOJI_PANEL_COLUMNS

internal fun topicReplyComposerEmojiCellSizeDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_CELL_SIZE_DP

internal fun topicReplyComposerEmojiIconSizeDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_ICON_SIZE_DP

internal fun topicReplyComposerEmojiPanelMaxHeightDp(): Int = TOPIC_REPLY_COMPOSER_EMOJI_PANEL_MAX_HEIGHT_DP

internal fun topicReplyComposerEmojiTabIconRes(index: Int): Int {
    val safeIndex = index.coerceIn(0, TOPIC_REPLY_COMPOSER_EMOJI_TAB_ICON_RES.lastIndex)
    return TOPIC_REPLY_COMPOSER_EMOJI_TAB_ICON_RES[safeIndex]
}

internal fun topicReplyComposerEmojiCategoryIconUrl(baseUrl: String, category: DiscourseEmojiCategory): String? {
    val representative = category.emojis.firstOrNull { it.url.isNotBlank() } ?: return null
    return resolvePostImageUrl(baseUrl, representative.url)
}

internal fun topicReplyComposerTopViewEnterDurationMs(): Long = TOPIC_REPLY_COMPOSER_TOP_VIEW_ENTER_DURATION_MS

internal fun topicReplyComposerTopViewHideDurationMs(): Long = TOPIC_REPLY_COMPOSER_TOP_VIEW_HIDE_DURATION_MS

internal fun topicReplyComposerEditTextBoundsAnimationDurationMs(): Long = TOPIC_REPLY_COMPOSER_EDIT_TEXT_BOUNDS_ANIMATION_DURATION_MS

internal fun topicReplyComposerSendButtonLoadingRepeatDelayMs(): Long = TOPIC_REPLY_COMPOSER_SEND_BUTTON_LOADING_REPEAT_DELAY_MS

internal fun topicReplyComposerShouldDelayTopViewForKeyboard(keyboardInset: Int, emojiPopupShowing: Boolean): Boolean {
    return topicReplyComposerKeyboardInsetPx(keyboardInset) == 0 && !emojiPopupShowing
}

internal fun topicReplyComposerShouldIgnoreEmojiButton(
    keyboardAnimationInProgress: Boolean,
    emojiPanelAnimationInProgress: Boolean = false,
    waitingForKeyboardOpenAfterAnimation: Boolean,
): Boolean {
    return keyboardAnimationInProgress || emojiPanelAnimationInProgress || waitingForKeyboardOpenAfterAnimation
}

internal fun topicReplyComposerShouldHideEmojiPanelForKeyboard(
    keyboardVisible: Boolean,
    emojiPanelShowing: Boolean,
    waitingForKeyboardOpenAfterEmoji: Boolean,
): Boolean {
    return keyboardVisible && emojiPanelShowing && waitingForKeyboardOpenAfterEmoji
}

internal fun topicReplyComposerShouldFlushDelayedTopView(
    keyboardVisible: Boolean,
    showTopViewPending: Boolean,
): Boolean {
    return keyboardVisible && showTopViewPending
}

internal fun topicReplyComposerShouldWaitForKeyboardOpen(
    keyboardInset: Int,
    usingHardwareInput: Boolean,
    isInMultiwindow: Boolean,
): Boolean {
    return topicReplyComposerKeyboardInsetPx(keyboardInset) == 0 &&
        !usingHardwareInput &&
        !isInMultiwindow
}

internal fun topicReplyComposerShouldWaitForKeyboardOpenAfterPopup(
    usingHardwareInput: Boolean,
    isInMultiwindow: Boolean,
): Boolean {
    return !usingHardwareInput && !isInMultiwindow
}

internal fun topicReplyComposerShouldRetryOpenKeyboard(
    dismissed: Boolean,
    waitingForKeyboardOpen: Boolean,
    keyboardInset: Int,
    usingHardwareInput: Boolean,
    isInMultiwindow: Boolean,
): Boolean {
    return !dismissed &&
        waitingForKeyboardOpen &&
        topicReplyComposerShouldWaitForKeyboardOpen(
            keyboardInset = keyboardInset,
            usingHardwareInput = usingHardwareInput,
            isInMultiwindow = isInMultiwindow,
        )
}

internal fun topicReplyComposerShouldHideEmojiPanelFromEditTextBack(
    keyCode: Int,
    action: Int,
    keyboardVisible: Boolean,
    emojiPanelShowing: Boolean,
): Boolean {
    return keyCode == KeyEvent.KEYCODE_BACK &&
        action == KeyEvent.ACTION_UP &&
        !keyboardVisible &&
        emojiPanelShowing
}

internal fun topicReplyComposerShouldCloseKeyboardFromEditTextBack(
    keyCode: Int,
    action: Int,
    keyboardVisible: Boolean,
    emojiPanelShowing: Boolean,
): Boolean {
    return keyCode == KeyEvent.KEYCODE_BACK &&
        action == KeyEvent.ACTION_UP &&
        keyboardVisible &&
        !emojiPanelShowing
}

internal fun topicReplyComposerTopViewTranslationY(
    headerHeightPx: Int,
    visibilityProgress: Float,
): Float {
    return headerHeightPx * (1f - visibilityProgress.coerceIn(0f, 1f))
}

internal fun topicReplyComposerRequestKeyboard(target: View): Boolean {
    target.requestFocus()
    if (AndroidUtilities.showKeyboard(target)) {
        return true
    }
    target.clearFocus()
    target.requestFocus()
    return AndroidUtilities.showKeyboard(target)
}

internal fun topicReplyComposerEditTextLineCount(lineCount: Int): Int = lineCount.coerceAtLeast(1)

internal fun topicReplyComposerEditTextOffsetDelta(
    oldHeight: Int,
    newHeight: Int,
    oldScrollY: Int,
    newScrollY: Int,
): Float {
    return ((oldHeight - newHeight) + (oldScrollY - newScrollY)).toFloat()
}

internal fun topicReplyComposerShouldAnimateEditTextBounds(
    oldLineCount: Int,
    newLineCount: Int,
    oldHeight: Int,
    newHeight: Int,
    oldScrollY: Int,
    newScrollY: Int,
): Boolean {
    return topicReplyComposerEditTextLineCount(oldLineCount) != topicReplyComposerEditTextLineCount(newLineCount) &&
        (oldHeight != newHeight || oldScrollY != newScrollY)
}

internal fun topicReplyComposerKeyboardInsetPx(keyboardHeight: Int): Int {
    val keyboardOnlyHeight = (keyboardHeight - AndroidUtilities.navigationBarHeight).coerceAtLeast(0)
    return if (keyboardOnlyHeight > AndroidUtilities.dp(20f)) keyboardOnlyHeight else 0
}

internal fun topicReplyComposerPanelKeyboardInsetPx(inset: Int): Int = inset.coerceAtLeast(0)

internal fun topicReplyComposerActiveKeyboardInset(panelAttached: Boolean, keyboardInset: Int): Int {
    return if (panelAttached) keyboardInset.coerceAtLeast(0) else 0
}

internal fun topicReplyComposerEmojiPanelHeightPx(
    keyboardInset: Int,
    fallbackHeight: Int = AndroidUtilities.dp(200f),
    rootHeight: Int = AndroidUtilities.displaySize.y,
): Int {
    val rawHeight = topicReplyComposerKeyboardInsetPx(keyboardInset).takeIf { it > 0 }
        ?: fallbackHeight.takeIf { it > 0 }
        ?: AndroidUtilities.dp(200f)
    val maxHeight = topicReplyComposerEmojiPanelMaxHeightPx(rootHeight)
    val minHeight = minOf(AndroidUtilities.dp(200f), maxHeight)
    return rawHeight.coerceIn(minHeight, maxHeight)
}

internal fun topicReplyComposerEmojiPanelMaxHeightPx(rootHeight: Int = AndroidUtilities.displaySize.y): Int {
    val hardMax = AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_PANEL_MAX_HEIGHT_DP.toFloat())
    val screenMax = if (rootHeight > 0) {
        (rootHeight * TOPIC_REPLY_COMPOSER_EMOJI_PANEL_MAX_SCREEN_FRACTION).toInt()
    } else {
        hardMax
    }
    return minOf(hardMax, maxOf(AndroidUtilities.dp(200f), screenMax))
}

internal fun topicReplyComposerActiveBottomInset(panelAttached: Boolean, keyboardInset: Int, emojiPanelHeight: Int): Int {
    if (!panelAttached) {
        return 0
    }
    val emojiInset = emojiPanelHeight.coerceAtLeast(0)
    return if (emojiInset > 0) emojiInset else keyboardInset.coerceAtLeast(0)
}

internal fun topicDetailPanStartOffset(keyboardHeight: Int, popupShowing: Boolean, popupHeight: Int): Int {
    return if (keyboardHeight <= AndroidUtilities.dp(20f) && popupShowing) {
        popupHeight.coerceAtLeast(0)
    } else {
        0
    }
}

/**
 * ChatActivityFragmentView.heightAnimationEnabled() subset for TopicDetail.
 * BottomSheetTabs height is applied through ActionBarLayout margins + AdjustPan's
 * getExpandedHeight(); TopicDetail must not disable pan solely because tabs are
 * visible (that forced an insets-only path and overshot).
 */
internal fun topicDetailPanHeightAnimationEnabled(
    hasParentActivity: Boolean,
    fragmentPaused: Boolean,
    hasBlockingPopup: Boolean,
    hasShownAttachedSheet: Boolean,
    bottomTabsAnimating: Boolean,
    isLastFragment: Boolean,
): Boolean {
    if (!hasParentActivity || fragmentPaused || hasBlockingPopup) {
        return false
    }
    // Only block pan while a sheet transition is actively animating tabs height.
    if (hasShownAttachedSheet || bottomTabsAnimating) {
        return false
    }
    return isLastFragment
}

/**
 * TopicDetail is a normal top-down list. Unlike Chat's reversed chatListViewPaddingTop
 * path, contentPanTranslation must NOT be folded into list top padding — that cancels
 * AdjustPan's root translation and kills the whole-page keyboard motion.
 * Kept as an explicit identity helper so call sites/tests document the decision.
 */
internal fun topicDetailListTopPaddingPx(baseTopPadding: Int, contentPanTranslation: Float): Int {
    // contentPanTranslation is intentionally ignored for top padding — top-down list.
    return baseTopPadding.coerceAtLeast(0)
}

/** Chat list top pad includes ActionBar height so content draws under glass chrome. */
internal fun topicDetailListContentTopPaddingPx(actionBarHeight: Int, extra: Int = 0): Int {
    return actionBarHeight.coerceAtLeast(0) + extra.coerceAtLeast(0)
}

/**
 * Top-down list whole-page keyboard motion. Chat gets this "for free" from a reversed
 * list + bottom padding; TopicDetail applies the same animated IME lift as translationY
 * on the list container so posts rise with the island under a fixed ActionBar.
 */
internal fun topicDetailContentLiftPx(animatedImeBottomInset: Float): Float {
    val inset = animatedImeBottomInset.coerceAtLeast(0f)
    return if (inset == 0f) 0f else -inset
}

/**
 * When BottomSheetTabs are visible, ActionBarLayout already shortens LayoutContainer by
 * (nav + tabs). Window-level IME insets still report distance from the display bottom.
 * Applying the full IME under the island double-counts the reserved strip and the
 * composer overshoots, then settles — the "moves too high, then drops" glitch.
 */
internal fun topicDetailIslandBottomInsetPx(
    windowMaxBottomInset: Int,
    bottomTabsHeight: Int,
): Int {
    return (windowMaxBottomInset - bottomTabsHeight.coerceAtLeast(0)).coerceAtLeast(0)
}

/**
 * While AdjustPan is carrying the host, only keep the non-IME floor under the island
 * so pan + inset animation do not stack.
 */
internal fun topicDetailPanSafeBottomInsetPx(
    navigationBarInset: Int,
    inAppKeyboardHeight: Int,
    systemImeHeight: Int,
): Int {
    if (systemImeHeight > 0) {
        // IME is the pan driver; don't also lift the island by IME.
        return maxOf(navigationBarInset.coerceAtLeast(0), 0)
    }
    return maxOf(navigationBarInset.coerceAtLeast(0), inAppKeyboardHeight.coerceAtLeast(0))
}

internal fun topicDetailShouldApplyInputInsets(
    panInProgress: Boolean,
    systemImeHeight: Int,
): Boolean {
    return !panInProgress || systemImeHeight <= AndroidUtilities.dp(20f)
}

internal fun topicReplyComposerEmojiTabsBottomY(contentTopInset: Int): Int {
    return contentTopInset.coerceAtLeast(0) + AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_PANEL_TABS_HEIGHT_DP.toFloat())
}

internal fun topicReplyComposerEmojiGridTopPaddingPx(contentTopInset: Int): Int {
    return topicReplyComposerEmojiTabsBottomY(contentTopInset)
}

internal fun topicReplyComposerEmojiGridClipTopPx(contentTopInset: Int): Int {
    return topicReplyComposerEmojiTabsBottomY(contentTopInset) + 1
}

internal fun topicReplyComposerEmojiSectionScrollOffsetPx(contentTopInset: Int): Int {
    return AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_EMOJI_SECTION_SCROLL_OFFSET_DP.toFloat())
}

internal fun topicReplyComposerEmojiShouldUseHelperScroll(
    targetAttached: Boolean,
    targetPosition: Int,
    firstVisiblePosition: Int,
    spanCount: Int,
    animationsEnabled: Boolean,
): Boolean {
    if (!animationsEnabled || firstVisiblePosition == RecyclerView.NO_POSITION) {
        return true
    }
    val threshold = spanCount.coerceAtLeast(1) * TOPIC_REPLY_COMPOSER_EMOJI_HELPER_SCROLL_ROWS
    return !targetAttached && kotlin.math.abs(targetPosition - firstVisiblePosition) > threshold
}

internal fun topicReplyComposerEmojiCategoryForPosition(position: Int, categoryStartPositions: List<Int>): Int {
    if (categoryStartPositions.isEmpty()) {
        return 0
    }
    val safePosition = position.coerceAtLeast(0)
    var result = 0
    for (index in categoryStartPositions.indices) {
        if (safePosition >= categoryStartPositions[index]) {
            result = index
        } else {
            break
        }
    }
    return result
}

internal fun topicReplyComposerPanelBottomInsetForListPadding(
    targetInset: Int,
    animatedInset: Int,
    animationInProgress: Boolean,
): Int {
    return if (animationInProgress) {
        animatedInset.coerceAtLeast(0)
    } else {
        targetInset.coerceAtLeast(0)
    }
}

internal fun topicReplyComposerListBottomPadding(basePadding: Int, panelHeight: Int, keyboardInset: Int): Int {
    return basePadding.coerceAtLeast(0) + panelHeight.coerceAtLeast(0) + keyboardInset.coerceAtLeast(0)
}

internal fun topicReplyComposerVisiblePanelHeightForListPadding(
    panelHeight: Int,
    animatedInputFieldHeight: Float,
    targetInputFieldHeight: Int,
): Int {
    if (panelHeight <= 0) {
        return 0
    }
    if (targetInputFieldHeight <= 0 || animatedInputFieldHeight <= 0f) {
        return panelHeight
    }
    val inputDelta = targetInputFieldHeight - animatedInputFieldHeight
    return kotlin.math.ceil(panelHeight - inputDelta).toInt().coerceAtLeast(0)
}

internal fun topicReplyComposerShouldSubmitFromKey(
    keyCode: Int,
    action: Int,
    shiftPressed: Boolean,
    ctrlPressed: Boolean,
    sendByEnter: Boolean = false,
): Boolean {
    if (keyCode != KeyEvent.KEYCODE_ENTER || action != KeyEvent.ACTION_DOWN || shiftPressed) {
        return false
    }
    return if (sendByEnter) !ctrlPressed else ctrlPressed
}

internal data class TopicReplyComposerSendButtonState(
    val visible: Boolean,
    val enabled: Boolean,
    val alpha: Float,
    val scale: Float,
)

internal fun topicReplyComposerSendButtonState(hasText: Boolean, isSubmitting: Boolean): TopicReplyComposerSendButtonState {
    val visible = hasText || isSubmitting
    return TopicReplyComposerSendButtonState(
        visible = visible,
        enabled = hasText && !isSubmitting,
        alpha = if (visible) 1f else 0f,
        scale = if (visible) 1f else 0.1f,
    )
}

internal fun topicReplyComposerEmojiMarkup(reactionId: String): String {
    val id = reactionId.trim().trim(':').replace(' ', '_')
    return if (id.isBlank()) "" else ":$id:"
}

internal fun topicReplyComposerEmojiInsertionText(
    currentText: CharSequence,
    selectionStart: Int,
    selectionEnd: Int,
    reactionId: String,
): String {
    val markup = topicReplyComposerEmojiMarkup(reactionId)
    if (markup.isBlank()) return ""
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, currentText.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(0, currentText.length)
    val prefix = if (start > 0 && !currentText[start - 1].isWhitespace()) " " else ""
    val suffix = if (end < currentText.length && !currentText[end].isWhitespace()) " " else ""
    return prefix + markup + suffix
}

/** The markdown an uploaded file contributes: the site's URL, a readable alt from the filename. */
internal fun topicReplyComposerMediaMarkup(alt: String, url: String): String {
    if (url.isBlank()) return ""
    val name = alt.trim().replace("]", "").replace("\n", " ").ifBlank { "image" }
    return "![$name](${url.trim()})"
}

/**
 * Where an uploaded file lands in the draft: on a line of its own (the web composer writes
 * `![name](url)` the same way), with a newline first when the caret is mid-line and a trailing one
 * so typing continues on a fresh line.
 */
internal fun topicReplyComposerMediaInsertionText(
    currentText: CharSequence,
    selectionStart: Int,
    selectionEnd: Int,
    markdown: String,
): String {
    if (markdown.isBlank()) return ""
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, currentText.length)
    val prefix = if (start > 0 && currentText[start - 1] != '\n') "\n" else ""
    return prefix + markdown + "\n"
}

private fun topicReplyComposerFallbackEmojiCategories(
    reactionIds: List<String>,
    emojiCatalog: Map<String, String>,
): List<DiscourseEmojiCategory> {
    val emojis = reactionIds.distinct().mapNotNull { id ->
        val url = emojiCatalog[id] ?: PostReactionRendering.iconUrl(id, null, emojiCatalog)
        if (url.isNullOrBlank()) {
            null
        } else {
            DiscourseEmoji(name = id, url = url)
        }
    }
    return if (emojis.isEmpty()) {
        emptyList()
    } else {
        listOf(DiscourseEmojiCategory(id = "reactions", title = "Reactions", emojis = emojis))
    }
}

private fun drawEmojiLoadingPlaceholder(
    canvas: Canvas,
    paint: Paint,
    left: Float,
    top: Float,
    size: Float,
    radius: Float,
) {
    AndroidUtilities.rectTmp.set(left, top, left + size, top + size)
    paint.color = Theme.multAlpha(Theme.getColor(Theme.key_emptyListPlaceholder), 0.22f)
    canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, paint)
}

private open class TopicInputIslandFrameLayout(context: Context) : FrameLayout(context) {
    init {
        clipChildren = false
        clipToPadding = false
    }
}

private class ComposerBackgroundLayout(context: Context) : LinearLayout(context) {
    private var topView: View? = null
    private var textFieldContainer: View? = null
    private var inputFieldHeightProvider: (() -> Float)? = null

    init {
        setWillNotDraw(false)
        setPadding(
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_PANEL_HORIZONTAL_INSET_DP),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_PANEL_TOP_INSET_DP),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_PANEL_HORIZONTAL_INSET_DP),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_PANEL_BOTTOM_INSET_DP),
        )
    }

    fun setEnterViewClipTargets(
        topView: View,
        textFieldContainer: View,
        inputFieldHeightProvider: () -> Float,
    ) {
        this.topView = topView
        this.textFieldContainer = textFieldContainer
        this.inputFieldHeightProvider = inputFieldHeightProvider
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val shouldClip = child === topView || child === textFieldContainer
        if (!shouldClip) {
            return super.drawChild(canvas, child, drawingTime)
        }
        val inputHeight = inputFieldHeightProvider?.invoke()
            ?.takeIf { it > 0f }
            ?: AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_DEFAULT_HEIGHT_DP.toFloat()).toFloat()
        val separatorY = topicReplyComposerEnterViewSeparatorY(height, inputHeight)
        val save = canvas.save()
        if (child === textFieldContainer) {
            canvas.clipRect(0f, separatorY, width.toFloat(), height.toFloat())
        } else if (child === topView) {
            canvas.clipRect(0f, 0f, width.toFloat(), separatorY)
        }
        val result = super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(save)
        return result
    }

}

private class ReplyContextView(context: Context) : ChatActivityEnterTopView(context) {
    private var animator: ValueAnimator? = null
    private var hasBoundContent = false
    private val replyLayout = ChatReplyContainer(context, null)
    private val closeButton = ImageView(context).apply {
        setColorFilter(PorterDuffColorFilter(Theme.getColor(Theme.key_chat_replyPanelClose), PorterDuff.Mode.MULTIPLY))
        setImageResource(TelegramR.drawable.input_clear)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(io.github.baba983.nativedo.R.string.topic_reply_composer_clear_context)
        background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1, AndroidUtilities.dp(19f))
        isFocusable = true
    }

    init {
        minimumHeight = AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_TOP_VIEW_HEIGHT_DP.toFloat())
        addReplyView(replyLayout, LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT,
            LayoutHelper.MATCH_PARENT.toFloat(),
            Gravity.NO_GRAVITY,
            0f,
            0f,
            52f,
            0f,
        ))
        replyLayout.current().active = true
        replyLayout.current().icon.setImageResource(TelegramR.drawable.ic_ab_reply)
        replyLayout.current().image.visibility = View.GONE
        addView(closeButton, LayoutHelper.createFrame(
            52f,
            TOPIC_REPLY_COMPOSER_REPLY_HEADER_HEIGHT_DP.toFloat(),
            Gravity.RIGHT or Gravity.TOP,
            0f,
            0.5f,
            0f,
            0f,
        ))
    }

    fun prepareEnterAnimation() {
        animator?.cancel()
        animate().cancel()
        visibility = View.VISIBLE
        alpha = 1f
        translationY = topicReplyComposerTopViewTranslationY(targetHeight(), visibilityProgress = 0f)
        setAnimatedHeight(0)
    }

    fun startEnterAnimation() {
        if (visibility != View.VISIBLE) {
            return
        }
        animator?.cancel()
        animate().cancel()
        val targetHeight = targetHeight()
        val fromHeight = currentAnimatedHeight()
        if (fromHeight == targetHeight && alpha == 1f && translationY == 0f) {
            return
        }
        val fromVisibilityProgress = if (targetHeight > 0) {
            (fromHeight.toFloat() / targetHeight).coerceIn(0f, 1f)
        } else {
            1f
        }
        var cancelled = false
        val expand = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = topicReplyComposerTopViewEnterDurationMs()
            interpolator = TOPIC_REPLY_COMPOSER_EDIT_TEXT_BOUNDS_INTERPOLATOR
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val visibilityProgress = lerpFloat(fromVisibilityProgress, 1f, progress)
                setAnimatedHeight((targetHeight * visibilityProgress).toInt())
                alpha = 1f
                translationY = topicReplyComposerTopViewTranslationY(targetHeight, visibilityProgress)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (animator === animation) {
                        cancelled = true
                        animator = null
                    }
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator === animation) {
                        animator = null
                    }
                    if (cancelled) {
                        return
                    }
                    setAnimatedHeight(targetHeight)
                    alpha = 1f
                    translationY = 0f
                }
            })
        }
        animator = expand
        expand.start()
    }

    fun dismiss(animated: Boolean) {
        animator?.cancel()
        animate().cancel()
        if (!animated || !isAttachedToWindow || measuredHeight <= 0) {
            visibility = View.GONE
            alpha = 1f
            translationY = 0f
            setAnimatedHeight(0)
            return
        }
        val params = layoutParams ?: return
        val fromHeight = currentAnimatedHeight()
        val targetHeight = targetHeight()
        val fromVisibilityProgress = if (targetHeight > 0) {
            (fromHeight.toFloat() / targetHeight).coerceIn(0f, 1f)
        } else {
            0f
        }
        params.height = fromHeight
        layoutParams = params
        var cancelled = false
        val collapse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = topicReplyComposerTopViewHideDurationMs()
            interpolator = TOPIC_REPLY_COMPOSER_EDIT_TEXT_BOUNDS_INTERPOLATOR
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val visibilityProgress = lerpFloat(fromVisibilityProgress, 0f, progress)
                val lp = layoutParams
                lp.height = (targetHeight * visibilityProgress).toInt()
                layoutParams = lp
                alpha = 1f
                translationY = topicReplyComposerTopViewTranslationY(targetHeight, visibilityProgress)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator === animation) {
                        animator = null
                    }
                    if (cancelled) {
                        return
                    }
                    visibility = View.GONE
                    alpha = 1f
                    translationY = 0f
                    val lp = layoutParams
                    lp.height = 0
                    layoutParams = lp
                }
            })
        }
        animator = collapse
        collapse.start()
    }

    private fun currentAnimatedHeight(): Int {
        val paramsHeight = layoutParams?.height ?: 0
        return if (paramsHeight >= 0) paramsHeight else measuredHeight
    }

    private fun setAnimatedHeight(height: Int) {
        val params = layoutParams ?: return
        val nextHeight = height.coerceAtLeast(0)
        if (params.height == nextHeight) {
            return
        }
        params.height = nextHeight
        layoutParams = params
    }

    private fun targetHeight(): Int {
        return AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_TOP_VIEW_HEIGHT_DP.toFloat())
    }

    fun bind(title: String, subtitle: String, onClose: () -> Unit, canOpen: Boolean, onOpen: () -> Boolean) {
        visibility = View.VISIBLE
        val layout = if (hasBoundContent) replyLayout.layouts[1] else replyLayout.current()
        layout.icon.setImageResource(TelegramR.drawable.ic_ab_reply)
        layout.image.visibility = View.GONE
        layout.name.setText(Emoji.replaceEmoji(title, layout.name.paint.fontMetricsInt, false))
        layout.obj.setText(Emoji.replaceEmoji(subtitle, layout.obj.paint.fontMetricsInt, false))
        layout.updateColors()
        if (hasBoundContent) {
            replyLayout.switchLayouts(true)
        } else {
            layout.active = true
            hasBoundContent = true
        }
        closeButton.setOnClickListener { onClose() }
        replyLayout.isClickable = canOpen
        replyLayout.isLongClickable = canOpen
        if (canOpen) {
            replyLayout.setOnClickListener {
                onOpen()
            }
            replyLayout.setOnLongClickListener {
                onOpen()
            }
        } else {
            replyLayout.setOnClickListener(null)
            replyLayout.setOnLongClickListener(null)
        }
    }
}

internal enum class ComposerIconKind {
    Smile,
    Keyboard,
}

internal enum class ComposerIconTransitState {
    SMILE_TO_KEYBOARD,
    KEYBOARD_TO_SMILE,
}

internal fun topicReplyComposerIconTransitState(
    from: ComposerIconKind?,
    to: ComposerIconKind,
): ComposerIconTransitState? {
    return when {
        from == ComposerIconKind.Smile && to == ComposerIconKind.Keyboard -> ComposerIconTransitState.SMILE_TO_KEYBOARD
        from == ComposerIconKind.Keyboard && to == ComposerIconKind.Smile -> ComposerIconTransitState.KEYBOARD_TO_SMILE
        else -> null
    }
}

private class ComposerIconButton(
    context: Context,
    initialKind: ComposerIconKind,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var kind = initialKind
    private var previousKind: ComposerIconKind? = null
    private var transitState: ComposerIconTransitState? = null
    private var iconSwitchAnimator: ValueAnimator? = null
    private var iconSwitchProgress = 1f

    init {
        val padding = AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_PADDING_DP)
        setPadding(padding, padding, padding, padding)
        background = Theme.createInsetRoundRectDrawable(
            Theme.getColor(Theme.key_listSelector),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_RADIUS_DP).toFloat(),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_W_DP),
            AndroidUtilities.dp(TOPIC_REPLY_COMPOSER_ICON_BUTTON_SELECTOR_INSET_H_DP),
        )
        isFocusable = true
    }

    fun setKind(kind: ComposerIconKind, animated: Boolean) {
        if (this.kind == kind) {
            return
        }
        iconSwitchAnimator?.cancel()
        iconSwitchAnimator = null
        val fromKind = this.kind
        previousKind = fromKind
        transitState = topicReplyComposerIconTransitState(fromKind, kind)
        this.kind = kind
        iconSwitchProgress = if (animated) 0f else 1f
        if (!animated) {
            previousKind = null
            transitState = null
            invalidate()
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = topicReplyComposerIconSwitchDurationMs()
            interpolator = CubicBezierInterpolator.DEFAULT
            addUpdateListener { animation ->
                iconSwitchProgress = animation.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (iconSwitchAnimator === animation) {
                        previousKind = null
                        transitState = null
                        iconSwitchProgress = 1f
                        iconSwitchAnimator = null
                        invalidate()
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (iconSwitchAnimator === animation) {
                        iconSwitchAnimator = null
                    }
                }
            })
        }
        iconSwitchAnimator = animator
        animator.start()
    }

    override fun onDetachedFromWindow() {
        iconSwitchAnimator?.cancel()
        iconSwitchAnimator = null
        previousKind = null
        transitState = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val baseAlpha = if (isEnabled) 255 else 110
        val transition = transitState
        if (transition == ComposerIconTransitState.SMILE_TO_KEYBOARD || transition == ComposerIconTransitState.KEYBOARD_TO_SMILE) {
            val morphProgress = if (transition == ComposerIconTransitState.SMILE_TO_KEYBOARD) {
                iconSwitchProgress
            } else {
                1f - iconSwitchProgress
            }
            drawSmileKeyboardMorph(
                canvas = canvas,
                color = composerIconColor(ComposerIconKind.Smile),
                alpha = baseAlpha,
                progress = morphProgress,
            )
            return
        }
        previousKind?.takeIf { iconSwitchProgress < 1f }?.let { oldKind ->
            val spec = iconDrawSpec(
                kind = oldKind,
                transition = transition,
                progress = iconSwitchProgress,
                appearing = false,
            )
            drawIcon(
                canvas = canvas,
                iconKind = oldKind,
                color = composerIconColor(oldKind),
                alpha = (baseAlpha * spec.alpha).toInt().coerceIn(0, 255),
                scale = spec.scale,
                translationY = spec.translationY,
                rotation = spec.rotation,
            )
        }
        val spec = iconDrawSpec(
            kind = kind,
            transition = transition,
            progress = iconSwitchProgress,
            appearing = true,
        )
        drawIcon(
            canvas = canvas,
            iconKind = kind,
            color = composerIconColor(kind),
            alpha = (baseAlpha * spec.alpha).toInt().coerceIn(0, 255),
            scale = spec.scale,
            translationY = spec.translationY,
            rotation = spec.rotation,
        )
    }

    private fun drawSmileKeyboardMorph(canvas: Canvas, color: Int, alpha: Int, progress: Float) {
        if (alpha <= 0) {
            return
        }
        val t = CubicBezierInterpolator.DEFAULT.getInterpolation(progress.coerceIn(0f, 1f))
        val cx = width / 2f
        val cy = height / 2f

        paint.color = color
        paint.alpha = alpha
        paint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        val halfWidth = lerpFloat(AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(11f).toFloat(), t)
        val halfHeight = lerpFloat(AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(8f).toFloat(), t)
        val radius = lerpFloat(AndroidUtilities.dp(10f).toFloat(), AndroidUtilities.dp(2f).toFloat(), t)
        rect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
        canvas.drawRoundRect(rect, radius, radius, paint)

        val smileAlpha = ((1f - (t / 0.72f).coerceIn(0f, 1f)) * alpha).toInt().coerceIn(0, 255)
        if (smileAlpha > 0) {
            paint.alpha = smileAlpha
            paint.style = Paint.Style.FILL
            val eyeRadius = lerpFloat(AndroidUtilities.dp(1.4f).toFloat(), AndroidUtilities.dp(0.8f).toFloat(), t)
            val eyeY = lerpFloat(cy - AndroidUtilities.dp(3f), cy - AndroidUtilities.dp(5f), t)
            val eyeSpread = lerpFloat(AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(7f).toFloat(), t)
            canvas.drawCircle(cx - eyeSpread, eyeY, eyeRadius, paint)
            canvas.drawCircle(cx + eyeSpread, eyeY, eyeRadius, paint)

            paint.style = Paint.Style.STROKE
            rect.set(
                cx - AndroidUtilities.dp(5f),
                cy - AndroidUtilities.dp(3f),
                cx + AndroidUtilities.dp(5f),
                cy + AndroidUtilities.dp(6f),
            )
            canvas.drawArc(rect, 25f, 130f, false, paint)
        }

        val keyAlpha = (((t - 0.28f) / 0.72f).coerceIn(0f, 1f) * alpha).toInt().coerceIn(0, 255)
        if (keyAlpha > 0) {
            paint.alpha = keyAlpha
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = AndroidUtilities.dp(1.7f).toFloat()
            val keyTop = cy - AndroidUtilities.dp(3f)
            val keySpacing = AndroidUtilities.dp(5f)
            for (row in 0..1) {
                val y = keyTop + row * AndroidUtilities.dp(4f)
                canvas.drawLine(cx - keySpacing, y, cx - keySpacing + AndroidUtilities.dp(1f), y, paint)
                canvas.drawLine(cx, y, cx + AndroidUtilities.dp(1f), y, paint)
                canvas.drawLine(cx + keySpacing, y, cx + keySpacing + AndroidUtilities.dp(1f), y, paint)
            }
            canvas.drawLine(cx - AndroidUtilities.dp(5f), cy + AndroidUtilities.dp(5f), cx + AndroidUtilities.dp(5f), cy + AndroidUtilities.dp(5f), paint)
        }
        paint.alpha = 255
    }

    private fun drawIcon(
        canvas: Canvas,
        iconKind: ComposerIconKind,
        color: Int,
        alpha: Int,
        scale: Float,
        translationY: Float,
        rotation: Float,
    ) {
        if (alpha <= 0) {
            return
        }
        val restoreCount = canvas.save()
        if (translationY != 0f) {
            canvas.translate(0f, translationY)
        }
        if (scale != 1f) {
            canvas.scale(scale, scale, width / 2f, height / 2f)
        }
        if (rotation != 0f) {
            canvas.rotate(rotation, width / 2f, height / 2f)
        }
        paint.color = color
        paint.alpha = alpha
        paint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        val cx = width / 2f
        val cy = height / 2f
        when (iconKind) {
            ComposerIconKind.Smile -> {
                val radius = AndroidUtilities.dp(10f).toFloat()
                canvas.drawCircle(cx, cy, radius, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx - AndroidUtilities.dp(4f), cy - AndroidUtilities.dp(3f), AndroidUtilities.dp(1.4f).toFloat(), paint)
                canvas.drawCircle(cx + AndroidUtilities.dp(4f), cy - AndroidUtilities.dp(3f), AndroidUtilities.dp(1.4f).toFloat(), paint)
                paint.style = Paint.Style.STROKE
                rect.set(cx - AndroidUtilities.dp(5f), cy - AndroidUtilities.dp(3f), cx + AndroidUtilities.dp(5f), cy + AndroidUtilities.dp(6f))
                canvas.drawArc(rect, 25f, 130f, false, paint)
            }
            ComposerIconKind.Keyboard -> {
                val halfWidth = AndroidUtilities.dp(11f).toFloat()
                val halfHeight = AndroidUtilities.dp(8f).toFloat()
                rect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), paint)
                val keyTop = cy - AndroidUtilities.dp(3f)
                val keySpacing = AndroidUtilities.dp(5f)
                paint.strokeWidth = AndroidUtilities.dp(1.7f).toFloat()
                for (row in 0..1) {
                    val y = keyTop + row * AndroidUtilities.dp(4f)
                    canvas.drawLine(cx - keySpacing, y, cx - keySpacing + AndroidUtilities.dp(1f), y, paint)
                    canvas.drawLine(cx, y, cx + AndroidUtilities.dp(1f), y, paint)
                    canvas.drawLine(cx + keySpacing, y, cx + keySpacing + AndroidUtilities.dp(1f), y, paint)
                }
                canvas.drawLine(cx - AndroidUtilities.dp(5f), cy + AndroidUtilities.dp(5f), cx + AndroidUtilities.dp(5f), cy + AndroidUtilities.dp(5f), paint)
            }
        }
        paint.alpha = 255
        canvas.restoreToCount(restoreCount)
    }
}

private data class ComposerIconDrawSpec(
    val alpha: Float,
    val scale: Float,
    val translationY: Float,
    val rotation: Float,
)

private fun iconDrawSpec(
    kind: ComposerIconKind,
    transition: ComposerIconTransitState?,
    progress: Float,
    appearing: Boolean,
): ComposerIconDrawSpec {
    val t = progress.coerceIn(0f, 1f)
    val smallShift = AndroidUtilities.dp(2.5f).toFloat()
    return when (transition) {
        ComposerIconTransitState.SMILE_TO_KEYBOARD -> {
            if (appearing && kind == ComposerIconKind.Keyboard) {
                ComposerIconDrawSpec(
                    alpha = t,
                    scale = lerpFloat(0.5f, 1f, t),
                    translationY = lerpFloat(smallShift, 0f, t),
                    rotation = 0f,
                )
            } else {
                ComposerIconDrawSpec(
                    alpha = 1f - t,
                    scale = lerpFloat(1f, 0.5f, t),
                    translationY = lerpFloat(0f, -smallShift, t),
                    rotation = 0f,
                )
            }
        }
        ComposerIconTransitState.KEYBOARD_TO_SMILE -> {
            if (appearing && kind == ComposerIconKind.Smile) {
                ComposerIconDrawSpec(
                    alpha = t,
                    scale = lerpFloat(0.5f, 1f, t),
                    translationY = lerpFloat(-smallShift, 0f, t),
                    rotation = 0f,
                )
            } else {
                ComposerIconDrawSpec(
                    alpha = 1f - t,
                    scale = lerpFloat(1f, 0.5f, t),
                    translationY = lerpFloat(0f, smallShift, t),
                    rotation = 0f,
                )
            }
        }
        null -> {
            ComposerIconDrawSpec(
                alpha = if (appearing) t else 1f - t,
                scale = if (appearing) lerpFloat(0.86f, 1f, t) else lerpFloat(1f, 0.86f, t),
                translationY = 0f,
                rotation = 0f,
            )
        }
    }
}

private fun composerIconColor(kind: ComposerIconKind): Int {
    return when (kind) {
        ComposerIconKind.Smile,
        ComposerIconKind.Keyboard -> Theme.getColor(Theme.key_glass_defaultIcon)
    }
}

/**
 * The composer's round action button: a filled circle with an icon and, while [busy], the arcs the
 * send button spins. The composer uses one for sending and one for attaching media in the same
 * slot - the attach button shows while there is nothing to send, the send button once there is.
 */
private class ComposerCircleButton(
    context: Context,
    @androidx.annotation.DrawableRes iconRes: Int,
    shown: Boolean = false,
) : View(context) {
    private var shown = shown
    private var shownAnimator: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val icon: Drawable = context.resources.getDrawable(iconRes).mutate().apply {
        setColorFilter(PorterDuffColorFilter(Theme.getColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.SRC_IN))
    }
    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dp(2f).toFloat()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val bounce = ButtonBounce(this)
    private val loadingInterpolator = CubicBezierInterpolator(0.4, 0.0, 0.2, 1.0)
    private val loadingAnimatedShown = AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT)
    private val appear = AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT)
    var busy: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            loadingAnimatedShown.setDelay(
                if (value && loadingAnimatedShown.get() >= 1f) {
                    topicReplyComposerSendButtonLoadingRepeatDelayMs()
                } else {
                    0L
                },
            )
            field = value
            invalidate()
        }

    init {
        isFocusable = true
        applyShown(shown)
    }

    /**
     * Shows or hides the button; the two buttons the composer shares its slot between cross-fade
     * and scale into each other. [animated] is what the panel passes: an attached view animates,
     * one that is still being built applies the end state.
     */
    fun setShown(shown: Boolean, animated: Boolean) {
        if (this.shown == shown) return
        this.shown = shown
        shownAnimator?.cancel()
        shownAnimator = null
        if (!animated) {
            applyShown(shown)
            return
        }
        if (shown) {
            visibility = View.VISIBLE
        }
        val fromAlpha = alpha
        val fromScaleX = scaleX
        val fromScaleY = scaleY
        if (shown && fromAlpha < 0.25f) {
            appear()
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                alpha = lerpFloat(fromAlpha, if (shown) 1f else 0f, t)
                scaleX = lerpFloat(fromScaleX, if (shown) 1f else 0.1f, t)
                scaleY = lerpFloat(fromScaleY, if (shown) 1f else 0.1f, t)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (shownAnimator === animation) {
                        if (!shown) visibility = View.INVISIBLE
                        shownAnimator = null
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (shownAnimator === animation) {
                        shownAnimator = null
                    }
                }
            })
        }
        shownAnimator = animator
        animator.start()
    }

    private fun applyShown(shown: Boolean) {
        visibility = if (shown) View.VISIBLE else View.INVISIBLE
        alpha = if (shown) 1f else 0f
        scaleX = if (shown) 1f else 0.1f
        scaleY = if (shown) 1f else 0.1f
    }

    fun appear() {
        appear.force(0f)
        invalidate()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        bounce.setPressed(pressed)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (alpha <= 0f) {
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val activeSize = (height - AndroidUtilities.dp(8f)).coerceAtLeast(0)
            if (!topicReplyComposerSendButtonShouldHandleDown(
                    x = event.x,
                    y = event.y,
                    viewWidth = width,
                    viewHeight = height,
                    activeWidth = activeSize,
                    activeHeight = activeSize,
                )
            ) {
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = AndroidUtilities.dp(38f).toFloat()
        val margin = AndroidUtilities.dp(3f).toFloat()
        rect.set(width - size - margin, height - size - margin, width - margin, height - margin)
        val scale = bounce.getScale(0.1f)
        val restore = if (scale != 1f) {
            val count = canvas.save()
            canvas.scale(scale, scale, rect.centerX(), rect.centerY())
            count
        } else {
            -1
        }
        paint.style = Paint.Style.FILL
        paint.color = Theme.getColor(Theme.key_chat_messagePanelSend)
        paint.alpha = if (isEnabled || busy) 255 else 110
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)

        val loadingShown = loadingAnimatedShown.set(if (busy) 1f else 0f)
        paint.color = Theme.getColor(Theme.key_dialogFloatingIcon)
        paint.alpha = (255f * (1f - loadingShown)).toInt().coerceIn(0, 255)
        if (loadingShown > 0f) {
            loadingPaint.color = Theme.getColor(Theme.key_dialogFloatingIcon)
            loadingPaint.alpha = (255f * loadingShown).toInt().coerceIn(0, 255)
            val spinner = AndroidUtilities.dp(topicReplyComposerSendButtonLoadingRadiusDp()).toFloat()
            rect.set(rect.centerX() - spinner, rect.centerY() - spinner, rect.centerX() + spinner, rect.centerY() + spinner)
            val time = System.currentTimeMillis() % 5400L
            var segment0 = kotlin.math.max(0f, 1520f * time / 5400f - 20f)
            var segment1 = 1520f * time / 5400f
            for (i in 0..3) {
                segment1 += loadingInterpolator.getInterpolation((time - i * 1350L) / 667f) * 250f
                segment0 += loadingInterpolator.getInterpolation((time - (667L + i * 1350L)) / 667f) * 250f
            }
            canvas.drawArc(rect, segment0, segment1 - segment0, false, loadingPaint)
            invalidate()
        }
        if (paint.alpha > 0) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val appearProgress = appear.set(1f)
            val iconScale = lerpFloat(1f, 0.6f, loadingShown)
            val iconRestore = canvas.save()
            canvas.translate(
                -AndroidUtilities.dp(24f) * (1f - appearProgress),
                AndroidUtilities.dp(24f) * (1f - appearProgress),
            )
            canvas.scale(
                iconScale * lerpFloat(0.35f, 1f, appearProgress),
                iconScale * lerpFloat(0.35f, 1f, appearProgress),
                cx,
                cy,
            )
            canvas.rotate(60f * (1f - appearProgress), cx, cy)
            icon.alpha = paint.alpha
            val iconLeft = Math.round(cx - icon.intrinsicWidth / 2f)
            val iconTop = Math.round(cy - icon.intrinsicHeight / 2f)
            icon.setBounds(
                iconLeft,
                iconTop,
                iconLeft + icon.intrinsicWidth,
                iconTop + icon.intrinsicHeight,
            )
            icon.draw(canvas)
            icon.alpha = 255
            canvas.restoreToCount(iconRestore)
            if (appearProgress < 1f) {
                invalidate()
            }
        }
        paint.alpha = 255
        if (restore >= 0) {
            canvas.restoreToCount(restore)
        }
    }
}

private fun replyHeaderTitle(context: Context, replyToPost: Post?, replyToPostNumber: Int?): String {
    val action = context.getString(io.github.baba983.nativedo.R.string.topic_reader_action_reply)
    val author = replyToPost?.let { topicPostDisplayName(it.authorDisplayName, it.authorUsername) }?.takeIf { it.isNotBlank() }
    val floor = replyToPostNumber?.let(::topicFloorLabelForPostNumber)
    return listOfNotNull(action, author, floor?.let { "($it)" }).joinToString(" ")
}

private fun replyHeaderSubtitle(initialText: String, replyToPost: Post?): String {
    val quotedLine = initialText.takeIf { it.trimStart().startsWith("[quote") }
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { line ->
            line.isNotBlank() && !line.startsWith("[quote") && line != "[/quote]"
        }
        ?.removePrefix(">")
        ?.trim()
    if (!quotedLine.isNullOrBlank()) {
        return quotedLine
    }
    return replyToPost?.authorUsername?.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
}

private fun lerpFloat(from: Float, to: Float, progress: Float): Float {
    return from + (to - from) * progress
}

private fun lerpInt(from: Int, to: Int, progress: Float): Int {
    return (from + (to - from) * progress).toInt()
}

internal fun discourseQuoteMarkup(text: CharSequence, sourcePost: Post?, topicId: Long): String {
    val quoteText = text.toString().trim('\n')
    val postNumber = sourcePost?.postNumber
    val username = sourcePost?.authorUsername?.takeIf { it.isNotBlank() }
    if (postNumber != null && username != null) {
        val escapedUsername = username.replace("\"", "\\\"")
        return "[quote=\"$escapedUsername, post:$postNumber, topic:$topicId\"]\n$quoteText\n[/quote]"
    }
    return quoteText.lineSequence().joinToString("\n") { line -> "> $line" }
}

internal fun topicReactionUsersForFilter(
    groups: List<PostReactionUsersGroup>,
    selectedReactionId: String?,
): List<PostReactionUser> {
    if (selectedReactionId != TOPIC_REACTION_ALL_FILTER_ID) {
        return groups.firstOrNull { group -> group.id == selectedReactionId }?.users.orEmpty()
    }
    return groups
        .flatMap { group -> group.users }
        .sortedByDescending { user -> user.createdAt }
        .distinctBy { user -> user.username }
}

internal fun topicReplyFooterCount(post: Post, loadedPreviewCount: Int): Int =
    maxOf(post.replyCount, loadedPreviewCount)

internal fun topicReplySkeletonRows(totalCount: Int): Int =
    totalCount.coerceIn(1, REPLY_SKELETON_MAX_VISIBLE_ROWS)

internal fun topicDetailShouldUpdateCollapsedTitle(searchOpen: Boolean, searchFieldVisible: Boolean): Boolean {
    return !searchOpen && !searchFieldVisible
}

internal fun topicDetailIdentitySubtitle(
    context: Context,
    totalPosts: Int,
    viewCount: Int,
    tags: List<String> = emptyList(),
): String {
    val replies = totalPosts.coerceAtLeast(0)
    val views = viewCount.coerceAtLeast(0)
    val base = when {
        replies > 0 && views > 0 -> context.getString(R.string.topic_detail_subtitle_replies_views, replies, views)
        replies > 0 -> context.getString(R.string.topic_detail_subtitle_replies, replies)
        views > 0 -> context.getString(R.string.topic_detail_subtitle_views, views)
        else -> ""
    }
    val tag = tags.firstOrNull()?.takeIf { it.isNotBlank() }
    return if (tag != null && base.isNotBlank()) {
        "$tag · $base"
    } else {
        tag ?: base
    }
}

internal fun topicDetailIdentityDisplayName(
    firstPost: Post?,
    fallbackTitle: String,
    initialIdentity: TopicInitialIdentity? = null,
): String {
    val display = firstPost?.authorDisplayName?.takeIf { it.isNotBlank() }
    val username = firstPost?.authorUsername?.takeIf { it.isNotBlank() }
    val initialUsername = initialIdentity?.username?.takeIf { it.isNotBlank() }
    return display ?: username ?: initialUsername ?: fallbackTitle
}

internal fun topicDetailIdentityAvatarUrl(
    baseUrl: String,
    firstPost: Post?,
    initialIdentity: TopicInitialIdentity? = null,
): String {
    val avatarTemplate = firstPost?.authorAvatarTemplate?.takeIf { it.isNotBlank() }
        ?: initialIdentity?.avatarTemplate.orEmpty()
    val animatedAvatarUrl = firstPost?.authorAnimatedAvatarUrl?.takeIf { it.isNotBlank() }
        ?: initialIdentity?.animatedAvatarUrl.orEmpty()
    if (avatarTemplate.isBlank() && animatedAvatarUrl.isBlank()) return ""
    return resolveAvatarUrl(
        baseUrl = baseUrl,
        avatarTemplate = avatarTemplate,
        animatedAvatarUrl = animatedAvatarUrl,
        size = TOPIC_AVATAR_REQUEST_SIZE,
    )
}

internal fun topicPostsBelowViewportOrNull(streamPostIds: List<Long>, lastVisiblePostId: Long?): Int? {
    if (lastVisiblePostId == null || streamPostIds.isEmpty()) return null
    val index = streamPostIds.indexOf(lastVisiblePostId)
    return if (index < 0) null else (streamPostIds.lastIndex - index).coerceAtLeast(0)
}

internal fun topicPostsAboveViewportOrNull(streamPostIds: List<Long>, firstVisiblePostId: Long?): Int? {
    if (firstVisiblePostId == null || streamPostIds.isEmpty()) return null
    val index = streamPostIds.indexOf(firstVisiblePostId)
    return index.takeIf { it >= 0 }
}

internal fun topicShouldShowPageDown(
    postsBelowViewport: Int,
    selectionActive: Boolean,
    canShowFromScroll: Boolean,
): Boolean {
    if (postsBelowViewport <= 0 || selectionActive) return false
    // Telegram's canShowPagedownButton: initially true (the topic opens at the
    // oldest post, so the page-down button invites the jump before the first
    // bottom-reach), then the scroll machine (totalDy threshold) owns it.
    return canShowFromScroll
}

internal fun topicShouldShowReturnToTop(
    postsAboveViewport: Int,
    selectionActive: Boolean,
): Boolean = postsAboveViewport > 0 && !selectionActive

internal fun topicPageDownControlsBottomOffset(panelHeight: Int, panelBottomInset: Int): Int {
    return panelHeight.coerceAtLeast(0) + panelBottomInset.coerceAtLeast(0) + AndroidUtilities.dp(4f)
}

internal enum class TopicSelectionTouchDispatch {
    CONSUME,
    DISPATCH_CANCEL,
    DISPATCH_ORIGINAL,
}

internal fun topicSelectionTouchDispatch(
    overlayHandled: Boolean,
    tapClearedSelection: Boolean,
): TopicSelectionTouchDispatch {
    return when {
        overlayHandled -> TopicSelectionTouchDispatch.CONSUME
        tapClearedSelection -> TopicSelectionTouchDispatch.DISPATCH_CANCEL
        else -> TopicSelectionTouchDispatch.DISPATCH_ORIGINAL
    }
}

private class TopicViewModelFactory(
    private val repository: TopicRepository,
    private val topicId: Long,
    private val replyDraftStore: io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftStore,
    private val replyDraftKey: TopicReplyDraftKey?,
    private val readPositionStore: io.github.baba983.nativedo.feature.topic.data.TopicReadPositionStore,
    private val readPositionKey: TopicReadPositionKey?,
    private val readReportScope: kotlinx.coroutines.CoroutineScope?,
    private val canTrackReadPosition: Boolean,
    private val initialPostNumber: Int?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return TopicViewModel(
            repository = repository,
            topicId = topicId,
            replyDraftStore = replyDraftStore,
            replyDraftKey = replyDraftKey,
            readPositionStore = readPositionStore,
            readPositionKey = readPositionKey,
            readReportScope = readReportScope,
            canTrackReadPosition = canTrackReadPosition,
            initialPostNumber = initialPostNumber,
        ) as T
    }
}

/**
 * Compact ChatAvatarContainer-shaped identity strip for TopicDetail ActionBar.
 * Layout mirrors upstream ChatAvatarContainer (avatar 42, leftPadding 8, title/subtitle
 * offsets). Hosted with WRAP_CONTENT + left 53 / right menu gutters so back / menu stay
 * outside the hit target — same as ChatActivity.avatarContainer.
 */
internal class TopicIdentityContainer(context: Context) : FrameLayout(context) {
    private val avatarDrawable = AvatarDrawable()
    // Draw AvatarDrawable directly. BackupImageView's ImageReceiver path is flaky for
    // monogram-only avatars under Paparazzi / when no remote image is bound.
    private val avatarView = object : View(context) {
        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            // Theme "Blue" defaultColors may leave avatar keys at 0 → AvatarDrawable
            // paints pure black. Prefer the NativeDo/Telegram palette when missing.
            if (avatarDrawable.color == 0 || avatarDrawable.color == 0xFF000000.toInt()) {
                avatarDrawable.setColor(0xFFFF845E.toInt(), 0xFFD45246.toInt())
            }
            avatarDrawable.setBounds(0, 0, width, height)
            avatarDrawable.draw(canvas)
        }
    }
    private val remoteAvatarView = BackupImageView(context).apply {
        setRoundRadius(AndroidUtilities.dp(TOPIC_IDENTITY_AVATAR_SIZE_DP / 2f))
        visibility = GONE
    }
    private val titleView = SimpleTextView(context).apply {
        setTextSize(TOPIC_IDENTITY_TITLE_SIZE_DP)
        setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM))
        setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle))
        setGravity(Gravity.LEFT or Gravity.TOP)
        // ChatAvatarContainer: long titles use gradient ellipsize (soft edge), not a hard
        // clip line. Keep a right padding so the fade has room inside the middle pill.
        setScrollNonFitText(true)
        setEllipsizeByGradient(TOPIC_IDENTITY_TEXT_FADE_DP)
        setRightPadding(AndroidUtilities.dp(TOPIC_IDENTITY_TEXT_RIGHT_PADDING_DP.toFloat()))
    }
    private val subtitleView = SimpleTextView(context).apply {
        setTextSize(TOPIC_IDENTITY_SUBTITLE_SIZE_DP)
        setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        setGravity(Gravity.LEFT or Gravity.TOP)
        setScrollNonFitText(true)
        setEllipsizeByGradient(TOPIC_IDENTITY_TEXT_FADE_DP)
        setRightPadding(AndroidUtilities.dp(TOPIC_IDENTITY_TEXT_RIGHT_PADDING_DP.toFloat()))
    }
    private var boundTitle: String = ""
    private var boundSubtitle: String = ""
    private var boundAvatarUrl: String = ""
    private var boundDisplayName: String = ""
    private var avatarInfoApplied: Boolean = false
    private var occupyStatusBar: Boolean = true
    private val leftPadding = AndroidUtilities.dp(TOPIC_IDENTITY_LEFT_PADDING_DP)

    init {
        clipChildren = false
        clipToPadding = false
        addView(avatarView)
        addView(remoteAvatarView)
        addView(titleView)
        addView(subtitleView)
    }

    fun setOccupyStatusBar(value: Boolean) {
        if (occupyStatusBar == value) return
        occupyStatusBar = value
        requestLayout()
    }

    fun bind(title: String, subtitle: String, avatarUrl: String, displayName: String) {
        if (boundTitle != title) {
            boundTitle = title
            titleView.setText(title)
        }
        if (boundSubtitle != subtitle) {
            boundSubtitle = subtitle
            subtitleView.setText(subtitle)
            subtitleView.visibility = if (subtitle.isBlank()) GONE else VISIBLE
        }
        val monogram = displayName.ifBlank { title }
        val avatarChanged = !avatarInfoApplied || boundAvatarUrl != avatarUrl || boundDisplayName != monogram
        if (avatarChanged) {
            boundAvatarUrl = avatarUrl
            boundDisplayName = monogram
            avatarInfoApplied = true
            // Non-zero seed so palette isn't stuck on the first red slot for every topic.
            val seed = monogram.hashCode().toLong().let { if (it == 0L) 1L else it }
            avatarDrawable.setInfo(seed, monogram)
            if (avatarUrl.isBlank()) {
                remoteAvatarView.visibility = GONE
                avatarView.visibility = VISIBLE
                avatarView.invalidate()
            } else {
                avatarView.visibility = GONE
                remoteAvatarView.visibility = VISIBLE
                remoteAvatarView.setImage(
                    ImageLocation.getForPath(avatarUrl),
                    avatarImageFilter(avatarUrl, TOPIC_AVATAR_FILTER_SIZE),
                    avatarDrawable,
                    null,
                )
            }
        }
        requestLayout()
    }

    fun setIdentityVisible(visible: Boolean) {
        // ChatAvatarContainer uses alpha while searching; INVISIBLE keeps layout
        // space for glass middle-pill measure when we want the pill to stay.
        val target = if (visible) VISIBLE else INVISIBLE
        if (visibility != target) {
            visibility = target
        }
        alpha = if (visible) 1f else 0f
        isEnabled = visible
        isClickable = visible
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // ChatAvatarContainer.onMeasure: width from parent, availableWidth = width - 54 - 16.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 }
            ?: (ActionBar.getCurrentActionBarHeight() + if (occupyStatusBar) AndroidUtilities.statusBarHeight else 0)
        val avatarSize = AndroidUtilities.dp(TOPIC_IDENTITY_AVATAR_SIZE_DP.toFloat())
        val avatarSpec = MeasureSpec.makeMeasureSpec(avatarSize, MeasureSpec.EXACTLY)
        avatarView.measure(avatarSpec, avatarSpec)
        remoteAvatarView.measure(avatarSpec, avatarSpec)
        // ChatAvatarContainer: availableWidth = width - avatarColumn(54) - trailing(16).
        // Trailing reserves pill inner padding so long titles don't kiss the glass edge;
        // SimpleTextView fade/ellipsize paints in the right padding band.
        val availableWidth = (
            width
                - AndroidUtilities.dp(54f)
                - AndroidUtilities.dp(TOPIC_IDENTITY_TEXT_TRAILING_RESERVE_DP.toFloat())
        ).coerceAtLeast(AndroidUtilities.dp(32f))
        titleView.measure(
            MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24f + 8f), MeasureSpec.AT_MOST),
        )
        if (subtitleView.visibility != GONE) {
            subtitleView.measure(
                MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20f), MeasureSpec.AT_MOST),
            )
        } else {
            subtitleView.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // ChatAvatarContainer.onLayout geometry (avatar 42, leftPadding 8, title 1.3, subtitle 24).
        val actionBarHeight = ActionBar.getCurrentActionBarHeight()
        val statusInset = if (occupyStatusBar) AndroidUtilities.statusBarHeight else 0
        val avatarSize = AndroidUtilities.dp(TOPIC_IDENTITY_AVATAR_SIZE_DP.toFloat())
        val viewTop = (actionBarHeight - avatarSize) / 2 + statusInset
        avatarView.layout(
            leftPadding,
            viewTop + 1,
            leftPadding + avatarSize,
            viewTop + 1 + avatarSize,
        )
        remoteAvatarView.layout(
            leftPadding,
            viewTop + 1,
            leftPadding + avatarSize,
            viewTop + 1 + avatarSize,
        )
        val textLeft = leftPadding + AndroidUtilities.dp(54f)
        if (subtitleView.visibility != GONE) {
            val titleTop = viewTop + AndroidUtilities.dp(TOPIC_IDENTITY_TITLE_TOP_OFFSET_DP)
            titleView.layout(
                textLeft,
                titleTop,
                textLeft + titleView.measuredWidth,
                titleTop + titleView.measuredHeight,
            )
            val subtitleTop = viewTop + AndroidUtilities.dp(TOPIC_IDENTITY_SUBTITLE_TOP_OFFSET_DP)
            subtitleView.layout(
                textLeft,
                subtitleTop,
                textLeft + subtitleView.measuredWidth,
                subtitleTop + subtitleView.measuredHeight,
            )
        } else {
            val titleTop = viewTop + AndroidUtilities.dp(11f)
            titleView.layout(
                textLeft,
                titleTop,
                textLeft + titleView.measuredWidth,
                titleTop + titleView.measuredHeight,
            )
        }
    }
}

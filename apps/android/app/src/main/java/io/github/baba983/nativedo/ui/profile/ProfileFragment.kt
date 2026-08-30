/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's org/telegram/ui/ProfileActivity.java, with the
 * logged-out call to action adapted from org/telegram/ui/IntroActivity.java.
 */
package io.github.baba983.nativedo.ui.profile

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.text.Html
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import org.telegram.messenger.R as TelegramR
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.normalizeInlineHtml
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import io.github.baba983.nativedo.feature.profile.ui.ProfileUiState
import io.github.baba983.nativedo.feature.profile.ui.ProfileViewModel
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedUiState
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedViewModel
import io.github.baba983.nativedo.ui.auth.LoginWebViewFragment
import io.github.baba983.nativedo.ui.main.MainTabsActivity
import io.github.baba983.nativedo.ui.topic.TopicDetailFragment
import io.github.baba983.nativedo.ui.topic.toTopicInitialIdentity
import java.util.ArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MessageObject
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.QRCodeBottomSheet
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.AudioPlayerAlert
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.ProfileActivity
import org.telegram.ui.Components.ProfileGalleryBlurView
import org.telegram.ui.Components.ProfileGalleryView
import org.telegram.ui.Components.ProfileGooeyView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.ui.Components.ScaleStateListAnimator
import org.telegram.ui.Components.voip.CellFlickerDrawable
import org.telegram.ui.Cells.AboutLinkCell
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.PhotoViewer
import org.telegram.ui.PinchToZoomHelper

private const val VIEW_TYPE_ABOUT = 10_004
private const val VIEW_TYPE_PROFILE_LOADING = 10_006
private const val VIEW_TYPE_PROFILE_EMPTY = 10_007
private const val VIEW_TYPE_PROFILE_CONTENT = 10_008
private const val VIEW_TYPE_PROFILE_SESSION = 10_009
private const val PROFILE_RESUME_REFRESH_INTERVAL_MS = 30_000L

private const val ID_USERNAME = 10
private const val ID_JOINED = 11
private const val ACTION_RETRY_PROFILE = 3
private const val ACTION_LOGIN = 5
private const val MENU_OPEN_PROFILE = 101
private const val MENU_REFRESH_PROFILE = 102
private const val MENU_LOGIN = 103
private const val MENU_OTHER = 105

private val COPYABLE_ROW_IDS = setOf(
    ID_USERNAME,
)

class ProfileFragment : BaseFragment(), MainTabsActivity.TabFragmentDelegate {
    private var viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var viewModel: ProfileViewModel
    private lateinit var appContainer: AppContainer
    private var listView: UniversalRecyclerView? = null
    private var universalAdapter: UniversalAdapter? = null
    private var sessionState: AppSessionState = AppSessionState.Unknown
    private var profileState: ProfileUiState = ProfileUiState()

    private var topView: ProfileActivity.TopView? = null
    private var avatarContainer: FrameLayout? = null
    private var avatarGooey: ProfileGooeyView? = null
    private var avatarImageView: AvatarImageView? = null
    private var profileOverlaysView: ProfileActivity.OverlaysView? = null
    private var avatarsViewPager: ProfileGalleryView? = null
    private var avatarsBlurView: ProfileGalleryBlurView? = null
    private var avatarContainer2: FrameLayout? = null
    private var profileActionsView: DiscourseProfileActionsView? = null
    private var profileActionsEnabled: Boolean = false
    private var profileChromeVisible: Boolean = false
    private var otherItem: ActionBarMenuItem? = null
    private var nameTextView = arrayOfNulls<SimpleTextView>(2) // [0]=dark(collapsed), [1]=white(expanded)
    private var subtitleView = arrayOfNulls<SimpleTextView>(2)
    private var mediaCounterTextView: AudioPlayerAlert.ClippingTextViewSwitcher? = null
    private var avatarViewerUrl: String = ""
    private var avatarViewerTitle: String = ""
    private var avatarViewerSubtitle: String = ""
    private var avatarDisplayFilter: String = ""
    private var profileWebUrl: String = ""
    private var hasResumedOnce = false
    private var refreshProfileOnNextResume = false
    private var lastResumeRefreshAt = 0L
    private var selectedProfileFeedPage = ProfileFeedPage.Topics
    private var profileContentLayout: ProfileContentLayout? = null
    private var topicsFeedViewModel: ProfileFeedViewModel? = null
    private var repliesFeedViewModel: ProfileFeedViewModel? = null
    private var topicsFeedState = ProfileFeedUiState()
    private var repliesFeedState = ProfileFeedUiState()
    private var profileFeedUsername: String? = null
    private var topicsFeedJob: kotlinx.coroutines.Job? = null
    private var repliesFeedJob: kotlinx.coroutines.Job? = null
    private val profileContentFactory = ProfileContentFactory()
    private var navigationBarBottomInset = 0
    private var pinchToZoomHelper: PinchToZoomHelper? = null

    private var extraHeight: Float = 0f
    private var avatarY: Float = 0f
    private var avatarScale: Float = 1f
    private var pullUpProgress: Float = 0f
    private var expandProgress: Float = 0f
    private var isPulledDown: Boolean = false
    private var allowPullingDown: Boolean = false
    private var listViewVelocityY: Float = 0f
    private var canExpandAvatar: Boolean = false
    private var openingAvatar: Boolean = false
    // Mirrors upstream ProfileActivity.openAnimationInProgress. Guards
    // checkListViewScroll and needLayout against overwriting extraHeight
    // during a fragment open/close transition animation. NativeDo has no
    // transition animation today (always false), but the field exists so
    // that adding one later is safe and structurally matches upstream.
    private var openAnimationInProgress: Boolean = false
    private var justFullyExpanded = false
    private var initialProfileListAnchored = false
    private var outerListBottomPadding = 0
    private var profileListContentHeight = 0
    private var profileListContentGeneration = 0

    private var expandAnimator: ValueAnimator? = null
    private var currentExpandAnimatorValue: Float = 0f
    private var currentExpandAnimatorFraction: Float = 0f
    private var expandAnimatorValues: FloatArray = floatArrayOf(0f, 1f)
    // Preserve the live start position when a morph begins mid-scroll.
    private var avatarMorphStartTranslationY = 0f
    private var expandNameXStartedFrom = 0f
    private var expandNameYStartedFrom = 0f
    private var expandOnlineXStartedFrom = 0f
    private var expandOnlineYStartedFrom = 0f
    private var mediaHeaderAnimator: ValueAnimator? = null
    private var mediaHeaderVisible = false
    private var mediaHeaderAnimationProgress = 0f

    private fun getHeaderOnlyExtraHeight(): Int {
        if (!profileChromeVisible) return 0
        return AndroidUtilities.dp(if (getActionsExtraHeight() == 0) 168f else 152f)
    }

    private fun getActionsExtraHeight(): Int {
        return if (profileActionsEnabled) AndroidUtilities.dp(74f) else 0
    }

    private fun getHeaderExtraHeight(): Int = getHeaderOnlyExtraHeight() + getActionsExtraHeight()

    private fun calculateHeaderExtraDiff(): Float {
        return profileHeaderExtraDiff(extraHeight, getActionsExtraHeight(), getHeaderOnlyExtraHeight())
    }

    private fun updateProfileChromeAvailability(visible: Boolean) {
        if (profileChromeVisible == visible) {
            updateSessionChrome()
            return
        }
        profileChromeVisible = visible
        expandAnimator?.cancel()
        openingAvatar = false
        allowPullingDown = false
        isPulledDown = false
        currentExpandAnimatorValue = 0f
        currentExpandAnimatorFraction = 0f
        expandProgress = 0f
        pullUpProgress = 0f
        extraHeight = if (visible) getHeaderExtraHeight().toFloat() else 0f
        initialProfileListAnchored = false
        outerListBottomPadding = getOuterListBottomInset()
        updateSessionChrome()
        updateLayout()
    }

    private fun updateSessionChrome() {
        topView?.visibility = if (profileChromeVisible) View.VISIBLE else View.GONE
        avatarContainer2?.visibility = if (profileChromeVisible) View.VISIBLE else View.GONE
        if (profileChromeVisible) {
            actionBar.setTitle(null)
            actionBar.setCastShadows(false)
            actionBar.setBackgroundColor(Color.TRANSPARENT)
        } else {
            val title = context?.getString(R.string.tab_profile).orEmpty()
            if (actionBar.title != title) {
                actionBar.setTitle(title)
            }
            actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle))
            actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false)
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false)
            actionBar.setCastShadows(true)
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault))
        }
        // The profile surface stays gray in every session state: the white
        // logged-out paint here used to fill the whole body (list included)
        // on the first open after install, while the session was still being
        // resolved, and the loading skeleton was invisible on it.
        val background = Theme.getColor(Theme.key_windowBackgroundGray)
        fragmentView?.setBackgroundColor(background)
        listView?.setBackgroundColor(background)
        updateProfileActionBarMenu()
    }

    override fun createView(context: Context): View {
        Theme.createProfileResources(context)
        actionBar.setCastShadows(false)
        actionBar.setAddToContainer(false)
        actionBar.setClipContent(true)
        actionBar.setBackgroundColor(Color.TRANSPARENT)
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue), false)
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false)
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), true)
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet())
        // The back slot carries the profile QR entry (Telegram profile parity);
        // sharing stays in the actions row (分享资料).
        applyProfileQrBackButton()

        appContainer = (context.applicationContext as NativeDoApp).appContainer
        sessionState = appContainer.sessionCoordinator.sessionState.value
        profileChromeVisible = profileShowsIdentityChrome(sessionState)
        profileActionsEnabled = profileShowsIdentityChrome(sessionState)
        viewModel = ViewModelProvider(
            this,
            ProfileViewModelFactory(appContainer.profileRepository),
        )[ProfileViewModel::class.java]
        setupProfileActionBarMenu(context)
        (sessionState as? AppSessionState.Authenticated)?.username?.let(::ensureProfileFeedModels)

        extraHeight = getHeaderExtraHeight().toFloat()
        initialProfileListAnchored = false
        outerListBottomPadding = getOuterListBottomInset()
        profileListContentHeight = 0

        val container = ContainerView(context)
        container.setWillNotDraw(false)
        container.setBackgroundColor(0)
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            if (navigationBarBottomInset != bottomInset) {
                navigationBarBottomInset = bottomInset
                updateLayout()
                profileContentLayout?.setBottomContentInset(getProfileContentBottomInset())
            }
            insets
        }

        val recycler = object : UniversalRecyclerView(
            this,
            { items, adapter -> fillItems(items, adapter) },
            { item, view, _, _, _ -> handleItemClick(item, view) },
            { item, _, _, _, _ -> handleItemLongClick(item) },
        ) {
            private var velocityTracker: VelocityTracker? = null

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(e)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(e)
                        velocityTracker?.computeCurrentVelocity(1000)
                        listViewVelocityY = velocityTracker?.getYVelocity(e.getPointerId(e.actionIndex)) ?: 0f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        velocityTracker?.addMovement(e)
                        velocityTracker?.computeCurrentVelocity(1000)
                        listViewVelocityY = velocityTracker?.getYVelocity(e.getPointerId(e.actionIndex)) ?: 0f
                    }
                }
                val result = super.onTouchEvent(e)
                if (
                    e.actionMasked == MotionEvent.ACTION_MOVE &&
                    canExpandAvatar &&
                    avatarViewerUrl.isNotBlank() &&
                    extraHeight >= getProfileViewportWidth() + getActionsExtraHeight() - 1
                ) {
                    openAvatarViewer(ignoreDragging = true)
                    return false
                }
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (justFullyExpanded) {
                        justFullyExpanded = false
                        setFlingerStoppingEnabled(true)
                    }
                    settleHeaderAfterTouch()
                }
                return result
            }

        }
        recycler.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        recycler.setVerticalScrollBarEnabled(false)
        recycler.setSections(
            { view: View ->
                view.parent === recycler &&
                    recycler.isInsideForcedSection(recycler.getChildAdapterPosition(view))
            },
            { _: Int -> false },
            AndroidUtilities.dp(12f),
            AndroidUtilities.dpf2(16f),
            recycler::drawBackgroundRect,
            false,
        )
        recycler.applyPaddingToSections = false
        recycler.clipToPadding = false
        val initialListTopPadding = getProfileListTopPadding()
        recycler.setPadding(
            0,
            initialListTopPadding,
            0,
            getOuterListBottomInset(),
        )

        val recyclerRef = recycler
        val profileLayoutManager = object : LinearLayoutManager(context) {
            override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
                var adjustedDy = dy
                val view = findViewByPosition(0)
                if (view != null && !openingAvatar) {
                    val canScroll = view.top - getHeaderExtraHeight()
                    // Arm the overscroll pull only from touch-driven scrolling
                    // (drag/fling). Layout-correction scrolls right after the
                    // first anchor can transiently leave the row below the
                    // header; arming here used to pop the gallery open on its
                    // own on the first profile visit.
                    val touchScrolling = recyclerRef.scrollState == RecyclerView.SCROLL_STATE_DRAGGING ||
                        recyclerRef.scrollState == RecyclerView.SCROLL_STATE_SETTLING
                    if (!allowPullingDown && canScroll > adjustedDy && touchScrolling) {
                        adjustedDy = canScroll
                        if (avatarImageView?.imageReceiver?.hasNotThumb() == true &&
                            !AndroidUtilities.isAccessibilityScreenReaderEnabled()
                        ) {
                            allowPullingDown = true
                        }
                    } else if (allowPullingDown) {
                        if (adjustedDy >= canScroll) {
                            adjustedDy = canScroll
                            allowPullingDown = false
                        } else if (recyclerRef.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            if (!isPulledDown) {
                                adjustedDy /= 2
                            }
                        }
                    }
                }
                if (justFullyExpanded && !recyclerRef.isFlingerRunning) {
                    return 0
                }
                return super.scrollVerticallyBy(adjustedDy, recycler, state)
            }
        }
        recycler.layoutManager = profileLayoutManager
        recycler.setLayoutManager(profileLayoutManager)
        profileLayoutManager.scrollToPositionWithOffset(
            0,
            profileInitialRowOffset(getHeaderExtraHeight(), initialListTopPadding),
        )
        recycler.isNestedScrollingEnabled = true

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                checkListViewScroll()
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (openingAvatar && newState != RecyclerView.SCROLL_STATE_SETTLING) {
                    openingAvatar = false
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    snapFirstRowToHeaderAtRest()
                }
            }
        })
        // Match upstream ProfileActivity.onLayout: clear flags then re-sync
        // extraHeight from the actual row top via checkListViewScroll(). Do
        // NOT reposition here — upstream's onLayout never calls
        // scrollToPositionWithOffset. ensureInitialProfileListAnchor handles
        // the first-layout anchor with its own latch guard.
        recycler.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            recycler.post {
                ensureInitialProfileListAnchor()
                checkListViewScroll()
            }
        }
        container.addView(recycler, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL))

        val tv = ProfileActivity.TopView(context).apply {
            actionBar = this@ProfileFragment.actionBar
        }
        tv.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        container.addView(tv)
        topView = tv
        val ac2 = FrameLayout(context)
        container.addView(ac2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.MATCH_PARENT.toFloat(), Gravity.START, 0f, 0f, 0f, 0f))
        avatarContainer2 = ac2

        val ac = object : FrameLayout(context) {
            override fun setScaleX(scaleX: Float) {
                super.setScaleX(scaleX)
                updateGooey()
            }

            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                updateGooey()
            }
        }
        ac.setPivotX(0f)
        ac.setPivotY(0f)
        avatarContainer = ac

        val gooey = ProfileGooeyView(context)
        gooey.addView(ac, LayoutHelper.createFrame(100f, 100f, Gravity.TOP or Gravity.LEFT))
        ac2.addView(gooey, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        avatarGooey = gooey

        val aiv = AvatarImageView(context)
        aiv.setOnClickListener {
            if (!expandAvatarOnClick()) {
                openAvatarViewer()
            }
        }
        aiv.setOnLongClickListener {
            openAvatarViewer()
            true
        }
        ac.addView(aiv, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        avatarImageView = aiv

        val galleryBlur = ProfileGalleryBlurView(context).apply {
            setSize(getActionsExtraHeight())
            visibility = View.GONE
        }
        val gallery = ProfileGalleryView(context, recycler).apply {
            setBlurView(galleryBlur)
            setActionsHeight(getActionsExtraHeight())
            visibility = View.GONE
        }
        ac2.addView(gallery, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        ac2.addView(
            galleryBlur,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                (getActionsExtraHeight() + AndroidUtilities.dp(64f) * 1.5f) /
                    AndroidUtilities.density,
            ),
        )
        avatarsViewPager = gallery
        avatarsBlurView = galleryBlur
        aiv.createBlurEffect(getActionsExtraHeight())
        aiv.setAvatarsViewPager(gallery)

        val overlaysView = ProfileActivity.OverlaysView(context).apply {
            actionBar = this@ProfileFragment.actionBar
            avatarsViewPager = gallery
            actionsExtraHeight = getActionsExtraHeight()
        }
        ac2.addView(overlaysView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        profileOverlaysView = overlaysView

        val name0 = SimpleTextView(context)
        name0.setTextSizePx(AndroidUtilities.dp(17.5f))
        name0.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle))
        name0.setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(12f))
        name0.setGravity(Gravity.LEFT)
        name0.setTypeface(AndroidUtilities.bold())
        name0.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f))
        name0.setPivotX(0f)
        name0.setPivotY(0f)
        name0.setEllipsizeByGradient(true)
        name0.setAlpha(0f) // hidden by default (shown when collapsed)
        ac2.addView(name0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 109f, -6f, 40f, 0f))
        nameTextView[0] = name0

        val name1 = SimpleTextView(context)
        name1.setTextSizePx(AndroidUtilities.dp(17.5f))
        name1.setTextColor(Theme.getColor(Theme.key_profile_title))
        name1.setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(4f))
        name1.setGravity(Gravity.LEFT)
        name1.setTypeface(AndroidUtilities.bold())
        name1.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f))
        name1.setPivotX(0f)
        name1.setPivotY(0f)
        name1.setEllipsizeByGradient(true)
        name1.setScrollNonFitText(true)
        name1.setAlpha(1f) // shown by default
        ac2.addView(name1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 109f, -6f, 0f, 0f))
        nameTextView[1] = name1

        val sub0 = SimpleTextView(context)
        sub0.setTextSizePx(AndroidUtilities.dp(13.5f))
        sub0.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        sub0.setGravity(Gravity.LEFT)
        sub0.setPivotX(AndroidUtilities.dp(8f).toFloat())
        sub0.setPivotY(AndroidUtilities.dp(8f).toFloat())
        sub0.setEllipsizeByGradient(true)
        sub0.setAlpha(0f)
        ac2.addView(sub0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 109f, 0f, 40f, 0f))
        subtitleView[0] = sub0

        val sub1 = SimpleTextView(context)
        sub1.setTextSizePx(AndroidUtilities.dp(13.5f))
        sub1.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        sub1.setPadding(AndroidUtilities.dp(4f), AndroidUtilities.dp(2f), AndroidUtilities.dp(4f), AndroidUtilities.dp(2f))
        sub1.setGravity(Gravity.LEFT)
        sub1.setPivotX(AndroidUtilities.dp(8f).toFloat())
        sub1.setPivotY(AndroidUtilities.dp(8f).toFloat())
        sub1.setEllipsizeByGradient(true)
        sub1.setAlpha(1f)
        ac2.addView(sub1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 105f, -2f, 4f, 0f))
        subtitleView[1] = sub1

        val mediaCounter = object : AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            override fun createTextView(): TextView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_player_actionBarSubtitle))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, AndroidUtilities.dp(13.5f).toFloat())
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.LEFT
            }
        }
        mediaCounter.alpha = 0f
        ac2.addView(
            mediaCounter,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT.toFloat(),
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.LEFT or Gravity.TOP,
                109.33f,
                -2f,
                8f,
                0f,
            ),
        )
        mediaCounterTextView = mediaCounter
        updateSelectedProfileFeedText(animated = false)

        val actions = DiscourseProfileActionsView(context, AndroidUtilities.dp(74f)).apply {
            setOnActionClickListener { key, _, _ -> handleProfileAction(key) }
            visibility = if (profileActionsEnabled) View.VISIBLE else View.GONE
        }
        ac2.addView(actions, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        profileActionsView = actions
        galleryBlur.setActionsView(actions)
        updateProfileActions(animated = false)
        actions.post(actions::finishOpeningLayout)

        container.addView(actionBar)

        listView = recycler
        universalAdapter = recycler.adapter
        fragmentView = container

        setupPinchToZoom(container)

        updateSessionChrome()
        updateLayout()
        updateInfo()

        expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { anim -> setAvatarExpandProgress(anim.animatedFraction) }
        }

        viewScope.launch {
            appContainer.sessionCoordinator.sessionState.collectLatest { state ->
                sessionState = state
                updateProfileChromeAvailability(profileShowsIdentityChrome(state))
                if (state is AppSessionState.Authenticated) {
                    ensureProfileFeedModels(state.username)
                } else {
                    clearProfileFeedModels()
                }
                updateProfileActionsAvailability(profileShowsIdentityChrome(state))
                viewModel.onSessionStateChanged(state)
                updateInfo()
                updateList()
            }
        }
        viewScope.launch {
            viewModel.uiState.collectLatest { state ->
                profileState = state
                updateInfo()
                updateList()
            }
        }

        return container
    }

    override fun clearViews() {
        // Bottom-tab recreation destroys the view tree but keeps the fragment:
        // cancel the collectors with the old views, then rebuild the scope so
        // the next createView() can relaunch them.
        viewScope.cancel()
        clearProfileFeedModels()
        viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        super.clearViews()
    }

    private fun ensureProfileFeedModels(username: String) {
        if (profileFeedUsername == username && topicsFeedViewModel != null && repliesFeedViewModel != null) {
            return
        }
        topicsFeedJob?.cancel()
        repliesFeedJob?.cancel()
        profileFeedUsername = username
        topicsFeedState = ProfileFeedUiState()
        repliesFeedState = ProfileFeedUiState()

        val repository = ProfileFeedRepository(appContainer.discourseApiClient)
        val topics = ViewModelProvider(
            this,
            ProfileFeedViewModelFactory(
                username,
                io.github.baba983.nativedo.feature.profile.ui.ProfileFeedType.Topics,
                repository,
                appContainer.categoryStore,
            ),
        ).get("profile-feed:$username:topics", ProfileFeedViewModel::class.java)
        val replies = ViewModelProvider(
            this,
            ProfileFeedViewModelFactory(
                username,
                io.github.baba983.nativedo.feature.profile.ui.ProfileFeedType.Replies,
                repository,
                appContainer.categoryStore,
            ),
        ).get("profile-feed:$username:replies", ProfileFeedViewModel::class.java)
        topicsFeedViewModel = topics
        repliesFeedViewModel = replies
        topicsFeedJob = viewScope.launch {
            topics.uiState.collectLatest { state ->
                topicsFeedState = state
                profileContentLayout?.submit(ProfileFeedPage.Topics, state)
                updateSelectedProfileFeedText()
            }
        }
        repliesFeedJob = viewScope.launch {
            replies.uiState.collectLatest { state ->
                repliesFeedState = state
                profileContentLayout?.submit(ProfileFeedPage.Replies, state)
                updateSelectedProfileFeedText()
            }
        }
    }

    private fun clearProfileFeedModels() {
        profileFeedUsername = null
        topicsFeedJob?.cancel()
        repliesFeedJob?.cancel()
        topicsFeedJob = null
        repliesFeedJob = null
        topicsFeedViewModel = null
        repliesFeedViewModel = null
        topicsFeedState = ProfileFeedUiState()
        repliesFeedState = ProfileFeedUiState()
        profileContentLayout = null
    }

    private fun getStatusBarHeight(): Int = AndroidUtilities.getStatusBarHeight(context)

    private fun getActionBarHeight(): Int = ActionBar.getCurrentActionBarHeight()

    private fun getHeaderTopOffset(): Int = getStatusBarHeight() + getActionBarHeight()

    private fun getProfileContentBottomInset(): Int = navigationBarBottomInset +
        AndroidUtilities.dp((MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 12).toFloat())

    private fun getOuterListBottomInset(): Int = if (sessionState is AppSessionState.Authenticated) {
        0
    } else {
        getProfileContentBottomInset()
    }

    private fun getProfileListTopPadding(): Int = if (profileChromeVisible) {
        profileSessionListTopPadding(
            profileChromeVisible = true,
            profileTopPadding = profileListTopPadding(
                viewportWidth = getProfileViewportWidth(),
                actionsExtraHeight = getActionsExtraHeight(),
            ),
        )
    } else {
        profileSessionListTopPadding(profileChromeVisible = false, profileTopPadding = 0)
    }

    private fun updateLayout() {
        (listView?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            val topMargin = getHeaderTopOffset()
            if (params.topMargin != topMargin) {
                params.topMargin = topMargin
                listView?.layoutParams = params
            }
        }
        val topPadding = getProfileListTopPadding()
        val bottomPadding = outerListBottomPadding.coerceAtLeast(getOuterListBottomInset())
        val rv = listView
        val paddingChanged = rv != null &&
            (rv.paddingTop != topPadding || rv.paddingBottom != bottomPadding)
        rv?.setPadding(0, topPadding, 0, bottomPadding)

        // Match upstream ProfileActivity: only reposition the first row when
        // the padding actually changed or the scroll was invalidated. An
        // unconditional scrollToPositionWithOffset on every onResume/layout
        // races with checkListViewScroll — it re-pins the row to a stale
        // extraHeight, then checkListViewScroll reads the new top and writes
        // extraHeight again, producing a visible one-frame avatar/text jump
        // ("drag") when switching tabs back to profile.
        if (paddingChanged) {
            val layoutManager = rv?.layoutManager as? LinearLayoutManager
            val view = layoutManager?.findViewByPosition(0)
            if (view != null) {
                var top = view.top
                val pos = 0
                if (pos == 0 && !allowPullingDown && top > getHeaderExtraHeight()) {
                    top = getHeaderExtraHeight()
                }
                layoutManager.scrollToPositionWithOffset(pos, top - topPadding)
            }
        }

        needLayout()
    }

    private fun updateActionsPosition() {
        val actions = profileActionsView ?: return
        if (!profileActionsEnabled) {
            actions.visibility = View.GONE
            return
        }
        actions.visibility = View.VISIBLE
        actions.setParentExpanded(currentExpandAnimatorValue)
        val newTop = getHeaderTopOffset()
        val bottom = extraHeight + newTop
        val height = Math.min(getActionsExtraHeight().toFloat(), bottom - newTop).coerceAtLeast(0f)
        actions.updatePosition(bottom - height, height)
    }

    private fun updateProfileActionsAvailability(enabled: Boolean) {
        if (profileActionsEnabled == enabled) {
            updateProfileActions(animated = true)
            return
        }
        val oldHeaderHeight = getHeaderExtraHeight()
        val oldExtraHeight = extraHeight
        profileActionsEnabled = enabled
        val newHeaderHeight = getHeaderExtraHeight()
        val mappedExtraHeight = if (oldExtraHeight > oldHeaderHeight) {
            newHeaderHeight + (oldExtraHeight - oldHeaderHeight)
        } else if (oldHeaderHeight > 0) {
            oldExtraHeight / oldHeaderHeight * newHeaderHeight
        } else {
            newHeaderHeight.toFloat()
        }

        profileActionsView?.visibility = if (enabled) View.VISIBLE else View.GONE
        avatarsViewPager?.setActionsHeight(getActionsExtraHeight())
        avatarsBlurView?.setSize(getActionsExtraHeight())
        avatarImageView?.createBlurEffect(getActionsExtraHeight())
        updateProfileActions(animated = true)
        val newTopPadding = getProfileListTopPadding()
        val rv = listView
        val paddingChanged = rv != null && rv.paddingTop != newTopPadding
        rv?.setPadding(
            0,
            newTopPadding,
            0,
            outerListBottomPadding.coerceAtLeast(getOuterListBottomInset()),
        )
        val layoutManager = rv?.layoutManager as? LinearLayoutManager
        val firstRow = layoutManager?.findViewByPosition(0)
        // Match upstream ProfileActivity onMeasure: only reposition when
        // padding changed (currentPaddingTop != paddingTop). When at rest,
        // anchor to the new header height; otherwise map the old scroll
        // proportionally — upstream uses the same at-rest vs mapped split.
        if (firstRow != null && paddingChanged) {
            val atRest = firstRow.top >= 0 &&
                firstRow.top <= oldHeaderHeight + AndroidUtilities.dp(24f)
            val targetExtra = if (atRest) newHeaderHeight.toFloat() else mappedExtraHeight
            layoutManager.scrollToPositionWithOffset(0, targetExtra.toInt() - newTopPadding)
            checkListViewScroll()
        } else if (firstRow != null) {
            checkListViewScroll()
        } else {
            extraHeight = mappedExtraHeight
            needLayout()
        }
    }

    private fun updateProfileActions(animated: Boolean) {
        val actions = profileActionsView ?: return
        updateProfileActionsColor()
        if (!profileActionsEnabled) {
            actions.setActions(emptyList(), animated)
            return
        }
        actions.setActions(
            listOf(
                DiscourseProfileActionsView.ActionSpec(
                    DiscourseProfileActionsView.KEY_EDIT_PROFILE,
                    actions.context.getString(R.string.profile_action_edit),
                    TelegramR.drawable.filled_profile_edit_24,
                    TelegramR.drawable.outline_profile_edit_24,
                ),
                DiscourseProfileActionsView.ActionSpec(
                    DiscourseProfileActionsView.KEY_TOPICS,
                    actions.context.getString(R.string.profile_action_topics),
                    TelegramR.drawable.tabs_chats_active_24,
                    TelegramR.drawable.tabs_chats_24,
                ),
                DiscourseProfileActionsView.ActionSpec(
                    DiscourseProfileActionsView.KEY_REPLIES,
                    actions.context.getString(R.string.profile_action_replies),
                    TelegramR.drawable.filled_profile_message_24,
                    TelegramR.drawable.outline_profile_message_24,
                ),
                DiscourseProfileActionsView.ActionSpec(
                    DiscourseProfileActionsView.KEY_SHARE,
                    actions.context.getString(R.string.profile_action_share),
                    TelegramR.drawable.action_share,
                    TelegramR.drawable.msg_share,
                ),
            ),
            animated,
        )
    }

    private fun updateProfileActionsColor() {
        val actions = profileActionsView ?: return
        val actionBarColor = Theme.getColor(Theme.key_actionBarDefault)
        val brightness = AndroidUtilities.computePerceivedBrightness(actionBarColor)
        val buttonColor = when {
            brightness > .8f -> Color.WHITE
            brightness < .2f -> Theme.multAlpha(Theme.adaptHSV(actionBarColor, .02f, .25f), .35f)
            else -> {
                val adapted = Theme.adaptHSV(actionBarColor, .5f, -.28f)
                Theme.multAlpha(adapted, .15f)
            }
        }
        actions.setActionsColor(buttonColor)
    }

    // The profile row target: the bound profile first, else the live session identity.
    private fun currentUsername(): String? =
        profileState.username
            ?: (sessionState as? AppSessionState.Authenticated)?.username
            ?: (sessionState as? AppSessionState.Restoring)?.snapshot?.username

    private fun handleProfileAction(key: Int) {
        val username = currentUsername() ?: return
        when (key) {
            DiscourseProfileActionsView.KEY_EDIT_PROFILE -> {
                refreshProfileOnNextResume = true
                openBrowser("${appContainer.baseUrl.trimEnd('/')}/u/$username/preferences/profile")
            }
            DiscourseProfileActionsView.KEY_TOPICS -> {
                openProfileContent(ProfileFeedPage.Topics)
            }
            DiscourseProfileActionsView.KEY_REPLIES -> {
                openProfileContent(ProfileFeedPage.Replies)
            }
            DiscourseProfileActionsView.KEY_SHARE -> shareProfile()
        }
    }

    private fun openProfileContent(page: ProfileFeedPage) {
        selectedProfileFeedPage = page
        updateSelectedProfileFeedText()
        profileContentLayout?.selectPage(page, animated = true)
        val recycler = listView ?: return
        val lastPosition = (recycler.adapter?.itemCount ?: 0) - 1
        if (lastPosition >= 0) {
            recycler.smoothScrollToPosition(lastPosition)
        }
    }

    private fun updateSelectedProfileFeedText(animated: Boolean = true) {
        val counter = mediaCounterTextView ?: return
        val context = getContext() ?: return
        val state = when (selectedProfileFeedPage) {
            ProfileFeedPage.Topics -> topicsFeedState
            ProfileFeedPage.Replies -> repliesFeedState
        }
        val count = profileFeedSubtitleCount(state)
        val text = when (selectedProfileFeedPage) {
            ProfileFeedPage.Topics -> when {
                count == null -> context.getString(R.string.profile_tab_topics)
                count == 0 -> context.getString(R.string.profile_feed_topics_count_zero)
                state.hasMore -> context.getString(R.string.profile_feed_topics_count_more, count)
                else -> context.getString(R.string.profile_feed_topics_count, count)
            }
            ProfileFeedPage.Replies -> when {
                count == null -> context.getString(R.string.profile_tab_replies)
                count == 0 -> context.getString(R.string.profile_feed_replies_count_zero)
                state.hasMore -> context.getString(R.string.profile_feed_replies_count_more, count)
                else -> context.getString(R.string.profile_feed_replies_count, count)
            }
        }
        counter.setText(text, animated && mediaHeaderAnimationProgress > 0f)
    }

    // Back-slot QR entry (Telegram profile parity): outline_header_qr_24
    // icon tinted like the other header icons, opening the QR share sheet.
    private fun applyProfileQrBackButton() {
        actionBar.setBackButtonImage(TelegramR.drawable.outline_header_qr_24)
        actionBar.backButtonImageView.setColorFilter(
            Theme.getColor(Theme.key_actionBarDefaultIcon),
            PorterDuff.Mode.SRC_IN,
        )
        actionBar.setBackButtonContentDescription(context?.getString(R.string.profile_qr_code))
        actionBar.backButtonImageView.setOnClickListener { openProfileQr() }
    }

    private fun openProfileQr() {
        val ctx = context ?: return
        val username = currentUsername() ?: return
        val link = "${appContainer.baseUrl.trimEnd('/')}/u/$username"
        val sheet = QRCodeBottomSheet(
            ctx,
            avatarViewerTitle.ifBlank { "@$username" },
            link,
            ctx.getString(R.string.profile_qr_help),
            true,
        )
        // Pure QR for the short profile URL: the vendor sheet's center
        // overlay only scales sensibly for long invite-link payloads.
        sheet.show()
    }

    private fun shareProfile() {
        val activity = parentActivity ?: return
        val url = profileWebUrl.takeIf(String::isNotBlank) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, avatarViewerTitle)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.profile_action_share)))
    }

    private fun needLayout() {
        if (!profileChromeVisible) {
            listView?.setTopGlowOffset(0)
            listView?.overScrollMode = View.OVER_SCROLL_NEVER
            return
        }
        val diff = calculateHeaderExtraDiff()
        val statusBarHeight = getStatusBarHeight()
        val actionBarHeight = getActionBarHeight()
        val newTop = statusBarHeight + actionBarHeight
        val width = getProfileViewportWidth()

        // Keep the overscroll glow below the live header.
        listView?.setTopGlowOffset(extraHeight.toInt())
        listView?.overScrollMode = if (
            extraHeight > getHeaderExtraHeight() &&
            extraHeight < (listView?.measuredWidth ?: 0) + getActionsExtraHeight() - newTop
        ) {
            View.OVER_SCROLL_NEVER
        } else {
            View.OVER_SCROLL_ALWAYS
        }

        topView?.let { tv ->
            tv.extraHeight = extraHeight
            tv.mediaHeaderAnimationProgress = mediaHeaderAnimationProgress
            val lp = tv.layoutParams as? FrameLayout.LayoutParams ?: return@let
            val h = statusBarHeight + actionBarHeight + extraHeight
            if (lp.height != h.toInt()) {
                lp.height = h.toInt()
                lp.topMargin = 0
                tv.layoutParams = lp
            }
        }

        val h = extraHeight

        // The scroll branch and animator must not write translationY in the same frame.
        val notchInfo = avatarGooey?.notchInfo
        val endY = if (notchInfo != null && notchInfo.isLikelyCircle) {
            notchInfo.bounds.centerY()
        } else {
            -AndroidUtilities.dpf2(29f)
        }
        val startY = newTop - AndroidUtilities.dpf2(21f)
        avatarY = AndroidUtilities.lerp(endY, startY, diff)

        if (h > getHeaderExtraHeight() || isPulledDown) {
            val expandDistance = (width - newTop - getHeaderOnlyExtraHeight()).toFloat().coerceAtLeast(1f)
            expandProgress = Utilities.clamp01((h - getHeaderExtraHeight()) / expandDistance)
            avatarScale = AndroidUtilities.lerp(96f / 42f, 138f / 42f,
                Math.min(1f, expandProgress * 3f)) / 100f * 42f
            pullUpProgress = 0f
            val inExpandedAvatarZone = profileKeepsAvatarExpanded(
                allowPullingDown = allowPullingDown,
                openingAvatar = openingAvatar,
                expandProgress = expandProgress,
            )
            val durationFactor = Math.min(
                AndroidUtilities.dpf2(2000f),
                Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY)),
            ) / AndroidUtilities.dpf2(1100f)

            if (inExpandedAvatarZone) {
                if (!isPulledDown) {
                    isPulledDown = true
                    // Expanded media uses light system-bar icons.
                    NotificationCenter.getGlobalInstance().postNotificationName(
                        NotificationCenter.needCheckSystemBarColors,
                        true,
                    )
                    profileOverlaysView?.setOverlaysVisible(true, durationFactor)
                    // Keep decoding through the avatar-to-gallery handoff.
                    avatarImageView?.let { aiv ->
                        aiv.setForegroundImage(
                            avatarsViewPager?.getImageLocation(0),
                            avatarDisplayFilter,
                            aiv.imageReceiver.getDrawable(),
                        )
                    }
                    avatarImageView?.drawForeground(true)
                    avatarImageView?.setForegroundAlpha(currentExpandAnimatorValue)
                    avatarImageView?.let { avatarsViewPager?.prepareTransitionFrom(it) }
                    avatarContainer?.visibility = View.VISIBLE
                    avatarsViewPager?.alpha = 0f
                    avatarsViewPager?.visibility = View.VISIBLE

                    val animator = expandAnimator
                    if (animator != null) {
                        // Resume a running reversal from its live value; otherwise start cleanly.
                        val wasRunning = animator.isRunning
                        animator.cancel()
                        val value = if (wasRunning) {
                            AndroidUtilities.lerp(
                                expandAnimatorValues[0],
                                expandAnimatorValues[1],
                                currentExpandAnimatorFraction,
                            )
                        } else {
                            0f
                        }
                        expandAnimatorValues[0] = value
                        expandAnimatorValues[1] = 1f
                        avatarMorphStartTranslationY = avatarContainer?.translationY ?: 0f
                        animator.interpolator = CubicBezierInterpolator.EASE_BOTH
                        animator.duration = Math.max(1L, ((1f - value) * 250f / durationFactor).toLong())
                        animator.addListener(createExpandAnimatorListener())

                        // Drive the list and morph with one non-interruptible timing curve.
                        val firstView = (listView?.layoutManager as? LinearLayoutManager)?.findViewByPosition(0)
                        if (firstView != null) {
                            justFullyExpanded = true
                            listView?.smoothScrollBy(
                                0,
                                firstView.top -
                                    (listView?.measuredWidth ?: 0) -
                                    getActionsExtraHeight() +
                                    getHeaderTopOffset(),
                                animator.duration.toInt(),
                                CubicBezierInterpolator.EASE_BOTH,
                                false,
                            )
                        }
                        expandNameXStartedFrom = nameTextView[1]?.translationX ?: 0f
                        expandNameYStartedFrom = nameTextView[1]?.translationY ?: 0f
                        expandOnlineXStartedFrom = subtitleView[1]?.translationX ?: 0f
                        expandOnlineYStartedFrom = subtitleView[1]?.translationY ?: 0f
                        animator.start()
                    }

                    try {
                        avatarContainer?.performHapticFeedback(
                            android.view.HapticFeedbackConstants.LONG_PRESS,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                        )
                    } catch (_: Exception) {}
                }

                avatarsViewPager?.updateSize(width, (h + newTop).toInt())

                if (expandAnimator?.isRunning != true) {
                    val stableExpandedMedia = profileUsesFullAvatarGeometry(
                        isPulledDown = isPulledDown,
                        expandAnimatorValue = currentExpandAnimatorValue,
                    )
                    if (stableExpandedMedia) {
                        avatarContainer?.visibility = View.GONE
                        avatarsViewPager?.alpha = 1f
                        avatarsViewPager?.visibility = View.VISIBLE
                        // Avoid an async decode swap at the ownership boundary.
                        avatarImageView?.let { avatarsViewPager?.showAvatarContent(it) }
                        nameTextView[1]?.let { nt ->
                            nt.translationX = AndroidUtilities.dpf2(18f) - nt.left
                            nt.translationY = newTop + h - getActionsExtraHeight() - AndroidUtilities.dpf2(30f) - nt.bottom
                        }
                        subtitleView[1]?.let { sv ->
                            sv.translationX = AndroidUtilities.dpf2(16f) - sv.left
                            sv.translationY = newTop + h - getActionsExtraHeight() - AndroidUtilities.dpf2(10f) - sv.bottom
                            mediaCounterTextView?.translationX = sv.translationX
                            mediaCounterTextView?.translationY = sv.translationY
                        }
                    }
                }
            } else {
                if (isPulledDown) {
                    startCollapseMorph(durationFactor)
                }

                // Keep the 100dp container stable; pull changes use transforms.
                avatarContainer?.let { ac ->
                    if (expandAnimator?.isRunning != true) {
                        val params = ac.layoutParams as FrameLayout.LayoutParams
                        val collapsedSize = AndroidUtilities.dpf2(100f).toInt()
                        if (params.width != collapsedSize || params.height != collapsedSize || params.leftMargin != 0) {
                            params.width = collapsedSize
                            params.height = collapsedSize
                            params.leftMargin = 0
                            ac.requestLayout()
                        }
                    }
                    ac.scaleX = avatarScale
                    ac.scaleY = avatarScale
                    val params = ac.layoutParams as FrameLayout.LayoutParams
                    ac.translationX = width / 2f - params.width * avatarScale * 0.5f
                }
                // Track the title continuously until the pull crosses the threshold.
                if (expandAnimator?.isRunning != true) {
                    avatarImageView?.setRoundRadiusForExpand(AndroidUtilities.dp(50f))
                    val endNameY = statusBarHeight + actionBarHeight / 2f * (1f + diff) -
                        AndroidUtilities.dp(21f)
                    layoutNameAndSubtitle(diff, newTop, width, endNameY)
                }
            }
        }

        else {
            expandProgress = 0f

            // Settle an expansion interrupted below its normal collapse threshold.
            if (isPulledDown) {
                startCollapseMorph(1f)
                return
            }

            val notchInfo = avatarGooey?.notchInfo
            val intoSize = if (notchInfo != null && notchInfo.isLikelyCircle) {
                0.5f * notchInfo.bounds.width() / AndroidUtilities.density
            } else {
                24f
            }
            avatarScale = AndroidUtilities.lerp(intoSize, 96f, diff) / 100f
            pullUpProgress = 1.0f - diff

            updateGooey()

            // Do not overwrite a running 1→0 gallery morph. Its final frame already
            // lands on this same 100dp/radius-50 geometry using the current scroll data.
            if (expandAnimator?.isRunning != true) {
                currentExpandAnimatorValue = 0f
                currentExpandAnimatorFraction = 0f
                expandAnimatorValues = floatArrayOf(0f, 1f)
                avatarImageView?.setBlurRadiusProgressForExpand(0f, avatarScale, false)
                avatarsViewPager?.visibility = View.GONE

                avatarContainer?.let { ac ->
                    val params = ac.layoutParams as FrameLayout.LayoutParams
                    val collapsedSize = AndroidUtilities.dpf2(100f).toInt()
                    if (params.width != collapsedSize || params.height != collapsedSize || params.leftMargin != 0) {
                        params.width = collapsedSize
                        params.height = collapsedSize
                        params.leftMargin = 0
                        ac.requestLayout()
                    }

                    ac.scaleX = avatarScale
                    ac.scaleY = avatarScale

                    val avatarX = width / 2f - params.width * avatarScale * 0.5f
                    ac.translationX = avatarX
                    ac.translationY = Math.ceil(avatarY.toDouble()).toFloat()

                    avatarImageView?.setRoundRadiusForExpand(AndroidUtilities.dp(50f))
                }

                val endNameY = statusBarHeight + actionBarHeight / 2f * (1f + diff) -
                    AndroidUtilities.dp(21f)
                layoutNameAndSubtitle(diff, newTop, width, endNameY)
            }
        }

        // Normal scroll updates widths without forcing per-frame relayout.
        applyExpandColorBlends(
            currentExpandAnimatorValue,
            requestLayout = h > getHeaderExtraHeight() || isPulledDown,
        )
        updateActionsPosition()

        // Restrict the fading scrims to the live gallery area.
        val overlays = profileOverlaysView
        if (overlays != null && (isPulledDown || overlays.isOverlaysVisible())) {
            val lp = overlays.layoutParams
            lp.width = listView?.measuredWidth ?: width
            lp.height = (extraHeight + newTop).toInt()
            overlays.requestLayout()
        }
    }

    private fun setupPinchToZoom(container: ContainerView) {
        val activity = parentActivity ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        pinchToZoomHelper = object : PinchToZoomHelper(decorView, container) {
            override fun invalidateViews() {
                super.invalidateViews()
                fragmentView?.invalidate()
            }

            override fun drawOverlays(
                canvas: Canvas,
                alpha: Float,
                parentOffsetX: Float,
                parentOffsetY: Float,
                clipTop: Float,
                clipBottom: Float,
            ) {
                if (alpha > 0) {
                    // The collapsed avatar can start the pinch while the gallery
                    // is GONE, so its measured size is not a valid overlay bound.
                    // Use the carrier container until the gallery takes over.
                    val pagerW = avatarsViewPager?.measuredWidth?.takeIf { it > 0 }
                        ?: container.measuredWidth
                    val pagerH = avatarsViewPager?.measuredHeight?.takeIf { it > 0 }
                        ?: container.measuredHeight
                    AndroidUtilities.rectTmp.set(
                        0f,
                        0f,
                        pagerW.toFloat(),
                        pagerH.toFloat() + AndroidUtilities.dp(30f).toFloat(),
                    )
                    canvas.saveLayerAlpha(
                        AndroidUtilities.rectTmp,
                        (255 * alpha).toInt(),
                        Canvas.ALL_SAVE_FLAG,
                    )
                    avatarContainer2?.draw(canvas)
                    canvas.save()
                    canvas.translate(actionBar.x.toFloat(), actionBar.y.toFloat())
                    actionBar.draw(canvas)
                    canvas.restore()
                    canvas.restore()
                }
            }

            override fun zoomEnabled(child: View, receiver: ImageReceiver): Boolean {
                if (!super.zoomEnabled(child, receiver)) return false
                return listView?.scrollState != RecyclerView.SCROLL_STATE_DRAGGING
            }
        }.apply {
            setCallback(object : PinchToZoomHelper.Callback {
                override fun onZoomStarted(messageObject: MessageObject?) {
                    listView?.cancelClickRunnables(true)
                    val receiver = pinchToZoomHelper?.photoImage
                        ?: avatarImageView?.imageReceiver
                        ?: return
                    topView?.setBackgroundColor(
                        ColorUtils.blendARGB(
                            profileHeaderAverageColor(receiver),
                            Theme.getColor(Theme.key_windowBackgroundGray),
                            0.1f,
                        ),
                    )
                }

                override fun onZoomFinished(messageObject: MessageObject?) {
                    avatarsBlurView?.restartAlpha()
                }
            })
        }
        avatarsViewPager?.setPinchToZoomHelper(pinchToZoomHelper)
        avatarImageView?.let { aiv ->
            aiv.pinchToZoomHandler = { event ->
                pinchToZoomHelper?.checkPinchToZoom(
                    event,
                    aiv,
                    aiv.imageReceiver,
                    null,
                    null,
                    null,
                ) == true
            }
        }
    }

    private fun profileHeaderAverageColor(receiver: ImageReceiver): Int {
        // NativeDo profile avatars are bitmap-backed, so no vector-gradient branch is needed.
        return AndroidUtilities.calcBitmapColor(receiver.bitmap)
    }

    // Collapse an interrupted expansion back into the 100dp header avatar,
    // morphing from the gallery's current drawable. Shared by the expanded-zone
    // and collapsed-zone settle paths in [needLayout].
    private fun startCollapseMorph(durationFactor: Float) {
        isPulledDown = false
        topView?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needCheckSystemBarColors,
            true,
        )
        profileOverlaysView?.setOverlaysVisible(false, durationFactor)
        val animator = expandAnimator
        if (animator != null) {
            animator.cancel()
            avatarImageView?.imageReceiver?.setAllowStartAnimation(true)
            avatarImageView?.imageReceiver?.startAnimation()
            // Start collapse from the current gallery size.
            val collapseWidth = getProfileViewportWidth()
            val collapseTop = getStatusBarHeight() + getActionBarHeight()
            val currentVisualProgress = Utilities.clamp01(
                ((extraHeight + collapseTop) - AndroidUtilities.dpf2(100f) * avatarScale) /
                    (collapseWidth - AndroidUtilities.dpf2(100f) * avatarScale).coerceAtLeast(1f)
            )
            expandAnimatorValues[0] = currentVisualProgress
            expandAnimatorValues[1] = 0f
            animator.interpolator = CubicBezierInterpolator.EASE_BOTH
            // A completed expansion collapses over the full base duration.
            animator.duration = Math.max(1L, (250f / durationFactor).toLong())
            // Continue the morph from the gallery's current drawable.
            avatarsViewPager?.imageReceiver?.getDrawableSafe()?.let { holder ->
                avatarImageView?.drawForeground(true)
                avatarImageView?.setForegroundImageDrawable(holder)
                avatarImageView?.setForegroundAlpha(1f)
            }
            animator.addListener(createExpandAnimatorListener())
            avatarContainer?.visibility = View.VISIBLE
            avatarsViewPager?.alpha = 0f
            animator.start()
        }
        try {
            avatarContainer?.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        } catch (_: Exception) {}
    }

    private fun settleHeaderAfterTouch() {
        if (!profileChromeVisible) return
        val rv = listView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val view = layoutManager.findViewByPosition(0) ?: return
        if (allowPullingDown) {
            if (isPulledDown) {
                rv.smoothScrollBy(
                    0,
                    view.top - rv.measuredWidth - getActionsExtraHeight() + getHeaderTopOffset(),
                    CubicBezierInterpolator.EASE_OUT_QUINT,
                )
            } else {
                rv.smoothScrollBy(
                    0,
                    view.top - getHeaderExtraHeight(),
                    CubicBezierInterpolator.EASE_OUT_QUINT,
                )
            }
        } else {
            val actionsExtraHeight = getActionsExtraHeight()
            when (
                profileHeaderReleaseTarget(
                    extraHeight = extraHeight,
                    headerExtraHeight = getHeaderExtraHeight(),
                    actionsExtraHeight = actionsExtraHeight,
                    velocityY = listViewVelocityY,
                )
            ) {
                ProfileHeaderReleaseTarget.ACTIONS -> rv.smoothScrollBy(
                    0,
                    (extraHeight - actionsExtraHeight).toInt(),
                    CubicBezierInterpolator.EASE_OUT_QUINT,
                )
                ProfileHeaderReleaseTarget.COLLAPSED -> {
                    val dy = if (actionsExtraHeight > 0) actionsExtraHeight - extraHeight else extraHeight
                    rv.smoothScrollBy(0, dy.toInt(), CubicBezierInterpolator.EASE_OUT_QUINT)
                }
                ProfileHeaderReleaseTarget.HEADER -> rv.smoothScrollBy(
                    0,
                    view.top - getHeaderExtraHeight(),
                    CubicBezierInterpolator.EASE_OUT_QUINT,
                )
                ProfileHeaderReleaseTarget.NONE -> Unit
            }
        }
        listViewVelocityY = 0f
    }

    private fun createExpandAnimatorListener(): android.animation.AnimatorListenerAdapter {
        return object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                expandAnimator?.removeListener(this)
                justFullyExpanded = false
                listView?.setFlingerStoppingEnabled(true)
                if (isPulledDown) {
                    // Switch ownership only after the gallery is opaque.
                    topView?.setBackgroundColor(Color.BLACK)
                    avatarImageView?.clearForeground()
                    avatarImageView?.drawForeground(true)
                    avatarContainer?.visibility = View.GONE
                    avatarsViewPager?.alpha = 1f
                    avatarsViewPager?.visibility = View.VISIBLE
                    // Keep the avatar drawable visible until the gallery decode arrives.
                    avatarImageView?.let { avatarsViewPager?.showAvatarContent(it) }
                } else {
                    avatarImageView?.clearForeground()
                    avatarImageView?.drawForeground(true)
                    avatarContainer?.visibility = View.VISIBLE
                    avatarsViewPager?.visibility = View.GONE
                    avatarsViewPager?.alpha = 1f
                    currentExpandAnimatorFraction = 0f
                    expandAnimatorValues[0] = 0f
                    expandAnimatorValues[1] = 1f
                    // Reapply collapsed geometry even when scrolling has stopped.
                    listView?.post(::needLayout)
                }
            }

            override fun onAnimationCancel(animation: android.animation.Animator) {
                expandAnimator?.removeListener(this)
                justFullyExpanded = false
                listView?.setFlingerStoppingEnabled(true)
                // A cancellation without a restart still needs a settled frame.
                listView?.post(::needLayout)
            }
        }
    }

    private fun expandAvatarOnClick(): Boolean {
        if (!canExpandAvatar || AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
            return false
        }
        if (expandAnimator?.isRunning == true) {
            return true
        }
        if (isPulledDown || extraHeight > getHeaderExtraHeight() + AndroidUtilities.dp(24f)) {
            return false
        }
        val rv = listView ?: return false
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return false
        val view = layoutManager.findViewByPosition(0) ?: return false
        val expandedTop = profileHeaderExpandedTargetTop(
            listWidth = rv.measuredWidth,
            actionsExtraHeight = getActionsExtraHeight(),
            actionBarHeight = getHeaderTopOffset(),
        )
        openingAvatar = true
        allowPullingDown = true
        rv.smoothScrollBy(0, view.top - expandedTop, CubicBezierInterpolator.EASE_OUT_QUINT)
        return true
    }

    private fun checkListViewScroll() {
        val rv = listView ?: return
        if (rv.visibility != View.VISIBLE || rv.childCount <= 0 || openAnimationInProgress) return
        if (!profileChromeVisible) {
            if (extraHeight != 0f) extraHeight = 0f
            setMediaHeaderVisible(false)
            return
        }

        var newOffset = 0
        var child: View? = null
        for (i in 0 until rv.childCount) {
            if (rv.getChildAdapterPosition(rv.getChildAt(i)) == 0) {
                child = rv.getChildAt(i)
                break
            }
        }
        val top = child?.top ?: 0

        if (child != null) {
            val holder = rv.findContainingViewHolder(child) as? RecyclerView.ViewHolder
            val adapterPosition = holder?.adapterPosition ?: RecyclerView.NO_POSITION
            if (top >= 0 && adapterPosition == 0) {
                newOffset = top
            }
        }

        if (extraHeight != newOffset.toFloat()) {
            extraHeight = newOffset.toFloat()
            topView?.invalidate()
            needLayout()
        }
        val content = profileContentLayout
        if (content != null && content.isShown) {
            content.setVisibleContentHeight(
                // Match SharedMediaLayout/ProfileActivity: visible height is
                // measured to the bottom of the host viewport; the feed itself
                // already reserves the main-tab chrome with bottom padding.
                profileContentViewportHeight(rv.height, content.top),
            )
        }
        setMediaHeaderVisible(
            content != null && content.isShown &&
                profileMediaHeaderShouldOpen(content.top, 0),
        )
    }

    private fun updateOuterListBottomPadding() {
        val recycler = listView ?: return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager
        val firstRow = layoutManager?.findViewByPosition(0)
        val anchorTop = firstRow?.top?.coerceAtMost(getHeaderExtraHeight())
        val baseInset = getOuterListBottomInset()
        if (!profileChromeVisible) {
            outerListBottomPadding = baseInset
            if (recycler.paddingTop != 0 || recycler.paddingBottom != baseInset) {
                recycler.setPadding(recycler.paddingLeft, 0, recycler.paddingRight, baseInset)
            }
            return
        }
        val desiredPadding = if (profileListContentHeight <= 0) {
            baseInset
        } else {
            val listHeight = recycler.measuredHeight.takeIf { it > 0 } ?: recycler.height
            val visibleHeight = (listHeight - getHeaderTopOffset()).coerceAtLeast(0)
            profileOuterListBottomPadding(
                baseInset = baseInset,
                visibleHeight = visibleHeight,
                headerExtraHeight = getHeaderExtraHeight(),
                contentHeight = profileListContentHeight,
            )
        }
        if (outerListBottomPadding == desiredPadding && recycler.paddingBottom == desiredPadding) return
        outerListBottomPadding = desiredPadding
        recycler.setPadding(
            recycler.paddingLeft,
            recycler.paddingTop,
            recycler.paddingRight,
            desiredPadding,
        )
        if (anchorTop != null && !allowPullingDown) {
            layoutManager.scrollToPositionWithOffset(0, anchorTop - recycler.paddingTop)
        }
    }

    private fun ensureInitialProfileListAnchor() {
        if (initialProfileListAnchored || allowPullingDown || openingAvatar || isPulledDown) return
        val recycler = listView ?: return
        val adapter = recycler.adapter ?: return
        if (adapter.itemCount == 0) return
        val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return
        layoutManager.scrollToPositionWithOffset(
            0,
            if (profileChromeVisible) {
                profileInitialRowOffset(getHeaderExtraHeight(), recycler.paddingTop)
            } else {
                0
            },
        )
        initialProfileListAnchored = true
        recycler.post(::checkListViewScroll)
    }

    // Preserve the 0.96 rest-state scale when inset changes move the first row.
    private fun snapFirstRowToHeaderAtRest() {
        if (!profileChromeVisible) return
        if (isPulledDown || openingAvatar || expandAnimator?.isRunning == true) return
        val rv = listView ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val view = lm.findViewByPosition(0) ?: return
        val header = getHeaderExtraHeight()
        if (view.top < header - AndroidUtilities.dp(4f) || view.top > header + AndroidUtilities.dp(4f)) return
        if (view.top == header) return
        lm.scrollToPositionWithOffset(0, header - rv.paddingTop)
        rv.post(::checkListViewScroll)
    }

    private fun updateGooey() {
        val gooey = avatarGooey ?: return
        gooey.setPullProgress(pullUpProgress)
        // AndroidX uses (value, min, max); Utilities.clamp uses reversed bounds.
        val clamped = androidx.core.math.MathUtils.clamp(pullUpProgress, 0.2f, 0.7f)
        gooey.setBlurIntensity(Math.min((clamped - 0.2f) / 0.5f, 0.75f))
        gooey.setGooeyEnabled(pullUpProgress > 0 && pullUpProgress < 1)
        gooey.visibility = if (pullUpProgress >= 1.0f) View.GONE else View.VISIBLE
    }

    // Owns avatar-container dimensions while the expand animator is running.
    private fun setAvatarExpandProgress(animatedFraction: Float) {
        val value = AndroidUtilities.lerp(expandAnimatorValues[0], expandAnimatorValues[1], animatedFraction)
        currentExpandAnimatorValue = value
        currentExpandAnimatorFraction = animatedFraction
        profileActionsView?.setParentExpanded(value)

        val newTop = getStatusBarHeight() + getActionBarHeight()
        val width = getProfileViewportWidth()

        // Scroll and animation share the live render scale.
        avatarContainer?.setScaleX(avatarScale)
        avatarContainer?.setScaleY(avatarScale)
        // Expansion starts at the held frame; collapse targets live row geometry.
        val morphStartY = if (expandAnimatorValues[1] > expandAnimatorValues[0]) {
            avatarMorphStartTranslationY
        } else {
            Math.ceil(avatarY.toDouble()).toFloat()
        }
        avatarContainer?.translationY = AndroidUtilities.lerp(morphStartY, 0f, value)

        avatarImageView?.setRoundRadiusForExpand(
            AndroidUtilities.lerp(AndroidUtilities.dp(50f).toFloat(), 0f, value).toInt()
        )
        avatarImageView?.setBlurRadiusProgressForExpand(value, avatarScale, isPulledDown)
        avatarImageView?.setForegroundAlpha(value)

        // Divide target dimensions by render scale to keep visual size continuous.
        avatarContainer?.let { ac ->
            val params = ac.layoutParams as FrameLayout.LayoutParams
            params.width = AndroidUtilities.lerp(
                AndroidUtilities.dpf2(100f),
                width.toFloat() / avatarScale,
                value,
            ).toInt()
            params.height = AndroidUtilities.lerp(
                AndroidUtilities.dpf2(100f),
                (extraHeight + newTop) / avatarScale,
                value,
            ).toInt()
            params.leftMargin = 0
            ac.translationX = width / 2f - params.width * avatarScale * 0.5f
            ac.requestLayout()
        }

        val diff = calculateHeaderExtraDiff()
        refreshNameAndOnlineYBasedOnExpand(diff, newTop, width, value)

        nameTextView[1]?.let { nt ->
            nt.scaleX = AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, value)
            nt.scaleY = AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, value)
            nt.setPivotY(AndroidUtilities.lerp(0f, nt.measuredHeight.toFloat(), value))
        }

        applyExpandColorBlends(value)

        // Visibility ownership changes only at animator endpoints.
        avatarContainer?.visibility = View.VISIBLE

        topView?.invalidate()
        updateActionsPosition()
    }

    // Position title layers along the expansion Bezier.
    private fun refreshNameAndOnlineYBasedOnExpand(diff: Float, newTop: Int, width: Int, value: Float) {
        val kx = AndroidUtilities.dpf2(8f)
        val ky = if (isPulledDown) AndroidUtilities.dpf2(8f) else AndroidUtilities.dpf2(-24f)

        // Freeze the transition start while its compact target follows scroll.
        val nameXStart = AndroidUtilities.lerp(
            -AndroidUtilities.dpf2(42f + 21f),
            expandNameXStartedFrom,
            diff,
        )
        val onlineXStart = AndroidUtilities.lerp(
            -AndroidUtilities.dpf2(42f + 21f),
            expandOnlineXStartedFrom,
            diff,
        )
        nameTextView[1]?.let { nt ->
            val lp = nt.layoutParams as FrameLayout.LayoutParams
            val nameXEnd = AndroidUtilities.dpf2(18f) - lp.leftMargin
            val cx = kx + nameXStart + (nameXEnd - nameXStart) / 2f
            nt.translationX = (1f - value) * (1f - value) * nameXStart +
                2f * (1f - value) * value * cx +
                value * value * nameXEnd
        }
        nameTextView[0]?.alpha = profileHeaderTitleLayerAlpha(0)
        nameTextView[1]?.alpha = profileHeaderTitleLayerAlpha(1)

        subtitleView[1]?.let { sv ->
            val lp = sv.layoutParams as FrameLayout.LayoutParams
            val onlineXEnd = AndroidUtilities.dpf2(16f) - lp.leftMargin
            val cx = kx + onlineXStart + (onlineXEnd - onlineXStart) / 2f
            sv.translationX = (1f - value) * (1f - value) * onlineXStart +
                2f * (1f - value) * value * cx +
                value * value * onlineXEnd
        }
        subtitleView[0]?.alpha = profileHeaderSubtitleLayerAlpha(0, mediaHeaderAnimationProgress)
        subtitleView[1]?.alpha = profileHeaderSubtitleLayerAlpha(1, mediaHeaderAnimationProgress)
        mediaCounterTextView?.translationX = subtitleView[1]?.translationX ?: 0f
        mediaCounterTextView?.alpha = profileMediaCounterAlpha(mediaHeaderAnimationProgress)

        avatarContainer?.let {
            // Clamp against the fixed compact-title baseline.
            val minEndNameY = getStatusBarHeight() + getActionBarHeight() / 2f - AndroidUtilities.dp(21f)
            val minNameY = Math.floor(minEndNameY.toDouble()).toFloat() + AndroidUtilities.dp(1.3f)
            val minOnlineY = minNameY + AndroidUtilities.dpf2(22.7f)

            // Expansion ends at gallery bottom; collapse follows live compact positions.
            val nameYEnd = newTop + extraHeight - getActionsExtraHeight() -
                AndroidUtilities.dpf2(30f) - (nameTextView[1]?.bottom ?: 0)
            val onlineYEnd = newTop + extraHeight - getActionsExtraHeight() -
                AndroidUtilities.dpf2(10f) - (subtitleView[1]?.bottom ?: 0)
            val nameYStart: Float
            val onlineYStart: Float
            if (isPulledDown) {
                nameYStart = expandNameYStartedFrom
                onlineYStart = expandOnlineYStartedFrom
            } else {
                val (branch2NameY, branch2OnlineY) = branch2NamePositions(diff, newTop, width)
                expandNameYStartedFrom = branch2NameY
                expandOnlineYStartedFrom = branch2OnlineY
                nameYStart = branch2NameY
                onlineYStart = branch2OnlineY
            }
            val nameCy = ky + nameYStart + (nameYEnd - nameYStart) / 2f
            val nameY = (1f - value) * (1f - value) * nameYStart +
                2f * (1f - value) * value * nameCy +
                value * value * nameYEnd
            val clampedNameY = Math.max(minNameY, nameY)

            val onlineCy = ky + onlineYStart + (onlineYEnd - onlineYStart) / 2f
            val onlineY = (1f - value) * (1f - value) * onlineYStart +
                2f * (1f - value) * value * onlineCy +
                value * value * onlineYEnd
            val clampedOnlineY = Math.max(minOnlineY, onlineY)

            for (a in 0..1) {
                nameTextView[a]?.translationY = clampedNameY
                subtitleView[a]?.translationY = clampedOnlineY
            }
            mediaCounterTextView?.translationY = clampedOnlineY
        }
    }

    private fun layoutNameAndSubtitle(diff: Float, newTop: Int, width: Int, endNameY: Float) {
        val nameScale = 1.0f + 0.12f * diff
        val minimizedX = -AndroidUtilities.dpf2(42f + 4f)

        for (a in 0..1) {
            val nt = nameTextView[a] ?: continue
            val layoutParams = nt.layoutParams as FrameLayout.LayoutParams

            nt.scaleX = nameScale
            nt.scaleY = nameScale
            nt.setPivotY(0f)

            nt.alpha = profileHeaderTitleLayerAlpha(a)

            // Paint width remains stable while the layout width is animated.
            val availableWidth = if (a == 0 || layoutParams.width <= 0) width.toFloat() else layoutParams.width.toFloat()
            val textWidth = Math.min(nt.getExactWidth(), availableWidth) * nameScale
            val nameX = AndroidUtilities.lerp(minimizedX,
                width / 2f - (layoutParams.leftMargin + textWidth * 0.5f), diff)
            nt.translationX = nameX
        }

        for (a in 0..1) {
            val sv = subtitleView[a] ?: continue
            val layoutParams = sv.layoutParams as FrameLayout.LayoutParams
            val measuredWidth = if (sv.measuredWidth > 0) sv.measuredWidth.toFloat() else width.toFloat()
            val subtitleWidth = Math.min(sv.getExactWidth(), measuredWidth)
            val onlineX = AndroidUtilities.lerp(minimizedX,
                width / 2f - (layoutParams.leftMargin + subtitleWidth * 0.5f), diff)
            sv.translationX = onlineX
            sv.alpha = profileHeaderSubtitleLayerAlpha(a, mediaHeaderAnimationProgress)
        }
        mediaCounterTextView?.translationX = subtitleView[1]?.translationX ?: 0f
        mediaCounterTextView?.alpha = profileMediaCounterAlpha(mediaHeaderAnimationProgress)

        if (avatarContainer != null) {
            if (isPulledDown && currentExpandAnimatorValue >= 0.999f) {
                val nameY = newTop + extraHeight - getActionsExtraHeight() - AndroidUtilities.dpf2(30f) - (nameTextView[1]?.bottom ?: 0)
                val onlineY = newTop + extraHeight - getActionsExtraHeight() - AndroidUtilities.dpf2(10f) - (subtitleView[1]?.bottom ?: 0)
                val minNameY = Math.floor(endNameY.toDouble()).toFloat() + AndroidUtilities.dp(1.3f)
                val minOnlineY = minNameY + AndroidUtilities.dpf2(22.7f)

                for (a in 0..1) {
                    nameTextView[a]?.translationY = Math.max(minNameY, nameY)
                    subtitleView[a]?.translationY = Math.max(minOnlineY, onlineY)
                }
                mediaCounterTextView?.translationY = Math.max(minOnlineY, onlineY)
            } else {
                // Use scale-derived geometry while container params are transitioning.
                val (nameY, onlineY) = branch2NamePositions(diff, newTop, width)
                for (a in 0..1) {
                    nameTextView[a]?.translationY = nameY
                    subtitleView[a]?.translationY = onlineY
                }
                mediaCounterTextView?.translationY = onlineY
            }
        }
    }

    // Shared compact-scroll positions and collapse-animation endpoint.
    private fun branch2NamePositions(diff: Float, newTop: Int, width: Int): Pair<Float, Float> {
        val endNameY = getStatusBarHeight() + getActionBarHeight() / 2f * (1f + diff) -
            AndroidUtilities.dp(21f)
        val expand = Utilities.clamp01(
            (extraHeight - getHeaderExtraHeight()) /
                (width - newTop - getHeaderOnlyExtraHeight()).toFloat().coerceAtLeast(1f),
        )
        val textScale = if (extraHeight < getHeaderExtraHeight() && expand < 0.33f) {
            (24 + (54f + (42 - 24)) * diff) / 42.0f
        } else {
            AndroidUtilities.lerp(
                (42f + 54f) / 42f,
                (42f + 42f + 54f) / 42f,
                Math.min(1f, expand * 3f),
            )
        }
        val avatarBottom = Math.floor(endNameY.toDouble()).toFloat() +
            (AndroidUtilities.dp(42f) * textScale + AndroidUtilities.dpf2(8f)) * diff
        val nameY = avatarBottom + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7f) * diff
        val onlineY = avatarBottom + AndroidUtilities.dp(24f) +
            Math.floor((11f * AndroidUtilities.density).toDouble()).toFloat() * diff
        return nameY to onlineY
    }

    private fun applyExpandColorBlends(progress: Float, requestLayout: Boolean = true) {
        if (!profileChromeVisible) {
            return
        }
        updateHeaderTextWidths(progress, requestLayout)

        val mediaTitleColor = ColorUtils.blendARGB(
            Theme.getColor(Theme.key_profile_title),
            Theme.getColor(Theme.key_player_actionBarTitle),
            mediaHeaderAnimationProgress,
        )
        nameTextView[1]?.setTextColor(
            ColorUtils.blendARGB(
                mediaTitleColor,
                Color.WHITE,
                progress,
            ),
        )

        subtitleView[1]?.setTextColor(ColorUtils.blendARGB(
            Theme.getColor(Theme.key_actionBarDefaultSubtitle),
            0xB3FFFFFF.toInt(),
            progress,
        ))
        subtitleView[1]?.alpha = 1f - mediaHeaderAnimationProgress
        mediaCounterTextView?.alpha = profileMediaCounterAlpha(mediaHeaderAnimationProgress)

        val profileIconColor = ColorUtils.blendARGB(
            Theme.getColor(Theme.key_actionBarDefaultIcon),
            Color.WHITE,
            progress,
        )
        actionBar.setItemsColor(
            ColorUtils.blendARGB(
                profileIconColor,
                Theme.getColor(Theme.key_actionBarActionModeDefaultIcon),
                mediaHeaderAnimationProgress,
            ), false,
        )
        val profileSelectorColor = ColorUtils.blendARGB(
            Theme.getColor(Theme.key_avatar_actionBarSelectorBlue),
            Theme.ACTION_BAR_WHITE_SELECTOR_COLOR,
            progress,
        )
        actionBar.setItemsBackgroundColor(
            ColorUtils.blendARGB(
                profileSelectorColor,
                Theme.getColor(Theme.key_actionBarActionModeDefaultSelector),
                mediaHeaderAnimationProgress,
            ), false,
        )
        actionBar.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun setMediaHeaderVisible(visible: Boolean) {
        profileContentLayout?.setPinnedToTop(visible)
        if (mediaHeaderVisible == visible) return
        mediaHeaderVisible = visible
        mediaHeaderAnimator?.cancel()
        mediaHeaderAnimator = ValueAnimator.ofFloat(
            mediaHeaderAnimationProgress,
            if (visible) 1f else 0f,
        ).apply {
            duration = 150L
            interpolator = CubicBezierInterpolator.DEFAULT
            addUpdateListener { animation ->
                mediaHeaderAnimationProgress = animation.animatedValue as Float
                topView?.mediaHeaderAnimationProgress = mediaHeaderAnimationProgress
                applyExpandColorBlends(Math.max(expandProgress, currentExpandAnimatorValue))
                topView?.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (mediaHeaderAnimator == animation) {
                        mediaHeaderAnimator = null
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (mediaHeaderAnimator == animation) {
                        mediaHeaderAnimator = null
                    }
                }
            })
            start()
        }
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.needCheckSystemBarColors,
            true,
        )
    }

    private fun updateHeaderTextWidths(progress: Float, requestLayout: Boolean) {
        val viewWidth = getProfileViewportWidth()
        val diff = calculateHeaderExtraDiff()
        val name = nameTextView[1] ?: return
        // Width must use the scale owned by this frame's layout or animator pass.
        val minScale: Float
        val maxScale: Float
        if (extraHeight > getHeaderExtraHeight() || expandAnimator?.isRunning == true) {
            minScale = name.scaleX
            maxScale = name.scaleX
        } else if (diff >= 1f) {
            minScale = maxOf(name.scaleX, 1.12f)
            maxScale = minScale
        } else {
            minScale = 1f
            maxScale = 1.12f
        }

        // Reserve action-bar chrome in compact mode.
        val collapsedChromeWidth = AndroidUtilities.dp(95f)
        val minOnlineWidth = (viewWidth - collapsedChromeWidth).coerceAtLeast(AndroidUtilities.dp(120f))
        val maxOnlineWidth = (viewWidth - AndroidUtilities.dp(36f)).coerceAtLeast(AndroidUtilities.dp(120f))
        val interpolation = CubicBezierInterpolator.EASE_IN.getInterpolation(diff)
        val minNameWidth = minOnlineWidth / minScale
        val maxNameWidth = maxOnlineWidth / maxScale
        var nameWidth = Math.ceil(AndroidUtilities.lerp(minNameWidth, maxNameWidth, interpolation).toDouble()).toInt()

        val nameParams = name.layoutParams as FrameLayout.LayoutParams
        val translatedX = nameParams.leftMargin + name.translationX
        val translatedWidth = ((viewWidth - AndroidUtilities.dp(18f)) / name.scaleX - translatedX).toInt()
        nameWidth = minOf(nameWidth, translatedWidth).coerceAtLeast(AndroidUtilities.dp(120f))
        if (nameParams.width != nameWidth) {
            nameParams.width = nameWidth
            if (requestLayout) {
                name.requestLayout()
            }
        }

        val subtitle = subtitleView[1] ?: return
        val subtitleWidth = Math.ceil(
            AndroidUtilities.lerp(minOnlineWidth.toFloat(), maxOnlineWidth.toFloat(), interpolation).toDouble(),
        ).toInt()
        val subtitleParams = subtitle.layoutParams as FrameLayout.LayoutParams
        if (subtitleParams.width != subtitleWidth) {
            subtitleParams.width = subtitleWidth
            if (requestLayout) {
                subtitle.requestLayout()
            }
        }
    }

    // --- Info updates ---

    @Suppress("IMPLICIT_CAST")
    private fun updateInfo() {
        val title: String
        val subtitle: String
        val avatarTemplate: String
        val animatedAvatarUrl: String
        val showAvatar: Boolean

        val state = sessionState
        when (state) {
            is AppSessionState.Authenticated -> {
                val snapshot = state.snapshot
                val profile = profileState.profile
                val displayName = profile?.displayName?.takeIf(String::isNotBlank)
                val username = profile?.username?.takeIf(String::isNotBlank) ?: snapshot.username
                title = displayName ?: snapshot.displayName.takeIf(String::isNotBlank) ?: snapshot.username
                subtitle = buildHeaderSubtitle(profile).ifBlank {
                    if (profile == null) {
                        context?.getString(R.string.profile_session_restoring_subtitle).orEmpty()
                    } else {
                        "已登录"
                    }
                }
                avatarTemplate = profile?.avatarTemplate ?: snapshot.avatarTemplate
                animatedAvatarUrl = profile?.animatedAvatarUrl.orEmpty()
                showAvatar = true
                avatarViewerTitle = title
                avatarViewerSubtitle = subtitle.ifBlank { "@$username" }
                avatarViewerUrl = resolveAvatarUrl(appContainer.baseUrl, avatarTemplate, animatedAvatarUrl, 512)
                profileWebUrl = "${appContainer.baseUrl.trimEnd('/')}/u/${username}"
            }
            is AppSessionState.Restoring -> {
                val snapshot = state.snapshot
                title = context?.getString(R.string.profile_session_restoring_title).orEmpty()
                subtitle = snapshot?.displayName?.takeIf(String::isNotBlank)
                    ?: context?.getString(R.string.profile_session_restoring_subtitle).orEmpty()
                avatarTemplate = snapshot?.avatarTemplate.orEmpty()
                animatedAvatarUrl = ""
                showAvatar = snapshot != null
                avatarViewerTitle = title
                avatarViewerSubtitle = subtitle
                avatarViewerUrl = resolveAvatarUrl(appContainer.baseUrl, avatarTemplate, "", 512)
                profileWebUrl = ""
            }
            is AppSessionState.Anonymous -> {
                title = context?.getString(R.string.profile_login_title).orEmpty()
                subtitle = context?.getString(R.string.profile_login_subtitle).orEmpty()
                avatarTemplate = ""
                animatedAvatarUrl = ""
                showAvatar = false
                avatarViewerTitle = title
                avatarViewerSubtitle = subtitle
                avatarViewerUrl = ""
                profileWebUrl = ""
            }
            is AppSessionState.Expired -> {
                val snapshot = state.snapshot
                val presentation = sessionPresentation(state.reason)
                title = snapshot?.displayName?.takeIf(String::isNotBlank)
                    ?: context?.getString(presentation.titleRes).orEmpty()
                subtitle = context?.getString(presentation.subtitleRes).orEmpty()
                avatarTemplate = snapshot?.avatarTemplate.orEmpty()
                animatedAvatarUrl = ""
                showAvatar = snapshot != null
                avatarViewerTitle = title
                avatarViewerSubtitle = subtitle
                avatarViewerUrl = resolveAvatarUrl(appContainer.baseUrl, avatarTemplate, "", 512)
                profileWebUrl = ""
            }
            else -> {
                title = context?.getString(R.string.profile_session_restoring_title).orEmpty()
                subtitle = context?.getString(R.string.profile_session_restoring_subtitle).orEmpty()
                avatarTemplate = ""
                animatedAvatarUrl = ""
                showAvatar = false
                avatarViewerTitle = title
                avatarViewerSubtitle = subtitle
                avatarViewerUrl = ""
                profileWebUrl = ""
            }
        }

        nameTextView[0]?.text = title
        nameTextView[1]?.text = title
        subtitleView[0]?.text = subtitle
        subtitleView[1]?.text = subtitle

        avatarImageView?.let { aiv ->
            aiv.localAvatarDrawable.setInfo(0, title)
            canExpandAvatar = showAvatar && (avatarTemplate.isNotEmpty() || animatedAvatarUrl.isNotEmpty())
            aiv.isClickable = canExpandAvatar
            aiv.isLongClickable = canExpandAvatar
            aiv.importantForAccessibility = if (canExpandAvatar) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (showAvatar && (avatarTemplate.isNotEmpty() || animatedAvatarUrl.isNotEmpty())) {
                // Share one request and filter across avatar, gallery, and viewer.
                val avatarUrl = resolveAvatarUrl(appContainer.baseUrl, avatarTemplate, animatedAvatarUrl, 512)
                val imageFilter = avatarImageFilter(animatedAvatarUrl, 512)
                avatarDisplayFilter = imageFilter
                aiv.imageReceiver.setAllowDecodeSingleFrame(animatedAvatarUrl.isBlank())
                aiv.imageReceiver.setImage(avatarUrl, imageFilter, aiv.localAvatarDrawable, null, 0)
                avatarsViewPager?.setImage(
                    avatarUrl,
                    imageFilter,
                    aiv.localAvatarDrawable,
                    animatedAvatarUrl.isNotBlank(),
                )
            } else {
                aiv.imageReceiver.setAllowDecodeSingleFrame(true)
                aiv.imageReceiver.setImageBitmap(null as android.graphics.Bitmap?)
                avatarsViewPager?.setImage(null, null, aiv.localAvatarDrawable, false)
            }
        }

        updateProfileActionBarMenu()

        needLayout()
    }

    private fun setupProfileActionBarMenu(context: Context) {
        val menu = actionBar.createMenu()
        // Keep profile actions under one overflow item.
        val overflow = menu.addItem(MENU_OTHER, TelegramR.drawable.ic_ab_other)
        overflow.setContentDescription(context.getString(R.string.action_more_options))
        // Resource IDs let ActionBarMenuSubItem apply its theme tint.
        overflow.addSubItem(MENU_OPEN_PROFILE, R.drawable.settings_link,
            context.getString(R.string.profile_menu_open_in_browser))
        overflow.addSubItem(MENU_REFRESH_PROFILE, R.drawable.settings_refresh,
            context.getString(R.string.profile_menu_refresh))
        overflow.addSubItem(MENU_LOGIN, R.drawable.settings_login,
            context.getString(R.string.profile_menu_login))
        otherItem = overflow
        actionBar.setActionBarMenuOnItemClick(
            object : ActionBar.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    when (id) {
                        MENU_OPEN_PROFILE -> if (profileWebUrl.isNotBlank()) {
                            refreshProfileOnNextResume = true
                            openBrowser(profileWebUrl)
                        }
                        MENU_REFRESH_PROFILE -> viewModel.retry()
                        MENU_LOGIN -> presentFragment(LoginWebViewFragment())
                    }
                }
            },
        )
        updateProfileActionBarMenu()
    }

    private fun updateProfileActionBarMenu() {
        val authenticated = sessionState is AppSessionState.Authenticated
        otherItem?.visibility = if (authenticated) View.VISIBLE else View.GONE
        if (!profileChromeVisible) {
            actionBar.setBackButtonDrawable(null)
            return
        }
        // The header QR affordance is meaningful only with a profile URL.
        if (profileWebUrl.isNotBlank()) {
            if (actionBar.backButtonImageView.visibility != View.VISIBLE) {
                applyProfileQrBackButton()
            }
        } else if (actionBar.backButtonImageView.visibility != View.GONE) {
            actionBar.setBackButtonDrawable(null)
        }
        val overflow = otherItem ?: return
        if (profileWebUrl.isNotBlank()) overflow.showSubItem(MENU_OPEN_PROFILE) else overflow.hideSubItem(MENU_OPEN_PROFILE)
        if (authenticated) overflow.showSubItem(MENU_REFRESH_PROFILE) else overflow.hideSubItem(MENU_REFRESH_PROFILE)
        if (authenticated) overflow.hideSubItem(MENU_LOGIN) else overflow.showSubItem(MENU_LOGIN)
    }

    private fun buildHeaderSubtitle(profile: UserProfile?): String {
        if (profile == null) return ""
        val activity = profileFormatRelativeActivity(profile.lastSeenAt)
        val title = profile.title.takeIf(String::isNotBlank)
        val group = profile.primaryGroupName.takeIf(String::isNotBlank)
        return listOfNotNull(
            activity.takeIf(String::isNotBlank)?.let { "最近活动 $it" },
            title,
            group,
        ).take(2).joinToString(" · ")
    }

    private fun openAvatarViewer(ignoreDragging: Boolean = false) {
        val url = avatarViewerUrl.takeIf(String::isNotBlank) ?: return
        val avatarView = avatarImageView ?: return
        val avatarHost = avatarContainer ?: return
        if (!ignoreDragging && listView?.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) return
        val viewer = PhotoViewer.getInstance()
        if (viewer.isVisibleOrAnimating) return
        viewer.setParentActivity(this)
        val provider = object : PhotoViewer.EmptyPhotoViewerProvider() {
            override fun getPlaceForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                index: Int,
                needPreview: Boolean,
                closing: Boolean,
            ): PhotoViewer.PlaceProviderObject? {
                if (profileAvatarCloseUsesFlyOut(closing, avatarHost.scaleX)) return null
                val galleryView = avatarsViewPager?.currentItemView
                val fromGallery = galleryView?.isShown == true && avatarsViewPager?.alpha == 1f
                val anchorView = if (fromGallery) galleryView else avatarView
                if (anchorView == null || !anchorView.isShown) return null
                val sourceScale = if (fromGallery) 1f else avatarHost.scaleX
                val viewLocation = IntArray(2)
                anchorView.getLocationInWindow(viewLocation)
                val receiver = anchorView.imageReceiver
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = viewLocation[0]
                    viewY = viewLocation[1]
                    parentView = anchorView
                    imageReceiver = receiver
                    thumb = receiver.getBitmapSafe()
                    radius = receiver.getRoundRadius(true)
                    scale = sourceScale
                    fadeIn = !fromGallery && sourceScale > 0.96f
                    // Keep the gallery receiver visible for return-to-source closes.
                    keepImageReceiverVisible = fromGallery
                    clipTopAddition = 0
                    clipBottomAddition = 0
                }
            }

            override fun getThumbForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                index: Int,
            ): ImageReceiver.BitmapHolder? =
                if (avatarsViewPager?.isShown == true && avatarsViewPager?.alpha == 1f) {
                    avatarsViewPager?.imageReceiver?.getBitmapSafe()
                } else {
                    avatarView.imageReceiver.getBitmapSafe()
                }

            override fun willHidePhotoViewer() {
                // Gallery visibility is owned by keepImageReceiverVisible.
                avatarView.imageReceiver.setVisible(true, true)
                profileActionsView?.setParentExpanded(currentExpandAnimatorValue)
                profileActionsView?.invalidate()
            }
        }
        val activeReceiver = if (avatarsViewPager?.isShown == true && avatarsViewPager?.alpha == 1f) {
            avatarsViewPager?.imageReceiver
        } else {
            avatarView.imageReceiver
        }
        val sourceWidth = activeReceiver?.bitmapWidth?.coerceAtLeast(1) ?: 1
        val sourceHeight = activeReceiver?.bitmapHeight?.coerceAtLeast(1) ?: 1
        val item = PhotoViewer.UrlPhotoItem(
            url,
            null,
            sourceWidth,
            sourceHeight,
            null,
            avatarViewerTitle,
            avatarViewerSubtitle,
        )
        if (!viewer.openPhotoItems(listOf(item), 0, provider)) {
            openBrowser(url)
        }
    }

    private fun updateList() {
        // Stable IDs let DiffUtil refresh content without moving header rows.
        universalAdapter?.update(true)
        profileListContentGeneration++
        listView?.requestLayout()
        listView?.post(::ensureInitialProfileListAnchor)
    }

    private fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val state = sessionState
        if (state is AppSessionState.Authenticated) {
            when {
                profileState.errorMessage != null && profileState.profile == null -> {
                    items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
                    items.add(UItem.ofFactory(ProfileEmptyFactory()).apply {
                        id = ACTION_RETRY_PROFILE
                        text = context?.getString(R.string.profile_load_error_title).orEmpty()
                        subtext = buildString {
                            append(profileState.errorMessage.orEmpty())
                            append('\n')
                            append(context?.getString(R.string.action_retry).orEmpty())
                        }
                    })
                    // A WebView/cached snapshot may still be usable while the
                    // native profile request is recovering. Keep the action
                    // reachable without presenting a false logout message.
                    if (state.freshness == SessionFreshness.Cached ||
                        state.freshness == SessionFreshness.WebView
                    ) {
                        items.add(sessionStateItem(sessionPresentation(SessionExpiredReason.RecoveryFailed)))
                    }
                    items.add(UItem.asShadow(null))
                }
                else -> {
                    // Keep identity row IDs stable while profile data arrives.
                    val profile = profileState.profile
                    val username = profile?.username?.takeIf(String::isNotBlank) ?: state.snapshot.username

                    items.add(UItem.asSpace(AndroidUtilities.dp(6f)).apply {
                        transparent = true
                    })
                    adapter.whiteSectionStart()
                    items.add(UItem.ofFactory(AboutFactory()).apply { id = VIEW_TYPE_ABOUT })
                    items.add(
                        TextDetailCell.Factory.of(
                            ID_USERNAME,
                            "@$username",
                            context?.getString(R.string.profile_username_label).orEmpty(),
                        ).apply { hideDivider = true },
                    )
                    items.add(
                        TextDetailCell.Factory.of(
                            ID_JOINED,
                            formatJoinedValue(profile),
                            context?.getString(R.string.profile_joined_label).orEmpty(),
                        ),
                    )
                    adapter.whiteSectionEnd()
                    items.add(UItem.asSpace(AndroidUtilities.dp(14f)).apply {
                        transparent = true
                    })
                }
            }
        } else {
            when (state) {
                is AppSessionState.Anonymous -> {
                    items.add(sessionStateItem(SessionPresentation.signIn()))
                }
                is AppSessionState.Expired -> {
                    items.add(sessionStateItem(sessionPresentation(state.reason)))
                }
                else -> {
                    items.add(UItem.ofFactory(ProfileLoadingFactory()).apply { id = VIEW_TYPE_PROFILE_LOADING })
                }
            }
        }

        // Keep the final content row during loading so tab geometry stays stable.
        if (state is AppSessionState.Authenticated && (profileState.profile != null || profileState.isLoading)) {
            items.add(UItem.ofFactory(profileContentFactory).apply {
                id = VIEW_TYPE_PROFILE_CONTENT
            })
        }
    }

    private fun formatJoinedDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val instant = java.time.Instant.parse(isoDate)
            java.time.format.DateTimeFormatter.ofPattern("yyyy年M月")
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant)
        } catch (_: Exception) {
            profileFormatDate(isoDate)
        }
    }

    private fun formatJoinedValue(profile: UserProfile?): String {
        val joined = profile?.createdAt?.takeIf(String::isNotBlank)
            ?: return context?.getString(R.string.profile_about_loading).orEmpty()
        return formatJoinedDate(joined)
    }

    // Returning true lets RecyclerListView provide long-press haptics.
    private fun handleItemLongClick(item: UItem): Boolean {
        if (item.id !in COPYABLE_ROW_IDS) return false
        val value = item.text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) return false
        AndroidUtilities.addToClipboard(value)
        BulletinFactory.of(this)
            .createCopyBulletin(context?.getString(R.string.profile_value_copied).orEmpty())
            .show()
        return true
    }

    private fun handleItemClick(item: UItem, view: View) {
        if (item.id == VIEW_TYPE_ABOUT && view is AboutLinkCell) {
            view.onClick()
            return
        }
        when (item.id) {
            ACTION_RETRY_PROFILE -> viewModel.retry()
            ACTION_LOGIN -> presentFragment(LoginWebViewFragment())
        }
    }

    override fun isSupportEdgeToEdge(): Boolean = true

    override fun drawEdgeNavigationBar(): Boolean = false

    override fun isLightStatusBar(): Boolean {
        val color = when {
            isPulledDown -> return false
            actionBar != null && actionBar.isActionModeShowed ->
                Theme.getColor(Theme.key_actionBarActionModeDefault)
            // Upstream ProfileActivity: the pinned media header paints the
            // action-bar strip white, so status-bar icons flip to dark there.
            mediaHeaderVisible -> Theme.getColor(Theme.key_windowBackgroundWhite)
            else -> Theme.getColor(Theme.key_actionBarDefault)
        }
        return ColorUtils.calculateLuminance(color) > 0.7
    }

    private inner class ContainerView(context: Context) : SizeNotifierFrameLayout(context), NestedScrollingParent3 {

        private val nestedScrollingParentHelper = NestedScrollingParentHelper(this)
        private var lastProfileLayoutWidth = 0
        private var cachedContentGeneration = -1
        private var cachedContentWidth = -1

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            (listView?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.topMargin = getHeaderTopOffset()
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val recycler = listView
            if (recycler != null) {
                applyProfileListTopPadding(recycler, measuredWidth)
            }
            recycler?.measure(
                View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY),
            )
            if (recycler != null) {
                // Binding every row is expensive (Html bio row included), so
                // only re-measure when the item list or viewport width changed.
                if (cachedContentGeneration != profileListContentGeneration ||
                    cachedContentWidth != measuredWidth
                ) {
                    profileListContentHeight = measureProfileListContentHeight(recycler, measuredWidth)
                    cachedContentGeneration = profileListContentGeneration
                    cachedContentWidth = measuredWidth
                }
                updateOuterListBottomPadding()
            }
        }

        private fun applyProfileListTopPadding(recycler: UniversalRecyclerView, viewportWidth: Int) {
            val desiredPadding = if (profileChromeVisible) {
                profileListTopPadding(
                    viewportWidth = viewportWidth,
                    actionsExtraHeight = getActionsExtraHeight(),
                )
            } else {
                0
            }
            if (recycler.paddingTop == desiredPadding) return
            val layoutManager = recycler.layoutManager as? LinearLayoutManager ?: return
            val firstPosition = layoutManager.findFirstVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
                ?: 0
            val firstRow = layoutManager.findViewByPosition(firstPosition)
            val anchorTop = if (firstPosition == 0) {
                firstRow?.top?.coerceAtMost(getHeaderExtraHeight())
                    ?: getHeaderExtraHeight()
            } else {
                firstRow?.top ?: recycler.paddingTop
            }
            if (!allowPullingDown) {
                layoutManager.scrollToPositionWithOffset(
                    firstPosition,
                    anchorTop - desiredPadding,
                )
            }
            if (recycler.paddingTop != desiredPadding) {
                recycler.setPadding(
                    recycler.paddingLeft,
                    desiredPadding,
                    recycler.paddingRight,
                    recycler.paddingBottom,
                )
            }
        }

        private fun measureProfileListContentHeight(
            recycler: UniversalRecyclerView,
            width: Int,
        ): Int {
            val adapter = universalAdapter ?: return 0
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(recycler.measuredHeight, View.MeasureSpec.UNSPECIFIED)
            var contentHeight = 0
            for (position in 0 until adapter.itemCount) {
                val viewType = adapter.getItemViewType(position)
                if (viewType == VIEW_TYPE_PROFILE_CONTENT) {
                    contentHeight += recycler.measuredHeight
                    continue
                }
                val holder = adapter.createViewHolder(recycler, viewType)
                adapter.onBindViewHolder(holder, position)
                holder.itemView.measure(widthSpec, heightSpec)
                contentHeight += holder.itemView.measuredHeight
            }
            return contentHeight
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val recycler = listView
            super.onLayout(changed, left, top, right, bottom)
            recycler?.layout(0, getHeaderTopOffset(), measuredWidth, getHeaderTopOffset() + measuredHeight)
            // Recomputing extraHeight here would feed requestLayout back into onLayout.
            val layoutWidth = right - left
            if (layoutWidth > 0 && layoutWidth != lastProfileLayoutWidth) {
                lastProfileLayoutWidth = layoutWidth
                updateLayout()
            }
        }

        override fun onNestedScroll(
            target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int, consumed: IntArray,
        ) {
            val outer = listView ?: return
            val content = profileContentLayout ?: return
            if (target !== outer || !content.isAttachedToWindow || dyUnconsumed == 0) return
            val request = profileInnerScrollFromOuter(
                dyUnconsumed = dyUnconsumed,
                contentTop = content.top,
                pinTop = 0,
            )
            if (request == 0) return
            val inner = content.currentListView() ?: return
            try {
                consumed[1] += request
                inner.scrollBy(0, request)
            } catch (_: IndexOutOfBoundsException) {
                // The inner adapter can swap mid-gesture; resync its view cache.
                AndroidUtilities.runOnUIThread {
                    if (!inner.isComputingLayout) {
                        inner.adapter?.notifyDataSetChanged()
                    }
                }
            }
        }

        override fun onNestedScroll(
            target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int,
        ) = Unit

        override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean =
            super.onNestedPreFling(target, velocityX, velocityY)

        override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
            val outer = listView ?: return
            val content = profileContentLayout ?: return
            if (target !== outer || !content.isAttachedToWindow || dy >= 0) return
            if (!profileMediaHeaderShouldOpen(content.top, 0)) return
            val inner = content.currentListView() ?: return
            val manager = inner.layoutManager as? LinearLayoutManager ?: return
            val firstPosition = manager.findFirstVisibleItemPosition()
            if (firstPosition == RecyclerView.NO_POSITION) return
            val holder = inner.findViewHolderForAdapterPosition(firstPosition)
            val firstTop = holder?.itemView?.top ?: -1
            val request = profileInnerScrollBackToTopRequest(
                dy = dy,
                firstPosition = firstPosition,
                firstTop = firstTop,
                paddingTop = inner.paddingTop,
            )
            if (request != 0) {
                consumed[1] += request
                inner.scrollBy(0, request)
            }
        }

        override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean =
            profileContentLayout?.isAttachedToWindow == true &&
                axes == ViewCompat.SCROLL_AXIS_VERTICAL

        override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes, type)
        }

        override fun onStopNestedScroll(target: View, type: Int) {
            nestedScrollingParentHelper.onStopNestedScroll(target, type)
        }

        override fun onStopNestedScroll(child: View) {}

        override fun dispatchDraw(canvas: Canvas) {
            drawBackground(canvas)
            super.dispatchDraw(canvas)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            // The pinch overlay owns its complete touch stream.
            if (pinchToZoomHelper?.isInOverlayMode() == true) {
                return pinchToZoomHelper!!.onTouchEvent(ev)
            }
            return super.dispatchTouchEvent(ev)
        }

        override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
            // PinchToZoomHelper redraws these views above the overlay.
            if (
                pinchToZoomHelper?.isInOverlayMode() == true &&
                (child === avatarContainer2 || child === actionBar)
            ) {
                return true
            }
            return super.drawChild(canvas, child, drawingTime)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray))
    }

    // Adds NativeDo accessibility and placeholder wiring to the vendored carrier.
    class AvatarImageView(context: Context) : ProfileActivity.AvatarImageView(context) {
        val localAvatarDrawable = AvatarDrawable()
        var pinchToZoomHandler: ((MotionEvent) -> Boolean)? = null

        init {
            imageReceiver.setAllowDecodeSingleFrame(true)
            setRoundRadius(AndroidUtilities.dp(50f))
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (pinchToZoomHandler?.invoke(event) == true) {
                return true
            }
            return super.onTouchEvent(event)
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            if (imageReceiver.hasNotThumb()) {
                info.className = "android.widget.ImageButton"
                info.text = context.getString(R.string.profile_avatar_accessibility)
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLICK,
                        context.getString(R.string.action_open),
                    ),
                )
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        context.getString(R.string.profile_avatar_open_viewer),
                    ),
                )
            } else {
                info.isVisibleToUser = false
            }
        }
    }

    private inner class ProfileLoadingFactory : UItem.ViewFactory {
        override fun getViewType(): Int = VIEW_TYPE_PROFILE_LOADING
        override fun createView(context: Context): View = FlickerLoadingView(context).apply {
            setViewType(FlickerLoadingView.CHECKBOX_TYPE)
            setIsSingleCell(true)
            setItemsCount(2)
        }
        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter) = Unit
        override fun isEnabled(item: UItem): Boolean = false
    }

    private inner class ProfileEmptyFactory : UItem.ViewFactory {
        override fun getViewType(): Int = VIEW_TYPE_PROFILE_EMPTY
        override fun createView(context: Context): View = StickerEmptyView(
            context,
            null,
            StickerEmptyView.STICKER_TYPE_SEARCH,
        ).apply {
            minimumHeight = AndroidUtilities.dp(156f)
            showProgress(false, false)
        }

        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter) {
            (view as StickerEmptyView).apply {
                title(item.text ?: "")
                subtitle(item.subtext ?: "")
                showProgress(false, false)
            }
        }

        override fun isEnabled(item: UItem): Boolean = item.id == ACTION_RETRY_PROFILE
    }

    private inner class ProfileContentFactory : UItem.ViewFactory {
        override fun getViewType(): Int = VIEW_TYPE_PROFILE_CONTENT

        override fun createView(context: Context): View = ProfileContentLayout(
            context = context,
            baseUrl = appContainer.baseUrl,
            onTopicSelected = { topic, page ->
                presentFragment(
                    TopicDetailFragment(
                        topic.id,
                        topic.title,
                        if (page == ProfileFeedPage.Replies) topic.targetPostNumber else null,
                        if (page == ProfileFeedPage.Replies) topic.targetPostId else null,
                        if (page == ProfileFeedPage.Topics) topic.toTopicInitialIdentity() else null,
                    ),
                )
            },
            onPageSelected = { page ->
                selectedProfileFeedPage = page
                updateSelectedProfileFeedText()
            },
            onLoadMore = { page ->
                when (page) {
                    ProfileFeedPage.Topics -> topicsFeedViewModel?.loadNextPage()
                    ProfileFeedPage.Replies -> repliesFeedViewModel?.loadNextPage()
                }
            },
            onRetry = { page ->
                when (page) {
                    ProfileFeedPage.Topics -> topicsFeedViewModel?.retry()
                    ProfileFeedPage.Replies -> repliesFeedViewModel?.retry()
                }
            },
            onRetryLoadMore = { page ->
                when (page) {
                    ProfileFeedPage.Topics -> topicsFeedViewModel?.retryLoadMore()
                    ProfileFeedPage.Replies -> repliesFeedViewModel?.retryLoadMore()
                }
            },
            viewportHeightProvider = {
                val outerHeight = listView?.height?.takeIf { it > 0 }
                    ?: fragmentView?.height?.takeIf { it > 0 }
                    ?: AndroidUtilities.displaySize.y
                profileContentViewportHeight(outerHeight, 0)
            },
            // ProfileContentLayout supplies the themed fallback when RenderNode is unavailable.
            blurSourceRoot = fragmentView as? ViewGroup,
        ).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBottomContentInset(getProfileContentBottomInset())
        }.also { profileContentLayout = it }

        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter) {
            val content = view as ProfileContentLayout
            // Factory rows default to white; expose the gray outer surface here.
            content.setBackgroundColor(Color.TRANSPARENT)
            content.selectPage(selectedProfileFeedPage, animated = false)
            content.submit(ProfileFeedPage.Topics, topicsFeedState)
            content.submit(ProfileFeedPage.Replies, repliesFeedState)
        }

        override fun isEnabled(item: UItem): Boolean = false
    }

    private inner class AboutFactory : UItem.ViewFactory {
        override fun getViewType(): Int = VIEW_TYPE_ABOUT
        override fun createView(context: Context): View = object : AboutLinkCell(context, this@ProfileFragment) {
            override fun didPressUrl(url: String, progress: Browser.Progress?) {
                Browser.openUrl(
                    context,
                    Uri.parse(url),
                    true,
                    true,
                    false,
                    progress,
                    null,
                    false,
                    true,
                    false,
                )
            }

            override fun didResizeEnd() {
                listView?.requestLayout()
            }
        }

        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter) {
            val bio = profileState.profile?.bio.orEmpty()
            // Keep the row stable while profile data loads.
            val parsed = when {
                profileState.profile == null -> context.getString(R.string.profile_about_loading)
                bio.isBlank() -> context.getString(R.string.profile_about_empty)
                else -> Html.fromHtml(
                    normalizeInlineHtml(bio),
                    Html.FROM_HTML_MODE_COMPACT,
                ).trimEnd()
            }
            (view as AboutLinkCell).apply {
                setTextAndValue(
                    parsed,
                    context.getString(R.string.profile_about_title),
                    false,
                )
                // Match upstream ProfileActivity: the own-profile bio is
                // always fully expanded — disable the show-more collapse
                // button so long Discourse bios never fold.
                setMoreButtonDisabled(true)
                updateColors()
            }
        }
        override fun isEnabled(item: UItem): Boolean = true
    }

    private fun sessionStateItem(presentation: SessionPresentation): UItem =
        UItem.ofFactory(ProfileSessionFactory()).apply {
            id = ACTION_LOGIN
            text = context?.getString(presentation.titleRes).orEmpty()
            subtext = context?.getString(presentation.subtitleRes).orEmpty()
            textValue = context?.getString(presentation.actionRes).orEmpty()
            intValue = presentation.action.ordinal
            red = presentation.danger
        }

    private inner class ProfileSessionFactory : UItem.ViewFactory {
        override fun getViewType(): Int = VIEW_TYPE_PROFILE_SESSION

        override fun createView(context: Context): View = ProfileSessionStateCell(context)

        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter) {
            (view as ProfileSessionStateCell).bind(
                titleText = item.text,
                subtitleText = item.subtext,
                actionText = item.textValue,
                danger = item.red,
                onAction = {
                    when (SessionAction.entries.getOrNull(item.intValue)) {
                        SessionAction.RetryRestore -> viewScope.launch {
                            appContainer.sessionCoordinator.start()
                        }
                        SessionAction.OpenLogin,
                        null,
                        -> presentFragment(LoginWebViewFragment())
                    }
                },
            )
            view.bottomContentInset = getProfileContentBottomInset()
        }

        override fun isEnabled(item: UItem): Boolean = false
    }

    private fun getProfileViewportWidth(): Int =
        profileViewportWidth(
            fragmentWidth = fragmentView?.width ?: 0,
            listWidth = listView?.measuredWidth ?: 0,
            displayWidth = AndroidUtilities.displaySize.x,
        )

    override fun onParentScrollToTop() {
        val inner = profileContentLayout?.currentListView()
        if (inner?.canScrollVertically(-1) == true) {
            inner.smoothScrollToPosition(0)
        } else {
            listView?.smoothScrollToPosition(0)
        }
    }

    override fun canParentTabsSlide(event: MotionEvent, forward: Boolean): Boolean {
        val content = profileContentLayout
        if (content?.isShown == true) {
            val contentBounds = android.graphics.Rect()
            if (
                content.getGlobalVisibleRect(contentBounds) &&
                contentBounds.contains(event.rawX.toInt(), event.rawY.toInt())
            ) {
                if (content.isTrackingOrAnimating()) return false
                if (forward || content.selectedPage() == ProfileFeedPage.Replies) return false
            }
        }
        val avatarHost = avatarContainer ?: return true
        if (!avatarHost.isShown) return true
        val bounds = android.graphics.Rect()
        return !avatarHost.getGlobalVisibleRect(bounds) ||
            !bounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    // Register profile chrome and row theme keys.
    override fun getThemeDescriptions(): java.util.ArrayList<ThemeDescription> {
        val descriptions = java.util.ArrayList<ThemeDescription>()
        val chromeDelegate = ThemeDescription.ThemeDescriptionDelegate {
            recolorProfileChrome()
        }
        if (actionBar != null) {
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
                    ThemeDescription.FLAG_AB_SELECTORCOLOR,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_avatar_actionBarSelectorBlue,
                ),
            )
        }
        val list = listView
        if (list != null) {
            descriptions.add(
                ThemeDescription(
                    list,
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
                    list,
                    ThemeDescription.FLAG_BACKGROUND,
                    null,
                    null as android.graphics.Paint?,
                    null,
                    null,
                    Theme.key_windowBackgroundGray,
                ),
            )
            descriptions.add(
                ThemeDescription(
                    list,
                    0,
                    arrayOf(
                        AboutLinkCell::class.java,
                        ProfileContentLayout::class.java,
                        ProfileSessionStateCell::class.java,
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
            Theme.key_actionBarDefaultTitle,
            Theme.key_windowBackgroundGray,
            Theme.key_profile_title,
            Theme.key_player_actionBarTitle,
            Theme.key_profile_actionBackground,
            Theme.key_profile_actionIcon,
            Theme.key_actionBarDefaultSubtitle,
            Theme.key_divider,
            Theme.key_text_RedBold,
            Theme.key_windowBackgroundWhiteBlackText,
            Theme.key_windowBackgroundWhiteBlueText,
            Theme.key_windowBackgroundWhiteGrayText,
            Theme.key_windowBackgroundWhiteLinkText,
            Theme.key_windowBackgroundWhiteLinkSelection,
            Theme.key_profile_tabText,
            Theme.key_profile_tabSelectedText,
            Theme.key_profile_tabSelectedLine,
            Theme.key_profile_tabSelector,
            Theme.key_actionBarActionModeDefault,
            Theme.key_actionBarActionModeDefaultIcon,
            Theme.key_actionBarActionModeDefaultSelector,
            Theme.key_player_actionBarSubtitle,
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

    private fun recolorProfileChrome() {
        if (!profileChromeVisible) {
            updateSessionChrome()
            val list = listView
            if (list != null) {
                for (index in 0 until list.childCount) {
                    (list.getChildAt(index) as? Theme.Colorable)?.updateColors()
                }
            }
            return
        }
        (fragmentView as? View)?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        topView?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        updateProfileActionsColor()
        nameTextView[0]?.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle))
        // Expanded title is blended in scroll updates; refresh base expanded color.
        nameTextView[1]?.setTextColor(Theme.getColor(Theme.key_profile_title))
        subtitleView[0]?.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        subtitleView[1]?.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        val mediaSubtitleColor = Theme.getColor(Theme.key_player_actionBarSubtitle)
        mediaCounterTextView?.getTextView()?.setTextColor(mediaSubtitleColor)
        mediaCounterTextView?.getNextTextView()?.setTextColor(mediaSubtitleColor)
        applyExpandColorBlends(expandProgress)
        profileActionsView?.invalidate()
        val list = listView
        if (list != null) {
            for (index in 0 until list.childCount) {
                (list.getChildAt(index) as? ProfileContentLayout)?.updateColors()
                (list.getChildAt(index) as? Theme.Colorable)?.updateColors()
            }
        }
        profileContentLayout?.updateColors()
    }

    override fun onResume() {
        super.onResume()
        val now = SystemClock.elapsedRealtime()
        if (!hasResumedOnce) {
            hasResumedOnce = true
            lastResumeRefreshAt = now
        } else if (
            sessionState is AppSessionState.Authenticated &&
            !profileState.isLoading &&
            (refreshProfileOnNextResume || now - lastResumeRefreshAt >= PROFILE_RESUME_REFRESH_INTERVAL_MS)
        ) {
            refreshProfileOnNextResume = false
            lastResumeRefreshAt = now
            viewModel.retry()
        }
        recolorProfileChrome()
        updateList()
    }

    override fun onFragmentDestroy() {
        mediaHeaderAnimator?.removeAllUpdateListeners()
        mediaHeaderAnimator?.removeAllListeners()
        mediaHeaderAnimator?.cancel()
        mediaHeaderAnimator = null
        expandAnimator?.removeAllUpdateListeners()
        expandAnimator?.removeAllListeners()
        expandAnimator?.cancel()
        expandAnimator = null
        avatarImageView?.imageReceiver?.setDelegate(null)
        avatarsViewPager?.onDestroy()
        avatarsBlurView = null
        pinchToZoomHelper?.clear()
        pinchToZoomHelper = null
        viewScope.cancel()
        super.onFragmentDestroy()
    }

    private fun openBrowser(url: String) {
        val context = getContext() ?: return
        Browser.openUrl(context, url)
    }

}

internal fun profileHeaderExtraDiff(
    extraHeight: Float,
    actionsExtraHeight: Int,
    headerOnlyExtraHeight: Int,
): Float {
    if (headerOnlyExtraHeight <= 0) return 0f
    return Utilities.clamp01((extraHeight - actionsExtraHeight) / headerOnlyExtraHeight)
}

internal fun profileShowsIdentityChrome(state: AppSessionState): Boolean =
    state is AppSessionState.Authenticated ||
        state is AppSessionState.Restoring && state.snapshot != null

internal fun profileSessionListTopPadding(
    profileChromeVisible: Boolean,
    profileTopPadding: Int,
): Int = if (profileChromeVisible) profileTopPadding else 0

internal fun profileListTopPadding(
    viewportWidth: Int,
    actionsExtraHeight: Int,
): Int = viewportWidth + actionsExtraHeight

internal fun profileInitialRowOffset(headerExtraHeight: Int, listTopPadding: Int): Int =
    headerExtraHeight - listTopPadding

internal fun profileOuterListBottomPadding(
    baseInset: Int,
    visibleHeight: Int,
    headerExtraHeight: Int,
    contentHeight: Int,
): Int = maxOf(baseInset, visibleHeight - headerExtraHeight - contentHeight)

internal enum class ProfileHeaderReleaseTarget {
    NONE,
    /** Collapse the surplus above the actions row. */
    ACTIONS,
    /** Settle fully collapsed behind the actions row. */
    COLLAPSED,
    /** Settle at the normal header height. */
    HEADER,
}

internal fun profileHeaderReleaseTarget(
    extraHeight: Float,
    headerExtraHeight: Int,
    actionsExtraHeight: Int,
    velocityY: Float,
): ProfileHeaderReleaseTarget {
    if (extraHeight <= 0f) return ProfileHeaderReleaseTarget.NONE
    val hasActions = actionsExtraHeight > 0
    if (
        hasActions &&
        (extraHeight < headerExtraHeight * 0.6f || velocityY < -1000f) &&
        extraHeight > actionsExtraHeight * 0.6f
    ) {
        return ProfileHeaderReleaseTarget.ACTIONS
    }
    if (hasActions && extraHeight < actionsExtraHeight * 0.6f) {
        return ProfileHeaderReleaseTarget.COLLAPSED
    }
    if (!hasActions && velocityY < -1000f) {
        return ProfileHeaderReleaseTarget.COLLAPSED
    }
    return ProfileHeaderReleaseTarget.HEADER
}

internal fun profileHeaderExpandedTargetTop(
    listWidth: Int,
    actionsExtraHeight: Int,
    actionBarHeight: Int,
): Int = listWidth + actionsExtraHeight - actionBarHeight

internal fun profileKeepsAvatarExpanded(
    allowPullingDown: Boolean,
    openingAvatar: Boolean,
    expandProgress: Float,
): Boolean = allowPullingDown && (openingAvatar || expandProgress >= 0.33f)

internal fun profileUsesFullAvatarGeometry(
    isPulledDown: Boolean,
    expandAnimatorValue: Float,
): Boolean = isPulledDown && expandAnimatorValue >= 0.999f

internal fun profileAvatarCloseUsesFlyOut(closing: Boolean, avatarScale: Float): Boolean =
    closing && avatarScale > 0.96f

internal fun profileMediaHeaderShouldOpen(contentTop: Int, pinTop: Int): Boolean = contentTop <= pinTop

internal fun profileViewportWidth(fragmentWidth: Int, listWidth: Int, displayWidth: Int): Int =
    fragmentWidth.takeIf { it > 0 }
        ?: listWidth.takeIf { it > 0 }
        ?: displayWidth

internal fun profileMediaHeaderTopBoundary(height: Int, progress: Float): Int =
    (height * (1f - Utilities.clamp01(progress))).toInt()

internal fun profileVersionFooterReplacesTerminalShadow(lastViewType: Int?): Boolean =
    lastViewType == UniversalAdapter.VIEW_TYPE_SHADOW

internal fun profileHeaderTitleLayerAlpha(layer: Int): Float = if (layer == 0) 0f else 1f

internal fun profileHeaderSubtitleLayerAlpha(layer: Int, mediaHeaderAnimationProgress: Float): Float =
    if (layer == 0) 0f else 1f - Utilities.clamp01(mediaHeaderAnimationProgress)

internal fun profileMediaCounterAlpha(mediaHeaderAnimationProgress: Float): Float =
    Utilities.clamp01(mediaHeaderAnimationProgress)

internal fun profileFeedSubtitleCount(state: ProfileFeedUiState): Int? = when {
    state.items.isNotEmpty() -> state.items.size
    state.isLoading || state.errorMessage != null -> null
    else -> 0
}

internal fun profileInnerScrollFromOuter(
    dyUnconsumed: Int,
    contentTop: Int,
    pinTop: Int,
): Int = if (profileMediaHeaderShouldOpen(contentTop, pinTop)) dyUnconsumed else 0

internal fun profileInnerScrollBackToTopRequest(
    dy: Int,
    firstPosition: Int,
    firstTop: Int,
    paddingTop: Int,
): Int {
    if (dy >= 0 || firstPosition == RecyclerView.NO_POSITION) return 0
    if (firstPosition != 0) return dy
    if (firstTop == paddingTop) return 0
    return maxOf(dy, firstTop - paddingTop)
}

internal fun profileContentViewportHeight(outerHeight: Int, pinTop: Int): Int =
    (outerHeight - pinTop).coerceAtLeast(0)

internal class ProfileViewModelFactory(
    private val repository: ProfileRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(repository = repository) as T
    }
}

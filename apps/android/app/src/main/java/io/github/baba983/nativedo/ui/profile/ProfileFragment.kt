/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ProfileActivity.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java
 *
 * Profile page with Telegram's collapsible profile structure:
 * TopView cover background, avatar with gooey/waterdrop absorption,
 * title/subtitle that animate on scroll.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.profile

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.text.Html
import android.text.InputType
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.BuildConfig
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import org.telegram.messenger.R as TelegramR
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
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
import io.github.baba983.nativedo.debug.DebugTopicFixtureSheet
import io.github.baba983.nativedo.ui.auth.LoginWebViewFragment
import io.github.baba983.nativedo.ui.main.MainTabsActivity
import io.github.baba983.nativedo.ui.profile.IconSettingCell.Factory as IconSettingFactory
import io.github.baba983.nativedo.ui.topic.TopicDetailFragment
import io.github.baba983.nativedo.ui.topic.toTopicInitialIdentity
import io.github.baba983.nativedo.ui.theme.ThemeSettingsFragment
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
import org.telegram.ui.web.WebBrowserSettings
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ButtonBounce
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ProfileGooeyView
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.StickerEmptyView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.ui.Cells.AboutLinkCell
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.PhotoViewer

private const val VIEW_TYPE_ABOUT = 10_004
private const val VIEW_TYPE_PROFILE_LOADING = 10_006
private const val VIEW_TYPE_PROFILE_EMPTY = 10_007
private const val VIEW_TYPE_PROFILE_CONTENT = 10_008
private const val PROFILE_RESUME_REFRESH_INTERVAL_MS = 30_000L

private const val ID_USERNAME = 10
private const val ID_TRUST_LEVEL = 11
private const val ID_TITLE = 12
private const val ID_TIME_READ = 13
private const val ID_JOINED = 14
private const val ID_LAST_SEEN = 15
private const val ID_PROFILE_VIEWS = 16
private const val ID_BADGE_COUNT = 17
private const val ID_ROLE = 18
private const val ID_GAMIFICATION = 19
private const val ID_ACCEPTED_ANSWERS = 20
private const val ID_FOLLOWERS = 21
private const val ID_FOLLOWING = 22
private const val ID_FLAIR = 23
private const val ID_PRIMARY_GROUP = 24
private const val ID_LAST_POSTED = 25
private const val ID_RECENT_TIME_READ = 26
private const val ID_CAKE_DATE = 27
private const val ACTION_DEV_URL = 1
private const val ACTION_TOPIC_URL = 2
private const val ACTION_TOPIC_RENDER_FIXTURES = 6
private const val ACTION_BROWSER_SETTINGS = 7
private const val ACTION_APPEARANCE = 8
private const val ACTION_RETRY_PROFILE = 3
private const val ACTION_LOGOUT = 4
private const val ACTION_LOGIN = 5
private const val MENU_OPEN_PROFILE = 101
private const val MENU_REFRESH_PROFILE = 102
private const val MENU_LOGIN = 103
private const val MENU_LOGOUT = 104
private const val MENU_OTHER = 105

// Identity/stats rows whose value can be long-pressed to copy, mirroring upstream
// ProfileActivity.processOnClickOrPress (copy on tap/long-press of info rows).
private val COPYABLE_ROW_IDS = setOf(
    ID_USERNAME, ID_ROLE, ID_TRUST_LEVEL, ID_TITLE, ID_GAMIFICATION,
    ID_ACCEPTED_ANSWERS, ID_FOLLOWERS, ID_FOLLOWING, ID_LAST_SEEN,
    ID_TIME_READ, ID_RECENT_TIME_READ, ID_PROFILE_VIEWS, ID_BADGE_COUNT, ID_JOINED,
    ID_FLAIR, ID_PRIMARY_GROUP, ID_LAST_POSTED, ID_CAKE_DATE,
)

/**
 * Profile page mirroring Telegram's ProfileActivity (the version used in the bottom tab).
 *
 * Scroll architecture (mirrors ProfileActivity):
 * - RecyclerView with paddingTop = headerExtraHeight
 * - extraHeight tracks how much of the header is visible (first child's top offset)
 * - diff = clamp01(extraHeight / headerOnlyExtraHeight) — 0 = collapsed, 1 = expanded
 * - Avatar uses scaleX/Y with ProfileGooeyView for waterdrop absorption effect
 * - TopView draws the cover background from y=0 (covers status bar)
 */
class ProfileFragment : BaseFragment(), MainTabsActivity.TabFragmentDelegate {
    private val fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var viewModel: ProfileViewModel
    private lateinit var appContainer: AppContainer
    private var listView: UniversalRecyclerView? = null
    private var universalAdapter: UniversalAdapter? = null
    private var sessionState: AppSessionState = AppSessionState.Unknown
    private var profileState: ProfileUiState = ProfileUiState()

    // Views (mirrors ProfileActivity view hierarchy)
    private var topView: TopView? = null
    private var avatarContainer: FrameLayout? = null
    private var avatarGooey: ProfileGooeyView? = null
    private var avatarImageView: AvatarImageView? = null
    private var avatarProgressView: RadialProgressView? = null
    private var avatarContainer2: FrameLayout? = null
    private var profileActionsView: DiscourseProfileActionsView? = null
    private var profileActionsEnabled: Boolean = false
    // ActionBar overflow menu (mirrors upstream ProfileActivity.otherItem —
    // single "..." item carrying a popup submenu of all profile actions, instead
    // of four flat top-level icons). L4036 / L11991–12118.
    private var otherItem: ActionBarMenuItem? = null
    private var nameTextView = arrayOfNulls<SimpleTextView>(2) // [0]=dark(collapsed), [1]=white(expanded)
    private var subtitleView = arrayOfNulls<SimpleTextView>(2)
    private var avatarViewerUrl: String = ""
    private var avatarViewerTitle: String = ""
    private var avatarViewerSubtitle: String = ""
    private var profileWebUrl: String = ""
    private var topicFixtureSheet: DebugTopicFixtureSheet? = null
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

    // Scroll state (mirrors ProfileActivity)
    private var extraHeight: Float = 0f
    private var avatarY: Float = 0f
    private var avatarScale: Float = 1f
    private var pullUpProgress: Float = 0f
    private var expandProgress: Float = 0f
    private var isPulledDown: Boolean = false
    private var allowPullingDown: Boolean = false
    private var listViewVelocityY: Float = 0f
    private var canExpandAvatar: Boolean = false

    // Expand animator (mirrors ProfileActivity.expandAnimator)
    private var expandAnimator: ValueAnimator? = null
    private var currentExpandAnimatorValue: Float = 0f
    private var currentExpandAnimatorFraction: Float = 0f
    private var expandAnimatorValues: FloatArray = floatArrayOf(0f, 1f)
    private var animateHeaderWithExpand = false
    private var expandAnimationCancelled = false
    private var animatedHeaderStartTop = 0
    private var animatedHeaderTargetTop = 0
    private var mediaHeaderAnimator: ValueAnimator? = null
    private var mediaHeaderVisible = false
    private var mediaHeaderProgress = 0f

    // --- Header height constants (mirrors ProfileActivity) ---

    private fun getHeaderOnlyExtraHeight(): Int {
        return AndroidUtilities.dp(if (getActionsExtraHeight() == 0) 168f else 152f)
    }

    private fun getActionsExtraHeight(): Int {
        return if (profileActionsEnabled) AndroidUtilities.dp(74f) else 0
    }

    private fun getHeaderExtraHeight(): Int = getHeaderOnlyExtraHeight() + getActionsExtraHeight()

    private fun calculateHeaderExtraDiff(): Float {
        return profileHeaderExtraDiff(extraHeight, getActionsExtraHeight(), getHeaderOnlyExtraHeight())
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
        // ProfileFragment is a bottom tab (MainTabsActivity.POSITION_PROFILE), not a
        // pushed fragment, so there's no parent to go back to — hide the back button
        // entirely. Upstream ProfileActivity hasMainTabs branch (L2480–2491) swaps the
        // back drawable for a QR icon; NativeDo has no QR feature, so we pass null,
        // which ActionBar.setBackButtonDrawable maps to backButtonImageView GONE.
        actionBar.setBackButtonDrawable(null)

        appContainer = (context.applicationContext as NativeDoApp).appContainer
        sessionState = appContainer.sessionCoordinator.sessionState.value
        profileActionsEnabled = sessionState is AppSessionState.Authenticated
        viewModel = ViewModelProvider(
            this,
            ProfileViewModelFactory(ProfileRepository(appContainer.discourseApiClient)),
        )[ProfileViewModel::class.java]
        setupProfileActionBarMenu(context)
        (sessionState as? AppSessionState.Authenticated)?.username?.let(::ensureProfileFeedModels)

        extraHeight = getHeaderExtraHeight().toFloat()

        // --- Container (mirrors ProfileActivity.NestedFrameLayout) ---
        val container = ContainerView(context)
        container.setWillNotDraw(false)
        container.setBackgroundColor(0)

        // --- RecyclerView (mirrors ProfileActivity.listView) ---
        val recycler = object : UniversalRecyclerView(
            this,
            { items, _ -> fillItems(items) },
            { item, view, _, _, _ -> handleItemClick(item, view) },
            { item, _, _, _, _ -> handleItemLongClick(item) },
        ) {
            private var velocityTracker: VelocityTracker? = null

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // A new drag takes ownership from the single profile expansion clock.
                        if (expandAnimator?.isRunning == true) {
                            expandAnimator?.cancel()
                            isPulledDown = currentExpandAnimatorValue >= 0.33f
                            allowPullingDown = extraHeight > getHeaderExtraHeight()
                        }
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
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    settleHeaderAfterTouch()
                }
                return result
            }
        }
        recycler.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        recycler.clipToPadding = false
        recycler.setPadding(
            0,
            getListTopForExtraHeight(getHeaderExtraHeight()),
            0,
            getOuterListBottomInset(),
        )

        // Custom LinearLayoutManager that supports allowPullingDown (overscroll to expand avatar).
        // Mirrors upstream ProfileActivity's custom layoutManager.scrollVerticallyBy.
        val recyclerRef = recycler
        val profileLayoutManager = object : LinearLayoutManager(context) {
            override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
                val view = findViewByPosition(0)
                if (view != null) {
                    val canScroll = view.top - getListTopForExtraHeight(getHeaderExtraHeight())
                    if (!allowPullingDown && canScroll > dy) {
                        // Snap to headerExtraHeight boundary, then enable pulling down
                        val result = super.scrollVerticallyBy(canScroll, recycler, state)
                        if (avatarImageView?.imageReceiver?.hasNotThumb() == true &&
                            !AndroidUtilities.isAccessibilityScreenReaderEnabled()
                        ) {
                            allowPullingDown = true
                        }
                        return result
                    } else if (allowPullingDown) {
                        if (dy >= canScroll) {
                            // User scrolled back past the boundary — disable pulling
                            allowPullingDown = false
                        } else if (recyclerRef.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            if (!isPulledDown) {
                                // Slow down the overscroll to give a "stretchy" feel
                                return super.scrollVerticallyBy(dy / 2, recycler, state)
                            }
                        }
                    }
                }
                return super.scrollVerticallyBy(dy, recycler, state)
            }
        }
        recycler.layoutManager = profileLayoutManager
        recycler.setLayoutManager(profileLayoutManager)

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                checkListViewScroll()
            }
        })
        container.addView(recycler, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL))

        // --- TopView (mirrors ProfileActivity.TopView — cover background from y=0) ---
        val tv = TopView(context)
        tv.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        container.addView(tv)
        topView = tv

        // --- avatarContainer2 (mirrors ProfileActivity — MATCH×MATCH overlay for avatar + name) ---
        val ac2 = FrameLayout(context)
        container.addView(ac2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.MATCH_PARENT.toFloat(), Gravity.START, 0f, 0f, 0f, 0f))
        avatarContainer2 = ac2

        // --- avatarContainer (the 100×100 avatar frame, positioned inside GooeyView) ---
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

        // --- ProfileGooeyView (mirrors ProfileActivity — waterdrop absorption effect) ---
        val gooey = ProfileGooeyView(context)
        gooey.addView(ac, LayoutHelper.createFrame(100f, 100f, Gravity.TOP or Gravity.LEFT))
        ac2.addView(gooey, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        avatarGooey = gooey

        // --- AvatarImageView (mirrors ProfileActivity.AvatarImageView — BackupImageView subclass) ---
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

        val avatarProgress = object : RadialProgressView(context) {
            private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55000000
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawCircle(width / 2f, height / 2f, width / 2f, scrimPaint)
                super.onDraw(canvas)
            }
        }
        avatarProgress.setSize(AndroidUtilities.dp(26f))
        avatarProgress.setProgressColor(Color.WHITE)
        ac.addView(avatarProgress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))
        avatarProgressView = avatarProgress
        showAvatarProgress(false, false)
        aiv.imageReceiver.setDelegate { _, set, thumb, _ ->
            showAvatarProgress(canExpandAvatar && (!set || thumb), true)
        }

        // --- nameTextView[0] (collapsed: dark, sits in actionBar area) ---
        // Mirrors upstream nameTextView[0] with key_actionBarDefaultTitle color.
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

        // --- nameTextView[1] (expanded: white, sits below avatar) ---
        // Mirrors upstream nameTextView[1] with key_profile_title → WHITE blend.
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

        // --- subtitleView[0] (collapsed: dark) ---
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

        // --- subtitleView[1] (expanded: white) ---
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

        val actions = DiscourseProfileActionsView(context, AndroidUtilities.dp(74f)).apply {
            setOnActionClickListener { key, _, _ -> handleProfileAction(key) }
            visibility = if (profileActionsEnabled) View.VISIBLE else View.GONE
        }
        ac2.addView(actions, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        profileActionsView = actions
        updateProfileActions(animated = false)
        actions.post(actions::finishOpeningLayout)

        // ActionBar on top of everything
        container.addView(actionBar)

        listView = recycler
        universalAdapter = recycler.adapter
        fragmentView = container

        updateLayout()
        updateInfo()

        // Telegram drives the gallery transition and list settle with one duration and
        // interpolator. NativeDo has one image instead of a gallery, so this animator
        // owns both the avatar progress and the optional first-row offset.
        expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { anim ->
                if (animateHeaderWithExpand) {
                    val layoutManager = listView?.layoutManager as? LinearLayoutManager
                    val top = AndroidUtilities.lerp(
                        animatedHeaderStartTop.toFloat(),
                        animatedHeaderTargetTop.toFloat(),
                        anim.animatedFraction,
                    ).toInt()
                    layoutManager?.scrollToPositionWithOffset(0, top)
                    checkListViewScroll()
                }
                setAvatarExpandProgress(anim.animatedFraction)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    expandAnimationCancelled = false
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    expandAnimationCancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!expandAnimationCancelled && animateHeaderWithExpand) {
                        (listView?.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(0, animatedHeaderTargetTop)
                        checkListViewScroll()
                    }
                    animateHeaderWithExpand = false
                    if (!expandAnimationCancelled && currentExpandAnimatorValue <= 0f) {
                        currentExpandAnimatorFraction = 0f
                        expandAnimatorValues[0] = 0f
                        expandAnimatorValues[1] = 1f
                    }
                }
            })
        }

        // Observe session state
        fragmentScope.launch {
            appContainer.sessionCoordinator.sessionState.collectLatest { state ->
                sessionState = state
                if (state is AppSessionState.Authenticated) {
                    ensureProfileFeedModels(state.username)
                } else {
                    clearProfileFeedModels()
                }
                updateProfileActionsAvailability(state is AppSessionState.Authenticated)
                viewModel.onSessionStateChanged(state)
                updateInfo()
                updateList()
            }
        }
        fragmentScope.launch {
            viewModel.uiState.collectLatest { state ->
                profileState = state
                updateInfo()
                updateList()
            }
        }

        return container
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
        topicsFeedJob = fragmentScope.launch {
            topics.uiState.collectLatest { state ->
                topicsFeedState = state
                profileContentLayout?.submit(ProfileFeedPage.Topics, state)
            }
        }
        repliesFeedJob = fragmentScope.launch {
            replies.uiState.collectLatest { state ->
                repliesFeedState = state
                profileContentLayout?.submit(ProfileFeedPage.Replies, state)
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

    // --- Layout (mirrors ProfileActivity.updateLayout / needLayout) ---

    private fun getStatusBarHeight(): Int = AndroidUtilities.getStatusBarHeight(context)

    private fun getActionBarHeight(): Int = ActionBar.getCurrentActionBarHeight()

    private fun getHeaderTopOffset(): Int = getStatusBarHeight() + getActionBarHeight()

    private fun getListTopForExtraHeight(extra: Int): Int = getHeaderTopOffset() + extra

    private fun getOuterListBottomInset(): Int = if (sessionState is AppSessionState.Authenticated) {
        0
    } else {
        AndroidUtilities.dp((MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 12).toFloat())
    }

    private fun updateLayout() {
        (listView?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = 0
        listView?.setPadding(
            0,
            getListTopForExtraHeight(getHeaderExtraHeight()),
            0,
            getOuterListBottomInset(),
        )

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
        updateProfileActions(animated = true)
        listView?.setPadding(
            0,
            getListTopForExtraHeight(newHeaderHeight),
            0,
            getOuterListBottomInset(),
        )
        val layoutManager = listView?.layoutManager as? LinearLayoutManager
        if (layoutManager?.findViewByPosition(0) != null) {
            layoutManager.scrollToPositionWithOffset(0, getHeaderTopOffset() + mappedExtraHeight.toInt())
            checkListViewScroll()
        } else {
            extraHeight = mappedExtraHeight
            needLayout()
        }
    }

    private fun updateProfileActions(animated: Boolean) {
        val actions = profileActionsView ?: return
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

    private fun handleProfileAction(key: Int) {
        val username = profileState.username
            ?: (sessionState as? AppSessionState.Authenticated)?.username
            ?: return
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
        profileContentLayout?.selectPage(page, animated = true)
        val recycler = listView ?: return
        val lastPosition = (recycler.adapter?.itemCount ?: 0) - 1
        if (lastPosition >= 0) {
            recycler.smoothScrollToPosition(lastPosition)
        }
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

    /**
     * NativeDo's ProfileActivity-shaped header layout.
     *
     * This method computes avatarScale, avatarY, expandProgress, pullUpProgress,
     * and drives the avatar visuals when expandAnimator is NOT running.
     * When isPulledDown triggers, the expandAnimator (via setAvatarExpandProgress)
     * takes over avatar sizing for the full-expand animation.
     *
     * Key upstream invariant: needLayout does NOT modify avatarContainer
     * width/height/margin when expandAnimator is running — that belongs
     * to setAvatarExpandProgress exclusively.
     */
    private fun needLayout() {
        val diff = calculateHeaderExtraDiff()
        val statusBarHeight = getStatusBarHeight()
        val actionBarHeight = getActionBarHeight()
        val newTop = statusBarHeight + actionBarHeight
        val width = getProfileViewportWidth()

        // --- TopView height ---
        topView?.let { tv ->
            val lp = tv.layoutParams as? FrameLayout.LayoutParams ?: return@let
            val h = statusBarHeight + actionBarHeight + extraHeight
            if (lp.height != h.toInt()) {
                lp.height = h.toInt()
                lp.topMargin = 0
                tv.layoutParams = lp
            }
        }

        val h = extraHeight

        // ===== BRANCH 1: h > headerExtraHeight || isPulledDown =====
        // Mirrors upstream lines 8414-8527
        if (h > getHeaderExtraHeight() || isPulledDown) {
            // expandProgress: how far past the header the scroll has gone
            val expandDistance = (width - newTop - getHeaderOnlyExtraHeight()).toFloat().coerceAtLeast(1f)
            expandProgress = Utilities.clamp01((h - getHeaderExtraHeight()) / expandDistance)

            // avatarScale: reaches max (1.38) when expandProgress >= 0.33
            avatarScale = AndroidUtilities.lerp(96f / 42f, 138f / 42f,
                Math.min(1f, expandProgress * 3f)) / 100f * 42f
            pullUpProgress = 0f

            // --- isPulledDown trigger (mirrors upstream lines 8429-8507) ---
            if (allowPullingDown && expandProgress >= 0.33f && !isPulledDown) {
                val rv = listView ?: return
                val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
                val view0 = layoutManager.findViewByPosition(0)
                if (view0 != null) {
                    val targetTopExpand = profileHeaderExpandedTargetTop(
                        listWidth = rv.measuredWidth,
                        actionsExtraHeight = getActionsExtraHeight(),
                        actionBarHeight = newTop,
                    )
                    animateAvatarAndHeader(targetTopExpand, expanded = true)
                }

                // Haptic (mirrors upstream 8503-8506)
                try {
                    avatarContainer?.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                    )
                } catch (_: Exception) {}

                return
            }

            // --- Avatar / name-online positioning in the expand zone ---
            // Upstream has two branches inside `h > headerExtraHeight || isPulledDown`:
            //   allowPullingDown && expandProgress >= 0.33  → expand zone (avatarsViewPager
            //     covers the screen; avatarContainer is set GONE on animator end)
            //   else                                          → drag/collapse zone
            // We have no avatarsViewPager, so we keep avatarContainer visible. When the
            // expandAnimator has finished and we're in the stable expand zone
            // (isPulledDown && expandProgress >= 0.33), we must KEEP the avatar at its
            // full-expand geometry — driving it from expandProgress here would shrink it
            // (expandProgress ~0.4 at the full-expand scroll position, NOT 1.0), which is
            // the "avatar only grows slightly" bug. Only drive from expandProgress when
            // NOT in the stable expand zone (still dragging, or scrolling back down past
            // the 0.33 threshold). Mirrors upstream 8515-8527 for the drag branch.
            if (expandAnimator?.isRunning != true) {
                if (!isPulledDown || expandProgress < 0.33f) {
                    // Drag / sub-threshold zone: drive avatar from expandProgress
                    avatarY = AndroidUtilities.lerp(avatarY, 0f, expandProgress)

                    avatarContainer?.let { ac ->
                        val params = ac.layoutParams as FrameLayout.LayoutParams
                        params.width = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), width.toFloat() / avatarScale, expandProgress).toInt()
                        params.height = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), (h + newTop) / avatarScale, expandProgress).toInt()
                        params.leftMargin = 0
                        ac.scaleX = avatarScale
                        ac.scaleY = avatarScale
                        ac.translationX = width / 2f - params.width * avatarScale * 0.5f
                        ac.translationY = Math.ceil(avatarY.toDouble()).toFloat()
                        ac.requestLayout()
                        avatarImageView?.setRoundRadiusForExpand(
                            AndroidUtilities.lerp(AndroidUtilities.dp(50f).toFloat(), 0f, expandProgress).toInt()
                        )
                    }
                } else {
                    // Stable expand zone (isPulledDown && expandProgress >= 0.33):
                    // maintain the value=1 full-expand geometry with the CURRENT extraHeight
                    // so the avatar always covers the TopView (mirrors upstream
                    // onAnimationEnd → topView.setBackgroundColor(BLACK) + avatarsViewPager
                    // sized to width × (h + newTop)).
                    avatarContainer?.let { ac ->
                        val params = ac.layoutParams as FrameLayout.LayoutParams
                        params.width = (width.toFloat() / avatarScale).toInt()
                        params.height = ((h + newTop) / avatarScale).toInt()
                        params.leftMargin = 0
                        ac.scaleX = avatarScale
                        ac.scaleY = avatarScale
                        ac.translationX = width / 2f - params.width * avatarScale * 0.5f
                        ac.translationY = 0f
                        ac.requestLayout()
                        avatarImageView?.setRoundRadiusForExpand(0)
                        ac.visibility = View.VISIBLE
                    }
                }

                // Name/online position at expanded position (both cases)
                // (mirrors upstream lines 8520-8523)
                nameTextView[1]?.let { nt ->
                    nt.translationX = AndroidUtilities.dpf2(18f) - nt.left
                    nt.translationY = newTop + h - getActionsExtraHeight() - AndroidUtilities.dpf2(30f) - nt.bottom
                }
                subtitleView[1]?.let { sv ->
                    sv.translationX = AndroidUtilities.dpf2(16f) - sv.left
                    sv.translationY = newTop + h - getActionsExtraHeight() - AndroidUtilities.dpf2(10f) - sv.bottom
                }
            }

        // ===== BRANCH 2: h <= headerExtraHeight (collapsed / normal scroll) =====
        // Mirrors upstream lines 8677-8800
        } else {
            expandProgress = 0f

            // Collapse trigger: when isPulledDown scrolls back into normal range
            // (mirrors upstream lines 8529-8591)
            if (isPulledDown) {
                isPulledDown = false
                animateAvatarAndHeader(targetTop = null, expanded = false)

                // Haptic on collapse
                try {
                    avatarContainer?.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                    )
                } catch (_: Exception) {}

                return
            }

            // Normal scroll range: compute collapsed avatar position
            currentExpandAnimatorValue = 0f
            currentExpandAnimatorFraction = 0f
            expandAnimatorValues = floatArrayOf(0f, 1f)

            val notchInfo = avatarGooey?.notchInfo
            val intoSize = if (notchInfo != null && notchInfo.isLikelyCircle) {
                0.5f * notchInfo.bounds.width() / AndroidUtilities.density
            } else {
                24f
            }
            avatarScale = AndroidUtilities.lerp(intoSize, 96f, diff) / 100f
            pullUpProgress = 1.0f - diff

            val endY = if (notchInfo != null && notchInfo.isLikelyCircle) {
                notchInfo.bounds.centerY()
            } else {
                -AndroidUtilities.dpf2(29f)
            }
            val startY = (newTop - AndroidUtilities.dpf2(21f))
            avatarY = AndroidUtilities.lerp(endY, startY, diff)

            avatarContainer?.let { ac ->
                val params = ac.layoutParams as FrameLayout.LayoutParams
                params.width = AndroidUtilities.dpf2(100f).toInt()
                params.height = AndroidUtilities.dpf2(100f).toInt()
                params.leftMargin = 0
                ac.layoutParams = params

                ac.scaleX = avatarScale
                ac.scaleY = avatarScale

                val avatarX = width / 2f - params.width * avatarScale * 0.5f
                ac.translationX = avatarX
                ac.translationY = Math.ceil(avatarY.toDouble()).toFloat()

                avatarImageView?.setRoundRadiusForExpand(AndroidUtilities.dp(50f))
            }

            updateGooey()

            // Name/subtitle position in normal scroll
            val endNameY = (statusBarHeight + actionBarHeight / 2f * (1f + diff) - AndroidUtilities.dp(21f))
            layoutNameAndSubtitle(diff, newTop, width, endNameY)
        }

        // --- Color blends ---
        applyExpandColorBlends(expandProgress)
        updateActionsPosition()
    }

    private fun settleHeaderAfterTouch() {
        val rv = listView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val view = layoutManager.findViewByPosition(0) ?: return
        val collapsedTop = getListTopForExtraHeight(getHeaderExtraHeight())
        val newTop = getStatusBarHeight() + getActionBarHeight()

        if (allowPullingDown) {
            // ProfileActivity settles the pull gesture from isPulledDown. The threshold
            // transition itself happens in needLayout; release only chooses the matching
            // stable list offset.
            if (isPulledDown) {
                val expandedTop = profileHeaderExpandedTargetTop(
                    listWidth = rv.measuredWidth,
                    actionsExtraHeight = getActionsExtraHeight(),
                    actionBarHeight = newTop,
                )
                smoothHeaderBy(view.top - expandedTop)
            } else {
                smoothHeaderBy(profileHeaderSettleDy(view.top, getHeaderTopOffset(), getHeaderExtraHeight()))
            }
        } else {
            val actionsHeight = getActionsExtraHeight()
            when (
                profileHeaderReleaseTarget(
                    extraHeight = extraHeight,
                    headerExtraHeight = getHeaderExtraHeight(),
                    actionsExtraHeight = actionsHeight,
                    velocityY = listViewVelocityY,
                )
            ) {
                ProfileHeaderReleaseTarget.COLLAPSED -> {
                    val dy = if (actionsHeight > 0) actionsHeight - extraHeight else extraHeight
                    smoothHeaderBy(dy.toInt())
                }
                ProfileHeaderReleaseTarget.ACTIONS -> smoothHeaderBy((extraHeight - actionsHeight).toInt())
                ProfileHeaderReleaseTarget.HEADER -> smoothHeaderBy(view.top - collapsedTop)
                ProfileHeaderReleaseTarget.NONE -> Unit
            }
        }
        listViewVelocityY = 0f
    }

    private fun smoothHeaderBy(dy: Int) {
        if (dy == 0) return
        listView?.smoothScrollBy(0, dy, CubicBezierInterpolator.EASE_OUT_QUINT)
    }

    private fun toggleAvatarExpansion() {
        if (!canExpandAvatar) return
        val rv = listView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val view = layoutManager.findViewByPosition(0) ?: return
        val newTop = getStatusBarHeight() + getActionBarHeight()
        val expandedTop = profileHeaderExpandedTargetTop(
            listWidth = rv.measuredWidth,
            actionsExtraHeight = getActionsExtraHeight(),
            actionBarHeight = newTop,
        ).coerceAtLeast(getListTopForExtraHeight(getHeaderExtraHeight()))
        val isExpanding = extraHeight <= getHeaderExtraHeight() + AndroidUtilities.dp(24f)
        val targetTop = if (!isExpanding) {
            getListTopForExtraHeight(getHeaderExtraHeight())
        } else {
            allowPullingDown = true
            expandedTop
        }
        // Mirrors upstream: haptic on expand/collapse
        avatarContainer?.performHapticFeedback(
            if (isExpanding) android.view.HapticFeedbackConstants.LONG_PRESS
            else android.view.HapticFeedbackConstants.KEYBOARD_TAP,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
        if (isExpanding) {
            animateAvatarAndHeader(targetTop, expanded = true)
        } else {
            animateAvatarAndHeader(targetTop, expanded = false)
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
        toggleAvatarExpansion()
        return true
    }

    private fun animateAvatarAndHeader(targetTop: Int?, expanded: Boolean) {
        val animator = expandAnimator ?: return
        val currentValue = currentExpandAnimatorValue
        val targetValue = if (expanded) 1f else 0f
        val remaining = Math.abs(targetValue - currentValue)
        if (remaining <= 0.001f && targetTop == null) return

        animator.cancel()
        expandAnimatorValues[0] = currentValue
        expandAnimatorValues[1] = targetValue
        isPulledDown = expanded
        allowPullingDown = expanded

        val layoutManager = listView?.layoutManager as? LinearLayoutManager
        val firstView = layoutManager?.findViewByPosition(0)
        animateHeaderWithExpand = targetTop != null && firstView != null
        if (animateHeaderWithExpand) {
            animatedHeaderStartTop = firstView!!.top
            animatedHeaderTargetTop = targetTop!!
        }

        val durationFactor = Math.min(
            AndroidUtilities.dpf2(2000f),
            Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY)),
        ) / AndroidUtilities.dpf2(1100f)
        animator.interpolator = CubicBezierInterpolator.EASE_BOTH
        animator.duration = Math.max(1L, (remaining * 250f / durationFactor).toLong())
        animator.start()
    }

    /**
     * Mirrors ProfileActivity.checkListViewScroll().
     * Computes extraHeight from the first list item's position.
     */
    private fun checkListViewScroll() {
        val rv = listView ?: return
        if (rv.visibility != View.VISIBLE || rv.childCount <= 0) return

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
            val topOffset = getHeaderTopOffset()
            if (top >= topOffset && adapterPosition == 0) {
                newOffset = top - topOffset
            }
        }

        if (extraHeight != newOffset.toFloat()) {
            extraHeight = newOffset.toFloat()
            topView?.invalidate()
            needLayout()
        }
        val content = profileContentLayout
        setMediaHeaderVisible(
            content != null && content.isShown &&
                profileMediaHeaderShouldOpen(content.top, getHeaderTopOffset()),
        )
    }

    /**
     * Mirrors ProfileActivity.updateGooey().
     * Controls the gooey/waterdrop absorption effect.
     */
    private fun updateGooey() {
        val gooey = avatarGooey ?: return
        gooey.setPullProgress(pullUpProgress)
        // Mirrors upstream: MathUtils.clamp(pullUpProgress, 0.2f, 0.7f)
        // MathUtils.clamp(value, min, max) — our Utilities.clamp(value, max, min) has reversed args
        val clamped = androidx.core.math.MathUtils.clamp(pullUpProgress, 0.2f, 0.7f)
        // Mirrors upstream: blur intensity caps at 0.75f, not 1.0f (line 6007)
        gooey.setBlurIntensity(Math.min((clamped - 0.2f) / 0.5f, 0.75f))
        gooey.setGooeyEnabled(pullUpProgress > 0 && pullUpProgress < 1)
        gooey.visibility = if (pullUpProgress >= 1.0f) View.GONE else View.VISIBLE
    }

    // --- Expand animator (mirrors ProfileActivity.expandAnimator + setAvatarExpandProgress) ---

    /**
     * Start the dedicated expand/collapse animator.
     * Mirrors upstream: when isPulledDown triggers, the expandAnimator smoothly
     * transitions the avatar from its current state to fully expanded (value=1)
     * or fully collapsed (value=0).
     */
    /**
     * ProfileActivity-shaped avatar expansion adapted to NativeDo's single image view.
     * Called by expandAnimator on each frame. This is the ONLY place that modifies
     * avatarContainer width/height/margin during the expand/collapse animation.
     */
    private fun setAvatarExpandProgress(animatedFraction: Float) {
        val value = AndroidUtilities.lerp(expandAnimatorValues[0], expandAnimatorValues[1], animatedFraction)
        currentExpandAnimatorValue = value
        currentExpandAnimatorFraction = animatedFraction
        profileActionsView?.setParentExpanded(value)

        val newTop = getStatusBarHeight() + getActionBarHeight()
        val width = getProfileViewportWidth()

        // --- Avatar scale (pre-computed by needLayout, just apply) ---
        avatarContainer?.scaleX = avatarScale
        avatarContainer?.scaleY = avatarScale

        // --- Avatar translation Y (mirrors upstream line 6255) ---
        avatarContainer?.translationY = AndroidUtilities.lerp(Math.ceil(avatarY.toDouble()).toFloat(), 0f, value)

        // --- Round radius: dp(50) circle → 0 rectangle (mirrors upstream line 6256) ---
        avatarImageView?.setRoundRadiusForExpand(
            AndroidUtilities.lerp(AndroidUtilities.dp(50f).toFloat(), 0f, value).toInt()
        )

        // --- Avatar container size — THE KEY: this makes the avatar fill the screen ---
        // Mirrors upstream lines 6385-6390 exactly. Upstream mutates the params object
        // in place, calls fixAvatarImageInCenter() (sets translationX from the new
        // params), then avatarContainer.requestLayout(). We match that order instead
        // of setLayoutParams(params) so requestLayout is explicit and translationX
        // reflects the new width/margin before the next layout pass.
        avatarContainer?.let { ac ->
            val params = ac.layoutParams as FrameLayout.LayoutParams
            params.width = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), width.toFloat() / avatarScale, value).toInt()
            params.height = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), (extraHeight + newTop) / avatarScale, value).toInt()
            params.leftMargin = 0
            // Center horizontally (mirrors upstream fixAvatarImageInCenter, called
            // BEFORE requestLayout so translationX reflects the new params)
            ac.translationX = width / 2f - params.width * avatarScale * 0.5f
            ac.requestLayout()
        }

        // --- Name/online position (mirrors upstream lines 6208-6215) ---
        val diff = calculateHeaderExtraDiff()
        if (!isPulledDown) {
            refreshNameAndOnlineYBasedOnExpand(diff, newTop, value)
        } else if (extraHeight >= getHeaderOnlyExtraHeight() && expandProgress < 0.33f) {
            // Mirrors upstream: isPulledDown but scrolling back into the collapse
            // transition zone (extraHeight still >= headerOnly, but expandProgress
            // dropped below 0.33). Upstream calls refreshNameAndOnlineY() which
            // recomputes nameY/onlineY from the current scroll; our port re-runs
            // the same Bezier-curve positioning with the isPulledDown control point.
            refreshNameAndOnlineYBasedOnExpand(diff, newTop, value)
        }

        // --- Name scale (mirrors upstream lines 6369-6371) ---
        nameTextView[1]?.let { nt ->
            nt.scaleX = AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, value)
            nt.scaleY = AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, value)
            nt.setPivotY(AndroidUtilities.lerp(0f, nt.measuredHeight.toFloat(), value))
        }

        // --- Color blends ---
        applyExpandColorBlends(value)

        // Telegram swaps the small avatar for avatarsViewPager here. NativeDo has a
        // single Discourse image, so the expanded AvatarImageView is the successor and
        // must stay visible throughout the transition.
        avatarContainer?.visibility = profileAvatarContainerVisibility(
            expandValue = value,
            hasExpandedMediaSuccessor = false,
        )

        topView?.invalidate()
        updateActionsPosition()
    }

    /**
     * Mirrors upstream refreshNameAndOnlineYBasedOnExpand (lines 8802-8960).
     * Positions name/online text during expandAnimator using quadratic Bezier curves.
     */
    private fun refreshNameAndOnlineYBasedOnExpand(diff: Float, newTop: Int, value: Float) {
        val width = getProfileViewportWidth()
        val kx = AndroidUtilities.dpf2(8f)
        val ky = if (isPulledDown) AndroidUtilities.dpf2(8f) else AndroidUtilities.dpf2(-24f)

        // --- Name X: quadratic Bezier from collapsed position → expanded position ---
        for (a in 0..1) {
            val nt = nameTextView[a] ?: continue
            val lp = nt.layoutParams as FrameLayout.LayoutParams

            // Start position (current scroll-driven position)
            val availWidth = if (a == 0 || lp.width <= 0) width.toFloat() else lp.width.toFloat()
            val textWidth = Math.min(nt.getTextWidth().toFloat(), availWidth) * nt.scaleX
            val nameXStart = if (a == 0) -AndroidUtilities.dpf2(42f + 21f) else
                AndroidUtilities.lerp(-AndroidUtilities.dpf2(42f + 21f),
                    width / 2f - (lp.leftMargin + textWidth * 0.5f), diff)

            // End position (mirrors upstream line 6326: dpf2(18) - leftMargin)
            val nameXEnd = AndroidUtilities.dpf2(18f) - lp.leftMargin

            // Quadratic Bezier: (1-t)²*P0 + 2(1-t)t*C + t²*P1
            val cx = kx + nameXStart + (nameXEnd - nameXStart) / 2f
            nt.translationX = (1f - value) * (1f - value) * nameXStart +
                2f * (1f - value) * value * cx +
                value * value * nameXEnd

            nt.alpha = profileHeaderTitleLayerAlpha(a)
        }

        // --- Online/subtitle X ---
        for (a in 0..1) {
            val sv = subtitleView[a] ?: continue
            val lp = sv.layoutParams as FrameLayout.LayoutParams
            val mWidth = if (sv.measuredWidth > 0) sv.measuredWidth.toFloat() else width.toFloat()
            val subWidth = Math.min(sv.getTextWidth().toFloat(), mWidth)

            val onlineXStart = AndroidUtilities.lerp(-AndroidUtilities.dpf2(42f + 21f),
                width / 2f - (lp.leftMargin + subWidth * 0.5f), diff)
            val onlineXEnd = AndroidUtilities.dpf2(16f) - lp.leftMargin

            val cx = kx + onlineXStart + (onlineXEnd - onlineXStart) / 2f
            sv.translationX = (1f - value) * (1f - value) * onlineXStart +
                2f * (1f - value) * value * cx +
                value * value * onlineXEnd

            sv.alpha = profileHeaderSubtitleLayerAlpha(a, mediaHeaderProgress)
        }

        // --- Name Y: quadratic Bezier (mirrors upstream lines 6329-6338) ---
        avatarContainer?.let { ac ->
            val endNameY = (getStatusBarHeight() + getActionBarHeight() / 2f * (1f + diff) - AndroidUtilities.dp(21f))
            val minNameY = Math.floor(endNameY.toDouble()).toFloat() + AndroidUtilities.dp(1.3f)
            val minOnlineY = minNameY + AndroidUtilities.dpf2(22.7f)

            // Name Y start: current scroll position
            val avatarBottom = Math.floor(endNameY.toDouble()).toFloat() +
                (ac.height * ac.scaleY + AndroidUtilities.dpf2(8f)) * diff
            val nameYStart = avatarBottom + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7f) * diff
            // Name Y end: bottom of expanded cover (mirrors upstream line 6327)
            val nameYEnd = newTop + extraHeight - getActionsExtraHeight() - AndroidUtilities.dpf2(30f) -
                (nameTextView[1]?.bottom ?: 0)

            val nameCy = ky + nameYStart + (nameYEnd - nameYStart) / 2f
            val nameY = (1f - value) * (1f - value) * nameYStart +
                2f * (1f - value) * value * nameCy +
                value * value * nameYEnd
            val clampedNameY = Math.max(minNameY, nameY)

            // Online Y start and end
            val onlineYStart = avatarBottom + AndroidUtilities.dp(24f) + 11f * AndroidUtilities.density * diff
            val onlineYEnd = newTop + extraHeight - getActionsExtraHeight() - AndroidUtilities.dpf2(10f) -
                (subtitleView[1]?.bottom ?: 0)

            val onlineCy = ky + onlineYStart + (onlineYEnd - onlineYStart) / 2f
            val onlineY = (1f - value) * (1f - value) * onlineYStart +
                2f * (1f - value) * value * onlineCy +
                value * value * onlineYEnd
            val clampedOnlineY = Math.max(minOnlineY, onlineY)

            for (a in 0..1) {
                nameTextView[a]?.translationY = clampedNameY
                subtitleView[a]?.translationY = clampedOnlineY
            }
        }
    }

    /**
     * Layout name and subtitle during normal (non-animated) scroll.
     * Used by needLayout() in collapsed/normal scroll range.
     */
    private fun layoutNameAndSubtitle(diff: Float, newTop: Int, width: Int, endNameY: Float) {
        val nameScale = 1.0f + 0.12f * diff

        for (a in 0..1) {
            val nt = nameTextView[a] ?: continue
            val layoutParams = nt.layoutParams as FrameLayout.LayoutParams

            nt.scaleX = nameScale
            nt.scaleY = nameScale
            nt.setPivotY(0f)

            nt.alpha = profileHeaderTitleLayerAlpha(a)

            val availableWidth = if (a == 0 || layoutParams.width <= 0) width.toFloat() else layoutParams.width.toFloat()
            val textWidth = Math.min(nt.getTextWidth().toFloat(), availableWidth) * nt.scaleX
            val nameX = AndroidUtilities.lerp(-AndroidUtilities.dpf2(42f + 21f),
                width / 2f - (layoutParams.leftMargin + textWidth * 0.5f), diff)
            nt.translationX = nameX
        }

        for (a in 0..1) {
            val sv = subtitleView[a] ?: continue
            val layoutParams = sv.layoutParams as FrameLayout.LayoutParams
            val measuredWidth = if (sv.measuredWidth > 0) sv.measuredWidth.toFloat() else width.toFloat()
            val subtitleWidth = Math.min(sv.getTextWidth().toFloat(), measuredWidth)
            val onlineX = AndroidUtilities.lerp(-AndroidUtilities.dpf2(42f + 21f),
                width / 2f - (layoutParams.leftMargin + subtitleWidth * 0.5f), diff)
            sv.translationX = onlineX
            sv.alpha = profileHeaderSubtitleLayerAlpha(a, mediaHeaderProgress)
        }

        avatarContainer?.let { ac ->
            if (extraHeight > getHeaderExtraHeight() || isPulledDown) {
                val nameY = newTop + extraHeight - getActionsExtraHeight() - AndroidUtilities.dpf2(30f) - (nameTextView[1]?.bottom ?: 0)
                val onlineY = newTop + extraHeight - getActionsExtraHeight() - AndroidUtilities.dpf2(10f) - (subtitleView[1]?.bottom ?: 0)
                val minNameY = Math.floor(endNameY.toDouble()).toFloat() + AndroidUtilities.dp(1.3f)
                val minOnlineY = minNameY + AndroidUtilities.dpf2(22.7f)

                for (a in 0..1) {
                    nameTextView[a]?.translationY = Math.max(minNameY, nameY)
                    subtitleView[a]?.translationY = Math.max(minOnlineY, onlineY)
                }
            } else {
                val avatarBottom = Math.floor(endNameY.toDouble()).toFloat() +
                    (ac.height * ac.scaleY + AndroidUtilities.dpf2(8f)) * diff
                val nameY = avatarBottom + AndroidUtilities.dp(1.3f) + AndroidUtilities.dp(7f) * diff
                val onlineY = avatarBottom + AndroidUtilities.dp(24f) + 11f * AndroidUtilities.density * diff
                for (a in 0..1) {
                    nameTextView[a]?.translationY = nameY
                    subtitleView[a]?.translationY = onlineY
                }
            }
        }
    }

    /**
     * Apply color blends based on expand progress.
     * Shared between needLayout() and setAvatarExpandProgress().
     */
    private fun applyExpandColorBlends(progress: Float) {
        updateHeaderTextWidths(progress)

        val mediaTitleColor = ColorUtils.blendARGB(
            Theme.getColor(Theme.key_profile_title),
            Theme.getColor(Theme.key_player_actionBarTitle),
            mediaHeaderProgress,
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
        subtitleView[1]?.alpha = 1f - mediaHeaderProgress

        val profileIconColor = ColorUtils.blendARGB(
            Theme.getColor(Theme.key_actionBarDefaultIcon),
            Color.WHITE,
            progress,
        )
        actionBar.setItemsColor(
            ColorUtils.blendARGB(
                profileIconColor,
                Theme.getColor(Theme.key_actionBarActionModeDefaultIcon),
                mediaHeaderProgress,
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
                mediaHeaderProgress,
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
            mediaHeaderProgress,
            if (visible) 1f else 0f,
        ).apply {
            duration = 150L
            interpolator = CubicBezierInterpolator.DEFAULT
            addUpdateListener { animation ->
                mediaHeaderProgress = animation.animatedValue as Float
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

    private fun updateHeaderTextWidths(progress: Float) {
        val viewWidth = getProfileViewportWidth()
        val diff = calculateHeaderExtraDiff()
        val name = nameTextView[1] ?: return
        val nameScale = AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, progress)
        val minScale: Float
        val maxScale: Float
        if (extraHeight > getHeaderExtraHeight() || expandAnimator?.isRunning == true) {
            minScale = nameScale
            maxScale = nameScale
        } else if (diff >= 1f) {
            minScale = maxOf(nameScale, 1.12f)
            maxScale = minScale
        } else {
            minScale = 1f
            maxScale = 1.12f
        }

        // Mirrors ProfileActivity.needLayoutText(): the collapsed width leaves room
        // for ActionBar controls, while the expanded width tracks the active scale.
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
            name.requestLayout()
        }

        val subtitle = subtitleView[1] ?: return
        val subtitleWidth = Math.ceil(
            AndroidUtilities.lerp(minOnlineWidth.toFloat(), maxOnlineWidth.toFloat(), interpolation).toDouble(),
        ).toInt()
        val subtitleParams = subtitle.layoutParams as FrameLayout.LayoutParams
        if (subtitleParams.width != subtitleWidth) {
            subtitleParams.width = subtitleWidth
            subtitle.requestLayout()
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
                title = context?.getString(R.string.profile_session_expired_title).orEmpty()
                subtitle = context?.getString(R.string.profile_session_expired_subtitle).orEmpty()
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

        // Set avatar
        avatarImageView?.let { aiv ->
            aiv.localAvatarDrawable.setInfo(0, title)
            canExpandAvatar = showAvatar && (avatarTemplate.isNotEmpty() || animatedAvatarUrl.isNotEmpty())
            aiv.isClickable = canExpandAvatar
            aiv.isLongClickable = canExpandAvatar
            aiv.importantForAccessibility = if (canExpandAvatar) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (showAvatar && (avatarTemplate.isNotEmpty() || animatedAvatarUrl.isNotEmpty())) {
                val avatarUrl = resolveAvatarUrl(appContainer.baseUrl, avatarTemplate, animatedAvatarUrl, 360)
                aiv.imageReceiver.setAllowDecodeSingleFrame(animatedAvatarUrl.isBlank())
                showAvatarProgress(true, false)
                aiv.imageReceiver.setImage(avatarUrl, avatarImageFilter(animatedAvatarUrl, 360), aiv.localAvatarDrawable, null, 0)
            } else {
                showAvatarProgress(false, false)
                aiv.imageReceiver.setAllowDecodeSingleFrame(true)
                aiv.imageReceiver.setImageBitmap(null as android.graphics.Bitmap?)
            }
        }

        updateProfileActionBarMenu()

        needLayout()
    }

    private fun showAvatarProgress(show: Boolean, animated: Boolean) {
        val progress = avatarProgressView ?: return
        progress.animate().setListener(null).withEndAction(null).cancel()
        if (!animated) {
            progress.alpha = if (show) 1f else 0f
            progress.visibility = if (show) View.VISIBLE else View.INVISIBLE
            return
        }
        if (show) {
            progress.visibility = View.VISIBLE
            progress.animate().alpha(1f).setDuration(180L).start()
        } else {
            progress.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    if (progress.alpha == 0f) {
                        progress.visibility = View.INVISIBLE
                    }
                }
                .start()
        }
    }

    private fun setupProfileActionBarMenu(context: Context) {
        val menu = actionBar.createMenu()
        // Mirrors upstream ProfileActivity L4036: a single overflow ("...") item
        // carries all profile actions as a popup submenu, instead of four flat
        // top-level icons. Sub-items are added once and toggled via show/hideSubItem
        // (upstream L11991–12118 uses the same pattern).
        val overflow = menu.addItem(MENU_OTHER, TelegramR.drawable.ic_ab_other)
        overflow.setContentDescription(context.getString(R.string.action_more_options))
        // Pass icons as int resources (not Drawable) so ActionBarMenuSubItem
        // applies the key_actionBarDefaultSubmenuItemIcon MULTIPLY tint — the
        // Drawable overload clears the color filter and renders vector icons at
        // their native fillColor (#FFFFFF = invisible on light submenu bg).
        // Mirrors upstream addSubItem(id, R.drawable.msg_*, text) (L518).
        overflow.addSubItem(MENU_OPEN_PROFILE, R.drawable.settings_link,
            context.getString(R.string.profile_menu_open_in_browser))
        overflow.addSubItem(MENU_REFRESH_PROFILE, R.drawable.settings_refresh,
            context.getString(R.string.profile_menu_refresh))
        overflow.addSubItem(MENU_LOGIN, R.drawable.settings_login,
            context.getString(R.string.profile_menu_login))
        // Logout sub-item marked red, mirroring upstream report/block_contact red
        // sub-items (ProfileActivity L12040). key_text_RedBold = red text + icon.
        overflow.addSubItem(MENU_LOGOUT, R.drawable.settings_logout,
            context.getString(R.string.profile_menu_logout))
            .setColors(Theme.getColor(Theme.key_text_RedBold), Theme.getColor(Theme.key_text_RedBold))
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
                        MENU_LOGOUT -> showLogoutConfirm()
                    }
                }
            },
        )
        updateProfileActionBarMenu()
    }

    private fun updateProfileActionBarMenu() {
        val authenticated = sessionState is AppSessionState.Authenticated
        val overflow = otherItem ?: return
        if (profileWebUrl.isNotBlank()) overflow.showSubItem(MENU_OPEN_PROFILE) else overflow.hideSubItem(MENU_OPEN_PROFILE)
        if (authenticated) overflow.showSubItem(MENU_REFRESH_PROFILE) else overflow.hideSubItem(MENU_REFRESH_PROFILE)
        if (authenticated) overflow.hideSubItem(MENU_LOGIN) else overflow.showSubItem(MENU_LOGIN)
        if (authenticated) overflow.showSubItem(MENU_LOGOUT) else overflow.hideSubItem(MENU_LOGOUT)
    }

    private fun buildHeaderSubtitle(profile: UserProfile?): String {
        if (profile == null) return ""
        val activity = formatRelativeActivity(profile.lastSeenAt)
        val title = profile.title.takeIf(String::isNotBlank)
        val group = profile.primaryGroupName.takeIf(String::isNotBlank)
        return listOfNotNull(
            activity.takeIf(String::isNotBlank)?.let { "最近活动 $it" },
            title,
            group,
        ).take(2).joinToString(" · ")
    }

    private fun openAvatarViewer() {
        val url = avatarViewerUrl.takeIf(String::isNotBlank) ?: return
        val avatarView = avatarImageView ?: return
        val viewer = PhotoViewer.getInstance()
        viewer.setParentActivity(this)
        val provider = object : PhotoViewer.EmptyPhotoViewerProvider() {
            override fun getPlaceForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                index: Int,
                needPreview: Boolean,
                closing: Boolean,
            ): PhotoViewer.PlaceProviderObject {
                val parent = fragmentView ?: avatarView
                val viewLocation = IntArray(2)
                avatarView.getLocationInWindow(viewLocation)
                val receiver = avatarView.imageReceiver
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = viewLocation[0]
                    viewY = viewLocation[1]
                    viewWidth = receiver.imageWidth.toInt().coerceAtLeast(1)
                    viewHeight = receiver.imageHeight.toInt().coerceAtLeast(1)
                    parentView = parent
                    imageReceiver = receiver
                    thumb = if (needPreview) receiver.getBitmapSafe() else null
                    radius = receiver.getRoundRadius(true)
                    clipTopAddition = getHeaderTopOffset()
                    clipBottomAddition = 0
                }
            }

            override fun getThumbForPhoto(
                messageObject: MessageObject?,
                fileLocation: TLRPC.FileLocation?,
                index: Int,
            ): ImageReceiver.BitmapHolder? = avatarView.imageReceiver.getBitmapSafe()
        }
        val item = PhotoViewer.UrlPhotoItem(
            url,
            null,
            avatarView.measuredWidth.coerceAtLeast(1),
            avatarView.measuredHeight.coerceAtLeast(1),
            null,
            avatarViewerTitle,
            avatarViewerSubtitle,
        )
        if (!viewer.openPhotoItems(listOf(item), 0, provider)) {
            openBrowser(url)
        }
    }

    // --- List ---

    private fun updateList() {
        universalAdapter?.update(false)
    }

    private fun fillItems(items: ArrayList<UItem>) {
        val state = sessionState
        if (state is AppSessionState.Authenticated) {
            when {
                profileState.isLoading -> {
                    items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
                    items.add(UItem.ofFactory(ProfileLoadingFactory()).apply { id = VIEW_TYPE_PROFILE_LOADING })
                    items.add(UItem.asShadow(null))
                }
                profileState.errorMessage != null -> {
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
                    // A profile-load failure on a session that was only restored from cache
                    // (never re-verified against the server) most likely means the session is
                    // actually dead — cookies look present locally but the server rejects them
                    // (e.g. server-side logout or Cloudflare). Surface a re-login path so the
                    // user isn't stuck with a broken cached profile and no login button.
                    if (state.freshness == SessionFreshness.Cached) {
                        items.add(
                            IconSettingFactory.of(
                                ACTION_LOGIN,
                                IconSettingCell.Colors.RED_TOP, IconSettingCell.Colors.RED_BOTTOM,
                                R.drawable.settings_login,
                                context?.getString(R.string.profile_session_expired_title).orEmpty(),
                                context?.getString(R.string.profile_session_expired_subtitle).orEmpty(),
                                context?.getString(R.string.profile_login_again_action).orEmpty(),
                            )
                        )
                    }
                    items.add(UItem.asShadow(null))
                }
                profileState.profile != null -> {
                    val profile = profileState.profile!!
                    var hasProfileInfo = false

                    if (profile.bio.isNotBlank()) {
                        items.add(UItem.ofFactory(AboutFactory()).apply { id = VIEW_TYPE_ABOUT })
                        hasProfileInfo = true
                    }

                    // Telegram keeps the profile information in consecutive
                    // TextDetailCell blocks. Discourse-specific labels keep each row
                    // understandable without inserting a header and shadow per group.
                    val username = profile.username
                    val trustLabel = trustLevelLabel(profile.trustLevel)
                    val hasTitle = profile.title.isNotBlank()
                    val role = when {
                        profile.admin -> "管理员"
                        profile.moderator -> "版主"
                        else -> null
                    }
                    val flair = profile.flairName.trim()
                    val primaryGroup = profile.primaryGroupName.trim()
                    val lastSeen = formatDate(profile.lastSeenAt)
                    val lastPosted = formatRelativeActivity(profile.lastPostedAt)
                    val joined = formatDate(profile.createdAt)
                    if (username.isNotBlank() || trustLabel != null || hasTitle || role != null) {
                        hasProfileInfo = true
                        if (username.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_USERNAME, "@$username",
                                context?.getString(R.string.profile_username_label).orEmpty()))
                        }
                        if (role != null) {
                            items.add(TextDetailCell.Factory.of(ID_ROLE, role, "角色"))
                        }
                        if (trustLabel != null) {
                            items.add(TextDetailCell.Factory.of(ID_TRUST_LEVEL, trustLabel,
                                context?.getString(R.string.profile_trust_level_label).orEmpty()))
                        }
                        if (hasTitle) {
                            items.add(TextDetailCell.Factory.of(ID_TITLE, profile.title,
                                context?.getString(R.string.profile_title_label).orEmpty()))
                        }
                    }

                    if (flair.isNotBlank() || primaryGroup.isNotBlank()) {
                        hasProfileInfo = true
                        if (flair.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_FLAIR, flair,
                                context?.getString(R.string.profile_flair_label).orEmpty()))
                        }
                        if (primaryGroup.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_PRIMARY_GROUP, primaryGroup,
                                context?.getString(R.string.profile_group_label).orEmpty()))
                        }
                    }

                    val hasStats = profile.gamificationScore > 0 || profile.acceptedAnswers > 0 ||
                        profile.totalFollowers > 0 || profile.totalFollowing > 0 ||
                        profile.profileViewCount > 0 || profile.badgeCount > 0
                    if (hasStats) {
                        hasProfileInfo = true
                        if (profile.gamificationScore > 0) {
                            items.add(TextDetailCell.Factory.of(ID_GAMIFICATION, profile.gamificationScore.toString(),
                                context?.getString(R.string.profile_gamification_label).orEmpty()))
                        }
                        if (profile.acceptedAnswers > 0) {
                            items.add(TextDetailCell.Factory.of(ID_ACCEPTED_ANSWERS, profile.acceptedAnswers.toString(),
                                context?.getString(R.string.profile_accepted_answers_label).orEmpty()))
                        }
                        if (profile.totalFollowers > 0) {
                            items.add(TextDetailCell.Factory.of(ID_FOLLOWERS, profile.totalFollowers.toString(),
                                context?.getString(R.string.profile_followers_label).orEmpty()))
                        }
                        if (profile.totalFollowing > 0) {
                            items.add(TextDetailCell.Factory.of(ID_FOLLOWING, profile.totalFollowing.toString(),
                                context?.getString(R.string.profile_following_label).orEmpty()))
                        }
                        if (profile.profileViewCount > 0) {
                            items.add(TextDetailCell.Factory.of(ID_PROFILE_VIEWS, profile.profileViewCount.toString(),
                                context?.getString(R.string.profile_profile_views_label).orEmpty()))
                        }
                        if (profile.badgeCount > 0) {
                            items.add(TextDetailCell.Factory.of(ID_BADGE_COUNT, profile.badgeCount.toString(),
                                context?.getString(R.string.profile_badges_label).orEmpty()))
                        }
                    }

                    val hasActivity = lastSeen.isNotBlank() || lastPosted.isNotBlank() ||
                        profile.timeRead > 0 || profile.recentTimeRead > 0 || joined.isNotBlank() ||
                        profile.cakeDate.isNotBlank()
                    if (hasActivity) {
                        hasProfileInfo = true
                        if (lastSeen.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_LAST_SEEN, formatRelativeActivity(profile.lastSeenAt),
                                context?.getString(R.string.profile_last_seen_label).orEmpty()))
                        }
                        if (lastPosted.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_LAST_POSTED, lastPosted,
                                context?.getString(R.string.profile_last_posted_label).orEmpty()))
                        }
                        if (profile.timeRead > 0) {
                            items.add(TextDetailCell.Factory.of(ID_TIME_READ, formatTimeRead(profile.timeRead),
                                context?.getString(R.string.profile_time_read_label).orEmpty()))
                        }
                        if (profile.recentTimeRead > 0) {
                            items.add(TextDetailCell.Factory.of(ID_RECENT_TIME_READ, formatTimeRead(profile.recentTimeRead),
                                context?.getString(R.string.profile_recent_time_read_label).orEmpty()))
                        }
                        if (joined.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_JOINED, joined,
                                context?.getString(R.string.profile_joined_label).orEmpty()))
                        }
                        if (profile.cakeDate.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_CAKE_DATE, formatDate(profile.cakeDate),
                                context?.getString(R.string.profile_cake_date_label).orEmpty()))
                        }
                    }
                    if (hasProfileInfo) {
                        items.add(UItem.asShadow(null))
                    }

                    // --- Account action row (no header — mirrors upstream single
                    // action rows like unblockRow/deleteReactionRow: a lone logout
                    // doesn't need its own section header) ---
                    items.add(
                        IconSettingFactory.of(
                            ACTION_LOGOUT,
                            IconSettingCell.Colors.RED_TOP, IconSettingCell.Colors.RED_BOTTOM,
                            R.drawable.settings_logout,
                            context?.getString(R.string.profile_logout_action).orEmpty(),
                            context?.getString(R.string.profile_logout_subtitle).orEmpty(),
                        )
                    )
                    items.add(UItem.asShadow(null))
                }
            }
        } else {
            items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
            when (state) {
                is AppSessionState.Anonymous -> {
                    items.add(
                        IconSettingFactory.of(
                            ACTION_LOGIN,
                            IconSettingCell.Colors.BLUE_TOP, IconSettingCell.Colors.BLUE_BOTTOM,
                            R.drawable.settings_login,
                            context?.getString(R.string.profile_login_title).orEmpty(),
                            context?.getString(R.string.profile_login_subtitle).orEmpty(),
                            context?.getString(R.string.profile_login_action).orEmpty(),
                        )
                    )
                }
                is AppSessionState.Expired -> {
                    items.add(
                        IconSettingFactory.of(
                            ACTION_LOGIN,
                            IconSettingCell.Colors.RED_TOP, IconSettingCell.Colors.RED_BOTTOM,
                            R.drawable.settings_login,
                            context?.getString(R.string.profile_session_expired_title).orEmpty(),
                            context?.getString(R.string.profile_session_expired_subtitle).orEmpty(),
                            context?.getString(R.string.profile_login_again_action).orEmpty(),
                        )
                    )
                }
                else -> {
                    items.add(UItem.ofFactory(ProfileLoadingFactory()).apply { id = VIEW_TYPE_PROFILE_LOADING })
                }
            }
            items.add(UItem.asShadow(null))
        }

        // --- App / browser ---
        items.add(UItem.asHeader(context?.getString(R.string.profile_app_section_title).orEmpty()))
        items.add(
            IconSettingFactory.of(
                ACTION_APPEARANCE,
                IconSettingCell.Colors.ORANGE_TOP, IconSettingCell.Colors.ORANGE_BOTTOM,
                R.drawable.settings_refresh,
                context?.getString(R.string.profile_appearance_title).orEmpty(),
                appearanceThemeSummary(),
                if (Theme.isCurrentThemeDark()) {
                    context?.getString(R.string.profile_appearance_value_night).orEmpty()
                } else {
                    context?.getString(R.string.profile_appearance_value_day).orEmpty()
                },
            )
        )
        items.add(
            IconSettingFactory.of(
                ACTION_BROWSER_SETTINGS,
                IconSettingCell.Colors.BLUE_TOP, IconSettingCell.Colors.BLUE_BOTTOM,
                R.drawable.settings_link,
                context?.getString(R.string.profile_browser_settings_title).orEmpty(),
                context?.getString(R.string.profile_browser_settings_subtitle).orEmpty(),
            )
        )
        items.add(UItem.asShadow(null))

        // --- Debug section (last, debug builds only) ---
        // Mirrors SettingsActivity.java:737 — dev tools are gated behind a build flag so
        // anonymous/release users never see them.
        addDevToolsItems(items)

        // The centered footer supplies the settings section shadow itself. Keeping the
        // preceding shadow would make UniversalAdapter resolve drawable resource 0.
        if (profileVersionFooterReplacesTerminalShadow(items.lastOrNull()?.viewType)) {
            items.removeAt(items.lastIndex)
        }
        items.add(UItem.asCenterShadow("NativeDo v${BuildConfig.VERSION_NAME}"))

        // Telegram keeps SharedMediaLayout as the final outer-list row. Profile
        // settings must precede it so the pinned tabs can own the remaining scroll.
        if (state is AppSessionState.Authenticated && profileState.profile != null) {
            items.add(UItem.ofFactory(profileContentFactory).apply {
                id = VIEW_TYPE_PROFILE_CONTENT
            })
        }
    }

    private fun appearanceThemeSummary(): String {
        val dayName = Theme.getCurrentTheme()?.getName().orEmpty()
        val nightName = Theme.getCurrentNightTheme()?.getName().orEmpty()
        return context?.getString(R.string.profile_appearance_subtitle, dayName, nightName).orEmpty()
    }

    private fun addDevToolsItems(items: ArrayList<UItem>) {
        if (!BuildConfig.DEBUG) return
        items.add(UItem.asHeader(context?.getString(R.string.profile_debug_title).orEmpty()))
        items.add(
            IconSettingFactory.of(
                ACTION_DEV_URL,
                IconSettingCell.Colors.BLUE_TOP, IconSettingCell.Colors.BLUE_BOTTOM,
                R.drawable.settings_link,
                context?.getString(R.string.profile_dev_url_title).orEmpty(),
                context?.getString(R.string.profile_dev_tools_subtitle).orEmpty(),
            )
        )
        items.add(
            IconSettingFactory.of(
                ACTION_TOPIC_RENDER_FIXTURES,
                IconSettingCell.Colors.ORANGE_TOP, IconSettingCell.Colors.ORANGE_BOTTOM,
                R.drawable.settings_topic,
                context?.getString(R.string.profile_topic_fixture_tools_title).orEmpty(),
                context?.getString(R.string.profile_topic_fixture_tools_subtitle).orEmpty(),
            )
        )
        items.add(
            IconSettingFactory.of(
                ACTION_TOPIC_URL,
                IconSettingCell.Colors.GREEN_TOP, IconSettingCell.Colors.GREEN_BOTTOM,
                R.drawable.settings_topic,
                context?.getString(R.string.profile_topic_dev_url_title).orEmpty(),
                context?.getString(R.string.profile_topic_dev_tools_subtitle).orEmpty(),
            )
        )
        items.add(UItem.asShadow(null))
    }

    // --- Formatting helpers for Discourse profile fields ---

    private fun trustLevelLabel(level: Int): String? = when (level) {
        0 -> "新用户 (TL0)"
        1 -> "基本用户 (TL1)"
        2 -> "成员 (TL2)"
        3 -> "活跃用户 (TL3)"
        4 -> "领袖 (TL4)"
        else -> null
    }

    private fun formatTimeRead(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun formatRelativeActivity(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val timestamp = java.time.Instant.parse(isoDate).toEpochMilli()
            DateUtils.getRelativeTimeSpanString(
                timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
        } catch (_: Exception) {
            formatDate(isoDate)
        }
    }

    private fun formatDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val instant = java.time.Instant.parse(isoDate)
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant)
        } catch (_: Exception) {
            isoDate.take(10)
        }
    }

    /**
     * Long-press an identity/stats row to copy its value, mirroring upstream
     * ProfileActivity.processOnClickOrPress (copy on press of username/bio/etc).
     * Returning true makes RecyclerListView fire the long-press haptic automatically.
     */
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
            ACTION_LOGOUT -> showLogoutConfirm()
            ACTION_RETRY_PROFILE -> viewModel.retry()
            ACTION_LOGIN -> presentFragment(LoginWebViewFragment())
            ACTION_BROWSER_SETTINGS -> openBrowserSettings()
            ACTION_APPEARANCE -> presentFragment(ThemeSettingsFragment())
            ACTION_DEV_URL -> showUrlInputDialog(
                title = context?.getString(R.string.profile_dev_url_title).orEmpty(),
                message = context?.getString(R.string.profile_dev_url_message).orEmpty(),
                hint = context?.getString(R.string.profile_dev_url_hint).orEmpty(),
                actionText = context?.getString(R.string.profile_dev_url_open).orEmpty(),
                invalidText = context?.getString(R.string.profile_dev_url_invalid).orEmpty(),
                parser = ::normalizeHttpUrl,
                onSubmit = ::openBrowser,
            )
            ACTION_TOPIC_RENDER_FIXTURES -> showTopicRenderFixturesSheet()
            ACTION_TOPIC_URL -> showUrlInputDialog(
                title = context?.getString(R.string.profile_topic_dev_url_title).orEmpty(),
                message = context?.getString(R.string.profile_topic_dev_url_message).orEmpty(),
                hint = context?.getString(R.string.profile_topic_dev_url_hint).orEmpty(),
                initialText = "https://linux.do/t/topic/",
                actionText = context?.getString(R.string.profile_topic_dev_url_open).orEmpty(),
                invalidText = context?.getString(R.string.profile_topic_dev_url_invalid).orEmpty(),
                parser = { parseLinuxDoTopicId(it)?.toString() },
                onSubmit = { topicId -> presentFragment(TopicDetailFragment(topicId.toLong())) },
            )
        }
    }

    private fun showLogoutConfirm() {
        val parentActivity = getParentActivity() ?: return
        val dialog = AlertDialog.Builder(parentActivity, getResourceProvider())
            .setTitle(parentActivity.getString(R.string.profile_logout_confirm_title))
            .setMessage(parentActivity.getString(R.string.profile_logout_confirm_message))
            .setPositiveButton(parentActivity.getString(R.string.profile_logout_action)) { _, _ ->
                fragmentScope.launch {
                    appContainer.sessionCoordinator.onLogout()
                }
            }
            .setNegativeButton(parentActivity.getString(R.string.action_cancel), null)
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .create()
        showDialog(dialog)
    }

    // --- Edge-to-edge support (mirrors ProfileActivity) ---

    override fun isSupportEdgeToEdge(): Boolean = true

    override fun drawEdgeNavigationBar(): Boolean = false

    override fun isLightStatusBar(): Boolean {
        val color = when {
            isPulledDown -> return false
            actionBar != null && actionBar.isActionModeShowed ->
                Theme.getColor(Theme.key_actionBarActionModeDefault)
            mediaHeaderVisible -> Theme.getColor(Theme.key_windowBackgroundWhite)
            else -> Theme.getColor(Theme.key_actionBarDefault)
        }
        return ColorUtils.calculateLuminance(color) > 0.7
    }

    // --- ContainerView (mirrors ProfileActivity.NestedFrameLayout) ---

    private inner class ContainerView(context: Context) : SizeNotifierFrameLayout(context), NestedScrollingParent3 {

        private val nestedScrollingParentHelper = NestedScrollingParentHelper(this)
        private var lastProfileLayoutWidth = 0

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            checkListViewScroll()
            val layoutWidth = right - left
            if (layoutWidth > 0 && layoutWidth != lastProfileLayoutWidth) {
                lastProfileLayoutWidth = layoutWidth
                needLayout()
            }
        }

        private fun isCurrentInnerList(target: View): Boolean =
            profileContentLayout?.currentListView() === target

        private fun consumeOuterScroll(dy: Int): Int {
            if (dy == 0) return 0
            val outer = listView ?: return 0
            val content = profileContentLayout ?: return 0
            if (!content.isAttachedToWindow) return 0

            val before = content.top
            val requested = if (dy > 0) {
                val remainingToPin = before - getHeaderTopOffset()
                if (remainingToPin <= 0) return 0
                dy.coerceAtMost(remainingToPin)
            } else {
                dy
            }
            outer.scrollBy(0, requested)
            return before - content.top
        }

        override fun onNestedScroll(
            target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int, consumed: IntArray,
        ) {
            if (!isCurrentInnerList(target) || dyUnconsumed == 0) return
            consumed[1] += consumeOuterScroll(dyUnconsumed)
        }

        override fun onNestedScroll(
            target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int,
        ) {
            if (isCurrentInnerList(target) && dyUnconsumed != 0) {
                consumeOuterScroll(dyUnconsumed)
            }
        }

        override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean =
            super.onNestedPreFling(target, velocityX, velocityY)

        override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
            if (!isCurrentInnerList(target) || dy == 0) return
            val contentTop = profileContentLayout?.top ?: return
            val request = profileOuterScrollRequest(
                dy = dy,
                contentTop = contentTop,
                pinTop = getHeaderTopOffset(),
                innerCanScrollUp = target.canScrollVertically(-1),
            )
            if (request != 0) {
                consumed[1] += consumeOuterScroll(request)
            }
        }

        override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean =
            profileContentLayout != null &&
                isCurrentInnerList(target) &&
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
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundGray))
    }

    // --- Telegram profile cover layer (no peer-color branch) ---

    private inner class TopView(context: Context) : View(context), Theme.Colorable {
        private var currentColor: Int = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setWillNotDraw(false)
        }

        override fun setBackgroundColor(color: Int) {
            if (color != currentColor) {
                currentColor = color
                paint.color = color
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val viewHeight = height
            if (width <= 0 || viewHeight <= 0) return
            val mediaTop = profileMediaHeaderTopBoundary(viewHeight, mediaHeaderProgress)
            val expandBlend = Math.max(expandProgress, currentExpandAnimatorValue)
            if (mediaTop > 0) {
                paint.color = ColorUtils.blendARGB(currentColor, Color.BLACK, expandBlend)
                canvas.drawRect(0f, 0f, width.toFloat(), mediaTop.toFloat(), paint)
            }
            if (mediaTop < viewHeight) {
                paint.color = Theme.getColor(Theme.key_windowBackgroundWhite)
                canvas.drawRect(0f, mediaTop.toFloat(), width.toFloat(), viewHeight.toFloat(), paint)
            }
        }

        override fun updateColors() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        }
    }

    // --- AvatarImageView (mirrors ProfileActivity.AvatarImageView) ---
    // Uses BackupImageView + ImageReceiver pattern from upstream.

    class AvatarImageView(context: Context) : org.telegram.ui.Components.BackupImageView(context) {
        val localAvatarDrawable = AvatarDrawable()
        private val bounce = ButtonBounce(this, 1f, 3f)

        init {
            imageReceiver.setAllowDecodeSingleFrame(true)
            setRoundRadius(AndroidUtilities.dp(50f))
        }

        /**
         * Mirrors upstream AvatarImageView.setRoundRadiusForExpand().
         * When expanded (pulled down), the round radius lerps from dp(50)=circle to 0=rectangle.
         */
        fun setRoundRadiusForExpand(value: Int) {
            setRoundRadius(value)
        }

        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            bounce.setPressed(pressed)
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

        override fun onDraw(canvas: Canvas) {
            val scale = bounce.getScale(0.04f)
            if (scale != 1f) {
                canvas.save()
                canvas.scale(scale, scale, width / 2f, height / 2f)
            }
            super.onDraw(canvas)
            if (scale != 1f) {
                canvas.restore()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            imageReceiver.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            imageReceiver.onDetachedFromWindow()
        }
    }

    private inner class ProfileLoadingFactory : UItem.ViewFactory {
        override fun getViewType(): Int = VIEW_TYPE_PROFILE_LOADING
        override fun createView(context: Context): View = LoadingCell(context)
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
        ).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }.also { profileContentLayout = it }

        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter) {
            val content = view as ProfileContentLayout
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
            val parsed = Html.fromHtml(normalizeInlineHtml(bio), Html.FROM_HTML_MODE_COMPACT)
            (view as AboutLinkCell).apply {
                setTextAndValue(
                    parsed,
                    context.getString(R.string.profile_about_title),
                    false,
                )
                updateColors()
            }
        }
        override fun isEnabled(item: UItem): Boolean = true
    }

    // --- Helpers ---

    private fun getProfileViewportWidth(): Int =
        profileViewportWidth(
            fragmentWidth = fragmentView?.width ?: 0,
            listWidth = listView?.measuredWidth ?: 0,
            displayWidth = AndroidUtilities.displaySize.x,
        )

    // --- Theme delegate ---

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

    /**
     * Settings-shaped ThemeDescription list: UniversalRecyclerView listClasses for
     * IconSettingCell / AboutLinkCell, chrome delegate for header TopView + name/subtitle.
     * Theme keys used by the header and settings cells are explicit animation tracks.
     */
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
                        IconSettingCell::class.java,
                        AboutLinkCell::class.java,
                        ProfileContentLayout::class.java,
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
        (fragmentView as? View)?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        topView?.updateColors()
        nameTextView[0]?.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle))
        // Expanded title is blended in scroll updates; refresh base expanded color.
        nameTextView[1]?.setTextColor(Theme.getColor(Theme.key_profile_title))
        subtitleView[0]?.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        subtitleView[1]?.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        applyExpandColorBlends(expandProgress)
        profileActionsView?.invalidate()
        val list = listView
        if (list != null) {
            for (index in 0 until list.childCount) {
                (list.getChildAt(index) as? ProfileContentLayout)?.updateColors()
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
        avatarProgressView?.animate()?.setListener(null)?.withEndAction(null)?.cancel()
        avatarImageView?.imageReceiver?.setDelegate(null)
        topicFixtureSheet?.dismiss()
        topicFixtureSheet = null
        fragmentScope.cancel()
        super.onFragmentDestroy()
    }

    // --- URL input dialog ---

    private fun showTopicRenderFixturesSheet() {
        val context = getContext() ?: return
        topicFixtureSheet?.dismiss()
        topicFixtureSheet = DebugTopicFixtureSheet(
            context = context,
            fragment = this,
            onDismissed = { topicFixtureSheet = null },
        ).also { it.show() }
    }

    private fun showUrlInputDialog(
        title: String,
        message: String,
        hint: String,
        initialText: String = "",
        actionText: String,
        invalidText: String,
        parser: (String) -> String?,
        onSubmit: (String) -> Unit,
    ) {
        val context = getContext() ?: return
        val input = EditTextBoldCursor(context).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSingleLine(true)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()))
            setHintTextColor(
                ColorUtils.setAlphaComponent(
                    Theme.getColor(Theme.key_dialogIcon, getResourceProvider()),
                    180,
                ),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            background = inputBackground()
            setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)
            if (initialText.isNotEmpty()) {
                setText(initialText)
                setSelection(initialText.length)
            }
        }
        val inputContainer = FrameLayout(context).apply {
            setPadding(AndroidUtilities.dp(24f), 0, AndroidUtilities.dp(24f), AndroidUtilities.dp(4f))
            addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.TOP))
        }
        fun submit(currentDialog: android.content.DialogInterface) {
            val parsed = parser(input.text?.toString().orEmpty())
            if (parsed == null) {
                input.error = invalidText
            } else {
                currentDialog.dismiss()
                onSubmit(parsed)
            }
        }
        val dialog = AlertDialog.Builder(context, getResourceProvider())
            .setTitle(title)
            .setMessage(message)
            .setView(inputContainer)
            .setNegativeButton(context.getString(android.R.string.cancel)) { currentDialog, _ ->
                currentDialog.dismiss()
            }
            .setPositiveButton(actionText) { currentDialog, _ ->
                submit(currentDialog)
            }
            .create()
        dialog.setDismissDialogByButtons(false)
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit(dialog)
                true
            } else {
                false
            }
        }
        showDialog(dialog)
        input.post {
            input.requestFocus()
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun normalizeHttpUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val uri = Uri.parse(trimmed)
        val scheme = uri.scheme?.lowercase()
        if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
            return uri.toString()
        }
        return null
    }

    private fun parseLinuxDoTopicId(value: String): Long? {
        val url = normalizeHttpUrl(value) ?: return null
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host != "linux.do") return null
        val segments = uri.pathSegments
        val topicMarkerIndex = segments.indexOf("t")
        if (topicMarkerIndex < 0) return null
        return segments.drop(topicMarkerIndex + 1).firstNotNullOfOrNull { segment -> segment.toLongOrNull() }
    }

    
    private fun openBrowserSettings() {
        val params = BaseFragment.BottomSheetParams().apply { transitionFromLeft = true }
        showAsSheet(WebBrowserSettings { entry ->
            Browser.openUrl(parentActivity ?: return@WebBrowserSettings, entry.url)
        }, params)
    }

    private fun openBrowser(url: String) {
        val context = getContext() ?: return
        Browser.openUrl(context, url)
    }

    private fun inputBackground(): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(Theme.getColor(Theme.key_dialogBackgroundGray, getResourceProvider()))
        d.cornerRadius = AndroidUtilities.dpf2(10f)
        val strokeWidth = AndroidUtilities.dp(1f)
        val strokeColor = ColorUtils.setAlphaComponent(
            Theme.getColor(Theme.key_dialogIcon, getResourceProvider()),
            96,
        )
        d.setStroke(strokeWidth, strokeColor)
        return d
    }

}

internal enum class ProfileHeaderReleaseTarget {
    NONE,
    COLLAPSED,
    ACTIONS,
    HEADER,
}

internal fun profileHeaderExtraDiff(
    extraHeight: Float,
    actionsExtraHeight: Int,
    headerOnlyExtraHeight: Int,
): Float {
    if (headerOnlyExtraHeight <= 0) return 0f
    return Utilities.clamp01((extraHeight - actionsExtraHeight) / headerOnlyExtraHeight)
}

internal fun profileHeaderExpandedTargetTop(
    listWidth: Int,
    actionsExtraHeight: Int,
    actionBarHeight: Int,
): Int = listWidth + actionsExtraHeight - actionBarHeight

internal fun profileAvatarContainerVisibility(
    expandValue: Float,
    hasExpandedMediaSuccessor: Boolean,
): Int = if (hasExpandedMediaSuccessor && expandValue >= 1f) View.GONE else View.VISIBLE

internal fun profileHeaderSettleDy(
    viewTop: Int,
    headerTopOffset: Int,
    headerExtraHeight: Int,
): Int = viewTop - headerTopOffset - headerExtraHeight

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

internal fun profileHeaderSubtitleLayerAlpha(layer: Int, mediaHeaderProgress: Float): Float =
    if (layer == 0) 0f else 1f - Utilities.clamp01(mediaHeaderProgress)

internal fun profileOuterScrollRequest(
    dy: Int,
    contentTop: Int,
    pinTop: Int,
    innerCanScrollUp: Boolean,
): Int = when {
    dy > 0 && contentTop > pinTop -> dy.coerceAtMost(contentTop - pinTop)
    dy < 0 && !innerCanScrollUp -> dy
    else -> 0
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

private class ProfileViewModelFactory(
    private val repository: ProfileRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(repository = repository) as T
    }
}

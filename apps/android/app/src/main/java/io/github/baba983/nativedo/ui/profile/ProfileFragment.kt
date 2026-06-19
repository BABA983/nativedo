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

import android.app.Dialog
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.text.Html
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.normalizeInlineHtml
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import io.github.baba983.nativedo.feature.profile.ui.ProfileUiState
import io.github.baba983.nativedo.feature.profile.ui.ProfileViewModel
import io.github.baba983.nativedo.ui.auth.LoginWebViewFragment
import io.github.baba983.nativedo.ui.main.MainTabsActivity
import io.github.baba983.nativedo.ui.profile.IconSettingCell.Factory as IconSettingFactory
import io.github.baba983.nativedo.ui.topic.TopicDetailFragment
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
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ButtonBounce
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LinkSpanDrawable
import org.telegram.ui.Components.ProfileGooeyView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.ui.Cells.TextDetailCell
import org.telegram.ui.PhotoViewer

private const val VIEW_TYPE_BIO = 10_004

private const val ID_USERNAME = 10
private const val ID_TRUST_LEVEL = 11
private const val ID_TITLE = 12
private const val ID_TIME_READ = 13
private const val ID_JOINED = 14
private const val ID_LAST_SEEN = 15
private const val ID_PROFILE_VIEWS = 16
private const val ID_BADGE_COUNT = 17
private const val ACTION_DEV_URL = 1
private const val ACTION_TOPIC_URL = 2
private const val ACTION_RETRY_PROFILE = 3
private const val ACTION_LOGOUT = 4
private const val ACTION_LOGIN = 5
private const val MENU_OPEN_PROFILE = 101
private const val MENU_REFRESH_PROFILE = 102
private const val MENU_LOGIN = 103
private const val MENU_LOGOUT = 104

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
class ProfileFragment : BaseFragment(), Theme.ThemeDelegate {
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
    private var avatarContainer2: FrameLayout? = null
    private var openProfileMenuItem: View? = null
    private var refreshProfileMenuItem: View? = null
    private var loginMenuItem: View? = null
    private var logoutMenuItem: View? = null
    private var nameTextView = arrayOfNulls<SimpleTextView>(2) // [0]=dark(collapsed), [1]=white(expanded)
    private var subtitleView = arrayOfNulls<SimpleTextView>(2)
    private var avatarViewerUrl: String = ""
    private var avatarViewerTitle: String = ""
    private var avatarViewerSubtitle: String = ""
    private var profileWebUrl: String = ""

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
    private var headerAnimator: ValueAnimator? = null

    // Expand animator (mirrors ProfileActivity.expandAnimator)
    // Dedicated ValueAnimator that drives setAvatarExpandProgress()
    // when isPulledDown is triggered — independent of scroll position.
    private var expandAnimator: ValueAnimator? = null
    private var currentExpandAnimatorValue: Float = 0f
    private var currentExpandAnimatorFraction: Float = 0f
    private var expandAnimatorValues: FloatArray = floatArrayOf(0f, 1f)

    // --- Header height constants (mirrors ProfileActivity) ---

    private fun getHeaderOnlyExtraHeight(): Int {
        return AndroidUtilities.dp(152f)
    }

    private fun getHeaderExtraHeight(): Int = getHeaderOnlyExtraHeight()

    private fun calculateHeaderExtraDiff(): Float {
        return Utilities.clamp01(extraHeight / getHeaderOnlyExtraHeight())
    }

    override fun createView(context: Context): View {
        Theme.addThemeDelegate(this)
        actionBar.setCastShadows(false)
        actionBar.setAddToContainer(false)
        actionBar.setClipContent(true)
        actionBar.setBackgroundColor(Color.TRANSPARENT)
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_avatar_actionBarSelectorBlue), false)
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false)
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), true)
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet())
        actionBar.setBackButtonDrawable(BackDrawable(false))

        appContainer = (context.applicationContext as NativeDoApp).appContainer
        viewModel = ViewModelProvider(
            this,
            ProfileViewModelFactory(repository = ProfileRepository(appContainer.discourseApiClient)),
        )[ProfileViewModel::class.java]
        setupProfileActionBarMenu(context)

        extraHeight = getHeaderExtraHeight().toFloat()

        // --- Container (mirrors ProfileActivity.NestedFrameLayout) ---
        val container = ContainerView(context)
        container.setWillNotDraw(false)
        container.setBackgroundColor(0)

        // --- RecyclerView (mirrors ProfileActivity.listView) ---
        val recycler = object : UniversalRecyclerView(
            this,
            { items, _ -> fillItems(items) },
            { item, _, _, _, _ -> handleItemClick(item) },
            null,
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
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    settleHeaderAfterTouch()
                }
                return result
            }
        }
        recycler.adapter.setApplyBackground(false)
        recycler.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        recycler.clipToPadding = false
        recycler.setSections()
        recycler.setPadding(0, getListTopForExtraHeight(getHeaderExtraHeight()), 0,
            AndroidUtilities.dp((MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 12).toFloat()))

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
                        if (avatarImageView?.imageReceiver?.hasNotThumb() == true) {
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
        gooey.addView(ac, LayoutHelper.createFrame(100f, 100f, Gravity.TOP or Gravity.LEFT, 64f, 0f, 0f, 0f))
        ac2.addView(gooey, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        avatarGooey = gooey

        // --- AvatarImageView (mirrors ProfileActivity.AvatarImageView — BackupImageView subclass) ---
        val aiv = AvatarImageView(context)
        aiv.setOnClickListener { openAvatarViewer() }
        ac.addView(aiv, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        avatarImageView = aiv

        // --- nameTextView[0] (collapsed: dark, sits in actionBar area) ---
        // Mirrors upstream nameTextView[0] with key_actionBarDefaultTitle color.
        val name0 = SimpleTextView(context)
        name0.setTextSize(18)
        name0.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle))
        name0.setGravity(Gravity.LEFT)
        name0.setTypeface(AndroidUtilities.bold())
        name0.setPivotX(0f)
        name0.setPivotY(0f)
        name0.setEllipsizeByGradient(true)
        name0.setScrollNonFitText(true)
        name0.setAlpha(0f) // hidden by default (shown when collapsed)
        ac2.addView(name0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, -6f, 40f, 0f))
        nameTextView[0] = name0

        // --- nameTextView[1] (expanded: white, sits below avatar) ---
        // Mirrors upstream nameTextView[1] with key_profile_title → WHITE blend.
        val name1 = SimpleTextView(context)
        name1.setTextSize(18)
        name1.setTextColor(Theme.getColor(Theme.key_profile_title))
        name1.setGravity(Gravity.LEFT)
        name1.setTypeface(AndroidUtilities.bold())
        name1.setPivotX(0f)
        name1.setPivotY(0f)
        name1.setEllipsizeByGradient(true)
        name1.setScrollNonFitText(true)
        name1.setAlpha(1f) // shown by default
        ac2.addView(name1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, -6f, 0f, 0f))
        nameTextView[1] = name1

        // --- subtitleView[0] (collapsed: dark) ---
        val sub0 = SimpleTextView(context)
        sub0.setTextSize(14)
        sub0.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        sub0.setGravity(Gravity.LEFT)
        sub0.setPivotX(AndroidUtilities.dp(8f).toFloat())
        sub0.setPivotY(AndroidUtilities.dp(8f).toFloat())
        sub0.setEllipsizeByGradient(true)
        sub0.setScrollNonFitText(true)
        sub0.setAlpha(0f)
        ac2.addView(sub0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, 0f, 40f, 0f))
        subtitleView[0] = sub0

        // --- subtitleView[1] (expanded: white) ---
        val sub1 = SimpleTextView(context)
        sub1.setTextSize(14)
        sub1.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle))
        sub1.setGravity(Gravity.LEFT)
        sub1.setPivotX(AndroidUtilities.dp(8f).toFloat())
        sub1.setPivotY(AndroidUtilities.dp(8f).toFloat())
        sub1.setEllipsizeByGradient(true)
        sub1.setScrollNonFitText(true)
        sub1.setAlpha(1f)
        ac2.addView(sub1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 114f, -2f, 4f, 0f))
        subtitleView[1] = sub1

        // ActionBar on top of everything
        container.addView(actionBar)

        listView = recycler
        universalAdapter = recycler.adapter
        fragmentView = container

        updateLayout()
        updateInfo()

        // --- Expand animator (mirrors ProfileActivity.expandAnimator) ---
        // Dedicated animator that drives setAvatarExpandProgress() when
        // isPulledDown triggers. Independent of scroll position for smooth animation.
        expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { anim ->
                setAvatarExpandProgress(anim.animatedFraction)
            }
        }

        // Observe session state
        fragmentScope.launch {
            appContainer.sessionCoordinator.sessionState.collectLatest { state ->
                sessionState = state
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

    // --- Layout (mirrors ProfileActivity.updateLayout / needLayout) ---

    private fun getStatusBarHeight(): Int = AndroidUtilities.getStatusBarHeight(context)

    private fun getActionBarHeight(): Int = ActionBar.getCurrentActionBarHeight()

    private fun getHeaderTopOffset(): Int = getStatusBarHeight() + getActionBarHeight()

    private fun getListTopForExtraHeight(extra: Int): Int = getHeaderTopOffset() + extra

    private fun updateLayout() {
        (listView?.layoutParams as? FrameLayout.LayoutParams)?.topMargin = 0
        listView?.setPadding(
            0,
            getListTopForExtraHeight(getHeaderExtraHeight()),
            0,
            AndroidUtilities.dp((MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 12).toFloat()),
        )

        needLayout()
    }

    /**
     * Mirrors ProfileActivity.needLayout() — 1:1 structure alignment.
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
        val width = fragmentView?.width ?: AndroidUtilities.displaySize.x

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
                isPulledDown = true

                // Start expandAnimator from current value → 1 (mirrors upstream 8454-8499)
                expandAnimator?.cancel()
                val value = AndroidUtilities.lerp(expandAnimatorValues[0], expandAnimatorValues[1], currentExpandAnimatorFraction)
                expandAnimatorValues[0] = value
                expandAnimatorValues[1] = 1f

                val durationFactor = Math.min(AndroidUtilities.dpf2(2000f),
                    Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY))) /
                    AndroidUtilities.dpf2(1100f)

                expandAnimator?.apply {
                    interpolator = CubicBezierInterpolator.EASE_BOTH
                    duration = ((1f - value) * 250f / durationFactor).toLong()
                    // Scroll the list to fully expanded position in sync (mirrors upstream 8490-8496)
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            expandAnimator?.removeListener(this)
                            // Mirrors upstream 8478-8481: fully expanded state
                            // We don't have avatarsViewPager, so we just keep avatarContainer
                            // filling the screen (setAvatarExpandProgress has already done this)
                        }
                    })
                    start()
                }

                // Scroll the list to fully expanded position (mirrors upstream 8490-8496)
                // Upstream uses listView.smoothScrollBy with duration+interpolator, which we
                // don't have. Use ValueAnimator to drive scrollToPositionWithOffset instead.
                val rv = listView ?: return
                val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
                val view0 = layoutManager.findViewByPosition(0)
                if (view0 != null) {
                    val targetTopExpand = rv.measuredWidth + newTop
                    val dy = view0.top - targetTopExpand
                    rv.smoothScrollBy(0, dy, CubicBezierInterpolator.EASE_OUT_QUINT)
                }

                // Haptic (mirrors upstream 8503-8506)
                try {
                    avatarContainer?.performHapticFeedback(
                        android.view.HapticFeedbackConstants.LONG_PRESS,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                    )
                } catch (_: Exception) {}

                return  // expandAnimator is now running — setAvatarExpandProgress will drive visuals
            }

            // --- Not isPulledDown (still dragging in expand zone) ---
            // Drive avatar directly from expandProgress (mirrors upstream 8515-8527)
            if (expandAnimator?.isRunning != true) {
                // Avatar Y moves toward 0 (top) as expand increases
                avatarY = AndroidUtilities.lerp(avatarY, 0f, expandProgress)

                avatarContainer?.let { ac ->
                    val params = ac.layoutParams as FrameLayout.LayoutParams
                    // Avatar size grows with expandProgress (mirrors setAvatarExpandProgress formula)
                    params.width = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), width.toFloat() / avatarScale, expandProgress).toInt()
                    params.height = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), (h + newTop) / avatarScale, expandProgress).toInt()
                    params.leftMargin = AndroidUtilities.lerp(AndroidUtilities.dpf2(64f), 0f, expandProgress).toInt()
                    ac.layoutParams = params

                    ac.scaleX = avatarScale
                    ac.scaleY = avatarScale

                    // Center horizontally (mirrors upstream fixAvatarImageInCenter)
                    val avatarX = width / 2f - (params.leftMargin + params.width * avatarScale * 0.5f)
                    ac.translationX = avatarX
                    ac.translationY = Math.ceil(avatarY.toDouble()).toFloat()

                    avatarImageView?.setRoundRadiusForExpand(
                        AndroidUtilities.lerp(AndroidUtilities.dp(50f).toFloat(), 0f, expandProgress).toInt()
                    )
                }

                // Name/online position when in expand zone but not fully pulled down
                // (mirrors upstream lines 8520-8523)
                nameTextView[1]?.let { nt ->
                    nt.translationX = AndroidUtilities.dpf2(18f) - nt.left
                    nt.translationY = newTop + h - AndroidUtilities.dpf2(30f) - nt.bottom
                }
                subtitleView[1]?.let { sv ->
                    sv.translationX = AndroidUtilities.dpf2(16f) - sv.left
                    sv.translationY = newTop + h - AndroidUtilities.dpf2(10f) - sv.bottom
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

                expandAnimator?.cancel()
                val value = AndroidUtilities.lerp(expandAnimatorValues[0], expandAnimatorValues[1], currentExpandAnimatorFraction)
                expandAnimatorValues[0] = value
                expandAnimatorValues[1] = 0f

                val durationFactor = Math.min(AndroidUtilities.dpf2(2000f),
                    Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY))) /
                    AndroidUtilities.dpf2(1100f)

                expandAnimator?.apply {
                    interpolator = CubicBezierInterpolator.EASE_BOTH
                    duration = (value * 250f / durationFactor).toLong()
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            expandAnimator?.removeListener(this)
                            // Mirrors upstream: after collapse, avatarContainer returns to 100×100
                            currentExpandAnimatorValue = 0f
                            currentExpandAnimatorFraction = 0f
                            expandAnimatorValues[0] = 0f
                            expandAnimatorValues[1] = 1f
                        }
                    })
                    start()
                }

                // Haptic on collapse
                try {
                    avatarContainer?.performHapticFeedback(
                        android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
                    )
                } catch (_: Exception) {}

                return  // expandAnimator is running — setAvatarExpandProgress will drive collapse
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
                params.leftMargin = AndroidUtilities.dpf2(64f).toInt()
                ac.layoutParams = params

                ac.scaleX = avatarScale
                ac.scaleY = avatarScale

                val avatarX = width / 2f - (params.leftMargin + params.width * avatarScale * 0.5f)
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
    }

    private fun settleHeaderAfterTouch() {
        val rv = listView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val view = layoutManager.findViewByPosition(0) ?: return
        val collapsedTop = getListTopForExtraHeight(getHeaderExtraHeight())

        if (allowPullingDown || isPulledDown) {
            // Mirrors upstream: decide whether to fully expand or collapse back
            if (expandProgress >= 0.33f && allowPullingDown) {
                // Past threshold: trigger expand — needLayout will start expandAnimator
                isPulledDown = true
                val expandedTop = rv.measuredWidth.coerceAtLeast(collapsedTop)
                animateHeaderTo(expandedTop, view.top)
            } else {
                // Below threshold or already pulled down: collapse back
                allowPullingDown = false
                isPulledDown = false
                if (currentExpandAnimatorValue > 0f) {
                    // Start collapse animator inline (mirrors upstream needLayout collapse trigger)
                    expandAnimator?.cancel()
                    expandAnimatorValues[0] = currentExpandAnimatorValue
                    expandAnimatorValues[1] = 0f
                    val df = Math.min(AndroidUtilities.dpf2(2000f),
                        Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY))) /
                        AndroidUtilities.dpf2(1100f)
                    expandAnimator?.apply {
                        interpolator = CubicBezierInterpolator.EASE_BOTH
                        duration = (currentExpandAnimatorValue * 250f / df).toLong()
                        start()
                    }
                }
                smoothHeaderBy(view.top - collapsedTop)
            }
        } else if (extraHeight > 0f && listViewVelocityY < -1000f) {
            smoothHeaderBy(extraHeight.toInt())
        } else if (extraHeight > 0f) {
            smoothHeaderBy(view.top - collapsedTop)
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
        val expandedTop = rv.measuredWidth.coerceAtLeast(getListTopForExtraHeight(getHeaderExtraHeight()))
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
            // Expand: animate header scroll position, needLayout will detect
            // expandProgress >= 0.33 and start the expandAnimator
            animateHeaderTo(targetTop, view.top)
        } else {
            // Collapse: start the collapse expandAnimator AND scroll back
            isPulledDown = false
            allowPullingDown = false
            // Start collapse animator inline (mirrors upstream needLayout collapse trigger)
            expandAnimator?.cancel()
            expandAnimatorValues[0] = currentExpandAnimatorValue
            expandAnimatorValues[1] = 0f
            val df = Math.min(AndroidUtilities.dpf2(2000f),
                Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY))) /
                AndroidUtilities.dpf2(1100f)
            expandAnimator?.apply {
                interpolator = CubicBezierInterpolator.EASE_BOTH
                duration = (currentExpandAnimatorValue * 250f / df).toLong()
                start()
            }
            animateHeaderTo(targetTop, view.top)
        }
    }

    private fun animateHeaderTo(targetTop: Int, currentTop: Int) {
        val rv = listView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val collapsedTop = getListTopForExtraHeight(getHeaderExtraHeight())
        val isExpanding = targetTop > collapsedTop

        // Mirrors upstream: velocity-dependent duration
        // durationFactor = min(dpf2(2000), max(dpf2(1100), abs(velocity))) / dpf2(1100)
        val durationFactor = Math.min(AndroidUtilities.dpf2(2000f),
            Math.max(AndroidUtilities.dpf2(1100f), Math.abs(listViewVelocityY))) /
            AndroidUtilities.dpf2(1100f)

        headerAnimator?.cancel()
        headerAnimator = ValueAnimator.ofInt(currentTop, targetTop).apply {
            // Mirrors upstream: EASE_BOTH for expand/collapse, duration = 250/durationFactor
            duration = if (isExpanding) {
                (250f / durationFactor).toLong()
            } else {
                (250f / durationFactor).toLong()
            }
            interpolator = CubicBezierInterpolator.EASE_BOTH
            addUpdateListener { animator ->
                layoutManager.scrollToPositionWithOffset(0, animator.animatedValue as Int)
                checkListViewScroll()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (headerAnimator == animation) {
                        headerAnimator = null
                        allowPullingDown = targetTop > collapsedTop
                        isPulledDown = targetTop > collapsedTop
                        layoutManager.scrollToPositionWithOffset(0, targetTop)
                        checkListViewScroll()
                    }
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (headerAnimator == animation) {
                        headerAnimator = null
                    }
                }
            })
            start()
        }
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
        gooey.setBlurIntensity(Math.min((clamped - 0.2f) / 0.5f * 0.75f, 0.75f))
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
     * Mirrors ProfileActivity.setAvatarExpandProgress() — 1:1 structure alignment.
     * Called by expandAnimator on each frame. This is the ONLY place that modifies
     * avatarContainer width/height/margin during the expand/collapse animation.
     */
    private fun setAvatarExpandProgress(animatedFraction: Float) {
        val value = AndroidUtilities.lerp(expandAnimatorValues[0], expandAnimatorValues[1], animatedFraction)
        currentExpandAnimatorValue = value
        currentExpandAnimatorFraction = animatedFraction

        val newTop = getStatusBarHeight() + getActionBarHeight()
        val width = fragmentView?.width ?: AndroidUtilities.displaySize.x

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
        // Mirrors upstream lines 6385-6390 exactly.
        avatarContainer?.let { ac ->
            val params = ac.layoutParams as FrameLayout.LayoutParams
            params.width = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), width.toFloat() / avatarScale, value).toInt()
            params.height = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), (extraHeight + newTop) / avatarScale, value).toInt()
            params.leftMargin = AndroidUtilities.lerp(AndroidUtilities.dpf2(64f), 0f, value).toInt()
            ac.layoutParams = params

            // Center horizontally (mirrors upstream fixAvatarImageInCenter)
            ac.translationX = width / 2f - (params.leftMargin + params.width * avatarScale * 0.5f)
        }

        // --- Name/online position (mirrors upstream lines 6286-6351) ---
        val diff = calculateHeaderExtraDiff()
        if (!isPulledDown) {
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

        topView?.invalidate()
    }

    /**
     * Mirrors upstream refreshNameAndOnlineYBasedOnExpand (lines 8802-8960).
     * Positions name/online text during expandAnimator using quadratic Bezier curves.
     */
    private fun refreshNameAndOnlineYBasedOnExpand(diff: Float, newTop: Int, value: Float) {
        val width = fragmentView?.width ?: AndroidUtilities.displaySize.x
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

            nt.alpha = if (a == 0) (1f - diff) * (1f - value) else diff
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

            sv.alpha = if (a == 0) (1f - diff) * (1f - value) else diff
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
            val nameYEnd = newTop + extraHeight - AndroidUtilities.dpf2(30f) -
                (nameTextView[1]?.bottom ?: 0)

            val nameCy = ky + nameYStart + (nameYEnd - nameYStart) / 2f
            val nameY = (1f - value) * (1f - value) * nameYStart +
                2f * (1f - value) * value * nameCy +
                value * value * nameYEnd
            val clampedNameY = Math.max(minNameY, nameY)

            // Online Y start and end
            val onlineYStart = avatarBottom + AndroidUtilities.dp(24f) + 11f * AndroidUtilities.density * diff
            val onlineYEnd = newTop + extraHeight - AndroidUtilities.dpf2(10f) -
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

            nt.alpha = if (a == 0) 1f - diff else diff

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
            sv.alpha = if (a == 0) 1f - diff else diff
        }

        avatarContainer?.let { ac ->
            if (extraHeight > getHeaderExtraHeight() || isPulledDown) {
                val nameY = newTop + extraHeight - AndroidUtilities.dpf2(30f) - (nameTextView[1]?.bottom ?: 0)
                val onlineY = newTop + extraHeight - AndroidUtilities.dpf2(10f) - (subtitleView[1]?.bottom ?: 0)
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

        nameTextView[1]?.setTextColor(ColorUtils.blendARGB(
            Theme.getColor(Theme.key_profile_title),
            Color.WHITE,
            progress,
        ))

        subtitleView[1]?.setTextColor(ColorUtils.blendARGB(
            Theme.getColor(Theme.key_actionBarDefaultSubtitle),
            0xB3FFFFFF.toInt(),
            progress,
        ))

        actionBar.setItemsColor(
            ColorUtils.blendARGB(
                Theme.getColor(Theme.key_actionBarDefaultIcon),
                Color.WHITE,
                progress,
            ), false,
        )
        actionBar.setItemsBackgroundColor(
            ColorUtils.blendARGB(
                Theme.getColor(Theme.key_avatar_actionBarSelectorBlue),
                Theme.ACTION_BAR_WHITE_SELECTOR_COLOR,
                progress,
            ), false,
        )

        actionBar.setBackgroundColor(
            ColorUtils.setAlphaComponent(
                Theme.getColor(Theme.key_windowBackgroundGray),
                AndroidUtilities.lerp(68f, 16f, progress).toInt().coerceIn(0, 255),
            ),
        )
    }

    private fun updateHeaderTextWidths(progress: Float) {
        val width = fragmentView?.width ?: AndroidUtilities.displaySize.x
        val diff = calculateHeaderExtraDiff()
        val nameScale = AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, progress)
        val nameWidth = ((width - AndroidUtilities.dp(32f)) / nameScale).toInt()
            .coerceAtLeast(AndroidUtilities.dp(120f))
        val subtitleWidth = (width - AndroidUtilities.dp(32f))
            .coerceAtLeast(AndroidUtilities.dp(120f))

        for (view in arrayOf(nameTextView[0], nameTextView[1])) {
            val lp = view?.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (lp.width != nameWidth) {
                lp.width = nameWidth
                view.layoutParams = lp
            }
        }
        for (view in arrayOf(subtitleView[0], subtitleView[1])) {
            val lp = view?.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (lp.width != subtitleWidth) {
                lp.width = subtitleWidth
                view.layoutParams = lp
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
            if (showAvatar && (avatarTemplate.isNotEmpty() || animatedAvatarUrl.isNotEmpty())) {
                val avatarUrl = resolveAvatarUrl(appContainer.baseUrl, avatarTemplate, animatedAvatarUrl, 360)
                aiv.imageReceiver.setAllowDecodeSingleFrame(animatedAvatarUrl.isBlank())
                aiv.imageReceiver.setImage(avatarUrl, avatarImageFilter(animatedAvatarUrl, 360), aiv.localAvatarDrawable, null, 0)
            } else {
                aiv.imageReceiver.setAllowDecodeSingleFrame(true)
                aiv.imageReceiver.setImageBitmap(null as android.graphics.Bitmap?)
            }
        }

        updateProfileActionBarMenu()

        needLayout()
    }

    private fun setupProfileActionBarMenu(context: Context) {
        val menu = actionBar.createMenu()
        openProfileMenuItem = menu.addItem(MENU_OPEN_PROFILE, R.drawable.settings_link).apply {
            contentDescription = context.getString(R.string.post_image_viewer_action_open_in_browser)
        }
        refreshProfileMenuItem = menu.addItem(MENU_REFRESH_PROFILE, R.drawable.settings_refresh).apply {
            contentDescription = context.getString(R.string.action_retry)
        }
        loginMenuItem = menu.addItem(MENU_LOGIN, R.drawable.settings_login).apply {
            contentDescription = context.getString(R.string.profile_login_action)
        }
        logoutMenuItem = menu.addItem(MENU_LOGOUT, R.drawable.settings_logout).apply {
            contentDescription = context.getString(R.string.profile_logout_action)
        }
        actionBar.setActionBarMenuOnItemClick(
            object : ActionBar.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    when (id) {
                        MENU_OPEN_PROFILE -> if (profileWebUrl.isNotBlank()) openBrowser(profileWebUrl)
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
        openProfileMenuItem?.visibility = if (profileWebUrl.isNotBlank()) View.VISIBLE else View.GONE
        refreshProfileMenuItem?.visibility = View.VISIBLE
        loginMenuItem?.visibility = if (authenticated) View.GONE else View.VISIBLE
        logoutMenuItem?.visibility = if (authenticated) View.VISIBLE else View.GONE
    }

    private fun buildHeaderSubtitle(profile: UserProfile?): String {
        if (profile == null) return ""
        val lastSeen = formatDate(profile.lastSeenAt)
        if (lastSeen.isNotBlank()) {
            return "最近活动 $lastSeen"
        }
        if (profile.title.isNotBlank()) {
            return profile.title
        }
        val trustLabel = trustLevelLabel(profile.trustLevel)
        if (!trustLabel.isNullOrBlank()) {
            return trustLabel
        }
        return ""
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
                val parentLocation = IntArray(2)
                val viewLocation = IntArray(2)
                parent.getLocationInWindow(parentLocation)
                avatarView.getLocationInWindow(viewLocation)
                val receiver = avatarView.imageReceiver
                return PhotoViewer.PlaceProviderObject().apply {
                    viewX = viewLocation[0] - parentLocation[0] + receiver.imageX.toInt()
                    viewY = viewLocation[1] - parentLocation[1] + receiver.imageY.toInt()
                    viewWidth = receiver.imageWidth.toInt().coerceAtLeast(1)
                    viewHeight = receiver.imageHeight.toInt().coerceAtLeast(1)
                    parentView = parent
                    imageReceiver = receiver
                    thumb = if (needPreview) receiver.getBitmapSafe() else null
                    radius = if (needPreview) intArrayOf(
                        AndroidUtilities.dp(50f),
                        AndroidUtilities.dp(50f),
                        AndroidUtilities.dp(50f),
                        AndroidUtilities.dp(50f),
                    ) else null
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
                    items.add(UItem.asInfo(context?.getString(R.string.profile_session_restoring_title).orEmpty(), null))
                    items.add(UItem.asShadow(null))
                }
                profileState.errorMessage != null -> {
                    items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
                    items.add(
                        IconSettingFactory.of(
                            ACTION_RETRY_PROFILE,
                            IconSettingCell.Colors.ORANGE_TOP, IconSettingCell.Colors.ORANGE_BOTTOM,
                            R.drawable.settings_refresh,
                            context?.getString(R.string.profile_load_error_title).orEmpty(),
                            profileState.errorMessage.orEmpty(),
                            context?.getString(R.string.action_retry).orEmpty(),
                        )
                    )
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

                    // --- About (bio) section ---
                    if (profile.bio.isNotBlank()) {
                        items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
                        items.add(UItem.ofFactory(BioFactory()).apply { id = VIEW_TYPE_BIO })
                        items.add(UItem.asShadow(null))
                    }

                    // --- Identity section ---
                    val username = profile.username
                    val trustLabel = trustLevelLabel(profile.trustLevel)
                    val hasTitle = profile.title.isNotBlank()
                    if (username.isNotBlank() || trustLabel != null || hasTitle) {
                        items.add(UItem.asHeader(context?.getString(R.string.profile_identity_title).orEmpty()))
                        if (username.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_USERNAME, "@$username", "Username"))
                        }
                        if (trustLabel != null) {
                            items.add(TextDetailCell.Factory.of(ID_TRUST_LEVEL, trustLabel, "Trust Level"))
                        }
                        if (hasTitle) {
                            items.add(TextDetailCell.Factory.of(ID_TITLE, profile.title, "Title"))
                        }
                        items.add(UItem.asShadow(null))
                    }

                    val lastSeen = formatDate(profile.lastSeenAt)
                    val joined = formatDate(profile.createdAt)
                    val hasStats = lastSeen.isNotBlank() || profile.timeRead > 0 ||
                        profile.profileViewCount > 0 || profile.badgeCount > 0 || joined.isNotBlank()
                    if (hasStats) {
                        items.add(UItem.asHeader(context?.getString(R.string.profile_stats_title).orEmpty()))
                        if (lastSeen.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_LAST_SEEN, lastSeen, "最近活动"))
                        }
                        if (profile.timeRead > 0) {
                            items.add(TextDetailCell.Factory.of(ID_TIME_READ, formatTimeRead(profile.timeRead), "阅读时长"))
                        }
                        if (profile.profileViewCount > 0) {
                            items.add(TextDetailCell.Factory.of(ID_PROFILE_VIEWS, profile.profileViewCount.toString(), "资料浏览"))
                        }
                        if (profile.badgeCount > 0) {
                            items.add(TextDetailCell.Factory.of(ID_BADGE_COUNT, profile.badgeCount.toString(), "徽章"))
                        }
                        if (joined.isNotBlank()) {
                            items.add(TextDetailCell.Factory.of(ID_JOINED, joined, "加入时间"))
                        }
                        items.add(UItem.asShadow(null))
                    }

                    // --- Account section ---
                    items.add(UItem.asHeader(context?.getString(R.string.profile_account_title).orEmpty()))
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
                    items.add(UItem.asInfo(context?.getString(R.string.profile_session_restoring_title).orEmpty(), null))
                }
            }
            items.add(UItem.asShadow(null))
        }

        // --- Debug section (last, debug builds only) ---
        // Mirrors SettingsActivity.java:737 — dev tools are gated behind a build flag so
        // anonymous/release users never see them.
        addDevToolsItems(items)
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

    private fun handleItemClick(item: UItem) {
        when (item.id) {
            ACTION_LOGOUT -> showLogoutConfirm()
            ACTION_RETRY_PROFILE -> viewModel.retry()
            ACTION_LOGIN -> presentFragment(LoginWebViewFragment())
            ACTION_DEV_URL -> showUrlInputDialog(
                title = context?.getString(R.string.profile_dev_url_title).orEmpty(),
                message = context?.getString(R.string.profile_dev_url_message).orEmpty(),
                hint = context?.getString(R.string.profile_dev_url_hint).orEmpty(),
                actionText = context?.getString(R.string.profile_dev_url_open).orEmpty(),
                invalidText = context?.getString(R.string.profile_dev_url_invalid).orEmpty(),
                parser = ::normalizeHttpUrl,
                onSubmit = ::openBrowser,
            )
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
        AlertDialog.Builder(parentActivity, getResourceProvider())
            .setTitle(parentActivity.getString(R.string.profile_logout_confirm_title))
            .setMessage(parentActivity.getString(R.string.profile_logout_confirm_message))
            .setPositiveButton(parentActivity.getString(R.string.profile_logout_action)) { _, _ ->
                fragmentScope.launch {
                    appContainer.sessionCoordinator.onLogout()
                }
            }
            .setNegativeButton(parentActivity.getString(R.string.action_cancel), null)
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .show()
    }

    // --- Edge-to-edge support (mirrors ProfileActivity) ---

    override fun isSupportEdgeToEdge(): Boolean = true

    override fun drawEdgeNavigationBar(): Boolean = false

    // --- ContainerView (mirrors ProfileActivity.NestedFrameLayout) ---

    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private inner class ContainerView(context: Context) : SizeNotifierFrameLayout(context), NestedScrollingParent3 {

        private val nestedScrollingParentHelper = NestedScrollingParentHelper(this)

        override fun onNestedScroll(
            target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int, consumed: IntArray,
        ) {}

        override fun onNestedScroll(
            target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
            type: Int,
        ) {}

        override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean =
            super.onNestedPreFling(target, velocityX, velocityY)

        override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {}

        override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean =
            axes == ViewCompat.SCROLL_AXIS_VERTICAL

        override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes)
        }

        override fun onStopNestedScroll(target: View, type: Int) {
            nestedScrollingParentHelper.onStopNestedScroll(target)
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

    private fun drawBackgroundRect(canvas: Canvas, rect: RectF, r: Float, alpha: Float) {
        sectionPaint.setShadowLayer(
            AndroidUtilities.dpf2(1.5f), 0f, 0f,
            Theme.multAlpha(Color.BLACK and 0x20FFFFFF.toInt(), alpha),
        )
        sectionPaint.color = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite), alpha)
        canvas.drawRoundRect(rect, r, r, sectionPaint)
    }

    // --- TopView (mirrors ProfileActivity.TopView) ---
    // Draws the profile cover background from y=0 (covers the status bar area).
    // In upstream this also draws blur and animated peer patterns.

    private inner class TopView(context: Context) : View(context), Theme.Colorable {
        private var currentColor: Int = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var topGradient: LinearGradient? = null
        private var bottomGradient: LinearGradient? = null
        private var radialGradient: RadialGradient? = null
        private var gradientWidth = 0
        private var gradientHeight = 0
        private var gradientColor = 0

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
            val actionBarHeight = ActionBar.getCurrentActionBarHeight()
            val statusBarHeight = if (!AndroidUtilities.isTablet()) AndroidUtilities.statusBarHeight else 0
            val w = measuredWidth
            val h = (statusBarHeight + actionBarHeight + extraHeight).toInt().coerceAtLeast(1)
            if (w <= 0) return

            // Mirrors upstream: when fully expanded, TopView background becomes Color.BLACK
            val baseColor = ColorUtils.blendARGB(currentColor, Color.BLACK, expandProgress)
            paint.color = baseColor
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

            val diff = calculateHeaderExtraDiff()

            ensureGradients(w, h, baseColor)

            radialGradient?.let {
                scrimPaint.shader = it
                scrimPaint.alpha = ((1f - expandProgress) * 120).toInt().coerceIn(0, 120)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scrimPaint)
                scrimPaint.shader = null
            }

            topGradient?.let {
                scrimPaint.shader = it
                scrimPaint.alpha = ((0.18f + 0.34f * diff + 0.30f * expandProgress) * 255).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, 0f, w.toFloat(), (statusBarHeight + actionBarHeight + AndroidUtilities.dp(20f)).toFloat(), scrimPaint)
                scrimPaint.shader = null
            }

            bottomGradient?.let {
                val overlayHeight = AndroidUtilities.dp(96f)
                val top = (h - overlayHeight).coerceAtLeast(0).toFloat()
                scrimPaint.shader = it
                scrimPaint.alpha = ((0.20f + 0.35f * diff) * (1f - expandProgress * 0.35f) * 255).toInt().coerceIn(0, 255)
                canvas.drawRect(0f, top, w.toFloat(), h.toFloat(), scrimPaint)
                scrimPaint.shader = null
            }

            val overlayAlpha = (52 * diff * (1f - expandProgress)).toInt()
            if (overlayAlpha > 0) {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, overlayAlpha)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
        }

        private fun ensureGradients(width: Int, height: Int, baseColor: Int) {
            if (width == gradientWidth && height == gradientHeight && baseColor == gradientColor) return
            gradientWidth = width
            gradientHeight = height
            gradientColor = baseColor
            topGradient = LinearGradient(
                0f, 0f, 0f, (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(28f)).toFloat(),
                intArrayOf(0x8A000000.toInt(), 0x22000000, 0x00000000),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
            bottomGradient = LinearGradient(
                0f, (height - AndroidUtilities.dp(96f)).coerceAtLeast(0).toFloat(), 0f, height.toFloat(),
                intArrayOf(0x00000000, 0x42000000, 0x72000000),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            val accent = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                baseColor,
                0.62f,
            )
            radialGradient = RadialGradient(
                width * 0.5f,
                (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight()).toFloat(),
                width.coerceAtLeast(height).toFloat() * 0.78f,
                intArrayOf(ColorUtils.setAlphaComponent(accent, 96), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        override fun updateColors() {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        }
    }

    // --- AvatarImageView (mirrors ProfileActivity.AvatarImageView) ---
    // Uses BackupImageView + ImageReceiver pattern from upstream.

    class AvatarImageView(context: Context) : org.telegram.ui.Components.BackupImageView(context) {
        val localAvatarDrawable = AvatarDrawable()
        private var roundRadiusExpand: Int = AndroidUtilities.dp(50f)
        private val bounce = ButtonBounce(this, 1f, 3f)
        private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val loadingRect = RectF()

        init {
            imageReceiver.setAllowDecodeSingleFrame(true)
            setRoundRadius(AndroidUtilities.dp(50f))
        }

        /**
         * Mirrors upstream AvatarImageView.setRoundRadiusForExpand().
         * When expanded (pulled down), the round radius lerps from dp(50)=circle to 0=rectangle.
         */
        fun setRoundRadiusForExpand(value: Int) {
            roundRadiusExpand = value
            setRoundRadius(value)
        }

        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            bounce.setPressed(pressed)
        }

        override fun onDraw(canvas: Canvas) {
            val scale = bounce.getScale(0.04f)
            if (scale != 1f) {
                canvas.save()
                canvas.scale(scale, scale, width / 2f, height / 2f)
            }
            super.onDraw(canvas)
            drawLoadingRing(canvas)
            if (scale != 1f) {
                canvas.restore()
            }
        }

        private fun drawLoadingRing(canvas: Canvas) {
            if (!imageReceiver.hasImageSet() || imageReceiver.hasImageLoaded()) {
                return
            }
            val inset = AndroidUtilities.dp(3f).toFloat()
            loadingRect.set(inset, inset, width - inset, height - inset)
            if (loadingRect.width() <= 0f || loadingRect.height() <= 0f) {
                return
            }
            loadingPaint.strokeWidth = AndroidUtilities.dp(2f).toFloat()
            loadingPaint.color = ColorUtils.setAlphaComponent(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                180,
            )
            val start = ((SystemClock.uptimeMillis() % 1200L) / 1200f) * 360f
            canvas.drawArc(loadingRect, start, 82f, false, loadingPaint)
            postInvalidateOnAnimation()
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

    // --- Bio cell ---

    private inner class BioFactory : UItem.ViewFactory {
        override fun getViewType(): Int = VIEW_TYPE_BIO
        override fun createView(context: Context): View = BioCell(context)
        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter) {
            (view as BioCell).bind(profileState.profile?.bio.orEmpty())
        }
        override fun isEnabled(item: UItem): Boolean = false
    }

    private inner class BioCell(context: Context) : FrameLayout(context), Theme.Colorable {
        private val textView = LinkSpanDrawable.LinksTextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1.0f)
            highlightColor = Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection)
        }
        private var currentBio: String? = null

        init {
            setPadding(
                AndroidUtilities.dp(20f),
                AndroidUtilities.dp(12f),
                AndroidUtilities.dp(20f),
                AndroidUtilities.dp(16f),
            )
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        }

        fun bind(bio: String) {
            if (currentBio != bio) {
                currentBio = bio
                textView.text = buildBioText(bio)
            }
            updateColors()
        }

        override fun updateColors() {
            background = sectionBackground()
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
            textView.highlightColor = Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection)
        }

        private fun buildBioText(bio: String): CharSequence {
            val parsed = Html.fromHtml(normalizeInlineHtml(bio), Html.FROM_HTML_MODE_COMPACT)
            val text = SpannableStringBuilder(parsed)
            text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                val flags = text.getSpanFlags(span)
                val url = span.url
                text.removeSpan(span)
                text.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        Browser.openUrl(parentActivity ?: return, url)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        super.updateDrawState(ds)
                        ds.color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
                    }
                }, start, end, flags)
            }
            return text
        }
    }

    // --- Helpers ---

    private fun sectionBackground(): GradientDrawable = GradientDrawable().apply {
        color = android.content.res.ColorStateList.valueOf(Theme.getColor(Theme.key_windowBackgroundWhite))
        cornerRadius = 0f
    }

    // --- Theme delegate ---

    override fun didSetNewTheme() {
        (fragmentView as? View)?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        listView?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        topView?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        updateList()
    }

    override fun onFragmentDestroy() {
        Theme.removeThemeDelegate(this)
        fragmentScope.cancel()
        super.onFragmentDestroy()
    }

    // --- URL input dialog ---

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
        UrlInputDialog(context, title, message, hint, initialText, actionText, invalidText, parser, onSubmit).show()
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

    private fun openBrowser(url: String) {
        val context = getContext() ?: return
        Browser.openUrl(context, url)
    }

    private inner class UrlInputDialog(
        context: Context,
        private val titleText: String,
        private val messageText: String,
        private val hintText: String,
        private val initialText: String,
        private val actionText: String,
        private val invalidText: String,
        private val parser: (String) -> String?,
        private val onSubmit: (String) -> Unit,
    ) : Dialog(context) {
        private val input = EditTextBoldCursor(context)

        override fun show() {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(createContentView())
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setDimAmount(0.32f)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            super.show()
            window?.setLayout(
                context.resources.displayMetrics.widthPixels - AndroidUtilities.dp(48f),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            input.post {
                input.requestFocus()
                context.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        private fun createContentView(): View {
            val content = LinearLayout(context)
            content.orientation = LinearLayout.VERTICAL
            content.background = dialogBackground()
            content.setPadding(
                AndroidUtilities.dp(22f),
                AndroidUtilities.dp(20f),
                AndroidUtilities.dp(22f),
                AndroidUtilities.dp(10f),
            )

            val title = createText(context, 20f, true)
            title.text = titleText

            val message = createText(context, 14f)
            message.text = messageText
            message.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))

            input.hint = hintText
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            input.imeOptions = EditorInfo.IME_ACTION_GO
            input.setSingleLine(true)
            input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            input.setHintTextColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 180))
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            input.background = inputBackground()
            input.setPadding(AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(14f), 0)
            if (initialText.isNotEmpty()) {
                input.setText(initialText)
                input.setSelection(initialText.length)
            }

            fun submit() {
                val parsed = parser(input.text?.toString().orEmpty())
                if (parsed == null) {
                    input.error = invalidText
                    return
                }
                dismiss()
                onSubmit(parsed)
            }
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    submit()
                    true
                } else {
                    false
                }
            }

            val titleLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            content.addView(title, titleLp)

            val messageLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            (messageLp as ViewGroup.MarginLayoutParams).topMargin = AndroidUtilities.dp(10f)
            content.addView(message, messageLp)

            val inputLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(50f))
            (inputLp as ViewGroup.MarginLayoutParams).topMargin = AndroidUtilities.dp(18f)
            content.addView(input, inputLp)

            val buttonsRow = LinearLayout(context)
            buttonsRow.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            buttonsRow.addView(createButton(context, context.getString(android.R.string.cancel), false) { dismiss() })
            buttonsRow.addView(createButton(context, actionText, true) { submit() })
            val buttonsLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56f))
            (buttonsLp as ViewGroup.MarginLayoutParams).topMargin = AndroidUtilities.dp(8f)
            content.addView(buttonsRow, buttonsLp)

            return content
        }
    }

    private fun dialogBackground(): GradientDrawable = GradientDrawable().apply {
        color = android.content.res.ColorStateList.valueOf(Theme.getColor(Theme.key_windowBackgroundWhite))
        cornerRadius = AndroidUtilities.dpf2(14f)
    }

    private fun inputBackground(): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(Theme.getColor(Theme.key_windowBackgroundGray))
        d.cornerRadius = AndroidUtilities.dpf2(10f)
        val strokeWidth = AndroidUtilities.dp(1f)
        val strokeColor = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 210)
        d.setStroke(strokeWidth, strokeColor)
        return d
    }

    private fun createText(context: Context, sizeSp: Float, bold: Boolean = false): TextView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun createButton(context: Context, text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val primaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
        val btn = TextView(context)
        btn.text = text.uppercase()
        btn.gravity = Gravity.CENTER
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        btn.typeface = Typeface.DEFAULT_BOLD
        btn.setTextColor(if (primary) primaryColor else Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        val btnView = btn as View
        btnView.minimumWidth = AndroidUtilities.dp(64f)
        btnView.minimumHeight = AndroidUtilities.dp(40f)
        btnView.setPadding(AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(12f), 0)
        btn.background = Theme.createSelectorDrawable(
            ColorUtils.setAlphaComponent(primaryColor, if (primary) 28 else 18),
            Theme.RIPPLE_MASK_ALL,
        )
        btn.setOnClickListener { onClick() }
        return btn
    }
}

private class ProfileViewModelFactory(
    private val repository: ProfileRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(repository = repository) as T
    }
}

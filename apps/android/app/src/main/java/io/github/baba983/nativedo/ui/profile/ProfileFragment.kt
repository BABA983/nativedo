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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.InputType
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
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import io.github.baba983.nativedo.feature.profile.ui.ProfileUiState
import io.github.baba983.nativedo.feature.profile.ui.ProfileViewModel
import io.github.baba983.nativedo.ui.auth.LoginWebViewFragment
import io.github.baba983.nativedo.ui.browser.BrowserSheet
import io.github.baba983.nativedo.ui.main.MainTabsActivity
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
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ProfileGooeyView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.SizeNotifierFrameLayout
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.ui.Cells.TextDetailCell

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
    private var nameTextView = arrayOfNulls<SimpleTextView>(2) // [0]=dark(collapsed), [1]=white(expanded)
    private var subtitleView = arrayOfNulls<SimpleTextView>(2)

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

    // --- Header height constants (mirrors ProfileActivity) ---

    private fun getHeaderOnlyExtraHeight(): Int {
        // No actions buttons row — upstream returns dp(168) when getActionsExtraHeight==0
        return AndroidUtilities.dp(168f)
    }

    private fun getActionsExtraHeight(): Int = 0

    private fun getHeaderExtraHeight(): Int = getHeaderOnlyExtraHeight() + getActionsExtraHeight()

    private fun calculateHeaderExtraDiff(): Float {
        return Utilities.clamp01((extraHeight - getActionsExtraHeight()) / getHeaderOnlyExtraHeight())
    }

    override fun createView(context: Context): View {
        Theme.addThemeDelegate(this)
        actionBar.setCastShadows(false)
        actionBar.setAddToContainer(false)
        actionBar.setClipContent(true)
        actionBar.setBackgroundColor(Color.TRANSPARENT)
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet())
        actionBar.setBackButtonDrawable(BackDrawable(false))

        appContainer = (context.applicationContext as NativeDoApp).appContainer
        viewModel = ViewModelProvider(
            this,
            ProfileViewModelFactory(repository = ProfileRepository(appContainer.discourseApiClient)),
        )[ProfileViewModel::class.java]

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
        aiv.setOnClickListener { toggleAvatarExpansion() }
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
        name0.setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(12f))
        name0.setEllipsizeByGradient(true)
        name0.setScrollNonFitText(true)
        name0.setAlpha(0f) // hidden by default (shown when collapsed)
        ac2.addView(name0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, -6f, 40f, 0f))
        nameTextView[0] = name0

        // --- nameTextView[1] (expanded: white, sits below avatar) ---
        // Mirrors upstream nameTextView[1] with key_profile_title → WHITE blend.
        val name1 = SimpleTextView(context)
        name1.setTextSize(18)
        name1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        name1.setGravity(Gravity.LEFT)
        name1.setTypeface(AndroidUtilities.bold())
        name1.setPivotX(0f)
        name1.setPivotY(0f)
        name1.setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(4f))
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
        sub1.setTextColor(Theme.multAlpha(Color.WHITE, 0.55f))
        sub1.setGravity(Gravity.LEFT)
        sub1.setPivotX(AndroidUtilities.dp(8f).toFloat())
        sub1.setPivotY(AndroidUtilities.dp(8f).toFloat())
        sub1.setEllipsizeByGradient(true)
        sub1.setScrollNonFitText(true)
        sub1.setAlpha(1f)
        ac2.addView(sub1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.TOP, 118f, 0f, 40f, 0f))
        subtitleView[1] = sub1

        // ActionBar on top of everything
        container.addView(actionBar)

        listView = recycler
        universalAdapter = recycler.adapter
        fragmentView = container

        updateLayout()
        updateInfo()

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
     * Mirrors ProfileActivity.needLayout().
     * Positions avatar, title, subtitle based on scroll progress.
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

        // --- endNameY: the Y where the avatar center sits in expanded state ---
        val endNameY = (statusBarHeight + actionBarHeight / 2f * (1f + diff) - AndroidUtilities.dp(21f))

        // --- Avatar position (mirrors ProfileActivity scroll logic) ---
        avatarContainer?.let { ac ->
            val params = ac.layoutParams as FrameLayout.LayoutParams

            if (extraHeight > getHeaderExtraHeight() || isPulledDown) {
                // Expanded state (pulled down past header)
                val expandDistance = (width - newTop - getHeaderOnlyExtraHeight()).toFloat().coerceAtLeast(1f)
                expandProgress = Utilities.clamp01((extraHeight - getHeaderExtraHeight()) /
                    expandDistance)

                // Set isPulledDown when expandProgress crosses threshold (mirrors upstream)
                if (allowPullingDown && expandProgress >= 0.33f && !isPulledDown) {
                    isPulledDown = true
                }

                avatarScale = AndroidUtilities.lerp(96f / 42f, 138f / 42f,
                    Math.min(1f, expandProgress * 3f)) / 100f * 42f
                pullUpProgress = 0f

                // Avatar Y: lerp from previous avatarY toward 0 (top of screen)
                // Mirrors upstream: avatarContainer.setTranslationY(lerp(ceil(avatarY), 0, expandValue))
                avatarY = AndroidUtilities.lerp(avatarY, 0f, expandProgress)

                // When pulled down, change width/height/margin (mirrors upstream setAvatarExpandProgress)
                // Upstream: params.width = lerp(dpf2(100), width/avatarScale, value)
                params.width = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), width.toFloat() / avatarScale, expandProgress).toInt()
                params.height = AndroidUtilities.lerp(AndroidUtilities.dpf2(100f), (extraHeight + newTop) / avatarScale, expandProgress).toInt()
                params.leftMargin = AndroidUtilities.lerp(AndroidUtilities.dpf2(64f), 0f, expandProgress).toInt()
                ac.layoutParams = params
            } else if (extraHeight <= getHeaderExtraHeight()) {
                // Collapsed / default state — normal scroll range
                expandProgress = 0f // Reset: roundRadius returns to dp(50)=circle

                // Reset isPulledDown when back in normal scroll range (mirrors upstream)
                if (isPulledDown) {
                    isPulledDown = false
                }

                val intoSize = 24f
                avatarScale = AndroidUtilities.lerp(intoSize, 96f, diff) / 100f
                pullUpProgress = 1.0f - diff

                // Avatar Y position: lerp from status bar center (collapsed) to below action bar (expanded)
                val endY = -AndroidUtilities.dpf2(29f)
                val startY = (newTop - AndroidUtilities.dpf2(21f))
                avatarY = AndroidUtilities.lerp(endY, startY, diff)

                // Keep fixed 100×100, dp(64) left margin — only scaleX/Y changes
                params.width = AndroidUtilities.dpf2(100f).toInt()
                params.height = AndroidUtilities.dpf2(100f).toInt()
                params.leftMargin = AndroidUtilities.dpf2(64f).toInt()
                ac.layoutParams = params
            }

            ac.scaleX = avatarScale
            ac.scaleY = avatarScale

            // Center avatar horizontally (mirrors upstream fixAvatarImageInCenter)
            val avatarX = width / 2f - (params.leftMargin + params.width * avatarScale * 0.5f)
            ac.translationX = avatarX
            ac.translationY = Math.ceil(avatarY.toDouble()).toFloat()

            // AvatarImageView roundRadius: lerp dp(50) → 0 based on expandProgress
            // Mirrors upstream: avatarImage.setRoundRadiusForExpand(lerp(getSmallAvatarRoundRadius(), 0, value))
            avatarImageView?.setRoundRadiusForExpand(
                AndroidUtilities.lerp(AndroidUtilities.dp(50f).toFloat(), 0f, expandProgress).toInt()
            )

            updateGooey()
        }

        // --- nameTextView/subtitleView position & cross-fade (mirrors ProfileActivity) ---
        val nameScale = 1.0f + 0.12f * diff

        // Mirrors upstream: for a==0 use viewportWidth, for a==1 use params.width
        for (a in 0..1) {
            val nt = nameTextView[a] ?: continue
            val layoutParams = nt.layoutParams as FrameLayout.LayoutParams

            // When pulledDown + expandAnimator running, upstream scales name[1] up to 1.38
            if (a == 1 && extraHeight > getHeaderOnlyExtraHeight()) {
                nt.scaleX = AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, expandProgress)
                nt.scaleY = AndroidUtilities.lerp(1f + 0.12f * diff, 1.38f, expandProgress)
                // PivotY shifts to bottom when expanded (upstream line 6295)
                nt.setPivotY(AndroidUtilities.lerp(0f, nt.measuredHeight.toFloat(), expandProgress))
            } else {
                nt.scaleX = nameScale
                nt.scaleY = nameScale
                nt.setPivotY(0f)
            }

            // Cross-fade alpha: [0] fades in when collapsed (diff≈0), [1] fades in when expanded (diff≈1)
            nt.alpha = if (a == 0) 1f - diff else diff

            // nameX: upstream uses viewportWidth for a==0, params.width for a==1
            val availableWidth = if (a == 0 || layoutParams.width <= 0) {
                width.toFloat()
            } else {
                layoutParams.width.toFloat()
            }
            val textWidth = Math.min(nt.getTextWidth().toFloat(), availableWidth) * nt.scaleX
            val nameX = AndroidUtilities.lerp(-AndroidUtilities.dpf2(42f + 4f),
                width / 2f - (118f + textWidth * 0.5f), diff)
            nt.translationX = nameX
        }

        for (a in 0..1) {
            val sv = subtitleView[a] ?: continue
            val measuredWidth = if (sv.measuredWidth > 0) sv.measuredWidth.toFloat() else width.toFloat()
            val subtitleWidth = Math.min(sv.getTextWidth().toFloat(), measuredWidth)
            val onlineX = AndroidUtilities.lerp(-AndroidUtilities.dpf2(42f + 4f),
                width / 2f - (114f + subtitleWidth * 0.5f), diff)
            sv.translationX = onlineX

            // Cross-fade alpha
            sv.alpha = if (a == 0) 1f - diff else diff
        }

        // --- Name Y position ---
        // In normal scroll: based on avatarBottom (mirrors upstream line 8615-8617)
        // In isPulledDown: name goes to bottom of full-width cover (mirrors upstream line 8411)
        avatarContainer?.let { ac ->
            if (extraHeight > getHeaderExtraHeight() || isPulledDown) {
                // When expanded, position name at bottom of the cover area
                // Mirrors upstream: nameTextViewY = newTop + h - getActionsExtraHeight() - dpf2(30) - bottom
                val nameY = newTop + extraHeight - AndroidUtilities.dpf2(30f) - (nameTextView[1]?.bottom ?: 0)
                val onlineY = newTop + extraHeight - AndroidUtilities.dpf2(10f) - (subtitleView[1]?.bottom ?: 0)

                // Clamp: don't let name go above the minimum actionBar position
                val minNameY = Math.floor(endNameY.toDouble()).toFloat() + AndroidUtilities.dp(1.3f)
                val minOnlineY = minNameY + AndroidUtilities.dpf2(22.7f)

                nameTextView[0]?.translationY = Math.max(minNameY, nameY)
                nameTextView[1]?.translationY = Math.max(minNameY, nameY)
                subtitleView[0]?.translationY = Math.max(minOnlineY, onlineY)
                subtitleView[1]?.translationY = Math.max(minOnlineY, onlineY)
            } else {
                // Normal scroll: position below avatar
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

        // nameTextView[1] color: blend from profile_title → WHITE based on expandProgress
        // Mirrors upstream: nameTextView[1].setTextColor(blendARGB(key_profile_title, WHITE, value))
        nameTextView[1]?.setTextColor(ColorUtils.blendARGB(
            Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
            Color.WHITE,
            expandProgress,
        ))

        // subtitleView[1] color: blend toward more transparent white on expand
        // Mirrors upstream: onlineTextView[1].setTextColor(blendARGB(statusColor, 0xB3FFFFFF, value))
        subtitleView[1]?.setTextColor(ColorUtils.blendARGB(
            Theme.multAlpha(Color.WHITE, 0.55f),
            0xB3FFFFFF.toInt(),
            expandProgress,
        ))

        // ActionBar background alpha
        val actionBarAlpha = 1f - diff
        actionBar.setBackgroundColor(Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefault), actionBarAlpha))
    }

    private fun settleHeaderAfterTouch() {
        val rv = listView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val view = layoutManager.findViewByPosition(0) ?: return
        if (allowPullingDown || isPulledDown) {
            allowPullingDown = false
            isPulledDown = false
            smoothHeaderBy(view.top - getListTopForExtraHeight(getHeaderExtraHeight()))
        } else if (extraHeight > 0f && listViewVelocityY < -1000f) {
            smoothHeaderBy(extraHeight.toInt())
        } else if (extraHeight > 0f) {
            smoothHeaderBy(view.top - getListTopForExtraHeight(getHeaderExtraHeight()))
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
        val targetTop = if (extraHeight > getHeaderExtraHeight() + AndroidUtilities.dp(24f)) {
            getListTopForExtraHeight(getHeaderExtraHeight())
        } else {
            allowPullingDown = true
            expandedTop
        }
        animateHeaderTo(targetTop, view.top)
    }

    private fun animateHeaderTo(targetTop: Int, currentTop: Int) {
        val rv = listView ?: return
        val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
        val collapsedTop = getListTopForExtraHeight(getHeaderExtraHeight())
        headerAnimator?.cancel()
        headerAnimator = ValueAnimator.ofInt(currentTop, targetTop).apply {
            duration = 320L
            interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
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
        gooey.setBlurIntensity(Math.min((clamped - 0.2f) / 0.5f, 1.0f))
        gooey.setGooeyEnabled(pullUpProgress > 0 && pullUpProgress < 1)
        gooey.visibility = if (pullUpProgress >= 1.0f) View.GONE else View.VISIBLE
    }

    // --- Info updates ---

    @Suppress("IMPLICIT_CAST")
    private fun updateInfo() {
        val title: String
        val subtitle: String
        val avatarTemplate: String
        val showAvatar: Boolean

        val state = sessionState
        when (state) {
            is AppSessionState.Authenticated -> {
                val snapshot = state.snapshot
                val profile = profileState.profile
                val displayName = profile?.displayName?.takeIf(String::isNotBlank)
                title = displayName ?: snapshot.displayName.takeIf(String::isNotBlank) ?: snapshot.username
                subtitle = "@${profile?.username ?: snapshot.username}"
                avatarTemplate = profile?.avatarTemplate ?: snapshot.avatarTemplate
                showAvatar = true
            }
            is AppSessionState.Restoring -> {
                val snapshot = state.snapshot
                title = context?.getString(R.string.profile_session_restoring_title).orEmpty()
                subtitle = snapshot?.displayName?.takeIf(String::isNotBlank)
                    ?: context?.getString(R.string.profile_session_restoring_subtitle).orEmpty()
                avatarTemplate = snapshot?.avatarTemplate.orEmpty()
                showAvatar = snapshot != null
            }
            is AppSessionState.Anonymous -> {
                title = context?.getString(R.string.profile_login_title).orEmpty()
                subtitle = context?.getString(R.string.profile_login_subtitle).orEmpty()
                avatarTemplate = ""
                showAvatar = false
            }
            is AppSessionState.Expired -> {
                val snapshot = state.snapshot
                title = context?.getString(R.string.profile_session_expired_title).orEmpty()
                subtitle = context?.getString(R.string.profile_session_expired_subtitle).orEmpty()
                avatarTemplate = snapshot?.avatarTemplate.orEmpty()
                showAvatar = snapshot != null
            }
            else -> {
                title = context?.getString(R.string.profile_session_restoring_title).orEmpty()
                subtitle = context?.getString(R.string.profile_session_restoring_subtitle).orEmpty()
                avatarTemplate = ""
                showAvatar = false
            }
        }

        nameTextView[0]?.text = title
        nameTextView[1]?.text = title
        subtitleView[0]?.text = subtitle
        subtitleView[1]?.text = subtitle

        // Set avatar
        avatarImageView?.let { aiv ->
            aiv.localAvatarDrawable.setInfo(0, title)
            canExpandAvatar = showAvatar && avatarTemplate.isNotEmpty()
            aiv.isClickable = canExpandAvatar
            if (showAvatar && avatarTemplate.isNotEmpty()) {
                val avatarUrl = appContainer.baseUrl + avatarTemplate.replace("{size}", "360")
                aiv.imageReceiver.setImage(avatarUrl, "360_360", aiv.localAvatarDrawable, null, 0)
            } else {
                aiv.imageReceiver.setImageBitmap(null as android.graphics.Bitmap?)
            }
        }

        needLayout()
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
                    items.add(UItem.asShadow(null))
                    items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
                    items.add(UItem.asInfo(context?.getString(R.string.profile_session_restoring_title).orEmpty(), null))
                    items.add(UItem.asShadow(null))
                }
                profileState.errorMessage != null -> {
                    items.add(UItem.asShadow(null))
                    items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
                    items.add(
                        UItem.asInfo(
                            ACTION_RETRY_PROFILE,
                            context?.getString(R.string.profile_load_error_title).orEmpty(),
                            profileState.errorMessage.orEmpty(),
                            context?.getString(R.string.action_retry).orEmpty(),
                        ).accent(),
                    )
                    items.add(UItem.asShadow(null))
                }
                profileState.profile != null -> {
                    val profile = profileState.profile!!
                    items.add(UItem.asShadow(null))

                    // Identity section
                    val username = profile.username
                    if (username.isNotBlank()) {
                        items.add(TextDetailCell.Factory.of(ID_USERNAME, "@$username", "Username"))
                    }
                    val trustLabel = trustLevelLabel(profile.trustLevel)
                    if (trustLabel != null) {
                        items.add(TextDetailCell.Factory.of(ID_TRUST_LEVEL, trustLabel, "Trust Level"))
                    }
                    if (profile.title.isNotBlank()) {
                        items.add(TextDetailCell.Factory.of(ID_TITLE, profile.title, "Title"))
                    }
                    if (profile.bio.isNotBlank()) {
                        items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
                        items.add(UItem.ofFactory(BioFactory()).apply { id = VIEW_TYPE_BIO })
                    }
                    items.add(UItem.asShadow(null))

                    // Stats section
                    items.add(UItem.asShadow(null))
                    if (profile.timeRead > 0) {
                        items.add(TextDetailCell.Factory.of(ID_TIME_READ, formatTimeRead(profile.timeRead), "阅读时长"))
                    }
                    if (profile.createdAt.isNotBlank()) {
                        items.add(TextDetailCell.Factory.of(ID_JOINED, formatDate(profile.createdAt), "加入时间"))
                    }
                    if (profile.lastSeenAt.isNotBlank()) {
                        items.add(TextDetailCell.Factory.of(ID_LAST_SEEN, formatDate(profile.lastSeenAt), "最近活动"))
                    }
                    if (profile.profileViewCount > 0) {
                        items.add(TextDetailCell.Factory.of(ID_PROFILE_VIEWS, "${profile.profileViewCount}", "Profile Views"))
                    }
                    if (profile.badgeCount > 0) {
                        items.add(TextDetailCell.Factory.of(ID_BADGE_COUNT, "${profile.badgeCount}", "Badges"))
                    }
                    items.add(UItem.asShadow(null))
                }
            }
        } else {
            items.add(UItem.asShadow(null))
            items.add(UItem.asHeader(context?.getString(R.string.profile_about_title).orEmpty()))
            when (state) {
                is AppSessionState.Anonymous -> {
                    items.add(
                        UItem.asInfo(
                            ACTION_LOGIN,
                            context?.getString(R.string.profile_login_title).orEmpty(),
                            context?.getString(R.string.profile_login_subtitle).orEmpty(),
                            context?.getString(R.string.profile_login_action).orEmpty(),
                        ).accent(),
                    )
                }
                is AppSessionState.Expired -> {
                    items.add(
                        UItem.asInfo(
                            ACTION_LOGIN,
                            context?.getString(R.string.profile_session_expired_title).orEmpty(),
                            context?.getString(R.string.profile_session_expired_subtitle).orEmpty(),
                            context?.getString(R.string.profile_login_again_action).orEmpty(),
                        ).accent(),
                    )
                }
                else -> {
                    items.add(UItem.asInfo(context?.getString(R.string.profile_session_restoring_title).orEmpty(), null))
                }
            }
            items.add(UItem.asShadow(null))
        }

        // Dev tools section
        items.add(UItem.asHeader(context?.getString(R.string.profile_dev_tools_title).orEmpty()))
        items.add(
            UItem.asSetting(
                ACTION_DEV_URL,
                0,
                context?.getString(R.string.profile_dev_url_title).orEmpty(),
                context?.getString(R.string.profile_dev_tools_subtitle).orEmpty(),
                null,
            ),
        )
        items.add(
            UItem.asSetting(
                ACTION_TOPIC_URL,
                0,
                context?.getString(R.string.profile_topic_dev_url_title).orEmpty(),
                context?.getString(R.string.profile_topic_dev_tools_subtitle).orEmpty(),
                null,
            ),
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
                actionText = context?.getString(R.string.profile_topic_dev_url_open).orEmpty(),
                invalidText = context?.getString(R.string.profile_topic_dev_url_invalid).orEmpty(),
                parser = { parseLinuxDoTopicId(it)?.toString() },
                onSubmit = { topicId -> presentFragment(TopicDetailFragment(topicId.toLong())) },
            )
        }
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
        private var gradient: LinearGradient? = null
        private var gradientHeight = 0
        private var gradientTopColor = 0
        private var gradientBottomColor = 0

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
            val h = (statusBarHeight + actionBarHeight + extraHeight).toInt()
            val topColor = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_actionBarDefault),
                Theme.getColor(Theme.key_avatar_backgroundBlue),
                0.82f,
            )
            val bottomColor = ColorUtils.blendARGB(
                currentColor,
                Theme.getColor(Theme.key_avatar_background2Blue),
                0.72f,
            )
            if (gradient == null || gradientHeight != h || gradientTopColor != topColor || gradientBottomColor != bottomColor) {
                gradientHeight = h
                gradientTopColor = topColor
                gradientBottomColor = bottomColor
                gradient = LinearGradient(
                    0f,
                    0f,
                    0f,
                    h.toFloat().coerceAtLeast(1f),
                    topColor,
                    bottomColor,
                    Shader.TileMode.CLAMP,
                )
            }
            paint.shader = gradient
            canvas.drawRect(0f, 0f, measuredWidth.toFloat(), h.toFloat(), paint)
            paint.shader = null

            val overlayAlpha = (72 * calculateHeaderExtraDiff()).toInt()
            if (overlayAlpha > 0) {
                paint.color = ColorUtils.setAlphaComponent(Color.BLACK, overlayAlpha)
                canvas.drawRect(0f, 0f, measuredWidth.toFloat(), h.toFloat(), paint)
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
        private var roundRadiusExpand: Int = AndroidUtilities.dp(50f)

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

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            imageReceiver.onAttachedToWindow()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            imageReceiver.onDetachedFromWindow()
        }
    }

    // --- Bio cell (ComposeView-based) ---

    private inner class BioFactory : UItem.ViewFactory {
        override fun getViewType(): Int = VIEW_TYPE_BIO
        override fun createView(context: Context): View = BioCell(context)
        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter) {
            (view as BioCell).bind(profileState.profile?.bio.orEmpty())
        }
        override fun isEnabled(item: UItem): Boolean = false
    }

    private inner class BioCell(context: Context) : LinearLayout(context), Theme.Colorable {
        private val composeView = androidx.compose.ui.platform.ComposeView(context)
        private var currentBio: String? = null

        init {
            orientation = VERTICAL
            setPadding(
                AndroidUtilities.dp(20f),
                AndroidUtilities.dp(12f),
                AndroidUtilities.dp(20f),
                AndroidUtilities.dp(16f),
            )
            addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            composeView.setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
        }

        fun bind(bio: String) {
            if (currentBio != bio) {
                currentBio = bio
                composeView.setContent {
                    HtmlPostBodyView(
                        html = bio,
                        cookieStore = appContainer.cookieStore,
                        baseUrl = appContainer.baseUrl,
                    )
                }
            }
            updateColors()
        }

        override fun updateColors() {
            background = sectionBackground()
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
        actionText: String,
        invalidText: String,
        parser: (String) -> String?,
        onSubmit: (String) -> Unit,
    ) {
        val context = getContext() ?: return
        UrlInputDialog(context, title, message, hint, actionText, invalidText, parser, onSubmit).show()
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
        val sheet = BrowserSheet(this, url)
        addSheet(sheet)
        sheet.show()
    }

    private inner class UrlInputDialog(
        context: Context,
        private val titleText: String,
        private val messageText: String,
        private val hintText: String,
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

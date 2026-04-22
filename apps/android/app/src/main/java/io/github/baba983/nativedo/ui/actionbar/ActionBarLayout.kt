/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.ui.ActionBar.ActionBarLayout.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 as published by
 * Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-only
 */
package io.github.baba983.nativedo.ui.actionbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.baba983.nativedo.core.event.NotificationCenter
import io.github.baba983.nativedo.core.event.NotificationCenterDelegate
import io.github.baba983.nativedo.core.theme.NdTheme
import io.github.baba983.nativedo.core.util.AndroidUtilities
import io.github.baba983.nativedo.core.util.CubicBezierInterpolator
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

class ActionBarLayout(
    context: Context,
) : FrameLayout(context), INavigationLayout, NotificationCenterDelegate {
    private companion object {
        const val TransitionDurationMs: Long = 220L
        const val BackgroundShiftFactor: Float = 0.25f
        const val SwipeDismissThreshold: Float = 0.35f
        const val SwipeVelocityThresholdDpPerSecond: Float = 1000f
        const val SwipeStartThresholdCm: Float = 0.4f
        const val EdgeShadowWidthDp: Float = 20f
        const val BackScrimMaxAlpha: Int = 120
        const val StateEntriesKey: String = "action_bar_layout_entries"
        const val StateEntryNavigationKey: String = "action_bar_layout_entry_navigation_key"
        const val StateEntryArguments: String = "action_bar_layout_entry_arguments"
    }

    private val fragmentHolders = mutableListOf<FragmentHolder>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var delegate: INavigationLayoutDelegate? = null
    private var isPaused: Boolean = true
    private var transitionAnimator: AnimatorSet? = null
    private var maybeStartTracking: Boolean = false
    private var swipeBackInProgress: Boolean = false
    private var startedTrackingPointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var startedTrackingX: Float = 0f
    private var startedTrackingY: Float = 0f
    private var velocityTracker: VelocityTracker? = null
    private var lastVisibleFragment: BaseFragment? = null
    private val backScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeShadowMatrix = Matrix()
    private val edgeShadowWidth = AndroidUtilities.dpf2(EdgeShadowWidthDp)
    private val edgeShadowGradient = LinearGradient(
        0f,
        0f,
        edgeShadowWidth,
        0f,
        intArrayOf(0x00000000, 0x2A000000),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP,
    )

    init {
        background = NdTheme.getThemedDrawable(NdTheme.key_windowBackgroundGray)
        fitsSystemWindows = false
        edgeShadowPaint.shader = edgeShadowGradient
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotificationCenter.addObserver(this, NotificationCenter.themeDidChange)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        NotificationCenter.removeObserver(this, NotificationCenter.themeDidChange)
        recycleVelocityTracker()
        maybeStartTracking = false
        swipeBackInProgress = false
        lastVisibleFragment = null
        super.onDetachedFromWindow()
    }

    override fun didReceivedNotification(id: Int, vararg args: Any?) {
        if (id == NotificationCenter.themeDidChange) {
            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawNavigationDepth(canvas)
    }

    override fun presentFragment(fragment: BaseFragment, animated: Boolean): Boolean {
        if (transitionAnimator != null || swipeBackInProgress) {
            return false
        }
        if (fragmentHolders.any { it.fragment === fragment }) {
            return false
        }
        fragment.parentLayout = this
        val fragmentView = fragment.ensureView(context) ?: return false
        val previousHolder = fragmentHolders.lastOrNull()
        if (!isPaused) {
            previousHolder?.fragment?.setPaused(true)
        }
        previousHolder?.view?.visibility = VISIBLE

        val holder = FragmentHolder(fragment = fragment, view = fragmentView)
        fragmentHolders += holder
        addView(
            fragmentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        notifyStackChanged()
        notifyVisibleFragmentChanged(fragment)

        if (previousHolder == null || !animated) {
            previousHolder?.view?.visibility = GONE
            if (!isPaused) {
                fragment.setPaused(false)
            }
            fragment.onBecomeFullyVisible()
            return true
        }

        startOpenAnimation(previousHolder, holder)
        return true
    }

    override fun closeLastFragment(animated: Boolean): Boolean {
        if (transitionAnimator != null || swipeBackInProgress) {
            return false
        }
        if (fragmentHolders.size <= 1) {
            return false
        }
        val currentHolder = fragmentHolders.last()
        val previousHolder = fragmentHolders[fragmentHolders.lastIndex - 1]
        if (!isPaused) {
            currentHolder.fragment.setPaused(true)
            previousHolder.fragment.setPaused(false)
        }
        previousHolder.view.visibility = VISIBLE

        if (!animated) {
            finishClose(currentHolder, previousHolder)
            return true
        }

        previousHolder.view.translationX = backgroundTranslationOffset()
        notifyVisibleFragmentChanged(previousHolder.fragment)
        startCloseAnimation(
            currentHolder = currentHolder,
            previousHolder = previousHolder,
            startCurrentTranslation = currentHolder.view.translationX,
            startPreviousTranslation = previousHolder.view.translationX,
        )
        return true
    }

    override fun getFragmentStack(): List<BaseFragment> {
        return fragmentHolders.map { it.fragment }
    }

    override fun addFragmentToStack(fragment: BaseFragment, position: Int): Boolean {
        if (position !in 0..fragmentHolders.size || fragmentHolders.any { it.fragment === fragment }) {
            return false
        }
        if (position == fragmentHolders.size) {
            return presentFragment(fragment, animated = false)
        }
        fragment.parentLayout = this
        val fragmentView = fragment.ensureView(context) ?: return false
        val holder = FragmentHolder(fragment = fragment, view = fragmentView)
        fragmentHolders.add(position, holder)
        addView(
            fragmentView,
            position,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        fragmentView.visibility = GONE
        notifyStackChanged()
        return true
    }

    override fun removeFragmentFromStack(fragment: BaseFragment): Boolean {
        if (transitionAnimator != null || swipeBackInProgress) {
            return false
        }
        val holderIndex = fragmentHolders.indexOfFirst { it.fragment === fragment }
        if (holderIndex == -1) {
            return false
        }
        if (holderIndex == fragmentHolders.lastIndex) {
            return closeLastFragment(animated = false)
        }
        val holder = fragmentHolders.removeAt(holderIndex)
        removeView(holder.view)
        holder.fragment.destroyFragment()
        notifyStackChanged()
        return true
    }

    override fun setDelegate(delegate: INavigationLayoutDelegate?) {
        this.delegate = delegate
        if (delegate != null) {
            delegate.onFragmentStackChanged(getFragmentStack())
            delegate.onVisibleFragmentChanged(lastVisibleFragment ?: fragmentHolders.lastOrNull()?.fragment)
        }
    }

    override fun onBackPressed(): Boolean {
        if (transitionAnimator != null || swipeBackInProgress) {
            return true
        }
        val topFragment = fragmentHolders.lastOrNull()?.fragment ?: return false
        if (!topFragment.onBackPressed()) {
            return true
        }
        return closeLastFragment(animated = true)
    }

    override fun onResume() {
        isPaused = false
        fragmentHolders.lastOrNull()?.fragment?.setPaused(false)
    }

    override fun onPause() {
        fragmentHolders.lastOrNull()?.fragment?.setPaused(true)
        isPaused = true
    }

    fun saveNavigationState(): Bundle? {
        if (fragmentHolders.isEmpty()) {
            return null
        }
        val entries = ArrayList<Bundle>(fragmentHolders.size)
        fragmentHolders.forEach { holder ->
            val navigationKey = holder.fragment.getNavigationKey() ?: return null
            entries += Bundle().apply {
                putString(StateEntryNavigationKey, navigationKey)
                putBundle(StateEntryArguments, Bundle(holder.fragment.saveArguments()))
            }
        }
        return Bundle().apply {
            putParcelableArrayList(StateEntriesKey, entries)
        }
    }

    fun restoreNavigationState(
        state: Bundle,
        fragmentFactory: (String, Bundle?) -> BaseFragment?,
    ): Boolean {
        if (fragmentHolders.isNotEmpty() || transitionAnimator != null || swipeBackInProgress) {
            return false
        }
        val entries = state.getParcelableArrayList<Bundle>(StateEntriesKey) ?: return false
        if (entries.isEmpty()) {
            return false
        }
        val fragments = entries.map { entry ->
            val navigationKey = entry.getString(StateEntryNavigationKey) ?: return false
            fragmentFactory(navigationKey, entry.getBundle(StateEntryArguments)) ?: return false
        }
        fragments.forEach { fragment ->
            if (!presentFragment(fragment, animated = false)) {
                return false
            }
        }
        return true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return handleSwipeBackTouch(event) || super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleSwipeBackTouch(event) || super.onTouchEvent(event)
    }

    private fun finishClose(
        currentHolder: FragmentHolder,
        previousHolder: FragmentHolder,
    ) {
        fragmentHolders.remove(currentHolder)
        removeView(currentHolder.view)
        currentHolder.fragment.destroyFragment()
        previousHolder.view.visibility = VISIBLE
        notifyVisibleFragmentChanged(previousHolder.fragment)
        if (!isPaused) {
            previousHolder.fragment.setPaused(false)
        }
        previousHolder.fragment.onBecomeFullyVisible()
        notifyStackChanged()
    }

    private fun notifyStackChanged() {
        delegate?.onFragmentStackChanged(getFragmentStack())
    }

    private fun notifyVisibleFragmentChanged(fragment: BaseFragment?) {
        if (lastVisibleFragment === fragment) {
            return
        }
        lastVisibleFragment = fragment
        delegate?.onVisibleFragmentChanged(fragment)
    }

    private fun startOpenAnimation(
        previousHolder: FragmentHolder,
        currentHolder: FragmentHolder,
    ) {
        val width = resolveContainerWidth()
        val previousTranslation = backgroundTranslationOffset(width)
        currentHolder.view.translationX = width
        previousHolder.view.translationX = 0f
        notifyTransitionStart(previousHolder.fragment, currentHolder.fragment, backward = false)
        transitionAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(currentHolder.view, View.TRANSLATION_X, width, 0f),
                ObjectAnimator.ofFloat(previousHolder.view, View.TRANSLATION_X, 0f, previousTranslation),
            )
            duration = TransitionDurationMs
            interpolator = CubicBezierInterpolator.EASE_OUT
            addListener(
                object : AnimatorListenerAdapter() {
                    private var cancelled: Boolean = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                        transitionAnimator = null
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (cancelled) {
                            return
                        }
                        transitionAnimator = null
                        previousHolder.view.visibility = GONE
                        previousHolder.view.translationX = 0f
                        currentHolder.view.translationX = 0f
                        notifyTransitionEnd(previousHolder.fragment, currentHolder.fragment, backward = false)
                        if (!isPaused) {
                            currentHolder.fragment.setPaused(false)
                        }
                        currentHolder.fragment.onBecomeFullyVisible()
                    }
                },
            )
        }
        transitionAnimator?.start()
    }

    private fun startCloseAnimation(
        currentHolder: FragmentHolder,
        previousHolder: FragmentHolder,
        startCurrentTranslation: Float,
        startPreviousTranslation: Float,
    ) {
        val width = resolveContainerWidth()
        val currentTranslation = startCurrentTranslation.coerceIn(0f, width)
        previousHolder.view.visibility = VISIBLE
        notifyTransitionStart(currentHolder.fragment, previousHolder.fragment, backward = true)
        transitionAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(currentHolder.view, View.TRANSLATION_X, currentTranslation, width),
                ObjectAnimator.ofFloat(previousHolder.view, View.TRANSLATION_X, startPreviousTranslation, 0f),
            )
            duration = resolveTransitionDuration(width - currentTranslation, width)
            interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
            addListener(
                object : AnimatorListenerAdapter() {
                    private var cancelled: Boolean = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                        transitionAnimator = null
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (cancelled) {
                            return
                        }
                        transitionAnimator = null
                        swipeBackInProgress = false
                        currentHolder.view.translationX = 0f
                        previousHolder.view.translationX = 0f
                        currentHolder.fragment.onSlideProgress(1f)
                        previousHolder.fragment.onSlideProgress(1f)
                        finishClose(currentHolder, previousHolder)
                        notifyTransitionEnd(currentHolder.fragment, previousHolder.fragment, backward = true)
                    }
                },
            )
        }
        transitionAnimator?.start()
    }

    private fun startSwipeCancelAnimation(
        currentHolder: FragmentHolder,
        previousHolder: FragmentHolder,
    ) {
        val width = resolveContainerWidth()
        val currentTranslation = currentHolder.view.translationX.coerceIn(0f, width)
        transitionAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(currentHolder.view, View.TRANSLATION_X, currentTranslation, 0f),
                ObjectAnimator.ofFloat(
                    previousHolder.view,
                    View.TRANSLATION_X,
                    previousHolder.view.translationX,
                    backgroundTranslationOffset(width),
                ),
            )
            duration = resolveTransitionDuration(currentTranslation, width)
            interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
            addListener(
                object : AnimatorListenerAdapter() {
                    private var cancelled: Boolean = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                        transitionAnimator = null
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (cancelled) {
                            return
                        }
                        transitionAnimator = null
                        swipeBackInProgress = false
                        currentHolder.view.translationX = 0f
                        previousHolder.view.translationX = 0f
                        previousHolder.view.visibility = GONE
                        if (!isPaused) {
                            previousHolder.fragment.setPaused(true)
                        }
                        currentHolder.fragment.onSlideProgress(0f)
                        previousHolder.fragment.onSlideProgress(0f)
                        lastVisibleFragment = currentHolder.fragment
                        delegate?.onVisibleFragmentChanged(currentHolder.fragment)
                    }
                },
            )
        }
        transitionAnimator?.start()
    }

    private fun handleSwipeBackTouch(event: MotionEvent): Boolean {
        if (transitionAnimator != null || fragmentHolders.size <= 1) {
            return false
        }
        val topFragment = fragmentHolders.lastOrNull()?.fragment ?: return false
        if (!topFragment.isSwipeBackEnabled()) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                maybeStartTracking = event.x <= AndroidUtilities.dp(32)
                swipeBackInProgress = false
                startedTrackingPointerId = event.getPointerId(0)
                startedTrackingX = event.x
                startedTrackingY = event.y
                recycleVelocityTracker()
                velocityTracker = VelocityTracker.obtain().also { tracker ->
                    tracker.addMovement(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(startedTrackingPointerId)
                if (pointerIndex < 0) {
                    return swipeBackInProgress
                }
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                velocityTracker?.addMovement(event)
                if (maybeStartTracking && !swipeBackInProgress) {
                    val dx = x - startedTrackingX
                    val dy = abs(y - startedTrackingY)
                    if (dx >= resolveSwipeStartThresholdPx() && dx / 3f > dy) {
                        startedTrackingX = x
                        startedTrackingY = y
                        prepareForSwipeBack()
                        return true
                    }
                }
                if (swipeBackInProgress) {
                    updateSwipeBack((x - startedTrackingX).coerceAtLeast(0f))
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) != startedTrackingPointerId) {
                    return swipeBackInProgress
                }
                velocityTracker?.addMovement(event)
                finishSwipeBackGesture(cancelled = false)
                return swipeBackInProgress
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                finishSwipeBackGesture(cancelled = true)
                return swipeBackInProgress
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                finishSwipeBackGesture(cancelled = false)
                return swipeBackInProgress
            }
        }
        return swipeBackInProgress
    }

    private fun prepareForSwipeBack() {
        maybeStartTracking = false
        swipeBackInProgress = true
        val previousHolder = fragmentHolders[fragmentHolders.lastIndex - 1]
        previousHolder.view.visibility = VISIBLE
        previousHolder.view.translationX = backgroundTranslationOffset()
        AndroidUtilities.hideKeyboard(findFocus())
        if (!isPaused) {
            previousHolder.fragment.setPaused(false)
        }
        notifyVisibleFragmentChanged(previousHolder.fragment)
        previousHolder.view.bringToFront()
        fragmentHolders.last().view.bringToFront()
        updateSwipeBack(0f)
    }

    private fun updateSwipeBack(translationX: Float) {
        val currentHolder = fragmentHolders.last()
        val previousHolder = fragmentHolders[fragmentHolders.lastIndex - 1]
        val width = resolveContainerWidth()
        val clampedTranslation = translationX.coerceIn(0f, width)
        val progress = (clampedTranslation / width).coerceIn(0f, 1f)
        currentHolder.view.translationX = clampedTranslation
        previousHolder.view.translationX = backgroundTranslationOffset(width) * (1f - progress)
        currentHolder.fragment.onSlideProgress(progress)
        previousHolder.fragment.onSlideProgress(progress)
    }

    private fun finishSwipeBackGesture(cancelled: Boolean) {
        val wasTracking = swipeBackInProgress
        maybeStartTracking = false
        if (!wasTracking) {
            recycleVelocityTracker()
            return
        }
        val currentHolder = fragmentHolders.last()
        val previousHolder = fragmentHolders[fragmentHolders.lastIndex - 1]
        velocityTracker?.computeCurrentVelocity(1000)
        val velocityX = velocityTracker?.xVelocity ?: 0f
        val velocityY = velocityTracker?.yVelocity ?: 0f
        recycleVelocityTracker()
        val shouldDismiss = !cancelled && (
            currentHolder.view.translationX >= resolveContainerWidth() * SwipeDismissThreshold ||
                (velocityX > AndroidUtilities.dpf2(SwipeVelocityThresholdDpPerSecond) && velocityX > abs(velocityY))
            )
        if (shouldDismiss) {
            if (!isPaused) {
                currentHolder.fragment.setPaused(true)
            }
            startCloseAnimation(
                currentHolder = currentHolder,
                previousHolder = previousHolder,
                startCurrentTranslation = currentHolder.view.translationX,
                startPreviousTranslation = previousHolder.view.translationX,
            )
        } else {
            startSwipeCancelAnimation(currentHolder, previousHolder)
        }
    }

    private fun notifyTransitionStart(
        from: BaseFragment?,
        to: BaseFragment,
        backward: Boolean,
    ) {
        from?.onTransitionAnimationStart(isOpen = false, backward = backward)
        to.onTransitionAnimationStart(isOpen = true, backward = backward)
    }

    private fun notifyTransitionEnd(
        from: BaseFragment?,
        to: BaseFragment,
        backward: Boolean,
    ) {
        from?.onTransitionAnimationEnd(isOpen = false, backward = backward)
        to.onTransitionAnimationEnd(isOpen = true, backward = backward)
    }

    private fun resolveContainerWidth(): Float {
        return width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
    }

    private fun backgroundTranslationOffset(width: Float = resolveContainerWidth()): Float {
        return -min(width * BackgroundShiftFactor, AndroidUtilities.dpf2(48f))
    }

    private fun resolveTransitionDuration(
        distance: Float,
        width: Float,
    ): Long {
        val progress = (distance / width).coerceIn(0.2f, 1f)
        return (TransitionDurationMs * progress).roundToLong()
    }

    private fun resolveSwipeStartThresholdPx(): Float {
        return maxOf(touchSlop.toFloat(), (SwipeStartThresholdCm / 2.54f) * resources.displayMetrics.xdpi)
    }

    private fun drawNavigationDepth(canvas: Canvas) {
        if (fragmentHolders.size <= 1) {
            return
        }
        val currentHolder = fragmentHolders.last()
        val previousHolder = fragmentHolders[fragmentHolders.lastIndex - 1]
        if (previousHolder.view.visibility != VISIBLE) {
            return
        }
        val width = resolveContainerWidth()
        val translationX = currentHolder.view.translationX.coerceIn(0f, width)
        if (translationX <= 0f) {
            return
        }

        val scrimOpacity = ((width - translationX) / width).coerceIn(0f, 0.8f)
        backScrimPaint.color = AndroidUtilities.argb(
            alpha = (BackScrimMaxAlpha * scrimOpacity).toInt(),
            red = 0,
            green = 0,
            blue = 0,
        )
        canvas.drawRect(
            0f,
            paddingTop.toFloat(),
            translationX,
            (height - paddingBottom).toFloat(),
            backScrimPaint,
        )

        val shadowLeft = (translationX - edgeShadowWidth).coerceAtLeast(0f)
        edgeShadowMatrix.reset()
        edgeShadowMatrix.postTranslate(shadowLeft, 0f)
        edgeShadowGradient.setLocalMatrix(edgeShadowMatrix)
        canvas.drawRect(
            shadowLeft,
            paddingTop.toFloat(),
            translationX,
            (height - paddingBottom).toFloat(),
            edgeShadowPaint,
        )
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private data class FragmentHolder(
        val fragment: BaseFragment,
        val view: View,
    )
}

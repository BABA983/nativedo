/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.ui.ActionBar.BaseFragment.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BaseFragment.java
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

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

abstract class BaseFragment {
    var parentLayout: ActionBarLayout? = null
        internal set

    var actionBar: ActionBar? = null
        protected set

    val pageScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var fragmentView: View? = null
    private var lifecycleCreated: Boolean = false
    private var paused: Boolean = true

    abstract fun createView(context: Context): View

    open fun onFragmentCreate(): Boolean = true

    open fun onFragmentDestroy() {
        pageScope.cancel()
    }

    open fun onResume() = Unit

    open fun onPause() = Unit

    open fun onBackPressed(): Boolean = true

    open fun isSwipeBackEnabled(): Boolean = true

    open fun onSlideProgress(progress: Float) = Unit

    open fun onTransitionAnimationStart(isOpen: Boolean, backward: Boolean) = Unit

    open fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) = Unit

    open fun onBecomeFullyVisible() = Unit

    open fun getNavigationBarColor(): Int? = null

    open fun hasForceLightStatusBar(): Boolean = false

    open fun getNavigationKey(): String? = null

    open fun saveArguments(): Bundle = Bundle()

    protected open fun onCreateActionBar(context: Context): ActionBar? = null

    internal fun ensureView(context: Context): View? {
        if (fragmentView != null) {
            return fragmentView
        }
        if (!lifecycleCreated) {
            lifecycleCreated = onFragmentCreate()
        }
        if (!lifecycleCreated) {
            return null
        }
        actionBar = onCreateActionBar(context)?.also { createdActionBar ->
            createdActionBar.setOnBackClickListener {
                parentLayout?.closeLastFragment(animated = true)
            }
        }
        val contentView = createView(context)
        fragmentView = wrapWithActionBar(
            context = context,
            contentView = contentView,
            actionBar = actionBar,
        )
        return fragmentView
    }

    internal fun destroyFragment() {
        onFragmentDestroy()
        parentLayout = null
        actionBar = null
        fragmentView = null
        paused = true
    }

    internal fun setPaused(value: Boolean) {
        if (paused == value) {
            return
        }
        paused = value
        if (value) {
            onPause()
        } else {
            onResume()
        }
    }

    fun isPaused(): Boolean = paused

    private fun wrapWithActionBar(
        context: Context,
        contentView: View,
        actionBar: ActionBar?,
    ): View {
        if (actionBar == null) {
            return contentView
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                actionBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                contentView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
        }
    }
}

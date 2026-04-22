/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.ui.ActionBar.ActionBar.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java
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
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.baba983.nativedo.core.event.NotificationCenter
import io.github.baba983.nativedo.core.event.NotificationCenterDelegate
import io.github.baba983.nativedo.core.theme.NdTheme
import io.github.baba983.nativedo.core.theme.NdUiConfig
import io.github.baba983.nativedo.core.util.AndroidUtilities
import kotlin.math.max

class ActionBar(
    context: Context,
) : FrameLayout(context), NotificationCenterDelegate {
    private val defaultBackDrawable = BackDrawable(false)
    private var backButtonDrawable: Drawable? = defaultBackDrawable
    private var onBackClickListener: (() -> Unit)? = null

    private val backButtonImageView = ImageView(context)
    private val titleTextView = SimpleTextView(context)
    private val subtitleTextView = SimpleTextView(context)
    private val menuView = FrameLayout(context)
    private val shadowView = View(context)

    init {
        background = NdTheme.getThemedDrawable(NdTheme.key_actionBarDefault)
        minimumHeight = dp(56f)
        clipChildren = false
        clipToPadding = false

        createBackButtonImage()
        createTitleTextView()
        createSubtitleTextView()

        addView(menuView)
        addView(shadowView)
        applyTheme()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotificationCenter.addObserver(this, NotificationCenter.themeDidChange)
        applyTheme()
    }

    override fun onDetachedFromWindow() {
        NotificationCenter.removeObserver(this, NotificationCenter.themeDidChange)
        super.onDetachedFromWindow()
    }

    override fun didReceivedNotification(id: Int, vararg args: Any?) {
        if (id == NotificationCenter.themeDidChange) {
            applyTheme()
        }
    }

    fun setTitle(title: CharSequence?) {
        titleTextView.text = title ?: ""
        requestLayout()
    }

    fun setSubtitle(subtitle: CharSequence?) {
        subtitleTextView.text = subtitle ?: ""
        subtitleTextView.visibility = if (subtitle.isNullOrBlank()) GONE else VISIBLE
        requestLayout()
    }

    fun setBackButtonVisible(visible: Boolean) {
        backButtonImageView.visibility = if (visible && backButtonDrawable != null) VISIBLE else GONE
        requestLayout()
    }

    fun setOnBackClickListener(listener: (() -> Unit)?) {
        onBackClickListener = listener
    }

    fun setBackButtonDrawable(drawable: Drawable?) {
        backButtonDrawable = drawable
        backButtonImageView.setImageDrawable(drawable)
        if (drawable == null) {
            backButtonImageView.visibility = GONE
        }
        applyTheme()
        requestLayout()
    }

    fun createMenu(): ViewGroup = menuView

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val actionBarHeight = dp(56f)
        val menuHeightSpec = MeasureSpec.makeMeasureSpec(actionBarHeight, MeasureSpec.EXACTLY)

        backButtonImageView.measure(
            MeasureSpec.makeMeasureSpec(dp(54f), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(54f), MeasureSpec.EXACTLY),
        )
        menuView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
            menuHeightSpec,
        )

        val leftInset = if (backButtonImageView.visibility == VISIBLE) dp(72f) else dp(18f)
        val rightInset = menuView.measuredWidth + dp(16f)
        val availableWidth = max(0, width - leftInset - rightInset)
        val titleWidthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST)

        titleTextView.measure(
            titleWidthSpec,
            MeasureSpec.makeMeasureSpec(actionBarHeight, MeasureSpec.AT_MOST),
        )
        if (subtitleTextView.visibility != GONE) {
            subtitleTextView.measure(
                titleWidthSpec,
                MeasureSpec.makeMeasureSpec(actionBarHeight, MeasureSpec.AT_MOST),
            )
        } else {
            subtitleTextView.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
            )
        }
        shadowView.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(3f), MeasureSpec.EXACTLY),
        )

        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(actionBarHeight, heightMeasureSpec),
        )
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val width = right - left
        val height = bottom - top

        val backTop = (height - backButtonImageView.measuredHeight) / 2
        backButtonImageView.layout(
            0,
            backTop,
            backButtonImageView.measuredWidth,
            backTop + backButtonImageView.measuredHeight,
        )

        val menuLeft = width - menuView.measuredWidth
        menuView.layout(
            menuLeft,
            0,
            width,
            menuView.measuredHeight,
        )

        val textLeft = if (backButtonImageView.visibility == VISIBLE) dp(72f) else dp(18f)
        if (subtitleTextView.visibility == GONE) {
            val titleTop = (height - titleTextView.measuredHeight) / 2
            titleTextView.layout(
                textLeft,
                titleTop,
                textLeft + titleTextView.measuredWidth,
                titleTop + titleTextView.measuredHeight,
            )
        } else {
            val totalTextHeight = titleTextView.measuredHeight + subtitleTextView.measuredHeight
            val titleTop = (height - totalTextHeight) / 2
            titleTextView.layout(
                textLeft,
                titleTop,
                textLeft + titleTextView.measuredWidth,
                titleTop + titleTextView.measuredHeight,
            )
            val subtitleTop = titleTop + titleTextView.measuredHeight
            subtitleTextView.layout(
                textLeft,
                subtitleTop,
                textLeft + subtitleTextView.measuredWidth,
                subtitleTop + subtitleTextView.measuredHeight,
            )
        }

        shadowView.layout(
            0,
            height - shadowView.measuredHeight,
            width,
            height,
        )
    }

    private fun createBackButtonImage() {
        backButtonImageView.apply {
            scaleType = ImageView.ScaleType.CENTER
            visibility = GONE
            background = NdTheme.createSelectorDrawable(NdTheme.key_actionBarDefaultSelector)
            setImageDrawable(defaultBackDrawable)
            contentDescription = "Back"
            setOnClickListener { onBackClickListener?.invoke() }
        }
        addView(backButtonImageView)
    }

    private fun createTitleTextView() {
        titleTextView.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSizeSp(20f)
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(0, dp(8f), 0, dp(8f))
        }
        addView(titleTextView)
    }

    private fun createSubtitleTextView() {
        subtitleTextView.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = GONE
            setTextSizeSp(14f)
        }
        addView(subtitleTextView)
    }

    private fun applyTheme() {
        titleTextView.setTextColor(NdTheme.getColor(NdTheme.key_actionBarDefaultTitle))
        subtitleTextView.setTextColor(NdTheme.getColor(NdTheme.key_actionBarDefaultSubtitle))
        val iconColor = NdTheme.getColor(NdTheme.key_actionBarDefaultIcon)
        (backButtonDrawable as? BackDrawable)?.apply {
            setColor(iconColor)
            setRotatedColor(iconColor)
        }
        shadowView.background = NdTheme.createHeaderShadowDrawable()
        shadowView.visibility = if (NdUiConfig.drawActionBarShadow) VISIBLE else GONE
        invalidate()
        backButtonImageView.invalidate()
        titleTextView.invalidate()
        subtitleTextView.invalidate()
        shadowView.invalidate()
    }

    private fun dp(value: Float): Int = AndroidUtilities.dp(value)
}

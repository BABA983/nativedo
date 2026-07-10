package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.isAnimatedDiscourseImageUrl
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.feature.topic.ui.PollOptionRef
import io.github.baba983.nativedo.feature.topic.ui.TopicPollVotersSheetUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicPollVotersUiState
import io.github.baba983.nativedo.ui.topic.cell.TopicFooterCell
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import kotlin.math.max
import kotlin.math.min

internal class TopicPollVotersSheet(
    context: Context,
    private val fragment: BaseFragment,
    private val baseUrl: String,
    private val onOptionSelected: (String) -> Unit,
    private val onUserSelected: (String) -> Unit,
    private val onDismissed: () -> Unit,
) : BottomSheet(context, false, null) {
    private val adapter = Adapter()
    private val recyclerListView = RecyclerListView(context)
    private val fixedHeaderView = PollFixedHeaderView(context, backgroundPaddingLeft, onOptionSelected)
    private val rect = RectF()
    private val sizePolicy = StableUserListSheetSizePolicy(AndroidUtilities.dp(56f))
    private var scrollOffsetY = 0
    private var ignoreLayout = false
    private var fullHeight = false
    private var contentRowsHeight = sizePolicy.rowsHeight()
    private var statusBarOpen: Boolean? = null
    private var lastSelectedOptionId: String? = null

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
                        val diff = min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop)
                        top -= diff
                        radProgress = 1f - min(1f, diff * 2f / AndroidUtilities.statusBarHeight)
                    }
                    if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                        statusBarHeight = min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop)
                    }
                }
                shadowDrawable.setBounds(0, top, measuredWidth, measuredHeight)
                shadowDrawable.draw(canvas)
                if (radProgress != 1f) {
                    Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_dialogBackground)
                    rect.set(
                        backgroundPaddingLeft.toFloat(),
                        (backgroundPaddingTop + top).toFloat(),
                        (measuredWidth - backgroundPaddingLeft).toFloat(),
                        (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat(),
                    )
                    val radius = AndroidUtilities.dp(12f) * radProgress
                    canvas.drawRoundRect(rect, radius, radius, Theme.dialogs_onlineCirclePaint)
                }
                val handleAlpha = max(0f, min(1f, (y - AndroidUtilities.statusBarHeight).toFloat() / AndroidUtilities.dp(16f)))
                if (handleAlpha > 0f) {
                    val width = AndroidUtilities.dp(36f)
                    rect.set(
                        (measuredWidth - width) / 2f,
                        y.toFloat(),
                        (measuredWidth + width) / 2f,
                        (y + AndroidUtilities.dp(4f)).toFloat(),
                    )
                    Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_sheet_scrollUp)
                    Theme.dialogs_onlineCirclePaint.alpha = (Theme.dialogs_onlineCirclePaint.alpha * handleAlpha).toInt()
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), Theme.dialogs_onlineCirclePaint)
                }
                if (statusBarHeight > 0) {
                    Theme.dialogs_onlineCirclePaint.color = Theme.getColor(Theme.key_dialogBackground)
                    canvas.drawRect(
                        backgroundPaddingLeft.toFloat(),
                        (AndroidUtilities.statusBarHeight - statusBarHeight).toFloat(),
                        (measuredWidth - backgroundPaddingLeft).toFloat(),
                        AndroidUtilities.statusBarHeight.toFloat(),
                        Theme.dialogs_onlineCirclePaint,
                    )
                }
                updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2)
            }
        }
        containerView.setWillNotDraw(false)
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground))
        adapter.configure(baseUrl, onUserSelected)
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

    fun submit(sheetState: TopicPollVotersSheetUiState, votersState: TopicPollVotersUiState) {
        val tabOptions = buildTabOptions(sheetState, votersState)
        val selected = votersState.selectedOptionId ?: tabOptions.firstOrNull()?.id
        val selectedGroup = votersState.groups.firstOrNull { group -> group.id == selected }
        val selectedUsers = selectedGroup?.users.orEmpty()
        val selectedId = selected
        val predictedUserCount = tabOptions.firstOrNull { option -> option.id == selectedId }?.votes
        val selectedChanged = lastSelectedOptionId != selectedId
        lastSelectedOptionId = selectedId
        fixedHeaderView.bind(
            title = sheetState.pollTitle?.takeIf { it.isNotBlank() } ?: "投票结果",
            options = tabOptions,
            selectedId = selectedId,
        )
        contentRowsHeight = sizePolicy.rowsHeight(
            userCount = if (votersState.isLoading) predictedUserCount else selectedUsers.size,
        )
        adapter.submit(votersState, selectedUsers, sizePolicy.skeletonRows(predictedUserCount))
        containerView.requestLayout()
        if (selectedChanged) {
            recyclerListView.post { recyclerListView.scrollToPosition(0) }
        }
    }

    override fun canDismissWithSwipe(): Boolean = false

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
        data class Loading(val rowCount: Int) : Row
        data class Empty(val message: String) : Row
        data class User(val user: PostReactionUser, val divider: Boolean) : Row
    }

    private class Adapter : RecyclerListView.SelectionAdapter() {
        private var rows: List<Row> = emptyList()
        private var baseUrl: String = ""
        private var onUserSelected: (String) -> Unit = {}
        private var topSpacerHeight: Int = AndroidUtilities.dp(140f)

        fun configure(baseUrl: String, onUserSelected: (String) -> Unit) {
            this.baseUrl = baseUrl
            this.onUserSelected = onUserSelected
        }

        fun setTopSpacerHeight(height: Int) {
            if (topSpacerHeight == height) return
            topSpacerHeight = height
            rows = rows.map { row -> if (row is Row.TopSpacer) Row.TopSpacer(height) else row }
            notifyDataSetChanged()
        }

        fun submit(
            votersState: TopicPollVotersUiState,
            selectedUsers: List<PostReactionUser>,
            loadingRowCount: Int,
        ) {
            rows = buildList {
                if (topSpacerHeight > 0) {
                    add(Row.TopSpacer(topSpacerHeight))
                }
                if (votersState.isLoading) {
                    add(Row.Loading(loadingRowCount))
                } else if (selectedUsers.isEmpty()) {
                    add(Row.Empty(votersState.errorMessage ?: "暂无投票用户"))
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
                is Row.TopSpacer -> VIEW_TOP_SPACER
                is Row.Loading -> VIEW_LOADING
                is Row.Empty -> VIEW_EMPTY
                is Row.User -> VIEW_USER
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = when (viewType) {
                VIEW_TOP_SPACER -> TopSpacerView(parent.context)
                VIEW_LOADING -> PollUsersSkeletonView(parent.context)
                VIEW_USER -> PollUserRowView(parent.context, baseUrl)
                else -> TopicFooterCell(parent.context)
            }
            return RecyclerListView.Holder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.TopSpacer -> (holder.itemView as TopSpacerView).setHeight(row.height)
                is Row.Loading -> (holder.itemView as PollUsersSkeletonView).setRowCount(row.rowCount)
                is Row.Empty -> (holder.itemView as TopicFooterCell).bind(row.message, null)
                is Row.User -> (holder.itemView as PollUserRowView).bind(row.user, row.divider, onUserSelected)
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

    private class PollUsersSkeletonView(context: Context) : View(context) {
        private val rect = RectF()
        private val clipRect = Rect()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rowHeight = AndroidUtilities.dp(56f)
        private var rowCount = 5
        private val avatarSize = AndroidUtilities.dp(36f)
        private val horizontalInset = AndroidUtilities.dp(18f)

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
            paint.color = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.12f)
            canvas.getClipBounds(clipRect)
            val firstRow = (clipRect.top / rowHeight).coerceIn(0, rowCount)
            val lastRow = ((clipRect.bottom + rowHeight - 1) / rowHeight).coerceIn(firstRow, rowCount)
            for (index in firstRow until lastRow) {
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

    private class PollFixedHeaderView(
        context: Context,
        private val horizontalInset: Int,
        private val onOptionSelected: (String) -> Unit,
    ) : FrameLayout(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val backgroundRect = RectF()
        private val titleView = TextView(context).apply {
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            textSize = 20f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setPadding(AndroidUtilities.dp(18f), AndroidUtilities.dp(6f), AndroidUtilities.dp(18f), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        private val tabsView = PollOptionTabsView(context, onOptionSelected)
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

        fun bind(title: String, options: List<PollOptionRef>, selectedId: String?) {
            titleView.text = title
            tabsView.bind(options, selectedId)
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

    private class PollOptionTabsView(
        context: Context,
        private val onOptionSelected: (String) -> Unit,
    ) : FrameLayout(context) {
        private val chips = mutableListOf<PollOptionTabChipView>()

        init {
            setPadding(AndroidUtilities.dp(12f), AndroidUtilities.dp(10f), AndroidUtilities.dp(12f), AndroidUtilities.dp(8f))
        }

        fun bind(options: List<PollOptionRef>, selectedId: String?) {
            removeAllViews()
            chips.clear()
            options.forEach { option ->
                val chip = PollOptionTabChipView(context).apply {
                    bind(option, selected = option.id == selectedId)
                    setOnClickListener { onOptionSelected(option.id) }
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

    private class PollOptionTabChipView(context: Context) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(13f).toFloat()
        }
        private val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(13f).toFloat()
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        private var label = ""
        private var count = 0
        private var selected = false

        fun bind(option: PollOptionRef, selected: Boolean) {
            label = option.label.trim()
            count = option.votes
            this.selected = selected
            requestLayout()
            invalidate()
        }

        private fun ellipsizedLabel(maxWidth: Float): String {
            if (maxWidth <= 0f) {
                return label
            }
            val ellipsized = TextUtils.ellipsize(label, labelPaint, maxWidth, TextUtils.TruncateAt.END)
            return ellipsized?.toString() ?: label
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            countPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            labelPaint.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            val countText = count.toString()
            val countWidth = countPaint.measureText(countText)
            val maxLabelWidth = AndroidUtilities.dp(160f).toFloat()
            val labelWidth = min(labelPaint.measureText(ellipsizedLabel(maxLabelWidth)), maxLabelWidth)
            val desired = AndroidUtilities.dp(18f) + labelWidth + AndroidUtilities.dp(8f) + countWidth
            setMeasuredDimension(desired.toInt().coerceAtLeast(AndroidUtilities.dp(52f)), MeasureSpec.getSize(heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            val accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
            val secondaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
            backgroundPaint.color = if (selected) Theme.multAlpha(accentColor, 0.12f) else Theme.getColor(Theme.key_windowBackgroundGray)
            val radius = height / 2f
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, backgroundPaint)
            labelPaint.color = if (selected) accentColor else Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
            countPaint.color = if (selected) accentColor else secondaryColor
            val countText = count.toString()
            val countWidth = countPaint.measureText(countText)
            val maxLabelWidth = (width - AndroidUtilities.dp(18f) - AndroidUtilities.dp(8f) - countWidth).coerceAtLeast(0f)
            val labelX = AndroidUtilities.dp(12f).toFloat()
            val textY = height / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(ellipsizedLabel(maxLabelWidth), labelX, textY, labelPaint)
            canvas.drawText(countText, width - AndroidUtilities.dp(12f) - countWidth, textY, countPaint)
        }
    }

    private class PollUserRowView(
        context: Context,
        private val baseUrl: String,
    ) : FrameLayout(context) {
        private val avatarSize = AndroidUtilities.dp(36f)
        private val avatarDrawable = AvatarDrawable()
        private val avatarImage = ImageReceiver(this).apply {
            setRoundRadius(avatarSize / 2)
            setAllowLoadingOnAttachedOnly(true)
            setDelegate { _, _, _, _ -> invalidate() }
            setVisibleInvalidate { invalidate() }
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

        fun bind(user: PostReactionUser, showDivider: Boolean, onUserSelected: (String) -> Unit) {
            val name = user.displayName.ifBlank { user.username }
            nameView.text = name
            usernameView.text = if (user.username.isBlank()) "" else "@${user.username}"
            avatarDrawable.setInfo(user.username.hashCode().toLong(), name)
            val avatarUrl = resolveAvatarUrl(baseUrl, user.avatarTemplate, user.animatedAvatarUrl, 96)
            avatarImage.setAllowDecodeSingleFrame(!isAnimatedDiscourseImageUrl(avatarUrl))
            if (avatarUrl.isBlank()) {
                avatarImage.setImage(null, null, avatarDrawable, null, user, 0)
            } else {
                avatarImage.setImage(ImageLocation.getForPath(avatarUrl), avatarImageFilter(user.animatedAvatarUrl, 50), avatarDrawable, null, user, 0)
            }
            divider.visibility = if (showDivider) VISIBLE else GONE
            setOnClickListener {
                val username = user.username
                if (username.isNotBlank()) onUserSelected(username)
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

    private companion object {
        private const val VIEW_TOP_SPACER = 40
        private const val VIEW_LOADING = 41
        private const val VIEW_EMPTY = 42
        private const val VIEW_USER = 43

        private fun buildTabOptions(
            sheetState: TopicPollVotersSheetUiState,
            votersState: TopicPollVotersUiState,
        ): List<PollOptionRef> {
            val fromSheet = sheetState.options.filter { option -> option.id.isNotBlank() }
            if (fromSheet.isEmpty()) {
                return votersState.groups.map { group ->
                    PollOptionRef(id = group.id, label = group.id, votes = group.count)
                }
            }
            return fromSheet.map { option ->
                val group = votersState.groups.firstOrNull { it.id == option.id }
                option.copy(
                    votes = group?.count?.takeIf { it > 0 }
                        ?: group?.users?.size?.takeIf { it > 0 }
                        ?: option.votes,
                )
            }.filter { option ->
                option.votes > 0
                    || votersState.isLoading
                    || votersState.groups.any { group -> group.id == option.id && group.users.isNotEmpty() }
            }.ifEmpty { fromSheet }
        }
    }
}

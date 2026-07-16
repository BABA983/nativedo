package io.github.baba983.nativedo.ui.topic

import android.animation.ValueAnimator
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.ui.avatarImageFilter
import io.github.baba983.nativedo.core.ui.isAnimatedDiscourseImageUrl
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.feature.topic.ui.PollOptionRef
import io.github.baba983.nativedo.feature.topic.ui.TopicPollVotersSheetUiState
import io.github.baba983.nativedo.feature.topic.ui.TopicPollVotersUiState
import io.github.baba983.nativedo.ui.components.DiscourseUserCell
import io.github.baba983.nativedo.ui.components.FixedHeaderRecyclerBottomSheet
import io.github.baba983.nativedo.ui.topic.cell.TopicFooterCell
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.LoadingCell
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.RecyclerListView
import kotlin.math.min

internal class TopicPollVotersSheet(
    context: Context,
    fragment: BaseFragment,
    private val baseUrl: String,
    private val onOptionSelected: (String) -> Unit,
    private val onLoadMore: () -> Unit,
    private val onRetry: () -> Unit,
    private val onUserSelected: (String) -> Unit,
    private val onDismissed: () -> Unit,
) : FixedHeaderRecyclerBottomSheet(context, fragment) {
    private val sheetContext = context
    private val pager = ViewPager(context)
    private val pagerAdapter = PollPagesAdapter()
    private val pagesByOptionId = linkedMapOf<String, PollVotersPage>()
    private var optionIds: List<String> = emptyList()
    private val fixedHeaderView = PollFixedHeaderView(
        context,
        backgroundPaddingLeft,
        onOptionSelected = { optionId ->
            selectOption(optionId, smoothScroll = true, notifySelection = true)
        },
    )
    private val sizePolicy = StableUserListSheetSizePolicy(AndroidUtilities.dp(DiscourseUserCell.ITEM_HEIGHT_DP.toFloat()))
    private var contentHeightAnimator: ValueAnimator? = null
    private var lastSelectedOptionId: String? = null
    private var suppressPageSelectionCallback = false
    private var pagerScrollState = ViewPager.SCROLL_STATE_IDLE

    init {
        setOnDismissListener(DialogInterface.OnDismissListener { onDismissed() })
        pager.adapter = pagerAdapter
        pager.offscreenPageLimit = 1
        pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val fromId = optionIds.getOrNull(position) ?: return
                val fromPage = pagesByOptionId[fromId] ?: return
                val toId = optionIds.getOrNull(position + 1)
                val toPage = toId?.let(pagesByOptionId::get)
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
                val optionId = optionIds.getOrNull(position) ?: return
                lastSelectedOptionId = optionId
                if (!suppressPageSelectionCallback) {
                    onOptionSelected(optionId)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                pagerScrollState = state
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    val optionId = optionIds.getOrNull(pager.currentItem) ?: return
                    val page = pagesByOptionId[optionId] ?: return
                    setActiveRecyclerListView(page.listView)
                    setContentHeightPx(page.contentHeight)
                    fixedHeaderView.setSelectedOption(optionId)
                }
            }
        })
            setSheetContentView(pager)
            setFixedHeaderView(fixedHeaderView)
            setHeaderShadowDelegate { visible -> fixedHeaderView.setShadowVisible(visible) }
            setKeepSheetPositionOnScroll(true)
            setMinimumContentHeightPx(sizePolicy.minimumHeight())
        setContentHeightPx(sizePolicy.rowsHeight())
    }

    fun submit(sheetState: TopicPollVotersSheetUiState, votersState: TopicPollVotersUiState) {
        val tabOptions = buildTabOptions(sheetState, votersState)
        val selected = votersState.selectedOptionId ?: tabOptions.firstOrNull()?.id
        val selectedId = selected?.takeIf { candidate -> tabOptions.any { it.id == candidate } }
            ?: tabOptions.firstOrNull()?.id
        val selectedChanged = lastSelectedOptionId != selectedId
        val animateSelectionChange = lastSelectedOptionId != null && selectedChanged
        reconcilePages(tabOptions.map(PollOptionRef::id))
        fixedHeaderView.bind(
            title = sheetState.pollTitle?.takeIf { it.isNotBlank() } ?: "投票结果",
            options = tabOptions,
            selectedId = selectedId,
        )
        optionIds.forEach { optionId ->
            val page = pagesByOptionId.getValue(optionId)
            val optionState = votersState.optionPages[optionId]
            val users = votersState.groups.firstOrNull { group -> group.id == optionId }?.users.orEmpty()
            val predictedUserCount = tabOptions.firstOrNull { option -> option.id == optionId }?.votes
            val isInitialLoading = optionState?.let { it.isLoading && !it.hasLoaded } == true
            page.canLoadMore = optionState?.let { it.hasLoaded && !it.isLoading && !it.endReached } == true
            page.contentHeight = sizePolicy.rowsHeight(
                userCount = if (isInitialLoading) predictedUserCount else users.size,
            )
            page.adapter.submit(
                isLoading = isInitialLoading,
                isLoadingMore = optionState?.let { it.isLoading && it.hasLoaded } == true,
                errorMessage = optionState?.errorMessage,
                selectedUsers = users,
                loadingRowCount = sizePolicy.skeletonRows(predictedUserCount),
                onRetry = onRetry,
            )
        }
        val activeId = selectedId?.takeIf(optionIds::contains) ?: optionIds.firstOrNull() ?: return
        val activePage = pagesByOptionId.getValue(activeId)
        val selectedIndex = optionIds.indexOf(activeId)
        if (pager.currentItem != selectedIndex) {
            suppressPageSelectionCallback = true
            pager.setCurrentItem(selectedIndex, false)
            suppressPageSelectionCallback = false
        }
        lastSelectedOptionId = activeId
        if (pagerScrollState == ViewPager.SCROLL_STATE_IDLE) {
            setActiveRecyclerListView(activePage.listView)
            setContentHeight(activePage.contentHeight, animateSelectionChange)
        }
    }

    private fun reconcilePages(nextOptionIds: List<String>) {
        if (optionIds == nextOptionIds) return
        optionIds = nextOptionIds
        optionIds.forEach(::pageForOption)
        pagerAdapter.notifyDataSetChanged()
    }

    private fun pageForOption(optionId: String): PollVotersPage {
        return pagesByOptionId.getOrPut(optionId) {
            val listView = RecyclerListView(sheetContext).apply {
                itemAnimator = null
                layoutManager = LinearLayoutManager(sheetContext)
            }
            val pageAdapter = Adapter().apply {
                configure(baseUrl)
                setTopSpacerHeight(0)
            }
            val page = PollVotersPage(optionId, listView, pageAdapter, sizePolicy.minimumHeight())
            listView.adapter = pageAdapter
            listView.setOnItemClickListener(RecyclerListView.OnItemClickListener { _, position ->
                pageAdapter.usernameAt(position)?.let(onUserSelected)
            })
            listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || !page.canLoadMore || optionIds.getOrNull(pager.currentItem) != optionId) return
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (layoutManager.findLastVisibleItemPosition() >= pageAdapter.itemCount - 3) {
                        page.canLoadMore = false
                        onLoadMore()
                    }
                }
            })
            registerScrollableRecyclerListView(listView)
            page
        }
    }

    private fun selectOption(optionId: String, smoothScroll: Boolean, notifySelection: Boolean) {
        val position = optionIds.indexOf(optionId)
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

    private data class PollVotersPage(
        val optionId: String,
        val listView: RecyclerListView,
        val adapter: Adapter,
        var contentHeight: Int,
        var canLoadMore: Boolean = false,
    )

    private inner class PollPagesAdapter : PagerAdapter() {
        override fun getCount(): Int = optionIds.size

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === (`object` as PollVotersPage).listView
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val page = pageForOption(optionIds[position])
            (page.listView.parent as? ViewGroup)?.removeView(page.listView)
            container.addView(page.listView)
            return page
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView((`object` as PollVotersPage).listView)
        }

        override fun getItemPosition(`object`: Any): Int {
            val position = optionIds.indexOf((`object` as PollVotersPage).optionId)
            return if (position >= 0) position else POSITION_NONE
        }
    }

    private sealed interface Row {
        data class TopSpacer(val height: Int) : Row
        data class Loading(val rowCount: Int) : Row
        data class Empty(val message: String, val onRetry: (() -> Unit)?) : Row
        data class User(val user: PostReactionUser) : Row
        data object LoadingMore : Row
        data class LoadMoreError(val message: String, val onRetry: () -> Unit) : Row
    }

    private class Adapter : RecyclerListView.SelectionAdapter() {
        private var rows: List<Row> = emptyList()
        private var baseUrl: String = ""
        private var topSpacerHeight: Int = AndroidUtilities.dp(140f)

        fun configure(baseUrl: String) {
            this.baseUrl = baseUrl
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
            isLoading: Boolean,
            isLoadingMore: Boolean,
            errorMessage: String?,
            selectedUsers: List<PostReactionUser>,
            loadingRowCount: Int,
            onRetry: () -> Unit,
        ) {
            rows = buildList {
                if (topSpacerHeight > 0) {
                    add(Row.TopSpacer(topSpacerHeight))
                }
                if (isLoading) {
                    add(Row.Loading(loadingRowCount))
                } else if (selectedUsers.isEmpty()) {
                    add(Row.Empty(errorMessage ?: "暂无投票用户", errorMessage?.let { onRetry }))
                } else {
                    selectedUsers.forEach { user ->
                        add(Row.User(user))
                    }
                    when {
                        isLoadingMore -> add(Row.LoadingMore)
                        errorMessage != null -> add(Row.LoadMoreError(errorMessage, onRetry))
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
                Row.LoadingMore -> VIEW_LOADING_MORE
                is Row.LoadMoreError -> VIEW_EMPTY
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = when (viewType) {
                VIEW_TOP_SPACER -> TopSpacerView(parent.context)
                VIEW_LOADING -> PollUsersSkeletonView(parent.context)
                VIEW_USER -> DiscourseUserCell(parent.context, DiscourseUserCell.STYLE_POLL)
                VIEW_LOADING_MORE -> LoadingCell(parent.context)
                else -> TopicFooterCell(parent.context)
            }
            return RecyclerListView.Holder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.TopSpacer -> (holder.itemView as TopSpacerView).setHeight(row.height)
                is Row.Loading -> (holder.itemView as PollUsersSkeletonView).setRowCount(row.rowCount)
                is Row.Empty -> (holder.itemView as TopicFooterCell).bind(
                    row.message,
                    row.onRetry?.let { retry -> Runnable(retry) },
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
                Row.LoadingMore -> Unit
                is Row.LoadMoreError -> (holder.itemView as TopicFooterCell).bind(row.message, Runnable(row.onRetry))
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
        private val rowHeight = AndroidUtilities.dp(DiscourseUserCell.ITEM_HEIGHT_DP.toFloat())
        private var rowCount = 5
        private val avatarSize = AndroidUtilities.dp(36f)
        private val horizontalInset = AndroidUtilities.dp(8f)

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
                val textLeft = AndroidUtilities.dp(58f)
                drawRound(canvas, textLeft, top + AndroidUtilities.dp(20f), width - AndroidUtilities.dp(53f), top + AndroidUtilities.dp(28f), 8f)
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

        fun setPageProgress(fromId: String, toId: String, progress: Float) {
            tabsView.setPageProgress(fromId, toId, progress)
        }

        fun setSelectedOption(optionId: String) {
            tabsView.setSelectedOption(optionId)
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
    ) : HorizontalScrollView(context) {
        private val tabsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(AndroidUtilities.dp(6f), 0, 0, 0)
        }
        private val chips = mutableListOf<PollOptionTabChipView>()
        private var selectedId: String? = null
        private var selectionAnimator: ValueAnimator? = null

        init {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            clipToPadding = false
            addView(tabsContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }

        fun bind(options: List<PollOptionRef>, selectedId: String?) {
            val previousSelectedId = this.selectedId
            this.selectedId = selectedId
            selectionAnimator?.cancel()
            tabsContainer.removeAllViews()
            chips.clear()
            options.forEach { option ->
                val chip = PollOptionTabChipView(context).apply {
                    optionId = option.id
                    bind(option, selectionProgress = if (option.id == selectedId) 1f else 0f)
                    setOnClickListener {
                        if (this@PollOptionTabsView.selectedId != option.id) {
                            onOptionSelected(option.id)
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
            requestLayout()
            post {
                val selectedChip = chips.firstOrNull { chip -> chip.optionId == selectedId } ?: return@post
                val targetScroll = centeredScrollFor(selectedChip)
                if (previousSelectedId != null && previousSelectedId != selectedId) {
                    val startScroll = scrollX
                    val previousChip = chips.firstOrNull { chip -> chip.optionId == previousSelectedId }
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
            val fromChip = chips.firstOrNull { chip -> chip.optionId == fromId } ?: return
            val toChip = chips.firstOrNull { chip -> chip.optionId == toId } ?: return
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
            val fromScroll = centeredScrollFor(fromChip).toFloat()
            val toScroll = centeredScrollFor(toChip).toFloat()
            scrollTo((fromScroll + (toScroll - fromScroll) * normalizedProgress).toInt(), 0)
        }

        fun setSelectedOption(optionId: String) {
            selectedId = optionId
            selectionAnimator?.cancel()
            chips.forEach { chip ->
                chip.setSelectionProgress(if (chip.optionId == optionId) 1f else 0f)
            }
            post {
                val selectedChip = chips.firstOrNull { chip -> chip.optionId == optionId } ?: return@post
                scrollTo(centeredScrollFor(selectedChip), 0)
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44f), MeasureSpec.EXACTLY))
        }

        private fun centeredScrollFor(chip: View): Int {
            return (chip.left - (width - chip.width) / 2).coerceAtLeast(0)
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
        var optionId: String = ""
        private var selectionProgress = 0f

        fun bind(option: PollOptionRef, selectionProgress: Float) {
            label = option.label.trim()
            count = option.votes
            this.selectionProgress = selectionProgress
            isSelected = selectionProgress >= 0.5f
            contentDescription = "$label, $count"
            requestLayout()
            invalidate()
        }

        fun setSelectionProgress(progress: Float) {
            val normalized = progress.coerceIn(0f, 1f)
            if (selectionProgress == normalized) return
            selectionProgress = normalized
            isSelected = normalized >= 0.5f
            invalidate()
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = "android.widget.Button"
            info.isClickable = true
            info.isSelected = isSelected
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
            labelPaint.typeface = android.graphics.Typeface.DEFAULT
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
            backgroundPaint.color = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_windowBackgroundGray),
                Theme.multAlpha(accentColor, 0.12f),
                selectionProgress,
            )
            val radius = height / 2f
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, backgroundPaint)
            labelPaint.color = ColorUtils.blendARGB(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                accentColor,
                selectionProgress,
            )
            countPaint.color = ColorUtils.blendARGB(secondaryColor, accentColor, selectionProgress)
            val countText = count.toString()
            val countWidth = countPaint.measureText(countText)
            val maxLabelWidth = (width - AndroidUtilities.dp(18f) - AndroidUtilities.dp(8f) - countWidth).coerceAtLeast(0f)
            val labelX = AndroidUtilities.dp(12f).toFloat()
            val textY = height / 2f - (labelPaint.descent() + labelPaint.ascent()) / 2f
            canvas.drawText(ellipsizedLabel(maxLabelWidth), labelX, textY, labelPaint)
            canvas.drawText(countText, width - AndroidUtilities.dp(12f) - countWidth, textY, countPaint)
        }
    }

    private companion object {
        private const val VIEW_TOP_SPACER = 40
        private const val VIEW_LOADING = 41
        private const val VIEW_EMPTY = 42
        private const val VIEW_USER = 43
        private const val VIEW_LOADING_MORE = 44

        private fun lerpInt(from: Int, to: Int, progress: Float): Int {
            return (from + (to - from) * progress).toInt()
        }

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

/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ChatMessageCell and ArticleViewer
 * block cells, adapted to NativeDo/Discourse post models.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.cell

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.defaultReactionFallback
import io.github.baba983.nativedo.core.ui.resolveAvatarUrl
import io.github.baba983.nativedo.feature.topic.ui.topicPostTimeLabel
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlock
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockFactory
import io.github.baba983.nativedo.ui.topic.body.PostBodyBuildContext
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.body.PostBodyDelegate
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.BaseCell
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Components.AvatarDrawable
import java.util.ArrayList

internal class TopicPostCell(context: android.content.Context) :
    BaseCell(context),
    Theme.Colorable,
    TextSelectionHelper.ArticleSelectableView,
    PostBodyDelegate {
    var delegate: TopicPostCellDelegate? = null
    var textSelectionHelper: TextSelectionHelper.ArticleTextSelectionHelper? = null

    private val horizontalPadding = AndroidUtilities.dp(18f)
    private val verticalPadding = AndroidUtilities.dp(12f)
    private val avatarSize = AndroidUtilities.dp(42f)
    private val avatarGap = AndroidUtilities.dp(10f)
    private val headerBottomSpacing = AndroidUtilities.dp(10f)
    private val blockSpacing = AndroidUtilities.dp(10f)
    private val footerTopSpacing = AndroidUtilities.dp(12f)
    private val replyFooterHeight = AndroidUtilities.dp(24f)
    private val replyAvatarSize = AndroidUtilities.dp(16f)
    private val replyAvatarOverlap = AndroidUtilities.dp(4f)
    private val reactionTopSpacing = AndroidUtilities.dp(12f)
    private val reactionChipHeight = AndroidUtilities.dp(26f)
    private val reactionChipHorizontalPadding = AndroidUtilities.dp(9f)
    private val reactionChipSpacing = AndroidUtilities.dp(6f)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.getColor(Theme.key_divider)
        strokeWidth = AndroidUtilities.dp(1f).toFloat()
    }
    private val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
        textSize = AndroidUtilities.dp(15f).toFloat()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val metaPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        textSize = AndroidUtilities.dp(13f).toFloat()
    }
    private val reactionChipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), 0.12f)
    }
    private val replyFooterPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.08f)
    }
    private val reactionChipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dp(1f).toFloat()
    }
    private val reactionTextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        textSize = AndroidUtilities.dp(12f).toFloat()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val replyAvatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val replyTextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        textSize = AndroidUtilities.dp(13f).toFloat()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val avatarDrawable = AvatarDrawable()
    private val avatarImage = ImageReceiver(this).apply {
        setRoundRadius(avatarSize / 2)
        setAllowLoadingOnAttachedOnly(true)
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private data class ReactionChip(
        val reaction: PostReaction?,
        val label: String,
        val iconReceiver: ImageReceiver? = null,
        val rect: RectF = RectF(),
        val iconRect: RectF = RectF(),
    )

    private var post: Post? = null
    private var previousPost: Post? = null
    private var nextPost: Post? = null
    private var isFirstPost = false
    private var isLastPost = false
    private var topicId: Long = 0L
    private var baseUrl: String = "https://linux.do/"
    private var localPostsByNumber: Map<Int, String> = emptyMap()
    private var emojiCatalog: Map<String, String> = emptyMap()
    private var parsedBlocks: List<CookedPostBlock> = emptyList()
    private var blockState: PostBodyBlockState? = null
    private var bodyBlocks: List<PostBodyBlock> = emptyList()
    private var bodyContext: PostBodyBuildContext? = null
    private var titleLayout: StaticLayout? = null
    private var metaLayout: StaticLayout? = null
    private var blockTops = IntArray(0)
    private var blockHeights = IntArray(0)
    private var reactionChips: List<ReactionChip> = emptyList()
    private var sortedReactions: List<PostReaction> = emptyList()
    private var replyCount = 0
    private var recentReplyParticipantLabels: List<String> = emptyList()
    private var replyFooterTop = 0
    private var measuredReplyFooterHeight = 0
    private var reactionRowTop = 0
    private var reactionRowHeight = 0
    private var headerTextLeft = horizontalPadding
    private var bodyLeft = horizontalPadding
    private var activeTouchBlockTop = 0
    private var bodyTop = 0
    private var bodyWidth = 0
    private var measuredCellHeight = AndroidUtilities.dp(80f)
    private var avatarUrl: String? = null
    private val reactionIconReceivers = linkedMapOf<String, ImageReceiver>()
    private var downX = 0f
    private var downY = 0f
    private var didLongPressPost = false
    private var ignoreSelectionTouchUntilUp = false
    private var pressedReactionIndex = -1
    private var replyFooterPressed = false

    init {
        setWillNotDraw(false)
    }

    fun setPost(
        post: Post,
        prev: Post? = null,
        next: Post? = null,
        isFirst: Boolean = false,
        isLast: Boolean = false,
        currentTopicId: Long,
        baseUrl: String,
        localPostsByNumber: Map<Int, String> = emptyMap(),
        emojiCatalog: Map<String, String> = emptyMap(),
        replyCount: Int = 0,
        recentReplyParticipantLabels: List<String> = emptyList(),
        parsedBlocks: List<CookedPostBlock>? = null,
        blockState: PostBodyBlockState? = null,
    ) {
        this.post = post
        this.previousPost = prev
        this.nextPost = next
        this.isFirstPost = isFirst
        this.isLastPost = isLast
        this.topicId = currentTopicId
        this.baseUrl = baseUrl
        this.localPostsByNumber = localPostsByNumber
        this.emojiCatalog = emojiCatalog
        this.replyCount = replyCount
        this.recentReplyParticipantLabels = recentReplyParticipantLabels
        loadAvatar(post)
        reactionChips = buildReactionChips(post)
        this.parsedBlocks = parsedBlocks ?: parseCookedPostBody(post.cookedHtml.ifBlank { "<p>（空内容）</p>" }, baseUrl)
        this.blockState = blockState
        rebuildBodyContext()
        requestLayout()
        invalidate()
    }

    override fun updateColors() {
        dividerPaint.color = Theme.getColor(Theme.key_divider)
        titlePaint.color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
        metaPaint.color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        reactionTextPaint.color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        replyTextPaint.color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        replyFooterPressedPaint.color = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.08f)
        rebuildBodyContext()
        requestLayout()
        invalidate()
    }

    override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
        ensureBodyBlocks()
        bodyBlocks.forEachIndexed { index, block ->
            block.fillTextLayoutBlocks(blocks, bodyLeft, blockTops.getOrNull(index) ?: 0)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        headerTextLeft = horizontalPadding + avatarSize + avatarGap
        bodyLeft = horizontalPadding
        bodyWidth = (width - bodyLeft - horizontalPadding).coerceAtLeast(AndroidUtilities.dp(120f))
        ensureBodyBlocks()
        val headerTextWidth = (width - headerTextLeft - horizontalPadding).coerceAtLeast(AndroidUtilities.dp(120f))
        titleLayout = buildLayout(displayName(), titlePaint, headerTextWidth, maxLines = 1)
        metaLayout = buildLayout(metaLine(), metaPaint, headerTextWidth, maxLines = 1)
        avatarImage.setImageCoords(
            horizontalPadding.toFloat(),
            verticalPadding.toFloat(),
            avatarSize.toFloat(),
            avatarSize.toFloat(),
        )
        var y = verticalPadding
        val headerTextHeight = (titleLayout?.height ?: 0) + AndroidUtilities.dp(3f) + (metaLayout?.height ?: 0)
        y += maxOf(avatarSize, headerTextHeight)
        y += headerBottomSpacing
        bodyTop = y
        blockTops = IntArray(bodyBlocks.size)
        blockHeights = IntArray(bodyBlocks.size)
        bodyBlocks.forEachIndexed { index, block ->
            if (index > 0) y += spacingBeforeBlock(block)
            blockTops[index] = y
            val height = block.measure(bodyWidth)
            blockHeights[index] = height
            y += height
        }
        measuredReplyFooterHeight = measureReplyFooter()
        if (measuredReplyFooterHeight > 0) {
            y += footerTopSpacing
            replyFooterTop = y
            y += measuredReplyFooterHeight
        } else {
            replyFooterTop = 0
        }
        reactionRowHeight = measureReactionRow(bodyWidth)
        if (reactionRowHeight > 0) {
            y += reactionTopSpacing
            reactionRowTop = y
            y += reactionRowHeight
        } else {
            reactionRowTop = 0
        }
        measuredCellHeight = y + verticalPadding
        setMeasuredDimension(width, measuredCellHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        avatarImage.draw(canvas)
        canvas.save()
        canvas.translate(headerTextLeft.toFloat(), verticalPadding.toFloat())
        titleLayout?.draw(canvas)
        canvas.translate(0f, ((titleLayout?.height ?: 0) + AndroidUtilities.dp(3f)).toFloat())
        metaLayout?.draw(canvas)
        canvas.restore()
        bodyBlocks.forEachIndexed { index, block ->
            canvas.save()
            canvas.translate(bodyLeft.toFloat(), blockTops[index].toFloat())
            block.draw(canvas, bodyWidth)
            canvas.restore()
        }
        textSelectionHelper?.draw(canvas, this, 0)
        if (measuredReplyFooterHeight > 0) {
            canvas.save()
            canvas.translate(bodyLeft.toFloat(), replyFooterTop.toFloat())
            drawReplyFooter(canvas)
            canvas.restore()
        }
        if (reactionRowHeight > 0) {
            canvas.save()
            canvas.translate(bodyLeft.toFloat(), reactionRowTop.toFloat())
            drawReactionRow(canvas)
            canvas.restore()
        }
        canvas.drawLine(0f, (measuredCellHeight - 1).toFloat(), width.toFloat(), (measuredCellHeight - 1).toFloat(), dividerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        textSelectionHelper?.let { helper ->
            if (helper.isInSelectionMode()) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    if (ignoreSelectionTouchUntilUp) {
                        ignoreSelectionTouchUntilUp = false
                    } else {
                        helper.clear()
                    }
                }
                return true
            }
        }
        val localX = event.x - bodyLeft
        val localY = event.y
        bodyBlocks.forEachIndexed { index, block ->
            val top = blockTops.getOrNull(index) ?: return@forEachIndexed
            val bottom = top + (blockHeights.getOrNull(index) ?: 0)
            if (localY >= top && localY <= bottom) {
                activeTouchBlockTop = top
                if (block.onTouch(event, localX, localY - top)) {
                    return true
                }
            }
        }
        if (measuredReplyFooterHeight > 0 && localY >= replyFooterTop && localY <= replyFooterTop + measuredReplyFooterHeight) {
            if (handleReplyFooterTouch(event, localX, localY - replyFooterTop)) {
                return true
            }
        }
        if (reactionRowHeight > 0 && localY >= reactionRowTop && localY <= reactionRowTop + reactionRowHeight) {
            if (handleReactionTouch(event, localX, localY - reactionRowTop)) {
                return true
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                didLongPressPost = false
                startCheckLongPress()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop) {
                    cancelCheckLongPress()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelCheckLongPress()
                return didLongPressPost
            }
        }
        if (didLongPressPost) return true
        return super.onTouchEvent(event)
    }

    override fun onLongPress(): Boolean {
        textSelectionHelper?.let { helper ->
            if (helper.isSelectable(this)) {
                helper.setMaybeView(downX.toInt(), downY.toInt(), this)
                helper.trySelect(this)
                if (helper.isInSelectionMode()) {
                    ignoreSelectionTouchUntilUp = true
                    return true
                }
            }
        }
        val currentPost = post ?: return false
        didLongPressPost = true
        delegate?.didLongPressPost(currentPost)
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        avatarImage.onAttachedToWindow()
        reactionIconReceivers.values.forEach(ImageReceiver::onAttachedToWindow)
        bodyBlocks.forEach(PostBodyBlock::onAttachedToWindow)
    }

    override fun onDetachedFromWindow() {
        bodyBlocks.forEach(PostBodyBlock::onDetachedFromWindow)
        bodyContext?.imagePool?.onDetachedFromWindow()
        reactionIconReceivers.values.forEach(ImageReceiver::onDetachedFromWindow)
        avatarImage.onDetachedFromWindow()
        super.onDetachedFromWindow()
    }

    override fun onLinkClick(url: String) {
        delegate?.didPressLink(url)
    }

    override fun onTopicClick(topicId: Long) {
        delegate?.didPressTopic(topicId)
    }

    override fun onUserClick(username: String) {
        delegate?.didPressUser(username)
    }

    override fun onLinkLongPress(url: String) {
        delegate?.didLongPressLink(url)
    }

    override fun onImageClick(url: String, gallery: List<GalleryImage>, sourceBoundsInWindow: RectF?, hostView: View) {
        delegate?.didPressImage(
            url = url,
            gallery = gallery,
            sourceBoundsInWindow = sourceBoundsInWindow?.let(::blockLocalRectToWindow),
            hostView = this,
        )
    }

    override fun onQuoteExpand(topicId: Long, postNumber: Int, onResult: (String?) -> Unit) {
        delegate?.didExpandQuote(topicId, postNumber, onResult) ?: onResult(null)
    }

    private fun rebuildBodyContext() {
        bodyBlocks.forEach(PostBodyBlock::onDetachedFromWindow)
        bodyContext?.imagePool?.clear()
        bodyContext = null
        bodyBlocks = emptyList()
    }

    private fun ensureBodyBlocks() {
        if (bodyBlocks.isNotEmpty()) return
        val context = PostBodyBuildContext(
            host = this,
            baseUrl = baseUrl,
            delegate = this,
            textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
            secondaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
            accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
            chipBackgroundColor = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), 0.15f),
            dividerColor = Theme.getColor(Theme.key_divider),
        )
        bodyContext = context
        bodyBlocks = PostBodyBlockFactory.build(parsedBlocks, context, blockState)
        if (isAttachedToWindow) bodyBlocks.forEach(PostBodyBlock::onAttachedToWindow)
    }

    private fun blockLocalRectToWindow(rect: RectF): RectF {
        val location = IntArray(2)
        getLocationInWindow(location)
        return RectF(
            location[0] + bodyLeft + rect.left,
            location[1] + activeTouchBlockTop + rect.top,
            location[0] + bodyLeft + rect.right,
            location[1] + activeTouchBlockTop + rect.bottom,
        )
    }

    private fun loadAvatar(post: Post) {
        val name = post.authorDisplayName.ifBlank { post.authorUsername }
        avatarDrawable.setInfo(post.authorUsername.hashCode().toLong(), name)
        avatarDrawable.setRoundRadius(avatarSize / 2)

        val nextAvatarUrl = resolveAvatarUrl(
            baseUrl = baseUrl,
            avatarTemplate = post.authorAvatarTemplate,
            animatedAvatarUrl = post.authorAnimatedAvatarUrl,
            size = 96,
        )
        if (avatarUrl == nextAvatarUrl) return

        avatarUrl = nextAvatarUrl
        if (nextAvatarUrl.isBlank()) {
            avatarImage.setImage(null, null, avatarDrawable, null, post, 0)
        } else {
            avatarImage.setImage(ImageLocation.getForPath(nextAvatarUrl), "50_50", avatarDrawable, null, post, 0)
        }
    }

    private fun measureReplyFooter(): Int {
        return if (replyCount > 0 || recentReplyParticipantLabels.isNotEmpty()) replyFooterHeight else 0
    }

    private fun drawReplyFooter(canvas: Canvas) {
        val accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
        val secondaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        val avatarLabels = recentReplyParticipantLabels.take(3).ifEmpty { listOf("reply") }
        val avatarsWidth = avatarLabels.size * replyAvatarSize - (avatarLabels.size - 1).coerceAtLeast(0) * replyAvatarOverlap
        val label = if (replyCount > 0) {
            "$replyCount ${if (replyCount == 1) "reply" else "replies"}"
        } else {
            "Replies"
        }
        if (replyFooterPressed) {
            val radius = AndroidUtilities.dp(8f).toFloat()
            val right = (avatarsWidth + AndroidUtilities.dp(8f) + replyTextPaint.measureText(label) + AndroidUtilities.dp(10f))
                .coerceAtMost(bodyWidth.toFloat())
            canvas.drawRoundRect(0f, 0f, right, replyFooterHeight.toFloat(), radius, radius, replyFooterPressedPaint)
        }
        avatarLabels.forEachIndexed { index, label ->
            val left = index * (replyAvatarSize - replyAvatarOverlap)
            val cx = left + replyAvatarSize / 2f
            val cy = replyFooterHeight / 2f
            replyAvatarPaint.color = if (index == 0) {
                Theme.multAlpha(accentColor, 0.28f)
            } else {
                Theme.multAlpha(secondaryColor, 0.14f)
            }
            canvas.drawCircle(cx, cy, replyAvatarSize / 2f, replyAvatarPaint)
            replyTextPaint.color = Theme.multAlpha(secondaryColor, 0.86f)
            replyTextPaint.textSize = AndroidUtilities.dp(9f).toFloat()
            val initial = label.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
            val textX = cx - replyTextPaint.measureText(initial) / 2f
            val textY = cy - (replyTextPaint.ascent() + replyTextPaint.descent()) / 2f
            canvas.drawText(initial, textX, textY, replyTextPaint)
        }
        replyTextPaint.textSize = AndroidUtilities.dp(13f).toFloat()
        replyTextPaint.color = secondaryColor
        val textY = replyFooterHeight / 2f - (replyTextPaint.ascent() + replyTextPaint.descent()) / 2f
        canvas.drawText(label, avatarsWidth + AndroidUtilities.dp(8f).toFloat(), textY, replyTextPaint)
    }

    private fun handleReplyFooterTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val inside = localX >= 0f && localX <= bodyWidth && localY >= 0f && localY <= measuredReplyFooterHeight
        if (!inside) {
            if (replyFooterPressed) {
                replyFooterPressed = false
                invalidate()
                return true
            }
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            replyFooterPressed = true
            invalidate()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            replyFooterPressed = false
            invalidate()
            post?.let { delegate?.didPressReply(it) }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            replyFooterPressed = false
            invalidate()
            return true
        }
        return replyFooterPressed
    }

    private fun buildReactionChips(post: Post): List<ReactionChip> {
        sortedReactions = post.reactions
            .ifEmpty { post.defaultReactionFallback(emojiCatalog) }
            .filter { it.count > 0 }
            .sortedWith(
                compareByDescending<PostReaction> { it.reacted }
                    .thenByDescending { it.count }
                    .thenBy { it.id },
            )
        val visible = sortedReactions.take(MAX_VISIBLE_REACTIONS).map { reaction ->
            val iconReceiver = reactionIconReceiver(reaction)
            ReactionChip(
                reaction = reaction,
                label = if (iconReceiver != null) reaction.count.toString() else "${reactionLabel(reaction)} ${reaction.count}",
                iconReceiver = iconReceiver,
            )
        }
        val hiddenCount = (sortedReactions.size - visible.size).coerceAtLeast(0)
        return if (hiddenCount > 0) {
            visible + ReactionChip(reaction = null, label = "+$hiddenCount")
        } else {
            visible
        }
    }

    private fun measureReactionRow(maxWidth: Int): Int {
        if (reactionChips.isEmpty()) return 0
        var x = 0f
        val iconSize = AndroidUtilities.dp(14f)
        val iconGap = AndroidUtilities.dp(4f)
        reactionChips.forEach { chip ->
            val textWidth = reactionTextPaint.measureText(chip.label)
            val iconWidth = if (chip.iconReceiver != null) iconSize + iconGap else 0
            val chipWidth = (textWidth + iconWidth + reactionChipHorizontalPadding * 2).coerceAtLeast(
                AndroidUtilities.dp(34f).toFloat(),
            )
            if (x > 0f && x + reactionChipSpacing + chipWidth > maxWidth) {
                chip.rect.setEmpty()
                return@forEach
            }
            if (x > 0f) x += reactionChipSpacing
            chip.rect.set(x, 0f, x + chipWidth, reactionChipHeight.toFloat())
            if (chip.iconReceiver != null) {
                val iconTop = (reactionChipHeight - iconSize) / 2f
                chip.iconRect.set(
                    chip.rect.left + reactionChipHorizontalPadding,
                    iconTop,
                    chip.rect.left + reactionChipHorizontalPadding + iconSize,
                    iconTop + iconSize,
                )
            } else {
                chip.iconRect.setEmpty()
            }
            x += chipWidth
        }
        return if (reactionChips.any { !it.rect.isEmpty }) reactionChipHeight else 0
    }

    private fun drawReactionRow(canvas: Canvas) {
        val radius = reactionChipHeight / 2f
        val accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
        val secondaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        reactionChips.forEachIndexed { index, chip ->
            if (chip.rect.isEmpty) return@forEachIndexed
            val reacted = chip.reaction?.reacted == true
            reactionChipPaint.color = if (reacted) {
                Theme.multAlpha(accentColor, 0.18f)
            } else {
                Theme.multAlpha(accentColor, 0.08f)
            }
            reactionChipStrokePaint.color = if (reacted) {
                Theme.multAlpha(accentColor, 0.28f)
            } else {
                Theme.multAlpha(Theme.getColor(Theme.key_divider), 0.42f)
            }
            canvas.drawRoundRect(chip.rect, radius, radius, reactionChipPaint)
            canvas.drawRoundRect(chip.rect, radius, radius, reactionChipStrokePaint)
            if (index == pressedReactionIndex) {
                canvas.drawRoundRect(chip.rect, radius, radius, pressedOverlayPaint)
            }

            reactionTextPaint.color = if (reacted) accentColor else secondaryColor
            val textY = chip.rect.centerY() - (reactionTextPaint.ascent() + reactionTextPaint.descent()) / 2f
            chip.iconReceiver?.let { receiver ->
                receiver.setImageCoords(chip.iconRect.left, chip.iconRect.top, chip.iconRect.width(), chip.iconRect.height())
                receiver.draw(canvas)
            }
            val textX = if (chip.iconReceiver != null) {
                chip.iconRect.right + AndroidUtilities.dp(4f)
            } else {
                chip.rect.left + reactionChipHorizontalPadding
            }
            canvas.drawText(chip.label, textX, textY, reactionTextPaint)
        }
    }

    private fun handleReactionTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val index = reactionChips.indexOfFirst { !it.rect.isEmpty && it.rect.contains(localX, localY) }
        if (index < 0) return false
        val chip = reactionChips[index]
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pressedReactionIndex = index
            invalidate()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            pressedReactionIndex = -1
            invalidate()
            val currentPost = post ?: return true
            val reaction = chip.reaction
            if (reaction != null) {
                delegate?.didPressReaction(currentPost, reaction)
            } else {
                delegate?.didPressMoreReactions(currentPost, sortedReactions, reaction?.id)
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            pressedReactionIndex = -1
            invalidate()
            return true
        }
        return pressedReactionIndex == index
    }

    private fun spacingBeforeBlock(block: PostBodyBlock): Int {
        return when (block.javaClass.simpleName) {
            "HeadingBlock" -> AndroidUtilities.dp(16f)
            "ImageBlock", "CollageBlock", "OneboxBlock", "PollBlock", "TableBlock" -> AndroidUtilities.dp(12f)
            "RuleBlock" -> AndroidUtilities.dp(6f)
            else -> blockSpacing
        }
    }

    private fun reactionLabel(reaction: PostReaction): String {
        if (reaction.id == "heart" && reaction.iconUrl.isNullOrBlank()) return "♡"
        return reaction.id
            .replace('_', ' ')
            .replace("+1", "👍")
            .replace("laughing", "😄")
            .replace("clap", "👏")
            .replace("confetti ball", "🎉")
    }

    private fun reactionIconReceiver(reaction: PostReaction): ImageReceiver? {
        val rawUrl = reaction.iconUrl ?: emojiCatalog[reaction.id] ?: return null
        val url = resolvePostImageUrl(baseUrl, rawUrl)
        return reactionIconReceivers.getOrPut(url) {
            ImageReceiver(this).apply {
                setAllowLoadingOnAttachedOnly(true)
                setImage(ImageLocation.getForPath(url), null, null, null, url, 0)
                if (isAttachedToWindow) onAttachedToWindow()
            }
        }
    }

    private fun displayName(): String {
        val post = post ?: return "Post"
        return post.authorDisplayName.ifBlank { post.authorUsername }
    }

    private fun metaLine(): String {
        val post = post ?: return ""
        return buildString {
            append('@')
            append(post.authorUsername)
            post.postNumber?.let { append(" · #").append(it) }
            post.authorTitle.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            topicPostTimeLabel(post.createdAt)?.let { append(" · ").append(it) }
        }
    }

    private fun buildLayout(text: String, paint: TextPaint, maxWidth: Int, maxLines: Int): StaticLayout {
        val source = text.ifBlank { " " }
        return StaticLayout.Builder.obtain(source, 0, source.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    private companion object {
        private const val MAX_VISIBLE_REACTIONS = 3
    }
}

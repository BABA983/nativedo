/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ChatMessageCell and ArticleViewer
 * block cells, adapted to NativeDo/Discourse post models.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.cell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.baba983.nativedo.core.html.CookedPostBlock;
import io.github.baba983.nativedo.core.html.CookedPostBodyParserKt;
import io.github.baba983.nativedo.core.html.GalleryImage;
import io.github.baba983.nativedo.core.model.Post;
import io.github.baba983.nativedo.core.model.PostReaction;
import io.github.baba983.nativedo.core.ui.DiscourseUiHelpersKt;
import io.github.baba983.nativedo.feature.topic.ui.TopicReplyTargetResolverKt;
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlock;
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockFactory;
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState;
import io.github.baba983.nativedo.ui.topic.body.PostBodyBuildContext;
import io.github.baba983.nativedo.ui.topic.body.PostBodyDelegate;
import io.github.baba983.nativedo.ui.topic.body.PostBodyImagePool;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.PhotoViewer;

public class TopicPostCell extends BaseCell implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView, PostBodyDelegate {
    public TopicPostCellDelegate delegate;
    public TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper;

    private static final int MAX_VISIBLE_REACTIONS = 3;
    private static final int[] PRESSED_STATE = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};

    private final int horizontalPadding = AndroidUtilities.dp(18f);
    private final int verticalPadding = AndroidUtilities.dp(12f);
    private final int avatarSize = AndroidUtilities.dp(42f);
    private final int avatarGap = AndroidUtilities.dp(10f);
    private final int headerBottomSpacing = AndroidUtilities.dp(10f);
    private final int blockSpacing = AndroidUtilities.dp(10f);
    private final int footerTopSpacing = AndroidUtilities.dp(12f);
    private final int replyFooterHeight = AndroidUtilities.dp(40f);
    private final int replyAvatarSize = AndroidUtilities.dp(24f);
    private final int replyAvatarStep = AndroidUtilities.dp(17f);
    private final int reactionTopSpacing = AndroidUtilities.dp(12f);
    private final int reactionChipHeight = AndroidUtilities.dp(26f);
    private final int reactionChipHorizontalPadding = AndroidUtilities.dp(9f);
    private final int reactionChipSpacing = AndroidUtilities.dp(6f);

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint metaPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint reactionChipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressedOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint reactionChipStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint reactionTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF highlightRect = new RectF();
    private final RectF replyFooterSelectorRect = new RectF();
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final ImageReceiver avatarImage;
    private final int touchSlop;
    private final ArrayList<ReplyAvatar> replyAvatars = new ArrayList<>();
    private final LinkedHashMap<String, ImageReceiver> reactionIconReceivers = new LinkedHashMap<>();
    private final AnimatedFloat jumpHighlightProgress = new AnimatedFloat(0f, this, 0, 640, CubicBezierInterpolator.EASE_OUT_QUINT);
    private Drawable replyFooterSelector;

    private Post post;
    private Post previousPost;
    private Post nextPost;
    private boolean isFirstPost;
    private boolean isLastPost;
    private long topicId;
    private String baseUrl = "https://linux.do/";
    private Map<Integer, String> localPostsByNumber = Collections.emptyMap();
    private Map<String, String> emojiCatalog = Collections.emptyMap();
    private List<CookedPostBlock> parsedBlocks = Collections.emptyList();
    private PostBodyBlockState blockState;
    private List<PostBodyBlock> bodyBlocks = Collections.emptyList();
    private PostBodyBuildContext bodyContext;
    private StaticLayout titleLayout;
    private StaticLayout metaLayout;
    private int[] blockTops = new int[0];
    private int[] blockHeights = new int[0];
    private List<ReactionChip> reactionChips = Collections.emptyList();
    private List<PostReaction> sortedReactions = Collections.emptyList();
    private int replyCount;
    private List<Post> recentReplyParticipants = Collections.emptyList();
    private int replyFooterTop;
    private int measuredReplyFooterHeight;
    private int reactionRowTop;
    private int reactionRowHeight;
    private int headerTextLeft = horizontalPadding;
    private int bodyLeft = horizontalPadding;
    private int activeTouchBlockTop;
    private int activeTouchBlockIndex = -1;
    private int bodyTop;
    private int bodyWidth;
    private int measuredCellHeight = AndroidUtilities.dp(80f);
    private String avatarUrl;
    private float downX;
    private float downY;
    private boolean didLongPressPost;
    private boolean ignoreSelectionTouchUntilUp;
    private int pressedReactionIndex = -1;
    private boolean replyFooterPressed;
    private int jumpHighlightNonce;
    private int finishingJumpHighlightNonce;
    private String hiddenImageUrl;
    private Runnable jumpHighlightFinishedCallback;

    public TopicPostCell(Context context) {
        super(context);
        dividerPaint.setStrokeWidth(AndroidUtilities.dp(1f));
        titlePaint.setTextSize(AndroidUtilities.dp(15f));
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        metaPaint.setTextSize(AndroidUtilities.dp(13f));
        reactionChipStrokePaint.setStyle(Paint.Style.STROKE);
        reactionChipStrokePaint.setStrokeWidth(AndroidUtilities.dp(1f));
        reactionTextPaint.setTextSize(AndroidUtilities.dp(12f));
        reactionTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        replyTextPaint.setTextSize(AndroidUtilities.dp(13f));
        replyTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(avatarSize / 2);
        avatarImage.setAllowLoadingOnAttachedOnly(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
        updateColors();
    }

    public void setDelegate(TopicPostCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setTextSelectionHelper(TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper) {
        this.textSelectionHelper = textSelectionHelper;
    }

    public void setHiddenImageUrl(String hiddenImageUrl) {
        if (TextUtils.equals(this.hiddenImageUrl, hiddenImageUrl)) {
            return;
        }
        this.hiddenImageUrl = hiddenImageUrl;
        invalidate();
    }

    public void setPost(Post post, Post prev, Post next, boolean isFirst, boolean isLast, long currentTopicId, String baseUrl,
                        Map<Integer, String> localPostsByNumber, Map<String, String> emojiCatalog, int replyCount,
                        List<Post> recentReplyParticipants, List<CookedPostBlock> parsedBlocks,
                        PostBodyBlockState blockState, int jumpHighlightNonce, Runnable jumpHighlightFinishedCallback) {
        long oldPostId = this.post != null ? this.post.getId() : Long.MIN_VALUE;
        this.post = post;
        this.previousPost = prev;
        this.nextPost = next;
        this.isFirstPost = isFirst;
        this.isLastPost = isLast;
        this.topicId = currentTopicId;
        this.baseUrl = baseUrl;
        this.localPostsByNumber = localPostsByNumber != null ? localPostsByNumber : Collections.emptyMap();
        this.emojiCatalog = emojiCatalog != null ? emojiCatalog : Collections.emptyMap();
        this.replyCount = replyCount;
        this.recentReplyParticipants = recentReplyParticipants != null ? recentReplyParticipants : Collections.emptyList();
        this.parsedBlocks = parsedBlocks != null ? parsedBlocks : CookedPostBodyParserKt.parseCookedPostBody(blankFallback(post.getCookedHtml()), baseUrl);
        this.blockState = blockState;
        this.jumpHighlightFinishedCallback = jumpHighlightFinishedCallback;
        if (jumpHighlightNonce > 0 && jumpHighlightNonce != this.jumpHighlightNonce) {
            this.jumpHighlightNonce = jumpHighlightNonce;
            this.finishingJumpHighlightNonce = jumpHighlightNonce;
            jumpHighlightProgress.force(1f);
        } else if (jumpHighlightNonce <= 0 || oldPostId != post.getId()) {
            this.jumpHighlightNonce = 0;
            this.finishingJumpHighlightNonce = 0;
            jumpHighlightProgress.force(0f);
        }
        loadAvatar(post);
        rebuildReplyAvatars();
        reactionChips = buildReactionChips(post);
        rebuildBodyContext();
        requestLayout();
        invalidate();
    }

    @Override
    public void updateColors() {
        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
        titlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        metaPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        reactionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        replyTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        pressedOverlayPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), 0.12f));
        highlightPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        replyFooterSelector = Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.12f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, replyFooterHeight / 2);
        rebuildBodyContext();
        requestLayout();
        invalidate();
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
        ensureBodyBlocks();
        for (int i = 0; i < bodyBlocks.size(); i++) {
            bodyBlocks.get(i).fillTextLayoutBlocks(blocks, bodyLeft, i < blockTops.length ? blockTops[i] : 0);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        headerTextLeft = horizontalPadding + avatarSize + avatarGap;
        bodyLeft = horizontalPadding;
        bodyWidth = Math.max(width - bodyLeft - horizontalPadding, AndroidUtilities.dp(120f));
        ensureBodyBlocks();
        int headerTextWidth = Math.max(width - headerTextLeft - horizontalPadding, AndroidUtilities.dp(120f));
        titleLayout = buildLayout(displayName(), titlePaint, headerTextWidth, 1);
        metaLayout = buildLayout(metaLine(), metaPaint, headerTextWidth, 1);
        avatarImage.setImageCoords(horizontalPadding, verticalPadding, avatarSize, avatarSize);
        int y = verticalPadding;
        int headerTextHeight = (titleLayout != null ? titleLayout.getHeight() : 0) + AndroidUtilities.dp(3f) + (metaLayout != null ? metaLayout.getHeight() : 0);
        y += Math.max(avatarSize, headerTextHeight);
        y += headerBottomSpacing;
        bodyTop = y;
        blockTops = new int[bodyBlocks.size()];
        blockHeights = new int[bodyBlocks.size()];
        for (int i = 0; i < bodyBlocks.size(); i++) {
            if (i > 0) {
                y += spacingBeforeBlock(bodyBlocks.get(i));
            }
            blockTops[i] = y;
            int height = bodyBlocks.get(i).measure(bodyWidth);
            blockHeights[i] = height;
            y += height;
        }
        measuredReplyFooterHeight = measureReplyFooter();
        if (measuredReplyFooterHeight > 0) {
            y += footerTopSpacing;
            replyFooterTop = y;
            y += measuredReplyFooterHeight;
        } else {
            replyFooterTop = 0;
        }
        reactionRowHeight = measureReactionRow(bodyWidth);
        if (reactionRowHeight > 0) {
            y += reactionTopSpacing;
            reactionRowTop = y;
            y += reactionRowHeight;
        } else {
            reactionRowTop = 0;
        }
        measuredCellHeight = y + verticalPadding;
        setMeasuredDimension(width, measuredCellHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        drawJumpHighlight(canvas);
        avatarImage.draw(canvas);
        canvas.save();
        canvas.translate(headerTextLeft, verticalPadding);
        if (titleLayout != null) {
            titleLayout.draw(canvas);
        }
        canvas.translate(0, (titleLayout != null ? titleLayout.getHeight() : 0) + AndroidUtilities.dp(3f));
        if (metaLayout != null) {
            metaLayout.draw(canvas);
        }
        canvas.restore();
        for (int i = 0; i < bodyBlocks.size(); i++) {
            canvas.save();
            canvas.translate(bodyLeft, blockTops[i]);
            bodyBlocks.get(i).draw(canvas, bodyWidth);
            canvas.restore();
        }
        if (textSelectionHelper != null) {
            textSelectionHelper.draw(canvas, this, 0);
        }
        if (measuredReplyFooterHeight > 0) {
            canvas.save();
            canvas.translate(bodyLeft, replyFooterTop);
            drawReplyFooter(canvas);
            canvas.restore();
        }
        if (reactionRowHeight > 0) {
            canvas.save();
            canvas.translate(bodyLeft, reactionRowTop);
            drawReactionRow(canvas);
            canvas.restore();
        }
        canvas.drawLine(0, measuredCellHeight - 1, getWidth(), measuredCellHeight - 1, dividerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            if (ignoreSelectionTouchUntilUp) {
                boolean handled = textSelectionHelper.continueOneTouchSelection(this, event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    ignoreSelectionTouchUntilUp = false;
                }
                return handled;
            }
            return true;
        }
        int action = event.getActionMasked();
        float localX = event.getX() - bodyLeft;
        float localY = event.getY();
        for (int i = 0; i < bodyBlocks.size(); i++) {
            int top = i < blockTops.length ? blockTops[i] : 0;
            int bottom = top + (i < blockHeights.length ? blockHeights[i] : 0);
            if (localY >= top && localY <= bottom) {
                activeTouchBlockTop = top;
                activeTouchBlockIndex = i;
                PostBodyBlock block = bodyBlocks.get(i);
                if (action == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    didLongPressPost = false;
                    ignoreSelectionTouchUntilUp = false;
                    if (shouldStartSelectionBeforeBlockTouch(block)) {
                        startCheckLongPress();
                    }
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(event.getX() - downX) > touchSlop || Math.abs(event.getY() - downY) > touchSlop) {
                        cancelCheckLongPress();
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    cancelCheckLongPress();
                }
                if (block.onTouch(event, localX, localY - top)) {
                    return true;
                }
            }
        }
        if (measuredReplyFooterHeight > 0 && (replyFooterPressed || localY >= replyFooterTop && localY <= replyFooterTop + measuredReplyFooterHeight)) {
            if (handleReplyFooterTouch(event, localX, localY - replyFooterTop)) {
                return true;
            }
        }
        if (reactionRowHeight > 0 && localY >= reactionRowTop && localY <= reactionRowTop + reactionRowHeight) {
            if (handleReactionTouch(event, localX, localY - reactionRowTop)) {
                return true;
            }
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                didLongPressPost = false;
                ignoreSelectionTouchUntilUp = false;
                activeTouchBlockIndex = -1;
                startCheckLongPress();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > touchSlop || Math.abs(event.getY() - downY) > touchSlop) {
                    cancelCheckLongPress();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelCheckLongPress();
                return didLongPressPost;
        }
        if (didLongPressPost) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected boolean onLongPress() {
        if (textSelectionHelper != null && textSelectionHelper.isSelectable(this)) {
            textSelectionHelper.setMaybeView((int) downX, (int) downY, this);
            textSelectionHelper.trySelect(this);
            if (textSelectionHelper.isInSelectionMode()) {
                ignoreSelectionTouchUntilUp = true;
                textSelectionHelper.startOneTouchSelection();
                cancelActiveBodyTouch();
                return true;
            }
        }
        Post currentPost = post;
        if (currentPost == null) {
            return false;
        }
        didLongPressPost = true;
        cancelActiveBodyTouch();
        if (delegate != null) {
            delegate.didLongPressPost(currentPost);
        }
        return true;
    }

    private boolean shouldStartSelectionBeforeBlockTouch(PostBodyBlock block) {
        String name = block.getClass().getSimpleName();
        return "ParagraphBlock".equals(name)
                || "HeadingBlock".equals(name)
                || "ListBlock".equals(name)
                || "QuoteBlock".equals(name)
                || "SpoilerBlock".equals(name)
                || "CodeBlock".equals(name)
                || "OneboxBlock".equals(name)
                || "PollBlock".equals(name)
                || "TableBlock".equals(name);
    }

    private void cancelActiveBodyTouch() {
        if (activeTouchBlockIndex < 0 || activeTouchBlockIndex >= bodyBlocks.size()) {
            return;
        }
        MotionEvent cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, downX - bodyLeft, downY - activeTouchBlockTop, 0);
        bodyBlocks.get(activeTouchBlockIndex).onTouch(cancelEvent, cancelEvent.getX(), cancelEvent.getY());
        cancelEvent.recycle();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        for (ImageReceiver receiver : reactionIconReceivers.values()) {
            receiver.onAttachedToWindow();
        }
        for (ReplyAvatar avatar : replyAvatars) {
            avatar.receiver.onAttachedToWindow();
        }
        for (PostBodyBlock block : bodyBlocks) {
            block.onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        for (PostBodyBlock block : bodyBlocks) {
            block.onDetachedFromWindow();
        }
        if (bodyContext != null) {
            bodyContext.getImagePool().onDetachedFromWindow();
        }
        for (ImageReceiver receiver : reactionIconReceivers.values()) {
            receiver.onDetachedFromWindow();
        }
        for (ReplyAvatar avatar : replyAvatars) {
            avatar.receiver.onDetachedFromWindow();
        }
        avatarImage.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    @Override
    public void onLinkClick(String url) {
        if (delegate != null) {
            delegate.didPressLink(url);
        }
    }

    @Override
    public void onTopicClick(long topicId) {
        if (delegate != null) {
            delegate.didPressTopic(topicId);
        }
    }

    @Override
    public void onUserClick(String username) {
        if (delegate != null) {
            delegate.didPressUser(username);
        }
    }

    @Override
    public void onLinkLongPress(String url) {
        if (delegate != null) {
            delegate.didLongPressLink(url);
        }
    }

    @Override
    public void onImageClick(String url, List<GalleryImage> gallery, RectF sourceBoundsInBlock, View hostView, Function1<? super String, ? extends RectF> sourceBoundsProvider, Function1<? super String, ? extends ImageReceiver> sourceImageReceiverProvider, Function1<? super String, ? extends ImageReceiver.BitmapHolder> sourceBitmapProvider, Function1<? super String, int[]> sourceRadiusProvider) {
        if (delegate != null && post != null) {
            RectF sourceBounds = sourceBoundsInBlock != null ? blockLocalRectToWindow(sourceBoundsInBlock) : null;
            Function1<String, RectF> windowBoundsProvider = imageUrl -> {
                RectF localBounds = sourceBoundsProvider != null ? sourceBoundsProvider.invoke(imageUrl) : null;
                if (localBounds != null) {
                    return blockLocalRectToWindow(localBounds);
                }
                ImageSource source = imageSourceForUrl(imageUrl);
                return source != null ? blockLocalRectToWindow(source.bounds, source.blockTop) : null;
            };
            Function1<String, ImageReceiver> receiverProvider = imageUrl -> {
                ImageReceiver receiver = sourceImageReceiverProvider != null ? sourceImageReceiverProvider.invoke(imageUrl) : null;
                if (receiver != null) {
                    return receiver;
                }
                ImageSource source = imageSourceForUrl(imageUrl);
                return source != null ? source.receiver : null;
            };
            Function1<String, ImageReceiver.BitmapHolder> bitmapProvider = imageUrl -> {
                ImageReceiver.BitmapHolder holder = sourceBitmapProvider != null ? sourceBitmapProvider.invoke(imageUrl) : null;
                if (holder != null) {
                    return holder;
                }
                ImageSource source = imageSourceForUrl(imageUrl);
                return source != null ? source.block.imageBitmapForUrl(imageUrl) : null;
            };
            Function1<String, int[]> radiusProvider = imageUrl -> {
                int[] radius = sourceRadiusProvider != null ? sourceRadiusProvider.invoke(imageUrl) : null;
                if (radius != null) {
                    return radius;
                }
                ImageSource source = imageSourceForUrl(imageUrl);
                return source != null ? source.radius : null;
            };
            delegate.didPressImage(post, url, gallery, sourceBounds, this, windowBoundsProvider, receiverProvider, bitmapProvider, radiusProvider);
        }
    }

    @Override
    public void onQuoteExpand(long topicId, int postNumber, Function1<? super String, Unit> onResult) {
        if (delegate != null) {
            delegate.didExpandQuote(topicId, postNumber, onResult);
        } else if (onResult != null) {
            onResult.invoke(null);
        }
    }

    @Override
    public void onPollOptionSelected(String pollName, String optionId) {
        if (delegate != null && post != null) {
            delegate.didPressPollOption(post, pollName, optionId);
        }
    }

    private void drawJumpHighlight(Canvas canvas) {
        if (finishingJumpHighlightNonce <= 0) {
            return;
        }
        float progress = jumpHighlightProgress.set(0f);
        if (progress > 0f) {
            int alpha = Math.min(70, Math.max(0, (int) (70 * progress)));
            highlightPaint.setAlpha(alpha);
            highlightRect.set(0, 0, getWidth(), measuredCellHeight);
            canvas.drawRect(highlightRect, highlightPaint);
        }
        if (progress <= 0f && !jumpHighlightProgress.isInProgress()) {
            int nonce = finishingJumpHighlightNonce;
            finishingJumpHighlightNonce = 0;
            if (jumpHighlightFinishedCallback != null) {
                jumpHighlightFinishedCallback.run();
            }
            if (jumpHighlightNonce == nonce) {
                jumpHighlightNonce = 0;
            }
        }
    }

    private void rebuildBodyContext() {
        for (PostBodyBlock block : bodyBlocks) {
            block.onDetachedFromWindow();
        }
        if (bodyContext != null) {
            bodyContext.getImagePool().clear();
        }
        bodyContext = null;
        bodyBlocks = Collections.emptyList();
    }

    private void ensureBodyBlocks() {
        if (!bodyBlocks.isEmpty()) {
            return;
        }
        PostBodyBuildContext context = new PostBodyBuildContext(
                this,
                baseUrl,
                this,
                () -> hiddenImageUrl,
                new PostBodyImagePool(this),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), 0.15f),
                Theme.getColor(Theme.key_divider),
                () -> {
                    if (textSelectionHelper != null) {
                        textSelectionHelper.invalidate();
                    } else {
                        invalidate();
                    }
                    return Unit.INSTANCE;
                }
        );
        bodyContext = context;
        bodyBlocks = PostBodyBlockFactory.INSTANCE.build(parsedBlocks, context, blockState);
        if (isAttachedToWindow()) {
            for (PostBodyBlock block : bodyBlocks) {
                block.onAttachedToWindow();
            }
        }
    }

    private ImageSource imageSourceForUrl(String imageUrl) {
        ensureBodyBlocks();
        for (int i = 0; i < bodyBlocks.size(); i++) {
            PostBodyBlock block = bodyBlocks.get(i);
            RectF bounds = block.imageBoundsInBlock(imageUrl);
            if (bounds == null) {
                continue;
            }
            ImageReceiver receiver = block.imageReceiverForUrl(imageUrl);
            return new ImageSource(
                    block,
                    bounds,
                    i < blockTops.length ? blockTops[i] : 0,
                    receiver,
                    block.imageRadiusForUrl(imageUrl)
            );
        }
        return null;
    }

    public RectF imageBoundsInWindowForUrl(String imageUrl) {
        ImageSource source = imageSourceForUrl(imageUrl);
        return source == null ? null : blockLocalRectToWindow(source.bounds, source.blockTop);
    }

    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        ImageSource source = imageSourceForUrl(imageUrl);
        return source == null ? null : source.receiver;
    }

    public ImageReceiver.BitmapHolder imageBitmapForUrl(String imageUrl) {
        ImageSource source = imageSourceForUrl(imageUrl);
        return source == null || source.receiver == null ? null : source.receiver.getBitmapSafe();
    }

    public int[] imageRadiusForUrl(String imageUrl) {
        ImageSource source = imageSourceForUrl(imageUrl);
        return source == null || source.radius == null ? null : Arrays.copyOf(source.radius, source.radius.length);
    }

    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(String imageUrl, boolean needPreview, boolean closing) {
        ImageSource source = imageSourceForUrl(imageUrl);
        if (source == null) {
            return null;
        }
        RectF bounds = blockLocalRectToWindow(source.bounds, source.blockTop);
        ImageReceiver receiver = source.receiver;
        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
        object.viewX = (int) (bounds.left - (receiver != null ? receiver.getImageX() : 0f));
        object.viewY = (int) (bounds.top - (receiver != null ? receiver.getImageY() : 0f));
        object.viewWidth = (int) (receiver != null ? receiver.getImageWidth() : bounds.width());
        object.viewHeight = (int) (receiver != null ? receiver.getImageHeight() : bounds.height());
        object.parentView = this;
        object.imageReceiver = receiver;
        object.thumb = needPreview && receiver != null ? receiver.getBitmapSafe() : null;
        object.radius = needPreview && source.radius != null ? Arrays.copyOf(source.radius, source.radius.length) : null;
        object.scale = 1.0f;
        return object;
    }

    private RectF blockLocalRectToWindow(RectF rect) {
        return blockLocalRectToWindow(rect, activeTouchBlockTop);
    }

    private RectF blockLocalRectToWindow(RectF rect, int blockTop) {
        int[] location = new int[2];
        getLocationInWindow(location);
        return new RectF(
                location[0] + bodyLeft + rect.left,
                location[1] + blockTop + rect.top,
                location[0] + bodyLeft + rect.right,
                location[1] + blockTop + rect.bottom
        );
    }

    private void loadAvatar(Post post) {
        String name = post.getAuthorDisplayName().isEmpty() ? post.getAuthorUsername() : post.getAuthorDisplayName();
        avatarDrawable.setInfo((long) post.getAuthorUsername().hashCode(), name);
        avatarDrawable.setRoundRadius(avatarSize / 2);
        String nextAvatarUrl = DiscourseUiHelpersKt.resolveAvatarUrl(baseUrl, post.getAuthorAvatarTemplate(), post.getAuthorAnimatedAvatarUrl(), 96);
        if (nextAvatarUrl.equals(avatarUrl)) {
            return;
        }
        avatarUrl = nextAvatarUrl;
        if (nextAvatarUrl.isEmpty()) {
            avatarImage.setImage(null, null, avatarDrawable, null, post, 0);
        } else {
            avatarImage.setImage(ImageLocation.getForPath(nextAvatarUrl), "50_50", avatarDrawable, null, post, 0);
        }
    }

    private int measureReplyFooter() {
        return replyCount > 0 || !recentReplyParticipants.isEmpty() ? replyFooterHeight : 0;
    }

    private void drawReplyFooter(Canvas canvas) {
        int secondaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int avatarsWidth = replyAvatarsWidth(replyAvatars.size());
        String label = replyFooterLabel();
        updateReplyFooterSelectorRect();
        if (replyFooterSelector != null) {
            replyFooterSelector.setBounds((int) replyFooterSelectorRect.left, (int) replyFooterSelectorRect.top, (int) replyFooterSelectorRect.right, (int) replyFooterSelectorRect.bottom);
            replyFooterSelector.draw(canvas);
        }
        int contentLeft = AndroidUtilities.dp(10f);
        for (int i = 0; i < replyAvatars.size(); i++) {
            ReplyAvatar avatar = replyAvatars.get(i);
            int left = contentLeft + replyAvatarOffset(i);
            avatar.receiver.setImageCoords(left, (replyFooterHeight - replyAvatarSize) / 2f, replyAvatarSize, replyAvatarSize);
            avatar.receiver.draw(canvas);
        }
        replyTextPaint.setTextSize(AndroidUtilities.dp(13f));
        replyTextPaint.setColor(secondaryColor);
        float textY = replyFooterHeight / 2f - (replyTextPaint.ascent() + replyTextPaint.descent()) / 2f;
        canvas.drawText(label, contentLeft + avatarsWidth + AndroidUtilities.dp(8f), textY, replyTextPaint);
    }

    private boolean handleReplyFooterTouch(MotionEvent event, float localX, float localY) {
        updateReplyFooterSelectorRect();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!replyFooterSelectorRect.contains(localX, localY)) {
                return false;
            }
            setReplyFooterPressed(true, localX, localY);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (!replyFooterPressed) {
                return false;
            }
            setReplyFooterPressed(false, localX, localY);
            if (post != null && delegate != null) {
                delegate.didPressReply(post);
            }
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (replyFooterPressed && replyFooterSelector != null) {
                replyFooterSelector.setHotspot(localX, localY);
            }
            return replyFooterPressed;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (replyFooterPressed) {
                setReplyFooterPressed(false, localX, localY);
                return true;
            }
            return false;
        }
        return replyFooterPressed;
    }

    private void rebuildReplyAvatars() {
        for (ReplyAvatar avatar : replyAvatars) {
            avatar.receiver.onDetachedFromWindow();
        }
        replyAvatars.clear();
        int count = Math.min(3, recentReplyParticipants.size());
        for (int i = 0; i < count; i++) {
            Post participant = recentReplyParticipants.get(i);
            String name = participant.getAuthorDisplayName().isEmpty() ? participant.getAuthorUsername() : participant.getAuthorDisplayName();
            AvatarDrawable fallback = new AvatarDrawable();
            fallback.setInfo((long) participant.getAuthorUsername().hashCode(), name);
            fallback.setRoundRadius(replyAvatarSize / 2);
            ImageReceiver receiver = new ImageReceiver(this);
            receiver.setRoundRadius(replyAvatarSize / 2);
            receiver.setAllowLoadingOnAttachedOnly(true);
            String url = DiscourseUiHelpersKt.resolveAvatarUrl(baseUrl, participant.getAuthorAvatarTemplate(), participant.getAuthorAnimatedAvatarUrl(), 96);
            if (url.isEmpty()) {
                receiver.setImage(null, null, fallback, null, participant, 0);
            } else {
                receiver.setImage(ImageLocation.getForPath(url), "50_50", fallback, null, participant, 0);
            }
            if (isAttachedToWindow()) {
                receiver.onAttachedToWindow();
            }
            replyAvatars.add(new ReplyAvatar(receiver));
        }
    }

    private String replyFooterLabel() {
        return replyCount > 0 ? replyCount + " " + (replyCount == 1 ? "reply" : "replies") : "Replies";
    }

    private void updateReplyFooterSelectorRect() {
        int avatarsWidth = replyAvatarsWidth(replyAvatars.size());
        float selectorRight = Math.min(AndroidUtilities.dp(10f) + avatarsWidth + AndroidUtilities.dp(8f) + replyTextPaint.measureText(replyFooterLabel()) + AndroidUtilities.dp(12f), bodyWidth);
        replyFooterSelectorRect.set(0, 0, selectorRight, replyFooterHeight);
    }

    private int replyAvatarsWidth(int avatarCount) {
        if (avatarCount <= 0) {
            return 0;
        }
        return AndroidUtilities.dp(2f) + replyAvatarSize + Math.max(avatarCount - 1, 0) * replyAvatarStep;
    }

    private int replyAvatarOffset(int index) {
        return AndroidUtilities.dp(2f) + index * replyAvatarStep;
    }

    private void setReplyFooterPressed(boolean pressed, float localX, float localY) {
        replyFooterPressed = pressed;
        if (replyFooterSelector != null) {
            replyFooterSelector.setHotspot(localX, localY);
            replyFooterSelector.setState(pressed ? PRESSED_STATE : StateSet.NOTHING);
        }
        invalidate();
    }

    private List<ReactionChip> buildReactionChips(Post post) {
        List<PostReaction> source = new ArrayList<>(post.getReactions());
        if (source.isEmpty() && post.getReactionUsersCount() > 0) {
            String id = post.getCurrentUserReaction() != null ? post.getCurrentUserReaction() : "heart";
            source.add(new PostReaction(id, post.getReactionUsersCount(), emojiCatalog.get(id), post.getCurrentUserReaction() != null));
        }
        source.removeIf(reaction -> reaction.getCount() <= 0);
        source.sort(Comparator
                .comparing((PostReaction reaction) -> reaction.getReacted()).reversed()
                .thenComparing(Comparator.comparingInt(PostReaction::getCount).reversed())
                .thenComparing(PostReaction::getId));
        sortedReactions = source;
        List<ReactionChip> visible = new ArrayList<>();
        int limit = Math.min(MAX_VISIBLE_REACTIONS, source.size());
        for (int i = 0; i < limit; i++) {
            PostReaction reaction = source.get(i);
            ImageReceiver iconReceiver = reactionIconReceiver(reaction);
            String label = iconReceiver != null ? String.valueOf(reaction.getCount()) : reactionLabel(reaction) + " " + reaction.getCount();
            visible.add(new ReactionChip(reaction, label, iconReceiver));
        }
        int hiddenCount = Math.max(source.size() - visible.size(), 0);
        if (hiddenCount > 0) {
            visible.add(new ReactionChip(null, "+" + hiddenCount, null));
        }
        return visible;
    }

    private int measureReactionRow(int maxWidth) {
        if (reactionChips.isEmpty()) {
            return 0;
        }
        float x = 0f;
        int iconSize = AndroidUtilities.dp(14f);
        int iconGap = AndroidUtilities.dp(4f);
        for (ReactionChip chip : reactionChips) {
            float textWidth = reactionTextPaint.measureText(chip.label);
            int iconWidth = chip.iconReceiver != null ? iconSize + iconGap : 0;
            float chipWidth = Math.max(textWidth + iconWidth + reactionChipHorizontalPadding * 2f, AndroidUtilities.dp(34f));
            if (x > 0f && x + reactionChipSpacing + chipWidth > maxWidth) {
                chip.rect.setEmpty();
                continue;
            }
            if (x > 0f) {
                x += reactionChipSpacing;
            }
            chip.rect.set(x, 0f, x + chipWidth, reactionChipHeight);
            if (chip.iconReceiver != null) {
                float iconTop = (reactionChipHeight - iconSize) / 2f;
                chip.iconRect.set(chip.rect.left + reactionChipHorizontalPadding, iconTop, chip.rect.left + reactionChipHorizontalPadding + iconSize, iconTop + iconSize);
            } else {
                chip.iconRect.setEmpty();
            }
            x += chipWidth;
        }
        for (ReactionChip chip : reactionChips) {
            if (!chip.rect.isEmpty()) {
                return reactionChipHeight;
            }
        }
        return 0;
    }

    private void drawReactionRow(Canvas canvas) {
        float radius = reactionChipHeight / 2f;
        int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        int secondaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        for (int i = 0; i < reactionChips.size(); i++) {
            ReactionChip chip = reactionChips.get(i);
            if (chip.rect.isEmpty()) {
                continue;
            }
            boolean reacted = chip.reaction != null && chip.reaction.getReacted();
            reactionChipPaint.setColor(reacted ? Theme.multAlpha(accentColor, 0.18f) : Theme.multAlpha(accentColor, 0.08f));
            reactionChipStrokePaint.setColor(reacted ? Theme.multAlpha(accentColor, 0.28f) : Theme.multAlpha(Theme.getColor(Theme.key_divider), 0.42f));
            canvas.drawRoundRect(chip.rect, radius, radius, reactionChipPaint);
            canvas.drawRoundRect(chip.rect, radius, radius, reactionChipStrokePaint);
            if (i == pressedReactionIndex) {
                canvas.drawRoundRect(chip.rect, radius, radius, pressedOverlayPaint);
            }
            reactionTextPaint.setColor(reacted ? accentColor : secondaryColor);
            float textY = chip.rect.centerY() - (reactionTextPaint.ascent() + reactionTextPaint.descent()) / 2f;
            if (chip.iconReceiver != null) {
                chip.iconReceiver.setImageCoords(chip.iconRect.left, chip.iconRect.top, chip.iconRect.width(), chip.iconRect.height());
                chip.iconReceiver.draw(canvas);
            }
            float textX = chip.iconReceiver != null ? chip.iconRect.right + AndroidUtilities.dp(4f) : chip.rect.left + reactionChipHorizontalPadding;
            canvas.drawText(chip.label, textX, textY, reactionTextPaint);
        }
    }

    private boolean handleReactionTouch(MotionEvent event, float localX, float localY) {
        int index = -1;
        for (int i = 0; i < reactionChips.size(); i++) {
            ReactionChip chip = reactionChips.get(i);
            if (!chip.rect.isEmpty() && chip.rect.contains(localX, localY)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return false;
        }
        ReactionChip chip = reactionChips.get(index);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pressedReactionIndex = index;
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            pressedReactionIndex = -1;
            invalidate();
            Post currentPost = post;
            if (currentPost == null) {
                return true;
            }
            if (delegate != null) {
                if (chip.reaction != null) {
                    delegate.didPressReaction(currentPost, chip.reaction);
                } else {
                    delegate.didPressMoreReactions(currentPost, sortedReactions, null);
                }
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            pressedReactionIndex = -1;
            invalidate();
            return true;
        }
        return pressedReactionIndex == index;
    }

    private int spacingBeforeBlock(PostBodyBlock block) {
        String name = block.getClass().getSimpleName();
        if ("HeadingBlock".equals(name)) {
            return AndroidUtilities.dp(16f);
        }
        if ("ImageBlock".equals(name) || "CollageBlock".equals(name) || "OneboxBlock".equals(name) || "PollBlock".equals(name) || "TableBlock".equals(name)) {
            return AndroidUtilities.dp(12f);
        }
        if ("RuleBlock".equals(name)) {
            return AndroidUtilities.dp(6f);
        }
        return blockSpacing;
    }

    private String reactionLabel(PostReaction reaction) {
        if ("heart".equals(reaction.getId()) && (reaction.getIconUrl() == null || reaction.getIconUrl().isEmpty())) {
            return "♡";
        }
        return reaction.getId()
                .replace('_', ' ')
                .replace("+1", "👍")
                .replace("laughing", "😄")
                .replace("clap", "👏")
                .replace("confetti ball", "🎉");
    }

    private ImageReceiver reactionIconReceiver(PostReaction reaction) {
        String rawUrl = reaction.getIconUrl() != null ? reaction.getIconUrl() : emojiCatalog.get(reaction.getId());
        if (rawUrl == null) {
            return null;
        }
        String url = CookedPostBodyParserKt.resolvePostImageUrl(baseUrl, rawUrl);
        ImageReceiver receiver = reactionIconReceivers.get(url);
        if (receiver == null) {
            receiver = new ImageReceiver(this);
            receiver.setAllowLoadingOnAttachedOnly(true);
            receiver.setImage(ImageLocation.getForPath(url), null, null, null, url, 0);
            if (isAttachedToWindow()) {
                receiver.onAttachedToWindow();
            }
            reactionIconReceivers.put(url, receiver);
        }
        return receiver;
    }

    private String displayName() {
        Post currentPost = post;
        if (currentPost == null) {
            return "Post";
        }
        return currentPost.getAuthorDisplayName().isEmpty() ? currentPost.getAuthorUsername() : currentPost.getAuthorDisplayName();
    }

    private String metaLine() {
        Post currentPost = post;
        if (currentPost == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('@').append(currentPost.getAuthorUsername());
        if (currentPost.getPostNumber() != null) {
            builder.append(" · #").append(currentPost.getPostNumber());
        }
        if (!currentPost.getAuthorTitle().isEmpty()) {
            builder.append(" · ").append(currentPost.getAuthorTitle());
        }
        String timeLabel = TopicReplyTargetResolverKt.topicPostTimeLabel(currentPost.getCreatedAt(), java.time.Instant.now(), java.time.ZoneId.systemDefault());
        if (timeLabel != null) {
            builder.append(" · ").append(timeLabel);
        }
        return builder.toString();
    }

    private StaticLayout buildLayout(String text, TextPaint paint, int maxWidth, int maxLines) {
        String source = text == null || text.isEmpty() ? " " : text;
        return StaticLayout.Builder.obtain(source, 0, source.length(), paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build();
    }

    private String blankFallback(String html) {
        return html == null || html.isEmpty() ? "<p>（空内容）</p>" : html;
    }

    private static class ReactionChip {
        final PostReaction reaction;
        final String label;
        final ImageReceiver iconReceiver;
        final RectF rect = new RectF();
        final RectF iconRect = new RectF();

        ReactionChip(PostReaction reaction, String label, ImageReceiver iconReceiver) {
            this.reaction = reaction;
            this.label = label;
            this.iconReceiver = iconReceiver;
        }
    }

    private static class ReplyAvatar {
        final ImageReceiver receiver;

        ReplyAvatar(ImageReceiver receiver) {
            this.receiver = receiver;
        }
    }

    private static class ImageSource {
        final PostBodyBlock block;
        final RectF bounds;
        final int blockTop;
        final ImageReceiver receiver;
        final int[] radius;

        ImageSource(PostBodyBlock block, RectF bounds, int blockTop, ImageReceiver receiver, int[] radius) {
            this.block = block;
            this.bounds = bounds;
            this.blockTop = blockTop;
            this.receiver = receiver;
            this.radius = radius;
        }
    }
}

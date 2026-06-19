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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

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
import io.github.baba983.nativedo.core.ui.DiscourseFlairKt;
import io.github.baba983.nativedo.core.ui.DiscourseUiHelpersKt;
import io.github.baba983.nativedo.feature.topic.ui.TopicReplyTargetResolverKt;
import io.github.baba983.nativedo.ui.topic.body.EmbeddedPostBodyBlock;
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlock;
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockFactory;
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState;
import io.github.baba983.nativedo.ui.topic.body.PostBodyBuildContext;
import io.github.baba983.nativedo.ui.topic.body.PostBodyDelegate;
import io.github.baba983.nativedo.ui.topic.body.PostBodyImagePool;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ReplyMessageLine;
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
    private final int reactionButtonHeight = AndroidUtilities.dp(26f);
    private final int reactionButtonSpacing = AndroidUtilities.dp(4f);
    private final int reactionButtonTouchSlop = AndroidUtilities.dp(4f);

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint metaPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint reactionButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint reactionTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avatarFlairBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avatarFlairBadgeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avatarFlairIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF highlightRect = new RectF();
    private final RectF avatarFlairBadgeRect = new RectF();
    private final RectF avatarFlairContentRect = new RectF();
    private final RectF replyFooterSelectorRect = new RectF();
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final ImageReceiver avatarImage;
    private final ImageReceiver avatarFlairImage;
    private final int touchSlop;
    private final ArrayList<ReplyAvatar> replyAvatars = new ArrayList<>();
    private final LinkedHashMap<String, ImageReceiver> reactionIconReceivers = new LinkedHashMap<>();
    private final AnimatedFloat jumpHighlightProgress = new AnimatedFloat(0f, this, 0, 640, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final ReplyMessageLine replyLine = new ReplyMessageLine(this);
    private final ButtonBounce replyBounce = new ButtonBounce(this, 2.0f, 2.0f);
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
    private List<String> validReactionIds = Collections.emptyList();
    private List<CookedPostBlock> parsedBlocks = Collections.emptyList();
    private PostBodyBlockState blockState;
    private List<PostBodyBlock> bodyBlocks = Collections.emptyList();
    private PostBodyBuildContext bodyContext;
    private StaticLayout titleLayout;
    private StaticLayout metaLayout;
    private int[] blockTops = new int[0];
    private int[] blockHeights = new int[0];
    private int[] blockLefts = new int[0];
    private int[] blockWidths = new int[0];
    private List<ReactionButton> reactionButtons = Collections.emptyList();
    private List<PostReaction> sortedReactions = Collections.emptyList();
    private int replyCount;
    private List<Post> recentReplyParticipants = Collections.emptyList();
    private int replyFooterTop;
    private int measuredReplyFooterHeight;
    private int reactionRowTop;
    private int reactionRowHeight;
    private int headerTextLeft = horizontalPadding;
    private int chromeLeft = horizontalPadding;
    private int chromeWidth;
    private int activeTouchBlockTop;
    private int activeTouchBlockLeft;
    private int activeTouchBlockIndex = -1;
    private boolean activeBodyTouchStream;
    private int bodyTop;
    private int measuredCellHeight = AndroidUtilities.dp(80f);
    private String avatarUrl;
    private String avatarFilter;
    private String avatarFlairUrl;
    private String avatarFlairFilter;
    private String avatarFlairIconKey;
    private String svgSpriteMarkup = "";
    private Bitmap avatarFlairIconBitmap;
    private int avatarFlairBadgeColor;
    private float downX;
    private float downY;
    private boolean didLongPressPost;
    private boolean ignoreSelectionTouchUntilUp;
    private int pressedReactionButton = -1;
    private float pressedReactionDownX;
    private float pressedReactionDownY;
    private boolean replyFooterPressed;
    private float replyFooterDownX;
    private float replyFooterDownY;
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
        reactionTextPaint.setTextSize(AndroidUtilities.dp(12f));
        reactionTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        replyTextPaint.setTextSize(AndroidUtilities.dp(13f));
        replyTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        avatarFlairBadgePaint.setStyle(Paint.Style.FILL);
        avatarFlairBadgeStrokePaint.setStyle(Paint.Style.STROKE);
        avatarFlairBadgeStrokePaint.setStrokeWidth(AndroidUtilities.dp(1f));
        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(avatarSize / 2);
        avatarImage.setAllowLoadingOnAttachedOnly(true);
        avatarImage.setDelegate((imageReceiver, set, thumb, memCache) -> invalidateAvatarArea());
        avatarImage.setVisibleInvalidate(this::invalidateAvatarArea);
        avatarFlairImage = new ImageReceiver(this);
        avatarFlairImage.setAllowLoadingOnAttachedOnly(true);
        avatarFlairImage.setDelegate((imageReceiver, set, thumb, memCache) -> invalidateAvatarArea());
        avatarFlairImage.setVisibleInvalidate(this::invalidateAvatarArea);
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
        invalidateEmbeddedBodyViews();
        invalidate();
    }

    public void setSvgSpriteMarkup(String svgSpriteMarkup) {
        String nextMarkup = svgSpriteMarkup == null ? "" : svgSpriteMarkup;
        if (TextUtils.equals(this.svgSpriteMarkup, nextMarkup)) {
            return;
        }
        this.svgSpriteMarkup = nextMarkup;
        avatarFlairIconKey = null;
        if (post != null) {
            loadAvatarFlair(post);
            rebuildBodyContext();
            requestLayout();
        }
        invalidateAvatarArea();
        invalidate();
    }

    public void setPost(Post post, Post prev, Post next, boolean isFirst, boolean isLast, long currentTopicId, String baseUrl,
                        Map<Integer, String> localPostsByNumber, Map<String, String> emojiCatalog, List<String> validReactionIds, int replyCount,
                        List<Post> recentReplyParticipants, List<CookedPostBlock> parsedBlocks,
                        PostBodyBlockState blockState, int jumpHighlightNonce, Runnable jumpHighlightFinishedCallback) {
        resetTouchState();
        long oldPostId = this.post != null ? this.post.getId() : Long.MIN_VALUE;
        if (oldPostId != post.getId()) {
            hiddenImageUrl = null;
        }
        this.post = post;
        this.previousPost = prev;
        this.nextPost = next;
        this.isFirstPost = isFirst;
        this.isLastPost = isLast;
        this.topicId = currentTopicId;
        this.baseUrl = baseUrl;
        this.localPostsByNumber = localPostsByNumber != null ? localPostsByNumber : Collections.emptyMap();
        this.emojiCatalog = emojiCatalog != null ? emojiCatalog : Collections.emptyMap();
        this.validReactionIds = validReactionIds != null ? validReactionIds : Collections.emptyList();
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
        reactionButtons = buildReactionButtons(post);
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
        highlightPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        avatarFlairBadgeStrokePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.12f));
        replyFooterSelector = Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.12f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, replyFooterHeight / 2);
        rebuildBodyContext();
        requestLayout();
        invalidate();
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
        ensureBodyBlocks();
        for (int i = 0; i < bodyBlocks.size(); i++) {
            bodyBlocks.get(i).fillTextLayoutBlocks(blocks,
                    i < blockLefts.length ? blockLefts[i] : 0,
                    i < blockTops.length ? blockTops[i] : 0);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        headerTextLeft = horizontalPadding + avatarSize + avatarGap;
        chromeLeft = horizontalPadding;
        chromeWidth = Math.max(width - chromeLeft - horizontalPadding, AndroidUtilities.dp(120f));
        ensureBodyBlocks();
        int headerTextWidth = Math.max(width - headerTextLeft - horizontalPadding, AndroidUtilities.dp(120f));
        titleLayout = buildLayout(displayName(), titlePaint, headerTextWidth, 1);
        metaLayout = buildLayout(metaLine(), metaPaint, headerTextWidth, 1);
        avatarImage.setImageCoords(horizontalPadding, verticalPadding, avatarSize, avatarSize);
        int y = verticalPadding;
        int headerTextHeight = (titleLayout != null ? titleLayout.getHeight() : 0) + AndroidUtilities.dp(3f) + (metaLayout != null ? metaLayout.getHeight() : 0);
        updateAvatarFlairCoords();
        y += Math.max(avatarSize, headerTextHeight);
        y += headerBottomSpacing;
        bodyTop = y;
        blockTops = new int[bodyBlocks.size()];
        blockHeights = new int[bodyBlocks.size()];
        blockLefts = new int[bodyBlocks.size()];
        blockWidths = new int[bodyBlocks.size()];
        for (int i = 0; i < bodyBlocks.size(); i++) {
            if (i > 0) {
                y += spacingBeforeBlock(bodyBlocks.get(i));
            }
            blockTops[i] = y;
            PostBodyBlock block = bodyBlocks.get(i);
            int inset = Math.max(block.horizontalInsetPx(), 0);
            int blockW = Math.max(width - inset * 2, AndroidUtilities.dp(120f));
            blockLefts[i] = inset;
            blockWidths[i] = blockW;
            int height = block.measure(blockW);
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
        reactionRowHeight = measureReactionRow(chromeWidth);
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
        drawAvatarFlair(canvas);
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
            canvas.translate(blockLefts[i], blockTops[i]);
            PostBodyBlock block = bodyBlocks.get(i);
            if (embeddedView(block) == null) {
                block.draw(canvas, blockWidths[i]);
            }
            canvas.restore();
        }
        if (measuredReplyFooterHeight > 0) {
            canvas.save();
            canvas.translate(chromeLeft, replyFooterTop);
            drawReplyFooter(canvas);
            canvas.restore();
        }
        if (reactionRowHeight > 0) {
            canvas.save();
            canvas.translate(chromeLeft, reactionRowTop);
            drawReactionRow(canvas);
            canvas.restore();
        }
        canvas.drawLine(0, measuredCellHeight - 1, getWidth(), measuredCellHeight - 1, dividerPaint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (textSelectionHelper != null) {
            textSelectionHelper.draw(canvas, this, 0);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                prepareLongPressCandidate(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > touchSlop || Math.abs(event.getY() - downY) > touchSlop) {
                    cancelCheckLongPress();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelCheckLongPress();
                break;
            default:
                break;
        }
        return false;
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
        float chromeX = event.getX() - chromeLeft;
        float localY = event.getY();
        if (activeBodyTouchStream && action != MotionEvent.ACTION_DOWN) {
            if (dispatchActiveBodyTouch(event)) {
                return true;
            }
        }
        for (int i = 0; i < bodyBlocks.size(); i++) {
            int top = i < blockTops.length ? blockTops[i] : 0;
            int bottom = top + (i < blockHeights.length ? blockHeights[i] : 0);
            if (localY >= top && localY <= bottom) {
                int left = i < blockLefts.length ? blockLefts[i] : 0;
                float blockX = event.getX() - left;
                activeTouchBlockTop = top;
                activeTouchBlockLeft = left;
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
                if (dispatchBlockTouch(block, event, blockX, localY - top)) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        activeBodyTouchStream = true;
                    }
                    return true;
                }
            }
        }
        if (measuredReplyFooterHeight > 0 && (replyFooterPressed || localY >= replyFooterTop && localY <= replyFooterTop + measuredReplyFooterHeight)) {
            if (handleReplyFooterTouch(event, chromeX, localY - replyFooterTop)) {
                return true;
            }
        }
        if (reactionRowHeight > 0 && (pressedReactionButton >= 0 || localY >= reactionRowTop && localY <= reactionRowTop + reactionRowHeight)) {
            if (handleReactionTouch(event, chromeX, localY - reactionRowTop)) {
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

    private void prepareLongPressCandidate(MotionEvent event) {
        downX = event.getX();
        downY = event.getY();
        didLongPressPost = false;
        ignoreSelectionTouchUntilUp = false;
        int blockIndex = findBodyBlockIndex(event.getY());
        activeTouchBlockIndex = blockIndex;
        activeTouchBlockTop = blockIndex >= 0 && blockIndex < blockTops.length ? blockTops[blockIndex] : 0;
        activeTouchBlockLeft = blockIndex >= 0 && blockIndex < blockLefts.length ? blockLefts[blockIndex] : 0;
        if (blockIndex >= 0 && blockIndex < bodyBlocks.size()) {
            if (shouldStartSelectionBeforeBlockTouch(bodyBlocks.get(blockIndex))) {
                startCheckLongPress();
            }
        } else {
            startCheckLongPress();
        }
    }

    private int findBodyBlockIndex(float y) {
        for (int i = 0; i < bodyBlocks.size(); i++) {
            int top = i < blockTops.length ? blockTops[i] : 0;
            int bottom = top + (i < blockHeights.length ? blockHeights[i] : 0);
            if (y >= top && y <= bottom) {
                return i;
            }
        }
        return -1;
    }

    private boolean shouldStartSelectionBeforeBlockTouch(PostBodyBlock block) {
        String name = block.getClass().getSimpleName();
        return "ParagraphBlock".equals(name)
                || "HeadingBlock".equals(name)
                || "ListBlock".equals(name)
                || "QuoteBlock".equals(name)
                || "DetailsBlock".equals(name)
                || "CodeBlock".equals(name)
                || "CollageBlock".equals(name)
                || "CalloutBlock".equals(name)
                || "OneboxBlock".equals(name)
                || "PollBlock".equals(name)
                || "TableBlock".equals(name);
    }

    private boolean dispatchBlockTouch(PostBodyBlock block, MotionEvent event, float blockX, float blockY) {
        View embeddedView = embeddedView(block);
        if (embeddedView == null) {
            return block.onTouch(event, blockX, blockY);
        }
        float childX = blockX - (embeddedView.getLeft() - activeTouchBlockLeft);
        float childY = blockY - (embeddedView.getTop() - activeTouchBlockTop);
        MotionEvent childEvent = MotionEvent.obtain(event);
        childEvent.setLocation(childX, childY);
        boolean handled = embeddedView.dispatchTouchEvent(childEvent);
        childEvent.recycle();
        return handled;
    }

    private void cancelActiveBodyTouch() {
        if (activeTouchBlockIndex < 0 || activeTouchBlockIndex >= bodyBlocks.size()) {
            return;
        }
        activeBodyTouchStream = false;
        MotionEvent cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, downX - activeTouchBlockLeft, downY - activeTouchBlockTop, 0);
        PostBodyBlock block = bodyBlocks.get(activeTouchBlockIndex);
        dispatchBlockTouch(block, cancelEvent, cancelEvent.getX(), cancelEvent.getY());
        cancelEvent.recycle();
    }

    private boolean dispatchActiveBodyTouch(MotionEvent event) {
        if (activeTouchBlockIndex < 0 || activeTouchBlockIndex >= bodyBlocks.size()) {
            activeBodyTouchStream = false;
            return false;
        }
        PostBodyBlock block = bodyBlocks.get(activeTouchBlockIndex);
        float blockX = event.getX() - activeTouchBlockLeft;
        float blockY = event.getY() - activeTouchBlockTop;
        boolean handled = dispatchBlockTouch(block, event, blockX, blockY);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || !handled) {
            activeBodyTouchStream = false;
        }
        return handled;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < bodyBlocks.size(); i++) {
            PostBodyBlock block = bodyBlocks.get(i);
            if (block instanceof EmbeddedPostBodyBlock) {
                int blockTop = i < blockTops.length ? blockTops[i] : 0;
                int blockHeight = i < blockHeights.length ? blockHeights[i] : 0;
                int blockLeft = i < blockLefts.length ? blockLefts[i] : 0;
                int blockWidth = i < blockWidths.length ? blockWidths[i] : 0;
                ((EmbeddedPostBodyBlock) block).layoutEmbeddedView(blockLeft, blockTop, blockWidth, blockHeight);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarImage.onAttachedToWindow();
        avatarFlairImage.onAttachedToWindow();
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
        resetTouchState();
        setHiddenImageUrl(null);
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
        avatarFlairImage.onDetachedFromWindow();
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
            final int sourceBlockIndex = activeTouchBlockIndex;
            final int sourceBlockTop = activeTouchBlockTop;
            final int sourceBlockLeft = activeTouchBlockLeft;
            ImageSource activeSource = imageSourceForUrlAtIndex(url, sourceBlockIndex);
            RectF sourceBounds = activeSource != null
                    ? blockLocalRectToWindow(activeSource.bounds, activeSource.blockTop, activeSource.blockLeft)
                    : sourceBoundsInBlock != null ? blockLocalRectToWindow(sourceBoundsInBlock, sourceBlockTop, sourceBlockLeft) : null;
            Function1<String, RectF> windowBoundsProvider = imageUrl -> {
                ImageSource activeBlockSource = imageSourceForUrlAtIndex(imageUrl, sourceBlockIndex);
                if (activeBlockSource != null) {
                    return blockLocalRectToWindow(activeBlockSource.bounds, activeBlockSource.blockTop, activeBlockSource.blockLeft);
                }
                RectF localBounds = sourceBoundsProvider != null ? sourceBoundsProvider.invoke(imageUrl) : null;
                if (localBounds != null) {
                    return blockLocalRectToWindow(localBounds, sourceBlockTop, sourceBlockLeft);
                }
                ImageSource source = imageSourceForUrl(imageUrl);
                return source != null ? blockLocalRectToWindow(source.bounds, source.blockTop, source.blockLeft) : null;
            };
            Function1<String, ImageReceiver> receiverProvider = imageUrl -> {
                ImageSource activeBlockSource = imageSourceForUrlAtIndex(imageUrl, sourceBlockIndex);
                if (activeBlockSource != null && activeBlockSource.receiver != null) {
                    return activeBlockSource.receiver;
                }
                ImageReceiver receiver = sourceImageReceiverProvider != null ? sourceImageReceiverProvider.invoke(imageUrl) : null;
                if (receiver != null) {
                    return receiver;
                }
                ImageSource source = imageSourceForUrl(imageUrl);
                return source != null ? source.receiver : null;
            };
            Function1<String, ImageReceiver.BitmapHolder> bitmapProvider = imageUrl -> {
                ImageSource activeBlockSource = imageSourceForUrlAtIndex(imageUrl, sourceBlockIndex);
                if (activeBlockSource != null) {
                    ImageReceiver.BitmapHolder holder = activeBlockSource.block.imageBitmapForUrl(imageUrl);
                    if (holder != null) {
                        return holder;
                    }
                }
                ImageReceiver.BitmapHolder holder = sourceBitmapProvider != null ? sourceBitmapProvider.invoke(imageUrl) : null;
                if (holder != null) {
                    return holder;
                }
                ImageSource source = imageSourceForUrl(imageUrl);
                return source != null ? source.block.imageBitmapForUrl(imageUrl) : null;
            };
            Function1<String, int[]> radiusProvider = imageUrl -> {
                ImageSource activeBlockSource = imageSourceForUrlAtIndex(imageUrl, sourceBlockIndex);
                if (activeBlockSource != null && activeBlockSource.radius != null) {
                    return activeBlockSource.radius;
                }
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
    public void onPollOptionsSelected(String pollName, List<String> optionIds) {
        if (delegate != null && post != null) {
            delegate.didPressPollOptions(post, pollName, optionIds);
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
        resetTouchState();
        for (PostBodyBlock block : bodyBlocks) {
            View embeddedView = embeddedView(block);
            if (embeddedView != null && embeddedView.getParent() == this) {
                removeView(embeddedView);
            }
            block.onDetachedFromWindow();
        }
        if (bodyContext != null) {
            bodyContext.getImagePool().clear();
        }
        bodyContext = null;
        bodyBlocks = Collections.emptyList();
    }

    private void resetTouchState() {
        cancelCheckLongPress();
        activeBodyTouchStream = false;
        activeTouchBlockIndex = -1;
        activeTouchBlockTop = 0;
        activeTouchBlockLeft = 0;
        if (pressedReactionButton >= 0 && pressedReactionButton < reactionButtons.size()) {
            reactionButtons.get(pressedReactionButton).bounce.setPressed(false);
        }
        pressedReactionButton = -1;
        if (replyFooterPressed || replyBounce.isPressed()) {
            setReplyFooterPressed(false, 0f, 0f);
        }
        didLongPressPost = false;
        ignoreSelectionTouchUntilUp = false;
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
                svgSpriteMarkup,
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
        for (PostBodyBlock block : bodyBlocks) {
            View embeddedView = embeddedView(block);
            if (embeddedView != null && embeddedView.getParent() != this) {
                if (embeddedView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) embeddedView.getParent()).removeView(embeddedView);
                }
                addView(embeddedView);
            }
        }
        if (isAttachedToWindow()) {
            for (PostBodyBlock block : bodyBlocks) {
                block.onAttachedToWindow();
            }
        }
    }

    private static View embeddedView(PostBodyBlock block) {
        return block instanceof EmbeddedPostBodyBlock ? ((EmbeddedPostBodyBlock) block).embeddedView() : null;
    }

    private void invalidateEmbeddedBodyViews() {
        for (PostBodyBlock block : bodyBlocks) {
            View view = embeddedView(block);
            if (view != null) {
                invalidateViewTree(view);
            }
        }
    }

    private static void invalidateViewTree(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateViewTree(group.getChildAt(i));
            }
        }
    }

    private ImageSource imageSourceForUrl(String imageUrl) {
        ensureBodyBlocks();
        for (int i = 0; i < bodyBlocks.size(); i++) {
            ImageSource source = imageSourceForUrlAtIndex(imageUrl, i);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private ImageSource imageSourceForUrlAtIndex(String imageUrl, int blockIndex) {
        ensureBodyBlocks();
        if (blockIndex < 0 || blockIndex >= bodyBlocks.size()) {
            return null;
        }
        PostBodyBlock block = bodyBlocks.get(blockIndex);
        RectF bounds = block.imageBoundsInBlock(imageUrl);
        if (bounds == null) {
            return null;
        }
        ImageReceiver receiver = block.imageReceiverForUrl(imageUrl);
        return new ImageSource(
                block,
                bounds,
                blockIndex < blockTops.length ? blockTops[blockIndex] : 0,
                blockIndex < blockLefts.length ? blockLefts[blockIndex] : 0,
                receiver,
                block.imageRadiusForUrl(imageUrl)
        );
    }

    public RectF imageBoundsInWindowForUrl(String imageUrl) {
        ImageSource source = imageSourceForUrl(imageUrl);
        return source == null ? null : blockLocalRectToWindow(source.bounds, source.blockTop, source.blockLeft);
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
        RectF bounds = blockLocalRectToWindow(source.bounds, source.blockTop, source.blockLeft);
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

    private RectF blockLocalRectToWindow(RectF rect, int blockTop, int blockLeft) {
        int[] location = new int[2];
        getLocationInWindow(location);
        return new RectF(
                location[0] + blockLeft + rect.left,
                location[1] + blockTop + rect.top,
                location[0] + blockLeft + rect.right,
                location[1] + blockTop + rect.bottom
        );
    }

    private void loadAvatar(Post post) {
        String name = post.getAuthorDisplayName().isEmpty() ? post.getAuthorUsername() : post.getAuthorDisplayName();
        avatarDrawable.setInfo((long) post.getAuthorUsername().hashCode(), name);
        avatarDrawable.setRoundRadius(avatarSize / 2);
        String nextAvatarUrl = DiscourseUiHelpersKt.resolveAvatarUrl(baseUrl, post.getAuthorAvatarTemplate(), post.getAuthorAnimatedAvatarUrl(), 96);
        String nextAvatarFilter = DiscourseUiHelpersKt.avatarImageFilter(post.getAuthorAnimatedAvatarUrl(), 50);
        avatarImage.setAllowDecodeSingleFrame(!DiscourseUiHelpersKt.isAnimatedDiscourseImageUrl(nextAvatarUrl));
        if (!nextAvatarUrl.equals(avatarUrl) || !nextAvatarFilter.equals(avatarFilter)) {
            avatarUrl = nextAvatarUrl;
            avatarFilter = nextAvatarFilter;
            if (nextAvatarUrl.isEmpty()) {
                avatarImage.setImage(null, null, avatarDrawable, null, post, 0);
            } else {
                avatarImage.setImage(ImageLocation.getForPath(nextAvatarUrl), nextAvatarFilter, avatarDrawable, null, post, 0);
            }
        }
        loadAvatarFlair(post);
    }

    private void loadAvatarFlair(Post post) {
        String rawFlairUrl = post.getAuthorFlairUrl();
        avatarFlairBadgeColor = parseDiscourseColor(post.getAuthorFlairBgColor(), Theme.getColor(Theme.key_windowBackgroundWhite));
        if (rawFlairUrl != null && DiscourseUiHelpersKt.looksLikeDiscourseImageUrl(rawFlairUrl)) {
            loadAvatarFlairImage(post, rawFlairUrl);
        } else {
            loadAvatarFlairIcon(post, rawFlairUrl);
        }
    }

    private void loadAvatarFlairImage(Post post, String rawFlairUrl) {
        String nextFlairUrl = DiscourseUiHelpersKt.resolveDiscourseUrl(baseUrl, rawFlairUrl);
        String nextFlairFilter = nextFlairUrl.isEmpty() ? null : "14_14";
        avatarFlairIconKey = "";
        avatarFlairIconBitmap = null;
        avatarFlairImage.setAllowDecodeSingleFrame(!DiscourseUiHelpersKt.isAnimatedDiscourseImageUrl(nextFlairUrl));
        if (nextFlairUrl.equals(avatarFlairUrl) && TextUtils.equals(nextFlairFilter, avatarFlairFilter)) {
            return;
        }
        avatarFlairUrl = nextFlairUrl;
        avatarFlairFilter = nextFlairFilter;
        if (nextFlairUrl.isEmpty()) {
            avatarFlairImage.setImage(null, null, null, null, post, 0);
        } else {
            avatarFlairImage.setImage(ImageLocation.getForPath(nextFlairUrl), nextFlairFilter, null, null, post, 0);
        }
    }

    private void loadAvatarFlairIcon(Post post, String rawFlairUrl) {
        avatarFlairUrl = "";
        avatarFlairFilter = null;
        avatarFlairImage.setImage(null, null, null, null, post, 0);
        String normalizedFlairUrl = rawFlairUrl == null ? "" : rawFlairUrl.trim();
        String nextIconKey = !normalizedFlairUrl.isEmpty() && !svgSpriteMarkup.isEmpty()
                ? normalizedFlairUrl + "\n" + post.getAuthorFlairColor() + "\n" + svgSpriteMarkup.hashCode()
                : "";
        if (TextUtils.equals(nextIconKey, avatarFlairIconKey)) {
            return;
        }
        avatarFlairIconKey = nextIconKey;
        avatarFlairIconBitmap = nextIconKey.isEmpty()
                ? null
                : renderAvatarFlairIcon(normalizedFlairUrl, post.getAuthorFlairColor());
    }

    private Bitmap renderAvatarFlairIcon(String rawFlairUrl, String rawTintColor) {
        String svgDocument = DiscourseFlairKt.buildDiscourseFlairSvgDocument(
                svgSpriteMarkup,
                rawFlairUrl,
                rawTintColor == null ? "" : rawTintColor
        );
        if (TextUtils.isEmpty(svgDocument)) {
            return null;
        }
        int size = avatarFlairContentSize();
        return DiscourseFlairKt.renderDiscourseSvgDocumentToBitmap(svgDocument, size, size);
    }

    private void updateAvatarFlairCoords() {
        int badgeSize = AndroidUtilities.dp(16f);
        int contentInset = AndroidUtilities.dp(2f);
        float left = horizontalPadding + avatarSize - badgeSize;
        float top = verticalPadding + avatarSize - badgeSize;
        avatarFlairBadgeRect.set(left, top, left + badgeSize, top + badgeSize);
        int contentSize = avatarFlairContentSize();
        avatarFlairContentRect.set(left + contentInset, top + contentInset, left + contentInset + contentSize, top + contentInset + contentSize);
        avatarFlairImage.setRoundRadius(contentSize / 2);
        avatarFlairImage.setImageCoords(avatarFlairContentRect.left, avatarFlairContentRect.top, contentSize, contentSize);
    }

    private int avatarFlairContentSize() {
        return Math.max(AndroidUtilities.dp(16f) - AndroidUtilities.dp(2f) * 2, AndroidUtilities.dp(8f));
    }

    private void drawAvatarFlair(Canvas canvas) {
        if ((avatarFlairUrl == null || avatarFlairUrl.isEmpty()) && avatarFlairIconBitmap == null) {
            return;
        }
        updateAvatarFlairCoords();
        avatarFlairBadgePaint.setColor(avatarFlairBadgeColor);
        canvas.drawOval(avatarFlairBadgeRect, avatarFlairBadgePaint);
        canvas.drawOval(avatarFlairBadgeRect, avatarFlairBadgeStrokePaint);
        if (avatarFlairIconBitmap != null) {
            canvas.drawBitmap(avatarFlairIconBitmap, null, avatarFlairContentRect, avatarFlairIconPaint);
        } else {
            avatarFlairImage.draw(canvas);
        }
    }

    private void invalidateAvatarArea() {
        int expand = AndroidUtilities.dp(4f);
        invalidate(
                horizontalPadding - expand,
                verticalPadding - expand,
                horizontalPadding + avatarSize + expand,
                verticalPadding + avatarSize + expand
        );
    }

    private void invalidateReplyFooterArea() {
        if (measuredReplyFooterHeight <= 0) {
            invalidate();
            return;
        }
        invalidate(chromeLeft, replyFooterTop, chromeLeft + chromeWidth, replyFooterTop + measuredReplyFooterHeight);
    }

    private static int parseDiscourseColor(String rawHex, int fallback) {
        if (rawHex == null) {
            return fallback;
        }
        String normalized = rawHex.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return fallback;
        }
        if (normalized.length() == 3 || normalized.length() == 4) {
            StringBuilder expanded = new StringBuilder(normalized.length() * 2);
            for (int i = 0; i < normalized.length(); i++) {
                char c = normalized.charAt(i);
                expanded.append(c).append(c);
            }
            normalized = expanded.toString();
        }
        if (normalized.length() == 6) {
            try {
                return Color.parseColor("#" + normalized);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
        if (normalized.length() == 8) {
            String argb = normalized.substring(6) + normalized.substring(0, 6);
            try {
                return (int) Long.parseLong(argb, 16);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private int measureReplyFooter() {
        return replyCount > 0 || !recentReplyParticipants.isEmpty() ? replyFooterHeight : 0;
    }

    private void drawReplyFooter(Canvas canvas) {
        int avatarsWidth = replyAvatarsWidth(replyAvatars.size());
        String label = replyFooterLabel();
        updateReplyFooterSelectorRect();
        float scale = replyBounce.getScale(0.0125f);
        int restoreCount = -1;
        if (scale != 1f) {
            restoreCount = canvas.save();
            canvas.scale(scale, scale, replyFooterSelectorRect.centerX(), replyFooterSelectorRect.centerY());
        }
        int replyColor = replyLine.check(null, null, null, null, ReplyMessageLine.TYPE_REPLY);
        replyLine.drawBackground(canvas, replyFooterSelectorRect, 5, 5, 5, 1f, false, false);
        replyLine.drawLine(canvas, replyFooterSelectorRect, 1f);
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
        replyTextPaint.setColor(replyColor);
        float textY = replyFooterHeight / 2f - (replyTextPaint.ascent() + replyTextPaint.descent()) / 2f;
        canvas.drawText(label, contentLeft + avatarsWidth + AndroidUtilities.dp(8f), textY, replyTextPaint);
        if (restoreCount >= 0) {
            canvas.restoreToCount(restoreCount);
        }
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
            if (replyFooterPressed
                    && (!replyFooterSelectorRect.contains(localX, localY)
                    || Math.abs(localX - replyFooterDownX) > touchSlop
                    || Math.abs(localY - replyFooterDownY) > touchSlop)) {
                setReplyFooterPressed(false, localX, localY);
                return false;
            }
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
            receiver.setDelegate((imageReceiver, set, thumb, memCache) -> invalidateReplyFooterArea());
            receiver.setVisibleInvalidate(this::invalidateReplyFooterArea);
            String url = DiscourseUiHelpersKt.resolveAvatarUrl(baseUrl, participant.getAuthorAvatarTemplate(), participant.getAuthorAnimatedAvatarUrl(), 96);
            receiver.setAllowDecodeSingleFrame(!DiscourseUiHelpersKt.isAnimatedDiscourseImageUrl(url));
            if (url.isEmpty()) {
                receiver.setImage(null, null, fallback, null, participant, 0);
            } else {
                receiver.setImage(ImageLocation.getForPath(url), DiscourseUiHelpersKt.avatarImageFilter(participant.getAuthorAnimatedAvatarUrl(), 50), fallback, null, participant, 0);
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
        float selectorRight = Math.min(AndroidUtilities.dp(10f) + avatarsWidth + AndroidUtilities.dp(8f) + replyTextPaint.measureText(replyFooterLabel()) + AndroidUtilities.dp(12f), chromeWidth);
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
        if (pressed) {
            replyFooterDownX = localX;
            replyFooterDownY = localY;
        }
        if (replyFooterSelector != null) {
            replyFooterSelector.setHotspot(localX, localY);
            replyFooterSelector.setState(pressed ? PRESSED_STATE : StateSet.NOTHING);
        }
        replyBounce.setPressed(pressed);
        invalidate();
    }

    private List<ReactionButton> buildReactionButtons(Post post) {
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
        List<ReactionButton> visible = new ArrayList<>();
        List<ReactionButtonSpec> specs = buildReactionButtonSpecs(source, validReactionIds, emojiCatalog, MAX_VISIBLE_REACTIONS);
        for (ReactionButtonSpec spec : specs) {
            PostReaction reaction = spec.postReaction;
            ImageReceiver iconReceiver = reaction != null ? reactionIconReceiver(reaction) : null;
            visible.add(new ReactionButton(this, reaction, spec.countText, iconReceiver));
        }
        return visible;
    }

    static List<ReactionButtonSpec> buildReactionButtonSpecs(List<PostReaction> sortedReactions, List<String> validReactionIds, Map<String, String> emojiCatalog, int maxVisible) {
        List<ReactionButtonSpec> visible = new ArrayList<>();
        List<PostReaction> source = sortedReactions != null ? sortedReactions : Collections.emptyList();
        int limit = Math.min(maxVisible, source.size());
        for (int i = 0; i < limit; i++) {
            PostReaction reaction = source.get(i);
            boolean hasIcon = reaction.getIconUrl() != null || emojiCatalog != null && emojiCatalog.containsKey(reaction.getId());
            String countText = hasIcon ? String.valueOf(reaction.getCount()) : reactionLabel(reaction) + " " + reaction.getCount();
            visible.add(new ReactionButtonSpec(reaction, countText));
        }
        int hiddenCount = Math.max(source.size() - visible.size(), 0);
        if (hiddenCount > 0) {
            visible.add(new ReactionButtonSpec(null, "+" + hiddenCount));
        } else if (validReactionIds != null && !validReactionIds.isEmpty()) {
            visible.add(new ReactionButtonSpec(null, "+"));
        }
        return visible;
    }

    private int measureReactionRow(int maxWidth) {
        if (reactionButtons.isEmpty()) {
            return 0;
        }
        int currentX = 0;
        int currentY = 0;
        int lineOffset = 0;
        int iconSize = AndroidUtilities.dp(20f);
        int iconGap = AndroidUtilities.dp(4f);
        for (ReactionButton button : reactionButtons) {
            int textWidth = (int) Math.ceil(reactionTextPaint.measureText(button.countText));
            if (button.imageReceiver != null) {
                button.width = Math.max(AndroidUtilities.dp(8f) + iconSize + iconGap + textWidth + AndroidUtilities.dp(8f), AndroidUtilities.dp(34f));
            } else {
                button.width = Math.max(AndroidUtilities.dp(8f) + textWidth + AndroidUtilities.dp(8f), AndroidUtilities.dp(34f));
            }
            button.height = reactionButtonHeight;
            if (currentX > 0 && currentX + button.width > maxWidth) {
                currentX = 0;
                currentY += button.height + reactionButtonSpacing;
                lineOffset++;
            }
            button.x = currentX;
            button.y = currentY;
            button.top = lineOffset;
            if (button.imageReceiver != null) {
                float iconTop = button.y + (button.height - iconSize) / 2f;
                button.drawingImageRect.set(button.x + AndroidUtilities.dp(8f), iconTop, button.x + AndroidUtilities.dp(8f) + iconSize, iconTop + iconSize);
            } else {
                button.drawingImageRect.setEmpty();
            }
            currentX += button.width + reactionButtonSpacing;
        }
        return currentY + reactionButtonHeight;
    }

    private void drawReactionRow(Canvas canvas) {
        float radius = reactionButtonHeight / 2f;
        int selectedBackground = Theme.getColor(Theme.key_chat_inReactionButtonBackground);
        int selectedText = Theme.getColor(Theme.key_chat_inReactionButtonTextSelected);
        int background = Theme.multAlpha(Theme.getColor(Theme.key_chat_inReactionButtonBackground), 0.156f);
        int text = Theme.getColor(Theme.key_chat_inReactionButtonText);
        for (int i = 0; i < reactionButtons.size(); i++) {
            ReactionButton button = reactionButtons.get(i);
            float scale = button.bounce.getScale(0.1f);
            button.isSelected = button.choosen;
            button.lastDrawnBackgroundColor = button.isSelected ? selectedBackground : background;
            button.lastDrawnTextColor = button.isSelected ? selectedText : text;
            reactionButtonPaint.setColor(button.lastDrawnBackgroundColor);
            button.rect.set(button.x, button.y, button.x + button.width, button.y + button.height);
            int restoreCount = -1;
            if (scale != 1f) {
                restoreCount = canvas.save();
                canvas.scale(scale, scale, button.rect.centerX(), button.rect.centerY());
            }
            canvas.drawRoundRect(button.rect, radius, radius, reactionButtonPaint);
            reactionTextPaint.setColor(button.lastDrawnTextColor);
            float textY = button.rect.centerY() - (reactionTextPaint.ascent() + reactionTextPaint.descent()) / 2f;
            if (button.imageReceiver != null) {
                button.imageReceiver.setImageCoords(button.drawingImageRect.left, button.drawingImageRect.top, button.drawingImageRect.width(), button.drawingImageRect.height());
                button.imageReceiver.draw(canvas);
            }
            float textX = button.imageReceiver != null ? button.drawingImageRect.right + AndroidUtilities.dp(4f) : button.x + AndroidUtilities.dp(8f);
            canvas.drawText(button.countText, textX, textY, reactionTextPaint);
            if (restoreCount >= 0) {
                canvas.restoreToCount(restoreCount);
            }
        }
    }

    private boolean handleReactionTouch(MotionEvent event, float localX, float localY) {
        int index = -1;
        for (int i = 0; i < reactionButtons.size(); i++) {
            ReactionButton button = reactionButtons.get(i);
            if (localX > button.x - reactionButtonTouchSlop
                    && localX < button.x + button.width + reactionButtonTouchSlop
                    && localY > button.y - reactionButtonTouchSlop
                    && localY < button.y + button.height + reactionButtonTouchSlop) {
                index = i;
                break;
            }
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (index < 0) {
                return false;
            }
            pressedReactionButton = index;
            pressedReactionDownX = localX;
            pressedReactionDownY = localY;
            reactionButtons.get(index).bounce.setPressed(true);
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (pressedReactionButton >= 0
                    && (pressedReactionButton != index
                    || Math.abs(localX - pressedReactionDownX) > touchSlop
                    || Math.abs(localY - pressedReactionDownY) > touchSlop)) {
                reactionButtons.get(pressedReactionButton).bounce.setPressed(false);
                pressedReactionButton = -1;
                invalidate();
            }
            return pressedReactionButton >= 0;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int released = pressedReactionButton;
            boolean wasPressed = released >= 0;
            if (wasPressed) {
                reactionButtons.get(released).bounce.setPressed(false);
            }
            pressedReactionButton = -1;
            if (wasPressed) {
                invalidate();
            }
            Post currentPost = post;
            if (!wasPressed) {
                return false;
            }
            if (released != index || currentPost == null) {
                return true;
            }
            ReactionButton button = reactionButtons.get(released);
            if (delegate != null) {
                if (button.postReaction != null) {
                    delegate.didPressReaction(currentPost, button.postReaction);
                } else {
                    delegate.didPressMoreReactions(currentPost, sortedReactions, null);
                }
            }
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (pressedReactionButton >= 0) {
                reactionButtons.get(pressedReactionButton).bounce.setPressed(false);
                pressedReactionButton = -1;
                invalidate();
                return true;
            }
            return false;
        }
        return pressedReactionButton >= 0;
    }

    private int spacingBeforeBlock(PostBodyBlock block) {
        String name = block.getClass().getSimpleName();
        if ("ParagraphBlock".equals(name)
                || "HeadingBlock".equals(name)
                || "RuleBlock".equals(name)
                || "QuoteBlock".equals(name)
                || "DetailsBlock".equals(name)
                || "CodeBlock".equals(name)
                || "CalloutBlock".equals(name)) {
            return 0;
        }
        if ("ImageBlock".equals(name) || "CollageBlock".equals(name)) {
            return 0;
        }
        if ("OneboxBlock".equals(name) || "PollBlock".equals(name) || "TableBlock".equals(name)) {
            return AndroidUtilities.dp(12f);
        }
        return blockSpacing;
    }

    private static String reactionLabel(PostReaction reaction) {
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
        CharSequence layoutSource = Emoji.replaceEmoji(source, paint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BASELINE);
        return StaticLayout.Builder.obtain(layoutSource, 0, layoutSource.length(), paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build();
    }

    private String blankFallback(String html) {
        return html == null || html.isEmpty() ? "<p>（空内容）</p>" : html;
    }

    static class ReactionButtonSpec {
        final PostReaction postReaction;
        final String countText;

        ReactionButtonSpec(PostReaction postReaction, String countText) {
            this.postReaction = postReaction;
            this.countText = countText;
        }
    }

    private static class ReactionButton {
        final PostReaction postReaction;
        final String key;
        final String countText;
        final int count;
        final int realCount;
        final boolean choosen;
        boolean isSelected;
        int x;
        int y;
        int top;
        int width;
        int height;
        int lastDrawnTextColor;
        int lastDrawnBackgroundColor;
        final ImageReceiver imageReceiver;
        final RectF rect = new RectF();
        final RectF drawingImageRect = new RectF();
        final ButtonBounce bounce;

        ReactionButton(View parentView, PostReaction reaction, String countText, ImageReceiver imageReceiver) {
            postReaction = reaction;
            key = reaction != null ? reaction.getId() : "more";
            count = reaction != null ? reaction.getCount() : 0;
            realCount = count;
            choosen = reaction != null && reaction.getReacted();
            this.countText = countText;
            this.imageReceiver = imageReceiver;
            this.bounce = new ButtonBounce(parentView);
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
        final int blockLeft;
        final ImageReceiver receiver;
        final int[] radius;

        ImageSource(PostBodyBlock block, RectF bounds, int blockTop, int blockLeft, ImageReceiver receiver, int[] radius) {
            this.block = block;
            this.bounds = bounds;
            this.blockTop = blockTop;
            this.blockLeft = blockLeft;
            this.receiver = receiver;
            this.radius = radius;
        }
    }
}

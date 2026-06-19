/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's ChatMessageCell poll button
 * measurement, drawing, gesture, and result-animation patterns.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic.body;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import io.github.baba983.nativedo.core.html.CookedPostBlock;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.poll.buttons.PollButtonDrawable;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

final class PollBlock implements PostBodyBlock {
    private static final int[] PRESSED_STATE = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    static final int POLL_ANIMATION_DURATION_MS = 300;
    static final float CHECK_STROKE_WIDTH_DP = 1.833f;
    static final float FOOTER_TEXT_SIZE_DP = 13f;
    static final float MIN_POLL_WIDTH_DP = 120f;
    static final float OPTION_TEXT_LEFT_DP = 35f;
    static final float CONTENT_RIGHT_INSET_DP = 18f;
    static final float CONTENT_MIN_WIDTH_DP = 80f;
    static final float POLL_VERTICAL_PADDING_DP = 12f;
    static final float STATUS_TEXT_GAP_DP = 8f;
    static final float TITLE_MIN_WIDTH_DP = 60f;
    static final float STATUS_MIN_WIDTH_DP = 40f;
    static final float TITLE_TO_OPTIONS_GAP_DP = 12f;
    static final float RESULT_MIN_PROGRESS_DP = 5f;
    static final float OPTION_MIN_TEXT_WIDTH_DP = 60f;
    static final float OPTION_MIN_TITLE_HEIGHT_DP = 24f;
    static final float OPTION_RESULT_EXTRA_HEIGHT_DP = 14f;
    static final float OPTION_DEFAULT_EXTRA_HEIGHT_DP = 8f;
    static final float OPTION_VOTE_CONTROL_LEFT_DP = 35f;
    static final float SELECTOR_HORIZONTAL_INSET_DP = 8f;
    static final float SELECTOR_VERTICAL_INSET_DP = 13f;
    static final float OPTION_SPACING_DP = 8f;
    static final float SUBMIT_BUTTON_HEIGHT_DP = 34f;
    static final float SUBMIT_BUTTON_RADIUS_DP = 6f;
    static final float FOOTER_TOP_GAP_DP = 2f;
    static final float RESULT_LINE_TOP_GAP_DP = 7f;
    static final float RESULT_LINE_HEIGHT_DP = 5f;
    static final float TITLE_CLIP_VERTICAL_INSET_DP = 2f;
    static final float PERCENT_X_OFFSET_DP = 6.5f;
    static final float RESULT_LINE_WIDTH_INSET_DP = 23f;
    static final float POLL_BUTTON_LEFT_EXTRA_DP = 9f;
    static final float POLL_BUTTON_RIGHT_EXTRA_DP = 1f;
    static final float VOTE_CONTROL_CENTER_X_OFFSET_DP = 22f;
    static final float VOTE_CONTROL_CENTER_Y_DP = 9f;
    static final float MULTI_CHOICE_RADIUS_DP = 8f;
    static final float SINGLE_CHOICE_RADIUS_DP = 8.5f;
    static final float MULTI_CHOICE_CORNER_RADIUS_DP = 4f;
    static final float MULTI_CHOICE_CHECK_SIZE_DP = 8f;
    static final float SINGLE_CHOICE_CHECK_SIZE_DP = 8.5f;
    static final float RESULT_CHECK_CENTER_X_OFFSET_DP = 15.5f;
    static final float RESULT_CHECK_CENTER_Y_OFFSET_DP = 2.5f;
    static final float RESULT_CHECK_SIZE_DP = 7f;
    static final float CHECKBOX_CUSTOM_RADIUS_DP = 5f;
    static final int MULTI_CHOICE_CHECKBOX_SIZE = 19;
    static final int SINGLE_CHOICE_CHECKBOX_SIZE = 20;

    private static final class PollButton {
        final CookedPostBlock.PollOption answer;
        StaticLayout title;
        BuiltText titleText;
        BuiltText.Handle titleHandle;
        StaticLayout percentLayout;
        final RectF selectorBounds = new RectF();
        int selectorDrawableColor;
        Drawable selectorDrawable;
        final ArrayList<SpoilerEffect> spoilers = new ArrayList<>();
        final Stack<SpoilerEffect> spoilersPool = new Stack<>();
        final Path spoilerBoundsPath = new Path();
        final Path spoilerRipplePath = new Path();
        final ArticleLinkPressState linkPressState = new ArticleLinkPressState();
        int x;
        int y;
        int height;
        int percent;
        int prevPercent;
        float percentProgress;
        float prevPercentProgress;
        boolean chosen;
        boolean prevChosen;
        int count;
        float titleX;
        float titleY;
        boolean spoilersRevealed;
        boolean pressedSpoiler;
        float downX;
        float downY;
        PollButtonDrawable pollButtonDrawable;
        CheckBoxBase checkBox;

        PollButton(CookedPostBlock.PollOption answer) {
            this.answer = answer;
        }

        void detachTitle() {
            if (titleHandle != null) {
                titleHandle.detach();
                titleHandle = null;
            }
            linkPressState.reset();
            pressedSpoiler = false;
        }

        void attach() {
            if (checkBox != null) {
                checkBox.onAttachedToWindow();
            }
            if (pollButtonDrawable != null) {
                pollButtonDrawable.attach();
            }
        }

        void detach() {
            if (checkBox != null) {
                checkBox.onDetachedFromWindow();
            }
            if (pollButtonDrawable != null) {
                pollButtonDrawable.detach();
            }
        }

        void attachTitle(android.view.View host) {
            if (titleText != null && titleHandle == null) {
                titleHandle = titleText.attach(host);
            }
            for (int i = 0; i < spoilers.size(); i++) {
                spoilers.get(i).setParentView(host);
            }
        }

        boolean hasPressedRichText() {
            return linkPressState.isPressed() || pressedSpoiler;
        }

        boolean onTitleRichTextTouch(MotionEvent event, float localX, float localY, PostBodyBuildContext context, int touchSlop, Runnable onRevealed) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (linkPressState.onTouch(event, localX, localY, title, titleText, context, touchSlop, context.getHost()::invalidate)) {
                        return true;
                    }
                    if (!SpoilerTextLayout.contains(spoilers, localX, localY)) {
                        return false;
                    }
                    pressedSpoiler = true;
                    downX = localX;
                    downY = localY;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (linkPressState.isPressed()) {
                        return linkPressState.onTouch(event, localX, localY, title, titleText, context, touchSlop, context.getHost()::invalidate);
                    }
                    if (!pressedSpoiler) {
                        return false;
                    }
                    if (Math.abs(localX - downX) > touchSlop || Math.abs(localY - downY) > touchSlop) {
                        pressedSpoiler = false;
                        return false;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (linkPressState.isPressed()) {
                        return linkPressState.onTouch(event, localX, localY, title, titleText, context, touchSlop, context.getHost()::invalidate);
                    }
                    if (!pressedSpoiler) {
                        return false;
                    }
                    pressedSpoiler = false;
                    return SpoilerTextLayout.reveal(context.getHost(), title, spoilersPool, spoilers, localX, localY, () -> {
                        spoilersRevealed = true;
                        onRevealed.run();
                    });
                case MotionEvent.ACTION_CANCEL:
                    if (linkPressState.isPressed()) {
                        return linkPressState.onTouch(event, localX, localY, title, titleText, context, touchSlop, context.getHost()::invalidate);
                    }
                    if (pressedSpoiler) {
                        pressedSpoiler = false;
                        return true;
                    }
                    return false;
                default:
                    return hasPressedRichText();
            }
        }

        void drawTitle(Canvas canvas, Runnable invalidate) {
            linkPressState.draw(canvas, invalidate);
            SpoilerTextLayout.draw(canvas, title, spoilers, spoilerBoundsPath, spoilerRipplePath);
        }
    }

    private static final class PollPercentCandidate {
        final int index;
        int percent;
        float decimal;

        PollPercentCandidate(int index, float percent) {
            this.index = index;
            float clamped = Math.max(0f, Math.min(100f, percent));
            this.percent = clamp((int) clamped, 0, 100);
            this.decimal = clamped - this.percent;
        }
    }

    private final CookedPostBlock.Poll block;
    private final PostBodyBuildContext context;
    private final PostBodyBlockState state;
    private final PostBodyBlockState inlineState;
    private final int stateIndex;
    private final String inlineStateKey;
    private final Paint resultLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint;
    private final TextPaint statusPaint;
    private final TextPaint optionPaint;
    private final TextPaint percentPaint;
    private final TextPaint footerPaint;
    private final AnimatedFloat pollAnimationProgress;
    private final PostBodyTextBuilder textBuilder;
    private final int touchSlop;
    private final ArrayList<PollButton> pollButtons = new ArrayList<>();
    private StaticLayout titleLayout;
    private StaticLayout statusLayout;
    private StaticLayout footerLayout;
    private StaticLayout submitLayout;
    private final RectF submitBounds = new RectF();
    private int measuredHeight;
    private int contentWidth;
    private int buttonLeft;
    private int pressedVoteButton = -1;
    private boolean submitPressed;
    private int activeRichTextButtonIndex = -1;
    private boolean lastShowResults;

    PollBlock(CookedPostBlock.Poll block, PostBodyBuildContext context, PostBodyBlockState state, int stateIndex) {
        this(block, context, state, stateIndex, state, "poll:" + stateIndex);
    }

    PollBlock(CookedPostBlock.Poll block, PostBodyBuildContext context, PostBodyBlockState state, int stateIndex, PostBodyBlockState inlineState, String inlineStateKey) {
        this.block = block;
        this.context = context;
        this.state = state;
        this.inlineState = inlineState;
        this.stateIndex = stateIndex;
        this.inlineStateKey = inlineStateKey;
        resultLinePaint.setColor(context.getPollBarColor());
        selectedLinePaint.setColor(Theme.multAlpha(context.getAccentColor(), 0.36f));
        pressedPaint.setColor(Theme.multAlpha(context.getAccentColor(), 0.10f));
        checkStrokePaint.setStyle(Paint.Style.STROKE);
        checkStrokePaint.setStrokeWidth(checkStrokeWidthPx());
        titlePaint = new TextPaint(context.getParagraphPaint());
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        statusPaint = new TextPaint(context.getQuotePaint());
        statusPaint.setColor(context.getAccentColor());
        statusPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        optionPaint = new TextPaint(context.getQuotePaint());
        percentPaint = new TextPaint(context.getQuotePaint());
        percentPaint.setColor(context.getSecondaryTextColor());
        percentPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        footerPaint = new TextPaint(context.getQuotePaint());
        footerPaint.setColor(context.getSecondaryTextColor());
        footerPaint.setTextSize(footerTextSizePx());
        pollAnimationProgress = new AnimatedFloat(0f, context.getHost(), 0, pollAnimationDurationMs(), CubicBezierInterpolator.EASE_OUT);
        textBuilder = new PostBodyTextBuilder(context);
        touchSlop = ViewConfiguration.get(context.getHost().getContext()).getScaledTouchSlop();
    }

    @Override
    public int horizontalInsetPx() {
        return 0;
    }
    @Override
    public int measure(int maxWidthPx) {
        int width = Math.max(maxWidthPx, minPollWidthPx());
        buttonLeft = optionTextLeftPx();
        contentWidth = Math.max(width - buttonLeft - contentRightInsetPx(), contentMinWidthPx());
        List<CookedPostBlock.PollOption> options = block.getOptions().isEmpty()
                ? Collections.singletonList(new CookedPostBlock.PollOption(null, "暂无选项", null, null, false, null))
                : block.getOptions();
        ensureButtons(options);
        boolean showResults = shouldShowResults();
        if (showResults != lastShowResults) {
            lastShowResults = showResults;
        }
        float animationProgress = pollAnimationProgress.set(showResults ? 1f : 0f);

        int padding = pollVerticalPaddingPx();
        int statusWidth = statusText().isEmpty() ? 0 : (int) Math.ceil(statusPaint.measureText(statusText())) + statusTextGapPx();
        int titleWidth = Math.max(contentWidth - statusWidth, titleMinWidthPx());
        String title = block.getName() != null && !block.getName().trim().isEmpty() ? block.getName() : "投票";
        titleLayout = layout(title, titlePaint, titleWidth, 2);
        statusLayout = statusText().isEmpty() ? null : layout(statusText(), statusPaint, Math.max(statusWidth, statusMinWidthPx()), 1);
        int y = padding + Math.max(titleLayout != null ? titleLayout.getHeight() : 0, statusLayout != null ? statusLayout.getHeight() : 0) + titleToOptionsGapPx();

        int[] percents = optionPercents(options);
        for (int i = 0; i < pollButtons.size(); i++) {
            PollButton button = pollButtons.get(i);
            ensureButtonChrome(button);
            button.prevPercent = button.percent;
            button.prevPercentProgress = button.percentProgress;
            button.prevChosen = button.chosen;
            button.percent = percents[i];
            button.percentProgress = Math.max(resultMinProgressPx() / (float) Math.max(contentWidth, 1), button.percent / 100f);
            button.chosen = isChosen(button.answer, i);
            button.checkBox.setChecked(button.chosen, button.prevChosen != button.chosen);
            button.checkBox.checkScale = showResults ? 1f : 0.85f;
            button.count = button.answer.getVotes() != null ? button.answer.getVotes() : 0;
            button.pollButtonDrawable.setVotersVisible(showResults, false);
            button.pollButtonDrawable.setVotersCount(button.count, false);
            int textWidth = Math.max(contentWidth, optionMinTextWidthPx());
            buildButtonTitle(button, textWidth);
            button.percentLayout = showResults ? percentLayout(button.percent + "%") : null;
            button.y = y;
            button.height = Math.max(button.title.getHeight(), optionMinTitleHeightPx()) + (showResults ? optionResultExtraHeightPx() : optionDefaultExtraHeightPx());
            button.x = buttonLeft - optionVoteControlLeftPx();
            button.titleX = buttonLeft;
            button.titleY = y;
            button.selectorBounds.set(
                    button.x - selectorHorizontalInsetPx(),
                    y - selectorVerticalInsetPx(),
                    buttonLeft + contentWidth,
                    y + button.height + selectorVerticalInsetPx()
            );
            y += button.height + optionSpacingPx();
        }

        if (shouldShowMultiSelectSubmitButton()) {
            submitLayout = layout("提交投票", statusPaint, contentWidth, 1);
            int submitHeight = submitButtonHeightPx();
            submitBounds.set(buttonLeft, y, buttonLeft + contentWidth, y + submitHeight);
            y += submitHeight + optionSpacingPx();
        } else {
            submitLayout = null;
            submitBounds.setEmpty();
        }

        footerLayout = layout(footerText(), footerPaint, contentWidth, 2);
        y += footerTopGapPx() + (footerLayout != null ? footerLayout.getHeight() : 0);
        measuredHeight = y + padding;
        if (animationProgress > 0f && animationProgress < 1f) {
            context.getHost().invalidate();
        }
        return measuredHeight;
    }

    @Override
    public void draw(Canvas canvas, int parentWidth) {
        float progress = pollAnimationProgress.set(shouldShowResults() ? 1f : 0f);
        int padding = pollVerticalPaddingPx();
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(buttonLeft, padding);
            titleLayout.draw(canvas);
            canvas.restore();
        }
        if (statusLayout != null) {
            canvas.save();
            canvas.translate(buttonLeft + contentWidth - statusLayout.getWidth(), padding);
            statusLayout.draw(canvas);
            canvas.restore();
        }
        for (int i = 0; i < pollButtons.size(); i++) {
            drawButton(canvas, pollButtons.get(i), i, progress);
        }
        drawSubmitButton(canvas);
        if (footerLayout != null) {
            canvas.save();
            canvas.translate(buttonLeft, measuredHeight - padding - footerLayout.getHeight());
            footerLayout.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks, int x, int y) {
        int padding = pollVerticalPaddingPx();
        if (titleLayout != null) {
            blocks.add(new PostSelectionLayoutBlock(titleLayout, x + buttonLeft, y + padding, 0, null));
        }
        if (statusLayout != null) {
            blocks.add(new PostSelectionLayoutBlock(statusLayout, x + buttonLeft + contentWidth - statusLayout.getWidth(), y + padding, 0, null));
        }
        if (submitLayout != null) {
            blocks.add(new PostSelectionLayoutBlock(
                    submitLayout,
                    x + (int) (submitBounds.left + (submitBounds.width() - submitLayout.getWidth()) / 2f),
                    y + (int) (submitBounds.top + (submitBounds.height() - submitLayout.getHeight()) / 2f),
                    0,
                    null
            ));
        }
        for (int i = 0; i < pollButtons.size(); i++) {
            PollButton button = pollButtons.get(i);
            int row = i + 1;
            if (button.title != null) {
                blocks.add(new PostSelectionLayoutBlock(button.title, x + (int) button.titleX, y + (int) button.titleY, row, null));
            }
            if (button.percentLayout != null) {
                blocks.add(new PostSelectionLayoutBlock(button.percentLayout, x + percentLayoutX(button.percentLayout), y + (int) button.titleY, row, null));
            }
        }
        if (footerLayout != null) {
            blocks.add(new PostSelectionLayoutBlock(footerLayout, x + buttonLeft, y + measuredHeight - padding - footerLayout.getHeight(), 0, null));
        }
    }

    @Override
    public boolean onTouch(MotionEvent event, float localX, float localY) {
        int action = event.getActionMasked();
        if (activeRichTextButtonIndex >= 0 && action != MotionEvent.ACTION_DOWN) {
            PollButton button = pollButtons.get(activeRichTextButtonIndex);
            boolean handled = button.onTitleRichTextTouch(event, localX - button.titleX, localY - button.titleY, context, touchSlop, () -> markButtonSpoilersRevealed(button));
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || !handled) {
                activeRichTextButtonIndex = -1;
            }
            return handled;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            for (int i = 0; i < pollButtons.size(); i++) {
                PollButton button = pollButtons.get(i);
                if (button.title != null
                        && localX >= button.titleX
                        && localX <= button.titleX + button.title.getWidth()
                        && localY >= button.titleY
                        && localY <= button.titleY + button.title.getHeight()
                        && button.onTitleRichTextTouch(event, localX - button.titleX, localY - button.titleY, context, touchSlop, () -> markButtonSpoilersRevealed(button))) {
                    activeRichTextButtonIndex = i;
                    return true;
                }
            }
        }
        int index = buttonIndexAt(localX, localY);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (submitBounds.contains(localX, localY)) {
                    if (!canSubmitMultiSelectVote()) {
                        return false;
                    }
                    submitPressed = true;
                    context.getHost().invalidate();
                    return true;
                }
                if (index < 0 || !canVote(index)) {
                    return false;
                }
                pressedVoteButton = index;
                setPollButtonPressed(index, true, localX, localY);
                context.getHost().invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (submitPressed) {
                    if (!submitBounds.contains(localX, localY)) {
                        submitPressed = false;
                        context.getHost().invalidate();
                    }
                    return true;
                }
                if (pressedVoteButton >= 0 && pressedVoteButton != index) {
                    setPollButtonPressed(pressedVoteButton, false, localX, localY);
                    pressedVoteButton = -1;
                    context.getHost().invalidate();
                } else if (pressedVoteButton >= 0) {
                    setPollButtonHotspot(pressedVoteButton, localX, localY);
                }
                return pressedVoteButton >= 0;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (submitPressed) {
                    boolean submitWasPressed = event.getActionMasked() == MotionEvent.ACTION_UP && submitBounds.contains(localX, localY);
                    submitPressed = false;
                    context.getHost().invalidate();
                    if (submitWasPressed) {
                        submitMultiSelectVote();
                    }
                    return true;
                }
                int released = pressedVoteButton;
                boolean wasPressed = released >= 0;
                if (wasPressed) {
                    setPollButtonPressed(released, false, localX, localY);
                }
                pressedVoteButton = -1;
                if (wasPressed) {
                    context.getHost().invalidate();
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP && wasPressed && released == index && canVote(released)) {
                    if (state != null) {
                        updateLocalSelection(released);
                        syncButtonChoiceState(released, true);
                    }
                    if (isMultipleChoice()) {
                        context.getHost().invalidate();
                    } else {
                        List<String> optionIds = selectedOptionIdsForVote(released);
                        if (!optionIds.isEmpty()) {
                            markLocalSubmitted();
                            context.getDelegate().onPollOptionsSelected(block.getName(), optionIds);
                        }
                    }
                }
                return wasPressed;
            default:
                return false;
        }
    }

    @Override
    public RectF imageBoundsInBlock(String imageUrl) {
        return null;
    }

    @Override
    public ImageReceiver imageReceiverForUrl(String imageUrl) {
        return null;
    }

    @Override
    public ImageReceiver.BitmapHolder imageBitmapForUrl(String imageUrl) {
        return null;
    }

    @Override
    public int[] imageRadiusForUrl(String imageUrl) {
        return null;
    }

    @Override
    public void onAttachedToWindow() {
        for (PollButton button : pollButtons) {
            button.attachTitle(context.getHost());
            button.attach();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        activeRichTextButtonIndex = -1;
        if (pressedVoteButton >= 0) {
            setPollButtonPressed(pressedVoteButton, false, 0f, 0f);
        }
        pressedVoteButton = -1;
        submitPressed = false;
        for (PollButton button : pollButtons) {
            button.detachTitle();
            button.detach();
        }
    }

    @Override
    public void applyTheme() {
        for (PollButton button : pollButtons) {
            if (button.pollButtonDrawable != null) {
                button.pollButtonDrawable.setVotersCountTextColor(context.getSecondaryTextColor());
            }
        }
        context.getHost().invalidate();
    }

    private void drawButton(Canvas canvas, PollButton button, int index, float pollProgress) {
        boolean showResults = shouldShowResults();
        boolean chosen = button.chosen;
        drawButtonSelector(canvas, button);
        if (showResults) {
            int currentPercent = clamp(Math.round(button.prevPercent + (button.percent - button.prevPercent) * pollProgress), 0, 100);
            float currentProgress = button.prevPercentProgress + (button.percentProgress - button.prevPercentProgress) * pollProgress;
            int lineLeft = buttonLeft;
            int lineRight = lineLeft + resultLineWidth();
            int lineTop = button.y + button.title.getHeight() + resultLineTopGapPx();
            int lineHeight = resultLineHeightPx();
            resultLinePaint.setAlpha(255);
            canvas.drawRoundRect(lineLeft, lineTop, lineRight, lineTop + lineHeight, lineHeight / 2f, lineHeight / 2f, resultLinePaint);
            RectF selected = new RectF(lineLeft, lineTop, lineLeft + Math.max(resultLineMinWidthPx(), (lineRight - lineLeft) * currentProgress), lineTop + lineHeight);
            selectedLinePaint.setAlpha(chosen ? 255 : 160);
            canvas.drawRoundRect(selected, lineHeight / 2f, lineHeight / 2f, chosen ? selectedLinePaint : resultLinePaint);
            if (button.percentLayout != null) {
                StaticLayout percent = percentLayout(currentPercent + "%");
                canvas.save();
                canvas.translate(percentLayoutX(percent), button.titleY);
                percent.draw(canvas);
                canvas.restore();
            }
            if (button.pollButtonDrawable != null && button.count > 0) {
                button.pollButtonDrawable.setBounds(
                        pollButtonLeft(),
                        button.y - selectorVerticalInsetPx(),
                        pollButtonRight(),
                        button.y + button.height + selectorVerticalInsetPx()
                );
                button.pollButtonDrawable.setVotersCountTextColor(context.getSecondaryTextColor());
                button.pollButtonDrawable.draw(canvas);
            }
        }
        if (button.title != null) {
            canvas.save();
            int textWidth = contentWidth;
            canvas.clipRect(0, -titleClipVerticalInsetPx(), textWidth, button.title.getHeight() + titleClipVerticalInsetPx());
            canvas.translate(button.titleX, button.titleY);
            button.drawTitle(canvas, context.getHost()::invalidate);
            canvas.restore();
        }
        if (showResults) {
            if (chosen || button.prevChosen || button.checkBox != null && button.checkBox.getProgress() > 0f) {
                drawResultCheck(canvas, button);
            }
        } else {
            drawVoteControl(canvas, button);
        }
    }

    private void drawButtonSelector(Canvas canvas, PollButton button) {
        ensureButtonSelector(button);
        int selectorColor = Theme.multAlpha(context.getSecondaryTextColor(), 0.12f);
        if (button.selectorDrawableColor != selectorColor) {
            button.selectorDrawableColor = selectorColor;
            Theme.setSelectorDrawableColor(button.selectorDrawable, selectorColor, false);
        }
        button.selectorDrawable.setBounds(
                (int) button.selectorBounds.left,
                (int) button.selectorBounds.top,
                (int) button.selectorBounds.right,
                (int) button.selectorBounds.bottom
        );
        button.selectorDrawable.draw(canvas);
    }

    private void drawSubmitButton(Canvas canvas) {
        if (submitLayout == null || submitBounds.isEmpty()) {
            return;
        }
        boolean enabled = canSubmitMultiSelectVote();
        int backgroundColor = enabled
                ? Theme.multAlpha(context.getAccentColor(), submitPressed ? 0.22f : 0.12f)
                : Theme.multAlpha(context.getSecondaryTextColor(), 0.10f);
        int textColor = enabled ? context.getAccentColor() : context.getSecondaryTextColor();
        pressedPaint.setColor(backgroundColor);
        statusPaint.setColor(textColor);
        canvas.drawRoundRect(submitBounds, submitButtonRadiusPx(), submitButtonRadiusPx(), pressedPaint);
        canvas.save();
        canvas.translate(
                submitBounds.left + (submitBounds.width() - submitLayout.getWidth()) / 2f,
                submitBounds.top + (submitBounds.height() - submitLayout.getHeight()) / 2f
        );
        submitLayout.draw(canvas);
        canvas.restore();
        statusPaint.setColor(context.getAccentColor());
        pressedPaint.setColor(Theme.multAlpha(context.getAccentColor(), 0.10f));
    }

    private int percentLayoutX(StaticLayout layout) {
        return Math.round(buttonLeft - percentXOffsetPx() - layout.getWidth());
    }

    private int resultLineWidth() {
        return Math.max(contentWidth - resultLineWidthInsetPx(), resultLineMinWidthPx());
    }

    private int pollButtonLeft() {
        return buttonLeft - optionVoteControlLeftPx() - pollButtonLeftExtraPx();
    }

    private int pollButtonRight() {
        return buttonLeft + contentWidth + pollButtonRightExtraPx();
    }

    static int pollAnimationDurationMs() {
        return POLL_ANIMATION_DURATION_MS;
    }

    static int checkStrokeWidthPx() {
        return AndroidUtilities.dp(CHECK_STROKE_WIDTH_DP);
    }

    static int footerTextSizePx() {
        return AndroidUtilities.dp(FOOTER_TEXT_SIZE_DP);
    }

    static int minPollWidthPx() {
        return AndroidUtilities.dp(MIN_POLL_WIDTH_DP);
    }

    static int optionTextLeftPx() {
        return AndroidUtilities.dp(OPTION_TEXT_LEFT_DP);
    }

    static int contentRightInsetPx() {
        return AndroidUtilities.dp(CONTENT_RIGHT_INSET_DP);
    }

    static int contentMinWidthPx() {
        return AndroidUtilities.dp(CONTENT_MIN_WIDTH_DP);
    }

    static int pollVerticalPaddingPx() {
        return AndroidUtilities.dp(POLL_VERTICAL_PADDING_DP);
    }

    static int statusTextGapPx() {
        return AndroidUtilities.dp(STATUS_TEXT_GAP_DP);
    }

    static int titleMinWidthPx() {
        return AndroidUtilities.dp(TITLE_MIN_WIDTH_DP);
    }

    static int statusMinWidthPx() {
        return AndroidUtilities.dp(STATUS_MIN_WIDTH_DP);
    }

    static int titleToOptionsGapPx() {
        return AndroidUtilities.dp(TITLE_TO_OPTIONS_GAP_DP);
    }

    static int resultMinProgressPx() {
        return AndroidUtilities.dp(RESULT_MIN_PROGRESS_DP);
    }

    static int optionMinTextWidthPx() {
        return AndroidUtilities.dp(OPTION_MIN_TEXT_WIDTH_DP);
    }

    static int optionMinTitleHeightPx() {
        return AndroidUtilities.dp(OPTION_MIN_TITLE_HEIGHT_DP);
    }

    static int optionResultExtraHeightPx() {
        return AndroidUtilities.dp(OPTION_RESULT_EXTRA_HEIGHT_DP);
    }

    static int optionDefaultExtraHeightPx() {
        return AndroidUtilities.dp(OPTION_DEFAULT_EXTRA_HEIGHT_DP);
    }

    static int optionVoteControlLeftPx() {
        return AndroidUtilities.dp(OPTION_VOTE_CONTROL_LEFT_DP);
    }

    static int selectorHorizontalInsetPx() {
        return AndroidUtilities.dp(SELECTOR_HORIZONTAL_INSET_DP);
    }

    static int selectorVerticalInsetPx() {
        return AndroidUtilities.dp(SELECTOR_VERTICAL_INSET_DP);
    }

    static int optionSpacingPx() {
        return AndroidUtilities.dp(OPTION_SPACING_DP);
    }

    static int submitButtonHeightPx() {
        return AndroidUtilities.dp(SUBMIT_BUTTON_HEIGHT_DP);
    }

    static int submitButtonRadiusPx() {
        return AndroidUtilities.dp(SUBMIT_BUTTON_RADIUS_DP);
    }

    static int footerTopGapPx() {
        return AndroidUtilities.dp(FOOTER_TOP_GAP_DP);
    }

    static int resultLineTopGapPx() {
        return AndroidUtilities.dp(RESULT_LINE_TOP_GAP_DP);
    }

    static int resultLineHeightPx() {
        return AndroidUtilities.dp(RESULT_LINE_HEIGHT_DP);
    }

    static int resultLineMinWidthPx() {
        return AndroidUtilities.dp(RESULT_MIN_PROGRESS_DP);
    }

    static int titleClipVerticalInsetPx() {
        return AndroidUtilities.dp(TITLE_CLIP_VERTICAL_INSET_DP);
    }

    static int percentXOffsetPx() {
        return AndroidUtilities.dp(PERCENT_X_OFFSET_DP);
    }

    static int resultLineWidthInsetPx() {
        return AndroidUtilities.dp(RESULT_LINE_WIDTH_INSET_DP);
    }

    static int pollButtonLeftExtraPx() {
        return AndroidUtilities.dp(POLL_BUTTON_LEFT_EXTRA_DP);
    }

    static int pollButtonRightExtraPx() {
        return AndroidUtilities.dp(POLL_BUTTON_RIGHT_EXTRA_DP);
    }

    static int voteControlCenterXOffsetPx() {
        return AndroidUtilities.dp(VOTE_CONTROL_CENTER_X_OFFSET_DP);
    }

    static int voteControlCenterYPx() {
        return AndroidUtilities.dp(VOTE_CONTROL_CENTER_Y_DP);
    }

    static int multiChoiceRadiusPx() {
        return AndroidUtilities.dp(MULTI_CHOICE_RADIUS_DP);
    }

    static int singleChoiceRadiusPx() {
        return AndroidUtilities.dp(SINGLE_CHOICE_RADIUS_DP);
    }

    static int multiChoiceCornerRadiusPx() {
        return AndroidUtilities.dp(MULTI_CHOICE_CORNER_RADIUS_DP);
    }

    static int multiChoiceCheckSizePx() {
        return AndroidUtilities.dp(MULTI_CHOICE_CHECK_SIZE_DP);
    }

    static int singleChoiceCheckSizePx() {
        return AndroidUtilities.dp(SINGLE_CHOICE_CHECK_SIZE_DP);
    }

    static int resultCheckCenterXOffsetPx() {
        return AndroidUtilities.dp(RESULT_CHECK_CENTER_X_OFFSET_DP);
    }

    static int resultCheckCenterYOffsetPx() {
        return AndroidUtilities.dp(RESULT_CHECK_CENTER_Y_OFFSET_DP);
    }

    static int resultCheckSizePx() {
        return AndroidUtilities.dp(RESULT_CHECK_SIZE_DP);
    }

    static int checkboxCustomRadiusPx() {
        return AndroidUtilities.dp(CHECKBOX_CUSTOM_RADIUS_DP);
    }

    private StaticLayout percentLayout(String text) {
        return layout(text, percentPaint, (int) Math.ceil(percentPaint.measureText(text)) + 1, 1);
    }

    private void buildButtonTitle(PollButton button, int textWidth) {
        if (button.titleHandle != null) {
            button.titleHandle.detach();
            button.titleHandle = null;
        }
        String html = pollOptionLabelHtml(button.answer);
        button.titleText = textBuilder.build(html, optionPaint);
        CharSequence source = button.titleText.getSpannable();
        button.title = ArticleTextLayout.builder(source, optionPaint, textWidth)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        String spoilerKey = pollOptionSpoilerStateKey(button.answer, html);
        button.spoilersRevealed = inlineState != null && inlineState.getRevealedInlineSpoilersByKey().contains(spoilerKey);
        SpoilerTextLayout.collect(context.getHost(), button.title, button.spoilersPool, button.spoilers, button.spoilersRevealed);
        if (context.getHost().isAttachedToWindow()) {
            button.attachTitle(context.getHost());
        }
    }

    private void drawVoteControl(Canvas canvas, PollButton button) {
        float cx = buttonLeft - voteControlCenterXOffsetPx();
        float cy = button.y + voteControlCenterYPx();
        float radius = isMultipleChoice() ? multiChoiceRadiusPx() : singleChoiceRadiusPx();
        checkStrokePaint.setStyle(Paint.Style.STROKE);
        checkStrokePaint.setColor(context.getAccentColor());
        if (isMultipleChoice()) {
            canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius,
                    multiChoiceCornerRadiusPx(), multiChoiceCornerRadiusPx(), checkStrokePaint);
        } else {
            canvas.drawCircle(cx, cy, radius, checkStrokePaint);
        }
        drawCheckBox(canvas, button, cx, cy, isMultipleChoice() ? multiChoiceCheckSizePx() : singleChoiceCheckSizePx(), isMultipleChoice());
    }

    private void drawResultCheck(Canvas canvas, PollButton button) {
        int lineTop = button.y + button.title.getHeight() + resultLineTopGapPx();
        float cx = buttonLeft - resultCheckCenterXOffsetPx();
        float cy = lineTop + resultCheckCenterYOffsetPx();
        drawCheckBox(canvas, button, cx, cy, resultCheckSizePx(), isMultipleChoice());
    }

    private void drawCheckBox(Canvas canvas, PollButton button, float cx, float cy, int size, boolean square) {
        if (button.checkBox == null) {
            ensureButtonChrome(button);
        }
        button.checkBox.setColor(-1, Theme.key_chat_inAudioSeekbarFill, Theme.key_checkboxCheck);
        button.checkBox.setBounds(Math.round(cx - size / 2f), Math.round(cy - size / 2f), size, size);
        button.checkBox.setCustomRadius(square ? checkboxCustomRadiusPx() : 0);
        button.checkBox.setSize(square ? MULTI_CHOICE_CHECKBOX_SIZE : SINGLE_CHOICE_CHECKBOX_SIZE);
        button.checkBox.draw(canvas);
    }

    private void ensureButtonChrome(PollButton button) {
        ensureButtonSelector(button);
        boolean createdAttachable = false;
        if (button.pollButtonDrawable == null) {
            button.pollButtonDrawable = new PollButtonDrawable(0, context.getHost());
            button.pollButtonDrawable.setHasMediaPadding(false);
            createdAttachable = true;
        }
        if (button.checkBox == null) {
            button.checkBox = new CheckBoxBase(context.getHost(), 20, null);
            button.checkBox.setDrawUnchecked(false);
            button.checkBox.setCuttingCheck(true);
            button.checkBox.setBackgroundType(9);
            createdAttachable = true;
        }
        if (createdAttachable && context.getHost().isAttachedToWindow()) {
            button.attach();
        }
    }

    private void ensureButtonSelector(PollButton button) {
        if (button.selectorDrawable == null) {
            button.selectorDrawableColor = Theme.multAlpha(context.getSecondaryTextColor(), 0.12f);
            button.selectorDrawable = Theme.createRadSelectorDrawable(button.selectorDrawableColor, 0, 0);
        }
        button.selectorDrawable.setCallback(context.getHost());
    }

    private void ensureButtons(List<CookedPostBlock.PollOption> options) {
        if (pollButtons.size() == options.size()) {
            for (int i = 0; i < options.size(); i++) {
                if (pollButtons.get(i).answer == options.get(i)) {
                    continue;
                }
                detachButtons();
                pollButtons.clear();
                break;
            }
        } else {
            detachButtons();
            pollButtons.clear();
        }
        if (pollButtons.isEmpty()) {
            for (CookedPostBlock.PollOption option : options) {
                pollButtons.add(new PollButton(option));
            }
        }
    }

    private void detachButtons() {
        for (PollButton button : pollButtons) {
            button.detachTitle();
            button.detach();
        }
    }

    private int[] optionPercents(List<CookedPostBlock.PollOption> options) {
        int[] result = new int[options.size()];
        boolean hasPercent = false;
        for (CookedPostBlock.PollOption option : options) {
            hasPercent |= option.getPercent() != null;
        }
        ArrayList<PollPercentCandidate> sorted = new ArrayList<>();
        if (hasPercent) {
            for (int i = 0; i < options.size(); i++) {
                Float percent = options.get(i).getPercent();
                PollPercentCandidate candidate = new PollPercentCandidate(i, (percent != null ? percent : 0f) * 100f);
                result[i] = candidate.percent;
                sorted.add(candidate);
            }
            normalizePollPercents(result, sorted);
            return result;
        }
        int total = 0;
        for (CookedPostBlock.PollOption option : options) {
            if (option.getVotes() != null) {
                total += option.getVotes();
            }
        }
        if (total <= 0 && block.getVoters() != null) {
            total = block.getVoters();
        }
        if (total <= 0) {
            return result;
        }
        for (int i = 0; i < options.size(); i++) {
            Integer votes = options.get(i).getVotes();
            PollPercentCandidate candidate = new PollPercentCandidate(i, (votes != null ? votes : 0) * 100f / total);
            result[i] = candidate.percent;
            sorted.add(candidate);
        }
        normalizePollPercents(result, sorted);
        return result;
    }

    private static void normalizePollPercents(int[] result, ArrayList<PollPercentCandidate> sortedPollButtons) {
        int restPercent = 100;
        boolean hasDifferent = false;
        int previousPercent = 0;
        for (int percent : result) {
            if (previousPercent == 0) {
                previousPercent = percent;
            } else if (percent != 0 && previousPercent != percent) {
                hasDifferent = true;
            }
            restPercent -= percent;
        }
        if (!hasDifferent || restPercent == 0 || sortedPollButtons == null || sortedPollButtons.isEmpty()) {
            return;
        }

        Collections.sort(sortedPollButtons, (o1, o2) -> {
            if (o1.decimal > o2.decimal) {
                return -1;
            } else if (o1.decimal < o2.decimal) {
                return 1;
            }
            if (o1.percent > o2.percent) {
                return 1;
            } else if (o1.percent < o2.percent) {
                return -1;
            }
            return 0;
        });
        for (int i = 0, n = sortedPollButtons.size(); i < n; i++) {
            if (restPercent <= 0) {
                break;
            }

            PollPercentCandidate b = sortedPollButtons.get(i);
            if (b.percent > 0) {
                b.percent += 1;
                result[b.index] = b.percent;
                restPercent--;
            }
        }
    }

    private boolean isChosen(CookedPostBlock.PollOption option, int index) {
        Set<Integer> local = state != null ? state.getSelectedPollOptionIndexesByIndex().get(stateIndex) : null;
        return option.getSelected() || local != null && local.contains(index);
    }

    private boolean shouldShowResults() {
        if ("closed".equals(block.getStatus())) {
            return true;
        }
        if (hasLocalSubmittedVote()) {
            return true;
        }
        if (block.getVoters() != null && block.getVoters() > 0) {
            return true;
        }
        for (CookedPostBlock.PollOption option : block.getOptions()) {
            if (option.getSelected() || option.getPercent() != null || option.getVotes() != null && option.getVotes() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean canVote(int index) {
        if (index < 0 || index >= pollButtons.size()) {
            return false;
        }
        PollButton button = pollButtons.get(index);
        return block.getName() != null
                && !block.getName().trim().isEmpty()
                && button.answer.getId() != null
                && !button.answer.getId().trim().isEmpty()
                && !"closed".equals(block.getStatus())
                && !hasLocalSubmittedVote()
                && !hasServerSelection();
    }

    private int buttonIndexAt(float x, float y) {
        for (int i = 0; i < pollButtons.size(); i++) {
            if (pollButtons.get(i).selectorBounds.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private void setPollButtonPressed(int index, boolean pressed, float x, float y) {
        if (index < 0 || index >= pollButtons.size()) {
            return;
        }
        PollButton button = pollButtons.get(index);
        ensureButtonSelector(button);
        button.selectorDrawable.setHotspot(x, y);
        button.selectorDrawable.setState(pressed ? PRESSED_STATE : StateSet.NOTHING);
    }

    private void setPollButtonHotspot(int index, float x, float y) {
        if (index < 0 || index >= pollButtons.size()) {
            return;
        }
        PollButton button = pollButtons.get(index);
        ensureButtonSelector(button);
        button.selectorDrawable.setHotspot(x, y);
    }

    private boolean isMultipleChoice() {
        String type = block.getType();
        return type != null && (type.contains("multiple") || type.contains("checkbox"));
    }

    private String statusText() {
        if ("closed".equals(block.getStatus())) {
            return "已结束";
        }
        if (hasLocalSubmittedVote()) {
            return "已投票";
        }
        for (CookedPostBlock.PollOption option : block.getOptions()) {
            if (option.getSelected()) {
                return "已投票";
            }
        }
        return "";
    }

    private void updateLocalSelection(int index) {
        Set<Integer> selected = state.getSelectedPollOptionIndexesByIndex().get(stateIndex);
        if (selected == null) {
            selected = initialSelectedIndexes();
            state.getSelectedPollOptionIndexesByIndex().put(stateIndex, selected);
        }
        if (isMultipleChoice()) {
            if (!selected.remove(index)) {
                selected.add(index);
            }
            if (selected.isEmpty()) {
                state.getSelectedPollOptionIndexesByIndex().remove(stateIndex);
            }
        } else {
            selected.clear();
            selected.add(index);
        }
    }

    private void syncButtonChoiceState(int index, boolean animated) {
        if (index < 0 || index >= pollButtons.size()) {
            return;
        }
        PollButton button = pollButtons.get(index);
        ensureButtonChrome(button);
        button.prevChosen = button.chosen;
        button.chosen = isChosen(button.answer, index);
        button.checkBox.setChecked(button.chosen, animated && button.prevChosen != button.chosen);
    }

    private Set<Integer> initialSelectedIndexes() {
        Set<Integer> selected = new LinkedHashSet<>();
        for (int i = 0; i < pollButtons.size(); i++) {
            if (pollButtons.get(i).answer.getSelected()) {
                selected.add(i);
            }
        }
        return selected;
    }

    private List<String> selectedOptionIdsForVote(int fallbackIndex) {
        Set<Integer> selected = state != null ? state.getSelectedPollOptionIndexesByIndex().get(stateIndex) : null;
        if (selected == null || selected.isEmpty()) {
            PollButton fallback = fallbackIndex >= 0 && fallbackIndex < pollButtons.size() ? pollButtons.get(fallbackIndex) : null;
            String optionId = fallback != null ? fallback.answer.getId() : null;
            return optionId != null && !optionId.trim().isEmpty()
                    ? Collections.singletonList(optionId)
                    : Collections.emptyList();
        }
        ArrayList<String> optionIds = new ArrayList<>();
        for (Integer index : selected) {
            if (index == null || index < 0 || index >= pollButtons.size()) {
                continue;
            }
            String optionId = pollButtons.get(index).answer.getId();
            if (optionId != null && !optionId.trim().isEmpty()) {
                optionIds.add(optionId);
            }
        }
        return optionIds;
    }

    private boolean hasLocalSubmittedVote() {
        return state != null && state.getSubmittedPollIndexes().contains(stateIndex);
    }

    private boolean hasServerSelection() {
        for (CookedPostBlock.PollOption option : block.getOptions()) {
            if (option.getSelected()) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldShowMultiSelectSubmitButton() {
        return isMultipleChoice()
                && block.getName() != null
                && !block.getName().trim().isEmpty()
                && !"closed".equals(block.getStatus())
                && !hasLocalSubmittedVote()
                && !hasServerSelection();
    }

    private boolean canSubmitMultiSelectVote() {
        return shouldShowMultiSelectSubmitButton() && !selectedOptionIdsForVote(-1).isEmpty();
    }

    private void submitMultiSelectVote() {
        List<String> optionIds = selectedOptionIdsForVote(-1);
        if (optionIds.isEmpty()) {
            return;
        }
        markLocalSubmitted();
        context.getDelegate().onPollOptionsSelected(block.getName(), optionIds);
    }

    private void markLocalSubmitted() {
        if (state != null) {
            state.getSubmittedPollIndexes().add(stateIndex);
        }
        context.getHost().requestLayout();
        context.getHost().invalidate();
    }

    private void markButtonSpoilersRevealed(PollButton button) {
        button.spoilersRevealed = true;
        if (inlineState != null) {
            inlineState.getRevealedInlineSpoilersByKey().add(pollOptionSpoilerStateKey(button.answer, pollOptionLabelHtml(button.answer)));
        }
    }

    private String pollOptionSpoilerStateKey(CookedPostBlock.PollOption option, String html) {
        String id = option.getId() != null ? option.getId() : option.getLabel();
        String scope = inlineStateKey != null ? inlineStateKey : "poll:" + stateIndex;
        return TextLayoutBlock.inlineSpoilerStateKey(scope + ":option:" + id, html);
    }

    private static String pollOptionLabelHtml(CookedPostBlock.PollOption option) {
        String html = option.getLabelHtml();
        if (html != null && !html.trim().isEmpty()) {
            return html;
        }
        return TextUtils.htmlEncode(option.getLabel());
    }

    private String footerText() {
        StringBuilder builder = new StringBuilder();
        builder.append(block.getVoters() != null ? block.getVoters() + " 票" : "投票");
        if (block.getType() != null && !block.getType().trim().isEmpty()) {
            builder.append(" · ").append(block.getType());
        }
        return builder.toString();
    }

    private StaticLayout layout(String text, TextPaint paint, int width, int maxLines) {
        String value = text == null || text.isEmpty() ? " " : text;
        return ArticleTextLayout.builder(value, paint, width)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

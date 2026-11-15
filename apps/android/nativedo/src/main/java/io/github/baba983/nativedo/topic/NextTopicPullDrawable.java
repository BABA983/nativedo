// SPDX-License-Identifier: GPL-2.0-or-later
// Adapted from Telegram's org.telegram.ui.ChatPullingDownDrawable.
package io.github.baba983.nativedo.topic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.StaticLayoutEx;

/**
 * The card a reader pulls up from the bottom of a topic's list once it is at the real end: it names
 * the next topic the site suggested and asks for one more upward swipe.
 *
 * <p>Adapted from Telegram's {@code ChatPullingDownDrawable} (package {@code org.telegram.ui},
 * GPL-2.0-or-later): the growth, the arrow→check change and the release/bounce animation are
 * upstream's, kept as they are. What is dropped is everything about Telegram itself - the account,
 * the {@code MessagesController.isForum} read, the {@code ensureMessagesLoaded}/dialog lookups, the
 * folder/forum/empty-stub branches and the unread counter (this client does not claim an unread the
 * site did not tell it). The card is drawn for a Discourse suggested topic's title instead of a
 * Telegram chat's avatar and name.</p>
 */
class NextTopicPullDrawable {

    /** The card spans the same 110dp pull the reference does. */
    static final int PULL_THRESHOLD_DP = 110;

    private static final CharSequence HINT_KEEP_SWIPING = "继续上滑查看其他话题";
    private static final CharSequence HINT_RELEASE = "松开查看其他话题";

    private final View fragmentView;

    private Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint cardFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF card = new android.graphics.RectF();

    private StaticLayout chatNameLayout;
    private StaticLayout layout1;
    private StaticLayout layout2;
    private int chatNameWidth;
    private int layout1Width;
    private int layout2Width;

    private int lastWidth;
    private int lastTitleHash;
    private float circleRadius;

    private AnimatorSet showReleaseAnimator;
    private Runnable onAnimationFinishRunnable;

    private float swipeToReleaseProgress;
    private float bounceProgress;
    private float checkProgress;
    private float lastProgress;
    private long lastHapticTime;
    private boolean animateSwipeToRelease;
    private boolean animateCheck;

    /** The title of the topic the site suggested; never the current one. */
    private CharSequence targetTitle = "";

    NextTopicPullDrawable(View fragmentView) {
        this.fragmentView = fragmentView;
        arrowPaint.setStrokeWidth(AndroidUtilities.dpf2(2.8f));
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextSize(AndroidUtilities.dp(14));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint2.setTextSize(AndroidUtilities.dp(13));
    }

    /** The candidate the card names; resetting the layouts makes the next width rebuild them. */
    void setTarget(CharSequence title) {
        CharSequence value = title == null ? "" : title;
        if (TextUtils.equals(value, targetTitle)) {
            return;
        }
        targetTitle = value;
        lastTitleHash = 0;
    }

    /**
     * Lays the card's text out for [width]; the reference rebuilds its layouts the same way, when
     * the width or the topic it names changes.
     */
    void setWidth(int width) {
        int titleHash = targetTitle == null ? 0 : targetTitle.hashCode();
        if (width == lastWidth && titleHash == lastTitleHash) {
            return;
        }
        lastWidth = width;
        lastTitleHash = titleHash;
        circleRadius = AndroidUtilities.dp(20);

        chatNameWidth = (int) textPaint.measureText(targetTitle, 0, targetTitle.length());
        chatNameWidth = Math.min(chatNameWidth, Math.max(AndroidUtilities.dp(40), width - AndroidUtilities.dp(96)));
        chatNameLayout = StaticLayoutEx.createStaticLayout(targetTitle, textPaint, chatNameWidth,
                Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false, TextUtils.TruncateAt.END,
                chatNameWidth, 1);

        layout1Width = (int) textPaint2.measureText(HINT_KEEP_SWIPING, 0, HINT_KEEP_SWIPING.length());
        layout1 = new StaticLayout(HINT_KEEP_SWIPING, textPaint2, layout1Width,
                Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

        layout2Width = (int) textPaint2.measureText(HINT_RELEASE, 0, HINT_RELEASE.length());
        layout2 = new StaticLayout(HINT_RELEASE, textPaint2, layout2Width,
                Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
    }

    /**
     * Draws the card above [anchorY], given the pull [progress] (0..1). The reveal, the arrow->check
     * change and the bounce are the reference's; only where the pieces sit is adapted to a title
     * card instead of an avatar card.
     */
    void draw(Canvas canvas, View parent, float anchorY, float progress, float alpha) {
        float offset = AndroidUtilities.dp(PULL_THRESHOLD_DP) * progress;
        if (offset < AndroidUtilities.dp(8)) {
            return;
        }
        if (progress < 0.2f) {
            alpha *= progress * 5f;
        }
        int cx = lastWidth / 2;
        Theme.applyServiceShaderMatrix(lastWidth, parent.getMeasuredHeight(), 0, anchorY - offset);

        textPaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
        arrowPaint.setColor(Theme.getColor(Theme.key_chat_serviceText));
        textPaint2.setColor(Theme.getColor(Theme.key_chat_serviceText));

        int oldAlpha = Theme.getThemePaint(Theme.key_paint_chatActionBackground).getAlpha();
        int oldAlpha1 = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
        int oldAlpha2 = textPaint.getAlpha();
        int oldAlpha3 = arrowPaint.getAlpha();
        float applied = Math.max(0f, Math.min(1f, alpha));
        Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (oldAlpha1 * applied));
        Theme.getThemePaint(Theme.key_paint_chatActionBackground).setAlpha((int) (oldAlpha * applied));
        textPaint.setAlpha((int) (oldAlpha2 * applied));
        arrowPaint.setAlpha((int) (oldAlpha3 * applied));

        if ((progress >= 1f && lastProgress < 1f) || (progress < 1f && lastProgress == 1f)) {
            long time = System.currentTimeMillis();
            if (time - lastHapticTime > 100) {
                try {
                    parent.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {
                }
                lastHapticTime = time;
            }
            lastProgress = progress;
        }

        if (progress == 1f && !animateSwipeToRelease) {
            animateSwipeToRelease = true;
            animateCheck = true;
            showReleaseState(true, parent);
        } else if (progress != 1f && animateSwipeToRelease) {
            animateSwipeToRelease = false;
            showReleaseState(false, parent);
        }

        canvas.save();
        canvas.translate(0, anchorY);
        float bounceOffset = bounceProgress * -AndroidUtilities.dp(4);

        // The card, grown from the anchor: the reference's own growth, only wider so a title fits.
        float halfWidth = Math.min(AndroidUtilities.dp(170), (lastWidth - AndroidUtilities.dp(32)) / 2f);
        card.set(cx - halfWidth, -offset + bounceOffset, cx + halfWidth, -AndroidUtilities.dp(4));
        drawBackground(canvas, card, Math.max(0f, Math.min(1f, alpha)));

        // Fixed line spacing keeps the title and hint apart as the card grows.
        float hintY = card.bottom - AndroidUtilities.dp(10)
                - Math.max(layout1.getHeight(), layout2.getHeight());
        float titleY = hintY - AndroidUtilities.dp(6) - chatNameLayout.getHeight();
        AndroidUtilities.rectTmp.set(card);
        canvas.save();
        canvas.clipRect(card);

        // The arrow, turned into a check once the threshold is reached (the reference's own change).
        float iconCy = Math.min(-offset + offset * 0.28f + bounceOffset,
                titleY - AndroidUtilities.dp(18));
        if (swipeToReleaseProgress < 1f) {
            arrowPaint.setAlpha((int) (oldAlpha3 * applied * (1f - swipeToReleaseProgress)));
            drawArrow(canvas, cx, iconCy, AndroidUtilities.dp(24) * progress);
        }
        arrowPaint.setAlpha((int) (oldAlpha3 * applied * swipeToReleaseProgress));
        drawCheck(canvas, cx, iconCy);
        arrowPaint.setAlpha((int) (oldAlpha3 * applied));

        // The candidate the site suggested, named while the reader drags.
        if (chatNameLayout != null) {
            float titleAlpha = applied * Math.max(0f, Math.min(1f, progress));
            int wasAlpha = textPaint.getAlpha();
            textPaint.setAlpha((int) (oldAlpha2 * titleAlpha));
            canvas.save();
            canvas.translate(cx - chatNameWidth / 2f, titleY);
            chatNameLayout.draw(canvas);
            canvas.restore();
            textPaint.setAlpha(wasAlpha);
        }

        // The ask: keep swiping, or let go. They cross-fade the way the reference's do, each sliding
        // a little as it goes, so the two lines do not sit on top of each other mid-change.
        drawHint(canvas, cx, layout1, layout1Width, hintY - AndroidUtilities.dp(10) * swipeToReleaseProgress,
                applied, oldAlpha2, 1f - swipeToReleaseProgress);
        drawHint(canvas, cx, layout2, layout2Width, hintY + AndroidUtilities.dp(10) * (1f - swipeToReleaseProgress),
                applied, oldAlpha2, swipeToReleaseProgress);

        canvas.restore();
        canvas.restore();

        Theme.getThemePaint(Theme.key_paint_chatActionBackground).setAlpha(oldAlpha);
        Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha1);
        textPaint.setAlpha(oldAlpha2);
        arrowPaint.setAlpha(oldAlpha3);
    }

    private void drawHint(Canvas canvas, int cx, StaticLayout layout, int width, float y,
            float applied, int baseAlpha, float visibility) {
        if (layout == null || visibility <= 0f) {
            return;
        }
        textPaint2.setAlpha((int) (baseAlpha * applied * visibility));
        canvas.save();
        canvas.translate(cx - width / 2f, y);
        layout.draw(canvas);
        canvas.restore();
        textPaint2.setAlpha(baseAlpha);
    }

    private void drawCheck(Canvas canvas, float cx, float cy) {
        if (!animateCheck) {
            return;
        }
        if (checkProgress < 1f) {
            checkProgress += 16 / 220f;
            if (checkProgress > 1f) {
                checkProgress = 1f;
            }
        }
        float p1 = checkProgress > 0.5f ? 1f : checkProgress / 0.5f;
        float p2 = checkProgress < 0.5f ? 0 : (checkProgress - 0.5f) / 0.5f;
        canvas.save();
        canvas.translate(cx - AndroidUtilities.dp(24), cy - AndroidUtilities.dp(24));
        float x1 = AndroidUtilities.dp(16);
        float y1 = AndroidUtilities.dp(26);
        float x2 = AndroidUtilities.dp(22);
        float y2 = AndroidUtilities.dp(32);
        float x3 = AndroidUtilities.dp(32);
        float y3 = AndroidUtilities.dp(20);
        canvas.drawLine(x1, y1, x1 * (1f - p1) + x2 * p1, y1 * (1f - p1) + y2 * p1, arrowPaint);
        if (p2 > 0) {
            canvas.drawLine(x2, y2, x2 * (1f - p2) + x3 * p2, y2 * (1f - p2) + y3 * p2, arrowPaint);
        }
        canvas.restore();
    }

    private void drawBackground(Canvas canvas, android.graphics.RectF rect, float alpha) {
        // The reference's card is a glass pill over content Telegram blurs; this screen has no blur
        // behind it, so the service colour is laid down opaquely first - otherwise the posts behind
        // the card read through the candidate's title and the hint.
        cardFillPaint.setColor(Theme.getColor(Theme.key_chat_serviceBackground));
        cardFillPaint.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(rect, circleRadius, circleRadius, cardFillPaint);
        int oldAlpha1 = Theme.getThemePaint(Theme.key_paint_chatActionBackground).getAlpha();
        Theme.getThemePaint(Theme.key_paint_chatActionBackground).setAlpha((int) (oldAlpha1 * alpha));
        canvas.drawRoundRect(rect, circleRadius, circleRadius,
                Theme.getThemePaint(Theme.key_paint_chatActionBackground));
        Theme.getThemePaint(Theme.key_paint_chatActionBackground).setAlpha(oldAlpha1);
        if (Theme.hasGradientService()) {
            int oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (oldAlpha2 * alpha));
            canvas.drawRoundRect(rect, circleRadius, circleRadius,
                    Theme.chat_actionBackgroundGradientDarkenPaint);
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha2);
        }
    }

    /** The reference's own release/bounce animation, unchanged. */
    private void showReleaseState(boolean show, View parent) {
        if (showReleaseAnimator != null) {
            showReleaseAnimator.removeAllListeners();
            showReleaseAnimator.cancel();
        }
        if (show) {
            ValueAnimator out = ValueAnimator.ofFloat(swipeToReleaseProgress, 1f);
            out.addUpdateListener(animation -> {
                swipeToReleaseProgress = (float) animation.getAnimatedValue();
                parent.invalidate();
                fragmentView.invalidate();
            });
            out.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            out.setDuration(250);

            bounceProgress = 0;

            ValueAnimator bounceUp = ValueAnimator.ofFloat(0f, 1f);
            bounceUp.addUpdateListener(animation -> {
                bounceProgress = (float) animation.getAnimatedValue();
                parent.invalidate();
            });
            bounceUp.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            bounceUp.setDuration(180);

            ValueAnimator bounceDown = ValueAnimator.ofFloat(1f, -0.5f);
            bounceDown.addUpdateListener(animation -> {
                bounceProgress = (float) animation.getAnimatedValue();
                parent.invalidate();
            });
            bounceDown.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            bounceDown.setDuration(120);

            ValueAnimator bounceOut = ValueAnimator.ofFloat(-0.5f, 0f);
            bounceOut.addUpdateListener(animation -> {
                bounceProgress = (float) animation.getAnimatedValue();
                parent.invalidate();
            });
            bounceOut.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            bounceOut.setDuration(100);

            showReleaseAnimator = new AnimatorSet();
            showReleaseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    bounceProgress = 0f;
                    swipeToReleaseProgress = 1f;
                    parent.invalidate();
                    fragmentView.invalidate();
                    if (onAnimationFinishRunnable != null) {
                        onAnimationFinishRunnable.run();
                        onAnimationFinishRunnable = null;
                    }
                }
            });

            AnimatorSet bounce = new AnimatorSet();
            bounce.playSequentially(bounceUp, bounceDown, bounceOut);

            showReleaseAnimator.playTogether(out, bounce);
            showReleaseAnimator.start();
        } else {
            ValueAnimator out = ValueAnimator.ofFloat(swipeToReleaseProgress, 0f);
            out.addUpdateListener(animation -> {
                swipeToReleaseProgress = (float) animation.getAnimatedValue();
                fragmentView.invalidate();
                parent.invalidate();
            });
            out.setInterpolator(CubicBezierInterpolator.DEFAULT);
            out.setDuration(220);
            showReleaseAnimator = new AnimatorSet();
            showReleaseAnimator.playTogether(out);
            showReleaseAnimator.start();
        }
    }

    private void drawArrow(Canvas canvas, float cx, float cy, float size) {
        if (size <= 0) {
            return;
        }
        canvas.save();
        float s = size / AndroidUtilities.dpf2(24);
        canvas.scale(s, s, cx, cy - AndroidUtilities.dp(20));
        canvas.translate(cx - AndroidUtilities.dp2(12), cy - AndroidUtilities.dp(12));
        canvas.drawLine(AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(4f), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(22), arrowPaint);
        canvas.drawLine(AndroidUtilities.dpf2(3.5f), AndroidUtilities.dpf2(12), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(3.5f), arrowPaint);
        canvas.drawLine(AndroidUtilities.dpf2(25 - 3.5f), AndroidUtilities.dpf2(12), AndroidUtilities.dpf2(12.5f), AndroidUtilities.dpf2(3.5f), arrowPaint);
        canvas.restore();
    }

    /** The reference's own finish hand-off: the check lands, then the caller's runnable runs. */
    void runOnAnimationFinish(Runnable runnable) {
        if (showReleaseAnimator != null) {
            showReleaseAnimator.removeAllListeners();
            showReleaseAnimator.cancel();
        }
        onAnimationFinishRunnable = runnable;
        showReleaseAnimator = new AnimatorSet();

        ValueAnimator out = ValueAnimator.ofFloat(swipeToReleaseProgress, 1f);
        out.addUpdateListener(animation -> {
            swipeToReleaseProgress = (float) animation.getAnimatedValue();
            fragmentView.invalidate();
        });
        ValueAnimator bounceOut = ValueAnimator.ofFloat(bounceProgress, 0f);
        bounceOut.addUpdateListener(animation -> {
            bounceProgress = (float) animation.getAnimatedValue();
            fragmentView.invalidate();
        });
        showReleaseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                bounceProgress = 0f;
                swipeToReleaseProgress = 1f;
                fragmentView.invalidate();
                if (onAnimationFinishRunnable != null) {
                    onAnimationFinishRunnable.run();
                    onAnimationFinishRunnable = null;
                }
            }
        });
        showReleaseAnimator.playTogether(out, bounceOut);
        showReleaseAnimator.setDuration(120);
        showReleaseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        showReleaseAnimator.start();
    }

    boolean animationIsRunning() {
        return swipeToReleaseProgress != 1f;
    }

    void reset() {
        checkProgress = 0;
        animateCheck = false;
    }
}

// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/Components/AnimatedTextView.java - AnimatedTextDrawable
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: text-animation=L1, title-morph=L1
// HOST-WIRING:
//   - AndroidUtilities and CubicBezierInterpolator imports rewritten to NativeDo.
// ADAPTED:
//   - Extracts only AnimatedTextDrawable for WebActionBar title/subtitle morphing.
// OMITTED:
//   - AnimatedEmojiSpan drawing, gradient ellipsize, color animation helpers, and the
//     outer AnimatedTextView widget are outside the current browser action-bar title slice.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/AnimatedTextView.java.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import io.github.baba983.nativedo.core.AndroidUtilities;

public class AnimatedTextDrawable extends Drawable {

    private final TextPaint textPaint = new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private int gravity = 0;
    private boolean isRTL = false;

    private float currentWidth;
    private float currentHeight;
    private Part[] currentParts;
    private CharSequence currentText;

    private float oldWidth;
    private float oldHeight;
    private Part[] oldParts;
    private CharSequence oldText;

    private float t = 0;
    private boolean moveDown = true;
    private ValueAnimator animator;
    private CharSequence toSetText;
    private boolean toSetTextMoveDown;

    private long animateDelay = 0;
    private long animateDuration = 320;
    private TimeInterpolator animateInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT;
    private float animateWave = -1;
    private float moveAmplitude = .3f;
    private float scaleAmplitude = 0;

    private int alpha = 255;
    private final Rect bounds = new Rect();

    private boolean splitByWords;
    private boolean preserveIndex;
    private boolean startFromEnd;
    private boolean enforceByLetter;
    private boolean allowCancel;
    public boolean ignoreRTL;
    public boolean updateAll;
    public boolean centerY = true;

    private int overrideFullWidth;
    private boolean includeFontPadding = true;
    private Runnable onAnimationFinishListener;
    private Runnable widthUpdatedListener;

    public AnimatedTextDrawable() {
        this(false, false, false);
    }

    public AnimatedTextDrawable(boolean splitByWords, boolean preserveIndex, boolean startFromEnd) {
        this(splitByWords, preserveIndex, startFromEnd, false);
    }

    public AnimatedTextDrawable(boolean splitByWords, boolean preserveIndex, boolean startFromEnd, boolean enforceByLetter) {
        this.splitByWords = splitByWords;
        this.preserveIndex = preserveIndex;
        this.startFromEnd = startFromEnd;
        this.enforceByLetter = enforceByLetter;
    }

    public void setSplitByWords(boolean splitByWords) {
        this.splitByWords = splitByWords;
    }

    public void setHacks(boolean splitByWords, boolean preserveIndex, boolean startFromEnd) {
        setHacks(splitByWords, preserveIndex, startFromEnd, false);
    }

    public void setHacks(boolean splitByWords, boolean preserveIndex, boolean startFromEnd, boolean enforceByLetter) {
        this.splitByWords = splitByWords;
        this.preserveIndex = preserveIndex;
        this.startFromEnd = startFromEnd;
        this.enforceByLetter = enforceByLetter;
    }

    public void setAllowCancel(boolean allowCancel) {
        this.allowCancel = allowCancel;
    }

    public void setEllipsizeByGradient(boolean enabled) {
        invalidateSelf();
    }

    public void setOnAnimationFinishListener(Runnable listener) {
        onAnimationFinishListener = listener;
    }

    public void setOverrideFullWidth(int value) {
        overrideFullWidth = value;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        int fullWidth = bounds.width();
        int fullHeight = bounds.height();
        if (currentParts != null && oldParts != null && t != 1) {
            float width = AndroidUtilities.lerp(oldWidth, currentWidth, t);
            float height = AndroidUtilities.lerp(oldHeight, currentHeight, t);
            if (centerY) {
                canvas.translate(0, (fullHeight - height) / 2f);
            }
            for (int i = 0; i < currentParts.length; ++i) {
                Part current = currentParts[i];
                int j = current.toOppositeIndex;
                float x = current.offset;
                float y = 0;
                if (isRTL && !ignoreRTL) {
                    x = currentWidth - (x + current.width);
                }
                float localT = t;
                if (animateWave > 0) {
                    localT = AndroidUtilities.cascade(t, i, currentParts.length, animateWave);
                }
                if (j >= 0) {
                    Part old = oldParts[j];
                    float oldX = old.offset;
                    if (isRTL && !ignoreRTL) {
                        oldX = oldWidth - (oldX + old.width);
                    }
                    x = AndroidUtilities.lerp(oldX - old.left, x - current.left, t);
                    applyAlphaInternal(1f);
                } else {
                    x -= current.left;
                    y = -textPaint.getTextSize() * moveAmplitude * (1f - localT) * (moveDown ? 1f : -1f);
                    applyAlphaInternal(localT);
                }
                canvas.save();
                float localWidth = j >= 0 ? width : currentWidth;
                if ((gravity | ~Gravity.LEFT) != ~0) {
                    if ((gravity | ~Gravity.RIGHT) == ~0) {
                        x += fullWidth - localWidth;
                    } else if ((gravity | ~Gravity.CENTER_HORIZONTAL) == ~0) {
                        x += (fullWidth - localWidth) / 2f;
                    } else if (isRTL && !ignoreRTL) {
                        x += fullWidth - localWidth;
                    }
                }
                canvas.translate(x, y);
                if (j < 0 && scaleAmplitude > 0) {
                    final float s = AndroidUtilities.lerp(1f - scaleAmplitude, 1f, t);
                    canvas.scale(s, s, current.width / 2f, current.layout.getHeight() / 2f);
                }
                current.draw(canvas);
                canvas.restore();
            }
            for (int i = 0; i < oldParts.length; ++i) {
                Part old = oldParts[i];
                int j = old.toOppositeIndex;
                if (j >= 0) {
                    continue;
                }
                float localT = t;
                if (animateWave > 0) {
                    localT = AndroidUtilities.cascade(t, i, oldParts.length, animateWave);
                }
                float x = old.offset;
                float y = textPaint.getTextSize() * moveAmplitude * localT * (moveDown ? 1f : -1f);
                applyAlphaInternal(1f - localT);
                canvas.save();
                if (isRTL && !ignoreRTL) {
                    x = oldWidth - (x + old.width);
                }
                x -= old.left;
                if ((gravity | ~Gravity.LEFT) != ~0) {
                    if ((gravity | ~Gravity.RIGHT) == ~0) {
                        x += fullWidth - oldWidth;
                    } else if ((gravity | ~Gravity.CENTER_HORIZONTAL) == ~0) {
                        x += (fullWidth - oldWidth) / 2f;
                    } else if (isRTL && !ignoreRTL) {
                        x += fullWidth - oldWidth;
                    }
                }
                canvas.translate(x, y);
                if (scaleAmplitude > 0) {
                    final float s = AndroidUtilities.lerp(1f, 1f - scaleAmplitude, t);
                    canvas.scale(s, s, old.width / 2f, old.layout.getHeight() / 2f);
                }
                old.draw(canvas);
                canvas.restore();
            }
        } else {
            if (centerY) {
                canvas.translate(0, (fullHeight - currentHeight) / 2f);
            }
            if (currentParts != null) {
                applyAlphaInternal(1f);
                for (Part current : currentParts) {
                    canvas.save();
                    float x = current.offset;
                    if (isRTL && !ignoreRTL) {
                        x = currentWidth - (x + current.width);
                    }
                    x -= current.left;
                    if ((gravity | ~Gravity.LEFT) != ~0) {
                        if ((gravity | ~Gravity.RIGHT) == ~0) {
                            x += fullWidth - currentWidth;
                        } else if ((gravity | ~Gravity.CENTER_HORIZONTAL) == ~0) {
                            x += (fullWidth - currentWidth) / 2f;
                        } else if (isRTL && !ignoreRTL) {
                            x += fullWidth - currentWidth;
                        }
                    }
                    canvas.translate(x, 0);
                    current.draw(canvas);
                    canvas.restore();
                }
            }
        }
        canvas.restore();
    }

    private void applyAlphaInternal(float t) {
        textPaint.setAlpha((int) (alpha * t));
    }

    public void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
        }
    }

    public boolean isAnimating() {
        return animator != null && animator.isRunning();
    }

    public void setText(CharSequence text) {
        setText(text, true);
    }

    public void setText(CharSequence text, boolean animated) {
        setText(text, animated, true);
    }

    public void setText(CharSequence text, boolean animated, boolean moveDown) {
        if (this.currentText == null || text == null) {
            animated = false;
        }
        if (text == null) {
            text = "";
        }
        final int width = overrideFullWidth > 0 ? overrideFullWidth : bounds.width();
        if (animated) {
            if (TextUtils.equals(text, currentText)) {
                return;
            }

            if (allowCancel) {
                if (animator != null) {
                    animator.cancel();
                    animator = null;
                }
            } else if (isAnimating()) {
                toSetText = text;
                toSetTextMoveDown = moveDown;
                return;
            }

            oldText = currentText;
            currentText = text;

            ArrayList<Part> currentPartsList = new ArrayList<>();
            ArrayList<Part> oldPartsList = new ArrayList<>();

            currentWidth = currentHeight = 0;
            oldWidth = oldHeight = 0;
            isRTL = isRTL(currentText);

            RegionCallback onEqualRegion = (part, from, to) -> {
                StaticLayout layout = makeLayout(part, width - (int) Math.ceil(Math.min(currentWidth, oldWidth)));
                final Part currentPart = new Part(layout, currentWidth, oldPartsList.size());
                final Part oldPart = new Part(layout, oldWidth, oldPartsList.size());
                currentPartsList.add(currentPart);
                oldPartsList.add(oldPart);
                float partWidth = currentPart.width;
                currentWidth += partWidth;
                oldWidth += partWidth;
                currentHeight = Math.max(currentHeight, layout.getHeight());
                oldHeight = Math.max(oldHeight, layout.getHeight());
            };
            RegionCallback onNewPart = (part, from, to) -> {
                StaticLayout layout = makeLayout(part, width - (int) Math.ceil(currentWidth));
                final Part currentPart = new Part(layout, currentWidth, -1);
                currentPartsList.add(currentPart);
                currentWidth += currentPart.width;
                currentHeight = Math.max(currentHeight, layout.getHeight());
            };
            RegionCallback onOldPart = (part, from, to) -> {
                StaticLayout layout = makeLayout(part, width - (int) Math.ceil(oldWidth));
                final Part oldPart = new Part(layout, oldWidth, -1);
                oldPartsList.add(oldPart);
                oldWidth += oldPart.width;
                oldHeight = Math.max(oldHeight, oldPart.layout.getHeight());
            };

            CharSequence from = splitByWords ? new WordSequence(oldText) : oldText;
            CharSequence to = splitByWords ? new WordSequence(currentText) : currentText;

            diff(from, to, onEqualRegion, onNewPart, onOldPart);

            currentParts = currentPartsList.toArray(new Part[0]);
            oldParts = oldPartsList.toArray(new Part[0]);
            if (animator != null) {
                animator.cancel();
            }

            this.moveDown = moveDown;
            animator = ValueAnimator.ofFloat(t = 0f, 1f);
            if (widthUpdatedListener != null) {
                widthUpdatedListener.run();
            }
            animator.addUpdateListener(animation -> {
                t = (float) animation.getAnimatedValue();
                invalidateSelf();
                if (widthUpdatedListener != null) {
                    widthUpdatedListener.run();
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    oldParts = null;
                    oldText = null;
                    oldWidth = 0;
                    t = 0;
                    invalidateSelf();
                    if (widthUpdatedListener != null) {
                        widthUpdatedListener.run();
                    }
                    animator = null;

                    if (toSetText != null) {
                        setText(toSetText, true, toSetTextMoveDown);
                        toSetText = null;
                        toSetTextMoveDown = false;
                    } else if (onAnimationFinishListener != null) {
                        onAnimationFinishListener.run();
                    }
                }
            });
            animator.setStartDelay(animateDelay);
            animator.setDuration(animateDuration);
            animator.setInterpolator(animateInterpolator);
            animator.start();
        } else {
            if (animator != null) {
                animator.cancel();
            }
            animator = null;
            toSetText = null;
            toSetTextMoveDown = false;
            t = 0;

            if (!text.equals(currentText)) {
                currentParts = new Part[1];
                currentParts[0] = new Part(makeLayout(currentText = text, width), 0, -1);
                currentWidth = currentParts[0].width;
                currentHeight = currentParts[0].layout.getHeight();
                isRTL = isRTL(currentText);
            }

            oldParts = null;
            oldText = null;
            oldWidth = 0;
            oldHeight = 0;

            invalidateSelf();
            if (widthUpdatedListener != null) {
                widthUpdatedListener.run();
            }
        }
    }

    public CharSequence getText() {
        return currentText;
    }

    public float getWidth() {
        return Math.max(currentWidth, oldWidth);
    }

    public float getCurrentWidth() {
        if (currentParts != null && oldParts != null) {
            return AndroidUtilities.lerp(oldWidth, currentWidth, t);
        }
        return currentWidth;
    }

    public float getHeight() {
        return currentHeight;
    }

    private StaticLayout makeLayout(CharSequence textPart, int width) {
        if (width <= 0) {
            width = Math.max(1, Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(textPart, 0, textPart.length(), textPaint, width)
                    .setMaxLines(1)
                    .setLineSpacing(0, 1)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setEllipsizedWidth(width)
                    .setIncludePad(includeFontPadding)
                    .build();
        }
        return new StaticLayout(
                textPart,
                0,
                textPart.length(),
                textPaint,
                width,
                Layout.Alignment.ALIGN_NORMAL,
                1,
                0,
                includeFontPadding,
                TextUtils.TruncateAt.END,
                width
        );
    }

    private void part(RegionCallback onPart, CharSequence text, int start, int end) {
        if (enforceByLetter && text.length() > 1) {
            for (int i = 0; i < text.length(); ++i) {
                onPart.run(text.subSequence(i, i + 1), start + i, start + i + 1);
            }
            return;
        }
        onPart.run(text, start, end);
    }

    private void diff(final CharSequence oldText, final CharSequence newText, RegionCallback onEqualPart, RegionCallback onNewPart, RegionCallback onOldPart) {
        if (updateAll) {
            part(onOldPart, oldText, 0, oldText.length());
            part(onNewPart, newText, 0, newText.length());
            return;
        }
        if (preserveIndex) {
            boolean equal = true;
            int start = 0;
            int minLength = Math.min(newText.length(), oldText.length());
            if (startFromEnd) {
                ArrayList<Integer> indexes = new ArrayList<>();
                boolean eq = true;
                for (int i = 0; i <= minLength; ++i) {
                    int a = newText.length() - i - 1;
                    int b = oldText.length() - i - 1;
                    boolean thisEqual = a >= 0 && b >= 0 && partEquals(newText, oldText, a, b);
                    if (equal != thisEqual || i == minLength) {
                        if (i - start > 0) {
                            if (indexes.size() == 0) {
                                eq = equal;
                            }
                            indexes.add(i - start);
                        }
                        equal = thisEqual;
                        start = i;
                    }
                }
                int a = newText.length() - minLength;
                int b = oldText.length() - minLength;
                if (a > 0) {
                    part(onNewPart, newText.subSequence(0, a), 0, a);
                }
                if (b > 0) {
                    part(onOldPart, oldText.subSequence(0, b), 0, b);
                }
                for (int i = indexes.size() - 1; i >= 0; --i) {
                    int count = indexes.get(i);
                    if ((i % 2 == 0) == eq) {
                        if (newText.length() > oldText.length()) {
                            onEqualPart.run(newText.subSequence(a, a + count), a, a + count);
                        } else {
                            onEqualPart.run(oldText.subSequence(b, b + count), b, b + count);
                        }
                    } else {
                        part(onNewPart, newText.subSequence(a, a + count), a, a + count);
                        part(onOldPart, oldText.subSequence(b, b + count), b, b + count);
                    }
                    a += count;
                    b += count;
                }
            } else {
                for (int i = 0; i <= minLength; ++i) {
                    boolean thisEqual = i < minLength && partEquals(newText, oldText, i, i);
                    if (equal != thisEqual || i == minLength) {
                        if (i - start > 0) {
                            if (equal) {
                                part(onEqualPart, newText.subSequence(start, i), start, i);
                            } else {
                                part(onNewPart, newText.subSequence(start, i), start, i);
                                part(onOldPart, oldText.subSequence(start, i), start, i);
                            }
                        }
                        equal = thisEqual;
                        start = i;
                    }
                }
                if (newText.length() - minLength > 0) {
                    part(onNewPart, newText.subSequence(minLength, newText.length()), minLength, newText.length());
                }
                if (oldText.length() - minLength > 0) {
                    part(onOldPart, oldText.subSequence(minLength, oldText.length()), minLength, oldText.length());
                }
            }
        } else {
            int astart = 0;
            int bstart = 0;
            boolean equal = true;
            int a = 0;
            int b = 0;
            int minLength = Math.min(newText.length(), oldText.length());
            for (; a <= minLength; ++a) {
                boolean thisEqual = a < minLength && partEquals(newText, oldText, a, b);
                if (equal != thisEqual || a == minLength) {
                    if (a == minLength) {
                        a = newText.length();
                        b = oldText.length();
                    }
                    int alen = a - astart;
                    int blen = b - bstart;
                    if (alen > 0 || blen > 0) {
                        if (alen == blen && equal) {
                            onEqualPart.run(newText.subSequence(astart, a), astart, a);
                        } else {
                            if (alen > 0) {
                                part(onNewPart, newText.subSequence(astart, a), astart, a);
                            }
                            if (blen > 0) {
                                part(onOldPart, oldText.subSequence(bstart, b), bstart, b);
                            }
                        }
                    }
                    equal = thisEqual;
                    astart = a;
                    bstart = b;
                }
                if (thisEqual) {
                    b++;
                }
            }
        }
    }

    private static boolean partEquals(CharSequence a, CharSequence b, int aIndex, int bIndex) {
        if (a instanceof WordSequence && b instanceof WordSequence) {
            CharSequence wordA = ((WordSequence) a).wordAt(aIndex);
            CharSequence wordB = ((WordSequence) b).wordAt(bIndex);
            return wordA == null && wordB == null || wordA != null && wordA.equals(wordB);
        }
        return a == null && b == null || a != null && b != null && a.charAt(aIndex) == b.charAt(bIndex);
    }

    public void setTextSize(float textSizePx) {
        final float lastTextPaint = textPaint.getTextSize();
        textPaint.setTextSize(textSizePx);
        if (Math.abs(lastTextPaint - textSizePx) > 0.5f) {
            final int width = overrideFullWidth > 0 ? overrideFullWidth : bounds.width();
            if (currentParts != null) {
                currentWidth = 0;
                currentHeight = 0;
                for (int i = 0; i < currentParts.length; ++i) {
                    StaticLayout layout = makeLayout(currentParts[i].layout.getText(), width - (int) Math.ceil(Math.min(currentWidth, oldWidth)));
                    currentParts[i] = new Part(layout, currentParts[i].offset, currentParts[i].toOppositeIndex);
                    currentWidth += currentParts[i].width;
                    currentHeight = Math.max(currentHeight, currentParts[i].layout.getHeight());
                }
            }
            if (oldParts != null) {
                oldWidth = 0;
                oldHeight = 0;
                for (int i = 0; i < oldParts.length; ++i) {
                    StaticLayout layout = makeLayout(oldParts[i].layout.getText(), width - (int) Math.ceil(Math.min(currentWidth, oldWidth)));
                    oldParts[i] = new Part(layout, oldParts[i].offset, oldParts[i].toOppositeIndex);
                    oldWidth += oldParts[i].width;
                    oldHeight = Math.max(oldHeight, oldParts[i].layout.getHeight());
                }
            }
            invalidateSelf();
        }
    }

    public float getTextSize() {
        return textPaint.getTextSize();
    }

    public void setTextColor(int color) {
        textPaint.setColor(color);
        alpha = android.graphics.Color.alpha(color);
        invalidateSelf();
    }

    public int getTextColor() {
        return textPaint.getColor();
    }

    public void setTypeface(Typeface typeface) {
        textPaint.setTypeface(typeface);
    }

    public void setGravity(int gravity) {
        this.gravity = gravity;
    }

    public int getGravity() {
        return gravity;
    }

    public void setAnimationProperties(float moveAmplitude, long startDelay, long duration, TimeInterpolator interpolator) {
        setAnimationProperties(moveAmplitude, startDelay, duration, 1.0f, interpolator);
    }

    public void setAnimationProperties(float moveAmplitude, long startDelay, long duration, float wave, TimeInterpolator interpolator) {
        this.moveAmplitude = moveAmplitude;
        animateDelay = startDelay;
        animateDuration = duration;
        animateWave = wave;
        animateInterpolator = interpolator;
    }

    public void setScaleProperty(float scale) {
        scaleAmplitude = scale;
    }

    public TextPaint getPaint() {
        return textPaint;
    }

    public float isNotEmpty() {
        return AndroidUtilities.lerp(
                oldText == null || oldText.length() <= 0 ? 0f : 1f,
                currentText == null || currentText.length() <= 0 ? 0f : 1f,
                oldText == null ? 1f : t
        );
    }

    public void setOnWidthUpdatedListener(Runnable listener) {
        widthUpdatedListener = listener;
    }

    public void setIncludeFontPadding(boolean includeFontPadding) {
        this.includeFontPadding = includeFontPadding;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        textPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setBounds(@NonNull Rect bounds) {
        super.setBounds(bounds);
        this.bounds.set(bounds);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        bounds.set(left, top, right, bottom);
    }

    @NonNull
    @Override
    public Rect getDirtyBounds() {
        return bounds;
    }

    private static boolean isRTL(CharSequence text) {
        if (text == null || text.length() <= 0) {
            return false;
        }
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c >= 0x590 && c <= 0x6ff) {
                return true;
            }
        }
        return false;
    }

    private interface RegionCallback {
        void run(CharSequence part, int start, int end);
    }

    private final class Part {
        StaticLayout layout;
        float offset;
        int toOppositeIndex;
        float left;
        float width;

        Part(StaticLayout layout, float offset, int toOppositeIndex) {
            this.layout = layout;
            this.toOppositeIndex = toOppositeIndex;
            layout(offset);
        }

        void layout(float offset) {
            this.offset = offset;
            this.left = layout == null || layout.getLineCount() <= 0 ? 0 : layout.getLineLeft(0);
            this.width = layout == null || layout.getLineCount() <= 0 ? 0 : layout.getLineWidth(0);
        }

        void draw(Canvas canvas) {
            layout.draw(canvas);
        }
    }

    private static class WordSequence implements CharSequence {
        private static final char SPACE = ' ';

        private final CharSequence[] words;
        private final int length;

        WordSequence(CharSequence text) {
            if (text == null) {
                words = new CharSequence[0];
                length = 0;
                return;
            }
            length = text.length();
            int spacesCount = 0;
            for (int i = 0; i < length; ++i) {
                if (text.charAt(i) == SPACE) {
                    spacesCount++;
                }
            }
            int j = 0;
            words = new CharSequence[spacesCount + 1];
            int start = 0;
            for (int i = 0; i <= length; ++i) {
                if (i == length || text.charAt(i) == SPACE) {
                    words[j++] = text.subSequence(start, i + (i < length ? 1 : 0));
                    start = i + 1;
                }
            }
        }

        CharSequence wordAt(int i) {
            if (i < 0 || i >= words.length) {
                return null;
            }
            return words[i];
        }

        @Override
        public int length() {
            return words.length;
        }

        @Override
        public char charAt(int i) {
            for (CharSequence word : words) {
                if (i < word.length()) {
                    return word.charAt(i);
                }
                i -= word.length();
            }
            return 0;
        }

        @NonNull
        @Override
        public CharSequence subSequence(int from, int to) {
            return TextUtils.concat(java.util.Arrays.copyOfRange(words, from, to));
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (CharSequence word : words) {
                builder.append(word);
            }
            return builder.toString();
        }
    }
}

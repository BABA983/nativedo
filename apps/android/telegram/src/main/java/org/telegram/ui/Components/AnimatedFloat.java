// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/Components/AnimatedFloat.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: animation-helper=L2
// HOST-WIRING:
//   - Imports CubicBezierInterpolator from NativeDo's components package.
// ADAPTED:
//   - Keeps the public API surface (set, get, force, setDuration, setDelay) used by
//     LineProgressView and WebActionBar; reimplements the internal time-based easing
//     using a from/target/start triple instead of upstream's transitionProgress field.
// OMITTED:
//   - Multi-arg overloads, Runnable invalidate target, getValue/getTransitionProgress*,
//     getTargetValue/setParent helpers not yet referenced by NativeDo.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/AnimatedFloat.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/AnimatedFloat.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

import android.animation.TimeInterpolator;
import android.view.View;

/**
 * Lightweight time-based value smoother. {@link #set} pushes a new target; subsequent {@link #get}
 * calls return the eased current value and invalidate the parent View so onDraw is re-entered until
 * the transition settles.
 */
public class AnimatedFloat {

    private float fromValue;
    private float targetValue;
    private float currentValue;
    private long transitionStart;
    private long transitionDuration = 200;
    private long transitionDelay = 0;
    private TimeInterpolator interpolator;
    private View parentToInvalidate;
    private Runnable invalidateCallback;
    private boolean firstSet = true;

    public AnimatedFloat() {
    }

    public AnimatedFloat(long transitionDuration, TimeInterpolator interpolator) {
        this.transitionDuration = transitionDuration;
        this.interpolator = interpolator;
    }

    public AnimatedFloat(View parentToInvalidate) {
        this.parentToInvalidate = parentToInvalidate;
    }

    /** ADAPTED: Runnable-based invalidate callback (used by Drawables that have no parent View). */
    public AnimatedFloat(float initialValue, Runnable invalidateCallback, long transitionDelay, long transitionDuration, TimeInterpolator interpolator) {
        this.invalidateCallback = invalidateCallback;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.interpolator = interpolator;
        this.currentValue = this.fromValue = this.targetValue = initialValue;
        this.firstSet = false;
    }

    public AnimatedFloat(View parentToInvalidate, long transitionDuration, TimeInterpolator interpolator) {
        this.parentToInvalidate = parentToInvalidate;
        this.transitionDuration = transitionDuration;
        this.interpolator = interpolator;
    }

    public AnimatedFloat(View parentToInvalidate, long transitionDelay, long transitionDuration, TimeInterpolator interpolator) {
        this.parentToInvalidate = parentToInvalidate;
        this.transitionDelay = transitionDelay;
        this.transitionDuration = transitionDuration;
        this.interpolator = interpolator;
    }

    public AnimatedFloat(float initialValue, View parentToInvalidate, long transitionDelay, long transitionDuration, TimeInterpolator interpolator) {
        this(parentToInvalidate, transitionDelay, transitionDuration, interpolator);
        this.currentValue = this.fromValue = this.targetValue = initialValue;
        this.firstSet = false;
    }

    public float set(float mustBe) {
        if (firstSet) {
            currentValue = fromValue = targetValue = mustBe;
            firstSet = false;
            return mustBe;
        }
        if (mustBe != targetValue) {
            fromValue = computeCurrent();
            currentValue = fromValue;
            targetValue = mustBe;
            transitionStart = System.currentTimeMillis();
        }
        return get();
    }

    public float set(boolean mustBe) {
        return set(mustBe ? 1f : 0f);
    }

    /** ADAPTED: 2-arg set with optional skip-animation flag, matches upstream AnimatedFloat.set(float, boolean). */
    public float set(float mustBe, boolean immediate) {
        if (immediate) {
            force(mustBe);
            return mustBe;
        }
        return set(mustBe);
    }

    public float get() {
        if (firstSet) {
            return currentValue;
        }
        currentValue = computeCurrent();
        if (currentValue != targetValue) {
            if (parentToInvalidate != null) {
                parentToInvalidate.invalidate();
            }
            if (invalidateCallback != null) {
                invalidateCallback.run();
            }
        }
        return currentValue;
    }

    public void force(float value) {
        currentValue = fromValue = targetValue = value;
        firstSet = false;
    }

    public void setDuration(long duration) {
        this.transitionDuration = duration;
    }

    public void setDelay(long delay) {
        this.transitionDelay = delay;
    }

    public boolean isInProgress() {
        return !firstSet && currentValue != targetValue;
    }

    private float computeCurrent() {
        long elapsed = System.currentTimeMillis() - transitionStart - transitionDelay;
        if (elapsed <= 0) {
            return fromValue;
        }
        if (elapsed >= transitionDuration || transitionDuration <= 0) {
            return targetValue;
        }
        float p = (float) elapsed / (float) transitionDuration;
        if (interpolator != null) {
            p = interpolator.getInterpolation(p);
        }
        return fromValue + (targetValue - fromValue) * p;
    }
}

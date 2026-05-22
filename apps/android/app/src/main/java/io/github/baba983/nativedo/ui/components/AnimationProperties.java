// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/Components/AnimationProperties.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: Property wrappers, clipping image animation progress.
// HOST-WIRING:
//   - Package relocated to io.github.baba983.nativedo.ui.components.
// ADAPTED-BACKEND:
//   - None.
// ADAPTED-PRODUCT:
//   - Keeps only the upstream property types and property objects required by the current local callers.
// OMITTED:
//   - Telegram-only properties for types not present in this checkout, such as DialogCell.
/*
 * This file is part of NativeDo.
 *
 * Derived from Telegram for Android's
 * org/telegram/ui/Components/AnimationProperties.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
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
package io.github.baba983.nativedo.ui.components;

import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.Property;
import android.view.animation.OvershootInterpolator;

import io.github.baba983.nativedo.telegram.messenger.ImageReceiver;
import io.github.baba983.nativedo.telegram.ui.PhotoViewer;

public class AnimationProperties {

    public static OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.9f);

    public static abstract class FloatProperty<T> extends Property<T, Float> {

        public FloatProperty(String name) {
            super(Float.class, name);
        }

        public abstract void setValue(T object, float value);

        @Override
        public final void set(T object, Float value) {
            setValue(object, value);
        }
    }

    public static abstract class IntProperty<T> extends Property<T, Integer> {

        public IntProperty(String name) {
            super(Integer.class, name);
        }

        public abstract void setValue(T object, int value);

        @Override
        public final void set(T object, Integer value) {
            setValue(object, value);
        }
    }

    public static final Property<Paint, Integer> PAINT_ALPHA = new IntProperty<Paint>("alpha") {
        @Override
        public void setValue(Paint object, int value) {
            object.setAlpha(value);
        }

        @Override
        public Integer get(Paint object) {
            return object.getAlpha();
        }
    };

    public static final Property<Paint, Integer> PAINT_COLOR = new IntProperty<Paint>("color") {
        @Override
        public void setValue(Paint object, int value) {
            object.setColor(value);
        }

        @Override
        public Integer get(Paint object) {
            return object.getColor();
        }
    };

    public static final Property<ImageReceiver, Float> IMAGE_RECEIVER_ALPHA = new FloatProperty<ImageReceiver>("currentAlpha") {
        @Override
        public void setValue(ImageReceiver object, float value) {
            object.setCurrentAlpha(value);
        }

        @Override
        public Float get(ImageReceiver object) {
            return object.getCurrentAlpha();
        }
    };

    public static final Property<Drawable, Integer> DRAWABLE_ALPHA = new IntProperty<Drawable>("alpha") {
        @Override
        public void setValue(Drawable object, int value) {
            object.setAlpha(value);
        }

        @Override
        public Integer get(Drawable object) {
            return object.getAlpha();
        }
    };

    public static final Property<Drawable, Integer> COLOR_DRAWABLE_ALPHA = DRAWABLE_ALPHA;

    public static final Property<ShapeDrawable, Integer> SHAPE_DRAWABLE_ALPHA = new IntProperty<ShapeDrawable>("alpha") {
        @Override
        public void setValue(ShapeDrawable object, int value) {
            object.getPaint().setAlpha(value);
        }

        @Override
        public Integer get(ShapeDrawable object) {
            return object.getPaint().getAlpha();
        }
    };

    public static final Property<ClippingImageView, Float> CLIPPING_IMAGE_VIEW_PROGRESS = new FloatProperty<ClippingImageView>("animationProgress") {
        @Override
        public void setValue(ClippingImageView object, float value) {
            object.setAnimationProgress(value);
        }

        @Override
        public Float get(ClippingImageView object) {
            return object.getAnimationProgress();
        }
    };

    public static final Property<PhotoViewer, Float> PHOTO_VIEWER_ANIMATION_VALUE = new FloatProperty<PhotoViewer>("animationValue") {
        @Override
        public void setValue(PhotoViewer object, float value) {
            object.setAnimationValue(value);
        }

        @Override
        public Float get(PhotoViewer object) {
            return object.getAnimationValue();
        }
    };
}

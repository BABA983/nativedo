/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/SimpleFloatPropertyCompat.java.
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
package org.telegram.ui.Components;

import androidx.dynamicanimation.animation.FloatPropertyCompat;

public class SimpleFloatPropertyCompat<T> extends FloatPropertyCompat<T> {
    private Getter<T> getter;
    private Setter<T> setter;
    private float multiplier = 1f;

    public SimpleFloatPropertyCompat(String name, Getter<T> getter, Setter<T> setter) {
        super(name);
        this.getter = getter;
        this.setter = setter;
    }

    public SimpleFloatPropertyCompat<T> setMultiplier(float multiplier) {
        this.multiplier = multiplier;
        return this;
    }

    public float getMultiplier() {
        return multiplier;
    }

    @Override
    public float getValue(T object) {
        return getter.get(object) * multiplier;
    }

    @Override
    public void setValue(T object, float value) {
        setter.set(object, value / multiplier);
    }

    public interface Getter<T> {
        float get(T obj);
    }

    public interface Setter<T> {
        void set(T obj, float value);
    }
}

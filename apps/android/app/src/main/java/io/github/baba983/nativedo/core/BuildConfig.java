/*
 * This file is part of NativeDo.
 *
 * Provides a compile-compatible shim for Telegram's BuildConfig so that
 * ported UI code can reference BuildConfig.DEBUG_PRIVATE_VERSION.
 *
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.core;

/**
 * Minimal shim matching Telegram's {@code BuildConfig} API surface.
 */
public final class BuildConfig {

    /** Always false; NativeDo does not have Telegram's private build variant. */
    public static final boolean DEBUG_PRIVATE_VERSION = false;

    private BuildConfig() {
    }
}

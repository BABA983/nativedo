// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/messenger/AnimationNotificationsLocker.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: compile-shim=L2, animation-notification-lock=L2
// HOST-WIRING:
//   - Preserves lock/unlock callsites used by Telegram-derived animation code.
// ADAPTED:
//   - Methods are no-ops because NativeDo does not have Telegram NotificationCenter delivery deferral.
// OMITTED:
//   - Account-scoped NotificationCenter animation indexes and delivery pause/resume behavior.
/*
 * This file is part of NativeDo.
 *
 * Compile-only shim for Telegram for Android's
 * org/telegram/messenger/AnimationNotificationsLocker.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/AnimationNotificationsLocker.java
 *
 * NativeDo slice: a no-op stub so that L0 ports of Telegram code that call
 * `notificationsLocker.lock()` / `unlock()` compile without pulling in the
 * full NotificationCenter delivery-deferral machinery. The lock is meant to
 * pause notification delivery while a UI animation runs; on this client the
 * underlying delivery system does not exist, so locking is a no-op. Replace
 * the body when (and only when) we re-introduce notification deferral.
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
package io.github.baba983.nativedo.core;

public class AnimationNotificationsLocker {

    public AnimationNotificationsLocker() {
    }

    public AnimationNotificationsLocker(int[] allowedNotifications) {
    }

    public void lock() {
    }

    public void lock(long maxDuration) {
    }

    public void unlock() {
    }

    public void unlock(long delay) {
    }
}

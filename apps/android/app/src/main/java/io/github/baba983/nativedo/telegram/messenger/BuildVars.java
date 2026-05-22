// PORT-MODE: Native Feature
// UPSTREAM-SCOPE: org/telegram/messenger/BuildVars.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - None.
// ADAPTED-BACKEND:
//   - DEBUG_PRIVATE_VERSION always false; NativeDo does not have Telegram's private build variant.
// OMITTED:
//   - Telegram's full BuildVars fields.
/*
 * Stub for compile compatibility.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.telegram.messenger;

public final class BuildVars {
    public static final boolean DEBUG_PRIVATE_VERSION = false;
    private BuildVars() {}
}

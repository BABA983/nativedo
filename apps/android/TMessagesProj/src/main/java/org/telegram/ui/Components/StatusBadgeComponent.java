/*
 * This file is part of NativeDo.
 *
 * Native shim for Telegram for Android's org/telegram/ui/Components/StatusBadgeComponent.java.
 * The upstream component renders Telegram-only badges (verified check, emoji status,
 * premium star) next to a name; NativeDo models carry none of that, so the shim keeps the
 * pin's public API and always reports "no badge".
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class StatusBadgeComponent {

    public StatusBadgeComponent(View parentView) {
    }

    public StatusBadgeComponent(View parentView, int sizeDp) {
    }

    public Drawable updateDrawable(TLObject object, int colorFilter, boolean animated) {
        return null;
    }

    public Drawable updateDrawable(TLRPC.User user, TLRPC.Chat chat, int colorFilter, boolean animated) {
        return null;
    }

    public Drawable getDrawable() {
        return null;
    }

    public void onAttachedToWindow() {
    }

    public void onDetachedFromWindow() {
    }
}

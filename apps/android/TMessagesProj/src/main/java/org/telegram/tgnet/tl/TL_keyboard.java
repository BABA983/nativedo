/*
 * Host surface for Telegram TL_keyboard types: the rich-message button row's
 * button model. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.tgnet.tl;

import org.telegram.tgnet.TLObject;

/**
 * The pin's keyboard model, carried as data holders for the rich-message
 * pipeline ({@code TL_iv.pageBlockButtonRow}, {@code TL_iv.textButton}): the
 * field subset our ports read and write, no serializers, and the button
 * protocol as a marker interface until a caller needs its accessors (the pin's
 * version carries {@code getType}/{@code getText}/{@code getUrl}).
 *
 * The concrete button types ({@code TL_inlineButtonTypeUrl}, … {@code TL_inlineButtonTypeBuy},
 * and the {@code TL_button}/reply families) land with the feature that builds
 * them, not before.
 */
public final class TL_keyboard {
    private TL_keyboard() {}

    public abstract static class ButtonTypeProto extends TLObject {
    }

    public abstract static class InlineButtonType extends ButtonTypeProto {
    }

    /** Marker for the pin's interface; the accessors arrive with the button row itself. */
    public interface KeyboardButtonProto {
    }

    public static class RichButtonStyle extends TLObject {
        public int flags;
        public boolean bg_primary;
        public boolean bg_danger;
        public boolean bg_success;
        public boolean link;
    }

    public static class PageButton extends TLObject implements KeyboardButtonProto {
        public static final int constructor = 0x692A5488;

        public int flags;
        public TL_iv.RichText text;
        public InlineButtonType type;
        public RichButtonStyle style;
    }
}

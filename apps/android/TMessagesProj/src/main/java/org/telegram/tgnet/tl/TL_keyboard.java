/*
 * Host surface for Telegram TL_keyboard types: the rich-message button row's
 * button model plus the reply-keyboard row/button holders, from the pin's
 * org/telegram/tgnet/tl/TL_keyboard.java.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.tgnet.tl;

import org.telegram.tgnet.TLObject;

import java.util.ArrayList;

/**
 * The pin's keyboard model, carried as data holders: the field subset our ports
 * read and write, no serializers. Types grow here as a caller needs them; the
 * pin's layer-variant subclasses come with the wire layer.
 */
public final class TL_keyboard {
    private TL_keyboard() {}

    public abstract static class ButtonTypeProto extends TLObject {}

    public abstract static class InlineButtonType extends ButtonTypeProto {}

    public interface KeyboardButtonProto {

        ButtonTypeProto getType();

        String getText();

        @Deprecated
        default String getUrl() {
            final ButtonTypeProto type = getType();
            if (type instanceof TL_inlineButtonTypeUrl) {
                return ((TL_inlineButtonTypeUrl)type).url;
            }
            if (type instanceof TL_inlineButtonTypeUrlAuth) {
                return ((TL_inlineButtonTypeUrlAuth) type).url;
            }
            if (type instanceof TL_inlineButtonTypeWebView) {
                return ((TL_inlineButtonTypeWebView) type).url;
            }
            if (type instanceof TL_buttonTypeSimpleWebView) {
                return ((TL_buttonTypeSimpleWebView) type).url;
            }
            return null;
        }

        @Deprecated
        default byte[] getData() {
            final ButtonTypeProto type = getType();
            if (type instanceof TL_inlineButtonTypeCallback) {
                return ((TL_inlineButtonTypeCallback) type).data;
            }

            return null;
        }
    }

    public static class TL_inlineButtonTypeUrl extends InlineButtonType {
        public static final int constructor = 0xECA4F8D4;

        public String url;
    }

    public static class TL_inlineButtonTypeWebView extends InlineButtonType {
        public static final int constructor = 0x3BCAB5B4;

        public String url;
    }

    public static class TL_inlineButtonTypeCopy extends InlineButtonType {
        public static final int constructor = 0xB41D3272;

        public String copy_text;
    }

    public static class TL_inlineButtonTypeUserProfile extends InlineButtonType {
        public static final int constructor = 0x3FA33FCF;

        public long user_id;
    }

    public static class TL_inlineButtonTypeUrlAuth extends InlineButtonType {
        public static final int constructor = 0xBFD02DA2;

        public int flags;
        public String fwd_text;
        public String url;
        public int button_id;
    }

    public static class TL_inlineButtonTypeSwitchInline extends InlineButtonType {
        public static final int constructor = 0x93773FF5;

        public int flags;
        public boolean same_peer;
        public String query;
        // pin: public ArrayList<TLRPC.InlineQueryPeerType> peer_types;
    }

    public static class TL_inlineButtonTypeBuy extends InlineButtonType {
        public static final int constructor = 0x48BAD7A5;
    }

    public static class TL_inlineButtonTypeCallback extends InlineButtonType {
        public static final int constructor = 0x2955BC38;

        public int flags;
        public boolean requires_password;
        public byte[] data;
    }

    public static class TL_inlineButtonTypeDisabled extends InlineButtonType {
        public static final int constructor = 0xA438619D;
    }

    public abstract static class ButtonType extends ButtonTypeProto {}

    public static class TL_buttonTypeSimpleWebView extends ButtonType {
        public static final int constructor = 0xC01A597A;

        public String url;
    }

    public static class PageButton extends TLObject implements KeyboardButtonProto {
        public static final int constructor = 0x692A5488;

        public int flags;
        public TL_iv.RichText text;
        public InlineButtonType type;
        public RichButtonStyle style;

        @Override
        public InlineButtonType getType() {
            return type;
        }

        @Override
        public String getText() {
            if (text instanceof TL_iv.textPlain) {
                return ((TL_iv.textPlain) text).text;
            }
            return null;
        }
    }

    public static class RichButtonStyle extends TLObject {
        public static final int constructor = 0x03C610BD;

        public int flags;
        public boolean bg_primary;
        public boolean bg_danger;
        public boolean bg_success;
        public boolean link;
    }

    public static class KeyboardButtonStyle extends TLObject {
        public static final int constructor = 0x4FDD3430;

        public int flags;
        public boolean bg_primary;
        public boolean bg_danger;
        public boolean bg_success;
        public long icon;
    }

    public static class KeyboardButtonRow extends TLObject {
        public static final int constructor = 0x77608b83;

        public ArrayList<KeyboardButton> buttons = new ArrayList<>();
    }

    public static abstract class KeyboardInlineButtonRow extends TLObject {
        public ArrayList<KeyboardInlineButton> buttons = new ArrayList<>();
    }

    public static class TL_keyboardInlineButtonRow extends KeyboardInlineButtonRow {
        public static final int constructor = 0x19420AF6;
    }

    public abstract static class KeyboardButton extends TLObject implements KeyboardButtonProto {
        protected int flags;
        public String text;
        public KeyboardButtonStyle style;
        public ButtonType type;

        @Override
        public ButtonType getType() {
            return type;
        }

        @Override
        public String getText() {
            return text;
        }
    }

    public static abstract class KeyboardInlineButton extends TLObject implements KeyboardButtonProto {
        public int flags;
        public String text;
        public KeyboardButtonStyle style;
        public InlineButtonType type;

        @Override
        public InlineButtonType getType() {
            return type;
        }

        @Override
        public String getText() {
            return text;
        }
    }

    public static class TL_keyboardInlineButton extends KeyboardInlineButton {
        public static final int constructor = 0x11C1A322;
    }
}

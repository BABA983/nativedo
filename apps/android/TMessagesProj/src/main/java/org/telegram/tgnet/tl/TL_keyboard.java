/*
 * Host surface for Telegram TL_keyboard types: the rich-message button row's
 * button model plus the reply-keyboard row/button holders, from the pin's
 * org/telegram/tgnet/tl/TL_keyboard.java.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.tgnet.tl;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;

import java.util.ArrayList;

/**
 * The pin's keyboard model: the field subset our ports read and write, with the
 * wire closure the editor's button row reaches - {@link PageButton},
 * {@link RichButtonStyle} and the {@link InlineButtonType} family the editor
 * builds (url / copy / user-profile) plus the types a loaded post may carry.
 * Constructor ids, flags and field order come from the pin's
 * org/telegram/tgnet/tl/TL_keyboard.java.
 *
 * Left as data holders on purpose: the reply-keyboard row/button holders and
 * their {@link ButtonType} ({@code KeyboardButton}, {@code KeyboardInlineButton},
 * {@code TL_buttonTypeSimpleWebView}), and the pin's network-only inline types
 * ({@code Game}, the two {@code input*} variants). None of them is reachable
 * from the rich editor, and none is serialized here.
 */
public final class TL_keyboard {
    private TL_keyboard() {}

    public abstract static class ButtonTypeProto extends TLObject {}

    public abstract static class InlineButtonType extends ButtonTypeProto {

        public static InlineButtonType TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            return TLdeserialize(InlineButtonType.class, fromConstructor(constructor), stream, constructor, exception);
        }

        private static InlineButtonType fromConstructor(int constructor) {
            switch (constructor) {
                case TL_inlineButtonTypeUrl.constructor:
                    return new TL_inlineButtonTypeUrl();
                case TL_inlineButtonTypeUrlAuth.constructor:
                    return new TL_inlineButtonTypeUrlAuth();
                case TL_inlineButtonTypeWebView.constructor:
                    return new TL_inlineButtonTypeWebView();
                case TL_inlineButtonTypeCallback.constructor:
                    return new TL_inlineButtonTypeCallback();
                case TL_inlineButtonTypeBuy.constructor:
                    return new TL_inlineButtonTypeBuy();
                case TL_inlineButtonTypeSwitchInline.constructor:
                    return new TL_inlineButtonTypeSwitchInline();
                case TL_inlineButtonTypeUserProfile.constructor:
                    return new TL_inlineButtonTypeUserProfile();
                case TL_inlineButtonTypeCopy.constructor:
                    return new TL_inlineButtonTypeCopy();
                case TL_inlineButtonTypeDisabled.constructor:
                    return new TL_inlineButtonTypeDisabled();
                default:
                    return null;
            }
        }
    }

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

        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
        }
    }

    public static class TL_inlineButtonTypeWebView extends InlineButtonType {
        public static final int constructor = 0x3BCAB5B4;

        public String url;

        public void readParams(InputSerializedData stream, boolean exception) {
            url = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(url);
        }
    }

    public static class TL_inlineButtonTypeCopy extends InlineButtonType {
        public static final int constructor = 0xB41D3272;

        public String copy_text;

        public void readParams(InputSerializedData stream, boolean exception) {
            copy_text = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeString(copy_text);
        }
    }

    public static class TL_inlineButtonTypeUserProfile extends InlineButtonType {
        public static final int constructor = 0x3FA33FCF;

        public long user_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            user_id = stream.readInt64(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            stream.writeInt64(user_id);
        }
    }

    public static class TL_inlineButtonTypeUrlAuth extends InlineButtonType {
        public static final int constructor = 0xBFD02DA2;

        public int flags;
        public String fwd_text;
        public String url;
        public int button_id;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            if (hasFlag(flags, FLAG_0)) {
                fwd_text = stream.readString(exception);
            }
            url = stream.readString(exception);
            button_id = stream.readInt32(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, fwd_text != null);
            stream.writeInt32(flags);
            if (hasFlag(flags, FLAG_0)) {
                stream.writeString(fwd_text);
            }
            stream.writeString(url);
            stream.writeInt32(button_id);
        }
    }

    public static class TL_inlineButtonTypeSwitchInline extends InlineButtonType {
        public static final int constructor = 0x93773FF5;

        public int flags;
        public boolean same_peer;
        public String query;
        // pin: public ArrayList<TLRPC.InlineQueryPeerType> peer_types (flag 1). Not carried here:
        // TLRPC.InlineQueryPeerType has no serializer in this tree, and the editor never builds a
        // switch-inline button (RichInlineButtonSpan.isSupported). The flag is cleared so a write
        // never emits a vector this class cannot produce.

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            same_peer = hasFlag(flags, FLAG_0);
            query = stream.readString(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, same_peer);
            flags = setFlag(flags, FLAG_1, false);
            stream.writeInt32(flags);
            stream.writeString(query);
        }
    }

    public static class TL_inlineButtonTypeBuy extends InlineButtonType {
        public static final int constructor = 0x48BAD7A5;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
    }

    public static class TL_inlineButtonTypeCallback extends InlineButtonType {
        public static final int constructor = 0x2955BC38;

        public int flags;
        public boolean requires_password;
        public byte[] data;

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            requires_password = hasFlag(flags, FLAG_0);
            data = stream.readByteArray(exception);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, requires_password);
            stream.writeInt32(flags);
            stream.writeByteArray(data);
        }
    }

    public static class TL_inlineButtonTypeDisabled extends InlineButtonType {
        public static final int constructor = 0xA438619D;

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
        }
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

        public static PageButton TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final PageButton result = constructor != PageButton.constructor ? null : new PageButton();
            return TLdeserialize(PageButton.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            text = TL_iv.RichText.TLdeserialize(stream, stream.readInt32(exception), exception);
            type = InlineButtonType.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (hasFlag(flags, FLAG_0)) {
                style = RichButtonStyle.TLdeserialize(stream, stream.readInt32(exception), exception);
            }
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, style != null);
            stream.writeInt32(flags);
            text.serializeToStream(stream);
            type.serializeToStream(stream);
            if (hasFlag(flags, FLAG_0)) {
                style.serializeToStream(stream);
            }
        }

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

        public static RichButtonStyle TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
            final RichButtonStyle result = constructor != RichButtonStyle.constructor ? null : new RichButtonStyle();
            return TLdeserialize(RichButtonStyle.class, result, stream, constructor, exception);
        }

        public void readParams(InputSerializedData stream, boolean exception) {
            flags = stream.readInt32(exception);
            bg_primary = hasFlag(flags, FLAG_0);
            bg_danger = hasFlag(flags, FLAG_1);
            bg_success = hasFlag(flags, FLAG_2);
            link = hasFlag(flags, FLAG_3);
        }

        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(constructor);
            flags = setFlag(flags, FLAG_0, bg_primary);
            flags = setFlag(flags, FLAG_1, bg_danger);
            flags = setFlag(flags, FLAG_2, bg_success);
            flags = setFlag(flags, FLAG_3, link);
            stream.writeInt32(flags);
        }
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

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import org.telegram.tgnet.tl.TL_iv;

/**
 * NativeDo-trimmed stub of org.telegram.messenger.MessageObject.
 *
 * <p>Upstream {@code MessageObject} is ~13000 lines and pulls in MediaController,
 * MessagesController, account context, full TL serialization, etc. For LinuxDo's
 * HTTP-only image pipeline we only need {@code MessageObject} to exist as a
 * type so {@code ImageReceiver/ImageLoader/FileLoader} signatures resolve. We
 * expose a small set of getters that callers read; everything else returns
 * null/false/0.
 *
 * <p>Fields are added on demand as compilation requires them.
 */
public class MessageObject {

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_PHOTO = 1;
    public static final int TYPE_VIDEO = 3;
    public static final int TYPE_GIF = 8;
    public static final int TYPE_STICKER = 13;
    public static final int TYPE_ANIMATED_STICKER = 15;
    public static final int TYPE_PAID_MEDIA = 28;
    public static final int TYPE_STORY = 23;
    public static final int TYPE_ROUND_VIDEO = 5;
    public static final int TYPE_VOICE = 2;
    public static final int POSITION_FLAG_LEFT = 1;
    public static final int POSITION_FLAG_RIGHT = 2;
    public static final int POSITION_FLAG_TOP = 4;
    public static final int POSITION_FLAG_BOTTOM = 8;

    public int currentAccount;
    public int type;
    public boolean putInDownloadsStore;
    public boolean isDownloadingFile;
    public boolean mediaExists;
    public Object lastGeoWebFileSet;
    public Object lastGeoWebFileLoaded;
    public TLRPC.Message messageOwner;
    public TLObject photoThumbsObject;
    public TLObject photoThumbsObject2;
    public java.util.ArrayList<TLRPC.PhotoSize> photoThumbs;
    public java.util.ArrayList<TLRPC.PhotoSize> photoThumbs2;
    public TLRPC.Document emojiAnimatedSticker;
    public boolean preview;
    public boolean isLivePhoto;
    public boolean videoEditedInfoSet;
    public VideoEditedInfo videoEditedInfo;
    public MediaController.CropState cropState;
    public float forceSeekTo = -1;
    public int ttl;
    public String sponsoredUrl;
    public TLRPC.MessageMedia sponsoredMedia;
    public String sponsoredButtonText;
    public java.util.ArrayList<Object> entities = new java.util.ArrayList<>();
    public CharSequence caption;
    public CharSequence messageText;
    public CharSequence youtubeDescription;
    public boolean scheduled;
    public float audioProgress;
    public int audioProgressSec;
    public int audioPlayerDuration;

    public MessageObject() {
    }

    public MessageObject(int account, TLRPC.Message message, boolean generateLayout, boolean checkMediaExists) {
        currentAccount = account;
        messageOwner = message;
    }

    public static class GroupedMessagePosition {
        public byte flags;
        public int minX;
        public int maxX;
        public int minY;
        public int maxY;
        public float pw;
        public float ph;
        public float left;
        public float top;
        public float right;
        public float bottom;
        public float aspectRatio;
        public float[] siblingHeights;
        public int leftSpanOffset;
        public int spanSize;
        public boolean edge;
        public boolean last;

        public void set(int minX, int maxX, int minY, int maxY, int w, float h, int flags) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.pw = w;
            this.ph = h;
            this.flags = (byte) flags;
        }
    }

    public TLRPC.Document getDocument() {
        return null;
    }

    public TLRPC.Photo getPhoto() {
        return null;
    }

    public boolean isVideo() {
        return false;
    }

    public boolean isYouTubeVideo() {
        return false;
    }

    public boolean isOut() {
        return false;
    }

    public boolean isGif() {
        return false;
    }

    public boolean isSticker() {
        return false;
    }

    public boolean isAnimatedSticker() {
        return false;
    }

    public int getId() {
        return 0;
    }

    public long getDialogId() {
        return 0L;
    }

    public long getGroupIdForUse() {
        return messageOwner != null ? messageOwner.grouped_id : 0L;
    }

    public String getFileName() {
        return null;
    }

    public boolean isEditing() {
        return false;
    }

    public boolean isSendError() {
        return false;
    }

    /** Matches upstream {@code MessageObject.getMedia(Message)}. */
    public static TLRPC.MessageMedia getMedia(TLRPC.Message message) {
        if (message == null) {
            return null;
        }
        return message.media;
    }

    /** Matches upstream {@code MessageObject.getMedia(MessageObject)}. */
    public static TLRPC.MessageMedia getMedia(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        return getMedia(messageObject.messageOwner);
    }

    public static boolean isVoiceDocument(TLRPC.Document document) {
        return false;
    }

    public static boolean isVideoDocument(TLRPC.Document document) {
        return false;
    }

    public static boolean isDocumentHasThumb(TLRPC.Document document) {
        return document != null && document.thumbs != null && !document.thumbs.isEmpty();
    }

    public static boolean isVideoSticker(TLRPC.Document document) {
        return false;
    }

    public static boolean isStickerDocument(TLRPC.Document document) {
        return false;
    }

    public static boolean isAnimatedStickerDocument(TLRPC.Document document, boolean allowWithoutSet) {
        return false;
    }

    public static boolean isGifDocument(TLRPC.Document document) {
        return false;
    }

    public static boolean isWebM(TLRPC.Document document) {
        return false;
    }

    public static boolean isImageWebDocument(TLRPC.WebDocument document) {
        return false;
    }

    public static boolean isPhoto(MessageObject message) {
        return false;
    }

    public boolean isAnyKindOfSticker() {
        return false;
    }

    public boolean isRoundVideo() {
        return false;
    }

    public boolean isMusic() {
        return false;
    }

    public boolean isVoice() {
        return false;
    }

    public boolean isDocument() {
        return false;
    }

    public boolean isSending() {
        return false;
    }

    public static boolean isRoundVideoDocument(TLRPC.Document document) {
        return false;
    }

    public static boolean isVoiceWebDocument(TLRPC.WebDocument document) {
        return false;
    }

    public static boolean isVideoWebDocument(TLRPC.WebDocument document) {
        return false;
    }

    public static boolean isVoiceWebDocument(WebFile document) {
        return false;
    }

    public static boolean isVideoWebDocument(WebFile document) {
        return false;
    }

    public static boolean isImageWebDocument(WebFile document) {
        return false;
    }

    public static void addUrlsByPattern(boolean isOut, CharSequence charSequence, boolean botCommands, int patternType, int duration, boolean check) {
    }

    public long getSize() {
        return 0L;
    }

    public int getDuration() {
        return 0;
    }

    public String getMusicTitle(boolean unknown) {
        return "";
    }

    public String getMusicAuthor(boolean unknown) {
        return "";
    }

    public void checkMediaExistance() {
    }

    public int getMediaType() {
        return 0;
    }

    public static boolean canPreviewDocument(TLRPC.Document document) {
        return false;
    }

    public static boolean shouldEncryptPhotoOrVideo(int currentAccount, TLRPC.Message message) {
        return false;
    }

    public boolean isSponsored() {
        return sponsoredUrl != null;
    }

    public boolean isOutOwner() {
        return false;
    }

    public int getQuickReplyId() {
        return 0;
    }

    public CharSequence translatedText;
    public String sponsoredAdditionalInfo;

    public static boolean isGifDocument(WebFile document) {
        return false;
    }

    public static boolean isVideoSticker(WebFile document) {
        return false;
    }

    /** Upstream's helper: the sticker set a document belongs to, or null. */
    public static TLRPC.InputStickerSet getInputStickerSet(TLRPC.Document document) {
        if (document == null) {
            return null;
        }
        for (int a = 0, N = document.attributes.size(); a < N; a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker ||
                    attribute instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                    return null;
                }
                return attribute.stickerset;
            }
        }
        return null;
    }

    /** Upstream's helper: a custom emoji that renders as text color rather than as a picture. */
    public static boolean isTextColorEmoji(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        for (int a = 0, N = document.attributes.size(); a < N; a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetID && attribute.stickerset.id == 1269403972611866647L) {
                    return true;
                }
                return ((TLRPC.TL_documentAttributeCustomEmoji) attribute).text_color;
            }
        }
        return false;
    }

    // --- pin members the rich-message renderer reads (host surface) ---

    public boolean isSpoilersRevealed;
    public TLRPC.PeerColor sponsoredColor;
    public int overrideLinkColor = -1;
    public TLRPC.TL_peerColorCollectible overrideLinkPeerColor;
    public boolean translated;


    public boolean shouldDrawWithoutBackground() {
        return !isSponsored() && (type == TYPE_STICKER || type == TYPE_ANIMATED_STICKER || type == TYPE_ROUND_VIDEO);
    }

    public static long getFromChatId(TLRPC.Message message) {
        return getPeerId(message.from_id);
    }

    public long getFromChatId() {
        return getFromChatId(messageOwner);
    }

    public static long getPeerId(TLRPC.Peer peer) {
        if (peer == null) {
            return 0;
        }
        if (peer instanceof TLRPC.TL_peerChat) {
            return -peer.chat_id;
        } else if (peer instanceof TLRPC.TL_peerChannel) {
            return -peer.channel_id;
        } else if (peer instanceof TLRPC.TL_peerUser) {
            return peer.user_id;
        }
        return 0;
    }

    public int getRealId() {
        return messageOwner.realId != 0 ? messageOwner.realId : (int) messageOwner.id;
    }

    public TL_iv.RichMessage getDisplayRichMessage() {
        if (messageOwner == null) {
            return null;
        }
        if (translated && messageOwner.translatedRichMessage != null) {
            return messageOwner.translatedRichMessage;
        }
        return messageOwner.rich_message;
    }

    public boolean isFromUser() {
        return messageOwner.from_id instanceof TLRPC.TL_peerUser && !messageOwner.post;
    }

    /**
     * Pin shape minus the mega-group check: the pin asks ChatObject about the resolved chat, and
     * this port carries no chat state for a Discourse topic.
     */
    public boolean isFromChannel() {
        return messageOwner.from_id instanceof TLRPC.TL_peerChannel;
    }

    public static StaticLayout makeStaticLayout(CharSequence text_, TextPaint paint, int width, float lineSpacingMult, float lineSpacingAdd, boolean dontIncludePad) {
        return makeStaticLayout(text_, paint, width, lineSpacingMult, lineSpacingAdd, dontIncludePad, Layout.Alignment.ALIGN_NORMAL);
    }

    public static StaticLayout makeStaticLayout(CharSequence text_, TextPaint paint, int width, float lineSpacingMult, float lineSpacingAdd, boolean dontIncludePad, Layout.Alignment alignment) {
        if (width <= 0) width = 1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final CharSequence text = /* Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                PrecomputedText.create(text_, new PrecomputedText.Params.Builder(paint).build()) :*/ text_;

            StaticLayout.Builder builder =
                    StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                            .setLineSpacing(lineSpacingAdd, lineSpacingMult)
                            .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                            .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                            .setAlignment(alignment);
            if (dontIncludePad) {
                builder.setIncludePad(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setUseLineSpacingFromFallbacks(false);
            }
            StaticLayout layout = builder.build();

            boolean realWidthLarger = false;
            for (int l = 0; l < layout.getLineCount(); ++l) {
                if (layout.getLineRight(l) > width) {
                    realWidthLarger = true;
                    break;
                }
            }
            if (realWidthLarger) {
                builder = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                                .setLineSpacing(lineSpacingAdd, lineSpacingMult)
                                .setBreakStrategy(StaticLayout.BREAK_STRATEGY_SIMPLE)
                                .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                                .setAlignment(alignment);
                if (dontIncludePad) {
                    builder.setIncludePad(false);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setUseLineSpacingFromFallbacks(false);
                }
                layout = builder.build();
            }

            return layout;
        } else {
            return new StaticLayout(text_, paint, width, alignment, lineSpacingMult, lineSpacingAdd, false);
        }
    }

    private static String capitalizeFirst(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /** Pin holder for a laid-out text block; only the language label is read by the renderer. */
    public static class TextLayoutBlock {
        public static String capitalizeLanguage(String lng) {
            if (lng == null) return null;
            String llng = lng.toLowerCase().replaceAll("\\W|lang$", "");
            switch (llng) {
                case "js":
                case "javascript":
                    return "JavaScript";
                case "ts":
                case "typescript":
                    return "TypeScript";
                case "objc":
                case "objectivec":
                    return "Objective-C";
                case "md":
                case "markdown":
                    return "Markdown";
                case "rb":
                case "ruby":
                    return "Ruby";
                case "py":
                case "python":
                    return "Python";
                case "visual-basic":
                    return "Visual Basic";
                case "vbnet":
                    return "VB.NET";
                case "gdscript":
                    return "GDScript";
                case "bbcode":
                    return "BBCode";
                case "actionscript": return "ActionScript";
                case "autohotkey": return "AutoHotKey";
                case "cpp": return "C++";
                case "csharp":
                case "cs":
                    return "C#";
                case "fsharp":
                    return "F#";
                case "aspnet": return "ASP.NET";
                case "http":
                case "html":
                case "css":
                case "scss":
                case "less":
                case "asm":
                case "nasm":
                case "wasm":
                case "xml":
                case "yaml":
                case "yml":
                case "php":
                case "json":
                case "json5":
                case "r":
                case "ini":
                case "glsl":
                case "hlsl":
                case "csv":
                case "cobol":
                case "jsx":
                case "tsx":
                case "tl":
                case "sql":
                case "qml":
                case "sass":
                case "matlab":
                case "jsonp":
                    return lng.toUpperCase();
                case "tl-b":
                case "tlb":
                    return "TL-B";
                case "func":
                    return "FunC";
            }
            return capitalizeFirst(lng);
        }
    }
}

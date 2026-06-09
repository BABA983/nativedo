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
}

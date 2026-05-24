/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.MediaController.
 * Upstream coordinates audio/video playback, voice messages, gallery scanning,
 * round-video recording, etc. NativeDo doesn't yet wire any of that — only the
 * static helpers and inner types PhotoViewer/ImageLoader reference are exposed.
 */
public class MediaController {

    public static final int VIDEO_BITRATE_AUTO = -1;
    public static final int MEDIA_FILE = 0;
    public static final int MEDIA_PHOTO = 1;
    public static final int MEDIA_VIDEO = 2;
    public static final int MEDIA_DOCUMENT = 3;

    private static final MediaController[] Instance = new MediaController[UserConfig.MAX_ACCOUNT_COUNT];

    private MediaController() {
    }

    public static MediaController getInstance() {
        return getInstance(UserConfig.selectedAccount);
    }

    public static MediaController getInstance(int account) {
        MediaController local = Instance[account];
        if (local == null) {
            synchronized (MediaController.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new MediaController();
                }
            }
        }
        return local;
    }

    public boolean isPlayingMessage(MessageObject messageObject) {
        return false;
    }

    public boolean isMessagePaused() {
        return false;
    }

    public void cleanupPlayer(boolean notify, boolean stopService) {
    }

    public void cleanupPlayer(boolean playNext, boolean notify, boolean stopService, boolean byVoiceEnd) {
    }

    public MessageObject getPlayingMessageObject() {
        return null;
    }

    public boolean playMessage(MessageObject messageObject) {
        return false;
    }

    public boolean pauseMessage(MessageObject messageObject) {
        return false;
    }

    public boolean lockOrientation(int orientation) {
        return false;
    }

    public boolean unlockOrientation() {
        return false;
    }

    public static class MediaEditState {
        public CharSequence caption;
        public String paintPath;
        public String croppedPaintPath;
        public String thumbPath;
        public String imagePath;
        public String fullPaintPath;
        public CropState cropState;
        public VideoEditedInfo editedInfo;
        public java.util.ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
        public java.util.ArrayList<VideoEditedInfo.MediaEntity> croppedMediaEntities;
        public String filterPath;
        public boolean isFiltered;
        public boolean isPainted;
        public boolean isCropped;
    }

    public static class CropState {
        public float cropPx;
        public float cropPy;
        public float cropScale = 1f;
        public float cropRotate;
        public float cropPw = 1f;
        public float cropPh = 1f;
        public int transformWidth;
        public int transformHeight;
        public int transformRotation;
        public boolean mirrored;
        public float initialOrientation;
        public float orientation;
        public boolean useMatrix;
        public android.graphics.Matrix matrix;
        public boolean lockedAspectRatio;

        public CropState clone() {
            CropState s = new CropState();
            s.cropPx = cropPx;
            s.cropPy = cropPy;
            s.cropScale = cropScale;
            s.cropRotate = cropRotate;
            s.cropPw = cropPw;
            s.cropPh = cropPh;
            s.transformWidth = transformWidth;
            s.transformHeight = transformHeight;
            s.transformRotation = transformRotation;
            s.mirrored = mirrored;
            s.initialOrientation = initialOrientation;
            s.orientation = orientation;
            s.useMatrix = useMatrix;
            s.matrix = matrix;
            s.lockedAspectRatio = lockedAspectRatio;
            return s;
        }
    }

    public static class PhotoEntry extends MediaEditState {
        public int bucketId;
        public int imageId;
        public long dateTaken;
        public int duration;
        public int width;
        public int height;
        public long size;
        public String path;
        public String coverPath;
        public int orientation;
        public int invert;
        public boolean isVideo;
        public boolean isLivePhoto;
        public boolean isAttachSpoilerRevealed;
        public boolean hasSpoiler;
        public boolean isMuted;
        public boolean canDeleteAfter;
        public String reply_to;
        public float startTime;
        public float endTime;
        public float volume = 1f;
        public int ttl;
        public long averageDuration;
        public boolean scheduled;
        public CharSequence translatedText;
        public java.util.ArrayList<Object> entities = new java.util.ArrayList<>();
        public TLRPC.VideoSize emojiMarkup;

        public String getPath() {
            return path;
        }

        public boolean isLivePhoto() {
            return isLivePhoto;
        }
    }

    public static class SearchImage extends MediaEditState {
        public String id;
        public String imageUrl;
        public String thumbUrl;
        public String localUrl;
        public int width;
        public int height;
        public int size;
        public int type;
        public int date;
        public int ttl;
        public boolean isLivePhoto;
        public CharSequence caption;
        public TLRPC.Document document;
        public TLRPC.Photo photo;
    }

    public static class AudioEntry {
        public long id;
        public String author;
        public String title;
        public String genre;
        public int duration;
        public String path;
    }

    public static class SavedFilterState {
        public float enhanceValue;
        public float exposureValue;
        public float contrastValue;
        public float warmthValue;
        public float saturationValue;
        public float fadeValue;
        public int tintShadowsColor;
        public int tintHighlightsColor;
        public float highlightsValue;
        public float shadowsValue;
        public float vignetteValue;
        public float grainValue;
        public int blurType;
        public float sharpenValue;
        public Object curvesToolValue;
        public float blurExcludeSize;
        public android.graphics.Point blurExcludePoint;
        public float blurExcludeBlurSize;
        public float blurAngle;
        public float softenSkinValue;
    }

    public static final String VIDEO_MIME_TYPE = "video/mp4";

    public static int extractRealEncoderBitrate(int width, int height, int bitrate, boolean highQuality) {
        return bitrate;
    }

    public boolean scheduleVideoConvert(MessageObject messageObject, boolean isEmpty, boolean foreground, boolean force) {
        return false;
    }

    public static int makeVideoBitrate(int originalHeight, int originalWidth, int originalBitrate, int width, int height) {
        return originalBitrate;
    }

    public void tryResumePausedAudio() {
        // no-op
    }

    public void saveFile(String fullPath, android.app.Activity activity, int type, Object name, Object mime, java.util.function.Consumer<android.net.Uri> onSaved) {
        if (onSaved != null) onSaved.accept(null);
    }
}

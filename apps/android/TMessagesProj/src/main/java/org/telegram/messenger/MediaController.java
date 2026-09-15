/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.CurvesToolValue;

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

    /** NativeDo has no voice-record/proximity playback subsystem. */
    public boolean isRecordingOrListeningByProximity() {
        return false;
    }

    public boolean playMessage(MessageObject messageObject) {
        return false;
    }

    public boolean pauseMessage(MessageObject messageObject) {
        return false;
    }

    public void seekToProgress(MessageObject messageObject, float progress) {
    }

    public boolean setPlaylist(java.util.ArrayList<MessageObject> messages, MessageObject current, int position, boolean loadMusic, Object playlistGlobalSearchParams) {
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
        public TLRPC.PhotoSize thumbPhotoSize;
        public TLRPC.PhotoSize photoSize;
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
        public CurvesToolValue curvesToolValue = new CurvesToolValue();
        public float blurExcludeSize;
        public org.telegram.ui.Components.Point blurExcludePoint;
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
        if (fullPath == null) {
            if (onSaved != null) onSaved.accept(null);
            return;
        }
        java.io.File sourceFile = new java.io.File(fullPath);
        if (!sourceFile.exists()) {
            if (onSaved != null) onSaved.accept(null);
            return;
        }
        new Thread(() -> {
            try {
                android.net.Uri uri;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    uri = saveFileInternal(type, sourceFile);
                } else {
                    uri = saveFileLegacy(type, sourceFile);
                }
                if (onSaved != null) {
                    android.net.Uri finalUri = uri;
                    AndroidUtilities.runOnUIThread(() -> onSaved.accept(finalUri));
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (onSaved != null) {
                    AndroidUtilities.runOnUIThread(() -> onSaved.accept(null));
                }
            }
        }).start();
    }

    private static android.net.Uri saveFileInternal(int type, java.io.File sourceFile) {
        try {
            android.content.ContentValues contentValues = new android.content.ContentValues();
            String extension = FileLoader.getFileExtension(sourceFile);
            String mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            android.net.Uri uriToInsert;
            int selectedType = type;
            if ((type == 0 || type == 1) && mimeType != null) {
                if (mimeType.startsWith("image")) selectedType = 0;
                if (mimeType.startsWith("video")) selectedType = 1;
            }
            String filename = AndroidUtilities.generateFileName(selectedType, extension);
            if (selectedType == 0) {
                java.io.File dirDest = new java.io.File(android.os.Environment.DIRECTORY_PICTURES, "NativeDo");
                contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, dirDest + java.io.File.separator);
                uriToInsert = android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
                contentValues.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename);
            } else if (selectedType == 1) {
                java.io.File dirDest = new java.io.File(android.os.Environment.DIRECTORY_MOVIES, "NativeDo");
                contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, dirDest + java.io.File.separator);
                uriToInsert = android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
                contentValues.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, filename);
            } else {
                java.io.File dirDest = new java.io.File(android.os.Environment.DIRECTORY_DOWNLOADS, "NativeDo");
                contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, dirDest + java.io.File.separator);
                uriToInsert = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
                contentValues.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename);
            }
            contentValues.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType);
            android.content.Context context = ApplicationLoader.applicationContext;
            if (context == null) return null;
            android.net.Uri dstUri = context.getContentResolver().insert(uriToInsert, contentValues);
            if (dstUri != null) {
                java.io.InputStream is = new java.io.FileInputStream(sourceFile);
                java.io.OutputStream os = context.getContentResolver().openOutputStream(dstUri);
                AndroidUtilities.copyFile(is, os);
            }
            return dstUri;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static android.net.Uri saveFileLegacy(int type, java.io.File sourceFile) {
        try {
            java.io.File destFile;
            if (type == 0) {
                destFile = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "NativeDo");
                destFile.mkdirs();
                destFile = new java.io.File(destFile, AndroidUtilities.generateFileName(0, FileLoader.getFileExtension(sourceFile)));
            } else if (type == 1) {
                destFile = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "NativeDo");
                destFile.mkdirs();
                destFile = new java.io.File(destFile, AndroidUtilities.generateFileName(1, FileLoader.getFileExtension(sourceFile)));
            } else {
                destFile = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "NativeDo");
                destFile.mkdirs();
                String name = sourceFile.getName();
                destFile = new java.io.File(destFile, name);
                int idx = name.lastIndexOf('.');
                for (int a = 0; a < 10 && destFile.exists(); a++) {
                    String newName = idx != -1
                            ? name.substring(0, idx) + "(" + (a + 1) + ")" + name.substring(idx)
                            : name + "(" + (a + 1) + ")";
                    destFile = new java.io.File(destFile.getParentFile(), newName);
                }
            }
            if (!destFile.exists()) {
                destFile.createNewFile();
            }
            java.io.InputStream is = new java.io.FileInputStream(sourceFile);
            java.io.OutputStream os = new java.io.FileOutputStream(destFile);
            AndroidUtilities.copyFile(is, os);
            if (type == 2) {
                android.app.DownloadManager dm = (android.app.DownloadManager) ApplicationLoader.applicationContext.getSystemService(android.content.Context.DOWNLOAD_SERVICE);
                dm.addCompletedDownload(destFile.getName(), destFile.getName(), false, "", destFile.getAbsolutePath(), destFile.length(), true);
            } else {
                AndroidUtilities.addMediaToGallery(destFile);
            }
            return android.net.Uri.fromFile(destFile);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}

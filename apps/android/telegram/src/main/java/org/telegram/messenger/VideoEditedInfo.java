/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.VideoEditedInfo.
 * Upstream stores trim/crop/filter info before encoding via ffmpeg. NativeDo
 * doesn't edit videos; only the public fields PhotoViewer touches are kept.
 */
public class VideoEditedInfo {

    public static class MediaEntity {
        public byte type;
        public byte subType;
        public float x;
        public float y;
        public float rotation;
        public float width;
        public float height;
        public float scale;
        public CharSequence text;
        public String thumbPath;
        public android.graphics.Bitmap bitmap;
        public TLRPC.Document document;
        public int color;
        public int fontSize;
        public int alignment;
        public int customTextView;
        public String textTypeface;
        public int viewWidth;
        public int viewHeight;
        public float roundRadius;
        public boolean isAnimated;
        public boolean isVideo;
    }

    public long startTime;
    public long endTime;
    public float start;
    public float end;
    public int resultWidth;
    public int resultHeight;
    public int rotationValue;
    public int originalWidth;
    public int originalHeight;
    public int bitrate;
    public int framerate;
    public long estimatedSize;
    public long estimatedDuration;
    public long originalDuration;
    public boolean isStory;
    public boolean muted;
    public boolean roundVideo;
    public boolean shouldLimitFps;
    public String originalPath;
    public String filterState;
    public String paintPath;
    public TLRPC.Document document;
    public TLRPC.InputDocument inputDocument;
    public String fileName;
    public MediaController.CropState cropState;
    public java.util.ArrayList<MediaEntity> mediaEntities;
}

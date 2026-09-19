/*
 * Host stub for the pin's org/telegram/ui/iv/RichMediaUploader.java. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.iv;

import android.graphics.BitmapFactory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.nativedo.RichMediaUploadBridge;
import org.telegram.tgnet.TLRPC;

import java.io.File;

/**
 * NativeDo: the pin's uploader pushes a file to Telegram - {@code FileLoader.uploadFile}, then it
 * waits on {@code NotificationCenter.fileUploaded} and builds a {@code TLRPC.Photo}/{@code Document}
 * from the message media the server answers with. This client uploads to Discourse, so the pin's
 * two halves are replaced: the transport lives behind {@link RichMediaUploadBridge} (installed by
 * {@code :app}, {@code POST /uploads.json}), and the objects this class hands to its listener are
 * fabricated around the uploaded URL - {@code id} is the bridge's id for that URL (so the send path
 * can turn a media block's id back into a URL) and {@code sizes}/{@code thumbs} carry the dimensions
 * the editor's cells read. The public surface is the pin's, so the editor's call sites stay upstream.
 */
public class RichMediaUploader {

    public interface Listener {
        default void onWidthHeightResolved(int w, int h) {}
        default void onProgress(float progress) {}
        default void onPhotoUploaded(TLRPC.Photo photo) {}
        default void onVideoUploaded(TLRPC.Document document) {}
        default void onAudioUploaded(TLRPC.Document document) {}
        default void onDocumentUploaded(TLRPC.Document document) {}
        default void onError() {}
    }

    private final int currentAccount;
    private final String path;
    private final boolean isVideo;
    private final boolean isAudio;
    private final boolean isDocument;
    private final int videoWidth;
    private final int videoHeight;
    private final int videoDurationSec;
    private final TLRPC.Document audioDocument;
    private final Listener listener;

    private boolean started;
    private boolean cancelled;
    private boolean finished;

    public RichMediaUploader(int currentAccount, String path, Listener listener) {
        this(currentAccount, path, false, 0, 0, 0, listener);
    }

    public RichMediaUploader(int currentAccount, String path, boolean isVideo, int width, int height, int durationSec, Listener listener) {
        this.currentAccount = currentAccount;
        this.path = path;
        this.isVideo = isVideo;
        this.isAudio = false;
        this.isDocument = false;
        this.videoWidth = width;
        this.videoHeight = height;
        this.videoDurationSec = durationSec;
        this.audioDocument = null;
        this.listener = listener;
    }

    private RichMediaUploader(int currentAccount, String path, TLRPC.Document document, boolean isDocument, Listener listener) {
        this.currentAccount = currentAccount;
        this.path = path;
        this.isVideo = false;
        this.isAudio = !isDocument;
        this.isDocument = isDocument;
        this.videoWidth = 0;
        this.videoHeight = 0;
        this.videoDurationSec = 0;
        this.audioDocument = document;
        this.listener = listener;
    }

    public static RichMediaUploader forAudio(int currentAccount, String path, TLRPC.Document audioDocument, Listener listener) {
        return new RichMediaUploader(currentAccount, path, audioDocument, false, listener);
    }

    public static RichMediaUploader forDocument(int currentAccount, String path, TLRPC.Document document, Listener listener) {
        return new RichMediaUploader(currentAccount, path, document, true, listener);
    }

    public String getPath() {
        return path;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void start() {
        if (started || cancelled || finished) return;
        started = true;
        if (path == null || path.isEmpty()) {
            fail();
            return;
        }
        if (isVideo) {
            if (listener != null && videoWidth > 0 && videoHeight > 0) {
                listener.onWidthHeightResolved(videoWidth, videoHeight);
            }
        } else if (!isAudio && !isDocument) {
            resolvePhotoDimensions();
        }
        final int width = isVideo ? videoWidth : photoWidth;
        final int height = isVideo ? videoHeight : photoHeight;
        RichMediaUploadBridge.upload(
            new RichMediaUploadBridge.Request(currentAccount, path, isVideo, isAudio, isDocument, width, height, videoDurationSec),
            new RichMediaUploadBridge.Callback() {
                @Override
                public void onProgress(float progress) {
                    if (cancelled || finished) return;
                    if (listener != null) {
                        listener.onProgress(progress);
                    }
                }

                @Override
                public void onUploaded(String url, String filename, int w, int h, int durationSec) {
                    if (cancelled || finished) return;
                    finished = true;
                    final long id = RichMediaUploadBridge.registerUrl(url);
                    if (id == 0) {
                        fail();
                        return;
                    }
                    if (listener != null && w > 0 && h > 0) {
                        listener.onWidthHeightResolved(w, h);
                    }
                    if (isVideo) {
                        if (listener != null) listener.onVideoUploaded(fabricateDocument(id, filename, url, w, h, durationSec, true));
                    } else if (isAudio) {
                        if (listener != null) listener.onAudioUploaded(fabricateDocument(id, filename, url, w, h, durationSec, false));
                    } else if (isDocument) {
                        if (listener != null) listener.onDocumentUploaded(fabricateDocument(id, filename, url, w, h, durationSec, false));
                    } else {
                        if (listener != null) listener.onPhotoUploaded(fabricatePhoto(id, w, h));
                    }
                }

                @Override
                public void onError() {
                    fail();
                }
            }
        );
    }

    public void cancel() {
        if (cancelled || finished) return;
        cancelled = true;
        RichMediaUploadBridge.cancel(path);
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    private void fail() {
        if (finished || cancelled) return;
        finished = true;
        if (listener != null) {
            listener.onError();
        }
    }

    private int photoWidth;
    private int photoHeight;

    private void resolvePhotoDimensions() {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            photoWidth = opts.outWidth;
            photoHeight = opts.outHeight;
            if (opts.outWidth > 0 && opts.outHeight > 0 && listener != null) {
                listener.onWidthHeightResolved(opts.outWidth, opts.outHeight);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static TLRPC.Photo fabricatePhoto(long id, int width, int height) {
        final TLRPC.TL_photo photo = new TLRPC.TL_photo();
        photo.id = id;
        photo.dc_id = 0;
        photo.date = (int) (System.currentTimeMillis() / 1000);
        photo.sizes = new java.util.ArrayList<>();
        final TLRPC.TL_photoSize size = new TLRPC.TL_photoSize();
        size.type = "x";
        size.w = width;
        size.h = height;
        size.size = width * height;
        size.location = new TLRPC.TL_fileLocationToBeDeprecated();
        photo.sizes.add(size);
        return photo;
    }

    private static TLRPC.Document fabricateDocument(long id, String filename, String url, int width, int height, int durationSec, boolean video) {
        final TLRPC.TL_document document = new TLRPC.TL_document();
        document.id = id;
        document.dc_id = 0;
        document.date = (int) (System.currentTimeMillis() / 1000);
        final String name = filename != null && !filename.isEmpty() ? filename : new File(url == null ? "" : url).getName();
        document.mime_type = guessMimeType(name, video);
        final TLRPC.TL_documentAttributeFilename attribute = new TLRPC.TL_documentAttributeFilename();
        attribute.file_name = name;
        document.attributes = new java.util.ArrayList<>();
        document.attributes.add(attribute);
        if (video) {
            final TLRPC.TL_documentAttributeVideo videoAttribute = new TLRPC.TL_documentAttributeVideo();
            videoAttribute.w = width;
            videoAttribute.h = height;
            videoAttribute.duration = durationSec;
            document.attributes.add(videoAttribute);
        } else {
            final TLRPC.TL_documentAttributeAudio audioAttribute = new TLRPC.TL_documentAttributeAudio();
            audioAttribute.duration = durationSec;
            document.attributes.add(audioAttribute);
        }
        if (width > 0 && height > 0) {
            document.thumbs = new java.util.ArrayList<>();
            final TLRPC.TL_photoSize thumb = new TLRPC.TL_photoSize();
            thumb.type = "x";
            thumb.w = width;
            thumb.h = height;
            thumb.location = new TLRPC.TL_fileLocationToBeDeprecated();
            document.thumbs.add(thumb);
        }
        return document;
    }

    private static String guessMimeType(String name, boolean video) {
        final String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a") || lower.endsWith(".aac")) return "audio/mp4";
        if (lower.endsWith(".ogg") || lower.endsWith(".opus")) return "audio/ogg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return video ? "video/mp4" : "image/jpeg";
    }
}

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

/**
 * NativeDo: the pin's uploader pushes a file to Telegram - {@code FileLoader.uploadFile}, then it
 * waits on {@code NotificationCenter.fileUploaded} and builds a {@code TLRPC.Photo}/{@code Document}
 * from the message media the server answers with. This client uploads to Discourse, so the pin's
 * two halves are replaced: the transport lives behind {@link RichMediaUploadBridge} (installed by
 * {@code :app}, {@code POST /uploads.json}), and the objects this class hands to its listener are
 * fabricated around the uploaded URL by {@link RichMediaUploadBridge} - {@code id} is the bridge's
 * id for that URL (so the send path can turn a media block's id back into a URL) and
 * {@code sizes}/{@code thumbs} carry the dimensions the editor's cells read. The public surface is
 * the pin's, so the editor's call sites stay upstream.
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
                        if (listener != null) listener.onVideoUploaded(RichMediaUploadBridge.fabricateDocument(id, url, filename, true, false, w, h, durationSec));
                    } else if (isAudio) {
                        if (listener != null) listener.onAudioUploaded(RichMediaUploadBridge.fabricateDocument(id, url, filename, false, true, w, h, durationSec));
                    } else if (isDocument) {
                        if (listener != null) listener.onDocumentUploaded(RichMediaUploadBridge.fabricateDocument(id, url, filename, false, false, w, h, durationSec));
                    } else {
                        if (listener != null) listener.onPhotoUploaded(RichMediaUploadBridge.fabricatePhoto(id, w, h));
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
}

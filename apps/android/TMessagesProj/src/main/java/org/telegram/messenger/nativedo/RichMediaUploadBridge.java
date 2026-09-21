/*
 * NativeDo bridge for the rich editor's media uploads. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger.nativedo;

import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NativeDo: the pin's editor uploads media to Telegram ({@code messages.uploadMedia} through
 * {@code FileLoader}, see the pin's {@code iv/RichMediaUploader}); this client uploads to Discourse
 * ({@code POST /uploads.json}). This bridge is the seam between the two: the editor's uploader asks
 * it for an upload, {@code :app} installs an {@link Uploader} that talks to the site, and the result
 * comes back as a URL.
 *
 * <p>Because the editor's model is Telegram's (a media block stores a {@code Photo}/{@code Document}
 * id, not a URL), the bridge also owns the id ↔ URL mapping: the uploader mints an id for the
 * uploaded URL with {@link #registerUrl(String)} and hands that id out inside the fabricated
 * {@code TLRPC} object, so the send path (our blocks → Discourse markdown bridge) can turn the id
 * back into the URL with {@link #urlForId(long)}.</p>
 *
 * <p>The same fabrication runs the other way for an import: a post loaded from the site arrives as
 * markdown whose media blocks carry only a URL, and the editor's resolve path only holds media whose
 * {@code TLRPC} object is ready - so the caller registers the URL and builds the object with
 * {@link #fabricatePhoto} / {@link #fabricateDocument}.</p>
 */
public final class RichMediaUploadBridge {

    private RichMediaUploadBridge() {}

    public static final class Request {
        public final int currentAccount;
        public final String path;
        public final boolean video;
        public final boolean audio;
        public final boolean document;
        public final int width;
        public final int height;
        public final int durationSec;

        public Request(int currentAccount, String path, boolean video, boolean audio, boolean document, int width, int height, int durationSec) {
            this.currentAccount = currentAccount;
            this.path = path;
            this.video = video;
            this.audio = audio;
            this.document = document;
            this.width = width;
            this.height = height;
            this.durationSec = durationSec;
        }
    }

    public interface Callback {
        void onProgress(float progress);

        /** The site's URL for the uploaded file, plus whatever dimensions it reports. */
        void onUploaded(String url, String filename, int width, int height, int durationSec);

        void onError();
    }

    /** Installed by {@code :app}; the editor only sees the bridge. */
    public interface Uploader {
        void upload(Request request, Callback callback);

        void cancel(String path);
    }

    private static volatile Uploader uploader;

    /** Test/install hook: {@code null} removes the uploader (uploads then report an error). */
    public static void setUploader(Uploader value) {
        uploader = value;
    }

    public static boolean hasUploader() {
        return uploader != null;
    }

    public static void upload(Request request, Callback callback) {
        final Uploader current = uploader;
        if (current == null) {
            if (callback != null) {
                callback.onError();
            }
            return;
        }
        current.upload(request, callback);
    }

    public static void cancel(String path) {
        final Uploader current = uploader;
        if (current != null) {
            current.cancel(path);
        }
    }

    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final ConcurrentHashMap<Long, String> URL_BY_ID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> ID_BY_URL = new ConcurrentHashMap<>();

    /** An id for this URL, stable for as long as the URL is registered. */
    public static long registerUrl(String url) {
        if (url == null || url.isEmpty()) {
            return 0;
        }
        final Long existing = ID_BY_URL.get(url);
        if (existing != null) {
            return existing;
        }
        final long id = NEXT_ID.getAndIncrement();
        final Long raced = ID_BY_URL.putIfAbsent(url, id);
        if (raced != null) {
            return raced;
        }
        URL_BY_ID.put(id, url);
        return id;
    }

    public static String urlForId(long id) {
        return URL_BY_ID.get(id);
    }

    public static void releaseId(long id) {
        final String url = URL_BY_ID.remove(id);
        if (url != null) {
            ID_BY_URL.remove(url, id);
        }
    }

    // ---------------------------------------------------------------- import

    /**
     * A {@code TLRPC.Photo} for a URL the editor already has: the object the uploader hands its
     * listener after an upload, and what an import of an already-hosted image needs. {@code id} is
     * the id the URL is registered under (so {@link #urlForId(long)} returns it and the send path
     * can write the markdown back), and {@code sizes} carries whatever dimensions are known (0 when
     * unknown - the editor's media cell falls back to its own height).
     */
    public static TLRPC.Photo fabricatePhoto(long id, int width, int height) {
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

    /**
     * A {@code TLRPC.Document} for a URL the editor already has. {@code video}/{@code audio} pick
     * the media attribute (a video, an audio track); neither makes it a plain file document, which
     * keeps only its name attribute. {@code id} is the id the URL is registered under, and
     * {@code filename} - or the URL's own last segment - is the display name.
     */
    public static TLRPC.Document fabricateDocument(long id, String url, String filename, boolean video, boolean audio, int width, int height, int durationSec) {
        final TLRPC.TL_document document = new TLRPC.TL_document();
        document.id = id;
        document.dc_id = 0;
        document.date = (int) (System.currentTimeMillis() / 1000);
        final String name = filename != null && !filename.isEmpty() ? filename
            : (url == null || url.isEmpty() ? "file" : new File(url).getName());
        document.mime_type = guessMimeType(name, video, audio);
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
        } else if (audio) {
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

    private static String guessMimeType(String name, boolean video, boolean audio) {
        final String lower = name == null ? "" : name.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a") || lower.endsWith(".aac")) return "audio/mp4";
        if (lower.endsWith(".ogg") || lower.endsWith(".opus")) return "audio/ogg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (video) return "video/mp4";
        if (audio) return "audio/mpeg";
        return "application/octet-stream";
    }
}

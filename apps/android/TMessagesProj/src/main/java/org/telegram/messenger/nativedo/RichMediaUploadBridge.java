/*
 * NativeDo bridge for the rich editor's media uploads. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger.nativedo;

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
}

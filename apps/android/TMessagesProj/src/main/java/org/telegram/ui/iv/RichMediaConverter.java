/*
 * Host stub for the pin's org/telegram/ui/iv/RichMediaConverter.java. Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.iv;

import org.telegram.messenger.MediaController;

/**
 * NativeDo: the pin's converter turns a picked item into what Telegram's uploader wants - it
 * re-encodes cropped/animated media to mp4 ({@code MediaController.cancelVideoConvert},
 * {@code VideoEditedInfo} fields, {@code NotificationCenter.filePreparingStarted}) before
 * {@code RichMediaUploader} pushes it. This client sends the picked file to Discourse as it is (the
 * site does its own processing), so the conversion step has nothing to do: the animated-entity check
 * answers false - our picker does not carry Telegram sticker entities - and {@code start()} reports
 * the entry's own path and dimensions straight away, which is the shape the editor's call site
 * expects (it then starts the upload with that path).
 */
public class RichMediaConverter {

    public interface Listener {
        default void onProgress(float progress) {}
        /** mp4Path is the finished converted file; w/h/durationSec describe the produced video. */
        void onDone(String mp4Path, int width, int height, int durationSec);
        void onError();
    }

    private final int currentAccount;
    private final MediaController.PhotoEntry entry;
    private final Listener listener;

    private boolean started;
    private boolean cancelled;
    private boolean finished;

    public RichMediaConverter(int currentAccount, MediaController.PhotoEntry entry, Listener listener) {
        this.currentAccount = currentAccount;
        this.entry = entry;
        this.listener = listener;
    }

    public static boolean hasAnimatedMediaEntities(MediaController.PhotoEntry entry) {
        // NativeDo: nothing to convert - see the class comment.
        return false;
    }

    public void start() {
        if (started || cancelled || finished) return;
        started = true;
        final String path = entry == null ? null : entry.path;
        if (path == null || path.isEmpty()) {
            if (listener != null) listener.onError();
            finished = true;
            return;
        }
        finished = true;
        if (listener != null) {
            listener.onDone(path, entry.width, entry.height, entry.duration);
        }
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}

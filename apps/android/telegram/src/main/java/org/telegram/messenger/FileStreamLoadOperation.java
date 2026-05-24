/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import java.util.HashMap;

/**
 * NativeDo stub of org.telegram.messenger.FileStreamLoadOperation.
 * Upstream streams MTProto file content to ExoPlayer. NativeDo doesn't stream
 * over MTProto; only the static map FileLoader references is preserved.
 */
public class FileStreamLoadOperation implements FileLoadOperationStream {

    public static final HashMap<Long, FileStreamLoadOperation> allStreams = new HashMap<>();

    public FileStreamLoadOperation() {
    }

    @Override
    public void newDataAvailable() {
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import java.util.HashMap;

import android.net.Uri;
import org.telegram.tgnet.TLRPC;

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

    public static int getStreamPrioriy(TLRPC.Document document) {
        return 0;
    }

    public static void setPriorityForDocument(TLRPC.Document document, int priority) {
    }

    public static Uri prepareUri(int currentAccount, TLRPC.Document document, TLRPC.WebPage webPage) {
        return Uri.EMPTY;
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import java.io.File;

/**
 * NativeDo stub of org.telegram.messenger.FilePathDatabase (572 lines upstream).
 *
 * <p>Upstream stores file→on-disk-path mappings in SQLite. NativeDo doesn't
 * persist these — every lookup returns null/false. Exposes the {@code FileMeta}
 * inner type FileLoader references in its public API.
 */
public class FilePathDatabase {

    public static final int FLAG_LOCALLY_CREATED = 1;

    public static class FileMeta {
        public long dialogId;
        public long messageId;
        public int messageType;
        public long messageSize;
        public String localPath;
        public String file_name;
        public String file_name_fixed;
        public boolean isDownloadingFile;
    }

    public static class PathData {
        public long id;
        public int dc;
        public int type;

        public PathData(long id, int dc, int type) {
            this.id = id;
            this.dc = dc;
            this.type = type;
        }
    }

    public FilePathDatabase(int account) {
    }

    public String getPath(long documentId, int dc, int type, boolean useQueue) {
        return null;
    }

    public void putPath(long id, int dc, int type, int flags, String path) {
    }

    public void putPath(long id, int dc, int type, String path) {
    }

    public void clear() {
    }

    public boolean hasAnotherRefOnFile(String path) {
        return false;
    }

    public FileMeta getFileDialogId(File file, FileMeta fileMeta) {
        return fileMeta;
    }

    public void saveFileDialogId(File file, FileMeta fileMeta) {
    }

    public boolean isLocallyCreated(String path) {
        return false;
    }

    public void checkMediaExistance(java.util.ArrayList<MessageObject> messageObjects) {
    }
}

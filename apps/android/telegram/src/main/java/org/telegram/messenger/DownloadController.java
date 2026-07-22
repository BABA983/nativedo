/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.DownloadController.
 *
 * <p>Upstream throttles autodownloads based on user prefs and network type.
 * NativeDo loads images on demand only; the controller is a no-op singleton.
 */
public class DownloadController {
    public interface FileDownloadProgressListener {
        default int getObserverTag() { return 0; }
        default void onFailedDownload(String fileName, boolean canceled) {}
        default void onSuccessDownload(String fileName) {}
        default void onProgressDownload(String fileName, long downloadedSize, long totalSize) {}
        default void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {}
    }

    public static final int AUTODOWNLOAD_TYPE_PHOTO = 1;
    public static final int AUTODOWNLOAD_TYPE_VIDEO = 2;
    public static final int AUTODOWNLOAD_TYPE_DOCUMENT = 4;
    public static final int AUTODOWNLOAD_TYPE_AUDIO = 8;

    private static final DownloadController[] Instance = new DownloadController[UserConfig.MAX_ACCOUNT_COUNT];

    public final int currentAccount;

    public java.util.ArrayList<MessageObject> recentDownloadingFiles = new java.util.ArrayList<>();
    public java.util.ArrayList<MessageObject> downloadingFiles = new java.util.ArrayList<>();

    private DownloadController(int account) {
        this.currentAccount = account;
    }

    public static DownloadController getInstance(int account) {
        DownloadController local = Instance[account];
        if (local == null) {
            synchronized (DownloadController.class) {
                local = Instance[account];
                if (local == null) {
                    Instance[account] = local = new DownloadController(account);
                }
            }
        }
        return local;
    }

    public boolean canDownloadMedia(int type, long size) {
        return true;
    }

    public boolean canDownloadMedia(TLRPC.Message message) {
        return true;
    }

    public boolean canDownloadMedia(int type, TLObject object) {
        return true;
    }

    public int getMaxVideoBitrate() {
        return 0;
    }

    public void startDownloadFile(TLRPC.Document document, MessageObject parent) {
    }

    public void onDownloadComplete(MessageObject messageObject) {
    }

    public void onDownloadFail(MessageObject messageObject, int reason) {
    }

    public boolean canDownloadNextTrack() {
        return false;
    }

    public void clearRecentDownloadedFiles() {
        recentDownloadingFiles.clear();
    }

    public java.util.ArrayList<MessageObject> getRecentDownloadingFiles() {
        return recentDownloadingFiles;
    }

    public static int generateObserverTag() { return 0; }
    public int getCurrentDownloadMask() { return 0; }
    public void addLoadingFileObserver(String fileName, FileDownloadProgressListener listener) {
        addLoadingFileObserver(fileName, listener, listener);
    }
    public void addLoadingFileObserver(String fileName, Object parentObject, FileDownloadProgressListener listener) {}
    public void removeLoadingFileObserver(FileDownloadProgressListener listener) {}
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;

/**
 * NativeDo stub of org.telegram.messenger.FileLoadOperation (2639 lines upstream).
 *
 * <p>Upstream does chunked MTProto/CDN file downloads, encryption, range
 * resumption, and priority-queue dispatch. HTTP image loading in NativeDo
 * bypasses this entirely (ImageLoader uses an HttpURLConnection task). This
 * stub exposes the API surface FileLoader/ImageReceiver/ImageLoader reference
 * so the receiver pipeline compiles.
 */
public class FileLoadOperation {

    public interface FileLoadOperationDelegate {
        void didPreFinishLoading(FileLoadOperation operation, java.io.File finalFile);
        void didFinishLoadingFile(FileLoadOperation operation, java.io.File finalFile);
        void didFailedLoadingFile(FileLoadOperation operation, int reason);
        void didChangedLoadProgress(FileLoadOperation operation, long uploadedSize, long totalSize);
        void saveFilePath(FilePathDatabase.PathData pathSaveData, java.io.File cacheFileFinal);
        boolean hasAnotherRefOnFile(String path);
        default boolean isLocallyCreatedFile(String path) { return false; }
    }

    public int state;
    public int priority;
    public int currentAccount;
    public boolean preloadFinished;
    public boolean preFinished;
    public boolean caughtPremiumFloodWait;
    public boolean isStream;
    public boolean isStory;
    public boolean cacheFileFinal;
    public boolean cacheFileType;
    public java.util.ArrayList<String> uiRequestTokens = new java.util.ArrayList<>();
    public TLObject location;
    public TLRPC.Document document;
    public WebFile webFile;
    public ImageLocation imageLocation;
    public Object parentObject;
    public String fileName;
    public long totalBytesCount;
    public long downloadedBytes;
    public int cacheType;
    public long requestedBytesCount;
    public long lastProgressUpdateTime;
    public boolean isPreloadVideoOperation;
    public FilePathDatabase.PathData pathSaveData;
    private FileLoaderPriorityQueue queue;
    private FileLoadOperationDelegate delegate;

    public FileLoadOperation() {
    }

    public FileLoadOperation(ImageLocation imageLocation, Object parent, String extension, long size) {
    }

    public FileLoadOperation(SecureDocument secureDocument) {
    }

    public FileLoadOperation(TLRPC.Document document, Object parentObject) {
        this.document = document;
        this.parentObject = parentObject;
    }

    public FileLoadOperation(int currentAccount, WebFile webDocument) {
        this.currentAccount = currentAccount;
        this.webFile = webDocument;
    }

    public boolean start() {
        return false;
    }

    public boolean start(FileLoadOperationStream stream, long streamOffset, boolean steam) {
        return false;
    }

    public void cancel() {
    }

    public void cancel(boolean deleteFiles) {
    }

    public void pause() {
    }

    public boolean wasStarted() {
        return false;
    }

    public boolean isLoading() {
        return false;
    }

    public boolean isPaused() {
        return false;
    }

    public boolean isPreloadVideoOperation() {
        return isPreloadVideoOperation;
    }

    public boolean isPreloadFinished() {
        return preloadFinished;
    }

    public void setIsPreloadVideoOperation(boolean preload) {
        this.isPreloadVideoOperation = preload;
    }

    public int getCurrentType() {
        return 0;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int value) {
        this.priority = value;
    }

    public void setQueue(FileLoaderPriorityQueue queue) {
        this.queue = queue;
    }

    public FileLoaderPriorityQueue getQueue() {
        return queue;
    }

    public FileLoadOperationDelegate getDelegate() {
        return delegate;
    }

    public void setDelegate(FileLoadOperationDelegate delegate) {
        this.delegate = delegate;
    }

    public void setForceRequest(boolean forceRequest) {
    }

    public boolean isForceRequest() {
        return false;
    }

    public void setEncryptFile(boolean encryptFile) {
    }

    public void setStream(FileLoadOperationStream stream, boolean steam, long streamOffset) {
    }

    public void setEncryptedFile(boolean encrypted) {
    }

    public boolean isCancelled() {
        return false;
    }

    public void setPaths(int currentAccount, String fileName, FileLoaderPriorityQueue queue, File store, File temp, String ext) {
    }

    public void updateProgress() {
    }

    public File getCacheFileFinal() {
        return null;
    }

    public File getCurrentFile() {
        return null;
    }

    public String getFileName() {
        return fileName;
    }

    public long getDownloadedLengthFromOffset(long offset, long length) {
        return 0;
    }

    public boolean hasAnotherRefOnFile() {
        return false;
    }

    public int getPositionInQueue() {
        return queue == null ? -1 : queue.getPosition(this);
    }

    public void onNetworkChanged(boolean slow) {
    }

    public int getDatacenterId() {
        return 0;
    }

    public long getDownloadedLengthFromOffset(long offset) {
        return 0;
    }

    public float getDownloadedLengthFromOffset(float position) {
        return 0f;
    }

    public boolean checkNewDataAvailable(long newAvailableSize, long finalSize, Float progress) {
        return false;
    }

    public boolean checkPrefixPreloadFinished() {
        return false;
    }
}

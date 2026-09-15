/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.messenger.FileUploadOperation.
 * Upstream coordinates one chunked MTProto upload. NativeDo doesn't upload via
 * MTProto, so only the type signature is exposed.
 */
public class FileUploadOperation {

    public interface FileUploadOperationDelegate {
        void didFinishUploadingFile(FileUploadOperation operation, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile, byte[] key, byte[] iv);
        void didFailedUploadingFile(FileUploadOperation operation);
        void didChangedUploadProgress(FileUploadOperation operation, long uploadedSize, long totalSize);
        default void fileDidUploaded(String location, TLRPC.InputFile inputFile, TLRPC.InputEncryptedFile inputEncryptedFile, byte[] key, byte[] iv, long totalSize) {}
        default void fileDidFailedUpload(String location, boolean isEncrypted) {}
    }

    public int state;
    public int currentAccount;
    public boolean caughtPremiumFloodWait;
    public java.util.ArrayList<Integer> uiRequestTokens = new java.util.ArrayList<>();
    public TLObject location;
    public String uploadingFilePath;
    public long totalFileSize;
    public long uploadedBytes;
    public long lastProgressUpdateTime;
    private FileUploadOperationDelegate delegate;

    public FileUploadOperation() {
    }

    public FileUploadOperation(int currentAccount, String location, boolean encrypted, long estimated, int type) {
        this.currentAccount = currentAccount;
    }

    public void start() {
    }

    public void cancel() {
    }

    public void setForceSmallFile() {
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    public FileUploadOperationDelegate getDelegate() {
        return delegate;
    }

    public void setDelegate(FileUploadOperationDelegate delegate) {
        this.delegate = delegate;
    }

    public void checkNewDataAvailable(long newAvailableSize, long finalSize, Float progress) {
    }

    public void onNetworkChanged(boolean slow) {
    }
}

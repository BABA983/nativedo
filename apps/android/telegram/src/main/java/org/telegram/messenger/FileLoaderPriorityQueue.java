/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import java.util.ArrayList;

/**
 * NativeDo stub of org.telegram.messenger.FileLoaderPriorityQueue.
 * Upstream orders concurrent file downloads by priority. NativeDo doesn't run
 * that scheduler — the receiver-side image pipeline goes straight through
 * FileLoadOperation. Only the API surface FileLoader references is preserved.
 */
public class FileLoaderPriorityQueue {

    public static final int TYPE_SMALL = 0;
    public static final int TYPE_LARGE = 1;

    public static final int PRIORITY_VALUE_MAX = 1_000_000;
    public static final int PRIORITY_VALUE_NORMAL = 65_536;
    public static final int PRIORITY_VALUE_LOW = 0;

    private final int type;
    private final int currentAccount;
    private final String name;
    private final ArrayList<FileLoadOperation> operations = new ArrayList<>();

    public FileLoaderPriorityQueue(int currentAccount, String name, int type) {
        this.currentAccount = currentAccount;
        this.name = name;
        this.type = type;
    }

    public FileLoaderPriorityQueue(int currentAccount, String name, int type, DispatchQueue queue) {
        this(currentAccount, name, type);
    }

    public int getType() {
        return type;
    }

    public void add(FileLoadOperation operation) {
    }

    public void cancel(FileLoadOperation operation) {
    }

    public boolean remove(FileLoadOperation operation) {
        return false;
    }

    public boolean checkLoadingOperations() {
        return false;
    }

    public boolean checkLoadingOperations(boolean force) {
        return false;
    }

    public int getPosition(FileLoadOperation operation) {
        return -1;
    }

    public int getCount() {
        return operations.size();
    }

    public int getMaxActiveOperationsCount() {
        return 0;
    }
}

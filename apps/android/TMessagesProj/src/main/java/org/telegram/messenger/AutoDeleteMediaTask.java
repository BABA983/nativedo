/*
 * This file is part of NativeDo.
 *
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutoDeleteMediaTask {

    public static final Set<String> usingFilePaths = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final int DEFAULT_KEEP_MEDIA_SECONDS = 60 * 60 * 24 * 30;
    private static final long DEFAULT_CACHE_LIMIT = 1024L * 1024L * 1024L;

    public static void run() {
        int time = (int) (System.currentTimeMillis() / 1000);
        if (Math.abs(time - SharedConfig.lastKeepMediaCheckTime) < 24 * 60 * 60) {
            return;
        }
        SharedConfig.lastKeepMediaCheckTime = time;
        File cacheDir = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);
        if (cacheDir == null) {
            return;
        }

        Utilities.cacheClearQueue.postRunnable(() -> {
            try {
                long keepMediaTime = time - DEFAULT_KEEP_MEDIA_SECONDS;
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file == null || file.isDirectory() || usingFilePaths.contains(file.getAbsolutePath())) {
                            continue;
                        }
                        String name = file.getName();
                        if (".nomedia".equals(name) || name.endsWith("_temp.jpg") || name.endsWith("_temp.png") || name.endsWith("_temp.webp")) {
                            continue;
                        }
                        long lastUsageTime = Utilities.getLastUsageFileTime(file.getAbsolutePath());
                        if (lastUsageTime > 316000000 && lastUsageTime < keepMediaTime && !usingFilePaths.contains(file.getPath())) {
                            try {
                                file.delete();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }
                enforceCacheSize(cacheDir);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            MessagesController.getGlobalMainSettings().edit()
                    .putInt("lastKeepMediaCheckTime", SharedConfig.lastKeepMediaCheckTime)
                    .apply();
        });
    }

    private static void enforceCacheSize(File cacheDir) {
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }
        ArrayList<FileInfoInternal> allFiles = new ArrayList<>();
        long totalSize = 0;
        for (File file : files) {
            if (file == null || file.isDirectory() || ".nomedia".equals(file.getName()) || usingFilePaths.contains(file.getAbsolutePath())) {
                continue;
            }
            FileInfoInternal info = new FileInfoInternal(file);
            allFiles.add(info);
            totalSize += info.size;
        }
        if (totalSize <= DEFAULT_CACHE_LIMIT) {
            return;
        }
        Collections.sort(allFiles, (o1, o2) -> {
            if (o2.lastUsageDate > o1.lastUsageDate) {
                return -1;
            } else if (o2.lastUsageDate < o1.lastUsageDate) {
                return 1;
            }
            return 0;
        });
        for (FileInfoInternal info : allFiles) {
            if (info.lastUsageDate <= 0) {
                continue;
            }
            totalSize -= info.size;
            try {
                info.file.delete();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (totalSize < DEFAULT_CACHE_LIMIT) {
                break;
            }
        }
    }

    private static class FileInfoInternal {
        final File file;
        final long size;
        final long lastUsageDate;

        FileInfoInternal(File file) {
            this.file = file;
            this.size = file.length();
            this.lastUsageDate = Utilities.getLastUsageFileTime(file.getAbsolutePath());
        }
    }
}

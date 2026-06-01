// SPDX-License-Identifier: GPL-2.0-or-later
//
// NativeDo-authored local bookmark store. Mirrors the on-disk persistence
// pattern of BrowserHistory (a SerializedData .dat file, independent of any
// messaging backend) so that the web browser's "Add bookmark" action persists
// locally instead of going through Telegram Saved-Messages / Discourse.
//
// This is not a vendored Telegram file; do not add a vendor-manifest entry.

package org.telegram.ui.web;

import android.text.TextUtils;
import android.util.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class WebBookmarks {

    private static final LongSparseArray<WebBookmarks> instances = new LongSparseArray<>();

    public static WebBookmarks getInstance(int currentAccount) {
        WebBookmarks instance = instances.get(currentAccount);
        if (instance == null) {
            instance = new WebBookmarks(currentAccount);
            instances.put(currentAccount, instance);
        }
        return instance;
    }

    private final int currentAccount;

    private boolean loading, loaded;
    private final ArrayList<BrowserHistory.Entry> bookmarks = new ArrayList<>();
    private final LongSparseArray<BrowserHistory.Entry> bookmarksById = new LongSparseArray<>();
    private ArrayList<Utilities.Callback<ArrayList<BrowserHistory.Entry>>> callbacks;

    private WebBookmarks(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    private File getBookmarksFile() {
        return new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "webbookmarks_" + currentAccount + ".dat");
    }

    private static long idForUrl(String url) {
        return Utilities.bytesToLong(Utilities.computeSHA256((url == null ? "" : url).getBytes()));
    }

    public void preload() {
        if (loading || loaded) {
            return;
        }
        loading = true;
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<BrowserHistory.Entry> loadedBookmarks = new ArrayList<>();
            try {
                final File file = getBookmarksFile();
                if (file.exists()) {
                    final SerializedData stream = new SerializedData(file);
                    final long count = stream.readInt64(true);
                    for (long i = 0; i < count; ++i) {
                        final BrowserHistory.Entry entry = new BrowserHistory.Entry();
                        entry.readParams(stream, true);
                        loadedBookmarks.add(entry);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            AndroidUtilities.runOnUIThread(() -> {
                for (int i = 0; i < loadedBookmarks.size(); ++i) {
                    final BrowserHistory.Entry entry = loadedBookmarks.get(i);
                    if (bookmarksById.indexOfKey(entry.id) < 0) {
                        bookmarks.add(entry);
                        bookmarksById.put(entry.id, entry);
                    }
                }
                loaded = true;
                loading = false;
                if (callbacks != null) {
                    for (Utilities.Callback<ArrayList<BrowserHistory.Entry>> callback : callbacks) {
                        callback.run(bookmarks);
                    }
                    callbacks = null;
                }
            });
        });
    }

    // Returns the in-memory list. Newest first. Triggers a background load on
    // first access; callers observing NotificationCenter.bookmarkAdded will be
    // refreshed once loading completes.
    public ArrayList<BrowserHistory.Entry> getAll() {
        return getAll(null);
    }

    public ArrayList<BrowserHistory.Entry> getAll(Utilities.Callback<ArrayList<BrowserHistory.Entry>> loadedCallback) {
        if (loaded) {
            // Already loaded: deliver on the next UI loop (never inline) so callers
            // that invoke getAll() from a fragment's onFragmentCreate() — before its
            // listView exists — don't run their whenUpdated() against a null view.
            if (loadedCallback != null) {
                AndroidUtilities.runOnUIThread(() -> loadedCallback.run(bookmarks));
            }
            return bookmarks;
        }
        if (loadedCallback != null) {
            if (callbacks == null) {
                callbacks = new ArrayList<>();
            }
            callbacks.add(loadedCallback);
        }
        preload();
        return loadedCallback != null ? null : bookmarks;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public BrowserHistory.Entry add(String url, String title) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        preload();
        final long id = idForUrl(url);
        BrowserHistory.Entry entry = bookmarksById.get(id);
        if (entry != null) {
            bookmarks.remove(entry);
        } else {
            entry = new BrowserHistory.Entry();
            entry.id = id;
        }
        entry.url = url;
        entry.time = System.currentTimeMillis();
        final String domain = AndroidUtilities.getHostAuthority(url, true);
        final WebMetadataCache.WebMetadata cachedMeta = TextUtils.isEmpty(domain) ? null : WebMetadataCache.getInstance().get(domain);
        if (entry.meta == null) {
            entry.meta = new WebMetadataCache.WebMetadata();
        }
        entry.meta.domain = domain;
        if (cachedMeta != null) {
            if (TextUtils.isEmpty(entry.meta.title)) {
                entry.meta.title = cachedMeta.title;
            }
            if (TextUtils.isEmpty(entry.meta.sitename)) {
                entry.meta.sitename = cachedMeta.sitename;
            }
            entry.meta.actionBarColor = cachedMeta.actionBarColor;
            entry.meta.backgroundColor = cachedMeta.backgroundColor;
            if (cachedMeta.favicon != null) {
                entry.meta.favicon = cachedMeta.favicon;
                entry.meta.faviconBytes = cachedMeta.faviconBytes;
            }
        }
        if (!TextUtils.isEmpty(title)) {
            entry.meta.title = title;
        }
        bookmarks.add(0, entry);
        bookmarksById.put(id, entry);
        scheduleSave();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.bookmarkAdded, entry);
        return entry;
    }

    public void remove(long id) {
        preload();
        final BrowserHistory.Entry entry = bookmarksById.get(id);
        if (entry == null) {
            return;
        }
        bookmarks.remove(entry);
        bookmarksById.remove(id);
        scheduleSave();
    }

    public void remove(ArrayList<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        preload();
        for (int i = 0; i < ids.size(); ++i) {
            final long id = ids.get(i);
            final BrowserHistory.Entry entry = bookmarksById.get(id);
            if (entry != null) {
                bookmarks.remove(entry);
                bookmarksById.remove(id);
            }
        }
        scheduleSave();
    }

    public void clear() {
        bookmarks.clear();
        bookmarksById.clear();
        try {
            final File file = getBookmarksFile();
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void scheduleSave() {
        AndroidUtilities.cancelRunOnUIThread(this::save);
        AndroidUtilities.runOnUIThread(this::save, 1_000);
    }

    private void save() {
        final ArrayList<BrowserHistory.Entry> snapshot = new ArrayList<>(bookmarks);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final File file = getBookmarksFile();
                if (!file.exists()) {
                    file.createNewFile();
                }

                final long count = snapshot.size();
                final SerializedData sizeStream = new SerializedData(true);
                sizeStream.writeInt64(count);
                for (BrowserHistory.Entry e : snapshot) {
                    e.serializeToStream(sizeStream);
                }

                final SerializedData stream = new SerializedData(sizeStream.length());
                stream.writeInt64(count);
                for (BrowserHistory.Entry e : snapshot) {
                    e.serializeToStream(stream);
                }

                try {
                    FileOutputStream os = new FileOutputStream(file);
                    os.write(stream.toByteArray());
                    os.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }
}

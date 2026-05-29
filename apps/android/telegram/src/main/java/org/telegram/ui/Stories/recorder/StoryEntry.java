package org.telegram.ui.Stories.recorder;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;

public class StoryEntry {
    public static File makeCacheFile(int account, String ext) {
        String suffix = ext == null || ext.isEmpty() ? "tmp" : ext;
        return new File(AndroidUtilities.getCacheDir(), "web-" + System.nanoTime() + "." + suffix);
    }
}

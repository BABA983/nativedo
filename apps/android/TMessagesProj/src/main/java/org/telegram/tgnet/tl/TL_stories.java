package org.telegram.tgnet.tl;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * Minimal TL_stories stub. Only referenced by FileLoader/ImageLoader/ImageReceiver.
 */
public class TL_stories {
    public static class TL_mediaAreaChannelPost {
    }

    public static class StoryItem extends TLObject {
        public int id;
        public long dialogId;
        public TLRPC.Photo photo;
        public TLRPC.Document document;
        public TLRPC.MessageMedia media;
        public boolean isPublic;
    }
}

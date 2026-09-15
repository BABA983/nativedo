/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Stories;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * NativeDo stub of org.telegram.ui.Stories.StoryItem (and the wider Stories
 * module). LinuxDo doesn't have Stories; we expose the type signature so
 * FileLoader/ImageLoader's Stories-parent-object branches compile.
 */
public class StoryItem extends TLObject {
    public int id;
    public long dialogId;
    public TLRPC.Photo photo;
    public TLRPC.Document document;
    public TLRPC.MessageMedia media;
    public boolean isPublic;
}

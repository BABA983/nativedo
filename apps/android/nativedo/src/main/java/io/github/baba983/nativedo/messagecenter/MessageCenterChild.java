package io.github.baba983.nativedo.messagecenter;

import android.view.View;

/**
 * A page a {@link MessageCenterFragment} hosts: it hands the center's own title-bar menu the entry
 * point it would have drawn on a bar of its own, and takes the strip's height as the room its
 * content keeps at the top.
 *
 * <p>The center owns the single title bar (a page that stacked its own bar under it would be a
 * second row), so an operation that used to live on a page's own bar - the notifications page's
 * mark-all-read, the chats page's refresh, the private-messages page's box choice - is reached from
 * the center's menu instead. The center passes the anchor its menu is hung from; the page shows the
 * same options it would have shown anchored there.</p>
 */
public interface MessageCenterChild {

    /** Shows this page's own title-bar options, anchored at [anchor]. */
    void showMessageCenterOptions(View anchor);

    /**
     * The strip's height in pixels, the room this page keeps above its first row.
     *
     * <p>The center's pages begin at the action bar's bottom, under the capsule strip the center
     * draws over them; the strip is a fixed surface, so a page's content scrolls behind it. A page
     * starting its content at the top of the pager would have its first row hidden behind the
     * capsule, so it adds this inset to its list's top padding and keeps clipping off that padding
     * ({@code setClipToPadding(false)}): the first row then starts below the capsule, and rows
     * further down the list still pass under it as the reader scrolls.</p>
     *
     * <p>The center hands the inset over when it instantiates the page and again once the strip has
     * been measured; a later call replaces the value rather than adding to it. A page without a
     * list of its own may ignore it.</p>
     */
    default void setMessageCenterTopInset(int inset) {
    }
}

package io.github.baba983.nativedo.messagecenter;

import android.view.View;

/**
 * A page a {@link MessageCenterFragment} hosts: it hands the center's own title-bar menu the entry
 * point it would have drawn on a bar of its own.
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
}

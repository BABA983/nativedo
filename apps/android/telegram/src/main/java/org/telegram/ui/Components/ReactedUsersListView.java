/*
 * NativeDo host-local constant surface for FlickerLoadingView.
 * Upstream ReactedUsersListView is not fully vendored; only ITEM_HEIGHT_DP is required
 * so FlickerLoadingView can stay pin-shaped.
 */
package org.telegram.ui.Components;

/**
 * Constant holder matching upstream ReactedUsersListView.ITEM_HEIGHT_DP (50).
 * Full reacted-users list UI is not ported.
 */
public final class ReactedUsersListView {
    public static final int ITEM_HEIGHT_DP = 50;

    private ReactedUsersListView() {}
}

package org.telegram.ui.ActionBar;

import android.view.View;

/**
 * Abstract bridge for BrowserSheet so :telegram does not depend on :app.
 * The real implementation lives in {@code io.github.baba983.nativedo.ui.browser.BrowserSheet}
 * and is injected at runtime by the host app.
 *
 * V1: only the methods actually called by BottomSheetTabs are declared here.
 */
public abstract class BrowserSheet implements BaseFragment.AttachedSheet {

    /** Called by BottomSheetTabs when re-showing an existing tab. */
    public abstract void reset();

    /** Called by BottomSheetTabs to attach the sheet to a fragment. */
    public abstract void attachInternal(BaseFragment fragment);

    /** Called by BottomSheetTabs to animate the sheet open. */
    public abstract void animateOpen(boolean open, boolean tabs, Runnable onComplete);

    /** Called by BottomSheetTabs when destroying a tab from the tab strip. */
    public abstract void destroyFromTabs();
}

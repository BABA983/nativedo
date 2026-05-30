package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay;

/**
 * Stub for Telegram LaunchActivity. Only checkFreeDiscSpaceStatic is referenced by FileLoader.
 * V1: instance/getBottomSheetTabs added for AdjustPanLayoutHelper compatibility.
 */
public class LaunchActivity extends Activity {
    public static LaunchActivity instance;
    public static Runnable whenResumed;
    private static ActionBarLayout actionBarLayout;
    public DrawerLayoutContainer drawerLayoutContainer = new DrawerLayoutContainer();
    public android.widget.FrameLayout sheetContainer;

    public static void setActionBarLayout(ActionBarLayout layout) {
        actionBarLayout = layout;
    }

    public static Activity getActionBarLayoutActivity() {
        Context context = actionBarLayout == null ? null : actionBarLayout.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @Override
    public boolean isFinishing() {
        Activity activity = getActionBarLayoutActivity();
        return activity == null || activity.isFinishing();
    }

    public BottomSheetTabs getBottomSheetTabs() {
        return actionBarLayout == null ? null : actionBarLayout.getBottomSheetTabs();
    }

    public BottomSheetTabsOverlay getBottomSheetTabsOverlay() {
        return actionBarLayout == null ? null : actionBarLayout.getBottomSheetTabsOverlay();
    }

    public static BaseFragment getSafeLastFragment() {
        return actionBarLayout == null ? null : actionBarLayout.getLastFragment();
    }

    public void checkSystemBarColors(boolean a, boolean b, boolean c) {}

    public boolean presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation) {
        return actionBarLayout != null && actionBarLayout.presentFragment(fragment);
    }

    public static class DrawerLayoutContainer {
        public void setAllowDrawContent(boolean value) {}
    }

    public static boolean checkFreeDiscSpaceStatic(int type) {
        return true;
    }
}

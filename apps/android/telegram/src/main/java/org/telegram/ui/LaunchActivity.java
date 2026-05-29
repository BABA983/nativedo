package org.telegram.ui;

import android.app.Activity;

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
    public DrawerLayoutContainer drawerLayoutContainer = new DrawerLayoutContainer();
    public android.widget.FrameLayout sheetContainer;

    public BottomSheetTabs getBottomSheetTabs() {
        return null;
    }

    public BottomSheetTabsOverlay getBottomSheetTabsOverlay() {
        return null;
    }

    public static BaseFragment getSafeLastFragment() {
        return null;
    }

    public void checkSystemBarColors(boolean a, boolean b, boolean c) {}

    public boolean presentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation) {
        return false;
    }

    public static class DrawerLayoutContainer {
        public void setAllowDrawContent(boolean value) {}
    }

    public static boolean checkFreeDiscSpaceStatic(int type) {
        return true;
    }
}

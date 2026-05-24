package org.telegram.ui;

import org.telegram.ui.ActionBar.BottomSheetTabs;

/**
 * Stub for Telegram LaunchActivity. Only checkFreeDiscSpaceStatic is referenced by FileLoader.
 * V1: instance/getBottomSheetTabs added for AdjustPanLayoutHelper compatibility.
 */
public class LaunchActivity {
    public static LaunchActivity instance;

    public BottomSheetTabs getBottomSheetTabs() {
        return null;
    }

    public static boolean checkFreeDiscSpaceStatic(int type) {
        return true;
    }
}

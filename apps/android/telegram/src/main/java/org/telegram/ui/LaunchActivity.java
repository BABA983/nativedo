package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.activity.ComponentActivity;

import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;

/**
 * NativeDo host facade for Telegram-derived root services.
 */
public class LaunchActivity extends ComponentActivity {
    public static LaunchActivity instance;
    public static Runnable whenResumed;
    private static ActionBarLayout actionBarLayout;
    public DrawerLayoutContainer drawerLayoutContainer;
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
        if (activity == null || activity == this) {
            return super.isFinishing();
        }
        return activity.isFinishing();
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

    public static boolean checkFreeDiscSpaceStatic(int type) {
        return true;
    }
}

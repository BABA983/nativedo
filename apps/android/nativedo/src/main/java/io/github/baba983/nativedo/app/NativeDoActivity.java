package io.github.baba983.nativedo.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

import io.github.baba983.nativedo.topic.TopicListFragment;

/**
 * NativeDo's shell: it hosts upstream's {@link DrawerLayoutContainer} and {@link ActionBarLayout}
 * and pushes our fragments into them.
 *
 * <p>Upstream's own {@code LaunchActivity} cannot be reused. It is Telegram's onboarding flow, not a
 * shell: it calls {@code ApplicationLoader.postInitApplication()} (which builds a
 * {@code MessagesController} and a {@code MediaController} for every account, opening Telegram's
 * database), keys everything off {@code UserConfig.getInstance(...).isClientActivated()}, and
 * returns {@code new LoginActivity()} when no Telegram account exists. None of that applies to a
 * linux.do client, and the hooks that would disable it are the entire Telegram account model.</p>
 */
public class NativeDoActivity extends Activity {

    private ActionBarLayout actionBarLayout;
    private DrawerLayoutContainer drawerLayoutContainer;
    private FrameLayout contentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(org.telegram.messenger.R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);

        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());
        Theme.createCommonResources(this);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);
        Theme.createCommonMessageResources();

        // `main == false`: the main layout is Telegram's tabbed shell, and it dereferences
        // LaunchActivity (which this client does not have) while creating its bottom-sheet tab bar.
        // The two behaviours `main` controls are that bar and its height, neither of which applies here.
        actionBarLayout = new ActionBarLayout(this, false);
        actionBarLayout.setFragmentStack(new ArrayList<>());

        drawerLayoutContainer = new DrawerLayoutContainer(this);
        drawerLayoutContainer.setActionBarLayout(actionBarLayout);
        drawerLayoutContainer.setParentActionBarLayout(actionBarLayout);
        actionBarLayout.setDrawerLayoutContainer(drawerLayoutContainer);

        contentView = new FrameLayout(this);
        contentView.setClipChildren(false);
        contentView.setClipToPadding(false);
        contentView.addView(drawerLayoutContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        drawerLayoutContainer.addView(actionBarLayout.getView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(contentView);

        if (savedInstanceState == null) {
            actionBarLayout.presentFragment(new TopicListFragment(), false);
        }
    }

    /** Fragments push their next screen through the shell rather than holding a reference to it. */
    public void present(BaseFragment fragment) {
        actionBarLayout.presentFragment(fragment, true);
    }

    @Override
    public void onBackPressed() {
        if (actionBarLayout != null && actionBarLayout.getFragmentStack().size() > 1) {
            actionBarLayout.onBackPressed();
            return;
        }
        super.onBackPressed();
    }
}
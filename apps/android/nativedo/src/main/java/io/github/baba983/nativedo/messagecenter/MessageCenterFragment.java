package io.github.baba983.nativedo.messagecenter;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.ViewPagerActivity;

import io.github.baba983.nativedo.app.GlassPanelHost;
import io.github.baba983.nativedo.app.NativeDoMainTabsActivity;
import io.github.baba983.nativedo.chat.PublicChannelsFragment;
import io.github.baba983.nativedo.messages.PrivateMessagesFragment;
import io.github.baba983.nativedo.notifications.NotificationsFragment;

/** One message entry; upstream's fragment pager owns the three existing screens' lifecycle. */
public class MessageCenterFragment extends ViewPagerActivity implements NativeDoMainTabsActivity.TabPage {
    public static final int PAGE_NOTIFICATIONS = 0;
    public static final int PAGE_MESSAGES = 1;
    public static final int PAGE_CHANNELS = 2;
    private static final String[] LABELS = {"通知", "私信", "聊天"};
    private static final int STRIP_HEIGHT_DP = 50;

    private final int initialPage;
    private final boolean hasMainTabs;
    private ScrollSlidingTextTabStrip tabsView;
    private ActionBarMenuItem optionsItem;
    private GlassPanelHost glassHost;
    private FrameLayout stripBand;
    private final NotificationCenter.NotificationCenterDelegate themeObserver =
            (id, account, args) -> AndroidUtilities.runOnUIThread(this::updateColors);

    private MessageCenterFragment(int initialPage, boolean hasMainTabs) {
        this.initialPage = initialPage;
        this.hasMainTabs = hasMainTabs;
    }

    public static MessageCenterFragment forMainTabs() {
        return new MessageCenterFragment(PAGE_NOTIFICATIONS, true);
    }

    public static MessageCenterFragment create(int initialPage) {
        return new MessageCenterFragment(initialPage, false);
    }

    @Override
    protected int getStartPosition() {
        return initialPage;
    }

    @Override
    protected int getFragmentsCount() {
        return LABELS.length;
    }

    @Override
    protected BaseFragment createBaseFragmentAt(int position) {
        Bundle args = new Bundle();
        args.putBoolean("messageCenter", true);
        args.putBoolean("hasMainTabs", hasMainTabs);
        BaseFragment page;
        switch (position) {
            case PAGE_MESSAGES: page = new PrivateMessagesFragment(args); break;
            case PAGE_CHANNELS: page = new PublicChannelsFragment(args); break;
            default: page = new NotificationsFragment(args); break;
        }
        // Upstream's page factory is the one place a page is instantiated: hand the strip's own
        // height over here, before the page's view exists, so its list is built with the room
        // already in place rather than laid out once behind the capsule.
        applyTopInset(page, dp(STRIP_HEIGHT_DP));
        return page;
    }

    @Override
    public ActionBar createActionBar(Context context) {
        return new ActionBar(context, resourceProvider);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(themeObserver, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(themeObserver, NotificationCenter.needSetDayNightTheme);
        return super.onFragmentCreate();
    }

    @Override
    protected FrameLayout createContentView(Context context) {
        return new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (glassHost != null) glassHost.record();
                super.dispatchDraw(canvas);
            }
        };
    }

    @Override
    public View createView(Context context) {
        super.createView(context);
        actionBar.setAddToContainer(false);
        actionBar.setTitle("消息");
        if (!hasMainTabs) actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        optionsItem = actionBar.createMenu().addItem(1, org.telegram.messenger.R.drawable.ic_ab_other);
        optionsItem.setContentDescription("更多");
        optionsItem.setOnClickListener(v -> showCurrentPageOptions());

        tabsView = new ScrollSlidingTextTabStrip(context, resourceProvider);
        tabsView.animationDuration = 320;
        tabsView.setColors(Theme.key_profile_tabSelectedLine, Theme.key_profile_tabSelectedText,
                Theme.key_profile_tabText, Theme.key_profile_tabSelector);
        tabsView.setUseMinimalWidth(true);
        // The closed capsule's inset is part of the strip before it is first measured, which is
        // how upstream builds the profile's capsule (SharedMediaLayout: setPadding, then
        // setOpen(false)). Applied only after a layout, the strip is measured without it: its row
        // of tabs sits at the strip's own edge while the capsule's clip and the tap highlight
        // start 6dp inside it, the selected label reads flush against that edge and off-centre in
        // its highlight, and the wrap-content strip keeps a scroll range as wide as the inset -
        // so choosing 聊天 scrolls the row 12dp left, cutting 通知's label at the capsule's edge
        // and pushing 聊天's highlight past the other. The layout listener below still flips
        // upstream's own open flag; with the inset already in place it changes no geometry.
        tabsView.setPadding(dp(6), dp(7), dp(6), dp(7));
        tabsView.setClipToPadding(false);
        for (int page = 0; page < LABELS.length; page++) tabsView.addTextTab(page, LABELS[page]);
        tabsView.setInitialTabId(viewPager.getCurrentPosition());
        tabsView.finishAddingTabs();
        tabsView.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int page, boolean forward) {
                viewPager.scrollToPosition(page);
            }

            @Override
            public void onPageScrolled(float progress) { }

            @Override
            public void onSamePageSelected() {
                onParentScrollToTop();
            }
        });
        glassHost = new GlassPanelHost(viewPager, contentView);
        BlurredBackgroundDrawable background = glassHost.createPanel(
                tabsView, BlurredBackgroundProviderImpl.topPanel(resourceProvider));
        background.setRadius(dp(18));
        background.setPadding(dp(6.666f));
        tabsView.setBackground(null);
        tabsView.setBlurredBackground(background);
        // Establish the closed capsule after measurement. Before that, upstream would animate
        // its clipping path from an empty rectangle rather than the tabs' measured bounds.
        // The strip already carries the closed inset (above), so this only sets upstream's own
        // open flag: it re-applies the same padding and recomputes the same clip.
        tabsView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ((ScrollSlidingTextTabStrip) v).setOpen(false));

        stripBand = new FrameLayout(context);
        // The band lays the capsule out but is not a surface: the pages begin at the bar's bottom
        // and scroll under it, so what shows around the glass is the page itself, not a window
        // coloured strip that would hide the rows passing behind the capsule.
        stripBand.setBackgroundColor(Color.TRANSPARENT);
        stripBand.addView(tabsView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, STRIP_HEIGHT_DP, Gravity.CENTER));
        contentView.addView(stripBand, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, STRIP_HEIGHT_DP, Gravity.TOP));
        contentView.addView(actionBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        actionBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutHeader());
        layoutHeader();
        updateColors();
        return fragmentView;
    }

    /**
     * The bar on top, the pages under it: the strip is a fixed surface the pages scroll behind,
     * not a band that pushes them down (the profile's capsule, SharedMediaLayout:3751). A pager
     * starting below the strip could never draw a row in the capsule's band, and a page whose
     * list did not know about the strip would start its first row hidden behind the capsule; the
     * page holds the room itself through {@link MessageCenterChild#setMessageCenterTopInset(int)}.
     */
    private void layoutHeader() {
        int barHeight = actionBar.getMeasuredHeight();
        FrameLayout.LayoutParams stripParams = (FrameLayout.LayoutParams) stripBand.getLayoutParams();
        FrameLayout.LayoutParams pageParams = (FrameLayout.LayoutParams) viewPager.getLayoutParams();
        if (stripParams.topMargin != barHeight) {
            stripParams.topMargin = barHeight;
            stripBand.setLayoutParams(stripParams);
        }
        if (pageParams.topMargin != barHeight) {
            pageParams.topMargin = barHeight;
            viewPager.setLayoutParams(pageParams);
        }
        // Pages made before the strip was measured (the starting page) take the measured height;
        // pages made later already have it from createBaseFragmentAt.
        int stripHeight = stripBand.getMeasuredHeight() > 0
                ? stripBand.getMeasuredHeight() : dp(STRIP_HEIGHT_DP);
        for (int index = 0; index < fragmentsArr.size(); index++) {
            applyTopInset(fragmentsArr.valueAt(index).fragment, stripHeight);
        }
    }

    /** Hands [page] its room above the first row, when it keeps one. */
    private void applyTopInset(BaseFragment page, int inset) {
        if (page instanceof MessageCenterChild) {
            ((MessageCenterChild) page).setMessageCenterTopInset(inset);
        }
    }

    @Override
    protected void onViewPagerTabAnimationUpdate(boolean manual) {
        if (tabsView == null) return;
        int current = viewPager.getCurrentPosition();
        int next = viewPager.getNextPosition();
        if (next != current && next >= 0 && next < LABELS.length) {
            float fraction = Utilities.clamp(
                    Math.abs(viewPager.getPositionAnimated() - current) / Math.abs(next - current), 1f, 0f);
            tabsView.selectTabWithId(next, fraction);
        }
    }

    @Override
    protected void onViewPagerScrollEnd() {
        if (tabsView != null) tabsView.selectTabWithId(viewPager.getCurrentPosition(), 1f);
    }

    // Within messages, drags change categories; the outer main tabs remain reachable by tap.
    @Override
    public boolean canParentTabsSlide(MotionEvent event, boolean forward) { return false; }

    @Override
    public void onParentScrollToTop() {
        BaseFragment child = getCurrentVisibleFragment();
        if (child instanceof NativeDoMainTabsActivity.TabPage) {
            ((NativeDoMainTabsActivity.TabPage) child).onParentScrollToTop();
        }
    }

    private void showCurrentPageOptions() {
        BaseFragment child = getCurrentVisibleFragment();
        if (child instanceof MessageCenterChild) {
            ((MessageCenterChild) child).showMessageCenterOptions(optionsItem);
        }
    }

    private void updateColors() {
        if (tabsView != null) tabsView.updateColors();
        if (glassHost != null) glassHost.updateColors();
        if (actionBar != null) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
            actionBar.setTitleColor(getThemedColor(Theme.key_actionBarDefaultTitle));
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);
            actionBar.updateColors();
        }
        // The band itself stays transparent in either theme: only the capsule is a surface.
        if (stripBand != null) stripBand.setBackgroundColor(Color.TRANSPARENT);
        if (fragmentView != null) fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
    }

    @Override
    public void clearViews() {
        super.clearViews();
        tabsView = null;
        optionsItem = null;
        glassHost = null;
        stripBand = null;
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(themeObserver, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().removeObserver(themeObserver, NotificationCenter.needSetDayNightTheme);
        super.onFragmentDestroy();
    }
}

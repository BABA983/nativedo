package io.github.baba983.nativedo.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.ViewPagerFixed;

/**
 * The profile's tab area.
 *
 * <p>Ported from upstream's {@code SharedMediaLayout} - the tab strip, its colors and the paging
 * mechanism, not the Telegram-account-bound class itself (its pages load a user's photos, stories
 * and music through tgnet). The strip is upstream's own
 * (SharedMediaLayout.java:3740-3760 builds it with {@code dp(7)} padding, a 50dp frame and
 * {@code animationDuration = 320}; 4404-4405 set the profile tab colors and the minimal width),
 * and the pages are laid out the way SharedMediaLayout pads its own page lists
 * (SharedMediaLayout.java:3100: {@code setPadding(0, dp(48 + 6), 0, 0)}, which is what
 * {@link #PAGE_TOP_PADDING_DP} repeats). The pager is upstream's {@link ViewPagerFixed}, the
 * component DialogsActivity's search and other in-app pagers use; its protected
 * {@code onTabPageSelected} is overridden here to keep the strip in step when a swipe lands.</p>
 *
 * <p>What is left out is everything account-bound: the media preloader, the fast-scroll tooltip,
 * the action mode, the search item and the story/gift pages.</p>
 */
public class ProfileSharedMediaView extends FrameLayout {

    /** The strip's own height (SharedMediaLayout.java:3751: a 50dp frame with 7dp padding). */
    public static final int STRIP_HEIGHT_DP = 50;

    /**
     * The room a page keeps above its first row for the strip, as SharedMediaLayout's pages do
     * ({@code dp(48 + 6)} at SharedMediaLayout.java:3100).
     */
    public static final int PAGE_TOP_PADDING_DP = 54;

    /** What the fragment supplies: the tabs' names and one view per tab. */
    public interface Delegate {

        int getTabCount();

        CharSequence getTabTitle(int index);

        /**
         * Builds the page's view, once. The fragment draws the page's list, its shimmer and its
         * empty state and keeps the reference for paging; the media view only holds the pager.
         */
        View onCreatePageView(int index);

        /** A tab became the visible page; the first call is also the page's first load. */
        void onPageSelected(int index);

        /** The reader tapped the tab already showing: the page scrolls back to its top. */
        void onPageReselected(int index);
    }

    private final Delegate delegate;
    private final ScrollSlidingTextTabStrip tabStrip;
    private final ViewPagerFixed pager;
    private int currentTab = -1;
    private boolean tabsBuilt;

    public ProfileSharedMediaView(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, @NonNull Delegate delegate) {
        super(context);
        this.delegate = delegate;

        tabStrip = new ScrollSlidingTextTabStrip(context, resourcesProvider);
        tabStrip.animationDuration = 320;
        tabStrip.setColors(
                Theme.key_profile_tabSelectedLine,
                Theme.key_profile_tabSelectedText,
                Theme.key_profile_tabText,
                Theme.key_profile_tabSelector);
        tabStrip.setUseMinimalWidth(true);
        tabStrip.setPadding(0, dp(7), 0, dp(7));
        tabStrip.setClipToPadding(false);
        // The strip sits over the pages, and a page's rows scroll under it: it needs a surface of
        // its own. Upstream draws its blur3 glass here (SharedMediaLayout.java:3743-3749); this
        // screen keeps the theme's own background so the rows behind it do not show through.
        tabStrip.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        tabStrip.setOpen(false);
        tabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int pageId, boolean forward) {
                if (pageId == currentTab) {
                    return;
                }
                currentTab = pageId;
                pager.setPosition(pageId);
                delegate.onPageSelected(pageId);
            }

            @Override
            public void onSamePageSelected() {
                if (currentTab >= 0) {
                    delegate.onPageReselected(currentTab);
                }
            }

            @Override
            public void onPageScrolled(float progress) {
                // The pager runs its own swipe animation; the indicator follows on settle.
            }
        });

        pager = new ViewPagerFixed(context) {
            @Override
            protected void onTabPageSelected(int position, boolean forward) {
                if (currentTab == position) {
                    return;
                }
                currentTab = position;
                tabStrip.selectTabWithId(position, 1f);
                delegate.onPageSelected(position);
            }
        };
        pager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return delegate.getTabCount();
            }

            @Override
            public View createView(int viewType) {
                return delegate.onCreatePageView(viewType);
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                // A page's rows are bound by the fragment when they arrive.
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public CharSequence getItemTitle(int position) {
                return delegate.getTabTitle(position);
            }
        });

        // The pager goes in first and the strip over it: the strip is the pinned surface the
        // pages scroll under (upstream adds it after the pages, SharedMediaLayout.java:3751).
        addView(pager, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        addView(tabStrip, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                STRIP_HEIGHT_DP,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, -2, 0, -2, 0));
        selectTab(0, false);
    }

    /**
     * Fills the strip's tab list. Called once from the fragment after its child view is attached:
     * upstream also adds its tabs after construction (SharedMediaLayout.updateTabs).
     */
    public void buildTabs() {
        if (tabsBuilt) {
            return;
        }
        tabsBuilt = true;
        int count = delegate.getTabCount();
        for (int i = 0; i < count; i++) {
            tabStrip.addTextTab(i, delegate.getTabTitle(i));
        }
        tabStrip.setInitialTabId(0);
        tabStrip.finishAddingTabs();
    }

    private void selectTab(int index, boolean notify) {
        currentTab = index;
        pager.setPosition(index);
        if (notify) {
            delegate.onPageSelected(index);
        }
    }

    public int getCurrentTab() {
        return currentTab;
    }

    /** The strip, for tests and for the fragment's own insets handling. */
    public ScrollSlidingTextTabStrip getTabStrip() {
        return tabStrip;
    }

    public ViewPagerFixed getPager() {
        return pager;
    }
}

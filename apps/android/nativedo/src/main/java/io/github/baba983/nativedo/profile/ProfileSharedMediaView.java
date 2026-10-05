package io.github.baba983.nativedo.profile;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;

import io.github.baba983.nativedo.app.GlassPanelHost;

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
    private final Theme.ResourcesProvider resourcesProvider;
    private final ScrollSlidingTextTabStrip tabStrip;
    private final ViewPagerFixed pager;
    /** The strip's blur source: the pages it floats over. */
    private final GlassPanelHost glassHost;
    private int currentTab = -1;
    private boolean tabsBuilt;
    /** Whether the strip is pinned under the action bar (the media header's own state). */
    private boolean pinnedToTop;

    public ProfileSharedMediaView(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, @NonNull Delegate delegate) {
        super(context);
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;

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
        pager = new ViewPagerFixed(context, resourcesProvider) {
            @Override
            protected long getManualScrollDuration() {
                // A tap switches pages the way SharedMediaLayout's does: its strip's own 320 ms
                // (animationDuration = 320, SharedMediaLayout.java:4403), with the pages sliding
                // under the same clock.
                return 320;
            }

            @Override
            public void onTabAnimationUpdate(boolean manual) {
                followStripWithPager();
            }

            @Override
            protected void onTabPageSelected(int position, boolean forward) {
                selectPagerTab(position);
            }

            @Override
            protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {
                // A finger swipe committed (ViewPagerFixed.swapViews calls this): the strip follows
                // the pager onto the page it landed on, and the page's own stream is asked for.
                selectPagerTab(position);
            }
        };
        // The strip's own surface is upstream's blur3 glass (SharedMediaLayout.java:3740-3750):
        // a rounded capsule over the pages, not a flat band. Upstream's factory is this client's
        // GlassPanelHost, the same one the home strip and the topic capsules use; on a device whose
        // blur is off it falls back to the provider's flat colour, exactly as upstream's own
        // provider does.
        glassHost = new GlassPanelHost(pager, this);
        BlurredBackgroundDrawable stripBackground = glassHost.createPanel(
                tabStrip, BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
        stripBackground.setRadius(dp(18));
        stripBackground.setPadding(dp(6.666f));
        // Upstream draws no flat background under the strip; the glass is the surface
        // (SharedMediaLayout.java:3748-3750: setBackground(null) + setBlurredBackground).
        tabStrip.setBackground(null);
        tabStrip.setBlurredBackground(stripBackground);
        tabStrip.setOpen(false);
        tabStrip.setDelegate(new ScrollSlidingTextTabStrip.ScrollSlidingTabStripDelegate() {
            @Override
            public void onPageSelected(int pageId, boolean forward) {
                if (pageId == currentTab) {
                    return;
                }
                currentTab = pageId;
                // A tap slides the page pair the way upstream's pages slide under its strip
                // (SharedMediaLayout's onPageScrolled translates the two pages); the pager's own
                // clock is the strip's 320 ms (getManualScrollDuration above).
                pager.scrollToPosition(pageId);
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
                // The pager owns the clock here, so the strip follows it (followStripWithPager);
                // upstream's pages are the ones that follow this callback.
            }
        });
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

    /**
     * The pager moved onto [position]: the strip follows it exactly and the page's own stream is
     * asked for. Called from a tap's {@code onTabPageSelected} (the strip has already animated
     * itself there, upstream's own way) and from a finger swipe's committed {@code onItemSelected}.
     */
    private void selectPagerTab(int position) {
        if (position < 0 || position >= delegate.getTabCount() || position == currentTab) {
            return;
        }
        currentTab = position;
        tabStrip.selectTabWithId(position, 1f);
        delegate.onPageSelected(position);
    }

    /**
     * The strip follows the pager while a swipe is in flight - the indicator slides between the two
     * tabs and their text colours interpolate - the way upstream's pages follow its strip
     * ({@code SharedMediaLayout} translates the pages in the strip's own {@code onPageScrolled}).
     * Here the pager owns the clock, so the strip is driven from it: the fraction is how far the
     * pager's animated position has come from the tab it is on toward the tab it is heading to - as
     * soon as the drag starts, so the indicator moves with the finger, and back to 0 when the drag
     * is cancelled.
     */
    private void followStripWithPager() {
        if (tabStrip.getTabsCount() < 2) {
            return;
        }
        View[] pages = pager.getViewPages();
        if (pages == null || pages.length < 2 || pages[1] == null) {
            return;
        }
        final int current = pager.getCurrentPosition();
        final int next = pager.getNextPosition();
        if (next == current || next < 0 || next >= tabStrip.getTabsCount()) {
            return;
        }
        final float p = pager.getPositionAnimated();
        final float frac = Utilities.clamp(Math.abs(p - current) / Math.abs(next - current), 1f, 0f);
        tabStrip.selectTabWithId(next, frac);
    }

    /** The media header's pinned state, upstream's {@code sharedMediaLayout.setPinnedToTop}. */
    public void setPinnedToTop(boolean pinnedToTop) {
        this.pinnedToTop = pinnedToTop;
    }

    public boolean isPinnedToTop() {
        return pinnedToTop;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // The strip's glass samples the pages under it, so its source has to be recorded in the
        // same pass that draws them (upstream's {@code SharedMediaLayout.drawListForBlur} does the
        // same for its strip's background).
        if (glassHost != null) {
            glassHost.record();
        }
        super.dispatchDraw(canvas);
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

    /** The day/night switch: the strip's glass and its tabs re-read the theme's colours. */
    public void updateColors() {
        if (tabStrip == null) {
            return;
        }
        glassHost.updateColors();
        tabStrip.updateColors();
    }
}

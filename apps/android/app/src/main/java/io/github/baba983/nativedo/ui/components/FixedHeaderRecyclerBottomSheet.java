/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of Telegram's org/telegram/ui/Components/StickersAlert.java.
 * It keeps the upstream sheet measurement, drawing, and scroll-offset shape
 * while exposing NativeDo-owned header, adapter, and content-height wiring.
 */
package io.github.baba983.nativedo.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class FixedHeaderRecyclerBottomSheet extends BottomSheet {

    public interface HeaderShadowDelegate {
        void setShadowVisible(boolean visible);
    }

    private final RecyclerListView recyclerListView;
    private final ArrayList<RecyclerListView> scrollableRecyclerViews = new ArrayList<>();
    private final RectF rect = new RectF();

    private View contentView;
    private RecyclerListView activeRecyclerListView;
    private RecyclerListView transitionFromRecyclerListView;
    private RecyclerListView transitionToRecyclerListView;
    private float recyclerListTransitionProgress;
    private View fixedHeaderView;
    private HeaderShadowDelegate headerShadowDelegate;
    private int scrollOffsetY;
    private int contentHeightPx;
    private int minimumContentHeightPx;
    private boolean ignoreLayout;
    private boolean fullHeight;
    private boolean keepSheetPositionOnScroll;
    private Boolean statusBarOpen;
    private int contentTopPadding;

    public FixedHeaderRecyclerBottomSheet(@NonNull Context context, @NonNull BaseFragment fragment) {
        super(context, false, null);
        setUseLightStatusBar(fragment.isLightStatusBar());

        recyclerListView = new RecyclerListView(context);
        containerView = new FrameLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return !isDismissed() && super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }

                int headerHeight = 0;
                if (fixedHeaderView != null) {
                    fixedHeaderView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    headerHeight = fixedHeaderView.getMeasuredHeight();
                }
                ViewGroup.LayoutParams rawParams = contentView.getLayoutParams();
                if (rawParams instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
                    if (params.topMargin != headerHeight) {
                        params.topMargin = headerHeight;
                        contentView.setLayoutParams(params);
                    }
                }

                int contentSize = headerHeight + Math.max(contentHeightPx, minimumContentHeightPx) + backgroundPaddingTop;
                if (Build.VERSION.SDK_INT >= 21) {
                    contentSize += AndroidUtilities.statusBarHeight;
                }
                int padding = contentSize < height / 5f * 3.2f ? 0 : (int) (height / 5f * 2);
                if (padding != 0 && contentSize < height) {
                    padding -= height - contentSize;
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
                }
                if (contentTopPadding != padding) {
                    contentTopPadding = padding;
                    ignoreLayout = true;
                    for (int i = 0; i < scrollableRecyclerViews.size(); i++) {
                        RecyclerListView listView = scrollableRecyclerViews.get(i);
                        listView.setPadding(0, padding, 0, AndroidUtilities.dp(8));
                    }
                    ignoreLayout = false;
                }
                fullHeight = contentSize >= height;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, height), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6);
                int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13);
                int statusBarHeight = 0;
                float radProgress = 1.0f;
                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    y += AndroidUtilities.statusBarHeight;

                    if (fullHeight) {
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
                            int diff = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop);
                            top -= diff;
                            radProgress = 1.0f - Math.min(1.0f, diff * 2f / AndroidUtilities.statusBarHeight);
                        }
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                            statusBarHeight = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop);
                        }
                    }
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), getMeasuredHeight());
                shadowDrawable.draw(canvas);

                if (radProgress != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_dialogBackground));
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
                }

                int width = AndroidUtilities.dp(36);
                rect.set((getMeasuredWidth() - width) / 2f, y, (getMeasuredWidth() + width) / 2f, y + AndroidUtilities.dp(4));
                Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_sheet_scrollUp));
                Theme.dialogs_onlineCirclePaint.setAlpha((int) (Theme.dialogs_onlineCirclePaint.getAlpha() * Math.max(0, Math.min(1f, (y - AndroidUtilities.statusBarHeight) / (float) AndroidUtilities.dp(16)))));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

                updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2);
                if (statusBarHeight > 0) {
                    Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_dialogBackground));
                    canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));

        recyclerListView.setItemAnimator(null);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setLayoutManager(new LinearLayoutManager(context));
        contentView = recyclerListView;
        activeRecyclerListView = recyclerListView;
        registerScrollableRecyclerListView(recyclerListView);
        containerView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
    }

    public final void registerScrollableRecyclerListView(@NonNull RecyclerListView listView) {
        if (scrollableRecyclerViews.contains(listView)) {
            return;
        }
        scrollableRecyclerViews.add(listView);
        listView.setClipToPadding(false);
        listView.setPadding(0, contentTopPadding, 0, AndroidUtilities.dp(8));
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (recyclerView == activeRecyclerListView) {
                    updateLayout();
                }
            }
        });
    }

    public final RecyclerListView getRecyclerListView() {
        return recyclerListView;
    }

    public final void setRecyclerAdapter(@Nullable RecyclerListView.SelectionAdapter adapter) {
        recyclerListView.setAdapter(adapter);
    }

    public final void setSheetContentView(@NonNull View view) {
        if (contentView == view) {
            return;
        }
        if (contentView != null) {
            containerView.removeView(contentView);
        }
        ViewParentCompat.removeFromParent(view);
        contentView = view;
        containerView.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        if (fixedHeaderView != null) {
            fixedHeaderView.bringToFront();
        }
        containerView.requestLayout();
    }

    public final void setActiveRecyclerListView(@NonNull RecyclerListView listView) {
        registerScrollableRecyclerListView(listView);
        transitionFromRecyclerListView = null;
        transitionToRecyclerListView = null;
        recyclerListTransitionProgress = 0f;
        activeRecyclerListView = listView;
        updateLayout();
    }

    public final void setRecyclerListTransition(
            @NonNull RecyclerListView fromListView,
            @NonNull RecyclerListView toListView,
            float progress
    ) {
        registerScrollableRecyclerListView(fromListView);
        registerScrollableRecyclerListView(toListView);
        transitionFromRecyclerListView = fromListView;
        transitionToRecyclerListView = toListView;
        recyclerListTransitionProgress = Math.max(0f, Math.min(1f, progress));
        updateLayout();
    }

    public final void setFixedHeaderView(@NonNull View view) {
        if (fixedHeaderView == view) {
            return;
        }
        if (fixedHeaderView != null) {
            containerView.removeView(fixedHeaderView);
        }
        ViewParentCompat.removeFromParent(view);
        fixedHeaderView = view;
        containerView.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        view.setTranslationY(scrollOffsetY);
        view.bringToFront();
        containerView.requestLayout();
    }

    public final void setHeaderShadowDelegate(@Nullable HeaderShadowDelegate delegate) {
        headerShadowDelegate = delegate;
    }

    public final void setKeepSheetPositionOnScroll(boolean keep) {
        if (keepSheetPositionOnScroll == keep) {
            return;
        }
        keepSheetPositionOnScroll = keep;
        updateLayout();
    }

    public final void setContentHeightPx(int contentHeightPx) {
        int normalized = Math.max(0, contentHeightPx);
        if (this.contentHeightPx == normalized) {
            return;
        }
        this.contentHeightPx = normalized;
        containerView.requestLayout();
    }

    public final int getContentHeightPx() {
        return contentHeightPx;
    }

    public final void setMinimumContentHeightPx(int minimumContentHeightPx) {
        int normalized = Math.max(0, minimumContentHeightPx);
        if (this.minimumContentHeightPx == normalized) {
            return;
        }
        this.minimumContentHeightPx = normalized;
        containerView.requestLayout();
    }

    public final void scrollToTop() {
        activeRecyclerListView.scrollToPosition(0);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private void updateLightStatusBar(boolean open) {
        if (statusBarOpen != null && statusBarOpen == open) {
            return;
        }
        boolean openBgLight = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f;
        boolean closedBgLight = AndroidUtilities.computePerceivedBrightness(Theme.blendOver(getThemedColor(Theme.key_actionBarDefault), 0x33000000)) > .721f;
        statusBarOpen = open;
        AndroidUtilities.setLightStatusBar(getWindow(), open ? openBgLight : closedBgLight);
    }

    private void updateLayout() {
        if (transitionFromRecyclerListView != null && transitionToRecyclerListView != null) {
            int fromOffset = calculateScrollOffset(transitionFromRecyclerListView);
            int toOffset = calculateScrollOffset(transitionToRecyclerListView);
            int contentOffset = (int) (fromOffset + (toOffset - fromOffset) * recyclerListTransitionProgress);
            RecyclerListView shadowListView = recyclerListTransitionProgress < .5f
                    ? transitionFromRecyclerListView
                    : transitionToRecyclerListView;
            int newOffset = keepSheetPositionOnScroll ? contentTopPadding : contentOffset;
            if (headerShadowDelegate != null) {
                headerShadowDelegate.setShadowVisible(
                        keepSheetPositionOnScroll
                                ? shadowListView.canScrollVertically(-1)
                                : contentOffset == 0 && shadowListView.canScrollVertically(-1)
                );
            }
            if (scrollOffsetY != newOffset) {
                setScrollOffsetY(newOffset);
            }
            return;
        }
        RecyclerListView listView = activeRecyclerListView;
        int contentOffset = calculateScrollOffset(listView);
        int newOffset = keepSheetPositionOnScroll ? contentTopPadding : contentOffset;
        if (headerShadowDelegate != null) {
            headerShadowDelegate.setShadowVisible(
                    keepSheetPositionOnScroll
                            ? listView.canScrollVertically(-1)
                            : contentOffset == 0 && listView.canScrollVertically(-1)
            );
        }
        if (scrollOffsetY != newOffset) {
            setScrollOffsetY(newOffset);
        }
    }

    private int calculateScrollOffset(@NonNull RecyclerListView listView) {
        if (listView.getChildCount() <= 0) {
            return listView.getPaddingTop();
        }
        int firstChildPosition = -1;
        View firstChild = null;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            int position = listView.getChildAdapterPosition(child);
            if (firstChildPosition == -1 || firstChildPosition > position) {
                firstChild = child;
                firstChildPosition = position;
            }
        }
        int newOffset = 0;
        if (firstChild != null && firstChild.getTop() >= 0) {
            newOffset = firstChild.getTop();
        }
        return newOffset;
    }

    private void setScrollOffsetY(int newOffset) {
        scrollOffsetY = newOffset;
        if (fixedHeaderView != null) {
            fixedHeaderView.setTranslationY(newOffset);
            fixedHeaderView.bringToFront();
        }
        containerView.invalidate();
    }

    private static final class ViewParentCompat {
        private static void removeFromParent(@NonNull View view) {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        }
    }
}

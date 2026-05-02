// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/Components/UniversalFragment.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Uses NativeDo BaseFragment/ActionBar and plain FrameLayout instead of Telegram SizeNotifierFrameLayout.
// ADAPTED-PRODUCT:
//   - Applies the white Telegram browser/settings page action-bar styling used by current NativeDo browser sheets.
// OMITTED:
//   - Telegram resource providers, AdjustPan helpers, account plumbing, and broader navigation integrations.
package io.github.baba983.nativedo.ui.components;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.actionbar.ActionBar;
import io.github.baba983.nativedo.ui.actionbar.BackDrawable;
import io.github.baba983.nativedo.ui.actionbar.BaseFragment;
import io.github.baba983.nativedo.ui.actionbar.Theme;

public abstract class UniversalFragment extends BaseFragment {

    public UniversalRecyclerView listView;
    private int savedScrollPosition = -1;
    private int savedScrollOffset;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle(getTitle());
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setCastShadows(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                savedScrollPosition = -1;
            }
        };
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView = contentView;
    }

    protected abstract CharSequence getTitle();

    protected abstract void fillItems(ArrayList<UItem> items, UniversalAdapter adapter);

    protected abstract void onClick(UItem item, View view, int position, float x, float y);

    protected abstract boolean onLongClick(UItem item, View view, int position, float x, float y);

    public void saveScrollPosition() {
        if (listView == null || listView.getChildCount() <= 0) {
            return;
        }
        View view = null;
        int position = -1;
        int top = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int childPosition = listView.getChildAdapterPosition(child);
            if (childPosition != RecyclerView.NO_POSITION && child.getTop() < top) {
                view = child;
                position = childPosition;
                top = child.getTop();
            }
        }
        if (view != null) {
            savedScrollPosition = position;
            savedScrollOffset = view.getTop();
            if (savedScrollPosition == 0 && savedScrollOffset > AndroidUtilities.dp(88)) {
                savedScrollOffset = AndroidUtilities.dp(88);
            }
            listView.layoutManager.scrollToPositionWithOffset(position, view.getTop() - listView.getPaddingTop());
        }
    }

    public void applyScrolledPosition() {
        if (savedScrollPosition >= 0 && listView != null) {
            listView.layoutManager.scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset - listView.getPaddingTop());
        }
    }
}

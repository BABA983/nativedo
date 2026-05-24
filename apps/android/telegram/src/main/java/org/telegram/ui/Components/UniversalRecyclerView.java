// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/Components/UniversalRecyclerView.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Now extends RecyclerListView (Telegram's real list) instead of AndroidX RecyclerView.
// ADAPTED-PRODUCT:
//   - Keeps Telegram's listView.adapter/layoutManager surface for UniversalFragment users.
// OMITTED:
//   - Grid spans, ItemTouchHelper drag reorder, section drawing, and RecyclerListView-specific listeners.
package org.telegram.ui.Components;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.ui.ActionBar.BaseFragment;

public class UniversalRecyclerView extends RecyclerListView {

    public LinearLayoutManager layoutManager;
    public final UniversalAdapter adapter;

    public UniversalRecyclerView(
            BaseFragment fragment,
            UniversalListCallbacks.Fill fillItems,
            @Nullable UniversalListCallbacks.Click onClick,
            @Nullable UniversalListCallbacks.LongClick onLongClick
    ) {
        this(fragment.getContext(), fillItems, onClick, onLongClick);
    }

    public UniversalRecyclerView(
            Context context,
            UniversalListCallbacks.Fill fillItems,
            @Nullable UniversalListCallbacks.Click onClick,
            @Nullable UniversalListCallbacks.LongClick onLongClick
    ) {
        super(context);
        setLayoutManager(layoutManager = new LinearLayoutManager(context));
        setAdapter(adapter = new UniversalAdapter(context, fillItems, onClick, onLongClick));
        setVerticalScrollBarEnabled(false);
        setClipToPadding(false);

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setAddDuration(350);
        itemAnimator.setRemoveDuration(350);
        itemAnimator.setMoveDuration(350);
        itemAnimator.setChangeDuration(350);
        setItemAnimator(itemAnimator);
    }
}

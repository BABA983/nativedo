// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/Components/UniversalRecyclerView.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Uses AndroidX RecyclerView and NativeDo BaseFragment.
// ADAPTED-PRODUCT:
//   - Keeps Telegram's listView.adapter/layoutManager surface for UniversalFragment users.
// OMITTED:
//   - Grid spans, ItemTouchHelper drag reorder, section drawing, and RecyclerListView-specific listeners.
package io.github.baba983.nativedo.ui.components;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.baba983.nativedo.ui.actionbar.BaseFragment;

public class UniversalRecyclerView extends RecyclerView {

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

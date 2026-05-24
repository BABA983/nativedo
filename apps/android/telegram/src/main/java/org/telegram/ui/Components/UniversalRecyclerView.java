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

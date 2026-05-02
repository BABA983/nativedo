// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/Components/UniversalAdapter.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Uses AndroidX RecyclerView instead of Telegram RecyclerListView/AdapterWithDiffUtils.
// ADAPTED-PRODUCT:
//   - Keeps Telegram's UniversalAdapter item flow, callback shape, and core view-type constants
//     while binding only NativeDo's currently ported browser/settings cells.
//   - Uses notifyDataSetChanged for updates until more upstream cell families are needed.
// OMITTED:
//   - Telegram dialog/user/business/statistics/check/switch factories, diff payloads, and drag reorder.
package io.github.baba983.nativedo.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Objects;

public class UniversalAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_CUSTOM = -1;
    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_TEXT = 3;
    public static final int VIEW_TYPE_CHECK = 4;
    public static final int VIEW_TYPE_SHADOW = 7;
    public static final int VIEW_TYPE_RADIO = 10;
    public static final int VIEW_TYPE_GRAY_SECTION = 31;
    public static final int VIEW_TYPE_BOOKMARK = 1000;
    public static final int VIEW_TYPE_WEBSITE = 1001;

    private final Context context;
    private final UniversalListCallbacks.Fill fillItems;
    @Nullable private final UniversalListCallbacks.Click onClick;
    @Nullable private final UniversalListCallbacks.LongClick onLongClick;
    private final ArrayList<UItem> items = new ArrayList<>();
    private float lastTouchX;
    private float lastTouchY;

    public UniversalAdapter(
            Context context,
            UniversalListCallbacks.Fill fillItems,
            @Nullable UniversalListCallbacks.Click onClick,
            @Nullable UniversalListCallbacks.LongClick onLongClick
    ) {
        this.context = context;
        this.fillItems = fillItems;
        this.onClick = onClick;
        this.onLongClick = onLongClick;
        update(false);
    }

    public void update(boolean animated) {
        ArrayList<UItem> oldItems = new ArrayList<>(items);
        ArrayList<UItem> newItems = new ArrayList<>();
        if (fillItems != null) {
            fillItems.run(newItems, this);
        }
        if (!animated || items.isEmpty()) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
            return;
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return isSameItem(oldItems.get(oldItemPosition), newItems.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return isSameContent(oldItems.get(oldItemPosition), newItems.get(newItemPosition));
            }
        }, false);
        items.clear();
        items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        UItem item = getItem(position);
        return item == null ? VIEW_TYPE_SHADOW : item.viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        UItem.ViewFactory factory = findFactoryForViewType(viewType);
        if (factory != null) {
            view = factory.createView(context);
        } else switch (viewType) {
            case VIEW_TYPE_HEADER:
                view = new UniversalCells.SectionCell(context, true);
                break;
            case VIEW_TYPE_GRAY_SECTION:
                view = new UniversalCells.SectionCell(context, false);
                break;
            case VIEW_TYPE_TEXT:
                view = new UniversalCells.TextCell(context);
                break;
            case VIEW_TYPE_CHECK:
                view = new UniversalCells.CheckCell(context);
                break;
            case VIEW_TYPE_RADIO:
                view = new UniversalCells.RadioCell(context);
                break;
            case VIEW_TYPE_BOOKMARK:
                view = new UniversalCells.BookmarkCell(context);
                break;
            case VIEW_TYPE_WEBSITE:
                view = new UniversalCells.WebsiteCell(context);
                break;
            case VIEW_TYPE_CUSTOM:
                view = new FrameLayout(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(
                                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                                heightMeasureSpec
                        );
                    }
                };
                break;
            case VIEW_TYPE_SHADOW:
            default:
                view = new UniversalCells.ShadowInfoCell(context);
                break;
        }
        return new RecyclerView.ViewHolder(view) {
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        UItem item = getItem(position);
        if (item == null) {
            return;
        }
        boolean divider = hasDivider(position);
        if (item.factory != null) {
            item.factory.bindView(holder.itemView, item, divider, this);
            bindClickListeners(holder, item);
            return;
        }
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_HEADER:
            case VIEW_TYPE_GRAY_SECTION:
                ((UniversalCells.SectionCell) holder.itemView).setText(item.text);
                break;
            case VIEW_TYPE_TEXT:
                ((UniversalCells.TextCell) holder.itemView).bind(item.text, item.subtext, item.textValue, item.iconResId, item.red, item.accent, divider);
                break;
            case VIEW_TYPE_CHECK:
                ((UniversalCells.CheckCell) holder.itemView).bind(item.text, item.checked, divider);
                break;
            case VIEW_TYPE_RADIO:
                ((UniversalCells.RadioCell) holder.itemView).bind(item.text, item.textValue, item.checked, divider);
                break;
            case VIEW_TYPE_BOOKMARK:
                ((UniversalCells.BookmarkCell) holder.itemView).bind(
                        item.text,
                        item.subtext,
                        item.object instanceof Bitmap ? (Bitmap) item.object : null,
                        item.checked,
                        item.longValue,
                        divider
                );
                break;
            case VIEW_TYPE_WEBSITE:
                ArrayList<String> domains = item.object2 instanceof ArrayList ? (ArrayList<String>) item.object2 : new ArrayList<>();
                if (domains.isEmpty() && !TextUtils.isEmpty(item.text)) {
                    domains.add(item.text.toString());
                }
                ((UniversalCells.WebsiteCell) holder.itemView).bind(
                        item.text,
                        domains,
                        item.object instanceof Bitmap ? (Bitmap) item.object : null,
                        divider
                );
                break;
            case VIEW_TYPE_CUSTOM:
                bindCustomView((FrameLayout) holder.itemView, item.view);
                break;
            case VIEW_TYPE_SHADOW:
            default:
                ((UniversalCells.ShadowInfoCell) holder.itemView).setText(item.text);
                break;
        }
        bindClickListeners(holder, item);
    }

    private void bindCustomView(FrameLayout container, @Nullable View view) {
        container.removeAllViews();
        if (view == null) {
            return;
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        container.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void bindClickListeners(RecyclerView.ViewHolder holder, UItem item) {
        if (!isEnabled(holder)) {
            holder.itemView.setOnTouchListener(null);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setOnLongClickListener(null);
            return;
        }
        holder.itemView.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.getX();
                lastTouchY = event.getY();
            }
            return false;
        });
        holder.itemView.setOnClickListener(onClick == null ? null : view -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                UItem currentItem = getItem(position);
                if (currentItem != null) {
                    onClick.run(currentItem, view, position, lastTouchX, lastTouchY);
                }
            }
        });
        holder.itemView.setOnLongClickListener(onLongClick == null ? null : view -> {
            int position = holder.getBindingAdapterPosition();
            UItem currentItem = getItem(position);
            return position != RecyclerView.NO_POSITION && currentItem != null && onLongClick.run(currentItem, view, position, lastTouchX, lastTouchY);
        });
    }

    private boolean hasDivider(int position) {
        UItem item = getItem(position);
        UItem nextItem = getItem(position + 1);
        return item != null && !item.hideDivider && nextItem != null && isShadow(nextItem.viewType) == isShadow(item.viewType);
    }

    @Nullable
    private UItem.ViewFactory findFactoryForViewType(int viewType) {
        for (int i = 0; i < items.size(); i++) {
            UItem item = items.get(i);
            if (item != null && item.factory != null && item.viewType == viewType) {
                return item.factory;
            }
        }
        return null;
    }

    public static boolean isShadow(int viewType) {
        return viewType == VIEW_TYPE_SHADOW || viewType == VIEW_TYPE_GRAY_SECTION;
    }

    private boolean isSameItem(UItem oldItem, UItem newItem) {
        if (oldItem.viewType != newItem.viewType) {
            return false;
        }
        if (oldItem.factory != null || newItem.factory != null) {
            return oldItem.id == newItem.id
                    && TextUtils.equals(oldItem.text, newItem.text)
                    && TextUtils.equals(oldItem.textValue, newItem.textValue);
        }
        if (oldItem.id != 0 || newItem.id != 0) {
            if (oldItem.id != newItem.id) {
                return false;
            }
            if (oldItem.viewType == VIEW_TYPE_WEBSITE) {
                return TextUtils.equals(oldItem.textValue, newItem.textValue);
            }
            return true;
        }
        return TextUtils.equals(oldItem.text, newItem.text) && Objects.equals(oldItem.object2, newItem.object2);
    }

    private boolean isSameContent(UItem oldItem, UItem newItem) {
        return oldItem.id == newItem.id
                && oldItem.checked == newItem.checked
                && oldItem.accent == newItem.accent
                && oldItem.red == newItem.red
                && oldItem.hideDivider == newItem.hideDivider
                && oldItem.iconResId == newItem.iconResId
                && oldItem.longValue == newItem.longValue
                && TextUtils.equals(oldItem.text, newItem.text)
                && TextUtils.equals(oldItem.subtext, newItem.subtext)
                && TextUtils.equals(oldItem.textValue, newItem.textValue)
                && Objects.equals(oldItem.object, newItem.object)
                && Objects.equals(oldItem.object2, newItem.object2);
    }

    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int viewType = holder.getItemViewType();
        UItem item = getItem(holder.getBindingAdapterPosition());
        if (item != null && item.factory != null) {
            return item.factory.isEnabled(item);
        }
        return viewType == VIEW_TYPE_TEXT
                || viewType == VIEW_TYPE_CHECK
                || viewType == VIEW_TYPE_RADIO
                || viewType == VIEW_TYPE_BOOKMARK
                || viewType == VIEW_TYPE_WEBSITE;
    }

    @Nullable
    public UItem getItem(int position) {
        if (position < 0 || position >= items.size()) {
            return null;
        }
        return items.get(position);
    }

    public UItem findItem(int itemId) {
        for (int i = 0; i < items.size(); i++) {
            UItem item = items.get(i);
            if (item != null && item.id == itemId) {
                return item;
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}

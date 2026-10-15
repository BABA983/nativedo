package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.glass.GlassTabView;

/**
 * A Telegram-shaped attachment sheet: the paperclip's own menu, in upstream's
 * {@link BottomSheet} shape over one horizontal list of account-free attach tabs.
 *
 * <p>Upstream's attach menu is {@code ChatAttachAlert}, but that object is account-backed: it
 * builds Telegram's gallery, media controllers and send helpers, none of which exist for a
 * Discourse account. This sheet is the same shape without them. The list, the tab strip and the
 * drag are upstream's own components (a {@link RecyclerListView} of {@link GlassTabView} install
 * tabs inside a {@link BottomSheet}); what each tab does is this client's, and every action here
 * reaches a site endpoint or the site's own raw, never a Telegram one.</p>
 *
 * <p>The sheet opens compact - the drag handle and one row of tabs - and expands by dragging the
 * sheet up, the way upstream's attach alert grows toward the full height of its host. The expanded
 * area is not blank: the active tab's own content fills it. The Poll tab keeps its question,
 * option and settings form inline, above the tab row, so the poll is written where it is read
 * instead of in a second popup; dragging back down settles the sheet compact again, and only a
 * downward drag while it is already compact dismisses it, which is upstream's own dismiss
 * gesture.</p>
 *
 * <p>Gallery and File cannot show an account-free grid, so they hand off to Android's own content
 * picker, and the rich-text tab opens upstream's editor - both are choices the site can carry.
 * There is no location, contact, music, sticker, emoji or premium tab, because a Discourse account
 * has no send path for any of them and a drawn control with nothing behind it is exactly what this
 * sheet exists to avoid.</p>
 */
public class DiscourseAttachSheet extends BottomSheet {

    /** The actions the site can carry. Wire them to the content picker, the rich editor or a poll. */
    public enum Action {
        GALLERY,
        FILES,
        RICH_EDITOR,
        POLL,
    }

    /** What a chosen tab means to the host screen. */
    public interface Listener {
        void onAttachAction(Action action);
    }

    /**
     * Where the sheet's inline poll raw goes: the host writes the poll into its own field. The sheet
     * requires one, because the Poll tab keeps its form in the sheet rather than in a second popup.
     */
    public interface PollSink {
        void onPoll(String raw);
    }

    private static final int HANDLE_WIDTH_DP = 36;
    private static final int HANDLE_TOP_MARGIN_DP = 8;
    private static final int HANDLE_HEIGHT_DP = 4;
    private static final int ROW_TOP_MARGIN_DP = 6;
    private static final int ROW_HEIGHT_DP = 70;
    private static final int ROW_BOTTOM_MARGIN_DP = 6;
    /** How far the sheet must be dragged before a finger counts as expanding/collapsing it. */
    private static final float SETTLE_FRACTION = 0.35f;

    private final Listener listener;
    private final PollSink pollSink;
    private final FrameLayout content;
    private final LinearLayout root;
    private final FrameLayout bodyHost;
    private final RecyclerListView row;
    private final ItemsAdapter adapter;
    private final DiscoursePollComposer.Form pollForm;

    private final int touchSlop;
    private float dragStartRawY = -1f;
    private boolean dragging;
    /** The height the content had when the drag began. */
    private int dragStartHeight;
    /** The height the content has been asked for, so the drag math never depends on a layout pass. */
    private int currentContentHeight;
    private boolean expanded;
    private Action selectedAction;

    /**
     * The sheet: the paperclip's menu. The Poll tab keeps its question/options/settings form in the
     * sheet, filling the expanded area above the tabs, and a successful Create hands the site's raw
     * to [pollSink]. The other tabs hand their action to [listener] and dismiss the sheet.
     */
    public DiscourseAttachSheet(@NonNull Context context, Theme.ResourcesProvider resourcesProvider,
            @NonNull Listener listener, @NonNull PollSink pollSink) {
        super(context, false, resourcesProvider);
        this.listener = listener;
        this.pollSink = pollSink;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        setOpenNoDelay(true);

        // The sheet's rounded background is upstream's own (the container view's shadow drawable).
        // The content is a column that fills whatever height the sheet has been asked for: the drag
        // handle, the active tab's body (weighted, so it takes the expanded space) and the tab row.
        content = new FrameLayout(context);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        View handle = new View(context);
        handle.setBackground(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(2), getThemedColor(Theme.key_dialogScrollGlow)));
        handle.setContentDescription("拖动或点击展开、收起");
        // A drag reaches the sheet through its own touch hook; a plain tap on the handle toggles
        // the same state, so a reader who cannot drag (an accessibility service) can still open and
        // close the panel.
        handle.setFocusable(true);
        handle.setClickable(true);
        handle.setOnClickListener(view -> setExpanded(!expanded));
        // LayoutHelper applies its own dp conversion to these sizes and margins, so they are dp
        // values here (not pixels): the compact height below sums exactly the space this handle and
        // the tab row take, and a pre-converted value would be scaled twice and overflow it.
        root.addView(handle, LayoutHelper.createLinear(
                HANDLE_WIDTH_DP, HANDLE_HEIGHT_DP,
                Gravity.CENTER_HORIZONTAL, 0, HANDLE_TOP_MARGIN_DP, 0, 0));

        bodyHost = new FrameLayout(context);
        bodyHost.setClipToPadding(false);
        root.addView(bodyHost, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 0, 1f));

        row = new RecyclerListView(context);
        row.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        row.setAdapter(adapter = new ItemsAdapter(context));
        row.setVerticalScrollBarEnabled(false);
        row.setHorizontalScrollBarEnabled(false);
        row.setItemAnimator(null);
        row.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        row.setClipToPadding(false);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.setOnItemClickListener((view, position) -> choose(adapter.actionAt(position)));
        root.addView(row, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, ROW_HEIGHT_DP,
                0, ROW_TOP_MARGIN_DP, 0, ROW_BOTTOM_MARGIN_DP));

        content.addView(root, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));

        // The poll is the one tab whose content lives in the sheet: it is built once and kept in the
        // weighted body, so expanding the sheet reveals it rather than a blank area.
        pollForm = DiscoursePollComposer.inline(context, resourcesProvider, raw -> {
            dismiss();
            pollSink.onPoll(raw);
        });
        ScrollView scroller = new ScrollView(context);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        scroller.addView(pollForm.getView(), LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        bodyHost.addView(scroller, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));
        selectedAction = Action.POLL;

        setCustomView(content);
    }

    /**
     * The container now holds the custom view, so the content can be given its compact height before
     * the sheet is first measured; without this the weighted body would measure to full height and
     * the sheet would open tall instead of as the tab row.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (currentContentHeight <= 0) {
            currentContentHeight = compactHeightPx();
        }
        applyContentHeight();
    }

    /** A tab was tapped. Poll stays in the sheet; the rest hand off and the sheet goes away. */
    private void choose(Action action) {
        if (action == Action.POLL) {
            selectAction(action);
            setExpanded(true);
            pollForm.focusQuestion();
            return;
        }
        dismiss();
        if (listener != null && action != null) {
            listener.onAttachAction(action);
        }
    }

    /**
     * Marks one tab chosen and shows its content. The tap drives this; a host that already knows
     * which tab it wants (a screen reusing the sheet) can set it directly.
     */
    public void selectAction(Action action) {
        selectedAction = action;
        adapter.notifyDataSetChanged();
    }

    /** Whether the sheet has been dragged to (or settled at) full height. */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Grows the sheet to full height or back to its compact row. The shipped gesture reaches this
     * through {@link #onContainerTouchEvent(MotionEvent)}; a host that already knows the state (a
     * chat screen reusing the sheet) can set it directly.
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        setContentHeight(expanded ? maxContentHeight() : compactHeightPx());
    }

    // --------------------------------------------------------------- dragging

    /**
     * The sheet's own drag. Upstream's {@link BottomSheet} only tracks a downward finger - the one
     * that dismisses - so the upward half of the gesture is answered here through upstream's own
     * touch hook: a finger that leaves the slop going up while the sheet is compact grows it toward
     * full height, and one going down while it is already expanded settles it back compact. A
     * downward drag while compact is left to the sheet, which dismisses. A drag that would not
     * change the state is left to the child (the expanded poll form scrolls under it).
     */
    @Override
    protected boolean onContainerTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            dragStartRawY = event.getRawY();
            dragging = false;
            return false;
        }
        if (dragStartRawY < 0f) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float dy = event.getRawY() - dragStartRawY;
            if (!dragging) {
                boolean wantsExpand = dy <= -touchSlop && !expanded;
                boolean wantsCollapse = dy >= touchSlop && expanded;
                if (!wantsExpand && !wantsCollapse) {
                    return false;
                }
                dragging = true;
                dragStartHeight = currentContentHeight > 0 ? currentContentHeight : compactHeightPx();
            }
            applyDragHeight(dy);
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean wasDragging = dragging;
            dragging = false;
            dragStartRawY = -1f;
            if (wasDragging) {
                settle();
                return true;
            }
            return false;
        }
        return false;
    }

    private void applyDragHeight(float dy) {
        int full = maxContentHeight();
        // A finger going up (negative dy) grows the sheet and one going down shrinks it, so the
        // height is the drag's start minus how far the finger has travelled.
        int target = Math.round(dragStartHeight - dy);
        target = Math.max(compactHeightPx(), Math.min(full, target));
        setContentHeight(target);
    }

    private void settle() {
        int full = maxContentHeight();
        int compact = compactHeightPx();
        expanded = currentContentHeight > compact + (full - compact) * SETTLE_FRACTION;
        setContentHeight(expanded ? full : compact);
    }

    /** The compact height: the handle, the tab row and their margins. */
    private int compactHeightPx() {
        // Sum the same dp values the children were given, so the weighted body is exactly zero when
        // the sheet is compact even when the density is fractional.
        return AndroidUtilities.dp(HANDLE_TOP_MARGIN_DP)
                + AndroidUtilities.dp(HANDLE_HEIGHT_DP)
                + AndroidUtilities.dp(ROW_TOP_MARGIN_DP)
                + AndroidUtilities.dp(ROW_HEIGHT_DP)
                + AndroidUtilities.dp(ROW_BOTTOM_MARGIN_DP);
    }

    /** The tallest the content may become: the sheet's own host, minus its background padding. */
    private int maxContentHeight() {
        ViewGroup sheet = getSheetContainer();
        ViewGroup host = getContainer();
        int available = host == null ? 0 : host.getMeasuredHeight();
        if (available <= 0) {
            available = AndroidUtilities.displaySize.y;
        }
        int padding = sheet == null ? 0 : sheet.getPaddingTop() + sheet.getPaddingBottom();
        int compact = compactHeightPx();
        return Math.max(compact, Math.max(compact + 1, available - padding));
    }

    private void setContentHeight(int height) {
        currentContentHeight = height;
        applyContentHeight();
    }

    private void applyContentHeight() {
        ViewGroup.LayoutParams params = content.getLayoutParams();
        if (params == null || params.height == currentContentHeight) {
            return;
        }
        params.height = currentContentHeight;
        content.setLayoutParams(params);
        content.requestLayout();
    }

    // --------------------------------------------------------------- the row

    private class ItemsAdapter extends RecyclerListView.SelectionAdapter {

        private final String[] labels = {"图库", "文件", "富文本", "投票"};

        ItemsAdapter(Context context) {
        }

        Action actionAt(int position) {
            switch (position) {
                case 0:
                    return Action.GALLERY;
                case 1:
                    return Action.FILES;
                case 2:
                    return Action.RICH_EDITOR;
                default:
                    return Action.POLL;
            }
        }

        @Override
        public int getItemCount() {
            return labels.length;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            GlassTabView tab = GlassTabView.createAttachTab(parent.getContext(), resourcesProvider);
            tab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            tab.setFocusable(true);
            tab.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new RecyclerListView.Holder(tab);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            GlassTabView tab = (GlassTabView) holder.itemView;
            Action action = actionAt(position);
            tab.setText(labels[position]);
            switch (action) {
                case GALLERY:
                    tab.setTabAnimation(GlassTabView.TabAnimation.GALLERY);
                    break;
                case FILES:
                    tab.setTabAnimation(GlassTabView.TabAnimation.FILES);
                    break;
                case RICH_EDITOR:
                    tab.setTabAnimation(GlassTabView.TabAnimation.ARTICLE);
                    break;
                default:
                    tab.setTabAnimation(GlassTabView.TabAnimation.POLL);
                    break;
            }
            tab.setSelected(selectedAction == action, false);
            tab.setContentDescription(labels[position]);
        }
    }
}

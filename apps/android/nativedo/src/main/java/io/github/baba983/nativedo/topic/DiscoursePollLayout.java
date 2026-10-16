// SPDX-License-Identifier: GPL-2.0-or-later
// Port of org/telegram/ui/Components/ChatAttachAlertPollLayout.java: upstream's poll pane, bound to
// Discourse's own poll raw.

package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

/**
 * The poll pane of the real attachment sheet, filled with the site's own poll form.
 *
 * <p>Upstream's {@link org.telegram.ui.Components.ChatAttachAlertPollLayout} is a Telegram poll
 * composer: its rows offer quiz mode, anonymous answers, subscriber-only voters, a duration limit
 * and a solution, and its {@code updateRows} casts the alert's host fragment to
 * {@code ChatActivity} (ChatAttachAlertPollLayout:1213) before it can lay anything out. linux.do
 * has none of those options and no {@code ChatActivity}, so upstream's pane cannot be shown
 * unmodified.</p>
 *
 * <p>What this pane keeps from upstream's geometry, rather than a second hand-built sheet, is the
 * part the reader sees:</p>
 * <ul>
 *   <li>the pane is a {@link RecyclerListView} over the alert's own nested-scrolling container, so
 *       the sheet's half-height/full-height drag is the alert's, not this class's;</li>
 *   <li>the list opens with a padding row as its first item, so the pane starts at half height with
 *       the form pushed down (the same {@code availableHeight / 5 * 2} the alert's own panes use),
 *       and the reader scrolls that padding away to bring the pane to full height;</li>
 *   <li>{@code getCurrentItemTop()} reports the form's own position, so the alert's top bar arrives
 *       (back arrow and title) exactly as it does over the gallery and the files pane, and
 *       {@code needsActionBar() == 1} claims that bar;</li>
 *   <li>leaving the pane - the bar's back action or a touch outside - discards only after the
 *       reader confirms a poll they started, and otherwise closes the whole sheet to the composer,
 *       never an in-place return to the gallery.</li>
 * </ul>
 *
 * <p>The pane's content is the existing {@link DiscoursePollComposer.Form}: the question, the
 * option fields, the one setting the site reads (multiple answers) and the site's 20-option default
 * cap. Create writes the site's own {@code [poll]} raw and hands it to the sheet, which is already
 * where every other attachment raw goes. No Telegram poll message, no Telegram-only option is
 * drawn, and no Telegram account, RPC or database is reached.</p>
 */
public class DiscoursePollLayout extends ChatAttachAlert.AttachAlertLayout {

    /** Where the finished site raw goes: the sheet's own poll sink. */
    public interface Sink {
        void onPoll(@NonNull String raw);
    }

    // The list's three rows, in order. The padding row is the opening spacer upstream's poll pane
    // also has as its first item; the form is the pane's content; the filler keeps the list
    // scrollable so the padding can be scrolled away even though the site's form is short.
    private static final int ROW_PADDING = 0;
    private static final int ROW_FORM = 1;
    private static final int ROW_FILLER = 2;

    /** The alert's own manager, with upstream's poll height: the bar plus the status bar. */
    private static final int LIST_ADDITIONAL_HEIGHT = AndroidUtilities.dp(53 + 12);

    private final DiscoursePollComposer.Form form;
    private final RecyclerListView listView;
    private final FillLastLinearLayoutManager layoutManager;
    private final ListAdapter listAdapter;

    private int topPadding;
    private boolean ignoreLayout;

    public DiscoursePollLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider,
            @NonNull Sink sink) {
        super(alert, context, resourcesProvider);

        occupyStatusBar = true;
        occupyNavigationBar = true;
        setFocusable(true);
        setFocusableInTouchMode(true);
        // The pane itself is transparent: the alert draws the sheet's own rounded background, and
        // the pane's rows sit on it. Painting the pane would smear that background over the sheet's
        // rounded top while the pane still sits at half height.
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            setDefaultFocusHighlightEnabled(false);
        }

        form = DiscoursePollComposer.inline(context, resourcesProvider, sink::onPoll);

        listView = new RecyclerListView(context, resourcesProvider);
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(context,
                LinearLayoutManager.VERTICAL, false,
                LIST_ADDITIONAL_HEIGHT + AndroidUtilities.statusBarHeight, listView));
        // As in Telegram's poll pane, exclude the opening spacer from the last-row fill calculation.
        layoutManager.setSkipFirstItem();
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                // The alert reads the offset back to place the sheet and reveal its top bar.
                parentAlert.updateLayout(DiscoursePollLayout.this, true, dy);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Settle the form under the top bar instead of leaving a split row, the way
                    // upstream's poll pane does.
                    int offset = AndroidUtilities.dp(13);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        RecyclerListView.Holder holder =
                                (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(ROW_FORM);
                        if (holder != null) {
                            final int dy = holder.itemView.getTop() - AndroidUtilities.statusBarHeight
                                    - LIST_ADDITIONAL_HEIGHT;
                            if (dy > 0) {
                                listView.smoothScrollBy(0, dy);
                            }
                        }
                    }
                }
            }
        });
        addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
    }

    // ------------------------------------------------------------ AttachAlertLayout

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        if (parentAlert != null && parentAlert.actionBar != null) {
            parentAlert.actionBar.setTitle("投票");
        }
        parentAlert.updateDoneItemEnabled();
        // Open at half height: the padding row is the first item, so this puts it at the top and
        // leaves the form below it.
        layoutManager.scrollToPositionWithOffset(0, 0);
        parentAlert.setAllowNestedScroll(true);
    }

    @Override
    public void onHide() {
        // Leaving the pane must not leave the sheet unable to be dragged by the next tab.
        if (parentAlert != null) {
            parentAlert.setAllowNestedScroll(true);
        }
    }

    @Override
    public void onHidden() {
        if (parentAlert != null) {
            parentAlert.updateDoneItemEnabled();
        }
    }

    /** The pane's own Create action, so a host can drive the same validation from elsewhere. */
    public void submit() {
        form.submit();
    }

    /** Gives the question field focus, so opening the pane raises the keyboard onto it. */
    public void focusQuestion() {
        form.focusQuestion();
    }

    /**
     * The site's poll raw is written by the form's own Create, so the pane contributes no Telegram
     * selection and no Telegram done item; its Create lives inside the form.
     */
    @Override
    public int getSelectedItemsCount() {
        return 0;
    }

    @Override
    public boolean hasDoneItem() {
        return false;
    }

    /** The pane rides the alert's top bar, the way upstream's poll pane and the files pane do. */
    @Override
    public int needsActionBar() {
        return 1;
    }

    /**
     * The top bar appears once the form has come up under it. Upstream's poll pane uses the same
     * offset; it is deliberately larger than the base, so the bar is not shown while the pane is
     * still at half height.
     */
    @Override
    public int getButtonsHideOffset() {
        return AndroidUtilities.dp(70);
    }

    @Override
    public int getListTopPadding() {
        return topPadding;
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(17);
    }

    /**
     * The form's own distance below the top bar, upstream's poll geometry: the form sits under the
     * opening padding, and this falls to the alert's floor once the reader has scrolled the padding
     * away. The alert reads it to place the sheet and to bring the top bar in.
     *
     * <p>This searches for the form's row rather than fixing an index, because this pane's list has
     * three rows (padding, form, filler) where upstream's has one row per poll field; the first
     * content row is the form here, wherever the list has scrolled to.</p>
     */
    @Override
    public int getCurrentItemTop() {
        if (listView == null || listView.getChildCount() == 0) {
            return Integer.MAX_VALUE;
        }
        int top = 0;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
            if (holder != null && holder.getAdapterPosition() == ROW_FORM) {
                top = (int) child.getY() - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(8 + 12);
                break;
            }
        }
        return (top > 0 ? top : 0) + AndroidUtilities.dp(25);
    }

    /**
     * The opening padding and the list's lower reserve, upstream's poll shape: the pane starts at
     * about two fifths of the host's height, so the sheet opens at half height with the top bar
     * out of the way. The keyboard takes the padding down to the bar's own height so the focused
     * field is never covered, and the list reserves the tab capsule (and the navigation bar) at its
     * foot.
     *
     * <p>The upstream manager fills the final row while excluding the opening spacer, so even
     * the shorter site form can scroll up to the sheet's expanded position.</p>
     */
    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            padding = AndroidUtilities.dp(52);
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                padding = (int) (availableHeight / 3.5f);
            } else {
                padding = availableHeight / 5 * 2;
            }
            padding -= AndroidUtilities.dp(13);
            if (padding < 0) {
                padding = 0;
            }
            parentAlert.setAllowNestedScroll(true);
        }
        padding += AndroidUtilities.statusBarHeight;

        ignoreLayout = true;
        if (topPadding != padding || listView.getPaddingBottom() != listPaddingBottom) {
            topPadding = padding;
            listView.setPaddingWithoutRequestLayout(0, 0, 0, listPaddingBottom);
            listAdapter.notifyItemChanged(ROW_PADDING);
        }
        ignoreLayout = false;
    }

    @Override
    public void scrollToTop() {
        listView.smoothScrollToPosition(ROW_FORM);
    }

    @Override
    public boolean shouldHideBottomButtons() {
        // The tab capsule gives way to the top bar while the pane is drawn under it, the way
        // upstream's own poll pane does.
        return true;
    }

    /**
     * Back leaves the pane the way the reader expects: a poll they started is confirmed first, and
     * otherwise the whole sheet closes to the composer. It never simply returns to the gallery.
     */
    @Override
    public boolean onBackPressed() {
        if (!checkDiscard()) {
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public boolean onDismissWithTouchOutside() {
        if (!checkDiscard()) {
            return false;
        }
        return super.onDismissWithTouchOutside();
    }

    /**
     * Upstream's own dirty check: a poll that was never created is confirmed before it is dropped.
     * A clean pane (or a Create, which dismisses without asking) leaves nothing to lose, so this
     * never prompts for it.
     */
    private boolean checkDiscard() {
        if (!form.hasContent()) {
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(parentAlert.getContext());
        builder.setTitle("放弃投票");
        builder.setMessage("你填写的投票还没创建，确定放弃吗？");
        builder.setPositiveButton("放弃", (dialog, which) -> parentAlert.dismiss());
        builder.setNegativeButton("取消", null);
        builder.show();
        return false;
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    // ------------------------------------------------------------ the pane's own list

    /**
     * Three rows: the opening padding, the pane's own form, and a filler. Each row keeps one view
     * type, so the form's single view holder is never recreated and the reader keeps what they
     * typed while the pane is off screen.
     */
    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;
        private View formView;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case ROW_PADDING: {
                    view = new View(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), topPadding);
                        }
                    };
                    view.setTag(RecyclerListView.TAG_NOT_SECTION);
                    break;
                }
                case ROW_FILLER: {
                    view = new View(mContext);
                    view.setTag(RecyclerListView.TAG_NOT_SECTION);
                    break;
                }
                case ROW_FORM:
                default: {
                    if (formView == null) {
                        formView = form.getView();
                    }
                    view = formView;
                    break;
                }
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            // The rows hold their own state; nothing to bind.
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }
}

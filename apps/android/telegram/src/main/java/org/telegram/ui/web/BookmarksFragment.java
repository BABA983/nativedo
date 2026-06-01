package org.telegram.ui.web;

import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.web.AddressBarList.getLink;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Util;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class BookmarksFragment extends UniversalFragment {

    private static HashMap<Integer, AddressBarList.BookmarksList> cachedLists;
    public AddressBarList.BookmarksList list = new AddressBarList.BookmarksList(currentAccount, this::updateWithOffset);
    public AddressBarList.BookmarksList searchList;

    private final Runnable closeToTabs;
    private final Utilities.Callback<String> whenClicked;

    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem gotoItem;
    private String query;

    private NumberTextView selectedCount;
    public HashSet<Long> selected = new HashSet<>();

    public boolean isSelected(BrowserHistory.Entry entry) {
        return entry != null && selected.contains(entry.id);
    }

    public void setSelected(BrowserHistory.Entry entry, boolean select) {
        if (entry == null) return;
        if (select) {
            selected.add(entry.id);
        } else {
            selected.remove(entry.id);
        }
    }

    public void deleteSelectedMessages() {
        final HashSet<Long> ids = new HashSet<>(selected);
        new AlertDialog.Builder(getContext(), getResourceProvider())
            .setTitle(formatPluralString("DeleteOptionsTitle", ids.size()))
            .setMessage(getString(ids.size() == 1 ? "AreYouSureUnsaveSingleMessage" : "AreYouSureUnsaveFewMessages"))
            .setPositiveButton(getString(R.string.Delete), (di, w) -> {
                final ArrayList<Long> idList = new ArrayList<>(ids);
                WebBookmarks.getInstance(currentAccount).remove(idList);
                list.delete(idList);
                if (searchList != null) {
                    searchList.delete(idList);
                }
                selected.clear();
                actionBar.hideActionMode();
                listView.adapter.update(true);
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .show();
    }

    public void gotoMessage() {
        // NativeDo: the upstream "go to message" jumps to the Saved-Messages
        // ChatActivity, which is Telegram-only and has no local-bookmark analog.
        // Treated as a no-op so it cannot crash; the menu item is hidden below.
    }

    public BookmarksFragment(Runnable closeToTabs, Utilities.Callback<String> whenClicked) {
        super();
        this.closeToTabs = closeToTabs;
        this.whenClicked = whenClicked;
    }

    @Override
    public View createView(Context context) {
        fragmentView = super.createView(context);

        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setActionModeColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), true);
        actionBar.setCastShadows(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        actionBar.hideActionMode();
                        selected.clear();
                        AndroidUtilities.forEachViews(listView, child -> {
                            if (child instanceof AddressBarList.BookmarkView) {
                                ((AddressBarList.BookmarkView) child).setChecked(false);
                            }
                        });
                    } else {
                        finishFragment();
                    }
                } else if (id == R.id.menu_delete) {
                    deleteSelectedMessages();
                } else if (id == R.id.menu_link) {
                    gotoMessage();
                }
            }
        });

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedCount = new NumberTextView(actionMode.getContext());
        selectedCount.setTextSize(18);
        selectedCount.setTypeface(AndroidUtilities.bold());
        selectedCount.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        selectedCount.setOnTouchListener((v, event) -> true);
        actionMode.addView(selectedCount, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

        gotoItem = actionMode.addItemWithWidth(R.id.menu_link, R.drawable.msg_message, AndroidUtilities.dp(54), getString(R.string.AccDescrGoToMessage));
        // NativeDo: "go to message" targets the Telegram Saved-Messages chat,
        // which has no local-bookmark equivalent; hide it.
        gotoItem.setVisibility(View.GONE);
        actionMode.addItemWithWidth(R.id.menu_delete, R.drawable.msg_delete, AndroidUtilities.dp(54), getString(R.string.Delete));

        searchItem = actionBar.createMenu().addItem(0, R.drawable.outline_header_search, getResourceProvider()).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {}

            @Override
            public void onSearchCollapse() {
                query = null;
                AndroidUtilities.cancelRunOnUIThread(applySearch);
                if (searchList != null) {
                    searchList.detach();
                    searchList = null;
                }
                if (listView != null) {
                    listView.adapter.update(true);
                    listView.layoutManager.scrollToPositionWithOffset(0, 0);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                final boolean hadQuery = !TextUtils.isEmpty(query);
                final String query = editText.getText().toString();
                if (!TextUtils.equals(BookmarksFragment.this.query, query)) {
                    BookmarksFragment.this.query = query;
                    if (searchList != null) {
                        searchList.detach();
                    }
                    searchList = new AddressBarList.BookmarksList(currentAccount, query, BookmarksFragment.this::updateWithOffset);
                    searchList.attach();
                    scheduleSearch();
                }
                if (listView != null) {
                    listView.adapter.update(true);
                    if (hadQuery != !TextUtils.isEmpty(query)) {
                        listView.layoutManager.scrollToPositionWithOffset(0, 0);
                    }
                }
            }

            private void scheduleSearch() {
                AndroidUtilities.cancelRunOnUIThread(applySearch);
                AndroidUtilities.runOnUIThread(applySearch, 500);
            }

            private Runnable applySearch = () -> {
                if (searchList != null) {
                    searchList.load();
                }
            };
        });
        searchItem.setSearchFieldHint(getString(R.string.Search));
        searchItem.setContentDescription(getString(R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(getThemedColor(Theme.key_player_time));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));

        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!listView.canScrollVertically(1)) {
                    if (TextUtils.isEmpty(query)) {
                        list.load();
                    } else if (searchList != null) {
                        searchList.load();
                    }
                }
                if (listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(fragmentView);
                }
            }
        });

        StickerEmptyView emptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.title.setText(getString(R.string.WebNoBookmarks));
        emptyView.subtitle.setVisibility(View.GONE);
        emptyView.showProgress(false, false);
        emptyView.setAnimateLayoutChange(true);
        ((FrameLayout) fragmentView).addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setEmptyView(emptyView);

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        list.attach();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        list.detach();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.WebBookmarks);
    }

    private final HashSet<String> addedUrls = new HashSet<>();
    private int lastId;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        addedUrls.clear();
        if (TextUtils.isEmpty(query)) {
            for (BrowserHistory.Entry entry : list.links) {
                final String url = entry == null ? null : entry.url;
                if (TextUtils.isEmpty(url)) continue;
                if (url.startsWith("#") || url.startsWith("$") || url.startsWith("@")) continue;
                addedUrls.add(url);
                items.add(AddressBarList.BookmarkView.Factory.as(entry, null).setChecked(isSelected(entry)));
            }
            if (!list.endReached) {
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
            }
        } else {
            for (BrowserHistory.Entry entry : list.links) {
                final String url = entry == null ? null : entry.url;
                if (TextUtils.isEmpty(url)) continue;
                if (url.startsWith("#") || url.startsWith("$") || url.startsWith("@")) continue;
                if (!matchesEntry(entry, query)) continue;
                addedUrls.add(url);
                items.add(AddressBarList.BookmarkView.Factory.as(entry, query).setChecked(isSelected(entry)));
            }
            for (BrowserHistory.Entry entry : searchList.links) {
                final String url = entry == null ? null : entry.url;
                if (TextUtils.isEmpty(url)) continue;
                if (url.startsWith("#") || url.startsWith("$") || url.startsWith("@")) continue;
                if (addedUrls.contains(url)) continue;
                addedUrls.add(url);
                items.add(AddressBarList.BookmarkView.Factory.as(entry, query).setChecked(isSelected(entry)));
            }
            if (!searchList.endReached) {
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
            }
        }
        if (!items.isEmpty()) {
            items.add(UItem.asShadow(null));
        }
    }

    public static boolean matches(String src, String q) {
        if (src == null || q == null) return false;
        final String lsrc = src.toLowerCase();
        final String lq = q.toLowerCase();
        if (lsrc.startsWith(lq) || lsrc.contains(" " + lq) || lsrc.contains("." + lq)) return true;
        final String tsrc = AndroidUtilities.translitSafe(lsrc);
        final String tq = AndroidUtilities.translitSafe(lq);
        return (tsrc.startsWith(tq) || tsrc.contains(" " + tq) || tsrc.contains("." + tq));
    }

    private static boolean matchesEntry(BrowserHistory.Entry entry, String query) {
        if (entry == null) return false;
        final String title = entry.meta != null ? entry.meta.title : null;
        final String sitename = entry.meta != null ? entry.meta.sitename : null;
        final String domain = entry.meta != null ? entry.meta.domain : AndroidUtilities.getHostAuthority(entry.url, true);
        return matches(domain, query) || matches(sitename, query) || matches(title, query) || matches(entry.url, query);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.instanceOf(AddressBarList.BookmarkView.Factory.class)) {
            if (actionBar.isActionModeShowed()) {
                clickSelect(item, view);
            } else {
                finishFragment();
                whenClicked.run(((BrowserHistory.Entry) item.object2).url);
            }
        }
    }

    public void clickSelect(UItem item, View view) {
        AddressBarList.BookmarkView cell = (AddressBarList.BookmarkView) view;
        BrowserHistory.Entry entry = (BrowserHistory.Entry) item.object2;
        if (isSelected(entry)) {
            setSelected(entry, false);
            cell.setChecked(false);
        } else {
            setSelected(entry, true);
            cell.setChecked(true);
        }
        selectedCount.setNumber(selected.size(), true);
        if (selected.isEmpty()) {
            actionBar.hideActionMode();
        } else {
            actionBar.showActionMode();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.instanceOf(AddressBarList.BookmarkView.Factory.class)) {
            clickSelect(item, view);
            return true;
        }
        return false;
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_windowBackgroundWhite)) > .721f;
    }

    private void updateWithOffset() {
        int position = -1, offset = 0;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            position = listView.getChildAdapterPosition(child);
            if (position < 0) continue;
            offset = child.getTop();
            break;
        }
        listView.adapter.update(true);
        if (position >= 0) {
            listView.layoutManager.scrollToPositionWithOffset(position, offset);
        } else {
            listView.layoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

}

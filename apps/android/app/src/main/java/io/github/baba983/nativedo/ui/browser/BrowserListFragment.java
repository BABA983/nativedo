/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/web/HistoryFragment.java and org/telegram/ui/web/BookmarksFragment.java.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.browser;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

class BrowserListFragment extends UniversalFragment {

    private static final int MENU_SEARCH = 1;

    interface Delegate {
        void onEntryClicked(BrowserStorage.Entry entry);
    }

    enum Mode {
        HISTORY,
        BOOKMARKS
    }

    private final Mode mode;
    private final Delegate delegate;
    private ActionBarMenuItem searchMenuItem;
    private EditText searchEditText;
    private TextView emptyView;
    private String query = "";
    private boolean searchVisible;

    BrowserListFragment(Mode mode, Delegate delegate) {
        this.mode = mode;
        this.delegate = delegate;
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        configureSearch(context);

        emptyView = new TextView(context);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        emptyView.setVisibility(View.GONE);
        ((FrameLayout) view).addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        listView.adapter.update(false);
        return view;
    }

    @Override
    protected CharSequence getTitle() {
        Context context = getContext();
        return context == null ? "" : context.getString(mode == Mode.HISTORY ? R.string.app_browser_history : R.string.app_browser_bookmarks);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) {
            updateEmptyView(true);
            return;
        }
        ArrayList<BrowserStorage.Entry> entries = mode == Mode.HISTORY
                ? BrowserStorage.getHistory(context)
                : BrowserStorage.getBookmarks(context);
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        int lastDateKey = Integer.MIN_VALUE;
        boolean hasEntries = false;
        int start = mode == Mode.HISTORY ? entries.size() - 1 : 0;
        int end = mode == Mode.HISTORY ? -1 : entries.size();
        int step = mode == Mode.HISTORY ? -1 : 1;
        for (int i = start; i != end; i += step) {
            BrowserStorage.Entry entry = entries.get(i);
            if (!matches(entry, query)) {
                continue;
            }
            if (mode == Mode.HISTORY) {
                calendar.setTimeInMillis(entry.time);
                int dateKey = calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100 + calendar.get(Calendar.DAY_OF_MONTH);
                if (dateKey != lastDateKey) {
                    lastDateKey = dateKey;
                    items.add(UItem.asGraySection(DateFormat.getDateInstance(DateFormat.MEDIUM).format(entry.time)));
                }
            }
            items.add(UItem.asBookmark((int) entry.id, entry.displayTitle(), entry.displaySubtitle(), entry.favicon, mode == Mode.HISTORY, entry.time).setObject2(entry));
            hasEntries = true;
        }
        if (hasEntries) {
            items.add(UItem.asShadow(null));
        }
        updateEmptyView(!hasEntries);
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object2 instanceof BrowserStorage.Entry && delegate != null) {
            delegate.onEntryClicked((BrowserStorage.Entry) item.object2);
        } else {
            finishFragment();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite)) > .721f;
    }

    @Override
    public boolean onBackPressed() {
        if (searchVisible) {
            closeSearch();
            return false;
        }
        return super.onBackPressed();
    }

    private void configureSearch(Context context) {
        ActionBarMenu menu = actionBar.createMenu();
        searchMenuItem = menu.addItem(MENU_SEARCH, android.R.drawable.ic_menu_search);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (searchVisible) {
                        closeSearch();
                    } else {
                        finishFragment();
                    }
                } else if (id == MENU_SEARCH) {
                    toggleSearch();
                }
            }
        });

        searchMenuItem.setIsSearchField(true);
        searchMenuItem.setSearchFieldHint(mode == Mode.HISTORY
                ? context.getString(R.string.app_browser_history_search_placeholder)
                : context.getString(R.string.app_browser_bookmark_search_placeholder));
        searchEditText = searchMenuItem.getSearchField();
        searchEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        searchMenuItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searchVisible = true;
            }

            @Override
            public void onSearchCollapse() {
                searchVisible = false;
                query = "";
                listView.adapter.update(false);
            }

            @Override
            public void onTextChanged(EditText editText) {
                query = editText == null ? "" : editText.getText().toString().trim();
                listView.adapter.update(false);
            }
        });
    }

    private boolean matches(BrowserStorage.Entry entry, String query) {
        if (entry == null) {
            return false;
        }
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        return contains(entry.url, lower)
                || contains(entry.title, lower)
                || contains(entry.subtitle, lower);
    }

    private boolean contains(@Nullable String value, String query) {
        if (value == null || query == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (containsBoundary(lower, query)) {
            return true;
        }
        String translitValue = translitSafe(lower);
        String translitQuery = translitSafe(query);
        return !TextUtils.isEmpty(translitQuery) && containsBoundary(translitValue, translitQuery);
    }

    private boolean containsBoundary(String value, String query) {
        return value.startsWith(query) || value.contains(" " + query) || value.contains("." + query);
    }

    private String translitSafe(String value) {
        if (value == null) {
            return "";
        }
        try {
            return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                    .replaceAll("\\p{Mn}+", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void toggleSearch() {
        if (searchVisible) {
            closeSearch();
        } else {
            openSearch();
        }
    }

    private void openSearch() {
        query = "";
        listView.adapter.update(false);
        listView.layoutManager.scrollToPositionWithOffset(0, 0);
        searchMenuItem.openSearch(true);
    }

    private void closeSearch() {
        searchMenuItem.closeSearch(true);
        listView.layoutManager.scrollToPositionWithOffset(0, 0);
    }

    private void updateEmptyView(boolean empty) {
        if (emptyView == null || listView == null) {
            return;
        }
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (mode == Mode.HISTORY) {
            emptyView.setText(TextUtils.isEmpty(query)
                    ? R.string.app_browser_history_empty
                    : R.string.app_browser_history_empty_filtered);
        } else {
            emptyView.setText(TextUtils.isEmpty(query)
                    ? R.string.app_browser_bookmarks_empty
                    : R.string.app_browser_bookmarks_empty_filtered);
        }
    }
}

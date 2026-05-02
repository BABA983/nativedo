// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/ActionBar/ActionBarMenuItem.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: actionbar-menu, popup-menu
// HOST-WIRING:
//   - Popup background/resource lookups use NativeDo R and Theme.
// ADAPTED-BACKEND:
//   - None.
// ADAPTED-PRODUCT:
//   - Adds setSubItemVisible so NativeDo's simple persistent popup layout can mirror Telegram
//     WebActionBar's conditional ItemOptions rows.
//   - Keeps a minimal Telegram-shaped search-field surface for browser history/bookmarks without
//     porting filters, captions, Lottie, or animated ActionBar visibility interpolation.
// OMITTED:
//   - Telegram filters, Lottie, action mode, complex popup rows, and drag/reorder menu behavior.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/ActionBarMenuItem.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarMenuItem.java
 *
 * NativeDo milestone slice: keeps icon/text menu items, simple submenu items,
 * popup positioning, parent menu click forwarding, and a minimal search field;
 * omits Telegram filters, lottie, action mode, and complex popup rows.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.actionbar;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

public class ActionBarMenuItem extends FrameLayout {

    public static class ActionBarMenuItemSearchListener {
        public void onSearchExpand() {
        }

        public void onSearchCollapse() {
        }

        public void onTextChanged(EditText editText) {
        }

        public void onSearchPressed(EditText editText) {
        }
    }

    protected ImageView iconView;
    protected TextView textView;
    private final ActionBarMenu parentMenu;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    private ActionBarPopupWindow popupWindow;
    private int popupItemsColor;
    private int popupItemsSelectorColor;
    private boolean isSearchField;
    private FrameLayout searchContainer;
    private EditText searchField;
    private ActionBarMenuItemSearchListener searchListener;

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor) {
        this(context, menu, backgroundColor, iconColor, false);
    }

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor, boolean text) {
        super(context);
        parentMenu = menu;
        popupItemsColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem);
        popupItemsSelectorColor = Theme.getColor(Theme.key_listSelector);
        if (backgroundColor != 0) {
            setBackground(Theme.createSelectorDrawable(backgroundColor));
        }
        if (text) {
            textView = new TextView(context);
            textView.setGravity(Gravity.CENTER);
            textView.setSingleLine(true);
            textView.setTextColor(iconColor);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        } else {
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            if (iconColor != 0) {
                iconView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            }
            addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
    }

    public TextView addSubItem(int id, CharSequence text) {
        createPopupLayout();
        TextView textView = new TextView(getContext());
        textView.setTextColor(popupItemsColor);
        textView.setBackground(Theme.createSelectorDrawable(popupItemsSelectorColor, Theme.RIPPLE_MASK_ALL));
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setMinWidth(AndroidUtilities.dp(196));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTag(id);
        textView.setText(text);
        popupLayout.addView(textView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48)));
        textView.setOnClickListener(view -> {
            if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
            if (parentMenu != null) {
                parentMenu.onItemClick((Integer) view.getTag());
            }
        });
        return textView;
    }

    public boolean hasSubMenu() {
        return popupLayout != null && popupLayout.getChildCount() > 0;
    }

    public ActionBarMenuItem setIsSearchField(boolean value) {
        isSearchField = value;
        return this;
    }

    public ActionBarMenuItem setActionBarMenuItemSearchListener(ActionBarMenuItemSearchListener listener) {
        searchListener = listener;
        return this;
    }

    public EditText getSearchField() {
        checkCreateSearchField();
        return searchField;
    }

    public void setSearchFieldHint(CharSequence hint) {
        checkCreateSearchField();
        if (searchField != null) {
            searchField.setHint(hint);
        }
        setContentDescription(hint);
    }

    public boolean isSearchFieldVisible() {
        return searchContainer != null && searchContainer.getVisibility() == VISIBLE;
    }

    public void openSearch(boolean openKeyboard) {
        checkCreateSearchField();
        if (searchContainer == null || searchContainer.getVisibility() == VISIBLE) {
            return;
        }
        setSearchLayoutExpanded(true);
        searchContainer.setVisibility(VISIBLE);
        if (iconView != null) {
            iconView.setVisibility(GONE);
        }
        searchField.setText("");
        searchField.requestFocus();
        if (openKeyboard) {
            InputMethodManager inputMethodManager = searchField.getContext().getSystemService(InputMethodManager.class);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
            }
        }
        if (searchListener != null) {
            searchListener.onSearchExpand();
        }
    }

    public void closeSearch(boolean hideKeyboard) {
        if (searchContainer == null || searchContainer.getVisibility() != VISIBLE) {
            return;
        }
        if (hideKeyboard) {
            AndroidUtilities.hideKeyboard(searchField);
        }
        searchField.clearFocus();
        searchField.setText("");
        searchContainer.setVisibility(GONE);
        if (iconView != null) {
            iconView.setVisibility(VISIBLE);
        }
        setSearchLayoutExpanded(false);
        if (searchListener != null) {
            searchListener.onSearchCollapse();
        }
    }

    public void toggleSubMenu() {
        if (!hasSubMenu()) {
            return;
        }
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
            return;
        }
        if (popupLayout.getParent() instanceof ViewGroup) {
            ((ViewGroup) popupLayout.getParent()).removeView(popupLayout);
        }
        updatePopupBackground();
        popupLayout.measure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST)
        );
        popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.setFocusable(true);
        int xOffset;
        int yOffset;
        View anchor;
        if (parentMenu != null) {
            anchor = parentMenu.parentActionBar;
            xOffset = getLeft() + parentMenu.getLeft() + getMeasuredWidth() - popupLayout.getMeasuredWidth();
            yOffset = -parentMenu.parentActionBar.getMeasuredHeight() + parentMenu.getTop() + parentMenu.getPaddingTop();
        } else {
            anchor = this;
            xOffset = getMeasuredWidth() - popupLayout.getMeasuredWidth();
            yOffset = 0;
        }
        popupWindow.showAsDropDown(anchor, xOffset, yOffset);
        popupWindow.startAnimation();
    }

    public void closeSubMenu() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public void setSubItemText(int id, CharSequence text) {
        if (popupLayout == null) {
            return;
        }
        View view = popupLayout.findViewWithTag(id);
        if (view instanceof TextView) {
            ((TextView) view).setText(text);
        }
    }

    public void setSubItemVisible(int id, boolean visible) {
        if (popupLayout == null) {
            return;
        }
        View view = popupLayout.findViewWithTag(id);
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
            view.setEnabled(visible);
        }
    }

    public void setSubItemEnabled(int id, boolean enabled) {
        if (popupLayout == null) {
            return;
        }
        View view = popupLayout.findViewWithTag(id);
        if (view != null) {
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    public void setIconColor(int color) {
        if (iconView != null) {
            iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
        if (textView != null) {
            textView.setTextColor(color);
        }
    }

    public void setPopupItemsColor(int color, boolean icon) {
        popupItemsColor = color;
        if (popupLayout == null) {
            return;
        }
        int count = popupLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            View view = popupLayout.getChildAt(i);
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(color);
            }
        }
    }

    public void setPopupItemsSelectorColor(int color) {
        popupItemsSelectorColor = color;
        if (popupLayout == null) {
            return;
        }
        int count = popupLayout.getChildCount();
        for (int i = 0; i < count; i++) {
            popupLayout.getChildAt(i).setBackground(Theme.createSelectorDrawable(color, Theme.RIPPLE_MASK_ALL));
        }
    }

    public void redrawPopup(int color) {
        updatePopupBackground();
    }

    public View getIconView() {
        return iconView;
    }

    private void checkCreateSearchField() {
        if (!isSearchField || searchContainer != null) {
            return;
        }
        searchContainer = new FrameLayout(getContext());
        searchContainer.setVisibility(GONE);
        searchContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        searchField = new EditText(getContext());
        searchField.setSingleLine(true);
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchField.setBackground(null);
        searchField.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(16), 0);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (searchListener != null) {
                    searchListener.onTextChanged(searchField);
                }
            }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (searchListener != null) {
                searchListener.onSearchPressed(searchField);
            }
            return false;
        });
        searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
        addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
    }

    private void setSearchLayoutExpanded(boolean expanded) {
        if (parentMenu == null) {
            return;
        }
        ViewGroup.LayoutParams itemLayoutParams = getLayoutParams();
        if (itemLayoutParams instanceof LinearLayout.LayoutParams) {
            itemLayoutParams.width = expanded ? getSearchExpandedWidth() : AndroidUtilities.dp(48);
            setLayoutParams(itemLayoutParams);
        }
        ViewGroup.LayoutParams menuLayoutParams = parentMenu.getLayoutParams();
        if (menuLayoutParams != null) {
            menuLayoutParams.width = expanded ? getSearchExpandedWidth() : LayoutHelper.WRAP_CONTENT;
            parentMenu.setLayoutParams(menuLayoutParams);
        }
        if (parentMenu.parentActionBar != null) {
            parentMenu.parentActionBar.requestLayout();
        }
    }

    private int getSearchExpandedWidth() {
        int width = parentMenu != null && parentMenu.parentActionBar != null ? parentMenu.parentActionBar.getMeasuredWidth() : 0;
        if (width <= 0) {
            width = AndroidUtilities.displaySize.x;
        }
        return Math.max(AndroidUtilities.dp(48), width - AndroidUtilities.dp(66));
    }

    private void createPopupLayout() {
        if (popupLayout != null) {
            return;
        }
        popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
        updatePopupBackground();
    }

    private void updatePopupBackground() {
        if (popupLayout == null) {
            return;
        }
        Drawable background = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert4);
        if (background != null) {
            background = background.mutate();
            background.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
            background.setAlpha(255);
            popupLayout.setBackgroundDrawable(background);
        }
    }
}

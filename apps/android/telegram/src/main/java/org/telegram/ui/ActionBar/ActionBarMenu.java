/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;

public class ActionBarMenu extends LinearLayout {

    protected ActionBar parentActionBar;

    public ActionBarMenu(Context context, ActionBar layer) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        parentActionBar = layer;
    }

    public ActionBarMenuItem addItem(int id, Drawable drawable) {
        return addItem(id, 0, null, parentActionBar.getItemsBackgroundColor(this), drawable, AndroidUtilities.dp(48), null);
    }

    public ActionBarMenuItem addItem(int id, int icon) {
        return addItem(id, icon, null, parentActionBar.getItemsBackgroundColor(this), null, AndroidUtilities.dp(48), null);
    }

    public ActionBarMenuItem addItem(int id, int icon, org.telegram.ui.ActionBar.Theme.ResourcesProvider resourcesProvider) {
        return addItem(id, icon, null, parentActionBar.getItemsBackgroundColor(this), null, AndroidUtilities.dp(48), null);
    }

    public ActionBarMenuItem addItemWithWidth(int id, int icon, int width, CharSequence title) {
        return addItem(id, icon, null, parentActionBar.getItemsBackgroundColor(this), null, width, title);
    }

    public ActionBarMenuItem addItem(int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title) {
        ActionBarMenuItem menuItem = new ActionBarMenuItem(getContext(), this, backgroundColor, parentActionBar.getItemsColor(this), text != null);
        menuItem.setTag(id);
        if (text != null) {
            menuItem.textView.setText(text);
            addView(menuItem, new LinearLayout.LayoutParams(width != 0 ? width : ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            if (drawable != null) {
                menuItem.iconView.setImageDrawable(drawable);
            } else if (icon != 0) {
                menuItem.iconView.setImageResource(icon);
            }
            addView(menuItem, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        menuItem.setOnClickListener(view -> {
            ActionBarMenuItem item = (ActionBarMenuItem) view;
            if (item.hasSubMenu()) {
                item.toggleSubMenu();
            } else if (item.isSearchField()) {
                item.openSearch(true);
            } else {
                onItemClick((Integer) view.getTag());
            }
        });
        if (title != null) {
            menuItem.setContentDescription(title);
        }
        return menuItem;
    }

    public void onItemClick(int id) {
        ActionBar.ActionBarMenuOnItemClick listener = parentActionBar.getActionBarMenuOnItemClick();
        if (listener != null) {
            listener.onItemClick(id);
        }
    }

    public void closeSearchField(boolean closeKeyboard) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.isSearchField() && item.isSearchFieldVisible()) {
                    parentActionBar.onSearchFieldVisibilityChanged(false);
                    item.toggleSearch(closeKeyboard);
                    break;
                }
            }
        }
    }

    public int getItemsMeasuredWidth(boolean ignoreAlpha) {
        int width = 0;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (!ignoreAlpha && (view.getAlpha() == 0 || view.getVisibility() != VISIBLE)) {
                continue;
            }
            if (view instanceof ActionBarMenuItem) {
                width += view.getMeasuredWidth();
            }
        }
        return width;
    }

    public boolean searchFieldVisible() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem && ((ActionBarMenuItem) view).isSearchFieldVisible()) {
                return true;
            }
        }
        return false;
    }

    public void translateXItems(float offset) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setTransitionOffset(offset);
            }
        }
    }

    public void addView(View view, LinearLayout.LayoutParams params) {
        super.addView(view, params);
    }

    public void hideAllPopupMenus() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).closeSubMenu();
            }
        }
    }

    public ActionBarMenuItem getItem(int id) {
        View view = findViewWithTag(id);
        return view instanceof ActionBarMenuItem ? (ActionBarMenuItem) view : null;
    }

    protected void updateItemsBackgroundColor() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                view.setBackground(Theme.createSelectorDrawable(parentActionBar.getItemsBackgroundColor(this)));
            }
        }
    }

    protected void updateItemsColor() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setIconColor(parentActionBar.getItemsColor(this));
            }
        }
    }

    public void setPopupItemsColor(int color, boolean icon) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setPopupItemsColor(color, icon);
            }
        }
    }

    public void setPopupItemsSelectorColor(int color) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setPopupItemsSelectorColor(color);
            }
        }
    }

    public void redrawPopup(int color) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).redrawPopup(color);
            }
        }
    }
}

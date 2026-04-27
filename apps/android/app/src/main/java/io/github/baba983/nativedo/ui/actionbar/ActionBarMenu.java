// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/ActionBar/ActionBarMenu.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: actionbar-menu=L1
// HOST-WIRING:
//   - Parent ActionBar click dispatch wired to NativeDo ActionBar.
// OMITTED:
//   - Telegram search, filters, lazy items, action mode, and advanced menu surfaces.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/ActionBarMenu.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarMenu.java
 *
 * NativeDo milestone slice: keeps horizontal menu item creation, submenu toggling,
 * and parent ActionBar click dispatch; omits Telegram search, filters, lazy items,
 * and action mode.
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
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import io.github.baba983.nativedo.core.AndroidUtilities;

public class ActionBarMenu extends LinearLayout {

    protected ActionBar parentActionBar;

    public ActionBarMenu(Context context, ActionBar layer) {
        super(context);
        setOrientation(LinearLayout.HORIZONTAL);
        parentActionBar = layer;
    }

    public ActionBarMenuItem addItem(int id, Drawable drawable) {
        return addItem(id, 0, null, parentActionBar.itemsBackgroundColor, drawable, AndroidUtilities.dp(48), null);
    }

    public ActionBarMenuItem addItem(int id, int icon) {
        return addItem(id, icon, null, parentActionBar.itemsBackgroundColor, null, AndroidUtilities.dp(48), null);
    }

    public ActionBarMenuItem addItemWithWidth(int id, int icon, int width, CharSequence title) {
        return addItem(id, icon, null, parentActionBar.itemsBackgroundColor, null, width, title);
    }

    public ActionBarMenuItem addItem(int id, int icon, CharSequence text, int backgroundColor, Drawable drawable, int width, CharSequence title) {
        ActionBarMenuItem menuItem = new ActionBarMenuItem(getContext(), this, backgroundColor, parentActionBar.itemsColor, text != null);
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
                view.setBackground(Theme.createSelectorDrawable(parentActionBar.itemsBackgroundColor));
            }
        }
    }

    protected void updateItemsColor() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View view = getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).setIconColor(parentActionBar.itemsColor);
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

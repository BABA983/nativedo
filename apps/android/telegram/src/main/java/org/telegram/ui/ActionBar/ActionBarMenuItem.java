/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Components.CloseProgressDrawable2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

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
    private int popupBackgroundColor;
    private int menuXOffset;
    private int menuYOffset;
    private boolean isSearchField;
    private FrameLayout searchContainer;
    private EditTextBoldCursor searchField;
    private ImageView clearButton;
    private ActionBarMenuItemSearchListener searchListener;
    private AnimatorSet searchContainerAnimator;
    private float transitionOffset;

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor) {
        this(context, menu, backgroundColor, iconColor, false);
    }

    public ActionBarMenuItem(Context context, ActionBarMenu menu, int backgroundColor, int iconColor, boolean text) {
        super(context);
        parentMenu = menu;
        popupItemsColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem);
        popupItemsSelectorColor = Theme.getColor(Theme.key_listSelector);
        popupBackgroundColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
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

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX + transitionOffset);
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

    public ActionBarMenuSubItem addSubItem(int id, Drawable iconDrawable, CharSequence text) {
        createPopupLayout();
        ActionBarMenuSubItem item = new ActionBarMenuSubItem(getContext(), false, false, null);
        item.setTag(id);
        item.setTextAndIcon(text, 0, iconDrawable);
        popupLayout.addView(item, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48)));
        item.setOnClickListener(view -> {
            if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
            if (parentMenu != null) {
                parentMenu.onItemClick((Integer) view.getTag());
            }
        });
        return item;
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

    public EditTextBoldCursor getSearchField() {
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

    public boolean isSearchField() {
        return isSearchField;
    }

    public void openSearch(boolean openKeyboard) {
        checkCreateSearchField();
        if (searchContainer == null || searchContainer.getVisibility() == VISIBLE || parentMenu == null) {
            return;
        }
        parentMenu.parentActionBar.onSearchFieldVisibilityChanged(toggleSearch(openKeyboard));
    }

    public void closeSearch(boolean hideKeyboard) {
        if (searchContainer == null || searchContainer.getVisibility() != VISIBLE) {
            return;
        }
        if (parentMenu != null && parentMenu.parentActionBar != null) {
            parentMenu.parentActionBar.onSearchFieldVisibilityChanged(false);
        }
        toggleSearch(hideKeyboard);
    }

    public boolean toggleSearch(boolean openKeyboard) {
        checkCreateSearchField();
        if (searchContainer == null || parentMenu == null) {
            return false;
        }
        ArrayList<View> menuIcons = new ArrayList<>();
        for (int i = 0; i < parentMenu.getChildCount(); i++) {
            View view = parentMenu.getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                View icon = ((ActionBarMenuItem) view).getIconView();
                if (icon != null) {
                    menuIcons.add(icon);
                }
            }
        }

        if (searchContainer.getTag() != null) {
            searchContainer.setTag(null);
            if (searchContainerAnimator != null) {
                searchContainerAnimator.removeAllListeners();
                searchContainerAnimator.cancel();
            }
            searchContainerAnimator = new AnimatorSet();
            searchContainerAnimator.playTogether(ObjectAnimator.ofFloat(searchContainer, View.ALPHA, searchContainer.getAlpha(), 0f));
            for (int i = 0; i < menuIcons.size(); i++) {
                menuIcons.get(i).setAlpha(0f);
                searchContainerAnimator.playTogether(ObjectAnimator.ofFloat(menuIcons.get(i), View.ALPHA, menuIcons.get(i).getAlpha(), 1f));
            }
            searchContainerAnimator.setDuration(150);
            searchContainerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    searchContainer.setAlpha(0f);
                    for (int i = 0; i < menuIcons.size(); i++) {
                        menuIcons.get(i).setAlpha(1f);
                    }
                    searchContainer.setVisibility(GONE);
                }
            });
            searchContainerAnimator.start();

            searchField.clearFocus();
            updateClearButton(false);
            setVisibility(VISIBLE);
            if (searchListener != null) {
                searchListener.onSearchCollapse();
            }
            if (openKeyboard) {
                AndroidUtilities.hideKeyboard(searchField);
            }
            parentMenu.requestLayout();
            requestLayout();
            return false;
        } else {
            searchContainer.setVisibility(VISIBLE);
            searchContainer.setAlpha(0f);
            if (searchContainerAnimator != null) {
                searchContainerAnimator.removeAllListeners();
                searchContainerAnimator.cancel();
            }
            searchContainerAnimator = new AnimatorSet();
            searchContainerAnimator.playTogether(ObjectAnimator.ofFloat(searchContainer, View.ALPHA, searchContainer.getAlpha(), 1f));
            for (int i = 0; i < menuIcons.size(); i++) {
                searchContainerAnimator.playTogether(ObjectAnimator.ofFloat(menuIcons.get(i), View.ALPHA, menuIcons.get(i).getAlpha(), 0f));
            }
            searchContainerAnimator.setDuration(150);
            searchContainerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    searchContainer.setAlpha(1f);
                    for (int i = 0; i < menuIcons.size(); i++) {
                        menuIcons.get(i).setAlpha(0f);
                    }
                }
            });
            searchContainerAnimator.start();
            setVisibility(GONE);
            searchField.setText("");
            updateClearButton(false);
            searchField.requestFocus();
            if (openKeyboard) {
                AndroidUtilities.showKeyboard(searchField);
            }
            searchContainer.setTag(1);
            if (searchListener != null) {
                searchListener.onSearchExpand();
            }
            return true;
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
        popupLayout.scrollToTop();
        popupLayout.measure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(40), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, MeasureSpec.AT_MOST)
        );
        popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setAnimationStyle(0);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.setFocusable(true);
        int xOffset;
        int yOffset;
        View anchor;
        if (parentMenu != null) {
            anchor = parentMenu.parentActionBar;
            xOffset = getLeft() + parentMenu.getLeft() + getMeasuredWidth() - popupLayout.getMeasuredWidth() + (int) getTranslationX() + menuXOffset;
            yOffset = -parentMenu.parentActionBar.getMeasuredHeight() + parentMenu.getTop() + parentMenu.getPaddingTop() + menuYOffset;
        } else {
            anchor = this;
            xOffset = getMeasuredWidth() - popupLayout.getMeasuredWidth() + menuXOffset;
            yOffset = menuYOffset;
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

    public void hideSubItem(int id) {
        setSubItemVisible(id, false);
    }

    public void showSubItem(int id) {
        setSubItemVisible(id, true);
    }

    public void showSubItem(int id, boolean animated) {
        setSubItemVisible(id, true);
    }

    public boolean hasSubItem(int id) {
        if (popupLayout == null) return false;
        return popupLayout.findViewWithTag(id) != null;
    }

    public ActionBarPopupWindow.ActionBarPopupWindowLayout getPopupLayout() {
        return popupLayout;
    }

    public void setSubItemShown(int id, boolean visible) {
        setSubItemVisible(id, visible);
    }

    public void checkHideMenuItem() {
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
        updateSearchFieldColors();
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
            } else if (view instanceof ActionBarMenuSubItem) {
                ActionBarMenuSubItem item = (ActionBarMenuSubItem) view;
                if (icon) {
                    item.setIconColor(color);
                } else {
                    item.setTextColor(color);
                }
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
            View child = popupLayout.getChildAt(i);
            if (child instanceof ActionBarMenuSubItem) {
                ((ActionBarMenuSubItem) child).setSelectorColor(color);
            } else {
                child.setBackground(Theme.createSelectorDrawable(color, Theme.RIPPLE_MASK_ALL));
            }
        }
    }

    public void setMenuXOffset(int offset) {
        menuXOffset = offset;
    }

    public void setMenuYOffset(int offset) {
        menuYOffset = offset;
    }

    public void redrawPopup(int color) {
        popupBackgroundColor = color;
        updatePopupBackground();
    }

    public ImageView getIconView() {
        return iconView;
    }

    public FrameLayout getSearchContainer() {
        return searchContainer;
    }

    public void setTransitionOffset(float offset) {
        transitionOffset = offset;
        setTranslationX(0);
    }

    /** Minimal Telegram search filter chip used by FragmentSearchField. */
    public static class SearchFilterView extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;
        private final ImageView iconView;
        private final TextView textView;
        private FiltersView.MediaFilterData filter;
        private boolean selectedForDelete;
        private boolean expanded;

        public SearchFilterView(Context context, Theme.ResourcesProvider resourcesProvider, boolean removable) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);
            setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL | Gravity.LEFT));

            textView = new TextView(context);
            textView.setSingleLine(true);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 26, 0, 0, 0));
            updateColors();
        }

        public void setData(FiltersView.MediaFilterData filter) {
            this.filter = filter;
            textView.setText(filter != null ? filter.getTitle() : null);
            if (filter != null && filter.iconResFilled != 0) {
                iconView.setImageResource(filter.iconResFilled);
                iconView.setVisibility(VISIBLE);
            } else {
                iconView.setVisibility(GONE);
            }
            requestLayout();
        }

        public FiltersView.MediaFilterData getFilter() {
            return filter;
        }

        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
            selectedForDelete = false;
            updateColors();
        }

        public boolean isSelectedForDelete() {
            return selectedForDelete;
        }

        public void setSelectedForDelete(boolean selectedForDelete) {
            this.selectedForDelete = selectedForDelete;
            updateColors();
        }

        public void updateColors() {
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            int selectorColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);
            textView.setTextColor(textColor);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(textColor, 0.65f), PorterDuff.Mode.MULTIPLY));
            int backgroundColor = Theme.multAlpha(textColor, selectedForDelete ? 0.16f : expanded ? 0.12f : 0.08f);
            setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16), backgroundColor, selectorColor, backgroundColor));
        }
    }

    /** Reaction-specific chip shell; NativeDo does not render reaction artwork yet. */
    public static class ReactionFilterView extends SearchFilterView {
        public ReactionFilterView(Context context, Theme.ResourcesProvider resourcesProvider, boolean removable) {
            super(context, resourcesProvider, removable);
        }
    }

    private void checkCreateSearchField() {
        if (!isSearchField || searchContainer != null) {
            return;
        }
        searchContainer = new FrameLayout(getContext());
        searchContainer.setVisibility(GONE);
        searchContainer.setClipChildren(false);
        searchField = new EditTextBoldCursor(getContext());
        searchField.setSingleLine(true);
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        searchField.setBackground(null);
        searchField.setPadding(0, 0, 0, 0);
        searchField.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_ACTION_SEARCH);
        searchField.setInputType(searchField.getInputType() | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        updateSearchFieldColors();
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
                updateClearButton(true);
            }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (searchListener != null && (actionId == EditorInfo.IME_ACTION_SEARCH ||
                event != null && event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                searchListener.onSearchPressed(searchField);
            }
            return false;
        });
        searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL | Gravity.START, 6, 0, 48, 0));
        clearButton = new ImageView(getContext());
        clearButton.setImageDrawable(new CloseProgressDrawable2() {
            @Override
            protected int getCurrentColor() {
                return parentMenu != null && parentMenu.parentActionBar != null
                    ? parentMenu.parentActionBar.itemsColor
                    : Theme.getColor(Theme.key_actionBarDefaultIcon);
            }
        });
        clearButton.setBackground(Theme.createSelectorDrawable(
            parentMenu != null && parentMenu.parentActionBar != null
                ? parentMenu.parentActionBar.itemsBackgroundColor
                : Theme.getColor(Theme.key_actionBarDefaultSelector),
            Theme.RIPPLE_MASK_CIRCLE_20DP));
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setVisibility(INVISIBLE);
        clearButton.setAlpha(0f);
        clearButton.setRotation(45f);
        clearButton.setScaleX(0f);
        clearButton.setScaleY(0f);
        clearButton.setOnClickListener(v -> {
            searchField.setText("");
            searchField.requestFocus();
            InputMethodManager inputMethodManager = searchField.getContext().getSystemService(InputMethodManager.class);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        searchContainer.addView(clearButton, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        if (parentMenu != null) {
            parentMenu.addView(searchContainer, 0, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 6, 0, 0, 0));
        }
    }

    private void updateSearchFieldColors() {
        if (searchField == null) {
            return;
        }
        searchField.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        searchField.setHintTextColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
    }

    private void updateClearButton(boolean animated) {
        if (clearButton == null || searchField == null) {
            return;
        }
        boolean visible = !TextUtils.isEmpty(searchField.getText());
        if (visible == (clearButton.getTag() != null)) {
            return;
        }
        clearButton.setTag(visible ? 1 : null);
        clearButton.animate().cancel();
        if (visible) {
            clearButton.setVisibility(VISIBLE);
        }
        float alpha = visible ? 1f : 0f;
        float rotation = visible ? 0f : 45f;
        float scale = visible ? 1f : 0f;
        if (animated) {
            clearButton.animate()
                .alpha(alpha)
                .rotation(rotation)
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(180)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!visible) {
                        clearButton.setVisibility(INVISIBLE);
                    }
                })
                .start();
        } else {
            clearButton.setAlpha(alpha);
            clearButton.setRotation(rotation);
            clearButton.setScaleX(scale);
            clearButton.setScaleY(scale);
            clearButton.setVisibility(visible ? VISIBLE : INVISIBLE);
        }
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
            background.setColorFilter(new PorterDuffColorFilter(popupBackgroundColor, PorterDuff.Mode.MULTIPLY));
            background.setAlpha(255);
            popupLayout.setBackgroundDrawable(background);
        }
    }
}

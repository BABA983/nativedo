package org.telegram.ui.Stories;

import android.graphics.Color;
import android.util.SparseIntArray;

import org.telegram.ui.ActionBar.Theme;

public class DarkThemeResourceProvider implements Theme.ResourcesProvider {
    private final SparseIntArray colors = new SparseIntArray();

    public DarkThemeResourceProvider() {
        colors.put(Theme.key_windowBackgroundWhite, 0xff1c1c1e);
        colors.put(Theme.key_windowBackgroundWhiteBlackText, Color.WHITE);
        colors.put(Theme.key_windowBackgroundWhiteGrayText, 0xff9b9b9f);
        colors.put(Theme.key_windowBackgroundWhiteBlueText, 0xff64b5ef);
        colors.put(Theme.key_listSelector, 0x2bffffff);
        colors.put(Theme.key_dialogBackground, 0xff1c1c1e);
        colors.put(Theme.key_dialogTextBlack, Color.WHITE);
        colors.put(Theme.key_dialogTextGray, 0xff9b9b9f);
        colors.put(Theme.key_dialogTextGray2, 0xff9b9b9f);
        colors.put(Theme.key_divider, 0x33ffffff);
    }

    @Override
    public int getColor(int key) {
        int index = colors.indexOfKey(key);
        return index >= 0 ? colors.valueAt(index) : Theme.getColor(key);
    }

    public int getColorOrDefault(int key) {
        return getColor(key);
    }

    @Override
    public boolean isDark() {
        return true;
    }
}

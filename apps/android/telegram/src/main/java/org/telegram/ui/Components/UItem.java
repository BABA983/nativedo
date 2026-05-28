package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class UItem {

    public static final int MAX_SPAN_COUNT = -1;

    public final int viewType;
    public int id;
    public boolean checked;
    public boolean accent;
    public boolean red;
    public boolean hideDivider;
    public int iconResId;
    public CharSequence text;
    @Nullable public CharSequence subtext;
    @Nullable public CharSequence textValue;
    @Nullable public View view;
    @Nullable public Object object;
    @Nullable public Object object2;
    public long longValue;
    public int spanCount = MAX_SPAN_COUNT;
    @Nullable public ViewFactory factory;

    public UItem(int viewType) {
        this.viewType = viewType;
    }

    public interface ViewFactory {
        int getViewType();

        View createView(Context context);

        void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter);

        default boolean isEnabled(UItem item) {
            return true;
        }
    }

    public UItem setChecked(boolean checked) {
        this.checked = checked;
        return this;
    }

    public UItem accent() {
        accent = true;
        return this;
    }

    public UItem red() {
        red = true;
        return this;
    }

    public UItem hideDivider() {
        hideDivider = true;
        return this;
    }

    public UItem setObject(@Nullable Object object) {
        this.object = object;
        return this;
    }

    public UItem setObject2(@Nullable Object object2) {
        this.object2 = object2;
        return this;
    }

    public static UItem asHeader(CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_HEADER);
        item.text = text;
        return item;
    }

    public static UItem asGraySection(CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_GRAY_SECTION);
        item.text = text;
        return item;
    }

    public static UItem asShadow(@Nullable CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SHADOW);
        item.text = text;
        return item;
    }

    public static UItem asButton(int id, int iconResId, CharSequence text) {
        return asButton(id, iconResId, text, null);
    }

    public static UItem asButton(int id, int iconResId, CharSequence text, @Nullable CharSequence value) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_TEXT);
        item.id = id;
        item.iconResId = iconResId;
        item.text = text;
        item.textValue = value;
        return item;
    }

    public static UItem asRadio(int id, CharSequence text, boolean checked) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_RADIO);
        item.id = id;
        item.text = text;
        item.checked = checked;
        return item;
    }

    public static UItem asCheck(int id, CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_CHECK);
        item.id = id;
        item.text = text;
        return item;
    }

    public static UItem asBookmark(int id, CharSequence title, CharSequence subtitle, @Nullable Bitmap favicon, boolean history, long time) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_BOOKMARK);
        item.id = id;
        item.text = title;
        item.subtext = subtitle;
        item.object = favicon;
        item.checked = history;
        item.longValue = time;
        return item;
    }

    public static UItem asWebsite(int id, CharSequence domain) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_WEBSITE);
        item.id = id;
        item.text = domain;
        return item;
    }

    public static UItem asWebsite(int id, CharSequence title, ArrayList<String> domains, @Nullable Bitmap favicon) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_WEBSITE);
        item.id = id;
        item.text = title;
        item.object = favicon;
        item.object2 = domains;
        item.textValue = domains.isEmpty() ? "" : domains.get(0);
        return item;
    }

    public static UItem asSetting(int id, int iconResId, CharSequence title, @Nullable CharSequence subtitle, @Nullable CharSequence value) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SETTING);
        item.id = id;
        item.iconResId = iconResId;
        item.text = title;
        item.subtext = subtitle;
        item.textValue = value;
        return item;
    }

    public static UItem asInfo(CharSequence title, @Nullable CharSequence text) {
        return asInfo(0, title, text, null);
    }

    public static UItem asInfo(int id, CharSequence title, @Nullable CharSequence text, @Nullable CharSequence action) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_INFO);
        item.id = id;
        item.text = title;
        item.subtext = text;
        item.textValue = action;
        return item;
    }

    public static UItem asSpace(int height) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SPACE);
        item.longValue = height;
        return item;
    }

    public static UItem asSpace(int tag, int height) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SPACE);
        item.id = tag;
        item.longValue = height;
        return item;
    }

    public static UItem asCustom(View view) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_CUSTOM);
        item.view = view;
        return item;
    }

    public static UItem ofFactory(ViewFactory factory) {
        UItem item = new UItem(factory.getViewType());
        item.factory = factory;
        return item;
    }
}

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;

public class UItem {

    public static final int MAX_SPAN_COUNT = -1;

    public final int viewType;
    public int id;
    public boolean checked;
    public boolean accent;
    public boolean red;
    public boolean hideDivider;
    public int iconResId;
    public int intValue;
    public CharSequence text;
    @Nullable public CharSequence subtext;
    @Nullable public CharSequence textValue;
    @Nullable public View view;
    @Nullable public Object object;
    @Nullable public Object object2;
    @Nullable public Drawable drawable;
    @Nullable public View.OnClickListener clickCallback;
    public long longValue;
    public int spanCount = MAX_SPAN_COUNT;
    @Nullable public ViewFactory factory;
    private static final HashMap<Class<? extends UItemFactory<?>>, UItemFactory<?>> factoryInstances = new HashMap<>();
    private static final HashMap<Integer, UItemFactory<?>> factories = new HashMap<>();

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

    public abstract static class UItemFactory<V extends View> implements ViewFactory {
        private static int nextViewType = 10000;
        public final int viewType = nextViewType++;

        public static void setup(UItemFactory<?> factory) {
            @SuppressWarnings("unchecked")
            Class<? extends UItemFactory<?>> factoryClass = (Class<? extends UItemFactory<?>>) factory.getClass();
            factoryInstances.put(factoryClass, factory);
            factories.put(factory.getViewType(), factory);
        }

        @Override
        public final V createView(Context context) {
            return createView(context, null, 0, 0, null);
        }

        @Override
        public int getViewType() {
            return viewType;
        }

        public abstract V createView(Context context, @Nullable RecyclerListView listView, int currentAccount, int classGuid, @Nullable org.telegram.ui.ActionBar.Theme.ResourcesProvider resourcesProvider);

        @Override
        public final void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter) {
            bindView(view, item, divider, adapter, null);
        }

        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, @Nullable UniversalRecyclerView listView) {
        }

        public boolean equals(UItem a, UItem b) {
            return a == b;
        }

        public boolean contentsEquals(UItem a, UItem b) {
            return equals(a, b);
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

    public static UItem asGraySection(CharSequence text, CharSequence button, View.OnClickListener onClickListener) {
        UItem item = asGraySection(text);
        item.subtext = button;
        item.clickCallback = onClickListener;
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

    public static UItem asButton(int id, Drawable icon, CharSequence text) {
        UItem item = asButton(id, 0, text, null);
        item.drawable = icon;
        return item;
    }

    public static UItem asRippleCheck(int id, CharSequence text) {
        return asCheck(id, text);
    }

    public static UItem asRadio(int id, CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_RADIO);
        item.id = id;
        item.text = text;
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
        item.intValue = height;
        return item;
    }

    public static UItem asSpace(int tag, int height) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SPACE);
        item.id = tag;
        item.longValue = height;
        item.intValue = height;
        return item;
    }

    public static UItem asFlicker(int type) {
        return asFlicker(0, type);
    }

    public static UItem asFlicker(int id, int type) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_FLICKER);
        item.id = id;
        item.intValue = type;
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

    public static <F extends UItemFactory<?>> UItem ofFactory(Class<F> factoryClass) {
        UItemFactory<?> factory = factoryInstances.get(factoryClass);
        if (factory == null) {
            throw new RuntimeException("UItemFactory was not setuped: " + factoryClass);
        }
        return ofFactory(factory);
    }

    public <F extends UItemFactory<?>> boolean instanceOf(Class<F> factoryClass) {
        UItemFactory<?> target = factoryInstances.get(factoryClass);
        return target != null && target == factory;
    }
}

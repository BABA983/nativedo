/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/ui/web/AddressBarList.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
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
package io.github.baba983.nativedo.ui.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class BrowserAddressPanel extends FrameLayout {

    interface StringCallback {
        void run(String value);
    }

    private static final int VIEW_TYPE_ADDRESS_QUERY = 2000;
    private static final int VIEW_TYPE_ADDRESS_BOOKMARK = 2001;
    private static final int VIEW_TYPE_ADDRESS_SECTION = 2002;
    private static final int QUERY_TYPE_RECENT = 0;
    private static final int QUERY_TYPE_SUGGESTION = 1;
    private static final long SUGGESTION_DEBOUNCE_MS = 300L;
    private static final ExecutorService SUGGESTION_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final UniversalRecyclerView listView;
    private final FrameLayout currentContainer;
    private final FrameLayout currentView;
    private final ImageView currentIconView;
    private final ImageView currentCopyView;
    private final LinearLayout currentTextContainer;
    private final TextView currentTitleView;
    private final TextView currentLinkView;
    private final View space;
    private final ArrayList<String> suggestions = new ArrayList<>();

    private int listBackgroundColor;
    private int grayBackgroundColor;
    private int textColor;
    private int rippleColor;
    private float openProgress;
    private boolean hideCurrent;
    private String inputText;
    private int suggestionGeneration;
    private Runnable pendingSuggestionRunnable;
    @Nullable private Bitmap currentFavicon;

    private Runnable onCurrentClick;
    private StringCallback onQueryClick;
    private StringCallback onQueryInsertClick;
    private StringCallback onUrlClick;
    private View.OnClickListener onCopyClick;

    public BrowserAddressPanel(Context context) {
        super(context);
        setWillNotDraw(false);

        listView = new UniversalRecyclerView(context, this::fillItems, this::itemClick, null);
        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        currentContainer = new FrameLayout(context);

        currentView = new FrameLayout(context);
        FrameLayout.LayoutParams currentLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL);
        currentLp.leftMargin = AndroidUtilities.dp(12);
        currentLp.rightMargin = AndroidUtilities.dp(12);
        currentLp.bottomMargin = AndroidUtilities.dp(15);
        currentContainer.addView(currentView, currentLp);

        currentIconView = new ImageView(context);
        currentIconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        currentIconView.setImageResource(R.drawable.msg_language);
        FrameLayout.LayoutParams iconLp = LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        iconLp.leftMargin = AndroidUtilities.dp(16);
        iconLp.rightMargin = AndroidUtilities.dp(16);
        iconLp.topMargin = AndroidUtilities.dp(16);
        iconLp.bottomMargin = AndroidUtilities.dp(16);
        currentView.addView(currentIconView, iconLp);

        currentCopyView = new ImageView(context);
        currentCopyView.setScaleType(ImageView.ScaleType.CENTER);
        currentCopyView.setImageResource(R.drawable.msg_copy);
        currentCopyView.setOnClickListener(v -> {
            if (onCopyClick != null) {
                onCopyClick.onClick(v);
            }
        });
        FrameLayout.LayoutParams copyLp = LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.RIGHT);
        copyLp.leftMargin = AndroidUtilities.dp(14);
        copyLp.topMargin = AndroidUtilities.dp(14);
        copyLp.rightMargin = AndroidUtilities.dp(14);
        copyLp.bottomMargin = AndroidUtilities.dp(14);
        currentView.addView(currentCopyView, copyLp);

        currentTextContainer = new LinearLayout(context);
        currentTextContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams textContainerLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        textContainerLp.leftMargin = AndroidUtilities.dp(54);
        textContainerLp.rightMargin = AndroidUtilities.dp(54);
        textContainerLp.topMargin = AndroidUtilities.dp(9);
        textContainerLp.bottomMargin = AndroidUtilities.dp(9);
        currentView.addView(currentTextContainer, textContainerLp);

        currentTitleView = new TextView(context);
        currentTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        currentTitleView.setTypeface(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? Typeface.create(null, 500, false)
                : Typeface.create("sans-serif-medium", Typeface.NORMAL));
        currentTitleView.setMaxLines(4);
        currentTitleView.setEllipsize(TextUtils.TruncateAt.END);
        currentTextContainer.addView(currentTitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        currentLinkView = new TextView(context);
        currentLinkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        currentLinkView.setMaxLines(3);
        currentLinkView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams linkLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        linkLp.topMargin = AndroidUtilities.dp(2);
        currentTextContainer.addView(currentLinkView, linkLp);

        space = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(6));
            }
        };

        setColors(Theme.getColor(Theme.key_iv_background), AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_iv_background)) >= .721f ? Color.BLACK : Color.WHITE);
        setOpenProgress(0f);
    }

    void setCurrent(
            String title,
            String url,
            Bitmap favicon,
            Runnable onCurrentClick,
            @Nullable StringCallback onQueryClick,
            @Nullable StringCallback onQueryInsertClick,
            @Nullable StringCallback onUrlClick,
            View.OnClickListener onCopyClick
    ) {
        currentTitleView.setText(TextUtils.isEmpty(title) ? "about:blank" : title);
        currentLinkView.setText(TextUtils.isEmpty(url) ? "about:blank" : url);
        currentFavicon = favicon;

        if (favicon != null) {
            currentIconView.setImageDrawable(new BitmapDrawable(getContext().getResources(), favicon));
            currentIconView.setColorFilter(null);
        } else {
            currentIconView.setImageResource(R.drawable.msg_language);
            currentIconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        }

        this.onCurrentClick = onCurrentClick;
        this.onQueryClick = onQueryClick;
        this.onQueryInsertClick = onQueryInsertClick;
        this.onUrlClick = onUrlClick;
        this.onCopyClick = onCopyClick;

        currentView.setOnClickListener(v -> {
            hideCurrent = true;
            listView.adapter.update(true);
            if (this.onCurrentClick != null) {
                this.onCurrentClick.run();
            }
        });

        hideCurrent = false;
        setInput(null);
        listView.adapter.update(true);
        listView.layoutManager.scrollToPositionWithOffset(0, 0);
    }

    public void setInput(@Nullable String input) {
        inputText = input == null ? null : input.trim();
        if (pendingSuggestionRunnable != null) {
            MAIN_HANDLER.removeCallbacks(pendingSuggestionRunnable);
            pendingSuggestionRunnable = null;
        }
        int generation = ++suggestionGeneration;
        boolean hadSuggestions = !suggestions.isEmpty();
        suggestions.clear();
        if (TextUtils.isEmpty(inputText)) {
            if (hadSuggestions) {
                listView.adapter.update(true);
                listView.layoutManager.scrollToPositionWithOffset(0, 0);
            } else {
                listView.adapter.update(false);
            }
            return;
        }
        listView.adapter.update(true);
        pendingSuggestionRunnable = () -> requestSuggestions(inputText, generation);
        MAIN_HANDLER.postDelayed(pendingSuggestionRunnable, SUGGESTION_DEBOUNCE_MS);
    }

    public void setOpenProgress(float progress) {
        if (Math.abs(this.openProgress - progress) > 0.0001f) {
            this.openProgress = progress;
            invalidate();
        }
    }

    public void setColors(int backgroundColor, int textColor) {
        this.listBackgroundColor = backgroundColor;
        this.textColor = textColor;

        float dark = AndroidUtilities.computePerceivedBrightness(backgroundColor) >= .721f ? 0f : 1f;
        grayBackgroundColor = ColorUtils.blendARGB(backgroundColor, textColor, AndroidUtilities.lerp(.05f, .12f, dark));
        rippleColor = ColorUtils.blendARGB(backgroundColor, textColor, AndroidUtilities.lerp(.12f, .22f, dark));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(AndroidUtilities.dp(15));
        cardBg.setColor(grayBackgroundColor);
        currentView.setBackground(cardBg);

        currentTitleView.setTextColor(textColor);
        currentLinkView.setTextColor(ColorUtils.setAlphaComponent(textColor, (int) (0xff * .6f)));
        if (currentFavicon == null) {
            currentIconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
        }
        currentCopyView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));

        listView.adapter.update(false);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight() * openProgress);
        canvas.drawColor(listBackgroundColor);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (openProgress < .3f) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!hideCurrent && suggestions.isEmpty()) {
            items.add(UItem.asCustom(currentContainer));
        }
        if (!suggestions.isEmpty()) {
            items.add(UItem.asCustom(space));
        }
        for (int i = 0; i < suggestions.size(); i++) {
            items.add(AddressQueryView.Factory.as(QUERY_TYPE_SUGGESTION, suggestions.get(i), i == 0, i == suggestions.size() - 1, this));
        }

        ArrayList<String> recentSearches = BrowserStorage.getRecentSearches(getContext());
        if (!recentSearches.isEmpty()) {
            items.add(AddressSectionView.Factory.as(getContext().getString(R.string.app_browser_recent_searches), getContext().getString(R.string.app_browser_address_recent_clear), this));
            for (int i = 0; i < recentSearches.size(); i++) {
                items.add(AddressQueryView.Factory.as(QUERY_TYPE_RECENT, recentSearches.get(i), i == 0, i == recentSearches.size() - 1, this));
            }
        }

        ArrayList<BrowserStorage.Entry> bookmarks = BrowserStorage.getBookmarks(getContext());
        if (!bookmarks.isEmpty()) {
            items.add(AddressSectionView.Factory.as(getContext().getString(R.string.app_browser_bookmarks), null, this));
            for (int i = 0; i < bookmarks.size(); i++) {
                items.add(AddressBookmarkView.Factory.as(bookmarks.get(i), i != bookmarks.size() - 1, this));
            }
        }
    }

    private void itemClick(UItem item, View view, int position, float x, float y) {
        if (item.factory == AddressQueryView.Factory.INSTANCE) {
            runQueryClick(item.text == null ? null : item.text.toString());
        } else if (item.factory == AddressBookmarkView.Factory.INSTANCE && item.object instanceof BrowserStorage.Entry) {
            BrowserStorage.Entry entry = (BrowserStorage.Entry) item.object;
            if (onUrlClick != null && !TextUtils.isEmpty(entry.url)) {
                onUrlClick.run(entry.url);
            }
        }
    }

    private void runQueryClick(@Nullable String query) {
        if (TextUtils.isEmpty(query)) {
            return;
        }
        if (onQueryClick != null) {
            onQueryClick.run(query);
        }
    }

    private void runQueryInsert(@Nullable String query) {
        if (TextUtils.isEmpty(query)) {
            return;
        }
        if (onQueryInsertClick != null) {
            onQueryInsertClick.run(query);
        }
    }

    private void clearRecentSearches() {
        BrowserStorage.clearRecentSearches(getContext());
        listView.adapter.update(true);
    }

    private void requestSuggestions(@Nullable String query, int generation) {
        if (TextUtils.isEmpty(query)) {
            return;
        }
        final BrowserStorage.SearchEngine engine = BrowserStorage.getSearchEngine(getContext());
        final String url = engine.buildAutocompleteUrl(query);
        SUGGESTION_EXECUTOR.execute(() -> {
            ArrayList<String> result = new ArrayList<>();
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setUseCaches(false);
                StringBuilder builder = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                }
                result = engine.extractSuggestions(builder.toString());
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            ArrayList<String> finalResult = result;
            MAIN_HANDLER.post(() -> {
                if (generation != suggestionGeneration || !TextUtils.equals(query, inputText)) {
                    return;
                }
                suggestions.clear();
                suggestions.addAll(finalResult);
                listView.adapter.update(true);
                listView.layoutManager.scrollToPositionWithOffset(0, 0);
            });
        });
    }

    private Drawable createFallbackIcon(@Nullable String text, int sizeDp) {
        return new LetterDrawable(text, sizeDp, textColor, grayBackgroundColor);
    }

    private static class AddressSectionView extends FrameLayout {
        private final TextView titleView;
        private final TextView actionView;

        AddressSectionView(Context context) {
            super(context);
            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams titleLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            titleLp.leftMargin = AndroidUtilities.dp(16);
            titleLp.rightMargin = AndroidUtilities.dp(92);
            addView(titleView, titleLp);

            actionView = new TextView(context);
            actionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            actionView.setTypeface(Typeface.DEFAULT_BOLD);
            actionView.setSingleLine(true);
            actionView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams actionLp = LayoutHelper.createFrame(76, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            actionLp.rightMargin = AndroidUtilities.dp(16);
            addView(actionView, actionLp);
        }

        void bind(UItem item) {
            BrowserAddressPanel parent = (BrowserAddressPanel) item.object;
            setBackgroundColor(parent.listBackgroundColor);
            titleView.setText(item.text);
            titleView.setTextColor(ColorUtils.setAlphaComponent(parent.textColor, (int) (0xff * .55f)));
            boolean hasAction = !TextUtils.isEmpty(item.textValue);
            FrameLayout.LayoutParams titleLp = (FrameLayout.LayoutParams) titleView.getLayoutParams();
            titleLp.rightMargin = AndroidUtilities.dp(hasAction ? 92 : 16);
            titleView.setLayoutParams(titleLp);
            actionView.setText(item.textValue);
            actionView.setTextColor(parent.textColor);
            actionView.setVisibility(hasAction ? VISIBLE : GONE);
            actionView.setOnClickListener(hasAction ? v -> parent.clearRecentSearches() : null);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY)
            );
        }

        static final class Factory implements UItem.ViewFactory {
            static final Factory INSTANCE = new Factory();

            static UItem as(String title, @Nullable String action, BrowserAddressPanel parent) {
                UItem item = UItem.ofFactory(INSTANCE);
                item.text = title;
                item.textValue = action;
                item.object = parent;
                return item;
            }

            @Override
            public int getViewType() {
                return VIEW_TYPE_ADDRESS_SECTION;
            }

            @Override
            public View createView(Context context) {
                return new AddressSectionView(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter) {
                ((AddressSectionView) view).bind(item);
            }

            @Override
            public boolean isEnabled(UItem item) {
                return false;
            }
        }
    }

    private static class AddressQueryView extends FrameLayout {
        private final ImageView iconView;
        private final TextView textView;
        private final InsertArrowView insertView;
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider;

        AddressQueryView(Context context) {
            super(context);
            setWillNotDraw(false);
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);
            FrameLayout.LayoutParams iconLp = LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT);
            iconLp.leftMargin = AndroidUtilities.dp(10);
            iconLp.topMargin = AndroidUtilities.dp(8);
            iconLp.rightMargin = AndroidUtilities.dp(8);
            iconLp.bottomMargin = AndroidUtilities.dp(8);
            addView(iconView, iconLp);

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams textLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT);
            textLp.leftMargin = AndroidUtilities.dp(64);
            textLp.topMargin = AndroidUtilities.dp(8);
            textLp.rightMargin = AndroidUtilities.dp(64);
            textLp.bottomMargin = AndroidUtilities.dp(8);
            addView(textView, textLp);

            insertView = new InsertArrowView(context);
            FrameLayout.LayoutParams insertLp = LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            insertLp.leftMargin = AndroidUtilities.dp(8);
            insertLp.topMargin = AndroidUtilities.dp(8);
            insertLp.rightMargin = AndroidUtilities.dp(10);
            insertLp.bottomMargin = AndroidUtilities.dp(8);
            addView(insertView, insertLp);
        }

        void bind(UItem item, boolean divider) {
            BrowserAddressPanel parent = (BrowserAddressPanel) item.object;
            String query = item.text == null ? "" : item.text.toString();
            setBackgroundColor(parent.grayBackgroundColor);
            setForeground(Theme.createSelectorDrawable(parent.rippleColor, Theme.RIPPLE_MASK_ALL));
            iconView.setImageResource(item.id == QUERY_TYPE_RECENT ? android.R.drawable.ic_menu_recent_history : android.R.drawable.ic_menu_search);
            iconView.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(parent.textColor, (int) (0xff * .6f)), PorterDuff.Mode.SRC_IN));
            textView.setText(query);
            textView.setTextColor(parent.textColor);
            insertView.setColor(ColorUtils.setAlphaComponent(parent.textColor, (int) (0xff * .6f)));
            insertView.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(parent.textColor, (int) (0xff * .15f)), Theme.RIPPLE_MASK_ALL));
            insertView.setOnClickListener(v -> parent.runQueryInsert(query));
            dividerPaint.setColor(ColorUtils.setAlphaComponent(parent.textColor, (int) (0xff * .1f)));
            needDivider = divider;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(AndroidUtilities.dp(64), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY)
            );
        }

        static final class Factory implements UItem.ViewFactory {
            static final Factory INSTANCE = new Factory();

            static UItem as(int type, String query, boolean top, boolean bottom, BrowserAddressPanel parent) {
                UItem item = UItem.ofFactory(INSTANCE);
                item.id = type;
                item.text = query;
                item.object = parent;
                item.accent = top;
                item.red = bottom;
                return item;
            }

            @Override
            public int getViewType() {
                return VIEW_TYPE_ADDRESS_QUERY;
            }

            @Override
            public View createView(Context context) {
                return new AddressQueryView(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter) {
                ((AddressQueryView) view).bind(item, divider);
            }
        }
    }

    private static class AddressBookmarkView extends FrameLayout {
        private final ImageView iconView;
        private final LinearLayout textLayout;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView arrowView;
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean needDivider;

        AddressBookmarkView(Context context) {
            super(context);
            setWillNotDraw(false);
            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            FrameLayout.LayoutParams iconLp = LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT);
            iconLp.leftMargin = AndroidUtilities.dp(10);
            iconLp.topMargin = AndroidUtilities.dp(8);
            iconLp.rightMargin = AndroidUtilities.dp(8);
            iconLp.bottomMargin = AndroidUtilities.dp(8);
            addView(iconView, iconLp);

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            FrameLayout.LayoutParams textLayoutLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT);
            textLayoutLp.leftMargin = AndroidUtilities.dp(64);
            textLayoutLp.rightMargin = AndroidUtilities.dp(52);
            addView(textLayout, textLayoutLp);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subtitleLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            subtitleLp.topMargin = AndroidUtilities.dp(3);
            textLayout.addView(subtitleView, subtitleLp);

            arrowView = new TextView(context);
            arrowView.setText(">");
            arrowView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            arrowView.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams arrowLp = LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            arrowLp.leftMargin = AndroidUtilities.dp(8);
            arrowLp.topMargin = AndroidUtilities.dp(8);
            arrowLp.rightMargin = AndroidUtilities.dp(8);
            arrowLp.bottomMargin = AndroidUtilities.dp(8);
            addView(arrowView, arrowLp);
        }

        void bind(UItem item, boolean divider) {
            BrowserAddressPanel parent = (BrowserAddressPanel) item.object2;
            BrowserStorage.Entry entry = (BrowserStorage.Entry) item.object;
            BrowserMetadataCache.Metadata meta = BrowserMetadataCache.getForUrl(getContext(), entry.url);
            String title = meta == null ? entry.displayTitle() : meta.displayTitle();
            String subtitle = TextUtils.isEmpty(entry.url) ? entry.displaySubtitle() : entry.url;
            Bitmap favicon = meta != null && meta.favicon != null ? meta.favicon : entry.favicon;

            setBackgroundColor(parent.grayBackgroundColor);
            setForeground(Theme.createSelectorDrawable(parent.rippleColor, Theme.RIPPLE_MASK_ALL));
            titleView.setText(title);
            titleView.setTextColor(parent.textColor);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(ColorUtils.setAlphaComponent(parent.textColor, (int) (0xff * .55f)));
            arrowView.setTextColor(ColorUtils.setAlphaComponent(parent.textColor, (int) (0xff * .6f)));
            if (favicon != null) {
                iconView.setImageDrawable(new BitmapDrawable(getResources(), favicon));
                iconView.clearColorFilter();
            } else {
                iconView.setImageDrawable(parent.createFallbackIcon(title, 32));
            }
            dividerPaint.setColor(ColorUtils.setAlphaComponent(parent.textColor, (int) (0xff * .1f)));
            needDivider = divider;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (needDivider) {
                canvas.drawRect(AndroidUtilities.dp(64), getHeight() - 1, getWidth(), getHeight(), dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY)
            );
        }

        static final class Factory implements UItem.ViewFactory {
            static final Factory INSTANCE = new Factory();

            static UItem as(BrowserStorage.Entry entry, boolean divider, BrowserAddressPanel parent) {
                UItem item = UItem.ofFactory(INSTANCE);
                item.text = entry.displayTitle();
                item.textValue = entry.url;
                item.object = entry;
                item.object2 = parent;
                if (!divider) {
                    item.hideDivider();
                }
                return item;
            }

            @Override
            public int getViewType() {
                return VIEW_TYPE_ADDRESS_BOOKMARK;
            }

            @Override
            public View createView(Context context) {
                return new AddressBookmarkView(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter) {
                ((AddressBookmarkView) view).bind(item, divider);
            }
        }
    }

    private static final class InsertArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        InsertArrowView(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setColor(int color) {
            paint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.drawLine(cx - AndroidUtilities.dp(6), cy + AndroidUtilities.dp(5), cx + AndroidUtilities.dp(6), cy - AndroidUtilities.dp(7), paint);
            canvas.drawLine(cx + AndroidUtilities.dp(6), cy - AndroidUtilities.dp(7), cx + AndroidUtilities.dp(6), cy + AndroidUtilities.dp(3), paint);
            canvas.drawLine(cx + AndroidUtilities.dp(6), cy - AndroidUtilities.dp(7), cx - AndroidUtilities.dp(4), cy - AndroidUtilities.dp(7), paint);
        }
    }

    private static final class LetterDrawable extends Drawable {
        private final String letter;
        private final int sizeDp;
        private final int textColor;
        private final int backgroundColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        LetterDrawable(@Nullable String text, int sizeDp, int textColor, int backgroundColor) {
            this.letter = TextUtils.isEmpty(text) ? "" : text.substring(0, 1).toUpperCase();
            this.sizeDp = sizeDp;
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            rect.set(getBounds());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.setAlphaComponent(textColor, (int) (0xff * .10f)));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
            if (!TextUtils.isEmpty(letter)) {
                paint.setColor(textColor);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(AndroidUtilities.dp(14));
                Paint.FontMetrics fm = paint.getFontMetrics();
                canvas.drawText(letter, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(sizeDp);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(sizeDp);
        }
    }
}

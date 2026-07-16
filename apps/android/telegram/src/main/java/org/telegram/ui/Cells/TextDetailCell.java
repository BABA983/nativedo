/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

/**
 * Simplified NativeDo port of Telegram's TextDetailCell.
 * Shows a title row and a subtitle/value row, with an optional divider.
 *
 * Differences from upstream:
 * - Uses plain TextView instead of SpoilersTextView/LinksTextView
 * - Single valueTextView instead of rightValueTextView
 * - Simplified Factory integration
 */
public class TextDetailCell extends FrameLayout {

    public static final int VIEW_TYPE = 10_002;

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextView textView;
    private final TextView valueTextView;
    private boolean needDivider;
    private boolean contentDescriptionValueFirst;

    public TextDetailCell(Context context) {
        super(context);
        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setPadding(dp(6), dp(2), dp(6), dp(5));
        addView(textView, LayoutHelper.createFrame(
            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
            LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,
            17, 8 - 2, 17, 0
        ));

        valueTextView = new TextView(context);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, dp(1), 0, dp(6));
        addView(valueTextView, LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
            LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT,
            17, 33 - 1, 17, 10 - 6
        ));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(60) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
        );
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setContentDescriptionValueFirst(boolean contentDescriptionValueFirst) {
        this.contentDescriptionValueFirst = contentDescriptionValueFirst;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        CharSequence text = textView.getText();
        CharSequence valueText = valueTextView.getText();
        if (!TextUtils.isEmpty(text) && !TextUtils.isEmpty(valueText)) {
            info.setText((contentDescriptionValueFirst ? valueText : text) + ": " + (contentDescriptionValueFirst ? text : valueText));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            Paint paint = dividerPaint;
            canvas.drawLine(
                LocaleController.isRTL ? 0 : dp(20),
                getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? dp(20) : 0),
                getMeasuredHeight() - 1,
                paint
            );
        }
    }

    public void updateColors() {
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
    }

    public static final class Factory extends UItem.UItemFactory<TextDetailCell> {
        static { setup(new Factory()); }

        @Override
        public TextDetailCell createView(Context context, org.telegram.ui.Components.RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new TextDetailCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, org.telegram.ui.Components.UniversalRecyclerView listView) {
            ((TextDetailCell) view).setTextAndValue(item.text, item.subtext, divider);
            ((TextDetailCell) view).updateColors();
        }

        @Override
        public boolean isClickable() {
            return false;
        }

        public static UItem of(int id, CharSequence title, CharSequence value) {
            final UItem uItem = UItem.ofFactory(Factory.class);
            uItem.id = id;
            uItem.text = title;
            uItem.subtext = value;
            return uItem;
        }
    }
}

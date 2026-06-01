package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.LayoutHelper;

public class ActionBarMenuSubItem extends FrameLayout {

    public final TextView textView;
    public final TextView subtextView;
    public final ImageView imageView;
    private int textColor;
    private int iconColor;
    private int selectorColor;
    private int iconResId;

    // Rounded selector state — mirrors upstream ActionBarMenuSubItem.
    private boolean top, bottom;
    private int selectorRad = 6;

    // Account/chat selector decoration (no CheckBox2 in NativeDo): checkView stays null.
    public boolean checkViewLeft = LocaleController.isRTL;
    public android.view.View checkView;

    public ActionBarMenuSubItem(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        textColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider);
        iconColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider);
        selectorColor = Theme.multAlpha(textColor, .12f);
        updateBackground();
        setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        textView = new TextView(context);
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(textColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        subtextView = new TextView(context);
        subtextView.setLines(1);
        subtextView.setSingleLine(true);
        subtextView.setGravity(Gravity.LEFT);
        subtextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtextView.setPadding(AndroidUtilities.dp(43), 0, 0, 0);
        FrameLayout.LayoutParams subtextParams = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        subtextParams.topMargin = AndroidUtilities.dp(10);
        addView(subtextView, subtextParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
    }

    public void setTextAndIcon(CharSequence text, int icon, Drawable iconDrawable) {
        textView.setText(text);
        if (iconDrawable != null) {
            iconResId = 0;
            imageView.setColorFilter(null);
            imageView.setImageDrawable(iconDrawable);
            imageView.setVisibility(VISIBLE);
            textView.setPadding(AndroidUtilities.dp(43), 0, 0, 0);
        } else if (icon != 0) {
            iconResId = icon;
            imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            imageView.setImageResource(icon);
            imageView.setVisibility(VISIBLE);
            textView.setPadding(AndroidUtilities.dp(43), 0, 0, 0);
        } else {
            iconResId = 0;
            imageView.setVisibility(INVISIBLE);
            textView.setPadding(0, 0, 0, 0);
        }
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setTextAndIcon(CharSequence text, int icon) {
        setTextAndIcon(text, icon, null);
    }

    public ActionBarMenuSubItem setColors(int textColor, int iconColor) {
        setTextColor(textColor);
        setIconColor(iconColor);
        return this;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
        textView.setTextColor(textColor);
    }

    public void setIconColor(int iconColor) {
        this.iconColor = iconColor;
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public void setSubtextColor(int color) {
        subtextView.setTextColor(color);
    }

    public void setSubtext(CharSequence text) {
        subtextView.setText(text);
        boolean visible = !TextUtils.isEmpty(text);
        subtextView.setVisibility(visible ? VISIBLE : GONE);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
        layoutParams.bottomMargin = visible ? AndroidUtilities.dp(10) : 0;
        textView.setLayoutParams(layoutParams);
    }

    public TextView getTextView() {
        return textView;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setIconDrawable(Drawable drawable) {
        setTextAndIcon(textView.getText(), 0, drawable);
    }

    public void setSelectorColor(int selectorColor) {
        this.selectorColor = selectorColor;
        updateBackground();
    }

    public void updateSelectorBackground(boolean top, boolean bottom) {
        if (this.top == top && this.bottom == bottom) {
            return;
        }
        this.top = top;
        this.bottom = bottom;
        updateBackground();
    }

    public void updateSelectorBackground(boolean top, boolean bottom, int selectorRad) {
        if (this.top == top && this.bottom == bottom && this.selectorRad == selectorRad) {
            return;
        }
        this.top = top;
        this.bottom = bottom;
        this.selectorRad = selectorRad;
        updateBackground();
    }

    public void updateBackground() {
        setBackground(Theme.createRadSelectorDrawable(selectorColor, top ? selectorRad : 0, bottom ? selectorRad : 0));
    }
}

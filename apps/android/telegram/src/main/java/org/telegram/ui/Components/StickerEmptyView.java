package org.telegram.ui.Components;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.TextView;

public class StickerEmptyView extends FrameLayout {
    public static final int STICKER_TYPE_SEARCH = 0;
    public final TextView title;
    public final TextView subtitle;

    public StickerEmptyView(Context context, Object sticker, int stickerType) {
        super(context);
        title = new TextView(context);
        subtitle = new TextView(context);
        addView(title);
        addView(subtitle);
    }

    public void showProgress(boolean show, boolean animated) {
    }

    public void title(CharSequence title) {
        this.title.setText(title);
    }

    public void subtitle(CharSequence subtitle) {
        this.subtitle.setText(subtitle);
    }

    public void setAnimateLayoutChange(boolean value) {
    }
}

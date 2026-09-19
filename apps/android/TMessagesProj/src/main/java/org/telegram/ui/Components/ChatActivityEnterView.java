/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.Theme;

/**
 * NativeDo stub of org.telegram.ui.Components.ChatActivityEnterView.
 * Upstream is the chat input bar (~14k lines). NativeDo only needs the type
 * signature so PhotoViewer's mentionContainer/chatActivityEnterView fields compile.
 */
public class ChatActivityEnterView extends FrameLayout {

    public ChatActivityEnterView(Context context) {
        super(context);
    }

    public void closeKeyboard() {
    }

    public void hidePopup(boolean byBackButton) {
    }

    public void hidePopup(boolean byBackButton, boolean animated) {
    }

    public void onResume() {
    }

    public void onPause() {
    }

    /**
     * NativeDo seam for the pin's 635-line send button (price text, stars, emoji states) - the rich
     * editor's toolbar mounts one and our host drives it (`setOnClickListener` -> `onSend`). This
     * keeps the surface the toolbar uses: the four-argument constructor, an overridable
     * `isOpen()`, `setResourceId(int)` and the drawable it paints, plus the usual View API. The
     * price/stars/emoji states land with the chat composer they belong to.
     */
    public static class SendButton extends View implements ItemOptions.ScrimView {

        public static final float INFINITE_LOADING = -3f;

        public final Theme.ResourcesProvider resourcesProvider;

        public int resId;

        private Drawable drawable;

        public SendButton(Context context, int resId, Theme.ResourcesProvider resourcesProvider) {
            this(context, resId, resourcesProvider, false);
        }

        public SendButton(Context context, int resId, Theme.ResourcesProvider resourcesProvider, boolean isNewDesignSendButton) {
            super(context);
            this.resId = resId;
            this.resourcesProvider = resourcesProvider;
            drawable = context.getResources().getDrawable(resId).mutate();
            drawable.setCallback(this);
        }

        public boolean isOpen() {
            return false;
        }

        public void setResourceId(int resId) {
            if (this.resId == resId) {
                return;
            }
            this.resId = resId;
            drawable = getResources().getDrawable(resId).mutate();
            drawable.setCallback(this);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawable == null) return;
            final int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
            final int left = (getMeasuredWidth() - size) / 2;
            final int top = (getMeasuredHeight() - size) / 2;
            drawable.setBounds(left, top, left + size, top + size);
            drawable.draw(canvas);
        }
    }
}

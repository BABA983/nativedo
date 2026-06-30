/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.view.View;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;

/** NativeDo minimal port of Telegram's EditTextBoldCursor for FragmentSearchField. */
public class EditTextBoldCursor extends EditText {
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cursorRect = new RectF();
    private float offsetY;
    private View windowView;
    private float cursorWidth = 2.0f;
    private int cursorSize = AndroidUtilities.dp(24f);
    private int cursorColor = Color.TRANSPARENT;
    private boolean allowDrawCursor = true;
    private boolean forceCursorEnd;
    private boolean textWatchersSuppressed;
    private Runnable delegate;

    public EditTextBoldCursor(Context context) {
        super(context);
        cursorPaint.setColor(cursorColor);
    }

    public void setClipToPadding(boolean clipToPadding) {
        // TextView does not expose ViewGroup's clipToPadding; FragmentSearchField
        // still clips the visible text region through setClipBounds().
    }

    public void setCursorWidth(float width) {
        cursorWidth = width;
        invalidate();
    }

    public void setCursorColor(int color) {
        cursorColor = color;
        cursorPaint.setColor(color);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            Drawable cursor = getTextCursorDrawable();
            if (cursor != null) {
                cursor = cursor.mutate();
                cursor.setTint(Color.TRANSPARENT);
                setTextCursorDrawable(cursor);
            }
        }
        invalidate();
    }

    public void setCursorSize(int value) {
        cursorSize = value;
        invalidate();
    }

    public void setAllowDrawCursor(boolean value) {
        allowDrawCursor = value;
        invalidate();
    }

    public void setForceCursorEnd(boolean cursorEnd) {
        forceCursorEnd = cursorEnd;
        invalidate();
    }

    public void setHandlesColor(int color) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        try {
            Drawable left = getTextSelectHandleLeft();
            if (left != null) {
                left = left.mutate();
                left.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                setTextSelectHandleLeft(left);
            }

            Drawable middle = getTextSelectHandle();
            if (middle != null) {
                middle = middle.mutate();
                middle.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                setTextSelectHandle(middle);
            }

            Drawable right = getTextSelectHandleRight();
            if (right != null) {
                right = right.mutate();
                right.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                setTextSelectHandleRight(right);
            }
        } catch (Throwable ignore) {
        }
    }

    public void setOffsetY(float offset) {
        if (offsetY == offset) {
            return;
        }
        offsetY = offset;
        invalidate();
    }

    public float getOffsetY() {
        return offsetY;
    }

    public void setWindowView(View view) {
        windowView = view;
    }

    public View getWindowView() {
        return windowView;
    }

    public void setTextWatchersSuppressed(boolean textWatchersSuppressed, boolean dispatchChanged) {
        this.textWatchersSuppressed = textWatchersSuppressed;
        if (!textWatchersSuppressed && dispatchChanged) {
            onTextChanged(getText(), 0, 0, getText() == null ? 0 : getText().length());
        }
    }

    public boolean isTextWatchersSuppressed() {
        return textWatchersSuppressed;
    }

    public void setDelegate(Runnable delegate) {
        this.delegate = delegate;
    }

    public void setAllowTextEntitiesIntersection(boolean value) {
    }

    public CharSequence getTextToUse() {
        return getText();
    }

    public boolean closeCreationLinkDialog(boolean invoked) {
        return false;
    }

    public void invalidateEffects() {
        invalidateForce();
    }

    public void invalidateForce() {
        invalidate();
        if (windowView != null) {
            windowView.invalidate();
        }
        View parent = getParent() instanceof View ? (View) getParent() : null;
        if (parent != null) {
            parent.invalidate();
        }
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (!textWatchersSuppressed && delegate != null) {
            delegate.run();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (offsetY == 0) {
            super.onDraw(canvas);
            drawTelegramCursor(canvas);
            return;
        }
        canvas.save();
        canvas.translate(0, offsetY);
        super.onDraw(canvas);
        drawTelegramCursor(canvas);
        canvas.restore();
    }

    private void drawTelegramCursor(Canvas canvas) {
        if (!allowDrawCursor || cursorColor == Color.TRANSPARENT || !isFocused()) {
            return;
        }
        if ((SystemClock.uptimeMillis() % 1000L) >= 500L) {
            invalidate();
            return;
        }
        Layout layout = getLayout();
        if (layout == null) {
            return;
        }
        int offset = forceCursorEnd ? layout.getText().length() : getSelectionStart();
        if (offset < 0) {
            return;
        }
        offset = Math.min(offset, layout.getText().length());
        int line = layout.getLineForOffset(offset);
        float horizontal = layout.getPrimaryHorizontal(offset);
        float cursorLeft = getCompoundPaddingLeft() + horizontal - getScrollX();
        float lineTop = getCompoundPaddingTop() + layout.getLineTop(line) - getScrollY();
        float lineBottom = getCompoundPaddingTop() + layout.getLineBottom(line) - getScrollY();
        float height = cursorSize;
        if (height <= 0 || height > lineBottom - lineTop) {
            height = lineBottom - lineTop;
        }
        float top = lineTop + (lineBottom - lineTop - height) / 2f;
        float width = Math.max(1f, AndroidUtilities.dp(cursorWidth));
        cursorRect.set(cursorLeft, top, cursorLeft + width, top + height);
        canvas.drawRoundRect(cursorRect, width / 2f, width / 2f, cursorPaint);
        invalidate();
    }

    public void setHintColor(int color) {
        setHintTextColor(color);
    }

    public void setHintText(CharSequence hint) {
        setHint(hint);
    }

    public void setLineColors(int color, int activeColor, int errorColor) {
    }
}

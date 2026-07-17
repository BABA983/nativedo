package org.telegram.ui.Cells;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;

/**
 * Upstream-shaped message cell surface used by RecyclerAnimationScrollHelper.
 * Host: ReactionsLayoutInBubble type omitted — getReactionsLayout default returns null
 * without a dedicated type dependency.
 */
public interface IMessageCell {

    MessageObject getMessageObject();
    default MessageObject.GroupedMessagePosition getCurrentPosition() { return null; }

    default Object getReactionsLayout() {
        return null;
    }

    default void didPressReactionFromLayout(Object reaction, boolean longpress, float x, float y) {}

    default float getSlidingOffsetX() { return 0; }
    default float getCheckBoxTranslation() { return 0; }
    default boolean willRemovedAfterAnimation() { return false; }

    default boolean drawPinnedTop() { return false; }
    default boolean drawPinnedBottom() { return false; }

    default void setAnimationRunning(boolean animationRunning, boolean willRemoved) {}

    default ImageReceiver getAvatarImage() { return null; }

    default boolean shouldDrawAlphaLayer() { return false; }

    default int getLayoutHeight() { return this.getMeasuredHeight(); }
    float getDeltaTop();
    float getDeltaLeft();
    float getDeltaBottom();
    float getDeltaRight();

    float getX();
    float getY();
    float getScaleX();
    float getScaleY();
    float getAlpha();
    float getPivotX();
    float getPivotY();
    int getWidth();
    int getHeight();
    int getMeasuredWidth();
    int getMeasuredHeight();
}

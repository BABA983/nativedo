package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.RecyclerListView;

/**
 * The list a topic's posts are drawn in.
 *
 * <p>A {@link ChatMessageCell} builds the avatar's image but does not draw it: upstream's chat list
 * draws it after the row, so the avatar can sit at the bottom of a run of messages by one author and
 * overlap the next row. The position is therefore the row's business, and this list is where the row
 * finishes — the same place upstream does it, in `drawChild`.</p>
 */
public class PostListView extends RecyclerListView {

    public PostListView(Context context) {
        super(context);
    }

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child instanceof ChatMessageCell) {
            drawAvatar(canvas, (ChatMessageCell) child);
        }
        return result;
    }

    /**
     * Upstream's own placement: the avatar hangs below the bubble's last line, never above the
     * row's top and never below the row's bottom, so it stays inside the row it belongs to.
     */
    private void drawAvatar(Canvas canvas, ChatMessageCell cell) {
        ImageReceiver avatar = cell.getAvatarImage();
        if (avatar == null) {
            return;
        }
        int top = childTop(cell);
        int y = top + (int) cell.getPaddingTopAnimated() + cell.getLayoutHeight();
        y += (int) cell.getTransitionParams().deltaBottom;
        int cellBottom = (int) (cell.getY() + cell.getMeasuredHeight());
        if (y > cellBottom) {
            y = cellBottom;
        }
        if (y - AndroidUtilities.dp(48) < top) {
            y = top + AndroidUtilities.dp(48);
        }
        avatar.setImageY(y - AndroidUtilities.dp(44));
        avatar.setVisible(true, false);
        avatar.setAlpha(1f);
        // Upstream's chat list fades a new avatar in, and the fade is the only thing that ever
        // raises the image's own alpha: without it the cell draws the author's initials forever,
        // because this list draws a settled avatar rather than running that animation.
        avatar.setCurrentAlpha(1f);
        avatar.draw(canvas);
    }

    private int childTop(View child) {
        return (int) child.getY() + child.getPaddingTop();
    }
}

package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
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

    /** Where a row's author comes from; the screen owns the participants. */
    public interface AvatarSource {
        TLRPC.User user(long senderId);
    }

    private AvatarSource avatarSource;

    /**
     * The initials a row shows while its author's picture is on its way. The cell builds one of its
     * own but does not hand it out, and the picture is given to the image loader without it: the
     * first ask would leave the bubble's avatar without anything to draw. The colour and the letters
     * come from the same {@link AvatarDrawable} the cell uses, so what is drawn before the picture
     * arrives is what would have been drawn anyway.
     */
    private final java.util.HashMap<Long, org.telegram.ui.Components.AvatarDrawable> placeholders =
            new java.util.HashMap<>();

    public PostListView(Context context) {
        super(context);
    }

    public void setAvatarSource(AvatarSource source) {
        avatarSource = source;
    }

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        // Only the bubble at the bottom of a floor's run carries the author's avatar: a chat draws it
        // under a run of messages by one author, and a bubble the run pins to the one below it is
        // not that last bubble.
        boolean lastOfRun = child instanceof ChatMessageCell && !((ChatMessageCell) child).isPinnedBottom();
        if (lastOfRun) {
            ensureAvatar((ChatMessageCell) child);
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (lastOfRun) {
            drawAvatar(canvas, (ChatMessageCell) child);
        }
        return result;
    }

    /**
     * Points the row at the avatar that was downloaded for its author. The cell asks upstream for
     * the author's photo whenever it is bound or attached again, and that photo is a file location
     * nothing fetches from (there is no Telegram account behind it). What the image loader can read
     * is the file our client downloaded, named by its path — the same way a post's own images are
     * drawn — and it is handed over here, after the cell's own decisions, so nothing takes it back.
     */
    private void ensureAvatar(ChatMessageCell cell) {
        ImageReceiver avatar = cell.getAvatarImage();
        if (avatar == null || avatarSource == null || cell.getMessageObject() == null
                || cell.getMessageObject().messageOwner == null) {
            return;
        }
        // A picture is here (this draw finds it) or ours is already being read: nothing to hand over.
        if (avatar.getBitmap() != null || avatar.getImageDrawable() != null) {
            return;
        }
        // The cell's own ask is a file location with no path — the picture is named by where it was
        // downloaded to, and only a location that carries that path can be read.
        org.telegram.messenger.ImageLocation current = avatar.getImageLocation();
        if (current != null && current.path != null) {
            return;
        }
        TLRPC.User author = avatarSource.user(
                org.telegram.messenger.MessageObject.getFromChatId(cell.getMessageObject().messageOwner));
        java.io.File file = author == null ? null : DiscourseAvatars.INSTANCE.localFile(author);
        if (file == null) {
            return;
        }
        avatar.setImage(
                org.telegram.messenger.ImageLocation.getForPath(file.getAbsolutePath()),
                "50_50", placeholder(avatar, author), null, null, 0);
    }

    /**
     * What the spot shows until the picture is read: the initials the cell itself put there, or one
     * of our own when it has none — the hand-over would otherwise leave the spot empty.
     */
    private org.telegram.ui.Components.AvatarDrawable placeholder(ImageReceiver avatar, TLRPC.User author) {
        if (avatar.getDrawable() instanceof org.telegram.ui.Components.AvatarDrawable) {
            return (org.telegram.ui.Components.AvatarDrawable) avatar.getDrawable();
        }
        org.telegram.ui.Components.AvatarDrawable drawable = placeholders.get(author.id);
        if (drawable == null) {
            drawable = new org.telegram.ui.Components.AvatarDrawable();
            drawable.setInfo(0, author);
            placeholders.put(author.id, drawable);
        }
        return drawable;
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

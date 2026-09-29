package io.github.baba983.nativedo.topic;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.DialogCell;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import io.github.baba983.nativedo.core.model.Post;
import io.github.baba983.nativedo.core.model.TopicSummary;

/**
 * Maps Discourse models onto the Telegram shapes the stock cells consume.
 *
 * <p>Upstream's cells read {@code TLRPC} and {@code MessageObject}, not our models, and both sides
 * are plain data holders with no behaviour of their own, so the mapping is a translation and not a
 * seam: nothing here decides what the UI does.</p>
 */
public final class DiscourseRows {

    /**
     * Authors are identified by a number in Telegram's model. Discourse has no numeric user id in
     * the payloads we consume, so the username is hashed into a stable id; the offset keeps the
     * range clear of the small positive ids Telegram reserves for itself.
     */
    private static final long USER_ID_BASE = 1_000_000_000L;

    public static DialogCell.CustomDialog topicRow(TopicSummary topic) {
        DialogCell.CustomDialog row = new DialogCell.CustomDialog();
        row.id = (int) (topic.getId() & 0x7fffffff);
        row.name = topic.getTitle();
        row.message = summaryLine(topic);
        row.date = epochSeconds(topic.getLastPostedAt());
        row.unread_count = topic.getUnreadCount();
        row.pinned = topic.isPinned();
        row.muted = topic.isMuted();
        return row;
    }

    /** The excerpt Discourse already computed, falling back to the reply count. */
    private static String summaryLine(TopicSummary topic) {
        String excerpt = topic.getExcerpt();
        if (excerpt != null && !excerpt.isEmpty()) {
            return excerpt;
        }
        String lastPoster = topic.getLastPosterUsername();
        int replies = topic.getReplyCount();
        if (lastPoster != null && !lastPoster.isEmpty()) {
            return lastPoster + " · " + replies;
        }
        return String.valueOf(replies);
    }

    public static TLRPC.User author(Post post) {
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = userId(post.getAuthorUsername());
        user.username = post.getAuthorUsername();
        String displayName = post.getAuthorDisplayName();
        user.first_name = displayName != null && !displayName.isEmpty()
                ? displayName
                : post.getAuthorUsername();
        return user;
    }

    public static long userId(String username) {
        if (username == null || username.isEmpty()) {
            return USER_ID_BASE;
        }
        return USER_ID_BASE + (Math.abs(username.hashCode()) % 1_000_000_000L);
    }

    /**
     * One post as a message the stock {@code ChatMessageCell} can draw. The body travels in
     * {@code rich_message}, which is how upstream itself carries a rich body for a locally built
     * message (see its theme preview), so no Telegram media or file model is involved.
     */
    public static MessageObject postMessage(Post post, long topicId, HashMap<Long, TLRPC.User> users) {
        TLRPC.User user = author(post);
        users.put(user.id, user);

        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = (int) post.getId();
        message.date = epochSeconds(post.getCreatedAt());
        message.dialog_id = topicId;
        // Local-message flag set used by upstream's own preview messages.
        message.flags = 257 + 8;
        message.message = "";
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = false;
        message.from_id = peer(user.id);
        message.peer_id = peer(user.id);
        message.rich_message = DiscourseCookedHtml.richMessage(post.getCookedHtml());

        MessageObject messageObject = new MessageObject(
                0, message, null, users, new HashMap<>(), null, null,
                true, false, 0, false, false, false);
        return messageObject;
    }

    private static TLRPC.TL_peerUser peer(long userId) {
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = userId;
        return peer;
    }

    /** Discourse sends ISO-8601; upstream's cells want whole seconds in local time. */
    static int epochSeconds(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) {
            return (int) (System.currentTimeMillis() / 1000L);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date parsed = format.parse(isoDate, new ParsePosition(0));
        return parsed == null
                ? (int) (System.currentTimeMillis() / 1000L)
                : (int) (parsed.getTime() / 1000L);
    }

    private DiscourseRows() {
    }
}
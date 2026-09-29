package io.github.baba983.nativedo.topic;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.DialogCell;

import java.text.ParsePosition;
import java.util.ArrayList;
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

    /** The topic's author, so the list row can draw their avatar instead of initials. */
    public static TLRPC.User topicAuthor(TopicSummary topic, String baseUrl) {
        return DiscourseAvatars.INSTANCE.user(
                topic.getAuthorUsername(),
                "",
                topic.getAuthorAvatarTemplate() == null ? "" : topic.getAuthorAvatarTemplate(),
                baseUrl
        );
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

    public static TLRPC.User author(Post post, String baseUrl) {
        return DiscourseAvatars.INSTANCE.user(
                post.getAuthorUsername(),
                post.getAuthorDisplayName() == null ? "" : post.getAuthorDisplayName(),
                post.getAuthorAvatarTemplate() == null ? "" : post.getAuthorAvatarTemplate(),
                baseUrl
        );
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
     * message (see its theme preview), so no Telegram media or file model is involved. The prepared
     * body carries the post's images; the message only refers to them by id.
     *
     * <p>A floor can be drawn as more than one bubble (its text segments and its polls), and a run
     * of bubbles by one author is a group to upstream: only its last bubble carries the floor's
     * reactions, the way a chat puts the chips under the last message of a run.</p>
     */
    public static MessageObject postMessage(
            Post post, long topicId, HashMap<Long, TLRPC.User> users, PreparedPostBody body,
            String baseUrl) {
        TLRPC.User user = author(post, baseUrl);
        users.put(user.id, user);

        TLRPC.TL_message message = localMessage(
                (int) post.getId(),
                epochSeconds(post.getCreatedAt()),
                topicId,
                user.id,
                new TLRPC.TL_messageMediaEmpty());
        message.rich_message = DiscourseCookedHtml.richMessage(body);
        for (PostAttachment attachment : body.getAttachments()) {
            message.rich_message.documents.add(DiscourseImageBridge.INSTANCE.documentFor(attachment));
        }
        return messageObject(message, users);
    }

    /**
     * A floor's reactions as the chips upstream draws at the bottom of a bubble. Upstream reads the
     * order it is given, so the order here is the order the site sent. They belong to the bubble at
     * the bottom of a floor's run, which is where a chat draws the chips under a run of messages.
     */
    public static void attachReactions(MessageObject messageObject, Post post) {
        if (messageObject != null && messageObject.messageOwner != null) {
            messageObject.messageOwner.reactions = reactions(post);
        }
    }

    /**
     * The message shape every bubble of a floor shares: one author, one floor, and no Telegram
     * state. The peer is the topic rather than the author's own user peer, which is what upstream's
     * group chats look like and what makes its cells measure a bubble with room for the author's
     * avatar - a message from a user peer is measured full width, which pushes an inset bubble past
     * the viewport (a wide table is what makes it visible).
     */
    public static TLRPC.TL_message localMessage(
            int messageId, int date, long topicId, long userId, TLRPC.MessageMedia media) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = messageId;
        message.date = date;
        message.dialog_id = topicId;
        // Local-message flag set used by upstream's own preview messages.
        message.flags = 257 + 8;
        message.message = "";
        message.media = media;
        message.out = false;
        // Nothing a forum post holds is ours to hand on, so no bubble may offer to forward one:
        // this is the flag upstream's own cells read to decide whether to draw the arrow at all.
        message.noforwards = true;
        message.from_id = peer(userId);
        message.peer_id = chatPeer(topicId);
        return message;
    }

    /** Wraps a locally built message the way the stock cells expect to receive it. */
    public static MessageObject messageObject(
            TLRPC.Message message, HashMap<Long, TLRPC.User> users) {
        return new MessageObject(
                0, message, null, users, new HashMap<>(), null, null,
                true, false, 0, false, false, false);
    }

    /**
     * A post's reactions as the chips upstream draws at the bottom of a bubble. Upstream reads the
     * order it is given, so the order here is the order the site sent.
     */
    private static TLRPC.TL_messageReactions reactions(Post post) {
        if (post.getReactions().isEmpty()) {
            return null;
        }
        TLRPC.TL_messageReactions reactions = new TLRPC.TL_messageReactions();
        reactions.results = new ArrayList<>();
        reactions.recent_reactions = new ArrayList<>();
        for (io.github.baba983.nativedo.core.model.PostReaction reaction : post.getReactions()) {
            TLRPC.TL_reactionEmoji emoji = new TLRPC.TL_reactionEmoji();
            emoji.emoticon = DiscourseReactions.INSTANCE.emoticonOf(reaction.getId());
            TLRPC.TL_reactionCount count = new TLRPC.TL_reactionCount();
            count.reaction = emoji;
            count.count = Math.max(1, reaction.getCount());
            count.chosen_order = reaction.getReacted() ? 1 : 0;
            reactions.results.add(count);
        }
        return reactions;
    }

    private static TLRPC.TL_peerUser peer(long userId) {
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = userId;
        return peer;
    }

    /**
     * The topic as a chat peer. Nothing is fetched or looked up for it - upstream only asks whether
     * the peer is a chat when it decides how wide a bubble may be - so a topic needs no Telegram chat
     * behind it, just the shape.
     */
    private static TLRPC.TL_peerChat chatPeer(long topicId) {
        TLRPC.TL_peerChat peer = new TLRPC.TL_peerChat();
        peer.chat_id = (int) topicId;
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
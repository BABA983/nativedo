package io.github.baba983.nativedo.topic;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;

import java.text.ParsePosition;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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

    /**
     * One row of the topic list. [readInFull] is the reader's own state for the topic rather than
     * the feed's: a topic they have read to its end draws no badge even when the feed the reader is
     * holding was answered before the site had taken that in.
     */
    public static DialogCell.CustomDialog topicRow(TopicSummary topic, boolean readInFull) {
        DialogCell.CustomDialog row = new DialogCell.CustomDialog();
        row.id = (int) (topic.getId() & 0x7fffffff);
        row.name = topic.getTitle();
        row.message = summaryLine(topic);
        row.date = epochSeconds(topic.getLastPostedAt());
        row.unread_count = readInFull ? 0 : topic.getUnreadCount();
        row.pinned = topic.isPinned();
        row.muted = topic.isMuted();
        return row;
    }

    /**
     * One row of the bookmarks list. A bookmark is a post in a topic, and the site lists it with what
     * the row needs: the topic's title, the post's excerpt and when the reader bookmarked it. Nothing
     * on it is unread - a bookmark is a thing the reader chose to keep.
     */
    public static DialogCell.CustomDialog bookmarkRow(io.github.baba983.nativedo.core.model.Bookmark bookmark) {
        DialogCell.CustomDialog row = new DialogCell.CustomDialog();
        row.id = (int) (bookmark.getTopicId() & 0x7fffffff);
        row.name = bookmark.getTitle();
        row.message = bookmark.getExcerpt();
        row.date = epochSeconds(bookmark.getCreatedAt());
        return row;
    }

    /** The topic's author, so the list row can draw their avatar instead of initials. */
    public static TLRPC.User topicAuthor(TopicSummary topic, String baseUrl) {
        return DiscourseAvatars.INSTANCE.user(
                topic.getAuthorUsername(),
                "",
                topic.getAuthorAvatarTemplate() == null ? "" : topic.getAuthorAvatarTemplate(),
                baseUrl,
                topic.getAuthorAnimatedAvatarUrl()
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
                baseUrl,
                post.getAuthorAnimatedAvatarUrl()
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
        return postMessage(post, topicId, users, body, baseUrl, null);
    }

    /**
     * One post as a message, answerable to another floor: when [replyTo] is given the message
     * carries upstream's reply header and the target's snapshot, so the bubble draws the reply
     * preview and a tap on it can find where it points.
     */
    public static MessageObject postMessage(
            Post post, long topicId, HashMap<Long, TLRPC.User> users, PreparedPostBody body,
            String baseUrl, TLRPC.Message replyTo) {
        TLRPC.User user = author(post, baseUrl);
        users.put(user.id, user);

        TLRPC.TL_message message = localMessage(
                (int) post.getId(),
                epochSeconds(post.getCreatedAt()),
                topicId,
                user.id,
                new TLRPC.TL_messageMediaEmpty());
        applyAuthorTitle(message, post);
        message.rich_message = DiscourseCookedHtml.richMessage(body);
        for (PostAttachment attachment : body.getAttachments()) {
            message.rich_message.documents.add(DiscourseImageBridge.INSTANCE.documentFor(attachment));
        }
        for (PostVideo video : body.getVideos()) {
            message.rich_message.documents.add(DiscourseImageBridge.INSTANCE.videoDocument(video));
        }
        if (replyTo != null) {
            applyReply(message, replyTo);
        }
        return messageObject(message, users);
    }

    /**
     * The site's own user title (`user_title`) as the tag a chat draws beside the sender's name.
     * Upstream reads {@code from_rank} for exactly that slot: a short label after the name, never a
     * rank this client asks Telegram for. An empty title leaves the message without one.
     */
    /** How wide an author's title tag may grow before it is ellipsized, in dp. */
    private static final int AUTHOR_TITLE_MAX_WIDTH_DP = 100;

    public static void applyAuthorTitle(TLRPC.Message message, Post post) {
        if (message == null || post == null) {
            return;
        }
        DiscourseUserTitles.INSTANCE.install();
        String title = post.getAuthorTitle();
        if (title == null) {
            return;
        }
        title = title.replace('\n', ' ').replace('\r', ' ').trim();
        if (title.isEmpty()) {
            return;
        }
        // The tag is one short label on the name line, not a paragraph: a site title that runs
        // longer is ellipsized so its layout cannot wrap over the body or take the whole name
        // width from the author. The name has upstream's own ellipsis after this reservation.
        if (Theme.chat_adminPaint != null) {
            title = TextUtils.ellipsize(
                    title,
                    Theme.chat_adminPaint,
                    AndroidUtilities.dp(AUTHOR_TITLE_MAX_WIDTH_DP),
                    TextUtils.TruncateAt.END
            ).toString();
        }
        message.from_rank = title;
    }

    /**
     * Writes upstream's reply header onto a locally built message: the flags a chat message carries
     * when it answers another one. Shared by every kind of bubble - text, poll, onebox - so a reply
     * reads the same whichever kind of bubble happens to be the floor's first.
     */
    public static void applyReply(TLRPC.Message message, TLRPC.Message replyTo) {
        if (message == null || replyTo == null) {
            return;
        }
        message.replyMessage = replyTo;
        TLRPC.TL_messageReplyHeader replyHeader = new TLRPC.TL_messageReplyHeader();
        // The header's own presence flag (bit 4, the one upstream's composer sets for a
        // reply_to_msg_id - TL_messageReplyHeader.FLAG_4).
        replyHeader.flags |= 16;
        replyHeader.reply_to_msg_id = replyTo.id;
        message.reply_to = replyHeader;
        message.flags |= TLRPC.MESSAGE_FLAG_REPLY;
    }

    /**
     * A reply's target as a plain message the preview can draw: the target's author and its text.
     *
     * <p>The real floor keeps its own rich body; this snapshot is a separate message, read only by
     * the reply block upstream draws on the answering bubble, so it is a text message rather than a
     * copy of the target's rich layout. That is what puts the author's name and the target's text
     * into the preview, which upstream reads from {@code replyMessageObject.messageText}.</p>
     */
    public static TLRPC.Message replySnapshot(
            Post target, long topicId, HashMap<Long, TLRPC.User> users, String baseUrl, String text) {
        TLRPC.User user = author(target, baseUrl);
        users.put(user.id, user);
        TLRPC.TL_message snapshot = localMessage(
                (int) target.getId(),
                epochSeconds(target.getCreatedAt()),
                topicId,
                user.id,
                new TLRPC.TL_messageMediaEmpty());
        snapshot.message = text == null ? "" : text;
        snapshot.date = epochSeconds(target.getCreatedAt());
        return snapshot;
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
        // Which messages this client built, for the hooks that must answer for a forum post and
        // not for a real Telegram message (the window's FLAG_SECURE gate).
        io.github.baba983.nativedo.hooks.NativeDoHooks.markClientMessage(message);
        message.from_id = peer(userId);
        message.peer_id = chatPeer(topicId);
        return message;
    }

    /**
     * Wraps a locally built message the way the stock cells expect to receive it.
     *
     * <p>Upstream queues a message that carries views or a reply footer for a views read, which is
     * Telegram's own bookkeeping and has nothing behind it here: the message is marked reloaded so
     * the stock cell never asks. That is the one bit the footer costs - no chat, no request.</p>
     */
    public static MessageObject messageObject(
            TLRPC.Message message, HashMap<Long, TLRPC.User> users) {
        MessageObject object = new MessageObject(
                0, message, null, users, new HashMap<>(), null, null,
                true, false, 0, false, false, false);
        object.viewsReloaded = true;
        return object;
    }

    /**
     * A direct reply's author as the peer the reply footer draws a face from. The id is the same
     * username hash the author's own messages carry, so a face and its author are one person.
     */
    public static TLRPC.Peer replierPeer(String username) {
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = userId(username);
        return peer;
    }

    /**
     * Writes the reply footer onto a floor's last bubble: the site's own {@code reply_count} as the
     * authoritative total, and the faces of the direct replies this session has loaded.
     *
     * <p>The total is the site's and never the loaded subset: a floor with five direct replies of
     * which three have been read says five. An unread reply has no face, and none is invented for
     * it. A floor with no replies gets no footer at all.</p>
     */
    public static void attachReplyFooter(
            MessageObject messageObject, int replyCount, long topicId, List<TLRPC.Peer> recentRepliers) {
        if (messageObject == null || messageObject.messageOwner == null || replyCount <= 0) {
            return;
        }
        TLRPC.TL_messageReplies replies = new TLRPC.TL_messageReplies();
        replies.replies = replyCount;
        replies.channel_id = topicId;
        if (recentRepliers != null && !recentRepliers.isEmpty()) {
            replies.recent_repliers = new ArrayList<>(recentRepliers);
        }
        messageObject.messageOwner.replies = replies;
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

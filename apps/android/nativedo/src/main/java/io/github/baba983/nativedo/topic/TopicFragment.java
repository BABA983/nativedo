package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.Post;
import io.github.baba983.nativedo.core.model.TopicDetail;

/**
 * One linux.do topic: every post is drawn by upstream's stock {@link ChatMessageCell} from a
 * {@code TL_message.rich_message} body, which is how upstream itself previews a locally built rich
 * message. Taps route to upstream's article photo viewer and {@link Browser}.
 */
public class TopicFragment extends BaseFragment {

    private static final String ARG_TOPIC_ID = "topic_id";
    private static final String ARG_TOPIC_TITLE = "topic_title";

    /**
     * How many posts one page of a long topic asks for. Discourse servers the ids of the whole
     * stream with the topic, so a page is a fetch of the next slice of ids.
     */
    private static final int PAGE_SIZE = 20;

    /** How close to the end the list has to come before the next page is asked for. */
    private static final int LOAD_MORE_THRESHOLD = 4;

    /** The document picker's request code; both attach entry points start it. */
    private static final int PICK_ATTACHMENT_REQUEST = 0x4e44;

    /** The height of our own attach strip, which sits on top of the composer. */
    private static final int STRIP_HEIGHT_DP = 36;

    /** Upstream fragments take their arguments through the constructor, not a setter. */
    public TopicFragment(android.os.Bundle args) {
        super(args);
    }

    private PostListView listView;
    private final List<MessageObject> posts = new ArrayList<>();
    private final List<List<PostImage>> postImages = new ArrayList<>();
    private final List<List<PostAttachment>> postAttachments = new ArrayList<>();
    /** The site's own id per row; a post's id is a long, while a Telegram message id is an int. */
    private final List<Long> postIds = new ArrayList<>();
    /** The post's own HTML, kept for copy: the rich body is a model, not text to put on a clipboard. */
    private final List<String> cookedBodies = new ArrayList<>();
    /** The username to address when answering a post. */
    private final List<String> cookedAuthors = new ArrayList<>();
    private final HashMap<Long, TLRPC.User> users = new HashMap<>();
    /** Per author, the avatar template that produced their profile photo; the file is fetched from it. */
    private final HashMap<Long, String> avatarTemplates = new HashMap<>();
    private org.telegram.messenger.ChatMessageSharedResources sharedResources;
    private AppContainer container;
    private long topicId;
    private ChatActivityEnterView composer;
    /** The floor this reply answers, when the reader picked one in the post menu. */
    private Integer replyToPostNumber;
    /** Our own attach entry point, for the files upstream's button cannot offer (see its builder). */
    private View attachStrip;
    private android.widget.TextView attachStripLabel;
    private boolean uploadingAttachment;
    /** Post numbers per row: replying is by number, and the payload does not always carry them. */
    private final List<Integer> postNumbers = new ArrayList<>();
    private List<Long> streamPostIds = new ArrayList<>();
    private int nextPageStart;
    private boolean loadingPage;

    public static TopicFragment forTopic(long topicId, String title) {
        android.os.Bundle args = new android.os.Bundle();
        args.putLong(ARG_TOPIC_ID, topicId);
        args.putString(ARG_TOPIC_TITLE, title);
        return new TopicFragment(args);
    }

    /**
     * The picker's answer: the file is uploaded to the site, and what the field receives is the
     * markdown the site's own composer would have written for it.
     */
    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != PICK_ATTACHMENT_REQUEST
                || resultCode != android.app.Activity.RESULT_OK
                || data == null
                || getParentActivity() == null
                || container == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        io.github.baba983.nativedo.core.media.PickedMedia picked =
                io.github.baba983.nativedo.core.media.PickedMediaResolver.INSTANCE.resolve(getParentActivity(), uri);
        if (picked == null) {
            showReplyError(new java.io.IOException("无法读取所选文件"));
            return;
        }
        setUploadingAttachment(true);
        DiscourseLoader.INSTANCE.uploadReplyMedia(container, picked, markup -> {
            io.github.baba983.nativedo.core.media.ReplyMarkupKt.insertReplyMarkup(
                    composer == null ? null : composer.getEditField(), markup);
            setUploadingAttachment(false);
            updateComposerActions();
            return kotlin.Unit.INSTANCE;
        }, error -> {
            setUploadingAttachment(false);
            showReplyError(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    @Override
    public boolean onFragmentCreate() {
        currentAccount = 0;
        return super.onFragmentCreate();
    }

    @Override
    protected View createView(Context context) {
        long topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        String title = getArguments() == null ? null : getArguments().getString(ARG_TOPIC_TITLE);
        actionBar.setTitle(title == null ? "话题" : title);
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        sharedResources = new org.telegram.messenger.ChatMessageSharedResources(context);
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context);
        contentView.setBackgroundColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));
        listView = new PostListView(context);
        listView.setAvatarSource(senderId -> users.get(senderId));
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new PostAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                maybeLoadNextPage();
            }
        });

        // The composer floats at the bottom over the list, so the list keeps its last post clear.
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(52));
        contentView.addView(listView, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));
        attachStrip = createAttachStrip(context);
        contentView.addView(attachStrip, org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                AndroidUtilities.dp(STRIP_HEIGHT_DP),
                Gravity.BOTTOM | Gravity.LEFT));
        attachStrip.setVisibility(View.GONE);
        contentView.addView(createComposer(contentView), org.telegram.ui.Components.LayoutHelper.createFrame(
                org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        fragmentView = contentView;
        loadTopic(topicId);
        return fragmentView;
    }

    /**
     * Upstream's own composer, the one a chat screen uses. It takes the chat it writes to as a
     * parameter, and this client has no chat: everything that reads it belongs to Telegram's send
     * paths (attachments, stickers, voice, bots), which this screen does not offer, so it is given
     * none. What is left is the field, the emoji panel and the send button, and the send button
     * reports to the delegate — which is where the post goes to Discourse.
     */
    private android.view.View createComposer(SizeNotifierFrameLayout contentView) {
        ChatActivityEnterView view = new ChatActivityEnterView(
                getParentActivity(), contentView, null, true, resourceProvider);
        view.setAllowStickersAndGifs(false, false, false);
        view.setDelegate(new ReplyDelegate());
        // The composer decides the button again whenever it is laid out (a keyboard, an emoji panel,
        // a first layout), so the decision is repeated there too.
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            updateComposerActions();
            placeAttachStrip(v.getHeight());
        });
        composer = view;
        return view;
    }

    /**
     * The composer's own attach button is offered only while the field is empty — that is upstream's
     * design, and a forum reply often wants a second file after the first one has already put text
     * in the field. This strip appears exactly then (the reader keeps the paperclip while the field
     * is empty), and it appends the next upload's markdown to whatever is already written.
     */
    private View createAttachStrip(Context context) {
        android.widget.LinearLayout strip = new android.widget.LinearLayout(context);
        strip.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        strip.setBackgroundColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_chat_messagePanelBackground));

        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource(org.telegram.messenger.R.drawable.msg_input_attach2);
        icon.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_chat_messagePanelIcons),
                android.graphics.PorterDuff.Mode.MULTIPLY));
        strip.addView(icon, org.telegram.ui.Components.LayoutHelper.createLinear(
                AndroidUtilities.dp(24), AndroidUtilities.dp(24)));

        attachStripLabel = new android.widget.TextView(context);
        attachStripLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        attachStripLabel.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_chat_messagePanelHint));
        attachStripLabel.setText("添加图片或附件");
        attachStripLabel.setSingleLine(true);
        strip.addView(attachStripLabel, org.telegram.ui.Components.LayoutHelper.createLinear(
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, 0f, 8, 0, 0, 0));

        strip.setContentDescription("添加图片或附件");
        strip.setOnClickListener(v -> openAttachmentPicker());
        return strip;
    }

    /** The system's file picker; what it answers is uploaded by [onActivityResultFragment]. */
    private void openAttachmentPicker() {
        if (getParentActivity() == null || uploadingAttachment) {
            return;
        }
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_ATTACHMENT_REQUEST);
    }

    /**
     * Where the strip sits: directly on top of the composer, whose height is only known once it has
     * been laid out (it grows with a second line, an emoji panel, the keyboard).
     */
    private void placeAttachStrip(int composerHeight) {
        if (attachStrip == null) {
            return;
        }
        ViewGroup.LayoutParams params = attachStrip.getLayoutParams();
        if (params instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams frame = (android.widget.FrameLayout.LayoutParams) params;
            if (frame.bottomMargin != composerHeight) {
                frame.bottomMargin = composerHeight;
                attachStrip.setLayoutParams(frame);
            }
        }
    }

    /** Shows the strip only while the field has something in it; see [createAttachStrip]. */
    private void updateAttachStrip() {
        if (attachStrip == null || composer == null) {
            return;
        }
        boolean hasText = composer.getEditField() != null && composer.getEditField().length() > 0;
        int wanted = hasText ? View.VISIBLE : View.GONE;
        if (attachStrip.getVisibility() != wanted) {
            attachStrip.setVisibility(wanted);
            // The strip is an overlay on the list, so the list keeps its last post clear of it too.
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(hasText ? 52 + STRIP_HEIGHT_DP : 52));
        }
    }

    /** A pick that is being uploaded; the strip says so and takes no second tap. */
    private void setUploadingAttachment(boolean uploading) {
        uploadingAttachment = uploading;
        if (attachStrip == null || attachStripLabel == null) {
            return;
        }
        attachStrip.setEnabled(!uploading);
        attachStrip.setAlpha(uploading ? 0.5f : 1f);
        attachStripLabel.setText(uploading ? "上传中…" : "添加图片或附件");
    }

    private void loadTopic(long topicId) {
        this.topicId = topicId;
        container = NativeDo.container(getParentActivity());
        DiscourseLoader.INSTANCE.topic(container, topicId, detail -> {
            streamPostIds = detail.getStreamPostIds();
            bind(detail);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            org.telegram.messenger.FileLog.e("topic failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void bind(TopicDetail detail) {
        posts.clear();
        postImages.clear();
        postAttachments.clear();
        postIds.clear();
        postNumbers.clear();
        cookedBodies.clear();
        cookedAuthors.clear();
        users.clear();
        avatarTemplates.clear();
        List<Post> loaded = firstPage(detail);
        for (Post post : loaded) {
            add(post, detail.getId());
        }
        // The stock cell resolves an author through MessagesController, not through the map it was
        // built with, so the authors have to be registered there before the cells bind. putUser with
        // fromCache=true is a map write: no request, no database.
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (TLRPC.User user : users.values()) {
            controller.putUser(user, true);
        }
        notifyPostsChanged();
        for (int index = 0; index < posts.size(); index++) {
            fetchPostMedia(index);
        }
        fetchAvatars();
    }

    /**
     * Avatars are part of the author, not of the post, so they are fetched once per participant and
     * the rows are re-bound when one lands: the cell asked for the avatar when it was bound, and
     * upstream's list only draws one that has finished loading.
     */
    private void fetchAvatars() {
        if (container == null) {
            return;
        }
        for (Map.Entry<Long, String> entry : avatarTemplates.entrySet()) {
            TLRPC.User user = users.get(entry.getKey());
            String url = DiscourseAvatars.INSTANCE.url(
                    entry.getValue(), container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (user == null || url == null || DiscourseAvatars.INSTANCE.isCached(user)) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, user, () -> {
                // The cell resolves its author through MessagesController on every bind, so the
                // refreshed user has to go back in there before the row is rebuilt.
                MessagesController.getInstance(currentAccount).putUser(user, true);
                rebuildRows();
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    /**
     * The topic payload carries the posts the reader sees first plus the ids of the whole stream;
     * the pages after it fetch the next slice of those ids. A topic short enough to arrive whole
     * leaves nothing to fetch.
     */
    private List<Post> firstPage(TopicDetail detail) {
        List<Post> all = detail.getPosts();
        nextPageStart = Math.min(all.size(), streamPostIds.size());
        return all;
    }

    private void add(Post post, long topicId) {
        PreparedPostBody body = DiscourseImageBridge.INSTANCE.prepare(
                post.getCookedHtml(), container.getBaseUrl());
        String avatarTemplate = post.getAuthorAvatarTemplate();
        if (avatarTemplate != null && !avatarTemplate.isEmpty()) {
            avatarTemplates.put(DiscourseRows.userId(post.getAuthorUsername()), avatarTemplate);
        }
        MessageObject messageObject = DiscourseRows.postMessage(
                post, topicId, users, body, container.getBaseUrl());
        posts.add(messageObject);
        postIds.add(post.getId());
        postNumbers.add(post.getPostNumber());
        cookedBodies.add(post.getCookedHtml());
        cookedAuthors.add(post.getAuthorUsername());
        postImages.add(body.getImages());
        postAttachments.add(body.getAttachments());
    }

    /** Fetches the next slice of the topic's stream once the list nears its end. */
    private void maybeLoadNextPage() {
        if (loadingPage || container == null || listView == null) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        if (manager == null || manager.findLastVisibleItemPosition() < posts.size() - 1 - LOAD_MORE_THRESHOLD) {
            return;
        }
        if (nextPageStart >= streamPostIds.size()) {
            return;
        }
        int end = Math.min(nextPageStart + PAGE_SIZE, streamPostIds.size());
        List<Long> page = new ArrayList<>(streamPostIds.subList(nextPageStart, end));
        loadingPage = true;
        long topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        DiscourseLoader.INSTANCE.topicPosts(container, topicId, page, loaded -> {
            loadingPage = false;
            nextPageStart = end;
            int firstNew = posts.size();
            for (Post post : loaded) {
                add(post, topicId);
            }
            MessagesController controller = MessagesController.getInstance(currentAccount);
            for (TLRPC.User user : users.values()) {
                controller.putUser(user, true);
            }
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemRangeInserted(firstNew, posts.size() - firstNew);
            }
            for (int index = firstNew; index < posts.size(); index++) {
                fetchPostMedia(index);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            loadingPage = false;
            org.telegram.messenger.FileLog.e("topic page failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void toggleReaction(long postId, String reactionId) {
        if (container == null) {
            return;
        }
        DiscourseLoader.INSTANCE.toggleReaction(container, postId, reactionId, () -> {
            // The site is the only place that knows the new count, so the topic is read again.
            loadTopic(topicId);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * What the reader can do with a post: copy its text, copy a link to it, or reply to it. The
     * dialog is upstream's own, so it looks like every other confirmation in the app.
     */
    private void showMessageMenu(int index) {
        if (getParentActivity() == null) {
            return;
        }
        MessageObject message = posts.get(index);
        String[] items = new String[]{"复制", "复制链接", "回复"};
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                AndroidUtilities.addToClipboard(plainText(message));
            } else if (which == 1) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        getParentActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link", postUrl(index)));
            } else {
                replyTo(index);
            }
        });
        showDialog(builder.create());
    }

    /** What a copy takes: the text the rich body was built from, without its markup. */
    private String plainText(MessageObject message) {
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(
                message.messageOwner.rich_message == null ? "" : rawBodyOf(message));
        return document.text();
    }

    private String rawBodyOf(MessageObject message) {
        int index = posts.indexOf(message);
        return index >= 0 && index < cookedBodies.size() ? cookedBodies.get(index) : "";
    }

    private String postUrl(int index) {
        if (index >= postIds.size() || container == null) {
            return container == null ? "" : container.getBaseUrl();
        }
        return container.getBaseUrl() + "t/" + topicId + "/" + postIds.get(index);
    }

    /**
     * What the composer's own state decides for the reader: the send button and our attach strip.
     * Upstream offers its voice button while the field is empty and this client has nothing behind
     * it, so the button is hidden until there is something to send. It is told after every change
     * upstream makes, because those are the moments it decides the button's state again.
     */
    private void updateComposerActions() {
        updateSendButtonVisibility();
        updateAttachStrip();
    }

    private void updateSendButtonVisibility() {
        if (composer == null) {
            return;
        }
        boolean hasText = composer.getEditField() != null
                && composer.getEditField().length() > 0;
        android.view.View button = composer.getSendButton();
        if (button == null) {
            return;
        }
        int wanted = hasText ? android.view.View.VISIBLE : android.view.View.GONE;
        if (button.getVisibility() != wanted) {
            button.setVisibility(wanted);
        }
    }

    /**
     * Replying to one post is a post number, not a post id: the site numbers posts within a topic
     * and the composer does not need to know which floor it answers.
     */
    private void replyTo(int index) {
        MessageObject message = posts.get(index);
        replyToPostNumber = message.messageOwner == null ? null : null;
        Integer number = postNumbers.get(index);
        replyToPostNumber = number;
        // Discourse addresses a reply with @username, and the author of the post is what the
        // reader answers to.
        String author = index < cookedAuthors.size() ? cookedAuthors.get(index) : null;
        if (composer != null && author != null && !author.isEmpty() && number != null) {
            composer.setFieldText("@" + author + " ");
            composer.setFieldFocused();
            AndroidUtilities.showKeyboard(composer.getEditField());
        }
    }

    private void sendReply(String raw, Integer replyToPostNumber) {
        if (container == null || raw.trim().isEmpty()) {
            return;
        }
        DiscourseLoader.INSTANCE.reply(container, topicId, raw, replyToPostNumber, () -> {
            if (composer != null) {
                composer.setFieldText("");
            }
            this.replyToPostNumber = null;
            loadTopic(topicId);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            showReplyError(error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void showReplyError(Throwable error) {
        if (getParentActivity() == null) {
            return;
        }
        org.telegram.messenger.FileLog.e("reply failed", error);
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle("回复失败");
        builder.setMessage(error.getMessage() == null ? error.toString() : error.getMessage());
        builder.setPositiveButton("好", null);
        showDialog(builder.create());
    }

    /** What the composer tells us: the text, and that the send button was pressed. */
    private class ReplyDelegate implements ChatActivityEnterView.ChatActivityEnterViewDelegate {

        @Override
        public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
            String raw = message == null ? "" : message.toString().trim();
            if (!raw.isEmpty()) {
                sendReply(raw, replyToPostNumber);
            }
        }

        @Override
        public void needSendTyping() {
        }

        @Override
        public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {
            updateComposerActions();
        }

        @Override
        public void onTextSelectionChanged(int start, int end) {
        }

        @Override
        public void onTextSpansChanged(CharSequence text) {
        }

        @Override
        public void onAttachButtonHidden() {
        }

        @Override
        public void onAttachButtonShow() {
        }

        @Override
        public void onWindowSizeChanged(int size) {
            updateComposerActions();
        }

        @Override
        public void onStickersTab(boolean opened) {
        }

        @Override
        public void onMessageEditEnd(boolean loading) {
        }

        /** Attachments in a reply go to the site first: what the field gets is the markdown its own composer would write. */
        @Override
        public void didPressAttachButton() {
            openAttachmentPicker();
        }

        @Override
        public void needStartRecordVideo(int state, boolean notify, int scheduleDate, int scheduleRepeatPeriod, int ttl, long effectId, long stars) {
        }

        @Override
        public void toggleVideoRecordingPause() {
        }

        @Override
        public boolean isVideoRecordingPaused() {
            return false;
        }

        @Override
        public void needChangeVideoPreviewState(int state, float seekProgress) {
        }

        @Override
        public void onSwitchRecordMode(boolean video) {
        }

        @Override
        public void onPreAudioVideoRecord() {
        }

        @Override
        public void needStartRecordAudio(int state) {
        }

        @Override
        public void needShowMediaBanHint() {
        }

        @Override
        public void onStickersExpandedChange() {
        }

        @Override
        public void onUpdateSlowModeButton(android.view.View button, boolean show, CharSequence time) {
        }

        @Override
        public void onSendLongClick() {
        }

        @Override
        public void onAudioVideoInterfaceUpdated() {
        }
    }

    /**
     * The media blocks size themselves from the photo, so a cell can be bound before its image
     * exists; re-binding the row is what makes the cell ask again and find the file. A document
     * does the same with its button state, which only turns into "open" once the file is there.
     */
    private void fetchPostMedia(int index) {
        if (container == null) {
            return;
        }
        List<PostImage> images = postImages.get(index);
        List<PostAttachment> attachments = postAttachments.get(index);
        Runnable onReady = () -> {
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemChanged(index);
            }
        };
        MessageObject message = index < posts.size() ? posts.get(index) : null;
        Runnable onAttachmentsReady = () -> {
            if (message != null) {
                DiscourseImageBridge.INSTANCE.applyAttachmentFiles(message.messageOwner, attachments);
            }
            onReady.run();
        };
        DiscourseLoader.INSTANCE.cacheImages(container, images, () -> {
            onReady.run();
            return kotlin.Unit.INSTANCE;
        });
        DiscourseLoader.INSTANCE.cacheAttachments(container, attachments, () -> {
            onAttachmentsReady.run();
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * Builds the rows again.
     *
     * <p>A cell asks for its author's avatar once and skips the request while it still holds the
     * same one, and a row is always bound before the picture has been fetched; assigning the
     * adapter again is what replaces the cells, and with them the state that remembered the
     * unanswered request.</p>
     */
    private void rebuildRows() {
        if (listView == null || listView.getAdapter() == null) {
            return;
        }
        listView.setAdapter(listView.getAdapter());
    }

    private void notifyPostsChanged() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private class PostAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ChatMessageCell cell = new ChatMessageCell(
                    parent.getContext(), currentAccount, false, sharedResources, resourceProvider);
            cell.setDelegate(new PostCellDelegate());
            cell.isChat = true;
            cell.setFullyDraw(false);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject message = posts.get(position);
            cell.setMessageObject(message, null, false, false, false);
            // Upstream binds a rich body by creating its layout and attaching it in the same breath,
            // but only while the cell is already on screen. A cell that is bound before it is
            // attached (and every re-bind after a download) would leave the body's blocks without a
            // view, which is what they open files and photos through. The cell detaches the layout
            // again when it leaves the screen, so attaching here is what upstream does, just not
            // conditional on the order of the two.
            if (message.richLayout != null) {
                message.richLayout.attach(cell);
            }
        }

        @Override
        public int getItemCount() {
            return posts.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }
    }

    /** Upstream's own callbacks; a tap on a photo or a link leaves for upstream's viewers. */
    private class PostCellDelegate implements ChatMessageCell.ChatMessageCellDelegate {

        /**
         * A tap on one of the reaction chips. The id comes back from the emoticon the chip drew,
         * which is the only field a chip carries, so the mapping has to be reversible.
         */
        @Override
        public void didPressReaction(ChatMessageCell cell, TLRPC.ReactionCount reaction, boolean longpress, float x, float y) {
            if (longpress || reaction == null || !(reaction.reaction instanceof TLRPC.TL_reactionEmoji)) {
                return;
            }
            int index = posts.indexOf(cell.getMessageObject());
            if (index < 0 || index >= postIds.size()) {
                return;
            }
            String reactionId = DiscourseReactions.INSTANCE.idOf(
                    ((TLRPC.TL_reactionEmoji) reaction.reaction).emoticon);
            toggleReaction(postIds.get(index), reactionId);
        }

        /** Long press: the three things a reader does with someone else's post. */
        @Override
        public void didLongPress(ChatMessageCell cell, float x, float y) {
            int index = posts.indexOf(cell.getMessageObject());
            if (index < 0) {
                return;
            }
            showMessageMenu(index);
        }

        /** A tap on an image in a post body; the cell routes rich media through this one. */
        @Override
        public boolean openArticlePhoto(ChatMessageCell cell, org.telegram.tgnet.tl.TL_iv.PageBlock block) {
            return ArticlePhotoViewer.open(TopicFragment.this, cell.getMessageObject(), block);
        }

        @Override
        public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
            if (longPress || !(url instanceof URLSpan)) {
                return;
            }
            Browser.openUrl(getParentActivity(), ((URLSpan) url).getURL());
        }
    }
}

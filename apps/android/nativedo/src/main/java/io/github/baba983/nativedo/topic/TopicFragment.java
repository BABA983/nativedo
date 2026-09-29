package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

    /** Upstream fragments take their arguments through the constructor, not a setter. */
    public TopicFragment(android.os.Bundle args) {
        super(args);
    }

    private RecyclerListView listView;
    private final List<MessageObject> posts = new ArrayList<>();
    private final List<List<PostImage>> postImages = new ArrayList<>();
    private final HashMap<Long, TLRPC.User> users = new HashMap<>();
    private org.telegram.messenger.ChatMessageSharedResources sharedResources;
    private AppContainer container;
    private List<Long> streamPostIds = new ArrayList<>();
    private int nextPageStart;
    private boolean loadingPage;

    public static TopicFragment forTopic(long topicId, String title) {
        android.os.Bundle args = new android.os.Bundle();
        args.putLong(ARG_TOPIC_ID, topicId);
        args.putString(ARG_TOPIC_TITLE, title);
        return new TopicFragment(args);
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
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new PostAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                maybeLoadNextPage();
            }
        });

        fragmentView = listView;
        loadTopic(topicId);
        return fragmentView;
    }

    private void loadTopic(long topicId) {
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
        users.clear();
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
        for (int index = 0; index < postImages.size(); index++) {
            cacheImages(index);
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
        MessageObject messageObject = DiscourseRows.postMessage(post, topicId, users, body);
        posts.add(messageObject);
        postImages.add(body.getImages());
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
            for (int index = firstNew; index < postImages.size(); index++) {
                cacheImages(index);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            loadingPage = false;
            org.telegram.messenger.FileLog.e("topic page failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /**
     * The media blocks size themselves from the photo, so a cell can be bound before its image
     * exists; re-binding the row is what makes the cell ask again and find the file.
     */
    private void cacheImages(int index) {
        List<PostImage> images = postImages.get(index);
        if (images.isEmpty() || container == null) {
            return;
        }
        DiscourseLoader.INSTANCE.cacheImages(container, images, () -> {
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyItemChanged(index);
            }
            return kotlin.Unit.INSTANCE;
        });
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
            cell.setMessageObject(posts.get(position), null, false, false, false);
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

package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PhotoViewer;

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
 * message. Taps route to upstream's {@link PhotoViewer} and {@link Browser}.
 */
public class TopicFragment extends BaseFragment {

    private static final String ARG_TOPIC_ID = "topic_id";
    private static final String ARG_TOPIC_TITLE = "topic_title";

    /** Upstream fragments take their arguments through the constructor, not a setter. */
    public TopicFragment(android.os.Bundle args) {
        super(args);
    }

    private RecyclerListView listView;
    private final List<MessageObject> posts = new ArrayList<>();
    private final HashMap<Long, TLRPC.User> users = new HashMap<>();
    private ChatMessageSharedResources sharedResources;
    private long topicId;

    public static TopicFragment forTopic(long topicId, String title) {
        android.os.Bundle args = new android.os.Bundle();
        args.putLong(ARG_TOPIC_ID, topicId);
        args.putString(ARG_TOPIC_TITLE, title);
        return new TopicFragment(args);
    }

    @Override
    public boolean onFragmentCreate() {
        topicId = getArguments() == null ? 0 : getArguments().getLong(ARG_TOPIC_ID, 0);
        return super.onFragmentCreate();
    }

    @Override
    protected View createView(Context context) {
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

        sharedResources = new ChatMessageSharedResources(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new PostAdapter());
        listView.setVerticalScrollBarEnabled(false);

        fragmentView = listView;
        loadTopic();
        return fragmentView;
    }

    private void loadTopic() {
        AppContainer container = NativeDo.container(getParentActivity());
        DiscourseLoader.INSTANCE.topic(container, topicId, detail -> {
            bind(detail);
            return kotlin.Unit.INSTANCE;
        }, error -> {
            org.telegram.messenger.FileLog.e("topic failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    private void bind(TopicDetail detail) {
        posts.clear();
        users.clear();
        for (Post post : DiscourseLoader.INSTANCE.posts(detail)) {
            posts.add(DiscourseRows.postMessage(post, detail.getId(), users));
        }
        // The stock cell resolves an author through MessagesController, not through the map it was
        // built with, so the authors have to be registered there before the cells bind. putUser with
        // fromCache=true is a map write: no request, no database.
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (TLRPC.User user : users.values()) {
            controller.putUser(user, true);
        }
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

        @Override
        public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
            openPhoto(cell);
        }

        @Override
        public void didPressExtendedMediaPreview(ChatMessageCell cell, org.telegram.tgnet.tl.TL_keyboard.KeyboardInlineButton button) {
            openPhoto(cell);
        }

        @Override
        public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
            if (longPress || !(url instanceof URLSpan)) {
                return;
            }
            Browser.openUrl(getParentActivity(), ((URLSpan) url).getURL());
        }

        private void openPhoto(ChatMessageCell cell) {
            int index = posts.indexOf(cell.getMessageObject());
            if (index < 0) {
                return;
            }
            PhotoViewer.getInstance().setParentActivity(getParentActivity(), resourceProvider);
            PhotoViewer.getInstance().openPhoto(
                    new ArrayList<>(posts), index, 0, 0, 0,
                    new PhotoViewer.EmptyPhotoViewerProvider());
        }
    }
}
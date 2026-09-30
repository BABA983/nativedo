package io.github.baba983.nativedo.search;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.app.NativeDo;
import io.github.baba983.nativedo.core.di.AppContainer;
import io.github.baba983.nativedo.core.model.SearchResult;
import io.github.baba983.nativedo.core.model.SearchUserSummary;
import io.github.baba983.nativedo.core.model.TopicSummary;
import io.github.baba983.nativedo.feature.topic.data.TopicReadMarks;
import io.github.baba983.nativedo.profile.ProfileFragment;
import io.github.baba983.nativedo.topic.DiscourseAvatars;
import io.github.baba983.nativedo.topic.DiscourseLoader;
import io.github.baba983.nativedo.topic.DiscourseRows;
import io.github.baba983.nativedo.topic.TopicFragment;

/**
 * Search over the site, as an action bar's own field over {@code /search.json}.
 *
 * <p>The field is upstream's ({@code ActionBarMenuItem.setIsSearchField}), so typing, the clear
 * button, and the way back closes it are upstream's behaviour. The answer is three lists - the
 * topics the site matched, the posts, and the people - and every one of them is drawn by a cell
 * upstream draws that kind of row with: a topic is its {@link DialogCell} row, a post is the topic
 * it sits in with the post's own excerpt under the title, and a person is a
 * {@link ProfileSearchCell}. A hit opens what it is: a topic or a post its topic at the floor it
 * was found on, a person their site profile.</p>
 */
public class SearchFragment extends BaseFragment {

    private static final int TYPE_TOPIC = 0;
    private static final int TYPE_POST = 1;
    private static final int TYPE_PERSON = 2;
    private static final int TYPE_NOTE = 3;

    /**
     * How long the field waits after the last keystroke before the site is asked. A reader types a
     * word at a time; without this every letter is a request.
     */
    private static final int SEARCH_DELAY_MS = 300;

    private RecyclerListView listView;
    private final List<Hit> hits = new ArrayList<>();
    private ActionBarMenuItem searchItem;
    private AppContainer container;
    private Runnable pendingSearch;
    /** The query the screen is showing, so an answer to an older one can be dropped. */
    private String currentQuery = "";

    @Override
    protected View createView(Context context) {
        actionBar.setTitle("搜索");
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.outline_header_search)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onTextChanged(EditText editText) {
                        askAfterTyping(editText.getText().toString().trim());
                    }
                });
        searchItem.setSearchFieldHint("搜索 linux.do");
        // The reader came here to search, so the field is open on arrival.
        actionBar.openSearchField(false);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new HitAdapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < hits.size()) {
                open(hits.get(position));
            }
        });

        container = NativeDo.container(getParentActivity());
        fragmentView = listView;
        return fragmentView;
    }

    /** The field the reader types in; the render harness drives the screen through it. */
    ActionBarMenuItem searchFieldItem() {
        return searchItem;
    }

    /**
     * A hit is what it points at: a topic or a post in one opens that topic at the floor the hit is
     * about, and a person opens their site profile.
     */
    private void open(Hit hit) {
        if (hit.type == TYPE_PERSON) {
            presentFragment(ProfileFragment.forUser(hit.person.getUsername(), hit.person.getName()));
            return;
        }
        if (hit.topic != null) {
            presentFragment(TopicFragment.forFloor(
                    hit.topic.getId(), hit.topic.getTitle(), hit.topic.getTargetPostNumber()));
        }
    }

    /**
     * The site is asked once the reader stops typing, and only for the query that is still in the
     * field when the answer arrives: a slow answer to a query the reader has since changed would
     * otherwise replace the rows with results for something else.
     */
    private void askAfterTyping(String query) {
        currentQuery = query;
        if (pendingSearch != null) {
            AndroidUtilities.cancelRunOnUIThread(pendingSearch);
            pendingSearch = null;
        }
        if (query.isEmpty()) {
            hits.clear();
            notifyHitsChanged();
            return;
        }
        pendingSearch = () -> {
            pendingSearch = null;
            ask(query);
        };
        AndroidUtilities.runOnUIThread(pendingSearch, SEARCH_DELAY_MS);
    }

    private void ask(String query) {
        if (container == null) {
            return;
        }
        DiscourseLoader.INSTANCE.search(container, query, result -> {
            if (query.equals(currentQuery)) {
                show(result);
            }
            return kotlin.Unit.INSTANCE;
        }, error -> {
            FileLog.e("search failed", error);
            return kotlin.Unit.INSTANCE;
        });
    }

    /** The site's answer as rows: its topics, its posts, then the people it matched. */
    private void show(SearchResult result) {
        hits.clear();
        for (TopicSummary topic : result.getTopics()) {
            hits.add(topicHit(TYPE_TOPIC, topic));
        }
        for (TopicSummary post : result.getPosts()) {
            hits.add(topicHit(TYPE_POST, post));
        }
        for (SearchUserSummary person : result.getUsers()) {
            hits.add(new Hit(
                    TYPE_PERSON, null, person,
                    DiscourseAvatars.INSTANCE.user(
                            person.getUsername(),
                            person.getName(),
                            person.getAvatarTemplate(),
                            container.getBaseUrl()),
                    person.getAvatarTemplate(), null));
        }
        if (hits.isEmpty()) {
            hits.add(new Hit(TYPE_NOTE, null, null, null, "", "没有找到相关内容"));
        }
        notifyHitsChanged();
        fetchAvatars();
    }

    /** A topic hit and a post hit are the same row: the topic, drawn with its author's picture. */
    private Hit topicHit(int type, TopicSummary topic) {
        return new Hit(
                type, topic, null,
                DiscourseRows.topicAuthor(topic, container.getBaseUrl()),
                topic.getAuthorAvatarTemplate(), null);
    }

    /**
     * The rows draw the author's initials until their picture is on disk, so each row's avatar is
     * fetched once and the row is re-bound when it lands.
     */
    private void fetchAvatars() {
        for (Hit hit : hits) {
            String url = DiscourseAvatars.INSTANCE.url(
                    hit.avatarTemplate, container.getBaseUrl(), DiscourseAvatars.DEFAULT_SIZE);
            if (url == null || hit.peer == null || DiscourseAvatars.INSTANCE.isCached(hit.peer)) {
                continue;
            }
            DiscourseLoader.INSTANCE.cacheAvatar(container, url, hit.peer, () -> {
                notifyHitsChanged();
                return kotlin.Unit.INSTANCE;
            });
        }
    }

    private void notifyHitsChanged() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private class HitAdapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            if (viewType == TYPE_PERSON) {
                // The cell upstream draws a person with in its own search results: a picture, a
                // name, and one line under it.
                ProfileSearchCell cell = new ProfileSearchCell(context, resourceProvider);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            if (viewType == TYPE_NOTE) {
                TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context, resourceProvider);
                cell.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            // A topic hit and a post hit are both a topic: the row the topic list draws, with the
            // excerpt the site sent. A post hit's excerpt is the post's own blurb.
            DialogCell cell = new DialogCell(null, context, false, false, currentAccount, resourceProvider);
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(72)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Hit hit = hits.get(position);
            if (holder.itemView instanceof DialogCell) {
                DialogCell cell = (DialogCell) holder.itemView;
                cell.setDialog(DiscourseRows.topicRow(
                        hit.topic, TopicReadMarks.INSTANCE.isReadInFull(hit.topic.getId())));
                ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(hit.peer);
                if (avatar != null) {
                    cell.avatarImage.setImage(
                            avatar, "50_50", cell.avatarImage.getDrawable(), null, hit.peer, 0);
                }
            } else if (holder.itemView instanceof ProfileSearchCell) {
                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                cell.setData(
                        hit.peer, null, hit.peer.first_name, "@" + hit.person.getUsername(), false, false);
                ImageLocation avatar = DiscourseAvatars.INSTANCE.imageLocation(hit.peer);
                if (avatar != null) {
                    cell.avatarImage.setImage(
                            avatar, "50_50", cell.avatarImage.getDrawable(), null, hit.peer, 0);
                }
            } else if (holder.itemView instanceof TextInfoPrivacyCell) {
                ((TextInfoPrivacyCell) holder.itemView).setText(hit.note);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return hits.get(position).type;
        }

        @Override
        public int getItemCount() {
            return hits.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            // A note is not a link; every hit is.
            return !(holder.itemView instanceof TextInfoPrivacyCell);
        }
    }

    /** One row of the answer: a topic, a post inside one, a person, or the note that nothing matched. */
    private static class Hit {
        final int type;
        final TopicSummary topic;
        final SearchUserSummary person;
        /** The synthetic site user the row draws a picture for; null for the note. */
        final TLRPC.User peer;
        final String avatarTemplate;
        final String note;

        private Hit(
                int type, TopicSummary topic, SearchUserSummary person, TLRPC.User peer,
                String avatarTemplate, String note) {
            this.type = type;
            this.topic = topic;
            this.person = person;
            this.peer = peer;
            this.avatarTemplate = avatarTemplate == null ? "" : avatarTemplate;
            this.note = note;
        }
    }
}

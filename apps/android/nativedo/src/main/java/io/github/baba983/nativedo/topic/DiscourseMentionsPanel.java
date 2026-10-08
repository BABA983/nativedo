// SPDX-License-Identifier: GPL-2.0-or-later
// Port of org/telegram/ui/Components/MentionsContainerView.java: upstream's mention panel, bound to linux.do.

package io.github.baba983.nativedo.topic;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MentionsAdapter;
import org.telegram.ui.Components.MentionsContainerView;

import java.util.List;

import io.github.baba983.nativedo.core.model.MentionCandidate;
import io.github.baba983.nativedo.hooks.NativeDoHooks;

/**
 * Upstream's mention panel ({@code MentionsContainerView}), opened by this client on its composer.
 *
 * <p>The panel is upstream's own - its list measurement, recycling, round-rect clipping and spring
 * reveal are untouched - and this subclass only supplies the rows' data. It is the
 * {@link NativeDoHooks.MentionsHost} bound to the instance: upstream's one construction site hands
 * this panel straight in (see {@code MentionsContainerView}, one {@code NativeDoHooks} line) and
 * gets the site's adapter back, with no process-wide registry. A real Telegram panel is not this
 * class, so Telegram keeps its own adapter.</p>
 */
public class DiscourseMentionsPanel extends MentionsContainerView implements NativeDoHooks.MentionsHost {

    public interface Listener {
        void onCandidateSelected(@NonNull MentionCandidate candidate);
    }

    private String baseUrl = "";
    private boolean darkTheme;
    private Listener listener;

    public DiscourseMentionsPanel(
            @NonNull Context context,
            @Nullable BaseFragment baseFragment,
            Theme.ResourcesProvider resourcesProvider) {
        super(context, 0L, 0L, baseFragment, resourcesProvider);
        // The padding row PaddedListAdapter adds is position 0; a real candidate is one past it.
        getListView().setOnItemClickListener((view, position) -> {
            if (position <= 0 || listener == null) {
                return;
            }
            MentionCandidate candidate = adapter().itemAt(position - 1);
            if (candidate != null) {
                listener.onCandidateSelected(candidate);
            }
        });
    }

    /** Answered from the panel's own construction with the panel as the host; see the class doc. */
    @Override
    public MentionsAdapter createMentionsAdapter(
            MentionsAdapter upstream,
            MentionsContainerView container,
            Context context,
            long dialogId,
            long threadMessageId,
            Theme.ResourcesProvider resourcesProvider) {
        // Upstream builds its Telegram-backed adapter and this replaces it; that first adapter
        // registered notification observers of its own, so it must be torn down here or they leak.
        upstream.onDestroy();
        return new DiscourseMentionsAdapter(this, context, dialogId, threadMessageId, resourcesProvider);
    }

    private DiscourseMentionsAdapter adapter() {
        return (DiscourseMentionsAdapter) getAdapter();
    }

    /** The site the candidates' avatars are fetched from. */
    public void setMentionBaseUrl(String url) {
        baseUrl = url == null ? "" : url;
    }

    public void setMentionDarkTheme(boolean dark) {
        darkTheme = dark;
        refreshRows();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Tears the adapter down. Upstream's adapter registered notification observers at construction,
     * so the controller calls this from its own destroy; otherwise they outlive the screen and a
     * rebuilt composer doubles every observer it adds.
     */
    public void destroy() {
        MentionsAdapter current = getAdapter();
        if (current != null) {
            current.onDestroy();
        }
    }

    /** Shows the ordered candidates, or hides the panel when there are none. */
    public void submit(List<MentionCandidate> candidates) {
        adapter().submit(candidates);
        updateVisibility(!candidates.isEmpty());
        refreshRows();
    }

    /** Hides the panel and lets upstream's own spring put it away. */
    public void hide() {
        updateVisibility(false);
    }

    /** Re-binds the visible rows, used after an avatar has landed on disk. */
    public void refreshRows() {
        adapter().rebindAll();
    }

    public boolean isShowing() {
        return isOpen();
    }

    String mentionBaseUrl() {
        return baseUrl;
    }

    boolean mentionIsDarkTheme() {
        return darkTheme;
    }
}

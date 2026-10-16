// SPDX-License-Identifier: GPL-2.0-or-later
// Port of org/telegram/ui/Components/ChatAttachAlert.java: upstream's attachment alert, bound to
// Discourse instead of a Telegram account.

package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlert.ChatAttachViewDelegate;
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout;
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout;
import org.telegram.ui.iv.BlockRow;
import org.telegram.ui.iv.ChatAttachAlertRichLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.baba983.nativedo.hooks.NativeDoHooks;

/**
 * The paperclip's attachment sheet: upstream's own {@link ChatAttachAlert}, bound to Discourse.
 *
 * <p>This is not a lookalike. Upstream's alert is the real container - its {@code BottomSheet}
 * drag, its glass tab capsule, its {@code RecyclerListView} of tabs, its pane transitions, its
 * photo grid, its document browser and its rich article pane. What this subclass changes is only
 * what the tabs mean: the gallery and files panes hand their selection to this client's own
 * URI upload path, the article pane is seeded and read back as Discourse markdown, and the poll
 * pane is the site's own poll raw rather than a Telegram poll message. Nothing here builds a
 * Telegram message, reaches Telegram RPC or writes Telegram's database.</p>
 *
 * <p>The alert's own account-backed behavior is answered through the instance-bound
 * {@link NativeDoHooks.AttachSheetHost} seam: this alert is the host, so the upstream call sites
 * hand the alert straight in and a real Telegram alert (not an instance of the interface) runs
 * unchanged. The tab set, the poll pane and the rich pane are the three places that seam is
 * reached.</p>
 *
 * <p>The screen owns the field the raw lands in. This sheet reports what the reader chose through
 * {@link Listener} (a picked URI set, the applied markdown) and {@link PollSink} (the site's poll
 * raw); it never writes a Telegram draft or sends a Telegram message. Leaving the article pane
 * saves supported text back into the host draft; other cancelled panes leave the field alone.</p>
 */
public class DiscourseAttachSheet extends ChatAttachAlert
        implements NativeDoHooks.AttachSheetHost, ChatAttachViewDelegate,
        ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate {

    /** The actions the site can carry. Wire them to the content picker, the rich editor or a poll. */
    public enum Action {
        GALLERY,
        FILES,
        RICH_EDITOR,
        POLL,
    }

    /** What a chosen tab means to the host screen. */
    public interface Listener {
        /**
         * A tab that hands off to the screen's own picker (the files pane's system-file entry) or
         * that the screen owns end to end. The gallery and files panes do not use this for a picked
         * file: they report it through {@link #onAttachUris(List)}.
         */
        void onAttachAction(Action action);

        /**
         * Files the reader picked in the gallery or files pane, as local URIs for the screen's own
         * upload path. Never a Telegram media object.
         */
        default void onAttachUris(@NonNull List<Uri> uris) {
        }

        /** The markdown the reader built in the article pane. */
        default void onRichText(@NonNull String raw) {
        }
    }

    /**
     * Where the sheet's poll raw goes: the host writes the poll into its own field. The site's
     * {@code [poll]} raw, exactly as the site's own composer would write it.
     */
    public interface PollSink {
        void onPoll(String raw);
    }

    private final Listener listener;
    private final PollSink pollSink;
    private final String initialRaw;
    private final Theme.ResourcesProvider resourcesProvider;
    /** The site's own poll pane, created once and reused, so its identity is stable. */
    private ChatAttachAlert.AttachAlertLayout pollLayout;
    private List<BlockRow> articleRows;

    /**
     * @param host         the screen hosting the sheet; upstream uses it for the activity and the
     *                     clipboard, and this client uses it as the non-chat host fragment. It must
     *                     not be a {@code ChatActivity} - that path is Telegram's.
     * @param listener     where a chosen tab and its picked files go
     * @param pollSink     where the site's poll raw goes
     * @param initialRaw   the composer's markdown, seeding the article pane; may be null
     */
    public DiscourseAttachSheet(@NonNull Context context, @NonNull BaseFragment host,
            Theme.ResourcesProvider resourcesProvider, @NonNull Listener listener, @NonNull PollSink pollSink,
            @Nullable String initialRaw) {
        super(context, host, false, false, false, resourcesProvider);
        this.listener = listener;
        this.pollSink = pollSink;
        this.initialRaw = initialRaw;
        this.resourcesProvider = resourcesProvider;

        // The alert's own actions are answered by this sheet: the gallery exit and the files pane's
        // system-file entry. Upstream's Telegram send never runs because the delegate is this.
        setDelegate((ChatAttachViewDelegate) this);
        setDocumentsDelegate(this);
    }

    /**
     * Shows the alert the way upstream's chat does: {@code init()} picks the opening pane (the
     * gallery, the default) and lays out the tabs, then the sheet is shown. A caller that only
     * calls the ordinary dialog {@code show()} would leave the alert on a blank frame.
     */
    public void open() {
        init();
        show();
    }

    public void openArticle() {
        selectAction(Action.RICH_EDITOR);
    }

    @Override
    public void showLayout(ChatAttachAlert.AttachAlertLayout layout) {
        if (articleRows != null && getCurrentAttachLayout() instanceof ChatAttachAlertRichLayout
                && getCurrentAttachLayout() != layout) {
            if (!canCloseRichAttachPane(articleRows, true)) {
                return;
            }
            onRichAttachPersistDraft(articleRows);
            setTypeButtonsHidden(false, false);
        }
        super.showLayout(layout);
    }

    @Override
    public void setTypeButtonsHidden(boolean hidden, boolean animated) {
        super.setTypeButtonsHidden(hidden, animated);
        // Creating a seeded article hides the tabs before showLayout resets their alpha. Its
        // onShow repeats the same hidden state, which upstream treats as a no-op. Apply that
        // settled state again so the attachment capsule cannot cover the article toolbar.
        if (hidden && !animated) {
            buttonsRecyclerViewWrapper.setAlpha(0f);
            buttonsRecyclerViewWrapper.setVisibility(android.view.View.INVISIBLE);
        }
    }

    /** Drive the upstream tab's own action, including its layout registration and transitions. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void selectAction(Action action) {
        int position = action.ordinal();
        androidx.recyclerview.widget.RecyclerView.Adapter adapter = buttonsRecyclerView.getAdapter();
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                adapter.createViewHolder(buttonsRecyclerView, adapter.getItemViewType(position));
        adapter.bindViewHolder(holder, position);
        buttonsRecyclerView.getOnItemClickListener().onItemClick(holder.itemView, position);
    }

    // ------------------------------------------------------ lifecycle

    /**
     * Upstream's dismissal removes the panes it built ({@code layouts[1..]}) but leaves the gallery
     * ({@code layouts[0]}) and every {@code NotificationCenter} observer this alert registered
     * alive until the host calls {@link #onDestroy()}. A chat screen calls it from its own destroy;
     * this client's screens only {@link #dismiss()} the sheet, so without this the gallery keeps its
     * observers for the life of the activity and a dismissed alert is never really torn down.
     *
     * <p>This runs after upstream's own removal, so the panes upstream already destroyed are gone
     * and only the gallery and the alert's observers are torn down - exactly once. It is the
     * teardown of a sheet that has actually closed; a pane that refuses to close (an unsaved
     * article the site cannot store, upstream's {@code checkDiscard}) never reaches a dismissal at
     * all, and this writes nothing: Apply, back, pane changes and pause persist the article before
     * its rows are cleared, so teardown cannot double-write the composer's field.</p>
     */
    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (!destroyed) {
            onDestroy();
        }
    }

    /**
     * The screens keep their {@code attachSheet} field until the fragment is destroyed and call
     * these on it even after the reader dismissed it. Once the alert is torn down they are no-ops:
     * upstream's own {@code onPause}/{@code onResume} iterate the panes and the gallery, which no
     * longer have a live sheet to drive.
     */
    @Override
    public void onPause() {
        if (!destroyed) {
            if (!isDismissed() && articleRows != null
                    && getCurrentAttachLayout() instanceof ChatAttachAlertRichLayout) {
                onRichAttachPersistDraft(articleRows);
            }
            super.onPause();
        }
    }

    @Override
    public void onResume() {
        if (!destroyed) {
            super.onResume();
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (destroyed) {
            return;
        }
        super.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        if (requestCode == org.telegram.ui.BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE) {
            getPhotoLayout().checkStorage();
            MediaController.loadGalleryPhotosAlbums(0);
        }
    }

    // ------------------------------------------------------ NativeDoHooks.AttachSheetHost

    @Override
    public ChatAttachAlert.AttachAlertLayout attachSheetPollLayout(ChatAttachAlert alert) {
        if (pollLayout == null) {
            pollLayout = new DiscoursePollLayout(alert, alert.getContext(), resourcesProvider, raw -> {
                dismiss();
                pollSink.onPoll(raw);
            });
        }
        return pollLayout;
    }

    @Override
    public boolean attachSheetIsPollLayout(ChatAttachAlert.AttachAlertLayout layout) {
        return layout != null && layout == pollLayout;
    }

    /** The composer's markdown, as the HTML upstream's rich list seeds from. */
    @Override
    public String richAttachSeedHtml() {
        if (initialRaw == null || initialRaw.trim().isEmpty()) {
            return null;
        }
        return DiscourseRichText.INSTANCE.rawToHtml(initialRaw);
    }

    @Override
    public void bindRichAttachRows(List<BlockRow> rows) {
        articleRows = rows;
    }

    /**
     * The article pane's Apply. The markdown is written back to the composer and the pane closes;
     * content the site cannot store keeps the pane open and tells the reader, and the answer stays
     * "handled" so upstream's Telegram article send never runs for it.
     */
    @Override
    public boolean onRichAttachApply(List<BlockRow> rows) {
        try {
            listener.onRichText(DiscourseRichText.INSTANCE.blocksToRaw(rows));
            dismiss();
        } catch (DiscourseRichText.UnsupportedContentException unsupported) {
            showUnsupported(unsupported);
        }
        return true;
    }

    /**
     * The article pane is keeping the reader's work (a back press, a transition out). The markdown
     * is written back; the pane is already leaving.
     */
    @Override
    public boolean onRichAttachPersistDraft(List<BlockRow> rows) {
        try {
            listener.onRichText(DiscourseRichText.INSTANCE.blocksToRaw(rows));
        } catch (DiscourseRichText.UnsupportedContentException ignored) {
            // Nothing can be written for content the site cannot store; the draft is left alone.
        }
        return true;
    }

    @Override
    public boolean canCloseRichAttachPane(List<BlockRow> rows, boolean showError) {
        try {
            DiscourseRichText.INSTANCE.blocksToRaw(rows);
            return true;
        } catch (DiscourseRichText.UnsupportedContentException unsupported) {
            if (showError) {
                showUnsupported(unsupported);
            }
            return false;
        }
    }

    private void showUnsupported(DiscourseRichText.UnsupportedContentException unsupported) {
        BaseFragment base = getBaseFragment();
        if (base == null) {
            return;
        }
        BulletinFactory.of(base)
                .createSimpleBulletin(org.telegram.messenger.R.raw.error,
                        "暂不支持的内容：" + unsupported.getConstruct())
                .show();
    }

    // ------------------------------------------------------ ChatAttachViewDelegate

    /**
     * The alert's own done action. Action 7 is the gallery's exit: the reader's selected device
     * images become local URIs for the screen's upload path, then the sheet closes. Every other
     * button is Telegram's and is not run - this sheet has no Telegram send.
     */
    @Override
    public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate,
            int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
        if (button == 7) {
            List<Uri> picked = selectedPhotoUris();
            if (!picked.isEmpty()) {
                listener.onAttachUris(picked);
            }
            dismiss();
        }
    }

    // ------------------------------------------------------ DocumentSelectActivityDelegate

    /**
     * The files pane's own list produced paths. Each becomes a local URI for the screen's upload
     * path; the sheet closes.
     */
    @Override
    public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<TLRPC.MessageEntity> captionEntities,
            ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate, int scheduleRepeatPeriod,
            long effectId, boolean invertMedia, long payStars) {
        List<Uri> picked = new ArrayList<>();
        if (files != null) {
            for (String path : files) {
                if (path != null) {
                    picked.add(Uri.fromFile(new File(path)));
                }
            }
        }
        if (!picked.isEmpty()) {
            listener.onAttachUris(picked);
        }
        dismiss();
    }

    /**
     * The files pane's system-file entry: the screen's own SAF picker, whose result the screen
     * already uploads. The sheet closes first so the picker is not behind it.
     */
    @Override
    public void startDocumentSelectActivity() {
        dismiss();
        listener.onAttachAction(Action.FILES);
    }

    // ------------------------------------------------------------- selected media

    /** The gallery pane's selection as local URIs, in the order the reader picked it. */
    private List<Uri> selectedPhotoUris() {
        List<Uri> uris = new ArrayList<>();
        ChatAttachAlertPhotoLayout photoLayout = getPhotoLayout();
        if (photoLayout == null) {
            return uris;
        }
        HashMap<Object, Object> selected = photoLayout.getSelectedPhotos();
        ArrayList<Object> order = photoLayout.getSelectedPhotosOrder();
        if (selected == null || order == null) {
            return uris;
        }
        for (Object key : order) {
            Object item = selected.get(key);
            if (item instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photo = (MediaController.PhotoEntry) item;
                String path = photo.imagePath != null ? photo.imagePath : photo.path;
                if (path != null && !path.isEmpty()) {
                    uris.add(Uri.fromFile(new File(path)));
                }
            }
        }
        return uris;
    }
}

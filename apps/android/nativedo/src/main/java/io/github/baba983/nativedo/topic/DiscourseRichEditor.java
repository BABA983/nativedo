// SPDX-License-Identifier: GPL-2.0-or-later
// Port of org/telegram/ui/iv/RichEditor.java: upstream's editor screen, bound to Discourse.

package io.github.baba983.nativedo.topic;

import io.github.baba983.nativedo.hooks.NativeDoHooks;

import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.iv.BlockRow;
import org.telegram.ui.iv.RichEditor;

import java.util.List;
import java.util.function.Consumer;

/**
 * Upstream's rich editor ({@code org.telegram.ui.iv.RichEditor}), opened by this client on the
 * reader's own composer.
 *
 * <p>The screen is upstream's own - its block list, its toolbar, its formatting, its constants -
 * and this subclass only binds it to Discourse. What the editor holds is the markdown linux.do
 * stores: the composer's raw goes in through {@link DiscourseRichText#rawToHtml}, and the rows the
 * reader built come back out through {@link DiscourseRichText#blocksToRaw}.</p>
 *
 * <p>The editor's Apply and draft paths, which upstream routes to {@code SendMessagesHelper} and
 * Telegram's draft database, are answered through {@link NativeDoHooks.RichEditorHost} - implemented
 * here, on the instance, so the seam needs no process-wide registry. The upstream callsites hand
 * this editor and its live {@code listView.rows} straight in.</p>
 */
public class DiscourseRichEditor extends RichEditor implements NativeDoHooks.RichEditorHost {

    /** Where the applied markdown goes: the composer this editor was opened from. */
    private final Consumer<String> apply;
    private List<BlockRow> liveRows;

    /**
     * @param raw   the composer's current markdown, put into the editor's own block model
     * @param apply the composer's write-back, called with the markdown the reader built
     */
    public DiscourseRichEditor(String raw, Consumer<String> apply) {
        super(DiscourseRichText.INSTANCE.rawToHtml(raw), true);
        this.apply = apply;
    }

    @Override
    public void bindRows(List<BlockRow> rows) {
        liveRows = rows;
    }

    @Override
    public void onPause() {
        if (liveRows != null) {
            write(liveRows);
        }
        super.onPause();
    }

    /**
     * Apply was pressed. The markdown is written back and only this editor closes; the screen that
     * opened it stays where it was.
     */
    @Override
    public boolean onApply(List<BlockRow> rows) {
        if (write(rows)) {
            finishFragment();
        }
        return true;
    }

    /**
     * The editor is keeping the reader's work (a back press, a transition out). The markdown is
     * written back; this editor is already on its way out.
     */
    @Override
    public boolean onPersistDraft(List<BlockRow> rows) {
        write(rows);
        return true;
    }

    @Override
    public boolean canClose(List<BlockRow> rows, boolean showError) {
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

    /**
     * Exports the editor's live rows to Discourse raw and hands them to the composer.
     *
     * <p>A document holding something the site cannot store is not applied: the editor keeps its
     * content and the reader is told, and the answer stays "handled" so upstream's Telegram send
     * and draft write never run for it.</p>
     *
     * @return true when the markdown was written back; false when the site cannot store it
     */
    private boolean write(List<BlockRow> rows) {
        try {
            apply.accept(DiscourseRichText.INSTANCE.blocksToRaw(rows));
            return true;
        } catch (DiscourseRichText.UnsupportedContentException unsupported) {
            showUnsupported(unsupported);
            return false;
        }
    }

    private void showUnsupported(DiscourseRichText.UnsupportedContentException unsupported) {
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.error, "暂不支持的内容：" + unsupported.getConstruct())
                .show();
    }
}

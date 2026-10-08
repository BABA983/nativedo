// SPDX-License-Identifier: GPL-2.0-or-later
// Port of the reply panel ChatActivity hosts on its composer
// (org/telegram/ui/ChatActivity.java + org/telegram/ui/Components/ChatReplyContainer.java).

package io.github.baba983.nativedo.topic;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterTopView;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatReplyContainer;
import org.telegram.ui.Components.LayoutHelper;

/**
 * The native reply panel ChatActivity puts over its composer, hosted on this screen's composer.
 *
 * <p>Upstream answers a message by showing a reply strip above the field - the target's author and
 * text, with a close button - not by writing anything into the field. This is that strip: it is
 * upstream's own {@link ChatActivityEnterTopView} carrying upstream's own {@link ChatReplyContainer},
 * added to the composer through upstream's own {@code addTopView}. The only thing this class supplies
 * is what the strip says and where a tap on it goes.</p>
 */
final class DiscourseReplyPanel {

    private final ChatActivityEnterView composer;
    private final ChatActivityEnterTopView topView;
    private final ChatReplyContainer reply;
    private Runnable jumpAction;
    private Runnable cancelAction;

    DiscourseReplyPanel(ChatActivityEnterView composer, Theme.ResourcesProvider resourcesProvider,
            Runnable cancelAction) {
        this.composer = composer;
        this.cancelAction = cancelAction;
        Context context = composer.getContext();

        topView = new ChatActivityEnterTopView(context);
        reply = new ChatReplyContainer(context, resourcesProvider);
        topView.addReplyView(reply, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,
                Gravity.NO_GRAVITY, 0, 0, 52, 0));

        // The strip's own action: upstream scrolls to the message it answers. Here the screen's jump
        // is what it runs.
        reply.setOnClickListener(v -> {
            if (jumpAction != null) {
                jumpAction.run();
            }
        });

        ImageView close = new ImageView(context);
        close.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        close.setImageResource(org.telegram.messenger.R.drawable.input_clear);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setBackgroundDrawable(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, resourcesProvider), 1, AndroidUtilities.dp(19)));
        close.setContentDescription("取消回复");
        close.setOnClickListener(v -> cancel());
        topView.addView(close, LayoutHelper.createFrame(
                52, 46, Gravity.RIGHT | Gravity.TOP, 0, 0.5f, 0, 0));

        composer.addTopView(topView, 48);
    }

    /** Shows the strip for a post the reader is answering; a tap on it runs [onJump]. */
    void show(CharSequence name, CharSequence text, Runnable onJump) {
        jumpAction = onJump;
        reply.current().icon.setImageResource(org.telegram.messenger.R.drawable.ic_ab_reply);
        reply.current().name.setText(name);
        reply.current().obj.setText(text);
        composer.showTopView(true, false);
    }

    /** Puts the strip away without telling the screen; the caller owns the reply target's store. */
    void hide() {
        jumpAction = null;
        composer.hideTopView(true);
    }

    /** The close button was pressed: the screen forgets the target and the strip goes away. */
    private void cancel() {
        jumpAction = null;
        Runnable action = cancelAction;
        if (action != null) {
            action.run();
        }
    }

    boolean isShowing() {
        return composer.isTopViewVisible();
    }
}

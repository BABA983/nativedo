// SPDX-License-Identifier: GPL-2.0-or-later

package io.github.baba983.nativedo.topic;

import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.TypingDotsDrawable;

/**
 * Upstream's animated typing dots on a header subtitle: the component {@code ChatAvatarContainer}
 * draws before a chat's printing status, attached here to whichever subtitle line a screen owns.
 * Both the topic's center capsule and a chat channel's action bar use it.
 *
 * <p>The owner shows it while another reader is typing or replying, in the subtitle's own colour,
 * and hides it when the line returns to its own status. The dots carry their own paint, so a theme
 * switch is another {@link #show} with the new colour.
 */
public final class TypingDotsSubtitle {

    private final TypingDotsDrawable dots = new TypingDotsDrawable(true);

    /** {@code isChat} selects upstream's in-chat vertical offset for the dots. */
    public TypingDotsSubtitle(boolean isChat) {
        dots.setIsChat(isChat);
        // Start from the three-dot phase instead of upstream's empty first frame (the drawable's
        // scales begin at zero, so its intro draws nothing until the first tick).
        dots.stop();
    }

    /** Attaches the dots before {@code subtitle}'s text, in {@code color}, and runs them. */
    public void show(SimpleTextView subtitle, int color) {
        dots.setColor(color);
        if (subtitle.getLeftDrawable() != dots) {
            subtitle.setLeftDrawable(dots);
        }
        dots.start();
    }

    /** Stops the dots and takes them off {@code subtitle}. */
    public void hide(SimpleTextView subtitle) {
        dots.stop();
        if (subtitle.getLeftDrawable() == dots) {
            subtitle.setLeftDrawable(null);
        }
    }
}

/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.bots;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class BotWebViewAttachedSheet {
    public static final int TYPE_WEB_VIEW_BUTTON = 0;
    public static final int TYPE_SIMPLE_WEB_VIEW_BUTTON = 1;
    public static final int TYPE_BOT_MENU_BUTTON = 2;
    public static final int TYPE_WEB_VIEW_BOT_APP = 3;
    public static final int TYPE_WEB_VIEW_BOT_MAIN = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            TYPE_WEB_VIEW_BUTTON,
            TYPE_SIMPLE_WEB_VIEW_BUTTON,
            TYPE_BOT_MENU_BUTTON,
            TYPE_WEB_VIEW_BOT_APP,
            TYPE_WEB_VIEW_BOT_MAIN
    })
    public @interface WebViewType {
    }
}

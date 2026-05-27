/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;

/** Minimal port shell for Telegram search filter data used by FragmentSearchField. */
public class FiltersView extends RecyclerListView {
    public static final int FILTER_TYPE_MEDIA = 0;
    public static final int FILTER_TYPE_FILES = 1;
    public static final int FILTER_TYPE_LINKS = 2;
    public static final int FILTER_TYPE_MUSIC = 3;
    public static final int FILTER_TYPE_CHAT = 4;
    public static final int FILTER_TYPE_VOICE = 5;
    public static final int FILTER_TYPE_DATE = 6;
    public static final int FILTER_TYPE_ARCHIVE = 7;

    public FiltersView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    public static class MediaFilterData {
        public final int iconResFilled;
        public final CharSequence title;
        public final int filterType;
        public boolean removable = true;
        public Object reaction;
        public Object chat;

        public MediaFilterData(int iconResFilled, CharSequence title, int filterType) {
            this.iconResFilled = iconResFilled;
            this.title = title;
            this.filterType = filterType;
        }

        public CharSequence getTitle() {
            return title;
        }
    }
}

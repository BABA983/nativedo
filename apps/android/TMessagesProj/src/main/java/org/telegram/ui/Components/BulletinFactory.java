/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public final class BulletinFactory {

    private final BaseFragment fragment;
    private final ViewGroup containerLayout;
    private final Theme.ResourcesProvider resourcesProvider;

    public static BulletinFactory of(BaseFragment fragment) {
        return new BulletinFactory(fragment, null, fragment != null ? fragment.getResourceProvider() : null);
    }

    public static BulletinFactory of(ViewGroup containerLayout, Theme.ResourcesProvider resourcesProvider) {
        return new BulletinFactory(null, containerLayout, resourcesProvider);
    }

    public static BulletinFactory global() {
        return new BulletinFactory(null, null, null);
    }

    public static BulletinFactory photoViewer(Theme.ResourcesProvider resourcesProvider) {
        return new BulletinFactory(null, null, resourcesProvider);
    }

    private BulletinFactory(BaseFragment fragment, ViewGroup containerLayout, Theme.ResourcesProvider resourcesProvider) {
        this.fragment = fragment;
        this.containerLayout = containerLayout;
        this.resourcesProvider = resourcesProvider;
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text) {
        return create(text, Bulletin.ICON_INFO, textLengthDuration(text), iconRawId);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, int type) {
        return create(text, type == 1 ? Bulletin.ICON_ERROR : Bulletin.ICON_INFO, textLengthDuration(text), iconRawId);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, CharSequence button, int duration, Runnable onButtonClick) {
        return create(text, Bulletin.ICON_INFO, duration, iconRawId).setAction(button, onButtonClick);
    }

    public Bulletin createCopyLinkBulletin() {
        if (!AndroidUtilities.shouldShowClipboardToast()) {
            return new Bulletin();
        }
        return create(
                LocaleController.getString("LinkCopied", R.string.LinkCopied),
                Bulletin.ICON_COPY,
                Bulletin.DURATION_SHORT,
                0
        );
    }

    public Bulletin createCopyBulletin(String text) {
        if (!AndroidUtilities.shouldShowClipboardToast()) {
            return new Bulletin();
        }
        return create(text, Bulletin.ICON_COPY, Bulletin.DURATION_SHORT, 0);
    }

    public Bulletin createErrorBulletin(CharSequence text) {
        return create(text, Bulletin.ICON_ERROR, Bulletin.DURATION_LONG, 0);
    }

    public Bulletin createDownloadBulletin(int type) {
        return createInfoBulletin(LocaleController.getString("FileSavedHintLinked", R.string.FileSavedHintLinked));
    }

    public Bulletin createInfoBulletin(CharSequence text) {
        return create(text, Bulletin.ICON_INFO, textLengthDuration(text), 0);
    }

    private Bulletin create(CharSequence text, int iconType, int duration, int iconRawId) {
        ViewGroup container = containerLayout();
        if (container == null) {
            return new Bulletin();
        }
        return new Bulletin(container, text, iconType, duration, resourcesProvider, iconRawId);
    }

    private ViewGroup containerLayout() {
        if (containerLayout != null) {
            return containerLayout;
        }
        return fragment != null ? fragment.getBulletinLayoutContainer() : null;
    }

    private static int textLengthDuration(CharSequence text) {
        return text != null && text.length() < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG;
    }
}

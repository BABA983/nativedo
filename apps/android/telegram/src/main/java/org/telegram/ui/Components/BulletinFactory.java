/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.view.View;
import android.view.ViewGroup;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/**
 * NativeDo stub of org.telegram.ui.Components.BulletinFactory.
 * Upstream shows snackbar-style notifications. NativeDo doesn't use Telegram
 * bulletins yet — all factory methods return a no-op chain.
 */
public final class BulletinFactory {

    public static class Bulletin {
        public Bulletin show() { return this; }
        public Bulletin show(boolean useTransform) { return this; }
        public Bulletin hide() { return this; }
        public void setDuration(int durationMs) {}
    }

    public static BulletinFactory of(BaseFragment fragment) {
        return new BulletinFactory();
    }

    public static BulletinFactory of(ViewGroup containerLayout, Theme.ResourcesProvider resourcesProvider) {
        return new BulletinFactory();
    }

    public static BulletinFactory global() {
        return new BulletinFactory();
    }

    public static BulletinFactory photoViewer(Theme.ResourcesProvider resourcesProvider) {
        return new BulletinFactory();
    }

    private BulletinFactory() {
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text) {
        return new Bulletin();
    }

    public Bulletin createErrorBulletin(CharSequence text) {
        return new Bulletin();
    }

    public Bulletin createDownloadBulletin(int type) {
        return new Bulletin();
    }

    public Bulletin createInfoBulletin(CharSequence text) {
        return new Bulletin();
    }
}

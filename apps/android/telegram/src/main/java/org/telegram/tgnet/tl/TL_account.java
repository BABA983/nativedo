/*
 * Host surface for Telegram TL_account types used by browser settings.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.tgnet.tl;

import org.telegram.tgnet.TLObject;

/**
 * Local data model for the browser settings surface from Telegram 12.9.0.
 * NativeDo persists this model locally; it does not expose Telegram MTProto
 * request/response serialization.
 */
public final class TL_account {
    private TL_account() {}

    public static class WebBrowserSettings extends TLObject {
    }

    public static class TL_webBrowserSettings extends WebBrowserSettings {
        public boolean open_external_browser;
        public boolean display_close_button;
        public java.util.ArrayList<WebDomainException> external_exceptions = new java.util.ArrayList<>();
        public java.util.ArrayList<WebDomainException> inapp_exceptions = new java.util.ArrayList<>();
        public long hash;
    }

    /**
     * Pin-shaped domain exception row. Host fills domain/title from
     * {@code MessagesController} + {@code WebMetadataCache}; favicon document
     * id is unused (0 → letter placeholder in WebsiteView).
     */
    public static class WebDomainException extends TLObject {
        public int flags;
        public String domain;
        public String url;
        public String title;
        public long favicon;

        public static boolean equalsByDomain(WebDomainException a, WebDomainException b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null || a.domain == null || b.domain == null) {
                return false;
            }
            return a.domain.equalsIgnoreCase(b.domain);
        }
    }
}

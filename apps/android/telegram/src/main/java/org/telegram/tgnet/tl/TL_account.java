/*
 * Host surface for Telegram TL_account types used by browser settings.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.tgnet.tl;

import org.telegram.tgnet.TLObject;

/**
 * Minimal host package. Only {@link WebDomainException} is needed for
 * WebBrowserSettings at pin 12.9.0; no MTProto serialize path.
 */
public final class TL_account {
    private TL_account() {}

    /**
     * Pin-shaped domain exception row. Host fills domain/title from
     * {@code RestrictedDomainsList} + {@code WebMetadataCache}; favicon
     * document id is unused (0 → letter placeholder in WebsiteView).
     */
    public static class WebDomainException extends TLObject {
        public int flags;
        public String domain;
        public String url;
        public String title;
        public long favicon;
    }
}

/*
 * Host surface for Telegram TL_iv types used by the post-body table pipeline.
 * Copyright (C) 2026 NativeDo contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.tgnet.tl;

import org.telegram.tgnet.TLRPC;

/**
 * Step 1 of the instant-view split from Telegram 12.10.2.
 *
 * At the pin the table types live here, while this tree still spells them
 * inside {@link TLRPC}. Until the full split lands (the remaining page-block
 * and rich-text classes plus their call sites), the pin's name aliases onto the
 * legacy definition, which is what lets {@code TableLayout} be the pin's file
 * verbatim.
 */
public final class TL_iv {
    private TL_iv() {}

    public static class pageTableCell extends TLRPC.TL_pageTableCell {
    }
}

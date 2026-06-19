/*
 * This file is part of NativeDo.
 *
 * Minimal QuoteSpan shell for Telegram SpoilerEffect quote-boundary checks.
 * Full quote chrome is ported separately when topic quote/reply UI is aligned.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.ui.Components;

public final class QuoteSpan {
    private QuoteSpan() {
    }

    public static class Block {
        public float top;
        public float bottom;
    }
}

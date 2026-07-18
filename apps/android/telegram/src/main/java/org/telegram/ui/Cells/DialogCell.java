package org.telegram.ui.Cells;

/**
 * Minimal DialogCell surface for pin SimpleTextView FixedWidthSpan nesting.
 * Full DialogCell UI is not ported under :telegram (NativeDo uses TopicCell).
 */
public class DialogCell {
    public static class FixedWidthSpan extends org.telegram.ui.Cells.FixedWidthSpan {
        public FixedWidthSpan(int width) {
            super(width);
        }
    }
}

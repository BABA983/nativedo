// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/ArticleViewer.java#getTextPaint
// UPSTREAM-COMMIT: master@2026-05-08
// EXACT-SUBSYSTEMS: typography sizing
// HOST-WIRING:
//   - Telegram dp(int) sizes -> Compose sp/dp via TextUnit constants.
// ADAPTED-BACKEND:
//   - None.
// ADAPTED-PRODUCT:
//   - h4-h6 step-down to 15.sp (Telegram instant view does not distinguish below
//     subheader, but Discourse posts often nest deeper headings inside <details>).
// OMITTED:
//   - SharedConfig.ivFontSize +/- adjustment (no global font size setting in NativeDo yet).
//   - Serif typefaces for headings (Telegram bundles mw_bold.ttf; we fall back to
//     system default until we wire fonts).
package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Body / paragraph text. Telegram TL_pageBlockParagraph: dp(16). */
internal val postBodyParagraphTextSize = 16.sp

/** List item text. Telegram list cell (BlockListItemCell): dp(16). */
internal val postBodyListItemTextSize = 16.sp

/** Bullet glyph size. Telegram listTextPointerPaint: dp(19). */
internal val postBodyListBulletTextSize = 19.sp

/** Numeric bullet (ordered list) size. Telegram listTextNumPaint: dp(16). */
internal val postBodyListNumberTextSize = 16.sp

/** Quote body text. Telegram TL_pageBlockBlockquote: dp(15). */
internal val postBodyQuoteTextSize = 15.sp

/** Quote / aside title and metadata. Telegram TL_pageBlockBlockquote caption: dp(14). */
internal val postBodyQuoteCaptionTextSize = 14.sp

/** Code / preformatted text. Telegram TL_pageBlockPreformatted: dp(14). */
internal val postBodyCodeTextSize = 14.sp

/** Image caption. Telegram TL_pageBlockPhoto caption: dp(14). */
internal val postBodyImageCaptionTextSize = 14.sp

/** h1 / Title. Telegram TL_pageBlockTitle: dp(23). */
internal val postBodyHeading1TextSize = 23.sp

/** h2 / Header / Subtitle. Telegram TL_pageBlockHeader & TL_pageBlockSubtitle: dp(20). */
internal val postBodyHeading2TextSize = 20.sp

/** h3 / Subheader. Telegram TL_pageBlockSubheader: dp(17). */
internal val postBodyHeading3TextSize = 17.sp

/** h4-h6. ADAPTED-PRODUCT: Telegram has no analogue below subheader. */
internal val postBodyHeadingMinorTextSize = 15.sp

/** Default block-to-block spacing. */
internal val postBodySpacingBetweenBlocks = 10.dp

/** Vertical breathing room before/after a heading. */
internal val postBodyHeadingTopSpacing = 18.dp
internal val postBodyMinorHeadingTopSpacing = 12.dp
internal val postBodyHeadingBottomSpacing = 6.dp

/** Spacing between adjacent list items. */
internal val postBodyListItemSpacing = 4.dp

/** Width reserved for the bullet/number column in lists. */
internal val postBodyListBulletColumnWidth = 24.dp

/** Quote block: left bar + content inset. Telegram quoteLinePaint dp(20-18) = 2dp wide. */
internal val postBodyQuoteBarWidth = 2.dp
internal val postBodyQuoteContentInset = 12.dp

/** Code block padding. */
internal val postBodyCodeBlockPadding = 12.dp

/** Horizontal rule (hr) thickness and spacing. */
internal val postBodyRuleThickness = 1.dp
internal val postBodyRuleVerticalSpacing = 12.dp

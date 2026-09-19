# Selection helper: adopting the pin's `TextSelectionHelper`

Status: **measured, not started** (2026-09-19, at `b461063`). This is the S5
selection-engine project from `docs/render-model-alignment.md`; the numbers below
supersede the first probe recorded there, because the second probe looked *inside*
the file instead of only counting errors.

## What we have, and what the pin has

- **ours:** `org/telegram/ui/Cells/TextSelectionHelper.java`, 2,347 lines - a
  12.6-era port of the article engine, plus NativeDo extras the pin does not have
  (`setOnQuote`, `setRecyclerView`, `destroy()`, the `TextSelectionBoundsProvider`
  interface `Emoji.EmojiSpan` implements, and the
  `getPrefixLayout`/`getPrefixX`/`getPrefixY`/`getClipBounds` members on
  `TextLayoutBlock`).
- **the popup is already upstream:** the action mode is `FloatingActionMode` +
  `FloatingToolbar` (byte-exact since `83eca05`), and the article menu is upstream's
  set (copy / quote / select-all / translate).
- **pin:** `TextSelectionHelper.java`, 4,091 lines, containing the base engine, the
  overlay, `SimpleTextSelectionHelper`, `ChatListTextSelectionHelper` (the dialogs
  list), `ArticleTextSelectionHelper` (what our post body uses), and the
  `SelectableView` / `ArticleSelectableView` / `TextLayoutBlock` interfaces.

## The probe, and where the 172 errors actually are

Copy the pin's file over ours, `:TMessagesProj:compileDebugJavaWithJavac`
(no other change), then count and locate:

| Where | Errors |
|---|---|
| inside the file | 167 |
| `Emoji.java` (our `TextSelectionBoundsProvider` adaptation) | 3 |
| `RichMessageLayout.java` (`getLayout()` returns `StaticLayout`; the pin wants `Layout`) | 2 |

Split by the file's own nested classes:

| Block (pin line range) | Errors |
|---|---|
| base fields + `TextSelectionOverlay` | 7 |
| `SimpleTextSelectionHelper` | 0 |
| **`ChatListTextSelectionHelper` (2101-2864)** | **160** |
| **`ArticleTextSelectionHelper` (2864-3937)** | **0** |
| interfaces (`SelectableView`, `TextLayoutBlock`, ...) | 0 |

So the scary number is one product gap, not engine debt: **160 of 167 in-file
errors are the chat-list (dialogs list) helper**, which reads
`MessageObject.TextLayoutBlock` / `MessageObject.TextLayoutBlocks` - the chat text
layout this tree never ported - and which we have no surface for. The parts our
post body actually runs (the article helper plus the interfaces) probe **clean**.

## The gap, by owner (what the errors ask for)

1. **Chat-shaped layout holders** - only needed by the chat-list block:
   `MessageObject.TextLayoutBlock` (pin `MessageObject.java:969`, 249 lines),
   `MessageObject.TextLayoutBlocks` (pin line 9,117, ~3.7k lines: the generator),
   `messageObject.textLayoutBlocks` / `richLayout` / `textXOffset`,
   `Message.edit_date`, and ~37 members on `ChatMessageCell`
   (`getCaptionLayout`, `hasCaptionLayout`, `getFactCheckLayout`,
   `getDescriptionlayout`, `getCurrentMessagesGroup`, `transitionParams`,
   `setSelectedBackgroundProgress`, `isDrawingSelectionBackground`,
   `invalidateOutbounds`, plus satisfying the `Cell` type bound).
2. **Our extras the pin does not have** (the real migration list):
   - `TextLayoutBlock.getPrefixLayout/getPrefixX/getPrefixY/getClipBounds` -
     16 uses across 4 files: `PostBodyBlock.kt` (our interface),
     `RichMessageBody.kt`, `TableBlock.java`, and `TopicPostCell.java`
     (**forced_exact**: needs the owner to relax the entry or a shim);
   - `Emoji.EmojiSpan implements TextSelectionHelper.TextSelectionBoundsProvider`
     (ours; the pin's `EmojiSpan extends ImageSpan`);
   - `getLayout()` returning `StaticLayout` where the pin's interface says `Layout`
     (the two `RichMessageLayout` errors and the same shape on our blocks);
   - the two helper calls in `TopicDetailFragment` that the pin lacks
     (`setOnQuote`, `setRecyclerView`) and `destroy()`.
3. **Small host pieces**: `AndroidUtilities.addToClipboard` overload,
   `SharedConfig.removeTextSelectionHint()`, and the imports we do not ship -
   `LanguageDetector` + `RestrictedLanguagesSelectActivity` (the translate UI) and
   `ui/iv/RichTextCell` (the rich-text editor half).

## Staged plan

**P1 - adopt the file minus the chat-list block (small, the bulk of S5).**
Take the pin's `TextSelectionHelper.java` and delete `ChatListTextSelectionHelper`
(a documented delta: the product has no dialogs list; the class is ~763 lines of the
4,091 and owns 160 of the 167 in-file errors). Fix the residue: `RichMessageLayout`'s
two `getLayout()` returns, then the migration list in point 2. Gate: full unit suite +
`verifyPaparazziDebug` + a device pass over the selection handles, emoji selection and
spoilers; the article helper's public API (`getOverlayView(Context)`, `setParentView`,
`setCallback`, `setOnTranslate`, `clear`, `isInSelectionMode`, `stopScrolling`,
`onParentScrolled`) already matches what `TopicDetailFragment` calls.

**P2 - the wire-outs fall.** Once the blocks carry the pin's `TextLayoutBlock`
shape, `RichMessageLayout`'s two `TextLayoutBlock` wire-outs and the math-span copy
wire-out go away, and the `getPrefix*` extras (ours) are either migrated to the pin's
interface or re-added as marked deltas - decide per member: `getPrefix*`/`getClipBounds`
are *our* drawing model, the pin draws prefixes through `LayoutBlock`.

**P3 - the chat-ready half (project, optional until chat).** For the file to need no
delta at all, port the layout producer: `MessageObject.TextLayoutBlock` +
`TextLayoutBlocks`, the `ChatMessageCell` surface in point 1, and fill
`textLayoutBlocks`/`richLayout` from `RichMessageLayout` (this is the "layout producer"
step the S4 notes always pointed at). That also unblocks `ChatListTextSelectionHelper`,
i.e. the dialogs list, which is only meaningful with chat.

## Open decisions for the owner

1. `TopicPostCell` is forced_exact and its `fillTextLayoutBlocks` forwarding is on the
   `getPrefix*` list: relax that entry for this project, or route the forwarding through
   a NativeDo shim that keeps the cell untouched?
2. `setOnQuote` (our composer's quote flow): keep as a two-line delta on the adopted
   file, or move the quote action to our side of the boundary?
3. Order: this project before or after the RLottie family.

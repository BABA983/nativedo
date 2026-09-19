# Selection helper: adopting the pin's `TextSelectionHelper`

Status: **P1 landed** (`5fd8dc3`, 2026-09-19); P2 and P3 below are what is left.
This is the S5 selection-engine project from `docs/render-model-alignment.md`; the
measurement below is kept as it was written (the second probe looked *inside* the
file instead of only counting errors), and "Landed" records the residue against it.

## Landed: P1, the file is upstream's (`5fd8dc3`)

`TMessagesProj/.../ui/Cells/TextSelectionHelper.java` is the pin's 4,091-line file,
3,336 lines on disk after exactly three deltas:

- `ChatListTextSelectionHelper` is omitted, with a two-line `// NativeDo:` notice
  where it sat (the dialogs list has no surface here, and it owns 160 of the 167
  in-file errors — the chat text layout it reads);
- an unused `import org.telegram.ui.iv.RichTextCell` goes (nothing in the tree
  implements that interface);
- `quoteText()` gets `if (messageObject == null && !canShowQuote()) return;` under a
  `// NativeDo:` comment, so a non-chat view reaches the quote hooks — upstream's
  `quoteText` only looks for a `MessageObject` on a `ChatMessageCell`.

**Open decision 1 resolved as (B), the pin-exact interface.** The four extras left
the interface instead of being kept as a compatibility block: `PostSelectionLayoutBlock`
(`PostBodyBlock.kt`) is `Layout`-typed with `getSelectionBounds()`, and
`getPrefixLayout`/`getPrefixX`/`getPrefixY`/`getClipBounds` have no reader anywhere in
the tree anymore (`grep` is clean). `RichMessageBody.OffsetTextLayoutBlock`,
`TableBlock`, `ListBlock` and `CodeBlock` follow; the span-level
`TextSelectionHelper.TextSelectionBoundsProvider` is gone, so `Emoji.EmojiSpan`
extends `ImageSpan` again and `InlineTextImages`/`PostBodyChipSpan` carry the pin's
shape (a `ReplacementSpan` plus an `IgnoreCopySpannable` marker where a copy-substitution
was needed). `TopicPostCell`'s long-press hit test now takes `getSelectionBounds()`,
and the block it tests no longer needs a prefix rectangle.

**Open decision 2 resolved as (1), upstream's extension points.** `TopicPostTextSelectionHelper`
(native, `:app`) subclasses the article helper and overrides `canShowQuote()` /
`onQuoteClick(MessageObject, int, int, CharSequence)` — the shape `ChatActivity:3490/3524`
and `MessagePreviewView:327/332` use — and `TopicDetailFragment` answers it with
`showReplyComposer(discourseQuoteMarkup(...))` as before. Our port's `setOnQuote`,
`setRecyclerView` and the prefix-geometry members are gone.

**What stayed app-side, on purpose:** `TopicPostTextSelectionHelper` also carries the
two behaviours our port had grown — list-adapter observation (a change at or before
`getEndCell()` clears the selection, through the pin's public accessors) and overlay
teardown (`destroy()`, called from the fragment's `onDestroyView`) — because the pin's
engine owns neither the RecyclerView nor the fragment lifecycle. The translate path
compiles against two native shims (`messenger/LanguageDetector.java`,
`ui/RestrictedLanguagesSelectActivity.java`: no ML Kit, no translation settings
screen), and `ChatListTextSelectionHelper` is the only class left out.

**Gates:** guard OK (562 tracked / 217 differ), full suite 768/0,
`:app:verifyPaparazziDebug` green with **no golden moved**, `:app:assembleDebug`
green. The device pass is `docs/device-verification.md` §9.

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

**P1 - adopt the file minus the chat-list block (landed, `5fd8dc3`; the bulk of
S5).** Take the pin's `TextSelectionHelper.java` and delete
`ChatListTextSelectionHelper` (a documented delta: the product has no dialogs list;
the class is ~763 lines of the 4,091 and owns 160 of the 167 in-file errors). Fix the
residue: `RichMessageLayout`'s two `getLayout()` returns, then the migration list in
point 2. Gate: full unit suite + `verifyPaparazziDebug` + a device pass over the
selection handles, emoji selection and spoilers; the article helper's public API
(`getOverlayView(Context)`, `setParentView`, `setCallback`, `setOnTranslate`,
`clear`, `isInSelectionMode`, `stopScrolling`, `onParentScrolled`) already matches
what `TopicDetailFragment` calls. **What the landing changed against this text:** the
interface migration list in point 2 went all the way rather than keeping a
compatibility block (decision 1 -> B), so `getPrefix*`/`getClipBounds` and the
`TextSelectionBoundsProvider` are gone instead of re-added; the two `RichMessageLayout`
`getLayout()` returns needed no change, since `StaticLayout extends Layout`.

**P2 - the wire-outs fall.** Once the blocks carry the pin's `TextLayoutBlock`
shape, `RichMessageLayout`'s two `TextLayoutBlock` wire-outs and the math-span copy
wire-out go away, and the `getPrefix*` extras (ours) are either migrated to the pin's
interface or re-added as marked deltas - decide per member: `getPrefix*`/`getClipBounds`
are *our* drawing model, the pin draws prefixes through `LayoutBlock`. **State after
P1:** the interface is the pin's everywhere, so what is left here is the renderer's
own three sites (`RichMessageLayout:3213`, `:2136` and `:9370`), whose comments still
say "12.6 shape"; the `ReplaceCopyTextSpannable` nested holder now exists (it comes
with the adopted file), so attaching it at `:2136` and walking it at `:9370` is
typing rather than a decision. The marker draws nothing (it is a plain class, not a
span style), so copied LaTeX is what changes, not pixels — the copy tests in
`TextSelectionHelperTest` are the gate, not Paparazzi.

**P3 - the chat-ready half (project, optional until chat).** For the file to need no
delta at all, port the layout producer: `MessageObject.TextLayoutBlock` +
`TextLayoutBlocks`, the `ChatMessageCell` surface in point 1, and fill
`textLayoutBlocks`/`richLayout` from `RichMessageLayout` (this is the "layout producer"
step the S4 notes always pointed at). That also unblocks `ChatListTextSelectionHelper`,
i.e. the dialogs list, which is only meaningful with chat.

## Open decisions for the owner (in detail)

**1. What our `TextLayoutBlock` interface carries that the pin's does not.**
*(Resolved in `5fd8dc3`: option (B), the pin-exact interface. The options and the
reasoning are kept below for the record.)*

Ours (`TextSelectionHelper.java:73-83`) declares

```java
StaticLayout getLayout();
int getX(); int getY(); int getRow(); CharSequence getPrefix();
default StaticLayout getPrefixLayout() { return null; }
default int getPrefixX() { return getX(); }
default int getPrefixY() { return getY(); }
default Rect getClipBounds() { return null; }
```

The pin's (line 3962) declares `Layout getLayout()`, the same four getters, and defaults
for `getPrefix()`, `getText()` and `getSelectionBounds()`. The four extras are **our drawing
model**: the prefix layout is the bullet/quote bar that is drawn and highlighted with the
text, and `getClipBounds()` is the block's clip rectangle. They are implemented or read in
four files - `PostBodyBlock.kt` (`PostSelectionLayoutBlock`), `RichMessageBody.kt`
(`OffsetTextLayoutBlock`), `TableBlock.java`, and `TopicPostCell.java` (~line 1232, the
long-press hit test that asks `bestBlock.getClipBounds()`).

- **(A) keep them as a compatibility block.** Adopt the pin's file and keep those five lines
  as a marked `// NativeDo:` block on the interface (`StaticLayout` satisfies the pin's
  `Layout` reads). The four files, including the forced-exact cell, do not change; the
  adopted file carries one delta, like `RichMessageLayout`'s eight.
- **(B) pin-exact interface.** Migrate the four files instead: the blocks produce the pin's
  shape and `TopicPostCell`'s hit test derives its bounds from the layout. That is the only
  option that touches a forced-exact file, so it needs the entry relaxed for one commit.

**2. What happens to the selection menu's Quote action.**
*(Resolved in `5fd8dc3`: option (1), upstream's hooks. Kept for the record.)*

Today our helper exposes `setOnQuote(callback)` (our addition) and `TopicDetailFragment`
answers it with `showReplyComposer(discourseQuoteMarkup(...))`. The pin has the same Quote
menu item but its extension point is a pair of `protected` hooks - `canShowQuote()` and
`onQuoteClick(MessageObject, int, int, CharSequence)` - and upstream implements them by
**subclassing**: `ChatActivity:3490/3524` (chat message selection) and
`MessagePreviewView:327/332`. The pin's `quoteText()` only fetches a `MessageObject` when
`selectedView instanceof ChatMessageCell`, so the article path (our post body) never reaches
the hooks today.

- **(1) upstream shape.** A ~6-line marked delta in `quoteText()` (continue with
  `onQuoteClick(null, start, end, str)` for non-chat views) plus a small
  `ArticleTextSelectionHelper` subclass in `:app` that overrides `canShowQuote()` /
  `onQuoteClick(...)` - the same shape `ChatActivity` uses. UX unchanged.
- **(2) cheapest.** Keep our `setOnQuote` setter as the delta. Same UX, extension point not
  upstream's.
- **(3) aligned by omission.** Drop Quote from the article selection menu (upstream's article
  path shows none) and add the action elsewhere - the post "..." menu today has
  REPLY/SELECT_TEXT/COPY_LINK and no Quote, so that is new UI. The only option that changes
  the product.

**3. Order:** this project before or after the RLottie family. *(Answered: after
S4, before the RLottie family.)*

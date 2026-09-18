# Render-model alignment: Telegram's model in, translated at the boundary

The question (owner, 2026-09-18): the composer's rule — *edit in Telegram's
model, translate to Discourse's format at the boundary* — looks like it should
apply to rendering too (topic-detail post bodies, `ArticleViewer`, the instant
view stack). Would translating into Telegram's model at the parse boundary make
it easier to copy upstream's code, instead of porting and wiring it by hand?

Short answer: yes for the model half, and that half is cheap; the renderer half
is blocked by one structural fact with a concrete way around it. This document
records the measurements behind both claims, then the staged plan.

## What the surfaces translate today

| Surface | Boundary | State |
|---|---|---|
| Composer | markdown ↔ Telegram spans | done in `15f5bdb`: the composer is spans, `TopicReplyComposerMarkdown` serializes/parses |
| In-app browser / Instant View | fetched HTML → `TL_iv` blocks | already the mirror's own parser (`WebInstantView`, ours 1300 / pin 1298 lines) — the principle is already in force |
| **Topic-detail post bodies** | Discourse cooked HTML → **our own model** | `CookedPostBodyParser` (838) emits `CookedPostBlock` (128) with **HTML strings**; the renderers then re-parse those fragments |

So the post body is the one surface where the boundary translates into *our*
model, and the renderers are hand-derived ports of `ArticleViewer`'s cells:

- `ui/topic/body/` is 11,256 lines, and every block's header says
  "Derived in part from Telegram for Android's `ArticleViewer.BlockXCell`"
  (`QuoteBlock`, `TableBlock`, `DetailsBlock`, `ParagraphBlock`, `ListBlock`,
  `ImageBlock` (`BlockPhotoCell`), `CollageBlock`, `CodeBlock`
  (`BlockPreformattedCell`), …).
- They bind to **our** seam (`PostBodyBuildContext` + `PostBodyDelegate`) and
  take HTML fragments (`QuoteTextBlock(String html, …)`), so they re-implement
  Telegram's inline model in our own code.

## Why the renderers cannot simply be copied today

`ArticleViewer.java`: ours 16,215 / pin 16,469 lines, and the rebase plan marks
it "epic closed, full-file pin replace off-limits" (host wiring: `WebBookmarks`,
sheet tabs, `ActivityResult`). Its cells are **inner classes**, so "copy the
cell" means lifting code out by hand — which is exactly the drift-prone port
pattern we have.

But the cells are much closer to standalone than the monolith suggests:

- they take the host as the **top-level** interface `IArticleViewer`
  (`org/telegram/ui/IArticleViewer.java` exists as its own file at the pin),
- they call three **`public static`** helpers on `ArticleViewer`
  (`createLayoutForText` 3 overloads, `checkLayoutForLinks`,
  `buildAccessibilityText`),
- the second dependency, `WebpageAdapter` (inner, `RecyclerListView.SelectionAdapter`),
  is touched through only eleven members: `currentPage`, `isRtl`,
  `blocks`, `itemHeights`, `sumItemHeights`, `fullHeight`, `photoBlocks`,
  `channelBlock`, `getTypeForBlock`, `getItemCount`, `cleanup`.

That is a mechanical extraction, not a rewrite: same shape as the `TLRPC → TL_iv`
split (`2e7c61c`), where each extracted file becomes a tracked mirror whose
delta the guard reports.

The one real decision behind copying cells is **how the body is drawn**: the
pin's cells are `View`s hosted by a page adapter, while our body is drawn on a
canvas inside one post cell (StaticLayouts + hit-testing, the design
`docs/topic-detail-rewrite.md` chose for list performance). Copying cells
verbatim means accepting small Views inside the post cell. That is a perf/UX
call, not a vendor call.

The pin's newer `org/telegram/ui/iv/` package (`RichBlockCell`, `RichDetailsCell`,
`RichBlockChrome`, `RichCaptionController`, `RichContainer`, `RichEditText`, …)
is the modern version of the same idea: **standalone** rich-block cells instead
of monolith inner classes — 47 files / 27,247 lines, and its entry points are
tied to Telegram's message/editor world. It is the strategic alternative if the
body renderer is ever rebuilt, and a subsystem, not a slice. The triage
documents it as deferred (`UniversalRecyclerView` needs `RichEditor`).

## How upstream renders, and which renderer to copy

Measured at the pin. Nothing in Telegram renders HTML at runtime: each surface
renders from a Telegram model, and external formats are converted at the edges.

| Surface | Model | Renderer | Host | Size / shape |
|---|---|---|---|---|
| Classic chat message | `TLRPC.MessageEntity` list over plain text | `MessageObject.addEntitiesToText` → spans (`TextStyleSpan`, `URLSpan*`, `CodeHighlighting`, `AnimatedEmojiSpan`) | `ChatMessageCell`, `StaticLayout` drawn on canvas | cell-bound, ours already mirrors this shape |
| Rich message (Bot API rich messages) | `TLRPC.Message.rich_message` = `TL_iv.RichMessage` (blocks + `RichText`) | `org/telegram/messenger/RichMessageLayout.java` | `ChatMessageCell` delegates touch/selection to it and draws it | **10,066 lines, one file, canvas-drawn** |
| Instant view page | `TL_iv.Page` (blocks + `RichText`) | `ArticleViewer`'s inner `Block*Cell` Views | `ArticleViewer.WebpageAdapter` (`RecyclerListView`) | 16,469-line monolith, rebase closed |
| Rich message editing / other hosts | same blocks | `ui/iv/Rich*Cell` + `RichEditor`, `RichTextStyle`, `RichHtml`, `RichMessageConvert` | attach-alert rich layout, AI compose sheet | 47 files / 27,247 lines, editor-bound |

Conversions upstream already ships, i.e. the "assemble into Telegram's format"
half of the idea: `MarkdownParser.parse(String, ArrayList<TL_iv.PageBlock>)`
(markdown → blocks, for `.md` documents and bot content), `ui/iv/RichHtml`
(HTML ↔ rich model, for the editor's paste/copy), `RichMessageConvert`.
Both directions exist: `MessageObject.formatRichMessage/formatRichBlock` flatten
blocks back into a `CharSequence` for previews and copy.

Two consequences for our plan:

1. `RichMessageLayout` is the closest thing to our post body that upstream has:
   it draws blocks on a canvas inside a chat cell, which is exactly our shape
   (single canvas-drawn cell, `StaticLayout`s, hit-testing), and it is **one
   file** instead of a monolith of inner Views. Its `MessageObject` use is
   shallow - `messageOwner` (for the blocks), `isOutOwner`, `getDisplayRichMessage`,
   `getDialogId`, `isSponsored`/`sponsoredColor`, `isSpoilersRevealed`, link
   colors, `currentAccount` - which is why hosting it with a
   `MessageObject`-shaped holder is plausible: our `MessageObject` is already a
   364-line port and our `ChatMessageCell` is a 52-line stub that would have to
   grow the delegate/`TransitionParams` seam.
2. The blocks are the same blocks as instant view, so R1 (make `TL_iv` the
   post-body model) is the precondition for either renderer. Discourse-only
   blocks (polls, oneboxes, callouts) stay ours and would be an added
   block case in whichever renderer we adopt.

Revised R2: the spike targets `RichMessageLayout` rather than an
`ArticleViewer` cell - same canvas shape as our body, one mirror file, shallow
host surface. The ArticleViewer-cell extraction stays the fallback if the
rich-message host turns out to be chat-entangled in ways the shallow access list
does not show.

## Staged plan

**R1 — make `TL_iv` the post-body model (cheap, pixel-gated).**
Step 1 landed: `core/ui/InlineRichText.kt` cooks cooked HTML into
`TL_iv.RichText` (styles, links, spoilers, sub/superscript, marked) with the
Discourse-only constructs (emoji images, SVG icons, hashtag chips, `[size]`)
kept in the token form the model has no case for, plus nine tests. Step 2 is the
consumer swap: `buildPostBodyText` walks the tree instead of `HtmlCompat` +
marker spans, which deletes the spoiler/style marker machinery, and after that
the blocks carry the model instead of HTML.
The parser stops emitting HTML strings and emits `TL_iv` page blocks plus
`TL_iv.RichText` runs (`textBold`/`textItalic`/`textMono`/`textStrike`/
`textUnderline`/`textSpoiler`/`textMarked`/`textUrl`/`textMention`/…), and the
blocks read the mirror's fields. `TL_iv` is already real at the pin's names
(`8c1e55f`, `2e7c61c`) and `TableLayout` is already the pin's file, so nothing
new lands in the mirror. Payoff: one place for markup semantics, inline text
consumes Telegram's model instead of a home-grown one, and any later cell copy
becomes mechanical. Gate: the eight `TopicPostCellScreenshotTest` goldens plus
the suite — a pure refactor of the boundary should not move a pixel.

**R2 — renderer spike: `RichMessageLayout` first, an `ArticleViewer` cell as the fallback.**
Land `RichMessageLayout` as a mirror file, host it with a `MessageObject`-shaped
holder carrying our post's blocks plus the shallow flag set above, and draw one
post body with it (the `ChatMessageCell` delegate/`TransitionParams` seam is the
work). If a post-body golden can be produced that way, the body renderer is
upstream's, one file, re-syncable. If the host turns out to be chat-entangled
beyond the access list, fall back to the cell extraction described above:
`BlockBlockquoteCell` into a mirror file hosted by `IArticleViewer` + a slim
adapter carrying the eleven members, then the View-vs-canvas decision once, for
all cells.

**R3 — if either spike holds, extend it block by block.**
Each adopted renderer becomes a mirror file with a reported delta instead of a
permanent hand port; what stays ours is what Discourse has and Telegram does not
(polls, oneboxes, callouts) plus the boundary parser. If both spikes fail,
record why here and keep the ports — R1 still pays for itself.

Explicit non-claim: R1 changes nothing visible; R2/R3 change how the body is
drawn, which is exactly why they need the goldens *and* a device pass
(`docs/device-verification.md`).

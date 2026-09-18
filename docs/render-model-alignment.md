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

## Staged plan

**R1 — make `TL_iv` the post-body model (cheap, pixel-gated).**
The parser stops emitting HTML strings and emits `TL_iv` page blocks plus
`TL_iv.RichText` runs (`textBold`/`textItalic`/`textMono`/`textStrike`/
`textUnderline`/`textSpoiler`/`textMarked`/`textUrl`/`textMention`/…), and the
blocks read the mirror's fields. `TL_iv` is already real at the pin's names
(`8c1e55f`, `2e7c61c`) and `TableLayout` is already the pin's file, so nothing
new lands in the mirror. Payoff: one place for markup semantics, inline text
consumes Telegram's model instead of a home-grown one, and any later cell copy
becomes mechanical. Gate: the eight `TopicPostCellScreenshotTest` goldens plus
the suite — a pure refactor of the boundary should not move a pixel.

**R2 — extraction spike: one cell.**
Take `BlockBlockquoteCell` (or `BlockPreformattedCell`), move it into a mirror
file, host it with `IArticleViewer` + a slim adapter interface carrying the
eleven members above, and render it inside one post cell. If the golden for that
block stays green, the extraction generalizes; then decide the View-vs-canvas
question once, for all cells.

**R3 — if R2 holds, extract the cells one at a time.**
Each cell becomes a mirror file with a reported delta instead of a permanent
hand port; what stays ours is what Discourse has and Telegram does not (polls,
oneboxes, callouts) and the boundary parser itself. If R2 fails, record the
failure here and keep the ports — R1 still pays for itself.

Explicit non-claim: R1 changes nothing visible; R2/R3 change how the body is
drawn, which is exactly why they need the goldens *and* a device pass
(`docs/device-verification.md`).

# ArticleViewer rebase plan (Telegram 12.8.1)

> **Status: epic closed at B21** (2026-07-18). Do **not** open further AV slices
> unless a named product goal reopens one of the parked items below.
>
> **Do not clean-FF** `ArticleViewer.java`. Full-file pin replace remains off-limits.
> WebInstantView S1 (TL_iv→TLRPC) is done; ArticleViewer must use the **same** type map.

## Why it is hard

| Fact | Implication |
|------|-------------|
| ~15k lines local / ~16.5k pin | Full-file review impossible |
| ~900 hunks va→pin | Naïve 3-way merge explodes |
| ~786 `TL_iv` refs on pin | Must S1-map before compile |
| Host: WebBookmarks, Sheet/BottomSheetTabs, ActivityResult | Blind pin loses NativeDo product wiring |
| `:app` uses only a **small** public surface | Contract tests can protect the real risk |

## Public contract (:app / topic)

| API | Consumer |
|-----|----------|
| `ArticleViewer.getInstance()` / `hasInstance()` | TableBlock, internal |
| `ArticleViewer.DrawingText` | `TableBlock`, `TableLayout` |
| `ArticleViewer.ErrorContainer` | `LoginWebViewFragment` |
| `ArticleViewer.dispatchActivityResult` | `MainActivity` |
| `ArticleViewer.Sheet` (indirect) | `BaseFragment` / tabs |

Topic body cells (`ParagraphBlock`, …) are **native ports**; they do not call most `Block*Cell` types.

## Phases

### Phase 0 — Guardrails (this pass)

- [x] Contract unit tests: `org.telegram.ui.ArticleViewerContractTest`
- [x] Manual checklist (below)
- [x] S1 preview script (does not write main tree)

```bash
./apps/android/gradlew -p apps/android :app:testDebugUnitTest \
  --tests 'org.telegram.ui.ArticleViewerContractTest' --quiet
```

### Phase 1 — Type-layer preview (this pass)

```bash
python3 scripts/vendor_articleviewer_s1_preview.py
python3 scripts/vendor_articleviewer_s1_preview.py --write /tmp/ArticleViewer-s1.java
```

Exit criteria: remaining `TL_iv` types empty; all map targets exist on local `TLRPC`.

### Phase 2 — Sheet / lifecycle (partial landed)

**Attempted full pin+S1:** 322 compile errors. Top blockers:

| Missing | Count (approx) | Notes |
|---------|----------------|-------|
| `IArticleViewer` base class | 71 | Pin extracts shared state/API into abstract base |
| Field moves (`popupWindow`, `pressedLink*`, `videoPlayer`, …) | many | Live on `IArticleViewer` / restructured fields |
| `Page.local`, `cancelTextSelectionRunnable`, jlatexmath | mid | Pin-only APIs / optional deps |
| Local-only file chooser | — | Must keep host `dispatchActivityResult` region |

**Do not** replace main `ArticleViewer.java` with pin until `IArticleViewer` (+ field moves) is planned as its own PR.

**Landed on local (safe subset of pin Sheet):**

- Nested-scroll dismiss gated with `!textSelectionHelper.isInSelectionMode()` (preFling / preScroll / stopNestedScroll)
- Corner radii: attached `dp(24)`, `drawInto` `lerp(dp(16), dp(18), …)` (was 16/10)
- Host preserved: WebBookmarks bookmark path, ActionBarLayout overlay dismiss, file chooser / `dispatchActivityResult`
- `refreshThemeColors` also updates `backgroundPaint` (pin 12.8.1)
- `getLastRichText(TL_textAnchor)` now returns (local bugfix aligned with intended control flow)
- `PageLayout.isLocal()` stub + `actionBar.setIsLocal` on URL/page transitions (always false until `Page.local` exists)


Next Phase 2 slice options:

1. Introduce thin local `IArticleViewer` **without** full pin body (API surface only) — high cost
2. Continue surgical Sheet/chrome patches only — preferred
3. Defer rest of AV until product needs full IV-in-viewer parity


### Phase B1 — Thin `IArticleViewer` (landed)

Introduced `org.telegram.ui.IArticleViewer` (API surface only):

- Abstract: account, text/link/gray colors, themed color, resources provider, `getResources()`, text-selection helper
- Defaults: `padx`/`pady` (18/8), `allowTouches`, empty `openWebpageUrl` / `openPhoto(TLRPC.PageBlock)` / …
- **Not moved yet:** `pressedLink*`, `videoPlayer`, `searchResults`, `popupWindow`, … still on `ArticleViewer`
- `ArticleViewer.Resources` empty shell (paint maps stay as existing statics until B2)
- `WebpageAdapter` visibility public for base type references

```bash
# verify
./apps/android/gradlew -p apps/android :telegram:compileDebugJavaWithJavac --quiet
./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'org.telegram.ui.ArticleViewerContractTest' --quiet
```

### Phase B2 — Field move (landed) + DrawingText(parent)

1. [x] Move pin-matching fields from `ArticleViewer` onto `IArticleViewer` (public)
2. [x] `DrawingText(IArticleViewer parent)` + no-arg convenience; draw via parent fields (B3)
3. [x] B4: Resources holds paint SparseArrays; static names alias shared instance
4. [x] B5: Resources owns paint color/font updates; outer paint creation/color entry points match pin
5. [x] B6: link-touch and text-selection drawing helpers use the pin-shaped `IArticleViewer` boundary
6. [x] B7a: text flags and paint selection use the pin-shaped static parent/resource boundary
7. [x] B7b: move supported S1 rich-text construction onto the parent-driven `getText` boundary
8. [x] B7c: move `createLayoutForText` onto the parent-driven core
9. [x] B8: parent-driven `makeProgress` + `drawQuoteLines` helpers (simple gutter sites)
10. [x] B9: pilot parent-reshape `BlockParagraphCell` (`IArticleViewer` + static helpers)
11. [x] B10: parent-reshape simple text blocks (Header/Title/Subtitle/Subheader/Kicker/Footer)
12. [x] B11: parent-reshape AuthorDate / Preformatted / Pullquote / Blockquote
13. [x] B12: parent-reshape Details + Related articles cells
14. [x] B13: parent-reshape List + OrderedList item cells
15. [x] B14: parent-reshape `BlockTableCell` (P0 product path)
16. [x] B15: parent-reshape media + channel cells (Photo/Video/Audio/Map/Channel) + loadChannel/joinChannel
17. [x] B16: EmbedPost static parent reshape + Embed/Collage/Slideshow helper rewire
18. [x] B17: `IBlock` + DrawingText bounds + `buildAccessibilityText` / `appendA11yLabel` foundation
19. [x] B18: wire remaining static parent cells to IBlock/a11y + AccDescrIV strings
20. [x] B19: spoiler attach / liveDrawingTexts / spoiler-hit
21. [x] B20a: WebActionBar search menu restores pin header height reset
22. [x] B20b: pin-shaped `DrawingText.attach/detach` on all Block* `onAttachedToWindow` / `onDetachedFromWindow` sites (incl. TableLayout children); EmbedPost + Slideshow a11y labels
23. [x] B21: vendor pin-identical `MultiLayoutTypingAnimator`; `DrawingText` implements `Block`, exposes `typingAnimator`, pin draw fade branch (IV leaves field null; rich-message hosts can bind later)

### Epic close (B21) — parked, not abandoned

Autonomous pin-alignment of ArticleViewer **stops here**. What landed covers the
`:app` public contract (`DrawingText`, `ErrorContainer`, `dispatchActivityResult`,
Sheet lifecycle host wiring) and the parent-driven Block* / paint / spoiler /
attach / typing **surface**. Remaining deltas are intentional host product or
S1 type omissions — re-open only with an explicit product ticket:

| Parked id | Topic | Re-open when |
|-----------|--------|--------------|
| ~~A3~~ | `TL_textSpoiler` content production | **Landed:** `TLRPC.TL_textSpoiler` + pin-shaped `getText`/`getLastRichText`/`getPlainText`/`setRichTextParents`. WebInstantView still does not emit spoilers from HTML (matches pin). Discourse topic spoilers stay on the native body path. |
| ~~A4~~ | Broader Sheet / WebActionBar chrome | **Partial landed:** pin-shaped `actionBar.setIsLocal` on page-switch / close / open stack transitions + `openWebpageUrl` offline confirm wrapper. Host kept: `WebBookmarks`, `RestrictedDomainsList` open-external, file chooser, `capturePreviewBitmap`, no `Page.local` File / markdown open. |
| A5 | Embed/Collage/Slideshow static reshape | Pin geometry bug or forced_exact audit requires it (pin is also non-static) |
| ~~A6~~ | Heading1–6 maps / `quoteLevels` | **Landed:** `PageBlock.quoteLevels` + multi-layer `drawQuoteLines`; `TL_pageBlockHeading1`…`6` + Resources heading paints (18–12dp); `isHeadingBlock` / BlockHeaderCell / adapter / setRichTextParents / auto-anchor; WebInstantView emits h1–h6 as Heading*; `TL_pageBlockBlockquoteBlocks` flattens with quoteLevels mask. S1 preview maps updated. |
| ~~A7~~ | CodeHighlighting / table long-press selection | **Landed:** vendored `CodeHighlighting` + `codelng.gzip`; `BlockPreformattedCell` uses pin `getText` + `getHighlighted(language)`; `BlockTableCell` pin long-press selection via `canStartSelection`/`dispatchTouchEvent`. |

**Default:** keep `modified:true` + `stuck_reason`; do not bulk-copy pin chrome.
Contract gate for any future AV PR remains Phase 0 tests + manual checklist.

### Phase B3 — DrawingText(parent) (landed)

- `DrawingText` stores `IArticleViewer parent`
- No-arg ctor → enclosing/`getInstance()` ArticleViewer (TableBlock-safe)
- `draw` reads `parent.searchResults` / `links` / pressed-link selection
- Typing fade: `MultiLayoutTypingAnimator` vendored (B21); spoiler + a11y conversion landed in B18/B19

### Phase 3 — WebActionBar / bookmarks chrome

Align with already-pinned `WebActionBar`, scheme-2 `AddressBarList` / `WebMetadataCache`.

### Phase 4 — Block cells (slice by product need)

| Priority | Area | Why |
|----------|------|-----|
| P0 | `DrawingText`, `BlockTableCell` | `TableBlock` hard dependency |
| P1 | Paragraph / header / photo / list | Full IV page in-viewer |
| P2 | Embed / channel / map / related | Rare |

One PR per group. Run `TableBlock` / body tests every time.

### Phase 5 — PhotoViewer bridge

`RealPageBlocksAdapter` / providers — only if IV image open is required.

## Host patch inventory (seed)

Grep local for re-apply anchors before Phase 2:

```bash
rg -n 'NativeDo|WebBookmarks|OMITTED|ADAPTED|getActionBarLayout' \
  apps/android/telegram/src/main/java/org/telegram/ui/ArticleViewer.java
```

Known product wiring today:

- Bookmark menu → `WebBookmarks.getInstance(currentAccount).add(url, title)` (not MTProto Saved Messages)

## Manual checklist (every ArticleViewer PR)

- [ ] Cold start app; open an external link in in-app browser sheet
- [ ] Sheet opens, scrolls, dismisses (back + swipe if applicable)
- [ ] Bookmark add/remove still hits **local** bookmarks (not Telegram Saved)
- [ ] Login WebView error container still shows (`ErrorContainer`)
- [ ] File chooser / `onActivityResult` path does not crash (`dispatchActivityResult`)
- [ ] Topic detail table cells still render (`TableBlock` + DrawingText)
- [ ] `scripts/vendor-status.sh` clean (or only expected stuck)

## Automated checklist

```bash
./apps/android/gradlew -p apps/android :telegram:compileDebugJavaWithJavac --quiet
./apps/android/gradlew -p apps/android :app:compileDebugKotlin --quiet
./apps/android/gradlew -p apps/android :app:testDebugUnitTest \
  --tests 'org.telegram.ui.ArticleViewerContractTest' \
  --tests 'io.github.baba983.nativedo.ui.topic.body.*' \
  --quiet
scripts/vendor-status.sh
```

## Anti-patterns

- Single PR full-file pin replace
- Mixing TableLayout pin (CellText/TL_iv) with ArticleViewer mid-flight
- Dropping WebBookmarks for upstream Saved-Messages send
- Merging without Phase 0 tests green

### Phase B4 — Resources paint bridge (landed)

- `ArticleViewer.Resources` now has pin-shaped `SparseArray<TextPaint>` fields + `isRichMessage`
- Static map fields (`paragraphTextPaints`, …) **alias** `resources.*` (same instances)
- `getResources()` returns the shared static instance
- Heading1–6 maps omitted (local S1 uses Header/Subheader only)

### Phase B5 — Paint resource updates (landed)

- `Resources.updatePaintColors(IArticleViewer)`, private `setMapColors(...)`,
  `updatePaintFonts(int)`, and private `updateFontEntry(...)` now match the pin's ownership
- Outer `createPaint(IArticleViewer, boolean)` and static
  `updatePaintColors(IArticleViewer)` match the pin-shaped host boundary
- `updatePaintSize()` correctly remains on outer `ArticleViewer`, as it does upstream
- Heading1–6 paint-map loops remain omitted because local S1 maps headings to Header/Subheader
- Shared map aliases, no-arg `Resources()` support, lazy `errorTextPaint` sync, and NativeDo
  bookmark/sheet/file-chooser wiring remain intact

### Phase B6 — Parent-driven interaction helpers (landed)

- `checkLayoutForLinks(IArticleViewer, ...)` and `drawTextSelection(IArticleViewer, ...)`
  now expose the pinned static helper boundary; existing instance wrappers remain for local cells
- `ArticleViewer.allowTouches()` keeps link handling gated while page-switch animation is active
- URL/anchor dispatch now lives in the pinned `handleLinkClick(WebpageAdapter, TextPaintUrlSpan)`
  host callback; long-press handling stays in the `checkLayoutForLinks(MotionEvent, View)` override
- Pressed-link cleanup now has the pinned parent-driven helper
- The pinned spoiler-hit branch remains deferred with the rest of the local `DrawingText` spoiler
  lifecycle; this slice does not introduce `TL_iv`, `TableLayout.CellText`, or block-cell reshaping

### Phase B7 — Parent-driven text layout foundation

#### B7a — Paint selection (landed)

- `TEXT_FLAG_*`, `getTextFlags(TLRPC.RichText)`, and `isListItemBlock(TLRPC.PageBlock)`
  now expose the pinned public static surface with the local S1 types
- `getTextPaint(IArticleViewer, ...)` selects maps, colors, font mode, and font-size source from
  the supplied parent's `Resources`; the instance wrapper remains for existing callers
- Preformatted blocks now force `TEXT_FLAG_MONO` as pinned; shared-resource lazy
  `errorTextPaint` synchronization remains intact
- Contract coverage verifies an isolated parent receives its own cached preformatted paint

#### B7b — Parent-driven getText (landed)

- `getLastRichText(TLRPC.RichText)` is the pinned public static helper over local S1 rich-text nodes
- `getText(IArticleViewer, WebpageAdapter|WebPage, ...)` exposes the pinned static parent boundary;
  instance wrappers still route existing cell callers through `this`
- Supported S1 nodes keep the recursive span construction path (style wrappers, url/email/phone,
  anchor, concat, marked, spoiler, image) and paint selection via `getTextPaint(parent, ...)`
- Inline image invert brightness now uses the pin-shaped
  `AndroidUtilities.computePerceivedBrightness(parent.getThemedColor(...))` path
- **A3:** `TL_textSpoiler` content production landed (see reopen table). Math (`textMath`) remains deferred.
- Contract coverage verifies parent-driven bold+url concat and spoiler span construction against isolated resources

#### B7c — Parent-driven createLayoutForText (landed)

- Overloads expose the pinned static `createLayoutForText(IArticleViewer, …)` surface; instance
  wrappers still route block-cell callers through `this`
- Body uses parent-driven `getText` / `getTextPaint`, parent text/gray colors for special paints
  (embed post author/date, channel name, related articles, list markers), and parent
  `searchResults` / `searchText` / `getAdapter()` for search offsets
- `DrawingText` remains a non-static nested class for `TableBlock` (`viewer.new DrawingText`);
  static core constructs via the ArticleViewer outer when the parent is not one
- Local Emoji shim keeps `ALIGN_BASELINE`; spoiler / `liveDrawingTexts` / typing attach lifecycle
  remain deferred with the local S1 surface
- Contract coverage verifies parent-driven paragraph layout and plain-text embed-author layout

### Phase B8 — Parent-driven progress + quote gutters (landed)

- `makeProgress` exposes the pinned static parent core; instance wrapper remains for local callers
- `drawQuoteLines(Canvas, IArticleViewer, PageBlock, height)` is the pin-shaped public helper;
  local S1 `PageBlock` has `level`/`bottom` only (no pin `quoteLevels` mask), so the helper draws
  the single-level gutter at `parent.padx()` when `level > 0`
- Ten block `onDraw` sites that previously inlined the simple `dp(18)…dp(20)` gutter now call
  `drawQuoteLines`; EmbedPost / BlockBlockquoteCell keep their specialized quote geometry
- Spoiler / typing / a11y / full Block*Cell `(IArticleViewer parent)` reshape remain deferred
- Contract coverage verifies null-safe parent-driven `drawQuoteLines` after `createPaint`

### Phase B9 — BlockParagraphCell parent reshape pilot (landed)

- `BlockParagraphCell` is now a static nested class with `(Context, IArticleViewer, WebpageAdapter)`
- Measure/draw/touch use `parent.padx()`/`pady()`, parent-driven `createLayoutForText` /
  `checkLayoutForLinks` / `drawTextSelection` / `drawQuoteLines`
- `WebpageAdapter` constructs with `ArticleViewer.this` as parent
- Deferred for this pilot: `IBlock` bounds, spoiler attach/detach, `buildAccessibilityText`,
  math-centered alignment (`TL_textMath`)
- Contract coverage measures a parent-hosted paragraph cell against isolated Resources

### Phase B10 — Simple text Block*Cell parent reshape (landed)

- Static parent reshape for: `BlockHeaderCell`, `BlockTitleCell`, `BlockSubtitleCell`,
  `BlockSubheaderCell`, `BlockKickerCell`, `BlockFooterCell`
- Each takes `(Context, IArticleViewer, WebpageAdapter)` and uses `parent.padx()`/`pady()` plus
  parent-driven `createLayoutForText` / `checkLayoutForLinks` / `drawTextSelection`
  (`drawQuoteLines` on Footer)
- `WebpageAdapter` constructs with `ArticleViewer.this`
- Deferred: `IBlock` bounds, spoiler attach/detach, `buildAccessibilityText`, AuthorDate /
  Preformatted / quote / list / media / table cells
- Contract coverage measures a parent-hosted header cell against isolated Resources

### Phase B11 — AuthorDate / Preformatted / quote cells (landed)

- Static parent reshape for: `BlockAuthorDateCell`, `BlockPreformattedCell`,
  `BlockPullquoteCell`, `BlockBlockquoteCell`
- Parent-driven `getText` / `createLayoutForText` / `checkLayoutForLinks` /
  `drawTextSelection`; Blockquote keeps specialized level/RTL gutter plus `drawQuoteLines`
- Preformatted keeps horizontal scroll + local `windowView` disallow-intercept host wiring;
  scroll selection helper comes from `parent.getTextSelectionHelper`
- Deferred: `IBlock` bounds, spoiler attach/detach, `buildAccessibilityText`, CodeHighlighting
  language path, list/media/table cells
- Contract coverage measures a parent-hosted pullquote cell against isolated Resources

### Phase B12 — Details + Related cells (landed)

- Static parent reshape for: `BlockDetailsCell`, `BlockRelatedArticlesHeaderCell`,
  `BlockRelatedArticlesCell`, `BlockRelatedArticlesShadowCell`
- Details uses `parent.getGrayTextColor()` for the arrow, `parent.padx()` for title width /
  arrow x, and parent-driven createLayout/checkLinks/drawTextSelection
- Related header/cell/shadow route theme/photo/layout through parent + adapter
- Deferred: list/ordered-list nested block holders, media cells, table, channel/embed,
  `IBlock` bounds, a11y conversion
- Contract coverage measures a parent-hosted details cell against isolated Resources

### Phase B13 — List / OrderedList item cells (landed)

- Static parent reshape for: `BlockListItemCell`, `BlockOrderedListItemCell`
- Nested block holders still come from `adapter.onCreateViewHolder` / `bindBlockToHolder`
- Measure/draw/touch use parent-driven createLayout/checkLinks/drawTextSelection and
  `parent.getTextSelectionHelper` when adjusting nested DrawingText offsets
- Geometry prefers `parent.padx()`/`pady()` (list first-item top uses pin-shaped
  `dp(2 + pady)`); pin checklist checkbox path and IBlock bounds remain deferred
- Contract coverage constructs both cells and measures the empty-block sentinel height

### Phase B14 — BlockTableCell parent reshape (landed)

- `BlockTableCell` is a static nested cell with `(Context, IArticleViewer, WebpageAdapter)`
- Scroll/title/cell text use parent-driven createLayout/checkLinks/drawTextSelection and
  `parent.getTextSelectionHelper`; search offsets write through adapter/`parent.getAdapter()`
- Geometry prefers `parent.padx()`/`pady()`; nested scroll disallow still uses local
  `windowView` host wiring when parent is an ArticleViewer
- Deferred: pin selection long-press dispatch, CellText attach/detach, IBlock bounds,
  media/channel/embed cells
- Contract coverage constructs a parent-hosted table cell and measures the empty-block sentinel
- `:app` `TableBlock` remains its own native port and does not depend on this nested class

### Phase B15 — Media + Channel cells (landed)

- Static parent reshape for: `BlockPhotoCell`, `BlockVideoCell`, `BlockAudioCell`,
  `BlockMapCell`, `BlockChannelCell`
- `loadChannel` / `joinChannel` expose pin-shaped static parent cores; instance wrappers remain
- Media cells use `parent.getCurrentAccount()` / themed colors / `parent.openPhoto` and keep
  ArticleViewer-only host wiring for pinch-to-zoom, parentFragment channel open, and video player
  fields via `parent.videoPlayer` / `parent.videoStates`
- Nested channel chips construct with `(parent, adapter, type=1)`
- Deferred: Embed/EmbedPost/Collage/Slideshow; pin WebView join result path; IBlock/a11y
- Contract coverage constructs parent-hosted media/channel cells and measures empty state

### Phase B16 — Embed family (landed)

- `BlockEmbedPostCell` is a static nested cell with `(Context, IArticleViewer, WebpageAdapter)`
  and parent-driven createLayout/checkLinks/drawTextSelection
- `BlockEmbedCell`, `BlockCollageCell`, `BlockSlideshowCell` remain non-static (matches pin 12.8.1;
  WebView / nested adapters need outer ArticleViewer host state) but now call parent-driven
  helpers via `ArticleViewer.this`
- Nested collage/slideshow photo+video cells already construct with `ArticleViewer.this`
- Deferred: making Embed/Collage/Slideshow fully static; Divider/DetailsBottom are already
  parent-less static paint helpers
- Contract coverage constructs a parent-hosted EmbedPost cell and measures the empty-block sentinel

### Phase B17 — IBlock + DrawingText bounds + a11y helpers (landed)

- `DrawingText` exposes pin-shaped `getBoundLeft` / `getBoundRight` / `getLastLineBoundRight`
  (cached) plus `setX`/`setY`/`setRow`
- `ArticleViewer.IBlock` is the public static bounds interface with `getMinWidth()` default
- `buildAccessibilityText(parent, adapter, layout)` converts URL spans to clickable a11y spans via
  `parent.handleLinkClick`; `appendA11yLabel` appends localized labels
- Simple text cells implement `IBlock` with `padx`-relative bounds: Paragraph, Header, Title,
  Subtitle, Subheader, Kicker, Footer; a11y paths use the new helpers where labels exist
- Deferred: spoiler/typing attach lifecycle, remaining Block*Cell IBlock coverage, full AccDescr
  string set beyond Title/Heading
- Contract coverage locks DrawingText bounds + a11y helpers + Paragraph `IBlock` empty bounds

### Phase B18 — Remaining static cells IBlock/a11y (landed)

- IBlock bounds on: AuthorDate, RelatedArticlesHeader, Preformatted, Pullquote, Blockquote,
  ListItem, OrderedListItem (plus B17 simple text set)
- a11y helpers on: Kicker/Footer labels, Related header/cell, Details expand/collapse, Table,
  Channel, Preformatted code, Pullquote/Blockquote multi-layout
- Added local `AccDescrIV*` strings (Kicker/Footer/Code/Pullquote/Blockquote/Related/Details/
  Expanded/Collapsed/Table) next to existing Title/Heading
- Deferred: spoiler/typing attach; deep nested list IBlock recursion with numLayout; media
  photo/video a11y polish; full Sheet chrome
- Contract coverage asserts AuthorDate/Pullquote/Preformatted implement `IBlock`

### Phase B19 — Spoiler attach / liveDrawingTexts (landed)

- `DrawingText` gains pin-shaped spoiler fields, `attach`/`detach`/`isAttached`, and spoiler draw via `SpoilerEffect.renderWithRipple`
- Static `liveDrawingTexts` registry + `createLayoutForText` tail matches pin (pool/list/replace/attach when view is attached)
- `checkLayoutForLinks` restores pin spoiler-hit / ripple / clear
- OMITTED (pre-B20b/B21): real AnimatedEmoji pipeline (stub no-op). Typing draw branch + cell attach sites landed in B20b/B21.
- **A3 landed:** `TLRPC.TL_textSpoiler` + `getText` applies `TextStyleSpan.FLAG_STYLE_SPOILER` (pin `TL_iv.textSpoiler` shape). S1 preview maps `textSpoiler` → `TL_textSpoiler` (no longer wrongly → `TL_textMarked`). HTML→spoiler production is still out of scope (pin WebInstantView does not emit it either).
- Contract: empty spoiler pools without spans; attach/detach lifecycle safe

### Phase B20a — WebActionBar search header height (landed)

- Search menu item restores pin `actionBar.setHeight(currentHeaderHeight = dp(56))` before `showSearch`
- Broader Sheet dismiss/preview, bookmark MTProto path, and open-external alert remain host-wired NativeDo deltas (not bulk-copied)

### Phase B21 — MultiLayoutTypingAnimator (landed)

- Vendored pin-identical `org.telegram.ui.MultiLayoutTypingAnimator` (`exact`, `modified:false`, forced_exact animation/drawing)
- `DrawingText` implements `MultiLayoutTypingAnimator.Block` with pin-shaped `getParentView()` / existing `StaticLayout getLayout()` (local TextLayoutBlock contract)
- `typingAnimator` field + pin draw branch (`needDraw` early-out + `drawLayoutWithLastLineFade`); IV path leaves field null so default draw/spoiler path unchanged
- Does **not** vendor RichMessageLayout / BotForumHelper start sites — those are the only pin producers of a non-null animator

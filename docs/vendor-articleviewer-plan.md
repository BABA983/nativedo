# ArticleViewer rebase plan (Telegram 12.8.1)

> **Do not clean-FF** `ArticleViewer.java`. Treat as a multi-PR epic.
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
10. [ ] Only then re-attempt larger pin body / Block*Cell reshape slices

Do **not** full-file replace until B2 compiles with contract tests green.

### Phase B3 — DrawingText(parent) (landed)

- `DrawingText` stores `IArticleViewer parent`
- No-arg ctor → enclosing/`getInstance()` ArticleViewer (TableBlock-safe)
- `draw` reads `parent.searchResults` / `links` / pressed-link selection
- Still **not** ported: MultiLayoutTypingAnimator, Spoiler, and a11y text conversion

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
  anchor, concat, marked, image) and paint selection via `getTextPaint(parent, ...)`
- Inline image invert brightness now uses the pin-shaped
  `AndroidUtilities.computePerceivedBrightness(parent.getThemedColor(...))` path
- Spoiler / math (`textSpoiler`, `textMath`) remain deferred with the rest of the local S1 surface
- Contract coverage verifies parent-driven bold+url concat span construction against isolated resources

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

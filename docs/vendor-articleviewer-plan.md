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

Next Phase 2 slice options:

1. Introduce thin local `IArticleViewer` **without** full pin body (API surface only) — high cost
2. Continue surgical Sheet/chrome patches only — preferred
3. Defer rest of AV until product needs full IV-in-viewer parity

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

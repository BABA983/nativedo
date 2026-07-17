# Browser / Instant View suite (global pin 12.8.1)

## Why this suite exists

NativeDo reuses Telegram's **in-app browser + Instant View** stack for:
- opening links in a Telegram-like sheet (`ArticleViewer` / browser chrome)
- site metadata (favicon, sitename) for chrome and bookmarks
- address bar suggestions / local bookmarks UI

Discourse topic **body** rendering mostly uses native ports of ArticleViewer *cells*
(`ParagraphBlock`, `TableBlock`, …), but the **browser chrome + IV extraction** still
lives under `org.telegram.ui.web.*` + `ArticleViewer`.

## Suite map

| File | Role | Status |
|------|------|--------|
| `WebMetadataCache` | Favicon/sitename cache + offscreen WebView extract | **Scheme-2 @ pin** (host wiring kept) |
| `AddressBarList` | Address bar list + bookmarks UI | **Scheme-2 @ pin** (WebBookmarks backend) |
| `WebInstantView` | HTML → Instant View page model | **S1 reverse-patch @ pin** (TL_iv→TLRPC + host readHTML) |
| `ArticleViewer` | Sheet chrome + IV display + related UI | **Epic after TL_iv / WebInstantView** |
| `Browser` (native) | URL open dispatch | Host shim (not exact FF) |
| `WebActionBar` | Browser action bar | Already @ pin |
| `WebBrowserSettings` | Telegram browser settings | Stuck (low product ROI) |

## Why not clean-FF everything

1. **WebInstantView / ArticleViewer (pin)** rename Instant View types to `TL_iv.*`.
   Local stack still speaks `TLRPC.TL_page*` (and topic `TableBlock` etc. depend on that).
2. **AddressBarList** must not go back to `MediaDataController.loadMedia` (MTProto Saved Messages).
3. **WebMetadataCache** pin is a thinner cache; local adds **offscreen WebView** extraction
   wired through `LaunchActivity.getActionBarLayoutActivity()` so IV/favicon works on NativeDo.

## How to proceed

### Done
- Scheme-2 baseline for `WebMetadataCache` + `AddressBarList`.
- **S1 reverse-patch** for `WebInstantView` (TL_iv→TLRPC; host Activity for readHTML).

### Next (when ready for real IV pin sync)
1. Pick **TL_iv strategy**:
   - **S1 reverse-patch** (recommended): take pin WebInstantView, map `TL_iv.*` → `TLRPC.*` page types.
   - **S2 thin shim**: `org.telegram.tgnet.tl.TL_iv` aliases to TLRPC.
   - **S3 full `TL_iv.java`**: last resort (~2k LOC).
2. Rebase `WebInstantView` with host `readHTML` Activity resolution retained.
3. Only then plan `ArticleViewer` multi-PR rebase.
4. Keep `GroupedPhotosListView` / `TableLayout` on same TL_iv decision (shared).

### Do not
- `vendor-ff` WebInstantView or ArticleViewer without TL_iv plan.
- Reintroduce MTProto bookmarks in AddressBarList.

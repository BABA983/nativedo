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
| `ArticleViewer` | Sheet chrome + IV display + related UI | **Epic closed at B21**; **A3 spoiler content landed** (A4–A7 parked) |
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

### Next (suite, not AV)
1. Keep `GroupedPhotosListView` / `TableLayout` on the existing S1 (TLRPC) decision unless product reopens TL_iv.
2. Triage other browser stuck files (`WebBrowserSettings`, …) only with product ROI.
3. **ArticleViewer** — closed; see below.

### Do not
- `vendor-ff` WebInstantView or ArticleViewer without an explicit product + TL plan.
- Reintroduce MTProto bookmarks in AddressBarList.
- Resume autonomous ArticleViewer slices (A4–A7) without a product ticket. A3 (`TL_textSpoiler`) is landed.

## ArticleViewer epic (closed at B21; A3 reopened and landed)

Phased parent-driven rebase **landed through B21**. **A3** (`TLRPC.TL_textSpoiler` content
production) later landed on product request. Remaining host chrome and S1 omissions (A4–A7)
stay parked by design. Full history, contract tests, and reopen table:
[`vendor-articleviewer-plan.md`](vendor-articleviewer-plan.md).

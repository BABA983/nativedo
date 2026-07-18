# Browser / Instant View suite (global pin 12.9.0)

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
| `ArticleViewer` | Sheet chrome + IV display + related UI | **Epic closed** (B21 + A3/A4p/A6/A7); host chrome kept; A5 wontfix |
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
2. Triage browser stuck files (`WebBrowserSettings`, `WebInstantView` host deltas, …) only with product ROI.
3. Prefer small exact+modified hygiene or ImageReceiver pair over reopening ArticleViewer.

### Do not
- `vendor-ff` WebInstantView or ArticleViewer without an explicit product + TL plan.
- Reintroduce MTProto bookmarks in AddressBarList.
- Resume autonomous ArticleViewer slices — epic is closed.

## ArticleViewer epic (closed)

B1–B21 + **A3** spoiler content + **A4-partial** local chrome + **A6** headings/quoteLevels +
**A7** CodeHighlighting/table selection. Host keeps WebBookmarks / RestrictedDomains / file chooser /
preview bitmap. **A5** wontfix (pin non-static). Full history:
[`vendor-articleviewer-plan.md`](vendor-articleviewer-plan.md).

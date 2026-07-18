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
| `WebMetadataCache` | Favicon/sitename cache + offscreen WebView extract | **Scheme-2 @ 12.9.0** + host extract; `host_wiring_keep_at_pin` |
| `AddressBarList` | Address bar list + bookmarks UI | **Scheme-2 @ 12.9.0** + WebBookmarks; `host_wiring_keep_at_pin` |
| `WebInstantView` | HTML → Instant View page model | **S1 @ 12.9.0** (TL_iv→TLRPC + host Activity); `host_wiring_keep_at_pin` |
| `BookmarksFragment` | Bookmarks UI | **@ 12.9.0** + WebBookmarks/Entry; no Saved-Messages jump; `host_wiring_keep_at_pin` |
| `RestrictedDomainsList` | Open-external domain allow/deny | **Host shim** (no upstream blob at pin); `native_host_no_upstream_blob` |
| `ArticleViewer` | Sheet chrome + IV display + related UI | **Epic closed** (B21 + A3/A4p/A6/A7); host chrome kept; A5 wontfix |
| `Browser` (native) | URL open dispatch | Host shim (not exact FF) |
| `WebActionBar` | Browser action bar | Already @ pin |
| `WebBrowserSettings` | Telegram browser settings | **pin-identical @ 12.9.0**; façade → SharedConfig/RestrictedDomainsList |

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

### Next (suite)
1. Keep `GroupedPhotosListView` / `TableLayout` on the existing S1 (TLRPC) decision unless product reopens TL_iv.
2. Browser core four + RestrictedDomainsList are **documented keep** — do not clean-FF.
3. Prefer `ImageReceiver`+`ImageLocation` pair or product-driven `WebBrowserSettings` over reopening AV/browser FF.

### Do not
- `vendor-ff` WebInstantView or ArticleViewer without an explicit product + TL plan.
- Reintroduce MTProto bookmarks in AddressBarList.
- Resume autonomous ArticleViewer slices — epic is closed.

## ArticleViewer epic (closed)

B1–B21 + **A3** spoiler content + **A4-partial** local chrome + **A6** headings/quoteLevels +
**A7** CodeHighlighting/table selection. Host keeps WebBookmarks / RestrictedDomains / file chooser /
preview bitmap. **A5** wontfix (pin non-static). Full history:
[`vendor-articleviewer-plan.md`](vendor-articleviewer-plan.md).

## Runtime wiring (NativeDo)

- `Browser.openUrl` uses `MessagesController.isWebBrowserOpenInApp(url)` =
  `SharedConfig.inappBrowser && !RestrictedDomainsList.isRestrictedUrl(url)`.
- Profile → **浏览器设置** opens `WebBrowserSettings` sheet (same as ArticleViewer chrome).
- Custom Tabs via `androidx.browser`: when in-app is off (or domain restricted) and
  `SharedConfig.customTabs` is true, `Browser.openUrl` tries Custom Tabs after
  non-browser app handlers; else `ACTION_VIEW`. Bound from `MainActivity`.

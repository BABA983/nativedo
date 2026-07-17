# Exact rebase queue (global pin 12.8.1)

## Scheme-2 complete (this area)
- `Utilities.java`
- `WebMetadataCache.java`
- `AddressBarList.java`

## Browser/IV still blocked (see docs/vendor-browser-iv-suite.md)
- `telegram/src/main/java/org/telegram/ui/web/WebInstantView.java` — TL_iv
- `telegram/src/main/java/org/telegram/ui/ArticleViewer.java` — epic after TL_iv

## Other / low ROI
- `WebBrowserSettings.java` — Telegram settings surface; prefer permanent stuck or native thin page

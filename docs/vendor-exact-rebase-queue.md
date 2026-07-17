# Exact rebase queue (global pin 12.8.1)

## Scheme-2 / S1 complete
- `Utilities.java` — scheme-2
- `WebMetadataCache.java` — scheme-2
- `AddressBarList.java` — scheme-2
- `WebInstantView.java` — **S1 reverse-patch** (TL_iv→TLRPC + host readHTML)

## Still blocked / epic
- `telegram/src/main/java/org/telegram/ui/ArticleViewer.java` — large UI + IV display; after product priority
- `WebBrowserSettings.java` — low ROI Telegram settings surface

## List stack (done earlier)
- `RecyclerListView.java`, `RecyclerAnimationScrollHelper.java`

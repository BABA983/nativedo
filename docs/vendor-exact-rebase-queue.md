# Exact rebase queue (global pin 12.8.1)

## Done
- List stack: RecyclerListView, RecyclerAnimationScrollHelper
- Utilities scheme-2
- Browser chrome scheme-2: WebMetadataCache, AddressBarList
- WebInstantView S1 reverse-patch
- ArticleViewer B1–B4 (IArticleViewer base/fields/DrawingText/Resources bridge) — full body still epic
- **Cleared entire "FF clean pin fails compile" class (5 files)** — pin 12.8.1 + host re-apply:
  - GraySectionCell
  - NestedSizeNotifierLayout
  - RLottieImageView
  - FragmentSearchField
  - FlickerLoadingView

## ArticleViewer (epic)
- See `docs/vendor-articleviewer-plan.md` (B1–B4 landed; no full-file pin)

## Still heavy stuck (not FF-host class)
- WebBrowserSettings — MessagesController browser policy APIs
- GroupedPhotosListView / TableLayout — TL_iv / CellText coupling
- ImageReceiver / ImageLocation — partial 12.8.1
- Native host shims (Theme, MessagesController, ApplicationLoader, …) — not FF candidates
- NativeDo ports under `app/` with stuck_reason (forced_exact reference only)

## Next global picks
1. Other small exact files still on `vendored_at` 009e… **with** non-FF stuck reasons (triage case-by-case)
2. Or forced_exact regression tests / product paths for the 5 just unstuck

# Exact rebase queue (global pin 12.8.1)

## Done
- List stack: RecyclerListView, RecyclerAnimationScrollHelper
- Utilities scheme-2
- Browser chrome scheme-2: WebMetadataCache, AddressBarList
- WebInstantView S1 reverse-patch
- ArticleViewer B1–B21 — **epic closed** (parent-driven cells, attach, typing surface; host chrome/S1 parked)
- **Cleared entire "FF clean pin fails compile" class (5 files)** — pin 12.8.1 + host re-apply:
  - GraySectionCell
  - NestedSizeNotifierLayout
  - RLottieImageView
  - FragmentSearchField
  - FlickerLoadingView
    - host contract: `FlickerLoadingViewHostContractTest` (REACTED_TYPE = 50dp)

## ArticleViewer (closed)
- See `docs/vendor-articleviewer-plan.md` — closed at B21. Do not resume AV slices without a product ticket (parked A3–A7).
- No full-file pin; `modified:true` + stuck_reason remain by design.

## Still heavy stuck (not FF-host class)
- WebBrowserSettings — MessagesController browser policy APIs
- GroupedPhotosListView / TableLayout — TL_iv / CellText coupling
- ImageReceiver / ImageLocation — partial 12.8.1
- Native host shims (Theme, MessagesController, ApplicationLoader, …) — not FF candidates
- NativeDo ports under `app/` with stuck_reason (forced_exact reference only)

## Next global picks
1. Other small exact files still on `vendored_at` 009e… **with** non-FF stuck reasons (triage case-by-case)
2. Or forced_exact regression tests / product paths for the 5 just unstuck
3. **Not** ArticleViewer — epic closed; only re-open with product ticket

## Testing policy (vendor FF)

Add a unit test **only** when it locks a *host-specific contract* that a clean pin FF would break:

| Worth it | Examples |
|----------|----------|
| Yes | Host constant replacing missing upstream type (`dp(50)` vs `ITEM_HEIGHT_DP`); public measure/API :app depends on |
| Yes | ArticleViewer public surface (`ErrorContainer`, `DrawingText` ctor) |
| No | "File compiles"; re-asserting pure upstream `forced_exact` math already covered by pin fidelity |
| No | Full onDraw / gesture / nested-scroll without product path or Paparazzi |

GraySectionCell / NestedSizeNotifierLayout / RLottieImageView / FragmentSearchField:
host deltas are structural (omit class, TextView swap) — compile + isolation is the check; no value in trivial construct tests.

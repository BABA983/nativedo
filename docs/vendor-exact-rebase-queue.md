# Exact rebase queue (global pin 12.9.0)

## Done
- Global pin bump 12.8.1 → **12.9.0** (`9bcf3d2769c6`): FF exact/clean + host-wire UniversalRecyclerView/BottomSheetWithRecycler; absorb small pin deltas into Utilities/LayoutHelper/RecyclerListView/FragmentSearchField; ThemeColors host colors retained
- List stack: RecyclerListView, RecyclerAnimationScrollHelper
- Utilities scheme-2
- Browser chrome scheme-2: WebMetadataCache, AddressBarList
- WebInstantView S1 reverse-patch
- ArticleViewer B1–B21 + A3 / A4-partial / A6 / A7 — **epic closed** (host chrome remainder + A5 wontfix)
- **Cleared entire "FF clean pin fails compile" class (5 files)** — pin 12.8.1 + host re-apply:
  - GraySectionCell
  - NestedSizeNotifierLayout
  - RLottieImageView
  - FragmentSearchField
  - FlickerLoadingView
    - host contract: `FlickerLoadingViewHostContractTest` (REACTED_TYPE = 50dp)

## ArticleViewer (closed — not next work)
- See `docs/vendor-articleviewer-plan.md`.
- Landed: parent-driven cells, attach, typing, spoiler content, headings/quoteLevels, CodeHighlighting, table selection, partial local chrome.
- Keep: WebBookmarks / RestrictedDomains / file chooser / preview bitmap (product host).
- Wontfix: Embed/Collage/Slideshow static reshape (matches pin).
- No full-file pin; `modified:true` + stuck_reason = host chrome only.

## Still heavy stuck (not FF-host class)
- WebBrowserSettings — MessagesController browser policy APIs
- GroupedPhotosListView / TableLayout — TL_iv / CellText coupling
- ImageReceiver / ImageLocation — legacy pin (re-sync only as a **pair** toward 12.9.0)
- Native host shims (Theme, MessagesController, ApplicationLoader, …) — not FF candidates
- NativeDo ports under `app/` with stuck_reason (forced_exact reference only)

## Next global picks (priority order)
1. ~~**Small exact+modified hygiene** at pin 12.9.0~~ — **done**: notes/stuck for host-wiring keep on AnimatedTextView, ThemeColors, LayoutHelper, GraySectionCell, FragmentSearchField, RLottieImageView, NestedSizeNotifierLayout, ScrollSlidingTextTabStrip, UniversalRecyclerView, IMessageCell, BottomSheetWithRecyclerListView, PullForegroundDrawable, DialogsItemAnimator, AnimationProperties, IArticleViewer (`stuck_ref: host_wiring_keep_at_pin`). Deltas are intentional; not FF candidates.
2. **Browser stack** if IV/bookmarks product needs it: `WebInstantView`, `AddressBarList`, `WebMetadataCache`, `BookmarksFragment`
3. **ImageReceiver + ImageLocation** pair only with a named image-pipeline goal (still legacy pin; ImageReceiver also import_only Log/AnimatedEmoji delta on top)
4. **Not ArticleViewer** — epic closed
5. Remaining exact+modified at pin with heavier `other` (Utilities, RecyclerListView) — only with product feel/bug

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

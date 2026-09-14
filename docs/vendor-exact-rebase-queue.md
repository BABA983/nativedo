> **Historical plan (retired manifest model).** Current workflow: `scripts/vendor-guard.sh` + [VENDOR.md](../VENDOR.md).
> Terms like `stuck_reason`, `modified:true`, and `vendor-manifest.json` below refer to the
> pre-overlay model; provenance is now derived.

# Exact rebase queue (global pin 12.9.0)

## Done
- Global pin bump 12.8.1 → **12.9.0** (`9bcf3d2769c6`): FF exact/clean + host-wire UniversalRecyclerView/BottomSheetWithRecycler; absorb small pin deltas into Utilities/LayoutHelper/RecyclerListView/FragmentSearchField; ThemeColors host colors retained
- List stack: RecyclerListView, RecyclerAnimationScrollHelper
- Utilities scheme-2
- Utilities + RecyclerListView host-keep re-align @ 12.9.0 (blur3 restored; no-JNI documented)
- ImageReceiver + ImageLocation restored pin-identical @ 12.9.0
- GroupedPhotosListView + TableLayout S1 reverse-patch @ 12.9.0 (TL_iv→TLRPC)
- WebBrowserSettings pin-identical @ 12.9.0 (browser façade)
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
- Keep: WebBookmarks / local browser settings / file chooser / preview bitmap (product host).
- Wontfix: Embed/Collage/Slideshow static reshape (matches pin).
- No full-file pin; `modified:true` + stuck_reason = host chrome only.

## Still heavy stuck (not FF-host class)
- ~~WebBrowserSettings~~ — **pin-identical @ 12.9.0** (MessagesController façade → local TL_webBrowserSettings)
- ~~GroupedPhotosListView / TableLayout~~ — **host keep @ 12.9.0** (S1 TL_iv→TLRPC; CellText+DrawingText)
- ~~ImageReceiver / ImageLocation~~ — **byte-identical @ 12.9.0** (pair re-sync; host TLRPC/emoji stubs)
- Native host shims (Theme, MessagesController, ApplicationLoader, …) — not FF candidates
- NativeDo ports under `app/` with stuck_reason (forced_exact reference only)

## Alignment policy (`:telegram`)

- Prefer **byte-identical pin** when host APIs exist (or can be filled with pin-shaped helpers).
- Host wiring lives in **manifest notes / stuck**, not long `// NativeDo` / `// HOST-WIRING` comments in source.
- Keep only unavoidable code deltas (missing types, product backends).

## Next global picks (priority order)
1. ~~**Small exact+modified hygiene**~~ — **done**; then **re-aligned to pin** where possible: `ScrollSlidingTextTabStrip`, `BottomSheetWithRecyclerListView` now `modified:false`. Restored pin call shapes via `HintView2.measureCorrectly/cutInFancyHalf`, `Emoji.replaceEmoji`, `TextViewEmojis`, `AndroidUtilities.setLightStatusBar(Dialog)`. Stripped host comment noise from related files.
2. ~~**Browser stack**~~ — **done (host keep, comments trimmed)**: WebInstantView / AddressBarList / BookmarksFragment / WebMetadataCache / local TL_webBrowserSettings.
3. ~~**ImageReceiver + ImageLocation**~~ — **done**: both `modified:false` byte-identical at pin 12.9.0; host stubs for InvalidateHolder / fromMessage / rich_message.
4. **Not ArticleViewer** — epic closed (still host-wired; avoid new comment sprawl).
6. ~~**WebBrowserSettings**~~ — **done**: pin-identical; single-account façade on local TL_webBrowserSettings.
5. ~~**Utilities + RecyclerListView**~~ — **done (host keep @ pin)**: RecyclerListView restored to pin-shaped blur3/SectionsAdapter/edge-effect with only `rectTmp2I` host delta; Utilities pin surface + no-JNI Java fallbacks, `stuck_ref=host_wiring_keep_at_pin`.

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

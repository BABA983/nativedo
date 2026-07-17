# Exact rebase queue (global pin 12.8.1)

## Done
- List stack: RecyclerListView, RecyclerAnimationScrollHelper
- Utilities scheme-2
- Browser chrome scheme-2: WebMetadataCache, AddressBarList
- WebInstantView S1 reverse-patch

## ArticleViewer (epic)
- Phase 0+1 done; Phase 2 **partial** (Sheet selection gate + radii). Full pin blocked on IArticleViewer — see plan.
- Contract tests: `org.telegram.ui.ArticleViewerContractTest`
- S1 preview: `scripts/vendor_articleviewer_s1_preview.py` (map complete; main file not replaced)

## Low ROI
- WebBrowserSettings — permanent stuck / native thin page

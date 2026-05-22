# PhotoViewer Pruning Plan

This plan captures the intended shape of the NativeDo PhotoViewer port: start from Telegram's upstream `PhotoViewer.java`, keep the high-fidelity viewer interactions, and prune product areas that are not needed for reading linux.do posts.

## Goal: viewer subset

Use **Option B: Viewer Subset** rather than a full byte-for-byte port or a rewrite. Copy the upstream component first, then remove specific feature areas while preserving the gesture, animation, measurement, drawing, and paging code paths that are easy to regress.

## Pruning decisions

| Area | Decision | Reason |
| --- | --- | --- |
| Paint / annotation | Cut | NativeDo is a reader; image markup is out of scope. |
| Crop / rotate | Cut | Image editing is not needed for post viewing. |
| Filters | Cut | Brightness, contrast, and filter tools are editor features. |
| Sticker maker | Cut | Telegram-specific creation flow. |
| Story recorder | Cut | NativeDo does not support Stories. |
| Chromecast | Cut | Casting is low priority and dependency-heavy. |
| Picture-in-picture | Cut for v1 | Reduce playback and lifecycle complexity. |
| Social / premium actions | Cut | Replace Telegram internals with Android system share where needed. |
| Video editing | Cut | Trimming, compression, cover editing, and export are out of scope. |
| Picker / local gallery selection | Cut | NativeDo opens media from linux.do content, not a local media picker. |

## Preserve

- Image browsing with pinch-to-zoom, pan, and double-tap zoom.
- Morph open/close animations between the post list and fullscreen viewer.
- Horizontal paging across images in the current post.
- Bottom thumbnail strip (`GroupedPhotosListView`) and captions.
- Basic actions: save to gallery, copy, system share, and long-press download menu.
- Basic MP4/GIF playback, including seek bar and fast-forward/rewind gestures.

## Integration strategy

### Adapter boundary

Avoid coupling NativeDo UI code to Telegram MTProto models. Prefer the existing `PhotoViewer.PageBlocksAdapter` path from Telegram Instant View:

- Implement a NativeDo adapter such as `DiscoursePagePhotosAdapter`.
- Map NativeDo URL-based photo specs to the page-block photo API.
- Use URL loading via `ImageLocation.getForPath(url)` when there is no Telegram file location.

### Stub policy

Keep Telegram model/controller stubs minimal:

- Include only fields and methods that the pruned viewer actually accesses.
- Return safe defaults for unavoidable checks.
- Throw `UnsupportedOperationException` for branches that should be unreachable after pruning.
- Do not expand stubs into a generic Telegram compatibility layer.

### Façade

Expose the viewer through a small NativeDo-facing façade, for example `NativeDoPhotoViewer.open(activity, photos, index)`, so feature code does not depend directly on Telegram-shaped APIs.

## Implementation steps

1. Copy upstream `PhotoViewer.java` into the Android source tree and record it in `vendor-manifest.json` with `port_mode = native`.
2. Run the vendor diff before each edit and keep gesture, animation, measurement, drawing, and recycling behavior upstream-shaped unless deliberately documented.
3. Prune the ten cut feature areas above.
4. Port only the viewer dependencies required by the preserved feature set, such as video playback, grouped thumbnails, captions, and image receiver infrastructure.
5. Add minimal Telegram stubs only when the pruned file still needs them to compile.
6. Implement the NativeDo adapter and façade.
7. Verify URL image loading, URL video/GIF playback, paging, zoom, captions, save/copy/share, and long-press download behavior.

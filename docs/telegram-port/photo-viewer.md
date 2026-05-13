# Telegram Workflow Map: Photo Viewer

1. Upstream scope:
   - `org/telegram/ui/PhotoViewer.java`
   - `org/telegram/ui/Components/GroupedPhotosListView.java`
   - `PhotoViewer.PlaceProviderObject`
   - `PhotoViewer.PhotoViewerProvider`
   - `ChatActivity.photoViewerProvider` / `getPlaceForPhoto(...)`

2. Entry points:
   - Telegram: message, page-block, profile, picker, and media-search surfaces call `PhotoViewer.openPhoto(...)` with a provider.
   - NativeDo: `BrowserInstantViewFragment` opens Instant View photo blocks through `ui.photo.PhotoViewer`.
   - NativeDo: topic cooked-post images open the same viewer from `TopicDetailFragment`.

3. Screens/fragments/sheets:
   - Telegram: activity-level overlay with top chrome, bottom grouped-photo strip, source transition, and provider-backed source visibility.
   - NativeDo: activity `WindowManager` overlay attached above the Telegram-derived fragment stack, using Telegram-shaped layout flags.

4. Menus/actions:
   - Telegram: close, more actions, save/share/open/edit/caption/action-mode depending on media type.
   - NativeDo current scope: close, save image, share URL, long-press actions, top chrome, and bottom grouped-photo strip.

5. State transitions:
   - Tap source image -> provider returns source bounds/thumb -> viewer hides source -> open animation morphs source rect to centered image.
   - While open, pinch/double-tap/pan/page-drag update the current image transform.
   - Drag down hides chrome, scales parent content, fades background, and either closes or restores image/chrome.
   - Back/top-left close hides chrome immediately, closes to the provider source when available, otherwise closes offscreen.
   - Close completion restores source visibility, system bars, parent scale, and active-viewer state.

6. Storage/backend dependencies:
   - Telegram: `MessageObject`, `TLRPC.PageBlock`, `ImageLocation`, `ImageReceiver`, `FileLoader`, and Telegram cache keys.
   - NativeDo: URL-backed `PhotoEntry`, provider source bounds, optional provider bitmap thumbnail, NativeDo authenticated image `OkHttpClient`, memory cache, and app-cache disk files. This is `ADAPTED-BACKEND`.

7. Navigation/back behavior:
   - Telegram consumes back while the viewer is visible and does not let the host stack animate underneath.
   - NativeDo `MainActivity`, `BrowserInstantViewFragment`, and `TopicDetailFragment` delegate back to `PhotoViewer` before host navigation. This is `HOST-WIRING`.

8. Unsupported or adapted dependencies:
   - `HOST-WIRING`: NativeDo attaches through Activity `WindowManager` with Telegram-shaped overlay flags, falling back to the decor view only if the window token is unavailable.
   - `HOST-WIRING`: NativeDo uses the local Telegram-derived `ActionBar` and menu classes.
   - `ADAPTED-BACKEND`: URL bitmap loading replaces Telegram `ImageReceiver` / `FileLoader`.
   - `ADAPTED-BACKEND`: Provider `thumb` bitmaps and URL cache replace Telegram's exact thumbnail receiver pipeline.
   - `ADAPTED-PRODUCT`: Topic and Instant View galleries are the currently rendered URL image groups, not Telegram chat grouped media.
   - `OMITTED`: video/document playback, media search paging, edit/crop, caption editing, action mode, PiP, spoilers, and exact `ClippingImageView`/`ImageReceiver` transport.
   - `PARITY-BLOCKER`: exact Telegram media backend parity needs `MessageObject`/`ImageReceiver`/`FileLoader` style host objects that NativeDo topic/browser content does not have.

Current task scope:
- In scope:
  - Keep current URL-backed `PhotoViewer` but align reported interaction regressions with Telegram's close/drag behavior.
  - Instant View opens the full rendered photo group so the bottom grouped-photo strip can display.
  - Provider thumbnails are accepted before network thumbnail loading so already rendered images can appear immediately.
  - Phase 1 Telegram-shape realignment: `windowView`/`containerView`/`FrameLayoutDrawer` scaffold, upstream `GestureDetector2`, and Telegram-named `leftImage`/`centerImage`/`rightImage` `ImageReceiver` slots for the existing URL image path.
- Deferred:
  - Full Telegram `ImageReceiver` thumbnail transport and `ClippingImageView` close/open object animation.
  - Full Telegram persistent `ImageReceiver`/`FileLoader` object transport; NativeDo now uses a shared URL bitmap memory/disk loader for the current viewer/browser/topic surfaces.
- Parity blockers:
  - NativeDo still lacks Telegram's persistent media/file-loader object graph, so exact object-level `PhotoViewer` backend parity is not available in this branch.

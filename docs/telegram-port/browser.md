# Telegram Workflow Map: Browser

1. Upstream scope:
   - `org/telegram/ui/ArticleViewer.java`
   - `org/telegram/ui/web/WebInstantView.java`
   - `org/telegram/ui/web/WebActionBar.java`
   - `org/telegram/ui/web/BrowserHistory.java`
   - `org/telegram/ui/web/BookmarksFragment.java`
   - `org/telegram/ui/web/HistoryFragment.java`
   - `org/telegram/ui/web/WebBrowserSettings.java`
   - `org/telegram/ui/web/AddressBarList.java`
   - `org/telegram/ui/web/WebMetadataCache.java`
   - `org/telegram/ui/web/RestrictedDomainsList.java`
   - `org/telegram/ui/Components/UniversalFragment.java`
   - `org/telegram/ui/Components/UniversalAdapter.java`
   - `org/telegram/ui/Components/UItem.java`
   - `org/telegram/ui/PhotoViewer.java`
   - `org/telegram/ui/PinchToZoomHelper.java`

2. Entry points:
   - Browser sheet more-menu actions for Instant View, Settings, History, Bookmarks, bookmark toggle, open external, copy link, and font size.
   - Browser fragment menu actions for the same browser feature surfaces.
   - In-document Instant View links, which may push another instant-view fragment when generated JSON is available.
   - Settings rows that open History inside the browser feature sheet.

3. Screens/fragments/sheets:
   - NativeDo `BrowserSheet` and `BrowserFragment` remain the browser hosts.
   - `BaseFragment.showAsSheet(...)` hosts browser subfeatures in a nested `ActionBarLayout` through NativeDo's `ActionBarSheet` host.
   - `BrowserInstantViewFragment` renders local cached-page-like Instant View content.
   - `BrowserListFragment` renders History and Bookmarks.
   - `BrowserSettingsFragment` renders browser settings.
   - `BrowserInstantPhotoViewer` is the current single-image overlay used from Instant View images.
   - `UniversalFragment`, `UniversalRecyclerView`, `UniversalAdapter`, `UItem`, and `UniversalCells` provide the list shell and cells used by Settings, History, and Bookmarks.

4. Menus/actions:
   - Web action bar menu: open external, copy link, share (NativeDo addition), Instant View, font size, history, settings, add/remove bookmark, forward.
   - Instant View actions: open external, copy link, change font size, open cached in-document Instant View links, open image viewer.
   - History/Bookmarks actions: search, open selected entry.
   - Settings actions: enable/disable in-app browser, open history, clear history (with pluralized page count), clear cookies/cache (with formatted sizes), add/remove or clear restricted domains (rows enriched with metadata sitename/favicon), choose search engine.
   - Deferred Telegram actions: forward button as a separate toolbar entry, action mode/bulk delete, Saved Messages bookmark navigation.
   - Address bar actions: current URL card, autocomplete suggestions, recent searches with clear, local bookmark rows, query insert arrow, and URL/query execution.

5. State transitions:
   - Browser page load updates history, bookmark state, current title/url, Instant View availability, and restricted-domain behavior.
   - Instant View transitions from generated JSON to parsed `BrowserPage.PageBlock` rows; empty/parse failure shows unavailable state.
   - Instant View scroll updates header height and reading progress.
   - Instant View font scale persists in `BrowserStorage`, clears the recycled row pool, and recreates rows.
   - Instant View image tap opens a single-image overlay; back dismisses the overlay before the fragment.
   - History and Bookmarks transition between normal, search, empty, and filtered-empty states.
   - Settings rows update local storage and refresh the Universal list.
   - Browser feature sheet push/pop uses `BaseFragment.showAsSheet(...)` plus the nested `ActionBarLayout`; selecting a history/bookmark entry loads the URL in the host and dismisses the sheet.

6. Storage/backend dependencies:
   - Telegram history uses `BrowserHistory` with serialized local data and metadata integration.
   - Telegram bookmarks are backed by Saved Messages and metadata cache paths.
   - Telegram settings include browser flags, custom tabs, restricted domains, metadata cache, and account/global state.
   - NativeDo uses `BrowserStorage` with `SharedPreferences` for history, bookmarks, restricted domains, search engine, external-link mode, and Instant View font scale. This is `ADAPTED-BACKEND`.
   - NativeDo stores favicon bitmaps inline as Base64 in local JSON. This is `ADAPTED-BACKEND`.
   - NativeDo Instant View uses generated JSON from the WebView/readability chain instead of Telegram remote `TLRPC.WebPage.cached_page`. This is `ADAPTED-BACKEND`.
   - NativeDo `BrowserMetadataCache` stores Telegram-shaped domain metadata in `SharedPreferences` JSON instead of Telegram TLObject/file serialization. This is `ADAPTED-BACKEND`.
   - NativeDo address suggestions use Telegram's default search engines and autocomplete parsing, but execute network requests through a local executor instead of Telegram `HttpGetTask`. This is `HOST-WIRING`.
   - NativeDo recent searches are local `BrowserStorage` entries capped at 20 and written only when a query is finally submitted. This is `ADAPTED-BACKEND`.

7. Navigation/back behavior:
   - Browser feature sheets are attached to the host fragment and dim over the current browser surface.
   - Settings can push History inside the same feature sheet.
   - History/Bookmarks entry selection dismisses the feature sheet and loads the URL in the host browser.
   - Instant View opens as a feature sheet and uses WebActionBar back to finish/dismiss through the host stack.
   - In-document Instant View links push a sibling `BrowserInstantViewFragment` when cached generated JSON exists; otherwise they open externally.
   - `BrowserInstantPhotoViewer` consumes back first while visible.
   - Telegram nested scroll-to-dismiss, keyboard-first attached-back handling, full browser page stack, and bottom-action-bar mode are deferred.

8. Unsupported or adapted dependencies:
   - `HOST-WIRING`: NativeDo package/import/resource/theme mapping, AndroidX `RecyclerView`, local `ActionBarLayout`, local `BaseFragment.AttachedSheet`, and `ActionBarSheet` as the current `showAsSheet(...)` host.
   - `ADAPTED-BACKEND`: `BrowserStorage` replaces Telegram history/bookmark/settings/recent-search backends; `BrowserMetadataCache` replaces Telegram metadata file serialization; generated Instant View JSON replaces `TLRPC.WebPage.cached_page`; Telegram `RichText` subclasses are represented by local `RichText` text/entities instead of TLObject instances.
   - `ADAPTED-PRODUCT`: Local Instant View renderer supports only the block families modeled by `BrowserPage`; Phase 2 adds Telegram-shaped `PageBlockUnsupported`, `PageBlockAnchor`, `PageBlockFooter`, `PageBlockEmbed`, `PageBlockVideo`, `PageBlockAudio`, `PageBlockCover`, `PageBlockSlideshow`, and `PageBlockCollage` models, but media/embed rows render as readable cards or expanded image sequences instead of Telegram's inline WebView/player/gallery internals; Phase 3 aligns parser coverage for Telegram rich-text tags, phone/email/link/anchor spans, inline image entities, and `figure source/srcset` alternatives, while paragraph/list/blockquote inline images are promoted to photo blocks until exact `TL_textImage` rendering is ported; Settings exposes only NativeDo-supported browser settings (cookie/cache rows render Formatter.formatShortFileSize, Clear History uses a pluralized page-count string, restricted-domain rows pull sitename/favicon from `BrowserMetadataCache`); WebActionBar overflow adds a NativeDo Share entry that fans out to `Intent.ACTION_SEND`; `BrowserInstantPhotoViewer` ports Telegram PhotoViewer/PinchToZoom gesture constants and transform math into NativeDo's single-image Instant View overlay.
   - `OMITTED`: Telegram `BottomSheet` Dialog internals, account-scoped bookmark sync, Saved Messages bookmark navigation, hidden WebView metadata retrieval for unopened URLs, remote web-page fallback, full media/file loader integration, inline embed WebView playback, audio/video player controls, exact slideshow/collage paging, global `PhotoViewerProvider`/`ImageReceiver`/pageBlocksAdapter media backend, action mode, bulk selection, separate toolbar forward button (forward stays in the overflow menu only), custom tabs, debug settings, bottom action bar, browser multi-page stack.
   - `PARITY-BLOCKER`: Full Telegram `PhotoViewer` backend is not ported; current image viewer ports the gesture/animation/measurement core into a NativeDo single-image overlay.
   - `PARITY-BLOCKER`: Exact Telegram media/embed/player/gallery behavior is not fully ported; current Phase 2 rows keep content visible and openable without claiming exact `ArticleViewer` parity.

Current task scope:
- In scope:
  - Document Browser as a `Feature Workflow Port` before continuing feature implementation.
  - Mark current Instant View, Settings, History, Bookmarks, storage, and Universal list support as workflow-level Browser work.
  - Route Settings, History, and Bookmarks through `BaseFragment.showAsSheet(...)` instead of a browser-local sheet container.
  - Align Browser Settings row order with Telegram `WebBrowserSettings`: enable in-app browser, clear cookies/cache, history, restricted domains, and search engine.
  - Align search engines with Telegram defaults: Google, DuckDuckGo, Bing, Yahoo, and Brave; remove NativeDo's previous linux.do search engine option.
  - Port the AddressBarList workflow for current page card, suggestions, recent searches, query insert, URL/query execution, and local bookmark rows.
  - Add a NativeDo `BrowserMetadataCache` backend for metadata-backed bookmark rows using live WebView title/favicon/siteName/color events.
  - Move Instant View model/adapter toward Telegram-shaped `PageBlock` rows for title/header/subheader/paragraph, quotes, list/ordered list, details, table, photo, preformatted, and divider.
  - Add Phase 2 Instant View content coverage for unsupported placeholders, anchors, footer text, embeds, video/audio cards, cover blocks, and slideshow/collage image sequences.
  - Add Phase 3 Instant View parser alignment for Telegram `parseRichText(...)` families: bold/italic/strike/fixed/marked/sub/sup/link/phone/email/anchor/inline image, list inline detection, table cell rich text, details summary/body rich text, empty anchor preservation, blockquote inline media, and `figure source/srcset` photo alternatives.
  - Port Phase 3 exact-component cores: `ArticleViewer.BlockTableCell`/`TableLayout` table drawing and measurement, `BlockDetailsCell` expand/collapse drawing and row updates, and `PhotoViewer`/`PinchToZoomHelper` single-image gesture physics.
  - Mark Phase 3 inline rich-image rendering as `ADAPTED-PRODUCT`: parser preserves the entity, but current text renderer uses a placeholder span in rich-text surfaces and promotes paragraph/list/blockquote images to visible photo blocks.
  - Mark Phase 2 media/embed/gallery rows as `ADAPTED-PRODUCT`: content is visible and openable, but inline WebView, player, and gallery gesture internals remain deferred.
  - Keep current staged behavior intact while making port boundaries reviewable.
- Deferred:
  - Exact `ArticleViewer.BlockListItemCell` and `BlockOrderedListItemCell` text-selection internals.
  - Exact Telegram `TL_textImage` inline image rendering through ArticleViewer image receiver spans.
  - Full Telegram `PhotoViewerProvider` / `ImageReceiver` / pageBlocksAdapter backend for multi-media Instant View galleries.
  - Inline `BlockEmbedCell` WebView, `BlockVideoCell` player, `BlockAudioCell` player, exact `BlockSlideshowCell` pager, exact `BlockCollageCell` grouped layout, hidden WebView metadata retrieval for unopened URLs, full action mode, multi-page browser stack, nested scroll dismiss, and bottom action bar.
- Parity blockers:
  - Local Instant View parser/model now exposes the Phase 2 media families, but generated readability JSON still cannot represent every Telegram cached-page/media backend field.
  - Full Telegram PhotoViewer backend is still omitted even though the single-image gesture core is ported.
  - Current media/embed/gallery rows do not match Telegram's inline player, WebView, or gesture physics.

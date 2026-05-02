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
   - `org/telegram/ui/Components/PinchToZoomHelper.java`

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
   - `ADAPTED-BACKEND`: `BrowserStorage` replaces Telegram history/bookmark/settings/recent-search backends; `BrowserMetadataCache` replaces Telegram metadata file serialization; generated Instant View JSON replaces `TLRPC.WebPage.cached_page`.
   - `ADAPTED-PRODUCT`: Local Instant View renderer supports only the block families modeled by `BrowserPage`; Settings exposes only NativeDo-supported browser settings (cookie/cache rows render Formatter.formatShortFileSize, Clear History uses a pluralized page-count string, restricted-domain rows pull sitename/favicon from `BrowserMetadataCache`); WebActionBar overflow adds a NativeDo Share entry that fans out to `Intent.ACTION_SEND`; `BrowserInstantPhotoViewer` is a single-image NativeDo overlay and does not claim Telegram `PhotoViewer` parity.
   - `OMITTED`: Telegram `BottomSheet` Dialog internals, account-scoped bookmark sync, Saved Messages bookmark navigation, hidden WebView metadata retrieval for unopened URLs, remote web-page fallback, full media/file loader integration, action mode, bulk selection, separate toolbar forward button (forward stays in the overflow menu only), custom tabs, debug settings, bottom action bar, browser multi-page stack.
   - `PARITY-BLOCKER`: Exact Telegram Instant View table measurement is not fully ported; current table renderer is a NativeDo implementation that consumes `PageBlockTable` but must not be declared exact parity.
   - `PARITY-BLOCKER`: Exact Telegram details animation/selection behavior is not fully ported; current details rows follow Telegram's adapter expansion/filtering path.
   - `PARITY-BLOCKER`: Exact Telegram `PhotoViewer` / `PinchToZoomHelper` gesture physics is not ported; current image viewer is a NativeDo single-image overlay.
   - `PARITY-BLOCKER`: Telegram media families not represented by `BrowserPage` are unsupported until parser/model/host support exists.

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
  - Mark current table/details geometry and photo-viewer gestures as current-phase adaptations/blockers rather than exact Telegram parity.
  - Keep current staged behavior intact while making port boundaries reviewable.
- Deferred:
  - Exact `ArticleViewer.BlockListItemCell` and `BlockOrderedListItemCell` text-selection internals.
  - Exact Telegram `PhotoViewer` / `PinchToZoomHelper` gesture and animation behavior.
  - Hidden WebView metadata retrieval for unopened URLs, share, forward button, full action mode, multi-page browser stack, nested scroll dismiss, and bottom action bar.
- Parity blockers:
  - Local Instant View parser/model does not expose all Telegram cached-page media families.
  - Current table/details renderers are not exact Telegram cells.
  - Current image viewer does not match Telegram PhotoViewer gesture physics.

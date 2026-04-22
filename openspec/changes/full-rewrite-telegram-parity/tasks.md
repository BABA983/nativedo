## 0. Ground Rules

> **默认业务实现使用新文件。** 新框架的业务与基础设施实现写入新的包路径下；旧代码（`core/`、`feature/`、`ui/`、`navigation/` 下的现有文件）仅作为参考，不在其上缝补。
> `MainActivity`、`NativeDoApplication`、Gradle/Manifest/Proguard、应用主题等集成边界文件允许按迁移需要修改。
> 新代码写入新的包路径下，完成后旧文件整体删除。旧代码的有用逻辑（如 Discourse API 端点定义、Session 管理细节）可以提取复制到新文件中。
> 现有测试不做一次性清空；仍然表达有效契约的网络/session/html parsing 测试先保留，待新实现和替代验证到位后再删除。
> 每个 Phase 结束时，确认该 Phase 涉及的旧文件无引用后批量删除。
> 对于明确采用 Telegram 同名类/方法的基础设施，默认保持 Telegram 的公开 API 形状与行为语义；可以只引入当前真正需要的子集，但不应把同名 helper 改造成语义不同的项目私有 convenience wrapper。若因 Android 平台限制、linux.do 场景裁剪、或 Telegram 的 native/MTProto/多账号依赖而必须偏离，需先在 change artifacts 中写明偏离理由，再实现。
> **无直接 consumer 不预建 helper。** 对 Telegram 没有对应公共类、且当前 Phase 没有真实调用方的工具类，不为“任务完整度”预先创建独立 `*Utils` / `*Helper` 文件。
> **默认不新增 substrate standalone unit test。** `core/util/`、`core/event/` 这类 Telegram substrate 除非在修复已复现回归，否则不新增 standalone unit test；验证以 host screen、manual smoke、integration 契约为主。

- 当前 2.x / 3.x 实现审视结论：保留为 parity-first 基线的有 `AndroidUtilities.kt`、`Utilities.kt`、`DispatchQueue.kt`、`FileLog.kt`、`AnimatedFloat.kt`、`AnimatedColor.kt`、`NotificationCenter.kt`、`AppLifecycleMonitor.kt`。
- 当前 2.x / 3.x 需要回退或重做的有 `LayoutHelper.kt`、`CubicBezierInterpolator.kt` 的常量/API 形状、`SpringAnimationUtils.kt`、`AndroidUtilitiesTest.kt`、`NotificationCenterTest.kt`。

## 1. Project Scaffolding & Build Config

- [ ] 1.1 Create new package structure under `io.github.baba983.nativedo/`: `core/util/`, `core/event/`, `core/theme/`, `core/media/`, `ui/actionbar/`, `ui/cells/`, `ui/components/`, `ui/web/`, `discourse/api/`, `discourse/model/`, `discourse/session/` — 全部新文件，不复用旧包
- [ ] 1.2 Update `build.gradle.kts` dependencies: remove Compose Navigation, keep OkHttp/Retrofit/kotlinx.serialization/Coil 3/Jsoup/Room, keep Compose runtime (for ComposeView embedding)
- [ ] 1.3 Add GPL v2 license header template for files derived from Telegram source
- [ ] 1.4 Configure proguard rules for new class structure
- [ ] 1.5 Implement `NdApp.kt` bootstrap and `NdRuntime.kt` single-account service holder: global context, main-thread handler, shared services registry, and early init of logging/theme/session infrastructure
- [ ] 1.6 Define `MainActivity.kt` as the simplified `LaunchActivity`-like host: keep a migration-time root switch between legacy Compose root and new `ActionBarLayout` root, bridge lifecycle/back/new-intent/window-insets/system-bar policy, and avoid introducing a separate screen router abstraction or second launcher Activity

## 2. Core Utilities (core/util/)

- [x] 2.1 Implement `AndroidUtilities.kt` as a parity-first Kotlin port of the currently needed subset of Telegram `org.telegram.messenger.AndroidUtilities.java`: `dp()`, `dpf2()`, `lerp()`, `displaySize`, `statusBarHeight`, `navigationBarHeight`, `density`, configuration change listener
- [x] 2.2 Implement color utilities: `blendColors()`, `setAlpha()`, `argb()` helpers
- [x] 2.3 Implement keyboard utilities: `showKeyboard()`, `hideKeyboard()`, keyboard height detection
- [x] 2.4 Implement slim `Utilities.kt` as a parity-first subset of Telegram `org.telegram.messenger.Utilities.java`: `clamp()`, `clamp01()`, main-thread helpers, and only the small cross-cutting helpers actually needed by new infrastructure
- [x] 2.5 Implement `DispatchQueue.kt` as a parity-first subset port of Telegram `org.telegram.messenger.DispatchQueue.java`: named serial background queue abstraction for logging, theme I/O, and image-related support work
- [x] 2.6 Implement `FileLog.kt` as a parity-first subset port of Telegram `org.telegram.messenger.FileLog.java`: async debug logger backed by `DispatchQueue`, available from app startup
- [ ] 2.7 Rework `LayoutHelper.kt` as a parity-first subset port of Telegram `org.telegram.ui.Components.LayoutHelper.java`: preserve imported method names, return types, and layout params semantics; `createScroll()` must follow Telegram `ScrollView.LayoutParams` behavior instead of a project-private substitute
- [ ] 2.8 Audit existing unit tests under `src/test/`: keep contract-bearing tests for network/session/html parsing, remove low-value standalone substrate tests (including `AndroidUtilitiesTest.kt` and `NotificationCenterTest.kt` unless they become regression tests), and only remove legacy UI/navigation/theme tests after the rewrite path has replacement verification

## 3. Animation Primitives (core/util/)

- [ ] 3.1 Rework `CubicBezierInterpolator.kt` to the adopted Telegram `org.telegram.ui.Components.CubicBezierInterpolator.java` API surface with predefined instances: `DEFAULT`, `EASE_OUT`, `EASE_OUT_QUINT`, `EASE_IN`, `EASE_BOTH`, `EASE_OUT_BACK`
- [x] 3.2 Implement `AnimatedFloat.kt` as a parity-first Kotlin port of Telegram `org.telegram.ui.Components.AnimatedFloat.java`: configurable duration/interpolator, `set(value)`, `set(value, force)`, `force(value)`, frame-driven `getValue()`, and optional deprecated `get()` compatibility if retained
- [x] 3.3 Implement `AnimatedColor.kt` as a parity-first Kotlin port of Telegram `org.telegram.ui.Components.AnimatedColor.java`: per-channel interpolation with `set(color)`, `set(color, force)`, and `force(color)`
- [ ] 3.4 Do not keep a standalone spring helper just to complete substrate parity. If a real UI consumer later needs spring motion, introduce it in that consumer first (using AndroidX DynamicAnimation directly unless a Telegram-style public API is explicitly documented in artifacts)
- [ ] 3.5 _(no standalone unit tests for these substrate classes by default; verify via the first real consumer screen and manual smoke test)_

## 4. Event Bus (core/event/)

- [x] 4.1 Implement `NotificationCenter.kt` as a parity-first subset port of Telegram `org.telegram.messenger.NotificationCenter.java`: singleton, `addObserver()`, `removeObserver()`, `postNotificationName()`, `NotificationCenterDelegate` interface
- [x] 4.2 Define Discourse-specific event constants: `topicsDidLoad`, `topicDetailDidLoad`, `sessionDidChange`, `sessionDidExpire`, `csrfTokenDidRefresh`, `themeDidChange`, `categoryDataDidLoad`, `searchResultsDidLoad`, `profileDidLoad`, `cloudflareChallenge`, `appForegroundStateChanged`, `networkStateChanged`
- [x] 4.3 Implement observer lifecycle safety: remove all observations for a delegate, prevent delivery to removed observers
- [x] 4.4 Document and enforce `NotificationCenter` boundaries: only use it for truly global cross-cutting events, not for screen data state that should stay local to a page/controller
- [x] 4.5 Implement `AppLifecycleMonitor.kt`: observe process foreground/background and connectivity changes, expose a minimal app/network state for offline banner, session restore, and Cloudflare recovery flows
- [ ] 4.6 _(event bus and lifecycle monitor are integration primitives — verify via app startup/background/network smoke test; do not keep standalone `NotificationCenter` unit tests by default unless they are protecting a reproduced regression)_

## 5. Theme Engine (core/theme/)

- [ ] 5.1 Implement `NdTheme.kt`: color-key HashMap, `getColor(key)`, `setColor(key, value)`, `applyTheme(ThemeInfo)`, fallback color for unknown keys
- [ ] 5.2 Define all color keys: window backgrounds, action bar, topic list, post body, code block, blockquote, link, mention, divider, dialog, trust level badge
- [ ] 5.3 Create `LightTheme.kt` palette: populate all color keys with light mode values matching linux.do website tones
- [ ] 5.4 Create `DarkTheme.kt` palette: populate all color keys with dark mode values with sufficient contrast
- [ ] 5.5 Implement theme mode management: `Light`/`Dark`/`System` enum, `SharedPreferences` persistence, system dark mode listener
- [ ] 5.6 Implement runtime theme switching: crossfade animation (~300ms), post `themeDidChange` notification
- [ ] 5.7 Implement theme-aware helpers: `createPaint(key)`, `getThemedDrawable(key)`, selector/ripple drawable factories, rounded background helpers, and ActionBar header shadow drawable creation
- [ ] 5.8 Implement `NdUiConfig.kt`: minimal global UI flags replacing only the tiny V0 subset of Telegram `SharedConfig` (`animationsEnabled`, `drawActionBarShadow`, `sectionShadows`, optional haptics toggle)
- [ ] 5.9 _(theme is runtime visual — verify by running app, no unit test)_

## 6. Navigation Stack (ui/actionbar/)

- [x] 6.1 Define `INavigationLayout.kt` interface (simplified from Telegram): `presentFragment()`, `closeLastFragment()`, `getFragmentStack()`, `addFragmentToStack()`, `removeFragmentFromStack()`, `setDelegate()`
- [x] 6.2 Implement `BaseFragment.kt`: abstract class with `createView()`, `onFragmentCreate()`, `onFragmentDestroy()`, `onResume()`, `onPause()`, `onBackPressed()`, `parentLayout` reference, optional `actionBar` property, page-level coroutine/request scope canceled on destroy, plus transition/system-bar hooks: `isSwipeBackEnabled()`, `onSlideProgress()`, `onTransitionAnimationStart/End()`, `onBecomeFullyVisible()`, `getNavigationBarColor()`, `hasForceLightStatusBar()`
- [ ] 6.3 Implement `ActionBarLayout.kt` core: fragment stack management, push/pop with slide animation (fragment slides in from right, previous fragment shifts left), invoke fragment transition hooks, and keep status/navigation bar state in sync during transitions
- [ ] 6.4 Implement `ActionBarLayout.kt` swipe-back gesture: left-edge touch detection, finger-following drag, 35% threshold or velocity-based dismissal, cancel animation
- [ ] 6.5 Implement ActionBar integration: include ActionBar at top of fragment view, wire back button to `closeLastFragment()`
- [ ] 6.6 Implement minimal navigation state save/restore: persist fragment descriptors and args bundle so the custom stack can recover after configuration change and process recreation
- [x] 6.7 Update `MainActivity.kt` to host the migration-time root switch and mount `ActionBarLayout` when rewrite mode is enabled
- [ ] 6.8 Create a `HelloFragment` test fragment to verify navigation push/pop/swipe-back/state-restore works end-to-end
- [ ] 6.9 _(navigation is visual/gesture — verify by running HelloFragment, rotating the device, and relaunching after process death, no unit test)_

## 7. ActionBar Component (ui/actionbar/)

- [ ] 7.1 Implement `BackDrawable.kt` and the minimal animated action icon drawables needed by V0 ActionBar state changes; add `MenuDrawable` only if V0 search/menu transitions actually depend on it
- [ ] 7.2 Implement `ActionBar.kt` ViewGroup: back button area with themed selector background + animated back drawable, title/subtitle `SimpleTextView`, menu area, theme-aware background/title/icon colors
- [ ] 7.3 Implement `ActionBarMenu.kt` and `ActionBarMenuItem.kt`: add items with icon/text, click listeners, optional popup submenu
- [ ] 7.4 Implement ActionBar search mode: `openSearchField()` / `closeSearchField()`, animated transition between title and search input, keyboard show/hide
- [ ] 7.5 Implement `SimpleTextView.kt`: lightweight text view using `Canvas.drawText()` instead of `TextView` for ActionBar titles (better performance)
- [ ] 7.6 _(ActionBar / SimpleTextView are visual substrate — verify via HelloFragment and real screen integration, no standalone unit test)_

## 8. RecyclerListView (ui/components/)

- [ ] 8.1 Implement `RecyclerListView.kt` extending RecyclerView: list-level `setOnItemClickListener` / `setOnItemLongClickListener` with position mapping
- [ ] 8.2 Implement fast scroll support: thumb indicator on right edge, drag-to-scroll
- [ ] 8.3 Implement sectioned adapter base class: `getSectionCount()`, `getCountForSection()`, `isSection()`, optional sticky headers
- [ ] 8.4 Implement item enter animations: staggered fade-up on initial load
- [ ] 8.5 Implement theme-aware divider ItemDecoration using `NdTheme.getColor(key_divider)`
- [ ] 8.6 _(RecyclerListView behavior is interaction-heavy — verify with a mock list screen, no standalone unit test by default)_

## 9. Canvas Cells (ui/cells/)

- [ ] 9.1 Implement `BaseCell.kt`: abstract View with default full-width measurement and abstract `onDraw(canvas)`, theme change invalidation listener
- [ ] 9.2 Implement `TopicCell.kt`: Canvas-drawn topic list item with title (multi-line truncated), category badge (colored dot + name), author avatar, reply/view counts, time, optional thumbnail
- [ ] 9.3 Implement `UserCell.kt`: Canvas-drawn user list item with avatar, username, display name, trust level badge
- [ ] 9.4 Implement `DividerCell.kt`: thin horizontal line using theme divider color
- [ ] 9.5 Implement `LoadingCell.kt`: shimmer/skeleton loading placeholder for list items
- [ ] 9.6 Wire all cells to invalidate on `themeDidChange` notification
- [ ] 9.7 _(Canvas cells are visual substrate — verify by rendering mock topic/user rows, no standalone unit test)_

## 10. Bottom Sheet (ui/components/)

- [ ] 10.1 Implement `BottomSheet.kt`: slide-up dialog with dim overlay, drag handle, gesture-based dismissal (40% threshold)
- [ ] 10.2 Implement nested scroll handling: drag dismissal only when inner content at top
- [ ] 10.3 Theme-aware styling: `key_dialogBackground`, `key_dialogBackgroundGray`
- [ ] 10.4 Dismiss on overlay tap and on back button
- [ ] 10.5 _(BottomSheet is gesture/animation heavy — verify manually in app, no standalone unit test)_

## 11. Avatar Drawable (ui/components/)

- [ ] 11.1 Implement `AvatarDrawable.kt`: letter-based avatar with deterministic color from username, circular rendering
- [ ] 11.2 Support multiple sizes: small (28dp), medium (40dp), large (56dp), xlarge (72dp) with scaled text
- [ ] 11.3 Integrate remote avatar loading via ImageReceiver/Coil with letter avatar as placeholder
- [ ] 11.4 Optional trust level badge overlay rendering at bottom-right
- [ ] 11.5 _(Avatar rendering is visual — verify against mock users and fallback states, no standalone unit test)_

## 12. Image Pipeline (core/media/)

- [ ] 12.1 Implement `ImageReceiver.kt`: wraps Coil 3 ImageRequest, provides `setImageUrl(url)`, `draw(canvas)`, `setRoundRadius()`, placeholder rendering
- [ ] 12.2 Implement View lifecycle hooks: `onAttachedToWindow()` starts load, `onDetachedFromWindow()` cancels load
- [ ] 12.3 Implement explicit `release()` semantics so recycled cells and destroyed fragments fully drop Coil targets, drawables, and callbacks
- [ ] 12.4 Implement circular and rounded rectangle clipping during Canvas draw (BitmapShader + RoundRect Path)
- [ ] 12.5 Implement crossfade transition from placeholder to loaded image
- [ ] 12.6 _(ImageReceiver visual behavior is verified in host views/cells; verify attach/detach/release via manual recycling smoke test, no standalone unit test unless a pure logic seam emerges)_

## 13. Discourse Models (discourse/model/)

- [ ] 13.1 Define `TopicSummary` data class with kotlinx.serialization annotations for `/latest.json` deserialization
- [ ] 13.2 Define `TopicDetail`, `Post`, `PosterInfo` data classes for `/t/{id}.json` deserialization
- [ ] 13.3 Define `CategorySummary` data class with color parsing utility
- [ ] 13.4 Define `SearchResult`, `UserSearchHit` data classes for `/search.json`
- [ ] 13.5 Define `UserProfile` data class for `/u/{username}.json`
- [ ] 13.6 Define `AppError` sealed class: `Network`, `Http`, `Parse`, `SessionExpired`
- [ ] 13.7 Write unit tests for JSON deserialization using fixtures from `fixtures/discourse/` (this is valuable — validates API contract)

## 14. Discourse Client (discourse/api/)

- [ ] 14.1 Implement `DiscourseApi.kt` Retrofit interface: all endpoints from `contracts/discourse/endpoints.md`
- [ ] 14.2 Implement `DiscourseClient.kt`: shared OkHttpClient singleton, Retrofit instance, interceptor chain (UserAgent, CSRF, Cloudflare)
- [ ] 14.3 Implement `UserAgentInterceptor.kt`: inject custom User-Agent header
- [ ] 14.4 Implement `CsrfInterceptor.kt`: inject X-CSRF-Token from CsrfTokenProvider on mutating requests
- [ ] 14.5 Implement error normalization: map HTTP 401→SessionExpired, other 4xx/5xx→Http, IOException→Network, SerializationException→Parse
- [ ] 14.6 Write tests for error normalization (401→SessionExpired, network errors) using MockWebServer (this is valuable — validates error handling contract)

## 15. Cookie Session (discourse/session/)

- [ ] 15.1 Implement `CookieSessionStore.kt`: `CookieJar` implementation with SharedPreferences persistence for `_t`, `_forum_session`, `cf_clearance`
- [ ] 15.2 Implement `syncFromWebView()`: read cookies from Android CookieManager, copy to OkHttp jar
- [ ] 15.3 Implement `isSessionValid()`, `getSessionUser()`, `clearSession()`
- [ ] 15.4 Post `sessionDidExpire` notification when `_t` cookie is missing or expired
- [ ] 15.5 Write tests for session validity and cookie sync (this is valuable — session bugs are hard to debug)
- [ ] 15.6 Implement `AuthSessionManager.kt`: cookie-backed auth/session state machine for restore, login completion from WebView, logout, session expiry, and recovery-required flows; prefer `*Manager` naming over `*Coordinator`

## 16. CSRF Token Provider (discourse/session/)

- [ ] 16.1 Implement `CsrfTokenProvider.kt`: primary source `/session/csrf`, fallback from BootstrapParser
- [ ] 16.2 Implement token caching (30-minute expiry), automatic refresh on 403
- [ ] 16.3 Implement thread-safe token access with coroutine Mutex (single in-flight refresh)
- [ ] 16.4 Write tests for dual-source fallback logic (this is valuable — CSRF failures break all writes)

## 17. Cloudflare Guard (discourse/session/)

- [ ] 17.1 Implement `CloudflareInterceptor.kt`: detect CF challenge responses (403 + cf-mitigated header or challenge HTML)
- [ ] 17.2 Post `cloudflareChallenge` notification with URL when challenge detected
- [ ] 17.3 Implement challenge throttling: max once per 5 minutes
- [ ] 17.4 Differentiate CF 403 from normal permission 403

## 18. Bootstrap Parser (discourse/session/)

- [ ] 18.1 Implement `BootstrapParser.kt` with Jsoup: parse `<meta name="csrf-token">`, `<meta name="current-user-username">`, user avatar from meta tags
- [ ] 18.2 Parse preloaded store JSON for category data extraction (id, name, slug, color, text_color, parent_category_id)
- [ ] 18.3 Return null for missing elements (no crashes on HTML structure changes)
- [ ] 18.4 Write tests using `fixtures/discourse/home-bootstrap.html` (this is valuable — HTML structure may change)

## 19. Cooked HTML Renderer (ui/components/)

- [ ] 19.1 Implement `CookedHtmlRenderer.kt`: Jsoup HTML parsing → `List<RenderBlock>` conversion
- [ ] 19.2 Implement `RenderBlock`, `TextDocument`, and `InlineEntity` models: block-level nodes include `Paragraph`, `Heading`, `HorizontalRule`, `CodeBlock`, `Blockquote`, `DiscourseQuote`, `Callout`, `Image`, `LightboxImage`, `ImageGrid`, `List`, `Table`, `Onebox`, `Details`, `Spoiler`, `Poll`, `RawHtml`; inline entities include text-style spans plus `Link`, `Mention`, `Hashtag`, `Emoji`, `InlineCode`
- [ ] 19.3 Implement basic text block conversion: `<p>`, `<h1-h6>`, `<hr>` → `Paragraph` / `Heading` / `HorizontalRule`
- [ ] 19.4 Implement code block conversion and rendering: `<pre><code>` with syntax language detection, monospace font, and themed background
- [ ] 19.5 Implement quote-family blocks: plain `<blockquote>`, markdown-style callouts (for example `[!warning]`), and Discourse quote asides `<aside class="quote">` with preserved `data-topic` / `data-post` / author metadata
- [ ] 19.6 Implement media blocks: plain `<img>`, lightbox-wrapped images `div.lightbox-wrapper > a.lightbox`, and `div.d-image-grid` image grids, preserving preview/original URLs and meta text when present
- [ ] 19.7 Implement structural blocks: `<ul>/<ol>`, `<table>` (including `div.md-table` wrappers), and Discourse oneboxes (`<aside class="onebox">`, `a.onebox`, `a.inline-onebox`)
- [ ] 19.8 Implement expandable/special blocks: `<details>/<summary>`, `div.spoiler`, and read-only `div.poll`
- [ ] 19.9 Implement inline parsing into `TextDocument.entities`: bold/italic/strike, normal links, mentions `<a class="mention">`, hashtags `<a class="hashtag-cooked">`, emoji `<img class="emoji">`, and inline code `<code>`
- [ ] 19.10 Implement cooked HTML normalization rules for decorative wrappers and ignorable nodes (for example `div.lightbox-wrapper`, `div.md-table`, empty `blockquote`, decorative `svg.d-icon`) before falling back to `RawHtml`
- [ ] 19.11 Implement `RawHtml` fallback: only genuinely unrecognized plugin/custom HTML uses the small WebView fallback
- [ ] 19.12 Theme-aware rendering: all rendered blocks and inline entities use `NdTheme` color keys for text/background/border/link/callout styling
- [ ] 19.13 Preserve baseline parity with the current parser: the rewrite must not regress below existing support already present in `PostBodyBlocks.kt` / `PostBodyBlockParser.kt` for `DetailsBlock`, `CalloutBlock`, `DiscourseQuoteBlock`, `LightboxBlock`, and `ImageGridBlock`
- [ ] 19.14 Write tests for HTML → `RenderBlock` / `InlineEntity` conversion using `fixtures/discourse/cooked-*.html` samples, covering quote metadata, lightbox metadata, poll parsing, spoiler/details parsing, inline entities, and old-parser parity cases (this is valuable — core rendering logic)

## 20. Topic Stream Pager (discourse/api/)

- [ ] 20.1 Implement `TopicStreamPager.kt`: initialize with topicId and streamPostIds, chunk size 20
- [ ] 20.2 Implement `loadNextPage()`: fetch next chunk via `GET /t/{id}/posts.json?post_ids[]=...`, append to loaded posts
- [ ] 20.3 Implement `jumpToPost(postNumber)`: calculate chunk, fetch if not cached, return position
- [ ] 20.4 Implement in-memory chunk caching: no re-fetch for previously loaded chunks
- [ ] 20.5 Expose `hasMore: Boolean` and `loadedPosts: List<Post>` state
- [ ] 20.6 Write tests for pagination chunk calculation and jump-to-post logic (this is valuable — pagination bugs cause missing posts)

## 21. Offline Cache (discourse/storage/)

- [ ] 21.1 Define Room database schema: `NdDatabase` with `TopicSummaryEntity`, `TopicDetailEntity`, `CategoryEntity` tables
- [ ] 21.2 Implement DAO interfaces: `TopicDao`, `CategoryDao` with insert, query, and delete operations
- [ ] 21.3 Implement cache invalidation: 5-min topic lists, 10-min topic details, 1-hour categories
- [ ] 21.4 Implement stale-while-revalidate pattern: return cached data immediately, update when fresh data arrives
- [ ] 21.5 Implement cache size view and clear-all functionality
- [ ] 21.6 Configure Coil 3 disk cache size (250MB default)

## 22. App Startup Wiring

- [x] 22.1 Update `NativeDoApplication.kt` to call `NdApp.init(context)` on startup
- [x] 22.2 Implement `NdApp.initForTesting()` / runtime replacement hooks with mock injection support
- [ ] 22.3 Implement `LaunchBootstrap.kt`: cold-start entry that asks `AuthSessionManager` to restore session and decides initial destination (`TopicsFragment` or `LoginFragment`)
- [ ] 22.4 Implement session restore logic: check cookie validity → load Topics or show Login
- [ ] 22.5 Wire the startup decision into `MainActivity`'s migration-time root switch so rewrite mode and legacy mode both keep a single launcher host

## 23. Topics Screen (feature/)

- [ ] 23.1 Implement `TopicsFragment.kt`: three-tab layout (Latest/New/Top) using `ViewPagerFixed` or tab-switching logic
- [ ] 23.2 Implement topic list adapter: bind `TopicSummary` to `TopicCell` in RecyclerListView
- [ ] 23.3 Implement pull-to-refresh: refresh indicator, re-fetch page 0
- [ ] 23.4 Implement infinite scroll pagination: detect scroll-near-bottom, load next page via `more_topics_url`
- [ ] 23.5 Implement pinned topic handling: pin icon, sorted to top in Latest tab only
- [ ] 23.6 Implement topic tap → `TopicDetailFragment` navigation
- [ ] 23.7 Implement category badge display: colored dot + category name from CategorySummary cache
- [ ] 23.8 Implement offline fallback: display cached topics with offline banner
- [ ] 23.9 Wire up to NotificationCenter: listen for `topicsDidLoad`, `themeDidChange`, `sessionDidExpire`

## 24. Topic Detail Screen (feature/)

- [ ] 24.1 Implement `TopicDetailFragment.kt`: ActionBar with topic title, topic header cell (title, category, tags, stats)
- [ ] 24.2 Implement post list adapter: bind `Post` to post cells using CookedHtmlRenderer for body
- [ ] 24.3 Implement post author info display: avatar, username, display name, trust level badge, creation time
- [ ] 24.4 Implement stream-based pagination: initial chunk from topic detail response, subsequent chunks via TopicStreamPager on scroll
- [ ] 24.5 Implement reply-to reference: "replied to @username" tap → scroll to target post (load chunk if needed)
- [ ] 24.6 Implement "Open in Browser" menu item → BrowserFragment
- [ ] 24.7 Wire up to NotificationCenter: listen for `topicDetailDidLoad`, `themeDidChange`

## 25. Browser Screen (feature/)

- [ ] 25.1 Implement shared WebView infrastructure for linux.do scenes: common settings/factory, cookie sync hooks, attach/detach/release rules, and base `WebViewClient` helpers reused by `BrowserFragment`, `LoginFragment`, and `RawHtml` fallback without merging their scene responsibilities
- [ ] 25.2 Implement `LinkManager.kt`: minimal URL classifier for topic/user/app-browser/external-app/system-browser targets; it only resolves URLs and does not own fragment navigation
- [ ] 25.3 Implement `BrowserFragment.kt`: WebView container with custom ActionBar (title + URL display)
- [ ] 25.4 Implement navigation controls: back/forward buttons (enabled/disabled by canGoBack/canGoForward), refresh, share
- [ ] 25.5 Implement progress bar: thin bar at ActionBar bottom tracking WebView load progress 0-100%
- [ ] 25.6 Implement cookie sharing: sync CookieSessionStore with WebView CookieManager
- [ ] 25.7 Implement internal link interception through `LinkManager`: detect linux.do topic/user URLs and route them natively; keep the rest in WebView or external browser as classified
- [ ] 25.8 Swipe-back support inherits from ActionBarLayout (no additional work needed)

## 26. Profile Screen (feature/)

- [ ] 26.1 Implement `ProfileFragment.kt`: ActionBar with username, large avatar header (72dp), collapsible on scroll
- [ ] 26.2 Display user info: username, display name, title, bio (via CookedHtmlRenderer), trust level, join date, last seen
- [ ] 26.3 Display statistics grid: topics, posts, likes given, likes received, with formatted numbers
- [ ] 26.4 Implement avatar tap → full-screen avatar view (stretch goal)
- [ ] 26.5 Implement logout action in own profile: clear session → navigate to LoginFragment

## 27. Search Screen (feature/)

- [ ] 27.1 Implement `SearchFragment.kt`: ActionBar in search mode, auto-focus and keyboard show
- [ ] 27.2 Implement debounced search (300ms) → `/search.json` API call
- [ ] 27.3 Implement combined results list: "Topics" section with TopicCells, "Users" section with UserCells
- [ ] 27.4 Implement empty state for no results
- [ ] 27.5 Topic tap → TopicDetailFragment, user tap → ProfileFragment

## 28. Login Screen (feature/)

- [ ] 28.1 Implement `LoginFragment.kt`: WebView loading `https://linux.do/login`
- [ ] 28.2 Keep `LoginFragment` separate from `BrowserFragment`: it reuses shared WebView infrastructure but remains an auth-specific scene rather than “Browser opening /login”
- [ ] 28.3 Implement login detection: monitor `_t` cookie via WebViewClient/CookieManager polling
- [ ] 28.4 Implement post-login flow: `syncFromWebView()` → `AuthSessionManager` verification / restore → navigate to TopicsFragment
- [ ] 28.5 Implement session verification fallback: if `/session/current.json` fails, use BootstrapParser
- [ ] 28.6 Implement auto-login on app start: check `isSessionValid()` → skip login or show LoginFragment

## 29. NdComposeTheme Bridge

- [ ] 29.1 Implement `NdComposeTheme.kt`: Compose theme that bridges NdTheme color keys to Compose `ColorScheme` values
- [ ] 29.2 Listen for `themeDidChange` notification and trigger recomposition

## 30. Integration & Cleanup

- [ ] 30.1 Wire bottom tab bar: Topics, Search, Profile tabs in root ActionBarLayout or custom tab container
- [ ] 30.2 Delete old navigation infrastructure: `navigation/` package, `NativeDoApp.kt`, `NativePageStackHost.kt`, `RootTabsHost.kt`, `AppRoot.kt` and all Compose Navigation graph code
- [ ] 30.3 Delete old theme files: `ui/theme/` package, `NativeDoGlassSurface.kt`, `NativeDoBackground.kt` and all NativeDoTheme/NativeDoThemeExtras references
- [ ] 30.4 Delete old UI components: `core/ui/HtmlPostBodyView.kt`, `core/ui/UserAvatar.kt`, `NativeDoPullToRefreshIndicator.kt`, `NativeDoBrowserScaffold.kt`, `NativeDoReaderScaffold.kt`, `NativeDoChromeScaffold.kt`, `NativeDoRootChrome.kt`, `BrowserHost.kt`, `BrowserTabsShell.kt`, `BrowserTabsTransition.kt`
- [ ] 30.5 Delete old feature screens: entire `feature/auth/`, `feature/browser/`, `feature/home/`, `feature/profile/`, `feature/search/`, `feature/topic/` packages
- [ ] 30.6 Delete old DI: `core/di/AppContainer.kt` and related references
- [ ] 30.7 Delete old model/network layers: `core/model/`, `core/network/`, `core/session/`, `core/storage/` — logic already migrated to new `discourse/` package
- [ ] 30.8 Verify no remaining imports from deleted packages: `grep -r "import.*nativedo.core\.\|import.*nativedo.feature\.\|import.*nativedo.navigation\." --include="*.kt"`
- [ ] 30.9 Full build verification: `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`
- [ ] 30.10 Run all unit tests: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- [ ] 30.11 Manual smoke test: login → topics → detail → browser → profile → search → logout → login

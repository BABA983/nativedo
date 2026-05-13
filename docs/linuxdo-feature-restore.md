# Linux.do Feature Restore

## Source

- Original branch: `/Users/baba/Documents/workspace_baba/linuxdo-native`
- Current branch: `/Users/baba/Documents/workspace_baba/linuxdo-native-refactor`

## Current Phase

In scope:

- App-level `AppContainer` for network, cookies, and image loading.
- WebView-backed login, cookie sync, session restore, and local logout.
- Discourse `latest/new/top` topic feeds.
- Read-only topic detail with initial posts and paged post loading.
- Basic cooked HTML rendering for text, links, quotes, code, lists, and images.
- Links open through the current Telegram-derived `BrowserSheet`.

Out of scope:

- Search and profile flows.
- Reply, edit, reactions, bookmarks, and post actions.
- Server-side logout.
- SVG sprite capture/cache for Discourse reaction and icon rendering.
- Full media viewer transition parity for post images.
- Full table/poll/details rendering parity for cooked Discourse blocks.

## Data Flow

`TopicListFragment` and `TopicDetailFragment` own the page `ViewModel`.

`Fragment -> ViewModel -> Repository -> DiscourseApiClient -> DiscourseApiService`

Compose screens render state and emit callbacks only. The root navigation remains the current Telegram-derived `ActionBarLayout` / `MainTabsActivity` host.

## Verification

- `./apps/android/gradlew -p apps/android :app:compileDebugKotlin --quiet`
- `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`
- Manual unauthenticated validation against `https://linux.do/` when the local network can reach it.

# Reading, session and state ownership

## One session, not synchronized copies

`CookieManager` is the single jar for login, the in-app browser and native requests.
A second persisted jar cannot safely mirror a rotating `_t`. Late responses are
fenced against newer tokens; challenge evidence preserves the session rather than
pretending it is logout. The [auth contract](../contracts/discourse/auth-flow.md)
owns the exact rules, including CSRF and explicit recovery.

The [composition root](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt)
shares this boundary with repositories and MessageBus. The device authorization
experiment produces a separate User API Key; it is **not** the production cookie
session and does not grant a general read/write mode. See its
[contract](../contracts/discourse/device-authorization.md).

## Reading progress is not the viewport

There are three distinct facts:

- **Server progress:** the highest floor accepted through `/topics/timings` and
  accumulated dwell; this determines server unread state.
- **Local read floor:** monotonic progress, retained even if a report fails.
- **Exit viewport:** floor plus pixel offset for restoring this device's visual
  position; it may move backward without marking anything unread.

In the active build, [TopicFragment](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/TopicFragment.java)
opens an explicit floor when supplied; otherwise its reporter resumes at the
greater of server progress and the local read floor. Read state is scoped by site,
account and topic. The viewport store exists but is not wired into this screen:
returning to a previous pixel offset is **not** current active behavior.

The legacy `:app` page restores the exit viewport before falling back to read
progress; explicit floor links and a user drag take precedence. See the
[scoped viewport contract](../contracts/discourse/models.md#local-topic-viewport).

### Same-screen topic search

The active build hosts [topic search](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/search/TopicSearch.kt)
inside the existing topic with Telegram's message/list search interaction. It reuses
the upstream search field, message-result cells, search side controls and animated
scrolling; Discourse remains the sole search/window
transport. Calendar and author controls are out of scope rather than dead buttons.
Both modes share query, selected floor and paginated results. Counts are explicitly
partial while the server advertises another page; a failed page retries that page.

Search temporarily suspends ordinary stream paging and read-position reporting.
Closing restores the saved window and pixel anchor without losing the composer
draft. Query changes, closing and destruction fence late search/window responses.
Rich bodies use their actual Telegram text layouts and rounded marked-range paths,
preserving existing HTML marks; plain messages use upstream text highlighting.
The [search flow](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/search/SearchTest.kt)
covers Chinese rich paragraphs, quotes/code, both modes, previous/next windows,
retry, theme change and restoration with the production-wired host harness. Device/GPU
scroll timing still requires Android evidence; host captures are not device passes.

[TopicReadReporter](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/feature/topic/data/TopicReadReporter.kt)
reports dwell deltas, not lifetime totals. Background time and an idle open screen
are not reading. Transient failures back off; a challenge blocks automatic reports
until explicitly confirmed recovery. Resume, scroll and simply closing a WebView
do not clear that block.

A recovery racing an in-flight report must not be lost or produce duplicate
reports; a later challenge must still block. Scheduling details belong in the
reporter, and race-verification guidance in [testing](testing.md#retain-contracts-not-implementation-mirrors).

## Offline content is not write authority

[DiscourseReadCache](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/core/storage/DiscourseReadCache.kt)
stores bounded wire payloads, decoded through the same mapping as live reads.
Persistent keys identify site/account and exact route/window; session generation
fences in-flight work rather than fragmenting persistent keys across restarts.
Logout clears the store and stale responses cannot refill it.

Only transport failure may select an exact cached answer. A denial, challenge,
malformed response or session change is not an outage cache hit. Cached topics
stay visibly offline/read-only until a complete live replacement, not merely a
retry start or a live chunk appended to cached content. Scope and limits are in
the [offline-read contract](../contracts/discourse/endpoints.md#native-offline-reads).

## Accepted writes and live updates

The site owns permissions and authoritative content/counts. A successful mutation
followed by failed readback remains accepted: offer reload, do not replay a delete
or chat send. Notification counts come from bootstrap/MessageBus, not guessed local
decrements; slow readback must not override newer bus state or another account.

MessageBus is a foreground session-owned poller, not OS background push. Use the
site's bootstrap polling base, channel-local positions and cross-origin credentials
as specified in the [endpoint contract](../contracts/discourse/endpoints.md).
Public Discourse Chat is distinct from private-message topics and Telegram channels;
plugin availability, membership and write permission remain server decisions.

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

### User-shared session diagnostics

The active build offers Settings → 诊断 for session recording, export and clear,
including while signed out or expired. Recording defaults off and enabling it asks
for consent. The choice persists across restarts; recording retains at most two
256 KiB event files in private, non-backed-up storage. Export includes only those
files and build/Android-version metadata in one cache ZIP via FileProvider and the
system share chooser, never an automatic upload. Disabling stops new recording but
keeps existing files; clearing deletes both event files and that export. If recording
remains enabled, subsequent events can create new logs. Already shared copies cannot
be revoked by clearing local files.

The [recorder](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/core/session/SessionDiagnostics.kt)
uses the existing fixed session events and response classifications, not arbitrary
logcat, Telegram's entire logs directory or exception messages. Cookie presence and
rotation are booleans/counters; cookie values, full URLs, account identities, request
and response bodies, databases and drafts are excluded from the archive. File writes
run on a single background queue; storage failures do not change request/session
results. Queued events from an earlier recording interval cannot reappear after
disable or clear. These logs provide classification evidence, not proof that every
login failure has the same cause. The host settings flow exercises a classified
response in the shared ZIP and access after expiry; isolated checks cover persistence,
bounded retention and pending-write isolation. Device sharing remains a separate
acceptance step.

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

The window's own state - where the loaded slice starts and ends in the stream, the pages on
their way, the edges that failed, and the reading mode the rows were read in - lives in
[TopicReadingWindow](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/TopicReadingWindow.kt),
a Kotlin class with no View. The screen asks it before every read and renders what it reports:
a page from a window that was replaced cannot move the new window's cursor, and a failed read
puts the mode the rows on screen were actually read in back. Its races and recovery rules are
covered by [its test](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/topic/TopicReadingWindowTest.kt);
the screen's own flows stay end to end in the paging and filter tests below.

The topic's floating page-down arrow appears while real stream posts remain below
the viewport, even when they are already read. Its badge counts only real posts
after the reporter's monotonic read mark, not all posts below the screen; scrolling
back cannot make that number grow. Deleted floors are not counted. If the read
floor is outside the loaded window and the server supplied no matching
`last_read_post_id`, the arrow omits the number rather than inventing a stream
position. Its tap still goes to the first unread floor, or the newest post when
everything is read. The [page-down flow](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/topic/TopicPageDownTest.kt)
checks forward/backward reading, the fully-read arrow, deleted floors and jump
loading/retry. Discussion-page counts remain the root's remaining replies, not
the ordinary topic's unread badge.

The way back up floats one control above the page-down button. When nothing
remains below the viewport the down arrow plays its own hide, and the up arrow
takes the resting slot it frees - the stack collapses to one button instead of
resting over an empty slot, and the pair rides back apart when the down arrow
returns. The up arrow's resting offset is a function of the down arrow's
visibility factor, so a frame mid-flight never leaves a gap. Its tap reads floor
1 when the loaded window starts mid-stream - row 0 is not floor 1 there - and
only scrolls to the top when the head is already loaded.

### Only the original poster

A long topic can be read as its original poster's floors: the overflow's
**只看楼主** row asks the site for its own author filter (`username_filters`, see
the [endpoint contract](../contracts/discourse/endpoints.md)) and re-reads the
window around the floor at the top of the viewport. The answer's stream is post 1
plus that author's posts, so forward/backward paging and the page-down count walk
the filtered list - a window in the middle of a topic may carry none of the
poster's floors, which is why the filter is not applied to the rows already held.
The capsule's second line names the filter while it is on, and the same row
becomes **显示全部**.

While the filter is on, no floor is credited as read: `topics/timings` may still
carry the reader's dwell, but no `timings[...]` field, and the reporter's mark
does not move. Reporting the poster's floor 40 would advance the site's cursor
past every floor in between, which the reader never saw. The page-down badge
counts the poster's posts below the viewport rather than unread posts, and the
end-of-topic read-on pull is withheld because the topic's real end was not
reached. A filtered window that does not arrive leaves the rows on screen
unchanged, so the row and the capsule go back to the mode those rows were read
in - not to the mode before the last tap, which a second tap can have replaced.
The [filter flow](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/topic/TopicOnlyOpTest.kt)
drives the overflow row, the filtered request, filtered paging, the suspended
read report and the way back.

The alternative - hiding non-poster rows from the loaded window - was rejected:
it cannot show a window that holds none of the poster's floors, would page the
whole topic, and could not count what is left. A third floating button was
rejected too: the two glass circles are already one stack, and this is a reading
mode that the overflow and the capsule can carry without a control that competes
with navigation.

The legacy `:app` page restores the exit viewport before falling back to read
progress; explicit floor links and a user drag take precedence. See the
[scoped viewport contract](../contracts/discourse/models.md#local-topic-viewport).

### A page that did not arrive

A page that fails while rows are already on screen keeps them and says in passing
what the next step is. The hint distinguishes the two causes: a page the session is
what it needs (`401`, or a challenge) offers the login/verification WebView and asks
for that same page once the reader is back, while an outage offers a retry from the
same cursor. The topic list's next-page footer follows the same split - its tap opens
the recovery WebView instead of retrying a request the site will refuse again. A chat
channel's older window that needs the session ends in the screen's own session state,
whose tap opens the login page; an outage there is a retry hint and leaves the rows
standing. The chat hint's retry repeats the read that failed with its own target,
direction and landing: an older page asks for the same older cursor, a refresh
refreshes, and a send's own readback still asks around the accepted id, so the message
just sent can appear. The [topic paging flow](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/topic/TopicPagingTest.kt)
and the [channel flow](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/chat/ChatChannelTest.kt)
drive both.

### Reading on from the end of a topic

When the reader has the whole real stream loaded and the list is at its end, one
more upward drag reveals the site's own suggestion for what to read next and
opens it on release. The card is an adapted port of Telegram's channel
pull ([ChatPullingDownDrawable][pull]): the 110dp threshold, the 0.65→0.45→0.05
damping, the arrow→check reveal and the release/bounce animation are upstream's;
the Telegram account, dialog/topic lookups, folder/forum branches and unread
counter are not, and the candidate is a Discourse topic instead of a chat.
Under the threshold, and on `ACTION_CANCEL`, the pull only rebounds.

The candidate is the first valid entry of the topic read's own ranked
`suggested_topics` that is not the current topic ([endpoint][t-endpoint]); the
client keeps the site's order, never guesses an id and never claims an unread.
A head window of a topic that has another page carries no `suggested_topics`
(Discourse closes that field while a next page follows), so for a long topic the
candidate comes from the slice that reaches the real end of the stream: that one
incremental chunk is fetched with `include_suggested=true`, and its own ranked
list becomes the candidate. Binding it does not reset the floors, the header or
the scroll, and a slice whose window has moved on is dropped rather than arming
the new window's pull.
No candidate means no pull. The gesture is also withheld from the states that
are not an ordinary read: the in-topic search, a discussion page, offline-cached
content, a page or window still loading or a failed edge, a text selection, and
a composer the reader is writing in (a draft or an attachment) or with its
keyboard up. Ordinary scrolling, flinging and the leftward reply drag are
untouched.

The switch uses the existing navigation and replaces the topic screen rather than
stacking one per topic, so continuous reading stays one screen deep and Back
returns to the list the reader came from. The gesture does not mark unseen floors
read; ordinary viewport/dwell reporting still runs when leaving and entering a
topic. The [read-on flow][test] covers the real threshold, the
under-threshold and cancel rebounds, the reverse drag, a page/window still
coming or failed, the search and a draft, no candidate, Back, and a doubled
release. It also covers a long topic whose head window carries no suggestion and
whose final slice does: the pull stays away until that slice lands, then an
over-threshold release opens the site's suggestion and Back returns to the list;
a final slice without a suggestion, and a stale last slice whose window the
reader has already left, both leave the pull away. The card is drawn by the list
itself, not by the screen's frame: a
`ViewGroup`'s own `dispatchDraw` output is recorded in its display list, which a
child's invalidation does not rebuild, so a card drawn in the frame would never
repaint as the pull grows.

[pull]: ../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/NextTopicPullDrawable.java
[t-endpoint]: ../contracts/discourse/endpoints.md
[test]: ../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/topic/NextTopicPullTest.kt


### The topic screen's date rows

The topic screen draws a day as a row of its own above that day's first floor, and
floats the day of the row at the top of the viewport while that day's row is above
the list's top edge. Both are upstream's chat interaction and upstream's own cell
([ChatActivity][chat-date]): one `ChatActionCell` draws the row and the floating
pill, so the plate, the text colour and the floating pill's reduced alpha come from
upstream rather than from a second drawing. The row's date is the day's midnight,
not the floor's hour, so the row and the pill always name the same day; a window
whose slice is prepended keeps one row at the seam when the slice's last date row
is the day the window already opens on. The seam reads that day from the slice's
last date row, not from its last row - the last row is a floor's bubble - and
keeps both rows when the days differ.

The pill is not a scroll-only overlay: it is up exactly while the day's row is
scrolled away, and the row and the pill hand over through the pill's own slide
(`floatingDateViewOffset`) as that row leaves or returns. That is why the pill can
stay standing after a scroll settles, and why it leaves when the row comes back
rather than on a timer of its own. A date row draws no floor of its own: it holds
the index of the floor it introduces, the way the unread divider does, so every
position-to-floor mapping stays aligned. A jump target, the last visible floor and the
floors a read report credits are still read from bubble rows, so the day pill above a
floor is never mistaken for the floor itself.

Non-goals: no tap-to-jump, because the site has no date-jump endpoint, so the pill
stays a label rather than a control. The channel keeps its overlay pill (below): a
channel has no per-day rows to hand over to.

### The chat channel's floating date

The active chat channel
([ChatChannelFragment](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/chat/ChatChannelFragment.java))
floats the date of the message at the top of the viewport, the way a chat does
([ChatActivity][chat-date]): the reader always knows which day they are reading
without a date separator taking a list row. The capsule is upstream's own
`ChatActionCell` floating date - the same cell and opaque night theme delegate the
topic list's new-topics pill already uses - added as a child of the screen's frame
below the header and above the rows, so it consumes no row, cannot overlap the
composer, and rides nothing but the header's own edge. The date is the chat date
the top visible message itself carries (`messageOwner.date`), not a scroll
position: status, note and header rows are skipped and the first visible adapter
position is read fresh on every scroll, so a history prepend or a live append
cannot leave a stale index behind. `setCustomDate` formats the same chat date a
chat's date separator shows, so crossing midnight changes the label and crossing a
year boundary adds the year. The capsule fades in (one 150 ms alpha set) while the
reader scrolls and fades out once the scroll settles; a screen that is loading,
empty, failed or no longer a session clears it at once instead of leaving the last
date standing.

Non-goals: no in-list date separators and no change to `messageRowOffset`/index
arithmetic - the capsule is an overlay, not a row. No tap-to-jump, because the
channel has no date-jump endpoint, so the capsule is a label rather than a control.
It does not touch read progress, the history viewport, pending arrivals or reply
targeting.

Alternative considered for the channel: hand-draw a lookalike capsule, or add date
separator rows. Both were rejected there - the first is a second drawing of
something upstream already draws, and the second changes the channel's row model for
a label the reader only sees while moving; the topic screen, whose rows the site
itself groups by day, takes the row instead (above).

Verification: the [chat channel flow][chat-test] drives the shipped screen with
messages on different days and real scrolling, and checks that the capsule names
the top message's own date, changes across a day boundary in both directions, keeps
the year before a year boundary, fades once the scroll settles and is cleared when
the session is lost. The host harness renders the visible, changed and faded states;
device/GPU timing of the fade is not claimed.

[chat-date]: ../apps/android/telegram/TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
[chat-test]: ../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/chat/ChatChannelTest.kt


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

## Message center navigation

The second main tab is **消息**, with **通知 / 私信 / 聊天** capsule tabs.
The [center](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/messagecenter/MessageCenterFragment.java)
hosts the existing screens with Telegram's `ViewPagerActivity`, keeping their lifecycle,
session checks and requests rather than building another combined feed. Its
`ScrollSlidingTextTabStrip` uses the profile's capsule and glass configuration.
Horizontal drags switch message categories, while the bottom main tabs remain
reachable by tap. This avoids two pagers competing for the same gesture.

One title bar owns the current page's actions: mark all notifications read,
choose the private-message inbox/sent/unread box, or refresh public channels.
Private-message boxes retain independent lists and scroll positions but do not add
a second row of tabs. Settings' private-message shortcut opens the same center on
私信. Pushed screens cover the main tab bar; hosted lists and the compose button
reserve its space only when the center is itself a main tab.

The capsule's closed inset is part of the strip before it is first measured (the
order upstream builds the profile's capsule with), so its row of tabs, its clipping
path and the tap highlight share that inset and a row whose tabs already fit has no
scroll range to move under them. A page over the main-tab strip reserves that
strip's band under its last row; where no strip hosts it, the compose button's own
clearance is the reservation. The two are alternatives, not a sum: the page's band
and the button's clearance both end near the same place, and adding them leaves an
empty window-coloured band between the last row and the tabs.

The capsule is a fixed surface the pages scroll behind, not a strip they start
below. The center's pager begins at the action bar's bottom, so a page's content
reaches up into the capsule's band, and the band itself is transparent - only the
glass capsule is a surface, and the blur behind it is the page's own content (the
same arrangement as the profile's capsule). Each hosted page takes the strip's
height through `MessageCenterChild.setMessageCenterTopInset` (handed over in
upstream's page factory, and again once the strip is measured); its list adds that
inset to its top padding and keeps `setClipToPadding(false)`. The first row
therefore starts below the capsule, while later rows scroll through the band under
it. The inset is the page's own: dropping it leaves the first row hidden behind the
capsule, and keeping the old pager margin instead puts the list's clipping edge
below the band, where no row can pass the capsule at all. The bottom reservation is
unaffected.

Private messages remain private topics, with their subject and the site's excerpt
when available. Without an excerpt, the list describes the known author rather
than showing a reply count as a message preview. It does not fetch each topic to
manufacture previews. Public Chat remains a separate protocol and opens the existing
native channel screen; this navigation change does not change chat transport.

The bottom badge still means server-owned unread **notifications**, not a sum of
notifications, private topics and channel messages. Combining those counts would
double-count related events and give busy channels disproportionate weight.
The host flow checks page switches, menu actions and push/back navigation, plus
the scroll-under geometry of all three pages: the pager beginning at the bar's
bottom, a transparent band, each page's list starting at the band's edge with the
capsule's height in its top padding, the first row below the capsule, and rows
drawn through the band after a scroll. Real device blur, animation pacing and
gesture arbitration still require device acceptance.

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

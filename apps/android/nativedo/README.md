# The full-upstream spike: `apps/android/nativedo`

This is the spike that asks whether a linux.do client can be built on a *complete*
upstream Telegram (12.10.5, `dc780e81ed1261c369c27870e8e0999a1eb0b600`) rather than on the
shrunk skeleton in `apps/android/TMessagesProj`.

## Layout

```
apps/android/telegram/            ← the upstream subtree, exactly as upstream ships it
  TMessagesProj/                  ← upstream's library module (2,147 java files)
  TMessagesProj_App/              ← upstream's app module (not ours)
  settings.gradle                 ← includes ':nativedo' from outside the subtree
apps/android/nativedo/            ← our module: the only place our code lives
  src/main/java/io/github/baba983/nativedo/
    app/       shell (ActivityBarLayout + tab strip + session gate)
    auth/      WebView login, cookie/session handling (moved over unchanged)
    core/      Discourse models, network, session, storage (moved over unchanged)
    topic/     topic list, topic detail, and the mapping onto upstream's cells
    profile/   one reader's site profile and their activity stream
    search/    the site's search behind upstream's action bar search field
    notifications/  the reader's in-app notifications over upstream's list row
    feature/   the copied data layer (profile, topics, notifications)
  src/test/java/io/github/baba983/nativedo/harness/   the render + side-effect harness
```

Our code never sits inside `apps/android/telegram/`. The one exception is
`io/github/baba983/nativedo/hooks/NativeDoHooks.java` inside `TMessagesProj`: it is a *new*
file, so a subtree pull cannot conflict with it, and it is where the one-line hooks
live that keep tgnet and Telegram's database switched off.

## The upstream lines

`git diff --stat <subtree-merge> -- apps/android/telegram/TMessagesProj/src/main/java/org/telegram
apps/android/telegram/settings.gradle` reports 3 files, 4 insertions: `ConnectionsManager.init` and
`ConnectionsManager.isTestBackend` return early while the hooks are off, `MessagesStorage.openDatabase`
does the same, and `settings.gradle` includes this module from outside the subtree. Nothing else
upstream is touched — rendering, gestures, layout and animation are upstream's own code.

## How upstream's cells get our data

* **Topic list** — `/latest.json` → `DialogCell.CustomDialog`, upstream's own row type for
  non-Telegram rows, inside a `RecyclerListView`. Tapping opens the topic.
* **Posts** — each post becomes `TLRPC.TL_message.rich_message` (the shape upstream itself uses
  for a locally built rich message) with the author as a `TLRPC.TL_user`; `ChatMessageCell`
  draws it. Upstream's `RichHtml` imports Discourse's cooked HTML directly, so the body is not
  re-mapped block by block. Discourse's presentational wrappers are unwrapped first, because
  `RichHtml` dispatches on the tag of each direct child.
* **Images** — upstream's rich media draws a photo that already exists in its own file cache and
  has no notion of a URL, so `DiscourseImageBridge` gives every image a stable id, rewrites the
  `<img>` into a media reference, and downloads the bytes (with the session's cookies) into the
  exact path upstream will look in. `PhotoViewer` then opens it full screen through a page-block
  adapter of our own, because upstream's own one is private to its chat screen.
* **Avatars** — `DiscourseAvatars` gives a participant a synthetic `UserProfilePhoto` whose file
  location names a stable id, and downloads the picture with the session's cookies. A row draws it
  the way upstream's chat list does (the cell only builds the image): the message list draws it after
  the row, and a dialog row is handed it through its own avatar receiver. The picture itself is read
  by the image loader from the file's path, because the cell's own ask names a Telegram datacenter
  that this client has none of — and that hand-over happens while the row is drawn, so nothing the
  cell decides later takes it back.
* **Attachments** — a post's file link becomes a document block whose `localPath` is where the
  bridge downloads it, so upstream's block shows its name and size and hands it to the system to
  open. The FileProvider paths this module ships cover the app cache those files live in.
* **Reactions** — a post's reactions become the chips upstream draws at the bottom of a bubble, and
  a tap toggles the reaction the chip stands for (`PUT .../custom-reactions/{id}/toggle.json`).
* **The post menu and reply** — a long press asks the reader what to do (copy, copy link, reply),
  and replying uses upstream's own composer (`ChatActivityEnterView`) at the bottom of the screen;
  its send button reports to a delegate, which posts the text to `/posts.json` with the CSRF token.
  The composer's paperclip offers a file while the field is empty; a strip of our own appears above
  the composer as soon as there is text, because upstream's own button steps aside then and a forum
  reply often wants a second picture. Each upload goes to `/uploads.json` and what the field receives
  is the markdown the site's own composer writes for the answer, pointing at its `upload://` handle.
* **Profiles** — a tap on a post's author (their avatar, or the name above the bubble) opens
  `/u/{username}.json` in a screen of our own. Upstream's `ProfileActivity` cannot be that screen:
  it is a Telegram account's profile, whose `onFragmentCreate` loads the full user through tgnet
  (`loadFullUser` sends `TL_users_getFullUser`, and the answer is written into Telegram's database)
  and whose tabs are that account's own photos, stories, contacts and gifts. So the screen is built
  from the cells upstream draws lists with: a `ProfileSearchCell` head carrying the reader's picture
  and their handle (the cell its own search and contact lists draw a person with — a picture, a
  name, one line under it, and no date), `HeaderCell` for the sections, `TextDetailCell` for the
  fields — the site's answer in the cell's own large line and the field's name under it, which is
  the way `ProfileActivity`'s info block writes a phone number above the word "Mobile" — and a
  `TextInfoPrivacyCell` for the bio. Under all of it the reader's activity: `user_actions.json`
  for the topics they started and for their replies, merged into one stream newest first, one
  `DialogCell` row per action, and a tap opens that action's topic at the floor it happened on.
* **Search** — the topic list's action bar carries upstream's search item
  (`ActionBarMenuItem.setIsSearchField`), and the screen behind it is upstream's own expanding field
  over `/search.json`: the site's three kinds of hit, each drawn by the cell upstream draws that row
  with - a topic hit is the topic list's `DialogCell`, a post hit is the topic it sits in with the
  post's own blurb, and a person is a `ProfileSearchCell`. A hit opens what it is: a topic or a post
  its topic at the floor it was found on, a person their site profile. The field waits 300 ms after
  the last keystroke, and an answer is only shown for the query still in the field when it arrives.
* **Notifications** — `/notifications.json` in a screen of our own, because upstream's notification
  screens are an account's own (its chats, its deliveries) and there is no account here. The row is
  upstream's `DialogCell` - a title, a line under it, a date and its unread badge; the site's actor
  goes in the line (its `data.display_username`, never `user_id`, which is the reader the
  notification was addressed to), the topic in the title, and a tap opens that topic at the
  notification's own floor. The site's topic-less notifications (a badge, likes it consolidated)
  have no destination and their rows are not tappable.
* **Theme and settings** — upstream's `ThemeActivity` (the appearance page) is the settings entry;
  the tab strip is upstream's `FilledTabsView`, with the feeds, the notifications and the settings
  page as its three entries.

## Rendering without a device

`src/test/java/.../harness` boots the real app under Robolectric with `@GraphicsMode(NATIVE)`
and renders the real screens to `.amp/in/artifacts/nativedo/`. Requirements that are easy to
miss:

* `@Config(instrumentedPackages = ["org.telegram"])` — without it upstream's `native` methods
  stay native and every cell dies on `UnsatisfiedLinkError`.
* the generated shadows in `harness/shadows/` must be listed in `@Config(shadows = [...])`;
  they replace the natives that live in `libtmessages.49.so`.
* loopers are drained with a task budget, and upstream's worker queues with
  `idleUpstreamQueues()`: a `Choreographer` frame callback keeps re-posting itself, so an
  unbounded drain never returns, and a cached image only reaches a cell once the queues turn.
* the harness asserts, per test, that tgnet never initialises and that Telegram's database is
  never opened.
* upstream's image singleton holds the file directories of the app that created it, and Robolectric
  shares one sandbox between test classes; the harness drops the instance at boot so each test reads
  and writes its own app's directories. Upstream's `PhotoViewer` is a singleton of the same kind:
  it is dropped too, because a viewer a test left open makes the next test's `openPhoto` refuse.

## Feature inventory

One row per user-facing capability: what the old app (`apps/android/app`) had, where this module
stands, and the upstream component each row is meant to use.

| Feature | Old app | Now | Upstream component |
| --- | --- | --- | --- |
| Sign-in and session restore (WebView `/login`, `_t` / `_forum_session` / `cf_clearance`, `/session/csrf`) | done | done | our own (moved from the old app's `ui/auth`) |
| Latest topic feed with paging | done | done | `DialogCell.CustomDialog` + `RecyclerListView` |
| New / top feeds, category and tag filters (`/site.json`) | done | missing | the same rows, driven by `CategoryStore` |
| Topic detail, cooked HTML to native blocks | done, 41 ports | done | upstream `RichHtml.parse` into `ChatMessageCell` |
| Post images and the lightbox | done | done | `PhotoViewer` |
| Links in a post | done | done | `Browser` |
| Onebox link previews (`aside.onebox`) | done, cards | done | upstream's link preview (`TL_messageMediaWebPage`): one bubble per onebox, the bare link line gone from the text, and no bubble draws a forward arrow (`noforwards`) |
| Video in a post (`<video>`) | partial, opened externally | done, to the clip's own size | upstream's video block (`pageBlockVideo` in `RichMessageLayout`'s media cell). Opening the topic fetches the cover only; the press on the block's own play button fetches the clip (up to 25 MB) while the block draws upstream's own loading state and progress, and the file opens in `PhotoViewer`. A clip over that, one the site refuses, and one the reader cancels - the block's own cancel button stops the transfer and drops the half-written file - is not played by upstream's viewer, which can only play a file this client holds: the first two are streamed by the device's own player, a cancel plays nothing and leaves the cover and the play button behind |
| Avatars | done | done | the avatar receivers of `ChatMessageCell` / `DialogCell` |
| Attachments: download, open with the system | done | done | upstream's document block |
| Long press a post: copy, copy link, reply | done | done | our delegate on `ChatMessageCell` |
| Reply (`POST /posts.json` with CSRF) | done, rich editor sheet | done, text only | `ChatActivityEnterView` |
| Reply with media (`POST /uploads.json`) | done | missing | `ChatAttachAlert` plus our uploader |
| Mentions and autocomplete (`/u/search/users`) | done | missing | the composer's mention panel |
| Reactions: toggle and who reacted | done | half, toggling only | upstream's reaction chips, `CustomEmojiReactionsWindow` |
| Polls: vote, retract, voter list | done | vote and retract; voter list missing | `TL_messageMediaPoll` with one bubble per poll, the floor split at its `div.poll`; a tap votes through our own `PUT /polls/vote`. Known gaps: `View Votes (N)` (upstream draws it, its voters list lives in `ChatActivity`, which this screen does not have) and `ranked_choice` (a tap cannot express a rank, so those polls open the floor menu instead of voting); poll option images and "add option" are not offered |
| Bookmarks (`/bookmarks.json`, `DELETE /posts/{id}/bookmark`) | done | missing | upstream's saved-messages menu entry |
| Read progress (`POST /topics/timings`) and restore | done | missing | upstream's unread divider and scroll-to-unread |
| Search: topics, posts, users, in-topic | done | done, the site's topics, posts and people from the action bar's own search field; a hit opens its topic at its floor, a person opens their profile; in-topic search missing | `ActionBarMenuItem.setIsSearchField` over `/search.json`, hits drawn by `DialogCell` / `ProfileSearchCell` |
| User profile (`/u/{username}.json`, activity feed) | done | done, `/user_actions.json` for the topics created and the replies merged into one stream, one upstream `DialogCell` row per action, a tap opens the topic at that floor | our own `BaseFragment` over `ProfileSearchCell` / `HeaderCell` / `TextDetailCell` / `TextInfoPrivacyCell` / `DialogCell`; `ProfileActivity` is unusable here (it loads the full user through tgnet and is bound to a Telegram account) |
| Notifications | not built | done, the reader's own list from `/notifications.json`; nothing pushes, and there is no unread count on the tab | upstream's notification screens are Telegram-account bound, so our own screen over upstream's `DialogCell`; unread is the row's own badge |
| Appearance and theme | done | done | `ThemeActivity` (`THEME_TYPE_BASIC`) |
| Account and session settings | done | missing | `SettingsActivity` subpages |
| linux.do emoji sprite in the composer | done | missing | upstream's emoji panel plus our sprite downloader |
| Floating date and jump to the read position | done | missing | upstream's chat date overlay |
| Text selection inside a post | done | missing | `ChatMessageCell`'s own selection |
| Share and deep links into a topic | partial | missing | `ShareActivity` plus an intent filter |

## Switch plan

Moving the entry point to `:nativedo` and deleting the old stack. Not started; it needs the human's
agreement.

* **CI** has to gain NDK `27.2.12479018`, CMake `3.22.1`, platform 36 and build-tools 36.0.0, and
  enough disk for `:TMessagesProj`'s CMake build (a cold arm64-v8a `assembleDebug` already produces
  a 40 MB `libtmessages.49.so`; a release builds every ABI).
* **The 15 submodule trees** that a subtree squash drops are fetched today by
  `.amp/in/artifacts/spike/fetch_submodules.sh` (pinned SHAs). That script has to move into
  `scripts/` and into the CI job and `.agents/setup`, or a fresh clone cannot build the library.
* **Delete:** `apps/android/app` (the old app), `apps/android/TMessagesProj` (the shrunk skeleton
  and its stubs), `apps/android/telegram/NativeDoSpike` (the superseded spike; its harness already
  lives in `src/test`), and the vendor tooling that only served the skeleton: `scripts/vendor-*.sh`,
  `scripts/vendor_guard.py`, `scripts/vendor-setup.sh`, `scripts/auto-vendor-copy.sh`,
  `vendor-overlay.json`, `VENDOR.md`, plus the root Gradle files and the pre-commit hook that point
  at the skeleton.
* **`AGENTS.md`** shrinks to three rules: `apps/android/telegram/**` is upstream at the pinned
  commit and is only ever pulled with `git subtree`; our code lives in `apps/android/nativedo/**`;
  the testing rules stay as they are. `VENDOR.md` disappears into that paragraph, because
  provenance is upstream's own tree plus one mainline commit.
* Nothing is deleted while the old app is still the entry point; this plan runs when the shells
  above are far enough along to replace it.

## Building

```sh
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDebugUnitTest
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a
```

`:nativedo` applies upstream's own app-side build plugin, which is what generates the
localization assets, the string-resource-id table and the lottie metadata that upstream's UI
reads at runtime; without it every upstream label renders as `LOC_ERR:<key>`. Native code needs
NDK 27.2.12479018 and the CMake toolchain; only arm64-v8a is built here.

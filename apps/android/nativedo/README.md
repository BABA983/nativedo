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
* **Theme and settings** — upstream's `ThemeActivity` (the appearance page) is the settings entry;
  the tab strip is upstream's `FilledTabsView`.

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
  and writes its own app's directories.

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
| Avatars | done | done | the avatar receivers of `ChatMessageCell` / `DialogCell` |
| Attachments: download, open with the system | done | done | upstream's document block |
| Long press a post: copy, copy link, reply | done | done | our delegate on `ChatMessageCell` |
| Reply (`POST /posts.json` with CSRF) | done, rich editor sheet | done, text only | `ChatActivityEnterView` |
| Reply with media (`POST /uploads.json`) | done | missing | `ChatAttachAlert` plus our uploader |
| Mentions and autocomplete (`/u/search/users`) | done | missing | the composer's mention panel |
| Reactions: toggle and who reacted | done | half, toggling only | upstream's reaction chips, `CustomEmojiReactionsWindow` |
| Polls: vote, retract, voter list | done | missing | `TL_messageMediaPoll`, `PollVotesAlert` |
| Bookmarks (`/bookmarks.json`, `DELETE /posts/{id}/bookmark`) | done | missing | upstream's saved-messages menu entry |
| Read progress (`POST /topics/timings`) and restore | done | missing | upstream's unread divider and scroll-to-unread |
| Search: topics, posts, users, in-topic | done | missing | search inside `DialogsActivity`, `SearchTabsAndFiltersLayout` |
| User profile (`/u/{username}.json`, activity feed) | done | missing | `ProfileActivity` |
| Notifications | not built | missing | upstream's notification screens are Telegram-account bound, so likely our own list |
| Appearance and theme | done | done | `ThemeActivity` (`THEME_TYPE_BASIC`) |
| Account and session settings | done | missing | `SettingsActivity` subpages |
| linux.do emoji sprite in the composer | done | missing | upstream's emoji panel plus our sprite downloader |
| Floating date and jump to the read position | done | missing | upstream's chat date overlay |
| Text selection inside a post | done | missing | `ChatMessageCell`'s own selection |
| Video in a post | partial, opened externally | missing | `VideoPlayer` through a rich media block |
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

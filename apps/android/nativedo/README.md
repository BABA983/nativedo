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
file, so a subtree pull cannot conflict with it, and it is where the three one-line hooks
live that keep tgnet and Telegram's database switched off.

## The three upstream lines

`git diff --stat <subtree-merge> -- apps/android/telegram/TMessagesProj/src/main/java/org/telegram`
reports 2 files, 3 insertions: `ConnectionsManager.init` and `ConnectionsManager.isTestBackend`
return early while the hooks are off, and `MessagesStorage.openDatabase` does the same. Nothing
else upstream is touched — rendering, gestures, layout and animation are upstream's own code.

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
  location names a stable id, downloads the picture with the session's cookies, and the rows draw
  it: the message list draws it after the row (upstream's chat list does the same, because the cell
  only builds the image), and a dialog row is handed it through its own avatar receiver.
* **Attachments** — a post's file link becomes a document block whose `localPath` is where the
  bridge downloads it, so upstream's block shows its name and size and hands it to the system to
  open. The FileProvider paths this module ships cover the app cache those files live in.
* **Reactions** — a post's reactions become the chips upstream draws at the bottom of a bubble, and
  a tap toggles the reaction the chip stands for (`PUT .../custom-reactions/{id}/toggle.json`).
* **The post menu and reply** — a long press asks the reader what to do (copy, copy link, reply),
  and replying uses upstream's own composer (`ChatActivityEnterView`) at the bottom of the screen;
  its send button reports to a delegate, which posts the text to `/posts.json` with the CSRF token.
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

## Building

```sh
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDebugUnitTest
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a
```

`:nativedo` applies upstream's own app-side build plugin, which is what generates the
localization assets, the string-resource-id table and the lottie metadata that upstream's UI
reads at runtime; without it every upstream label renders as `LOC_ERR:<key>`. Native code needs
NDK 27.2.12479018 and the CMake toolchain; only arm64-v8a is built here.

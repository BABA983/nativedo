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
    bookmarks/  the reader's own bookmarks over upstream's list row
    settings/   our own settings screen over upstream's cells (appearance, bookmarks, account)
    feature/   the copied data layer (profile, topics, notifications, bookmarks)
  src/test/java/io/github/baba983/nativedo/harness/   the render + side-effect harness
```

Our code never sits inside `apps/android/telegram/`. The one exception is
`io/github/baba983/nativedo/hooks/NativeDoHooks.java` inside `TMessagesProj`: it is a *new*
file, so a subtree pull cannot conflict with it, and it is where the one-line hooks
live that keep tgnet and Telegram's database switched off.

## The upstream lines

`git diff --stat <subtree-merge> -- apps/android/telegram/TMessagesProj apps/android/telegram/settings.gradle`
reports 12 upstream files, 23 inserted lines (18 of them replace the upstream line they sit on):
`ConnectionsManager.init` and `ConnectionsManager.isTestBackend` return early while the hooks are off,
`MessagesStorage.openDatabase` does the same, `RichMessageLayout` asks whether a file this client
fetches itself is still on its way (the media block's loading condition) and hands the block's own
cancel button to it, `MessagesController.isWebBrowserOpenInApp` asks the hook before it reads Telegram's
server setting, `LaunchActivity.getLastFragment` / `getSafeLastFragment` fall back to this client's
shell where `LaunchActivity` itself would answer, `Browser.openInTelegramBrowser` takes the minimized
browser tabs from the hook, `ActionBarLayout` hands a fresh `BottomSheetTabs` to the host's overlay,
and `BottomSheetTabs`, `ArticleViewer` and `BottomSheetTabDialog` look the host up the same way for a
sheet's dismiss, its navigation bar, its close button and the article lookup that is skipped while
tgnet is off; `settings.gradle` includes this module from outside the subtree. Every one of them is a
one-line call into `NativeDoHooks`; nothing else upstream is touched — rendering, gestures, layout and
animation are upstream's own code.

## The in-app browser

A link in a post goes through upstream's `Browser.openUrl` and opens upstream's `ArticleViewer` sheet:
the page's own action bar, its gestures, and the swipe that minimizes it into the `BottomSheetTabs`
strip, from which a tap restores it. Upstream finds all of that through `LaunchActivity`
(`instance.getBottomSheetTabs()`, `getSafeLastFragment()`, `getBottomSheetTabsOverlay()`), which this
client does not have, so `NativeDoHooks.BrowserHost` is the one place those four answers come from:
`NativeDoActivity` registers it, mirrors `LaunchActivity`'s host wiring (the `BottomSheetTabsOverlay`
in the content view, a main `ActionBarLayout` that builds the tabs, back closing the open tabs view),
and yields its own three-tab strip to the browser's while a page is minimized. The browser's own back
and close go through the sheet stack of the layout's sheet fragment, upstream's normal path.

Upstream decides in-app vs outside from `MessagesController.webBrowserSettings`, a Telegram account
setting fetched from the server; with no account it never arrives, and upstream's default would send
links to a custom tab or the system browser. This client's default is the in-app browser for every
`http(s)` URL (`NativeDoHooks.isWebBrowserOpenInApp`), and it is imposed only while the shell hosts
the app, so a real Telegram host keeps its own setting. One more Telegram-only step is skipped:
`ArticleViewer`'s `messages.getWebPage` lookup, which cannot be answered without tgnet, so the page
is loaded directly instead of waiting for it.

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
* **The post menu, bookmarks and reply** — a long press asks the reader what to do (copy, copy link,
  bookmark, reply);
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
  and whose tabs are that account's own photos, stories, contacts and gifts. So the screen keeps its
  own `BaseFragment` and takes upstream's own collapsing header for its head: the avatar lives in a
  `ProfileGooeyView`, and `ProfileActivity`'s `needLayout()` / `checkListViewScroll()` math moves and
  scales it while the name and status lines migrate into the screen's own transparent action bar.
  The sections below are the cells upstream draws lists with: `HeaderCell` for the sections,
  `TextDetailCell` for the fields — the site's answer in the cell's own large line and the field's
  name under it, which is the way `ProfileActivity`'s info block writes a phone number above the
  word "Mobile" — and a `TextInfoPrivacyCell` for the bio. Under all of it the reader's activity:
  `user_actions.json` for the topics they started and for their replies, merged into one stream
  newest first, one `DialogCell` row per action, and a tap opens that action's topic at the floor it
  happened on. Telegram-only parts of the header (stories ring, gift/emoji status, pull-down-to-open
  gallery, shared media, call buttons) are left out.
* **Search** — the home screen shows upstream's own `FragmentSearchField` where 12.10.5's chat list
  shows it, instead of an action-bar icon, and tapping it runs `DialogsActivity`'s open-search
  animation (the field's glass goes on, the list leaves over 200ms EASE_OUT, the shell's bottom bar
  steps away in `MainTabsActivity`'s own 380ms way) onto a `ViewPagerFixed` with the pill
  `TabsView` 12.10.5 makes its search tabs with. The tabs are this site's kinds of answer: 话题 /
  帖子 / 用户 are the topics, posts and people of one `/search.json` request, each drawn by the cell
  upstream draws that row with (`DialogCell`, `ProfileSearchCell`), and 分类 / 标签 are the site's
  own `/categories.json` and `/tags.json` narrowed to the query, because neither has a search route
  of its own. A hit opens what it is: a topic or a post its topic at the floor it was found on, a
  person their site profile, a category or tag the list filtered by it. The field waits 300 ms after
  the last keystroke, an answer is only shown for the query still in the field when it arrives, and
  loading/empty answers are upstream's `FlickerLoadingView` / `StickerEmptyView`. Back or the
  field's close button runs the close animation. The old pushed search screen is gone: every kind of
  hit it had (topics, posts, people) is on these tabs.
* **Notifications** — `/notifications.json` in a screen of our own, because upstream's notification
  screens are an account's own (its chats, its deliveries) and there is no account here. The row is
  upstream's `DialogCell` - a title, a line under it, a date and its unread badge; the site's actor
  goes in the line (its `data.display_username`, never `user_id`, which is the reader the
  notification was addressed to), the topic in the title, and a tap opens that topic at the
  notification's own floor. The site's topic-less notifications (a badge, likes it consolidated)
  have no destination and their rows are not tappable.
* **Theme and settings** — upstream's `ThemeActivity` (the appearance page) is the settings entry;
  `MainTabsLayout` and `GlassTabView` supply the three entries, their selection and gestures, with
  upstream's blur3 glass background. The host uses `AndroidUtilities.enableEdgeToEdge` and forwards
  system/IME insets through `DrawerLayoutContainer` and `ActionBarLayout`. Theme changes use the
  existing local application API (`save=false`), with local preferences and upstream color updates;
  they never save a theme to a Telegram account.

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
| New / top feeds | done | missing | the same rows, driven by our own feed calls |
| A category filter over the topic list (`/categories.json`, `/c/{slug}/{id}/l/latest.json`) | done | done: upstream's `FilterTabsView` as the floating glass capsule 12.10.5 presents its own filter tabs as, one tab per top-level category in the site's own order, the site's color as the dot in front of the name, and a tab loads that category's own feed; the strip floats over the list, which keeps its height as padding so rows scroll under it, and it appears when the categories arrive so a site that answers none leaves the list the whole screen | `FilterTabsView`, upstream's own list-filter control (the one its chat list filters chat folders with), with `BlurredBackgroundDrawableViewFactory` + `BlurredBackgroundProviderImpl.topPanel` over our `GlassPanelHost` |
| A tag filter over the topic list (`/tags.json`, `/tag/{slug}/l/latest.json`, `/tags/c/.../{tag}/{id}`) | not built | done: one item in the action bar (not a second strip, which would take the list's height) opens the site's tags in a dialog, the chosen tag shows in the title and clears in one tap, and it combines with the category tab over the site's own combined route | the action bar's own icon-and-dialog pattern, `ActionBarMenuItem` |
| Start a topic (a title, a category, a body → `POST /posts.json`) | not built | done: the list's own floating button - upstream's, the one its chat list starts a new chat with - opens upstream's titled field, upstream's composer for the body, and the site's categories behind a dialog; the send button appears only when the form is complete | `FragmentFloatingButton`, `EditTextBoldCursor`, `ChatActivityEnterView`, `AlertDialog` of the site's items |
| Topic detail, cooked HTML to native blocks | done, 41 ports | done | upstream `RichHtml.parse` into `ChatMessageCell` |
| Post images and the lightbox | done | done | `PhotoViewer` |
| Links in a post | done | done: a tap opens upstream's in-app browser — the `ArticleViewer` sheet over the topic, the page minimizing into Telegram's `BottomSheetTabs` strip and restoring from it; the shell supplies the host upstream looks up through `LaunchActivity`, and this client's default is in-app for `http(s)` links | `Browser.openUrl` → `openInTelegramBrowser` → `ArticleViewer` / `BottomSheetTabs` |
| Onebox link previews (`aside.onebox`) | done, cards | done | upstream's link preview (`TL_messageMediaWebPage`): one bubble per onebox, the bare link line gone from the text, and no bubble draws a forward arrow (`noforwards`) |
| Video in a post (`<video>`) | partial, opened externally | done, to the clip's own size | upstream's video block (`pageBlockVideo` in `RichMessageLayout`'s media cell). Opening the topic fetches the cover only; the press on the block's own play button fetches the clip (up to 25 MB) while the block draws upstream's own loading state and progress, and the file opens in `PhotoViewer`. A clip over that, one the site refuses, and one the reader cancels - the block's own cancel button stops the transfer and drops the half-written file - is not played by upstream's viewer, which can only play a file this client holds: the first two are streamed by the device's own player, a cancel plays nothing and leaves the cover and the play button behind |
| Avatars | done | done | the avatar receivers of `ChatMessageCell` / `DialogCell` |
| Attachments: download, open with the system | done | done | upstream's document block |
| Long press a post: copy, copy link, bookmark, reply | done | done | upstream's `ItemOptions` scrim menu anchored on the bubble; our delegate on `ChatMessageCell` supplies the choices |
| Swipe left on a post to answer it | done | done | ChatActivity's chat-list sliding view (`slidingView`/`setSlidingOffset`, reply disc, 50dp completion) |
| Reply (`POST /posts.json` with CSRF) | done, rich editor sheet | done, text and uploaded files | `ChatActivityEnterView` |
| Reply with media (`POST /uploads.json`) | done | done, the composer's own paperclip and our attach strip upload the file and insert the site's markdown for it | the composer's own attach button plus our uploader (`ChatAttachAlert`'s modal is Telegram's own) |
| Mentions and autocomplete (`/u/search/users`) | done | missing | the composer's mention panel |
| Reactions: toggle and who reacted | done | half, toggling only | upstream's reaction chips, `CustomEmojiReactionsWindow` |
| Polls: vote, retract, voter list | done | vote and retract; voter list missing | `TL_messageMediaPoll` with one bubble per poll, the floor split at its `div.poll`; a tap votes through our own `PUT /polls/vote`. Known gaps: `View Votes (N)` (upstream draws it, its voters list lives in `ChatActivity`, which this screen does not have) and `ranked_choice` (a tap cannot express a rank, so those polls open the floor menu instead of voting); poll option images and "add option" are not offered |
| Bookmarks (`/bookmarks.json`, `DELETE /posts/{id}/bookmark`) | done | done: the post menu adds and takes back a bookmark, and the reader's own list (`/u/{username}/bookmarks.json`, a 书签 row under 我的 on their own profile) draws the site's entries, a tap opening the topic at the bookmarked floor. The site's own envelope has a trap: a page with rows arrives under `user_bookmark_list`, an empty one is a bare `{"bookmarks":[]}` | upstream's `DialogCell` rows and the settings-shaped row on the profile |
| Read progress (`POST /topics/timings`) and restore | done | done, without upstream's unread divider: the floors the viewport reaches are reported as the reader moves (debounced) and at once when they leave, the dwell reported is the reader's real time - counted the way Discourse's own screen-track counts it, only while the screen is in front, a floor is on screen and the reader has scrolled within three minutes - a topic opens at the site's own `last_read_post_number` (or this device's further one), and the list's unread counts follow the site's answer - a topic read in full draws no badge even before the site has taken the report in | our own reporter over the site's endpoint; the list's rows are upstream's `DialogCell` |
| Search: topics, posts, users, categories, tags, in-topic | done | done: the home screen carries upstream's own `FragmentSearchField` (12.10.5's visible search entry, not a bar icon), and tapping it runs DialogsActivity's open-search animation - the list leaves, the pager arrives, the category strip fades, the bottom bar steps away - onto a `ViewPagerFixed` whose pill tabs are 话题 / 帖子 / 用户 / 分类 / 标签. The first three are one `/search.json` answer (topics, posts, users) with a 300ms debounce; 分类 and 标签 are `/categories.json` and `/tags.json` narrowed to the query, and picking one filters the home list. A hit opens its topic at its floor or a person their profile; the empty and loading states are upstream's `StickerEmptyView` / `FlickerLoadingView`; back or the field's close button runs the close animation; in-topic search missing | `FragmentSearchField`, `ViewPagerFixed` + its pill `TabsView` (+ `SearchTabsAndFiltersLayout`), `DialogsActivity`'s search animator math, `DialogCell` / `ProfileSearchCell` / `HashtagSearchCell` over `/search.json` / `/categories.json` / `/tags.json` |
| User profile (`/u/{username}.json`, activity feed) | done | done, `/user_actions.json` for the topics created and the replies merged into one stream, one upstream `DialogCell` row per action, a tap opens the topic at that floor; the head is upstream's own collapsing header (`ProfileGooeyView` + `ProfileActivity`'s `needLayout` math) | our own `BaseFragment` with `ProfileGooeyView` / `HeaderCell` / `TextDetailCell` / `TextInfoPrivacyCell` / `DialogCell`; `ProfileActivity` is unusable here (it loads the full user through tgnet and is bound to a Telegram account) |
| Notifications | not built | done, the reader's own list from `/notifications.json`; nothing pushes, and there is no unread count on the tab | upstream's notification screens are Telegram-account bound, so our own screen over upstream's `DialogCell`; unread is the row's own badge |
| Appearance and theme | done | done: the settings tab's 外观 row opens it | `ThemeActivity` (`THEME_TYPE_BASIC`) |
| Account and session settings | done | done: the settings tab is our own screen over upstream's cells (`HeaderCell` / `TextSettingsCell`), with the appearance page, the bookmark list and the account - the current reader and a sign-out that clears the cookies and returns to the login page | upstream's settings cells, the way `SettingsActivity` draws its own list |
| linux.do emoji sprite in the composer | done | missing | upstream's emoji panel plus our sprite downloader |
| Floating date and jump to the read position | done | missing | upstream's chat date overlay |
| Text selection inside a post | done | done: the post menu's 选择文本 picks the bubble's own text, and upstream's handles, extension, copy bar and clipboard take over from there | `ChatListTextSelectionHelper`, hosted the way `ChatActivity` hosts it |
| Share and deep links into a topic | partial | missing | `ShareActivity` plus an intent filter |

## 真机验证清单

每一项都是手感或真站点的疑问，orb 只能定正确性（golden 与 E2E），定不了这些。

- 分类条的手感：分类名字是否可读、颜色是否分得开、选中胶囊是否清楚、分类放不下时横向滑动是否被发现；分类到达前列表先出现再下移的跳变是否能接受。
- 标签菜单：栏上的标签图标是否能被看懂、标签多时弹窗里是否好找、选中的标签在标题里是否一眼可见、清除是否一步到位；分类加标签的组合请求是否返回符合预期的话题。
- 发新帖：手机键盘压在标题行和输入框上时是否可用，标题的 IME「下一项」是否落到正文，发送箭头的出现是否清楚，以及真站点对过短标题的原文拒绝是否显示出来。
- 阅读进度与真实站点：读完话题返回后 `/latest.json` 的 `unread_posts` 是否按实际读到的楼层下降，重开话题是否停在离开的楼层；真实停留时间的上报是前台、楼层在视口内、三分钟内有滚动才计时，读完一篇长帖后站点的阅读时长是否与体感一致。
- 书签：长按菜单的收藏与取消收藏、资料页与设置页的书签列表，在一个已有书签的账号上验证置顶顺序、被删楼层的行、已关闭话题的行。
- 设置页：外观 / 我的 / 账号三组的行高与间距是否符合上游观感；退出登录的确认框、清 cookie 后回到登录页、再登录回来后话题与通知是否正常。
- 通知 tab：没有推送和未读计数时，列表的排序和徽章是否仍然清楚。

### 帖子链接与内置浏览器（2026-10-04，`emulator-5554`，`nativedo-browser-wave2`）

- 链接的 before/after。旧构建里帖子中的 https 链接走不到上游的内嵌分支（它的宿主是 `LaunchActivity`，这个壳不是），
  落到 `CustomTabsIntent`/系统浏览器；本片在 `NativeDoHooks.BrowserHost` 里回答上游要找的四件事，`NativeDoActivity`
  照 `LaunchActivity` 接好宿主（contentView 里的 `BottomSheetTabsOverlay`、main `ActionBarLayout` 建 tabs、
  返回先关 tabs 面板、自家三格栏在页面最小化时让位），链接于是进入 Telegram 自己的 `ArticleViewer` 网页 sheet。
- 设置默认值：上游的内嵌开关是 `MessagesController.webBrowserSettings`（服务器下发的 Telegram 账号设置），
  没有账号它永远为空，上游默认会把 http(s) 链接放到 App 外（自定义标签页/系统浏览器）。本客户端明确改成
  默认内嵌（仅 http(s)，且只在我们的宿主存在时生效）；`ArticleViewer` 的 `messages.getWebPage` 查询在
  tgnet 关闭时直接跳过，页面立即加载而不是等一个不会来的回答。登录 WebView 的链接处理没有动：它只把
  非 http(s) scheme 交给 `Browser.openUrl`，本片的默认只拦 http(s)，那类链接仍照旧外部打开。
- 设备检查（覆盖安装 `-r -t`，未卸载未清数据，仍登录 @example_user）：
  - 帖子里的 linux.do 链接（社区官方 iOS App 帖中的 `https://linux.do/t/topic/2974886`）：进入内置浏览器，
    `X` + 标题/URL 两行 + 展开箭头 + 菜单的结构（不是 Chrome 自定义标签页），整页渲染。截图 `09-linuxdo-inapp`。
  - 向下滑：页面最小化为底部标签条（X + favicon + 标题 + 展开箭头），底部的 话题/通知/设置 栏按设计让位；
    点标签条恢复整页。截图 `10-minimize`、`11-restore`。
  - 返回键关闭浏览器，回到话题。截图 `13-back`。
  - 普通外链（帖子里的 App Store onebox → `apps.apple.com/us/app/linux-do-community`）同样进内置浏览器并加载整页。
    截图 `14-appstore`、`15-back-external`。
  - linux.do 深链仍走原生：显式给 `NativeDoActivity` 的 `VIEW` intent 原生打开该话题（`17/18`）；不带组件的
    `am start` 被系统解析器交给 Chrome（`16`，`pm resolve-activity` 返回 ResolverActivity），这是设备的系统首选状态，
    与本片改动无关。
  - `logcat` 只有 `Browser.openInExternalApp` 探测失败的 `ActivityNotFoundException`（上游自己 catch，内嵌分支的前置探测，
    属预期），crash buffer 为空，无 FATAL/ANR。摘录 `logcat-press-trace.txt`。
- 两个观察，留在后续：内置浏览器里的 linux.do 页面是匿名态（页头「Log In」，`/boards/topic/1` 显示 Access Denied）——
  打开浏览器前把 app 的 cookie store 同步进 WebView 还没接（截图为 `19-boards-link`）；浏览器菜单里的 历史/书签
  指向上游 Telegram 的收藏列表（`MediaDataController` 的媒体查询），点它不会崩但我们这边没有数据，是已知的接触面。
- 构建与测试：本 worktree 首次 `assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet`（原生 `.so` 取自同 commit 的设备
  worktree，Java/资源/dex/打包在本地跑），APK SHA-256 `192dc283cc83060169b52089a9336bb3b1f2a347d9155c399795d36743b21920`
  与设备 `base.apk` 一致（验后为了给满盘腾空间删掉了构建产物，哈希留在 `VERIFICATION.txt`）。`:nativedo:testDebugUnitTest`
  31 类 87 项全过（0 失败、0 error、0 skipped）；新增 `PostLinkBrowserTest` 固定失败模式：没有 `LaunchActivity` 时帖子
  链接仍要到达 `ArticleViewer` sheet 且一次 tgnet 请求都不发。`scripts/vendor-guard.sh --compile` OK（它管旧
  `apps/android/TMessagesProj` 骨架；本片的上游行在 `apps/android/telegram` 下，账记在「The upstream lines」一段）。
- 证据在 `.amp/in/artifacts/browser-wave2/`。

### 本片验证（2026-10-03，`emulator-5556`）

- 设置账号区的上游 `TextSettingsCell`「打开验证页」复用登录 WebView。恢复从 `/challenge`
  开始，首次登录仍从 `/login` 开始。旧 cookie 不自动完成，返回取消；保留登录、楼层和草稿，
  不填凭据、不发验证 POST、不重放回复。设置、登录、回复和阅读恢复的原断言均保留。
- 设备已从设置打开 `/challenge`。WebView 到达源站后显示站点 404 和「返回首页」；
  这不证明原生写入通过。设备点原生「完成验证」只同步会话，`/session/current.json` 返回 200，
  回到设置，没有写请求。实际 HTTP 错误仍不能完成；网页文案不等于 HTTP 状态。
  期间一次 `ERR_PROXY_CONNECTION_FAILED` 未计成功，原代理 `10.0.2.2:7890` 未改。
- 头像修复后的 `6eaab3b` HEAD 已串行强制执行五轮全量测试，每轮 30 个测试类、80 个用例，
  全部通过，记录在 `.amp/in/artifacts/final-five-pass/`。本次 `/challenge` 修正后又跑了完整
  30 类 80 项、相关测试和 arm64 构建。五连随后复现开场上报已清空时间，但测量前暂停仍硬等
  额外 POST 的测试竞态；现在暂停并等待在途上报结束后取基线，十秒前台／后台断言不变，
  生产 reporter 不改。诊断在 `.amp/in/artifacts/read-dwell-diagnostic/`；最终提交的五轮
  命令与 XML 保留在 `.amp/in/artifacts/challenge-recovery-final/`，失败即停。
- 验证页修复阶段 APK 用 `adb -s emulator-5556 install -r -t` 覆盖安装，版本 `0.1.0 (1)`，
  SHA-256 为 `2911409bd23b474726f46546c7a1799b74ca080bf42a863d65ce5906d271bd17`。
  保留首次安装数据；图片 `PhotoViewer` 已接入，完整内置 Browser 和整屏宿主迁移仍待验证。
- 验证页从另一页面返回曾重建 WebView、叠加完成按钮。新增导航回归测试先复现失败，
  再按上游 `BaseFragment` 约定保存 `fragmentView`；相关设置、首次登录、回复和阅读进度测试通过。
  覆盖安装后，从设置打开、打开私信再返回，设备保留原网页且只有一个完成按钮；返回取消不恢复。
- 仅在获授权的单人私信 2975944 上传 PNG、PDF 并各发送一次：两次 uploads 和两次 posts 均 200。
  完整 GET 读回 3 条帖子，图片标记仅第 2 楼、PDF 标记仅第 3 楼各一次。原生图片可见，
  点 PDF 块打开系统阅读器并显示测试文字；下载缓存 7,270 字节，SHA-256 与上传源一致。
  证据在 `.amp/in/artifacts/private-upload-verification/`。404 本身仍不证明 Cloudflare 写入放行。
- 视频设备验证受站点限制：一次 MP4 上传返回 422，没有发帖；GET bootstrap 确认允许扩展名
  不含 mp4/webm，附件上限 4,096 KB。未绕过限制、未重复上传。该私信仍无视频，按需下载／取消待验证。
  3 楼均无回应，当前只支持点已有 chips 切换，新增回应入口缺失，真实回应操作待验证。
- 最终导航修复后的五连使用串行 `:nativedo:testDebugUnitTest --rerun --quiet`，
  同一 HEAD 的命令、XML 和结果保存在 `.amp/in/artifacts/private-upload-final/`，失败即停；
  `:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet` 是构建 gate。
  `operation=posts/timings` 分开记录，不记录正文、cookie、query 或身份，不改变错误分类。
  构建、JVM 测试和网页 GET 都不替代未完成的设备结果。

### 上游玻璃 tabs 与 edge-to-edge（2026-10-03，`emulator-5556`）

- 直接接入 `MainTabsLayout`／`GlassTabView`／blur3，没有改 subtree 或增加 hook。话题、通知、设置
  的点击与选中态、子页隐藏／返回恢复、横屏、浅色／夜间模式均经设备检查，截图仅保留非敏感区域。
- `HomeTabsTest` 先复现旧 tabs、黑色安全区和未处理主题事件的失败，再验证真实导航、Skia 渲染、
  不对称 cutout、IME、无 Telegram 原生网络／数据库副作用；原登录、回复和阅读恢复断言保留。
- 完整软键盘在私信 2975944 的顶边为 1517px，回复框底边为 1513px；收起后恢复 2333px，
  tabs 隐藏，无回复／上传／回应请求。为检查完整键盘暂时关闭手写输入，结束时明确设回默认启用值 1。
- 覆盖安装版本 `0.1.0 (1)`；APK 与设备 SHA-256 都为
  `28d3912b954911eed5bfa0f67f840c28a7863dcd18f924bfa51749a27c7f523d`。登录和代理保留，无 crash，
  未生成 `cache4.db`。设置验证页仍可见完成按钮，返回取消回到设置。
- 最终 gate 是同一 HEAD 串行五次 `:nativedo:testDebugUnitTest --rerun --quiet`（30 类、84 项），
  失败即停；命令、HEAD、逐轮 XML、APK hash 与检查过的截图保留在 `.amp/in/artifacts/main-glass-tabs/`。
  arm64 `assembleDebug` 另行检查。视频下载／取消和新增回应仍受上一节的站点／入口限制，不能算设备通过。

### 长按菜单、文本选择与左滑回复（2026-10-03/04，`emulator-5556`）

- 长按帖子改成上游 `ItemOptions` 的 scrim 菜单：按下的气泡在暗幕之上保持高亮，菜单锚在它旁边。
  四项原行为不变，设备上逐项点过：复制、复制链接（两者的结果都由系统剪贴板浮层确认，分别是正文
  原文和 `https://linux.do/t/2977660/23284908`）、添加书签（真站点写入后菜单变为「取消书签」，再点
  一次还原）、回复（回复框出现 `@JerryXu09`）。
- 文本选择用上游 `ChatListTextSelectionHelper`，接线照 ChatActivity：overlay 挂在 content view、
  parent view 是列表、cell 通过 delegate 把触摸交给它、滚动调 `onParentScrolled`／`stopScrolling`、
  返回先清选择。它自己的发起方式要求气泡已经处于「菜单选中」状态，而我们的菜单是独占窗口的弹层，
  长按到不了 cell，所以选择从菜单进入：文本气泡的菜单多一项「选择文本」，选中整段后，高亮、两端
  手柄、拖动扩选、Copy 浮条、点空白／返回清除都是上游自己的。设备上扩选把选区缩到「已经用体验版
  回」，Copy 之后系统剪贴板浮层和粘贴回输入框两处都显示这段选中文本（不是整楼、不是空）。非文本
  气泡（投票）不提供这一项。
- 左滑回复按 ChatActivity 的 chat list 移植（0.4cm 起点阈值、`|dx|/3 > dy`、-80dp 夹取、50dp 触感与
  完成阈值、弹簧画的回复圆盘、松手减速、`setSlidingOffset`）：设备上拖动中气泡左移、圆盘出现并随
  阈值填充，过阈值松手后回复框带 `@leo_summit` 打开，松手未过阈值则回位。
- 不回归项（都经设备）：竖向滚动照常；选中状态下滚动列表，选区保留、浮条随滚动收起再出现（overlay
  不吃非手柄的触摸）；图片点击照常打开 PhotoViewer（上传测试图）；回应 chip 点了 ❤ 6→7 再点回 6，
  两次站点写都生效；正文链接照常进 `Browser.openUrl`（模拟器没有可处理 VIEW 的浏览器，是上一节就
  存在的内嵌浏览器限制）。logcat 没有 FATAL／ANR，crash buffer 为空，应用 `databases/` 里没有
  Telegram 的 `cache4.db`。
- 构建与测试：`assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet` 通过；APK 与设备
  `base.apk` 的 SHA-256 都是 `d159ede950b68b0c0f4180d0f9ff4cd162607c62b1dbfa99c9de87eefcddc8ca`。
  全量 `:nativedo:testDebugUnitTest` 30 类 85 例通过（0 失败、0 error、0 skipped）；`PostMenuTest`
  新增两条：菜单起始的选择是 rich 选择且覆盖整楼文本；overlay 不吞列表滚动、点空白清除选择。截图与
  设备 APK 留在 `.amp/in/artifacts/topic-wave1/`（`04-menu`、`08-copy`、`10-copy-link`、`16-swipe-reply`、
  `24-selection`、`26-selection-after-drag`、`29-pasted`、`37-scroll-with-selection`、`43-photoviewer` 等）。
- 决定与未验证：上游第二段长按（菜单打开后再长按正文）在本宿主不可能——`ItemOptions` 弹层持有窗口，
  第二下触摸到不了 cell，所以选择改从菜单进入；真站点投票（点选项）这轮没有单独执行，它要产生一次
  真实投票，回应 chip 已验证同一条触摸路径。

### 资料页水滴头像与折叠栏（2026-10-03，`linuxdo-native-profile-wave1`，AVD `Medium_Phone_API_36.0`，`emulator-5554`）

- 资料页的头部改成上游 `ProfileActivity` 自己的那套：头像放在 `ProfileGooeyView` 里，`needLayout()`
  的高度/缩放/位移、`checkListViewScroll()` 的滚动高度、`needLayoutText()` 的标题宽度和
  `updateGooey()` 的驱动都按上游常量和分支搬过来，name/online 两个 `SimpleTextView` 随折叠移进
  操作栏；页面自带透明操作栏（`setAddToContainer(false)`），列表 `topMargin = 状态栏 + 栏高`、
  首行从「资料」段开始、顶部留 `168dp` 头部高度，自绘的 `TopView` 盖住滚进来的行。每个搬来的块
  都在注释里点了上游方法名；未搬：故事环、礼物/emoji 状态、下拉展开画廊（`ProfileGalleryView`）、
  共享媒体、通话/写按钮、`TopView` 的 peer 颜色渐变。下拉展开是后续项，不是遗漏。
- JVM gate：同一 HEAD 上 `:nativedo:testDebugUnitTest --quiet` 30 类 85 项全过（0 失败、0 error、
  0 skipped）。新增 `ProfileScreenTest.theCollapsingHeaderKeepsTheProfileReadable`，先写失败模式
  （头部把字段盖住、或列表在头部移动时不再绑行），断言折叠中途字段仍可读；原有两条资料断言和
  `ProfileFeedTest`／`BookmarksTest` 的语义不变，只把「点第几行」的旧算法（首可见行 + 子序号）
  改成按列表尾部/可见起点算 —— 头部那 168dp 内边距会让前几行落在内边距里，布局管理器不把它们
  算作可见。
- 构建：`lockf ... :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet` 通过，
  APK 63.4 MB（含 40 MB `libtmessages.49.so`）。
- 设备：`adb -s emulator-5554 install -r -t` 覆盖安装成功（debug 包带 testOnly，与已有 APK 同签名），
  版本 `0.1.0 (1)`，设备 SHA-256 与构建产物一致
  （`2bbf4eb4b2e59171a6ff8ec1dc15c0adf97978cc85c9ebc3bed564b77e10775e`）；没有卸载、没有清数据、
  没有 crash（crash buffer 为空）、app 数据里没有 `cache4.db`。
- **设备上的折叠没有验到**：启动时站点侧会话已失效，`/session/current.json` 返回 404
  （`NativeDoSession status=404 accepted=true evidence=LoggedOut`、`hasToken/hasForumSession/
  hasClearance` 都为 true → `expired_ServerLoggedOut`），app 按自己的规则清掉会话与 cookie 并停在
  登录页。没有凭据，不在设备上模拟登录，所以浅色/夜间、顶部/中途/全折叠的截图和录屏都没有拿到；
  这一项是未验证，不是通过。logcat 摘录在 `.amp/in/artifacts/profile-wave1/`。
- 中途与全折叠的布局目前只有渲染 harness 的图（生产代码路径）：
  `.amp/in/artifacts/nativedo/profile-screen-mid.png`（头像缩小上收、名字与状态左移上移）和
  `profile-feed.png`（名字/状态停在操作栏左侧的上游位置，列表行紧贴栏下）。真机上的水滴与刘海/
  状态栏融合、GPU 模糊合成、边缘到边缘的状态栏图标对比度仍未验证；这台 AVD 是否带 cutout 也没有
  验证过（`ProfileGooeyView` 的刘海分支与无刘海分支都只看过代码）。
- 本片的命令、测试 XML 与设备证据保留在 `.amp/in/artifacts/profile-wave1/`。
- **合并后补验（`86d12cbf`，`emulator-5556`，已登录 @example_user）**：合并 HEAD 上 `:nativedo:testDebugUnitTest`
  30 类 86 项全过、arm64 `assembleDebug` 通过，`install -r -t` 覆盖安装（未卸载、未清数据）。从设置的
  账号行进入自己的资料页，浅色主题下三帧：顶部（大圆头像居中、名字与 `@example_user` 在下）、中途（头像缩小
  上收到操作栏那一行并开始模糊）、全折叠（头像消失，`example_user`／`@example_user` 两行标题停在返回箭头右侧，列表
  从栏下滚过，栏内没有透出内容）。crash buffer 为空。截图在 `.amp/in/artifacts/merged-wave1/`
  （`03-profile-top`、`04-profile-mid`、`05-profile-collapsed`）。仍未验：夜间主题、录屏下的连续水滴
  形变（静帧看不出颈部）、刘海融合；列表底部没有为手势条留内边距，最后一行会滚到手势条下面。

### 投票：单投、改票、撤回、多选切换与失败路径（2026-10-04，`emulator-5554`，已登录 @example_user）

- **前提与现场不符，已按授权补足**：指定的私信 2975944 的 3 楼当时没有任何投票——app 现场拉取的
  窗口（`/t/2975944/3.json`，以及为排除窗口遗漏再拉的 `/t/2975944/999`）都只有 1–3 楼，`polls`
  为空、cooked HTML 里也没有 `data-poll-name`（用临时的 `NativeDoPoll` 日志核实过，核完已撤回）。
  为让功能可验，用 app 自己的回复框在该私信发了第 4 楼（`POST /posts.json`，raw 就是两段
  `[poll]`），站点建出两个真实投票：`device_single`（regular，Alpha/Beta）与 `device_multi`
  （multiple，Red/Green/Blue）。这是设备上新增的测试内容；结束时两票都已撤回，两个投票本体保留
  （客户端没有删除入口）。找不到其他被授权的可投投票：站点搜索 `in:personal wbz` 命中的那条
  7 楼私信也没有投票。
- **发现并修复**：`regular` 投票的响应里 `poll.options[]` 不带 `chosen`（站点只给 `multiple`
  的每个选项合并 `chosen`），读者的选择只在响应顶层的 `vote` 数组里。`votePoll` 原来丢掉了它，
  所以投票后重建的那层楼把新票数画成「还没投」（底栏 `1 vote`，选项没有选中标记与百分比）。
  修复是 `DiscourseApiClient.votePoll` 把 `response.vote` 并回模型；`contracts/discourse/endpoints.md`
  的相应句子与 `fixtures/discourse/poll-vote-response.json` 同步修正（fixture 不再带 `chosen`，
  改由 `vote` 驱动；`PollTest` 断言不变，从此走的是真实响应的形状）。
- **单投**：点 Alpha → `GET /session/csrf` 200 + `PUT /polls/vote` 200 → 修复后立即出现
  「100% Alpha 1 / 0% Beta」、选中标记与 `View Votes (1)`；修复前的构建上复现过缺选中态的同
  画面（截图 `27-vote-alpha`，修复后 `30-change-to-beta`）。
- **改票**：已投 Alpha 时点 Beta → 一次 PUT 200 → 「0% Alpha / 100% Beta 1」。
- **撤回**：点已选的 Beta → `DELETE /polls/vote` 200 → 回到未投态（圆点、底栏 `No votes`）。
- **多选切换**：勾 Red → `Vote` → PUT 200 →「100% Red 1」；再点 Blue → 一次 PUT 200 →
  {Red, Blue} 各 100% 1；点掉 Red → 一次 PUT 200 → 只剩 Blue；点掉 Blue（集合为空）→
  DELETE 200 → 回到未投态。`selectionFor` 的「整组发送、空组即撤回」与设备一致。
- **离开重开看服务端**：返回列表再深链重开 `/t/2975944/4`：已投票时显示服务端的
  「100% Alpha 1 / View Votes (1)」，撤回后再重开两个投票都是 `No votes`——不是本地残留。
- **失败路径**：`cmd connectivity airplane-mode enable` 后点 Alpha，约 10 秒后弹出
  「投票失败／timeout」对话框，气泡仍是未投态（没有假的已投状态），关闭飞行模式再点同一下即
  PUT 200 成功。图 `36-airplane-fail`、`37-recovered`。
- 全程 `NativeDoSession` 会话行都是 `status=200 accepted=true`，token/论坛会话/clearance 都在；
  没有 challenge、没有 crash、没有打印任何 cookie/token。APK 是 arm64 `assembleDebug` 的
  `485e7da1…`（设备 `base.apk` SHA-256 相同，覆盖安装未卸载未清数据），`:nativedo:testDebugUnitTest`
  30 类 86 项全过（0 失败、0 error、0 skipped、exit 0）。截图与测试 XML 在
  `.amp/in/artifacts/poll-wave2/`。
- **未验证**：`ranked_choice`（客户端本来就把点按转成楼层菜单）、已关闭的投票、选项图与
  「添加选项」；`View Votes (N)` 点了没有反应（voter list 入口缺失，已知差距）；本机没有软键盘，
  多行 raw 是用 `input keyevent 66` 注入的（`send_by_enter=false`，回车只换行）。

### 资料页下拉展开、手势条内边距与夜间主题（2026-10-04，`linuxdo-native-profile2-wave2`，AVD `Medium_Phone_API_36.0`，`emulator-5554`）

- 底部内边距：资料页和壳一样接 insets（`ViewCompat.setOnApplyWindowInsetsListener`，读
  `AndroidUtilities.getDefaultWindowInsets` 的 bottom，和 `NativeDoActivity` 给自己的 content view
  用的同一套），列表底部 = 4dp + 手势条（本机 63px）。设备上滑到列表底：最后一行「后排参与」的文字
  底边距手势横条约 86px，不再压在横条下面（`17-list-bottom-inset.png` 与其 2x 放大
  `17-zoom-bottom.png`）。
- 下拉展开按上游 `ProfileActivity` 搬，常量和分支不改：列表顶部 padding 是整宽、首行用
  `scrollToPositionWithOffset(0, 168dp - 整宽)` 归位（3230-3245）；`scrollVerticallyBy` 的拖拽减半、
  松手回位与 disarm（4283-4312）；松手时 1/3 阈值决定收回还是动画到整宽方形（4129-4235、8443-8537）；
  方形、圆角与名字/`@` 的路径用 `setAvatarExpandProgress`（6254）和 `refreshNameAndOnlineY*`
  （8884-8897）的原式。设备上：短拉 200px 松手回弹（`04-pull-short-snapback.png`）；慢拉 1100px 松手
  后是整宽方形，白色 `example_user`／`@example_user` 压在照片左下角（`05-pull-mid.png`、`06-pull-expanded.png`）；上滑
  约 2/3 行程后方块收回为小圆头像（`07-after-collapse-swipe.png`），再滑到顶时头像缩小上移、名字进操作
  栏（`16-rest-check2.png`、暗色 `23-profile-collapsed-dark.png`）。
- 展开用的照片：站点模板的 `{size}` 填 720 单独下一份（独立的 id，落盘为
  `Telegram Images/0_1756365483.jpg`），linux.do 回的是自己的 288×288 上限；展开时经 `AvatarImageView`
  的 foreground 层按展开分数渐入，站点给不出更大图时退回小图放大（本机没有出现这一支）。
- 连续形变录屏（`collapse.mp4`，8s，慢速上下滑；`frames/` 逐帧、10fps/30fps 拼图
  `gooey-sheet-collapse.png`、`gooey-sheet-reexpand.png`、`gooey-sheet-30fps.png`）：头像随折叠连续
  缩小、上移并逐渐糊化，回程 30fps 逐帧平滑、没有单帧跳变。**但没出现 Telegram 那种明显的“颈部”**：
  这台 AVD 是 1 核 2GB，`SharedConfig` 判 LOW，走的是上游 `ProfileGooeyView` 的 CPU 实现
  （`stackBlurBitmap`），且屏幕没有刘海；CPU 实现把头像与屏幕上缘外的「黑条」合在一起模糊，在无刘海
  屏幕上只表现为贴住上缘时的变宽/晕开（`09-neck-window.png`、`zoom-5.6s-neck.png`），不是水滴颈。
  驱动代码与上游逐行一致（`updateGooey` 的 pullProgress/blur/gooeyEnabled/visibility、`needLayout` 的
  `avatarY`/`avatarScale` 公式、`setScaleX`/`onMeasure` 两个驱动点），没有可“按上游改”的差异；带
  RenderEffect 的 GPU 实现要求性能等级 ≥ AVERAGE，本机选不到。所以「水滴颈」在本机未证实，属设备
  分级限制，不能算通过。
- 夜间主题：设置 → 外观 → Switch to Night Mode 后，顶部（大圆头像、名字与 `@example_user`）、中途（糊化头像、
  列表行）、全折叠（名字进栏、列表从栏下滚过）都正常，背景、分隔线、栏下遮罩与文字对比度没问题
  （`21-profile-top-dark.png`、`22-profile-mid-dark.png`、`23-profile-collapsed-dark.png`）；切回日间
  主题再确认一次（`27-back-to-day.png`）。没有发现需要修的暗色问题。
- 两个入口都走过：设置 → 账号行（浅色 `03-profile-top-light.png`，暗色 `21`）；话题里点作者名
  （`25-topic-dark.png` → `26-profile-from-topic-dark.png`，打开 `momo199412` 的资料页）。
- 构建与测试：同一 HEAD `:nativedo:testDebugUnitTest` 30 类 86 例全过（0 失败、0 error、0 skipped）；
  `assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet` 通过，APK SHA-256
  `bc743975370c35d5afdb24dd6ca9c43cfa8ae2b90d7e1d427d03cdd2c46d7c8c`，`install -r -t` 覆盖安装
  （未卸载、未清数据）。harness 里资料页那两处滚动改成两次事件：第一次上滑只解除下拉武装并回到静止位，
  是上游自己的钳位，一次事件不会移动列表。
- 本片的命令、截图、录屏与拼图保留在 `.amp/in/artifacts/profile-wave2/`。

### 设备验证结果（2026-10-01/02，AVD `nativedo-test`／android-35 google_apis_tablet arm64-v8a，`emulator-5556`）

环境：Apple Silicon Mac mini，模拟器内全局代理 `10.0.2.2:7890`（这台机器上跑着的 ClashX Meta）。
本机 DNS 对 `linux.do` 被污染（解析到 `157.240.10.32`），不加代理连不上真实站点。一行一项：

- **WebView 登录与 Cloudflare**：通过。WebView 加载出真实登录页且没有弹挑战；退出登录后站点把 token
  判为登出（`expired_ServerLoggedOut`），登录页的 Cloudflare Turnstile 挑战能正常起来，读者也能重新登录。
- **tgnet 与 Telegram 数据库**：通过。`netstat` 只有通向代理的连接；app 目录没有 `cache4.db`
  （只有 WebView 自己的 `Databases.db`）；全程没有 `FATAL EXCEPTION`、没有 ANR。
- **通知 tab**：通过。真实 `/notifications.json` 的排序、徽章、日期都清楚，tab 上没有未读计数（预期）。
- **设置页**：通过。外观／我的／账号三组的行高与间距和上游一致；退出登录的确认框、清 cookie 后回到
  登录页都对（第一次点退出后进程消失、停在桌面，logcat 与 crash buffer 都没有痕迹，第二次没有复现）。
- **分类条**：通过。分类名可读、颜色分得开、选中胶囊清楚、放不下时横向滑动能被发现。
- **标签菜单**：通过，两处观察。栏上的图标更像漏斗而不是标签；选了分类之后标签在标题里会被截断
  （`linux.do · 国产替代 · #D…`）。分类加标签的组合请求返回的话题符合预期，× 一步清除。
- **帖子渲染、头像、onebox**：通过。上游 `ChatMessageCell` 的泡泡、作者色、时间、链接都对，头像都
  画出来了，`aside.onebox` 画成上游的链接预览卡片（`pan.quark.cn` / `pan.baidu.com` 那两段）。
- **帖子里的图片**：通过（`7949bec0` 之后）。修复前每一张都只是灰色空块，见下面第 4 条。
  本轮又统一了客户端身份（`31052dcb`）：图片请求以前写死一个常量 UA，现在和 API／WebView 用同一个
  （`AppContainer` 解析出来的真实 WebView UA）；改完头像与图片仍正常画出（`b1-01-feed.png`、
  `b1-04-pm-coldstart.png` 里的头像，`b1-05` 的站内页面）。这是修不一致，不宣称它是挑战的原因。
- **PhotoViewer**：通过。点图片全屏打开，页数「1 of 2」「1 of 12」，带上游的返回与更多菜单。
- **投票**：通过（渲染）。`TL_messageMediaPoll` 的两个泡泡、选项圆点、"Poll" 标签与 "18 votes／17 votes"
  都对；**投票动作没有执行**——它会在这个真实站点留痕，而用户没有指定测试话题。
- **发新帖**：部分通过。标题行、分类行与分类弹窗（站点的分类加颜色点）、composer 都在，发送箭头在表单
  完整前不出现（和文档一致）；没有实际发送，所以站点对过短标题的拒绝没看到。
- **书签**：列表通过；行摘要显示站点返回的 cooked HTML（`<a name="p-…" class="anchor">`）——`7ff28d8d`
  已把它按文本读，设备复验也通过了（见上）。
- **回应标签的 emoji**：部分通过（`8e2cf03f` 之后）。机制成立：站点给的图标被下载下来、按上游图片加载器要的
  键写进它读的那张表，之前什么都不画的 chips 现在画出图（`83-reactions4` / `84-reactions5` 两张）。
  **没有验证的是映射本身**：留下的、带 reactions 列表的 dump 只有公告帖那几张（`❤ 16, 👍 2`，在修复之前），
  84 那张话题没有 dump，7 个缓存文件名是 URL 的 md5，离线认不出对应哪个 reaction id——所以「画出来的图标
  分别对应哪个 id / iconUrl」我给不出证据。
  另外更正一句早先写错的话：id 不在站点 emoji 目录里时**不会**画文字，那条路上游只画数字
  （`PostReactionRendering.fallbackLabel`/`countText` 没有任何调用点）。所以 84 里 13/5/3/2 那几个裸数字，
  既可能是目录里没有的 id，也可能是图标没下下来的 id，两者画出来一模一样。
- **长按菜单**：通过（`1e25d718` 之后）。长按一层楼弹出上游自己的对话框：复制／复制链接／添加书签／回复。
- **书签摘要**：通过（`7ff28d8d` 的设备复验）。行里不再出现 `<a name="p-…">` 这类原始 HTML。
- **加载失败与重试**：通过（`46dfbcde` 之后）。把代理指到一个死端口时列表显示「没能读到话题列表／点一下重试」，
  把代理指回去再点一下，话题就回来了。
- **外观页文案**：部分通过（`f9d1c14e` 之后）。标题「外观」、行「正文字号」「更换背景」「话题列表」已经是
  这个客户端的说法；同页仍然留着 Telegram 的词：「Change Your Color」「Color theme」「Switch to Night Mode」
  「Browse Themes」「Message corners」，以及预览里那几条示例消息。
- **私信话题**（`/t/topic/2975944`）：通过（读取）。更正上一轮的结论：那次的 `403 evidence=Challenge`
  是 curl 的 TLS 指纹被挡，**不是 app**——同一个出口上 app 自己的请求全是 `status=200 accepted=true`
  （`NativeDoSession` 日志，`hasToken/hasForumSession/hasClearance` 都为 true），话题列表也一直正常。
  本轮给指定 URL 加了生产入口（站点的 `/t/<slug>/<id>[/<floor>]` 规则 + `VIEW` intent filter，
  `6c7675db`/`beabd574`），冷启动带这个链接能直接进到这条私信，正文和发帖人都画出来了
  （`b1-04-pm-coldstart.png`）。**仍然拿不到的是它的类型字段**：`archetype` / `allowed_users`
  只有 JSON 才有，而 curl 在这个出口上始终是 `403 cf-mitigated: challenge`，所以「它是用户发给自己的私信」
  这句话我没有独立证据，只能说它是一条只有该用户为收件人的话题、app 以登录会话读得到。
  **写操作没有做**（回复／上传／回应都留着，等用户明确指定这条话题再动）。
- **实体键盘**：配置通过，输入待用户确认。`hw.keyboard` 从 `no` 改成 `yes` 后冷启动，
  客户机出现了 `qwerty2` 键盘设备（`dumpsys input`：`13: qwerty2 (aka device 0 - built-in keyboard)`）；
  Mac 实体键盘的字母输入没有用 `adb input text` 冒充，等用户在窗口里实测。
- **加载失败与重试**（话题页）：通过（本轮 `2d85aaf6`/`0ffdb628`）。以前 `fetchTopic` 先请求
  `emojis.json`，目录失败会把正文一起带走，屏幕上只剩白底 + 一行日志；现在正文先发、目录是装饰性的、
  有缓存且失败不阻断，失败时屏幕给出来源分类的提示与重试入口
  （404 那条实测显示「没能读到这个话题／点一下重试／HTTP 404」，点一下正好再发一次请求，见
  `b1-06-topic-404-failure.png`/`b1-07-retry-after-404.png`）。挑战／登录失效那一支会打开登录 WebView、
  成功后回到原来那层重试——**这一支只有代码路径，设备上没有挑战可复现**。
- **视频与附件**：未验证（本轮）。视频块按上游的形状画出来了（灰底 + 播放按钮）；按播放后没有取回片子。
  上一轮把这条归给 Cloudflare 挑战是错的：同一出口上 app 的请求都是 `status=200`，那条结论作废，
  原因待查（本轮没有重测视频）。附件没有找到带附件的测试话题。
- **阅读进度**：部分通过。读完一段后返回、再打开同一话题，停在离开的楼层（离开时是 10:30 的那层，
  重开的第一屏是 10:31 的那层），设备自己的 `nativedo.topic_read_positions.xml` 也在写；
  站点侧 `unread_posts` 的下降没法量（那篇的未读是四位数，列表上的蓝色徽章看不出几十层的差别），
  阅读时长是否与体感一致更没量到。另外那一次请求超时后话题列表只是空着；现在列表和话题页都会说明
  失败并给出重试入口（`46dfbcde` 与本轮 `0ffdb628`）。
- **滚动流畅度**：只拿到模拟器的数（10 次滑动：253 帧、37% janky、50 分位 31 ms），模拟器的 GPU
  本来就不是真机的数，参考价值有限。
- **单测**：通过（本轮 `37f56de2` 之后）。`./apps/android/telegram/gradlew -p apps/android/telegram
  :nativedo:testDebugUnitTest`，清空 `build/test-results/testDebugUnitTest` 后**连续 5 次全量**都是
  **61 个用例、0 个失败、0 个 error、0 个 skipped，exit=0**。
  之前那条 `ReadProgressTest > theDwellIsTheReadersOwnForegroundTime` 有两层原因，都修了：
  (a) 生产侧 reporter 只在 `tick()` 里按「距上次 tick 的差」累计，tick 没跑到的那段就丢了；现在改成在
  tick／flush／report／离开前台四个点都从时钟取一次时间，晚到或漏掉的 tick 都丢不了，在途上报期间新增的时间
  由下一次上报带走（不再在 report 循环里反复取，否则每次请求都产生新时间、循环永不结束）。
  (b) 测试侧读到的是**窗口之前**那次上报：打开话题会排一次「它打开的楼层」的上报，这次上报和测试取的基线赛跑，
  所以断言有时读到窗口前的 body。现在先等这次上报落地再取基线。**断言没有放宽**（前台 10 秒仍是 9–12 秒，
  后台 10 秒仍不计），窗口那次上报实测约 11.4 秒。

设备上发现的问题（都已记录，六个已修）：

1. `ChatMessageCell` 因为 `noforwards` 给窗口加 `FLAG_SECURE`，整块话题页截图全黑、最近任务里是空缩略图
   ——`35376c48` 把它挪到 `NativeDoHooks.messageMaySecureWindow()`。
2. 书签摘要显示原始 HTML——`7ff28d8d`。
3. 帖子里的图片只写到 images 目录，而画它的 `ImageLoader` 按 CACHE_TYPE_CACHE 去 cache 目录找，所以
   永远是灰色空块——`7949bec0` 让两个目录都写一份（头像那条路早就这么做）。
4. 回应 chips 不画 emoji：上游的非自定义回应要从 `MediaDataController.getReactionsMap()` 里取图，那张表只由
   tgnet 的 `messages.getAvailableReactions` 填。`8e2cf03f` 改成把站点自己的图标（emoji 目录里
   `PostReaction.iconUrl` 那个地址）抓下来，按上游图片加载器要的键写进它读的那张 live 表：文档要非零
   `dc_id`（否则连 key 都算不出来），而芯片传的 cacheType 是 1，所以文件放在 `MediaDirCache/<dc_id>_<id>.png`。
   注意 `localPath` 在这条路上没用，那是附件块走的路。
5. 帖子里的图片只写到 images 目录，而画它的 `ImageLoader` 按 CACHE_TYPE_CACHE 去 cache 目录找，所以
   永远是灰色空块——`7949bec0` 让两个目录都写一份（头像那条路早就这么做）。
6. 长按打不开帖子菜单：上游的 cell 只在触摸落在它认为「可按下」的东西上时才武装长按，富文本的正文不算，
   所以 `onLongPress` 从不运行——`1e25d718` 让列表自己的 item long click 去开同一个对话框。
7. 抓取失败时列表只是空着，和「站点没有内容」长得一样——`46dfbcde` 让列表显示上游的
   `EmptyTextProgressView` 并允许点一下重试（它构造时自带的 touch listener 要清掉，否则点击被吞）。
8. 同一张图被两个写入者同时抓时共用 `<name>.part`，后一次 `renameTo` 找不到源而抛
   `cannot move … into place`，前一次的重命名还可能搬走半写的文件。要按目标文件串行化，或让每个写入者
   用自己的一次性 `.part` 名再原子改名；`DiscourseAvatars.cache` 与 `DiscourseImageBridge` 的
   封面／附件／图片四个调用点还是同一个形状（`DiscourseReactionEmoji` 里新写的那个已经按线程分了名字）。

已知且预期内的差异：分类和标签之外没有更多筛选；新话题要回列表重新读才出现；收藏没有 bulletin 提示；不支持站内私信（没有收件箱，也没有深链，界面上走不到一个私信话题）。

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

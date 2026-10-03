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
reports 4 upstream files, 6 inserted lines: `ConnectionsManager.init` and `ConnectionsManager.isTestBackend`
return early while the hooks are off, `MessagesStorage.openDatabase` does the same, `RichMessageLayout` asks
whether a file this client fetches itself is still on its way (the media block's loading condition) and
hands the block's own cancel button to it, and `settings.gradle` includes this module from outside the
subtree. Every one of them is a one-line call into `NativeDoHooks`; nothing else upstream is touched —
rendering, gestures, layout and animation are upstream's own code.

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
| A category filter over the topic list (`/categories.json`, `/c/{slug}/{id}/l/latest.json`) | done | done: upstream's `FilterTabsView` strip above the list, one tab per top-level category in the site's own order, the site's color as the dot in front of the name, and a tab loads that category's own feed; the strip appears when the categories arrive so a site that answers none leaves the list the whole screen | `FilterTabsView`, upstream's own list-filter control (the one its chat list filters chat folders with) |
| A tag filter over the topic list (`/tags.json`, `/tag/{slug}/l/latest.json`, `/tags/c/.../{tag}/{id}`) | not built | done: one item in the action bar (not a second strip, which would take the list's height) opens the site's tags in a dialog, the chosen tag shows in the title and clears in one tap, and it combines with the category tab over the site's own combined route | the action bar's own icon-and-dialog pattern, `ActionBarMenuItem` |
| Start a topic (a title, a category, a body → `POST /posts.json`) | not built | done: the list's own floating button - upstream's, the one its chat list starts a new chat with - opens upstream's titled field, upstream's composer for the body, and the site's categories behind a dialog; the send button appears only when the form is complete | `FragmentFloatingButton`, `EditTextBoldCursor`, `ChatActivityEnterView`, `AlertDialog` of the site's items |
| Topic detail, cooked HTML to native blocks | done, 41 ports | done | upstream `RichHtml.parse` into `ChatMessageCell` |
| Post images and the lightbox | done | done | `PhotoViewer` |
| Links in a post | done | routing wired; embedded browser not verified | `Browser.openUrl`; its embedded branch looks up the host through `LaunchActivity`, which this shell does not register as |
| Onebox link previews (`aside.onebox`) | done, cards | done | upstream's link preview (`TL_messageMediaWebPage`): one bubble per onebox, the bare link line gone from the text, and no bubble draws a forward arrow (`noforwards`) |
| Video in a post (`<video>`) | partial, opened externally | done, to the clip's own size | upstream's video block (`pageBlockVideo` in `RichMessageLayout`'s media cell). Opening the topic fetches the cover only; the press on the block's own play button fetches the clip (up to 25 MB) while the block draws upstream's own loading state and progress, and the file opens in `PhotoViewer`. A clip over that, one the site refuses, and one the reader cancels - the block's own cancel button stops the transfer and drops the half-written file - is not played by upstream's viewer, which can only play a file this client holds: the first two are streamed by the device's own player, a cancel plays nothing and leaves the cover and the play button behind |
| Avatars | done | done | the avatar receivers of `ChatMessageCell` / `DialogCell` |
| Attachments: download, open with the system | done | done | upstream's document block |
| Long press a post: copy, copy link, bookmark, reply | done | done | our delegate on `ChatMessageCell` |
| Reply (`POST /posts.json` with CSRF) | done, rich editor sheet | done, text and uploaded files | `ChatActivityEnterView` |
| Reply with media (`POST /uploads.json`) | done | done, the composer's own paperclip and our attach strip upload the file and insert the site's markdown for it | the composer's own attach button plus our uploader (`ChatAttachAlert`'s modal is Telegram's own) |
| Mentions and autocomplete (`/u/search/users`) | done | missing | the composer's mention panel |
| Reactions: toggle and who reacted | done | half, toggling only | upstream's reaction chips, `CustomEmojiReactionsWindow` |
| Polls: vote, retract, voter list | done | vote and retract; voter list missing | `TL_messageMediaPoll` with one bubble per poll, the floor split at its `div.poll`; a tap votes through our own `PUT /polls/vote`. Known gaps: `View Votes (N)` (upstream draws it, its voters list lives in `ChatActivity`, which this screen does not have) and `ranked_choice` (a tap cannot express a rank, so those polls open the floor menu instead of voting); poll option images and "add option" are not offered |
| Bookmarks (`/bookmarks.json`, `DELETE /posts/{id}/bookmark`) | done | done: the post menu adds and takes back a bookmark, and the reader's own list (`/u/{username}/bookmarks.json`, a 书签 row under 我的 on their own profile) draws the site's entries, a tap opening the topic at the bookmarked floor. The site's own envelope has a trap: a page with rows arrives under `user_bookmark_list`, an empty one is a bare `{"bookmarks":[]}` | upstream's `DialogCell` rows and the settings-shaped row on the profile |
| Read progress (`POST /topics/timings`) and restore | done | done, without upstream's unread divider: the floors the viewport reaches are reported as the reader moves (debounced) and at once when they leave, the dwell reported is the reader's real time - counted the way Discourse's own screen-track counts it, only while the screen is in front, a floor is on screen and the reader has scrolled within three minutes - a topic opens at the site's own `last_read_post_number` (or this device's further one), and the list's unread counts follow the site's answer - a topic read in full draws no badge even before the site has taken the report in | our own reporter over the site's endpoint; the list's rows are upstream's `DialogCell` |
| Search: topics, posts, users, in-topic | done | done, the site's topics, posts and people from the action bar's own search field; a hit opens its topic at its floor, a person opens their profile; in-topic search missing | `ActionBarMenuItem.setIsSearchField` over `/search.json`, hits drawn by `DialogCell` / `ProfileSearchCell` |
| User profile (`/u/{username}.json`, activity feed) | done | done, `/user_actions.json` for the topics created and the replies merged into one stream, one upstream `DialogCell` row per action, a tap opens the topic at that floor; the head is upstream's own collapsing header (`ProfileGooeyView` + `ProfileActivity`'s `needLayout` math) | our own `BaseFragment` with `ProfileGooeyView` / `HeaderCell` / `TextDetailCell` / `TextInfoPrivacyCell` / `DialogCell`; `ProfileActivity` is unusable here (it loads the full user through tgnet and is bound to a Telegram account) |
| Notifications | not built | done, the reader's own list from `/notifications.json`; nothing pushes, and there is no unread count on the tab | upstream's notification screens are Telegram-account bound, so our own screen over upstream's `DialogCell`; unread is the row's own badge |
| Appearance and theme | done | done: the settings tab's 外观 row opens it | `ThemeActivity` (`THEME_TYPE_BASIC`) |
| Account and session settings | done | done: the settings tab is our own screen over upstream's cells (`HeaderCell` / `TextSettingsCell`), with the appearance page, the bookmark list and the account - the current reader and a sign-out that clears the cookies and returns to the login page | upstream's settings cells, the way `SettingsActivity` draws its own list |
| linux.do emoji sprite in the composer | done | missing | upstream's emoji panel plus our sprite downloader |
| Floating date and jump to the read position | done | missing | upstream's chat date overlay |
| Text selection inside a post | done | missing | `ChatMessageCell`'s own selection |
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

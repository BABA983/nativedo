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
and yields its own four-tab strip to the browser's while a page is minimized. The browser's own back
and close go through the sheet stack of the layout's sheet fragment, upstream's normal path.

Upstream decides in-app vs outside from `MessagesController.webBrowserSettings`, a Telegram account
setting fetched from the server; with no account it never arrives, and upstream's default would send
links to a custom tab or the system browser. This client's default is the in-app browser for every
`http(s)` URL (`NativeDoHooks.isWebBrowserOpenInApp`), and the reader can turn it off with 设置 ->
在应用内打开链接: the choice is stored by `LinkOpenSettings` and handed to the same hook, after which
upstream's custom-tabs branch takes the link. The choice is imposed only while the shell hosts the
app, so a real Telegram host keeps its own setting. A link a post points at this site's own topic is
not a browser matter in either state: `TopicFragment` presents the topic itself. One more
Telegram-only step is skipped: `ArticleViewer`'s `messages.getWebPage` lookup, which cannot be
answered without tgnet, so the page is loaded directly instead of waiting for it.

The browser, the login WebView and the API client share one cookie store:
`android.webkit.CookieManager` is the only jar. OkHttp's side of it is a network interceptor that
reads `CookieManager.getCookie(url)` per exchange and writes each response `Set-Cookie` value
back verbatim (attributes included), for `linux.do` and its subdomains only; nothing is seeded or
imported between the WebViews and the native client, so a `_t` rotation by any of them is what
every other participant sends next. A response that arrives after another participant already
rotated `_t` may not write or delete the auth cookies, and a logout-shaped answer that lost that
race fails its own request instead of ending the session. The retired native snapshot
(`nativedo.cookie_store`) is read once into an empty jar and then deleted. Details in
`contracts/discourse/auth-flow.md`.

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
* **Profiles** — a tap on a post's author (their avatar, or the name above the bubble), or on the
  topic header's center capsule, opens `/u/{username}.json` in a screen of our own. Upstream's
  `ProfileActivity` cannot be that screen: it is a Telegram account's profile, whose
  `onFragmentCreate` loads the full user through tgnet (`loadFullUser` sends
  `TL_users_getFullUser`, and the answer is written into Telegram's database) and whose tabs are
  that account's own photos, stories, contacts and gifts. So the screen keeps its own `BaseFragment`
  and takes upstream's profile pieces one by one. The head is upstream's collapsing header: the
  avatar lives in a `ProfileGooeyView`, and `ProfileActivity`'s `needLayout()` /
  `checkListViewScroll()` math moves and scales it while the name and status lines migrate into the
  screen's own transparent action bar. A capsule tap hands the profile the capsule avatar's X, and
  the screen plays `ProfileActivity`'s own `playProfileAnimation == 2` flight for it (250 ms,
  `CubicBezierInterpolator.DEFAULT`, the avatar's average colour behind the header) - opening from a
  post avatar or 我的 keeps the ordinary transition. Under the title sits upstream's action row
  (`ProfileActionsView`, our port): 私信 (`GET /new-message?username=…` in the in-app browser,
  offered only when the profile says `can_send_private_message_to_user`), 关注 / 取消关注
  (`PUT|DELETE /follow/{username}`, the discourse-follow plugin), 静音 / 取消静音
  (`PUT /u/{username}/notification_level.json`) and 分享; the reader's own profile gets 书签 /
  设置 / 分享. The sections under it are the cells upstream draws lists with: `HeaderCell`,
  `TextDetailCell` (the site's answer in the cell's large line, the field's name under it) and a
  `TextInfoPrivacyCell` for the bio, fed by `/u/{username}.json` and `/u/{username}/summary.json`.
  The list ends in the site's own tabs - 话题 / 回复 / 点赞 / 书签(own only) / 徽章 over
  `user_actions.json` filters 4/5/1, the reader's bookmarks and `/user-badges/{u}.json` - drawn on
  a `ScrollSlidingTextTabStrip` + `ViewPagerFixed` pair ported from `SharedMediaLayout`'s mechanism
  (the strip pins to the list's top; a drag the list cannot take continues into the page's list,
  through a `NestedScrollingParent3` root like `ProfileActivity`'s `NestedFrameLayout`). Telegram-only
  parts of the header (stories ring, gift/emoji status, pull-down-to-open gallery, shared media, call
  buttons) are left out.
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
* **Theme and settings** — `MainTabsLayout` and `GlassTabView` supply the four entries (话题／通知／设置
  and the reader's avatar tab), their selection and gestures, with upstream's blur3 glass background. The
  host uses `AndroidUtilities.enableEdgeToEdge` and forwards system/IME insets through
  `DrawerLayoutContainer` and `ActionBarLayout`. The settings tab itself is upstream's
  `SettingsActivity` skeleton with our rows: a `UniversalRecyclerView` whose first item is a custom
  avatar header (`asCustomShadow(topView, 200 - 12)`: a 90dp round avatar, the 22sp name and the 13sp
  `@username`, upstream's own constants), a title that fades in as the header scrolls away, section
  headers, `SettingCell` rows wearing the `IconBackgroundColors` gradient squares, and a
  `WebBrowserSettings`-style check row for 在应用内打开链接. Upstream's appearance page (`ThemeActivity`)
  stays the way in to the themes, and theme changes use the existing local application API
  (`save=false`), with local preferences and upstream color updates; they never save a theme to a
  Telegram account.
* **The reader's own tab** — the fourth tab is upstream's avatar tab (`GlassTabView.createAvatar`):
  the label plus a 22dp round picture where the icon tabs draw their lottie. Telegram builds it from
  the account's own user; here the picture is the session's `DiscourseAvatars` user (downloaded with
  the session's cookies, like every other avatar). It hosts `ProfileFragment.forRoot` - the same
  screen a post's author opens, without the back button, because a root has nothing behind it.

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
| A "N 个新话题" pill when the site publishes new or bumped topics | not built | done: `/latest` and `/new` through the message bus count one incoming topic per id (the category the message named), the count follows the category tab in front of the reader (全部 counts all), and the pill refreshes the list, scrolls to top and clears on tap; a screen refresh clears it too; the list starts below the pill while it is shown | `ButtonWithCounterView` (upstream's rounded control) over `AndroidUtilities.updateViewVisibilityAnimated`'s upstream constants |
| New / top feeds | done | missing | the same rows, driven by our own feed calls |
| A category filter over the topic list (`/categories.json`, `/c/{slug}/{id}/l/latest.json`) | done | done: upstream's `FilterTabsView` as the floating glass capsule 12.10.5 presents its own filter tabs as, one tab per top-level category in the site's own order, the site's color as the dot in front of the name, and a tab loads that category's own feed; the strip floats over the list, which keeps its height as padding so rows scroll under it, and it appears when the categories arrive so a site that answers none leaves the list the whole screen | `FilterTabsView`, upstream's own list-filter control (the one its chat list filters chat folders with), with `BlurredBackgroundDrawableViewFactory` + `BlurredBackgroundProviderImpl.topPanel` over our `GlassPanelHost` |
| A tag filter over the topic list (`/tags.json`, `/tag/{slug}/l/latest.json`, `/tags/c/.../{tag}/{id}`) | not built | done: one item in the action bar (not a second strip, which would take the list's height) opens the site's tags in a dialog, the chosen tag shows in the title and clears in one tap, and it combines with the category tab over the site's own combined route | the action bar's own icon-and-dialog pattern, `ActionBarMenuItem` |
| Start a topic (a title, a category, a body → `POST /posts.json`) | not built | done: the list's own floating button - upstream's, the one its chat list starts a new chat with - opens upstream's titled field, upstream's composer for the body, and the site's categories behind a dialog; the send button appears only when the form is complete | `FragmentFloatingButton`, `EditTextBoldCursor`, `ChatActivityEnterView`, `AlertDialog` of the site's items |
| Topic detail, cooked HTML to native blocks | done, 41 ports | done | upstream `RichHtml.parse` into `ChatMessageCell` |
| Post images and the lightbox | done | done | `PhotoViewer` |
| Links in a post | done | done: a tap opens upstream's in-app browser — the `ArticleViewer` sheet over the topic, the page minimizing into Telegram's `BottomSheetTabs` strip and restoring from it; the shell supplies the host upstream looks up through `LaunchActivity`, this client's default is in-app for `http(s)` links, and a linux.do page is seeded with the app's session first (and only ever on linux.do's own hosts) | `Browser.openUrl` → `openInTelegramBrowser` → `ArticleViewer` / `BottomSheetTabs` |
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
| Bookmarks (`/bookmarks.json`, `DELETE /posts/{id}/bookmark`) | done | done: the post menu adds and takes back a bookmark, the reader's own 书签 button on their profile opens the list (`/u/{username}/bookmarks.json`), and the profile's own 书签 tab draws the same entries inline, a tap opening the topic at the bookmarked floor. The site's own envelope has a trap: a page with rows arrives under `user_bookmark_list`, an empty one is a bare `{"bookmarks":[]}` | upstream's `DialogCell` rows |
| Read progress (`POST /topics/timings`) and restore | done | done, without upstream's unread divider: the floors the viewport reaches are reported as the reader moves (debounced) and at once when they leave, the dwell reported is the reader's real time - counted the way Discourse's own screen-track counts it, only while the screen is in front, a floor is on screen and the reader has scrolled within three minutes - a topic opens at the site's own `last_read_post_number` (or this device's further one), and the list's unread counts follow the site's answer - a topic read in full draws no badge even before the site has taken the report in | our own reporter over the site's endpoint; the list's rows are upstream's `DialogCell` |
| Search: topics, posts, users, categories, tags, in-topic | done | done: the home screen carries upstream's own `FragmentSearchField` (12.10.5's visible search entry, not a bar icon), and tapping it runs DialogsActivity's open-search animation - the list leaves, the pager arrives, the category strip fades, the bottom bar steps away - onto a `ViewPagerFixed` whose pill tabs are 话题 / 帖子 / 用户 / 分类 / 标签. The first three are one `/search.json` answer (topics, posts, users) with a 300ms debounce; 分类 and 标签 are `/categories.json` and `/tags.json` narrowed to the query, and picking one filters the home list. A hit opens its topic at its floor or a person their profile; the empty and loading states are upstream's `StickerEmptyView` / `FlickerLoadingView`; back or the field's close button runs the close animation; in-topic search missing | `FragmentSearchField`, `ViewPagerFixed` + its pill `TabsView` (+ `SearchTabsAndFiltersLayout`), `DialogsActivity`'s search animator math, `DialogCell` / `ProfileSearchCell` / `HashtagSearchCell` over `/search.json` / `/categories.json` / `/tags.json` |
| User profile (`/u/{username}.json`, `/u/{username}/summary.json`, activity) | done | done: the head is upstream's collapsing header (`ProfileGooeyView` + `ProfileActivity`'s `needLayout` math), the capsule tap plays its `playProfileAnimation == 2` avatar flight, the row under it is upstream's `ProfileActionsView` port (私信 / 关注 / 静音 / 分享; 书签 / 设置 / 分享 on one's own), the sections are `HeaderCell` / `TextDetailCell` / `TextInfoPrivacyCell` over the profile + summary endpoints, and the list ends in 话题 / 回复 / 点赞 / 书签(own) / 徽章 tabs over `user_actions.json` filters 4/5/1, the bookmarks and `/user-badges/{u}.json`, on `ScrollSlidingTextTabStrip` + `ViewPagerFixed` | our own `BaseFragment` with the upstream components above; `ProfileActivity` is unusable here (it loads the full user through tgnet and is bound to a Telegram account) |
| Notifications | not built | done, the reader's own list from `/notifications.json`, and the 通知 tab's own counter is the site's unread count: seeded from the bootstrap page and then fed by the message bus `/notification/{userId}` channel (the server publishes a new count after notifications are read); opening the list does not mark anything read | upstream's notification screens are Telegram-account bound, so our own screen over upstream's `DialogCell`; the tab counter is upstream's `GlassTabView.setCounter`, the same mechanism `MainTabsActivity` puts on its chats tab |
| Appearance and theme | done | done: the settings tab's 外观 row opens it, and the home action bar's options menu switches night/day in place (upstream's own entries, with the circular reveal out of the menu icon) | `ThemeActivity` (`THEME_TYPE_BASIC`); `DialogsActivity`'s options item / `ItemOptions` / `showItemOptions()` / `switchTheme()` |
| Account and session settings | done | done: the settings tab is our own screen over upstream's cells (`HeaderCell` / `TextSettingsCell`), with the appearance page, the bookmark list and the account - the current reader and a sign-out that clears the cookies and returns to the login page | upstream's settings cells, the way `SettingsActivity` draws its own list |
| linux.do emoji sprite in the composer | done | missing | upstream's emoji panel plus our sprite downloader |
| Floating date and jump to the read position | done | missing | upstream's chat date overlay |
| Text selection inside a post | done | done: the post menu's 选择文本 picks the bubble's own text, and upstream's handles, extension, copy bar and clipboard take over from there | `ChatListTextSelectionHelper`, hosted the way `ChatActivity` hosts it |
| Share and deep links into a topic | partial | missing | `ShareActivity` plus an intent filter |

## 真机验证清单

每一项都是手感或真站点的疑问，orb 只能定正确性（golden 与 E2E），定不了这些。

### 资料页改版：胶囊开屏动画、动作条与站点 tabs（2026-10-05，`emulator-5554`，`nativedo-profile-redesign`，已登录 @example_user）

- 打开动画：中心胶囊的点按把胶囊头像在资料页坐标系里的 X（`ViewPositionWatcher.computeXCoordinateInParent`，上游 `ProfileActivity:10080` 同一手法）交给 `ProfileFragment.forUserFromAvatar`；页面按 `ProfileActivity` 的 `playProfileAnimation == 2`（250ms、`CubicBezierInterpolator.DEFAULT`、50ms 延迟启动）让头像从 42dp 的胶囊圆图长到页头 96dp 的静止大头像、圆角保持 50dp（本站落在折叠态页头，Telegram 落在展开态，其余常量与曲线照搬），列表 0→1 淡入并从左侧 48dp 归位，页头底色由透明白混到头像平均色（`AndroidUtilities.calcBitmapColor`）。点帖子头像与 我的 tab 仍走普通转场（`playProfileAnimation == 0`）。设备：话题页点胶囊进 Neo 的资料页，logcat `present fragment ProfileFragment args=Bundle[{avatarX=204.0, username=neo…}]`（`02-profile-from-capsule`）；本 AVD 的 `screenrecord` 仍冻结（两次尝试仅 1–2 帧，与既有记录一致），动效以 harness 的 `profile-open-from-capsule` 渲染 + 状态断言为证。
- 动作条（移植 `ui/Components/ProfileActionsView.java`）：头像下的 74dp 行，胶囊画法/出现与删除动画/图标全是上游的；颜色改为按用户名取的头像背板色（上游在模糊页头上用 40 alpha，本站页面是纯色底，改成实色）。四项按站点回答出现：私信（`can_send_private_message_to_user`，内嵌浏览器开 `/new-message?username=`）、关注/取消关注（discourse-follow 插件 `PUT|DELETE /follow/{u}`，写前取 csrf）、静音/取消静音（`PUT /u/{u}/notification_level.json`）、分享（系统分享，无接收方时复制链接）；自己的资料页是 书签/设置/分享。设备：Neo 的资料页显示 Message/取消关注/分享（Neo 已被 @example_user 关注，与站点 `is_followed` 一致，`02`），@example_user 自己的资料页显示 书签/设置/分享（`15-own-profile`）。「忽略」按站点 `can_ignore_user` 真实存在但未做确认流，README 的映射记录了这一条。
- 信息区：`HeaderCell`/`TextDetailCell`/`TextInfoPrivacyCell` 照旧，新增 用户名/所在地/个人网站/最后发帖，以及 `/u/{u}/summary.json` 的 话题/帖子/获赞/送出赞/访问天数/阅读帖子（summary 没到就不画，不拿 0 充数）。设备实拍 Neo 的统计：话题 649、帖子 21254、获赞 620454、送出赞 3629、访问天数 993、阅读帖子 1631688、阅读时长 3155 小时、关注者 12456（`04-profile-scrolled2`），summary 解析正确。
- tabs 区（`ProfileSharedMediaView`：`ScrollSlidingTextTabStrip` + `ViewPagerFixed`，条高 50dp / 7dp 内边距、页首行上留 54dp、圆角与颜色常量照 `SharedMediaLayout`；屏幕根布局实现 `NestedScrollingParent3`，tab 行顶到列表上缘后把列表吃不下的滚动交给当前页自己的列表，同 `ProfileActivity.NestedFrameLayout`）：话题/回复/点赞 是 `user_actions.json` 的 filter 4/5/1 各自分页（offset），书签仅自己（`/u/{u}/bookmarks.json` 每页 20），徽章是 `/user-badges/{u}.json`；每页有 `FlickerLoadingView` 与空态。设备：Neo 的 话题 页（`16-final-tab-topics`，真实帖子行，摘要已去 HTML）、徽章 页（`14-tab-badges`，热门链接/领导者/不错的回复 与授予日期）。
- 设备上改掉的两处：summary 晚到会重建 info rows，最初把作为最后一行的 tabs 行一起冲掉（列表底部只剩统计行、tabs 永远到不了）；现在 `buildRows` 在 tabs 视图存在时总是重新追加该行，harness 的 tabs 用例同时喂 summary fixture 来钉住。另：`user_actions` 的摘要会带 cooked HTML（真站点的 `<a href=…>`），按书签同款 `plainTextFromHtml()` 去标签。
- 未验证：真机（`449dd25d`）未装本片 APK（片内按规则只做只读设备）；录屏冻结，连续水滴动画只有 harness 帧与设备前后截图；关注/静音的写路径只在 harness 的 `FakeDiscourseSite` 上打过（设备上未对真实账号做写操作）。
- 构建与测试：`:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`（排除两个 CMake 任务，arm64 `.so` 用既有构建产物拷入本 worktree 的 `jni/arm64-v8a/`）通过；同 HEAD `:nativedo:testDebugUnitTest` 43 类 153 项全过（0 失败、0 error、0 skipped）。没有改 `apps/android/telegram` 子树（`TopicFragment.openTopicAuthorProfile` 与 profile 文件都在本模块），vendor-guard 不涉及。APK sha256 `9f7f3cb6…` 与设备 `base.apk` 一致；证据在 `.amp/in/artifacts/profile-redesign/`。

- 分类条的手感：分类名字是否可读、颜色是否分得开、选中胶囊是否清楚、分类放不下时横向滑动是否被发现；分类到达前列表先出现再下移的跳变是否能接受。
- 标签菜单：栏上的标签图标是否能被看懂、标签多时弹窗里是否好找、选中的标签在标题里是否一眼可见、清除是否一步到位；分类加标签的组合请求是否返回符合预期的话题。
- 发新帖：手机键盘压在标题行和输入框上时是否可用，标题的 IME「下一项」是否落到正文，发送箭头的出现是否清楚，以及真站点对过短标题的原文拒绝是否显示出来。
- 阅读进度与真实站点：读完话题返回后 `/latest.json` 的 `unread_posts` 是否按实际读到的楼层下降，重开话题是否停在离开的楼层；真实停留时间的上报是前台、楼层在视口内、三分钟内有滚动才计时，读完一篇长帖后站点的阅读时长是否与体感一致。
- 书签：长按菜单的收藏与取消收藏、资料页与设置页的书签列表，在一个已有书签的账号上验证置顶顺序、被删楼层的行、已关闭话题的行。
- 设置页：外观 / 链接 / 我的 / 账号四组的行高、间距与图标底色是否符合上游观感，滚动后标题淡入是否自然；「在应用内打开链接」打开与关闭两种状态下的行外观；退出登录的确认框、清 cookie 后回到登录页、再登录回来后话题与通知是否正常。
- 我的 tab：头像 tab 的图标是否认得出来（会话头像、22dp 圆角方形）、四个 tab 的宽度分配是否匀称；自己的资料页作为根没有返回箭头、下拉展开与折叠的手感是否与从帖子打开的作者资料页一致。
- 通知 tab：没有推送和未读计数时，列表的排序和徽章是否仍然清楚。
- 主页搜索框点按：字段从列表槽位滑进动作栏、标题淡出缩小、搜索胶囊从栏下缘出现的 200/350ms 手感；从收起态（栏上 🔍）打开与从静止态打开是否都顺；关闭时字段落回、标题与分类条回来是否自然；搜索页在玻璃下、键盘弹出时的样子。
- 主页分类横滑：手指拖动中搜索框/分类条是否跟手（本次改成偏移随进度 lerp），松手落点是否无跳变；跨过首/末 tab 时交给根 pager 的边界是否顺手。
- 主页翻页：列表快到底时是否出现聊天气泡行（加载 shimmer），新一页到达后是否直接接上、不重复行；失败时页脚的「没能读到更多话题」是否看得见、点得到（它在最后一行之下，可能要先滑一点）；到最后一页后是否干净地结束。

### 主页四件：搜索框进动作栏、返回不重读、分类滑动跟手、翻页页脚（2026-10-05，`emulator-5554`，`nativedo-home-motion`，**已登出，仅 harness**）

- 四个根因与上游落点（都是 DialogsActivity 12.10.5 的行号）：
  1. **点搜索框**：此前字段留在列表给它的槽位（`getSearchFieldAdditionOffset` 恒为 -4dp），搜索页却从「栏下缘 + 46dp」起算。现在照上游搬：`showSearch`（7459-7700）里 `animatorSearchVisible.setValue(show, true)`（307 行，EASE_OUT_QUINT 350ms）；字段位移是 `ContentView.dispatchDraw` 的 1081-1087——`lerp(scrollYOffset - dp(4), -dp(48), searchAnimationProgress)`，两端值即 `getSearchFieldAdditionOffset`（1624-1626）；栏标题按 `updateStoriesViewAlpha`（1659-1694）的 `1 - clamp(searchAnimationProgress * 2)` 淡出并缩到 0.4（`setUseContainerForTitles`，3172）；栏上条目按 `checkUi_menuItems`（14168-14177）与 `checkUi_itemSearchVisibility`（14119-14128）随 `animatorSearchVisible` 淡出/进入；`checkUi_filterTabsVisible`（13990-14010）让分类条按同一因子淡出缩小，`checkUi_searchFieldStyle`（14058）把字段玻璃交给它。搜索胶囊改在栏下缘（上游 4717 + onLayout 1286-1290：`childTop = actionBar.getMeasuredHeight()`，本屏的容器本来就以栏底为原点，所以 `TABS_TOP_DP` 46→0），搜索页行内边距随之只留胶囊高（`checkUi_searchPagesPaddings` 14014-14026）。字段还补了上游的触摸门（3281-3286：alpha < 0.25 不吃 down）。
  2. **返回不重读**：`onParentBecameVisible` 原先直接 `requestFeed`。上游的聊天列表 `onResume`（7029-7036）与 `onBecomeFullyVisible`（7353）都只更新自己的行、不读服务器，所以这里改成只就地重绑当前页的行；读完的话题由 `TopicReadMarks` 的新监听在打标那一刻把行上的角标去掉——topic 屏的 `leaveTopic` 在它 destroy 时打标，比列表屏 resume 早，若等「回来时重绑」会一直挂着旧角标。保留的读取：首载、失败重试、胶囊点按、标签切换、搜索里选中当前分类；本列表没有下拉刷新（上游聊天列表也没有）。App 长时间回前台也不重读（上游没有等价动作）。
  3. **分类横滑跟手**：上游 `switchToCurrentSelectedMode`（6827）把入页 list 用 `scrollToPositionWithOffset(0, (int) scrollYOffset)` 对齐到当前偏移（它的列表在内存里），此前这里没有对齐，滑完换页时 `dispatchDraw` 从新前页重算 → 一帧跳 48dp。现在在 `ViewPage.setTranslationX`（上游同名覆写 434-450）里按滑动进度把 `scrollYOffset` 从出发偏移 lerp 到入页自己的偏移（`computeScrollYOffset` 与 draw 同一公式；入页未布局时用它 feed 的保存位），滑动中冻结 draw 的重算，`swapPagesAtRest` 与松手结束用新前页的偏移收口，所以落点连续。
  4. **翻页**：`topic_list.more_topics_url` 作为「还有一页」的旗标，路由按同一配方重取、`page` 递增（契约里的 `/latest.json?page=1`、`/c/{slug}/{id}/l/latest.json?page=` 等）；触发是上游 `checkListLoad`（7794-7850）的 `lastVisibleItem >= size - 10`，滑动中/加载中不重复读，追加按 topic id 去重，答案没有 `more_topics_url` 就停并在换页落定时再查一次（上游 3661）。页脚是 DialogsAdapter 自己的加载格（`VIEW_TYPE_FLICKER`，686-697 与 1786-1792）：单格 `FlickerLoadingView(DIALOG_CELL_TYPE)`；失败换成两行「没能读到更多话题／点一下重试」，点它只重读那一页。
- 构建与测试：同一 HEAD `:nativedo:testDebugUnitTest` 全量 **41 类 141 项 0 失败 0 error 0 skipped**（XML 在 `apps/android/nativedo/build/test-results/testDebugUnitTest`，日期新）。新增 `HomeFeedTest` 3 项（返回不重读且角标就地消失；到底只读一次下一页、去重、无 more 即停；失败页脚的 tap 重试）、`HomeCategoryPagerTest.theHeaderFollowsACategorySwipeToTheIncomingPagesOffset`（拖动中偏移单向、单帧不超过总行程一半、中点字段半可见）、`SearchTest.theSearchFieldTravelsIntoTheBarAndComesBack`（开/关两端几何 + 过程帧）。**改写的旧断言**：`MessageBusUiTest.aScreenRefreshClearsTheCount` → `aReturnToTheListDoesNotRefreshItOrClearTheCount`（旧断言钉「返回会重读并清零」，现在钉「不重读、计数仍属读者」）；`ReadProgressTest.readingATopicReportsTheFloorsReachedAndTheListFollowsTheSite` 的返回段（旧断言等「list to be read again」并读 feed 模型上的 unread，现在钉「head 不再读」+ 行上角标为 0，行用反射读 `DialogCell.customDialog`）；`TopicCategoriesTest` 与 `TopicTagsTest` 的请求计数改为只数 `page=0`（新增的页请求命中同一路径，与「表头只读一次」的原意无关）。arm64 `:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过（排除两个 CMake 任务；APK 里一份 `lib/arm64-v8a/libtmessages.49.so`），APK SHA-256 `5814b76a6cccc6de9d5ece07228294be43a3915da7bc07944f72920757af2e0a`（63.5 MB）。没有改 `apps/android/telegram` 子树，vendor-guard 不涉及。
- 设备：`emulator-5554` 仍是登出状态（冷启动停在站点「登录 linux.do」页，截图 `device-01-signed-out-login-page.png`），按约定没有登录、没有绕过，本片是 **harness-only**。
- Harness 证据（`.amp/in/artifacts/home-motion/`，全部经 `view_media` 逐张核对）：`search-field-mid-open.png` 是点按后的早期过程帧（标题在淡出、搜索胶囊开始出现、列表在淡出，字段尚在原槽——该帧的位移随绘制发生，完整过程由测试的逐帧断言钉住）、`search-field-open.png` 结束帧（字段在栏内、标题消失、胶囊紧贴栏下缘且行在其下）；`search-field-mid-close.png` / `search-field-closed.png` 是关闭过程与回到静止（标题、分类条、行都回来）；`home-motion-category-swipe-mid.png` 是分类横滑中帧（两页同时在屏、分类条处在中间位置）；`home-motion-more-loading.png` 是底部加载 shimmer（最后一行 topic #29 之下）、`home-motion-more-failed.png` 是失败页脚「没能读到更多话题／点一下重试」（需在页脚出现后再滑一点才露出，图里在底栏之上）、`home-motion-more-end.png` 是站点说没有下一页后干净结束；`home-return-after-topic.png` 是读完返回后首行角标已去掉。
- 设备上未验证：搜索框进栏的真机动画与键盘、标题淡出的手感；分类横滑的跟手与玻璃下的样子；翻页 footer 的真实触摸与网络失败（本机登出，没有真实列表可点）；页脚出现在折叠下方时读者是否一定会滑到。真机上的这些按上面的「真机验证清单」条目补。
- 玻璃胶囊的模糊（真机，高档机）：打开任一话题，滚动到正文/图片从三个胶囊下穿过 —— 胶囊里应是糊化的内容（正文糊成灰块、图片糊成色块），不是一色不透明的白；夜间同理。若真机上仍是纯色，`adb logcat -s tmessages:D | grep "nativedo runtime config"` 会给出该机的性能档与 `chat blur`（上游在 LOW 档、以及 AVERAGE 档的默认 preset 里本来就不开模糊，见下）。

### 浮层的模糊路径：chat header 的 frosted 源与启动时的性能档（2026-10-05，`emulator-5554`，`nativedo-glass-blur`，**已登出，探针 + 日志**）

- 症状：真机（Xiaomi `24129PN74C` / `449dd25d`，API 35/36）上话题页三个胶囊不呈现 Telegram chat header 的半透明模糊玻璃，像不透明白色。此前各片只在 1 核模拟器（性能档 LOW）验证：那台机器 `SharedConfig.chatBlurEnabled()` 本来就是假，上游自己就走纯色回退（`BlurredBackgroundProviderImpl.topPanelChatActivity` 169-188 的 `checkBlurEnabled` 分支），所以**模糊分支从未出过像素**，只按代码形状读过。
- 先证明模糊分支本身能出像素：上游自己的 debug 覆盖是 `SharedConfig.overrideDevicePerformanceClass`（`SettingsActivity` 的 "Force performance class"，SharedConfig.java:1189-1195），chat 模糊开关是 `LiteMode.FLAG_CHAT_BLUR`（LiteMode.java:41）。本 worktree 在 `src/debug` 放了探针 `GlassProbeActivity`（不进 release，见下），它按话题页的接法搭同一套：`SizeNotifierFrameLayout.dispatchDraw` 里 `GlassPanelHost.record()`、聊天壁纸 `Theme.getCachedWallpaper()`（`setBackgroundImage`）、`ActionBar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanelChatActivity)`，内容为主贴卡（白卡 + 深色正文）盖在绿壁纸上。设备命令与结果：
  - `adb shell am start -n io.github.baba983.nativedo/io.github.baba983.nativedo.app.GlassProbeActivity --ez forcePerfClass true` → `GlassProbe: perfClass=2 chatBlurEnabled=true hasSource=true`，`08-probe-forced-light`（日间：胶囊里是白卡与正文的糊化、缝隙透绿）`09-probe-forced-dark`（夜间：深色胶囊透出下方内容，`dark=true`）；
  - 同屏不带覆盖（性能档 LOW）→ `perfClass=0 chatBlurEnabled=false hasSource=false`，`10-probe-fallback-low`：胶囊是纯色不透明（上游 LOW 回退原样）；`12-capsule-crops` 是两档 + 夜间的胶囊裁切对比。`02`/`05`/`06` 是过程记录（8dp 与 40dp 的对比）。
- 根因两处：
  1. **性能档与 LiteMode 从来没被显式加载**。上游的入口是 `ApplicationLoader.postInitApplication()` 里的 `SharedConfig.loadConfig()`（ApplicationLoader.java:247），我们的 `NativeDoApplication` 不跑 postInit（那会起 tgnet/存储/服务，hook 只是让它们不做事），只依赖 `SharedConfig` 静态初始化里那一次 `loadConfig()`（SharedConfig.java:511-513）。一旦这个静态初始化发生在 `applicationContext` 就绪之前（`Application.onCreate` 之前碰 `SharedConfig`/`LiteMode`/`AndroidUtilities` 的任何 provider 或 OEM 钩子），`loadConfig()` 因为 context 为空直接 return 且**再无人重试**：`overrideDevicePerformanceClass`/`devicePerformanceClass` 停在字段默认值 0，而 `getDevicePerformanceClass()` 读到的 0 正是 `PERFORMANCE_CLASS_LOW`（不该测也不测），`LiteMode.loadPreference()`（LiteMode.java:202-215）又按同一档算 preset —— 一台旗舰机于是永久走不透明分支，屏幕上与日志里都没有提示。修法：`NativeDoApplication.loadRuntimeConfig()`（onCreate 里调用）把上游那两件事按顺序补上——`SharedConfig.loadConfig()` 再 `LiteMode.loadPreference()`——并打一行 `FileLog`：`nativedo runtime config: performance class = …, measured = …, chat blur = …`（tag `tmessages`）。设备验过：真实状态冷启动是 `performance class = LOW, measured = LOW, chat blur = false`，写入 override 后同一行变成 `performance class = HIGH, measured = LOW, chat blur = true`。
  2. **录制的模糊不是 chat header 那一档**。`GlassPanelHost` 原来 `setBlur(dp(8))`（那是 `PeerStoriesView` 输入框那种小玻璃的档位，`BlurredBackgroundSourceRenderNode.setBlur` 只挂一层 blur）；ChatActivity 的 chat header 默认（非液态玻璃）用的是 `DownscaleScrollableNoiseSuppressor` 的 frosted `SourcePart`：`dpf2(40)` + 饱和度 ×3（DownscaleScrollableNoiseSuppressor.java:407-426），`DialogsActivity` 的列表条同源。修法：`GlassPanelHost.java:74-84` 改成 `node.setBlur(AndroidUtilities.dpf2(40), RenderNodeEffects.getSaturationX3RenderEffect())`，常量与那两处一致。
- 测试：新增 `GlassGateTest`（3 项）：LOW 档下 header 的玻璃 drawable 采样 `BlurredBackgroundSourceColor`、`overrideDevicePerformanceClass(HIGH)` + `LiteMode.PRESET_HIGH` 后采样 `BlurredBackgroundSourceRenderNode`、以及把 `SharedConfig`/`LiteMode` 反射置回「context 之前初始化过」的现场后 `loadRuntimeConfig()` 把档位与 `chatBlurEnabled()` 修回来（修复前这一项会红：字段停在 0）。全量 `:nativedo:testDebugUnitTest` 139 项 0 失败（`NewTopicTest` 全量跑中一次既有 flaky 命中，单独重跑 3/3 过）。
- 构建与设备：arm64 `:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过（本 worktree 从主 worktree 拷入 `TMessagesProj_Modules/media`、`lib/jlatexmath` 与同 commit 的 arm64 `libtmessages.49.so`，排除两个 CMake 任务），APK SHA-256 `f47d0265d74c37742a32db5f18861d751264b8bfee230f43d1f5cde643ffa2f8`；覆盖安装 `-r -t`，未卸载未清数据；冷启动仍停在登录页（`11-app-coldstart-login`、`NativeDoSession: auth_cookie_missing accepted=false`），启动日志正常；探针用完把 `overrideDevicePerformanceClass`/`lite_mode6` 从 `mainconfig` 清掉、主题回到 Blue（`10`/`11` 的截图即清理后的状态）。没有改 `apps/android/telegram` 子树，vendor-guard 不涉及。证据在 `.amp/in/artifacts/glass-blur/`。
- 未验证：真机（`449dd25d` 未连接）上的最终观感 —— 登出状态下打不开真话题页，探针验的是同一套 `GlassPanelHost` + `ActionBar.setupGlass` 接线，不是真话题页的像素；真机请按上面清单看一眼话题页胶囊与夜间。
- 预测返回：在话题里从左缘慢拉时，话题页是否跟手（右移约 56dp、轻微缩放，下一层列表在左侧露出），松手是否滑出回到列表、中途取消是否回弹；搜索打开、图片查看器（PhotoViewer）、内置浏览器（ArticleViewer / 浏览器底部 tab）、文本选择这些会先拿走 back 的界面上，起始手势都不应启动页面动画；根页面（首页第一个 tab、登录页）不是页面动画，而是交给系统 back-to-home 退出。

### 预测返回：话题→列表跟手滑出，根与 tab 的边界（2026-10-05，`emulator-5554`，`nativedo-predictive-back`，**已登出**）

- 上游 12.10.5 的预测返回分两层。`LaunchActivity`（735-849）在 API 34+ 注册 `OnBackAnimationCallback`：`onBackStarted` 只记状态；`onBackProgressed` 在首个大于 `LAZY_START = 0.015f` 的进度上做 `onBackStartedInternal`——先问 `onBackPressed(false)`（浏览器底部 tab、PhotoViewer、ArticleViewer 等），再 `actionBarLayout.onBackStarted(touchX, touchY)`，布局接管就把 `AnimationNotificationsLocker` 锁上，之后把扣掉 `LAZY_START` 的进度交给 `actionBarLayout.onBackProgress`；`onBackCancelled`/`onBackInvoked` 解锁并转给布局；API 33 只有普通 `OnBackInvokedCallback`；`onDestroy`（6886-6894）反注册。`onBackPressed(boolean)`（8322-8355）就是那份消费者清单，`invoked=false` 只回答不做事。`needCloseLastFragment`（8695-8714）在栈只剩一个 fragment 时直接 `finish()`、返回 false。跟手与缩放全在 `ActionBarLayout`：`onBackStarted`（1524，会自己挡掉动画中、跟手中、栈 ≤1、预览、有 sheet、当前 fragment 回答 `false` 或 `canBeginSlide()` 为 false）、`onBackProgress`（1566，`dp(56)` 的位移与 `innerTranslationX` 的缩放绘制）、`onBackCancelled`（1574）、`onBackInvoked`（1580）、`animateBackEndAnimation`（1595）。
- 本客户端按同一形状移植到 `NativeDoActivity`：API 34/33 两分支、`LAZY_START`、`started/invoked/predictiveBackStarted` 状态、`onBackStartedInternal`、`AnimationNotificationsLocker`、`onDestroy` 反注册都在（上游 6891 的 API-33 分支检查的是错字段，这里检查真正注册的那个，代码里有注释）；没有平板分支，因为这里只有一个 `actionBarLayout`；`onBackPressed(boolean)` 的消费者换成这个客户端有的——浏览器 `BottomSheetTabsOverlay.isOpen`、`ContentPreviewViewer`、`PhotoViewer`、`ArticleViewer`，顺序与上游一致（Telegram 专有的 passcode、SecretMediaViewer、调试抽屉没有）；`actionBarLayout.setDelegate(this)` 只为 `needCloseLastFragment`：栈 ≤1 时 `finish()`，让根上的 back 走系统自己的结束/back-to-home，而不是弹出一个空布局。`NativeDoMainTabsActivity` 补上 `MainTabsActivity.onBackPressed(boolean)`（781-793）：不在第一页时只在提交时回到第一页。ActionBar 的返回箭头、API<33 的硬件 back、三键导航都还走原来的 `onBackPressed()`（现在先过 `onBackPressed(true)` 再进 `actionBarLayout`）。
- 「谁会先拿走 back」逐项对过：打开中的 sheet/dialog 与 `canBeginSlide`（`ActionBarLayout.onBackStarted` 1524-1552 自己的检查）、`ContentPreviewViewer` / PhotoViewer / ArticleViewer（活动层的 `onBackPressed(boolean)`）、浏览器底部 tab overlay（同前）、帖子菜单的 `ItemOptions` 弹窗（它是可聚焦的 `ActionBarPopupWindow`，back 在弹窗自己的 `dispatchKeyEvent` 里被消费：`ItemOptions` 272-278 的 `KEYCODE_BACK` → dismiss、`ActionBarPopupWindow` 442-444 与 1381 的 `setFocusable`，动不到活动层）、文本选择（`TopicFragment.onBackPressed(false)` 回答 false）、首页搜索（`TopicListFragment.onBackPressed` 回答 false）——起始手势都不会启动页面 shrink；键盘在 `ActionBarLayout.onBackStarted` 里按上游 `hideKeyboard`。登录页 `LoginWebViewFragment.canBeginSlide()` 也已按 WebView 历史回答。
- 测试：新增 `PredictiveBackTest.kt` 5 项，跑在 Robolectric sdk=35（`robolectric.properties`），从窗口真正的 dispatcher 取回注册的 `OnBackAnimationCallback`（窗口 attach 前在 proxy 的 `mCallbacks`，attach 后取 `mActualDispatcher.getTopCallback()`），用框架构造器造 `BackEvent` 驱动：话题跟手且提交恰好弹一个 fragment、取消回弹且栈不变、根不启动动画且提交 `finish()`、非第一 tab 只在提交时回第一页、搜索打开时起始手势既不启动动画也不关搜索。全量 `:nativedo:testDebugUnitTest` 41 类 141 项 **0 失败 0 error 0 skipped**（此前两次全量里 `AvatarRenderTest` 的「等头像文件下发」超时；把两个主文件回退到 `609d1040` 后单独重跑同样超时、最终一次全量通过，因此判为既有 flaky，与本改动无关）。`assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过，APK SHA-256 `01039874a5ece48361ccdfe1bb132230b09a5e73f2e1853b9809dbded7983936`（arm64 一份 `libtmessages.49.so`）。
- 设备：登出状态可达的只有登录页与根。装的是 `1370b8e1…`（最终构建只多一条 `ContentPreviewViewer` 的 back 守卫，行为相同）。干净的一次边缘滑动里，`CoreBackPreview.startBackNavigation` 的 `callbackInfo` 是 `mPriority=0, mIsAnimationCallback=true`（正是登记上去的动画回调），`ShellBackPreview` 收到 `mAnimationCallback=true` 的 `TYPE_CALLBACK`；松手 `Trigger back without dispatching to animator`（登录页单 fragment 没有页面可动，与上游在根上的行为一致）→ `LAUNCHER_ONRESUME`，`needCloseLastFragment` 的 finish 让系统做 back-to-home，窗口回调从默认 -1 置空（反注册生效）。截图 `device-02-login.png`、`device-08-focused.png`。并行 glass 代理的锁超过 10 分钟未释放，且 13:33/13:38 两次把同包重装回它自己的 probe（`Killing ... due to installPackageLI`），第二次录屏因此作废并删除；原始证据与说明在 `.amp/in/artifacts/predictive-back/`（`device-notes.md`、`apk-sha256.txt`、`device-01..04` / `device-07` / `device-08`）。
- 未验证：话题→列表的真机跟手与滑出（要登录；按约定保持登出，harness 的 `predictive-back-mid-gesture.png` / `predictive-back-after-pop.png` 是同一路径的渲染）；真机取消手势的回弹逐帧；真机颜色/模糊。搜索、查看器、sheet 的「起始手势不启动动画」由 harness 用例与 `ActionBarLayout` 上游检查兜底。

### 主页「N 个新话题」胶囊与列表加载骨架（2026-10-05，`emulator-5554`，`spike/full-telegram-subtree`，**已登出，仅 harness**）

- 胶囊从 `ButtonWithCounterView` 换成聊天的浮动日期视图：`ChatActionCell` + `setCustomText`，画法与
  ChatActivity:7043-7100 的 `floatingDateView` 相同（服务消息背景 `key_paint_chatActionBackground`、文本
  `key_paint_chatActionText`、`isFloating()` 的 0.75 系数；没有聊天壁纸时上游就用这套纯色，白天是深灰底白字、
  夜间是深色底白字，harness 两张图都验过可读）。显示/隐藏照 ChatActivity:13600-13660：150ms alpha、tag 守卫；
  上游 hide 里的 `hideDateDelay` 是滚动日期逻辑，这里没有滚动日期，胶囊不延迟。位置照上游 11968 抵消 cell
  自带的 4dp 顶内边距，停在分类条下缘 8dp；它漂浮在第一行之上，`checkUi_listPadding` 不再为它留槽位
  （出现/消失不移动第一行），搜索页打开时隐藏，点按仍是刷新当前分类页并回顶。
- 主页分类页首载骨架按 DialogsActivity:4059-4064：每页 `FlickerLoadingView(DIALOG_CELL_TYPE)` 作列表的
  empty view（4695、9300-9306），骨架只在页没有 feed 时出现——切回已缓存的分类、搜索返回都不会再出现；
  读取失败时骨架让位给原来的失败文案。搜索页的五个 tab 各按上游自己搜索的 shimmer 类型
  （DialogsSearchAdapter:1628-1629 的 `DIALOG_TYPE`；用户页 SearchAdapter:375-377 的 `PROFILE_SEARCH_CELL`；
  分类/标签是上游全局搜索列表里的 hashtag 行，同一列表用 `DIALOG_TYPE`）。通知列表与「我的」动态列表的行
  都是 `DialogCell`，用 `DIALOG_CELL_TYPE`，只在第一页到达前显示。话题页首载不是骨架，按
  ChatActivity:6984-7016 居中画服务底色圆 + `RadialProgressView`（32dp、size 28dp、`key_chat_serviceText`），
  出现/消失用 ChatActivity:11866-11904 的 150ms alpha/scale。
- 主题：胶囊、骨架、话题页的圆/环都在原地重画（共享服务画笔与 `FlickerLoadingView` 的 onDraw 读色），
  主题用例额外钉了「切换前后是同一个 pill / 同一个 shimmer 实例」。
- 构建与测试：同一 HEAD `:nativedo:testDebugUnitTest` 全量 **40 类 136 项 0 失败 0 error 0 skipped**（新增
  `MessageBusUiTest.thePillFloatsOverTheFirstRowAndTheThemeSwitchKeepsIt`、
  `HomeCategoryPagerTest.theFirstLoadShimmerOnlyStandsInForAPageWithNoFeedYet`、
  `SearchTest.theSearchPagesShimmerWhileTheSiteIsAsked`、`TopicScreensTest.theTopicsFirstPageShowsTheChatsOwnLoadingRing`；
  切 tab 中帧的「两页在屏」改按页框计数——无 feed 的页列表被自己的骨架临时隐藏，是上游 empty view 的原样）。
  arm64 `:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过（`.so` 取同上游 commit 的
  设备 worktree 产物，排除 `configureCMakeDebug[arm64-v8a]` 与 `buildCMakeDebug[arm64-v8a][tmessages.49]`，
  并清掉 worktree 里残留的旧 CMake 输出以免打包出两份 `.so`），APK SHA-256
  `fcd006b6b341ba0f453daa3c78938a33b0a97cfac29d4af606605dab70840bc3`（63.5 MB，一份 `libmessages.49.so`）。
  没有改 `apps/android/telegram` 子树，vendor-guard 不涉及。
- 设备：冷启动一次就停在登录页（`device-cold-start-check2.png`，logcat `auth_cookie_missing accepted=false`），
  按约定没有登录/绕过，所以本片是 **harness-only**。证据在 `.amp/in/artifacts/pill-skeleton/`：
  `pill-light` / `pill-dark`（胶囊在分类条下、第一行之上，第一行位置未被推动；dark 图顶部的白条是揭示快照，
  和既有的 `home-category-theme-dark` 渲染一致）、`home-first-load-skeleton`（DIALOG_CELL 骨架行从分类条下开始）、
  `search-topics-loading` / `search-users-loading`、`topic-first-load-ring`（服务圆 + 白色弧）。
  未验证：真机颜色与模糊（本机 LOW 档）、点按胶囊的真机触感、骨架期间列表的滚动手感。

### 主页随滚动收起：搜索框滑进动作栏、分类条顶上（2026-10-04，`emulator-5554`，`nativedo-home-scroll`，已登录 @example_user）

- 上游 12.10.5 的手感是：动作栏不动，搜索框（48dp）随滚动上移并在同一距离里淡出，分类条跟着滑到
  动作栏正下方，滚动停下时按半个搜索框的高度吸附到全开或全收。这不是看来的：`DialogsActivity` 的
  `onScrolled` 里 `applyScrollY` 被写死成 `false`（4571），真正的偏移在 `ContentView.dispatchDraw` 里由列表自己
  算出来——`visiblePartAfterScroll = paddingTop - 第一行.getY()`，夹在 `[-48dp, 0]`（957-1006）；`setScrollY`
  （8918-8940）只做记账，`checkUi_searchFieldVisibility`（14036-14056）按这个值淡出搜索框并把动作栏里的搜索图标
  交出来（`alpha <= 0.01` → `animatorSearchButtonVisible`，14054 + 14119-14128），`updateContextViewPosition`
  （6560-6596）让分类条跟同一偏移减去 4dp×搜索框透明度，`getActionBarTop/getActionBarFullHeight`（862-886）让
  顶部的玻璃矩形和列表的裁剪线一起从「栏+框」收到「只剩栏」，停下的滚动在
  `checkAutoscrollToStories`（5760-5808）里用 `RecyclerListViewScroller.smoothScrollBy` 吸附（阈值 24dp），
  动作栏自身的阴影是 `animatorShadowVisible` + `drawHeaderShadow`（313、4605-4606、14284-14314）。
- 移植（`TopicListFragment`，未改 `apps/android/telegram` 子树）：动作栏改成这一屏自己的浮动栏
  （`setAddToContainer(false)`/`setCastShadows(false)`/`setClipContent(true)`，3527-3529；玻璃交给 frame 画，
  5425），frame 换成 `SizeNotifierFrameLayout` 并在 draw pass 里做上面那套；列表 padding 照上游
  （栏高 + 48 + 36+14 − 5）、裁到表头下缘；分类条与搜索框的布局也照上游（onLayout 1148-1151）。玻璃在 LOW 档
  是上游自己的纯色回退（`drawBlurRect` 930-950 的第一条分支），高档走同一处录制的模糊源。
- 设备检查（覆盖安装 `-r -t`，未卸载未清数据，仍登录 @example_user；主体是一片 APK SHA-256 `0770d5af…` 与设备 `base.apk` 一致，
  收尾的 null 守卫重打包成 `e2a375fd…` 并覆盖安装后复验了静止与收起两张 `56-rest-final-apk`、`57-collapsed-final-apk`）：
  静止浅色 `42-after-retry`；按住半收（`dumpsys` 第一行 421/392，滚动 34/63px）`26-held-small`、`27-mid-collapse`
  ——搜索框上移并半透明、分类条同步上移；松开在阈值上 → 吸附到全收（`dumpsys` 329 = 126px）`28-snap-to-collapsed`；
  全收 `11-collapsed-fixed`/`44-collapsed-fixed2`（搜索框消失、动作栏出现 🔍、分类条顶在栏下缘、行的裁剪线
  像素级确认在 y=210 = 栏高）；从收起态点动作栏 🔍 进搜索页 `45-search-from-collapsed-fixed2`，返回键两次
  回主页仍是收起态 `47-back-to-collapsed`；夜间 `53-dark-rest-rows`/`54-dark-collapsed-rows`，验后切回浅色
  `55-light-restored`。分类条选中、标签菜单、发帖 FAB 与底部四栏均未受影响（同一 APK 上点过一遍）。
- 设备上改掉的三处（`fdc1c0e7`）：frame 才是画这一步的 view，列表自己滚不会重画它，`onScrolled` 必须
  `fragmentView.invalidate()`（上游 4523-4524 就有）；搜索打开时不能再按列表位置重算偏移（一次 feed 重载会把
  搜索框顶到状态栏后面）；搜索页自己的布局从「栏下缘」起算，所以它的容器要带栏高的上边距。
- 未验证：真机模糊／液态玻璃（本机仍是 LOW 档，`chatBlurEnabled()` 假，只看得到上游的纯色回退）；`screenrecord`
  在这台模拟器上冻结（两次尝试，70 帧同哈希），滚动过程改用按住拖拽的截图 + `dumpsys` 行坐标记录，吸附用
  松开前后的坐标差证明；高档机上 frame 玻璃走的是同一处录制源，没有第二个几何分支。
- 构建与测试：`:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过；同 HEAD
  `:nativedo:testDebugUnitTest` 33 类 98 项全过（0 失败、0 error、0 skipped；全量跑中一次 `ReadProgressTest`
  既有 flaky 命中，单独重跑 3/3 过）。没有改 `apps/android/telegram` 子树，vendor-guard 不涉及。证据在
  `.amp/in/artifacts/home-scroll/`。

### 浮层下的列表：帖子与行穿过胶囊/分类条（2026-10-04，`emulator-5554`，`nativedo-glass-under`，已登录 @example_user）

- 症状两处、根因两个。其一：列表默认 `clipToPadding`，子行在 padding 的边界被裁成一条直线——话题页帖子在胶囊下缘、
  主页行在分类条下缘（`ChatActivity:6421`、`DialogsActivity:4070` 都关掉它），两处列表照做（`6995895a`）。其二：
  `GlassPanelHost` 原来录的是「白底 + 列表」且 API≥31 就建源；现在按上游的条件只在 `SharedConfig.chatBlurEnabled()`
  为真时建源（否则退回 `iBlur3SourceColor` 纯色，即上游 LOW 档的等价物），录制内容 = 壁纸视图（`ChatActivity:17239-17241`
  的壁纸链，话题页传入 `contentView.backgroundView`）+ 列表（`6cc5574c`）。
- 设备检查（覆盖安装 `-r -t`，未卸载未清数据，仍登录 @example_user；APK SHA-256 `04422cde…` 与设备 `base.apk` 一致）：
  - 话题页浅色：滚动到帖子中部后，帖子的标题行/正文/卡片从状态栏后和三个胶囊下穿过，胶囊之间的缺口里能看到同一
    行文字，胶囊内被不透明填充盖住（`after/scan-4`、`after/after-header-topic-gaps`）；静止时首帖仍在胶囊之下
    （top padding 照旧，`after/after-header-topic-rest`）。修复前同一位置是硬切线：表情条被拦腰截断、缺口里只有
    壁纸（`before/03-topic-scrolled3-before`、`before/before-header-topic`）。
  - 主页浅色：滚动后行连续地从搜索框/分类条下穿过，行在条左端的缺口处露出且半行不再被切（`after/after-header-home`；
    修复前 `before/05-home-scrolled-before`、`before/before-header-home`）。
  - 夜间：话题页状态栏后是帖子里的照片（`after/after-header-topic-dark`），主页行照旧穿过搜索框与分类条
    （`after/after-header-home-dark`）；验证后切回浅色（`after/41-home-light-restored`、`after/42-home-light-retry`）。
  - 不回归：长按菜单五项（`after/08-longpress-menu`）、选择文本 + Copy 浮层 + 两个句柄（`after/09-text-selection`、
    `after/12-top-text-selection`）、左滑回复带 `@ErJi` 与附件条（`after/13-swipe-reply`，随后清空输入框）、帖子图片
    进 PhotoViewer（`after/35-photoviewer-dark`）；crash buffer 为空，无 FATAL/ANR。
- 这台模拟器仍是 LOW 档（logcat `selected_class = 0`，cpu_count=1，API 36；`lite_mode6` 未写 → `PRESET_LOW`，无
  `FLAG_CHAT_BLUR`，`SharedConfig.chatBlurEnabled()` 为假）：上游在这档上胶囊是不透明的 `key_chat_topPanelBackground`
  （夜间 `key_actionBarDefault`）、分类条的源只是纯色，所以本机看不到模糊与液态玻璃，也没法用肉眼验证「壁纸 + 帖子」
  的录制内容；该路径只在 `chatBlurEnabled()` 为真的高档机型（provider 取 0.76 透明分支）上显示。真机未验证。
- 构建与测试：`:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过（本 worktree 从设备 worktree
  拷入 `TMessagesProj_Modules/media`、`lib/jlatexmath` 与同 commit 的 arm64 `libtmessages.49.so`，排除两个 CMake
  任务）；`:nativedo:testDebugUnitTest` 33 类 98 项全过（0 失败、0 error、0 skipped）。没有改 `apps/android/telegram`
  子树，vendor-guard 不涉及。证据在 `.amp/in/artifacts/glass-under/`。

### 话题胶囊栏：chat header 的玻璃胶囊（2026-10-04，`emulator-5554`，`nativedo-topic-capsules`，已登录 @example_user）

- 话题页的动作栏改成 12.10.5 聊天的 header：`ActionBar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanelChatActivity(...))`
  画左（返回）、中、右（菜单）三个胶囊（`ActionBar.java` 210-255、2219-2270），栏本身 `setAddToContainer(false)`、
  透明、`BackDrawable`（`ChatActivity` 3678-3686），加在本屏自己的 content view 上浮在帖列表之上；列表顶部留栏的
  实测高度 + 4dp（`ChatActivity` 11947-11952），帖子从胶囊下穿过。中胶囊是 `ChatAvatarContainer` 的 glass 模式
  （`ChatActivity` 4134-4162、4224）：标题、副标题「分类 · N 回复」（放得下才带回复数）、以及楼主头像（与行同一套
  `DiscourseAvatars` 合成用户 + 按路径交给 ImageReceiver）；点中胶囊开楼主的资料页。右胶囊是聊天那种 overflow
  （`ic_ab_other`，`ChatActivity` 4281），里面 书签 / 复制链接 / 在浏览器打开 三项（上游图标 `msg_fave` / `msg_link` /
  `msg_openin`）。话题内搜索没有容易的现成路径，这次没做。
- 设备检查（覆盖安装 `-r -t`，未卸载未清数据，仍登录 @example_user；APK SHA-256 `8122c3f7…` 与设备 `base.apk` 一致）：
  - 顶部与滚动：三个胶囊各自独立（左圆、中胶囊、右圆），标题按上游的渐变在胶囊右缘截断；滚动时帖子卡片从胶囊下
    穿过并被胶囊盖住（`03-topic-top`、`04-topic-scrolled`）。
  - 菜单：`⋮` 打开 书签 / 复制链接 / 在浏览器打开（`06-menu-open`）。书签点一下变成「取消书签」（站点
    `PUT /t/2981926/bookmark.json`，写前先取 `/session/csrf`，两次写都 200，见 `logcat-session.txt`），再点一次
    恢复「书签」，账号状态改回原样（`07-menu-bookmarked`、`08-menu-unbookmarked`）。复制链接把
    `https://linux.do/t/topic/2981926` 放进剪贴板，系统剪贴板浮层确认（`09-copy-link`）。在浏览器打开进的是内置
    浏览器（`ArticleViewer` 的 X + 标题/URL + 展开箭头 + 菜单），页面已登录渲染（`10-browser`）。
  - 中胶囊点按：打开了楼主「狐狸汉克 / @foxhank」的资料页（`05-op-profile-from-capsule`），与点帖子头像同一个屏幕。
  - 不回归：长按帖子菜单五项照旧（`19-longpress-menu`）；左滑开回复框并带上 `@hotowg`，附件条同时出现
    （`20-swipe-reply`）；帖子里的图仍进 `PhotoViewer`（`31-photoviewer-dark`）；输入框聚焦、光标在（`11`/`12`）。
  - 夜间：设置 → 外观 → Switch to Night Mode 后，三个胶囊在深色底上清楚可辨（深色玻璃、白色图标与标题），列表照旧
    从胶囊下穿过（`28-topic-dark`、`29-topic-dark-scrolled`）；验证后切回白天（`32-light-restored`）。
- **这台模拟器是 LOW perf 档**：`SharedConfig.chatBlurEnabled()` 为假，provider 走上游自己的无模糊分支
  （`BlurredBackgroundProviderImpl.topPanelChatActivity` 172-175），胶囊是不透明的 `key_chat_topPanelBackground`
  （夜间 `key_actionBarDefault`）；像素采样确认白天填充是纯白、描边也是白，所以白底列表上胶囊边界主要靠投影与
  背景内容衬托，视觉判读在「看得到/看不到轮廓」之间摇摆——这是上游无模糊分支的原样，不是本片改出来的差异。
  真机上带模糊/液态玻璃的外观本片未验证。
- 构建与测试：`:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过（本 worktree 的
  `TMessagesProj_Modules/media`、`lib/jlatexmath` 从设备 worktree 拷入，arm64 `libtmessages.49.so` 用设备 worktree
  的构建产物、排除两个 CMake 任务）；同 HEAD `:nativedo:testDebugUnitTest` 32 类 94 项全过（0 失败、0 error、
  0 skipped）。没有改 `apps/android/telegram` 子树，vendor-guard 不涉及。
- 未验证/观察：真机模糊与液态玻璃（本机选不到）；夜间下只看了胶囊与列表的对比度，没有重跑菜单与回复；logcat 的
  crash buffer 为空，唯一一条 ANR 记录（16:36）早于本次安装（16:50）。证据在 `.amp/in/artifacts/topic-capsules/`。

### 一个 cookie jar（2026-10-04，`emulator-5554`，`nativedo-cookie-sync`）

- 模型：`android.webkit.CookieManager` 是唯一的 cookie 存储（登录 WebView、内置浏览器、OkHttp 三方共用）。
  OkHttp 侧由网络拦截器在每次请求读 `getCookie(url)`、把响应的 `Set-Cookie` 原文写回（SameSite 等属性保真），
  只对 linux.do 及其子域读写。旧的 `nativedo.cookie_store` 快照只做一次性迁移（jar 为空才导入）后删除；
  本机快照为空（`legacy_cookie_migration accepted=false`）。两 jar 互抄、互相轮换的实现（上一片）全部删除。
- 设备状态与恢复：改前按约定备份了 WebView `Cookies` DB 与 `nativedo.*` 到 `.amp/in/session-backup/`。
  设备上原生 SessionStore 为空、WebView jar 持有 `_t`；覆盖安装后冷启动，第一条请求就是
  `route=session status=200`（`hasToken=true`），设置页显示「已登录 @example_user」（截图 `01`/`02`）——浏览器留下的会话
  被原生验证并采纳，这正是本次要修的正常冷启动路径。
- `_t` 轮换：13:43:19 冷启动第一条请求把浏览器那份 token 轮换走（`setsToken=true`），13:53:17 原生读帖时
  再次轮换（`revision=1 setsToken=true`）；两次之后紧接着进内置浏览器，站点页头都是头像、无「登录」
  （截图 `09`/`18`）。全程 logcat `expired_*` 0 次、crash buffer 为空；浏览器↔应用往返两次、强停后冷启动两次
  都仍是登录态（`16`/`17`/`19`）。
- 写路径：在原生帖子里点了一个 reaction chip，请求先拿到 `route=csrf status=200`，写请求被一次
  `403 evidence=Challenge` 挡下并按契约保留会话，重试 200；没有登出（chip 的选中态截图上未能确认）。
- 测试与构建：`:nativedo:testDebugUnitTest` 32 类 94 项全过（0 失败、0 error、0 skipped；新增
  `SharedCookieJarTest` 7 项，固定冷启动恢复、在途轮换不覆盖、陈旧登出形答复只失败当条请求、
  真登出只清 linux.do、迁移不覆盖浏览器 jar、跨站隔离）。`assembleDebug -Pandroid.injected.build.abi=arm64-v8a`
  通过（该 worktree 的 jni 子模块为空，用 `jni/arm64-v8a/` 预编译 `.so`、排除 CMake 任务，与上一片同做法），
  APK SHA-256 `f4d0f829328b8a6a311608f84c698a2291a8e0c776ca069c6e7e6e813bb61442`。本片没有改
  `apps/android/telegram` 子树，vendor-guard 不涉及。
- 历史一行：上一片（`8eefe051`）两个 jar 各自轮换 `_t`，原生请求带着滑出窗口的 token 被站点判为登出，
  耗掉了设备上唯一的登录（`expired_ServerLoggedOut`）。证据在 `.amp/in/artifacts/cookie-single-jar/`
  （截图 `01`–`19`、`logcat-session.txt`）。
- 交还与终态：14:09 另一个任务（`nativedo-settings-tab`，仍带旧两 jar 代码、基于 `cdcc23ad`）在同一
  app data 上安装并运行了它的构建；14:21 本片构建冷启动时站点已对 jar 里的 `_t` 回答
  `404 evidence=LoggedOut deletesToken=true`，会话被并行的第二份客户端消耗。本构建按契约清掉 linux.do
  cookie 与本地身份、停在登录页（截图 `20`），crash buffer 为空；按约定本片不自行登录。

### 主页玻璃分类条与搜索页（2026-10-04，`emulator-5554`，`nativedo-home-search`）

- 分类条改成 12.10.5 自己的浮动玻璃胶囊：`FilterTabsView` 以 `BlurredBackgroundProviderImpl.topPanel` 的模糊背景、圆角 18、内边距 6.666、两边 4dp 边距浮在列表上；列表把搜索框（48dp）加胶囊（50dp）的高度留成 padding，所以首行在胶囊下方、滚动时行从胶囊下穿过。上游 12.10.5 的 `onScrolled` 当时看的是把 `applyScrollY` 写死成 `false` 的那条死路，这一版也保持固定；它的真实偏移由 `ContentView.dispatchDraw` 从列表自己的位置算出来，后来的「主页随滚动收起」一片把它和搜索框、顶部玻璃一起补上了。模糊源是 `GlassPanelHost`：`DialogsActivity` 自己的 source render node + `BlurredBackgroundDrawableViewFactory` 收到我们这边一处，话题页以后的 action-bar 胶囊复用同一个类。
- 搜索改成 12.10.5 主列表的形态：顶部是上游的 `FragmentSearchField`（提示「搜索 linux.do」，`setHint` 而不是动画 hint drawable），点它按 `DialogsActivity` 的动画打开 - 列表 alpha→0（低性能档不做 0.95 缩放）、搜索页从 1.05 收到 1、进度值驱动分类条 `1 - searchVisible` 淡出与搜索胶囊从 0.98 出现，200ms EASE_OUT 开、180ms + 20ms 关；底部的 话题/通知/设置 栏同时按 `MainTabsActivity` 自己的方式（380ms EASE_OUT_QUINT、下移 40dp、alpha 与 0.85 缩放）让位与返回。设备录屏 `home-search-open-close.mp4`，逐帧拼图 `open-animation-frames.png` / `close-animation-frames.png`：打开时标题、分类条与列表一起让位、底部栏消失；关闭后列表、分类条与底部栏都回来。
- 搜索页是 `ViewPagerFixed` + 药丸 `TabsView`（装在 `SearchTabsAndFiltersLayout` 玻璃胶囊里）：话题/帖子/用户来自一次 `/search.json`（300ms 防抖，只显示输入框里那个查询的答案），分类/标签来自 `/categories.json` 与 `/tags.json`，按查询在客户端过滤。真实站点：`telegram` 的话题页命中话题行（`08-search-topics`）、帖子页显示带楼层摘要的帖子行（`09-search-posts`）、标签页显示 `#Telegram`（`10-search-tags`）；`claude` 的话题页同样有结果（暗色 `06-search-dark`）。空/加载状态是上游的 `StickerEmptyView` + `FlickerLoadingView`：没输入时「输入关键词开始搜索」（`12-search-idle`），没有命中时「没有找到相关内容」与鸭子贴纸（`11-search-empty`）。
- 关闭：字段的 × 一步关搜索，主页立即恢复（`13-close-button-home`）；返回键同样先关搜索（搜索打开时第一次返回收键盘，第二次返回关搜索且应用不退出）。返回关搜索要壳先把根屏幕问一遍，`NativeDoActivity.onBackPressed` 的单 fragment 分支现在先问 `getLastFragment()`。
- 主题：设置 → 外观 → Switch to Night Mode 后主页与搜索页（字段、药丸条、行、底部栏）对比度正常（`05-home-dark`、`06-search-dark`，这两张是切到夜间后重新进入应用拍的；再切回白天时主页在运行中即时恢复，见 `07-home-light-restored`）。
- 未验到：`用户` 页的有行状态 - 真站点对 `telegram`/`baba`/`neo`/`daud`/`linux` 都回了空 `users`，这一页在这几个查询下只有空状态；行与「点开资料页」由渲染 harness 的 `SearchTest` 覆盖（`ProfileSearchCell`）。`分类` 页同理只有空状态：linux.do 的分类名都是中文，而 `adb shell input text` 不能输入中文，没有 ASCII 命中查询；分类行与标签行是同一个 cell（`HashtagSearchCell` + `CategoryLabels` 的彩色圆点）。
- 设备上左缘那条系统浮条（麦克风/收键盘/搜索/表情/菜单）随输入法出现，不是本应用的 UI。
- 构建与测试：同一 HEAD `:nativedo:testDebugUnitTest` 31 类 87 项全过（0 失败 0 error 0 skipped）；一次全量命中既有的 `ReadProgressTest` 竞态（`the topic the reader finished is not unread any more`），单独重跑该 10 项连过 3/3，判为既有 flaky。`assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过，APK SHA-256 `c7e6a916757d30a78fc0ab6c2b81a4de7cc22163314aae30c7496de7d6c21e5d`，`install -r -t` 覆盖安装（未卸载未清数据）。设备期间另一 agent 清掉了会话，等 cookie-sync 恢复（@example_user）后继续；未登录阶段只到登录页。证据在 `.amp/in/artifacts/home-search/`。

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

### 设置页重绘、我的 tab 与「在应用内打开链接」（2026-10-04，`emulator-5554`，`nativedo-settings-tab`）

- 设置页照上游 `SettingsActivity` 的骨架重画：`UniversalRecyclerView` + `UItem.asCustomShadow/Header/Shadow`
  + `SettingCell`（`SettingCell.Factory`）与 `IconBackgroundColors` 渐变图标格；头部是上游自己的一组常量
  （120dp 容器、90dp 圆头像、22sp 名称、13sp `@用户名`，`asCustomShadow(topView, 200 - 12)`），
  列表滚过头像后标题按上游的 `updateActionBarVisible` 淡入。行组改成 外观（主题）→ 链接（在应用内打开链接 + 说明）
  → 我的（书签）→ 账号（打开验证页、退出登录）。旧「账号」行删除：自己的资料页现在是第四个 tab，设置页头部本身
  就是账号（决定见报告）。
- 第四个 tab「我的」= 上游的 avatar tab 形状（`GlassTabView.createAvatar`）：22dp 圆头像画在图标 tab 的 5dp 处，
  文字「我的」；头像来自 `DiscourseAvatars` 的会话用户，缺缓存时用会话 cookie 下载后回填。点按替换根页面而不是压栈，
  返回键不会回到设置页；自己的资料页作为根不画返回箭头（`ProfileFragment.forRoot`），帖子作者入口仍以压栈方式打开
  他人资料页（有返回箭头）。
- 「在应用内打开链接」= `LinkOpenSettings`（`nativedo_settings` 里的 `open_links_in_app`，默认开）+ 上游已存在的
  `MessagesController.isWebBrowserOpenInApp` 那一行 `// NativeDo:` 调用（本次没有改签名或调用点）。
  开：普通外链进 Telegram 内置浏览器；关：同一链接交系统 Chrome 自定义标签页。本站话题链接两种状态下都由
  `TopicFragment.didPressUrl` 自己打开话题（新行为，替换之前统一交给浏览器）。
- 设备检查（覆盖安装 `-r -t`，未卸载未清数据，仍登录 @example_user；设置页浅色与夜间都过了）：
  - 设置页浅色：头像头（example_user/@example_user）、四组行、渐变图标、蓝色勾选行、红色退出登录、底部四格栏（`01-settings-light`、`13-settings-light-restored`）。
  - 设置页夜间：同一页全部颜色跟随主题，勾选行保持蓝色选中态（`02-settings-dark`、`08-link-off-settings`、`11-link-on`）。
  - 我的 tab（夜间）：顶部/折叠/下拉（`03`、`04`、`05`）——折叠后名称与 @用户名移进透明动作栏，下拉后头像铺满整宽
    （站点 720px 头像）、名称画在图片下缘；浅色下的头像 tab 图标与四格栏宽见 `13`。验证后段设备侧会话被并行的
    cookie 改造置为登出（登录页），浅色的我的 tab 三态没取到，属未验证（会话恢复后补 `14`-`16`）。
  - 他人资料页：从帖子作者名打开 `momo199412` 的资料页，带返回箭头（`06`）。
  - 话题链接：内置浏览器开着和关着两种状态，点帖子里的 `https://linux.do/t/topic/2974886` 都原生打开话题
    「Version 1.0.0 Build 11」（`07`、`09`）。
  - 普通外链：App Store onebox 在关闭时进系统 Chrome 的自定义标签页（`10`，`ResumedActivity` 为 `com.android.chrome`），
    打开时进 Telegram 内置浏览器、下滑最小化成底部标签条（`12`）；结束时设置恢复为「开」，主题恢复为浅色。
- 构建与测试：`:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过（本 worktree 缺的原生依赖树
  按 pin 重新 fetch；arm64 `libtmessages.49.so` 取自同 commit 的 `linuxdo-native-device` worktree 的构建产物，
  Java/资源/dex/打包本地跑）。`:nativedo:testDebugUnitTest` 31 类 87 项全过（0 失败、0 error、0 skipped）。
  `scripts/vendor-guard.sh --compile` OK。第一版 APK 有两处设备上才发现的问题（`getTitlesContainer()` 为 null 的
  崩溃与标题未进容器不会淡出），已修并复验，见 `bb77ac7a`。
- 证据在 `.amp/in/artifacts/settings-tab/`。

### 设备码登录：`/login/device` 与 User API Key 贯通（2026-10-04，`emulator-5554`，`nativedo-device-login`）

- 协议：linux.do 的 `/login/device` 是 Discourse 核心的 User API Key 设备授权流（PR #40189）。
  应用 `POST /user-api-key/device.json`（RSA 公钥 + nonce + `scopes=session_info` + 每次安装固定的
  `client_id`）拿到 `user_code` / `verification_uri`（实测返回的核心路径是
  `/user-api-key/activate`，不是 `/login/device`），在站内浏览器里以 @example_user 的会话批准，应用按
  `interval=5s` 轮询 `POST /user-api-key/device/poll.json`，解密载荷、校验 nonce，得到的是
  **User API Key**（`User-Api-Key` 头），不是 `_t` cookie 会话。入口在
  设置 → 账号 →「设备码登录（实验）」（不动现有登录，密钥用 AndroidKeyStore 封存，可在此撤销）。
- scope 探测（页面里的「探测可用 scope（调试）」，5 个未批准授权码，10 分钟自行失效）：
  `read` → 403 `invalid_access`；`read,write` → 403 `invalid_access`；`notifications` → 200；
  `session_info` → 200；`one_time_password` → 400 `invalid_parameters`（核心设备流禁用）。
  所以登录默认 scope 取 `session_info`（`read`/`write` 在 linux.do 当前不在允许表里，
  `notifications` 也过但本片只需要身份读取）。不注册 `client_id` 也能创建，批准页只多一条
  「此应用程序未经本网站注册或验证」警告。
- 端到端（真实登录）：站内浏览器打开 `/user-api-key/activate?request=…`，页头是 @example_user、应用名与
  「读取用户会话信息」，输入 `user_code` 后点「授权」→「授权完成」；应用轮询得到 `authorized`，
  载荷 350 字符 / 256 字节，**用 PKCS#1 v1.5 打开成功**（OAEP-SHA1/256/512 全部失败；请求里发的
  `padding=oaep` 在 linux.do 侧没有生效，见 `contracts/discourse/device-authorization.md`），nonce
  校验通过，密钥落库（`key_storage=keystore`）；随后拿这把密钥请求 `GET /session/current.json`
  （`User-Api-Key` + `User-Api-Client-Id`）返回 **200，username=@example_user**（logcat
  `probe_session status=200 username=example_user`）。当前 App 仍走 cookie 会话，本片没有切换网络层。
- 密钥处置：全部撤销。应用内「撤销本机保存的设备密钥」调用 `POST /user-api-key/revoke`
  返回 200 并清掉本地存储（`key_revoke status=200` + `key_cleared`）；站点
  `u/example_user/preferences/security` 的「应用」区在授权后能看到这条（已批准 5 分钟、到期时间：永不），
  撤销后该区只剩 `LINUX DO iOS`（`37-apps-clean.png`）。第一次解密失败（v1 密钥只授权了 OAEP）
  产生的那把测试密钥也已在同一轮从站内页面清掉，账号上没有留下任何 NativeDo 密钥。
- RSA 密钥：AndroidKeyStore（v2 别名）生成 2048 位并支持 OAEP 与 PKCS#1 两种 padding
  （digest 授权 SHA1/256/512），生成时用真实加解密自检；Keystore 不可用时退回软件密钥，
  私钥 PKCS#8 由同一把 AndroidKeyStore AES/GCM 凭据密钥封存。解密按
  `oaep-sha1 → oaep-sha256 → oaep-sha512 → pkcs1` 依次尝试并记录生效的那一种。
- 构建与测试：`assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过（在途 worktree 缺的
  `TMessagesProj_Modules/media`、`jlatexmath` 从同 commit 的 `linuxdo-native-device` 补齐，
  arm64 `libtmessages.49.so` 同样取它的构建产物，`-x` 掉 configure/build CMake 两个任务）；
  设备验证所用 APK SHA-256 `a823d3714b1618a0be7a4aeaa7321af04ee663b141cec11af8ea4fafc540dd3e`
  （其后只改了注释与文档）。`:nativedo:testDebugUnitTest` 33 类 98 项全过（0 失败、0 error、
  0 skipped；新增 `DeviceLoginControllerTest` 4 项，钉住异 nonce 载荷拒收不落盘、正确 nonce
  存储并探测、denied/expired 终止且不再轮询）。全片未改 `apps/android/telegram` 子树，
  vendor-guard 不涉及。
- 证据在 `.amp/in/artifacts/device-login/`（`01`–`37` 截图：设置页新行、授权码、站内浏览器授权页、
  授权完成、scope 探测结果、授权成功、撤销后站点应用区；`logcat-a-probe.txt` /
  `logcat-b-first-approval.txt` / `logcat-c-full-flow.txt`，只含固定事件名、状态与 user_code，
  不含密钥、device_code 或 cookie）。

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
### tabs 宿主与底部灰带（2026-10-04，`emulator-5554`）

- tab 代码从 `NativeDoActivity` 移入新的 `NativeDoMainTabsActivity`（`ViewPagerActivity` ＋
  `MainTabsLayout`／`GlassTabView`／blur3）：话题／通知／设置／我的 四个 root 是 pager 的四个页面，
  点 tab 切换、点当前 tab 回顶部、左右滑动切换；被推入的话题／搜索等页面整个盖住 tabs，返回后
  回到原来那一页。我的 tab 是 `GlassTabView` 的 avatar 画法（`ProfileFragment.forRoot`，root 不画
  返回键）；退出登录时宿主不在栈上，所以没有 tabs（JVM 断言）。玻璃沿用旧宿主的做法：把 pager
  录进 source render node 并 `setBlur(dp(8))`，`BlurredBackgroundProviderImpl.mainTabs` 出胶囊；
  浅色主题下胶囊接近不透明，内容只在边缘透出（截图看不出强模糊，配置与旧设备的玻璃一致）。
- 灰带修复：删掉 `NativeDoActivity.getBottomTabsHeight` 覆盖后 `ActionBarLayout` 不再预留 tabs
  高度；四个 root 都 edge-to-edge（`isSupportEdgeToEdge()`＝true、`drawEdgeNavigationBar()`＝false），
  列表自身 padding bottom ＝ 导航栏 inset ＋ dp(MAIN_TABS_HEIGHT_WITH_MARGINS)、`clipToPadding＝false`，
  FAB 上移同样高度。设备上首页列表一直画到屏幕底部、从玻璃 tabs 后面滚过，无灰带；列表滚到底时
  最后一行完整停在 tabs 上方（`11-home-bottom.png`）。
- 截图在 `.amp/in/artifacts/tabs-host/`：`01-home.png`（无灰带、内容在玻璃后）、`02`/`03`/`04`
  （通知／设置／我的 的选中态与页面）、`05`/`06`（右滑到设置、左滑回我的）、`07`/`08`（话题整屏
  盖住 tabs、返回后 tabs 回来）、`09`/`10`（列表滚动、点当前 tab 回顶部）、`11`（滚到底后最后
  一行在 tabs 上方）。对照修复前的 `.amp/in/artifacts/home-now.png`；`00-before-live.png` 是安装
  新包前的运行中截图。
- 覆盖安装版本 `0.1.0 (1)`；APK 与设备 SHA-256 都为
  `7191645ad45467cdfc0cb1037e7632b1cd8f2465ac37483c57988c154aba3e33`。登录和代理保留，无
  crash／ANR，未生成 `cache4.db`。
- JVM gate：`:nativedo:testDebugUnitTest --quiet` 全量 30 类、102 项、0 失败；arm64
  `:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` 通过。`HomeTabsTest` 断言跨 tab
  保留同一个页面实例、不重读 feed、列表 padding 与底部无预留带、话题盖住宿主、第四个 avatar tab
  打开自己的 profile；`SettingsTabTest` 断言退出登录后窗口里没有 tab 宿主。
- 本片未复测：横屏、夜间模式切换后的宿主重建、深链冷启动（JVM 侧有主题事件与 insets 断言；
  深链结构沿用原来的「宿主先在栈、话题再压上」）。视频下载／取消、新增回应仍受站点限制，未动。

### 消息总线接入 UI（2026-10-05，`emulator-5554` 与真机 `449dd25d`）

- 数据层：`MessageBusSession`（`core/messagebus`）归 `AppContainer` 所有，一个登录会话一个
  `MessageBusClient`；`NativeDoActivity.onStart/onStop` 转发前台／后台，session 状态自己跟随
  登录／退出／失效——退出时停轮询、清两类状态并在下一个读者登录时重建 client（新的 client id）。
  话题列表订阅 `/latest`、`/new`，通知 badge 订阅 `/notification/{userId}`；起点是页面
  `script#data-preloaded` 里的 `topicTrackingStateMeta` 与 `currentUser.notification_channel_position`，
  badge 初始值取 `currentUser.all_unread_notifications_count`。轮询走 `AppContainer` 的 OkHttp
  （cookie、UA、请求头拦截器原样），不经过 session boundary，所以轮询的 404/挑战只会退避，不会触发登录。
- 真站点观察（`NativeDoMessageBus` 日志）：linux.do 首页的 preload 是 **JSON `script#data-preloaded`**
  （文本是对象、每个值是字符串包裹的 JSON），不是旧的 div attribute；`topicTrackingStateMeta` 有 7 个
  频道；`currentUser` 带 `id`、`notification_channel_position`、`unread_notifications`、
  `unread_high_priority_notifications`、`all_unread_notifications_count`。站点自己的 origin 上
  `POST /message-bus/.../poll` 被 Cloudflare 直接 404（`server: cloudflare`、10 字节 `Not Found.`）；
  页面的 `siteSettings.long_polling_base_url` 指向另一个 origin，用 `meta[name=shared_session_key]`
  作 `X-Shared-Session-Key` 轮询那里返回 200，body 是单个 JSON 数组（`Dont-Chunk: true`；响应上没有
  `Content-Length`/`Transfer-Encoding`）。节奏由消息驱动：linux.do 忙时完成一轮 0.2–9 秒，客户端
  delivery 后最小间隔 100 ms，全程无 403/429、无紧凑循环；配置错 base 的那段时间每次 poll 404，
  退避 3/6/9 秒（无风暴），改对 base 后恢复。
- 设备：模拟器 `emu-05-pill-final.png`（"9 个新话题" pill 在分类条下方、第一行完整可见）、
  `emu-09-final-run.png`（最终包冷启动 40 秒后的真实计数 "2 个新话题"，同样不压第一行）、
  `emu-06-after-tap.png`（点按后发了一次 `latest.json`、计数清零；随后到达的新消息又计成 "1 个新话题"，
  是实时行为）、`emu-07-cleared.png`（清零且列表已刷新的状态）、`emu-08-notifications.png`（通知列表；
  该读者的三个计数都是 0，所以 tab 上没有 badge——badge 的种子与更新由 JVM 测试覆盖）。真机
  `phone-01-pill.png` 同样画出 "3 个新话题" 且列表在 pill 下方完整显示。
- 真机 HyperOS 拒绝 `adb shell input tap`（`INJECT_EVENTS` 权限），所以点按的 E2E 只在模拟器执行；
  真机验证限于安装、冷启动、真实轮询与 pill 渲染。
- 覆盖安装版本 `0.1.0 (1)`，没有卸载（登录与代理保留），无签名冲突、无 crash／ANR，未生成 `cache4.db`；
  两台设备 `pm path` 拉回的 base.apk SHA-256 都是构建产物
  `fe6f5412d16f12c414f4bef1eacf8c18d249da8b059073df3f5b1022ca5f5ff0`（真机原来的
  `0.1.0-debug-20260929-203040` 被直接覆盖）。
- JVM gate：`:nativedo:testDebugUnitTest --rerun --quiet` 连续 5 轮全量，35 类、112 项、0 失败；
  arm64 `assembleDebug` 通过。测试期间发现并修掉一个真 bug：前台回调与 session 状态收集器并发进入
  `ensureStarted` 时会各建一个 client（其中一个在 session 之外永久轮询），现在启动在同一把锁下，
  `repeatedForegroundCallsDuringAStartBuildOneClient` 钉住它；`MessageBusBootstrapTest` 补了
  linux.do 的 script preload 形状与 `long_polling_base_url` 读取。
- 未验证：`/notification/{userId}` 的实时计数变化（该账号计数为 0、期间没有服务端计数更新），
  所以 通知 tab 的 badge 只在 JVM（bootstrap 种子 + `/notification` 消息）验证过；真机上只验证了
  通知列表与「0 计数不画 badge」。截图与设备 APK 拉回件在 `.amp/in/artifacts/messagebus-ui/`。

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

### 首页选项菜单与夜间切换（2026-10-05，`emulator-5554`）

- 首页动作栏的最右加上上游自己的三点项（`ActionBarMenuItem`，id 4、`ic_ab_other`，与
  `DialogsActivity.optionsItem` 同 id 同图标）；点开是上游的 `ItemOptions`
  （`setColors(key_actionBarDefaultTitle)`、`setDimAlpha(0x08)`、`show()` 后 `-dp(64)`），
  条目按上游顺序：夜间／日间模式、`addGap()`、发新帖、书签。上游那些没有 linux.do 含义的条目
  （联系人、隐私、社区、归档、Telegram 设置等）没有加入；发新帖与书签分别走悬浮按钮和设置页
  已在用的同一个 `NewTopicFragment`／`BookmarksFragment`。
- 主题切换照上游 `DialogsActivity.showItemOptions()` + `switchTheme()` 移植：按 `themeconfig`
  的 `lastDayTheme`／`lastDarkTheme` 选目标主题，把选项图标的中心 post 给
  `needSetDayNightTheme`；`NativeDoActivity` 多出上游 `LaunchActivity` 的那一支——先快照窗口，
  再做 `createCircularReveal`（400 ms、`Easings.easeInOutQuad`、`（h − pos[1]）/ dp(2.25)` 后放开
  系统栏颜色），`switchingTheme` 在动画结束复位；没有位置的切换（外观页）仍走原来的颜色动画。
- 上游的动画只清「最后一片之下」的 fragment；首页只有一个 tab 宿主时它什么都不清，页面的视图
  会把旧主题的颜色留住（设置行、分类条、胶囊都是构造时取色的）。当时宿主单独在栈上时主动
  `rebuildFragments(REBUILD_FLAG_REBUILD_LAST)`，在揭示下面把整套视图按新色重建。
  **2026-10-05 更新**：这次重建已删除——它让分类条消失再出现、feed 重读、选中与滚动位置归零；
  现在按上游 `getThemeDescriptions()`/`updateColors()` 的路子原地换色（见「分类条：夜间切换不眨眼
  与切 tab 滑动」一节）。
  重建不销毁 fragment，所以当时页面与宿主的 message-bus 监听在 `createView` 里先关旧的再挂新的：
  不这样每次切换会各泄漏一个 collector，`HomeMenuTest` 的第二条用例就是钉这个（去掉注销后它在
  「旧监听注销」处失败；该用例现在钉「切换不重建视图、也不多发监听」）。
- 模拟器逐项（截图在 `.amp/in/artifacts/home-menu/`）：`20` 最终包冷启动的栏（搜索、标签、三点
  在最右）；`21` 弹窗三条目与分隔；`25` 最终包的夜间揭示中间帧（黑圆从三点图标处扩起）、`05`／`07`
  两个方向的揭示中间帧与两段录屏；`04`／`22` 夜间主题；`09`／`10-*`／`23` 话题／通知／设置／我的
  四个 tab 全部换色；`11` 夜间下条目翻成「日间模式」（太阳图标）；`12`／`24`／`26` 回到日间无残留；
  `13` 书签页与 `15` 发新帖页由菜单打开、`14` 返回后首页栏完好；`16` 搜索页打开、`17` 返回首页、
  `18` 搜索返回后菜单仍能打开（新首页的搜索是整屏推入，不是 DialogActivity 那种栏内展开，所以
  没有 `checkUi_itemOptionsVisibility` 的对应物，只验证了开关前后不挡不吞）。
- 覆盖安装 `0.1.0 (1)`，未卸载；模拟器 `pm path` 拉回的 base.apk SHA-256 与构建产物都是
  `3c58da7a11c09bb5ca67a94254ace79e9ff149faae8ebd7a555a452a2a9c1b46`。无 crash／ANR，未生成
  `cache4.db`，登录和代理保留。
- JVM gate：清空 XML 后 `:nativedo:testDebugUnitTest --quiet` 全量 36 类、114 项、0 失败；新增
  `HomeMenuTest` 两条：真实弹窗打开书签与发新帖两屏；主题通知后宿主重建、message-bus client 不
  重启、旧视图监听注销。arm64 `assembleDebug` 通过。
- 真机 `449dd25d` 本轮未验证：会话中途它从 adb／USB 消失（`adb devices`、`adb reconnect`、重启
  adb server 都没有回来），所以没有做覆盖安装、冷启动和真机栏上三点的截图。没有卸载动作，机器
  回到 adb 后要补：`install -r -t`、冷启动、首页栏截图、`pm path` 拉回件与构建产物比对；真机
  HyperOS 仍拒绝 `input tap`，点按部分只留在模拟器。

### 合并 `nativedo-device`（2026-10-05，`emulator-5554`）

- 分支合并（`git merge --no-ff nativedo-device`）：tab 宿主只保留一个所有者——`NativeDoMainTabsActivity`
  继续持有 `ViewPagerActivity`＋玻璃条；device 分支放在 `NativeDoActivity` 里的 tab 相关行为都改挂到宿主
  （第四个 avatar tab、搜索打开时条收起、浏览器 BottomSheetTabs 占底栏时条让步、avatar 加载），
  `NativeDoActivity` 只保留浏览器宿主（`ActionBarLayout(main=true)`＋`BottomSheetTabsOverlay`＋
  `NativeDoHooks.setBrowserHost`）、主题圆形揭示、message-bus 前台／后台与深链、会话门。
  首页以 device 版为底（滚动收起搜索框、搜索 pager、玻璃分类条、FAB 隐藏、列表 padding 记
  header 高度），spike 的「N 个新话题」pill 与选项菜单（日夜切换走圆形揭示）叠加上去；pill 跟着
  header 的 panel offset，`checkUi_listPadding()` 同时算 header 与 pill 的槽位，收起数学不变。
  设置页与资料页以 device 内容为底、按 pager 页面补 edge-to-edge 的底部 inset。`AppContainer` 的一
  个 `CookieManager` jar 同时给 API、WebView 和 message bus 用。subtree 的 hook 行取 device 分支原样。
- JVM gate：`:nativedo:testDebugUnitTest --quiet` 全量 39 类、128 项、0 失败（XML 在
  `apps/android/nativedo/build/test-results/testDebugUnitTest`，时间新鲜）；`HomeTabsTest`（跨 tab
  保留页面、第四个 tab、insets、无底部预留带）、`MessageBusUiTest`（pill 在分类条下方）、
  `PostLinkBrowserTest`（内置浏览器 sheet）、`DeviceLoginControllerTest`、`SharedCookieJarTest` 都在
  合并代码上通过。
- `scripts/vendor-guard.sh --compile` 通过（597 个跟踪文件；输出 `=== vendor-guard: OK ===`）。
- arm64 `:nativedo:assembleDebug` 通过；APK SHA-256
  `3de45b5160aea1c7196611c1a76c8d0330d1e06843258b6d43b50ad34a5ed8df`，`adb -s emulator-5554
  install -r -t` 覆盖安装（未卸载、未清数据），`pm path` 拉回的 `base.apk` 与构建产物同 hash，
  版本 `0.1.0 (1)`。真机 `449dd25d` 不在 `adb devices`（`adb reconnect` 后仍未出现），本轮未安装。
- **设备 UI 复核被会话挡住**：冷启动（`01-cold-start.png`、`01b-cold-start-wait.png`）停在登录页。
  日志说明这是站点侧登出而不是合并回归：`NativeDoSession event=native_response status=404
  accepted=true evidence=LoggedOut ... hasToken/hasForumSession/hasClearance=true`，随后
  `expired_ServerLoggedOut`，app 按自己的规则清 cookie 回登录页。登录需要 owner 的凭据，没有在设备上
  代填。crash buffer 为空。
- 待 owner 登录后续做（本轮未验证）：四个 tab 与左右滑动、分类条与搜索框收起／回顶、搜索 pill pager、
  FAB 隐藏、pill 出现／点按、选项菜单日夜切换（结束时回到日间）、话题三玻璃胶囊与壁纸、链接进内置
  浏览器与 BottomSheetTabs、设置页、我的 tab、通知 badge。截图留在 `.amp/in/artifacts/merge-device/`。

### 分类条：夜间切换不眨眼与切 tab 滑动（2026-10-05，`emulator-5554`，`nativedo-strip-anim`，未登录）

- 两个症状、两个根因：
  1. **夜间切换时分类条消失再出现**：主题处理在「栈上只有 tab 宿主」时主动
     `rebuildFragments(REBUILD_FLAG_REBUILD_LAST)`（原 `NativeDoActivity.java:426-429`）→
     `ActionBarLayout.rebuildAllFragmentViews`（3122-3146）`clearViews()` ＋ `showLastFragment()`，
     宿主连同四个页面整棵重建；`TopicListFragment` 新建时分类条是 `GONE`（`createView` 里
     `tabsView.setVisibility(GONE)`），要等 `/categories.json` 再答一次、`buildTabs()` 之后才
     `VISIBLE`（`updateStripVisibility`，1254-1258），同时 feed 重新读、滚动位置归零——这就是
     「消失再出现」。上游不是这样：`DialogsActivity` 的 `getThemeDescriptions()` 委托里
     `filterTabsView.updateColors()`（12064），`ActionBarLayout.animateThemedValues` 把这些描述
     在 reveal 的 instant 分支一次应用，视图原地换色。
     修复：删掉那次 rebuild。宿主 `NativeDoMainTabsActivity.getThemeDescriptions()`
     （580-587，仿 `MainTabsActivity:1096-1104` 的 delegate）在 `updateColors()` 里给页面上色；
     `TopicListFragment` 的全局观察者原地 `updateColors` 分类条/玻璃源/搜索框/搜索页/pill，并像
     `DialogsActivity` 的 cell delegate（12022-12033）一样重绑当前页挂着的 `DialogCell`
     （`refreshPageRows` / `bindRow`）；设置、通知、我的三个 root 页各加同类观察者（设置页
     `listView.adapter.update(false)`，通知与我的 `notifyDataSetChanged`）。全部在揭示快照之下
     换色，不重建视图，选中 tab 与滚动位置保留。
  2. **点分类 tab 没有动画**：原来直接 `loadFeed()` 换掉同一份列表。按 `DialogsActivity` 的
     `viewPages[]` 对重建：`TopicListFragment.ViewPage`（← `DialogsActivity.ViewPage`:360）两页
     各带自己的 `RecyclerListView`/adapter/scroller/空状态，`CategoryFeed`（items＋authors＋载入
     状态＋滚动位置）按 tab 缓存；tap 走 `onPageSelected`（上游 3614-3634）→ `selectCategoryTab`
     把目标页摆到边外，`FilterTabsView` 的 320ms 指示器动画经 `onPageScrolled`（上游 3641-3667）
     驱动两页平移、到 1 交换；进度公式原样。列表区横滑按 `ContentView` 的触摸处理移植（上游
     1308-1355、1380-1583）：0.3cm slop、3000 起滑速度、1500 定方向、3500 忽略、1/3 宽回退、
     150-600ms 时长、五次方缓出插值（730-733），`startedTracking`/`maybeStartTracking`/
     `animatingForward`/`backAnimation`/`additionalOffset` 同名。`canParentTabsSlide` 按上游
     13601-13629：条上还有可滑的分类时列表自己处理；首 tab 只把后退、末 tab 只把前进交给 root
     pager；action bar 上的触摸总是 pager 的。本客户端没有 Telegram 账号、没有 chat swipe 设置
     可读，横滑分类固定开启（等价于上游 SWIPE_GESTURE_FOLDERS 那一支）。换页时收起数学读
     `viewPages[0].listView`；每页各自 320ms 的滚动吸附、FAB 隐藏与阴影监听照旧，只是按页持有。
- 证明：`HomeCategoryPagerTest` 四项钉住「主题切换不重建：同一 fragmentView/strip 实例、选中
  tab、滚动位置，且不发第二次请求」「切 tab 中两页同时在屏，结束后是新分类的 feed，切回第一页
  仍是原来的 items 和第一可见行」「横滑列表到相邻分类并滑回（同一套触摸事件走完 DOWN/MOVE/UP）」
  「首/末 tab 的 root pager 裁决」；`HomeMenuTest` 的原重建断言改成钉「不重建、不重启 message
  bus、不泄漏监听」。四张 harness 渲染图（浅色、切换后夜间、切 tab 中帧、切完）在
  `.amp/in/artifacts/strip-anim/`。
- 构建与测试：`:nativedo:testDebugUnitTest --quiet` 全量 40 类 132 项 0 失败 0 error 0 skipped
  （XML 在 `apps/android/nativedo/build/test-results/testDebugUnitTest`）；arm64
  `:nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet` 通过，APK SHA-256
  `029d71874a3ea2996f6e48dbdcc9d2f21c0b5bfc2bee828389acd76cab0ed7bb`，`install -r -t`
  覆盖安装（未卸载、未清数据），设备拉回的 `base.apk` 与构建产物同 hash，版本 `0.1.0`。
- **本片设备未验证（harness-only）**：冷启动一次（覆盖安装后）停在「登录 linux.do」的 Cloudflare
  验证页（`NativeDoSession event=auth_cookie_missing accepted=false`，截图
  `strip-anim/device/01-cold-start.png`），站点侧会话已登出；按约定没有登录。所以真机上的横滑
  跟手、圆形揭示下的原地换色、玻璃在滑动中的样子都只有 harness 证据，设备侧没有通过。
  没有改 `apps/android/telegram` 子树，vendor-guard 不涉及。

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

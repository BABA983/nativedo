# Hosting Telegram UI without Telegram services

## Decision

Use the full upstream subtree for UI behavior and keep Discourse product logic in
`nativedo`. The old partial mirror required maintaining too many reimplemented
views and stubs; the active build translates at the data/host boundary instead
of recreating drawing, gestures and animation. It is still a linux.do client,
not a Telegram account client.

## Host seam

[NativeDoHooks](../apps/android/telegram/TMessagesProj/src/main/java/io/github/baba983/nativedo/hooks/NativeDoHooks.java)
and narrow call sites are the allowed subtree differences. The
[hook guard](../scripts/telegram_hook_guard.py) is the inventory and gate; do not
maintain a second per-call-site ledger here.

- tgnet initialization and Telegram database opening are gated off.
- Browser hosting answers the shell questions upstream normally asks
  `LaunchActivity`: current fragment, tabs, overlay and the per-activity sheet stack.
  Sheet dismissal retains its original stack rather than looking up a replacement
  activity's stack. This preserves upstream `ArticleViewer` sheets, minimization
  and restoration without a Telegram activity.
- Editor and mention adapters bind product behavior to an individual component,
  not a global callback that outlives its owning screen.
- The attachment sheet (`ChatAttachAlert`) and the composer's recording gesture are
  hosted the same way. A `DiscourseAttachSheet` subclass answers upstream's
  account-backed questions (tab set, poll pane, article pane, whether the files pane
  needs a storage grant) while rendering stays upstream's; a record touch reaches only
  a delegate that implements the recording seam, so a real Telegram composer keeps its
  own recorder and video note. Forum voice review also uses upstream's actual
  `RecordedAudioPlayerView` and controls. The fragment supplies `ChatActivity`'s
  overlay insets and glass factory; the instance-bound host supplies AAC capture,
  sample-table trim/join and Discourse upload, not Telegram's Opus/account services.
  See [recorded audio](content-composer.md#attachment-sheet-and-recorded-audio).
- Other hooks cover concrete host differences such as local media loading and
  in-app-browser preferences. They do not authorize redesigning upstream renderers.

Ordinary links default to the in-app browser; the reader can disable this in settings.
[LinkOpenSettings](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/settings/LinkOpenSettings.kt)
owns that local choice because Telegram's server-delivered browser settings never
arrive in this host.

The browser overflow opens a locally backed settings sheet using upstream
`UniversalFragment` cells; it does not instantiate Telegram's account/browser
settings. History keeps upstream's local browser-history screen and sharing uses
Android's chooser. Saved Messages bookmark entries are omitted because their
Telegram account storage/send path is unavailable. Clearing browser cookies is
not offered here: those cookies can belong to the client's forum session.

## Startup and lifecycle are part of hosting

[NativeDoApplication](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/app/NativeDoApplication.java)
extends upstream's loader but does not invoke its full account-oriented
`postInitApplication`. It explicitly initializes the configuration, LiteMode,
locale, shared preferences and screen state used by UI code. Relying on early
static initializers can leave a capable device in the low-performance fallback.

[NativeDoActivity](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/app/NativeDoActivity.java)
owns navigation, the browser host, system bars, session gate and foreground
MessageBus lifetime. When cookies may hold a session, it presents the restored
main-tab shell before network verification, with upstream loading rows rather
than a blank window. Feed/category/avatar loading and composition still wait for
a confirmed session; held topic links open once that gate succeeds. Recoverable
failures keep an explicit retry or verification entrance. The shell ignores
callbacks after destruction and releases shared viewers at the final-instance boundary.

Do not treat an upstream view as a drop-in object without its host lifecycle.
Composer pause/resume/destroy, theme delegates, system/IME insets, viewer state
and Back arbitration matter as much as constructor arguments. A predictive Back
probe must not commit a screen change; an open input panel, selection or viewer
gets its own Back handling before page navigation.
A hosted attachment sheet is the same kind of object: the screen that opens it only
dismisses it, so the alert subclass owns its teardown - once upstream has removed the
panes it built, the subclass brings the gallery and the alert's notification observers
down exactly once - and a retained alert that has already been torn down ignores later
pause/resume and permission callbacks instead of driving dead views.
Runtime permission results follow the same upstream ownership: the activity
forwards to its top fragment, and a tab host forwards to its visible page. A
microphone permission answer must release that page's pending request without
restarting the activity or starting a recording automatically.

## Visual state is upstream state

Reuse upstream Theme and LiteMode rather than a second theme/performance system.
In-place color updates should retain screen state; rebuilding every fragment to
change colors loses scroll positions and reloads data. Glass effects follow the
device's enabled performance path; an opaque low-performance fallback is not
evidence that the blur path works.

Full-height fragment content and its action bar must share the intended transform
and inset ownership. Keep the bar in its content host when following that upstream
layout, so PhotoViewer transitions do not scale the body independently of the bar.
Wallpaper fade and content-sampling glass are different sources, not interchangeable.

Message-row animations are also a host contract: `ChatMessageCell` paints reply
previews and reactions using its own alpha. `PostListView` invalidates on each
addition-animation update, including completion; asset rebinds reuse the cell
instead of cross-fading two cells attached to the same rich layout. When local
assets arrive, the host marks the retained message and reactions for upstream
refresh; a notification alone does not rebuild an unchanged message's images.
For a retained `RichPhotoBlock`, the host cancels its unfulfilled image request
and reapplies upstream's receiver parameters as preview and original files arrive.
Synthetic photos enter ImageLoader as local filesystem paths, whose cache-only
decode cannot fall back to a datacenter download if a file disappears. Missing
files are withheld; HTTP completion also refreshes the open PhotoViewer's existing
receivers. Rebuilding the whole layout would discard expanded details and other
reading state. Already requested local previews and originals are left alone when
another asset arrives.

A topic's first-read failure uses upstream `ChatActionCell` service styling and
localized recovery text, not a full-screen progress label or raw exception message.
Its tap retries the read or opens visible session recovery as appropriate. Paging
failures retain readable posts and use the existing upstream Bulletin instead.

Inline emoji fallback-to-artwork upgrades need new rich runs, so they regenerate
the body inside the retained message while restoring expanded details and the viewport.
A late reply target updates only the first bubble's preview, keeping its body and
pending media callbacks. A capsule title arriving after initial layout requests
measurement again so its marquee layer does not retain the empty title's short bounds.

Profile header transforms are recomputed after child layout, not only when a
scroll changes the header offset. Pinned activity pages follow upstream's nested
scroll ownership: forward only the outer list's remainder into the current page,
and unwind that page on negative deltas before moving the outer profile. An inner
list's own nested request must not be fed back into itself. Async profile updates
wait for both default and custom entry transitions; the loading rows follow the
same fade/slide and begin below the resting header. Missing avatar bytes use
upstream initials rather than leaving an empty header.

### Homepage search

`DialogsActivity`'s homepage field-to-ActionBar transition is distinct from
`ChatActivity`'s in-topic search. These entrances deliberately do not look identical.
[SearchPanel](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/search/SearchPanel.java)
wires each Discourse result page's `RecyclerListView` to its `StickerEmptyView`;
the list owns loading/empty/result visibility. The skeleton uses the same tabs
inset through `FlickerLoadingView.setPaddingTop`, not ordinary View padding.
The result pager fills the screen; scrollable padding places the first row below
the ActionBar and tabs without clipping scrolled rows out of the header. While
searching, the screen's glass host records that result-only pager, so the field,
tabs and header band sample the same live rows without recording their own controls.
Closing restores the feed as the glass source. Header and navigation insets update
the tabs, rows and loading state together; lower-performance devices retain the
upstream solid-color fallback.
New results use `RecyclerItemsEnterAnimator` and upstream text highlighting;
topic/post rows use message-result `DialogCell` binding rather than altering
`CustomDialog`. A changed query clears old generic hits immediately; cached
categories and tags refilter immediately. Request generations fence late answers,
including an A → B → A query, closing and destruction. Telegram transport remains off.

Discourse categories remain site categories, not Telegram's hint-peer carousel.
Search includes child categories; explicitly choosing one adds its own feed tab
instead of silently opening the parent. Top-level tabs remain the default home
strip. The [search flow](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/search/SearchTest.kt)
checks query replacement, clear/retry, stale answers, category navigation and Back
through the production-wired host harness. Real IME/GPU/gesture timing still needs
a device pass.

### In-topic search

[TopicSearch](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/search/TopicSearch.kt)
owns the query and result paging inside the existing topic screen. Its input is
the ActionBar's own `ActionBarMenuItem` search field, not a second homepage field
placed over a hidden bar. The reader reaches it from the topic overflow's
`话题内搜索` row; the search item itself is held hidden so the bar's right side
stays the single overflow capsule. The item is hidden again after the field
collapses, because its own `toggleSearch` re-shows it on close. Opening also
drives the bar's search width factor (`ActionBar.setSearchFactor`) with the
capsule's own fade, the way `ChatActivity` drives it from its search animation
(ChatActivity:46603-46608). Without that factor `ActionBar` keeps the center glass
capsule at the avatar container's own width and re-centres it, so the hint is
drawn left of the capsule (the reported `搜索当前话题` overflow); at factor 1 the
capsule spans the bar where the field is laid across the menu
(ActionBar:2244). The overflow is a focusable window of its own, so the field's
own `showKeyboard` is dropped on the first open while that window still has focus;
the field asks the IME again once its window has focus, not on a fixed delay. The
back capsule closes search and restores the reading window and draft; the clear
button drops the query and hits but keeps search open. IME search lands on the
selected hit without pushing another topic screen. An unasked search reports
nothing in the count, never `没有结果`.

List mode uses upstream `DialogCell` rows in `ChatActivitySearchContainer` over
the wallpaper-backed translucent search-list surface. Its top and bottom fades
let rows pass behind the floating controls. The rounded bottom result capsule
uses upstream animated labels, with the toggle's measured width reserved before
laying out the count. List mode names the result count; message mode names the
selected result and uses upstream search-arrow controls. A paged answer reports
the loaded count, never an invented total. Calendar and author buttons are absent
until their filters have product behavior; they are not decorative shortcuts.

## Upstream updates

The subtree import is the source pin. Materialize native dependencies from its
gitlinks with `telegram-deps.sh`; do not borrow arbitrary `.so` files from another
checkout. Update only for a product goal, compare host hooks to the new source and
run the hook, app and device gates appropriate to the change.
The [legacy vendor workflow](../VENDOR.md) does not govern this subtree.

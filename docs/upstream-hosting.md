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
  `LaunchActivity`: current fragment, tabs and overlay. This preserves upstream
  `ArticleViewer` sheets, minimization and restoration without a Telegram activity.
- Editor and mention adapters bind product behavior to an individual component,
  not a global callback that outlives its owning screen.
- Other hooks cover concrete host differences such as local media loading and
  in-app-browser preferences. They do not authorize redesigning upstream renderers.

Ordinary links default to the in-app browser; the reader can disable this in settings.
[LinkOpenSettings](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/settings/LinkOpenSettings.kt)
owns that local choice because Telegram's server-delivered browser settings never
arrive in this host.

## Startup and lifecycle are part of hosting

[NativeDoApplication](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/app/NativeDoApplication.java)
extends upstream's loader but does not invoke its full account-oriented
`postInitApplication`. It explicitly initializes the configuration, LiteMode,
locale, shared preferences and screen state used by UI code. Relying on early
static initializers can leave a capable device in the low-performance fallback.

[NativeDoActivity](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/app/NativeDoActivity.java)
owns navigation, the browser host, system bars, session gate and foreground
MessageBus lifetime. It restores a narrow navigation state only after session
bootstrap, ignores callbacks to a destroyed shell, and releases shared viewers
at the appropriate final-instance boundary.

Do not treat an upstream view as a drop-in object without its host lifecycle.
Composer pause/resume/destroy, theme delegates, system/IME insets, viewer state
and Back arbitration matter as much as constructor arguments. A predictive Back
probe must not commit a screen change; an open input panel, selection or viewer
gets its own Back handling before page navigation.

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

## Upstream updates

The subtree import is the source pin. Materialize native dependencies from its
gitlinks with `telegram-deps.sh`; do not borrow arbitrary `.so` files from another
checkout. Update only for a product goal, compare host hooks to the new source and
run the hook, app and device gates appropriate to the change.
The [legacy vendor workflow](../VENDOR.md) does not govern this subtree.

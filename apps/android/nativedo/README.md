# NativeDo (`:nativedo`)

The linux.do client. This module is our app; everything under `apps/android/telegram` is upstream
Telegram, brought in as a git subtree and never edited except for single-line hooks.

## Layout

```
apps/android/
├── nativedo/                            ← our app (this module)
│   └── src/main/java/io/github/baba983/nativedo/
│       ├── app/                         ← shell: NativeDoActivity, NativeDo (the session holder)
│       ├── core/                        ← Discourse network, session, cookies, storage, models, HTML
│       ├── feature/<screen>/data/       ← repositories and local stores per screen
│       └── topic/                       ← the topic list and topic screens
└── telegram/                            ← upstream Telegram 12.10.5 (git subtree), plus
    └── TMessagesProj/src/main/java/io/github/baba983/nativedo/hooks/   ← NativeDoHooks
```

Two rules keep the subtree mergeable:

- **Our code never lives under `apps/android/telegram`.** The one exception is
  `NativeDoHooks`, a new file: a subtree pull cannot conflict with a file upstream does not have.
- **Upstream files get one line at most.** Today there are three: `ConnectionsManager.init`,
  `ConnectionsManager.isTestBackend` and `MessagesStorage.openDatabase`, each an early return
  guarded by a `NativeDoHooks` flag. `git diff` against the subtree merge shows them.

## Why a shell instead of upstream's LaunchActivity

Upstream's `LaunchActivity` is Telegram's onboarding flow, not a shell. It calls
`ApplicationLoader.postInitApplication()` (which builds a `MessagesController` and a
`MediaController` per account and opens Telegram's database), keys its whole branch on
`UserConfig.getInstance(...).isClientActivated()`, and hands back `new LoginActivity()` when no
Telegram account exists. The hooks that would turn that off are Telegram's entire account model.

So `NativeDoActivity` hosts upstream's `DrawerLayoutContainer` and `ActionBarLayout` directly and
pushes our fragments into them, which is the same thing upstream does one level up. Its
`ActionBarLayout` is built with `main = false`: the "main" layout is Telegram's tabbed shell and
dereferences `LaunchActivity` while creating its bottom-sheet tab bar.

## What is ours and what is upstream's

| Surface | Owner |
|---|---|
| Network, login (WebView + cookies), session, CSRF, storage | ours (`core/`) |
| Topic list rows | upstream `DialogCell` + `DialogCell.CustomDialog` |
| Post rendering | upstream `ChatMessageCell` from `TL_message.rich_message` |
| Photo viewer, link opening, theme | upstream `PhotoViewer`, `Browser`, `Theme` |
| Shell (drawer + navigation) | upstream `DrawerLayoutContainer` + `ActionBarLayout` |

`topic/DiscourseRows` is the translation between the two: Discourse models in, `TLRPC`/`MessageObject`
out. `topic/DiscourseCookedHtml` prepares a cooked post body for upstream's `RichHtml` importer.

## The Discourse cooked-HTML wrapper

Upstream's `RichHtml` understands `<table>`, but only as a direct child of the node it walks. Any
other `<div>` is treated as a paragraph, and Discourse wraps every table in `<div class="md-table">`,
so the table arrives one level down and is flattened into one run of text per row. Unwrapping that
wrapper is the whole fix; `DiscourseCookedHtmlTest` pins both halves.

## Tests

```bash
cd apps/android/telegram
./gradlew :nativedo:testDebugUnitTest        # 14 tests, ~20 s
./gradlew :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a
```

The screens are rendered on the JVM: Robolectric with `@GraphicsMode(NATIVE)` plus generated shadows
for the natives in `libtmessages.49.so` (`src/test/java/.../harness/shadows`, regenerate with
`.amp/in/artifacts/spike/gen_shadows.py`). Two `@Config` settings are load-bearing:
`instrumentedPackages = ["org.telegram"]` (Robolectric does not instrument those packages by
default, so the natives stay real) and `shadows = [...]` (shadows are not discovered from the
classpath). `harness/FakeDiscourseSite` serves `fixtures/discourse` over a local HTTP server, so the
screens are driven through the real client rather than stubbed models.

Screenshots land in `.amp/in/artifacts/nativedo/`. Every screen test also asserts that the stock
code initialised no tgnet, opened no Telegram database and reached no tgnet native;
`NativeDoHooksTest` proves those assertions are not vacuous by calling the same paths with the hooks
off.

The upstream native build needs the 15 submodule trees that `git subtree` drops; fetch them with
`.amp/in/artifacts/spike/fetch_submodules.sh`.
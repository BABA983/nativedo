# Code Layout & Language Conventions

This document defines **where code lives** and **what language it's written
in**, complementing the per-file vendoring rules in [`AGENTS.md`](../AGENTS.md)
and [`VENDOR.md`](../VENDOR.md).

> If a rule here contradicts `AGENTS.md` or `VENDOR.md`, the latter win for
> vendoring concerns. This doc is layout + language.

## Two-module layout

Vendored Telegram code lives in a **standalone Gradle module**, `:telegram`.
NativeDo product code lives in `:app`. `:app` depends on `:telegram`; the
reverse is forbidden by Gradle and by `scripts/vendor-isolation-check.sh`.

```
apps/android/
├── app/                                ← NativeDo product (:app module)
│   └── src/main/java/io/github/baba983/nativedo/
│       ├── core/                       ← Discourse models, network, session,
│       │                                 prefs, DI container. Kotlin preferred.
│       ├── feature/                    ← ViewModels, Repositories per screen.
│       │                                 Kotlin-only.
│       │   ├── home/
│       │   ├── topic/
│       │   └── profile/
│       └── ui/                         ← NativeDo-original views, Compose
│           │                             screens, and *native ports* of
│           │                             Telegram-derived widgets that bind
│           │                             to NativeDo data models.
│           ├── topic/                  ← TopicCell (native port of DialogCell),
│           │                             TopicListScreen.kt, etc.
│           ├── main/                   ← Custom ActionBar + tab host.
│           ├── browser/                ← WebView-based session login,
│           │                             Cloudflare fallback.
│           └── …
│
└── telegram/                           ← Vendored Telegram code (:telegram module)
    └── src/main/java/org/telegram/     ← Upstream package preserved verbatim.
        ├── messenger/                  ← org.telegram.messenger.*
        │                                 (AndroidUtilities, ImageLoader,
        │                                  ImageReceiver, NotificationCenter, …)
        ├── tgnet/                      ← org.telegram.tgnet.*
        │                                 (TLObject, TLRPC, ConnectionsManager, …)
        └── ui/                         ← org.telegram.ui.*
            ├── ActionBar/              ← Theme, BaseFragment, ActionBarLayout, …
            ├── Cells/                  ← EmptyCell, GraySectionCell, HeaderCell, …
            ├── Components/             ← RecyclerListView, AnimatedTextDrawable,
            │                             PullForegroundDrawable, ItemOptions, …
            └── Stories/                ← upstream Stories.*
```

## Why a separate module?

Putting vendored code in `:telegram` instead of inside `:app/.../telegram/`
buys three concrete things:

1. **Mechanical dependency direction.** `:telegram` has no `implementation
   project(":app")`, so the Java compiler refuses any `import
   io.github.baba983.nativedo.*` inside vendored code. The "vendor isolation"
   rule isn't an honor system anymore.
2. **Package parity with upstream.** Files in `:telegram` stay in `org.telegram.*`.
   That means a vendored file diffs cleanly against `vendor/telegram-android/...`
   — only host wiring (imports the host doesn't have, theme keys, AndroidX
   substitutions) shows up as drift, never a global package rewrite.
3. **Incremental build win.** When you only edit NativeDo product code, the
   `:telegram` classpath is up-to-date and `:app` recompiles in seconds even
   though `:telegram` is large.

## What goes where: decision tree

```diagram
                  ╭──────────────────────────────────────╮
                  │ Was this file copied or substantially │
                  │ adapted from a Telegram source file?  │
                  ╰────────────────┬─────────────────────╯
                                   │
                       ╭───────────┴───────────╮
                       │                       │
                      YES                      NO
                       │                       │
                       ▼                       ▼
       ╭─────────────────────────╮   ╭─────────────────────╮
       │ Does it bind to a       │   │ NativeDo-original.  │
       │ NativeDo data model     │   │ Lives in :app under │
       │ (TopicSummary, …)?      │   │   core/             │
       ╰────────────┬────────────╯   │   feature/          │
                    │                 │   ui/              │
        ╭───────────┴───────────╮     │                     │
       NO                      YES    │ No GPL header.      │
        │                       │     │ No manifest entry.  │
        ▼                       ▼     ╰─────────────────────╯
╭───────────────╮     ╭───────────────────╮
│ :telegram     │     │ :app native port  │
│ module.       │     │   io.github.…ui.* │
│ Package stays │     │ Mirrors upstream  │
│ org.telegram* │     │ measurement/draw/ │
│ Manifest +    │     │ animation shape,  │
│ GPL header.   │     │ binds NativeDo    │
│               │     │ data. Manifest +  │
│               │     │ GPL header.       │
╰───────────────╯     ╰───────────────────╯
```

The "substantially adapted" test (per AGENTS.md): if you started from an
upstream file and kept its layout / measurement / drawing / state machine
structure, even if you renamed methods and changed callbacks, that's
vendored-adapted. If you only borrowed the *idea*, it's NativeDo-original.

## Language choice

| Code | Language | Rule |
|---|---|---|
| Anything in `:telegram` | **Java** | Mandatory. Upstream is Java. Kotlin-ising vendored code breaks diff/re-sync. |
| Business logic in `:app` (ViewModel, Repository, Discourse models, parsers) | **Kotlin** | Mandatory. Use coroutines/Flow, `data class`, `sealed`. |
| Native ports in `:app/ui/**` (Telegram-derived custom views) | **Java** | If you're authoring `dp(...)`, `onMeasure`, `onDraw`, and want to read side-by-side with upstream Cell sources, stay in Java. Kotlin's property access (`paint.color =` vs `paint.setColor(...)`) slows comparison. |
| Façades over vendored APIs (no measurement/draw code) | **Kotlin** by default | Switch to Java only if the façade must `implements` a vendored Java interface heavy on callbacks. |
| Tests | **Kotlin** | JUnit4 + Truth + Turbine + MockWebServer + Robolectric. |
| Resources, XML, AndroidManifest | n/a | unchanged |

When in doubt → **Kotlin**. Don't refactor existing Kotlin to Java or vice
versa without a real reason.

## Façade / Bridge pattern

Vendored Telegram code exposes MTProto-shaped APIs
(`setForUserOrChat(TLObject, ...)`, `openPhoto(MessageObject, ...)`).
NativeDo business code must **NOT** call these directly. Instead:

1. Pick a NativeDo-side façade location: `ui/avatar/AvatarImageBinder`,
   `ui/photo/NativeDoPhotoViewer`, etc. (Always in `:app`, never in
   `:telegram`.)
2. Façade exposes a NativeDo-shaped API (URL strings, `PhotoSpec`,
   `AvatarSpec`).
3. Inside the façade, call the **URL-shaped overloads** of vendored APIs
   (e.g., `ImageReceiver.setImage(ImageLocation.getForPath(url), ...)`),
   never the `TLObject` overloads.

```diagram
╭─────────────────────╮     ╭──────────────────────╮     ╭──────────────────╮
│ feature/ ViewModel  │────▶│ ui/<purpose>/ Façade │────▶│ :telegram        │
│ Repository          │     │ (URL-shaped only,    │     │ org.telegram.*   │
│ (Kotlin)            │     │  no MTProto types)   │     │ (Java, vendored) │
╰─────────────────────╯     ╰──────────────────────╯     ╰──────────────────╯
```

Rationale: when we bump the vendored pin and a method signature changes,
the blast radius is the façade only.

### When `:telegram` needs NativeDo data

Define an **SPI interface inside `:telegram`** and implement it in `:app`.
Inject the impl at app start, never import the impl from inside `:telegram`.

| Need | Pattern | Example |
|---|---|---|
| Image loader (Coil) | SPI interface in `:telegram`, Coil impl in `:app` | `ImageReceiver.Host` ← `CoilImageReceiverHost` |
| Theme/locale colors that NativeDo overrides | Same SPI pattern | `Theme.ResourcesProvider` |
| Callback to business logic | Use vendored callback mechanism; NativeDo implements the interface | `PhotoViewerProvider`, `PageBlocksAdapter` |
| Network stack | Keep vendored code's own HTTP path; don't cross-reference NativeDo's | `ImageLoader` uses its own OkHttp |
| Replace a vendored implementation | Native port in `:app`, business calls the port directly | `TopicCell` replaces `DialogCell` |

## Stub vs Throw policy

Some vendored files reference MTProto types we don't use (`TLObject`,
`TL_photo`). They need to **compile**, but they should never **execute**.

| Situation | What to do |
|---|---|
| Empty type needed for compile (e.g. `TLObject`) | Stub: empty abstract class or interface, no methods, no fields. Place under the corresponding upstream package in `:telegram`. Manifest entry with `notes: "MTProto stub — empty type for compile only"`. |
| Vendored method that takes `TLObject` and we never call it (e.g. `setForUserOrChat`) | **Keep upstream body unchanged**. Don't stub the method. The dead branch is harmless and re-sync stays clean. |
| Same as above, but a misroute would cause a silent-but-wrong UI (e.g. blank avatar) | Replace the method body with `throw new UnsupportedOperationException("nativedo: use AvatarImageBinder")`. Manifest `notes` must record this. |
| A vendored helper returns a hardcoded default just to "make tests pass" | **Forbidden**. Either keep upstream behavior, or delete the method and switch the file to `port_mode = native` with `notes`. |

**No-op stubs that return `null`/`0`/empty are the worst option** — they
turn build-time errors into runtime silent failures. Avoid them.

## Dependency direction

```diagram
╭──────────────────────────────────╮
│ :app/feature/                    │  Kotlin, NativeDo
│   ViewModel, Repository, Nav     │
╰────────────────┬─────────────────╯
                 │
                 ▼
╭──────────────────────────────────╮
│ :app/ui/                         │  Kotlin/Java, NativeDo
│   façades, native ports,         │
│   Compose screens, custom views  │
╰────────────────┬─────────────────╯
                 │
                 ▼
╭──────────────────────────────────╮
│ :app/core/                       │  Kotlin/Java, NativeDo
│   model, network, session,       │
│   storage, di                    │
╰────────────────┬─────────────────╯
                 │
                 ▼
╭──────────────────────────────────╮
│ :telegram                        │  Java only, vendored
│   org.telegram.{messenger,tgnet, │  GPL-2.0+ headers
│                 ui.*}            │  mirrors upstream paths
╰────────────────┬─────────────────╯
                 │
                 ▼
╭──────────────────────────────────╮
│ java.* / android.* / androidx.*  │  system libs
╰──────────────────────────────────╯
```

**Hard rule: `:telegram` MUST NOT import any `io.github.baba983.nativedo.*`
package.** The Java compiler enforces this because `:telegram` doesn't
declare a dep on `:app`. `scripts/vendor-isolation-check.sh` catches stray
import lines that slip in through comments or copy-paste.

### Why this matters

1. **Re-sync safety** — when you `cp` a new upstream version into
   `:telegram`, any NativeDo import you added gets wiped out. If the
   rule is violated, the next sync silently breaks.
2. **No circular deps** — Gradle enforces the DAG at the module boundary.
3. **GPL boundary** — `:telegram` is GPL-2.0+ derivative. If it imported
   NativeDo-original code, the boundary blurs and license audit gets harder.

## Migration checklist (when adding or moving a vendored file)

When you `cp` a new file from `vendor/telegram-android/` into `:telegram`:

1. `cp vendor/telegram-android/TMessagesProj/src/main/java/org/telegram/<path>
       apps/android/telegram/src/main/java/org/telegram/<path>`
2. No `sed` package rewrite needed — package stays `org.telegram.*`.
3. Drop any upstream import the host can't satisfy (or replace with the
   AndroidX/host equivalent).
4. Add a manifest entry under `vendor-manifest.json`:
   `local: "telegram/src/main/java/org/telegram/<path>"`,
   `upstream: "org/telegram/<path>"`,
   `vendored_at: "<pinned_commit>"`,
   `modified: false` (true if you had to drop imports / change theme keys),
   `port_mode: "exact"`.
5. `scripts/vendor-isolation-check.sh` — must exit 0.
6. `./apps/android/gradlew -p apps/android :telegram:compileDebugJavaWithJavac --quiet`
7. Commit source + manifest update in **one commit**.

When you `git mv` a vendored file within `:telegram` (rare — only happens
if upstream itself moved the file): repeat the above with the new path,
plus `sed` over `import` statements in any file that referenced the old
location.

## Anti-patterns to avoid

- Two copies of the same vendored file (e.g., one in `:app` and one in
  `:telegram`). Pick `:telegram`, delete the other.
- Vendored code with package `io.github.baba983.nativedo.*`. It belongs in
  `:telegram` with package `org.telegram.*`.
- NativeDo-original file with a GPL header. Remove the header; AGENTS.md
  is explicit that originality is not signaled by the header, vendoring is.
- Kotlin file inside `:telegram`. Convert back to Java.
- Façade that exposes vendored types in its public signature (e.g.,
  `NativeDoPhotoViewer.open(List<PhotoViewer.PhotoEntry>)`). Use NativeDo
  types in public signatures and translate inside the façade.
- `:telegram` calling back into `:app` directly. Use an SPI interface
  defined in `:telegram` and inject the impl from `:app` at app start.

## When in doubt

Read `AGENTS.md` → `VENDOR.md` → this file → ask. The single hardest
question is "is this file vendored-adapted or NativeDo-original?" — when
unsure, look at the upstream file you started from. If 50%+ of method
shape, constants, or draw/measurement code lines up, it's vendored.

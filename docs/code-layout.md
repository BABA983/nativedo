# Code Layout & Language Conventions

This document defines **where code lives** and **what language it's written
in**, complementing the per-file vendoring rules in [`AGENTS.md`](../AGENTS.md)
and [`VENDOR.md`](../VENDOR.md).

> If a rule here contradicts `AGENTS.md` or `VENDOR.md`, the latter win for
> vendoring concerns. This doc is layout + language.

## Why layout matters

We vendor a lot of Telegram source. Mixing vendored Telegram code with
NativeDo-original code in the same package makes three things hard:

1. `scripts/vendor-diff.sh` — needs a stable local↔upstream path mapping.
2. License clarity — every file in `telegram/**` is GPL-2.0+ derivative.
3. Re-sync risk — when bumping the upstream pin, anything outside `telegram/**`
   is safe; everything inside needs review.

So the rule is simple: **vendored Telegram code lives under
`io.github.baba983.nativedo.telegram.*` only, mirroring upstream's
package structure.** Everything else is NativeDo-original.

## Directory map

```
apps/android/app/src/main/java/io/github/baba983/nativedo/
│
├── telegram/                ← ALL vendored Telegram code lives here.
│   │                          Package structure MIRRORS upstream
│   │                          (org.telegram.* → nativedo.telegram.*).
│   ├── messenger/           ← org.telegram.messenger.*
│   │                          (AndroidUtilities, ImageLoader, ImageReceiver,
│   │                           NotificationCenter, FileLoader, …)
│   ├── tgnet/               ← org.telegram.tgnet.*
│   │                          (TLObject, TLRPC, ConnectionsManager, …)
│   ├── ui/                  ← org.telegram.ui.*
│   │   ├── ActionBar/       ← Theme, BaseFragment, AlertDialog, …
│   │   ├── Cells/           ← EmptyCell, GraySectionCell, HeaderCell, …
│   │   │                      AND adapted-from-upstream cells
│   │   │                      (OpPostCell ← ArticleViewer,
│   │   │                       ReplyPostCell ← ChatMessageCell,
│   │   │                       TopicCell ← DialogCell).
│   │   ├── Components/      ← RecyclerListView, AvatarDrawable,
│   │   │                      AnimatedFileDrawable, ItemOptions, …
│   │   ├── Stories/         ← upstream Stories.*
│   │   └── PhotoViewer.java ← upstream top-level UI classes
│   └── …                    ← any other upstream package we adopt
│
├── feature/                 ← NativeDo business code (ViewModel,
│                              Repository, navigation graph).
│                              Kotlin-only.
│
├── core/                    ← NativeDo non-UI utilities that are NOT
│                              vendored (Discourse parsers, prefs, etc.).
│                              Kotlin preferred.
│
├── ui/                      ← NativeDo-original UI: façades, custom views
│                              we wrote from scratch, NativeDo-specific
│                              widgets. NO GPL headers here.
│   ├── avatar/              ← AvatarImageBinder + AvatarSpec (façade)
│   ├── photo/               ← NativeDoPhotoViewer (façade over
│   │                          telegram.ui.PhotoViewer)
│   └── …
│
├── MainActivity.kt          ← NativeDo entry points.
└── NativeDoApp.kt
```

### Naming inside `telegram/`

Use PascalCase package names (`Cells`, `Components`, `ActionBar`, `Stories`)
**identical to upstream** — not the Java-convention lowercase. The cost is a
few package names that look unusual; the benefit is `vendor-diff.sh` keeps
working without a path-translation table, and a re-sync just maps
`TMessagesProj/src/main/java/org/telegram/` → `io/github/baba983/nativedo/telegram/`
one-to-one.

## What goes where: decision tree

```diagram
                  ┌─────────────────────────────────────┐
                  │ Was this file copied or substantially
                  │ adapted from a Telegram source file? │
                  └────────┬────────────────────────────┘
                           │
                ┌──────────┴──────────┐
                │                     │
              YES                    NO
                │                     │
                ▼                     ▼
   ┌──────────────────────┐  ┌─────────────────────┐
   │ Goes under           │  │ NativeDo-original.  │
   │ telegram/<upstream   │  │ Lives under one of: │
   │ package>/.           │  │   feature/          │
   │                      │  │   core/             │
   │ Must have:           │  │   ui/<purpose>/     │
   │  - GPL header        │  │                     │
   │  - vendor-manifest   │  │ NO GPL header.      │
   │    entry             │  │ NO manifest entry.  │
   │  - upstream-mirrored │  │                     │
   │    package name      │  │                     │
   └──────────────────────┘  └─────────────────────┘
```

The "substantially adapted" test (per AGENTS.md): if you started from an
upstream file and kept its layout / measurement / drawing / state machine
structure, even if you renamed methods and changed callbacks, that's
vendored-adapted. If you only borrowed the *idea*, it's NativeDo.

## Language choice

| Code | Language | Rule |
|---|---|---|
| Anything under `telegram/**` | **Java** | Mandatory. Upstream is Java. Kotlin-ising vendored code breaks diff/re-sync tooling. |
| Business logic, ViewModel, Repository, Discourse models, parsers | **Kotlin** | Mandatory. Use coroutines/Flow, `data class`, `sealed`. |
| `ui/**` façades over vendored APIs | **Kotlin** by default | Switch to Java if the façade must `implements` a vendored Java interface heavy on callbacks (signature parity matters more than syntax sugar). |
| Custom Cells / Views that sit alongside vendored Cells | **Java** | If you're authoring `dp(...)`, `onMeasure`, `onDraw`, and want to read side-by-side with upstream Cell sources, stay in Java. Kotlin's property access changes the look of `paint.setColor(...)` vs `paint.color =` and slows comparison. |
| Tests | **Kotlin** | Use JUnit4 + Truth/AssertK. |
| Resources, XML, AndroidManifest | n/a | unchanged |

When in doubt → **Kotlin**. The above table is a guide; don't refactor
existing Kotlin to Java or vice versa without a real reason.

## Façade / Bridge pattern

Vendored Telegram code exposes MTProto-shaped APIs
(`setForUserOrChat(TLObject, ...)`, `openPhoto(MessageObject, ...)`).
NativeDo business code must **NOT** call these directly. Instead:

1. Pick a NativeDo-side façade location: `ui/avatar/AvatarImageBinder`,
   `ui/photo/NativeDoPhotoViewer`, etc.
2. Façade exposes a NativeDo-shaped API (URL strings, `PhotoSpec`,
   `AvatarSpec`).
3. Inside the façade, call the **URL-shaped overloads** of vendored APIs
   (e.g., `ImageReceiver.setImage(ImageLocation.getForPath(url), ...)`),
   never the `TLObject` overloads.

```
NativeDo business  ─────►  Façade (ui/avatar, ui/photo)  ─────►  Vendored (telegram/**)
                              │
                              ▼
                   URL-shaped overloads only,
                   no MTProto types touched.
```

Rationale: when we bump the vendored pin and a method signature changes,
the blast radius is the façade only.

## Stub vs Throw policy

Some vendored files reference MTProto types we don't use (`TLObject`,
`TL_photo`). They need to **compile**, but they should never **execute**.

| Situation | What to do |
|---|---|
| Empty type needed for compile (e.g. `TLObject`) | Stub: empty abstract class or interface, no methods, no fields. Place in `telegram/tgnet/`. Manifest entry with `notes: "MTProto stub — empty type for compile only"`. |
| Vendored method that takes `TLObject` and we never call it (e.g. `setForUserOrChat`) | **Keep upstream body unchanged**. Don't stub the method. The dead branch is harmless and re-sync stays clean. |
| Same as above, but a misroute would cause a silent-but-wrong UI (e.g. blank avatar) | Replace the method body with `throw new UnsupportedOperationException("nativedo: use AvatarImageBinder")`. Manifest `notes` must record this. |
| A vendored helper returns a hardcoded default just to "make tests pass" | **Forbidden**. Either keep upstream behavior, or delete the method and switch the file to `port_mode = native` with `notes`. |

**No-op stubs that return `null`/`0`/empty are the worst option** — they
turn build-time errors into runtime silent failures. Avoid them.

## Migration checklist (when moving a file)

When you `git mv` a vendored file between directories (e.g.,
`core/ImageLoader.java` → `telegram/messenger/ImageLoader.java`):

1. `git mv` to the new path.
2. `sed -i '' 's|^package <old>;|package <new>;|' <file>`
3. `rg -l "<old fully-qualified import>" ... | xargs sed -i '' 's|<old>|<new>|g'`
4. Update `vendor-manifest.json` `local` field.
5. `./scripts/vendor-status.sh` — must be clean (no MISSING / DRIFT).
6. `./apps/android/gradlew -p apps/android :app:compileDebugKotlin --quiet`
7. Commit move + manifest update + import rewrites **in one commit**.

If a vendored file existed without a manifest entry, **add it in the same
commit as the move** — see AGENTS.md rule 1.

## Dependency direction

Packages may only depend on packages **below** them in this stack.
An arrow from A → B means "A may import B":

```
┌──────────────────────────┐
│ feature/ (ViewModel, Repository) │  Kotlin, NativeDo
└─────────────┬────────────┘
              │
              ▼
┌──────────────────────────┐
│ ui/ (façades, NativeDo UI) │  Kotlin/Java, NativeDo
│   avatar/, profile/, …     │  NO GPL header
└─────────────┬────────────┘
              │
              ▼
┌──────────────────────────┐
│ core/ (NativeDo utilities) │  Kotlin/Java, NativeDo
│   model, network, session   │  NO GPL header
│   storage, di, ui           │
└─────────────┬────────────┘
              │
              ▼
┌──────────────────────────┐
│ telegram/ (vendored)       │  Java only, GPL header
│   messenger/ tgnet/ ui/    │  mirrors upstream paths
└─────────────┬────────────┘
              │
              ▼
┌──────────────────────────┐
│ java.* / android.* /       │  system libs
│ androidx.*                 │
└──────────────────────────┘
```

**Hard rule: `telegram/**` MUST NOT import any NativeDo package
(`core.*`, `feature.*`, `ui.*`).** The only allowed imports are:

- `java.*`, `android.*`, `androidx.*`
- Other `io.github.baba983.nativedo.telegram.*` classes
- Vendored SPI interfaces defined inside `telegram/` itself

### Why this matters

1. **Re-sync safety** — when you `cp` a new upstream version into
   `telegram/`, any NativeDo import you added gets wiped out. If
   the rule is violated, the next sync silently breaks.
2. **No circular deps** — `core/` uses `telegram/Utilities`; if
   `telegram/Utilities` also used `core/SomeHelper`, you'd have
   a cycle.
3. **GPL boundary** — `telegram/` is GPL-2.0+ derivative. If it
   imports NativeDo-original code, the boundary blurs and
   license audit gets harder.

### When telegram/ needs NativeDo data

| Need | Pattern | Example |
|---|---|---|
| Data from Discourse | Define SPI interface inside `telegram/`; implement in `core/` or `ui/` | `telegram.bridge.UserResolver` → `core.discourse.DiscourseUserResolver` |
| Callback to business logic | Use vendored callback mechanism; NativeDo implements the interface | `PhotoViewerProvider`, `PageBlocksAdapter` |
| Network stack | Keep vendored code's own HTTP path; don't cross-reference NativeDo's | `ImageLoader` uses its own OkHttp, not `core.network.*` |
| Replace a vendored implementation | Write a façade in `ui/`; business code calls façade only | `ui/photo/NativeDoPhotoViewer` wraps `telegram.ui.PhotoViewer` |

### Known placement debt

The following directories contain Telegram-derived code that lives
under `ui/` instead of `telegram/ui/`. Moving them fixes all current
vendor-isolation violations at once. Track progress here:

- [ ] `ui/actionbar/` → `telegram/ui/ActionBar/` (16 files)
- [ ] `ui/components/` → `telegram/ui/Components/` (32 files)
- [ ] `ui/cells/` → `telegram/ui/Cells/` (9 files)

Once moved, the `telegram/ → ui/` violations below disappear
automatically. Until then, `scripts/vendor-isolation-check.sh`
reports them as known-exceptions.

### Enforcement

Run `scripts/vendor-isolation-check.sh` before each commit that
touches `telegram/`. CI should also run it.

## Anti-patterns to avoid

- Two copies of the same vendored file (e.g., `core/ImageLoader.java` AND
  `telegram/messenger/ImageLoader.java`). Pick one — always the
  `telegram/` location — and delete the other.
- Vendored file in a NativeDo-original directory (e.g.,
  `core/AndroidUtilities.java` with GPL header). Move to `telegram/`.
- NativeDo-original file with a GPL header. Remove the header; AGENTS.md
  is explicit that originality is not signaled by the header, vendoring is.
- Kotlin file inside `telegram/**`. Convert back to Java.
- Façade that exposes vendored types in its public signature (e.g.,
  `NativeDoPhotoViewer.open(List<PhotoViewer.PhotoEntry>)`). Use NativeDo
  types in public signatures and translate inside the façade.

## When in doubt

Read `AGENTS.md` → `VENDOR.md` → this file → ask. The single hardest
question is "is this file vendored-adapted or NativeDo-original?" — when
unsure, look at the upstream file you started from. If 50%+ of method
shape, constants, or draw/measurement code lines up, it's vendored.

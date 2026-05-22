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

# AGENTS.md

## Telegram-Derived Code

Telegram source lives in the standalone `:TMessagesProj` Gradle module at
`apps/android/TMessagesProj/src/main/java/org/telegram/...` (upstream package kept,
so files diff cleanly and imports stay byte-identical). `:app` depends on
`:TMessagesProj`; a smaller class of **native ports** in `:app` mirrors an upstream
component's shape but binds to NativeDo models (e.g.
`io.github.baba983.nativedo.ui.topic.TopicCell` ←
`org/telegram/ui/Cells/DialogCell.java`).

We are a linux.do client, not a Telegram fork: the snapshot advances only when
a product goal needs upstream code, never on every upstream release.

### Where the truth lives

- **`vendor-overlay.json`** — the only human-owned vendor file, and it is small:
  the pin, the sync policy, and the *exceptions* (`native`, `upstream_ports`,
  `derived_from`, `forced_exact`, `reference`, `resources`).
- **Everything else is derived** from that overlay + the pristine checkout +
  the files on disk. There is no committed lock file, no per-file `modified`
  flag, and no per-file notes to maintain. `scripts/vendor-index.sh` prints the
  derived provenance for any path.
- **`scripts/vendor-guard.sh`** — run it before committing any change under
  `apps/android/TMessagesProj/**` or to the overlay. It reports problems with the
  command that fixes them; `--compile` also builds `:TMessagesProj`.
- **`VENDOR.md`** — the workflow (setup, diff, bump, check the pristine tree).
- **`docs/code-layout.md`** — directory layout, language choice, façade policy.
  Read before moving files or adding a façade.

### Rules

1. **`org.telegram.*` code is an upstream mirror.** Before changing any of it,
   read the upstream file: `scripts/vendor-diff.sh <local-path>` (add `--pinned`
   to see how far the file has drifted from the pinned copy). Don't rewrite
   semantics from memory; if you need different behavior, make it lazy.
2. **Our own code must not hide at an upstream path.** If a file under
   `:TMessagesProj` is NativeDo code (a stub, shim, or re-designed port), list it in
   the overlay's `native` array. That is the difference between "copy we
   re-sync" and "our file that happens to sit in `org.telegram.*`". A
   `:TMessagesProj` file with no upstream counterpart at the pin is classified as a
   shim automatically.
3. **Respect `forced_exact`.** Overlay entries list `gesture`, `animation`,
   `measurement`, `drawing`, `recycling` for components whose hand-feel must
   stay upstream-shaped: keep method shape, signatures (return types,
   parameters, overload set), constants (`dp(10)`, `0.3f`, `>= 3000`,
   `clamp(150, 600)`, `< 1px` snap), imports/short type names, and branch
   structure. The guard prints the test classes to run when you touch one: the
   file's entry in `scripts/vendor_guard.py` (`FORCED_EXACT_TESTS_BY_PATH`), or
   the shared default trio while it has none.
4. **Adding upstream files needs no bookkeeping.** Copy them (`scripts/auto-vendor-copy.sh`
   bulk-copies javac's missing classes) and commit: provenance is derived.
   Rewriting a file's `upstream` mapping or marking our own code is a one-line
   overlay edit.
5. **Host wiring is expected** — package/import adjustments, AndroidX or
   project-local type substitutions, `R.*` / `Theme.getColor(Theme.key_*)`
   rewrites, constructor/callback glue. It must not change the branch
   structure, constants, or measurement/drawing/gesture/animation/recycling
   behavior of a `forced_exact` subsystem.
6. **License headers on copied files stay.** That's how compliance works.
7. **Comment surface stays minimal.** Vendored files keep the upstream
   GPL/SPDX header. `:app` ports keep an SPDX `GPL-2.0-or-later` header plus one
   short attribution line naming the upstream path — no structured
   `PORT-MODE` / `EXACT-SUBSYSTEMS` / `OMITTED` blocks (retired; they went
   stale). A one-line `TODO(<topic>):` at a call site is fine.
8. **Files listed in the overlay's `reference` block** intentionally track an
   older upstream commit. Don't "fix" them to the pin; removing a path from
   `reference` is a deliberate decision.

## Build, Lint, And Test

- Do not run gradlew commands in parallel; add `--quiet` when possible.
- **All unit tests:** `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- **Single test class:** `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'io.github.baba983.nativedo.feature.home.ui.HomeViewModelTest' --quiet`
- **Paparazzi screenshots:** plain `testDebugUnitTest` only exercises rendering; pixel
  verification runs via `./apps/android/gradlew -p apps/android :app:verifyPaparazziDebug`
  (or `runUnitAndScreenshotTests`, which is the same task and what CI runs). Goldens live in
  `apps/android/app/src/test/snapshots/<platform>/images` and are committed; papering over the
  host is not possible (layoutlib's Skia paints text a little differently per OS), so there is
  one set per platform — `linux` (what CI verifies) and `macos` — and each checkout
  records/verifies only its own, because the `:app` build script points Paparazzi's
  `paparazzi.snapshot.dir` at `snapshots/$platform`. Either way `verifyPaparazziDebug` is a real
  local gate — a diff is either an intentional UI change or a bug. After an intentional UI
  change, re-record on the platforms you can run (`:app:recordPaparazziDebug`), and the other
  set is re-recorded wherever it is verified (the orb/CI for Linux); include both in the same
  commit. Snapshot names follow `<package>_<Class>_<method>[Theme].png`.
- **Assemble (runtime/resource changes):** `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`
- **Telegram-derived changes:** `./scripts/vendor-guard.sh --compile` (index + isolation +
  forced-exact + `:TMessagesProj` compile). The same check runs in `.githooks/pre-commit`
  (`--quick`) and in CI.
- For docs-only changes, verification can be limited to checking the edited Markdown for accuracy and consistency.

## Project reference

- `../telegram` — pristine Telegram checkout (`scripts/vendor-setup.sh --print-dir`); primary reference for UI components, animations, gesture interactions (swipe, long-press, pull-to-refresh), navigation transitions, and list performance patterns.
- `/Users/baba/Documents/workspace_github/stripe-android` — modern Android SDK reference for architecture and code patterns.
- `/Users/baba/Documents/workspace_github/fluxdo` — reference for linux.do-specific Discourse features and flows.
- <https://docs.discourse.org/> — canonical Discourse API documentation.

## Project Scope

- This repository builds a native client for `https://linux.do/`, not a generic Discourse SDK.
- Android is the active implementation target under `apps/android`; `apps/ios` is reserved for future parity work.

## Source Of Truth

- Start with `README.md` for the current project layout and local run commands.
- Treat `contracts/discourse/*.md` as the canonical reference for endpoints, models, and auth/session flow.
- Treat `fixtures/discourse/*` as the sample payloads that should stay aligned with the contracts.

## Network And Auth

- `linux.do` is a Discourse site. Prefer standard Discourse JSON endpoints and documented session flows over ad-hoc scraping.
- Login is WebView-backed and cookie-based. Do not introduce native username/password handling unless the user explicitly asks for a different auth strategy.
- Session-sensitive changes must account for `_t`, `_forum_session`, `cf_clearance`, bootstrap HTML parsing, and `/session/csrf`.
- If you change auth, cookies, CSRF, or endpoint semantics, update the matching files in `contracts/discourse/` and `fixtures/discourse/` in the same task when appropriate.
- Cloudflare handling should favor a reliable WebView fallback over brittle native workarounds.

## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.

<!-- CODEGRAPH_END -->

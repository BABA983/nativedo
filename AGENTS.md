# AGENTS.md

## Telegram-Derived Code

Some files under `apps/android/app/src/main/java/io/github/baba983/nativedo/` are
vendored from [Telegram for Android](https://github.com/DrKLO/Telegram). We don't
merge upstream back, but every file's origin and current delta must be discoverable.

### Where the truth lives

- **`vendor-manifest.json`** — single source of truth per file: upstream path,
  vendored commit, whether the local file differs from upstream, and which
  subsystems must stay upstream-shaped (`forced_exact`).
- **`VENDOR.md`** — workflow (setup, diff, status, re-sync, pin bump).
- **`docs/code-layout.md`** — directory layout, language choice (Java vs
  Kotlin), façade/Bridge pattern, stub-vs-throw policy. Read this before
  moving files between directories or starting a new façade.
- **`scripts/vendor-diff.sh <local-path>`** — diff a vendored file against the
  pinned upstream blob. Run this before editing a vendored file.
- **`scripts/vendor-status.sh`** — lint manifest `modified` flags against reality.

A file is _vendored_ only when Telegram source was copied, split out, or
substantially adapted. Files written from scratch are NativeDo code even if they
follow Telegram-like patterns. Do not add manifest entries, port-mode headers,
or origin comments unless actual Telegram source was copied or substantially
adapted.

### Rules

1. **A copied or substantially adapted Telegram file without a manifest entry is
   a bug.** Add the entry in the same commit that copies or derives the file.
2. **Before modifying a vendored file**, run `scripts/vendor-diff.sh <path>` so you
   see what's already been changed locally. Don't rewrite from memory.
3. **Respect `forced_exact`.** If a manifest entry lists `gesture`, `animation`,
   `measurement`, `drawing`, or `recycling`, keep that subsystem's method shape,
   constants (`dp(10)`, `0.3f`, `>= 3000`, `clamp(150, 600)`, `< 1px` snap), and
   branch structure aligned with upstream. These break in non-obvious ways when
   "looks close enough" rewrites slip in.
4. **`port_mode = exact`** — upstream fidelity is the goal. Drift only for host
   wiring (package, imports, `Theme.getColor(Theme.key_*)`, Resources).
5. **`port_mode = native`** — we've already diverged and don't claim Telegram
   parity. Any `forced_exact` subsystem still applies.
6. **Adapting backend deps** (Telegram messaging APIs → Discourse/linux.do) is
   expected. If the divergence is non-obvious, leave a one-line `notes` entry in
   the manifest.
7. **Adding a new vendored file**: copy upstream → fix package/imports → add
   manifest entry → run `scripts/vendor-status.sh` → commit source + manifest
   together. If the code is written from scratch, do not add a manifest entry.
8. **License headers on copied files stay.** That's how compliance works.

### Host wiring

For `port_mode = exact`, these changes are expected and still count as
`modified: true` when the local file differs byte-for-byte from upstream:

- package and import rewrites into `io.github.baba983.nativedo.*`
- AndroidX or project-local type substitutions needed to compile
- resource and theme-key rewrites (`R.*`, `Theme.getColor(Theme.key_*)`)
- constructor or callback glue needed to attach the component to NativeDo

Host wiring must not rewrite the branch structure, constants, measurement,
drawing, gesture, animation, or recycling behavior of a `forced_exact` subsystem.

### When something feels off

If a Telegram-derived component's interaction, animation, or measurement feels
wrong, **read the upstream file before patching**:

```bash
scripts/vendor-diff.sh ui/components/RecyclerListView.java
# or directly:
git -C vendor/telegram-android show <pinned_commit>:TMessagesProj/src/main/java/<path>
```

Re-align to upstream if the drift was unintentional. Don't "fix" gesture/animation/
measurement code by guessing.

## Build, Lint, And Test

- Do not run gradlew commands in parallel; add `--quiet` when possible.
- **All unit tests:** `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- **Single test class:** `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'io.github.baba983.nativedo.feature.home.ui.HomeViewModelTest' --quiet`
- **Assemble (runtime/resource changes):** `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`
- For docs-only changes, verification can be limited to checking the edited Markdown for accuracy and consistency.

## Project reference

- `./vendor/telegram-android` — primary reference for UI components, animations, gesture interactions (swipe, long-press, pull-to-refresh), navigation transitions, and list performance patterns.
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

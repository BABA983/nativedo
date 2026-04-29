# AGENTS.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:

- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:

- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## Hard Rules For Telegram-Derived Code

Telegram-derived code is a vendored upstream slice with NativeDo adapters around it. The goal is not byte-for-byte copying everywhere; the goal is that every local difference from Telegram is intentional, visible, and reviewable.

When adding or repairing Telegram-derived code, open the upstream file or upstream diff first. Do not recreate behavior from memory or write code that is merely "inspired by Telegram".

Local differences from upstream are allowed only in these buckets:

- **HOST-WIRING:** package/import/resource/theme/navigation glue required to compile inside NativeDo.
- **ADAPTED:** intentional NativeDo product difference. Keep the upstream symbol name when practical and record the reason.
- **OMITTED:** whole upstream subsystem removed because it is out of scope. Record the removed block and reason.

Unknown drift is not allowed. If behavior feels different from Telegram, compare against upstream before patching by guesswork. If a product-specific change is needed, record it as `ADAPTED` or put it in an L2 adapter.

### Port Levels

Every Telegram-derived file MUST declare its port level.

| Level | Use it for | Rule |
|---|---|---|
| **L0 - Verbatim Core** | Touch, gesture, animation, scroll, recycling, and interaction constants. | Kept code matches upstream except `HOST-WIRING`. Do not change branches, method shapes, magic numbers, durations, thresholds, or interpolators. |
| **L1 - Faithful Slice** | Upstream files where NativeDo only needs part of the class. | Kept code stays upstream-shaped. Whole out-of-scope subsystems may be `OMITTED`; intentional product differences must be `ADAPTED`. |
| **L2 - Bridged Adapter** | Glue from Telegram-derived views into NativeDo screens, ViewModels, Compose, resources, or product constraints. | NativeDo idioms are allowed. Put product-specific decisions here when practical instead of editing L0/L1 code. |
| **L3 - Native** | Pure NativeDo code, Discourse logic, repositories, app-specific UI. | No upstream preservation contract. Do not claim Telegram-derived behavior unless it is L0/L1/L2. |

### Subsystem Classes — Forced Minimum Levels

When a Telegram-derived file participates in any of these subsystems, the kept code in that subsystem must use at least the listed level, regardless of file-level declaration. A file may be L1 overall while specific subsystems inside it are L0.

| Subsystem | Forced minimum | Examples |
|---|---|---|
| Touch / gesture arbitration | **L0** | `ViewPagerFixed.onTouchEventInternal`, `BottomSheet`, `RecyclerListView` swipe |
| Animation curves, durations, interpolators | **L0** | `distanceInfluenceForSnapDuration`, quintic-out interpolator, `clamp(150, 600)` snap duration |
| List / view recycling pools | **L0** | `viewsByType`, `updateViewForIndex` swap logic |
| Edge tolerances and magic numbers in interaction code | **L0** | `< 1px` snap, `touchSlop = 0.3 cm`, `velX >= 3000` thresholds |
| Custom Path / Canvas drawing | L1 | `GlassTabView.dispatchDraw`, ActionBar back button |
| Lifecycle orchestration | L1 | `ViewPagerActivity.onPageSelected` evict/resume |
| Theme / resource mapping | L2 | `Theme.key_*` constants |
| Discourse / linux.do business logic | L3 | login, CSRF, bootstrap parse, repository |

### File Header (Required for L0/L1)

Every L0 and L1 file MUST include port metadata above or inside the copyright banner:

```
// UPSTREAM-LEVEL: L0 | L1
// UPSTREAM-PATH:  org/telegram/ui/Components/ViewPagerFixed.java
// UPSTREAM-COMMIT: <40-char sha or "master@<YYYY-MM-DD>">
// SUBSYSTEMS: gesture=L0, animation=L0, custom-layout=L1
// HOST-WIRING:
//   - Theme.ResourcesProvider -> Theme.getColor(Theme.key_*)
// ADAPTED:
//   - MAIN_TABS_MIN_CONTENT_WIDTH = 160dp - NativeDo currently has fixed Home/Profile tabs.
// OMITTED:
//   - drag-reorder (ItemTouchHelper path) - out of scope until multi-tab editing ships.
```

L2 files SHOULD have an abbreviated header (`UPSTREAM-LEVEL: L2`, `UPSTREAM-PATH:`, free-form notes). L3 files do not need this header.

### When You Find Something Missing

If a previously shipped slice dropped behavior that affects feel, compare against upstream first. For L0 subsystems, re-align the kept code to upstream instead of patching by guesswork. For intentional NativeDo differences, record the difference as `ADAPTED`.

### Workflow

When asked to add a Telegram-derived file or modify one:

1. Identify touched subsystems and their minimum level.
2. Open the upstream file or upstream diff before editing.
3. Keep L0/L1 code upstream-shaped; prefer L2 adapters for NativeDo-specific behavior.
4. Record every local difference as `HOST-WIRING`, `ADAPTED`, or `OMITTED`.
5. Verify retained method names, parameter lists, key fields, and interaction constants against upstream.
6. Build (`./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`) and exercise touch/gesture-related tests if applicable.

### Mechanical Self-Check Before Declaring Done

For an L0 or L1 file, summarize the upstream comparison in the final response or commit message:

- Upstream path and commit/date checked.
- Local differences added or changed under `HOST-WIRING`, `ADAPTED`, or `OMITTED`.
- L0 interaction constants checked, for example `dp(10)`, `0.3f`, `>= 3000`, `clamp(150, 600)`, `< 1`.
- Any unexpected divergence was fixed or explicitly escalated before declaring done.

## Build, Lint, And Test

- Do not run gradlew commands in parallel; add `--quiet` when possible.
- **All unit tests:** `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`
- **Single test class:** `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'io.github.baba983.nativedo.feature.home.ui.HomeViewModelTest' --quiet`
- **Assemble (runtime/resource changes):** `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`
- For docs-only changes, verification can be limited to checking the edited Markdown for accuracy and consistency.

## Project reference

- `/Users/baba/Documents/workspace_github/Telegram` — primary reference for UI components, animations, gesture interactions (swipe, long-press, pull-to-refresh), navigation transitions, and list performance patterns.
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

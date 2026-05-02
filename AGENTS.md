# AGENTS.md


## Hard Rules For Telegram-Derived Code

Telegram-derived work is not always a file-level slice. Some work is an exact component port; some is a complete feature workflow whose backend must be adapted to NativeDo. The goal is that every local difference from Telegram is intentional, visible, and reviewable, and that a feature cannot be declared done just because one upstream slice was copied.

When adding or repairing Telegram-derived behavior, open the upstream file, upstream diff, or upstream workflow first. Do not recreate behavior from memory or write code that is merely "inspired by Telegram" while claiming Telegram parity.

Local differences from upstream are allowed only in these buckets:

- **HOST-WIRING:** package/import/resource/theme/navigation glue required to compile inside NativeDo.
- **ADAPTED-BACKEND:** Telegram backend/storage/account dependencies replaced by NativeDo equivalents while keeping the user-visible workflow aligned.
- **ADAPTED-PRODUCT:** intentional NativeDo product difference. Keep the upstream symbol name when practical and record the reason.
- **OMITTED:** whole upstream subsystem removed because it is out of scope. Record the removed block and reason.
- **PARITY-BLOCKER:** missing dependency or host capability that prevents the Telegram workflow from being matched.

Unknown drift is not allowed. If behavior feels different from Telegram, compare against upstream before patching by guesswork. If a product-specific change is needed, record it as `ADAPTED-BACKEND` or `ADAPTED-PRODUCT`.

### Port Modes

Every new Telegram-derived change MUST choose a primary port mode before implementation.

| Mode | Use it for | Rule |
|---|---|---|
| **Exact Component Port** | Independent Telegram UI/interaction components: touch handlers, animation helpers, measured/drawn cells, recycler pools, path/canvas drawables. | Keep method shape, key fields, branch structure, constants, thresholds, durations, interpolators, measurement formulas, and recycler behavior aligned with upstream. Allowed changes are `HOST-WIRING` and recorded whole-subsystem omissions. No "looks close enough" rewrites. |
| **Feature Workflow Port** | Complete user-visible Telegram features: browser instant view, settings, bookmarks, history, media viewer flows, profile sheets, or any workflow spanning screens/storage/navigation. | Write a Telegram Workflow Map before implementation. Match the user-visible path first, then adapt NativeDo backend/host boundaries explicitly. A copied slice is not completion proof. |
| **Native Feature** | Pure NativeDo behavior, Discourse/linux.do logic, repositories, app-specific UI. | No upstream preservation contract. Do not claim Telegram parity. Do not add Telegram/GPL port headers just because the design was loosely inspired by Telegram. |

Existing files with legacy `UPSTREAM-LEVEL: L0/L1/L2/L3` headers do not need bulk migration. When a task touches one of those files, update the header only if it is directly relevant to the change.

### Forced Exact Components

When Telegram-derived work touches any of these subsystems, that subsystem MUST be handled as an `Exact Component Port`, even when it lives inside a larger `Feature Workflow Port`.

| Subsystem | Requirement | Examples |
|---|---|---|
| Touch / gesture arbitration | Exact Component Port | `ViewPagerFixed.onTouchEventInternal`, `BottomSheet`, `RecyclerListView` swipe |
| Animation curves, durations, interpolators | Exact Component Port | `distanceInfluenceForSnapDuration`, quintic-out interpolator, `clamp(150, 600)` snap duration |
| List / view recycling pools | Exact Component Port | `viewsByType`, `updateViewForIndex` swap logic |
| Edge tolerances and magic numbers in interaction code | Exact Component Port | `< 1px` snap, `touchSlop = 0.3 cm`, `velX >= 3000` thresholds |
| Measurement and layout formulas copied from Telegram | Exact Component Port | instant-view list indentation, cell height formulas, custom measure branches |
| Custom Path / Canvas drawing | Exact Component Port | `GlassTabView.dispatchDraw`, ActionBar back button, list bullets/numbers |
| Theme / resource mapping | Host wiring or Native Feature | `Theme.key_*` constants |
| Discourse / linux.do business logic | Native Feature | login, CSRF, bootstrap parse, repository |

### Exact Component Header

Every new or touched Exact Component Port should include port metadata above or inside the copyright banner:

```
// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/Components/ViewPagerFixed.java
// UPSTREAM-COMMIT: <40-char sha or "master@<YYYY-MM-DD>">
// EXACT-SUBSYSTEMS: gesture, animation, measurement
// HOST-WIRING:
//   - Theme.ResourcesProvider -> Theme.getColor(Theme.key_*)
// ADAPTED-BACKEND:
//   - None.
// ADAPTED-PRODUCT:
//   - MAIN_TABS_MIN_CONTENT_WIDTH = 160dp - NativeDo currently has fixed Home/Profile tabs.
// OMITTED:
//   - drag-reorder (ItemTouchHelper path) - out of scope until multi-tab editing ships.
```

### Feature Workflow Map

Every Feature Workflow Port MUST write a Telegram Workflow Map before implementation. Large features should use `docs/telegram-port/<feature>.md`; small features may put the map in the main file header. The map must cover the complete upstream workflow, even when the current task implements only one phase.

Use this structure:

```md
# Telegram Workflow Map: <Feature>

1. Upstream scope:
2. Entry points:
3. Screens/fragments/sheets:
4. Menus/actions:
5. State transitions:
6. Storage/backend dependencies:
7. Navigation/back behavior:
8. Unsupported or adapted dependencies:

Current task scope:
- In scope:
- Deferred:
- Parity blockers:
```

Rules for the map:

- Fill all 8 sections before coding. If a section does not apply, write `None` and explain why.
- `Upstream scope` must name the Telegram files/classes/methods that define the workflow.
- `Storage/backend dependencies` must separate Telegram backend behavior from NativeDo replacements.
- `Unsupported or adapted dependencies` must label each difference as `HOST-WIRING`, `ADAPTED-BACKEND`, `ADAPTED-PRODUCT`, `OMITTED`, or `PARITY-BLOCKER`.
- If a `PARITY-BLOCKER` affects the requested scope, stop implementation and report the blocker plus options. Continue only when the blocker does not affect the explicit current scope.
- Phased work is allowed, but the phase must be marked under `In scope` / `Deferred` / `Parity blockers`. Do not declare the whole Telegram feature complete unless the full map is implemented or explicitly omitted.

Examples:

- Browser Instant View, Browser Settings, Bookmarks, and History are `Feature Workflow Port` work.
- `WebViewSwipeContainer`, Telegram cell measurement/drawing, path/canvas icons, animation interpolators, and recycler-pool logic are `Exact Component Port` work.
- A Feature Workflow Port may and often should contain Exact Component Ports for its gesture, animation, measurement, drawing, or recycling pieces.

### When You Find Something Missing

If a previously shipped slice dropped behavior that affects feel or workflow parity, compare against upstream first. For forced exact subsystems, re-align the kept code to upstream instead of patching by guesswork. For Feature Workflow Port gaps, update the Workflow Map before coding more.

### Workflow

When asked to add or modify Telegram-derived behavior:

1. Choose `Exact Component Port`, `Feature Workflow Port`, or `Native Feature`.
2. Open the upstream source before editing. For feature work, write the Workflow Map first.
3. Identify any forced exact subsystems and keep them upstream-shaped.
4. Record every local difference as `HOST-WIRING`, `ADAPTED-BACKEND`, `ADAPTED-PRODUCT`, `OMITTED`, or `PARITY-BLOCKER`.
5. Verify retained method names, parameter lists, key fields, interaction constants, measurement formulas, and workflow states against upstream.
6. Build (`./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`) and exercise touch/gesture-related tests if applicable.

### Mechanical Self-Check Before Declaring Done

For an Exact Component Port, summarize the upstream comparison in the final response or commit message:

- Upstream path and commit/date checked.
- Local differences added or changed under `HOST-WIRING`, `ADAPTED-BACKEND`, `ADAPTED-PRODUCT`, or `OMITTED`.
- Interaction constants checked, for example `dp(10)`, `0.3f`, `>= 3000`, `clamp(150, 600)`, `< 1`.
- Any unexpected divergence was fixed or explicitly escalated before declaring done.

For a Feature Workflow Port, summarize:

- Workflow Map path or header location.
- Upstream workflow scope checked.
- Current phase `In scope` / `Deferred` / `Parity blockers`.
- Backend/storage differences labeled as `ADAPTED-BACKEND`.
- Any Exact Component Ports inside the feature and their upstream comparison.

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

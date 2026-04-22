# Telegram Substrate Demo Root Design

Date: 2026-04-22
Status: Draft for review
Related OpenSpec:
- `openspec/changes/full-rewrite-telegram-parity`
- `openspec/changes/split-telegram-parity-phases`

## Summary

This spec defines the first executable slice of the Telegram-based Android rewrite for NativeDo.

The goal is not to rebuild real linux.do features yet. The goal is to stand up the new Telegram-style UI substrate as the default app root on `feature/refactor`, then prove that the substrate is viable with a demo navigation flow:

- app launches into a new View-based demo root
- `DemoHomeFragment` presents a list of mock topics
- tapping a topic pushes `TopicDetailDemoFragment`
- a detail page can push another `TopicDetailDemoFragment`
- left action bar back, system back, and interactive back transitions all work on a real fragment stack

This is a clean-slate step. `main` remains the backup for the old Compose implementation.

## Why This Slice Exists

The rewrite is too large to execute as a single spec. The existing umbrella change describes the full Telegram-parity target, but the first practical milestone is much smaller:

1. remove Compose from the main navigation path
2. establish Telegram-style navigation and page ownership
3. validate the back transition model before real Discourse screens depend on it

If this substrate is unstable, every later page rewrite will inherit the same problems. If this substrate is solid, later feature work becomes a series of page migrations instead of repeated architecture changes.

## Source Of Truth

The reference source set for direct ports in this phase is:

- local repo: `/Users/baba/Documents/workspace_github/Telegram`
- upstream remote: `git@github.com:DrKLO/Telegram.git`

This phase does not mix code from a second Telegram Android source tree. The purpose is to keep provenance and license assumptions consistent.

## Goals

- Replace the current default Android app root with a Telegram-style `ActionBarLayout` host.
- Keep the main path entirely View-based. Compose must not be used for the demo root, navigation, list, or detail pages.
- Port the minimum Telegram substrate needed for real fragment-stack navigation.
- Support multi-level stack growth:
  - `DemoHomeFragment -> TopicDetailDemoFragment -> TopicDetailDemoFragment -> ...`
- Support Telegram-style back handling:
  - action bar back
  - system back
  - interactive back transition
- Support process or configuration restoration of the demo stack.
- Keep the scope narrow enough that it can be validated with one runnable demo root.

## Non-Goals

- No real Discourse topic list, topic detail, search, profile, browser, login, or image viewer.
- No bottom tab bar.
- No tablet dual-pane, layer stack, drawer, or multi-host activity setup.
- No attempt to port Telegram chat controllers, business models, or unrelated app infrastructure.
- No tests added only for coverage optics.

## Product Decisions

### 1. Clean-Slate Host

`MainActivity` becomes the single launcher host for the rewrite path. The old Compose root does not stay behind a flag or sandbox route in this branch.

Reasoning:

- the branch already exists for refactor work
- keeping two roots would add transitional code with little long-term value
- `main` is the fallback snapshot

### 2. Demo Root First

The first executable root is a demo root, not a partial real app shell.

Reasoning:

- it proves the substrate without binding early architecture decisions to unfinished business pages
- it keeps the acceptance criteria concrete
- it avoids fake progress from rebuilding app chrome before navigation works

### 3. Telegram Port Strategy

This phase follows a "cropped 1:1 port" strategy:

- keep Telegram class names and behavior where they are part of the substrate
- keep NativeDo-specific glue thin and explicit
- cut Telegram-only app infrastructure that does not serve the demo root

This is intentionally not a "Telegram-inspired rewrite" and also not a "copy the whole app shell" effort. The target is direct substrate parity inside a narrower boundary.

### 4. Back Transition Target

This phase supports both of Telegram's back paths, because the user-facing expectation is closer to predictive back than the legacy slide-only gesture:

- API 33 and above:
  - prioritize Telegram-style predictive back visuals
  - shrinking current page
  - rounded clipping during drag
  - dim and reveal of the previous page
  - scale pivot changes based on left or right edge origin
- API 28 to 32:
  - keep Telegram's classic left-edge interactive swipe back fallback

This avoids inventing a custom third transition model just to unify behavior across API levels.

## Architecture

### Runtime Boundary

The runtime layer stays intentionally small:

- `NativeDoApplication`
  - initializes the app runtime
- `NdApp`
  - exposes the rewrite runtime singleton
- `NdRuntime`
  - owns application-level dependencies needed by the substrate phase

This layer must not absorb screen logic or navigation policy.

### Host Boundary

- `MainActivity`
  - creates and owns the single `ActionBarLayout`
  - forwards system back events
  - applies system bar appearance
  - saves and restores navigation state
- `LaunchBootstrap`
  - attaches the initial fragment
  - restores a fragment instance from a saved navigation key plus arguments

`MainActivity` is only a host. It must not become the place where app flow decisions accumulate.

### Ported Telegram Substrate

The first-phase port set is:

- `ActionBarLayout`
- `BaseFragment`
- `ActionBar`
- `Theme`
- `AndroidUtilities`
- `LayoutHelper`
- `SimpleTextView`
- minimal `RecyclerListView`

The principle is simple:

- if a type is part of the navigation or rendering substrate, keep it close to Telegram
- if a type exists only to glue substrate into NativeDo, keep it thin and local

### Demo Fragments

- `DemoHomeFragment`
  - root fragment
  - renders a mock topic list
  - selecting an item pushes `TopicDetailDemoFragment`
- `TopicDetailDemoFragment`
  - renders a simple mock detail screen
  - can push another `TopicDetailDemoFragment`
  - is used to validate a real growing back stack

The detail fragment is intentionally reusable so the stack can grow without inventing additional fake screens.

## Navigation Model

### Stack Rules

- `ActionBarLayout` owns the real fragment stack.
- Each page is a `BaseFragment` subclass.
- Fragments create and own their own Views.
- Navigation must go through substrate stack operations, not ad-hoc activity routes.

### Required Flow

1. App launches into `DemoHomeFragment`.
2. Tapping a topic pushes `TopicDetailDemoFragment(topicId, depth)`.
3. From detail, the user can push another detail page.
4. From any detail page, the user can return via:
   - action bar back
   - system back
   - interactive back transition

### Restoration Rules

Saved state is limited to the minimum needed to rebuild the navigation stack:

- fragment navigation key
- fragment arguments bundle

This phase does not attempt full View snapshot restoration or generalized persistence. It only restores enough to rebuild the same demo stack after recreation.

## Transition Model

### API 33+ Predictive Back

For supported devices, the target is the Telegram predictive-back visual language:

- edge-origin-aware back start
- current page shrinks during gesture progress
- scale pivot changes depending on whether the back gesture begins from the left or right edge
- current page exits with rounded clipping
- previous page is progressively revealed underneath
- dim or scrim changes with gesture progress
- release completes either cancel or pop using the same transition state

This is not a static animation played after a back event. Gesture progress must directly drive the transition.

### API 28-32 Classic Interactive Back

For pre-predictive-back devices, the target is Telegram's classic `ActionBarLayout` slide-back behavior:

- left-edge gesture starts tracking
- current page translates with the finger
- previous page is attached behind the current page
- dim and layer shadow are rendered during drag
- release velocity and distance determine cancel vs pop

### Out Of Scope For This Phase

- shared-element transitions
- bottom-sheet-driven back choreography
- drawer or layered layout interactions
- animation behavior that depends on Telegram chat-specific UI

## UI And Rendering Boundaries

### View System Only In Main Path

The demo root must be fully View-based:

- no Compose root host
- no Compose navigation
- no Compose list for the mock topics
- no Compose detail content

Compose may remain in dependencies temporarily, but it must not participate in the demo navigation chain.

### Theme Surface

`Theme` is ported as a Telegram-style color-key system, but only the minimum key set is required in this phase:

- window background
- action bar background
- primary text
- secondary text
- divider or shadow-related colors
- scrim colors needed by transitions

This phase does not need a complete app theming matrix.

## Port Policy And License Boundary

The user requirement for this rewrite is: if the license permits it, port the code directly.

This spec therefore sets the following boundary:

- direct ports from the local `DrKLO/Telegram` source are allowed for substrate classes in scope
- direct ports must preserve provenance
- Telegram business logic and branded assets are out of scope

Required provenance rules for direct ports:

- record each directly ported class and its source path
- keep the mapping in a simple inventory document during implementation
- keep file-level provenance notes for heavily ported files when needed

Important constraint:

- this repository currently does not present a top-level project license in the working tree
- before external redistribution of a GPL-derived rewrite artifact, project-level license and notice handling must be resolved

That licensing work is not the implementation target of this phase, but the rewrite must not proceed as if provenance and redistribution obligations are irrelevant.

## Testing And Verification

The testing rule for this phase is "necessary only."

Expected verification:

- runnable demo root on device or emulator
- manual validation of multi-level push and pop
- manual validation of predictive back on API 33+ if available
- manual validation of classic swipe back on API 28-32 behavior path
- manual validation of restore after recreation

Expected automated coverage:

- small test for `LaunchBootstrap` navigation-key restoration
- small targeted test where navigation-state or action-bar dispatch logic is easy to break

Not required:

- broad UI snapshot tests
- tests added only to mirror every demo fragment branch
- speculative unit tests for ported Telegram code with no local behavior change

## Acceptance Criteria

The phase is complete when all of the following are true:

- app launch enters the new demo root by default
- the default root is `ActionBarLayout`, not the old Compose root
- `DemoHomeFragment` shows mock topics
- selecting a topic pushes `TopicDetailDemoFragment`
- a detail page can push another detail page repeatedly
- action bar back pops correctly
- system back pops correctly
- interactive back works on a real fragment stack
- API 33+ predictive back path includes shrink, reveal, dim, and edge-dependent pivot behavior
- API 28-32 fallback path matches Telegram's classic interactive slide-back model
- stack restoration rebuilds the visible demo flow after recreation
- the main navigation chain is entirely View-based

## Risks

### 1. `ActionBarLayout` Coupling Is Still Heavy

Even cropped, Telegram's navigation substrate assumes surrounding infrastructure. The implementation must actively cut dependencies instead of letting unrelated Telegram concepts leak into NativeDo.

### 2. Predictive Back Raises Phase Cost

Supporting predictive back in phase one is materially more work than only shipping classic swipe back. This is intentional because the user specifically wants the modern Telegram-like interaction, not just a basic drag-to-pop gesture.

### 3. Demo Success Can Hide Integration Cost

A successful demo root does not prove that browser, image viewer, cooked post rendering, or complex scrolling screens are solved. It only proves the substrate is viable enough for later page migration.

## Deferred Work After This Phase

If this phase succeeds, the next spec should decide the first real business page migration. Likely candidates:

- topics list
- topic detail
- browser

That follow-up spec should build on the substrate here instead of reopening host and navigation decisions.

## Resolved Decisions

- clean-slate on `feature/refactor`
- `main` is the backup for the old implementation
- new root is a demo root, not a parallel sandbox
- main path is View-only
- first phase uses a cropped 1:1 Telegram port strategy
- first phase supports predictive back on API 33+ and classic fallback on API 28-32
- demo stack must support repeated `TopicDetailDemoFragment` pushes

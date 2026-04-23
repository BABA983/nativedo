# NativeDo Telegram-Style Refactor Design

## Goal

Rebuild the Android app around Telegram's navigation foundation while keeping NativeDo-owned screens in Kotlin + Compose.

Milestone 1 is intentionally narrow:

- start on a single topic list root page
- open topic detail
- allow detail to push another detail repeatedly
- support action bar back, system back, edge swipe-back, and Android 14 predictive back
- match Telegram interaction quality and basic visual hierarchy, not full Telegram app parity

## Fixed Decisions

These decisions are already approved for this milestone:

- architecture: hybrid
- Telegram-derived navigation infrastructure + NativeDo-owned Kotlin business screens
- page rendering: Compose inside Telegram-style fragment containers
- root shell: single topic list page, no tabs, no drawer UX, no browser shell
- navigation validation: both `list -> detail -> back -> list` and `list -> detail -> detail -> detail`
- fidelity target: interaction + basic visual fidelity, not full high-fidelity Telegram cloning

## Hard Rules For Telegram-Derived Code

This is the most important implementation constraint for the milestone.

1. Files copied from Telegram must stay as close to upstream as possible.
2. Do not freely rename Telegram methods, parameters, classes, fields, or callbacks.
3. Do not freely rewrite internal control flow just to make the code look more Kotlin-idiomatic.
4. Prefer preserving upstream logic and surrounding it with minimal NativeDo adapters or stub dependencies.
5. If a Telegram file can be copied verbatim, copy it verbatim.
6. If a Telegram file cannot compile without extra support code, add the missing support code first instead of editing the copied logic.
7. If a copied file still requires a change, limit the change to the minimum needed to compile or integrate:
   - package rename
   - import rewrite
   - resource reference substitution
   - host-activity boundary wiring
   - compile-only compatibility shims
8. Any unavoidable behavior change in a Telegram-derived file must be documented inline in the file header or in a short adjacent note during implementation.

Practical consequence:

- NativeDo-owned code should absorb adaptation pressure.
- Telegram-derived code should not become a loose “inspired by Telegram” rewrite.

## Language Strategy

The app remains Kotlin-first overall, but milestone 1 prioritizes upstream fidelity over language purity.

- NativeDo-owned code is Kotlin.
- Compose screens are Kotlin.
- Telegram-derived infrastructure should default to Java when that is the safest way to keep a true 1:1 upstream copy.
- If a Telegram-derived file is implemented in Kotlin, it must still preserve upstream names, parameters, and behavior as closely as possible.

## Scope

### In Scope

- `MainActivity` as the NativeDo host activity
- `NativeDoApp` application class
- Telegram-derived navigation core under `core/` and `ui/`
- single stack navigation with root list and repeatable detail pushes
- Compose topic list and topic detail content
- mock topic data only
- back button, system back, edge swipe-back, predictive back
- GPL file headers on all Telegram-derived files

### Out Of Scope

- real Discourse networking
- login, cookies, CSRF, or session restore
- tabs, drawers, browser, search, profile, settings
- tablet split layout
- Telegram theme system beyond the minimum needed to render milestone 1
- preview mode, sheets, bubbles, action mode, multi-account support
- shared element transitions

## Package And Directory Structure

Approved structure with Kotlin-style lowercase packages:

```text
io.github.baba983.nativedo/
├── MainActivity.kt
├── NativeDoApp.kt
│
├── core/
│   ├── AndroidUtilities.java|kt
│   ├── FileLog.java|kt
│   ├── NotificationCenter.java|kt
│   └── SharedConfig.java|kt
│
└── ui/
    ├── actionbar/
    │   ├── ActionBar.java|kt
    │   ├── ActionBarLayout.java|kt
    │   ├── BackDrawable.java|kt
    │   ├── BaseFragment.java|kt
    │   ├── DrawerLayoutContainer.java|kt
    │   ├── INavigationLayout.java|kt
    │   └── SimpleTextView.java|kt
    │
    ├── components/
    │   ├── CubicBezierInterpolator.java|kt
    │   ├── LayoutHelper.java|kt
    │   └── SizeNotifierFrameLayout.java|kt
    │
    └── topic/
        ├── TopicListFragment.kt
        ├── TopicDetailFragment.kt
        ├── TopicListScreen.kt
        ├── TopicDetailScreen.kt
        └── MockTopics.kt
```

Notes:

- `java|kt` means the file extension is chosen per upstream-fidelity needs during implementation, with Java preferred for direct Telegram copies.
- `DrawerLayoutContainer` is not a milestone-1 feature. It only exists if `ActionBarLayout` requires it to compile or keep upstream signatures intact.
- Additional Telegram support files may be added only when they are direct compile/runtime dependencies of the copied navigation chain.

## Architecture

### 1. Host Activity Layer

`MainActivity` is NativeDo-owned code.

Responsibilities:

- window setup
- edge-to-edge setup
- back dispatcher registration
- Android 14 predictive back callback registration
- creation of the root `ActionBarLayout`
- installation of the initial root fragment

Non-responsibilities:

- no business navigation decisions
- no Compose navigation stack
- no direct topic rendering

### 2. Telegram-Derived Navigation Layer

`ActionBarLayout` owns the real navigation stack.

Responsibilities:

- present/push fragment
- pop fragment
- container choreography for push/pop transitions
- left-edge swipe tracking
- predictive back state handling
- action bar hosting
- previous-fragment reveal during back gestures

The source of truth for navigation is the fragment stack inside `ActionBarLayout`, not a second Compose navigation stack.

### 3. NativeDo Topic Layer

NativeDo-owned topic pages remain thin wrappers around Compose content.

- `TopicListFragment` extends Telegram-derived `BaseFragment`
- `TopicDetailFragment` extends Telegram-derived `BaseFragment`
- each fragment configures its `ActionBar`
- each fragment returns a `ComposeView` from `createView()`
- Compose emits callbacks
- the fragment translates callbacks into `presentFragment(...)` or back actions

## Telegram Copy Set For Milestone 1

### Required Core Files

- `core/AndroidUtilities`
- `core/FileLog`
- `core/NotificationCenter`
- `core/SharedConfig`
- `ui/actionbar/INavigationLayout`
- `ui/actionbar/BaseFragment`
- `ui/actionbar/ActionBarLayout`
- `ui/actionbar/ActionBar`
- `ui/actionbar/BackDrawable`
- `ui/actionbar/SimpleTextView`
- `ui/components/CubicBezierInterpolator`
- `ui/components/LayoutHelper`
- `ui/components/SizeNotifierFrameLayout`

### Conditional Support Files

Only add these if the copied navigation files require them:

- `ui/actionbar/DrawerLayoutContainer`
- tiny Telegram helper classes directly referenced by the files above

### Explicitly Not Part Of Milestone 1

- `LaunchActivity`
- bottom tabs
- overlay sheets
- preview mode
- story viewers
- browser infrastructure
- Telegram business screens

## GPL And Provenance Rules

Every Telegram-derived file must include:

- the GPL header
- the Telegram copyright
- the NativeDo copyright
- the exact upstream class or path reference
- the upstream source URL or revision/tag when known

The repository should treat Telegram-derived files as a clearly bounded area rather than mixing provenance invisibly into business code.

## Navigation Design

### Root Flow

```text
App launch
  -> TopicListFragment
      -> TopicDetailFragment(topic A)
          -> TopicDetailFragment(topic B)
              -> TopicDetailFragment(topic C)
```

Supported validation paths:

- `list -> detail -> back -> list`
- `list -> detail -> detail -> detail -> back -> back -> back`

### Screen Ownership

- `TopicListScreen` renders the list and emits `onTopicClick(topicId)`
- `TopicListFragment` receives the callback and pushes `TopicDetailFragment`
- `TopicDetailScreen` renders the detail and emits `onRelatedTopicClick(topicId)`
- `TopicDetailFragment` receives the callback and pushes another `TopicDetailFragment`

Compose is responsible for content only.
Telegram-derived navigation classes are responsible for all page-stack behavior.

## Interaction Design

### Push / Pop

- push animation follows Telegram-style right-to-left page presentation
- pop animation follows Telegram-style rightward dismissal
- action bar participates in the same container transition

### Left-Edge Swipe Back

The first milestone should preserve Telegram's gesture model:

- gesture begins from the left edge
- `ActionBarLayout` prepares the previous fragment in `containerViewBack`
- current fragment follows finger movement
- release decides cancel or complete
- velocity can force completion

### Predictive Back

For Android 14+:

- `MainActivity` registers `OnBackAnimationCallback`
- `onBackStarted`, `onBackProgressed`, `onBackCancelled`, and `onBackInvoked` forward to `ActionBarLayout`
- `ActionBarLayout` uses the same stack and container model as manual swipe-back
- cancellation returns the page cleanly to its original position
- completion finishes the same back transition without visual discontinuity

For Android 13:

- `MainActivity` registers `OnBackInvokedCallback`
- no progress animation is available
- completion still routes through `ActionBarLayout`

For Android 12 and below:

- standard back dispatch closes the top fragment or exits the activity when only the root remains

## Screen Design

### Topic List

Behavior:

- root page
- no back arrow
- title identifies the app/forum
- scrollable list of mock topics

Visual target:

- Telegram-like action bar height and spacing
- clean row rhythm
- basic metadata density suitable for a forum topic list

### Topic Detail

Behavior:

- shows back arrow
- shows topic title
- renders enough content to make transitions and nested detail pushes meaningful
- exposes related-topic actions that push new details repeatedly

Visual target:

- Telegram-like page framing and action bar behavior
- simple body layout, not full forum rendering

## Testing Strategy

### Automated

Automated tests are not a blanket requirement for every navigation branch in this milestone.

Rule:

- only add a test when it protects NativeDo-owned glue code or a small, high-risk regression point
- do not force broad test coverage onto direct Telegram copies just because the files are new
- prefer no test over low-value tests that merely restate Telegram behavior without adding confidence

For milestone 1, likely necessary automated coverage is limited to one small NativeDo-owned wiring test if that glue becomes non-trivial, for example:

- list click requests presentation of the correct detail fragment
- detail related-topic click requests presentation of another detail fragment

If those interactions stay simple and obvious in implementation, manual verification alone is acceptable for the first cut.

### Manual

1. launch to the list root
2. open detail A
3. open related detail B
4. open related detail C
5. back from C to B, B to A, A to list
6. perform left-edge swipe-back from a detail page
7. on Android 14, test predictive back cancel and commit
8. from the root list, confirm back exits the activity instead of popping a non-existent fragment

## Definition Of Done For Milestone 1

- Telegram-derived navigation foundation is integrated
- copied Telegram files keep upstream names, parameters, and logic as closely as practical
- GPL headers are present on all Telegram-derived files
- app starts on a topic list root page
- topic detail can be pushed repeatedly
- action bar back, system back, left-edge swipe-back, and predictive back all work through the same navigation stack
- Compose is used for page content only, not for a second navigation stack
- only necessary tests are added; broad coverage is not required for direct Telegram-derived behavior
- no real network or auth flow is introduced

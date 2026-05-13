# NativeDo Refactor Context

NativeDo is a native Android client for linux.do that combines Telegram-derived View infrastructure with NativeDo-owned Discourse feature content. This context records the language used to keep porting, refactor, and infrastructure decisions clear.

## Language

**Telegram View Infrastructure**:
Telegram-derived Android View-system code that owns app chrome, navigation, gestures, overlays, sheets, browser surfaces, and exact interaction components.
_Avoid_: Telegram style, Telegram UI, Java stuff

**NativeDo Feature Content**:
NativeDo-owned linux.do screens and components, usually Compose, that render feature state and emit user events without owning app chrome or page-stack behavior.
_Avoid_: Compose app, old UI, business UI

**Page-Scoped ViewModel**:
An AndroidX ViewModel owned by a Telegram `BaseFragment` stack entry through NativeDo's `ViewModelStoreOwner` host adaptation.
_Avoid_: activity ViewModel, global state holder

**Root Tabs Shell**:
The Telegram-derived `MainTabsActivity` root that hosts top-level NativeDo tabs while the outer `ActionBarLayout` owns the app page stack.
_Avoid_: Compose root scaffold, single-screen root

**NativeDo Business Layer**:
Kotlin models, repositories, ViewModels, session, storage, and Discourse API code that implements linux.do behavior independently of Telegram UI internals.
_Avoid_: backend, data stuff

**Login Workflow**:
The WebView-backed linux.do authentication flow that coordinates cookies, session restore, and Cloudflare-compatible browser fallback.
_Avoid_: login screen, auth UI

**Profile Skeleton**:
The first Profile tab content that renders loading/error/logged-in/logged-out states from profile/session data without launching the Login Workflow.
_Avoid_: profile feature, login entry

**Port Candidate**:
Code copied from the main branch or upstream Telegram as material to evaluate, adapt, rewrite, or discard for the refactor branch.
_Avoid_: migrated code, finished port

**Real Data Skeleton**:
The first accepted restore slice that wires linux.do data through repositories and ViewModels into minimal topic list/detail content inside the Telegram navigation host.
_Avoid_: full feature restore, complete topic experience

**Minimal Topic Content**:
The first topic list/detail Compose content shape: loading/error/empty, real rows/posts, basic cooked-body rendering, and navigation events without reactions, media overlays, popup infrastructure, bulletins, or full polish.
_Avoid_: old topic screen, full topic detail

**Basic Cooked Body Renderer**:
The first accepted Discourse cooked-HTML renderer subset for readable topic posts: paragraphs, links, quotes, code, lists, simple callouts, images as inline content, and internal-link callbacks.
_Avoid_: full post renderer, media viewer, instant view parity

**Media Viewer Workflow**:
The future Telegram-derived full-screen media viewing flow for images and galleries, including overlay, pinch/zoom, drag-dismiss, back handling, and transitions.
_Avoid_: Compose media viewer, image dialog

**Transient Overlay Feedback**:
Short-lived app-level feedback and contextual popup UI, such as bulletins, undo bars, and action popup menus.
_Avoid_: toast, Compose popup

**Theme Source**:
Telegram `Theme.java`, the single source of color and app-theme values for both View and Compose surfaces.
_Avoid_: Compose design system, NativeDo token source, topic theme

**Compose Theme Bridge**:
The minimal `ThemeCompose.kt` adapter that lets Compose observe **Theme Source** revisions and read Telegram theme keys.
_Avoid_: Compose theme system, design tokens

## Relationships

- **Telegram View Infrastructure** hosts **NativeDo Feature Content**.
- The **Root Tabs Shell** remains the first root screen for the refactor branch, but first-batch tab content stays minimal.
- **NativeDo Feature Content** is driven by the **NativeDo Business Layer**.
- **Page-Scoped ViewModels** are owned by **Telegram View Infrastructure** stack entries and cleared when those entries are removed.
- The **NativeDo Business Layer** must not depend on **Telegram View Infrastructure** or Compose UI types.
- A **Port Candidate** is not accepted refactor code until it is assigned to **Telegram View Infrastructure**, **NativeDo Feature Content**, or **NativeDo Business Layer** with a clear adaptation decision.
- **Port Candidates** are screened before landing; the refactor branch does not first accept a full main-branch port and then clean it up later.
- The **Real Data Skeleton** accepts topic list/detail data loading and minimal rendering before restoring reactions, media overlays, popup infrastructure, bulletins, and advanced interaction polish.
- **Minimal Topic Content** is rewritten around the refactor boundary; main-branch topic screens are source material, not accepted wholesale screen structure.
- The **Basic Cooked Body Renderer** belongs to **NativeDo Feature Content** and may show images inline, but does not own media overlays, galleries, popup actions, or Telegram exact viewer behavior.
- The **Media Viewer Workflow** is not part of the first **Real Data Skeleton**; staged Compose media viewer files are excluded rather than accepted as a temporary overlay stack.
- **Transient Overlay Feedback** is excluded from the first **Real Data Skeleton**; topic/detail errors render inline until Telegram-derived overlay feedback is deliberately ported.
- The **Login Workflow** is reviewed separately from the **Real Data Skeleton** even though its session primitives can be accepted into the **NativeDo Business Layer**.
- The **Profile Skeleton** is allowed in the **Root Tabs Shell** first batch, but it must not launch or depend on the **Login Workflow**.
- The **Theme Source** is shared by **Telegram View Infrastructure** and **NativeDo Feature Content**; Compose does not own a parallel semantic theme-token system.
- The **Compose Theme Bridge** may exist only to observe and read the **Theme Source**; staged `ui/theme/*` and topic-local theme token files are not accepted refactor infrastructure.

## Example dialogue

> **Dev:** "Should this image viewer stay as Compose?"
> **Domain expert:** "Only if it is **NativeDo Feature Content**; if it owns pinch, drag-dismiss, overlay, and back behavior, it belongs in **Telegram View Infrastructure** or remains a temporary **Port Candidate**."

## Flagged ambiguities

- "staged changes" was used as if it meant accepted refactor direction; resolved: staged changes are **Port Candidates** from main unless explicitly accepted into the refactor architecture.
- "port back" can mean copying main-branch implementation or accepting refactor-ready behavior; resolved: copying creates a **Port Candidate**, while acceptance requires fitting the **Telegram View Infrastructure** / **NativeDo Feature Content** / **NativeDo Business Layer** boundary.
- "restore topic/detail" could mean full interaction parity with main; resolved: the first restore target is the **Real Data Skeleton**, not reactions, media viewer, popup, bulletin, or full polish.
- "reuse topic screen" could mean accepting the main-branch screen structure; resolved: first-batch topic UI is **Minimal Topic Content**, with old screen files used only as source material.
- "post renderer" could mean plain text, main-branch full renderer, or instant-view parity; resolved: first-batch renderer is the **Basic Cooked Body Renderer**.
- "media viewer" could mean the main-branch Compose viewer or a Telegram port; resolved: the accepted target is a future **Media Viewer Workflow**, not staged Compose viewer files.
- "popup" and "bulletin" could mean content UI or app overlay infrastructure; resolved: first-batch **Transient Overlay Feedback** is excluded and represented with inline states only.
- "ViewModel scope" could mean Activity-wide state or page state; resolved: first-batch topic pages use **Page-Scoped ViewModels** backed by `BaseFragment`.
- "root screen" could mean a temporary topic-only root or the Telegram tab shell; resolved: first-batch uses the **Root Tabs Shell** with minimal tab content.
- "profile restore" could mean full profile/login behavior; resolved: first-batch Profile tab is only the **Profile Skeleton** and does not enter the **Login Workflow**.
- "theme tokens" referred to both main-branch Compose tokens and Telegram theme keys; resolved: the accepted **Theme Source** is Telegram `Theme.java`, not a separate Compose token system.
- "Compose theme" could mean a full Material token layer or a small bridge; resolved: only the **Compose Theme Bridge** is accepted.
- "auth restore" and "login UI" were bundled in main-branch port candidates; resolved: session primitives can land as **NativeDo Business Layer**, while the **Login Workflow** is a separate workflow decision.

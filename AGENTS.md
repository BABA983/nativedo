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

This is the most important implementation constraint for the milestone.

1. Telegram-derived code should be upstream-guided, not freely "inspired by Telegram" rewrites.
2. Prefer the smallest upstream-faithful slice that serves the current milestone:
   - copy a file nearly verbatim when the whole file is needed and its dependencies are acceptable
   - copy or port only the necessary methods/fields when the upstream file contains large out-of-scope systems
   - use minimal NativeDo adapters or compile-only stubs at integration boundaries
   - when upstream has a matching helper/class for the behavior being kept, preserve that helper/class name and shape instead of inventing a replacement wrapper or abstraction
3. For any Telegram behavior that is kept, preserve upstream names, parameters, classes, fields, callbacks, state variables, and control flow as much as practical.
4. Do not rewrite retained Telegram logic just to make it more Kotlin-idiomatic, cleaner, or more abstract.
5. If retained Telegram logic cannot compile because support code is missing, first decide whether that dependency is in scope:
   - if it is in scope, add the missing support code or a minimal compatibility shim
   - if it is out of scope, trim the unsupported branch instead of importing a large unused subsystem
6. Keep NativeDo-specific behavior outside Telegram-derived files when practical. Prefer thin adapters around copied code over mixing business logic into copied code.
7. If a Telegram-derived file requires changes, limit them to what is needed for the current milestone:
   - package rename
   - import rewrite
   - resource reference substitution
   - host-activity boundary wiring
   - compile-only compatibility shims
   - removal of clearly out-of-scope branches
8. Document the copy strategy in the file header or an adjacent note when the file is not a near-verbatim copy:
   - upstream file/path used
   - retained behavior
   - intentionally omitted Telegram subsystems
   - any unavoidable behavior changes

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

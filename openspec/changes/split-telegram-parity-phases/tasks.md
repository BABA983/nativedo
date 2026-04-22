## 1. Freeze The Monolith

- [x] 1.1 Review `openspec/changes/full-rewrite-telegram-parity/tasks.md` and map every existing task group to Phase 0-6 ownership
- [x] 1.2 Mark `full-rewrite-telegram-parity` as umbrella reference for the rewrite plan instead of the sole execution checklist for new work
- [x] 1.3 Identify any currently in-flight implementation items that must pause until Phase 0 audit results exist

## 2. Audit Existing Telegram-Related Files

- [x] 2.1 Inventory the already-landed 1.x, 2.x, and 3.x Telegram-related files and record file path, upstream reference, and current caller status
- [x] 2.2 Classify each inventoried file as `Telegram-derived` or `Telegram-inspired project-specific`
- [x] 2.3 Record parity status for each inventoried file as `strict subset`, `documented divergence`, or `not parity`
- [x] 2.4 Record disposition for each inventoried file as `keep and tighten`, `rework`, `delete`, or `reclassify`
- [x] 2.5 Explicitly audit the current known drift-sensitive files: `AndroidUtilities.kt`, `Utilities.kt`, `DispatchQueue.kt`, `FileLog.kt`, `AnimatedFloat.kt`, `AnimatedColor.kt`, `NotificationCenter.kt`, `AppLifecycleMonitor.kt`, `CubicBezierInterpolator.kt`, `LayoutHelper.kt`, `SpringAnimationUtils.kt`, `AndroidUtilitiesTest.kt`, and `NotificationCenterTest.kt`

## 3. Enforce Telegram Port Governance

- [x] 3.1 Define the repository-standard Telegram-derived GPL header template and required provenance fields for future parity files
- [x] 3.2 Apply or plan provenance remediation for every audited `Telegram-derived` file that currently lacks a compliant header
- [x] 3.3 Document every approved divergence from Telegram upstream with source class, reason, boundary, and affected callers
- [x] 3.4 Reject or queue removal for helper/wrapper APIs that do not satisfy the strict parity or project-specific criteria

## 4. Split Delivery Into Phase Units

- [x] 4.1 Create phase-scoped follow-up changes or task batches for Phase 1 Core Telegram Substrate, Phase 2 Navigation & ActionBar, Phase 3 Cells / Media / Rendering Substrate, Phase 4 Runtime / Auth / Discourse Integration, Phase 5 Feature Scenes, and Phase 6 Polish & Cleanup
- [x] 4.2 For each phase unit, declare owned files or subsystems, upstream references, dependency prerequisites, and exit gates
- [x] 4.3 Ensure feature-scene work is split into standalone page or small-batch phase units instead of returning to one monolithic backlog

## 5. Define Verification Gates

- [x] 5.1 Define Phase 0 completion criteria around audit coverage, provenance status, and disposition completeness
- [x] 5.2 Define host-smoke verification for substrate and navigation phases, including Telegram-style navigation motion and gesture checks
- [x] 5.3 Define contract or integration verification requirements for HTML parsing, session restore, and other data-contract-heavy phases
- [x] 5.4 Verify that no later phase is marked ready until its prerequisite phase gates are documented and satisfied

## 6. Close The Governance Change

- [x] 6.1 Reconcile the new phase plan against `full-rewrite-telegram-parity/proposal.md`, `design.md`, and `tasks.md` so the umbrella artifacts no longer imply free-form execution
- [x] 6.2 Run a final consistency pass on this change to confirm proposal, design, specs, and tasks all enforce the same parity and phase rules

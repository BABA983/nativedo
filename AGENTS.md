# AGENTS.md

## Start here

This is a native linux.do client, not a generic Discourse SDK or a Telegram fork.
Android is active; iOS is a scaffold.

- [README.md](README.md): setup, run and check commands.
- [docs/README.md](docs/README.md): current design knowledge; read the relevant theme.
- [docs/code-layout.md](docs/code-layout.md): ownership and language boundaries.
- [contracts/discourse/README.md](contracts/discourse/README.md): wire/auth semantics;
  keep matching `fixtures/discourse` payloads aligned when these change.

## Active Android build

`apps/android/nativedo` builds against the full `apps/android/telegram` subtree.
Our code belongs in `nativedo`, not at an upstream `org.telegram.*` path.
The sole host seam inside the subtree is `NativeDoHooks` and its narrow call sites.
The legacy `:app` / standalone `TMessagesProj` build is archived at tag
`legacy-partial-mirror` and is no longer in the tree; `apps/android/telegram` is
the only Android build.

- Read the pinned upstream source before changing Telegram integration. Preserve
  upstream rendering, measurement, gesture, animation and recycling behavior.
- The subtree import is the pin; advance it deliberately with `git subtree` for
  a product goal, not on every upstream release. Do not hand-maintain a second pin.
- Run `scripts/telegram-hook-guard.sh` for subtree changes. Its allowed host hooks
  are not permission to rewrite upstream behavior. Extend a hook only when a
  concrete product boundary needs it; keep callbacks instance-bound when appropriate.
- Keep tgnet and Telegram database initialization off. NativeDo owns Discourse
  transport, session and storage. Synthetic Telegram presentation objects do not
  authorize Telegram account/network activity.
- Keep copied license headers. Adapted ports carry SPDX `GPL-2.0-or-later` and
  one short upstream attribution, not structured port-status comment blocks.

## Network and session

Prefer standard Discourse JSON endpoints over scraping. Login is WebView-backed
and cookie-based; do not introduce native username/password handling. Account for
`_t`, `_forum_session`, `cf_clearance`, bootstrap parsing and `/session/csrf`.
Use visible WebView recovery rather than brittle Cloudflare workarounds. A challenge
is not logout; an authenticated cookie does not prove a native write will be accepted.
Never log credentials, cookie values or sensitive request/response content.

## Testing

- **Highly prefer E2E as the sole testing mechanism.** Drive the shipped flow and
  leave reviewable, repeatable evidence (a golden, captured log or transcript).
  Host/fake-site tests are not device/live-site passes.
- **Never write unit tests after you write code.** If
  isolation is necessary, write down the failure modes first, then encode a race,
  persistence/isolation rule, wire round-trip, error/retry path or accessibility contract
  E2E cannot reach. Constants, getter round-trips and "flag was set" are not oracles.
- Follow [testing](docs/testing.md) for evidence scope. No mandatory append to a
  verification Markdown log; report each run with the change it verifies.
- Run Gradle sequentially; use `--quiet` when possible. Commands live in README.
- Docs-only changes need accuracy, consistency and local-link checks, not an APK build.

## Maintaining documentation

- Before user-visible or cross-module behavior changes, explain the goal, non-goals,
  constraints, alternatives and verification approach. Put durable design choices in
  a proposal section of the relevant existing page; simple local changes can stay
  in the discussion. This does not require a new document or a fixed template.
- Update the existing topic in place. Document durable decisions, rationale and
  boundaries; link to code/contracts rather than copying their inventories.
- Distinguish proposals, accepted-but-unimplemented decisions and current behavior.
  Label dated observations and the build they describe; do not present any of these
  as implemented behavior in another build.
- When finishing a change, reconcile affected design pages, contracts, fixtures and
  links in the same change. Replace implemented proposals with current descriptions;
  merge duplicate explanations and delete obsolete text or pages. Keep unresolved
  constraints only while they still matter.
- When sources disagree, inspect the matching build's code/tests and protocol source.
  Code establishes current behavior, not automatically intended product behavior:
  distinguish stale documentation from an implementation defect, and surface an
  unresolved discrepancy rather than silently deciding which to preserve.
- Do not append development diaries, test totals, APK hashes or per-run screenshots
  to README/design pages. Keep run evidence with the change; history belongs in Git.
- Do not create raw-source or archive directories by default. Retain a source only
  when it is necessary to reproduce a contract or decision; fixtures already serve that role.
- Add a page only for a distinct maintained question; link it from `docs/README.md`.
  A design discussion in a thread does not automatically deserve a permanent document.

## CodeGraph

When `.codegraph/` exists, use `codegraph_explore` (MCP) or
`codegraph explore "<symbols or question>"` before grep/read for code discovery.
If unavailable, use normal source discovery; do not index just to satisfy this rule.

Reference sources: the pinned Telegram subtree and
[Discourse API docs](https://docs.discourse.org/).

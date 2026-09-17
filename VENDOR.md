# Vendored Code

This project vendors selected source files from
[Telegram for Android](https://github.com/DrKLO/Telegram) into a standalone
Gradle module, **`:TMessagesProj`**, located at
`apps/android/TMessagesProj/src/main/java/org/telegram/...`. A smaller class of
Telegram-derived code lives in `:app` as **native ports** (upstream component
shape, NativeDo data models).

Telegram for Android is licensed under **GPL-2.0-or-later**. Vendoring its
source makes this app a derivative work — the whole app is GPL-2.0-or-later.
Keep upstream copyright/license headers on copied files.

We are a linux.do client, not a Telegram fork. **The snapshot advances only when
a product goal needs upstream code**, never on every upstream release. That is
why provenance is derived instead of hand-maintained: there is nothing to keep
in sync between releases.

## Model

| Thing | Where | Who owns it |
|---|---|---|
| Pin, sync policy, exceptions | `vendor-overlay.json` | human (small, rarely changes) |
| Provenance index | computed on demand | `scripts/vendor-index.sh` |
| Pristine Telegram checkout | `../telegram` (sibling, gitignored) | `scripts/vendor-setup.sh` |

**There is no lock file, no per-file `modified` flag, and no per-file notes.**
The index is derived from the overlay + the pristine tree + the files on disk,
so it cannot go stale, and hand-editing it is impossible by construction.

### `vendor-overlay.json`

The overlay only lists what the derivation cannot infer:

| Field | Meaning |
|---|---|
| `upstream` | repo, `pinned_commit`, `pinned_date`, `subject`, `local_checkout` |
| `policy` | `sync: on-demand` plus the rationale, third-party prefixes |
| `native` | `:TMessagesProj` files that are *our* code (no upstream parity claim) |
| `upstream_ports` | `:app` ports → the upstream file they were derived from (always native) |
| `derived_from` | a `:TMessagesProj` file whose real upstream source differs from its path |
| `forced_exact` | subsystem lists (`gesture`, `animation`, `measurement`, `drawing`, `recycling`) that must stay upstream-shaped |
| `reference` | paths that intentionally track an older commit (historical baseline) |
| `resources` | copied resource groups (not per-file tracked) |

Everything else — which files have an upstream counterpart, whether they differ
byte-for-byte, which are scratch shims, which third-party trees are carried —
is computed. Adding a copied file needs **no** overlay edit.

### Resource groups

A resource group names a copied set (`kind`, `files`, `local_glob`,
`upstream_dir`, `vendored_at`) and the tools resolve every declared file to its
upstream path (the tail below `res/`/`assets/`, or the group's `paths`
override). Per-group annotations:

| Field | Meaning |
|---|---|
| `paths` | `{"<local rel>": "<upstream rel>"}` when the counterpart is named differently upstream |
| `ours` | declared files with no upstream counterpart (our own assets) |
| `differs` | acknowledged byte differences from `vendored_at` (icon/values edits) |

The guard byte-compares declared resources: an *unlisted* difference is an
error, a listed one that matches again is a warning, and a file the resolver
cannot place is a warning asking for `paths`/`ours`. The comparison needs the
baseline blob contents; a blob-filtered checkout without them reports those
files as "not compared" instead of fetching them one by one
(`scripts/vendor-setup.sh --prefetch` warms them, in parallel).

### Ledger warnings

Two labels can be wrong in ways that make the bump report lie, so the guard
reports both on every run: an `exact` file far smaller than its upstream
original (a stub wearing an upstream path → adopt upstream or list it in
`native`), and a `native` file that is byte-identical to upstream (a clean copy
→ drop it from `native`).

Declaring a path that no longer exists on disk is an error (that is the one kind
of rot the tools can still catch).

## Tooling

| Command | Purpose |
|---|---|
| `scripts/vendor-guard.sh [--quick\|--compile]` | the pre-flight check; errors name the fixing command |
| `scripts/vendor-index.sh [--path X\|--json\|--check]` | print the derived provenance |
| `scripts/vendor-status.sh [--bump-report REF]` | index + isolation + upstream-drift report |
| `scripts/vendor-diff.sh <path> [--pinned\|--at REF]` | `diff -u` against the baseline (or the pin) |
| `scripts/vendor-ff.sh [--to REF] [--apply]` | advance byte-clean `exact` files to a ref |
| `scripts/vendor-classify.sh` | bucket drift on modified files into import/theme/resource/other |
| `scripts/vendor-setup.sh [--status\|--fetch\|--prefetch\|--print-dir]` | manage the pristine checkout |
| `scripts/auto-vendor-copy.sh <javac-log>` | bulk-copy javac's missing classes into `:TMessagesProj` |
| `scripts/vendor-isolation-check.sh` | `:TMessagesProj` must not import `io.github.baba983.nativedo.*` |

Shared helpers live in `scripts/vendorlib.py`. Vendor tooling unit tests:
`python3 -m unittest discover -s scripts/tests -t scripts/tests`.

## Daily use

```bash
# Before committing anything under apps/android/TMessagesProj/** or the overlay
scripts/vendor-guard.sh --compile

# Look at one file's provenance
scripts/vendor-index.sh --path LensCell

# How much drift is there, and how much would a pin bump touch?
scripts/vendor-status.sh
scripts/vendor-status.sh --bump-report origin/master
```

## Pristine checkout

```bash
scripts/vendor-setup.sh              # clone if needed, fetch, checkout pin, warm blobs
scripts/vendor-setup.sh --print-dir  # where the checkout lives
scripts/vendor-setup.sh --status     # pin vs HEAD
```

Location resolution (first match wins):

1. `NATIVEDO_TELEGRAM_VENDOR_DIR` — explicit override, used as-is.
2. `upstream.local_checkout` in the overlay (committed default `../telegram`, the
   upstream repo name). A sibling named `Telegram` also matches
   (case-insensitive), so a plain `git clone …/DrKLO/Telegram.git` works.
3. A leftover in-repo `vendor/telegram-android` from before the move.

Clone the repo and the checkout side by side — the checkout is never committed.

## Adding a copied file

1. Copy it (`scripts/auto-vendor-copy.sh /tmp/javac-out.log` for bulk javac
   misses) — package stays `org.telegram.*`, no rewrite.
2. Adjust imports / host wiring as needed.
3. If it is **our** code rather than a copy, add it to the overlay's `native`.
4. `scripts/vendor-guard.sh --compile`, then commit.

## Re-syncing to a newer upstream

Only on demand, for a concrete product goal:

```bash
scripts/vendor-setup.sh --fetch
VD="$(scripts/vendor-setup.sh --print-dir)"
git -C "$VD" log <pin>..origin/master -- <upstream-path>
scripts/vendor-diff.sh <local-path> --at origin/master
```

Then either adopt the upstream change by hand (update the overlay pin when the
whole tree is ready to advance), or leave it and note the decision in the commit
message. `scripts/vendor-status.sh --bump-report` splits the work:

| Track | Condition | Action |
|---|---|---|
| Fast-forward | `exact`, byte-clean vs the pin | `scripts/vendor-ff.sh --to REF --apply` |
| Manual rebase | `exact`, modified | triage with `scripts/vendor-classify.sh`, apply by hand |
| Reference only | `native`, or `reference` paths | nothing; they do not track the pin |

Bumping the pin is a deliberate, heavy operation: record it in the overlay
(`pinned_commit`, `pinned_date`, `subject`) in the same commit as the files.

## History

The previous model (compact manifest + generated lock + per-file `notes`,
`modified` flags, `stuck_reason`) was retired in favour of the overlay + derived
index. The notes were archived verbatim in
[`docs/vendor-notes-archive.md`](docs/vendor-notes-archive.md) (historical, not
maintained); git history has the rest.

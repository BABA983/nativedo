# Vendored Code

This project vendors selected source files from
[Telegram for Android](https://github.com/DrKLO/Telegram) into a standalone
Gradle module, **`:telegram`**, located at
`apps/android/telegram/src/main/java/org/telegram/...`. A smaller, second
class of Telegram-derived code lives in `:app` as **native ports** that mirror
upstream component shape but bind to NativeDo data models (see
[`docs/code-layout.md`](docs/code-layout.md)).

Telegram for Android is licensed under **GPL-2.0-or-later**. Vendoring its
source makes this app a derivative work of Telegram — the whole app is therefore
GPL-2.0-or-later as well. Keep upstream copyright/license headers on copied
files, and don't strip the GPL notice when modifying them.

We do **not** merge upstream changes automatically. Vendored files
may be modified locally; the rules for *what kind of changes are allowed*
live in `AGENTS.md` (Telegram-Derived Code → Rules). This file describes
*what version we vendored* and *how to inspect / sync it*.

## Source of truth

- Upstream repo: <https://github.com/DrKLO/Telegram>
- Pinned commit: see `vendor-manifest.json` (`upstream.pinned_commit`)
- Pristine working copy: `vendor/telegram-android/` (read-only, do not edit)

The pristine copy is a `git clone` (or worktree) of the upstream repo checked
out at `upstream.pinned_commit`. It is **not** committed to this repo. The pin
in the compact, human-owned `vendor-manifest.json` is the single source of
truth. `vendor-manifest.lock.json` is its generated flat expansion.

Bootstrap / refresh it with:

```bash
scripts/vendor-setup.sh            # clone if needed, fetch, checkout pin
scripts/vendor-setup.sh --status   # pin vs HEAD
scripts/vendor-setup.sh --fetch    # fetch only
```

## Manifest

Every vendored file MUST have an effective entry generated from
`vendor-manifest.json`. The source manifest is compact: common pins, reasons,
path prefixes, and defaults are declared once; per-file overrides live under
`file_groups`. `vendor-manifest.lock.json` expands those groups into the flat
per-file records consumed by the scripts.

Together they record:

- which local files came from Telegram
- which upstream file each one came from
- whether the local file differs byte-for-byte from upstream
- which commit it was vendored from
- which subsystems (gesture / animation / measurement / drawing / recycling)
  must stay upstream-shaped (`forced_exact`)
- whether the port is `exact` or `native` (see AGENTS.md)

Only copied, split-out, or substantially adapted Telegram source is vendored.
NativeDo code written from scratch does not belong in the manifest, even when
it uses Telegram-like UI or architecture patterns. A copied or substantially
adapted Telegram file without a manifest entry is a bug — please add the
entry. The narrow exception is a scratch `org.telegram.*` compile surface:
track it under `compat_shim_groups` so it is discoverable without claiming it
as vendored Telegram source.

After editing the compact source, regenerate and verify the lock:

```bash
scripts/vendor-manifest.sh --write
scripts/vendor-manifest.sh --check

# Replace one stable per-file note and regenerate source + lock
scripts/vendor-manifest.sh --set-note <local-path> --note "<current contract>"
```

Do not edit `vendor-manifest.lock.json` directly.

### Where paths point

`local_root` is `apps/android/`. A `local` field always reads relative to
that:

- `telegram/src/main/java/org/telegram/...` — file in the `:telegram` module
  (most common; package stays `org.telegram.*`)
- `app/src/main/java/io/github/baba983/nativedo/...` — native port in `:app`
  (package is `io.github.baba983.nativedo.*`)

## Tooling

| Script | Purpose |
|---|---|
| `scripts/vendor-manifest.sh` | Generate/check the flat lock from the compact source manifest |
| `scripts/vendor-setup.sh` | Clone/fetch/checkout `vendor/telegram-android` at the pin |
| `scripts/vendor-status.sh` | Manifest health, isolation, unmanifested files, behind-pin |
| `scripts/vendor-diff.sh` | `diff -u` local file vs upstream blob |
| `scripts/vendor-ff.sh` | Fast-forward `exact` + `modified:false` files to the pin |
| `scripts/vendor-classify.sh` | Classify host-wiring drift on `modified` files |
| `scripts/auto-vendor-copy.sh` | Copy javac-missing symbols **and** register them in the manifest |
| `scripts/vendor-isolation-check.sh` | `:telegram` must not import `io.github.baba983.nativedo.*` |
| `vendor-allowlist.json` | Prefixes allowed on disk without a per-file manifest entry |

Shared helpers live in `scripts/vendorlib.py`. Prefer the shell wrappers above.

### Daily checks

```bash
# Source → generated-lock drift (fast; no pristine tree needed)
scripts/vendor-manifest.sh --check

# Health summary (exit 1 on consistency errors)
scripts/vendor-status.sh

# Stricter: warnings also fail
scripts/vendor-status.sh --strict

# Byte-check modified flags against vendored_at (slow; needs pristine tree)
scripts/vendor-status.sh --check-modified

# Machine-readable
scripts/vendor-status.sh --json
```

### Diff a file

```bash
# Against the entry's vendored_at
scripts/vendor-diff.sh telegram/src/main/java/org/telegram/ui/Components/RecyclerListView.java

# Against the global pin
scripts/vendor-diff.sh apps/android/telegram/src/main/java/org/telegram/ui/Components/RecyclerListView.java --pinned

# Against an arbitrary commit
scripts/vendor-diff.sh org/telegram/ui/Components/RecyclerListView.java --at origin/master

# Scan for modified-flag lies
scripts/vendor-diff.sh --stat
```

### Bulk-copy missing classes (javac)

```bash
./apps/android/gradlew -p apps/android :telegram:compileDebugJavaWithJavac \
    --quiet > /tmp/javac-out.log 2>&1 || true
scripts/auto-vendor-copy.sh /tmp/javac-out.log
scripts/vendor-isolation-check.sh
scripts/vendor-status.sh
```

`auto-vendor-copy` **always** writes/updates the compact source and regenerates
the lock for each copied file (`port_mode: exact`, `modified: false`,
`vendored_at: pin`). Do not `cp` upstream files by hand without a matching
manifest entry.

### Isolation

```bash
scripts/vendor-isolation-check.sh
```

### Fast-forward exact unmodified files (P2)

When the global pin moves, files that are still byte-identical to their
`vendored_at` blob can be advanced automatically:

```bash
# Dry-run (default): list what would overwrite
scripts/vendor-ff.sh

# Apply: copy target blob → local, set vendored_at, keep modified:false
scripts/vendor-ff.sh --apply

# Optional filters
scripts/vendor-ff.sh --only BottomSheet --apply
scripts/vendor-ff.sh --to origin/master   # dry-run against another ref
scripts/vendor-ff.sh --include-unchanged --apply  # also bump vendored_at when blob identical
```

Safety rails:

- only `port_mode: exact` **and** `modified: false`
- refuses to overwrite if local no longer matches `vendored_at` (fix the flag
  first, or pass `--force` deliberately)
- skips files whose upstream path is missing at the target commit

**Compile gate:** bulk `--apply` is not always safe — a file can be
byte-identical to its old `vendored_at` and still fail to compile after
advancing to a newer pin (new upstream symbols). Prefer:

1. `scripts/vendor-ff.sh` dry-run
2. apply in small batches, or one-by-one with
   `:telegram:compileDebugJavaWithJavac` between steps
3. on failure: restore the file, set `stuck_reason`, leave `vendored_at`

Entries with `stuck_reason` are **skipped** by `vendor-ff` unless
`--include-stuck` is passed.

After apply: `scripts/vendor-status.sh` and compile `:telegram`.

### Host-wiring classification (P2)

```bash
scripts/vendor-classify.sh                 # all modified entries
scripts/vendor-classify.sh --port-mode exact
scripts/vendor-classify.sh --only RecyclerListView
scripts/vendor-classify.sh --json
```

Buckets changed lines into `import` / `theme` / `resource_r` /
`nativedo_guard` / `package` / `comment_or_blank` / `other`, and reports a
`primary` label (`import_only`, `host_wiring_only`, `identical`, `other`, …).
Also lists `modified:false` entries whose bytes still differ (flag lies).

Use this before a rebase-assisted re-sync: import-only / host-wiring-only
files are cheaper to re-apply than `other`.

### Forced-exact regression (P2)

Hand-feel subsystems need more than code review. Prefer existing Paparazzi +
geometry unit tests over inventing a second visual stack.

| Kind | What | How |
|---|---|---|
| Geometry / constants | `LayoutHelper`, `CircularProgressDrawable` segment schedule, side-controls measure | Robolectric unit tests under `app/src/test/.../org/telegram/...` |
| Visual golden | Switch / CheckBox2 / CircularProgressDrawable | `ForcedExactControlsScreenshotTest` (Paparazzi) |
| Product surfaces | Topic swipe / scrim / cells (already native ports) | existing `*ScreenshotTest` |

```bash
# Geometry
./apps/android/gradlew -p apps/android :app:testDebugUnitTest \
  --tests 'org.telegram.ui.Components.LayoutHelperForcedExactTest' \
  --tests 'org.telegram.ui.Components.CircularProgressDrawableForcedExactTest' \
  --quiet

# Record / verify Paparazzi goldens
./scripts/recordScreenshots ForcedExactControlsScreenshotTest
./apps/android/gradlew -p apps/android :app:testDebugUnitTest \
  --tests 'org.telegram.ui.Components.ForcedExactControlsScreenshotTest' --quiet
# or whole suite:
./apps/android/gradlew -p apps/android runUnitAndScreenshotTests --quiet
```

When you re-sync a `forced_exact` component, re-record its golden if the
upstream drawing intentionally changed; otherwise a failed Paparazzi verify
is a regression signal.

## Adding a new vendored file

1. Confirm the file copies, splits out, or substantially adapts Telegram source.
   If it is written from scratch, it must not enter `file_groups`. A scratch
   upstream-package compile surface may instead enter `compat_shim_groups`.
2. For files going into `:telegram`: `cp` the upstream file into the same
   relative path under `apps/android/telegram/src/main/java/org/telegram/`.
   Package stays `org.telegram.*`; no `sed` rewrite needed.
3. For native ports going into `:app`: copy into the appropriate
   `io.github.baba983.nativedo.*` location, rewrite package and imports,
   bind to NativeDo models.
4. Adjust imports and host wiring (see `AGENTS.md` → Host wiring).
5. Add an entry under the appropriate `file_groups` group in
   `vendor-manifest.json`. Omit fields inherited from the defaults. Set `pin`
   only when the file is not at `upstream.pinned_commit`; set `modified: true`
   for import drops, theme-key rewrites, or constructor glue.
6. Run `scripts/vendor-manifest.sh --write` and
   `scripts/vendor-manifest.sh --check`.
7. Run `scripts/vendor-isolation-check.sh` and `scripts/vendor-status.sh`.
8. `./apps/android/gradlew -p apps/android :telegram:compileDebugJavaWithJavac
   --quiet` (and `:app:assembleDebug` if app code is affected).
9. Commit the source file, compact manifest, and generated lock together.

## Re-syncing one file to a newer upstream

1. `scripts/vendor-setup.sh --fetch` (or full setup).
2. Look at upstream activity:

   ```bash
   git -C vendor/telegram-android log <vendored_at>..origin/master -- <upstream-path>
   scripts/vendor-diff.sh <local-path> --at origin/master
   ```

3. If you want to absorb the changes:
   - update the local file (manually re-apply any local modifications)
   - update `pin`/the corresponding pin alias for that entry in the compact
     manifest
   - set `modified` correctly (`false` only if byte-identical)
   - clear `stuck_reason` if present
   - run tests that cover the file
4. If you decide *not* to absorb:
   - leave `vendored_at` as is
   - add a `stuck_reason` field explaining why

## Bumping the global pin

The top-level `upstream.pinned_commit` is the version the codebase as a
whole is *known to be safely re-syncable to*. Bump it only after:

- re-syncing every file that upstream has touched between the old and
  new pin, or
- explicitly skipping each file with a `stuck_reason`.

### Pin-bump report

```bash
# Files in the manifest that upstream touched between current pin and NEW
scripts/vendor-status.sh --bump-report <new_sha>
scripts/vendor-status.sh --bump-report origin/master
```

Tracks (`port_mode` / `modified`) guide the work:

| Track | Condition | Action |
|---|---|---|
| Fast-forward | `exact` + `modified: false` | `scripts/vendor-ff.sh --apply` |
| Rebase-assisted | `exact` + `modified: true` | 3-way merge; triage with `scripts/vendor-classify.sh` |
| Reference-only | `native` | Do not auto-overwrite; re-check `forced_exact` + Paparazzi/geometry tests |

This is a deliberately heavy operation; don't bump the pin casually.

After a successful bump, set the compact source manifest's upstream block:

```json
"upstream": {
  "repo": "git@github.com:DrKLO/Telegram.git",
  "pinned_commit": "<full 40-char sha>",
  "pinned_date": "YYYY-MM-DD"
}
```

Use the **full** SHA in `pinned_commit` and under `aliases.pins`. Entries at the
global pin omit `pin`; historical entries reference a pin alias. Short prefixes
are normalized by tooling but should not be written back.

## Manifest schema

Both manifest files are plain JSON. `vendor-manifest.json` is compact and
human-owned; `vendor-manifest.lock.json` is generated and flat.

Compact source structure:

| Field | Meaning |
|---|---|
| `schema_version` | Compact source schema version. |
| `upstream` | Repository, global pin, and pin date. |
| `defaults` | Values inherited by ordinary vendored entries. |
| `aliases.pins` | Full historical SHAs referenced by short logical names. |
| `aliases.stuck_reasons` | Reused synchronization blockers, stored once. |
| `aliases.notes` | Reused stable notes, stored once. |
| `file_groups` | Copied, split-out, or substantially adapted Telegram source. |
| `compat_shim_groups` | Scratch upstream-package compile/runtime surfaces; never eligible for vendor-ff. |
| `resources` | Copied resource groups. |

Each group declares `local_prefix`, optional `upstream_prefix`, and `entries`.
An entry's `path` is relative to `local_prefix`; `upstream` is omitted when it
can be derived from the two prefixes. Fields equal to the defaults are omitted.

Compact per-entry overrides:

| Field | Meaning |
|---|---|
| `path` | Path relative to the group's `local_prefix`. |
| `upstream` | Explicit upstream path when prefix mapping cannot derive it. |
| `pin` | Optional key in `aliases.pins`; omission means the global pin. |
| `modified` | Optional byte-drift override; ordinary entries default to `false`. |
| `port_mode` | Optional `exact`/`native` override; ordinary entries default to `exact`. |
| `forced_exact` | Optional forced-exact subsystem list. |
| `notes` / `notes_ref` | Stable current delta, or a key in `aliases.notes`. |
| `stuck_reason` / `stuck_ref` | Concrete FF blocker, or a key in `aliases.stuck_reasons`. |

`notes` must describe stable current host wiring, omissions, or behavior. Keep
phase-by-phase history and validation logs in Git or the relevant design doc.
The generated lock expands these into the established per-file fields:

| Field | Meaning |
|---|---|
| `local` | Path under `local_root` (the file you actually compile). |
| `upstream` | Path under `upstream_root` in the Telegram repo. |
| `vendored_at` | **Full** commit SHA in the Telegram repo this copy came from. |
| `modified` | `true` if the local file differs byte-for-byte from `vendored_at`. Package/import/host wiring counts. |
| `port_mode` | `exact` or `native`. Mirrors `AGENTS.md`. |
| `forced_exact` | Optional list. Subset of `gesture`, `animation`, `measurement`, `drawing`, `recycling`. Mirrors `AGENTS.md`. |
| `notes` | Optional stable string. |
| `stuck_reason` | Optional. Why this file is intentionally pinned to an older `vendored_at` than `upstream.pinned_commit`. |

Resource entries under `resources` follow the same shape but list copied
resource basenames, the upstream resource directory, the local directory
glob, and the commit they were copied from.

## Unmanifested files & allowlist

`:telegram` may contain files that are not per-file Telegram ports:

| Bucket | Policy |
|---|---|
| `androidx/` under `:telegram` | **Allowlisted** tree — Telegram's RecyclerView fork. See `vendor-allowlist.json`. |
| `com/google/android/exoplayer2/`, GMS/common shims | **Allowlisted** compile surface — not UI ports. |
| Scratch `org.telegram.*` / `me.vkryl.*` compile stubs | Track under `compat_shim_groups`; they are discoverable but are not vendored source. |
| Anything else on disk without entry or allowlist | `vendor-status` **warning** with bucket counts. |

```bash
# prefixes in vendor-allowlist.json are excluded from the unmanifested warning
scripts/vendor-status.sh
```

When auto-copying a missing symbol, prefer `scripts/auto-vendor-copy.sh` (registers
manifest entries). Do not leave new `org.telegram.*` files untracked.

## Consistency policy (enforced by `vendor-status`)


**Errors (exit 1):**

- compact source / generated lock drift (`scripts/vendor-manifest.sh --check`)
- `vendored_at` null/missing on any file or resource group
- manifest `local` path missing on disk
- duplicate `local` entries
- invalid `port_mode`
- compatibility shim not `native` + `modified:true`
- `vendor-isolation-check` failures
- with `--check-modified`: `modified` flag disagrees with bytes

**Warnings (exit 0 unless `--strict`):**

- `:telegram` `.java` files on disk not listed in the manifest
- files with `vendored_at` behind the global pin and no `stuck_reason`
- pristine tree missing or HEAD ≠ pin

Historical `vendored_at` values behind the global pin are allowed during
incremental re-sync; prefer adding `stuck_reason` when the lag is intentional.

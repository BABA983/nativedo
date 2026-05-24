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
*what version we vendored* and *how to inspect it*.

## Source of truth

- Upstream repo: <https://github.com/DrKLO/Telegram>
- Pinned commit: see `vendor-manifest.json` (`upstream.pinned_commit`)
- Pristine working copy: `vendor/telegram-android/` (read-only, do not edit)

The pristine copy is a manual `git clone` (or worktree) of the upstream repo
checked out at `upstream.pinned_commit`. It is **not** committed to this repo
(`vendor/telegram-android/` is gitignored). The pin in `vendor-manifest.json`
is the single source of truth.

## Manifest

Every vendored file MUST have an entry in `vendor-manifest.json`. The
manifest is the single source of truth for:

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
entry.

### Where paths point

`local_root` is `apps/android/`. A `local` field always reads relative to
that:

- `telegram/src/main/java/org/telegram/...` — file in the `:telegram` module
  (most common; package stays `org.telegram.*`)
- `app/src/main/java/io/github/baba983/nativedo/...` — native port in `:app`
  (package is `io.github.baba983.nativedo.*`)

## Common tasks

```bash
# Verify :telegram does not import io.github.baba983.nativedo.*
scripts/vendor-isolation-check.sh

# Bulk-copy missing vendored classes that javac is complaining about.
# Reads a javac error log and copies referenced upstream files into
# :telegram with no package rewrite. Reads vendor/telegram-android/ as source.
./apps/android/gradlew -p apps/android :telegram:compileDebugJavaWithJavac \
    --quiet > /tmp/javac-out.log 2>&1 || true
scripts/auto-vendor-copy.sh /tmp/javac-out.log
```

> **TODO (tooling)**: previously documented helpers `setup-vendor.sh`,
> `vendor-diff.sh`, and `vendor-status.sh` do not exist yet. Until they
> land, use `git -C vendor/telegram-android show <pinned_commit>:<upstream-path>`
> to inspect a vendored file's pristine upstream form, and inspect the
> manifest by hand or with `jq`.

### Inspecting a vendored file against upstream

```bash
# Compare the local copy against the pinned upstream form
COMMIT=$(jq -r .upstream.pinned_commit vendor-manifest.json)
LOCAL=apps/android/telegram/src/main/java/org/telegram/ui/Components/RecyclerListView.java
UPSTREAM=TMessagesProj/src/main/java/org/telegram/ui/Components/RecyclerListView.java
diff -u <(git -C vendor/telegram-android show "$COMMIT:$UPSTREAM") "$LOCAL"
```

If you do this often, wrap it in `scripts/vendor-diff.sh <local-path>`
(not yet written).

## Adding a new vendored file

1. Confirm the file copies, splits out, or substantially adapts Telegram source.
   If it is written from scratch, stop here: it is NativeDo code and should not
   get a manifest entry.
2. For files going into `:telegram`: `cp` the upstream file into the same
   relative path under `apps/android/telegram/src/main/java/org/telegram/`.
   Package stays `org.telegram.*`; no `sed` rewrite needed.
3. For native ports going into `:app`: copy into the appropriate
   `io.github.baba983.nativedo.*` location, rewrite package and imports,
   bind to NativeDo models.
4. Adjust imports and host wiring (see `AGENTS.md` → Host wiring).
5. Add an entry to `vendor-manifest.json`. Set `vendored_at` to the commit
   you copied from (usually `upstream.pinned_commit`). For files copied
   byte-identical from upstream, `modified: false`. For files that needed
   any import drop, theme-key rewrite, or constructor glue, `modified: true`.
6. Run `scripts/vendor-isolation-check.sh` and confirm no violations.
7. `./apps/android/gradlew -p apps/android :telegram:compileDebugJavaWithJavac
   --quiet` (and `:app:assembleDebug` if app code is affected).
8. Commit the source file and the manifest change together.

## Re-syncing one file to a newer upstream

1. `cd vendor/telegram-android && git fetch origin`
2. Look at upstream activity:

   ```bash
   git log <vendored_at>..origin/master -- <upstream-path>
   ```

3. If you want to absorb the changes:
   - update the local file (manually re-apply any local modifications)
   - update `vendored_at` for that entry in the manifest
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

This is a deliberately heavy operation; don't bump the pin casually.

## Manifest schema

`vendor-manifest.json` is plain JSON (no JSON5) so `jq` can read it. It
uses two conventions for human notes:

- `_comment` fields anywhere in the structure are ignored by tooling.
  Use them as section headers or schema reminders.
- `notes` on a file entry can be a string or an array of strings. The
  array form is just `["line 1", "line 2"]` and is concatenated with
  newlines if you ever pretty-print it.

Per-file fields:

| Field | Meaning |
|---|---|
| `local` | Path under `local_root` (the file you actually compile). |
| `upstream` | Path under `upstream_root` in the Telegram repo. |
| `vendored_at` | Commit SHA in the Telegram repo this copy came from. |
| `modified` | `true` if the local file differs byte-for-byte from `vendored_at`. Package/import/host wiring counts. |
| `port_mode` | `exact` or `native`. Mirrors `AGENTS.md`. |
| `forced_exact` | Optional list. Subset of `gesture`, `animation`, `measurement`, `drawing`, `recycling`. Mirrors `AGENTS.md`. |
| `notes` | Optional. String or array of strings. |
| `stuck_reason` | Optional. Why this file is intentionally pinned to an older `vendored_at` than `upstream.pinned_commit`. |

Resource entries under `resources` (if/when added) follow the same shape but
list copied resource basenames, the upstream resource directory, the local
directory glob, and the commit they were copied from.

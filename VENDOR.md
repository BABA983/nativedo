# Vendored Code

This project vendors selected source files from
[Telegram for Android](https://github.com/DrKLO/Telegram) into our
own package namespace under
`apps/android/app/src/main/java/io/github/baba983/nativedo/`.

Telegram for Android is licensed under **GPL-2.0-or-later**. Vendoring its
source makes this app a derivative work of Telegram — the whole app is therefore
GPL-2.0-or-later as well. Keep upstream copyright/license headers on copied
files, and don't strip the GPL notice when modifying them.

We do **not** merge upstream changes automatically. Vendored files
may be modified locally; the rules for *what kind of changes are allowed*
live in `AGENTS.md` (Telegram-Derived Code → Rules). This file describes
only *what version we vendored* and *how to inspect it*.

## Source of truth

- Upstream repo: <https://github.com/DrKLO/Telegram>
- Pinned commit: see `vendor-manifest.json` (`upstream.pinned_commit`)
- Pristine working copy: `vendor/telegram-android/` (read-only, do not edit)

The pristine copy is set up by `scripts/setup-vendor.sh` and is **not**
committed to this repo (`vendor/telegram-android/` is gitignored). The
pin in `vendor-manifest.json` is the single source of truth.

## Manifest

Every vendored file MUST have an entry in `vendor-manifest.json`. The
manifest is the single source of truth for:

- which local files came from Telegram
- which upstream file each one came from
- whether the local file differs byte-for-byte from upstream
- which commit it was vendored from

Only copied, split-out, or substantially adapted Telegram source is vendored.
NativeDo code written from scratch does not belong in the manifest, even when it
uses Telegram-like UI or architecture patterns. A copied or substantially
adapted Telegram file without a manifest entry is a bug — please add the entry.

## Common tasks

```bash
# One-time setup (also works to refresh after a pin bump)
./scripts/setup-vendor.sh

# Diff one vendored file against its pinned upstream
./scripts/vendor-diff.sh ui/components/RecyclerListView.java

# Lint: report manifest entries whose `modified` field is wrong,
# or source/resource files that don't exist locally / upstream
./scripts/vendor-status.sh

# Lint with diff sizes for every modified file
./scripts/vendor-status.sh --verbose
```

`vendor-status.sh` exits non-zero on any DRIFT / MISSING line — you can
wire it into a pre-commit hook later if the noise is worth it.

## Adding a new vendored file

1. Confirm the file copies, splits out, or substantially adapts Telegram source.
   If it is written from scratch, stop here: it is NativeDo code and should not
   get a manifest entry.
2. Copy the upstream file into the right local path under `local_root`.
3. Adjust package, imports, and host wiring (see `AGENTS.md`
   `Host wiring`).
4. Add an entry to `vendor-manifest.json`. Set `vendored_at` to the
   commit you copied from (usually `upstream.pinned_commit`).
5. Run `./scripts/vendor-status.sh` and confirm there is no DRIFT line
   for the new entry. If package/import/host wiring made the file differ
   from upstream, set `modified` to `true` even when behavior stayed exact.
6. Commit the source file and the manifest change together.

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
| `modified` | `true` if the local file differs byte-for-byte from `vendored_at`. Package/import/host wiring counts. Lint enforced. |
| `port_mode` | `exact` or `native`. Mirrors `AGENTS.md`. |
| `forced_exact` | Optional list. Subset of `gesture`, `animation`, `measurement`, `drawing`, `recycling`. Mirrors `AGENTS.md`. |
| `notes` | Optional. String or array of strings. |
| `stuck_reason` | Optional. Why this file is intentionally pinned to an older `vendored_at` than `upstream.pinned_commit`. |

Resource entries under `resources` are also linted. Each entry lists copied
resource basenames, the upstream resource directory, the local directory glob,
and the commit they were copied from.

# Legacy Telegram vendoring

**Scope: `apps/android/app` + standalone `apps/android/TMessagesProj` only.**
The active app uses a full subtree; see [upstream hosting](docs/upstream-hosting.md).
Keep this workflow while the legacy code exists. It is not a backlog for converting
the active build, and removing the legacy stack needs explicit agreement.

Vendoring Telegram makes the app a GPL-2.0-or-later derivative. Keep upstream
copyright/license headers and short attribution on adapted ports. Advance the
snapshot only when a product goal needs it, not on every upstream release.

## Source of truth

[vendor-overlay.json](vendor-overlay.json) is the only hand-maintained provenance
file. The index is derived from that overlay, pristine upstream blobs and local
files; there is no lock file, per-file modified flag or notes ledger.

| Overlay field | Meaning |
|---|---|
| `upstream` | repository, pinned commit and pristine checkout location |
| `policy` | on-demand sync and third-party prefixes |
| `native` | our shims/ports at library paths; no upstream parity claim |
| `upstream_ports` | product-side ports mapped to their upstream source |
| `derived_from` | mirror whose upstream source has a different path |
| `forced_exact` | subsystems whose upstream behavior/shape must be preserved |
| `reference` | intentional older baselines, not candidates for automatic re-sync |
| `resources` | copied resource groups with path/ownership/difference exceptions |

An ordinary new copied file needs no overlay entry. A native file must not hide
as an exact mirror. The guard catches missing paths and unacknowledged resource
differences, and warns about stub-sized exact files or byte-identical native files.
Resource groups use `paths` for renamed counterparts, `ours` for original assets
and `differs` for acknowledged byte differences. Prefetch missing baseline blobs
before claiming a resource was compared.

## Pristine checkout and inspection

```sh
./scripts/vendor-setup.sh
./scripts/vendor-setup.sh --print-dir
./scripts/vendor-setup.sh --status
./scripts/vendor-index.sh --path LensCell
./scripts/vendor-diff.sh <local-path> --pinned
./scripts/vendor-status.sh
```

The checkout is not a build dependency. Location resolution prefers
`NATIVEDO_TELEGRAM_VENDOR_DIR`, then overlay `upstream.local_checkout` (default
`../telegram`, including case-insensitive `Telegram`), then the old in-repo
`vendor/telegram-android` fallback. Audits read `git show <baseline>:<path>`, not
the checkout's current working files, so an ahead HEAD does not change the pin.
`vendor-setup.sh --prefetch` warms resource blobs; `--fetch` updates remote refs.

## Edit a mirror or port

1. Read its upstream source with `vendor-diff.sh` before editing. Preserve upstream
   packages; copied source belongs in `TMessagesProj`, product-bound ports in `app`.
2. Make only the required host wiring. If a file becomes our redesigned port/shim,
   classify it honestly in the overlay. The library must not import NativeDo code;
   host callbacks cross an existing library-owned interface.
3. Respect `forced_exact` method shape, signatures, imports, constants and branch
   structure for the listed gesture/animation/measurement/drawing/recycling subsystems.
   The guard names the observable gate; constants mirrored in a unit test are not a gate.
4. Leave `reference` paths at their declared older baseline unless deliberately
   changing that decision. Keep license headers; do not add structured port-status blocks.
5. Run `vendor-guard.sh --compile` and the named tests/visual checks. Commit related
   source and overlay changes together when committing is authorized.

Bulk javac missing-class copies use `scripts/auto-vendor-copy.sh <javac-log>`.
Test design and evidence rules are in [AGENTS](AGENTS.md) and [testing](docs/testing.md).

## Re-sync for a product goal

```sh
./scripts/vendor-setup.sh --fetch
./scripts/vendor-status.sh --bump-report <new-ref>
./scripts/vendor-diff.sh <local-path> --at <new-ref>
./scripts/vendor-classify.sh
# Inspect the proposed fast-forward before applying it.
./scripts/vendor-ff.sh --to <new-ref>
```

Byte-clean exact files can fast-forward (`vendor-ff.sh --apply`); modified mirrors
need manual triage/rebase. Native and reference paths do not automatically follow
the pin. Record a deliberate global pin advance in the overlay (`pinned_commit`,
`pinned_date`, `subject`) with its source changes, not before undecided drift is resolved.
A patch lying beside the pristine checkout is not pending work: establish whether
its content is already integrated before applying anything.

## Legacy host boundaries to preserve

These are protections for existing legacy wiring, not claims about the active subtree:

- Do not restart the old ArticleViewer alignment epic or replace its whole file
  with the pin. Browser/Instant View adaptations retain local page models,
  browser settings, bookmarks, file chooser and preview behavior.
- `WebBookmarks` is local storage, not Telegram Saved Messages. Wholesale adoption
  of `ui/iv` introduces another renderer/dependency; it is not routine re-sync.
- Theme uses one model, with product storage/remote capability through `ThemeBackend`.
  A remote theme catalog needs a product backend decision, not a second state machine.
- zxing comes from Maven; unused media3/ExoPlayer compile-time shims are native.
  Playback or other newly adopted dependencies require a product goal.
- The `reference` overlay, not a copied list in this document, identifies older
  components. Removing one of those exceptions is a deliberate change.

Old rich-message/editor/chat plans are not active implementation instructions.
For current content and state choices, read [content/composer](docs/content-composer.md)
and [reading/state](docs/reading-state.md).

## Gates and tooling

```sh
./scripts/vendor-guard.sh --compile
./scripts/vendor-isolation-check.sh
python3 -m unittest discover -s scripts/tests -t scripts/tests
```

`--quick` is the pre-commit check; `--compile` adds the legacy library build.
`vendor-index.sh --json` prints machine-readable provenance; `--check` validates it.
Helpers live in `scripts/vendorlib.py`. Legacy Paparazzi gates and build commands
live in [README](README.md#legacy-build).

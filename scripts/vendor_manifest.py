#!/usr/bin/env python3
"""Generate and verify the flat vendoring lock from the compact source.

The human-owned source is vendor-manifest.json. Existing vendoring tools consume
the expanded vendor-manifest.lock.json through vendorlib.load_manifest().
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    LOCK_MANIFEST_NAME,
    SOURCE_MANIFEST_NAME,
    ManifestError,
    bootstrap_manifest_source,
    check_manifest_lock,
    expand_source_manifest,
    load_manifest,
    load_source_manifest,
    manifest_lock_path,
    regenerate_manifest_lock,
    repo_root,
    save_manifest,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument(
        "--check",
        action="store_true",
        help="fail when the generated lock is missing or stale",
    )
    action.add_argument(
        "--write",
        action="store_true",
        help="regenerate vendor-manifest.lock.json from the compact source",
    )
    action.add_argument(
        "--bootstrap",
        action="store_true",
        help="one-time conversion of a legacy flat vendor-manifest.json",
    )
    action.add_argument(
        "--set-note",
        metavar="LOCAL_PATH",
        help="replace one effective entry's stable note and regenerate source+lock",
    )
    parser.add_argument(
        "--compat-shim",
        action="append",
        default=[],
        metavar="LOCAL_PATH",
        help="with --bootstrap, move this verified scratch shim out of files[]",
    )
    parser.add_argument(
        "--note",
        help="replacement text for --set-note",
    )
    args = parser.parse_args()
    if args.note and not args.set_note:
        parser.error("--note is only valid with --set-note")
    if args.compat_shim and not args.bootstrap:
        parser.error("--compat-shim is only valid with --bootstrap")
    return args


def main() -> int:
    args = parse_args()
    root = repo_root()
    try:
        if args.bootstrap:
            source = bootstrap_manifest_source(
                root,
                compat_shim_locals=set(args.compat_shim),
            )
            expanded = expand_source_manifest(source)
            print(
                f"bootstrapped {SOURCE_MANIFEST_NAME}: "
                f"files={len(expanded['files'])} "
                f"compat_shims={len(expanded['compat_shims'])}"
            )
            print(f"wrote {manifest_lock_path(root).relative_to(root)}")
            return 0

        if args.write:
            expanded = regenerate_manifest_lock(root)
            print(
                f"wrote {LOCK_MANIFEST_NAME}: "
                f"files={len(expanded['files'])} "
                f"compat_shims={len(expanded['compat_shims'])}"
            )
            return 0

        if args.set_note:
            note = (args.note or "").strip()
            if not note:
                raise ManifestError("--set-note requires non-empty --note text")
            manifest = load_manifest(root)
            matches = [
                entry
                for key in ("files", "compat_shims")
                for entry in manifest.get(key, [])
                if entry.get("local") == args.set_note
            ]
            if len(matches) != 1:
                raise ManifestError(
                    f"expected one entry for {args.set_note!r}, found {len(matches)}"
                )
            matches[0]["notes"] = note
            save_manifest(manifest, root)
            print(f"updated note: {args.set_note}")
            return 0

        ok, message = check_manifest_lock(root)
        print(message)
        return 0 if ok else 1
    except (ManifestError, OSError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

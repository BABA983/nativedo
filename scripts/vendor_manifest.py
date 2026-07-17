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
    load_source_manifest,
    manifest_lock_path,
    regenerate_manifest_lock,
    repo_root,
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
    parser.add_argument(
        "--compat-shim",
        action="append",
        default=[],
        metavar="LOCAL_PATH",
        help="with --bootstrap, move this verified scratch shim out of files[]",
    )
    return parser.parse_args()


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

        ok, message = check_manifest_lock(root)
        print(message)
        return 0 if ok else 1
    except (ManifestError, OSError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

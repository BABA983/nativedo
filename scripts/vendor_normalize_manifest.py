#!/usr/bin/env python3
"""Normalize vendoring pins and persist compact source + generated lock.

Default target pin: 9b50143d8896d255d03155598937e4f3e28afd86 (Telegram 12.8.1).

Does NOT rewrite file contents or force every vendored_at forward — only:
  - upstream.pinned_commit / pinned_date
  - expand short SHAs (009e97356 -> full)
  - fill null vendored_at with the global pin
  - leave historical full pins in place (status will report behind-global)

Usage:
  scripts/vendor_normalize_manifest.py
  scripts/vendor_normalize_manifest.py --dry-run
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    CURRENT_PIN,
    CURRENT_PIN_DATE,
    load_manifest,
    normalize_manifest_pins,
    repo_root,
    save_manifest,
)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--pin", default=CURRENT_PIN)
    ap.add_argument("--date", default=CURRENT_PIN_DATE)
    args = ap.parse_args()

    root = repo_root()
    manifest = load_manifest(root)
    counts = normalize_manifest_pins(
        manifest,
        set_global_pin=args.pin,
        set_global_date=args.date,
        fill_null_with=args.pin,
    )

    # Also fix the stale app BrowserSheet path note? leave content alone.
    print("normalize counts:", counts)
    print("global pin ->", manifest.get("upstream"))

    # show remaining nulls
    nulls = [
        e.get("local")
        for key in ("files", "compat_shims")
        for e in manifest.get(key, [])
        if not e.get("vendored_at")
    ]
    print("remaining null vendored_at:", nulls)

    if args.dry_run:
        print("dry-run: not writing")
        return 0

    save_manifest(manifest, root)
    print("wrote", root / "vendor-manifest.json", "and generated lock")
    return 0


if __name__ == "__main__":
    sys.exit(main())

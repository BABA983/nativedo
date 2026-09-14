#!/usr/bin/env python3
"""vendor-index: show or verify the derived provenance index.

The index is never committed. It is computed from `vendor-overlay.json`, the
pristine Telegram checkout, and the files on disk, so it cannot go stale or be
hand-edited into agreement.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    ManifestError,
    display_path,
    load_index,
    local_abs,
    repo_root,
    third_party_bucket,
    vendor_dir,
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--json", action="store_true", help="dump the full index")
    p.add_argument(
        "--path",
        metavar="PATH",
        help="print one entry matched by local path, basename, or upstream path",
    )
    p.add_argument(
        "--check",
        action="store_true",
        help="exit non-zero when the index cannot be derived",
    )
    return p.parse_args()


def summary(index: dict) -> str:
    files = index.get("files") or []
    shims = index.get("compat_shims") or []
    pin = (index.get("upstream") or {}).get("pinned_commit") or "?"
    modes = Counter(e.get("port_mode") for e in files)
    modified = Counter(bool(e.get("modified")) for e in files)
    buckets = Counter(
        third_party_bucket(e["local"]) for e in files if third_party_bucket(e["local"])
    )
    lines = [
        f"pin: {str(pin)[:12]}  local_root: {index.get('local_root')}",
        f"files: {len(files)} (exact={modes['exact']} native={modes['native']})  "
        f"shims: {len(shims)}  resources: {len(index.get('resources') or [])}",
        f"modified vs pin: true={modified[True]} false={modified[False]}",
        f"third-party: {dict(buckets) or '{}'}",
    ]
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    root = repo_root()
    vd = vendor_dir(root)
    try:
        index = load_index(root)
    except ManifestError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        if not vd.exists():
            print(
                f"hint: pristine Telegram tree missing at {display_path(vd, root)}; "
                "run scripts/vendor-setup.sh",
                file=sys.stderr,
            )
        return 1

    if args.json:
        print(json.dumps(index, indent=2, ensure_ascii=False))
        return 0

    if args.path:
        needle = args.path.strip().lstrip("./")
        entries = list(index.get("files") or []) + list(index.get("compat_shims") or [])
        exact = [e for e in entries if needle in (e.get("local"), e.get("upstream"))]
        suffix = [
            e
            for e in entries
            if needle in (e.get("local", "")[-len(needle) :], e.get("upstream", "")[-len(needle) :])
        ]
        fuzzy = [e for e in entries if needle in e.get("local", "") or needle in e.get("upstream", "")]
        hits = exact or suffix or fuzzy
        if not hits:
            print(f"no entry matches {args.path!r}", file=sys.stderr)
            return 1
        for entry in hits:
            path = local_abs(entry, index, root)
            print(
                f"{entry['local']}\n"
                f"  upstream:    {entry.get('upstream')}\n"
                f"  origin:      {entry.get('origin')}  port_mode: {entry.get('port_mode')}\n"
                f"  vendored_at: {entry.get('vendored_at')}\n"
                f"  modified:    {entry.get('modified')}\n"
                f"  forced_exact:{entry.get('forced_exact') or '-'}\n"
                f"  on_disk:     {path.is_file()}"
            )
        return 0

    print(summary(index))
    if args.check:
        print("OK: index derived cleanly")
    return 0


if __name__ == "__main__":
    sys.exit(main())

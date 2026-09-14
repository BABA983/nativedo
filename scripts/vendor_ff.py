#!/usr/bin/env python3
"""vendor-ff: fast-forward exact, byte-clean files to a target upstream ref.

Candidates are exact copies that still match their baseline blob byte-for-byte
(a clean FF cannot lose local work). Files listed in the overlay's `reference`
block are skipped unless --include-reference is passed: they intentionally track
an older commit.

On --apply the overlay's pinned_commit moves to the target; files upstream did
not touch are already identical, so nothing else needs updating.

  scripts/vendor-ff.sh                     # dry-run -> global pin (no-op unless pin moved)
  scripts/vendor-ff.sh --to origin/master --apply
  scripts/vendor-ff.sh --only BottomSheet   # substring filter
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    ManifestError,
    compare_to_upstream,
    git_rev_parse,
    git_show,
    load_index,
    local_abs,
    normalize_sha,
    repo_root,
    update_overlay_upstream,
    upstream_git_path,
    vendor_dir,
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--to", metavar="REF", help="target ref (default: overlay pin)")
    p.add_argument("--only", action="append", default=[], help="substring filter")
    p.add_argument("--apply", action="store_true", help="write changes")
    p.add_argument(
        "--force",
        action="store_true",
        help="overwrite even when the local file no longer matches its baseline",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    root = repo_root()
    try:
        index = load_index(root, with_modified=False)
    except ManifestError as e:
        print(f"ERROR: {e}", file=sys.stderr)
        return 1
    vd = vendor_dir(root)

    pin = normalize_sha((index.get("upstream") or {}).get("pinned_commit"))
    target = normalize_sha(args.to) if args.to else pin
    if not target:
        print("ERROR: no target commit", file=sys.stderr)
        return 1
    resolved = git_rev_parse(target, vd)
    if not resolved:
        print(f"ERROR: cannot resolve target {target}", file=sys.stderr)
        return 1
    target = resolved

    reference_paths = set((index.get("reference") or {}).get("paths") or [])
    candidates = []
    skipped_reference = 0
    for entry in index.get("files") or []:
        if entry.get("port_mode") != "exact":
            continue
        local = entry.get("local") or ""
        if args.only and not any(s in local for s in args.only):
            continue
        if local in reference_paths:
            # Intentional historical baseline: advancing one is a human
            # decision (remove it from overlay reference.paths first).
            skipped_reference += 1
            continue
        candidates.append(entry)

    stats = {
        "eligible": len(candidates),
        "would_ff": 0,
        "already_at_target": 0,
        "skipped_dirty": 0,
        "skipped_no_upstream": 0,
        "applied": 0,
    }
    actions: list[dict] = []

    for entry in candidates:
        local = entry["local"]
        up_path = upstream_git_path(entry, index)
        path = local_abs(entry, index, root)
        if not up_path or not path.is_file():
            stats["skipped_no_upstream"] += 1
            continue
        new_blob = git_show(target, up_path, vd)
        if new_blob is None:
            stats["skipped_no_upstream"] += 1
            actions.append({"local": local, "action": "skip_missing_at_target"})
            continue
        if not args.force:
            cmp = compare_to_upstream(entry, index, root)
            if cmp.get("byte_equal") is not True:
                stats["skipped_dirty"] += 1
                actions.append(
                    {
                        "local": local,
                        "action": "skip_local_dirty",
                        "hint": "local differs from its baseline blob; fix or --force",
                    }
                )
                continue
        if path.read_bytes() == new_blob:
            stats["already_at_target"] += 1
            continue
        stats["would_ff"] += 1
        actions.append(
            {"local": local, "action": "fast_forward", "to": target, "bytes": len(new_blob)}
        )
        if args.apply:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(new_blob)
            stats["applied"] += 1

    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"=== vendor-ff [{mode}] pin={str(pin)[:12]} target={target[:12]} ===")
    print(
        f"eligible={stats['eligible']} would_ff={stats['would_ff']} "
        f"applied={stats['applied']} already_at_target={stats['already_at_target']} "
        f"dirty_skip={stats['skipped_dirty']} "
        f"no_upstream={stats['skipped_no_upstream']} "
        f"reference_skip={skipped_reference}"
    )
    for row in actions:
        extra = f"  -> {str(row['to'])[:12]}" if row.get("to") else ""
        if row.get("hint"):
            extra += f"  ({row['hint']})"
        print(f"  [{row['action']}] {row['local']}{extra}")

    if args.apply and stats["applied"]:
        if args.only:
            print(
                "NOTE: --only was used — the overlay pin was NOT moved. "
                "Bump it once every clean file is at the target."
            )
        else:
            update_overlay_upstream(root, target)
            print(f"moved {index.get('overlay')} pin to {target[:12]}")
        print("Next: scripts/vendor-guard.sh --compile && scripts/vendor-status.sh")
    elif not args.apply and stats["would_ff"]:
        print("Re-run with --apply to write.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

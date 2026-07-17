#!/usr/bin/env python3
"""Fast-forward exact unmodified vendored files to a target upstream commit.

Safety rails:
  - Only touches entries with port_mode=exact and modified=false.
  - Skips entries with stuck_reason unless --include-stuck.
  - Refuses to overwrite if local bytes no longer match vendored_at
    (unless --force).
  - Default is dry-run; pass --apply to write files + manifest.
  - Prefer compile-gated one-by-one apply when bulk FF is uncertain.

Usage:
  scripts/vendor-ff.sh                     # dry-run → global pin
  scripts/vendor-ff.sh --apply
  scripts/vendor-ff.sh --to <sha> --apply
  scripts/vendor-ff.sh --only path/substring --apply
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    compare_to_upstream,
    git_rev_parse,
    git_show,
    load_manifest,
    local_abs,
    normalize_sha,
    repo_root,
    save_manifest,
    upstream_git_path,
    vendor_dir,
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--to",
        metavar="SHA",
        help="target commit (default: upstream.pinned_commit)",
    )
    p.add_argument(
        "--apply",
        action="store_true",
        help="write files and update manifest (default: dry-run)",
    )
    p.add_argument(
        "--force",
        action="store_true",
        help="overwrite even if local no longer matches vendored_at",
    )
    p.add_argument(
        "--only",
        action="append",
        default=[],
        help="substring filter on local path (repeatable)",
    )
    p.add_argument(
        "--include-unchanged",
        action="store_true",
        help="also bump vendored_at when upstream blob is identical",
    )
    p.add_argument(
        "--include-stuck",
        action="store_true",
        help="also process entries that have stuck_reason",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    root = repo_root()
    manifest = load_manifest(root)
    vd = vendor_dir(root)
    if not vd.exists():
        print("ERROR: vendor/telegram-android missing; run scripts/vendor-setup.sh", file=sys.stderr)
        return 1

    pin = normalize_sha((manifest.get("upstream") or {}).get("pinned_commit"))
    target = normalize_sha(args.to) if args.to else pin
    if not target:
        print("ERROR: no target commit", file=sys.stderr)
        return 1
    resolved = git_rev_parse(target, vd)
    if not resolved:
        print(f"ERROR: cannot resolve target {target}", file=sys.stderr)
        return 1
    target = resolved

    candidates = []
    for entry in manifest.get("files") or []:
        if entry.get("port_mode") != "exact":
            continue
        if entry.get("modified") is not False:
            continue
        if entry.get("stuck_reason") and not args.include_stuck:
            continue
        local = entry.get("local") or ""
        if args.only and not any(s in local for s in args.only):
            continue
        candidates.append(entry)

    stats = {
        "eligible": len(candidates),
        "would_ff": 0,
        "already_at_target": 0,
        "identical_bump_only": 0,
        "skipped_dirty": 0,
        "skipped_missing_local": 0,
        "skipped_missing_upstream": 0,
        "skipped_stuck": 0,
        "applied": 0,
    }
    actions: list[dict] = []

    for entry in candidates:
        local = entry["local"]
        va = normalize_sha(entry.get("vendored_at"))
        up_path = upstream_git_path(entry, manifest)
        path = local_abs(entry, manifest, root)

        if not path.is_file():
            stats["skipped_missing_local"] += 1
            actions.append({"local": local, "action": "skip_missing_local"})
            continue
        if not up_path:
            stats["skipped_missing_upstream"] += 1
            actions.append({"local": local, "action": "skip_no_upstream_path"})
            continue

        if va == target:
            # still verify blob exists
            blob = git_show(target, up_path, vd)
            if blob is None:
                stats["skipped_missing_upstream"] += 1
                actions.append({"local": local, "action": "skip_missing_at_target"})
                continue
            stats["already_at_target"] += 1
            actions.append({"local": local, "action": "already_at_target"})
            continue

        # Safety: local must still match vendored_at
        if not args.force and va:
            cmp = compare_to_upstream(entry, manifest, root)
            if cmp.get("error") == "upstream_blob_missing":
                # can't verify; refuse unless force
                stats["skipped_dirty"] += 1
                actions.append(
                    {
                        "local": local,
                        "action": "skip_cannot_verify_vendored_at",
                        "vendored_at": va,
                    }
                )
                continue
            if cmp.get("byte_equal") is False:
                stats["skipped_dirty"] += 1
                actions.append(
                    {
                        "local": local,
                        "action": "skip_local_dirty",
                        "hint": "declared modified:false but local differs from vendored_at; fix flag or --force",
                    }
                )
                continue

        new_blob = git_show(target, up_path, vd)
        if new_blob is None:
            stats["skipped_missing_upstream"] += 1
            actions.append({"local": local, "action": "skip_missing_at_target", "path": up_path})
            continue

        old_blob = git_show(va, up_path, vd) if va else None
        content_changed = old_blob is None or old_blob != new_blob

        if not content_changed and not args.include_unchanged:
            stats["identical_bump_only"] += 1
            actions.append(
                {
                    "local": local,
                    "action": "skip_identical_content",
                    "from": va,
                    "to": target,
                    "hint": "upstream blob unchanged; pass --include-unchanged to only bump vendored_at",
                }
            )
            continue

        stats["would_ff"] += 1
        actions.append(
            {
                "local": local,
                "action": "fast_forward" if content_changed else "bump_vendored_at",
                "from": va,
                "to": target,
                "bytes": len(new_blob),
            }
        )

        if args.apply:
            path.parent.mkdir(parents=True, exist_ok=True)
            if content_changed:
                path.write_bytes(new_blob)
            entry["vendored_at"] = target
            entry["modified"] = False
            # clear stuck_reason if any
            entry.pop("stuck_reason", None)
            stats["applied"] += 1

    # print report
    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"=== vendor-ff [{mode}] target={target[:12]} ===")
    print(
        f"eligible={stats['eligible']} would_ff={stats['would_ff']} "
        f"already_at_target={stats['already_at_target']} "
        f"identical_skip={stats['identical_bump_only']} "
        f"dirty_skip={stats['skipped_dirty']} "
        f"missing_local={stats['skipped_missing_local']} "
        f"missing_upstream={stats['skipped_missing_upstream']}"
    )
    for row in actions:
        act = row["action"]
        if act in ("already_at_target", "skip_identical_content") and not args.only:
            continue  # quiet unless filtered
        extra = ""
        if row.get("from") or row.get("to"):
            extra = f"  {str(row.get('from') or '')[:12]} -> {str(row.get('to') or '')[:12]}"
        if row.get("hint"):
            extra += f"  ({row['hint']})"
        print(f"  [{act}] {row['local']}{extra}")

    if args.apply and stats["applied"]:
        save_manifest(manifest, root)
        print(f"wrote manifest source+lock; applied={stats['applied']}")
        print("Next: scripts/vendor-status.sh && compile :telegram")
    elif not args.apply and stats["would_ff"]:
        print("Re-run with --apply to write.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

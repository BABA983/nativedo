#!/usr/bin/env python3
"""Classify host-wiring drift on modified vendored files.

Compares each file to its vendored_at blob and buckets changed lines into:
  import | theme | resource_r | nativedo_guard | package | comment | other

Also reports modified-flag mismatches (declared clean but differs, etc.).

Usage:
  scripts/vendor-classify.sh
  scripts/vendor-classify.sh --port-mode exact
  scripts/vendor-classify.sh --json
  scripts/vendor-classify.sh --only RecyclerListView
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    compare_to_upstream,
    git_show,
    load_manifest,
    local_abs,
    normalize_sha,
    repo_root,
    upstream_git_path,
    vendor_dir,
)

THEME_RE = re.compile(
    r"Theme\.(getColor|key_|setColor|applyTheme|createSimpleSelector|getServiceMessageColor)"
    r"|key_[A-Za-z0-9_]+"
)
RESOURCE_RE = re.compile(r"\bR\.(drawable|string|id|layout|color|dimen|raw|attr|style)\.")
GUARD_RE = re.compile(r"UnsupportedOperationException\s*\(\s*\"nativedo|//\s*nativedo:|NativeDo")
IMPORT_RE = re.compile(r"^\s*import\s+")
PACKAGE_RE = re.compile(r"^\s*package\s+")
COMMENT_RE = re.compile(r"^\s*(//|/\*|\*)")


def classify_line(line: str) -> str:
    s = line.lstrip("+-").rstrip("\n")
    if IMPORT_RE.match(s):
        return "import"
    if PACKAGE_RE.match(s):
        return "package"
    if COMMENT_RE.match(s) or not s.strip():
        return "comment_or_blank"
    if GUARD_RE.search(s):
        return "nativedo_guard"
    if THEME_RE.search(s):
        return "theme"
    if RESOURCE_RE.search(s):
        return "resource_r"
    return "other"


def unified_diff_lines(a: str, b: str) -> list[str]:
    import difflib

    return list(
        difflib.unified_diff(
            a.splitlines(keepends=True),
            b.splitlines(keepends=True),
            fromfile="upstream",
            tofile="local",
            n=0,
        )
    )


def summarize_diff(up_text: str, local_text: str) -> dict:
    lines = unified_diff_lines(up_text, local_text)
    cats: Counter[str] = Counter()
    changed = 0
    for line in lines:
        if not line or line.startswith(("+++", "---", "@@")):
            continue
        if line[0] not in "+-":
            continue
        changed += 1
        cats[classify_line(line)] += 1
    primary = "identical"
    if changed:
        # dominant non-comment bucket
        scored = {k: v for k, v in cats.items() if k != "comment_or_blank"}
        primary = max(scored, key=scored.get) if scored else "comment_or_blank"
        # refine: import-only
        if scored and set(scored) <= {"import"}:
            primary = "import_only"
        elif scored and set(scored) <= {"import", "theme", "resource_r", "comment_or_blank"}:
            if "other" not in scored and "nativedo_guard" not in scored:
                primary = "host_wiring_only"
    return {
        "changed_lines": changed,
        "categories": dict(cats),
        "primary": primary,
    }


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--port-mode", choices=["exact", "native", "all"], default="all")
    p.add_argument("--only", action="append", default=[])
    p.add_argument("--json", action="store_true")
    p.add_argument("--limit", type=int, default=0, help="max files to classify (0=all)")
    p.add_argument(
        "--modified-only",
        action="store_true",
        default=True,
        help="only entries with modified:true (default)",
    )
    p.add_argument(
        "--all-entries",
        action="store_true",
        help="classify every entry that has an upstream blob",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    root = repo_root()
    manifest = load_manifest(root)
    vd = vendor_dir(root)
    if not vd.exists():
        print(f"ERROR: pristine Telegram tree missing at {vd}; run scripts/vendor-setup.sh", file=sys.stderr)
        return 1

    rows = []
    mismatches = []
    skipped = Counter()

    for entry in manifest.get("files") or []:
        if args.port_mode != "all" and entry.get("port_mode") != args.port_mode:
            continue
        local = entry.get("local") or ""
        if args.only and not any(s in local for s in args.only):
            continue
        if not args.all_entries and entry.get("modified") is not True:
            # still check clean flags for mismatch when modified false
            if entry.get("modified") is False:
                cmp = compare_to_upstream(entry, manifest, root)
                if cmp.get("modified_mismatch"):
                    mismatches.append(
                        {
                            "local": local,
                            "error": cmp.get("error"),
                            "port_mode": entry.get("port_mode"),
                        }
                    )
            continue

        path = local_abs(entry, manifest, root)
        if not path.is_file():
            skipped["missing_local"] += 1
            continue
        up_path = upstream_git_path(entry, manifest)
        va = normalize_sha(entry.get("vendored_at"))
        if not up_path or not va:
            skipped["no_ref"] += 1
            continue
        blob = git_show(va, up_path, vd)
        if blob is None:
            skipped["missing_upstream"] += 1
            continue
        try:
            up_text = blob.decode("utf-8")
            local_text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            skipped["binary"] += 1
            continue

        summary = summarize_diff(up_text, local_text)
        row = {
            "local": local,
            "port_mode": entry.get("port_mode"),
            "modified": entry.get("modified"),
            "vendored_at": va,
            "forced_exact": entry.get("forced_exact") or [],
            **summary,
        }
        rows.append(row)
        if args.limit and len(rows) >= args.limit:
            break

    # primary distribution
    primary_c = Counter(r["primary"] for r in rows)
    port_c = Counter(r["port_mode"] for r in rows)

    report = {
        "classified": len(rows),
        "primary_distribution": dict(primary_c),
        "port_mode_distribution": dict(port_c),
        "skipped": dict(skipped),
        "modified_flag_mismatches": mismatches,
        "files": rows,
    }

    if args.json:
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 0

    print("=== vendor-classify ===")
    print(f"classified={len(rows)}  primary={dict(primary_c)}  port={dict(port_c)}")
    if skipped:
        print(f"skipped={dict(skipped)}")
    if mismatches:
        print(f"modified-flag mismatches ({len(mismatches)}):")
        for m in mismatches[:30]:
            print(f"  [{m.get('error')}] {m['local']}")
        if len(mismatches) > 30:
            print(f"  ... +{len(mismatches) - 30} more")

    # group listing by primary
    by_primary: dict[str, list] = {}
    for r in rows:
        by_primary.setdefault(r["primary"], []).append(r)

    for primary in sorted(by_primary, key=lambda k: (-len(by_primary[k]), k)):
        group = by_primary[primary]
        print(f"-- {primary} ({len(group)}) --")
        for r in group[:15]:
            fe = ",".join(r["forced_exact"]) if r["forced_exact"] else "-"
            print(
                f"  [{r['port_mode']}] lines={r['changed_lines']:<5} fe={fe:<28} "
                f"{r['local'].split('/')[-1]}"
            )
            cats = r["categories"]
            if cats:
                print(f"           cats={cats}")
        if len(group) > 15:
            print(f"  ... +{len(group) - 15} more")

    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""vendor-status: provenance, isolation, and upstream-drift report.

Everything is derived (overlay + pristine tree + disk): there are no flags to
maintain, so this tool only reports state.
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

# Allow `python3 scripts/vendor_status.py` without install.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    ManifestError,
    display_path,
    git_log_paths,
    git_rev_parse,
    load_index,
    normalize_sha,
    redundant_native,
    repo_root,
    resource_report,
    run_isolation_check,
    stub_candidates,
    third_party_bucket,
    third_party_prefixes,
    upstream_git_path,
    vendor_dir,
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--strict", action="store_true", help="warnings also fail the run")
    p.add_argument("--json", action="store_true", help="machine-readable summary")
    p.add_argument(
        "--bump-report",
        nargs="?",
        const="origin/master",
        metavar="NEW_SHA",
        help="list tracked files upstream touched between the pin and NEW_SHA",
    )
    p.add_argument(
        "--sample",
        type=int,
        default=20,
        help="max paths to print per issue bucket (default 20)",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    root = repo_root()
    report: dict = {"errors": [], "warnings": [], "info": [], "stats": {}}
    vd = vendor_dir(root)

    try:
        index = load_index(root)
    except ManifestError as e:
        report["errors"].append(str(e))
        if not vd.exists():
            report["errors"].append(
                f"pristine Telegram checkout missing at {display_path(vd, root)} "
                "(run scripts/vendor-setup.sh)"
            )
        index = None

    if index is not None:
        files = index.get("files") or []
        shims = index.get("compat_shims") or []
        resources = index.get("resources") or []
        pin = normalize_sha((index.get("upstream") or {}).get("pinned_commit"))
        report["stats"]["pinned_commit"] = pin
        report["stats"]["pinned_date"] = (index.get("upstream") or {}).get("pinned_date")
        report["stats"]["files"] = len(files)
        report["stats"]["compat_shims"] = len(shims)
        report["stats"]["resources"] = len(resources)
        report["stats"]["port_mode"] = dict(Counter(e.get("port_mode") for e in files))
        modified = Counter(bool(e.get("modified")) for e in files)
        report["stats"]["modified"] = {
            "true": modified[True],
            "false": modified[False],
        }
        buckets = Counter(
            b
            for b in (
                third_party_bucket(e["local"], third_party_prefixes(index))
                for e in files
            )
            if b
        )
        report["stats"]["third_party"] = dict(buckets)
        report["stats"]["forced_exact"] = sum(1 for e in files if e.get("forced_exact"))
        reference = index.get("reference") or {}
        report["stats"]["reference_paths"] = len(reference.get("paths") or [])
        report["info"].append(
            f"{len(files)} tracked file(s), {len(shims)} scratch shim(s), "
            f"{len(resources)} resource group(s)"
        )
        if reference.get("paths"):
            report["info"].append(
                f"{len(reference['paths'])} file(s) reference "
                f"{str(reference.get('commit'))[:12]} (historical baseline)"
            )
        if buckets:
            report["info"].append(f"third-party trees carried: {dict(buckets)}")

        # Ledger heuristics: a stub at an upstream path, a clean copy labelled
        # native. Both make the bump report lie, so they surface as warnings.
        stubs = stub_candidates(index)
        if stubs:
            report["warnings"].append(
                f"{len(stubs)} exact file(s) are far smaller than their upstream "
                "original (adopt upstream or list them in `native`): "
                + ", ".join(e["local"].split("/")[-1] for e in stubs[:5])
            )
        redundant = redundant_native(index)
        if redundant:
            report["warnings"].append(
                f"{len(redundant)} native-declared file(s) are byte-identical to "
                "upstream (drop them from `native`): "
                + ", ".join(e["local"] for e in redundant[:5])
            )

        resource_files = index.get("resource_files") or []
        if resource_files:
            res = resource_report(index)
            report["stats"]["resource_files"] = len(resource_files)
            report["stats"]["resource_buckets"] = {
                key: len(value) for key, value in res.items()
            }
            compared = sum(
                1
                for e in resource_files
                if not e.get("ours") and e.get("modified") is not None
            )
            report["info"].append(
                f"{len(resource_files)} declared resource file(s), "
                f"{compared} byte-compared: {len(res['identical'])} identical, "
                f"{len(res['acknowledged'])} acknowledged, "
                f"{len(res['ours'])} ours"
            )
            if compared < len(resource_files) - len(res["ours"]):
                report["info"].append(
                    f"{len(res['uncompared'])} resource file(s) not byte-compared — "
                    "run scripts/vendor-setup.sh --prefetch to materialize the blobs"
                )
            if res["differs"]:
                report["errors"].append(
                    f"{len(res['differs'])} declared resource file(s) changed vs "
                    "their declared baseline (add to the group's `differs` array "
                    "if intentional): "
                    + ", ".join(e["local"] for e in res["differs"][:5])
                )
            if res["unresolved"]:
                report["warnings"].append(
                    f"{len(res['unresolved'])} declared resource file(s) have no "
                    "upstream counterpart (annotate `paths` or `ours`): "
                    + ", ".join(e["local"] for e in res["unresolved"][:5])
                )
            if res["stale_acknowledged"]:
                report["warnings"].append(
                    f"{len(res['stale_acknowledged'])} acknowledged resource "
                    "file(s) are identical again (drop the `differs` entry): "
                    + ", ".join(e["local"] for e in res["stale_acknowledged"][:5])
                )

        vendor_head = git_rev_parse("HEAD", vd)
        report["stats"]["vendor_HEAD"] = vendor_head
        if pin and vendor_head and vendor_head != pin:
            report["warnings"].append(
                f"pristine checkout HEAD={vendor_head[:12]} != pin={pin[:12]} "
                "(fine for reading upstream; run scripts/vendor-setup.sh to reset)"
            )

    code, out = run_isolation_check(root)
    report["stats"]["isolation_ok"] = code == 0
    if code != 0:
        report["errors"].append("vendor-isolation-check failed")
        report["stats"]["isolation_output"] = out
    else:
        report["info"].append(out or "vendor isolation clean")

    if args.bump_report is not None and index is not None:
        ref = args.bump_report
        pin = report["stats"].get("pinned_commit")
        new_sha = git_rev_parse(ref, vd)
        if not new_sha:
            report["errors"].append(f"cannot resolve {ref}")
        elif not pin:
            report["errors"].append("cannot --bump-report without a pinned commit")
        else:
            files = index.get("files") or []
            paths = [p for p in (upstream_git_path(e, index) for e in files) if p]
            touched = set(git_log_paths(pin, new_sha, paths, vd))
            rows = [
                e
                for e in files
                if upstream_git_path(e, index) in touched
            ]
            tracks = Counter(
                ("exact/clean" if e.get("modified") is False else
                 "exact/modified" if e.get("port_mode") == "exact" else
                 "native")
                for e in rows
            )
            report["stats"]["bump_report"] = {
                "ref": new_sha,
                "touched": len(rows),
                "tracks": dict(tracks),
            }
            report["info"].append(
                f"bump report {pin[:12]}..{new_sha[:12]}: {len(rows)}/{len(files)} "
                f"tracked file(s) touched by upstream {dict(tracks)}"
            )
            for e in rows:
                report["stats"].setdefault("bump_paths", []).append(e["local"])
            if tracks.get("exact/modified"):
                report["warnings"].append(
                    f"{tracks['exact/modified']} exact file(s) need a manual rebase "
                    "when the pin moves (scripts/vendor-classify.sh lists the drift)"
                )

    if args.json:
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 1 if report["errors"] or (args.strict and report["warnings"]) else 0

    print("=== vendor-status ===")
    stats = report["stats"]
    if stats.get("pinned_commit"):
        print(
            f"pin: {stats['pinned_commit'][:12]}  date: {stats.get('pinned_date')}  "
            f"vendor_HEAD: {str(stats.get('vendor_HEAD'))[:12]}"
        )
        print(
            f"files: {stats.get('files')}  shims: {stats.get('compat_shims')}  "
            f"resources: {stats.get('resources')}  "
            f"port_mode: {stats.get('port_mode')}"
        )
        print(
            f"modified vs baseline: {stats.get('modified')}  "
            f"forced_exact: {stats.get('forced_exact')}  "
            f"reference: {stats.get('reference_paths')}"
        )
    for line in report["info"]:
        print(f"INFO  {line}")
    for line in report["warnings"]:
        print(f"WARN  {line}")
    for line in report["errors"]:
        print(f"ERROR {line}")
    failed = bool(report["errors"]) or (args.strict and report["warnings"])
    print(f"=== result: errors={len(report['errors'])} warnings={len(report['warnings'])} ===")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""vendor-status: manifest health, isolation, optional modified checks, bump report."""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

# Allow `python3 scripts/vendor_status.py` without install.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    CURRENT_PIN,
    compare_to_upstream,
    collect_on_disk_telegram_java,
    git_log_paths,
    git_rev_parse,
    load_manifest,
    local_abs,
    normalize_sha,
    repo_root,
    run_isolation_check,
    upstream_git_path,
    vendor_dir,
)



def load_allowlist(root: Path) -> list[dict]:
    path = root / "vendor-allowlist.json"
    if not path.is_file():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return []
    return list(data.get("prefixes") or [])


def is_allowlisted(rel: str, prefixes: list[dict]) -> bool:
    return any(rel.startswith(p.get("prefix") or "") for p in prefixes)


def bucket_path(rel: str) -> str:
    if "/androidx/" in rel:
        return "androidx_fork"
    if "/com/google/" in rel or "/com/android/" in rel:
        return "third_party_com"
    if "/me/vkryl/" in rel:
        return "me_vkryl"
    if "/org/telegram/tgnet/" in rel:
        return "tgnet"
    if "/org/telegram/messenger/" in rel:
        return "messenger"
    if "/org/telegram/ui/" in rel:
        return "ui"
    if "/org/telegram/" in rel:
        return "telegram_other"
    return "other"


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--strict", action="store_true", help="warnings also fail the run")
    p.add_argument(
        "--check-modified",
        action="store_true",
        help="byte-compare each file to vendored_at (requires vendor/telegram-android)",
    )
    p.add_argument(
        "--bump-report",
        nargs="?",
        const="HEAD",
        metavar="NEW_SHA",
        help="list manifest files touched between global pin and NEW_SHA (default HEAD)",
    )
    p.add_argument("--json", action="store_true", help="machine-readable summary")
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
    manifest = load_manifest(root)
    report: dict = {
        "errors": [],
        "warnings": [],
        "info": [],
        "stats": {},
    }

    up = manifest.get("upstream") or {}
    pin_raw = up.get("pinned_commit")
    pin = normalize_sha(pin_raw)
    report["stats"]["pinned_commit"] = pin
    report["stats"]["pinned_commit_raw"] = pin_raw
    report["stats"]["pinned_date"] = up.get("pinned_date")

    if not pin:
        report["errors"].append("upstream.pinned_commit is missing")
    elif pin != CURRENT_PIN and pin_raw != CURRENT_PIN:
        # Informational: tooling's expected current pin vs manifest.
        report["info"].append(
            f"manifest pin is {pin}; tooling CURRENT_PIN is {CURRENT_PIN}"
        )

    vd = vendor_dir(root)
    if not vd.exists():
        report["warnings"].append(
            f"pristine tree missing: {vd.relative_to(root)} "
            "(run scripts/vendor-setup.sh)"
        )
        vendor_head = None
    else:
        vendor_head = git_rev_parse("HEAD", vd)
        report["stats"]["vendor_HEAD"] = vendor_head
        if pin and vendor_head and not vendor_head.startswith(pin[:9]) and vendor_head != pin:
            # Allow prefix match for short pins.
            if normalize_sha(vendor_head) != pin and not vendor_head.startswith(str(pin)[:12]):
                report["warnings"].append(
                    f"vendor/telegram-android HEAD={vendor_head[:12]} "
                    f"!= pinned_commit={str(pin)[:12]} "
                    "(checkout pin with scripts/vendor-setup.sh)"
                )

    files = manifest.get("files") or []
    resources = manifest.get("resources") or []
    report["stats"]["file_entries"] = len(files)
    report["stats"]["resource_groups"] = len(resources)

    mode_c = Counter(e.get("port_mode") or "?" for e in files)
    mod_c = Counter(bool(e.get("modified")) for e in files)
    report["stats"]["port_mode"] = dict(mode_c)
    report["stats"]["modified"] = {"true": mod_c[True], "false": mod_c[False]}

    # --- per-file structural checks ---
    missing_disk: list[str] = []
    null_vendored: list[str] = []
    bad_port: list[str] = []
    pin_dist: Counter[str] = Counter()
    behind_global: list[str] = []
    no_stuck: list[str] = []
    local_set: set[str] = set()
    dup_local: list[str] = []

    for entry in files:
        local = entry.get("local") or ""
        if not local:
            report["errors"].append("file entry missing 'local'")
            continue
        if local in local_set:
            dup_local.append(local)
        local_set.add(local)

        if not local_abs(entry, manifest, root).is_file():
            missing_disk.append(local)

        va_raw = entry.get("vendored_at")
        va = normalize_sha(va_raw)
        if va is None:
            null_vendored.append(local)
            pin_dist["<null>"] += 1
        else:
            pin_dist[va[:12]] += 1
            if pin and va != pin and not str(pin).startswith(va) and not va.startswith(str(pin)[:12]):
                behind_global.append(local)
                if not entry.get("stuck_reason"):
                    no_stuck.append(local)

        pm = entry.get("port_mode")
        if pm not in ("exact", "native"):
            bad_port.append(local)

        if entry.get("modified") is None:
            report["warnings"].append(f"missing modified flag: {local}")

    report["stats"]["vendored_at_distribution"] = dict(pin_dist)

    if dup_local:
        report["errors"].append(f"duplicate local entries ({len(dup_local)})")
        report["stats"]["duplicate_locals"] = dup_local[: args.sample]
    if missing_disk:
        report["errors"].append(f"manifest paths missing on disk ({len(missing_disk)})")
        report["stats"]["missing_on_disk"] = missing_disk[: args.sample]
        report["stats"]["missing_on_disk_count"] = len(missing_disk)
    if null_vendored:
        report["errors"].append(f"entries with null/missing vendored_at ({len(null_vendored)})")
        report["stats"]["null_vendored_at"] = null_vendored
    if bad_port:
        report["errors"].append(f"invalid port_mode ({len(bad_port)})")
        report["stats"]["bad_port_mode"] = bad_port[: args.sample]
    if behind_global:
        report["info"].append(
            f"{len(behind_global)} file(s) vendored_at behind global pin "
            f"({len(no_stuck)} without stuck_reason)"
        )
        report["stats"]["behind_global_count"] = len(behind_global)
        report["stats"]["behind_global_sample"] = behind_global[: args.sample]
        report["stats"]["behind_without_stuck_sample"] = no_stuck[: args.sample]
        # Policy: warn, not error — historical pins are expected until re-sync.
        if no_stuck:
            report["warnings"].append(
                f"{len(no_stuck)} file(s) behind global pin without stuck_reason"
            )

    # --- disk files not in manifest (:telegram only; report, not always error) ---
    on_disk = collect_on_disk_telegram_java(root)
    allow_prefixes = load_allowlist(root)
    allowlisted = []
    unmanifested = []
    for pth in on_disk:
        if pth in local_set:
            continue
        if is_allowlisted(pth, allow_prefixes):
            allowlisted.append(pth)
        else:
            unmanifested.append(pth)
    bucket_counts = Counter(bucket_path(p) for p in unmanifested)
    allow_bucket_counts = Counter(bucket_path(p) for p in allowlisted)
    report["stats"]["telegram_java_on_disk"] = len(on_disk)
    report["stats"]["telegram_unmanifested"] = len(unmanifested)
    report["stats"]["telegram_allowlisted"] = len(allowlisted)
    report["stats"]["unmanifested_buckets"] = dict(bucket_counts)
    report["stats"]["allowlisted_buckets"] = dict(allow_bucket_counts)
    if unmanifested:
        report["warnings"].append(
            f"{len(unmanifested)} :telegram .java file(s) on disk not in manifest "
            f"(buckets={dict(bucket_counts)}; register or extend vendor-allowlist.json)"
        )
        report["stats"]["unmanifested_sample"] = unmanifested[: args.sample]
    if allowlisted:
        report["info"].append(
            f"{len(allowlisted)} on-disk path(s) covered by vendor-allowlist.json "
            f"(buckets={dict(allow_bucket_counts)})"
        )

    # resources: only check vendored_at present
    res_null = []
    for i, res in enumerate(resources):
        if not res.get("vendored_at"):
            res_null.append(i)
    if res_null:
        report["errors"].append(f"resource groups missing vendored_at: {res_null}")

    # isolation
    iso_code, iso_out = run_isolation_check(root)
    report["stats"]["isolation_ok"] = iso_code == 0
    if iso_code != 0:
        report["errors"].append("vendor-isolation-check failed")
        report["stats"]["isolation_output"] = iso_out
    else:
        report["info"].append(iso_out or "vendor isolation clean")

    # optional modified byte-check
    if args.check_modified:
        if not vd.exists():
            report["errors"].append("cannot --check-modified without vendor/telegram-android")
        else:
            mismatches = []
            unavailable = []
            checked = 0
            for entry in files:
                if not local_abs(entry, manifest, root).is_file():
                    continue
                cmp = compare_to_upstream(entry, manifest, root)
                checked += 1
                if cmp.get("error") == "upstream_blob_missing" or not cmp.get("upstream_available"):
                    if cmp.get("error") not in (None, "missing_on_disk"):
                        unavailable.append(entry.get("local"))
                    continue
                if cmp.get("modified_mismatch"):
                    mismatches.append(
                        {
                            "local": entry.get("local"),
                            "error": cmp.get("error"),
                            "declared_modified": cmp.get("declared_modified"),
                            "byte_equal": cmp.get("byte_equal"),
                        }
                    )
            report["stats"]["modified_checked"] = checked
            report["stats"]["modified_mismatches"] = len(mismatches)
            report["stats"]["upstream_unavailable"] = len(unavailable)
            if mismatches:
                report["errors"].append(
                    f"modified flag mismatches vs bytes ({len(mismatches)})"
                )
                report["stats"]["modified_mismatch_sample"] = mismatches[: args.sample]
            if unavailable:
                report["warnings"].append(
                    f"could not load upstream blob for {len(unavailable)} file(s)"
                )
                report["stats"]["upstream_unavailable_sample"] = unavailable[: args.sample]

    # bump report
    if args.bump_report is not None:
        new_ref = args.bump_report
        new_sha = git_rev_parse(new_ref, vd) if vd.exists() else None
        if not vd.exists():
            report["errors"].append("cannot --bump-report without vendor/telegram-android")
        elif not pin:
            report["errors"].append("cannot --bump-report without pinned_commit")
        elif not new_sha:
            report["errors"].append(f"cannot resolve NEW_SHA={new_ref}")
        else:
            up_paths = []
            path_to_local = {}
            for entry in files:
                gp = upstream_git_path(entry, manifest)
                if gp:
                    up_paths.append(gp)
                    path_to_local[gp] = entry.get("local")
            # git log pathspec can be huge; chunk it.
            touched: list[str] = []
            chunk = 80
            for i in range(0, len(up_paths), chunk):
                part = git_log_paths(pin, new_sha, up_paths[i : i + chunk], vd)
                touched.extend(part)
            # de-dupe preserve order
            seen = set()
            touched_u = []
            for t in touched:
                if t not in seen:
                    seen.add(t)
                    touched_u.append(t)
            rows = []
            for gp in touched_u:
                local = path_to_local.get(gp)
                entry = next((e for e in files if e.get("local") == local), {})
                rows.append(
                    {
                        "local": local,
                        "upstream": gp,
                        "port_mode": entry.get("port_mode"),
                        "modified": entry.get("modified"),
                        "vendored_at": entry.get("vendored_at"),
                        "stuck_reason": entry.get("stuck_reason"),
                    }
                )
            report["stats"]["bump"] = {
                "old": pin,
                "new": new_sha,
                "touched_count": len(rows),
                "touched": rows,
            }
            report["info"].append(
                f"bump report {pin[:12]}..{new_sha[:12]}: "
                f"{len(rows)}/{len(files)} manifest file(s) touched upstream"
            )

    # output
    if args.json:
        print(json.dumps(report, indent=2, ensure_ascii=False))
    else:
        _print_human(report, args.sample)

    n_err = len(report["errors"])
    n_warn = len(report["warnings"])
    if n_err:
        return 1
    if args.strict and n_warn:
        return 1
    return 0


def _print_human(report: dict, sample: int) -> None:
    stats = report.get("stats") or {}
    print("=== vendor-status ===")
    print(
        f"pin: {stats.get('pinned_commit')}  date: {stats.get('pinned_date')}  "
        f"vendor_HEAD: {stats.get('vendor_HEAD')}"
    )
    print(
        f"files: {stats.get('file_entries')}  "
        f"resources: {stats.get('resource_groups')}  "
        f"port_mode: {stats.get('port_mode')}  "
        f"modified: {stats.get('modified')}"
    )
    print(f"vendored_at dist: {stats.get('vendored_at_distribution')}")
    print(
        f":telegram java on disk: {stats.get('telegram_java_on_disk')}  "
        f"unmanifested: {stats.get('telegram_unmanifested')}  "
        f"allowlisted: {stats.get('telegram_allowlisted')}"
    )
    if stats.get("unmanifested_buckets"):
        print(f"unmanifested buckets: {stats.get('unmanifested_buckets')}")
    if stats.get("allowlisted_buckets"):
        print(f"allowlisted buckets: {stats.get('allowlisted_buckets')}")
    if "behind_global_count" in stats:
        print(
            f"behind global pin: {stats['behind_global_count']}  "
            f"(sample without stuck_reason: {stats.get('behind_without_stuck_sample')})"
        )
    for msg in report.get("info") or []:
        print(f"INFO  {msg}")
    for msg in report.get("warnings") or []:
        print(f"WARN  {msg}")
    for msg in report.get("errors") or []:
        print(f"ERROR {msg}")

    # samples
    for key, label in [
        ("missing_on_disk", "missing on disk"),
        ("null_vendored_at", "null vendored_at"),
        ("unmanifested_sample", "unmanifested sample"),
        ("modified_mismatch_sample", "modified mismatches"),
    ]:
        val = stats.get(key)
        if not val:
            continue
        print(f"-- {label} --")
        if isinstance(val, list):
            for item in val[:sample]:
                print(f"  {item}")
        else:
            print(f"  {val}")

    bump = stats.get("bump")
    if bump:
        print(
            f"-- bump {bump['old'][:12]}..{bump['new'][:12]} "
            f"({bump['touched_count']} files) --"
        )
        for row in bump["touched"][:sample]:
            print(
                f"  [{row.get('port_mode')}/{'mod' if row.get('modified') else 'clean'}] "
                f"{row.get('local')}"
            )
        if bump["touched_count"] > sample:
            print(f"  ... +{bump['touched_count'] - sample} more")

    n_err = len(report.get("errors") or [])
    n_warn = len(report.get("warnings") or [])
    print(f"=== result: errors={n_err} warnings={n_warn} ===")


if __name__ == "__main__":
    sys.exit(main())

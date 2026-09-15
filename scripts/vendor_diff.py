#!/usr/bin/env python3
"""vendor-diff: compare a local vendored file to its upstream blob."""
from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    git_show,
    load_index,
    local_abs,
    normalize_sha,
    repo_root,
    upstream_git_path,
    vendor_dir,
)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "path",
        nargs="?",
        help="local path, local_root-relative path, or upstream-style path",
    )
    p.add_argument(
        "--at",
        metavar="COMMIT",
        help="diff against this commit instead of entry vendored_at",
    )
    p.add_argument(
        "--pinned",
        action="store_true",
        help="diff against global upstream.pinned_commit",
    )
    p.add_argument(
        "--stat",
        action="store_true",
        help="list tracked files that differ from their upstream baseline",
    )
    p.add_argument(
        "--name-only",
        action="store_true",
        help="with --stat, only print paths",
    )
    return p.parse_args()


def resolve_entry(manifest: dict, path_arg: str, root: Path) -> dict:  # noqa: D401
    files = list(manifest.get("files") or []) + list(
        manifest.get("compat_shims") or []
    )
    exact_candidates = []
    fuzzy_candidates = []
    arg = path_arg.strip().lstrip("./")
    # strip absolute root
    try:
        arg_path = Path(arg)
        if arg_path.is_absolute():
            arg = str(arg_path.resolve().relative_to(root)).replace("\\", "/")
    except Exception:
        pass

    local_root = (manifest.get("local_root") or "apps/android/").rstrip("/") + "/"
    if arg.startswith(local_root):
        arg = arg[len(local_root) :]
    if arg.startswith("apps/android/"):
        arg = arg[len("apps/android/") :]

    for entry in files:
        local = entry.get("local") or ""
        upstream = entry.get("upstream") or ""
        if local == arg or upstream == arg:
            exact_candidates.append(entry)
        elif local.endswith("/" + arg) or local.endswith(arg):
            fuzzy_candidates.append(entry)
        elif upstream.endswith(arg):
            fuzzy_candidates.append(entry)
        elif arg.endswith(local) or arg.endswith(upstream):
            fuzzy_candidates.append(entry)

    candidates = exact_candidates or fuzzy_candidates

    # unique by local
    uniq = {}
    for e in candidates:
        uniq[e.get("local")] = e
    candidates = list(uniq.values())

    if len(candidates) == 1:
        return candidates[0]
    if not candidates:
        # synthesize for unmanifested :TMessagesProj path
        if arg.startswith("TMessagesProj/src/main/java/"):
            rel = arg[len("TMessagesProj/src/main/java/") :]
            return {
                "local": arg,
                "upstream": rel,
                "vendored_at": normalize_sha(
                    (manifest.get("upstream") or {}).get("pinned_commit")
                ),
                "modified": None,
                "port_mode": "exact",
                "_synthetic": True,
            }
        raise SystemExit(f"ERROR: no manifest entry matches {path_arg!r}")
    print("ERROR: ambiguous path, matches:", file=sys.stderr)
    for e in candidates:
        print(f"  {e.get('local')}", file=sys.stderr)
    raise SystemExit(2)


def main() -> int:
    args = parse_args()
    root = repo_root()
    manifest = load_index(root)
    vd = vendor_dir(root)

    if args.stat:
        n_drift = 0
        n_clean = 0
        n_skip = 0
        entries = list(manifest.get("files") or []) + list(
            manifest.get("compat_shims") or []
        )
        for entry in entries:
            if entry.get("modified") is None:
                n_skip += 1
                continue
            if entry["modified"]:
                n_drift += 1
                if args.name_only:
                    print(entry.get("local"))
                else:
                    print(f"DIFFERS  {entry.get('local')}")
            else:
                n_clean += 1
        if not args.name_only:
            print(f"clean={n_clean} differs={n_drift} skipped={n_skip}")
        return 0

    if not args.path:
        print("ERROR: path required (or use --stat)", file=sys.stderr)
        return 2

    if not vd.exists():
        print(f"ERROR: pristine Telegram tree missing at {vd}; run scripts/vendor-setup.sh", file=sys.stderr)
        return 1

    entry = resolve_entry(manifest, args.path, root)
    local_path = local_abs(entry, manifest, root)
    if not local_path.is_file():
        print(f"ERROR: local file missing: {local_path}", file=sys.stderr)
        return 1

    if args.pinned:
        commit = normalize_sha((manifest.get("upstream") or {}).get("pinned_commit"))
    elif args.at:
        commit = normalize_sha(args.at) or args.at
    else:
        commit = normalize_sha(entry.get("vendored_at")) or normalize_sha(
            (manifest.get("upstream") or {}).get("pinned_commit")
        )

    if not commit:
        print("ERROR: no commit to diff against", file=sys.stderr)
        return 1

    up_path = upstream_git_path(entry, manifest)
    if not up_path:
        print("ERROR: cannot resolve upstream path", file=sys.stderr)
        return 1

    blob = git_show(commit, up_path, vd)
    if blob is None:
        print(f"ERROR: git show {commit}:{up_path} failed", file=sys.stderr)
        return 1

    meta = (
        f"# local:    {entry.get('local')}\n"
        f"# upstream: {up_path}\n"
        f"# commit:   {commit}\n"
        f"# kind:     {'compat_shim' if entry in (manifest.get('compat_shims') or []) else 'vendored'}\n"
        f"# port_mode:{entry.get('port_mode')}  modified:{entry.get('modified')}  "
        f"synthetic:{bool(entry.get('_synthetic'))}\n"
    )
    print(meta, end="")

    with tempfile.NamedTemporaryFile(prefix="vendor-up-", suffix=".java", delete=False) as tmp:
        tmp.write(blob)
        tmp_path = tmp.name

    try:
        proc = subprocess.run(
            ["diff", "-u", tmp_path, str(local_path)],
            capture_output=True,
            text=True,
        )
        # Rewrite headers to readable paths
        out = proc.stdout
        out = out.replace(tmp_path, f"a/{up_path}")
        # second file path
        out = out.replace(str(local_path), f"b/{entry.get('local')}")
        sys.stdout.write(out)
        if proc.returncode == 0:
            print("(no differences)")
            return 0
        if proc.returncode == 1:
            return 1  # diffs found — conventional for diff
        print(proc.stderr, file=sys.stderr)
        return proc.returncode
    finally:
        Path(tmp_path).unlink(missing_ok=True)


if __name__ == "__main__":
    sys.exit(main())

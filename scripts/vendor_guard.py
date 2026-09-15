#!/usr/bin/env python3
"""vendor-guard: the single pre-flight check for Telegram-derived code.

Reports drift with the exact command to run next, so an agent (or a human) never
has to read the workflow doc to fix it.

Modes:
  --quick     overlay/index + isolation + forced-exact edits (< 1s; hook)
  default     quick + upstream drift report
  --compile   default + `:TMessagesProj:compileDebugJavaWithJavac`
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from vendorlib import (  # noqa: E402
    ManifestError,
    changed_with_head_source,
    comment_only_edit,
    display_path,
    git_log_paths,
    git_rev_parse,
    load_index,
    normalize_sha,
    repo_root,
    run_isolation_check,
    upstream_git_path,
    vendor_dir,
)

FORCED_EXACT_TESTS = [
    "org.telegram.ui.Components.LayoutHelperForcedExactTest",
    "org.telegram.ui.Components.CircularProgressDrawableForcedExactTest",
    "org.telegram.ui.Components.ForcedExactControlsScreenshotTest",
]

LEGACY_ARTIFACTS = ("vendor-manifest.json", "vendor-manifest.lock.json")

# Terms that only exist in the retired manifest model. Docs may keep them only
# when they declare themselves historical (see HISTORICAL_DOC_MARKER); VENDOR.md
# documents the retirement itself and the notes archive quotes the old file
# names, so those two are always allowed.
RETIRED_TERMS = (
    "vendor-manifest",
    "vendor-allowlist",
    "stuck_reason",
    "compat_shim_groups",
)
RETIRED_TERM_ALLOWLIST = ("VENDOR.md", "docs/vendor-notes-archive.md")
HISTORICAL_DOC_MARKER = "Historical plan (retired manifest model)"
LIVE_DOCS = ("*.md", "docs/**/*.md")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--quick", action="store_true", help="overlay + isolation only")
    p.add_argument("--compile", action="store_true", help="also compile :TMessagesProj")
    return p.parse_args()


class Guard:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.infos: list[str] = []

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def info(self, msg: str) -> None:
        self.infos.append(msg)


def check_legacy_artifacts(root: Path, guard: Guard) -> None:
    for name in LEGACY_ARTIFACTS:
        if (root / name).exists():
            guard.error(
                f"{name} is back — provenance is derived now. "
                "Run: git rm vendor-manifest*.json"
            )


def check_retired_terms(root: Path, guard: Guard) -> None:
    """Live docs must not describe the retired manifest model.

    Docs rot after a model change, and prose cannot be re-derived like the
    index is — so the one thing the guard can do is refuse to let a live doc
    reference artifacts that no longer exist. Historical plan docs opt out by
    keeping their banner.
    """
    for pattern in LIVE_DOCS:
        for path in sorted(root.glob(pattern)):
            if not path.is_file():
                continue
            rel = path.relative_to(root).as_posix()
            if rel in RETIRED_TERM_ALLOWLIST:
                continue
            text = path.read_text(encoding="utf-8", errors="ignore")
            if HISTORICAL_DOC_MARKER in text:
                continue
            hits = [term for term in RETIRED_TERMS if term in text]
            if hits:
                guard.error(
                    f"{rel} mentions retired vendor artifact(s): {', '.join(hits)}. "
                    "Provenance is derived from vendor-overlay.json now — rewrite the "
                    'doc, or add the "Historical plan (retired manifest model)" banner '
                    "if the file is kept for history."
                )


def check_forced_exact(index: dict, root: Path, guard: Guard) -> None:
    sources = changed_with_head_source(root)
    local_root = index.get("local_root", "apps/android/")
    entries = [
        e for e in (index.get("files") or []) if e.get("forced_exact")
    ]
    hits = []
    for e in entries:
        changed_path = next(
            (
                p
                for p in (f"{local_root}{e['local']}", e["local"])
                if p in sources
            ),
            None,
        )
        if changed_path is None:
            continue
        # A comment-only edit cannot move a gesture/measurement/drawing number,
        # so it does not deserve the "verify with these tests" prompt.
        if comment_only_edit(root, changed_path, sources[changed_path]):
            continue
        hits.append(e)
    if not hits:
        return
    guard.error(
        f"{len(hits)} forced-exact file(s) changed in the working tree. "
        "Keep gesture/measurement/drawing behavior upstream-shaped; verify with:"
    )
    for e in hits:
        guard.error(
            f"  {e['local']} [{','.join(e['forced_exact'])}]"
        )
    guard.error(
        "  run: ./apps/android/gradlew -p apps/android :app:testDebugUnitTest "
        + " ".join(f"--tests '{t}'" for t in FORCED_EXACT_TESTS)
    )


def check_drift(index: dict, root: Path, guard: Guard) -> None:
    vd = vendor_dir(root)
    pin = normalize_sha((index.get("upstream") or {}).get("pinned_commit"))
    head = git_rev_parse("origin/master", vd)
    if not pin or not head:
        return
    paths = [
        p
        for p in (upstream_git_path(e, index) for e in index.get("files") or [])
        if p
    ]
    touched = git_log_paths(pin, head, paths, vd)
    if touched:
        guard.info(
            f"upstream {head[:12]} touches {len(touched)} tracked file(s) "
            "(informational; re-sync only on demand)"
        )


def check_compile(root: Path, guard: Guard) -> None:
    gradlew = root / "apps" / "android" / "gradlew"
    if not gradlew.is_file():
        guard.info("gradlew missing; skipped :TMessagesProj compile")
        return
    proc = subprocess.run(
        [str(gradlew), "-p", str(root / "apps" / "android"),
         ":TMessagesProj:compileDebugJavaWithJavac", "--quiet"],
        cwd=str(root),
    )
    if proc.returncode != 0:
        guard.error(":TMessagesProj:compileDebugJavaWithJavac failed (see above)")


def main() -> int:
    args = parse_args()
    root = repo_root()
    guard = Guard()

    check_legacy_artifacts(root, guard)
    check_retired_terms(root, guard)

    index: dict | None = None
    try:
        index = load_index(root)
    except ManifestError as e:
        guard.error(str(e))
        vd = vendor_dir(root)
        if not vd.exists():
            guard.error(
                f"pristine Telegram tree missing at {display_path(vd, root)} — "
                "run scripts/vendor-setup.sh"
            )

    if index is not None:
        code, out = run_isolation_check(root)
        if code != 0:
            guard.error(out or "vendor-isolation-check failed")
        else:
            files = index.get("files") or []
            modified = sum(1 for e in files if e.get("modified"))
            guard.info(
                f"{len(files)} tracked file(s): {modified} differ from the pin, "
                f"{len(index.get('compat_shims') or [])} scratch shim(s)"
            )
        # forced-exact edits are cheap to detect and matter most for drift, so
        # they are part of --quick too; the upstream drift report (git log over
        # ~500 paths) only runs in the full mode.
        check_forced_exact(index, root, guard)
        if not args.quick:
            check_drift(index, root, guard)

    if args.compile:
        check_compile(root, guard)

    for line in guard.infos:
        print(f"INFO  {line}")
    for line in guard.errors:
        print(f"ERROR {line}")

    if guard.errors:
        print(f"=== vendor-guard: {len(guard.errors)} problem(s) ===")
        return 1
    print("=== vendor-guard: OK ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())

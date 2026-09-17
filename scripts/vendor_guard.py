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
    redundant_native,
    repo_root,
    resource_report,
    run_isolation_check,
    stub_candidates,
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
        self.warnings: list[str] = []
        self.infos: list[str] = []

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warnings.append(msg)

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
        # and neither can an import swap (upstream moving a class to another
        # package, say), so they do not deserve the "verify with these tests"
        # prompt.
        if comment_only_edit(
            root, changed_path, sources[changed_path], ignore_imports=True
        ):
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


def _sample(paths: list[str], limit: int = 3) -> str:
    head = ", ".join(paths[:limit])
    return f"{head}, …" if len(paths) > limit else head


def check_ledger(index: dict, guard: Guard, *, verbose: bool = True) -> None:
    """Both directions of a mislabelled vendored file.

    A copy that shrank to a fraction of its upstream original is a stub wearing
    an upstream path; a `native` file that still matches upstream is a clean
    copy wearing our label. Neither changes behaviour, but they make the bump
    report lie, so they are reported on every run.
    """
    stubs = stub_candidates(index)
    if stubs:
        guard.warn(
            f"{len(stubs)} exact file(s) are far smaller than their upstream "
            f"original ({_sample([e['local'].split('/')[-1] for e in stubs])}) — "
            "adopt the upstream file, or add them to vendor-overlay.json `native`"
        )
        if verbose:
            for entry in stubs[:10]:
                guard.warn(
                    f"  {entry['local']} "
                    f"({entry.get('local_bytes')}B vs upstream "
                    f"{entry.get('upstream_bytes')}B)"
                )
    redundant = redundant_native(index)
    if redundant:
        guard.warn(
            f"{len(redundant)} native-declared file(s) are byte-identical to "
            f"upstream ({_sample([e['local'] for e in redundant])}) — drop them "
            "from `native` so the bump report tracks them"
        )


def check_resources(index: dict, guard: Guard, *, verbose: bool = True) -> None:
    """Declared resource copies: unacknowledged drift is an error.

    Resources are declared per group, and their bytes are verified against the
    group's `vendored_at` baseline. A byte difference the overlay does not list
    in that group's `differs` array is drift nobody signed off on.
    """
    files = index.get("resource_files") or []
    if not files:
        return
    report = resource_report(index)
    compared = [
        e for e in files if not e.get("ours") and e.get("modified") is not None
    ]
    if report["uncompared"]:
        guard.info(
            f"{len(report['uncompared'])} declared resource file(s) were not "
            "compared (blob contents missing in this checkout — run "
            "scripts/vendor-setup.sh --prefetch for the byte check)"
        )
    guard.info(
        f"{len(files)} declared resource file(s): "
        f"{len(report['identical'])} identical, "
        f"{len(report['acknowledged'])} acknowledged drift, "
        f"{len(report['ours'])} ours, "
        f"{len(compared)} compared"
    )
    if report["differs"]:
        guard.error(
            f"{len(report['differs'])} declared resource file(s) changed vs "
            "their declared baseline — if intentional, add the path to that "
            "group's `differs` array in vendor-overlay.json; otherwise restore "
            "the file (git checkout -- <path>)"
        )
        for entry in report["differs"][:10] if verbose else report["differs"][:3]:
            guard.error(f"  {entry['local']}")
    if report["unresolved"]:
        guard.warn(
            f"{len(report['unresolved'])} declared resource file(s) have no "
            "upstream counterpart at their baseline — annotate the group's "
            "`paths` (counterpart elsewhere) or `ours` (our own asset)"
        )
        for entry in report["unresolved"][:10] if verbose else []:
            guard.warn(f"  {entry['local']}")
    if report["stale_acknowledged"]:
        guard.warn(
            f"{len(report['stale_acknowledged'])} acknowledged resource file(s) "
            "are identical to their baseline again — drop them from the group's "
            "`differs` array"
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
        check_ledger(index, guard, verbose=not args.quick)
        check_resources(index, guard, verbose=not args.quick)
        if not args.quick:
            check_drift(index, root, guard)

    if args.compile:
        check_compile(root, guard)

    for line in guard.infos:
        print(f"INFO  {line}")
    for line in guard.warnings:
        print(f"WARN  {line}")
    for line in guard.errors:
        print(f"ERROR {line}")

    if guard.errors:
        print(
            f"=== vendor-guard: {len(guard.errors)} problem(s), "
            f"{len(guard.warnings)} warning(s) ==="
        )
        return 1
    if guard.warnings:
        print(f"=== vendor-guard: OK ({len(guard.warnings)} warning(s)) ===")
        return 0
    print("=== vendor-guard: OK ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())

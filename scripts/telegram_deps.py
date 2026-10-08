#!/usr/bin/env python3
"""telegram-deps: materialize the pinned native dependency trees of the subtree.

`apps/android/telegram` is a `git subtree` import. A subtree import keeps
upstream's `.gitmodules` and the 15 gitlink entries, but a fresh clone has
empty dependency directories: the content a submodule would provide is not part
of the parent commit. The NDK build (and the media module's `core_settings.gradle`
at configure time) needs those 15 trees on disk before Gradle can run.

Each tree is fetched from its own remote at the exact commit recorded by the
gitlink in the pinned subtree, then extracted with `git archive`. Nothing is
copied from another worktree and no prebuilt `.so` is involved, so a fresh clone
builds from source. This never writes outside the gitlink paths and never adds
any Telegram/tgnet credential or secret.

The pin is the `git subtree` merge commit found in git history (the newest merge
of `apps/android/telegram`), so the SHAs follow the tree automatically; nothing
here trusts the README or a committed per-file manifest.

Modes:
  (default)   materialize every dependency that is missing or at the wrong pin
  --plan      print `sha<TAB>url<TAB>path` for each dependency, then exit 0
  --verify    check every dependency is on disk at its pinned commit and exit 1
              with a report if one is missing or stale (offline; no network)
  --base SHA  read the pinned gitlinks from SHA instead of the discovered merge
              (a wrong SHA fails; used by the tests)
  --timeout N per-git-command network timeout in seconds (default 1800)
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
from pathlib import Path
from typing import Callable, List, Optional

sys.path.insert(0, str(Path(__file__).resolve().parent))

from telegramlib import (  # noqa: E402
    DEP_STAMP,
    Dependency,
    TelegramPinError,
    dependency_plan,
    read_stamp,
    repo_root,
    verify_dependencies,
)


def _stderr_text(proc: subprocess.CompletedProcess) -> str:
    err = proc.stderr
    if err is None:
        return ""
    if isinstance(err, bytes):
        return err.decode("utf-8", "replace")
    return err


def _run(
    args: List[str],
    *,
    cwd: Optional[Path] = None,
    timeout: Optional[int] = None,
    stdout=subprocess.PIPE,
) -> subprocess.CompletedProcess:
    env = dict(os.environ)
    env.setdefault("GIT_TERMINAL_PROMPT", "0")
    return subprocess.run(
        args,
        cwd=str(cwd) if cwd else None,
        stdout=stdout,
        stderr=subprocess.PIPE,
        text=stdout == subprocess.PIPE,
        env=env,
        timeout=timeout,
    )


def _fetch_dependency(dep: Dependency, dest: Path, timeout: int) -> None:
    """Fetch `dep.sha` from `dep.url` and extract it into `dest`."""
    work = Path(tempfile.mkdtemp(prefix="telegram-dep-"))
    archive = work.with_suffix(".tar")
    try:
        _run(["git", "init", "-q", str(work)], timeout=timeout)
        _run(["git", "-C", str(work), "remote", "add", "origin", dep.url], timeout=timeout)

        # Prefer a shallow fetch of just the pinned commit. Some hosts
        # (chromium.googlesource.com) refuse shallow-by-sha, so fall back to a
        # full fetch of that commit.
        last: Optional[str] = None
        for fetch in (
            ["-C", str(work), "fetch", "-q", "--depth=1", "origin", dep.sha],
            ["-C", str(work), "fetch", "-q", "origin", dep.sha],
        ):
            proc = _run(["git", *fetch], timeout=timeout)
            if proc.returncode == 0:
                last = None
                break
            last = _stderr_text(proc) or "fetch failed"
        if last is not None:
            raise TelegramPinError(f"cannot fetch {dep.name} ({dep.url}): {last.strip()}")

        got = _run(["git", "-C", str(work), "rev-parse", "FETCH_HEAD"], timeout=timeout).stdout.strip()
        if got != dep.sha:
            raise TelegramPinError(
                f"{dep.name}: fetched {got[:12]} but the pin is {dep.sha[:12]}"
            )

        with open(archive, "wb") as fh:
            proc = _run(
                ["git", "-C", str(work), "archive", "--format=tar", "FETCH_HEAD"],
                timeout=timeout,
                stdout=fh,
            )
        if proc.returncode != 0:
            raise TelegramPinError(f"{dep.name}: git archive failed")

        shutil.rmtree(str(dest), ignore_errors=True)
        dest.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive) as tf:
            tf.extractall(str(dest))
    finally:
        shutil.rmtree(str(work), ignore_errors=True)
        try:
            archive.unlink()
        except OSError:
            pass


def materialize(
    root: Path,
    plan: List[Dependency],
    *,
    timeout: int,
    log: Callable[[str], None] = print,
) -> int:
    failures = 0
    for dep in plan:
        dest = root / dep.path
        on_disk = read_stamp(dest)
        if on_disk == dep.sha and dest.is_dir() and any(dest.iterdir()):
            log(f"ok      {dep.name} ({dep.sha[:12]})")
            continue
        started = time.time()
        log(f"fetch   {dep.name} ({dep.sha[:12]}) from {dep.url}")
        try:
            _fetch_dependency(dep, dest, timeout)
        except (TelegramPinError, subprocess.SubprocessError, OSError) as exc:
            failures += 1
            log(f"FAIL    {dep.name}: {exc}")
            continue
        (dest / DEP_STAMP).write_text(dep.sha + "\n", encoding="utf-8")
        log(f"done    {dep.name} in {time.time() - started:.0f}s")
    return failures


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", help="repository root (default: discovered)")
    parser.add_argument("--base", help="subtree pin commit (default: from git history)")
    parser.add_argument("--plan", action="store_true", help="print the resolved plan and exit")
    parser.add_argument("--verify", action="store_true", help="offline check only")
    parser.add_argument("--timeout", type=int, default=1800, help="per-git-command timeout (s)")
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    root = Path(args.root).resolve() if args.root else repo_root()
    try:
        plan = dependency_plan(root, args.base)
    except TelegramPinError as exc:
        print(f"ERROR {exc}")
        return 1

    if args.plan:
        for dep in plan:
            print(f"{dep.sha}\t{dep.url}\t{dep.path}")
        return 0

    if args.verify:
        problems = verify_dependencies(root, plan)
        for problem in problems:
            print(f"ERROR {problem}")
        print(f"=== telegram-deps: {len(plan)} pinned, {len(problems)} problem(s) ===")
        return 1 if problems else 0

    failures = materialize(root, plan, timeout=args.timeout)
    if failures:
        print(f"=== telegram-deps: {failures} dependency(ies) failed ===")
        return 1
    print(f"=== telegram-deps: {len(plan)} dependency(ies) at the pin ===")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

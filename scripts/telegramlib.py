#!/usr/bin/env python3
"""Shared helpers for the full-Telegram tools: telegram-deps and the hook guard.

`apps/android/telegram` is a `git subtree` import of upstream Telegram. Two
facts about that import drive everything here:

* The subtree carries upstream's `.gitmodules` and its 15 gitlink entries, so
  the pinned dependency SHAs are already in this repository's history. Nothing
  has to be copied between worktrees and no committed manifest has to be kept
  in sync: the pinned gitlinks are read straight from the subtree commit.
* The subtree is the pin, and that pin is found in git history (the merge
  commit that added or last replaced the prefix) rather than assumed from a
  README.

This module is deliberately standalone: it must not import the skeleton's
`vendorlib`/`vendor-overlay.json`, because that overlay classifies
`apps/android/TMessagesProj` (the old shrunk skeleton), not this tree.
"""
from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path
from typing import List, NamedTuple, Optional

# Repo-relative prefix the upstream subtree lives at.
SUBTREE_PREFIX = "apps/android/telegram"
# Upstream's house package for the one-line hooks; every allowed upstream edit
# mentions it.
HOOKS_PACKAGE = "io.github.baba983.nativedo.hooks"
# A per-dependency stamp written next to the materialized tree, so a warm
# checkout proves it already has the pinned content without touching the
# network. The stamp lives inside the dependency directory, which is a gitlink
# (a submodule path the parent does not track), so it never shows in
# `git status` and never gets committed.
DEP_STAMP = ".linuxdo-telegram-dep"


class TelegramPinError(RuntimeError):
    """The pinned subtree could not be resolved from this checkout."""


class Dependency(NamedTuple):
    name: str  # path relative to the subtree root, e.g. "TMessagesProj/jni/td"
    path: str  # repo-relative path, e.g. "apps/android/telegram/TMessagesProj/jni/td"
    url: str
    sha: str


def repo_root() -> Path:
    env = os.environ.get("NATIVEDO_ROOT")
    if env:
        return Path(env).resolve()
    return Path(__file__).resolve().parent.parent


def _git(
    args: List[str],
    root: Path,
    *,
    check: bool = True,
    timeout: Optional[int] = None,
) -> subprocess.CompletedProcess:
    env = dict(os.environ)
    # Never block on a credential prompt: these commands run non-interactively
    # in CI and in orb setup.
    env.setdefault("GIT_TERMINAL_PROMPT", "0")
    proc = subprocess.run(
        ["git", "-C", str(root), *args],
        capture_output=True,
        text=True,
        env=env,
        timeout=timeout,
    )
    if check and proc.returncode != 0:
        raise TelegramPinError(
            "git " + " ".join(args) + " failed: " + (proc.stderr.strip() or proc.stdout.strip())
        )
    return proc


def subtree_base_commit(root: Path) -> str:
    """The pin: the `git subtree` merge that added/replaced the prefix.

    Read from history (newest matching ancestor), not from the README, so a
    README that lags the tree cannot mislead the tools. A shallow clone has no
    such merge; callers turn that into an actionable error.
    """
    proc = _git(
        [
            "log",
            "--format=%H",
            "-1",
            "--merges",
            "--fixed-strings",
            "--grep=as 'apps/android/telegram'",
            "HEAD",
        ],
        root,
        check=False,
    )
    lines = [ln for ln in proc.stdout.splitlines() if ln.strip()]
    if not lines:
        raise TelegramPinError(
            "no `git subtree` merge for 'apps/android/telegram' in this clone; "
            "fetch full history (actions/checkout fetch-depth: 0) or run "
            "`git fetch --unshallow`"
        )
    return lines[0].strip()


def _assert_subtree_commit(root: Path, base: str) -> None:
    if not re.fullmatch(r"[0-9a-fA-F]{7,40}", base or ""):
        raise TelegramPinError(f"bad subtree pin {base!r}: not a commit id")
    proc = _git(["cat-file", "-e", base + "^{commit}"], root, check=False)
    if proc.returncode != 0:
        raise TelegramPinError(f"bad subtree pin {base}: not a commit in this repository")
    marker = _git(
        ["cat-file", "-e", f"{base}:{SUBTREE_PREFIX}/.gitmodules"], root, check=False
    )
    if marker.returncode != 0:
        raise TelegramPinError(
            f"wrong subtree pin {base}: {SUBTREE_PREFIX}/.gitmodules is not at that commit"
        )


def pinned_gitlinks(root: Path, base: Optional[str] = None) -> List[tuple]:
    """`(repo_path, sha)` for every gitlink in the pinned subtree, sorted."""
    commit = base or subtree_base_commit(root)
    _assert_subtree_commit(root, commit)
    proc = _git(["ls-tree", "-r", commit, "--", SUBTREE_PREFIX], root)
    entries: List[tuple] = []
    for line in proc.stdout.splitlines():
        meta, _, path = line.partition("\t")
        parts = meta.split()
        if len(parts) == 3 and parts[0] == "160000":
            entries.append((path, parts[2]))
    if not entries:
        raise TelegramPinError(
            f"wrong subtree pin {commit}: no gitlink dependencies under {SUBTREE_PREFIX}"
        )
    return sorted(entries)


def _gitmodules_path(root: Path) -> Path:
    return root / SUBTREE_PREFIX / ".gitmodules"


def submodule_urls(root: Path) -> dict:
    """`.gitmodules` path -> url, relative to the subtree root."""
    gitmodules = _gitmodules_path(root)
    if not gitmodules.is_file():
        raise TelegramPinError(f"missing {SUBTREE_PREFIX}/.gitmodules")
    proc = _git(
        [
            "config",
            "--file",
            str(gitmodules),
            "--get-regexp",
            r"^submodule\..*\.(path|url)$",
        ],
        root,
        check=False,
    )
    paths: dict = {}
    urls: dict = {}
    for line in proc.stdout.splitlines():
        key, _, value = line.partition(" ")
        match = re.match(r"submodule\.(.*)\.(path|url)$", key)
        if not match:
            continue
        if match.group(2) == "path":
            paths[match.group(1)] = value.strip()
        else:
            urls[match.group(1)] = value.strip()
    result = {paths[n]: urls[n] for n in paths if n in urls}
    if not result:
        raise TelegramPinError(f"{SUBTREE_PREFIX}/.gitmodules declares no dependencies")
    return result


def dependency_plan(root: Path, base: Optional[str] = None) -> List[Dependency]:
    """The 15 pinned source trees the native build needs, from the pin alone.

    Gitlinks are authoritative for the SHAs; `.gitmodules` stays authoritative
    for the URLs. A disagreement (a gitlink with no URL, or a submodule with no
    gitlink) means the tree is not a clean upstream import, so it fails.
    """
    gitlinks = pinned_gitlinks(root, base)
    urls = submodule_urls(root)
    plan: List[Dependency] = []
    for path, sha in gitlinks:
        rel = path[len(SUBTREE_PREFIX) + 1 :] if path.startswith(SUBTREE_PREFIX + "/") else path
        if rel not in urls:
            raise TelegramPinError(f"gitlink {rel} has no entry in .gitmodules")
        plan.append(Dependency(name=rel, path=path, url=urls[rel], sha=sha))
    if len(urls) != len(plan):
        extra = sorted(set(urls) - {d.name for d in plan})
        raise TelegramPinError(
            "gitlinks and .gitmodules disagree; submodules without a gitlink: "
            + ", ".join(extra)
        )
    return plan


def read_stamp(dep_dir: Path) -> Optional[str]:
    stamp = dep_dir / DEP_STAMP
    if not stamp.is_file():
        return None
    try:
        return stamp.read_text(encoding="utf-8").strip()
    except OSError:
        return None


def verify_dependencies(root: Path, plan: List[Dependency]) -> List[str]:
    """Offline check that every dependency is materialized at its pinned sha."""
    problems: List[str] = []
    for dep in plan:
        dep_dir = root / dep.path
        if not dep_dir.is_dir():
            problems.append(f"missing dependency: {dep.name} at {dep.path}")
            continue
        try:
            has_content = any(dep_dir.iterdir())
        except OSError:
            has_content = False
        if not has_content:
            problems.append(f"empty dependency: {dep.name} at {dep.path}")
            continue
        on_disk = read_stamp(dep_dir)
        if on_disk is None:
            problems.append(
                f"unverified dependency: {dep.name} has no pinned-content stamp; "
                "run scripts/telegram-deps.sh"
            )
        elif on_disk != dep.sha:
            problems.append(
                f"wrong pinned dependency: {dep.name} is {on_disk[:12]} but the pin is "
                f"{dep.sha[:12]}"
            )
    return problems

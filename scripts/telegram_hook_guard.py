#!/usr/bin/env python3
"""telegram-hook-guard: keep `apps/android/telegram` pristine except for hooks.

`apps/android/telegram` is a `git subtree` of upstream Telegram. Our client
talks to upstream through exactly one seam, the class
`io.github.baba983.nativedo.hooks.NativeDoHooks`; each upstream file touches it
with a single call (and `RichEditor` also carries the one import). Rendering,
gesture, measurement, recycling and animation stay upstream's own code.

This guard diffs the subtree against its pinned `git subtree` merge commit
(found in git history, never assumed from the README or a committed manifest)
and fails when an upstream file changed in any other way. Allowed, exactly:

  * upstream Java under `TMessagesProj/src/main/java/**` where every added line is
    a *minimal* hook callsite — importing the hooks class, a bare call, a call
    returned or assigned, or an `if` whose condition is the hook call and whose
    body is a literal `return` or a block, and nothing else on the line — or a
    line re-indented from upstream; and every removed line is the operand of such
    a call, a line that reappears among the added lines, or the brace/`if`
    scaffolding around the replacement — an unrelated upstream line may not be
    dropped merely because a hook call sits in the same hunk;
  * inside a hook guard block, only a single hand-off call to a method *this file
    itself declares* and a literal `return` (no assignment, no render call, no
    second call, no computed return), so arbitrary logic cannot hide behind a hook
    condition;
  * documented build/config additions, each carrying a `NativeDo` marker;
  * new files only under our own namespaces (`io/github/baba983/**`,
    `NativeDoSpike/**`) — our code does not hide at an upstream path.

Everything else — a rewritten render branch, a tweaked measurement, a deleted
upstream file, a changed dependency pin — fails.

This is deliberately separate from `scripts/vendor_guard.py`, which checks the
older `apps/android/TMessagesProj` skeleton against `vendor-overlay.json`. The
full subtree is not part of that overlay and must not be classified by it.

Usage:
  scripts/telegram-hook-guard.sh                 # the working tree vs the pin
  scripts/telegram-hook-guard.sh --base <sha>    # a specific pin (tests)
  scripts/telegram-hook-guard.sh --root <dir>    # a specific checkout (tests)
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path
from typing import List, Optional, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

from telegramlib import (  # noqa: E402
    HOOKS_PACKAGE,
    SUBTREE_PREFIX,
    TelegramPinError,
    pinned_gitlinks,
    repo_root,
    subtree_base_commit,
)

HOOK_IMPORT_RE = re.compile(
    r"^\s*import\s+" + re.escape(HOOKS_PACKAGE) + r"\.NativeDoHooks\s*;"
)
# Short (`NativeDoHooks.x`) or fully-qualified (`io.github...NativeDoHooks.x`).
HOOK_CALL_RE = re.compile(r"\bNativeDoHooks\s*\.")
NATIVEDO_MARKER = "NativeDo"

JAVA_ROOT = SUBTREE_PREFIX + "/TMessagesProj/src/main/java/"
OUR_JAVA_ROOT = JAVA_ROOT + "io/github/baba983/"
OUR_SPIKE_ROOT = SUBTREE_PREFIX + "/NativeDoSpike/"

BUILD_FILE_NAMES = {
    "settings.gradle",
    "settings.gradle.kts",
    "build.gradle",
    "build.gradle.kts",
    "gradle.properties",
    "core_settings.gradle",
}

# A `NativeDoHooks` call, optionally package-qualified, as it appears on a line.
_HOOK_CALL_SRC = r"(?:[A-Za-z_$][\w$.]*\.)*NativeDoHooks\.[A-Za-z_$][\w$]*\s*\(.*\)"
# The whole added line must be exactly one minimal hook callsite, not a hook call
# with extra code riding along: a bare call, a call returned or assigned, or an
# `if` whose condition is the call and whose body is a bare boolean exit or a block.
_HOOK_STMT_RE = re.compile(r"^(?:return\s+)?" + _HOOK_CALL_SRC + r";$")
_HOOK_IF_RE = re.compile(
    r"^if\s*\(.*" + _HOOK_CALL_SRC + r".*\)\s*(?:return(?:\s+(?:true|false))?;|\{)$"
)
_HOOK_ASSIGN_RE = re.compile(r"^[A-Za-z_$][\w$.<>,\[\]\s]*=\s*" + _HOOK_CALL_SRC + r";$")

# Inside a hook guard block: a hand-off is a bare call on a method the file
# itself declares; the only other statement is a literal `return`. Anything else
# (an assignment, a render call, a second call, a computed return) is logic and
# fails, so a guard block cannot smuggle an unrelated semantic change.
_BARE_CALL_RE = re.compile(r"^[A-Za-z_$][\w$]*\s*\(.*\)\s*;$")
_RETURN_RE = re.compile(r"^return(\s+(?:true|false))?;$")

# Pure structural lines a replacement may drop without changing semantics.
_SCAFFOLD = frozenset({"{", "}", "};", "else {", "} else {", "} else", ""})
_IF_LINE_RE = re.compile(r"^(?:if|else\s+if|\}\s*else)\b")

# Java keywords and literals, ignored when deciding whether a removed line shares
# a meaningful identifier with the hook call that replaced it.
_JAVA_KEYWORDS = frozenset(
    """
    abstract assert boolean break byte case catch char class const continue default do double else
    enum extends final finally float for goto if implements import instanceof int interface long
    native new package private protected public return short static strictfp super switch
    synchronized this throw throws transient try void volatile while var true false null
    """.split()
)


class Report:
    def __init__(self) -> None:
        self.errors: List[str] = []
        self.infos: List[str] = []

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def info(self, msg: str) -> None:
        self.infos.append(msg)


def _normalize(line: str) -> str:
    return " ".join(line.split())


def _code(line: str) -> str:
    """A line without its trailing `//` comment, for brace/statement shape."""
    return line.split("//", 1)[0].rstrip()


def _is_ours(path: str) -> bool:
    return path.startswith(OUR_JAVA_ROOT) or path.startswith(OUR_SPIKE_ROOT)


def _is_upstream_java(path: str) -> bool:
    return path.startswith(JAVA_ROOT) and path.endswith(".java") and not _is_ours(path)


def _is_build_file(path: str) -> bool:
    return Path(path).name in BUILD_FILE_NAMES


def _identifiers(code: str) -> set:
    """Meaningful identifiers of a line: words minus keywords and literals."""
    tokens = re.findall(r"[A-Za-z_$][\w$]*", code)
    return {token for token in tokens if token not in _JAVA_KEYWORDS}


def _minimal_hook_line(line: str) -> bool:
    """True when the added line is nothing but one hook callsite.

    Rejects a line that carries a second statement (`NativeDoHooks.f(); evil();`)
    or a hook call buried in unrelated code, so a hook line cannot hide logic.
    """
    code = _code(line).strip()
    if code.endswith(";"):
        if code.count(";") != 1:
            return False
    elif ";" in code:
        return False
    return bool(
        _HOOK_STMT_RE.match(code)
        or _HOOK_IF_RE.match(code)
        or _HOOK_ASSIGN_RE.match(code)
    )


def _declares_method(text: str, name: str) -> bool:
    """True when `text` declares a method called `name` (a hand-off target)."""
    pattern = (
        r"(?:^|[;{}\s])(?:[\w$.<>\[\],?]+\s+)+"
        + re.escape(name)
        + r"\s*\([^;{}]*\)\s*(?:throws[\w.,\s]+)?\{"
    )
    return re.search(pattern, text, re.MULTILINE) is not None


def _removal_explained(code: str, hook_codes: List[str]) -> bool:
    """True when a removed line is replaced by a hook call, not merely near one.

    A removal is explained only if it is the scaffolding around a replacement
    (a brace or an `if`/`else` header), or it is a control transfer matched by a
    hook line of the same kind, or it shares a meaningful identifier with an
    added hook line (`LaunchActivity.instance.getX()` -> `NativeDoHooks.getX()`
    or an expression handed in as the hook's argument). An unrelated upstream
    line dropped beside a hook call is not explained and fails.
    """
    stripped = code.strip()
    if stripped in _SCAFFOLD or _IF_LINE_RE.match(stripped):
        return True
    identifiers = _identifiers(code)
    if not identifiers:
        kind = re.match(r"(return|break|continue)\b", stripped)
        if kind:
            return any(
                re.match(r"\s*" + kind.group(1) + r"\b", hook_code)
                for hook_code in hook_codes
            )
        return False
    return any(identifiers & _identifiers(hook_code) for hook_code in hook_codes)


def parse_hunks(diff_text: str) -> List[Tuple[List[str], List[str]]]:
    """`[(added, removed)]` per hunk (`--unified` diff body, no headers)."""
    hunks: List[Tuple[List[str], List[str]]] = []
    added: List[str] = []
    removed: List[str] = []
    in_hunk = False
    for line in diff_text.splitlines():
        if line.startswith("@@ "):
            if in_hunk:
                hunks.append((added, removed))
            added, removed, in_hunk = [], [], True
            continue
        if not in_hunk:
            continue
        if line.startswith("+++ ") or line.startswith("--- "):
            continue
        if line.startswith("+"):
            added.append(line[1:])
        elif line.startswith("-"):
            removed.append(line[1:])
    if in_hunk:
        hunks.append((added, removed))
    return [(a, r) for a, r in hunks if a or r]


def analyze_java_hunks(
    path: str,
    hunks: List[Tuple[List[str], List[str]]],
    file_text: Optional[str] = None,
) -> List[str]:
    problems: List[str] = []
    for index, (added, removed) in enumerate(hunks):
        added_norm = {_normalize(ln) for ln in added}
        removed_norm = {_normalize(ln) for ln in removed}
        hook_codes = [_code(ln) for ln in added if HOOK_CALL_RE.search(ln)]
        depth = 0
        handoffs = 0
        for line in added:
            if HOOK_IMPORT_RE.match(line):
                continue
            if HOOK_CALL_RE.search(line):
                if not _minimal_hook_line(line):
                    problems.append(
                        f"{path}: hunk {index + 1} hook line is not a minimal "
                        f"NativeDoHooks callsite: {line.strip()}"
                    )
                if _code(line).endswith("{"):
                    depth += 1
                    handoffs = 0
                continue
            if depth > 0:
                code = _code(line).strip()
                if code == "}":
                    depth -= 1
                elif _BARE_CALL_RE.match(code):
                    handoffs += 1
                    target = code.split("(", 1)[0].strip()
                    if handoffs > 1:
                        problems.append(
                            f"{path}: hunk {index + 1} runs a second hand-off call "
                            f"inside a hook guard block: {line.strip()}"
                        )
                    elif file_text is not None and not _declares_method(file_text, target):
                        problems.append(
                            f"{path}: hunk {index + 1} hands off to {target}, which "
                            f"this file does not declare: {line.strip()}"
                        )
                elif not _RETURN_RE.match(code):
                    problems.append(
                        f"{path}: hunk {index + 1} puts non-hook logic inside a hook "
                        f"guard block: {line.strip()}"
                    )
                continue
            if _normalize(line) in removed_norm:
                continue
            problems.append(
                f"{path}: hunk {index + 1} adds a line that is not a minimal "
                f"NativeDoHooks callsite: {line.strip()}"
            )
        for line in removed:
            if _normalize(line) in added_norm:
                continue
            if _removal_explained(_code(line), hook_codes):
                continue
            problems.append(
                f"{path}: hunk {index + 1} removes an upstream line with no hook "
                f"replacement: {line.strip()}"
            )
    return problems


def analyze_build_hunks(path: str, hunks: List[Tuple[List[str], List[str]]]) -> List[str]:
    problems: List[str] = []
    for index, (added, removed) in enumerate(hunks):
        for line in removed:
            problems.append(
                f"{path}: hunk {index + 1} removes a build/config line: {line.strip()}"
            )
        for line in added:
            if NATIVEDO_MARKER not in line:
                problems.append(
                    f"{path}: hunk {index + 1} changes build/config without a NativeDo "
                    f"marker: {line.strip()}"
                )
    return problems


def _git(root: Path, args: List[str]) -> str:
    proc = subprocess.run(
        ["git", "-C", str(root), *args],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        raise TelegramPinError(proc.stderr.strip() or "git " + " ".join(args) + " failed")
    return proc.stdout


def _single_file_diff(root: Path, base: str, path: str) -> str:
    return _git(root, ["diff", "--unified=3", "--no-color", base, "--", path])


def check(root: Path, base: Optional[str] = None) -> Report:
    report = Report()
    commit = base or subtree_base_commit(root)
    # Validates the commit actually carries the subtree (a wrong pin fails here).
    gitlinks = {path for path, _sha in pinned_gitlinks(root, commit)}

    name_status = _git(
        root, ["diff", "--name-status", "--no-renames", commit, "--", SUBTREE_PREFIX]
    )
    for line in name_status.splitlines():
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        status, path = parts[0], parts[1]
        if path in gitlinks:
            report.error(f"dependency pin changed: {path}")
            continue
        if status == "A":
            if not _is_ours(path):
                report.error(f"new file at an upstream path: {path}")
            continue
        if status == "D":
            report.error(f"deleted upstream file: {path}")
            continue
        if status not in ("M", "T"):
            report.error(f"unexpected change ({status}) to upstream file: {path}")
            continue
        if _is_ours(path):
            continue
        if _is_build_file(path):
            hunks = parse_hunks(_single_file_diff(root, commit, path))
            report.errors.extend(analyze_build_hunks(path, hunks))
        elif _is_upstream_java(path):
            hunks = parse_hunks(_single_file_diff(root, commit, path))
            # The pinned text names the methods a hook guard block may hand off
            # to; a file may only call a method it (upstream) declares.
            file_text = _git(root, ["show", f"{commit}:{path}"])
            report.errors.extend(analyze_java_hunks(path, hunks, file_text))
        else:
            report.error(f"upstream file changed outside the hook seam: {path}")

    # Untracked files at an upstream path are a shim the diff above cannot see;
    # gitlink contents are not listed here (the parent does not track them).
    untracked = _git(
        root, ["ls-files", "--others", "--exclude-standard", "--", SUBTREE_PREFIX]
    )
    for path in untracked.splitlines():
        if path.strip() and not _is_ours(path):
            report.error(f"untracked file at an upstream path: {path}")

    report.info(
        f"{len(gitlinks)} pinned dependency gitlink(s); hook guard over {SUBTREE_PREFIX}"
    )
    return report


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("--root", help="repository root (default: discovered)")
    parser.add_argument("--base", help="subtree pin commit (default: from git history)")
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    root = Path(args.root).resolve() if args.root else repo_root()
    try:
        report = check(root, args.base)
    except TelegramPinError as exc:
        print(f"ERROR {exc}")
        return 1

    for line in report.infos:
        print(f"INFO  {line}")
    for line in report.errors:
        print(f"ERROR {line}")
    if report.errors:
        print(f"=== telegram-hook-guard: {len(report.errors)} problem(s) ===")
        return 1
    print("=== telegram-hook-guard: OK ===")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

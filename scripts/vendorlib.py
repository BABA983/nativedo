#!/usr/bin/env python3
"""Shared helpers for Telegram vendoring tooling.

Manifest: vendor-manifest.json at repo root.
Pristine upstream tree: vendor/telegram-android/ (gitignored).
Local copies: apps/android/ relative paths in each file entry.
"""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Optional, Union

PathLike = Union[Path, str]

# Known short / full pins that appear in historical manifests.
PIN_ALIASES = {
    "009e97356": "009e97356f966bb81eceba113d210230bf383122",
    "009e97356f966bb81eceba113d210230bf383122": "009e97356f966bb81eceba113d210230bf383122",
    "9b50143d": "9b50143d8896d255d03155598937e4f3e28afd86",
    "9b50143d8896d255d03155598937e4f3e28afd86": "9b50143d8896d255d03155598937e4f3e28afd86",
}

CURRENT_PIN = "9b50143d8896d255d03155598937e4f3e28afd86"
CURRENT_PIN_DATE = "2026-06-16"
CURRENT_PIN_SUBJECT = "update to 12.8.1 (6916)"

LEGACY_PIN = "009e97356f966bb81eceba113d210230bf383122"


def repo_root() -> Path:
    env = os.environ.get("NATIVEDO_ROOT")
    if env:
        return Path(env).resolve()
    return Path(__file__).resolve().parent.parent


def _as_path(root: Optional[PathLike] = None) -> Path:
    if root is None:
        return repo_root()
    return root if isinstance(root, Path) else Path(root)


def manifest_path(root: Optional[PathLike] = None) -> Path:
    return _as_path(root) / "vendor-manifest.json"


def vendor_dir(root: Optional[PathLike] = None) -> Path:
    return _as_path(root) / "vendor" / "telegram-android"


def load_manifest(root: Optional[PathLike] = None) -> dict[str, Any]:
    path = manifest_path(root)
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def save_manifest(data: dict[str, Any], root: Optional[PathLike] = None) -> None:
    path = manifest_path(root)
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    path.write_text(text, encoding="utf-8")


def normalize_sha(sha: Optional[str]) -> Optional[str]:
    if sha is None:
        return None
    s = str(sha).strip()
    if not s or s == "null":
        return None
    if s in PIN_ALIASES:
        return PIN_ALIASES[s]
    if len(s) < 40:
        full = git_rev_parse(s)
        if full:
            return full
    return s


def git_rev_parse(ref: str, cwd: Optional[PathLike] = None) -> Optional[str]:
    vd = _as_path(cwd) if cwd is not None else vendor_dir()
    try:
        out = subprocess.check_output(
            ["git", "-C", str(vd), "rev-parse", ref],
            stderr=subprocess.DEVNULL,
            text=True,
        ).strip()
        return out or None
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def git_show(commit: str, path: str, cwd: Optional[PathLike] = None) -> Optional[bytes]:
    vd = _as_path(cwd) if cwd is not None else vendor_dir()
    try:
        return subprocess.check_output(
            ["git", "-C", str(vd), "show", f"{commit}:{path}"],
            stderr=subprocess.DEVNULL,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def git_log_paths(
    old: str,
    new: str,
    paths: Iterable[str],
    cwd: Optional[PathLike] = None,
) -> list[str]:
    """Return subset of paths touched between old..new (name-only)."""
    vd = _as_path(cwd) if cwd is not None else vendor_dir()
    path_list = list(paths)
    if not path_list:
        return []
    try:
        out = subprocess.check_output(
            [
                "git",
                "-C",
                str(vd),
                "log",
                "--name-only",
                "--pretty=format:",
                f"{old}..{new}",
                "--",
                *path_list,
            ],
            stderr=subprocess.DEVNULL,
            text=True,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return []
    touched = {line.strip() for line in out.splitlines() if line.strip()}
    return [p for p in path_list if p in touched]


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def upstream_git_path(entry: dict[str, Any], manifest: dict[str, Any]) -> Optional[str]:
    """Map a file entry to path inside the Telegram git tree."""
    upstream = entry.get("upstream")
    if not upstream:
        return None
    # Manifest convention: upstream is under upstream_root (TMessagesProj/src/main/java)
    # and stored without that prefix, e.g. org/telegram/ui/Foo.java
    root = manifest.get("upstream_root", "TMessagesProj/src/main/java").rstrip("/")
    u = str(upstream).lstrip("/")
    if u.startswith("TMessagesProj/"):
        return u
    if u.startswith(("org/", "com/", "me/", "androidx/")):
        return f"{root}/{u}"
    return f"{root}/{u}"


def local_abs(
    entry: dict[str, Any],
    manifest: dict[str, Any],
    root: Optional[PathLike] = None,
) -> Path:
    r = _as_path(root)
    local_root = manifest.get("local_root", "apps/android/")
    return r / local_root / entry["local"]


def compare_to_upstream(
    entry: dict[str, Any],
    manifest: dict[str, Any],
    root: Optional[PathLike] = None,
) -> dict[str, Any]:
    """Compare local file bytes to git show of vendored_at:upstream_path."""
    result: dict[str, Any] = {
        "local": entry.get("local"),
        "exists": False,
        "upstream_available": False,
        "byte_equal": None,
        "declared_modified": bool(entry.get("modified")),
        "modified_mismatch": False,
        "error": None,
    }
    path = local_abs(entry, manifest, root)
    if not path.is_file():
        result["error"] = "missing_on_disk"
        return result
    result["exists"] = True
    local_bytes = path.read_bytes()
    result["local_sha256"] = sha256_bytes(local_bytes)

    vendored_at = normalize_sha(entry.get("vendored_at"))
    up_path = upstream_git_path(entry, manifest)
    if not vendored_at or not up_path:
        result["error"] = "no_upstream_ref"
        return result

    remote = git_show(vendored_at, up_path)
    if remote is None:
        result["error"] = "upstream_blob_missing"
        return result
    result["upstream_available"] = True
    result["upstream_sha256"] = sha256_bytes(remote)
    equal = local_bytes == remote
    result["byte_equal"] = equal
    if entry.get("modified") is False and not equal:
        result["modified_mismatch"] = True
        result["error"] = "declared_clean_but_differs"
    elif entry.get("modified") is True and equal:
        result["modified_mismatch"] = True
        result["error"] = "declared_modified_but_identical"
    return result


@dataclass
class StatusReport:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    info: list[str] = field(default_factory=list)
    stats: dict[str, Any] = field(default_factory=dict)

    def add_error(self, msg: str) -> None:
        self.errors.append(msg)

    def add_warning(self, msg: str) -> None:
        self.warnings.append(msg)

    def add_info(self, msg: str) -> None:
        self.info.append(msg)


def collect_on_disk_telegram_java(root: Optional[PathLike] = None) -> list[str]:
    r = _as_path(root)
    base = r / "apps" / "android" / "telegram" / "src" / "main" / "java"
    if not base.is_dir():
        return []
    out = []
    for p in base.rglob("*.java"):
        rel = p.relative_to(r / "apps" / "android").as_posix()
        out.append(rel)
    return sorted(out)


def run_isolation_check(root: Optional[PathLike] = None) -> tuple[int, str]:
    r = _as_path(root)
    script = r / "scripts" / "vendor-isolation-check.sh"
    if not script.is_file():
        return 0, "isolation script missing"
    try:
        proc = subprocess.run(
            ["bash", str(script)],
            cwd=str(r),
            capture_output=True,
            text=True,
        )
        return proc.returncode, (proc.stdout + proc.stderr).strip()
    except Exception as e:  # noqa: BLE001
        return 1, str(e)


def upsert_file_entry(
    manifest: dict[str, Any],
    *,
    local: str,
    upstream: str,
    vendored_at: str,
    modified: bool = False,
    port_mode: str = "exact",
    notes: Optional[str] = None,
    forced_exact: Optional[list[str]] = None,
) -> str:
    """Insert or update a files[] entry. Returns 'inserted' | 'updated' | 'unchanged'."""
    files = manifest.setdefault("files", [])
    for entry in files:
        if entry.get("local") == local:
            changed = False
            for k, v in {
                "upstream": upstream,
                "vendored_at": vendored_at,
                "modified": modified,
                "port_mode": port_mode,
            }.items():
                if entry.get(k) != v:
                    entry[k] = v
                    changed = True
            if notes is not None and entry.get("notes") != notes:
                entry["notes"] = notes
                changed = True
            if forced_exact is not None and entry.get("forced_exact") != forced_exact:
                entry["forced_exact"] = forced_exact
                changed = True
            return "updated" if changed else "unchanged"
    new_entry: dict[str, Any] = {
        "local": local,
        "upstream": upstream,
        "vendored_at": vendored_at,
        "modified": modified,
        "port_mode": port_mode,
    }
    if forced_exact:
        new_entry["forced_exact"] = forced_exact
    if notes:
        new_entry["notes"] = notes
    files.append(new_entry)
    return "inserted"


def normalize_manifest_pins(
    manifest: dict[str, Any],
    *,
    set_global_pin: Optional[str] = None,
    set_global_date: Optional[str] = None,
    fill_null_with: Optional[str] = None,
) -> dict[str, int]:
    """Expand short SHAs; optionally set global pin and fill null vendored_at."""
    counts = {
        "files_normalized": 0,
        "files_null_filled": 0,
        "resources_normalized": 0,
        "global_pin_set": 0,
    }
    if set_global_pin:
        up = manifest.setdefault("upstream", {})
        if up.get("pinned_commit") != set_global_pin:
            up["pinned_commit"] = set_global_pin
            counts["global_pin_set"] = 1
        if set_global_date:
            up["pinned_date"] = set_global_date

    for entry in manifest.get("files", []):
        raw = entry.get("vendored_at")
        if raw is None or raw == "" or raw == "null":
            if fill_null_with:
                entry["vendored_at"] = fill_null_with
                counts["files_null_filled"] += 1
            continue
        norm = normalize_sha(raw)
        if norm and norm != raw:
            entry["vendored_at"] = norm
            counts["files_normalized"] += 1

    for res in manifest.get("resources", []) or []:
        raw = res.get("vendored_at")
        if raw is None:
            if fill_null_with:
                res["vendored_at"] = fill_null_with
                counts["resources_normalized"] += 1
            continue
        norm = normalize_sha(raw)
        if norm and norm != raw:
            res["vendored_at"] = norm
            counts["resources_normalized"] += 1

    return counts


def eprint(*args: object) -> None:
    print(*args, file=sys.stderr)

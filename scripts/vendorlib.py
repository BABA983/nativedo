#!/usr/bin/env python3
"""Shared helpers for Telegram vendoring tooling.

Manifest source: vendor-manifest.json at repo root.
Generated flat lock: vendor-manifest.lock.json.
Pristine upstream tree: vendor/telegram-android/ (gitignored).
Local copies: apps/android/ relative paths in each file entry.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from collections import Counter
from contextlib import contextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Optional, Union

import fcntl

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

SOURCE_SCHEMA_VERSION = 1
SOURCE_MANIFEST_NAME = "vendor-manifest.json"
LOCK_MANIFEST_NAME = "vendor-manifest.lock.json"
SOURCE_DIGEST_KEY = "_vendor_source_digest"

TELEGRAM_JAVA_PREFIX = "telegram/src/main/java/"
APP_JAVA_PREFIX = "app/src/main/java/"

FILE_ENTRY_KEYS = {
    "_comment",
    "local",
    "upstream",
    "vendored_at",
    "modified",
    "port_mode",
    "forced_exact",
    "notes",
    "stuck_reason",
}
COMPACT_ENTRY_KEYS = {
    "path",
    "upstream",
    "pin",
    "modified",
    "port_mode",
    "forced_exact",
    "notes",
    "notes_ref",
    "stuck_reason",
    "stuck_ref",
}
GROUP_KEYS = {"comment", "local_prefix", "upstream_prefix", "entries"}
FORCED_EXACT_VALUES = {
    "gesture",
    "animation",
    "measurement",
    "drawing",
    "recycling",
}

NATIVE_HOST_STUCK = (
    "Native host shim/port under :telegram; diverged from Telegram semantics. "
    "vendored_at is historical reference only — not eligible for vendor-ff; "
    "re-sync only when product requires."
)
NATIVE_APP_STUCK = (
    "NativeDo port (package io.github.baba983.nativedo.*); vendored_at is an "
    "upstream reference for forced_exact hand-checks only — not eligible for vendor-ff."
)


class ManifestError(RuntimeError):
    """Invalid source schema or a stale generated lock."""


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
    """Path to the compact, human-owned manifest source."""
    return _as_path(root) / SOURCE_MANIFEST_NAME


def manifest_lock_path(root: Optional[PathLike] = None) -> Path:
    """Path to the generated flat manifest consumed by legacy tooling."""
    return _as_path(root) / LOCK_MANIFEST_NAME


def vendor_dir(root: Optional[PathLike] = None) -> Path:
    return _as_path(root) / "vendor" / "telegram-android"


def _read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ManifestError(f"{path.name}: top level must be an object")
    return data


def _json_text(data: dict[str, Any]) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def _source_digest(data: dict[str, Any]) -> str:
    return hashlib.sha256(_json_text(data).encode("utf-8")).hexdigest()


def _atomic_write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = _json_text(data)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        dir=path.parent,
        prefix=f".{path.name}.",
        suffix=".tmp",
        delete=False,
    ) as tmp:
        tmp.write(text)
        tmp.flush()
        os.fsync(tmp.fileno())
        tmp_path = Path(tmp.name)
    os.replace(tmp_path, path)


@contextmanager
def _manifest_write_lock(root: Optional[PathLike] = None):
    path = _as_path(root) / ".vendor-manifest.write.lock"
    with path.open("a+", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


def load_source_manifest(root: Optional[PathLike] = None) -> dict[str, Any]:
    source = _read_json(manifest_path(root))
    if source.get("schema_version") != SOURCE_SCHEMA_VERSION:
        raise ManifestError(
            f"{SOURCE_MANIFEST_NAME}: expected schema_version={SOURCE_SCHEMA_VERSION}; "
            "run scripts/vendor-manifest.sh --bootstrap for a legacy flat manifest"
        )
    return source


def _safe_rel_path(value: str, field_name: str) -> str:
    path = str(value or "")
    if not path or path.startswith("/") or ".." in Path(path).parts:
        raise ManifestError(f"invalid {field_name}: {value!r}")
    return path


def _resolve_alias(
    aliases: dict[str, Any],
    category: str,
    name: Optional[str],
    *,
    field_name: str,
) -> Optional[str]:
    if name is None:
        return None
    values = aliases.get(category) or {}
    value = values.get(name)
    if value is None:
        raise ManifestError(f"unknown {field_name} alias: {name!r}")
    if not isinstance(value, str):
        raise ManifestError(f"{field_name} alias {name!r} must resolve to a string")
    return value


def _expand_groups(
    source: dict[str, Any],
    *,
    group_key: str,
    kind: str,
) -> list[dict[str, Any]]:
    aliases = source.get("aliases") or {}
    defaults = source.get("defaults") or {}
    pin = (source.get("upstream") or {}).get("pinned_commit")
    default_modified = (
        True if kind == "compat_shims" else defaults.get("modified", False)
    )
    default_mode = (
        "native" if kind == "compat_shims" else defaults.get("port_mode", "exact")
    )
    out: list[dict[str, Any]] = []

    groups = source.get(group_key) or []
    if not isinstance(groups, list):
        raise ManifestError(f"{group_key} must be an array")
    for group in groups:
        if not isinstance(group, dict):
            raise ManifestError(f"{group_key} entries must be objects")
        unknown_group = set(group) - GROUP_KEYS
        if unknown_group:
            raise ManifestError(
                f"{group_key}: unsupported group fields {sorted(unknown_group)}"
            )
        local_prefix = str(group.get("local_prefix") or "")
        upstream_prefix = group.get("upstream_prefix")
        entries = group.get("entries") or []
        if not isinstance(entries, list) or not entries:
            raise ManifestError(f"{group_key} group must contain entries")

        for index, compact in enumerate(entries):
            if not isinstance(compact, dict):
                raise ManifestError(f"{group_key}.entries values must be objects")
            unknown_entry = set(compact) - COMPACT_ENTRY_KEYS
            if unknown_entry:
                raise ManifestError(
                    f"{group_key}: unsupported entry fields {sorted(unknown_entry)}"
                )
            path = _safe_rel_path(compact.get("path"), "path")
            local = _safe_rel_path(f"{local_prefix}{path}", "local")
            upstream = compact.get("upstream")
            if upstream is None:
                if upstream_prefix is None:
                    raise ManifestError(f"{local}: explicit upstream is required")
                upstream = f"{upstream_prefix}{path}"
            upstream = _safe_rel_path(str(upstream), "upstream")

            pin_alias = compact.get("pin")
            vendored_at = (
                _resolve_alias(aliases, "pins", pin_alias, field_name="pin")
                if pin_alias is not None
                else defaults.get("vendored_at", pin)
            )
            if vendored_at == "$upstream":
                vendored_at = pin
            if not vendored_at:
                raise ManifestError(f"{local}: vendored_at is required")

            entry: dict[str, Any] = {
                "local": local,
                "upstream": upstream,
                "vendored_at": vendored_at,
                "modified": compact.get("modified", default_modified),
                "port_mode": compact.get("port_mode", default_mode),
            }
            if index == 0 and group.get("comment"):
                entry = {"_comment": group["comment"], **entry}
            forced_exact = compact.get("forced_exact")
            if forced_exact:
                if not isinstance(forced_exact, list) or not set(forced_exact) <= FORCED_EXACT_VALUES:
                    raise ManifestError(f"{local}: invalid forced_exact")
                entry["forced_exact"] = forced_exact

            notes = compact.get("notes")
            if compact.get("notes_ref") is not None:
                if notes is not None:
                    raise ManifestError(f"{local}: use notes or notes_ref, not both")
                notes = _resolve_alias(
                    aliases,
                    "notes",
                    compact.get("notes_ref"),
                    field_name="notes_ref",
                )
            if notes is not None and not isinstance(notes, str):
                raise ManifestError(f"{local}: notes must be a string")
            if notes:
                entry["notes"] = notes

            stuck = compact.get("stuck_reason")
            if compact.get("stuck_ref") is not None:
                if stuck is not None:
                    raise ManifestError(f"{local}: use stuck_reason or stuck_ref, not both")
                stuck = _resolve_alias(
                    aliases,
                    "stuck_reasons",
                    compact.get("stuck_ref"),
                    field_name="stuck_ref",
                )
            if stuck:
                entry["stuck_reason"] = stuck
            out.append(entry)
    return out


def expand_source_manifest(source: dict[str, Any]) -> dict[str, Any]:
    if source.get("schema_version") != SOURCE_SCHEMA_VERSION:
        raise ManifestError(
            f"source schema_version must be {SOURCE_SCHEMA_VERSION}"
        )
    upstream = source.get("upstream")
    if not isinstance(upstream, dict) or not upstream.get("pinned_commit"):
        raise ManifestError("upstream.pinned_commit is required")

    resources = source.get("resources") or []
    if not isinstance(resources, list):
        raise ManifestError("resources must be an array")
    for index, resource in enumerate(resources):
        if not isinstance(resource, dict):
            raise ManifestError(f"resources[{index}] must be an object")
        if not resource.get("kind"):
            raise ManifestError(f"resources[{index}].kind is required")
        if not resource.get("vendored_at"):
            raise ManifestError(f"resources[{index}].vendored_at is required")

    manifest: dict[str, Any] = {
        "_comment": [
            "GENERATED FILE — edit vendor-manifest.json, then run scripts/vendor-manifest.sh --write.",
            "Flat per-file provenance is retained here for tooling and audits.",
            "Do not edit this lock directly.",
        ],
        "upstream": upstream,
        "local_root": source.get("local_root", "apps/android/"),
        "upstream_root": source.get(
            "upstream_root", "TMessagesProj/src/main/java"
        ),
        "files": _expand_groups(source, group_key="file_groups", kind="files"),
        "compat_shims": _expand_groups(
            source,
            group_key="compat_shim_groups",
            kind="compat_shims",
        ),
        "resources": resources,
    }

    seen: dict[str, str] = {}
    for kind in ("files", "compat_shims"):
        for entry in manifest[kind]:
            local = entry["local"]
            previous = seen.get(local)
            if previous:
                raise ManifestError(f"duplicate local {local!r} in {previous} and {kind}")
            seen[local] = kind
            if entry.get("port_mode") not in ("exact", "native"):
                raise ManifestError(f"{local}: invalid port_mode")
            if not isinstance(entry.get("modified"), bool):
                raise ManifestError(f"{local}: modified must be boolean")
            if kind == "compat_shims" and (
                entry.get("port_mode") != "native" or entry.get("modified") is not True
            ):
                raise ManifestError(
                    f"{local}: compat shim must be native and modified=true"
                )
    return manifest


def _normalized_note(value: Any) -> Optional[str]:
    if value is None:
        return None
    if isinstance(value, list):
        return " ".join(str(part).strip() for part in value if str(part).strip())
    text = str(value).strip()
    return text or None


def _alias_name(prefix: str, value: str) -> str:
    return f"{prefix}_{hashlib.sha256(value.encode('utf-8')).hexdigest()[:8]}"


def _unique_alias_name(preferred: str, existing: dict[str, str], value: str) -> str:
    name = preferred
    suffix = 2
    while name in existing and existing[name] != value:
        name = f"{preferred}_{suffix}"
        suffix += 1
    return name


def _build_aliases(manifest: dict[str, Any]) -> tuple[dict[str, Any], dict[str, str], dict[str, str], dict[str, str]]:
    all_entries = list(manifest.get("files") or []) + list(
        manifest.get("compat_shims") or []
    )
    global_pin = str((manifest.get("upstream") or {}).get("pinned_commit") or "")

    pins: dict[str, str] = {}
    pin_lookup: dict[str, str] = {}
    for value in dict.fromkeys(str(e.get("vendored_at") or "") for e in all_entries):
        if not value or value == global_pin:
            continue
        preferred = "legacy_009e97356" if value == LEGACY_PIN else f"pin_{value[:12]}"
        name = _unique_alias_name(preferred, pins, value)
        pins[name] = value
        pin_lookup[value] = name

    stuck_values = [
        str(e["stuck_reason"])
        for e in all_entries
        if e.get("stuck_reason")
    ]
    stuck_counts = Counter(stuck_values)
    stuck_aliases: dict[str, str] = {}
    stuck_lookup: dict[str, str] = {}
    for value in dict.fromkeys(stuck_values):
        if stuck_counts[value] < 2:
            continue
        if value == NATIVE_HOST_STUCK:
            preferred = "native_host_reference_only"
        elif value == NATIVE_APP_STUCK:
            preferred = "native_app_reference_only"
        else:
            preferred = _alias_name("stuck", value)
        name = _unique_alias_name(preferred, stuck_aliases, value)
        stuck_aliases[name] = value
        stuck_lookup[value] = name

    note_values = [
        note
        for e in all_entries
        if (note := _normalized_note(e.get("notes"))) is not None
    ]
    note_counts = Counter(note_values)
    note_aliases: dict[str, str] = {}
    note_lookup: dict[str, str] = {}
    for value in dict.fromkeys(note_values):
        if note_counts[value] < 2:
            continue
        auto_dependency = re.match(
            r"^Auto-registered unmanifested compile dependency \((\d+) LOC\)\.",
            value,
        )
        auto_source = re.match(
            r"^Auto-registered unmanifested on-disk source \((\d+) LOC\)\.",
            value,
        )
        if value == "auto-vendor-copy: registered from javac missing-symbol":
            preferred = "auto_vendor_copy"
        elif auto_dependency:
            preferred = (
                f"auto_registered_compile_dependency_{auto_dependency.group(1)}_loc"
            )
        elif auto_source:
            preferred = f"auto_registered_on_disk_source_{auto_source.group(1)}_loc"
        else:
            if note_counts[value] < 3:
                continue
            preferred = _alias_name("note", value)
        name = _unique_alias_name(preferred, note_aliases, value)
        note_aliases[name] = value
        note_lookup[value] = name

    aliases: dict[str, Any] = {}
    if pins:
        aliases["pins"] = pins
    if stuck_aliases:
        aliases["stuck_reasons"] = stuck_aliases
    if note_aliases:
        aliases["notes"] = note_aliases
    return aliases, pin_lookup, stuck_lookup, note_lookup


def _entry_prefix(local: str) -> tuple[str, Optional[str]]:
    if local.startswith(TELEGRAM_JAVA_PREFIX):
        return TELEGRAM_JAVA_PREFIX, ""
    if local.startswith(APP_JAVA_PREFIX):
        return APP_JAVA_PREFIX, None
    return "", None


def _compact_groups(
    entries: list[dict[str, Any]],
    *,
    global_pin: str,
    pin_lookup: dict[str, str],
    stuck_lookup: dict[str, str],
    note_lookup: dict[str, str],
    kind: str,
) -> list[dict[str, Any]]:
    groups: list[dict[str, Any]] = []
    current: Optional[dict[str, Any]] = None
    default_modified = kind == "compat_shims"
    default_mode = "native" if kind == "compat_shims" else "exact"

    for raw in entries:
        unknown = set(raw) - FILE_ENTRY_KEYS
        if unknown:
            raise ManifestError(
                f"{raw.get('local')}: unsupported flat fields {sorted(unknown)}"
            )
        local = _safe_rel_path(raw.get("local"), "local")
        local_prefix, upstream_prefix = _entry_prefix(local)
        comment = raw.get("_comment")
        if (
            current is None
            or current.get("local_prefix") != local_prefix
            or comment is not None
        ):
            current = {"local_prefix": local_prefix}
            if upstream_prefix is not None:
                current["upstream_prefix"] = upstream_prefix
            if comment:
                current["comment"] = comment
            current["entries"] = []
            groups.append(current)

        path = local[len(local_prefix) :] if local_prefix else local
        compact: dict[str, Any] = {"path": path}
        upstream = _safe_rel_path(raw.get("upstream"), "upstream")
        if upstream_prefix is None or upstream != f"{upstream_prefix}{path}":
            compact["upstream"] = upstream

        vendored_at = str(raw.get("vendored_at") or "")
        if vendored_at != global_pin:
            alias = pin_lookup.get(vendored_at)
            if not alias:
                raise ManifestError(f"{local}: missing pin alias for {vendored_at}")
            compact["pin"] = alias
        if raw.get("modified") is not default_modified:
            compact["modified"] = raw.get("modified")
        if raw.get("port_mode") != default_mode:
            compact["port_mode"] = raw.get("port_mode")
        if raw.get("forced_exact"):
            compact["forced_exact"] = raw["forced_exact"]

        note = _normalized_note(raw.get("notes"))
        if note:
            note_ref = note_lookup.get(note)
            if note_ref:
                compact["notes_ref"] = note_ref
            else:
                compact["notes"] = note

        stuck = raw.get("stuck_reason")
        if stuck and kind != "compat_shims":
            stuck_ref = stuck_lookup.get(str(stuck))
            if stuck_ref:
                compact["stuck_ref"] = stuck_ref
            else:
                compact["stuck_reason"] = stuck
        current["entries"].append(compact)
    return groups


def compact_manifest(
    manifest: dict[str, Any],
    *,
    compat_shim_locals: Optional[set[str]] = None,
) -> dict[str, Any]:
    """Convert a flat lock into the compact human-owned source schema."""
    files = list(manifest.get("files") or [])
    shims = list(manifest.get("compat_shims") or [])
    if compat_shim_locals:
        kept = []
        for entry in files:
            if entry.get("local") in compat_shim_locals:
                shims.append(entry)
            else:
                kept.append(entry)
        files = kept
        found = {e.get("local") for e in shims}
        missing = sorted(compat_shim_locals - found)
        if missing:
            raise ManifestError(f"compat shim paths not found: {missing}")

    flat_for_aliases = {**manifest, "files": files, "compat_shims": shims}
    aliases, pin_lookup, stuck_lookup, note_lookup = _build_aliases(flat_for_aliases)
    global_pin = str((manifest.get("upstream") or {}).get("pinned_commit") or "")
    if not global_pin:
        raise ManifestError("upstream.pinned_commit is required")

    return {
        "schema_version": SOURCE_SCHEMA_VERSION,
        "_comment": [
            "Human-owned compact vendoring manifest.",
            "Run scripts/vendor-manifest.sh --write after edits.",
            "vendor-manifest.lock.json is generated; do not edit it directly.",
        ],
        "upstream": manifest.get("upstream") or {},
        "local_root": manifest.get("local_root", "apps/android/"),
        "upstream_root": manifest.get(
            "upstream_root", "TMessagesProj/src/main/java"
        ),
        "defaults": {
            "vendored_at": "$upstream",
            "modified": False,
            "port_mode": "exact",
        },
        "aliases": aliases,
        "file_groups": _compact_groups(
            files,
            global_pin=global_pin,
            pin_lookup=pin_lookup,
            stuck_lookup=stuck_lookup,
            note_lookup=note_lookup,
            kind="files",
        ),
        "compat_shim_groups": _compact_groups(
            shims,
            global_pin=global_pin,
            pin_lookup=pin_lookup,
            stuck_lookup=stuck_lookup,
            note_lookup=note_lookup,
            kind="compat_shims",
        ),
        "resources": manifest.get("resources") or [],
    }


def check_manifest_lock(root: Optional[PathLike] = None) -> tuple[bool, str]:
    source = load_source_manifest(root)
    expected = expand_source_manifest(source)
    path = manifest_lock_path(root)
    if not path.is_file():
        return False, f"{LOCK_MANIFEST_NAME} is missing"
    actual = _read_json(path)
    if actual != expected:
        return (
            False,
            f"{LOCK_MANIFEST_NAME} is stale; run scripts/vendor-manifest.sh --write",
        )
    return True, "manifest lock is current"


def regenerate_manifest_lock(root: Optional[PathLike] = None) -> dict[str, Any]:
    with _manifest_write_lock(root):
        source = load_source_manifest(root)
        expanded = expand_source_manifest(source)
        _atomic_write_json(manifest_lock_path(root), expanded)
        return expanded


def bootstrap_manifest_source(
    root: Optional[PathLike] = None,
    *,
    compat_shim_locals: Optional[set[str]] = None,
) -> dict[str, Any]:
    path = manifest_path(root)
    legacy = _read_json(path)
    if legacy.get("schema_version") == SOURCE_SCHEMA_VERSION:
        raise ManifestError(f"{SOURCE_MANIFEST_NAME} is already compact")
    source = compact_manifest(legacy, compat_shim_locals=compat_shim_locals)
    with _manifest_write_lock(root):
        _atomic_write_json(path, source)
        _atomic_write_json(manifest_lock_path(root), expand_source_manifest(source))
    return source


def load_manifest(root: Optional[PathLike] = None) -> dict[str, Any]:
    source = load_source_manifest(root)
    expanded = expand_source_manifest(source)
    lock = manifest_lock_path(root)
    if not lock.is_file() or _read_json(lock) != expanded:
        raise ManifestError(
            f"{LOCK_MANIFEST_NAME} is stale; run scripts/vendor-manifest.sh --write"
        )
    expanded[SOURCE_DIGEST_KEY] = _source_digest(source)
    return expanded


def save_manifest(data: dict[str, Any], root: Optional[PathLike] = None) -> None:
    """Persist a mutated flat manifest through the compact source and lock."""
    with _manifest_write_lock(root):
        expected_digest = data.get(SOURCE_DIGEST_KEY)
        if expected_digest is not None:
            current_source = load_source_manifest(root)
            if _source_digest(current_source) != expected_digest:
                raise ManifestError(
                    f"{SOURCE_MANIFEST_NAME} changed after it was loaded; "
                    "reload and retry instead of overwriting another writer"
                )
        flat = dict(data)
        flat.pop(SOURCE_DIGEST_KEY, None)
        source = compact_manifest(flat)
        expanded = expand_source_manifest(source)
        _atomic_write_json(manifest_path(root), source)
        _atomic_write_json(manifest_lock_path(root), expanded)


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
        "compat_shims_normalized": 0,
        "compat_shims_null_filled": 0,
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

    for entry in manifest.get("compat_shims", []):
        raw = entry.get("vendored_at")
        if raw is None or raw == "" or raw == "null":
            if fill_null_with:
                entry["vendored_at"] = fill_null_with
                counts["compat_shims_null_filled"] += 1
            continue
        norm = normalize_sha(raw)
        if norm and norm != raw:
            entry["vendored_at"] = norm
            counts["compat_shims_normalized"] += 1

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

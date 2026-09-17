#!/usr/bin/env python3
"""Shared helpers for Telegram vendoring tooling.

Human-owned overlay: vendor-overlay.json at repo root.
Derived provenance index: built on demand from the overlay + pristine tree +
the files on disk (scripts/vendor-index.sh). There is no committed lock file and
no per-file `modified` flag: both are computed here.

Pristine upstream tree: a separate checkout outside the repo (gitignored,
default `../telegram`, named after the upstream repo); resolve it with
vendor_dir().
Local copies: paths relative to `local_root` (`apps/android/`).
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Optional, Union

PathLike = Union[Path, str]

OVERLAY_NAME = "vendor-overlay.json"
OVERLAY_SCHEMA_VERSION = 1

DEFAULT_UPSTREAM_ROOT = "TMessagesProj/src/main/java"
DEFAULT_LOCAL_ROOT = "apps/android/"
# The module directory mirrors the upstream project name, so a vendored file's
# local path equals its upstream path (modulo the `local_root` prefix). That is
# why this equals DEFAULT_UPSTREAM_ROOT: provenance for a copy is its path.
TELEGRAM_JAVA_PREFIX = "TMessagesProj/src/main/java/"
APP_JAVA_PREFIX = "app/src/main/java/"
SOURCE_SUFFIXES = (".java", ".kt")

# Reporting buckets only: third-party trees carried along with Telegram's
# source (patched AndroidX, ExoPlayer/GMS shims, tiny utilities).
THIRD_PARTY_PREFIXES = (
    "androidx/",
    "com/google/",
    "com/carrotsearch/",
)

FORCED_EXACT_VALUES = {
    "gesture",
    "animation",
    "measurement",
    "drawing",
    "recycling",
}

# Known historical pins; short prefixes are expanded through the pristine tree.
PIN_ALIASES = {
    "009e97356": "009e97356f966bb81eceba113d210230bf383122",
    "009e97356f966bb81eceba113d210230bf383122": "009e97356f966bb81eceba113d210230bf383122",
    "9b50143d": "9b50143d8896d255d03155598937e4f3e28afd86",
    "9b50143d8896d255d03155598937e4f3e28afd86": "9b50143d8896d255d03155598937e4f3e28afd86",
    "9bcf3d27": "9bcf3d2769c6d3f07105a992e5d9493e33ac3348",
    "9bcf3d2769c6d3f07105a992e5d9493e33ac3348": "9bcf3d2769c6d3f07105a992e5d9493e33ac3348",
}

# Pristine Telegram checkout location. It is intentionally outside the repo:
# override with the env var, otherwise `upstream.local_checkout` (default
# `../telegram`, the upstream repo name) applies; the legacy in-repo path is
# still detected.
VENDOR_DIR_ENV = "NATIVEDO_TELEGRAM_VENDOR_DIR"
DEFAULT_VENDOR_DIRNAME = "telegram"
LEGACY_VENDOR_DIR_PARTS = ("vendor", "telegram-android")


class ManifestError(RuntimeError):
    """Invalid overlay or an index that cannot be derived."""


def repo_root() -> Path:
    env = os.environ.get("NATIVEDO_ROOT")
    if env:
        return Path(env).resolve()
    return Path(__file__).resolve().parent.parent


def _as_path(root: Optional[PathLike] = None) -> Path:
    if root is None:
        return repo_root()
    return root if isinstance(root, Path) else Path(root)


def overlay_path(root: Optional[PathLike] = None) -> Path:
    """Path to the human-owned overlay."""
    return _as_path(root) / OVERLAY_NAME


def default_vendor_dir(root: Optional[PathLike] = None) -> Path:
    """Default pristine checkout: a sibling of the repo, `../telegram`."""
    sibling = _as_path(root).parent / DEFAULT_VENDOR_DIRNAME
    return Path(os.path.normpath(sibling))


def legacy_vendor_dir(root: Optional[PathLike] = None) -> Path:
    """Pre-move in-repo checkout: `<repo>/vendor/telegram-android`."""
    return _as_path(root).joinpath(*LEGACY_VENDOR_DIR_PARTS)


def _manifest_local_checkout(root: Optional[PathLike] = None) -> Optional[Path]:
    """`upstream.local_checkout` from the overlay, when declared.

    Relative values are resolved against the repo root; absolute values are
    taken as-is. Never raises: a malformed overlay is reported elsewhere.
    """
    try:
        source = _read_json(overlay_path(root))
    except (OSError, json.JSONDecodeError):
        return None
    value = (source.get("upstream") or {}).get("local_checkout")
    if not isinstance(value, str) or not value.strip():
        return None
    candidate = Path(value).expanduser()
    if candidate.is_absolute():
        return candidate
    return Path(os.path.normpath(_as_path(root) / candidate))


def _case_insensitive_sibling(path: Path) -> Optional[Path]:
    """The on-disk entry for `path`, matched ignoring case.

    `git clone https://github.com/DrKLO/Telegram.git` creates `Telegram/`, so a
    case-sensitive filesystem would otherwise miss an existing checkout named
    after the upstream repo.

    The parent directory is always scanned instead of trusting
    `path.exists()`: on a case-insensitive filesystem (macOS default) that
    check succeeds for `telegram` while only `Telegram` is on disk, and the
    resolved path would then carry a casing no directory has — which makes
    behaviour differ between platforms. Scanning returns the real entry, so
    both filesystems resolve to the same absolute path.
    """
    parent = path.parent
    if not parent.is_dir():
        return None
    wanted = path.name
    folded = wanted.casefold()
    fallback: Optional[Path] = None
    for candidate in sorted(parent.iterdir()):
        if candidate.name == wanted:
            return candidate
        if fallback is None and candidate.name.casefold() == folded:
            fallback = candidate
    return fallback


def vendor_dir(root: Optional[PathLike] = None) -> Path:
    """Resolve the pristine Telegram checkout used as the upstream reference.

    The tree lives outside the repo (it is large and never committed).
    Resolution order:

    1. `NATIVEDO_TELEGRAM_VENDOR_DIR` — explicit override, authoritative even
       when it does not exist yet (vendor-setup clones there).
    2. `upstream.local_checkout` in the overlay, else the sibling default
       `../telegram` (the upstream repo name, case-insensitively matched so a
       `git clone` of `DrKLO/Telegram` resolves too).
    3. The legacy in-repo `vendor/telegram-android` when the configured path is
       absent, so existing checkouts keep working until they are moved.
    """
    r = _as_path(root)
    env = os.environ.get(VENDOR_DIR_ENV, "").strip()
    if env:
        return Path(env).expanduser()
    configured = _manifest_local_checkout(r) or default_vendor_dir(r)
    sibling = _case_insensitive_sibling(configured)
    if sibling is not None:
        return sibling
    legacy = legacy_vendor_dir(r)
    if legacy.exists():
        return legacy
    return configured


def display_path(path: PathLike, root: Optional[PathLike] = None) -> str:
    """Repo-relative path when inside the repo, else the absolute path."""
    p = Path(path)
    try:
        return p.relative_to(_as_path(root)).as_posix()
    except ValueError:
        return str(p)


def _read_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ManifestError(f"{path.name}: top level must be an object")
    return data


# ---------------------------------------------------------------------------
# Overlay (human-owned)
# ---------------------------------------------------------------------------


def _require_str_list(value: Any, field_name: str) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list) or not all(isinstance(v, str) for v in value):
        raise ManifestError(f"{field_name} must be an array of strings")
    out = []
    for item in value:
        rel = str(item).strip().lstrip("/")
        if not rel:
            raise ManifestError(f"{field_name}: empty path")
        out.append(rel)
    if len(set(out)) != len(out):
        raise ManifestError(f"{field_name}: duplicate path")
    return out


def load_overlay(root: Optional[PathLike] = None) -> dict[str, Any]:
    """Load and validate the human-owned overlay."""
    path = overlay_path(root)
    if not path.is_file():
        raise ManifestError(f"{OVERLAY_NAME} missing at {path}")
    data = _read_json(path)
    if data.get("schema_version") != OVERLAY_SCHEMA_VERSION:
        raise ManifestError(
            f"{OVERLAY_NAME}: expected schema_version={OVERLAY_SCHEMA_VERSION}"
        )
    upstream = data.get("upstream")
    if not isinstance(upstream, dict) or not upstream.get("pinned_commit"):
        raise ManifestError(f"{OVERLAY_NAME}: upstream.pinned_commit is required")

    data["native"] = _require_str_list(data.get("native"), "native")

    ports = data.get("upstream_ports") or {}
    if not isinstance(ports, dict) or not all(
        isinstance(k, str) and isinstance(v, str) for k, v in ports.items()
    ):
        raise ManifestError("upstream_ports must be an object of local -> upstream")
    data["upstream_ports"] = {k.lstrip("/"): v.lstrip("/") for k, v in ports.items()}

    reference = data.get("reference") or {}
    if reference:
        if not isinstance(reference, dict) or not reference.get("commit"):
            raise ManifestError("reference must be an object with a commit")
        reference["paths"] = _require_str_list(reference.get("paths"), "reference.paths")
    data["reference"] = reference

    derived_from = data.get("derived_from") or {}
    if not isinstance(derived_from, dict) or not all(
        isinstance(k, str) and isinstance(v, str) for k, v in derived_from.items()
    ):
        raise ManifestError("derived_from must be an object of local -> upstream")
    data["derived_from"] = {
        k.lstrip("/"): v.lstrip("/") for k, v in derived_from.items()
    }

    forced = data.get("forced_exact") or {}
    if not isinstance(forced, dict):
        raise ManifestError("forced_exact must be an object of local -> [subsystem]")
    for local, subsystems in forced.items():
        if not isinstance(subsystems, list) or not set(subsystems) <= FORCED_EXACT_VALUES:
            raise ManifestError(
                f"forced_exact[{local}]: must be a subset of "
                f"{sorted(FORCED_EXACT_VALUES)}"
            )
    data["forced_exact"] = {
        str(k).lstrip("/"): sorted(set(v)) for k, v in forced.items()
    }

    resources = data.get("resources") or []
    if not isinstance(resources, list):
        raise ManifestError("resources must be an array")
    for i, res in enumerate(resources):
        if not isinstance(res, dict) or not res.get("kind") or not res.get("files"):
            raise ManifestError(f"resources[{i}]: kind and files are required")
        for field_name in ("upstream_dir", "local_glob"):
            value = res.get(field_name)
            if not isinstance(value, str) or not value.strip():
                raise ManifestError(
                    f"resources[{i}].{field_name} is required (the derived check "
                    "resolves each declared file against it)"
                )
        for field_name in ("paths",):
            value = res.get(field_name)
            if value is not None and (
                not isinstance(value, dict)
                or not all(isinstance(k, str) and isinstance(v, str) for k, v in value.items())
            ):
                raise ManifestError(
                    f"resources[{i}].{field_name} must be an object of "
                    "local-rel -> upstream-rel"
                )
        for field_name in ("ours", "differs"):
            value = res.get(field_name)
            if value is not None and (
                not isinstance(value, list) or not all(isinstance(v, str) for v in value)
            ):
                raise ManifestError(
                    f"resources[{i}].{field_name} must be an array of strings"
                )
    data["resources"] = resources
    return data


def _overlay_paths_must_exist(
    root: Path, local_root: str, overlay: dict[str, Any]
) -> None:
    """Dead overlay entries are the one kind of rot we can still catch."""
    declared: list[tuple[str, str]] = []
    declared += [(p, "native") for p in overlay.get("native") or []]
    declared += [(p, "upstream_ports") for p in (overlay.get("upstream_ports") or {})]
    declared += [(p, "derived_from") for p in (overlay.get("derived_from") or {})]
    declared += [
        (p, "reference.paths") for p in (overlay.get("reference") or {}).get("paths") or []
    ]
    declared += [(p, "forced_exact") for p in (overlay.get("forced_exact") or {})]
    missing = [
        (rel, field_name)
        for rel, field_name in declared
        if not (root / local_root / rel).is_file()
    ]
    if missing:
        first = missing[0]
        raise ManifestError(
            f"{OVERLAY_NAME}: {len(missing)} declared path(s) are not on disk; "
            f"e.g. {first[0]} ({first[1]}) — remove the entry or restore the file"
        )


# ---------------------------------------------------------------------------
# Git helpers (pristine tree)
# ---------------------------------------------------------------------------


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


def git_tree_blobs(
    commit: str, tree_prefix: str, cwd: Optional[PathLike] = None
) -> dict[str, str]:
    """`{path: blob_oid}` for one subtree of `commit` (single git call)."""
    vd = _as_path(cwd) if cwd is not None else vendor_dir()
    try:
        out = subprocess.check_output(
            ["git", "-C", str(vd), "ls-tree", "-r", commit, "--", tree_prefix],
            stderr=subprocess.DEVNULL,
            text=True,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return {}
    blobs: dict[str, str] = {}
    for line in out.splitlines():
        meta, _, path = line.partition("\t")
        parts = meta.split()
        if len(parts) >= 3 and parts[1] == "blob":
            blobs[path] = parts[2]
    return blobs


def git_blob_bytes(
    oids: Iterable[str],
    cwd: Optional[PathLike] = None,
    *,
    no_lazy_fetch: bool = False,
) -> dict[str, Optional[bytes]]:
    """`{oid: content_or_None}` via one `git cat-file --batch` call.

    `no_lazy_fetch` forbids the promisor remote from filling in missing objects
    (git >= 2.42): a blob-filtered checkout without the contents then answers
    "missing" immediately instead of paying one network round trip per object.
    """
    unique = list(dict.fromkeys(oids))
    if not unique:
        return {}
    vd = _as_path(cwd) if cwd is not None else vendor_dir()
    env = {**os.environ}
    if no_lazy_fetch:
        env["GIT_NO_LAZY_FETCH"] = "1"
    proc = subprocess.run(
        ["git", "-C", str(vd), "cat-file", "--batch"],
        input="".join(f"{oid}\n" for oid in unique).encode(),
        capture_output=True,
        env=env,
    )
    out = proc.stdout
    blobs: dict[str, Optional[bytes]] = {}
    pos = 0
    for oid in unique:
        end = out.find(b"\n", pos)
        if end < 0:
            blobs[oid] = None
            continue
        header = out[pos:end].decode("utf-8", "replace").split()
        if len(header) < 3 or header[1] != "blob":
            blobs[oid] = None
            pos = end + 1
            continue
        size = int(header[2])
        start = end + 1
        blobs[oid] = out[start : start + size]
        pos = start + size + 1  # trailing newline
    return blobs


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
    touched: set[str] = set()
    chunk = 120
    for i in range(0, len(path_list), chunk):
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
                    *path_list[i : i + chunk],
                ],
                stderr=subprocess.DEVNULL,
                text=True,
            )
        except (subprocess.CalledProcessError, FileNotFoundError):
            return []
        touched.update(line.strip() for line in out.splitlines() if line.strip())
    return [p for p in path_list if p in touched]


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


# ---------------------------------------------------------------------------
# Derived provenance index
# ---------------------------------------------------------------------------


def scan_managed_sources(root: Optional[PathLike] = None) -> list[str]:
    """`local_root`-relative sources under `:TMessagesProj` (java + kotlin)."""
    r = _as_path(root)
    base = r / DEFAULT_LOCAL_ROOT / TELEGRAM_JAVA_PREFIX
    if not base.is_dir():
        return []
    out = [
        (TELEGRAM_JAVA_PREFIX + p.relative_to(base).as_posix())
        for p in sorted(base.rglob("*"))
        if p.is_file() and p.suffix in SOURCE_SUFFIXES
    ]
    return out


def local_abs(
    entry: dict[str, Any],
    manifest: dict[str, Any],
    root: Optional[PathLike] = None,
) -> Path:
    r = _as_path(root)
    local_root = manifest.get("local_root", DEFAULT_LOCAL_ROOT)
    return r / local_root / entry["local"]


def upstream_git_path(entry: dict[str, Any], manifest: dict[str, Any]) -> Optional[str]:
    """Map a file entry to its path inside the Telegram git tree."""
    upstream = entry.get("upstream")
    if not upstream:
        return None
    root = manifest.get("upstream_root", DEFAULT_UPSTREAM_ROOT).rstrip("/")
    u = str(upstream).lstrip("/")
    if u.startswith("TMessagesProj/"):
        return u
    return f"{root}/{u}"


def third_party_bucket(
    local: str, prefixes: Optional[Iterable[str]] = None
) -> Optional[str]:
    """Bucket name for allowlisted/third-party trees, else None.

    `prefixes` defaults to the module constant; the overlay's
    `policy.third_party_prefixes` is the human-owned list (pass it in).
    """
    rel = local
    if rel.startswith(TELEGRAM_JAVA_PREFIX):
        rel = rel[len(TELEGRAM_JAVA_PREFIX) :]
    for prefix in prefixes if prefixes is not None else THIRD_PARTY_PREFIXES:
        if rel.startswith(prefix):
            return prefix.rstrip("/").replace("/", "_")
    return None


def third_party_prefixes(manifest: Optional[dict[str, Any]]) -> list[str]:
    """The third-party prefixes to report on: overlay policy, else the default."""
    policy = (manifest or {}).get("policy") or {}
    declared = policy.get("third_party_prefixes")
    if isinstance(declared, list) and all(isinstance(p, str) for p in declared):
        return [p for p in declared if p]
    return list(THIRD_PARTY_PREFIXES)


# ---------------------------------------------------------------------------
# Declared resource copies
# ---------------------------------------------------------------------------


def _resource_rel(local: str) -> Optional[str]:
    """Tail of a local resource path below `res/` or `assets/` (its identity)."""
    for marker in ("/res/", "/assets/"):
        if marker in local:
            return local.split(marker, 1)[1]
    return None


def resource_entries(
    root: Optional[PathLike] = None,
    *,
    overlay: Optional[dict[str, Any]] = None,
    local_root: Optional[str] = None,
    trees: Optional[dict[tuple[str, str], dict[str, str]]] = None,
) -> list[dict[str, Any]]:
    """Resolve every declared resource file to its upstream path.

    Resource groups are declared in the overlay as copied sets (`files` +
    `local_glob`, not per-file paths). Resolution is deterministic:

    1. the group's `paths` override for that file, else
    2. the tail below `upstream_dir` (e.g. `drawable-xxhdpi/foo.png`), else
    3. that tail without its first segment (globs whose `upstream_dir` already
       names the resource dir), else
    4. unresolved — annotate with `paths` (counterpart elsewhere) or `ours`
       (our own asset; nothing to compare against).

    Existence comes from `git ls-tree` (tree objects only), so resolution never
    pulls blob content; byte comparison happens later, and only where the
    contents are already in the checkout.

    Declared names that match no file on disk, and annotations that match no
    declared file, are errors: dead entries are the rot the tools can catch.
    """
    r = _as_path(root)
    overlay = overlay if overlay is not None else load_overlay(r)
    local_root = (local_root or overlay.get("local_root", DEFAULT_LOCAL_ROOT)).rstrip("/")
    vd = vendor_dir(r)
    tree_cache = trees if trees is not None else {}

    def tree(commit: str, upstream_dir: str) -> dict[str, str]:
        """`{path: blob_oid}` for one subtree — `ls-tree` reads no blob content."""
        key = (commit, upstream_dir)
        if key not in tree_cache:
            tree_cache[key] = git_tree_blobs(commit, upstream_dir, vd)
        return tree_cache[key]

    entries: list[dict[str, Any]] = []
    errors: list[str] = []
    for gi, group in enumerate(overlay.get("resources") or []):
        kind = group.get("kind") or "resource"
        upstream_dir = str(group.get("upstream_dir") or "").rstrip("/")
        baseline = normalize_sha(group.get("vendored_at")) or normalize_sha(
            (overlay.get("upstream") or {}).get("pinned_commit")
        )
        glob = str(group.get("local_glob") or "").rstrip("/")
        overrides = group.get("paths") or {}
        ours = set(group.get("ours") or [])
        if not upstream_dir or not baseline or not glob:
            raise ManifestError(
                f"resources[{gi}]: upstream_dir, local_glob and vendored_at "
                "(or a pinned commit) are required"
            )
        declared = list(group.get("files") or [])
        # `local_glob` is repo-relative (e.g. apps/android/app/src/main/res/drawable-*).
        search_glob = glob if glob.startswith(local_root + "/") else f"{local_root}/{glob}"
        rel_glob = search_glob[len(local_root) + 1 :]
        matched: set[str] = set()
        for name in declared:
            found = sorted(r.glob(f"{search_glob}/{name}"))
            if not found:
                errors.append(
                    f"resources[{gi}]: {rel_glob}/{name} is declared but not on disk"
                )
                continue
            for path in found:
                local = path.relative_to(r / local_root).as_posix()
                rel = _resource_rel(local)
                if rel is None:
                    errors.append(
                        f"resources[{gi}]: {local} is not below res/ or assets/"
                    )
                    continue
                matched.add(rel)
                override = overrides.get(rel)
                candidates = (
                    [f"{upstream_dir}/{override}"]
                    if override is not None
                    else (
                        []
                        if rel in ours
                        else [f"{upstream_dir}/{rel}"]
                        + (
                            [f"{upstream_dir}/{rel.split('/', 1)[1]}"]
                            if "/" in rel
                            else []
                        )
                    )
                )
                up_tree = tree(baseline, upstream_dir)
                git_path = next((c for c in candidates if c in up_tree), None)
                if override is not None and git_path is None:
                    errors.append(
                        f"resources[{gi}]: paths[{rel}] -> {override} does not "
                        f"exist at {baseline[:12]}"
                    )
                    continue
                entries.append(
                    {
                        "group": gi,
                        "kind": kind,
                        "local": local,
                        "rel": rel,
                        "upstream": git_path,
                        "upstream_rel": (
                            git_path[len(upstream_dir) + 1 :] if git_path else None
                        ),
                        "upstream_dir": upstream_dir,
                        "oid": up_tree[git_path] if git_path else None,
                        "vendored_at": baseline,
                        "ours": rel in ours,
                        "resolved": git_path is not None,
                    }
                )
        for rel in sorted(set(overrides) | ours):
            if rel not in matched:
                errors.append(
                    f"resources[{gi}]: {rel} is annotated (paths/ours) but no "
                    "declared file resolves to it"
                )
    if errors:
        raise ManifestError(
            f"{OVERLAY_NAME}: {len(errors)} resource group problem(s); "
            + "; ".join(errors[:3])
        )
    entries.sort(key=lambda e: e["local"])
    return entries


def resource_report(index: dict[str, Any]) -> dict[str, Any]:
    """Bucket the declared resource files of a derived index.

    `differs` holds the byte differences nobody acknowledged yet (the guard
    treats them as errors); `acknowledged` holds the ones the overlay lists in
    the group's `differs` array; `stale_acknowledged` holds acknowledged files
    that became identical again (drop the entry).
    """
    files = index.get("resource_files") or []
    acknowledged = set()
    for gi, group in enumerate(index.get("resources") or []):
        for rel in group.get("differs") or []:
            acknowledged.add(f"{gi}:{rel}")
    buckets: dict[str, list[dict[str, Any]]] = {
        "identical": [],
        "differs": [],
        "acknowledged": [],
        "stale_acknowledged": [],
        "ours": [],
        "unresolved": [],
        "uncompared": [],
    }
    for entry in files:
        key = f"{entry['group']}:{entry['rel']}"
        if entry.get("ours"):
            buckets["ours"].append(entry)
        elif not entry.get("resolved"):
            buckets["unresolved"].append(entry)
        elif entry.get("modified") is None:
            # Blob contents unavailable in this checkout (see
            # git_blob_bytes(no_lazy_fetch=True)).
            buckets["uncompared"].append(entry)
        elif entry.get("modified"):
            buckets[
                "acknowledged" if key in acknowledged else "differs"
            ].append(entry)
        else:
            buckets[
                "stale_acknowledged" if key in acknowledged else "identical"
            ].append(entry)
    return buckets


# ---------------------------------------------------------------------------
# Ledger heuristics (both directions of a mislabelled file)
# ---------------------------------------------------------------------------


def stub_candidates(index: dict[str, Any]) -> list[dict[str, Any]]:
    """`exact` files far smaller than their upstream original.

    A copy that shrank to a fraction of the upstream blob is almost always a
    hand-written stub wearing an upstream path — either adopt the upstream
    file or list it in the overlay's `native` array.
    """
    out = []
    for entry in index.get("files") or []:
        if entry.get("port_mode") != "exact" or not entry.get("modified"):
            continue
        local_bytes = entry.get("local_bytes")
        upstream_bytes = entry.get("upstream_bytes")
        if not local_bytes or not upstream_bytes:
            continue
        if local_bytes * 2 < upstream_bytes:
            out.append(entry)
    return out


def redundant_native(index: dict[str, Any]) -> list[dict[str, Any]]:
    """`native`-declared files whose bytes still equal the upstream blob.

    They are clean copies, so the label hides them from the bump report; drop
    them from `native` (or they are ours and have drifted since).
    """
    out = []
    for entry in index.get("files") or []:
        if entry.get("port_mode") != "native" or entry.get("origin") == "shim":
            continue
        if entry.get("modified") is False:
            out.append(entry)
    return out


def derive_index(
    root: Optional[PathLike] = None, *, with_modified: bool = True
) -> dict[str, Any]:
    """Build the flat provenance index from overlay + pristine tree + disk.

    Entry shape (kept compatible with the vendor tools):

    - `files`          — sources with an upstream counterpart at the pin
    - `compat_shims`   — `:TMessagesProj` sources with no upstream blob at the pin
    - `resource_files` — declared resource copies, resolved per file
    - `resources`      — the declared resource groups
    """
    r = _as_path(root)
    overlay = load_overlay(r)
    local_root = overlay.get("local_root", DEFAULT_LOCAL_ROOT)
    upstream_root = overlay.get("upstream_root", DEFAULT_UPSTREAM_ROOT)
    pin = normalize_sha((overlay.get("upstream") or {}).get("pinned_commit"))
    if not pin:
        raise ManifestError(f"{OVERLAY_NAME}: upstream.pinned_commit is invalid")
    _overlay_paths_must_exist(r, local_root, overlay)

    native = set(overlay.get("native") or [])
    forced_exact = overlay.get("forced_exact") or {}
    derived_from = overlay.get("derived_from") or {}
    reference = overlay.get("reference") or {}
    reference_commit = normalize_sha(reference.get("commit")) if reference else None
    reference_paths = set(reference.get("paths") or [])
    ports = overlay.get("upstream_ports") or {}

    manifest_meta = {
        "upstream": overlay.get("upstream") or {},
        "local_root": local_root,
        "upstream_root": upstream_root,
        "policy": overlay.get("policy") or {},
        "reference": reference,
        "resources": overlay.get("resources") or [],
        "overlay": str(overlay_path(r)),
    }

    pending: list[dict[str, Any]] = []
    for local in scan_managed_sources(r):
        upstream = derived_from.get(local, local[len(TELEGRAM_JAVA_PREFIX) :])
        pending.append({"local": local, "upstream": upstream, "origin": "vendored"})
    for local, upstream in sorted(ports.items()):
        pending.append({"local": local, "upstream": upstream, "origin": "port"})

    # One tree listing per baseline gives existence + blob ids for every path.
    vd = vendor_dir(r)
    if not vd.exists():
        raise ManifestError(
            f"pristine Telegram checkout missing at {vd} — run scripts/vendor-setup.sh"
        )
    trees: dict[str, dict[str, str]] = {}
    for commit in {pin, reference_commit} - {None}:
        trees[commit] = git_tree_blobs(commit, upstream_root, vd)
    resource_files = resource_entries(r, overlay=overlay, local_root=local_root)
    blob_bytes: dict[str, Optional[bytes]] = {}
    resource_blob_bytes: dict[str, Optional[bytes]] = {}
    if with_modified:
        wanted = []
        for entry in pending:
            baseline = (
                reference_commit
                if reference_commit and entry["local"] in reference_paths
                else pin
            )
            oid = trees.get(baseline, {}).get(upstream_git_path(entry, manifest_meta))
            if oid:
                wanted.append(oid)
        blob_bytes = git_blob_bytes(wanted, vd)
        # Resource copies are byte-verified only where their blobs are already
        # present (a warmed checkout); a cold blob-filtered clone — CI — must
        # not pay one network round trip per drawable, so it reports them as
        # "not compared" instead of fetching them.
        resource_blob_bytes = git_blob_bytes(
            [e["oid"] for e in resource_files if e.get("oid")], vd, no_lazy_fetch=True
        )

    files: list[dict[str, Any]] = []
    shims: list[dict[str, Any]] = []
    for entry in pending:
        local = entry["local"]
        up_rel = entry["upstream"]
        baseline = reference_commit if local in reference_paths else pin
        git_path = upstream_git_path(entry, manifest_meta)
        oid = trees.get(baseline, {}).get(git_path) if git_path else None
        item: dict[str, Any] = {
            "local": local,
            "upstream": up_rel,
            "vendored_at": baseline or pin,
            "port_mode": "native" if local in native else "exact",
        }
        if entry["origin"] == "port":
            item["port_mode"] = "native"
        if local in forced_exact:
            item["forced_exact"] = forced_exact[local]
        if entry["origin"] == "shim" or oid is None:
            # Declared upstream path, but nothing at that path at the pin:
            # a scratch host surface, not a copy.
            item["port_mode"] = "native"
            item["origin"] = "shim"
            shims.append(item)
            continue
        item["origin"] = entry["origin"]
        path = local_abs(item, manifest_meta, r)
        if path.is_file():
            item["local_bytes"] = path.stat().st_size
        if with_modified:
            blob = blob_bytes.get(oid)
            if blob is not None:
                item["upstream_bytes"] = len(blob)
            item["modified"] = (
                None if blob is None or not path.is_file() else path.read_bytes() != blob
            )
        files.append(item)

    for entry in resource_files:
        path = r / local_root / entry["local"]
        entry["local_bytes"] = path.stat().st_size if path.is_file() else None
        if with_modified:
            blob = (
                resource_blob_bytes.get(entry["oid"]) if entry.get("oid") else None
            )
            if blob is not None:
                entry["upstream_bytes"] = len(blob)
            entry["modified"] = (
                None
                if blob is None or not path.is_file()
                else path.read_bytes() != blob
            )

    files.sort(key=lambda e: e["local"])
    shims.sort(key=lambda e: e["local"])
    manifest_meta["files"] = files
    manifest_meta["compat_shims"] = shims
    manifest_meta["resource_files"] = resource_files
    return manifest_meta


def _atomic_write_json(path: Path, data: dict[str, Any]) -> None:
    import tempfile

    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
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


def update_overlay_upstream(
    root: Optional[PathLike],
    commit: str,
    *,
    date: Optional[str] = None,
) -> None:
    """Move the overlay's pinned commit (used by vendor-ff after --apply)."""
    path = overlay_path(root)
    data = _read_json(path)
    upstream = data.setdefault("upstream", {})
    upstream["pinned_commit"] = commit
    if date:
        upstream["pinned_date"] = date
    _atomic_write_json(path, data)


def load_index(
    root: Optional[PathLike] = None, *, with_modified: bool = True
) -> dict[str, Any]:
    """Alias for :func:`derive_index` — the vendor tools' entry point."""
    return derive_index(root, with_modified=with_modified)


def prefetch_specs(root: Optional[PathLike] = None) -> list[str]:
    """`<commit>:<path>` specs for every blob the index compares.

    Used by `vendor-setup.sh --prefetch` to warm a blob-filtered clone (the
    lazy per-object fetch would otherwise make the first index run slow).
    """
    r = _as_path(root)
    overlay = load_overlay(r)
    local_root = overlay.get("local_root", DEFAULT_LOCAL_ROOT)
    upstream_root = overlay.get("upstream_root", DEFAULT_UPSTREAM_ROOT)
    pin = normalize_sha((overlay.get("upstream") or {}).get("pinned_commit"))
    if not pin:
        raise ManifestError(f"{OVERLAY_NAME}: upstream.pinned_commit is invalid")
    reference = overlay.get("reference") or {}
    reference_commit = normalize_sha(reference.get("commit")) if reference else None
    reference_paths = set(reference.get("paths") or [])
    derived_from = overlay.get("derived_from") or {}
    meta = {"upstream_root": upstream_root, "local_root": local_root}

    paths: list[str] = []
    for local in scan_managed_sources(r):
        upstream = derived_from.get(local, local[len(TELEGRAM_JAVA_PREFIX) :])
        git_path = upstream_git_path({"upstream": upstream}, meta)
        baseline = reference_commit if local in reference_paths else pin
        paths.append(f"{baseline}:{git_path}")
    for local, upstream in (overlay.get("upstream_ports") or {}).items():
        git_path = upstream_git_path({"upstream": upstream}, meta)
        baseline = reference_commit if local in reference_paths else pin
        paths.append(f"{baseline}:{git_path}")
    for entry in resource_entries(r, overlay=overlay, local_root=local_root):
        if entry["resolved"]:
            paths.append(f"{entry['vendored_at']}:{entry['upstream']}")
    return sorted(set(paths))


def compare_to_upstream(
    entry: dict[str, Any],
    manifest: dict[str, Any],
    root: Optional[PathLike] = None,
) -> dict[str, Any]:
    """Compare one local file to its upstream blob at `vendored_at`."""
    result: dict[str, Any] = {
        "local": entry.get("local"),
        "exists": False,
        "upstream_available": False,
        "byte_equal": None,
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
    result["byte_equal"] = local_bytes == remote
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


def changed_since_head(root: Optional[PathLike] = None) -> set[str]:
    """Repo-relative paths with content changes (staged + unstaged).

    Pure renames (`R100`: same bytes at a new path) are skipped — moving a file
    is not a behavior change, and every consumer of this set guards behavior.
    A rename that also edits the file reports the new path.
    """
    return set(changed_with_head_source(root))


def changed_with_head_source(
    root: Optional[PathLike] = None,
) -> dict[str, Optional[str]]:
    """Changed path -> the HEAD path it came from (`None` when HEAD lacks it).

    The mapping lets callers diff a moved file against its own history instead
    of treating it as brand new.
    """
    r = _as_path(root)
    sources: dict[str, Optional[str]] = {}
    # One diff against HEAD covers staged *and* unstaged edits, and rename
    # detection then reports `R<score> old new` for a file that moved and was
    # edited in the same working state — which is what the provenance of a
    # comment-only edit needs.
    try:
        out = subprocess.check_output(
            ["git", "-C", str(r), "diff", "HEAD", "-M", "--name-status"],
            stderr=subprocess.DEVNULL,
            text=True,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return sources
    for line in out.splitlines():
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        status = parts[0]
        if status in ("R100", "C100"):
            continue
        if status.startswith(("R", "C")) and len(parts) >= 3:
            sources[parts[2].strip()] = parts[1].strip()
        elif status == "A":
            sources[parts[1].strip()] = None
        else:
            sources[parts[1].strip()] = parts[1].strip()
    return sources


def strip_comments(text: str) -> str:
    """Source text with comments and whitespace removed.

    Used to tell a comment-only edit from a behavior change; string literals are
    kept intact so a URL containing `//` is not mistaken for a comment.
    """
    out: list[str] = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c in ('"', "'"):
            quote = c
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == quote:
                    break
                j += 1
            out.append(text[i : min(j + 1, n)])
            i = min(j + 1, n)
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        out.append(c)
        i += 1
    return re.sub(r"\s+", "", "".join(out))


def comment_only_edit(
    root: Optional[PathLike], path: str, head_path: Optional[str]
) -> bool:
    """True when `path` differs from its HEAD blob only in comments/whitespace."""
    if not head_path:
        return False
    r = _as_path(root)
    proc = subprocess.run(
        ["git", "-C", str(r), "show", f"HEAD:{head_path}"],
        capture_output=True,
        check=False,
    )
    if proc.returncode != 0:
        return False
    try:
        new = (r / path).read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return False
    old = proc.stdout.decode("utf-8", "ignore")
    return strip_comments(old) == strip_comments(new)


def eprint(*args: object) -> None:
    print(*args, file=sys.stderr)

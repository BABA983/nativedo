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
TELEGRAM_JAVA_PREFIX = "telegram/src/main/java/"
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
    """`path` when it exists, else a sibling whose name matches ignoring case.

    `git clone https://github.com/DrKLO/Telegram.git` creates `Telegram/`, so a
    case-sensitive filesystem would otherwise miss an existing checkout named
    after the upstream repo.
    """
    if path.exists():
        return path
    parent = path.parent
    if not parent.is_dir():
        return None
    wanted = path.name.casefold()
    for candidate in sorted(parent.iterdir()):
        if candidate.name.casefold() == wanted:
            return candidate
    return None


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
    oids: Iterable[str], cwd: Optional[PathLike] = None
) -> dict[str, Optional[bytes]]:
    """`{oid: content_or_None}` via one `git cat-file --batch` call."""
    unique = list(dict.fromkeys(oids))
    if not unique:
        return {}
    vd = _as_path(cwd) if cwd is not None else vendor_dir()
    proc = subprocess.run(
        ["git", "-C", str(vd), "cat-file", "--batch"],
        input="".join(f"{oid}\n" for oid in unique).encode(),
        capture_output=True,
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
    """`local_root`-relative sources under `:telegram` (java + kotlin)."""
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


def third_party_bucket(local: str) -> Optional[str]:
    """Bucket name for allowlisted/third-party trees, else None."""
    rel = local
    if rel.startswith(TELEGRAM_JAVA_PREFIX):
        rel = rel[len(TELEGRAM_JAVA_PREFIX) :]
    for prefix in THIRD_PARTY_PREFIXES:
        if rel.startswith(prefix):
            return prefix.rstrip("/").replace("/", "_")
    return None


def derive_index(
    root: Optional[PathLike] = None, *, with_modified: bool = True
) -> dict[str, Any]:
    """Build the flat provenance index from overlay + pristine tree + disk.

    Entry shape (kept compatible with the vendor tools):

    - `files`        — sources with an upstream counterpart at the pin
    - `compat_shims` — `:telegram` sources with no upstream blob at the pin
    - `resources`    — declared resource copies
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
    blob_bytes: dict[str, Optional[bytes]] = {}
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
        item["origin"] = "vendored"
        if with_modified:
            blob = blob_bytes.get(oid)
            path = local_abs(item, manifest_meta, r)
            item["modified"] = (
                None if blob is None or not path.is_file() else path.read_bytes() != blob
            )
        files.append(item)

    files.sort(key=lambda e: e["local"])
    shims.sort(key=lambda e: e["local"])
    manifest_meta["files"] = files
    manifest_meta["compat_shims"] = shims
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
    """Repo-relative paths changed in the working tree (staged + unstaged)."""
    r = _as_path(root)
    changed: set[str] = set()
    for args in (["diff", "--name-only"], ["diff", "--cached", "--name-only"]):
        try:
            out = subprocess.check_output(
                ["git", "-C", str(r), *args], stderr=subprocess.DEVNULL, text=True
            )
        except (subprocess.CalledProcessError, FileNotFoundError):
            continue
        changed.update(line.strip() for line in out.splitlines() if line.strip())
    return changed


def eprint(*args: object) -> None:
    print(*args, file=sys.stderr)

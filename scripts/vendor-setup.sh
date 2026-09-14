#!/usr/bin/env bash
# scripts/vendor-setup.sh
#
# Ensure the pristine Telegram checkout exists and is checked out at the
# manifest pin (or a ref passed on the CLI).
#
# The checkout lives outside this repo — a sibling directory by default
# (`../telegram`). Override it with NATIVEDO_TELEGRAM_VENDOR_DIR or
# `upstream.local_checkout` in vendor-overlay.json. A leftover in-repo
# `vendor/telegram-android` is still detected, but move it out and delete the
# `vendor/` directory once you have switched.
#
# Usage:
#   scripts/vendor-setup.sh              # clone/fetch + checkout pinned_commit
#   scripts/vendor-setup.sh --fetch      # fetch only, keep current HEAD
#   scripts/vendor-setup.sh <commit>     # checkout a specific commit
#   scripts/vendor-setup.sh --status     # print pin vs HEAD (exit 1 on drift)
#   scripts/vendor-setup.sh --print-dir  # print the resolved checkout path
#   scripts/vendor-setup.sh --prefetch   # materialize blob contents for the pin
#                                        # and reference commits (vendor-index)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
MANIFEST="$ROOT/vendor-overlay.json"
DEFAULT_HTTPS="https://github.com/DrKLO/Telegram.git"

if [[ ! -f "$MANIFEST" ]]; then
  echo "ERROR: $MANIFEST not found" >&2
  exit 1
fi

vendor_python() {
  PYTHONPATH="$ROOT/scripts" NATIVEDO_ROOT="$ROOT" python3 -c "$1"
}

VENDOR_DIR="$(vendor_python 'from vendorlib import vendor_dir; print(vendor_dir())')"
LEGACY_DIR="$ROOT/vendor/telegram-android"

PIN="$(
  vendor_python "from vendorlib import load_overlay, normalize_sha; raw = (load_overlay('${ROOT}').get('upstream') or {}).get('pinned_commit'); print(normalize_sha(raw) or raw or '')"
)"
REPO="$(
  python3 -c "
import json
from pathlib import Path
m = json.loads(Path('${MANIFEST}').read_text())
print(m.get('upstream', {}).get('repo') or '')
"
)"
REPO="${REPO:-git@github.com:DrKLO/Telegram.git}"

# Prefer HTTPS clone so machines without GitHub SSH still work.
CLONE_URL="$REPO"
if [[ "$CLONE_URL" == git@github.com:* ]]; then
  path="${CLONE_URL#git@github.com:}"
  path="${path%.git}"
  CLONE_URL="https://github.com/${path}.git"
elif [[ -z "$CLONE_URL" ]]; then
  CLONE_URL="$DEFAULT_HTTPS"
fi

MODE="checkout"
TARGET_REF="$PIN"
PRINT_DIR=0
for arg in "$@"; do
  case "$arg" in
    --fetch) MODE="fetch" ;;
    --status) MODE="status" ;;
    --print-dir) PRINT_DIR=1 ;;
    --prefetch) MODE="prefetch" ;;
    --help|-h)
      sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    -*)
      echo "Unknown flag: $arg" >&2
      exit 2
      ;;
    *)
      TARGET_REF="$arg"
      MODE="checkout"
      ;;
  esac
done

if (( PRINT_DIR )); then
  echo "$VENDOR_DIR"
  exit 0
fi

if [[ -z "$PIN" && "$MODE" != "fetch" && "$MODE" != "prefetch" && "$TARGET_REF" == "$PIN" ]]; then
  echo "ERROR: upstream.pinned_commit missing in manifest" >&2
  exit 1
fi

if [[ "$VENDOR_DIR" == "$LEGACY_DIR" ]]; then
  echo "NOTE: using the legacy in-repo checkout $LEGACY_DIR" >&2
  echo "      Move it next to this repo ($(dirname "$ROOT")/telegram) to match" >&2
  echo "      the default, or set NATIVEDO_TELEGRAM_VENDOR_DIR." >&2
fi

mkdir -p "$(dirname "$VENDOR_DIR")"

prefetch_blobs() {
  # The vendor tools compare files against the pin and the historical
  # reference commit; a blob-filtered clone fetches those bytes lazily unless
  # we warm them once here.
  local refs=("$PIN")
  local ref_commit
  ref_commit="$(vendor_python "from vendorlib import load_overlay; print((load_overlay('${ROOT}').get('reference') or {}).get('commit') or '')")"
  [[ -n "$ref_commit" ]] && refs+=("$ref_commit")
  local ref
  for ref in "${refs[@]}"; do
    [[ -n "$ref" ]] || continue
    echo "Prefetching blob contents at ${ref:0:12} ..."
    git -C "$VENDOR_DIR" ls-tree -r "$ref" -- TMessagesProj/src/main/java |
      awk '{print $3}' |
      git -C "$VENDOR_DIR" cat-file --batch >/dev/null
  done
}

if [[ "$MODE" == "status" ]]; then
  if [[ ! -d "$VENDOR_DIR/.git" && ! -f "$VENDOR_DIR/.git" ]]; then
    echo "ERROR: pristine tree missing at $VENDOR_DIR" >&2
    echo "Run: scripts/vendor-setup.sh" >&2
    exit 1
  fi
  HEAD="$(git -C "$VENDOR_DIR" rev-parse HEAD)"
  RESOLVED_PIN="$(git -C "$VENDOR_DIR" rev-parse "$PIN" 2>/dev/null || true)"
  echo "manifest pin: ${PIN:-<none>}"
  echo "vendor dir:   $VENDOR_DIR"
  echo "vendor HEAD:  $HEAD"
  if [[ -n "$RESOLVED_PIN" && "$HEAD" == "$RESOLVED_PIN" ]]; then
    echo "OK: HEAD matches pin"
    exit 0
  fi
  echo "DRIFT: HEAD does not match pin"
  exit 1
fi

if [[ ! -d "$VENDOR_DIR/.git" && ! -f "$VENDOR_DIR/.git" ]]; then
  if [[ -d "$VENDOR_DIR" ]]; then
    echo "ERROR: $VENDOR_DIR exists but is not a git repo" >&2
    exit 1
  fi
  echo "Cloning Telegram into $VENDOR_DIR ..."
  echo "  url: $CLONE_URL"
  git clone --filter=blob:none "$CLONE_URL" "$VENDOR_DIR"
fi

echo "Fetching origin ..."
git -C "$VENDOR_DIR" fetch --tags origin 2>/dev/null || git -C "$VENDOR_DIR" fetch origin

HEAD="$(git -C "$VENDOR_DIR" rev-parse HEAD)"
echo "manifest pin: ${PIN:-<none>}"
echo "vendor dir:   $VENDOR_DIR"
echo "vendor HEAD:  $HEAD"
echo "target:       ${TARGET_REF:-<none>}"

if [[ "$MODE" == "fetch" ]]; then
  echo "OK: fetch complete (HEAD unchanged)"
  exit 0
fi

if [[ "$MODE" == "prefetch" ]]; then
  prefetch_blobs
  echo "OK: blob contents available for the vendor tools"
  exit 0
fi

if [[ -z "$TARGET_REF" ]]; then
  echo "ERROR: no target ref" >&2
  exit 1
fi

if ! git -C "$VENDOR_DIR" rev-parse --verify "${TARGET_REF}^{commit}" >/dev/null 2>&1; then
  echo "ERROR: cannot resolve $TARGET_REF" >&2
  exit 1
fi

# Detached HEAD at pin is intentional for a pristine tree.
git -C "$VENDOR_DIR" checkout --detach "$TARGET_REF"
NEW_HEAD="$(git -C "$VENDOR_DIR" rev-parse HEAD)"
echo "OK: checked out $NEW_HEAD"
git -C "$VENDOR_DIR" log -1 --oneline
prefetch_blobs

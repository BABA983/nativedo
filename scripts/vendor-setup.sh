#!/usr/bin/env bash
# scripts/vendor-setup.sh
#
# Ensure vendor/telegram-android exists and is checked out at the manifest pin
# (or a ref passed on the CLI).
#
# Usage:
#   scripts/vendor-setup.sh              # clone/fetch + checkout pinned_commit
#   scripts/vendor-setup.sh --fetch      # fetch only, keep current HEAD
#   scripts/vendor-setup.sh <commit>     # checkout a specific commit
#   scripts/vendor-setup.sh --status     # print pin vs HEAD (exit 1 on drift)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/vendor-manifest.json"
VENDOR_DIR="$ROOT/vendor/telegram-android"
DEFAULT_HTTPS="https://github.com/DrKLO/Telegram.git"

if [[ ! -f "$MANIFEST" ]]; then
  echo "ERROR: $MANIFEST not found" >&2
  exit 1
fi

PIN="$(
  python3 -c "
import sys
sys.path.insert(0, '${ROOT}/scripts')
from vendorlib import load_manifest, normalize_sha
m = load_manifest('${ROOT}')
raw = m.get('upstream', {}).get('pinned_commit')
print(normalize_sha(raw) or raw or '')
"
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
for arg in "$@"; do
  case "$arg" in
    --fetch) MODE="fetch" ;;
    --status) MODE="status" ;;
    --help|-h)
      sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
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

if [[ -z "$PIN" && "$MODE" != "fetch" && "$TARGET_REF" == "$PIN" ]]; then
  echo "ERROR: upstream.pinned_commit missing in manifest" >&2
  exit 1
fi

mkdir -p "$ROOT/vendor"

if [[ "$MODE" == "status" ]]; then
  if [[ ! -d "$VENDOR_DIR/.git" && ! -f "$VENDOR_DIR/.git" ]]; then
    echo "ERROR: pristine tree missing at $VENDOR_DIR" >&2
    echo "Run: scripts/vendor-setup.sh" >&2
    exit 1
  fi
  HEAD="$(git -C "$VENDOR_DIR" rev-parse HEAD)"
  RESOLVED_PIN="$(git -C "$VENDOR_DIR" rev-parse "$PIN" 2>/dev/null || true)"
  echo "manifest pin: ${PIN:-<none>}"
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
  echo "Cloning Telegram into vendor/telegram-android ..."
  echo "  url: $CLONE_URL"
  git clone --filter=blob:none "$CLONE_URL" "$VENDOR_DIR"
fi

echo "Fetching origin ..."
git -C "$VENDOR_DIR" fetch --tags origin 2>/dev/null || git -C "$VENDOR_DIR" fetch origin

HEAD="$(git -C "$VENDOR_DIR" rev-parse HEAD)"
echo "manifest pin: ${PIN:-<none>}"
echo "vendor HEAD:  $HEAD"
echo "target:       ${TARGET_REF:-<none>}"

if [[ "$MODE" == "fetch" ]]; then
  echo "OK: fetch complete (HEAD unchanged)"
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

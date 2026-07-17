#!/usr/bin/env bash
# scripts/vendor-status.sh
#
# Manifest / disk / pin health for vendored Telegram code.
# Exit 0 if no errors; 1 if consistency errors (warnings alone still exit 0
# unless --strict).
#
# Usage:
#   scripts/vendor-status.sh
#   scripts/vendor-status.sh --strict
#   scripts/vendor-status.sh --check-modified   # byte-compare every file (slow)
#   scripts/vendor-status.sh --bump-report [NEW_SHA]
#   scripts/vendor-status.sh --json
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
exec python3 "$ROOT/scripts/vendor_status.py" "$@"

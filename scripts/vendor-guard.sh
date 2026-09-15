#!/usr/bin/env bash
# scripts/vendor-guard.sh — pre-flight check for Telegram-derived code.
#
# Run this before committing any change under apps/android/TMessagesProj/** or to
# vendor-overlay.json. Errors print the command that fixes them.
#
# Usage:
#   scripts/vendor-guard.sh            # index + isolation + forced-exact + drift
#   scripts/vendor-guard.sh --quick    # index + isolation only (hook/CI fast path)
#   scripts/vendor-guard.sh --compile  # also compile :TMessagesProj
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
exec python3 "$ROOT/scripts/vendor_guard.py" "$@"

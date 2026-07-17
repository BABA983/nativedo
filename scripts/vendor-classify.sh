#!/usr/bin/env bash
# scripts/vendor-classify.sh — host-wiring / drift classification report
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
exec python3 "$ROOT/scripts/vendor_classify.py" "$@"

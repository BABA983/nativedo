#!/usr/bin/env bash
# scripts/vendor-ff.sh — fast-forward exact unmodified files to the pin (or --to).
# Default is dry-run; pass --apply to write.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
exec python3 "$ROOT/scripts/vendor_ff.py" "$@"

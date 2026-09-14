#!/usr/bin/env bash
# scripts/vendor-index.sh — show or verify the derived provenance index.
#
# The index is computed from vendor-overlay.json + the pristine Telegram
# checkout + the files on disk. Nothing to regenerate, nothing to hand-edit.
#
# Usage:
#   scripts/vendor-index.sh                 # summary
#   scripts/vendor-index.sh --check         # summary, fail if derivation fails
#   scripts/vendor-index.sh --path LensCell # one entry (local / upstream / suffix)
#   scripts/vendor-index.sh --json          # full index (machine readable)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
exec python3 "$ROOT/scripts/vendor_index.py" "$@"

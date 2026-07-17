#!/usr/bin/env bash
# scripts/vendor-diff.sh
#
# Diff a vendored local file against its upstream blob.
#
# Usage:
#   scripts/vendor-diff.sh <local-path>
#   scripts/vendor-diff.sh <local-path> --at <commit>
#   scripts/vendor-diff.sh <local-path> --pinned
#   scripts/vendor-diff.sh --stat              # summary for all modified:false mismatches
#
# <local-path> may be:
#   apps/android/telegram/src/main/java/org/telegram/...
#   telegram/src/main/java/org/telegram/...          (relative to local_root)
#   org/telegram/ui/Components/RecyclerListView.java  (upstream-style)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
exec python3 "$ROOT/scripts/vendor_diff.py" "$@"

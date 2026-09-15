#!/usr/bin/env bash
# scripts/auto-vendor-copy.sh
# Copy missing vendor classes that javac complains about into :TMessagesProj.
#
# Nothing to register: provenance is derived (scripts/vendor-index.sh). A copied
# file keeps its upstream-relative path, so the next guard run tracks it as an
# exact copy of its upstream blob at the pin. Add the path to the overlay's
# `native` array only when the file is our code rather than a copy.
#
# Skips: classes > MAX_LINES or with > MAX_IMPORTS telegram imports.
#
# After the :TMessagesProj module migration the destination mirrors the vendor
# package layout exactly (org.telegram.*, me.vkryl.*, com.google.*, etc.) so
# no `sed` package rewrite is needed — files are dropped in as-is.
#
# Usage:
#   scripts/auto-vendor-copy.sh [path-to-javac-error-log]
#   scripts/auto-vendor-copy.sh --dry-run [log]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="/tmp/javac-out.log"
DRY_RUN=0

case "${1:-}" in
  --dry-run)
    DRY_RUN=1
    LOG="${2:-$LOG}"
    ;;
  -h|--help)
    sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  "")
    ;;
  *)
    LOG="$1"
    ;;
esac

VENDOR_JAVA="$(
  PYTHONPATH="$ROOT/scripts" NATIVEDO_ROOT="$ROOT" python3 -c '
from vendorlib import load_overlay, vendor_dir
overlay = load_overlay()
print(vendor_dir() / overlay.get("upstream_root", "TMessagesProj/src/main/java"))
'
)"
DEST_JAVA="$ROOT/apps/android/TMessagesProj/src/main/java"
MAX_LINES=800
MAX_IMPORTS=10

if [[ ! -f "$LOG" ]]; then
  echo "Log file $LOG not found" >&2
  exit 1
fi

if [[ ! -d "$VENDOR_JAVA" ]]; then
  echo "ERROR: pristine tree missing at $VENDOR_JAVA" >&2
  echo "Run: scripts/vendor-setup.sh" >&2
  exit 1
fi

classes=$(
  grep -A4 "error: cannot find symbol" "$LOG" \
    | grep -E "symbol:[[:space:]]*class" \
    | sed -E 's/.*symbol:[[:space:]]*class[[:space:]]+([A-Za-z0-9_]+).*/\1/' \
    | sort -u \
    || true
)

copied=0
skipped_big=0
skipped_deps=0
skipped_missing=0
skipped_exists=0

for cls in $classes; do
  found=$(find "$VENDOR_JAVA" -name "$cls.java" 2>/dev/null | head -1)
  if [[ -z "$found" ]]; then
    skipped_missing=$((skipped_missing + 1))
    continue
  fi

  rel="${found#"$VENDOR_JAVA"/}"
  destPath="$DEST_JAVA/$rel"

  if [[ -f "$destPath" ]]; then
    skipped_exists=$((skipped_exists + 1))
    continue
  fi

  lines=$(wc -l < "$found" | tr -d ' ')
  if [[ "$lines" -gt $MAX_LINES ]]; then
    skipped_big=$((skipped_big + 1))
    echo "  SKIP $cls.java ($lines lines, too big)"
    continue
  fi

  imports=$(grep -cE "^import org\.telegram\." "$found" || true)
  if [[ "$imports" -gt $MAX_IMPORTS ]]; then
    skipped_deps=$((skipped_deps + 1))
    echo "  SKIP $cls.java ($imports telegram imports, too many)"
    continue
  fi

  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "  DRY-RUN COPY $cls.java ($lines lines, $imports imports) -> $rel"
    copied=$((copied + 1))
    continue
  fi

  mkdir -p "$(dirname "$destPath")"
  cp "$found" "$destPath"
  echo "  COPY $cls.java ($lines lines, $imports imports) -> $rel"
  copied=$((copied + 1))
done

echo ""
echo "Summary: copied=$copied, skipped_big=$skipped_big, skipped_deps=$skipped_deps, skipped_missing=$skipped_missing, skipped_exists=$skipped_exists"

if [[ "$copied" -gt 0 && "$DRY_RUN" -eq 0 ]]; then
  echo "Nothing to register: provenance is derived (scripts/vendor-index.sh)."
  echo "Next: scripts/vendor-guard.sh --compile"
fi

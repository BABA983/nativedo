#!/bin/bash
# scripts/auto-vendor-copy.sh
# Automatically copy missing vendor classes that javac complains about.
# Skips: classes > MAX_LINES or with > MAX_IMPORTS telegram imports.
#
# After the :telegram module migration the destination mirrors the vendor
# package layout exactly (org.telegram.*, me.vkryl.*, com.google.*, etc.) so
# no `sed` package rewrite is needed — files are dropped in as-is.
#
# Usage: scripts/auto-vendor-copy.sh [path-to-javac-error-log]

set -eu

LOG="${1:-/tmp/javac-out.log}"
VENDOR=/Users/baba/Documents/workspace_baba/linuxdo-native-refactor/vendor/telegram-android/TMessagesProj/src/main/java
DEST=/Users/baba/Documents/workspace_baba/linuxdo-native-refactor/apps/android/telegram/src/main/java
MAX_LINES=800
MAX_IMPORTS=10

if [ ! -f "$LOG" ]; then
  echo "Log file $LOG not found" >&2
  exit 1
fi

classes=$(grep -A4 "error: cannot find symbol" "$LOG" | grep "symbol:\s*class" | sed -E 's/.*symbol:[[:space:]]*class[[:space:]]+([A-Za-z0-9_]+).*/\1/' | sort -u)

copied=0
skipped_big=0
skipped_deps=0
skipped_missing=0
skipped_exists=0

for cls in $classes; do
  found=$(find "$VENDOR" -name "$cls.java" 2>/dev/null | head -1)
  if [ -z "$found" ]; then
    skipped_missing=$((skipped_missing+1))
    continue
  fi

  rel="${found#$VENDOR/}"
  destPath="$DEST/$rel"

  if [ -f "$destPath" ]; then
    skipped_exists=$((skipped_exists+1))
    continue
  fi

  lines=$(wc -l < "$found")
  if [ "$lines" -gt $MAX_LINES ]; then
    skipped_big=$((skipped_big+1))
    echo "  SKIP $cls.java ($lines lines, too big)"
    continue
  fi

  imports=$(grep -cE "^import org\.telegram\." "$found" || true)
  if [ "$imports" -gt $MAX_IMPORTS ]; then
    skipped_deps=$((skipped_deps+1))
    echo "  SKIP $cls.java ($imports telegram imports, too many)"
    continue
  fi

  mkdir -p "$(dirname "$destPath")"
  cp "$found" "$destPath"

  echo "  COPY $cls.java ($lines lines, $imports imports) -> ${destPath#$DEST/}"
  copied=$((copied+1))
done

echo ""
echo "Summary: copied=$copied, skipped_big=$skipped_big, skipped_deps=$skipped_deps, skipped_missing=$skipped_missing, skipped_exists=$skipped_exists"

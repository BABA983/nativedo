#!/bin/bash
# scripts/vendor-isolation-check.sh
#
# Enforces the rule: telegram/** MUST NOT import any NativeDo package
# (core.*, feature.*, ui.*). See docs/code-layout.md "Dependency direction".
set -euo pipefail

TELEGRAM_DIR="apps/android/app/src/main/java/io/github/baba983/nativedo/telegram"

# Known exceptions: files that import ui.* because the Telegram-derived
# code in ui/actionbar, ui/components, ui/cells hasn't been moved to
# telegram/ui/ yet. Remove entries as the placement debt is fixed.
#
# Format: "<substring-match>:<import-class>"
# e.g. "telegram/messenger/AndroidUtilities.java:ui.actionbar.Theme"
KNOWN_EXCEPTIONS=(
  "AndroidUtilities.java:ui.actionbar.Theme"
  "PhotoViewer.java:core.ui.RoundedImageView"
  "PhotoViewer.java:ui.actionbar.ActionBar"
  "PhotoViewer.java:ui.actionbar.ActionBarMenu"
  "PhotoViewer.java:ui.actionbar.ActionBarMenuItem"
  "PhotoViewer.java:ui.actionbar.ActionBarMenuSubItem"
  "PhotoViewer.java:ui.actionbar.BackDrawable"
)

if [ ! -d "$TELEGRAM_DIR" ]; then
  echo "OK: telegram/ directory not found (nothing to check)"
  exit 0
fi

# Find all violations
VIOLATIONS=$(rg "^import io\.github\.baba983\.nativedo\.(core|feature|ui)\." \
  "$TELEGRAM_DIR" --type java --type kotlin -n 2>/dev/null || true)

if [ -z "$VIOLATIONS" ]; then
  echo "OK: vendor isolation clean"
  exit 0
fi

# Check which violations are NOT in the known-exceptions list
NEW_VIOLATIONS=""
while IFS= read -r line; do
  # line format: full/path/telegram/messenger/AndroidUtilities.java:7:import io.github...ui.actionbar.Theme
  file_part=$(echo "$line" | sed 's/:.*//')  # full path
  import_part=$(echo "$line" | sed 's/.*import //' | sed 's/;.*//')  # FQN
  # Extract short class name from import for key matching
  import_short=$(echo "$import_part" | sed 's/io\.github\.baba983\.nativedo\.//')
  file_short=$(basename "$file_part")
  key="${file_short}:${import_short}"

  is_known=false
  for exc in "${KNOWN_EXCEPTIONS[@]}"; do
    if [ "$key" = "$exc" ]; then
      is_known=true
      break
    fi
  done

  if [ "$is_known" = false ]; then
    NEW_VIOLATIONS="${NEW_VIOLATIONS}${line}\n"
  fi
done <<< "$VIOLATIONS"

if [ -n "$NEW_VIOLATIONS" ]; then
  echo "FAIL: new vendor-isolation violations found!"
  echo ""
  echo "telegram/ files MUST NOT import NativeDo packages (core.*, feature.*, ui.*)."
  echo "See docs/code-layout.md 'Dependency direction'."
  echo ""
  echo "New violations:"
  echo -e "$NEW_VIOLATIONS"
  exit 1
fi

# All violations are known exceptions
KNOWN_COUNT=$(echo "$VIOLATIONS" | wc -l | tr -d ' ')
echo "OK: vendor isolation clean (${KNOWN_COUNT} known-exception(s) tracked in this script)"
echo "    Remove exceptions as ui/actionbar, ui/components, ui/cells move to telegram/ui/"
exit 0

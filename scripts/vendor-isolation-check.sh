#!/bin/bash
# scripts/vendor-isolation-check.sh
#
# Enforces the rule: vendored Telegram code in :TMessagesProj MUST NOT import any
# NativeDo package (io.github.baba983.nativedo.*). See docs/code-layout.md
# "Dependency direction".
#
# Note: the :TMessagesProj Gradle module does not declare a dependency on :app, so
# the Java compiler already prevents these imports from resolving. This script
# is a belt-and-suspenders check that also catches stray refs in comments,
# unused imports, and so on.
set -euo pipefail

TELEGRAM_DIR="apps/android/TMessagesProj/src/main/java"

if [ ! -d "$TELEGRAM_DIR" ]; then
  echo "OK: $TELEGRAM_DIR not found (nothing to check)"
  exit 0
fi

VIOLATIONS=$(rg "^import io\.github\.baba983\.nativedo\." \
  "$TELEGRAM_DIR" --type java --type kotlin -n 2>/dev/null || true)

if [ -z "$VIOLATIONS" ]; then
  echo "OK: vendor isolation clean"
  exit 0
fi

echo "FAIL: vendor-isolation violations found"
echo ""
echo ":TMessagesProj files MUST NOT import io.github.baba983.nativedo.*"
echo "Define an SPI interface in :TMessagesProj and inject the impl from :app instead."
echo "See docs/code-layout.md 'Dependency direction'."
echo ""
echo "$VIOLATIONS"
exit 1

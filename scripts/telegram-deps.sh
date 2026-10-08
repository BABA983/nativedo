#!/usr/bin/env bash
# scripts/telegram-deps.sh — materialize the pinned native dependency trees of
# the `apps/android/telegram` subtree so a fresh clone can build the library.
#
# The SHAs come from the subtree's gitlinks (via git history); each tree is
# fetched from its own `.gitmodules` remote and extracted with `git archive`.
# Idempotent: a tree already stamped at the pin is skipped. No prebuilt `.so`
# is copied from any other worktree.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
exec python3 "$ROOT/scripts/telegram_deps.py" "$@"

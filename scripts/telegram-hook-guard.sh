#!/usr/bin/env bash
# scripts/telegram-hook-guard.sh — keep the `apps/android/telegram` subtree
# pristine except for the one-line NativeDoHooks call sites.
#
# Diffs the subtree against its pinned `git subtree` merge commit (found in git
# history). Run before committing any change under apps/android/telegram/**.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export NATIVEDO_ROOT="$ROOT"
exec python3 "$ROOT/scripts/telegram_hook_guard.py" "$@"

#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

python3 <<'PY'
from pathlib import Path

host = Path('apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt').read_text()
app_root = Path('apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt').read_text()
sheet = Path('apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt').read_text()

score = 0
reasons = []

if 'val restoredFromDock: Boolean = false' not in host:
    score += 35
    reasons.append('browser_entry_missing_restore_marker:+35')

if 'browserEntry = session.browserEntry?.copy(' not in host or 'restoredFromDock = true' not in host:
    score += 30
    reasons.append('restore_docked_browser_not_marked_as_restore:+30')

if 'restoredFromDock = entry.restoredFromDock' not in app_root:
    score += 20
    reasons.append('app_root_not_propagating_restore_marker:+20')

if 'restoredFromDock: Boolean = false' not in sheet:
    score += 10
    reasons.append('sheet_container_missing_restore_parameter:+10')

if 'if (restoredFromDock)' not in sheet:
    score += 20
    reasons.append('sheet_container_treats_restore_like_fresh_open:+20')

print('Browser restore continuity diagnostics:')
if reasons:
    for reason in reasons:
        print(f' - {reason}')
else:
    print(' - no currently tracked penalties')
print(f'METRIC browser_restore_continuity_penalty={score}')
PY

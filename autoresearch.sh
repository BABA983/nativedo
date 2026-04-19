#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

python3 <<'PY'
from pathlib import Path

host = Path('apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserHost.kt').read_text()
sheet = Path('apps/android/app/src/main/java/io/github/baba983/nativedo/feature/browser/AppBrowserSheetContainer.kt').read_text()

score = 0
reasons = []

if 'foregroundActivationKey = session.browserEntry.foregroundActivationKey + 1' not in host and 'foregroundActivationKey = session.browserEntry?.foregroundActivationKey?.plus(1)' not in host:
    score += 30
    reasons.append('browser_restore_missing_reactivation_key:+30')

if 'foregroundActivationKey = session.readerEntry.foregroundActivationKey + 1' not in host and 'foregroundActivationKey = session.readerEntry?.foregroundActivationKey?.plus(1)' not in host:
    score += 20
    reasons.append('reader_restore_missing_reactivation_key:+20')

if 'buildAppBrowserSheetRestoreSettleMotionState' not in sheet:
    score += 20
    reasons.append('missing_restore_settle_helper:+20')

if 'restoreSettleProgress' not in sheet or 'animateTo(' not in sheet:
    score += 20
    reasons.append('missing_restore_settle_animation_path:+20')

print('Browser restore settle diagnostics:')
if reasons:
    for reason in reasons:
        print(f' - {reason}')
else:
    print(' - no currently tracked penalties')
print(f'METRIC browser_restore_settle_penalty={score}')
PY

#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

python3 <<'PY'
from pathlib import Path
import re

app_root = Path('apps/android/app/src/main/java/io/github/baba983/nativedo/ui/AppRoot.kt').read_text()
shell_text = Path('apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserTabsShell.kt').read_text()

score = 0
reasons = []

m = re.search(r"fun dismissBrowserTabsManager\(\) \{(.*?)\n        \}", app_root, re.S)
body = m.group(1) if m else ''
if 'BrowserTabsTransitionKind.CloseManager' not in body:
    score += 40
    reasons.append('dismiss_manager_missing_close_transition:+40')

if 'private const val BrowserTabsManagerCloseDurationMillis = 220' in shell_text:
    score += 20
    reasons.append('manager_close_duration_too_short:+20')

if 'lerp(72.dp.toPx(), 0f, openProgress.value)' in shell_text:
    score += 15
    reasons.append('manager_launch_translation_too_large:+15')

if 'alpha = 0.6f + (0.4f * openProgress.value)' in shell_text:
    score += 10
    reasons.append('manager_entry_alpha_too_low:+10')

print('Telegram motion diagnostics:')
if reasons:
    for reason in reasons:
        print(f' - {reason}')
else:
    print(' - no currently tracked penalties')
print(f'METRIC telegram_motion_penalty={score}')
PY

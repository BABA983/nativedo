#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

python3 <<'PY'
from pathlib import Path
text = Path('apps/android/app/src/main/java/io/github/baba983/nativedo/ui/BrowserTabsTransition.kt').read_text()

score = 0
reasons = []

if 'BrowserTabsTransitionKind.CloseManager -> 220' in text:
    score += 40
    reasons.append('close_manager_transition_duration_mismatch:+40')

if 'transition.sourceSurface == BrowserTabsTransitionSurface.Active' not in text and 'transition.targetSurface == BrowserTabsTransitionSurface.Active' not in text:
    score += 50
    reasons.append('active_surface_transitions_still_dim:+50')

print('Browser tabs transition fidelity diagnostics:')
if reasons:
    for reason in reasons:
        print(f' - {reason}')
else:
    print(' - no currently tracked penalties')
print(f'METRIC browser_tabs_transition_fidelity_penalty={score}')
PY

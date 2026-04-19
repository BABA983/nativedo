#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

python3 <<'PY'
from pathlib import Path

root = Path('apps/android/app/src/main/java/io/github/baba983/nativedo')
shell_text = (root / 'ui/BrowserTabsShell.kt').read_text()
transition_text = (root / 'ui/BrowserTabsTransition.kt').read_text()
sheet_text = (root / 'feature/browser/AppBrowserSheetContainer.kt').read_text()


def between(text: str, start: str, end: str) -> str:
    s = text.index(start)
    e = text.index(end, s)
    return text[s:e]


dock_card = between(
    shell_text,
    'private fun BrowserTabsDockCard(',
    '@Composable\ninternal fun BrowserTabsLargePreviewSurface(',
)
large_preview = between(
    shell_text,
    'internal fun BrowserTabsLargePreviewSurface(',
    '@Composable\ninternal fun BrowserTabsLeadingIcon(',
)
transition_dock = between(
    transition_text,
    'private fun BrowserTabsTransitionDockSurface(',
    'private fun browserTabsDismissTargetBounds(',
)

score = 0
reasons = []

if 'BrowserTabsLeadingIcon(' in dock_card:
    score += 25
    reasons.append('dock_card_has_extra_leading_icon:+25')

if 'BrowserTabsLeadingIcon(' in transition_dock:
    score += 20
    reasons.append('transition_dock_has_extra_leading_icon:+20')

if 'BrowserTabsLeadingIcon(' in large_preview:
    score += 20
    reasons.append('manager_header_has_extra_leading_icon:+20')

if 'imageVector = when (preview.kind)' in large_preview:
    score += 20
    reasons.append('manager_header_uses_kind_icon_instead_of_expand:+20')

if 'KeyboardArrowUp' not in large_preview:
    score += 20
    reasons.append('manager_header_missing_expand_chevron:+20')

if 'scale = 0.985f + ((1f - 0.985f) * clampedProgress)' in sheet_text:
    score += 10
    reasons.append('sheet_entry_scale_is_too_modal:+10')

if 'alpha = 0.92f + ((1f - 0.92f) * clampedProgress)' in sheet_text:
    score += 10
    reasons.append('sheet_entry_alpha_is_too_modal:+10')

print('Telegram parity diagnostics:')
if reasons:
    for reason in reasons:
        print(f' - {reason}')
else:
    print(' - no currently tracked penalties')
print(f'METRIC telegram_parity_penalty={score}')
PY

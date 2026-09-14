#!/usr/bin/env bash
# scripts/auto-vendor-copy.sh
# Copy missing vendor classes that javac complains about into :telegram.
#
# Provenance is derived (scripts/vendor-index.sh), so there is nothing to
# register here: a copied file is automatically tracked as an exact copy of its
# upstream path at the pin.
#
# Skips: classes > MAX_LINES or with > MAX_IMPORTS telegram imports.
#
# After the :telegram module migration the destination mirrors the vendor
# package layout exactly (org.telegram.*, me.vkryl.*, com.google.*, etc.) so
# no `sed` package rewrite is needed — files are dropped in as-is.
#
# Usage:
#   scripts/auto-vendor-copy.sh [path-to-javac-error-log]
#   scripts/auto-vendor-copy.sh --dry-run [log]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="${1:-/tmp/javac-out.log}"
DRY_RUN=0

if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=1
  LOG="${2:-/tmp/javac-out.log}"
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
fi

VENDOR_JAVA="$(
PYTHONPATH="$ROOT/scripts" NATIVEDO_ROOT="$ROOT" python3 -c '
from vendorlib import load_manifest, vendor_dir
m = load_manifest()
print(vendor_dir() / m.get("upstream_root", "TMessagesProj/src/main/java"))
'
)"
DEST_JAVA="$ROOT/apps/android/telegram/src/main/java"
MAX_LINES=800
MAX_IMPORTS=10

if [[ ! -f "$LOG" ]]; then
  echo "Log file $LOG not found" >&2
  exit 1
fi

if [[ ! -d "$VENDOR_JAVA" ]]; then
  echo "ERROR: pristine tree missing at $VENDOR_JAVA" >&2
  echo "Run: scripts/vendor-setup.sh" >&2
  exit 1
fi

# Resolve pin for new source-manifest entries.
PIN="$(
  python3 -c "
import sys
sys.path.insert(0, '${ROOT}/scripts')
from vendorlib import load_manifest, normalize_sha, CURRENT_PIN
m = load_manifest('${ROOT}')
raw = (m.get('upstream') or {}).get('pinned_commit')
print(normalize_sha(raw) or CURRENT_PIN)
"
)"

classes=$(
  grep -A4 "error: cannot find symbol" "$LOG" \
    | grep -E "symbol:[[:space:]]*class" \
    | sed -E 's/.*symbol:[[:space:]]*class[[:space:]]+([A-Za-z0-9_]+).*/\1/' \
    | sort -u \
    || true
)

copied=0
skipped_big=0
skipped_deps=0
skipped_missing=0
skipped_exists=0
manifest_inserted=0
manifest_updated=0

# Accumulate JSON lines for a single python manifest update at the end.
TMP_REG="$(mktemp)"
trap 'rm -f "$TMP_REG"' EXIT

for cls in $classes; do
  found=$(find "$VENDOR_JAVA" -name "$cls.java" 2>/dev/null | head -1)
  if [[ -z "$found" ]]; then
    skipped_missing=$((skipped_missing + 1))
    continue
  fi

  rel="${found#"$VENDOR_JAVA"/}"
  destPath="$DEST_JAVA/$rel"
  local_entry="telegram/src/main/java/$rel"
  # upstream field in manifest is relative to upstream_root (no TMessagesProj prefix)
  upstream_entry="$rel"

  if [[ -f "$destPath" ]]; then
    skipped_exists=$((skipped_exists + 1))
    continue
  fi

  lines=$(wc -l < "$found" | tr -d ' ')
  if [[ "$lines" -gt $MAX_LINES ]]; then
    skipped_big=$((skipped_big + 1))
    echo "  SKIP $cls.java ($lines lines, too big)"
    continue
  fi

  imports=$(grep -cE "^import org\.telegram\." "$found" || true)
  if [[ "$imports" -gt $MAX_IMPORTS ]]; then
    skipped_deps=$((skipped_deps + 1))
    echo "  SKIP $cls.java ($imports telegram imports, too many)"
    continue
  fi

  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "  DRY-RUN COPY $cls.java ($lines lines, $imports imports) -> $rel"
    echo "${local_entry}|${upstream_entry}" >>"$TMP_REG"
    copied=$((copied + 1))
    continue
  fi

  mkdir -p "$(dirname "$destPath")"
  cp "$found" "$destPath"
  echo "  COPY $cls.java ($lines lines, $imports imports) -> $rel"
  echo "${local_entry}|${upstream_entry}" >>"$TMP_REG"
  copied=$((copied + 1))
done

if [[ -s "$TMP_REG" && "$DRY_RUN" -eq 0 ]]; then
  stats="$(
    python3 -c "
import sys
sys.path.insert(0, '${ROOT}/scripts')
from vendorlib import load_manifest, save_manifest, upsert_file_entry

root = '${ROOT}'
pin = '${PIN}'
manifest = load_manifest(root)
inserted = updated = unchanged = 0
with open('${TMP_REG}', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        local, upstream = line.split('|', 1)
        action = upsert_file_entry(
            manifest,
            local=local,
            upstream=upstream,
            vendored_at=pin,
            modified=False,
            port_mode='exact',
            notes='auto-vendor-copy: registered from javac missing-symbol',
        )
        if action == 'inserted':
            inserted += 1
        elif action == 'updated':
            updated += 1
        else:
            unchanged += 1
save_manifest(manifest, root)
print(f'{inserted} {updated} {unchanged}')
"
  )"
  manifest_inserted=$(echo "$stats" | awk '{print $1}')
  manifest_updated=$(echo "$stats" | awk '{print $2}')
fi

echo ""
echo "Summary: copied=$copied, skipped_big=$skipped_big, skipped_deps=$skipped_deps, skipped_missing=$skipped_missing, skipped_exists=$skipped_exists"
if [[ "$DRY_RUN" -eq 0 ]]; then
  echo "Manifest source+lock: inserted=$manifest_inserted, updated=$manifest_updated (pin=$PIN)"
else
  echo "Manifest: dry-run (no write)"
fi

if [[ "$copied" -gt 0 && "$DRY_RUN" -eq 0 ]]; then
  echo "Next: scripts/vendor-isolation-check.sh && scripts/vendor-status.sh"
fi

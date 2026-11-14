#!/usr/bin/env bash
#
# Publish a cleaned history of this private repository to a new public remote.
#
# The script mirrors the source repository into a temporary directory, keeps only
# the branch (and tags) being published, rewrites every historical version of the
# personal profile fixtures, runs gitleaks and a PII check over the rewritten
# history, and only then - with --push - pushes the result to the target remote.
#
# It is a dry run by default. Nothing is pushed unless --push is passed.
#
# Usage:
#   scripts/publish-oss.sh [options] <target-remote> <source-branch>
#
# Example:
#   scripts/publish-oss.sh --source . --tag v0.1.0 \
#     git@github.com:BABA983/nativedo.git oss/prepare
#
#   scripts/publish-oss.sh --push --target-branch main \
#     git@github.com:BABA983/nativedo.git main
#
# Options:
#   --source <path-or-url>   Repository to mirror. Default: origin.
#   --target-branch <name>   Branch name in the public repository. Default: the
#                            source branch name.
#   --tag <name>             Keep this tag (repeatable). Tags not named here are
#                            dropped unless --keep-all-tags is used.
#   --keep-all-tags          Keep every tag in the source mirror.
#   --work-dir <dir>         Use this directory instead of a fresh mktemp dir.
#   --keep                   Keep the temporary work directory after the run.
#   --push                   Push after every check passes. Without it: dry run.
#   --no-gitleaks            Skip gitleaks. PII checks still run. Not recommended.
#   -h, --help               Show this help.
#
# Requirements: git, git-filter-repo (pip install git-filter-repo), gitleaks
# (https://github.com/gitleaks/gitleaks), python3.

set -euo pipefail

# Tools installed with `pip install --user` and the gitleaks release tarball live
# here; make the script work in a fresh shell that has not sourced a profile.
if [[ -d "$HOME/.local/bin" ]]; then
  PATH="$HOME/.local/bin:$PATH"
  export PATH
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  printf 'publish-oss: run this from inside the repository\n' >&2
  exit 2
}

SOURCE="origin"
TARGET_BRANCH=""
TAGS=()
KEEP_ALL_TAGS=0
WORK_DIR=""
KEEP=0
PUSH=0
RUN_GITLEAKS=1

usage() {
  sed -n '2,/^# Requirements:/p' "$0" | sed 's/^# \{0,1\}//'
}

die() {
  printf 'publish-oss: %s\n' "$*" >&2
  exit 1
}

note() {
  printf '\n== %s\n' "$*"
}

while (($#)); do
  case "$1" in
    --source)
      (($# >= 2)) || die "--source needs a value"
      SOURCE="$2"
      shift 2
      ;;
    --target-branch)
      (($# >= 2)) || die "--target-branch needs a value"
      TARGET_BRANCH="$2"
      shift 2
      ;;
    --tag)
      (($# >= 2)) || die "--tag needs a value"
      TAGS+=("$2")
      shift 2
      ;;
    --keep-all-tags)
      KEEP_ALL_TAGS=1
      shift
      ;;
    --work-dir)
      (($# >= 2)) || die "--work-dir needs a value"
      WORK_DIR="$2"
      shift 2
      ;;
    --keep)
      KEEP=1
      shift
      ;;
    --push)
      PUSH=1
      shift
      ;;
    --no-gitleaks)
      RUN_GITLEAKS=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      die "unknown option: $1"
      ;;
    *)
      break
      ;;
  esac
done

(($# == 2)) || {
  usage
  exit 2
}

TARGET="$1"
BRANCH="$2"
[[ -n "$TARGET_BRANCH" ]] || TARGET_BRANCH="$BRANCH"

command -v git >/dev/null 2>&1 || die "git is required"
command -v python3 >/dev/null 2>&1 || die "python3 is required"
command -v git-filter-repo >/dev/null 2>&1 || die "git-filter-repo is required (pip install git-filter-repo)"
if ((RUN_GITLEAKS)); then
  command -v gitleaks >/dev/null 2>&1 || die "gitleaks is required (https://github.com/gitleaks/gitleaks)"
fi

GITLEAKS_CONFIG="$REPO_ROOT/.gitleaks.toml"
[[ -f "$GITLEAKS_CONFIG" ]] || die "missing $GITLEAKS_CONFIG"

SANITIZED_DIR="$REPO_ROOT/fixtures/discourse"
SANITIZED_FULL="$SANITIZED_DIR/user-profile-full.json"
SANITIZED_PREFS="$SANITIZED_DIR/user-preferences.json"
SANITIZED_BADGES="$SANITIZED_DIR/user-badges.json"
for f in "$SANITIZED_FULL" "$SANITIZED_PREFS" "$SANITIZED_BADGES"; do
  [[ -f "$f" ]] || die "missing sanitized fixture: $f"
done

# Never use a replacement file that still contains the known private values.
if grep -E -q 'example_user|example|192\.0\.|192\.0\.|192\.0\.|192\.0\.|192\.0\.|192\.0\.|192\.0\.|192\.0\.|192\.0\.|192\.0\.|192\.0\.|(^|[^0-9])27023([^0-9]|$)|(^|[^A-Za-z])example_user([^A-Za-z]|$)' \
  "$SANITIZED_FULL" "$SANITIZED_PREFS" "$SANITIZED_BADGES"; then
  die "a sanitized fixture still contains known private values; refusing to rewrite"
fi

# Resolve a remote name (the default `origin`) to its URL; a local path or a URL
# is used as-is.
if git -C "$REPO_ROOT" remote get-url "$SOURCE" >/dev/null 2>&1; then
  SOURCE_URL="$(git -C "$REPO_ROOT" remote get-url "$SOURCE")"
else
  SOURCE_URL="$SOURCE"
fi

# Refuse to publish back into the private source repository.
normalize_remote() {
  local url="$1"
  url="${url%.git}"
  url="${url%/}"
  printf '%s' "$url" | tr '[:upper:]' '[:lower:]'
}
TARGET_NORM="$(normalize_remote "$TARGET")"
if [[ "$(normalize_remote "$SOURCE_URL")" == "$TARGET_NORM" ]]; then
  die "target is the private source repository; refusing to publish there"
fi
if [[ "$SOURCE" != "origin" && -d "$SOURCE" ]]; then
  LOCAL_SOURCE_URL="$(git -C "$SOURCE" remote get-url origin 2>/dev/null || true)"
  if [[ -n "$LOCAL_SOURCE_URL" && "$(normalize_remote "$LOCAL_SOURCE_URL")" == "$TARGET_NORM" ]]; then
    die "target is the private source repository; refusing to publish there"
  fi
fi

if [[ -z "$WORK_DIR" ]]; then
  WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/nativedo-oss.XXXXXX")"
  CREATED_WORK_DIR=1
else
  mkdir -p "$WORK_DIR"
  CREATED_WORK_DIR=0
fi
# filter-repo runs with the mirror as its working directory; every path handed to
# it must be absolute.
WORK_DIR="$(cd "$WORK_DIR" && pwd)"
if (( CREATED_WORK_DIR && ! KEEP )); then
  trap 'rm -rf "$WORK_DIR"' EXIT
fi

MIRROR="$WORK_DIR/repo.git"
GITLEAKS_REPORT="$WORK_DIR/gitleaks.json"
PII_LOG="$WORK_DIR/pii-check.txt"
BLOB_CALLBACK="$WORK_DIR/blob_callback.py"
REPLACEMENTS="$WORK_DIR/replace-text.txt"
MESSAGE_REPLACEMENTS="$WORK_DIR/replace-message.txt"

note "Mirroring $SOURCE ($SOURCE_URL)"
rm -rf "$MIRROR"
git clone --mirror --quiet "$SOURCE_URL" "$MIRROR"

git -C "$MIRROR" rev-parse --verify --quiet "refs/heads/$BRANCH^{commit}" >/dev/null ||
  die "source branch '$BRANCH' does not exist in $SOURCE"

note "Keeping refs/heads/$BRANCH"
KEEP_REFS=("refs/heads/$BRANCH")
if ((KEEP_ALL_TAGS)); then
  while IFS= read -r ref; do
    [[ -n "$ref" ]] && KEEP_REFS+=("$ref")
  done < <(git -C "$MIRROR" for-each-ref --format='%(refname)' refs/tags)
else
  for tag in "${TAGS[@]}"; do
    ref="refs/tags/$tag"
    git -C "$MIRROR" rev-parse --verify --quiet "$ref" >/dev/null ||
      die "tag '$tag' does not exist in $SOURCE"
    KEEP_REFS+=("$ref")
  done
fi

while IFS= read -r ref; do
  keep=0
  for wanted in "${KEEP_REFS[@]}"; do
    if [[ "$ref" == "$wanted" ]]; then
      keep=1
      break
    fi
  done
  if (( ! keep )); then
    git -C "$MIRROR" update-ref -d "$ref" || true
  fi
done < <(git -C "$MIRROR" for-each-ref --format='%(refname)')
git -C "$MIRROR" symbolic-ref HEAD "refs/heads/$BRANCH"

# The callback replaces each known fixture with the sanitized version from this
# checkout. Content signatures are used because filter-repo's blob callback does
# not receive the path.
cat >"$BLOB_CALLBACK" <<'PY'
import os

def read(path):
    with open(path, 'rb') as handle:
        return handle.read()

full = read(os.environ['NATIVEDO_SANITIZED_FULL'])
prefs = read(os.environ['NATIVEDO_SANITIZED_PREFS'])
badges = read(os.environ['NATIVEDO_SANITIZED_BADGES'])

data = blob.data
if b'"user_auth_tokens"' in data and b'"associated_accounts"' in data:
    blob.data = full
elif b'"user_option"' in data and b'"mailing_list_mode"' in data:
    blob.data = prefs
elif b'"user_badges"' in data and b'"granted_by_id"' in data:
    blob.data = badges
PY

# Fallback for copies outside the fixtures: the known email, the 27 login-record
# IPs (13 distinct addresses), the old username and the old user id. The IP
# patterns are anchored so a longer address cannot be partially replaced.
{
  printf '%s\n' 'user@example.com==>user@example.com'
  printf '%s\n' 'regex:(?i)example_user==>example_user'
  # Capital-D example_user is the account name; lowercase "daud" is deliberately not a
  # global rule because it also matches "dAudio" in upstream C++ identifiers.
  printf '%s\n' 'example_user==>example_user'
  i=0
  for ip in \
    192.0.2.1 \
    192.0.2.2 \
    192.0.2.3 \
    192.0.2.4 \
    192.0.2.5 \
    192.0.2.6 \
    192.0.2.7 \
    192.0.2.8 \
    192.0.2.9 \
    192.0.2.10 \
    192.0.2.11 \
    192.0.2.12 \
    192.0.2.13; do
    i=$((i + 1))
    escaped="${ip//./\\.}"
    printf 'regex:\\b%s\\b==>192.0.2.%d\n' "$escaped" "$i"
  done
} >"$REPLACEMENTS"

# Commit messages need the same scrub, plus the real address that appears only in
# Co-authored-by trailers and the lowercase account name. Blob replacement cannot
# use a bare lowercase "daud" rule because it also matches "dAudio" in upstream
# C++ identifiers; commit messages have no such identifier.
cp "$REPLACEMENTS" "$MESSAGE_REPLACEMENTS"
{
  printf '%s\n' 'user@example.com==>user@example.com'
  printf '%s\n' 'daud==>example_user'
} >>"$MESSAGE_REPLACEMENTS"

note "Rewriting history (fixtures + fallback replacements)"
export NATIVEDO_SANITIZED_FULL="$SANITIZED_FULL"
export NATIVEDO_SANITIZED_PREFS="$SANITIZED_PREFS"
export NATIVEDO_SANITIZED_BADGES="$SANITIZED_BADGES"
# filter-repo parses `git config --list` as key=value lines and trips over the
# orb's multi-line credential helper. The mirror needs no user/global config.
GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null \
  git -C "$MIRROR" filter-repo --force \
  --blob-callback "$BLOB_CALLBACK" \
  --replace-text "$REPLACEMENTS" \
  --replace-message "$MESSAGE_REPLACEMENTS"

git -C "$MIRROR" reflog expire --expire=now --all
git -C "$MIRROR" gc --prune=now --quiet

COMMIT_COUNT="$(git -C "$MIRROR" rev-list --count "refs/heads/$BRANCH")"
note "Rewritten history: $COMMIT_COUNT commits on $BRANCH"

if ((RUN_GITLEAKS)); then
  note "gitleaks over the rewritten history"
  set +e
  gitleaks git \
    --config "$GITLEAKS_CONFIG" \
    --redact \
    --no-banner \
    --report-format json \
    --report-path "$GITLEAKS_REPORT" \
    --exit-code 1 \
    "$MIRROR" >"$WORK_DIR/gitleaks.out" 2>&1
  gitleaks_status=$?
  set -e
  cat "$WORK_DIR/gitleaks.out"
  if ((gitleaks_status != 0)); then
    printf '\npublish-oss: gitleaks reported findings; see %s\n' "$GITLEAKS_REPORT" >&2
    exit 1
  fi
  if [[ -s "$GITLEAKS_REPORT" ]]; then
    findings="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))))' "$GITLEAKS_REPORT" 2>/dev/null || echo '?')"
    printf 'gitleaks: %s finding(s) in report (exit code 0)\n' "$findings"
  else
    printf 'gitleaks: no findings\n'
  fi
fi

note "PII check over the rewritten history"
python3 - "$MIRROR" "$SANITIZED_FULL" "$SANITIZED_PREFS" "$SANITIZED_BADGES" <<'PY' | tee "$PII_LOG"
import re
import subprocess
import sys

repo, full_path, prefs_path, badges_path = sys.argv[1:5]

known_ips = [
    b'192.0.2.1', b'192.0.2.2', b'192.0.2.3', b'192.0.2.4',
    b'192.0.2.5', b'192.0.2.6', b'192.0.2.7', b'192.0.2.8',
    b'192.0.2.9', b'192.0.2.10', b'192.0.2.11', b'192.0.2.12',
    b'192.0.2.13',
]
email_re = re.compile(rb'example_user(?:@example\.com|983@gmail\.com)', re.I)
word_re = re.compile(rb'example_user', re.I)
ip_re = re.compile(rb'(?<![0-9])(' + b'|'.join(re.escape(ip) for ip in known_ips) + rb')(?![0-9])')
client_ip_re = re.compile(rb'"client_ip"\s*:\s*"([^"]+)"')
doc_ip_re = re.compile(rb'^(?:192\.0\.2\.|198\.51\.100\.|203\.0\.113\.)')

def git(*args, input_bytes=None):
    return subprocess.run(
        ['git', '-C', repo, *args],
        input=input_bytes,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
    ).stdout

def read_file(path):
    with open(path, 'rb') as handle:
        return handle.read()

sanitized = {
    full_path: read_file(full_path),
    prefs_path: read_file(prefs_path),
    badges_path: read_file(badges_path),
}
fixture_rel = {
    full_path: 'fixtures/discourse/user-profile-full.json',
    prefs_path: 'fixtures/discourse/user-preferences.json',
    badges_path: 'fixtures/discourse/user-badges.json',
}

# Every object reachable from every kept ref.
object_ids = []
for line in git('rev-list', '--objects', '--all').splitlines():
    fields = line.split(maxsplit=1)
    if fields:
        object_ids.append(fields[0])

blobs = {}
proc = subprocess.Popen(
    ['git', '-C', repo, 'cat-file', '--batch'],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
)
assert proc.stdin is not None and proc.stdout is not None
for oid in object_ids:
    proc.stdin.write(oid + b'\n')
    proc.stdin.flush()
    header = proc.stdout.readline().split()
    if len(header) < 3:
        continue
    kind, size = header[1], int(header[2])
    data = proc.stdout.read(size)
    proc.stdout.read(1)  # trailing newline
    if kind == b'blob':
        blobs[oid] = data
proc.stdin.close()
proc.wait()

failures = []
for oid, data in blobs.items():
    if email_re.search(data) or word_re.search(data):
        failures.append(('known email/name', oid.decode()))
    if ip_re.search(data):
        failures.append(('known client IP', oid.decode()))

client_ip_blobs = {oid: data for oid, data in blobs.items() if b'"client_ip"' in data}
if client_ip_blobs:
    expected = sanitized[full_path]
    for oid, data in client_ip_blobs.items():
        if data != expected:
            failures.append(('client_ip outside the sanitized fixture', oid.decode()))
            continue
        for ip in client_ip_re.findall(data):
            if not doc_ip_re.match(ip):
                failures.append(('non-documentation client_ip ' + ip.decode(errors='replace'), oid.decode()))

# Every historical version of each fixture must be the sanitized blob.
for path, expected in sanitized.items():
    rel = fixture_rel[path]
    expected_oid = git('hash-object', '--stdin', input_bytes=expected).strip()
    oids = set()
    for line in git('rev-list', '--objects', '--all', '--', rel).splitlines():
        fields = line.split(maxsplit=1)
        if fields:
            oids.add(fields[0])
    blob_oids = {oid for oid in oids if oid in blobs}
    if not blob_oids:
        failures.append(('fixture missing from rewritten history', rel))
    for oid in sorted(blob_oids):
        if oid != expected_oid:
            failures.append(('stale fixture version', rel + ' ' + oid.decode()))
    print('fixture %s: %d historical blob(s), all sanitized' % (rel, len(blob_oids)))

# Commit metadata must not carry the known address either.
metadata = git('log', '--all', '--format=%ae%n%ce%n%B')
for label, pattern in (('commit email', email_re), ('commit text', word_re), ('commit IP', ip_re)):
    if pattern.search(metadata):
        failures.append((label, 'commit metadata'))

print('scanned %d unique blobs, %d commits' % (len(blobs), len(git('rev-list', '--all').splitlines())))
for label in ('known email/name', 'known client IP', 'client_ip outside the sanitized fixture',
              'non-documentation client_ip', 'fixture missing from rewritten history',
              'stale fixture version', 'commit email', 'commit text', 'commit IP'):
    count = sum(1 for failure in failures if failure[0] == label)
    print('  %-45s %d' % (label + ':', count))
if failures:
    print('\nPII check FAILED:', file=sys.stderr)
    for label, detail in failures[:40]:
        print('  %s: %s' % (label, detail), file=sys.stderr)
    if len(failures) > 40:
        print('  ... %d more' % (len(failures) - 40), file=sys.stderr)
    sys.exit(1)
print('PII check: clean')
PY

if ((PUSH)); then
  note "Pushing to $TARGET"
  git -C "$MIRROR" remote remove origin >/dev/null 2>&1 || true
  git -C "$MIRROR" remote add target "$TARGET"
  git -C "$MIRROR" push target "refs/heads/$BRANCH:refs/heads/$TARGET_BRANCH"
  if ((KEEP_ALL_TAGS)); then
    git -C "$MIRROR" push --tags target
  else
    for tag in "${TAGS[@]}"; do
      git -C "$MIRROR" push target "refs/tags/$tag"
    done
  fi
  printf '\npushed %s -> %s (%s)\n' "$BRANCH" "$TARGET" "$TARGET_BRANCH"
else
  note "Dry run: nothing pushed"
  printf 'would push refs/heads/%s -> %s refs/heads/%s\n' "$BRANCH" "$TARGET" "$TARGET_BRANCH"
  if ((KEEP_ALL_TAGS)); then
    printf 'would push all kept tags\n'
  else
    for tag in "${TAGS[@]}"; do
      printf 'would push refs/tags/%s\n' "$tag"
    done
  fi
  printf 'rewritten mirror: %s\n' "$MIRROR"
  printf 're-run with --push to publish\n'
fi

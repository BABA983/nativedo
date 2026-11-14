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
# The replacement set is derived from the historical user-profile-full.json in the
# source mirror, not hardcoded here. That keeps the published script free of the
# private addresses and lets the same script handle a later capture.
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
  python3 -m json.tool "$f" >/dev/null || die "not valid JSON: $f"
done

# The replacement fixture must itself be synthetic: example.com email addresses
# and RFC 5737 client addresses. This prevents accidentally using a real capture
# as the replacement.
python3 - "$SANITIZED_FULL" <<'PY'
import json
import re
import sys

data = json.load(open(sys.argv[1]))
user = data.get('user') or {}
email = user.get('email')
if email and not re.search(r'@example\.(com|org|net)$', email):
    raise SystemExit('sanitized full fixture has a non-example email')
for token in user.get('user_auth_tokens') or []:
    if not isinstance(token, dict):
        continue
    ip = token.get('client_ip')
    if ip and not re.match(r'^(192\.0\.2\.|198\.51\.100\.|203\.0\.113\.)', ip):
        raise SystemExit('sanitized full fixture has a non-documentation client_ip')
PY

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
PII_PATTERNS="$WORK_DIR/pii-patterns.json"
FILE_INFO_CALLBACK="$WORK_DIR/file-info-callback.py"
REPLACEMENTS="$WORK_DIR/replace-text.txt"
MESSAGE_REPLACEMENTS="$WORK_DIR/replace-message.txt"
ORIGINAL_FULL="$WORK_DIR/original-user-profile-full.json"

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

# Files we authored and do not want the history rewrite to touch. Their blob IDs
# are compared after the rewrite; this catches a callback that matches by content
# and accidentally replaces one of them.
PROTECTED_PATHS=()
PROTECTED_OIDS=()
for path in scripts/publish-oss.sh .gitleaks.toml LICENSE; do
  if oid="$(git -C "$MIRROR" rev-parse "refs/heads/$BRANCH:$path" 2>/dev/null)"; then
    PROTECTED_PATHS+=("$path")
    PROTECTED_OIDS+=("$oid")
  fi
done

# The first commit that added the fixture holds the original capture. The branch
# tip is already sanitized, so it cannot be used as the source of the PII set.
ORIGINAL_COMMIT="$(git -C "$MIRROR" log --all --diff-filter=A --format=%H -- \
  fixtures/discourse/user-profile-full.json | tail -n 1)"
[[ -n "$ORIGINAL_COMMIT" ]] ||
  die "cannot find the commit that added fixtures/discourse/user-profile-full.json"
git -C "$MIRROR" show "$ORIGINAL_COMMIT:fixtures/discourse/user-profile-full.json" >"$ORIGINAL_FULL"

note "Deriving the PII set from $ORIGINAL_COMMIT"
NATIVEDO_ORIGINAL_FULL="$ORIGINAL_FULL" \
NATIVEDO_SANITIZED_FULL="$SANITIZED_FULL" \
NATIVEDO_SANITIZED_PREFS="$SANITIZED_PREFS" \
NATIVEDO_SANITIZED_BADGES="$SANITIZED_BADGES" \
NATIVEDO_WORK_DIR="$WORK_DIR" \
NATIVEDO_MIRROR="$MIRROR" \
python3 - <<'PY'
import json
import os
import re
import subprocess

work = os.environ['NATIVEDO_WORK_DIR']
repo = os.environ['NATIVEDO_MIRROR']
original = json.load(open(os.environ['NATIVEDO_ORIGINAL_FULL']))
user = original.get('user') or {}

emails = set()
for value in [user.get('email')] + [
    account.get('description')
    for account in (user.get('associated_accounts') or [])
    if isinstance(account, dict)
]:
    if isinstance(value, str) and '@' in value:
        emails.add(value.strip())

# Co-authored-by trailers can carry an address whose local part extends the
# fixture's own address (for example a personal Gmail next to the forum email).
log = subprocess.run(
    ['git', '-C', repo, 'log', '--all', '--format=%B'],
    stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=True,
).stdout.decode('utf-8', 'replace')
message_emails = set(re.findall(r'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}', log))
fixture_locals = {email.split('@', 1)[0] for email in emails if '@' in email}
for email in message_emails:
    local = email.split('@', 1)[0]
    if any(local.startswith(prefix) for prefix in fixture_locals if prefix):
        emails.add(email)

def private_email(email):
    lower = email.lower()
    if lower.endswith(('@example.com', '@example.org', '@example.net')):
        return False
    if lower.endswith('@users.noreply.github.com'):
        return False
    if lower in ('amp@ampcode.com', 'noreply@anthropic.com'):
        return False
    if lower.startswith('noreply@'):
        return False
    return True

emails = sorted(email for email in emails if private_email(email))
email_locals = sorted({email.split('@', 1)[0] for email in emails if '@' in email})

username = user.get('username')
usernames = []
if isinstance(username, str) and username and username.lower() not in (
    'example_user', 'example user', 'system', 'example_inviter'
):
    usernames = [username]

def private_ip(ip):
    return not re.match(
        r'^(192\.0\.2\.|198\.51\.100\.|203\.0\.113\.|127\.|10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)',
        ip,
    )

ips = sorted({
    token.get('client_ip')
    for token in (user.get('user_auth_tokens') or [])
    if isinstance(token, dict) and token.get('client_ip')
})
ips = [ip for ip in ips if private_ip(ip)]

patterns = {
    'emails': emails,
    'email_locals': email_locals,
    'usernames': usernames,
    'ips': ips,
}
with open(os.path.join(work, 'pii-patterns.json'), 'w', encoding='utf-8') as handle:
    json.dump(patterns, handle, indent=2)
    handle.write('\n')

blob_rules = []
for email in emails:
    blob_rules.append('%s==>user@example.com' % email)
for local in email_locals:
    blob_rules.append('regex:(?i)%s==>example_user' % re.escape(local))
for name in usernames:
    blob_rules.append('%s==>example_user' % name)
for index, ip in enumerate(ips, 1):
    blob_rules.append('regex:\\b%s\\b==>192.0.2.%d' % (re.escape(ip), index))

with open(os.path.join(work, 'replace-text.txt'), 'w', encoding='utf-8') as handle:
    handle.write('\n'.join(blob_rules) + ('\n' if blob_rules else ''))

message_rules = list(blob_rules)
for name in usernames:
    message_rules.append('%s==>example_user' % name.lower())
with open(os.path.join(work, 'replace-message.txt'), 'w', encoding='utf-8') as handle:
    handle.write('\n'.join(message_rules) + ('\n' if message_rules else ''))

callback = r'''
if mode == b'160000':
    return (filename, mode, blob_id)
if 'sanitized' not in value.data:
    import os
    def read(path):
        with open(path, 'rb') as handle:
            return handle.read()
    value.data['sanitized'] = {
        b'fixtures/discourse/user-profile-full.json': read(os.environ['NATIVEDO_SANITIZED_FULL']),
        b'fixtures/discourse/user-preferences.json': read(os.environ['NATIVEDO_SANITIZED_PREFS']),
        b'fixtures/discourse/user-badges.json': read(os.environ['NATIVEDO_SANITIZED_BADGES']),
    }
original = value.get_contents_by_identifier(blob_id)
contents = value.data['sanitized'].get(filename, original)
if value.is_binary(contents):
    return (filename, mode, blob_id)
contents = value.apply_replace_text(contents)
if contents == original:
    return (filename, mode, blob_id)
return (filename, mode, value.insert_file_with_contents(contents))
'''
with open(os.path.join(work, 'file-info-callback.py'), 'w', encoding='utf-8') as handle:
    handle.write(callback)

print('PII set: %d email(s), %d local part(s), %d username(s), %d IP(s)' % (
    len(emails), len(email_locals), len(usernames), len(ips)))
PY

note "Rewriting history (fixture paths + derived replacements)"
export NATIVEDO_SANITIZED_FULL="$SANITIZED_FULL"
export NATIVEDO_SANITIZED_PREFS="$SANITIZED_PREFS"
export NATIVEDO_SANITIZED_BADGES="$SANITIZED_BADGES"
# filter-repo parses `git config --list` as key=value lines and trips over the
# orb's multi-line credential helper. The mirror needs no user/global config.
GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null \
  git -C "$MIRROR" filter-repo --force \
  --file-info-callback "$FILE_INFO_CALLBACK" \
  --replace-text "$REPLACEMENTS" \
  --replace-message "$MESSAGE_REPLACEMENTS"

git -C "$MIRROR" reflog expire --expire=now --all
git -C "$MIRROR" gc --prune=now --quiet

for i in "${!PROTECTED_PATHS[@]}"; do
  path="${PROTECTED_PATHS[$i]}"
  new_oid="$(git -C "$MIRROR" rev-parse "refs/heads/$BRANCH:$path" 2>/dev/null || true)"
  if [[ "$new_oid" != "${PROTECTED_OIDS[$i]}" ]]; then
    die "$path changed during the history rewrite; refusing to publish"
  fi
done

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
python3 - "$MIRROR" "$PII_PATTERNS" "$SANITIZED_FULL" "$SANITIZED_PREFS" "$SANITIZED_BADGES" <<'PY' | tee "$PII_LOG"
import json
import os
import re
import subprocess
import sys

repo, patterns_path, full_path, prefs_path, badges_path = sys.argv[1:6]
patterns = json.load(open(patterns_path))
emails = [pattern.encode() for pattern in patterns.get('emails', [])]
locals_ = [pattern.encode() for pattern in patterns.get('email_locals', [])]
usernames = [pattern.encode() for pattern in patterns.get('usernames', [])]
ips = [pattern.encode() for pattern in patterns.get('ips', [])]

email_res = [re.compile(re.escape(value), re.I) for value in emails + locals_]
username_res = [re.compile(re.escape(value)) for value in usernames]
lower_username_res = [re.compile(re.escape(value.lower())) for value in usernames]
ip_res = [
    re.compile(rb'(?<![0-9])' + re.escape(value) + rb'(?![0-9])')
    for value in ips
]
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
    if any(pattern.search(data) for pattern in email_res):
        failures.append(('known email/name', oid.decode()))
    if any(pattern.search(data) for pattern in username_res):
        failures.append(('known username', oid.decode()))
    if any(pattern.search(data) for pattern in ip_res):
        failures.append(('known client IP', oid.decode()))

client_ip_blobs = {
    oid: data
    for oid, data in blobs.items()
    if b'"client_ip"' in data
    and b'"user_auth_tokens"' in data
    and b'"associated_accounts"' in data
}
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
for label, pattern_list in (
    ('commit email', email_res),
    ('commit username', username_res + lower_username_res),
    ('commit IP', ip_res),
):
    if any(pattern.search(metadata) for pattern in pattern_list):
        failures.append((label, 'commit metadata'))

print('scanned %d unique blobs, %d commits' % (len(blobs), len(git('rev-list', '--all').splitlines())))
for label in ('known email/name', 'known username', 'known client IP',
              'client_ip outside the sanitized fixture', 'non-documentation client_ip',
              'fixture missing from rewritten history', 'stale fixture version',
              'commit email', 'commit username', 'commit IP'):
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

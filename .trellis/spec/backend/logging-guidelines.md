# Logging Guidelines

> How logging is done in this project.

---

## Overview

Logging is lightweight and client-side. The project wraps `android.util.Log` behind a small internal logger rather than using Timber or a structured logging framework.

---

## Log Levels

Available levels in `NativeDoLogger`:

- `Debug` - verbose development diagnostics such as payload/media summaries
- `Info` - informational events, currently also gated behind debug logging
- `Warning` - recoverable issues worth surfacing during diagnosis
- `Error` - actual failures with attached throwable when available

Example:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/log/NativeDoLogger.kt`

---

## Structured Logging

Logs are not JSON-structured. The current convention is:

- log through helper functions like `debugLog`, `warnLog`, `errorLog`
- use typed tags via `NativeDoLogTag`
- keep message formatting explicit and grep-friendly

Examples:

- `NativeDoLogTag.App`, `NativeDoLogTag.Http`, `NativeDoLogTag.Session`
- `formatPostMediaDebugSummary(...)` in `core/network/DiscourseApiClient.kt`

---

## What to Log

- session/auth transition diagnostics
- HTTP/media parsing diagnostics that help compare app behavior with Linux.do / Telegram references
- network failures where stack traces help debugging
- summaries of payload characteristics rather than dumping entire objects blindly

Examples:

- `core/network/DiscourseApiClient.kt`
- `core/network/NetworkDebugLoggingInterceptor.kt`

---

## What NOT to Log

- do not log cookies, CSRF tokens, or raw session secrets
- do not log full persisted snapshots if they may contain auth/session data
- do not add a second logging stack such as Timber without an explicit decision

---

## Common Mistakes

- Avoid ad-hoc `println` calls in app code when `NativeDoLogger` already covers the case.
- Be aware that the shared logger is currently created with debug logging enabled; do not assume production-safe verbosity unless that behavior is revisited.
- Prefer concise summaries over full-body payload dumps.
# Directory Structure

> How backend code is organized in this project.

---

## Overview

This repository does not currently contain an active backend service. The shipped implementation is an Android client under `apps/android`, and backend-facing behavior is implemented as client-side network code against Discourse endpoints.

Use this guideline to prevent AI tools from inventing a nonexistent server architecture.

---

## Directory Layout

```text
No dedicated backend source tree exists yet.

Current backend-adjacent code lives in:
- apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/
- apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/
- contracts/discourse/
- fixtures/discourse/
```

---

## Module Organization

- Treat `core/network/` as the home for HTTP contracts, DTOs, and client transport code.
- Treat `core/session/` as the home for session/bootstrap/auth coordination logic on the client.
- Treat `contracts/discourse/` as the source of truth for endpoint semantics.
- Treat `fixtures/discourse/` as sample payloads that should stay aligned with the contracts.

If a real backend is introduced later, create new backend guidelines based on the actual service code rather than extending this placeholder with guesses.

---

## Naming Conventions

Current backend-adjacent naming is client-oriented:

- Retrofit interfaces end in `Service`
- transport/client wrappers end in `Client`
- persistence/state holders end in `Store` or `Coordinator`

Examples include `DiscourseApiService`, `DiscourseApiClient`, `SessionCoordinator`, and `SessionStore`.

---

## Examples

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiService.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiClient.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/`
- `contracts/discourse/`

---

## Common Mistakes

- Do not document imaginary `src/server` or `api/` layouts that do not exist.
- Do not describe repository patterns from unrelated stacks such as Express, Spring, or Rails.
- Do not treat client-side Discourse integration code as if it were a standalone backend service.
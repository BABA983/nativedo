# Error Handling

> How errors are handled in this project.

---

## Overview

Because this repo has no standalone backend service, the most important backend-adjacent error handling happens in the Android network and session layers.

The dominant pattern is:

- catch transport-layer failures near the API client boundary
- classify session-expiry / Cloudflare challenge cases explicitly
- propagate failure to higher layers for user-facing presentation
- convert raw messages into friendlier UI messaging closer to the UI boundary

---

## Error Types

Current explicit error/domain types include:

- `AppError` sealed interface in `core/model/AppError.kt`
- `SessionRecoveryRequiredException` for auth/session recovery flows
- `SessionExpiredReason` in session coordination logic

Examples:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/AppError.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiClient.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/`

---

## Error Handling Patterns

- `DiscourseApiClient` wraps endpoint calls in `runWithSessionRecovery { ... }`.
- `HttpException` is inspected for server-logged-out and Cloudflare challenge cases.
- Session invalidation is coordinated centrally instead of being handled ad hoc in every feature.
- Feature/view-model code often consumes `Result<T>` and writes friendly state into `UiState`.
- UI error components convert raw transport messages into clearer human-facing copy.

Examples:

- `core/network/DiscourseApiClient.kt`
- `feature/home/ui/HomeViewModel.kt`
- `core/ui/RequestErrorCard.kt`

---

## API Error Responses

There is no backend API response envelope defined in this repository because the app consumes Discourse APIs rather than serving its own APIs.

When documenting endpoint behavior, update:

- `contracts/discourse/` for the expected contract
- `fixtures/discourse/` for sample payloads

---

## Common Mistakes

- Do not swallow `HttpException` details before session/auth classification has happened.
- Do not expose raw transport failures directly to users when a friendlier presentation helper exists.
- Do not assume `AppError` is fully adopted everywhere; check existing feature patterns before expanding it.
- Avoid reducing every failure to a nullable `error.message` if the calling layer needs stronger semantics.
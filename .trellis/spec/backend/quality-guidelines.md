# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

There is no standalone backend codebase in this repository, so backend quality checks mainly apply to backend-adjacent client code: endpoint contracts, session handling, network DTOs, and fixtures/contracts consistency.

---

## Forbidden Patterns

- Do not invent backend frameworks, modules, or deployment assumptions that do not exist in this repo.
- Do not add a second JSON/network stack when Retrofit + kotlinx.serialization is already the project standard.
- Do not bypass contract docs when changing auth/session-sensitive endpoint behavior.
- Do not add DI/database frameworks by default in backend-adjacent code.

Evidence:

- `AGENTS.md`
- `core/network/DiscourseApiService.kt`
- `core/di/AppContainer.kt`

---

## Required Patterns

- Keep HTTP contracts explicit in `DiscourseApiService` DTOs and method signatures.
- Map external payloads into app models rather than leaking raw transport DTOs broadly.
- Keep session recovery logic centralized.
- When auth/cookie/CSRF/endpoint semantics change, update matching docs in `contracts/discourse/` and `fixtures/discourse/` in the same task when appropriate.

Examples:

- `core/network/DiscourseApiService.kt`
- `core/network/DiscourseApiClient.kt`
- `contracts/discourse/`

---

## Testing Requirements

- Add or update JVM tests for API client behavior, parsers, serialization, and session handling when these areas change.
- Prefer MockWebServer and fixture-backed tests for endpoint behavior.
- Use docs-only verification when only Markdown/spec files changed.

Examples:

- `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/DiscourseApiClientTest.kt`
- `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/BootstrapHtmlParserTest.kt`
- `apps/android/app/build.gradle.kts`

---

## Code Review Checklist

- Did the change preserve Retrofit + kotlinx.serialization conventions?
- Are session-expiry / Cloudflare / logged-out cases still classified correctly?
- If endpoint semantics changed, were `contracts/discourse/` and fixtures updated too?
- Does the change avoid introducing imaginary backend architecture?
- Were relevant unit tests updated?
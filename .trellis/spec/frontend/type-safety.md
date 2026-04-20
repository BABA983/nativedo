# Type Safety

> Type safety patterns in this project.

---

## Overview

The codebase uses Kotlin's type system plus `kotlinx.serialization` for network DTOs and persisted snapshots. External payloads are decoded into DTOs and then mapped into app-facing models rather than leaking raw response objects everywhere.

---

## Type Organization

- DTOs and endpoint contracts live near the network layer, mainly in `core/network/DiscourseApiService.kt`.
- App-facing models live in `core/model/`.
- Feature-specific state snapshots live near their screen code, such as `feature/home/ui/HomeUiState.kt`.
- Persistence stores serialize typed snapshots instead of unstructured maps.

Examples:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiService.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeUiState.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/SessionStore.kt`

---

## Validation

There is no separate validation library like Zod or Yup. Runtime tolerance at system boundaries is mainly handled by `kotlinx.serialization` configuration and explicit parsing logic.

Current patterns:

- `NetworkJson.instance` enables `ignoreUnknownKeys = true` and `explicitNulls = false`
- DTO fields often have defaults to handle partial payloads safely
- custom serializers are used where Discourse payloads are inconsistent
- persistence decoding uses `runCatching { ... }.getOrNull()` when corrupted local data should fail soft

Examples:

- `core/network/NetworkJson.kt`
- `core/network/DiscourseApiService.kt`
- `core/storage/SessionStore.kt`

---

## Common Patterns

- Prefer `sealed interface` for closed app error/state variants when the domain is small and known.
- Prefer data classes for immutable UI snapshots and API/domain models.
- Prefer typed enums for limited UI choices, for example `HomeFeedTab`.
- Map transport-layer DTOs to models before exposing them to feature code.

Examples:

- `core/model/AppError.kt`
- `feature/home/ui/HomeUiState.kt`
- `feature/home/ui/HomeUiState.kt: HomeFeedTab`

---

## Forbidden Patterns

- Do not introduce untyped map-based payload handling when `@Serializable` DTOs are practical.
- Do not pass raw Retrofit DTOs deep into UI code.
- Do not rely on broad nullable soup when a default value, enum, or sealed type communicates intent better.
- Do not add a second JSON stack such as Gson or Moshi without a clear migration decision; this repo already standardizes on `kotlinx.serialization`.
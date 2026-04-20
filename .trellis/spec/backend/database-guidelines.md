# Database Guidelines

> Database patterns and conventions for this project.

---

## Overview

There is no database layer in this repository today. No backend database, Room schema, SQL migration system, or ORM is present in the active codebase.

Persistence is currently lightweight and client-side, mainly through SharedPreferences-backed stores that serialize typed snapshots as JSON.

---

## Query Patterns

Not applicable for now: there are no SQL queries, ORM repositories, or migration-managed tables in this repo.

The closest equivalent persistence pattern is:

- store typed snapshots in a dedicated store object
- delegate load/save to a persistence interface
- use a SharedPreferences-backed implementation in production
- fall back to an in-memory or no-op persistence in tests / no-context scenarios

Examples:

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/SessionStore.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/AppBrowserPreferenceStore.kt`
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt`

---

## Migrations

There is no migration system yet.

If persistence format changes, current practice is to fail soft where possible:

- decoding corrupted stored JSON may return `null`
- stores can rebuild fresh state instead of performing structured migrations

Example:

- `SharedPreferencesSessionStorePersistence.load()` in `core/storage/SessionStore.kt`

---

## Naming Conventions

For current persistence code:

- high-level state holders end in `Store`
- persistence adapters end in `Persistence`
- SharedPreferences-backed implementations are prefixed with `SharedPreferences`
- preference file names use explicit constants in `AppContainer`

Examples:

- `SessionStore`
- `SessionStorePersistence`
- `SharedPreferencesSessionStorePersistence`

---

## Common Mistakes

- Do not introduce Room/SQLite guidance into this file until those tools actually exist in the repo.
- Do not describe migrations, transactions, or query batching that are not implemented.
- Do not confuse network DTO mapping with database persistence.
- When editing existing stores, preserve typed snapshot serialization instead of falling back to unstructured string maps.
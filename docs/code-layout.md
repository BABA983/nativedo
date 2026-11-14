# Code layout and ownership

The active app hosts Telegram's UI, not Telegram's service. This is why product
code is outside the upstream subtree while presentation objects may use upstream
types. See [upstream hosting](upstream-hosting.md) for the host seam.

## One Android build

`apps/android/telegram` is the only Gradle root. It builds the upstream subtree's
`:TMessagesProj` and our `../nativedo` (`:nativedo`) module. A build or test run
against another Gradle root would not check this one.

## Active product code

All paths below are under
`apps/android/nativedo/src/main/java/io/github/baba983/nativedo/`:

- `app/`: Application/Activity, navigation, root tabs and host lifecycle.
- `auth/`: WebView login/recovery and experimental device authorization.
- `core/`: Discourse transport, DTOs/models, session, storage, MessageBus, presence and DI.
- `feature/`: repositories and persisted feature state (drafts/read positions).
- `topic/`, `chat/`, `messages/`, `profile/`, `search/`, `notifications/`,
  `bookmarks/`, `settings/`: product screens and their upstream UI adapters.

[AppContainer](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt)
is the composition root. It owns shared transport, the cookie/session boundary,
repositories, caches and draft stores. Screens consume those objects rather than
creating parallel session or storage systems.

```diagram
┌─────────────────────────────────┐
│ NativeDo screens and adapters   │────▶ Telegram UI / presentation models
└────────────────┬────────────────┘
                 ▼
┌─────────────────────────────────┐
│ Repositories / Discourse client │────▶ linux.do JSON
└────────────────┬────────────────┘
                 ▼
┌─────────────────────────────────┐
│ Session / shared jar / storage  │
└─────────────────────────────────┘
```

`TLRPC`, `MessageObject` and `TL_iv` are legitimate presentation types in active
UI adapters. They are not Discourse domain models and must not leak into wire
contracts or trigger Telegram RPC/storage. Do not introduce a wrapper solely
to hide a type if the existing adapter already owns the translation.

## Language and attribution

Keep upstream Java in Java. Prefer Kotlin for product data/network/state logic;
keep Java for upstream-shaped UI ports when it makes side-by-side comparison
clear. Tests use the existing Kotlin harness. Do not convert existing files
between languages without a concrete benefit.

Copied/adapted Telegram code retains its licensing and attribution; NativeDo code
must not masquerade as an upstream mirror. Keep comments short and local, not
per-file provenance ledgers.

## Legacy archive

The previous `:app` / standalone `TMessagesProj` stack, its vendor overlay,
tooling and Paparazzi goldens were removed after the subtree build replaced them.
They remain at tag `legacy-partial-mirror` for recovery; nothing in the active
tree imports or tests them.

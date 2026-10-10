# NativeDo

A native client for [linux.do](https://linux.do/), built with Telegram's Android UI
and Discourse's APIs. Android is the active target; `apps/ios` is a future scaffold.

Start with the [design index](docs/README.md) for architecture and rationale,
[Discourse contracts](contracts/discourse/README.md) for wire semantics, and
[AGENTS.md](AGENTS.md) for contributor instructions.

## Layout

- `apps/android/nativedo` — **active app**, our product code.
- `apps/android/telegram` — full upstream Telegram subtree and narrow host hooks.
- `contracts/discourse` — endpoint, model and authentication contracts.
- `fixtures/discourse` — sample wire payloads used by tests.
- `apps/android/app` + `apps/android/TMessagesProj` — **legacy build**, still retained.

The two Android builds have separate Gradle roots and test gates. See
[code layout](docs/code-layout.md); do not apply legacy vendor rules to the active subtree.

## Setup and run

Use JDK 17, Android SDK platform/build-tools 36, NDK `27.2.12479018` and CMake
`3.22.1`. Set `ANDROID_HOME` or the SDK path in
`apps/android/telegram/local.properties`. Orb setup and CI install this toolchain.

```sh
# Materialize native dependency trees at the subtree's pinned gitlinks.
./scripts/telegram-deps.sh

# Build the active arm64 APK.
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet
```

Alternatively open `apps/android/telegram` in Android Studio and run `nativedo`.
No separate Telegram clone or copied prebuilt `.so` is needed for this build.
The subtree import is the pin; dependency and hook tools resolve it from Git history.

## Checks

Run Gradle commands sequentially, not in parallel. Test scope and device limitations
are explained in [testing](docs/testing.md).

```sh
# Active app: production UI/network wiring against the fake site on the host JVM.
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDebugUnitTest --quiet
# Narrow a run by appending --tests '<fully-qualified test class>'.

# Upstream hook boundary and dependency tooling.
./scripts/telegram-hook-guard.sh
python3 -m unittest discover -s scripts/tests -t scripts/tests -p 'test_telegram_*.py'
./scripts/telegram-deps.sh --verify
```

`--verify` checks dependency stamps; an unstamped populated tree is unverified,
not proof of a matching or mismatching pin. The CI jobs are in
[ci.yml](.github/workflows/ci.yml).

## Legacy build

Only use these commands when working on `apps/android/app` or the standalone
`apps/android/TMessagesProj`:

```sh
./apps/android/gradlew -p apps/android :app:assembleDebug --quiet
./apps/android/gradlew -p apps/android runUnitAndScreenshotTests --quiet
# For intentional visual changes, record on each supported host before verifying.
./apps/android/gradlew -p apps/android :app:recordPaparazziDebug --quiet
./apps/android/gradlew -p apps/android :app:verifyPaparazziDebug --quiet
./scripts/vendor-guard.sh --compile
```

The [legacy vendor workflow](VENDOR.md) owns overlay, provenance and re-sync rules.
Neither this documentation refactor nor the active build removes the old stack.

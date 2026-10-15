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

Orb setup and the active CI job override upstream's 8 GB Gradle heap with 3 GB,
2 workers, no project parallelism and in-process Kotlin compilation. This is a
memory budget, not a universal optimum or a total-process memory limit. Local
16 GB machines can use the same settings in `~/.gradle/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
org.gradle.workers.max=2
org.gradle.parallel=false
kotlin.compiler.execution.strategy=in-process
```

Orbs below 6 GiB use a smaller 1536 MiB Gradle heap; that does not establish that
the full native build or test suite fits on such an orb. NativeDo tests separately
limit their heap to 3 GB, run one JVM at a time and restart it between classes to
release native-graphics allocations. Full tests are slower, but do not keep
accumulating those allocations in one process.

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

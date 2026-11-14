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

# Build the daily arm64 APK for installation through the phone's package installer.
# An injected ABI otherwise makes AGP mark the APK as test-only (install error -15).
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assembleDailyDebug -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false --quiet
```

Alternatively open `apps/android/telegram` in Android Studio and run `nativedo` (`dailyDebug`).
No separate Telegram clone or copied prebuilt `.so` is needed for this build.
The subtree import is the pin; dependency and hook tools resolve it from Git history.

A variant is a channel (flavor) × a build type:

| Variant | Package | Label | What it is |
| :-- | :-- | :-- | :-- |
| `dailyDebug` | `io.github.baba983.nativedo` | NativeDo | The device build: `src/debug`'s developer menu, glass probe, debug Telegram library |
| `dailyRelease` | same | NativeDo | The distributable build: no dev tools, not `debuggable` |
| `previewDebug` | `io.github.baba983.nativedo.preview` | NativeDo 测试版 | `dailyDebug` under a separate package: own login, drafts and storage |
| `previewRelease` | same as `previewDebug` | NativeDo 测试版 | `dailyRelease` under that separate package |

The channel decides only the package (and with it the label and the install slot next to a
signed-in app); the build type decides the dev tools, `debuggable` and which Telegram library the
build links. The old `preview` build type is therefore two variants now: `previewRelease` when
the trial build should look like what users get, `previewDebug` when it needs the dev tools.

`preview` builds the same code as `daily`; it exists so a build can be compared next to a
signed-in install instead of replacing it:

```sh
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assemblePreviewDebug -Pandroid.injected.build.abi=arm64-v8a -Pandroid.injected.testOnly=false --quiet
```

Non-release APKs carry their commit in `versionName` (`0.1.0-debug-<sha>`,
`0.1.0-preview-debug-<sha>`). `versionCode` stays `1` on purpose: hand-installed builds must
replace each other in either direction, and a bumped code would make installing an older build
fail as a downgrade.

Every variant signs with the shared key when one is configured, which is what lets an
APK from any builder replace the same package in place instead of failing with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` (-7). The key is never committed. The one key every
builder uses is `~/.nativedo/nativedo-signing.jks` (`CN=Android Debug`, SHA-256
`55:73:6E:EC:…:B3:8E:87`, valid to 2055): the Mac's original debug key, copied out of
`~/.android` so clearing that directory cannot change the app's identity. Keep a backup of
it; losing it means every install has to be removed before the next one lands.

Each builder points at its own copy:

- **Mac or another PC** — copy the `.jks`, then write
  `apps/android/nativedo/keystore.properties` (gitignored):

  ```properties
  storeFile=/path/to/nativedo-signing.jks   # absolute, or relative to apps/android/nativedo
  storePassword=...
  keyAlias=...
  keyPassword=...                            # optional; defaults to storePassword
  ```

  The same four values work as `NATIVEDO_KEYSTORE_FILE`, `NATIVEDO_KEYSTORE_PASSWORD`,
  `NATIVEDO_KEY_ALIAS` and `NATIVEDO_KEY_PASSWORD`.
- **Orb** — add the key to the Amp project's secrets as `NATIVEDO_KEYSTORE_B64`
  (`base64 < nativedo-signing.jks | tr -d '\n'`) plus the three password/alias values;
  `.agents/resume` materializes them into the key path and `keystore.properties` on every wake.
- **CI** — the same four names as repository secrets; the workflow materializes them the same way.

A build without this configuration falls back to that machine's Gradle debug key and says so at
error level, because `--quiet` would otherwise hide it. `:nativedo:signingReport` shows which one
a build used: `Config: shared` is the shared key, `Config: debug` is the per-machine fallback.

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
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDailyDebugUnitTest --quiet
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

## License, third-party code and disclaimer

NativeDo's own code is licensed under **GPL-2.0-or-later**. The full license text is
in [LICENSE](LICENSE).

This project is not affiliated with, endorsed by, or sponsored by Telegram. It does
not use Telegram's name or trademarks as its own; "Telegram" appears only to name the
upstream Android UI source this client builds on. That source is the
[DrKLO/Telegram](https://github.com/DrKLO/Telegram) subtree under
`apps/android/telegram`, distributed under GPL-2.0-or-later; its license text stays at
`apps/android/telegram/LICENSE`.

Third-party dependencies declared in `apps/android/nativedo/build.gradle`, and the
libraries vendored by the Telegram subtree, keep their own licenses. See
[THIRD_PARTY.md](THIRD_PARTY.md). Native libraries and other bundled components carry
their own notices from their upstream projects.

NativeDo is an unofficial client for linux.do, provided "as is", without warranty of
any kind. Use it at your own risk and follow linux.do's rules. linux.do is a
third-party service and is not part of this project.

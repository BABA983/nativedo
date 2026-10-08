# NativeDo

NativeDo for Linux.do. This repository keeps the Android app as the active implementation target and reserves space for a future iOS client.

## Layout

- `apps/android` - Android app, the current implementation target.
- `apps/ios` - Future iOS native scaffold.
- `contracts/discourse` - Shared API and auth contracts.
- `fixtures` - Sample payloads used by tests.
- `apps/android/TMessagesProj` - Vendored Telegram-derived sources compiled as the `:TMessagesProj` module (committed; enough to build).
- `apps/android/telegram` - The full upstream Telegram tree imported as a `git subtree` (byte-for-byte), the active native build.
- `apps/android/nativedo` - The linux.do client module, built against `apps/android/telegram`.
- `vendor-overlay.json` - The only hand-maintained vendor file: upstream pin, sync policy, and the exceptions to derived provenance.
- `../telegram` - Local pristine Telegram checkout for diff / re-sync tooling only (outside the repo, gitignored; not required to build).

## Setup

1. Install Android Studio or command-line Android SDK tools.
2. Use JDK 17.
3. Set `ANDROID_HOME` or configure `apps/android/telegram/local.properties` with your Android SDK path. Android Studio usually creates this file automatically.
4. Run `./scripts/telegram-deps.sh` to materialize the full Telegram build's pinned native dependencies.

Telegram's Java sources are committed under `apps/android/telegram`; no separate Telegram clone is needed. Its native dependency trees are prepared by the command above. The older `apps/android/TMessagesProj` skeleton remains available for the legacy `:app` build.

## Run

- Open `apps/android/telegram` in Android Studio and run the `nativedo` configuration.
- Or assemble arm64 from the command line with `./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet`.
- For the legacy `:app` build, open `apps/android` or use `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`.

## Full Telegram subtree (the active build)

The build under active development is our module `apps/android/nativedo` on top of
`apps/android/telegram`: a `git subtree` of upstream Telegram 12.10.5 (upstream
commit `343d9c99`), kept byte-for-byte except for the one-line
`io.github.baba983.nativedo.hooks.NativeDoHooks` call sites. The `git subtree`
merge that imported it is the pin, and `scripts/telegram-deps.sh` and the hook
guard resolve it from git history rather than trusting a manifest or this README.

- Build (arm64): `./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`
- Test: `./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDebugUnitTest`
- Toolchain: platform/build-tools 36, NDK `27.2.12479018`, CMake `3.22.1` — installed by `.agents/setup` and by the CI `nativedo` job.

A fresh clone has upstream's 15 gitlink dependency directories empty (a subtree
import does not carry the submodule content). `scripts/telegram-deps.sh` fetches
each pinned tree from its own `.gitmodules` remote at the commit its gitlink
records and extracts it with `git archive`; no prebuilt `.so` is copied from any
worktree. Run it before a native build — `.agents/setup` does, best-effort.

`scripts/telegram-hook-guard.sh` fails on any change to the subtree that is not a
minimal `NativeDoHooks` callsite, so a render, measurement, gesture or dependency
edit cannot slip in unnoticed. `apps/android/nativedo/README.md` documents the hook
set and the upstream lines it touches.

## Verification

- Active app: `./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDebugUnitTest --quiet`.
- Native dependency and hook tooling: `python3 -m unittest discover -s scripts/tests -t scripts/tests -p 'test_telegram_*.py'`.
- Upstream hook boundary: `./scripts/telegram-hook-guard.sh`.
- The active app's host UI checks exercise production fragments against `FakeDiscourseSite`; device verdicts and retained evidence are recorded in [`docs/device-verification.md`](docs/device-verification.md).

The following checks apply to the legacy `:app` stack, not the active `:nativedo` build:

- Run its shell test suite with `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`.
- Record one local Paparazzi screenshot class with `./scripts/recordScreenshots TopicPostCellScreenshotTest`.
- After recording, verify the local screenshot baselines with `./apps/android/gradlew -p apps/android :app:verifyPaparazziDebug --quiet`.
- Run unit and local screenshot verification together with `./apps/android/gradlew -p apps/android runUnitAndScreenshotTests --quiet`.
- Build a debug APK with `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`.

Paparazzi goldens live in `apps/android/app/src/test/snapshots/<platform>/images` and are committed: `:app:verifyPaparazziDebug` compares against the current host's set, and `:app:recordPaparazziDebug` re-records it for intentional UI changes. There is one set per host platform — `linux` (what CI verifies) and `macos` — because layoutlib's renderer paints differently on each (a Linux golden misses a macOS verify by far more than the 0.1% tolerance), and the `:app` build script points Paparazzi at `snapshots/$platform`. CI runs `runUnitAndScreenshotTests` — the full unit suite plus the pixel comparison — so a golden diff fails the build; record the set for the platform you are on, and let the orb/CI re-record the other. Use device tests for behavior that depends on IME/insets, separate windows, WebView, network images, or gestures.

## Vendored Telegram (optional)

Only needed when you change, audit, or re-sync Telegram-derived code (`:TMessagesProj` or native ports in `:app`).

```bash
# Clone DrKLO/Telegram next to this repo (default ../telegram) and check out the pinned commit
scripts/vendor-setup.sh
scripts/vendor-setup.sh --print-dir   # where the checkout lives

# Pre-flight (provenance index, isolation, forced-exact, optional compile)
scripts/vendor-guard.sh --compile
scripts/vendor-status.sh
```

Requires network access to GitHub on first setup. After that `scripts/vendor-diff.sh`, `scripts/vendor-ff.sh`, and the other tools derive everything from the overlay + the pristine tree; there is no lock file to regenerate.

Full workflow (overlay edits, pin bumps, adding files, host-wiring rules): [`VENDOR.md`](VENDOR.md) and [`AGENTS.md`](AGENTS.md).

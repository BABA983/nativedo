# NativeDo

NativeDo for Linux.do. This repository keeps the Android app as the active implementation target and reserves space for a future iOS client.

## Layout

- `apps/android` - Android app, the current implementation target.
- `apps/ios` - Future iOS native scaffold.
- `contracts/discourse` - Shared API and auth contracts.
- `fixtures` - Sample payloads used by tests.
- `apps/android/TMessagesProj` - Vendored Telegram-derived sources compiled as the `:TMessagesProj` module (committed; enough to build).
- `vendor-overlay.json` - The only hand-maintained vendor file: upstream pin, sync policy, and the exceptions to derived provenance.
- `../telegram` - Local pristine Telegram checkout for diff / re-sync tooling only (outside the repo, gitignored; not required to build).

## Setup

1. Install Android Studio or command-line Android SDK tools.
2. Use JDK 17.
3. Ensure `apps/android/local.properties` points to your Android SDK. Android Studio usually creates this file automatically.

You do **not** need to clone Telegram or run vendor scripts to assemble or run the app. The sources under `apps/android/TMessagesProj` are already in the repo.

## Run

- Open `apps/android` in Android Studio and run the `app` configuration.
- Or assemble from the command line with `./apps/android/gradlew -p apps/android :app:assembleDebug`.

## Verification

- Run the current shell test suite with `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --quiet`.
- Record one local Paparazzi screenshot class with `./scripts/recordScreenshots TopicPostCellScreenshotTest`.
- After recording, verify the local screenshot baselines with `./apps/android/gradlew -p apps/android :app:verifyPaparazziDebug --quiet`.
- Run unit and local screenshot verification together with `./apps/android/gradlew -p apps/android runUnitAndScreenshotTests --quiet`.
- Build a debug APK with `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`.

Paparazzi goldens live in `apps/android/app/src/test/snapshots/images` and are committed: `:app:verifyPaparazziDebug` compares against them, and `:app:recordPaparazziDebug` re-records them for intentional UI changes. Use device tests for behavior that depends on IME/insets, separate windows, WebView, network images, or gestures.

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

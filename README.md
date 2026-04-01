# NativeDo

NativeDo for Linux.do. This repository keeps the Android app as the active implementation target and reserves space for a future iOS client.

## Layout

- `apps/android` - Android app, the current implementation target.
- `apps/ios` - Future iOS native scaffold.
- `contracts/discourse` - Shared API and auth contracts.
- `fixtures` - Sample payloads used by tests.

## Status

Android v1 is being implemented from `docs/superpowers/plans/2026-03-31-linuxdo-android-v1.md`.

## Setup

1. Install Android Studio or command-line Android SDK tools.
2. Use JDK 17.
3. Ensure `apps/android/local.properties` points to your Android SDK. Android Studio usually creates this file automatically.

## Run

- Open `apps/android` in Android Studio and run the `app` configuration.
- Or assemble from the command line with `./apps/android/gradlew -p apps/android :app:assembleDebug`.

## Verification

- Run Task 1 unit tests with `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.navigation.RootDestinationTest"`.
- Run the current shell test suite with `./apps/android/gradlew -p apps/android :app:testDebugUnitTest`.
- Build a debug APK with `./apps/android/gradlew -p apps/android :app:assembleDebug`.

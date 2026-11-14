# Contributing to NativeDo

NativeDo is a native client for [linux.do](https://linux.do/). Android is the active
target; iOS is a scaffold.

## Before you start

- Read [AGENTS.md](AGENTS.md), [docs/code-layout.md](docs/code-layout.md) and the
  relevant page under [docs/](docs/README.md).
- Keep our code in `apps/android/nativedo`. The `apps/android/telegram` subtree is
  pinned upstream code: do not edit it except for the narrow `NativeDoHooks` call
  sites described in AGENTS.md.
- Never commit credentials, cookies, tokens, signing keys, real profile captures or
  other personal data. Fixtures must be synthetic; use `example.com` addresses and
  RFC 5737 documentation IP ranges.
- The app must not initialize Telegram's network stack or database, and must not
  require a Telegram account. Discourse transport, session and storage belong to
  NativeDo.

## License

NativeDo's own code is licensed under GPL-2.0-or-later. By submitting a contribution
you agree to license it under the same terms. There is no separate CLA.

## Build and test

Use JDK 17 and the Android SDK described in [README.md](README.md). Run Gradle
commands sequentially:

```sh
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDailyDebugUnitTest --quiet
./scripts/telegram-hook-guard.sh
```

Add or update tests when behavior changes. Prefer an end-to-end check through the
shipped flow; a host or fake-site test is not a device or live-site pass. For a
localized change, run the affected test class rather than the whole suite.

## Pull requests

- Keep a pull request to one concern and describe what changed, why, and how you
  verified it. Note the device or build you used.
- Do not include generated build output, APKs, native `.so` files or signing
  material.
- If a change touches a contract or fixture, update
  [contracts/discourse](contracts/discourse/README.md) and the matching fixture in
  the same pull request.
- Report security problems privately as described in [SECURITY.md](SECURITY.md),
  not in a public issue or pull request.

## Documentation

Update the existing design page that owns the topic instead of adding a new page.
Keep proposals, accepted-but-unimplemented decisions and current behavior clearly
distinguished. Do not add development diaries, test totals or APK hashes to design
pages.

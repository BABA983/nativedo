# Testing and evidence scope

Commands live in [README](../README.md#checks). This page defines the method,
not today's verdict. A historical passing run proves only the build and scenario
it exercised; it is not a current guarantee.

## Choose the gate

**Active `:nativedo`:** the
[host harness](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/harness/UpstreamUiHarness.kt)
boots production screens under Robolectric native graphics. `FakeDiscourseSite`
serves fixtures through MockWebServer and the real API/mapping path. Generated
shadows replace upstream native methods; assertions guard against tgnet/database
initialization. This checks wiring, state and host rendering, not a real device
or linux.do accepting the same requests or timing independence.

The `dailyDebug` APKs (arm64-v8a, armeabi-v7a, x86_64 and universal) are built
by CI and uploaded as unzipped artifacts for physical-device installation; the
host harness does not replace that device pass.

## Retain contracts, not implementation mirrors

Prefer the shipped flow end to end. Before implementation, name the failure modes
and choose inputs that distinguish correct behavior from plausible wrong behavior.
Isolation is reserved for a race, persistence/account isolation, wire round-trip,
error/retry or accessibility contract the E2E path cannot reach. Constants, getter
round-trips and private call-order assertions do not earn permanent tests.

For asynchronous work, wait for the named request/state transition; arbitrary sleeps
or one looper drain can hide a race. Failed full runs stay failed even if focused
reruns pass. Separate unmodified-baseline evidence from a suspected regression.

## Device and live-site acceptance

Select affected states rather than repeat a universal checklist: light/dark and
loading/error/offline states for visuals; system/emoji keyboard and navigation
modes for composer/insets; minimize/restore/Back for browser/viewer changes;
recreation/account switch/challenge for session and persistence changes.

Real IME windows, GPU blur, animation pacing and gesture feel need an emulator or
device pass appropriate to the claim; an emulator is not physical-device evidence.
Real cookies/Cloudflare and server permissions need the live-site flow. A host fixture
cannot prove plugin availability or account authorization. Physical-device-specific
claims require physical hardware. Do not perform live writes or destructive device
state changes without authorization; preserve the user's account/session state.

## Where evidence goes

Report the command, build identity, scenario, result and limits with the change
(thread/PR/CI), attaching the decisive screenshot, recording or log. Keep committed
goldens and fixtures where tests already own them. If a lasting finding changes a
design or contract, update that page in place; do not paste the run transcript into it.

`.amp/in/artifacts/` is ignored local review storage, **not** a durable Git archive.
Share needed evidence before disposing of scratch output; deleting untracked output
does not preserve it in Git. There is no mandatory append-only verification document
or raw-artifact archive. Removing such Markdown logs does not remove the requirement
to execute and honestly report verification.

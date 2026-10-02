# Read-progress recovery verification, 2026-10-02

Local fixture verification on `nativedo-device`, starting at `fb5440a6`.
No device action, real-site request, installation, push, environment setup, or branch switch was performed.
The installed emulator-5556 build remains the earlier `c0353e7b`; this record is not a device verdict or evidence that a real Cloudflare write challenge has cleared.

## Diagnosis

The baseline full `ReadProgressTest` failed its confirmed-recovery assertion. Per-test diagnostics contradicted the proposed classification difference:

```text
TEST aChallengedReporterIsReArmedOnlyByAConfirmedRecovery
route=/topics/timings status=403 type=text/html bytes=57 challengeMarker=true
evidence=Challenge auth=Authenticated generation=1
failure=SessionRecoveryRequiredException cause=HttpException
recovery: foreground=true running=false blocked=true nextAttemptAt=0 hasReport=true
requests after recovery: ... POST /topics/timings, GET /session/csrf
```

The network interceptor also classified the fixture as Challenge with accepted cookie revision 2. The API boundary still received its 57-byte body. The `HttpException` lines belonged to the separate 500 timing-report test, not this 403. There was no observed consumed body, wrong fixture match, stale-session fence rejection, or cross-test exception substitution on the failing request.

The old positive test parked once for 300 ms, then drained the main looper once. That drain could resume the CSRF request and initiate the POST immediately before the assertion. Observing receipt at the server was also not proof that the reporter had processed the response. Tests now pump callbacks until the named network completion, retaining a 300 ms real-time budget rather than extending the wait.

Three response-latch regressions were added before the production fix. The 9-test class then failed all three:

- Recovery during a held 403 response called `schedule()` while `running` was still true; the next report never started.
- Recovery during a held 500 response was replaced by a new backoff, and its pending flag survived.
- Recovery during a successful first batch remained pending when the next batch received a new challenge. A later flush then sent an unwanted third POST, expected 2, observed 3.

The fix releases the runner before scheduling, lets an overlapping recovery override the old failure's block or deadline, and consumes recovery before a new request or after a successful batch. Classification and session-boundary code are unchanged.

## Retained behavior checks

The tests use the shipped topic screen, repository, Retrofit service and cookie interceptor against `FakeDiscourseSite`. The exact deadline case uses the reporter's existing injected clock and its own coroutine scope, with the real repository and network stack. No repository result or model is stubbed.

- Challenge followed by scroll, flush and activity pause/resume stays at one POST beyond 60 seconds.
- Confirmed recovery after a settled challenge produces exactly one new attempt; another challenge blocks again.
- Held first responses of 403, 500 and 200 each produce exactly two attempts, then stop on the newer challenge even after flush and pause/resume.
- Transient retries observed through the screen all have request-clock gaps of at least 60,000 ms.
- A settled 500 at injected time 100,000 does not resend after resume, advance and flush at 159,999; flush at 160,000 releases one retry containing both the failed floors and the newly reached floors. This also checks that recovery during debounce cannot override a failure from a later request.

The original UI-clock test could not establish a precise pre-deadline resume: one diagnostic run recorded first POST at 2,820 and resume at 190,245. That setup assertion failed and is retained in the local diagnostic XML. The separate injected-clock case covers the boundary without weakening it. A later full-suite run also caught the original transient test advancing time before failure processing; it now waits for failure completion before advancing the backoff interval.

## Final executed gates

All commands ran serially from `/Users/baba/Documents/workspace_baba/linuxdo-native-device`.

```sh
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDebugUnitTest \
  --tests 'io.github.baba983.nativedo.topic.ReadProgressTest' \
  --tests 'io.github.baba983.nativedo.auth.SessionGateTest' \
  --tests 'io.github.baba983.nativedo.topic.ReplyTest' --quiet
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDebugUnitTest --quiet
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assembleDebug --quiet
```

- Related classes: exit 0, 14 tests, 0 failures/errors/skips. Fresh XML timestamps 2026-10-02T15:25:17.073Z through 15:25:28.962Z.
- Full suite: exit 0, 30 classes, 74 tests, 0 failures/errors/skips. Fresh XML timestamps 2026-10-02T15:27:09.837Z through 15:27:49.054Z.
- Assemble: exit 0, finished 2026-10-02T15:35:31Z. APK built only, not installed.

Local logs and XML copies are under `.amp/in/artifacts/classification-recovery/`: `baseline-class`, `scheduling-class`, `network-class`, `red-regressions`, `deadline-setup-xml`, `settled-related-xml`, `complete-full-xml`, and `assemble.log`. Earlier failed runs remain there; this is not an unchanged-code retry loop.

## Read-only reference comparison

Local `main` and local `origin/main` both remained at `cbe316d3`. Its `TopicViewModel.reportPendingReadPositions()` stops on any failed report; later scroll/flush can launch another. It has no challenge block, retry deadline or confirmed-recovery event. Its session-evidence classification matches the current code.

The local fluxdo checkout separates app focus from CF verification in `ScreenTrack`. Focus changes do not clear `_blockSendingUntil`; entering CF verification does clear pending timings and retry state. Allowed transient status codes use 5/10/20/40-second backoffs. The CF interceptor can verify, synchronize cookies and replay the original request. Those payload-dropping and automatic-replay policies were not adopted here: this slice retains unsent read progress and the previously chosen confirmed-recovery gate.

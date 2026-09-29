# NativeDoSpike — running the stock Telegram cells on the JVM

Spike-only module. It renders **unmodified upstream** `ChatMessageCell` / `DialogCell` from
`apps/android/telegram/` (Telegram 12.10.5, brought in with `git subtree`) so the linux.do
post body and topic list can be judged against upstream pixels without an emulator.

Delete this whole directory when the spike is over; nothing in it ships.

## Why Robolectric instead of Paparazzi

Paparazzi's layoutlib cannot load `libtmessages.49.so`, and every upstream UI class reaches
`ConnectionsManager`'s natives, so Paparazzi dies on `UnsatisfiedLinkError`. Robolectric with
`@GraphicsMode(NATIVE)` (set in `src/test/resources/robolectric.properties`) draws through real
Skia and — crucially — lets us replace the natives with shadows. See the parent thread's
`.amp/in/artifacts/spike/REPORT.md` for the full comparison.

## What makes it work

1. `@Config(instrumentedPackages = ["org.telegram"])` — Robolectric does not instrument
   `org.telegram.*` by default. Without this the `native` methods stay real natives and the
   shadows below are never consulted.
2. `@Config(shadows = [...])` — shadows must be listed explicitly; they are not discovered from
   the test classpath.
3. `shadows/Shadow*.java` are **generated** by `.amp/in/artifacts/spike/gen_shadows.py` from the
   `native` declarations in the upstream sources. Regenerate with:

   ```bash
   python3 .amp/in/artifacts/spike/gen_shadows.py
   ```

   Besides stubbing, `ShadowConnectionsManager` records every request the stock UI tries to make
   (`REQUEST_ATTEMPTS`, `LAST_REQUEST_CLASS`) and stops it at the `sendRequest` funnel, so no
   Telegram traffic can leave a host that has no account.
4. Upstream's build plugin generates two assets per variant (`string_resource_ids.bin`,
   `lottie_meta.bin`) that its UI reads at runtime. `build.gradle` reuses upstream's own
   `buildSrc` tasks (`GenerateStringResourceIdsAssetTask`, `GenerateLottieMetadataAssetFileTask`)
   fed with this module's *unit-test* runtime symbol list, and adds their output to the test APK's
   assets.

## Running

```bash
cd apps/android/telegram
./gradlew :NativeDoSpike:testDebugUnitTest --console=plain
```

Screenshots land in `.amp/in/artifacts/spike/*.png` (1233 px wide, Pixel-6 density).
The suite also asserts that tgnet is never initialised, that no Telegram database is opened, and
that nothing reaches the tgnet native layer while a cell is built and drawn.

## Native build prerequisites

`:TMessagesProj` needs the 15 upstream submodule trees that `git subtree` drops; fetch them with
`.amp/in/artifacts/spike/fetch_submodules.sh` (pinned SHAs are in that script). Without them the
CMake build fails on missing headers even though prebuilt `.a` files exist.
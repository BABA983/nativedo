# Third-party components

NativeDo's own code is licensed under GPL-2.0-or-later (see [LICENSE](LICENSE)). This
page records the third-party code that is built into or shipped with the Android app
and the license each component declares. It is a summary, not a replacement for the
license text and notices shipped by each project.

## Telegram Android subtree

| Component | Location | License |
| :-- | :-- | :-- |
| Telegram for Android ([DrKLO/Telegram](https://github.com/DrKLO/Telegram)) | `apps/android/telegram` | GPL-2.0-or-later. The verbatim text is at `apps/android/telegram/LICENSE`. |
| Vendored native libraries: absl, boringssl, breakpad, dav1d, ffmpeg, libsrtp, libvpx, libyuv, openh264, pffft, rnnoise, usrsctplib, wamr, xiph | `apps/android/telegram/TMessagesProj/jni/third_party` | Each library keeps its own upstream license; see its source headers and upstream project. |

The subtree is imported as-is. Its own license and file headers govern it; this
summary does not override them. Some native dependencies are fetched at their pinned
gitlinks rather than stored in this repository.

## Gradle dependencies

Declared in `apps/android/nativedo/build.gradle`.

### Runtime

| Dependency | Version | License |
| :-- | :-- | :-- |
| `com.android.tools:desugar_jdk_libs` | 2.1.5 | GPL-2.0 with the Classpath Exception |
| `androidx.core:core` | 1.16.0 | Apache-2.0 |
| `androidx.collection:collection` | 1.4.5 | Apache-2.0 |
| `androidx.dynamicanimation:dynamicanimation` | 1.0.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | Apache-2.0 |
| `com.squareup.retrofit2:retrofit` | 2.11.0 | Apache-2.0 |
| `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` | 1.0.0 | Apache-2.0 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Apache-2.0 |
| `org.jsoup:jsoup` | 1.18.1 | MIT |
| `com.googlecode.mp4parser:isoparser` | 1.0.6 | Apache-2.0 |

### Test only

| Dependency | Version | License |
| :-- | :-- | :-- |
| `junit:junit` | 4.13.2 | EPL-1.0 |
| `org.robolectric:robolectric` | 4.15.1 | Apache-2.0 |
| `androidx.test:core` | 1.6.1 | Apache-2.0 |
| `com.squareup.okhttp3:mockwebserver` | 4.12.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.8.1 | Apache-2.0 |

### Build tooling declared by the module

| Dependency | Version | License |
| :-- | :-- | :-- |
| `org.jetbrains.kotlin:kotlin-serialization` (Gradle plugin) | 2.1.0 | Apache-2.0 |
| Kotlin Gradle plugin and Kotlin standard library (transitive) | 2.1.0 | Apache-2.0 |

Transitive AndroidX annotations, Kotlin standard-library artifacts and Okio keep
their own Apache-2.0 licenses. The Android Gradle Plugin, Gradle itself and the
Android SDK are build tools and are not redistributed in the APK.

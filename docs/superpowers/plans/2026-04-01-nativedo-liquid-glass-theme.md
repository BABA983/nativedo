# NativeDo Liquid Glass Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lighter Telegram-inspired liquid glass visual system for NativeDo, including theme modes, floating chrome, and refreshed core reading surfaces.

**Architecture:** Keep the existing Compose + Material3 app structure and add a small NativeDo-specific design layer on top. Concentrate edits in theme, app shell, and a few high-traffic screens so the visual shift is obvious without changing app behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Navigation Compose, JUnit4, Truth

---

### Task 1: Document The Direct Edit Surface

**Files:**
- Modify: `docs/superpowers/specs/2026-04-01-nativedo-liquid-glass-theme-design.md`
- Modify: `docs/superpowers/plans/2026-04-01-nativedo-liquid-glass-theme.md`

- [ ] **Step 1: Confirm scope stays inside UI theming and shell**

Check that the implementation only touches:

```text
apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/*
apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt
apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt
apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/*
apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchRoute.kt
apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt
apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileRoute.kt
apps/android/app/src/main/res/values/strings.xml
apps/android/app/src/main/res/values/themes.xml
apps/android/app/src/main/res/values-night/themes.xml
apps/android/app/src/test/java/io/github/baba983/nativedo/ui/*
```

- [ ] **Step 2: Keep implementation out of network and navigation behavior**

Do not alter:

```text
core/network/*
core/session/*
feature/*/data/*
feature/*/domain/*
navigation/RootDestination.kt
navigation/NativeDoNavHost.kt route structure
```

### Task 2: Add Theme Mode And Visual Tokens

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Color.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Theme.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Type.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Shape.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/ThemeMode.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/ThemeModeTest.kt`

- [ ] **Step 1: Add a pure Kotlin test for theme mode cycling**

```kotlin
package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.ui.theme.ThemeMode
import org.junit.Test

class ThemeModeTest {

    @Test
    fun next_cyclesThroughAllModes() {
        assertThat(ThemeMode.System.next()).isEqualTo(ThemeMode.Light)
        assertThat(ThemeMode.Light.next()).isEqualTo(ThemeMode.Dark)
        assertThat(ThemeMode.Dark.next()).isEqualTo(ThemeMode.System)
    }
}
```

- [ ] **Step 2: Add theme mode and extended theme tokens**

Implement:

```kotlin
enum class ThemeMode {
    System,
    Light,
    Dark;

    fun next(): ThemeMode = when (this) {
        System -> Light
        Light -> Dark
        Dark -> System
    }
}
```

Add a small extended color model exposed through a composition local so UI chrome can read `glassBackground`, `glassStroke`, `accentMuted`, and related tokens.

- [ ] **Step 3: Replace the placeholder typography and shapes**

Define:

```kotlin
val NativeDoTypography = Typography(
    headlineLarge = TextStyle(...),
    headlineMedium = TextStyle(...),
    titleLarge = TextStyle(...),
    bodyLarge = TextStyle(...),
    bodyMedium = TextStyle(...),
    labelLarge = TextStyle(...),
)
```

Define rounded shapes for cards and floating bars in `Shape.kt`.

- [ ] **Step 4: Run the theme mode test**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.ui.ThemeModeTest"`

Expected: PASS with 1 test completed and 0 failures.

### Task 3: Wire System Bars And App-Level Theme State

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt`
- Modify: `apps/android/app/src/main/res/values/themes.xml`
- Modify: `apps/android/app/src/main/res/values-night/themes.xml`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Enable edge-to-edge**

Update `MainActivity.kt` so the window is edge-to-edge before Compose content is set.

- [ ] **Step 2: Add a top-bar theme action**

Expose a `ThemeMode` state in `NativeDoApp` and add a simple action that cycles:

```kotlin
var themeMode by rememberSaveable { mutableStateOf(ThemeMode.System) }
```

Use the current mode label/icon in the app bar action.

- [ ] **Step 3: Make XML window colors transparent-compatible**

Keep the base theme aligned with Compose by removing the hard full-screen solid treatment and allowing transparent system bars.

### Task 4: Rebuild The App Shell Chrome

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoBackground.kt`

- [ ] **Step 1: Add layered background**

Create a reusable background composable with soft gradients and highlights behind the scaffold.

- [ ] **Step 2: Replace the default top bar and bottom bar styling**

Use floating rounded containers, translucent fills, thin borders, and stronger selected tab indicators.

- [ ] **Step 3: Preserve existing navigation behavior**

Keep:

```kotlin
launchSingleTop = true
restoreState = true
popUpTo(navController.graph.startDestinationId) { saveState = true }
```

### Task 5: Refresh The Core Reading Surfaces

**Files:**
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/TopicListItem.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileRoute.kt`

- [ ] **Step 1: Rebuild topic items as cards**

Replace the plain `ListItem` with a card-style row that highlights title, author, and reply count using the new tokens.

- [ ] **Step 2: Restyle the home filter row**

Keep the same tabs, but move them into a more intentional glass container.

- [ ] **Step 3: Restyle search input and result list**

Keep the search behavior and result model unchanged. Only change surfaces and spacing.

- [ ] **Step 4: Restyle topic and profile detail containers**

Apply the same card language to detail content without changing data rendering.

### Task 6: Final Verification

**Files:**
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/ThemeModeTest.kt`

- [ ] **Step 1: Run targeted UI-related tests**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.ui.ThemeModeTest" --tests "io.github.baba983.nativedo.ui.NativeDoChromeStateTest"`

Expected: PASS with 0 failures.

- [ ] **Step 2: Run a debug build**

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`

Expected: PASS and APK generation completes without Kotlin or Compose compile errors.

- [ ] **Step 3: Review changed files for scope discipline**

Confirm that the diff is limited to UI theme, shell, and screen presentation files.

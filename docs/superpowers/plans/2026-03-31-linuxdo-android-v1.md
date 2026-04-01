# NativeDo Android V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first usable Android version of a native `linux.do` client with anonymous reading, topic detail, search, profile, and WebView-backed login/session recovery.

**Architecture:** Use a monorepo with a single Android app today and an iOS scaffold directory for future parity. Inside Android, keep a single app module with clear `core/` and `feature/` boundaries, manual dependency wiring through an `AppContainer`, and screen state driven by `ViewModel + StateFlow<UiState>`.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Retrofit, OkHttp, Kotlinx Serialization, Jsoup, Coil, DataStore, JUnit4, Turbine, MockWebServer

---

## File Structure Map

### Repository-Level Files

- Create: `.gitignore` — ignore Android build artifacts, IDE files, local secrets
- Create: `README.md` — repo overview, setup, run, and verification commands
- Create: `apps/ios/README.md` — future iOS scaffold and parity notes
- Create: `contracts/discourse/endpoints.md` — canonical endpoint list used by Android and future iOS
- Create: `contracts/discourse/models.md` — screen-facing model semantics and field meanings
- Create: `contracts/discourse/auth-flow.md` — WebView login, cookie sync, CSRF, Cloudflare fallback
- Create: `fixtures/discourse/latest-topics.json` — sample latest feed payload
- Create: `fixtures/discourse/topic-detail.json` — sample topic detail payload
- Create: `fixtures/discourse/search-topics.json` — sample search payload
- Create: `fixtures/discourse/user-profile.json` — sample profile payload
- Create: `fixtures/discourse/csrf.json` — sample CSRF payload
- Create: `fixtures/discourse/error-401.json` — sample unauthorized payload
- Create: `fixtures/discourse/home-bootstrap.html` — sample homepage HTML with `data-preloaded`

### Android Build Files

- Create: `apps/android/settings.gradle.kts` — Android project settings and module include
- Create: `apps/android/build.gradle.kts` — root Android Gradle config
- Create: `apps/android/gradle.properties` — JVM and Android flags
- Create: `apps/android/gradle/libs.versions.toml` — dependency catalog
- Create: `apps/android/app/build.gradle.kts` — app plugin, Compose, testing, resource dirs
- Create: `apps/android/app/src/main/AndroidManifest.xml` — application and internet permission

### Android App Shell

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/NativeDoApplication.kt` — app entry and `AppContainer`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt` — activity host for Compose app
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt` — top-level scaffold and session-aware chrome
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Color.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Theme.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Type.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/RootDestination.kt` — stable tab and route definitions
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt` — NavHost wiring

### Android Core Layer

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt` — manual dependency graph
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiService.kt` — Retrofit API definitions
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiClient.kt` — typed API client wrapper
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/NetworkJson.kt` — serialization config
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/RequestHeadersInterceptor.kt` — base headers
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/BootstrapHtmlParser.kt` — parse homepage bootstrap HTML
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/PersistentCookieJar.kt` — OkHttp cookie jar backed by store
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/CsrfTokenProvider.kt` — lazy CSRF fetch and refresh
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicSummary.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicDetail.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/Post.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/UserProfile.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/SearchResult.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/AppSessionState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/SessionCoordinator.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/CookieSyncBridge.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/CookieStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/SessionStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/UserPreferencesStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/RecentSearchStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`

### Android Features

- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeFeedCacheStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/domain/LoadHomeFeedUseCase.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/TopicListItem.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/data/TopicRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/domain/LoadTopicUseCase.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/data/SearchRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/domain/SearchTopicsUseCase.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchRoute.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/data/ProfileRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/domain/LoadProfileUseCase.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileRoute.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/auth/ui/LoginWebViewRoute.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings/ui/SettingsUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings/ui/SettingsViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings/ui/SettingsRoute.kt`

### Android Tests

- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/navigation/RootDestinationTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/DiscourseApiClientTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/BootstrapHtmlParserTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModelTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModelTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/search/ui/SearchViewModelTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/profile/ui/ProfileViewModelTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/session/SessionCoordinatorTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/data/HomeFeedCacheStoreTest.kt`
- Create: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/settings/ui/SettingsViewModelTest.kt`

---

### Task 1: Bootstrap the Monorepo and Android App Shell

**Files:**
- Create: `.gitignore`
- Create: `README.md`
- Create: `apps/ios/README.md`
- Create: `apps/android/settings.gradle.kts`
- Create: `apps/android/build.gradle.kts`
- Create: `apps/android/gradle.properties`
- Create: `apps/android/gradle/libs.versions.toml`
- Create: `apps/android/app/build.gradle.kts`
- Create: `apps/android/app/src/main/AndroidManifest.xml`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/NativeDoApplication.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Color.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Theme.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/theme/Type.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/RootDestination.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/navigation/RootDestinationTest.kt`

- [ ] **Step 1: Create the repo skeleton and Android build files**

```kotlin
// apps/android/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nativedo-android"
include(":app")
```

```toml
# apps/android/gradle/libs.versions.toml
[versions]
agp = "8.5.2"
kotlin = "2.0.20"
compose-bom = "2024.09.00"
lifecycle = "2.8.6"
navigation = "2.8.0"
activity-compose = "1.9.2"
core-ktx = "1.13.1"
junit4 = "4.13.2"
truth = "1.4.4"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
```

```kotlin
// apps/android/app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.baba983.nativedo"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.baba983.nativedo"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
}
```

- [ ] **Step 2: Write the failing navigation-shell test**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/navigation/RootDestinationTest.kt
package io.github.baba983.nativedo.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RootDestinationTest {

    @Test
    fun bottomTabs_areStable() {
        assertThat(RootDestination.bottomTabs.map { it.route })
            .containsExactly("home", "search", "profile")
            .inOrder()
    }

    @Test
    fun startDestination_isHome() {
        assertThat(RootDestination.start.route).isEqualTo("home")
    }
}
```

- [ ] **Step 3: Run the test and verify it fails**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.navigation.RootDestinationTest"`

Expected: FAIL with a compile error such as `Unresolved reference: RootDestination`.

- [ ] **Step 4: Implement the Android app shell and route definitions**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/RootDestination.kt
package io.github.baba983.nativedo.navigation

sealed class RootDestination(
    val route: String,
    val label: String,
) {
    data object Home : RootDestination(route = "home", label = "Home")
    data object Search : RootDestination(route = "search", label = "Search")
    data object Profile : RootDestination(route = "profile", label = "Profile")

    companion object {
        val start: RootDestination = Home
        val bottomTabs: List<RootDestination> = listOf(Home, Search, Profile)
    }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt
package io.github.baba983.nativedo.ui

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.baba983.nativedo.navigation.NativeDoNavHost
import io.github.baba983.nativedo.navigation.RootDestination

@Composable
fun NativeDoApp() {
    val navController = rememberNavController()
    val currentEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                RootDestination.bottomTabs.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navController.navigate(destination.route) },
                        label = { Text(destination.label) },
                        icon = {},
                    )
                }
            }
        }
    ) { padding ->
        NativeDoNavHost(navController = navController, contentPadding = padding)
    }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt
package io.github.baba983.nativedo.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NativeDoNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
) {
    NavHost(navController = navController, startDestination = RootDestination.start.route) {
        composable(RootDestination.Home.route) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Home")
            }
        }
        composable(RootDestination.Search.route) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search")
            }
        }
        composable(RootDestination.Profile.route) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Profile")
            }
        }
    }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/NativeDoApplication.kt
package io.github.baba983.nativedo

import android.app.Application
import io.github.baba983.nativedo.core.di.AppContainer

class NativeDoApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer() }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt
package io.github.baba983.nativedo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.baba983.nativedo.ui.NativeDoApp
import io.github.baba983.nativedo.ui.theme.NativeDoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NativeDoTheme {
                NativeDoApp()
            }
        }
    }
}
```

- [ ] **Step 5: Run tests and the debug build**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.navigation.RootDestinationTest"`

Expected: PASS with `BUILD SUCCESSFUL`.

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug`

Expected: PASS with a generated debug APK under `apps/android/app/build/outputs/apk/debug/`.

- [ ] **Step 6: Commit the shell**

```bash
git add .gitignore README.md apps/ios/README.md apps/android
git commit -m "chore: scaffold android app shell"
```

### Task 2: Add Discourse Contracts, Fixtures, and Networking Core

**Files:**
- Create: `contracts/discourse/endpoints.md`
- Create: `contracts/discourse/models.md`
- Create: `contracts/discourse/auth-flow.md`
- Create: `fixtures/discourse/latest-topics.json`
- Create: `fixtures/discourse/topic-detail.json`
- Create: `fixtures/discourse/search-topics.json`
- Create: `fixtures/discourse/user-profile.json`
- Create: `fixtures/discourse/csrf.json`
- Create: `fixtures/discourse/error-401.json`
- Create: `fixtures/discourse/home-bootstrap.html`
- Modify: `apps/android/app/build.gradle.kts`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiService.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiClient.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/NetworkJson.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/RequestHeadersInterceptor.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/BootstrapHtmlParser.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicSummary.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/TopicDetail.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/Post.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/UserProfile.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/SearchResult.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/model/AppError.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/DiscourseApiClientTest.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/BootstrapHtmlParserTest.kt`

- [ ] **Step 1: Extend the app dependencies for networking, parsing, and fixture-backed tests**

```toml
# apps/android/gradle/libs.versions.toml additions
[versions]
retrofit = "2.11.0"
okhttp = "4.12.0"
serialization = "1.7.3"
jsoup = "1.18.1"
coroutines = "1.8.1"
turbine = "1.1.0"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version = "1.0.0" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
```

```kotlin
// apps/android/app/build.gradle.kts additions
plugins {
    kotlin("plugin.serialization") version "2.0.20"
}

android {
    sourceSets["test"].resources.srcDir("../../../fixtures")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

- [ ] **Step 2: Write the failing API parsing and bootstrap tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/DiscourseApiClientTest.kt
package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.di.AppContainer
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DiscourseApiClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun latestTopics_mapsIntoTopicSummaryList() = runTest {
        val payload = checkNotNull(javaClass.classLoader?.getResource("discourse/latest-topics.json")).readText()
        server.enqueue(MockResponse().setBody(payload).setHeader("Content-Type", "application/json"))

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val topics = client.fetchLatest(page = 0)

        assertThat(topics).isNotEmpty()
        assertThat(topics.first().title).isNotEmpty()
    }
}
```

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/core/network/BootstrapHtmlParserTest.kt
package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BootstrapHtmlParserTest {
    @Test
    fun parser_extractsCurrentUserAndCsrfToken() {
        val html = checkNotNull(javaClass.classLoader?.getResource("discourse/home-bootstrap.html")).readText()
        val parsed = BootstrapHtmlParser.parse(html)

        assertThat(parsed.csrfToken).isNotEmpty()
        assertThat(parsed.currentUsername).isNotEmpty()
    }
}
```

- [ ] **Step 3: Run the tests and verify they fail**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.network.DiscourseApiClientTest" --tests "io.github.baba983.nativedo.core.network.BootstrapHtmlParserTest"`

Expected: FAIL with compile errors for missing `BootstrapHtmlParser`, `TestDiscourseClientFactory`, and networking classes.

- [ ] **Step 4: Implement the contracts, models, API service, and bootstrap parser**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiService.kt
package io.github.baba983.nativedo.core.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscourseApiService {
    @GET("latest.json")
    suspend fun latest(@Query("page") page: Int): TopicListResponseDto

    @GET("new.json")
    suspend fun newTopics(@Query("page") page: Int): TopicListResponseDto

    @GET("top.json")
    suspend fun topTopics(@Query("page") page: Int): TopicListResponseDto

    @GET("t/{id}.json")
    suspend fun topic(@Path("id") id: Long): TopicResponseDto

    @GET("search.json")
    suspend fun search(@Query("q") query: String): SearchResponseDto

    @GET("u/{username}.json")
    suspend fun profile(@Path("username") username: String): UserProfileDto

    @GET("session/csrf")
    suspend fun csrf(): CsrfResponseDto
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/DiscourseApiClient.kt
package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile

class DiscourseApiClient(
    private val service: DiscourseApiService,
) {
    suspend fun fetchLatest(page: Int): List<TopicSummary> = service.latest(page).topicList.topics.map { it.toModel() }
    suspend fun fetchNew(page: Int): List<TopicSummary> = service.newTopics(page).topicList.topics.map { it.toModel() }
    suspend fun fetchTop(page: Int): List<TopicSummary> = service.topTopics(page).topicList.topics.map { it.toModel() }
    suspend fun fetchTopic(topicId: Long): TopicDetail = service.topic(topicId).toModel()
    suspend fun search(query: String): SearchResult = service.search(query).toModel()
    suspend fun fetchProfile(username: String): UserProfile = service.profile(username).toModel()
    suspend fun fetchCsrfToken(): String = service.csrf().csrf
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/BootstrapHtmlParser.kt
package io.github.baba983.nativedo.core.network

import org.jsoup.Jsoup

data class ParsedBootstrap(
    val currentUsername: String?,
    val csrfToken: String,
)

object BootstrapHtmlParser {
    fun parse(html: String): ParsedBootstrap {
        val document = Jsoup.parse(html)
        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content").orEmpty()
        val preloaded = document.selectFirst("#data-preloaded")?.html().orEmpty() +
            document.selectFirst("body")?.attr("data-preloaded").orEmpty()
        val currentUsername = Regex("\"username\":\"([^\"]+)\"")
            .find(preloaded)
            ?.groupValues
            ?.getOrNull(1)

        return ParsedBootstrap(currentUsername = currentUsername, csrfToken = csrfToken)
    }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt
package io.github.baba983.nativedo.core.di

import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.network.RequestHeadersInterceptor
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class AppContainer(baseUrl: String = "https://linux.do/") {
    private val json: Json = NetworkJson.instance

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(RequestHeadersInterceptor())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val apiService = retrofit.create(DiscourseApiService::class.java)

    val discourseApiClient = DiscourseApiClient(apiService)
}
```

- [ ] **Step 5: Run the networking tests and debug build**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.network.DiscourseApiClientTest" --tests "io.github.baba983.nativedo.core.network.BootstrapHtmlParserTest"`

Expected: PASS with fixture-backed parsing tests succeeding.

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug`

Expected: PASS with networking dependencies resolving cleanly.

- [ ] **Step 6: Commit the API core**

```bash
git add contracts fixtures apps/android/app/src/main/java/io/github/baba983/nativedo/core apps/android/app/src/test/java/io/github/baba983/nativedo/core apps/android/app/build.gradle.kts apps/android/gradle/libs.versions.toml
git commit -m "feat: add discourse api core"
```

### Task 3: Implement the Home Feed Feature

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/domain/LoadHomeFeedUseCase.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/TopicListItem.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModelTest.kt`

- [ ] **Step 1: Write the failing home-state tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModelTest.kt
package io.github.baba983.nativedo.feature.home.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.home.data.FakeHomeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun loadLatest_emitsItems() = runTest {
        val viewModel = HomeViewModel(
            loadHomeFeed = FakeLoadHomeFeedUseCase(
                latest = listOf(TopicSummary(id = 1, title = "Linux.do topic", authorUsername = "baba"))
            )
        )

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            assertThat(awaitItem().items).hasSize(1)
        }
    }

    @Test
    fun switchFeed_reloadsRequestedTab() = runTest {
        val useCase = FakeLoadHomeFeedUseCase()
        val viewModel = HomeViewModel(loadHomeFeed = useCase)

        viewModel.onFeedSelected(HomeFeedTab.Top)

        assertThat(useCase.requestedTabs.last()).isEqualTo(HomeFeedTab.Top)
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.home.ui.HomeViewModelTest"`

Expected: FAIL because `HomeViewModel`, `HomeUiState`, `HomeFeedTab`, and the use case do not exist yet.

- [ ] **Step 3: Implement the repository, use case, state, and screen**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeUiState.kt
package io.github.baba983.nativedo.feature.home.ui

import io.github.baba983.nativedo.core.model.TopicSummary

enum class HomeFeedTab { Latest, New, Top }

data class HomeUiState(
    val selectedTab: HomeFeedTab = HomeFeedTab.Latest,
    val items: List<TopicSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty() && errorMessage == null
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeViewModel.kt
package io.github.baba983.nativedo.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.feature.home.domain.LoadHomeFeedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val loadHomeFeed: LoadHomeFeedUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { refresh(HomeFeedTab.Latest, initial = true) }

    fun onFeedSelected(tab: HomeFeedTab) {
        if (tab != _uiState.value.selectedTab) refresh(tab, initial = false)
    }

    fun onRefresh() = refresh(_uiState.value.selectedTab, initial = false)

    private fun refresh(tab: HomeFeedTab, initial: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedTab = tab,
                isLoading = initial,
                isRefreshing = !initial,
                errorMessage = null,
            )
            loadHomeFeed(tab).fold(
                onSuccess = { topics ->
                    _uiState.value = _uiState.value.copy(
                        items = topics,
                        isLoading = false,
                        isRefreshing = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message,
                    )
                }
            )
        }
    }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/domain/LoadHomeFeedUseCase.kt
package io.github.baba983.nativedo.feature.home.domain

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.home.data.HomeRepository
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab

class LoadHomeFeedUseCase(
    private val repository: HomeRepository,
) {
    suspend operator fun invoke(tab: HomeFeedTab): Result<List<TopicSummary>> = repository.load(tab)
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeRepository.kt
package io.github.baba983.nativedo.feature.home.data

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab

class HomeRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(tab: HomeFeedTab): Result<List<TopicSummary>> = runCatching {
        when (tab) {
            HomeFeedTab.Latest -> client.fetchLatest(page = 0)
            HomeFeedTab.New -> client.fetchNew(page = 0)
            HomeFeedTab.Top -> client.fetchTop(page = 0)
        }
    }
}
```

- [ ] **Step 4: Wire the real home route into navigation**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt (replace the temporary home route)
composable(RootDestination.Home.route) {
    HomeRoute(
        onTopicClick = { topicId -> navController.navigate("topic/$topicId") },
    )
}
```

- [ ] **Step 5: Run the home tests and debug build**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.home.ui.HomeViewModelTest"`

Expected: PASS with state transitions covered.

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug`

Expected: PASS with the real home feed screen compiled into the app.

- [ ] **Step 6: Commit the home feed**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt
git commit -m "feat: add home feed"
```

### Task 4: Implement Topic Detail Reading

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/data/TopicRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/domain/LoadTopicUseCase.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModelTest.kt`

- [ ] **Step 1: Write the failing topic-detail tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModelTest.kt
package io.github.baba983.nativedo.feature.topic.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TopicViewModelTest {
    @Test
    fun loadTopic_emitsTopicAndPosts() = runTest {
        val detail = TopicDetail(
            id = 42,
            title = "Compose on linux.do",
            posts = listOf(Post(id = 1, authorUsername = "baba", cookedHtml = "<p>Hello</p>", imageUrls = emptyList()))
        )
        val viewModel = TopicViewModel(loadTopic = FakeLoadTopicUseCase(detail))

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            assertThat(awaitItem().posts).hasSize(1)
        }
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.topic.ui.TopicViewModelTest"`

Expected: FAIL because topic detail classes do not exist yet.

- [ ] **Step 3: Implement the repository, use case, view model, and HTML body renderer**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt
package io.github.baba983.nativedo.core.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

@Composable
fun HtmlPostBodyView(html: String) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        },
    )
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicUiState.kt
package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.Post

data class TopicUiState(
    val title: String = "",
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
) {
    val canRetry: Boolean get() = errorMessage != null && posts.isEmpty()
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicViewModel.kt
package io.github.baba983.nativedo.feature.topic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.feature.topic.domain.LoadTopicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TopicViewModel(
    private val loadTopic: LoadTopicUseCase,
    private val topicId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            loadTopic(topicId).fold(
                onSuccess = { detail ->
                    _uiState.value = TopicUiState(
                        title = detail.title,
                        posts = detail.posts,
                        isLoading = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = TopicUiState(isLoading = false, errorMessage = error.message)
                }
            )
        }
    }
}
```

- [ ] **Step 4: Add the topic route to the nav graph and home-to-topic navigation**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt additions
composable("topic/{topicId}") { entry ->
    val topicId = entry.arguments?.getString("topicId")!!.toLong()
    TopicRoute(topicId = topicId)
}
```

- [ ] **Step 5: Run the topic tests and debug build**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.topic.ui.TopicViewModelTest"`

Expected: PASS with topic title and posts loaded into state.

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug`

Expected: PASS with topic detail route wired.

- [ ] **Step 6: Commit topic detail support**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic apps/android/app/src/test/java/io/github/baba983/nativedo/feature/topic apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt
git commit -m "feat: add topic detail reading"
```

### Task 5: Implement Search and Profile

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/RecentSearchStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/data/SearchRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/domain/SearchTopicsUseCase.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchRoute.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/data/ProfileRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/domain/LoadProfileUseCase.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/search/ui/SearchViewModelTest.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/profile/ui/ProfileViewModelTest.kt`

- [ ] **Step 1: Write the failing search and profile tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/search/ui/SearchViewModelTest.kt
package io.github.baba983.nativedo.feature.search.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.TopicSummary
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchViewModelTest {
    @Test
    fun submitQuery_updatesResultsAndRecentQueries() = runTest {
        val viewModel = SearchViewModel(
            searchTopics = FakeSearchTopicsUseCase(
                SearchResult(topics = listOf(TopicSummary(id = 2, title = "Search result", authorUsername = "baba")), users = emptyList())
            ),
            recentSearchStore = InMemoryRecentSearchStore()
        )

        viewModel.onQuerySubmitted("compose")

        assertThat(viewModel.uiState.value.topics).hasSize(1)
        assertThat(viewModel.uiState.value.recentQueries.first()).isEqualTo("compose")
    }
}
```

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/profile/ui/ProfileViewModelTest.kt
package io.github.baba983.nativedo.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProfileViewModelTest {
    @Test
    fun loadProfile_setsViewerAndProfile() = runTest {
        val viewModel = ProfileViewModel(
            loadProfile = FakeLoadProfileUseCase(UserProfile(username = "baba", displayName = "Baba", bio = "Compose user")),
            viewer = ProfileViewer.Other("baba")
        )

        assertThat(viewModel.uiState.value.profile?.username).isEqualTo("baba")
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.search.ui.SearchViewModelTest" --tests "io.github.baba983.nativedo.feature.profile.ui.ProfileViewModelTest"`

Expected: FAIL because search/profile state and store classes do not exist yet.

- [ ] **Step 3: Implement recent-search persistence and search/profile state holders**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/RecentSearchStore.kt
package io.github.baba983.nativedo.core.storage

import kotlinx.coroutines.flow.Flow

interface RecentSearchStore {
    val recentQueries: Flow<List<String>>
    suspend fun record(query: String)
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search/ui/SearchUiState.kt
package io.github.baba983.nativedo.feature.search.ui

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val topics: List<TopicSummary> = emptyList(),
    val users: List<UserProfile> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val errorMessage: String? = null,
)
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileUiState.kt
package io.github.baba983.nativedo.feature.profile.ui

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile

sealed interface ProfileViewer {
    data object Self : ProfileViewer
    data class Other(val username: String) : ProfileViewer
}

data class ProfileUiState(
    val viewer: ProfileViewer,
    val profile: UserProfile? = null,
    val recentTopics: List<TopicSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
```

- [ ] **Step 4: Wire search and profile routes into the nav graph**

```kotlin
// NativeDoNavHost.kt additions
composable(RootDestination.Search.route) {
    SearchRoute(
        onTopicClick = { topicId -> navController.navigate("topic/$topicId") },
        onUserClick = { username -> navController.navigate("user/$username") },
    )
}

composable(RootDestination.Profile.route) {
    ProfileRoute(viewer = ProfileViewer.Self)
}

composable("user/{username}") { entry ->
    ProfileRoute(viewer = ProfileViewer.Other(entry.arguments!!.getString("username")!!))
}
```

- [ ] **Step 5: Run search/profile tests and debug build**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.search.ui.SearchViewModelTest" --tests "io.github.baba983.nativedo.feature.profile.ui.ProfileViewModelTest"`

Expected: PASS with recent-search and profile-viewer behavior validated.

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug`

Expected: PASS with search and profile tabs showing real screens.

- [ ] **Step 6: Commit search and profile**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/RecentSearchStore.kt apps/android/app/src/main/java/io/github/baba983/nativedo/feature/search apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile apps/android/app/src/test/java/io/github/baba983/nativedo/feature/search apps/android/app/src/test/java/io/github/baba983/nativedo/feature/profile apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt
git commit -m "feat: add search and profile"
```

### Task 6: Add Session Coordination and WebView Login

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/CookieStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/SessionStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/PersistentCookieJar.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/CsrfTokenProvider.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/AppSessionState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/CookieSyncBridge.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/SessionCoordinator.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/auth/ui/LoginWebViewRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile/ui/ProfileRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/core/session/SessionCoordinatorTest.kt`

- [ ] **Step 1: Write the failing session-coordinator test**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/core/session/SessionCoordinatorTest.kt
package io.github.baba983.nativedo.core.session

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionCoordinatorTest {
    @Test
    fun restoreSession_emitsAuthenticatedWhenCookiesAndBootstrapUserExist() = runTest {
        val coordinator = SessionCoordinator(
            cookieStore = InMemoryCookieStore(hasSession = true),
            sessionStore = InMemorySessionStore(),
            apiClient = FakeSessionApiClient(currentUsername = "baba"),
            csrfTokenProvider = FakeCsrfTokenProvider(token = "csrf-token")
        )

        coordinator.restoreSession()

        coordinator.sessionState.test {
            assertThat(awaitItem()).isEqualTo(AppSessionState.Authenticating)
            assertThat(awaitItem()).isInstanceOf(AppSessionState.Authenticated::class.java)
        }
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.session.SessionCoordinatorTest"`

Expected: FAIL because session state, cookie store, and coordinator classes are missing.

- [ ] **Step 3: Implement stores, cookie jar, CSRF provider, and session state machine**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/AppSessionState.kt
package io.github.baba983.nativedo.core.session

sealed interface AppSessionState {
    data object Anonymous : AppSessionState
    data object Authenticating : AppSessionState
    data class Authenticated(val username: String, val csrfToken: String) : AppSessionState
    data object Expired : AppSessionState
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/core/session/SessionCoordinator.kt
package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.network.CsrfTokenProvider
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SessionBootstrapClient {
    suspend fun fetchBootstrap(): ParsedBootstrap
}

class SessionCoordinator(
    private val cookieStore: CookieStore,
    private val sessionStore: SessionStore,
    private val apiClient: SessionBootstrapClient,
    private val csrfTokenProvider: CsrfTokenProvider,
) {
    private val _sessionState = MutableStateFlow<AppSessionState>(AppSessionState.Anonymous)
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    suspend fun restoreSession() {
        if (!cookieStore.hasSessionCookies()) {
            _sessionState.value = AppSessionState.Anonymous
            return
        }
        _sessionState.value = AppSessionState.Authenticating

        runCatching {
            val bootstrap = apiClient.fetchBootstrap()
            val username = requireNotNull(bootstrap.currentUsername)
            val csrf = csrfTokenProvider.get(forceRefresh = true)
            sessionStore.save(username)
            AppSessionState.Authenticated(username, csrf)
        }.onSuccess {
            _sessionState.value = it
        }.onFailure {
            cookieStore.clear()
            sessionStore.clear()
            _sessionState.value = AppSessionState.Anonymous
        }
    }

    suspend fun onLogout() {
        cookieStore.clear()
        sessionStore.clear()
        _sessionState.value = AppSessionState.Anonymous
    }
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/auth/ui/LoginWebViewRoute.kt
package io.github.baba983.nativedo.feature.auth.ui

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LoginWebViewRoute(onLoginFinished: () -> Unit) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                loadUrl("https://linux.do/login")
            }
        },
        update = { webView ->
            if (webView.url?.contains("linux.do") == true) {
                onLoginFinished()
            }
        }
    )
}
```

- [ ] **Step 4: Connect login and logout entry points to the profile flow**

```kotlin
// ProfileRoute.kt sketch
if (uiState.isLoggedIn.not()) {
    Button(onClick = onLoginClick) { Text("Login") }
} else {
    Button(onClick = onLogoutClick) { Text("Logout") }
}
```

```kotlin
// NativeDoNavHost.kt addition
composable("login") {
    LoginWebViewRoute(
        onLoginFinished = {
            navController.popBackStack()
        }
    )
}
```

- [ ] **Step 5: Run session tests and the app build**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.core.session.SessionCoordinatorTest"`

Expected: PASS with `Anonymous -> Authenticating -> Authenticated` transitions verified.

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug`

Expected: PASS with WebView auth route compiled.

- [ ] **Step 6: Manually verify login smoke behavior on a device or emulator**

Run: `./apps/android/gradlew -p apps/android :app:installDebug`

Expected: App installs. Open Profile tab, tap `Login`, confirm a `linux.do` WebView opens, complete login, and verify Profile updates from anonymous to authenticated.

- [ ] **Step 7: Commit session and login support**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/core/session apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/PersistentCookieJar.kt apps/android/app/src/main/java/io/github/baba983/nativedo/core/network/CsrfTokenProvider.kt apps/android/app/src/main/java/io/github/baba983/nativedo/feature/auth apps/android/app/src/main/java/io/github/baba983/nativedo/feature/profile apps/android/app/src/test/java/io/github/baba983/nativedo/core/session apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt
git commit -m "feat: add session coordinator and webview login"
```

### Task 7: Add Settings, Lightweight Caching, and CI Hardening

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/UserPreferencesStore.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeFeedCacheStore.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeRepository.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings/ui/SettingsUiState.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings/ui/SettingsViewModel.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings/ui/SettingsRoute.kt`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/navigation/NativeDoNavHost.kt`
- Create: `.github/workflows/android.yml`
- Modify: `README.md`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/data/HomeFeedCacheStoreTest.kt`
- Test: `apps/android/app/src/test/java/io/github/baba983/nativedo/feature/settings/ui/SettingsViewModelTest.kt`

- [ ] **Step 1: Write the failing cache and settings tests**

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/data/HomeFeedCacheStoreTest.kt
package io.github.baba983.nativedo.feature.home.data

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeFeedCacheStoreTest {
    @Test
    fun saveAndReadRoundTrip() = runTest {
        val store = InMemoryHomeFeedCacheStore()
        val payload = listOf(TopicSummary(id = 1, title = "cached", authorUsername = "baba"))

        store.save("latest", payload)

        assertThat(store.read("latest")).hasSize(1)
    }
}
```

```kotlin
// apps/android/app/src/test/java/io/github/baba983/nativedo/feature/settings/ui/SettingsViewModelTest.kt
package io.github.baba983.nativedo.feature.settings.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun updateImageLoading_persistsPreference() = runTest {
        val store = InMemoryUserPreferencesStore()
        val viewModel = SettingsViewModel(store)

        viewModel.onImageLoadingChanged(ImageLoadingPreference.WifiOnly)

        assertThat(store.snapshot().imageLoading).isEqualTo(ImageLoadingPreference.WifiOnly)
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests "io.github.baba983.nativedo.feature.home.data.HomeFeedCacheStoreTest" --tests "io.github.baba983.nativedo.feature.settings.ui.SettingsViewModelTest"`

Expected: FAIL because cache and settings classes do not exist yet.

- [ ] **Step 3: Implement preferences, cached home feed, and settings screen**

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeFeedCacheStore.kt
package io.github.baba983.nativedo.feature.home.data

import io.github.baba983.nativedo.core.model.TopicSummary

interface HomeFeedCacheStore {
    suspend fun save(key: String, topics: List<TopicSummary>)
    suspend fun read(key: String): List<TopicSummary>
}
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings/ui/SettingsUiState.kt
package io.github.baba983.nativedo.feature.settings.ui

enum class ImageLoadingPreference { Always, WifiOnly, Never }
enum class ExternalLinkPreference { InAppBrowser, SystemBrowser }

data class SettingsUiState(
    val imageLoading: ImageLoadingPreference = ImageLoadingPreference.Always,
    val externalLinks: ExternalLinkPreference = ExternalLinkPreference.InAppBrowser,
    val appVersion: String = "0.1.0",
)
```

```kotlin
// apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings/ui/SettingsViewModel.kt
package io.github.baba983.nativedo.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.storage.UserPreferencesStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferencesStore: UserPreferencesStore,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = userPreferencesStore.preferences
        .map { prefs ->
            SettingsUiState(
                imageLoading = prefs.imageLoading,
                externalLinks = prefs.externalLinks,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onImageLoadingChanged(preference: ImageLoadingPreference) {
        viewModelScope.launch { userPreferencesStore.updateImageLoading(preference) }
    }
}
```

- [ ] **Step 4: Add repo-level CI and setup documentation**

```yaml
# .github/workflows/android.yml
name: Android

on:
  push:
    branches: [ main ]
  pull_request:

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - run: ./apps/android/gradlew -p apps/android :app:testDebugUnitTest :app:assembleDebug
```

```md
<!-- README.md additions -->
## Android Commands

- `./apps/android/gradlew -p apps/android :app:assembleDebug`
- `./apps/android/gradlew -p apps/android :app:testDebugUnitTest`
- `./apps/android/gradlew -p apps/android :app:installDebug`
```

- [ ] **Step 5: Run full unit tests and debug build**

Run: `./apps/android/gradlew -p apps/android :app:testDebugUnitTest :app:assembleDebug`

Expected: PASS with all unit tests green and the debug app assembled.

- [ ] **Step 6: Commit settings, cache, and CI**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/core/storage/UserPreferencesStore.kt apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/data/HomeFeedCacheStore.kt apps/android/app/src/main/java/io/github/baba983/nativedo/feature/settings apps/android/app/src/test/java/io/github/baba983/nativedo/feature/home/data/HomeFeedCacheStoreTest.kt apps/android/app/src/test/java/io/github/baba983/nativedo/feature/settings/ui/SettingsViewModelTest.kt .github/workflows/android.yml README.md
git commit -m "feat: add settings cache and ci"
```

---

## Delivery Notes

- Stop after Task 7 and keep `v2` features out of the branch.
- Do not add reply, like, bookmark, message bus, or upload support in this plan.
- If a dependency choice becomes unstable during execution, keep the architecture and file paths, swap only the library version or implementation detail, and note the deviation in the commit message.

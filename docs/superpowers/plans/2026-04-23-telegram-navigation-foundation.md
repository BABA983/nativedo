# Telegram Navigation Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build milestone 1 of the Android rewrite: a Telegram-style single-stack navigation shell with a Compose topic list root, repeatable topic detail pushes, edge swipe-back, and predictive back.

**Architecture:** NativeDo keeps `MainActivity`, `NativeDoApp`, and the Compose topic screens. Telegram-derived infrastructure lives under `core/`, `ui/actionbar/`, and `ui/components/`, with upstream names and behavior preserved as closely as practical. `ActionBarLayout` owns the only real navigation stack; Compose only renders page content and emits callbacks.

**Tech Stack:** Android View system, AppCompat, Jetpack Compose, Android predictive back APIs, Java 17/Kotlin 2.1, Gradle Android app module

---

### Task 1: Bootstrap The Android Host Shell

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/NativeDoApp.kt`
- Modify: `apps/android/app/src/main/AndroidManifest.xml`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Fix application wiring**

```kotlin
package io.github.baba983.nativedo

import android.app.Application

class NativeDoApp : Application()
```

Update the manifest application name from `.NativeDoApplication` to `.NativeDoApp`.

- [ ] **Step 2: Replace the empty activity with a host activity**

```kotlin
class MainActivity : ComponentActivity() {

    private lateinit var actionBarLayout: ActionBarLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        actionBarLayout = ActionBarLayout(this).apply {
            addFragmentToStack(TopicListFragment())
        }
        setContentView(actionBarLayout)
        registerPredictiveBackCallbacks()
    }
}
```

Keep the activity thin: window setup, root container setup, and back callback forwarding only.

- [ ] **Step 3: Add minimal strings needed by the new shell**

```xml
<string name="screen_topic_list">linux.do</string>
<string name="screen_topic_detail">Topic</string>
<string name="action_back">Back</string>
```

Only add strings that the milestone uses.

- [ ] **Step 4: Build-check the bootstrap stage**

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`

Expected: build still fails, but only because navigation classes and fragments are not added yet.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/NativeDoApp.kt \
  apps/android/app/src/main/AndroidManifest.xml \
  apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt \
  apps/android/app/src/main/res/values/strings.xml
git commit -m "feat: bootstrap telegram navigation host"
```

### Task 2: Add Telegram-Derived Core Support Files

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/AndroidUtilities.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/FileLog.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/NotificationCenter.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/core/SharedConfig.java`

- [ ] **Step 1: Add a shared GPL header block and create `AndroidUtilities`**

```java
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/AndroidUtilities.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.core;
```

Implement only the methods directly used by milestone-1 copies:

```java
public class AndroidUtilities {
    public static final Point displaySize = new Point();
    public static float density = 1f;

    public static void init(Context context) { ... }
    public static int dp(float value) { ... }
    public static float dpf2(float value) { ... }
    public static float lerp(float from, float to, float progress) { ... }
    public static int getPixelsInCM(float cm, boolean isX) { ... }
    public static void hideKeyboard(View view) { ... }
}
```

- [ ] **Step 2: Add tiny support stubs for logging, config, and notifications**

```java
public final class FileLog {
    public static void e(Throwable throwable) {
        Log.e("NativeDo", "Telegram-derived failure", throwable);
    }
}
```

```java
public final class SharedConfig {
    public static boolean drawActionBarShadow = true;
}
```

```java
public final class NotificationCenter {
    public static final int didSetNewTheme = 1;
}
```

Do not add extra surface area beyond what the copied files require.

- [ ] **Step 3: Compile-check support files**

Run: `./apps/android/gradlew -p apps/android :app:compileDebugKotlin --quiet`

Expected: support files compile; remaining failures point at missing UI/navigation classes.

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/core
git commit -m "feat: add telegram-derived core support stubs"
```

### Task 3: Add The Minimal Telegram UI Building Blocks

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/components/LayoutHelper.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/components/CubicBezierInterpolator.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/components/SizeNotifierFrameLayout.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/BackDrawable.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/SimpleTextView.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/DrawerLayoutContainer.java`

- [ ] **Step 1: Copy low-risk Telegram utilities nearly verbatim**

Use direct-copy-first behavior for:

```java
public class LayoutHelper { ... }
public class CubicBezierInterpolator implements Interpolator { ... }
public class BackDrawable extends Drawable { ... }
```

Only update package names/imports and the GPL header.

- [ ] **Step 2: Add the smallest possible text and layout helpers**

```java
public class SimpleTextView extends AppCompatTextView {
    public SimpleTextView(Context context) {
        super(context);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
    }
}
```

```java
public class SizeNotifierFrameLayout extends FrameLayout {
    public SizeNotifierFrameLayout(Context context) {
        super(context);
        setFitsSystemWindows(false);
    }
}
```

```java
public class DrawerLayoutContainer extends FrameLayout {
    public DrawerLayoutContainer(Context context) {
        super(context);
    }
}
```

`DrawerLayoutContainer` stays a compile-time placeholder unless `ActionBarLayout` truly needs more.

- [ ] **Step 3: Compile-check the copied building blocks**

Run: `./apps/android/gradlew -p apps/android :app:compileDebugJavaWithJavac --quiet`

Expected: helper classes compile; remaining failures point at missing action bar / fragment container classes.

- [ ] **Step 4: Commit**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/ui/components \
  apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/BackDrawable.java \
  apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/SimpleTextView.java \
  apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/DrawerLayoutContainer.java
git commit -m "feat: add telegram ui helper classes"
```

### Task 4: Implement The Navigation Core

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/INavigationLayout.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/BaseFragment.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/ActionBar.java`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar/ActionBarLayout.java`
- Modify: `apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt`

- [ ] **Step 1: Add the navigation contract**

```java
public interface INavigationLayout {
    boolean presentFragment(NavigationParams params);
    boolean addFragmentToStack(BaseFragment fragment, int position);
    void closeLastFragment(boolean animated, boolean forceNoAnimation);
    List<BaseFragment> getFragmentStack();

    default boolean addFragmentToStack(BaseFragment fragment) {
        return addFragmentToStack(fragment, -1);
    }

    default boolean presentFragment(BaseFragment fragment) {
        return presentFragment(new NavigationParams(fragment));
    }

    default void closeLastFragment(boolean animated) {
        closeLastFragment(animated, false);
    }

    class NavigationParams {
        public BaseFragment fragment;

        public NavigationParams(BaseFragment fragment) {
            this.fragment = fragment;
        }
    }
}
```

Keep upstream method names for the slice used in milestone 1.

- [ ] **Step 2: Add a minimal Telegram-style `BaseFragment`**

```java
public abstract class BaseFragment {
    protected View fragmentView;
    protected ActionBar actionBar;
    protected INavigationLayout parentLayout;

    public View createView(Context context) { return null; }
    public boolean onBackPressed() { return true; }
    public boolean canBeginSlide() { return true; }
    public boolean isSwipeBackEnabled(MotionEvent event) { return true; }
    public void onBeginSlide() { }
    public void onResume() { }
    public void onPause() { }
}
```

Preserve upstream names and lifecycle shape, even if the first implementation is smaller.

- [ ] **Step 3: Add a trimmed `ActionBar` focused on title + back button**

```java
public class ActionBar extends FrameLayout {
    public void setTitle(CharSequence value) { ... }
    public void setBackButtonDrawable(Drawable drawable) { ... }
    public void setActionBarMenuOnItemClick(ActionBarMenuOnItemClick listener) { ... }
}
```

Do not import Telegram menu/search/action-mode features that milestone 1 does not use.

- [ ] **Step 4: Add `ActionBarLayout` with one real fragment stack**

```java
public class ActionBarLayout extends FrameLayout implements INavigationLayout {
    private final ArrayList<BaseFragment> fragmentsStack = new ArrayList<>();
    private final FrameLayout containerViewBack;
    private final FrameLayout containerView;

    public boolean addFragmentToStack(BaseFragment fragment, int position) { ... }
    public boolean presentFragment(NavigationParams params) { ... }
    public void closeLastFragment(boolean animated, boolean forceNoAnimation) { ... }
    public void onBackStarted(float touchX, float touchY) { ... }
    public void onBackProgress(float progress) { ... }
    public void onBackCancelled() { ... }
    public void onBackInvoked() { ... }
}
```

Use Telegram’s container model:

- current page in `containerView`
- previous page moved into `containerViewBack` before interactive back
- edge swipe and predictive back both drive the same translation state

- [ ] **Step 5: Forward Android back callbacks from `MainActivity`**

```kotlin
private fun registerPredictiveBackCallbacks() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(...) { ... }
    } else {
        onBackPressedDispatcher.addCallback(this) {
            if (actionBarLayout.handleBackPressed()) return@addCallback
            finish()
        }
    }
}
```

Route all back paths into `ActionBarLayout`.

- [ ] **Step 6: Compile the navigation core**

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`

Expected: build still fails only on missing topic fragments/screens or remaining imports referenced by the navigation slice.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/ui/actionbar \
  apps/android/app/src/main/java/io/github/baba983/nativedo/MainActivity.kt
git commit -m "feat: add telegram-style navigation core"
```

### Task 5: Add Topic Fragments And Compose Content

**Files:**
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic/MockTopics.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic/TopicListScreen.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic/TopicDetailScreen.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic/TopicListFragment.kt`
- Create: `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic/TopicDetailFragment.kt`

- [ ] **Step 1: Add mock topic models and data**

```kotlin
data class MockTopic(
    val id: Int,
    val title: String,
    val category: String,
    val author: String,
    val replyCount: Int,
    val viewCount: Int,
    val likeCount: Int,
    val excerpt: String,
)

val mockTopics = listOf(...)
```

- [ ] **Step 2: Add the Compose list and detail screens**

```kotlin
@Composable
fun TopicListScreen(
    topics: List<MockTopic>,
    onTopicClick: (MockTopic) -> Unit,
)
```

```kotlin
@Composable
fun TopicDetailScreen(
    topic: MockTopic,
    onRelatedTopicClick: (MockTopic) -> Unit,
)
```

Keep the UI simple: list rows, title, excerpt, metadata, and related-topic entries.

- [ ] **Step 3: Wrap the Compose screens in Telegram-style fragments**

```kotlin
class TopicListFragment : BaseFragment() {
    override fun createView(context: Context): View {
        actionBar.setTitle(context.getString(R.string.screen_topic_list))
        return ComposeView(context).apply {
            setContent { TopicListScreen(mockTopics, ::openTopic) }
        }
    }
}
```

```kotlin
class TopicDetailFragment(private val topicId: Int) : BaseFragment() {
    override fun createView(context: Context): View {
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setTitle(currentTopic.title)
        return ComposeView(context).apply {
            setContent { TopicDetailScreen(currentTopic, ::openRelatedTopic) }
        }
    }
}
```

- [ ] **Step 4: Run the full build**

Run: `./apps/android/gradlew -p apps/android :app:assembleDebug --quiet`

Expected: PASS and produce `apps/android/app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic
git commit -m "feat: add topic list and detail milestone screens"
```

### Task 6: Verify Interactions And Add Only Necessary Test Coverage

**Files:**
- Create if needed: `apps/android/app/src/test/java/io/github/baba983/nativedo/ui/topic/TopicNavigationWiringTest.kt`

- [ ] **Step 1: Decide whether a glue test is actually needed**

Only add a test if the topic click → fragment presentation bridge is non-trivial. If the fragments directly call `presentFragment(...)` with obvious data, skip new tests.

- [ ] **Step 2: If needed, add one small wiring test**

```kotlin
@Test
fun relatedTopicClickBuildsNextDetailFragment() {
    val fragment = TopicDetailFragment(1)
    val next = fragment.buildNextFragmentForTest(2)
    assertThat(next.topicIdForTest()).isEqualTo(2)
}
```

Do not add broad tests that simply restate Telegram-derived navigation behavior.

- [ ] **Step 3: Run targeted verification**

Run if a new test exists:

`./apps/android/gradlew -p apps/android :app:testDebugUnitTest --tests 'io.github.baba983.nativedo.ui.topic.TopicNavigationWiringTest' --quiet`

Expected: PASS

- [ ] **Step 4: Run manual smoke verification**

Manual checklist:

- launch to the topic list root
- tap topic A, then related topic B, then related topic C
- return from C to B to A to list
- perform left-edge swipe-back on a detail page
- on Android 14, test predictive back cancel and commit
- from the root list, confirm back exits the activity

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src/test/java/io/github/baba983/nativedo/ui/topic/TopicNavigationWiringTest.kt
git commit -m "test: add minimal topic navigation wiring coverage"
```

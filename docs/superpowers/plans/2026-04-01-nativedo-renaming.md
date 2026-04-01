# NativeDo Renaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the Android app and supporting docs from LinuxDo to NativeDo, migrate Android package identifiers to `io.github.baba983.nativedo`, and remove the unused `demo/` scaffold.

**Architecture:** Keep the existing app structure and behavior intact. Apply a focused mechanical rename across Android source, tests, resources, and docs, while preserving `linux.do` as the target site name and keeping the repository slug unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle Kotlin DSL, Markdown docs

---

### Task 1: Rename Android identifiers and source packages

**Files:**
- Modify: `apps/android/app/build.gradle.kts`
- Modify: `apps/android/settings.gradle.kts`
- Modify: `apps/android/app/src/main/AndroidManifest.xml`
- Modify: `apps/android/app/src/main/res/values/strings.xml`
- Modify: `apps/android/app/src/main/res/values/themes.xml`
- Modify: `apps/android/app/src/main/res/values-night/themes.xml`
- Modify: `apps/android/app/src/main/java/io/github/baba/linuxdo/**`
- Modify: `apps/android/app/src/test/java/io/github/baba/linuxdo/**`

- [ ] Replace `namespace` and `applicationId` with `io.github.baba983.nativedo`.
- [ ] Rename product-facing classes and theme identifiers from `LinuxDo*` to `NativeDo*`.
- [ ] Move source and test packages from `io/github/baba/linuxdo` to `io/github/baba983/nativedo`.
- [ ] Update all `package` declarations and imports to the new package prefix.

### Task 2: Update docs and public-facing strings

**Files:**
- Modify: `README.md`
- Modify: `apps/ios/README.md`
- Modify: `docs/superpowers/specs/2026-03-31-linuxdo-native-client-design.md`
- Modify: `docs/superpowers/plans/2026-03-31-linuxdo-android-v1.md`
- Modify: `fixtures/discourse/latest-topics.json`
- Modify: `fixtures/discourse/topic-detail.json`

- [ ] Update the main product name to `NativeDo`.
- [ ] Keep `Linux.do` only where it refers to the target site/community.
- [ ] Replace old package references in docs and commands with `io.github.baba983.nativedo`.
- [ ] Keep repository slug references such as `linuxdo-native` unchanged.

### Task 3: Remove the unused demo scaffold

**Files:**
- Delete: `demo/**`

- [ ] Verify the `demo/` directory is not referenced by app code, build files, or docs.
- [ ] Delete the entire `demo/` directory once confirmed unused.

### Task 4: Verify the rename

**Files:**
- Verify only

- [ ] Run `./apps/android/gradlew -p apps/android :app:compileDebugKotlin`.
- [ ] Run `./apps/android/gradlew -p apps/android :app:testDebugUnitTest`.
- [ ] Run `./apps/android/gradlew -p apps/android :app:assembleDebug`.
- [ ] Review remaining `LinuxDo` / `io.github.baba.linuxdo` references and ensure only intentional historical references remain.

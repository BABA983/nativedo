# Telegram Theme Port

NativeDo ports Telegram's Theme subsystem as a dependency closure, not as
screen-specific color compatibility. The pinned source is Telegram Android
12.9.0 at `9bcf3d2769c6d3f07105a992e5d9493e33ac3348`.

## Architecture Boundary

Generic Theme state, parsing, drawing, scheduling, and animation remain Java in
`:TMessagesProj` under their upstream `org.telegram.*` packages. Product storage,
navigation, and remote capability decisions live in `:app` and are injected
through interfaces owned by `Theme`.

NativeDo does not have Telegram's MTProto theme catalog. Any upstream branch
that needs remote theme or wallpaper data must cross `ThemeBackend`; unsupported
operations fail explicitly. Local `.attheme` import, export, accents, wallpaper
blobs, and auto-night behavior remain supported.

## Dependency Matrix

| Area | Status | Port boundary |
|---|---|---|
| `Theme.java` model and runtime | Host-wired baseline | Pinned 12.9.0 file; `ThemeHost`, `ThemeBackend`, preview-position interface, and temporary consumer migration APIs are local deltas. |
| Built-in themes | Exact | Blue, Dark Blue, Arctic, Day, and Night assets are copied from the pin. |
| Color keys, fallbacks, accents | Exact baseline | Full pinned key table, fallback graph, accent state, serialization, and resource factories. |
| Local theme files | Exact baseline | `.attheme` parsing, WLS/WPS data, preview generation, local persistence, import/export model. |
| Theme persistence | Host-wired | Telegram preferences remain in `Theme`; `SharedConfig` and application bootstrap provide NativeDo storage/lifecycle wiring. |
| Remote theme catalog | Backend | `ThemeBackend`; NativeDo reports `supportsRemoteThemes() == false`. Remote catalog/install/upload remain explicit unsupported operations, while local editor final-save no longer attempts an upload. |
| Wallpaper rendering | Host-wired local workflow | `Theme` rendering and default-pattern resources are present. NativeDo exposes local image, solid/gradient color, and theme-default overrides. Telegram pattern catalog, remote wallpaper lookup, and remote refresh requests cross `ThemeBackend`; capability checks ignore imported remote metadata when that backend is unavailable. |
| Auto-night | Host-wired baseline | Pinned schedule, sunrise/sunset, sensor, screen-state receiver, persistence, and bulletin paths are present. System mode checks on resume/configuration change. Scheduled mode keeps the pinned `MessagesController.updateTimerProc` branch; MainActivity supplies the foreground tick missing with NativeDo's tgnet shim. NativeDo exposes off/system/manual schedule/ambient-light settings; location-based sunrise/sunset UI and device verification remain. |
| Theme transition animation | Host-wired baseline | `ThemeAnimationSettings`, `StartColorsProvider`, ThemeDescription interpolation/delegates, visible-dialog descriptions, notification locking, 200ms animator, and deferred-after-navigation state are active in `ActionBarLayout`; MainActivity routes auto-night through the shared entry point. Telegram navigation preview mode is not present in NativeDo, so its previous-fragment preview branch is outside the current host boundary. |
| Theme picker/preview/editor | Host-wired local workflow | `ThemeEditorView`, `ThemesHorizontalListCell`, `ThemeCell`, `ThemeTypeCell`, `ColorPicker`, and wallpaper controls are pinned copies/adapted copies. NativeDo hosts the local ThemeActivity row model with horizontal theme cards, accent dots, day/night slots, global topic font size, reduced-animation preference, local wallpaper overrides, auto-night including location sunrise/sunset, and validated local import/export on a background executor. Preview uses `TopicThemePreviewFragment` with production Topic Detail cells plus temporary theme apply/cancel. Telegram remote catalog and `ThemeSetUrlActivity` remain outside the product boundary. |
| NativeDo consumers | Host-wired baseline | Root navigation and current fragment descriptions use `animateThemedValues`; the former `ThemeDelegate`, `toggleTheme()`, and instant `applyThemeDescriptions()` bridges have been removed. Topic list/detail, profile, login, main tabs, and current profile feeds expose explicit descriptions for their visible custom paints and views. New screens must join the same description path. |
| Audio/VoIP theme helpers | Excluded | NativeDo has no audio visualizer, proximity voice playback, or VoIP call-wave product subsystem. |

## Ordered Delivery

1. Close Theme core dependencies, resources, manifest, and local backend SPI.
2. Close wallpaper, accent, and auto-night runtime paths.
3. Port navigation-level theme animation without changing upstream timing,
   branch structure, or transition constants.
4. Port theme picker, preview, editor, and local import/export UI.
5. Migrate NativeDo screens to shared Theme descriptions/animation and remove
   temporary bridges.
6. Run vendor isolation/status, Theme contracts, full debug assembly, and device
   checks for transitions, wallpaper, schedule/sensor switching, import/export,
   and process restart restoration.

The local phase-4 workflow now has a closed day/night state model for selection,
accent changes, import, creation, editing, deletion, and persistence. Theme test
runtime bootstrap also covers both Robolectric and Paparazzi without adding
test branches to `Theme.java`.

The phase-5 checkpoint now covers the active NativeDo consumers and restores
upstream-shaped `BaseFragment.visibleDialog` ownership plus the applicable
`ActionBarLayout` dialog-description animation branch. Topic list typography
also restores `DialogCell.buildLayout` ownership of the shared 17dp/16dp paints
and 13dp counter paint.

The remaining delivery work is device validation and any fixes it exposes.
Further remote catalog or theme URL sharing work requires a product backend
decision and must continue to reuse the same Theme model rather than adding a
second theme state machine.

## Verification Rule

Compile and unit-test success verifies the dependency and state-machine
contracts only. Visual parity requires device checks of transition frames,
wallpaper motion/blur/intensity, picker previews, editor updates, auto-night
changes, and recreation after process death.

## Latest Checkpoint

- Hidden-fragment theme rebuild via `BaseFragment.clearViews()` is active.
- Theme picker now uses `ThemesHorizontalListCell` plus accent chips instead of the vertical radio list.
- Theme preview opens `TopicThemePreviewFragment` with Topic Detail production cells.
- Auto-night location sunrise/sunset UI, permission request, and Geocoder city name are active.
- Theme import/export file copy and parse run on a background executor.
- Theme import rejects wrong extensions, oversized files, and payloads without recognized theme keys or wallpaper metadata.
- Global topic font size scales topic lists, topic headers, post chrome, and post-body paints; reduced-animation state is persisted.
- Local wallpaper overrides support solid/gradient colors, local images, and restoring the theme default.
- Remaining work: device validation matrix for process restart, day/night, accent, local wallpaper, blur/motion/pattern, font-size extremes, editor save/cancel; then decide remote catalog/`ThemeSetUrlActivity`.

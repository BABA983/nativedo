# TODO

## Bulletin Follow-up

### NativeDo current files

- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/Bulletin.kt`
  - Compose bulletin host, auto-hide timing, current visibility/content state
- `apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/RequestErrorCard.kt`
  - error card, copy raw error, copy-success bulletin
- `apps/android/app/src/main/java/io/github/baba983/nativedo/ui/NativeDoApp.kt`
  - root bulletin host mount point
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/home/ui/HomeRoute.kt`
  - refresh failure bulletin trigger on home feed
- `apps/android/app/src/main/java/io/github/baba983/nativedo/feature/topic/ui/TopicRoute.kt`
  - refresh failure bulletin trigger on topic page

### Telegram reference paths

- `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/Components/Bulletin.java`
  - core bulletin lifecycle
  - single visible bulletin handling
  - enter/exit transition timing
  - swipe dismiss
  - top/bottom offset delegate model
- `/Users/baba/Documents/workspace_github/Telegram/TMessagesProj/src/main/java/org/telegram/ui/Components/BulletinFactory.java`
  - common bulletin entry points
  - error/success/info style presets

### Follow-up ideas

- [ ] let a new bulletin replace the current bulletin cleanly
- [ ] move bulletin position with top/bottom inset more like Telegram
- [ ] support swipe-to-dismiss
- [ ] support pause auto-hide while pressed
- [ ] keep the implementation Compose-native, do not port Telegram View internals directly

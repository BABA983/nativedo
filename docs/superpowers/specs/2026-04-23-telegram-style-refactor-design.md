# NativeDo Telegram-Style Refactor Design

## Overview

Refactor the NativeDo Android app to use Telegram's navigation infrastructure (1:1 Kotlin translation), with Compose for page content. First milestone: Topic list → Topic detail navigation with swipe-back, predictive back, and Telegram-quality transition animations.

## Architecture: Option B Hybrid

- **Navigation layer**: Telegram's `ActionBarLayout` + `BaseFragment` system, translated Java → Kotlin
- **Page content**: Jetpack Compose inside each fragment's `createView()` via `ComposeView`
- **Why this works**: Telegram's swipe-back and predictive back operate on the container level (`containerView.translationX`), independent of inner content framework

## Package Structure

```
io.github.baba983.nativedo/
├── MainActivity.kt                     ← Entry point (maps to Telegram LaunchActivity)
├── NativeDoApp.kt                      ← Application class
│
├── core/                               ← Utilities (maps to Telegram messenger/)
│   ├── AndroidUtilities.kt             ← dp/px, screen size, keyboard utils
│   ├── FileLog.kt                      ← Logging
│   ├── NotificationCenter.kt           ← Event bus
│   └── SharedConfig.kt                 ← App config
│
└── ui/                                 ← All UI (maps to Telegram ui/)
    ├── ActionBar/                      ← Navigation infrastructure
    │   ├── ActionBar.kt                ← Top navigation bar
    │   ├── ActionBarLayout.kt          ← Navigation container (swipe-back, predictive back)
    │   ├── BaseFragment.kt             ← Fragment base class
    │   ├── INavigationLayout.kt        ← Navigation interface
    │   ├── DrawerLayoutContainer.kt    ← Drawer container
    │   ├── BackDrawable.kt             ← Animated back arrow drawable
    │   └── SimpleTextView.kt           ← High-performance text view
    │
    ├── Components/                     ← Shared UI components
    │   ├── CubicBezierInterpolator.kt  ← Animation curves
    │   ├── LayoutHelper.kt             ← Layout params factory
    │   └── SizeNotifierFrameLayout.kt  ← Keyboard-aware layout
    │
    ├── Cells/                          ← Future: custom cell views
    │
    ├── Theme.kt                        ← Color/theme system
    │
    ├── TopicListFragment.kt            ← Topic list page
    └── TopicDetailFragment.kt          ← Topic detail page
```

### Principles

- `ui/ActionBar/`, `ui/Components/`, `core/` are 1:1 Kotlin translations from Telegram Java source
- All translated files retain Telegram's GPL v2 license header
- Business fragments (`TopicListFragment`, `TopicDetailFragment`) are original code using Compose

## Navigation System

### View Hierarchy

```
MainActivity (Activity)
  └─ ActionBarLayout (FrameLayout, implements INavigationLayout)
       ├─ containerViewBack  ← Previous fragment container (visible during swipe)
       └─ containerView      ← Current fragment container
```

### BaseFragment Lifecycle

| Method | Purpose |
|---|---|
| `createView(context)` | Returns the fragment's root View (ComposeView for content) |
| `onResume()` | Fragment becomes visible |
| `onPause()` | Fragment becomes hidden |
| `onFragmentDestroy()` | Cleanup |
| `onBackPressed()` | Back button handling |
| `canBeginSlide()` | Whether swipe-back is allowed (default true) |
| `isSwipeBackEnabled()` | Whether edge gesture is enabled |

### Fragment + Compose Pattern

```kotlin
class TopicListFragment : BaseFragment() {
    override fun createView(context: Context): View {
        actionBar.setTitle("Linux.do")
        actionBar.setBackButtonDrawable(BackDrawable(false))

        val contentView = ComposeView(context).apply {
            setContent {
                TopicListScreen(
                    onTopicClick = { topicId ->
                        presentFragment(TopicDetailFragment(topicId))
                    }
                )
            }
        }

        val frameLayout = SizeNotifierFrameLayout(context)
        frameLayout.addView(contentView, LayoutHelper.createFrame(
            LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()
        ))
        return frameLayout
    }
}
```

### Gesture Interactions (from Telegram ActionBarLayout)

1. **Edge swipe-back**: `onTouchEvent` detects left-edge horizontal drag → `prepareForMoving()` loads previous fragment into `containerViewBack` → `containerView.translationX` follows finger
2. **Predictive back (Android 14+)**: `onBackStarted/onBackProgress/onBackCancelled/onBackInvoked` → same container translationX but max displacement `dp(56)` with scale effect
3. **Fling detection**: VelocityTracker detects fast swipe (>3500px/s), triggers immediate back animation
4. **Animation curve**: `CubicBezierInterpolator.EASE_OUT_QUINT` for predictive back end animation

### presentFragment Transition

- **Open**: New fragment slides in from right (`translationX: width → 0`), old fragment shifts left (`translationX: 0 → -width/3`) + dims slightly
- **Close**: Current fragment slides out right, previous fragment restores from left

## Pages

### TopicListFragment — Topic List

- ActionBar title: "Linux.do"
- Compose `LazyColumn` with 20 hardcoded mock topics
- Each item shows: category tag, time ago, title, reply/view/like counts
- Tap item → `presentFragment(TopicDetailFragment(topicId))`

### TopicDetailFragment — Topic Detail

- ActionBar with `BackDrawable`, title shows topic title
- Compose content: author info, post body (placeholder text), mock replies
- Tap reply → push another `TopicDetailFragment` (validates multi-level stack)
- Back arrow tap → `finishFragment()`

### Mock Data

```kotlin
data class MockTopic(
    val id: Int,
    val title: String,
    val category: String,
    val replyCount: Int,
    val viewCount: Int,
    val likeCount: Int,
    val lastPostAge: String
)
```

Hardcoded list, no network. Replaced with Discourse API later.

## Success Criteria

1. List page scrolls smoothly
2. Tap item → detail page slides in from right
3. Detail page back arrow → slides out right
4. Left-edge swipe-back follows finger + spring animation on release
5. Predictive back gesture (Android 14+) with scale effect
6. Detail page tap → push another detail (multi-level stack)
7. Multi-level consecutive back all work correctly
8. App compiles and runs with `./apps/android/gradlew -p apps/android :app:assembleDebug`

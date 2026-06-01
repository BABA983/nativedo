# Topic Detail Rewrite Plan

Rewrite the topic detail screen from Compose (`ComposeView` + `LazyColumn` +
Composable `TopicPostCell`) to a pure-View `RecyclerListView` + canvas-drawn
`BaseCell` architecture, mirroring upstream `ChatActivity` / `ChatMessageCell`
for the list shell and `ArticleViewer`'s `BlockXxxCell` family for the
per-floor body.

No Compose bridge. The app has not shipped, so we rewrite directly and delete
the Compose path once parity is reached.

This document is the single source of truth for the rewrite. Tick items off
in-place as phases land.

## Why

- Topic detail is the most visually rich screen in the app. Compose's
  `AndroidView(TextView)` per paragraph is fine for correctness but unable to
  hit upstream's gesture / animation / measurement fidelity.
- `ChatMessageCell` / `ArticleViewer` use canvas-drawn `View`s with
  `StaticLayout` + `ImageReceiver`. We already use the same pattern in
  [`TopicCell.java`](../apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic/TopicCell.java)
  and [`TopicListFragment.kt`](../apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic/TopicListFragment.kt),
  so the list shell pattern is proven inside this repo.
- The `CookedPostBlock` block model already exists inside
  [`HtmlPostBodyView.kt`](../apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/HtmlPostBodyView.kt#L1113-L1157)
  — only the rendering layer changes.

## Constraints

- Follow [`AGENTS.md`](../AGENTS.md) and [`docs/code-layout.md`](./code-layout.md):
  - Native ports of `BlockXxxCell` measurement / drawing in `:app` get
    SPDX `GPL-2.0-or-later` header + one-line attribution **and** a
    `vendor-manifest.json` entry (`port_mode: native`, `forced_exact:
    [measurement, drawing]` as appropriate).
  - From-scratch NativeDo code (e.g. reaction chip strip) needs neither a
    header nor a manifest entry.
- Reuse `:telegram` infrastructure (`RecyclerListView`, `BaseCell`,
  `SizeNotifierFrameLayout`, `ImageReceiver`, `LinkSpanDrawable`, `LinkPath`,
  `QuoteSpan`, `AnimatedFloat`, `Theme`, `AndroidUtilities`, ...).
- **Do not** introduce syntax highlighting in this rewrite (Compose path
  doesn't have it either — stay at parity).
- **Do not** port `ReactionsLayoutInBubble` from upstream. Its dependency
  graph (TLRPC reaction objects, AnimatedEmoji set, ...) is too heavy for
  Discourse's `emoji_name + count + reacted` model. We draw our own chips,
  borrowing only visual / measurement style.

## Architecture target

```diagram
TopicDetailFragment (BaseFragment, no ComposeView)
└── SizeNotifierFrameLayout
    ├── ActionBar
    ├── RecyclerListView (vertical, LinearLayoutManager)
    │   └── TopicAdapter (RecyclerListView.SelectionAdapter)
    │       ├── HEADER : TopicHeaderCell   (title + tags + category)
    │       ├── POSTS  : TopicPostCell     (one per floor, BaseCell, drawn)
    │       └── FOOTER : TopicFooterCell   (loading / retry / end-of-list)
    ├── PullForegroundDrawable             (same path as TopicListFragment)
    └── (later) ReplyEnterView / JumpToBottomFAB / FloatingDateView
```

Inside each `TopicPostCell` (custom-drawn `View`, **no child Views, no
`AndroidView`, no Compose**):

```diagram
TopicPostCell  ── extends BaseCell, implements Theme.Colorable
                                            + TextSelectionHelper.ArticleSelectableView
│
├── avatar  ImageReceiver  (AvatarDrawable + ImageLocation, same as TopicCell)
├── header StaticLayouts: displayName · @username · floor#N · relativeTime
│
├── body: List<PostBodyBlock>   ← rendered inline, NOT separate child Views
│        each block:
│          measure(maxWidthPx)         -> heightPx
│          draw(canvas)                 (canvas already translated)
│          onTouch(event, localX, localY)
│          onAttachedToWindow() / onDetachedFromWindow()
│          applyTheme()
│
│        concrete blocks (Phase 3 + 4):
│          ParagraphBlock   ← BlockParagraphCell
│          HeadingBlock     ← BlockHeaderCell / BlockSubheaderCell
│          RuleBlock        ← BlockDividerCell
│          ImageBlock       ← BlockPhotoCell
│          CollageBlock     ← BlockCollageCell
│          OneboxBlock      (NativeDo-original, link card)
│          QuoteBlock       ← BlockBlockquoteCell  (+ expand-quoted-post flow)
│          CodeBlock        ← BlockPreformattedCell (no syntax highlight)
│          ListBlock        ← BlockListItemCell / BlockOrderedListItemCell
│          TableBlock       ← BlockTableCell
│          SpoilerBlock     (NativeDo-original)
│          PollBlock        (NativeDo-original)
│
├── reactions row (drawn, NativeDo-original chip strip)
└── reply footer: "N replies" + recent reply avatar stack
```

## Module / package layout

```diagram
io.github.baba983.nativedo
├── core.html                       (NEW, pure-Kotlin, no Android UI deps)
│   ├── CookedPostBlock.kt              ← moved from HtmlPostBodyView.kt
│   ├── CookedPostBodyParser.kt         ← parseCookedPostBody + helpers (Jsoup only)
│   ├── GalleryImage.kt                 ← moved
│   ├── PostImageUrlResolver.kt         ← resolvePostImageUrl, cookedGalleryImages
│   └── TopicReplyTargetResolver.kt     ← reply-target shaping migrated from
│                                          TopicPostMeta.kt (NOT deleted)
│
└── ui.topic
    ├── TopicDetailFragment.kt          ← rewritten, no ComposeView
    ├── TopicAdapter.kt                 ← RecyclerListView.SelectionAdapter
    ├── cell/
    │   ├── TopicPostCell.java          ← BaseCell, canvas-drawn (Java)
    │   ├── TopicHeaderCell.java
    │   ├── TopicFooterCell.java
    │   └── TopicPostCellDelegate.java
    └── body/
        ├── PostBodyBlock.kt            ← interface
        ├── PostBodyBlockFactory.kt     ← CookedPostBlock[] → PostBodyBlock[]
        ├── PostBodyBuildContext.kt     ← paints, theme keys, ImageReceiver pool,
        │                                  delegate
        ├── PostBodyTextBuilder.kt      ← Spannable + StaticLayout pipeline
        │                                  (replaces buildInlinePostBodyText)
        ├── PostBodyImagePool.kt        ← per-cell ImageReceiver registry
        ├── ParagraphBlock.kt   HeadingBlock.kt   RuleBlock.kt
        ├── ImageBlock.kt       CollageBlock.kt
        ├── OneboxBlock.kt      QuoteBlock.kt     CodeBlock.kt
        ├── ListBlock.kt        TableBlock.kt
        ├── SpoilerBlock.kt     PollBlock.kt
        └── BuiltText.kt                ← output of PostBodyTextBuilder
```

### Files deleted at the end of the rewrite

Only after the parity checklist (Phase 6) passes:

- `feature/topic/ui/TopicDetailContent.kt`
- `feature/topic/ui/TopicDetailSkeleton.kt`
- `feature/topic/ui/TopicPostCell.kt`  (Compose)
- `feature/topic/ui/TopicPostMeta.kt`  (logic migrated to `core.html.TopicReplyTargetResolver`)
- `ui/topic/TopicDetailScreen.kt`
- `core/ui/HtmlPostBodyView.kt`  (after parser extraction)
- `core/ui/PostBodyTypography.kt`, `PostBodyInternalLinks.kt`,
  `PostBodyChipSpan.kt`, `InlineTextImages.kt`  (folded into
  `body/PostBodyTextBuilder.kt` and friends)

## Image loading — single channel via ImageReceiver

The app already routes the upstream `ImageLoader` HTTP path through our
OkHttp + `PersistentCookieJar`:

- [`NativeDoApp.kt`](../apps/android/app/src/main/java/io/github/baba983/nativedo/NativeDoApp.kt)
  installs `NativeDoImageDownloader` via
  `TelegramImageLoader.setHttpImageDownloader(...)`.
- The downloader uses
  [`AppContainer.imageOkHttpClient`](../apps/android/app/src/main/java/io/github/baba983/nativedo/core/di/AppContainer.kt#L74-L77)
  which carries `_t` / `_forum_session` / `cf_clearance` cookies, our UA, and
  Referer.

That means **every image in the rewritten topic detail page (avatars, post
images, onebox thumbnails, collage tiles, inline emoji/image spans) goes
through `ImageReceiver`**. No Coil fallback path. This also matches what
`TopicCell.java` and `ProfileFragment.kt` already do today.

## Image / link / URL coverage matrix

For Phase 3 and 4. Confirms each existing Compose code path has a target.

| Source HTML | Today (Compose) | Rewrite (View) |
|---|---|---|
| `<img>` inside paragraph | `AsyncImage` inline span | `DrawableSpan` backed by `ImageReceiver`, via `PostBodyTextBuilder` |
| Standalone `<img>` / `figure` | `CookedImageBlock` (AsyncImage) | `ImageBlock` (`ImageReceiver`) |
| Lightbox `<a class="lightbox">` group | `CookedCollageBlock` (AsyncImages) | `CollageBlock` (`ImageReceiver[]`) |
| `aside.quote` (collapsed) | `CookedQuoteBlock` + `fetchPostByNumber` | `QuoteBlock` + `delegate.expandQuote(topicId, postNumber)` |
| `<pre><code>` | `CookedCodeBlock` (horizontal scroll) | `CodeBlock` (horizontal scroll, monospace `StaticLayout`) |
| `<ul>` / `<ol>` | `CookedListBlock` | `ListBlock` |
| `<table>` | `CookedTableBlock` | `TableBlock` |
| `aside.onebox` | `CookedOneboxBlock` | `OneboxBlock` |
| `[poll]` widget | `CookedPollBlock` | `PollBlock` |
| `details` (spoiler) | `CookedSpoilerBlock` | `SpoilerBlock` |
| `<hr>` | `CookedRuleBlock` | `RuleBlock` |
| `<h1..h6>` | `CookedHeadingBlock` | `HeadingBlock` |
| Inline link | `URLSpan` in `TextView` | `URLSpan` in `Spannable`, hit-tested by `ParagraphBlock`, ripple via `LinkSpanDrawable` |
| Inline @user chip | `PostBodyChipSpan` in `TextView` | same span, drawn through `StaticLayout` |
| Inline `#topic` link | `PostBodyInternalLinks` parsing | same parser, span emitted into `Spannable` |

## Phases

Each phase is a self-contained, mergeable slice with a clear validation step.
Steps inside a phase are sequential; phases don't overlap.

### Phase 0 — parser / model extraction

Pure refactor, no behavior change. Compose path keeps compiling.

- [ ] Create `io.github.baba983.nativedo.core.html` package (pure Kotlin,
      Android UI deps forbidden — only Jsoup + stdlib).
- [ ] Move out of `HtmlPostBodyView.kt`:
  - `CookedPostBlock` sealed interface + all variants
  - `parseCookedPostBody` + walking helpers
  - `GalleryImage` data class
  - `cookedGalleryImages` (visibility raised to `internal` in new package)
  - `resolvePostImageUrl`
- [ ] `InlineImageRenderHandle` + `prepareInlineContent` +
      `applyInlineImagePlaceholders` **stay** in `core.ui` for now — Phase 1
      will replace them, not move them.
- [ ] Update all callers (`HtmlPostBodyView.kt`, `TopicPostCell.kt`,
      `TopicDetailContent.kt`) to import from `core.html`.
- [ ] `parseCookedPostBody` visibility: `internal` → `public` (it crosses
      module boundary now).

**Validate:** `:app:testDebugUnitTest` + `:app:assembleDebug` both green;
app launches and topic detail still renders identically.

### Phase 1 — text pipeline foundation

The hardest piece. Establishes the StaticLayout + Spannable pipeline that
every text-bearing block will reuse.

- [ ] Define `PostBodyBlock` interface:
  ```kotlin
  interface PostBodyBlock {
      fun measure(maxWidthPx: Int): Int            // returns height
      fun draw(canvas: Canvas, parentWidth: Int)
      fun onTouch(event: MotionEvent, localX: Float, localY: Float): Boolean
      fun onAttachedToWindow()
      fun onDetachedFromWindow()
      fun applyTheme()
  }
  ```
  Local coordinates are intentional — the cell strips its own padding +
  block offset before forwarding.
- [ ] Define `PostBodyBuildContext`: shared `TextPaint`s (paragraph /
      heading / mono / quote), accent + chip colors, `ImageReceiver` pool,
      `PostBodyDelegate`.
- [ ] Define `PostBodyDelegate`: `onLinkClick(url)`, `onTopicClick(id)`,
      `onUserClick(username)`.
- [ ] Implement `PostBodyTextBuilder`. Returns:
  ```kotlin
  class BuiltText(
      val spannable: Spannable,
      val pendingImages: List<PendingInlineImage>,
      val links: List<LinkRegion>,
  ) {
      fun attach(host: View, cookieStore: CookieStore): Handle
  }
  ```
  Inline images are `DrawableSpan`s backed by `ImageReceiver`; `attach()`
  starts the loads and arranges for `host.invalidate()` on each frame.
  **No `TextView.tag` dependency.** Port the span-building logic from
  `buildInlinePostBodyText` / `PostBodyChipSpan` / `PostBodyInternalLinks`.
- [ ] Implement `ParagraphBlock`, `HeadingBlock`, `RuleBlock`. Each owns a
      `StaticLayout` + (optional) `BuiltText.Handle`.
- [ ] Build a dev-only `TopicBodyHarnessFragment`: a `RecyclerListView` of
      paragraph-only rows fed from a hand-picked fixture under
      [`fixtures/discourse/`](../fixtures/discourse/). Entry point gated on
      `BuildConfig.DEBUG` — **not** wired into `TopicListFragment`'s real
      menu. Easy to delete in Phase 6.
- [ ] Unit tests on `PostBodyTextBuilder` for chip / link / inline-image
      span emission.

**Validate:** harness renders fixture text correctly with correct fonts /
spacing / line height; no Compose involvement in the harness path.

### Phase 2 — link interactivity

No selection in this phase.

- [ ] `LinkPath` + `LinkSpanDrawable` ripple on tap. Hit-test via
      `BuiltText.links` regions inside `ParagraphBlock.onTouch`.
- [ ] Wire delegate routes for `URLSpan`, `@user` chips, `#topic` links.
- [ ] Long-press on a link → `delegate.onLinkLongPress(url)` (Phase 6 may
      wire a context menu).
- [ ] **Selection is deferred to Phase 7.** `ArticleTextSelectionHelper` in
      `:telegram` is a no-op stub today — fixing that is its own port.

**Validate:** harness — tapping links shows ripple, fires delegate; no
unintended selection mode.

### Phase 3 — media blocks

Image pipeline goes through `ImageReceiver` (cookie auth already handled —
see "Image loading" above).

- [ ] `ImageBlock` — single `ImageReceiver`, supports source rect for image
      viewer hand-off via
      `delegate.onImagePress(url, gallery, sourceBounds, hostView)`.
      Reuses the existing `openImageViewer(...)` stub in `TopicDetailFragment`.
- [ ] `CollageBlock` — grid of `ImageReceiver`s, mirroring `BlockCollageCell`
      measurement.
- [ ] `OneboxBlock` — thumbnail `ImageReceiver` + title `StaticLayout` +
      description `StaticLayout`.

**Validate:** harness opens a topic with images / collage / oneboxes; all
load through OkHttp + cookies; tapping fires the image viewer stub.

### Phase 4 — remaining block types

Ported one type per commit. Each ships independently:

- [ ] `QuoteBlock` (with collapsed → expanded fetch flow)
- [ ] `CodeBlock` (monospace, horizontal scroll on the block, no highlight)
- [ ] `ListBlock` (ordered / unordered, nested)
- [ ] `TableBlock` (port `TableLayout` from `:telegram`)
- [ ] `SpoilerBlock` (collapsed bar + reveal animation via `AnimatedFloat`)
- [ ] `PollBlock`

**Validate after each:** matching fixture renders correctly in the harness.

### Phase 5 — TopicPostCell shell

The container that holds the body block list. Java to match `ChatMessageCell`
style and to keep cell-bind hot paths simple.

- [ ] `TopicPostCell extends BaseCell implements Theme.Colorable,
      TextSelectionHelper.ArticleSelectableView`.
- [ ] Owns: avatar `ImageReceiver` + `AvatarDrawable`; 1–2 header
      `StaticLayout`s; `List<PostBodyBlock>`; reactions strip; reply footer.
- [ ] Public API mirrors `ChatMessageCell.setMessageObject`:
  ```java
  void setPost(Post post, Post prev, Post next,
               boolean isFirst, boolean isLast,
               long currentTopicId,
               Map<Integer, String> localPostsByNumber);
  ```
- [ ] `TopicPostCellDelegate`:
      `didPressLink`, `didPressUser`, `didPressTopic`, `didPressImage`,
      `didPressReply`, `didPressReaction`, `didPressMoreReactions`,
      `didLongPressPost`, `didExpandQuote`.
- [ ] **Cell-level layout cache.** Key:
      `(post.id, post.updatedAt, post.editCount, widthPx)`.
      Caches `(List<PostBodyBlock>, totalHeightPx)`. `onViewRecycled` only
      detaches the current cell's `ImageReceiver`s — it does not invalidate
      the cache. Without this, fling re-Jsoup-parses every frame.
- [ ] Reactions strip: NativeDo-original chip layout. Reads `post.reactions`
      / `post.currentUserReaction`. Draws emoji via `ImageReceiver` from
      `emojiCatalog`.

**Validate:** harness swaps from "paragraph rows" to "full cell rows"
rendering real `Post` fixtures end-to-end.

### Phase 6 — TopicDetailFragment swap

Replace the Compose shell with the View shell. **Block on the parity
checklist below.**

- [ ] Rewrite `TopicDetailFragment.createView` to return
      `SizeNotifierFrameLayout(RecyclerListView + ActionBar + (placeholder for
      floating views))`.
- [ ] `TopicAdapter extends RecyclerListView.SelectionAdapter`, 3 view types
      (header / post / footer). `getItemId(position)` returns `post.id` for
      stable IDs.
- [ ] `DiffUtil.ItemCallback` with composite key:
  ```kotlin
  data class PostDiffKey(
      val id: Long,
      val updatedAt: String,
      val editCount: Int,
      val cookedHtmlHash: Int,
      val currentUserReaction: String?,
      val reactionUsersCount: Int,
      val reactionsSignature: Int,
      val authorAvatarTemplate: String,
      val authorTitle: String,
  )
  ```
  `updatedAt` alone misses optimistic reaction updates.
- [ ] Pagination via `RecyclerListView.OnScrollListener` (same threshold
      logic as `TopicListFragment`).
- [ ] Pull-to-refresh: reuse `TopicListFragment`'s `PullForegroundDrawable`
      wiring.
- [ ] `viewModel.uiState` collected via `lifecycleScope` +
      `Flow.collectLatest` → `TopicAdapter.submitList(...)`.
- [ ] Collapsed title: scroll listener detects first-post out-of-frame →
      `actionBar.setTitle(post.title)`.
- [ ] Image viewer hand-off: `TopicPostCellDelegate.didPressImage` →
      existing `openImageViewer(...)` stub.
- [ ] Migrate `TopicPostMeta.kt` reply-target shaping into
      `core.html.TopicReplyTargetResolver` (pure functions). Do **not**
      delete `TopicPostMeta.kt` until the new module compiles and is wired.
- [ ] Extend `TopicViewModel`:
  - `fun toggleReaction(postId: Long, reactionId: String)` — calls
    `discourseApiClient.toggleReaction`, applies `Post.toggledReaction`
    optimistically, rolls back on failure.
  - `val reactionSheet: StateFlow<TopicReactionSheetState?>` — drives the
    "reaction users" bottom sheet (currently lives in `TopicDetailContent`).
  - `fun openReactionSheet(postId: Long, initialReactionId: String?)` /
    `fun closeReactionSheet()`.
  - `fun expandQuote(topicId: Long, postNumber: Int)` → returns
    `Flow<Result<String>>` for `QuoteBlock`.
  - Jump-to-floor one-shot event channel (replaces `TopicJumpHighlight`).
- [ ] Reactions users sheet UI: `org.telegram.ui.ActionBar.BottomSheet`
      (not Material3 `ModalBottomSheet`).
- [ ] Delete the files listed under "Files deleted" above.
- [ ] Remove the dev-only `TopicBodyHarnessFragment` entry.

**Parity checklist — must all be green before deleting any Compose file:**

| Area | Compose behavior | View target |
|---|---|---|
| Initial load | spinner + `TopicDetailSkeleton` | `TopicFooterCell` loading state |
| Empty / error / retry | inline retry button | `TopicFooterCell` retry state |
| Pull-to-refresh | `PullToRefreshBox` | `PullForegroundDrawable` |
| Pagination | scroll near bottom → `loadMore` | `RecyclerListView.OnScrollListener` |
| Collapsed title | first-post off → `actionBar.setTitle` | scroll listener |
| Link / @user / #topic tap | `PostBodyDelegate` (in Compose) | `TopicPostCellDelegate` |
| Image tap | `onOpenImage(...)` → PhotoViewer stub | same delegate path, same stub |
| Quote collapsed → expanded | `fetchPostByNumber` callback | `QuoteBlock` + `delegate.didExpandQuote` |
| Reply target preview | `TopicPostMeta` shaping | `TopicReplyTargetResolver` |
| Reply jump highlight | `TopicJumpHighlight` state | one-shot event → `AnimatedFloat` on cell |
| Reaction toggle | optimistic `Post.toggledReaction` | same — moved into VM |
| Reaction picker | bottom sheet | Telegram `BottomSheet` |
| Reaction users sheet | bottom sheet | Telegram `BottomSheet`, state in VM |
| Replies-of-post count | footer row in cell | drawn footer in cell |

**Validate:** `:app:assembleDebug` + manual run through the checklist on a
real topic. Smoke-test on a long thread (≥100 posts) for fling smoothness.

### Phase 7 — text selection (separate effort)

Standalone, no longer blocks the rewrite.

- [ ] Decide: substantially port upstream `TextSelectionHelper.ArticleTextSelectionHelper`
      into `:telegram` (add manifest entry, `port_mode: exact`,
      `forced_exact: [gesture, drawing]`), or write a minimal NativeDo
      selection helper.
- [ ] Wire to `TopicPostCell` via `fillTextLayoutBlocks(...)`.

## Risks and open questions

- **Selection scope.** `ArticleTextSelectionHelper` is the only realistic
  source for multi-block selection. A real port pulls in upstream's
  `TextSelectionOverlay` rendering. Acceptable cost — scoped to Phase 7.
- **Cell layout cache memory.** Long topics × wide block lists could grow
  the cache. Bound it at e.g. 64 most recent posts (LRU), evict on
  configuration change.
- **Reactions data shape.** Confirm `emojiCatalog` always carries usable
  image URLs for every `reaction.id` we render — if not, fall back to
  `defaultReactionFallback` paths that already exist on `Post`.
- **AnimatedEmoji in reactions.** Out of scope for this rewrite. Reaction
  emoji are static `ImageReceiver`s for now.

## Conventions

- New Java cells (`TopicPostCell`, `TopicHeaderCell`, `TopicFooterCell`)
  carry SPDX header + one-line attribution to the upstream `BlockXxxCell` /
  `ChatMessageCell` they native-port from. Manifest entries:
  `port_mode: native`, `forced_exact` listing the subsystems whose constants
  / branch structure we keep (typically `measurement, drawing`; add
  `gesture` for cells with custom hit testing).
- New Kotlin blocks under `body/` follow the same rule when they port an
  upstream cell's measurement / drawing. NativeDo-original blocks
  (`SpoilerBlock`, `PollBlock`, `OneboxBlock`, reactions strip) get neither
  header nor manifest entry.
- Cell sub-area constants (`dp(...)`, animation durations, snap thresholds)
  match upstream when the corresponding subsystem is `forced_exact`.

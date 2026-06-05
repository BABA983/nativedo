# Topic Detail Rewrite Plan

Rewrite the topic detail screen from Compose (`ComposeView` + `LazyColumn` +
Composable `TopicPostCell`) to a pure-View `RecyclerListView` + canvas-drawn
`BaseCell` architecture.

The list shell should follow NativeDo's existing Telegram-style container
patterns in `TopicListFragment` / `TopicCell`, while the per-floor body should
borrow the measurement, drawing, and hit-testing ideas from Telegram's
`ChatMessageCell` / `ArticleViewer` family. Where Telegram already has a good
public component in `:telegram`, use it directly. Where the implementation is
Telegram-coupled but the UX is right, copy/port the algorithm into a NativeDo
body block or local sheet.

No Compose bridge. The app has not shipped, so we rewrite directly and delete
the Compose path once parity is reached.

This document is the single source of truth for the rewrite. Tick items off
in-place as phases land.

## Why

- Topic detail is the most visually rich screen in the app. Compose's
  `AndroidView(TextView)` per paragraph is fine for correctness but unable to
  hit upstream's gesture / animation / measurement fidelity.
- `TopicListFragment` / `TopicCell` already prove the shell pattern inside this
  repo. Telegram's `ChatActivity` / `ChatMessageCell` and `ArticleViewer` are
  still the best reference for measurement, drawing, ripple, hit-testing, and
  list-shell ergonomics.
- The `CookedPostBlock` block model now lives in
  [`core.html`](../apps/android/app/src/main/java/io/github/baba983/nativedo/core/html/CookedPostBlock.kt)
  — the rewrite is about replacing the rendering layer and, where it helps,
  reusing Telegram-style algorithms/components rather than rewriting everything
  from scratch.

## Constraints

- Follow [`AGENTS.md`](../AGENTS.md) and [`docs/code-layout.md`](./code-layout.md):
  - Native ports of Telegram measurement / drawing / gesture code in `:app`
    get SPDX `GPL-2.0-or-later` header + one-line attribution **and** a
    `vendor-manifest.json` entry (`port_mode: native`, `forced_exact:
    [measurement, drawing, gesture]` as appropriate).
  - Telegram UI components that do not bind NativeDo models should live in
    `:telegram` with upstream `org.telegram.*` packages. `:app` may consume
    them directly.
  - From-scratch NativeDo code (e.g. reaction chip strip) needs neither a
    header nor a manifest entry.
- Reuse `:telegram` infrastructure (`RecyclerListView`, `BaseCell`,
  `SizeNotifierFrameLayout`, `ImageReceiver`, `LinkSpanDrawable`, `LinkPath`,
  `AnimatedFloat`, `Theme`, `AndroidUtilities`, `BottomSheet`,
  `BottomSheetWithRecyclerListView`, ...).
- **Do not** introduce syntax highlighting in this rewrite (Compose path
  doesn't have it either — stay at parity).
- **Do not** port `ReactionsLayoutInBubble` from upstream. Its dependency
  graph (TLRPC reaction objects, AnimatedEmoji set, ...) is too heavy for
  Discourse's `emoji_name + count + reacted` model. We draw our own chips,
  borrowing only visual / measurement style.
- **Do not** treat `apps/android/telegram` stubs as the source of truth for
  non-trivial UI behavior. When you want Telegram behavior, diff against
  `vendor/telegram-android` and then either copy the component into `:telegram`
  or copy/port the algorithm into a NativeDo block.
- Telegram-derived blocks are split into three buckets:
  1. exact/native ports of `ArticleViewer` block geometry and rendering,
  2. selective native ports inspired by chat/webpage/poll/spoiler code,
  3. NativeDo-original blocks and sheets.

## Architecture target

```diagram
TopicDetailFragment (BaseFragment, no ComposeView)
└── SizeNotifierFrameLayout
    ├── ActionBar
    ├── RecyclerListView (vertical, LinearLayoutManager)
    │   └── TopicAdapter (RecyclerListView.SelectionAdapter)
    │       ├── HEADER : TopicHeaderCell   (title + date line + load progress + tags)
    │       ├── POSTS  : TopicPostCell     (one per floor, BaseCell, drawn)
    │       └── FOOTER : TopicFooterCell   (loading / retry / end-of-list)
    ├── PullForegroundDrawable             (replicated pattern, ~80 LOC, see Phase 7)
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
│        concrete blocks (Phase 2, 4 + 5):
│          ParagraphBlock   ← ArticleViewer.BlockParagraphCell
│          HeadingBlock     ← BlockHeaderCell / BlockSubheaderCell
│          RuleBlock        ← BlockDividerCell
│          ImageBlock       ← BlockPhotoCell layout, NativeDo URL backend
│          CollageBlock     ← BlockCollageCell layout, NativeDo URL backend
│          OneboxBlock      ← ChatMessageCell link-preview heuristics,
│                              adapted to Discourse onebox model
│          QuoteBlock       ← BlockBlockquoteCell  (+ expand-quoted-post flow)
│          CodeBlock        ← BlockPreformattedCell (no syntax highlight)
│          ListBlock        ← BlockListItemCell / BlockOrderedListItemCell
│          TableBlock       ← BlockTableCell / TableLayout algorithm,
│                              adapted to CookedTableBlock
│          SpoilerBlock     ← SpoilerEffect / SpoilersTextView reveal model
│                              when Telegram-style reveal is used
│          PollBlock        (NativeDo Discourse model; may borrow Telegram
│                              percent / result-animation behavior)
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
│   └── PostImageUrlResolver.kt         ← resolvePostImageUrl, cookedGalleryImages
│
├── feature.topic.ui
│   └── TopicReplyTargetResolver.kt     ← reply-target shaping migrated here;
│                                          NOT moved to core.html because it depends on
│                                          htmlToPreviewText, which is UI-layer)
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

Completed after the Phase 7 parity checklist passed on-device:

- [x] `feature/topic/ui/TopicDetailContent.kt`
- [x] `feature/topic/ui/TopicDetailSkeleton.kt`
- [x] `feature/topic/ui/TopicPostCell.kt`  (Compose)
- [x] `feature/topic/ui/TopicPostMeta.kt`  (logic migrated to `feature.topic.ui.TopicReplyTargetResolver`)
- [x] `ui/topic/TopicDetailScreen.kt`
- [x] `core/ui/HtmlPostBodyView.kt`
- [x] `core/ui/PostBodyTypography.kt`

Kept because the pure View renderer still uses them through
`body/PostBodyTextBuilder.kt`:

- `core/ui/PostBodyInternalLinks.kt`
- `core/ui/PostBodyChipSpan.kt`
- `core/ui/InlineTextImages.kt`
- `core/ui/PostBodyInlineSpans.kt`

## Telegram reuse strategy

Use the most Telegram code that fits without importing Telegram's business
model into NativeDo.

| NativeDo target | Telegram source | Reuse mode |
|---|---|---|
| List shell | `TopicListFragment` / upstream `ChatActivity` | local pattern + upstream reference |
| Post cell shell | `TopicCell` / upstream `ChatMessageCell` | NativeDo Java cell; port hot-path drawing/gesture patterns |
| Text blocks | `ArticleViewer.BlockParagraphCell`, header/footer cells | native-port measurement / drawing |
| Quote / code / list / divider | `ArticleViewer.BlockBlockquoteCell`, `BlockPreformattedCell`, `BlockListItemCell`, `BlockDividerCell` | native-port measurement / drawing |
| Image / collage | `ArticleViewer.BlockPhotoCell`, `BlockCollageCell` | native-port layout; NativeDo URL + `ImageReceiver` backend |
| Onebox | upstream `ChatMessageCell` link-preview code + `MessageObject` link description ideas | native-port layout / hit testing; NativeDo `OneboxPreview` model; no `TLRPC.WebPage` |
| Table | `ArticleViewer.BlockTableCell`, `TableLayout` | partial native-port; adapt algorithm to `CookedTableBlock` |
| Spoiler | `TextStyleSpan`, `SpoilerEffect`, `SpoilersTextView`, `SpoilersClickDetector` | trimmed NativeDo renderer; no `SpoilerEffect2` media/GL port |
| Poll | upstream chat poll behavior | NativeDo Discourse model; borrow only percent normalization / row-state / result animation as attributed helpers if copied |
| Reaction picker/users/replies sheets | `BottomSheet`, `BottomSheetWithRecyclerListView`, reaction-user row/tab concepts | use/copy generic sheet components into `:telegram`; NativeDo sheet contents in `:app` |

Directly usable generic components belong in `:telegram` under `org.telegram.*`.
NativeDo-bound renderers belong in `:app` under `io.github.baba983.nativedo.*`
and get manifest entries when Telegram algorithms are copied or substantially
adapted.

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

For the renderer phases. Confirms each existing Compose code path has a target.

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

Pure refactor, no behavior change while the Compose path still existed.

- [x] Create `io.github.baba983.nativedo.core.html` package (pure Kotlin,
      Android UI deps forbidden — only Jsoup + stdlib).
- [x] Move out of `HtmlPostBodyView.kt`:
  - `CookedPostBlock` sealed interface + all variants
  - `parseCookedPostBody` + walking helpers
  - `GalleryImage` data class
  - `cookedGalleryImages` (visibility raised to `internal` in new package)
  - `resolvePostImageUrl`
- [x] `prepareInlineContent` + `applyInlineImagePlaceholders` stay in
      `core.ui` and are now used by `PostBodyTextBuilder`.
- [x] Update all temporary Compose callers to import from `core.html`; those
      callers were later deleted in Phase 7.
- [x] Keep `internal` visibility — `:app` is a single Gradle module, so
      Kotlin module visibility still covers all of
      `io.github.baba983.nativedo.*`.

**Validate:** `:app:testDebugUnitTest` + `:app:assembleDebug` both green;
app launches and topic detail still renders identically.

### Phase 1 — ViewModel behavior migration

Move topic-detail behavior behind testable ViewModel / Repository APIs while the
Compose screen still exists. This separates behavior changes from the later
renderer swap.

- [x] Move reaction toggling into `TopicViewModel`:
  - `fun onReactionSelected(postId: Long, reactionId: String)` calls the
    repository, applies `Post.toggledReaction(...)` optimistically, and rolls
    back on failure.
  - Existing Compose UI calls this command instead of directly mutating state +
    calling `DiscourseApiClient`.
- [x] Move reaction-user sheet state into `TopicViewModel`:
  - `val reactionSheet: TopicReactionSheetUiState?`
  - `fun openReactionSheet(postId: Long, reactions: List<PostReaction>, initialReactionId: String?)`
  - `fun dismissReactionSheet()`
- [x] Add repository/ViewModel quote expansion API:
  - `suspend fun expandQuote(topicId: Long, postNumber: Int): Result<String>`
  - Keep post-number semantics explicit; reactions and stable ids use `post.id`,
    floors / replies / quotes / jumps use `postNumber`.
- [x] Add a one-shot jump-to-floor event channel to replace
      `TopicJumpHighlight` state during the View swap.
- [x] Migrate `TopicPostMeta.kt` reply-target shaping into
      `TopicReplyTargetResolver` (pure functions, same package as today). It
      depends on `htmlToPreviewText` from `core.ui`, so it stays UI-layer —
      **not** `core.html`.
- [x] Add / update `TopicViewModelTest` coverage for optimistic reaction
      update + rollback, reaction-sheet state, quote expansion, and
      `post.id` vs `postNumber` mapping.

**Validate:** Compose topic detail still behaved identically before the View
swap; existing `TopicViewModelTest` / `TopicReplyTargetResolverTest` pass.

### Phase 2 — text pipeline foundation

The hardest piece. Establishes the StaticLayout + Spannable pipeline that
every text-bearing block will reuse.

- [x] Define `PostBodyBlock` interface:
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
- [x] Define `PostBodyBuildContext`: shared `TextPaint`s (paragraph /
      heading / mono / quote), accent + chip colors, `ImageReceiver` pool,
      `PostBodyDelegate`.
- [x] Define `PostBodyDelegate`: `onLinkClick(url)`, `onTopicClick(id)`,
      `onUserClick(username)`. Phase 3 will add `onLinkLongPress(url)`.
- [x] Implement `PostBodyTextBuilder`. Returns:
  ```kotlin
  class BuiltText(
      val spannable: Spannable,
      val pendingImages: List<PendingInlineImage>,
      val links: List<LinkRegion>,
  ) {
      fun attach(host: View): Handle
  }
  ```
  `PostBodyTextBuilder` ports the span-building logic from
  `buildInlinePostBodyText` / `PostBodyChipSpan` / `PostBodyInternalLinks`.
  Inline image placeholder data is emitted through `pendingImages` and covered
  by tests; `BuiltText.attach()` now attaches each pending inline emoji/image
  to a host-owned `ImageReceiver`, so the View path uses the same image loader
  bridge as avatars, reaction icons, and post media. `TextLayoutBlock.measure()`
  also attaches newly built text immediately when the host cell is already on
  screen, so inline emoji do not miss their first load after a recycled cell is
  measured.
- [x] Implement `ParagraphBlock`, `HeadingBlock`, `RuleBlock`. Each owns a
      `StaticLayout` + (optional) `BuiltText.Handle`.
- [x] Build a dev-only `TopicBodyHarnessFragment` **extending
      `org.telegram.ui.ActionBar.BaseFragment`** (not Compose-hosted): a
      `RecyclerListView` of body-block rows fed from hand-picked fixtures under
      [`fixtures/discourse/`](../fixtures/discourse/). Entry point lives in
      `ProfileFragment`'s Dev tools section, is guarded by `BuildConfig.DEBUG`
      both when the row is added and when the click is handled, and is **not**
      wired into `TopicListFragment`'s real menu. Deleted in Phase 7.
- [x] Unit tests on `PostBodyTextBuilder` for chip / link / inline-image
      span emission.

**Validate:** harness renders fixture text correctly with correct fonts /
spacing / line height; no Compose involvement in the harness path.

### Phase 3 — link interactivity

No selection in this phase.

- [x] `LinkPath` + `LinkSpanDrawable`-style ripple on tap. Hit-test via
      `BuiltText.links` regions inside `ParagraphBlock.onTouch`.
- [x] Wire delegate routes for `URLSpan`, `@user` chips, `#topic` links.
- [x] Extend `PostBodyDelegate` with `onLinkLongPress(url)`. Long-press on
      a link calls it; Phase 7 may wire a context menu.
- [x] **Selection is handled in Phase 8.** `ArticleTextSelectionHelper` in
      `:telegram` now has a trimmed NativeDo topic-detail port; Phase 3 stays
      link-only except for the long-press hand-off.

**Validate:** harness — tapping links shows ripple, fires delegate; no
unintended selection mode.

### Phase 4 — media and onebox blocks

Image pipeline goes through `ImageReceiver` (cookie auth already handled —
see "Image loading" above).

- [x] `ImageBlock` — single `ImageReceiver`. Exposes
      `fun imageBoundsInCell(): RectF` returning the block-local rect of the
      drawn image. `TopicPostCell.onTouch` resolves that into window
      coordinates (`getLocationInWindow` + cell origin + block offset)
      before calling `delegate.didPressImage(url, gallery, sourceBoundsInWindow,
      hostView)`. Reuses the existing `openImageViewer(...)` stub in
      `TopicDetailFragment`.
- [x] `CollageBlock` — grid of `ImageReceiver`s, mirroring `BlockCollageCell`
      measurement. Same `imageBoundsInCell(index)` pattern per tile.
- [x] `OneboxBlock` — native port of upstream `ChatMessageCell` link-preview
      measurement / drawing / hit-testing, backed by a NativeDo
      `OneboxPreview` model. It now builds an immutable
      `OneboxPreviewLayout` containing thumbnail `ImageReceiver` bounds,
      site/title/description `StaticLayout`s, open-button bounds, total height,
      and separate hit regions for description/card/thumbnail/button. It does
      **not** import `TLRPC.WebPage` or `MessageObject` into `:app` body code.

**Validate:** harness opens real JSON/cooked fixture rows with images / collage /
oneboxes; all load through OkHttp + cookies. Tapping image/collage routes through
the image-viewer stub-shaped delegate path and records `url` / gallery size /
source window bounds for device verification; full `PhotoViewer` remains blocked
until the telegram/ui dependency graph is restored.

### Phase 5 — remaining block types

Ported one type per commit. Each ships independently:

- [x] `QuoteBlock` (collapsed → expanded fetch flow).
      Visual QuoteBlock rendering is in place (title, accent bar, quote body,
      link hit-testing). Discourse `aside.quote` parsing now preserves
      `data-topic` / `data-post`; `QuoteBlock` requests expansion through
      `PostBodyDelegate.onQuoteExpand`, `TopicPostCellDelegate.didExpandQuote`,
      and `TopicDetailFragment` -> `TopicViewModel.expandQuote()`, then replaces
      the block body and remeasures the cell.
      **Expanded state lives on the block instance** (`var expanded: Boolean`
      + cached fetched HTML). The adapter-level layout cache in Phase 6
      preserves block instances across rebind, so expand state survives
      scroll-recycle (matches user expectation; the current Compose
      `remember{}` loses it). Cache eviction or `Post.editCount` /
      `updatedAt` change rebuilds the block from scratch — expected, since
      the quoted content may have changed.
- [x] `CodeBlock` (monospace, horizontal scroll on the block, no highlight)
- [x] `ListBlock` (ordered / unordered, nested)
- [x] `TableBlock` — lightweight text table with uniform-width columns and
      horizontal scrolling is in place. Full `BlockTableCell` / `TableLayout`
      algorithm parity remains a follow-up; `org.telegram.ui.Components.TableLayout`
      is typed to Telegram page cells and is **not** a drop-in widget.
- [x] `SpoilerBlock` — plain Discourse `<details>` now renders as a
      NativeDo block-level collapsed / revealed card. If
      Telegram-style text reveal is used, borrow the marker-span,
      `Layout.getSelectionPath` range-to-rect, tap detector, and reveal-ripple
      model from `TextStyleSpan` / `SpoilerEffect` / `SpoilersTextView` /
      `SpoilersClickDetector`. Do **not** port `SpoilerEffect2` media/GL.
- [x] `PollBlock` — read-only Discourse poll renderer is in place for the
      current parsed model (name/type/status/voters/options). Full vote state,
      selected state, permissions, result percentages, and POST `/polls/vote`
      integration remain follow-up work. May borrow Telegram poll percent
      normalization, stable option-row state, pending-vote state, and result-bar
      animation as attributed helpers if copied.

**Validate after each:** matching fixture renders correctly in the harness.

### Phase 6 — TopicPostCell shell

The container that holds the body block list. A minimal Kotlin shell is in place
for harness validation and now extends Telegram's `BaseCell`; the final hot-path
cell can still be migrated to Java as parity work continues.

- [x] `TopicPostCell extends BaseCell implements Theme.Colorable,
      TextSelectionHelper.ArticleSelectableView`.
      The Kotlin shell now extends Telegram `BaseCell`, implements
      `Theme.Colorable`, implements the local
      `TextSelectionHelper.ArticleSelectableView`, and uses `BaseCell`
      long-press handling. Real text selection block collection is wired in
      Phase 8; final Java hot-path migration remains optional parity work.
- [x] Owns: avatar `ImageReceiver` + `AvatarDrawable`; 1–2 header
      `StaticLayout`s; `List<PostBodyBlock>`; reactions strip; reply footer.
      The shell owns avatar `ImageReceiver` + `AvatarDrawable`, header
      `StaticLayout`s, `List<PostBodyBlock>`, a clickable reaction strip, and a
      lightweight reply footer (`N replies` + avatar placeholders). Reaction
      emoji icons now draw through `ImageReceiver` from `PostReaction.iconUrl`
      / `emojiCatalog`; the View path wires reaction picker, users sheet, VM
      toggle integration, and replies-of-post sheet.
- [x] Public API mirrors `ChatMessageCell.setMessageObject`:
  ```java
  void setPost(Post post, Post prev, Post next,
               boolean isFirst, boolean isLast,
               long currentTopicId,
               Map<Integer, String> localPostsByNumber);
  ```
- [x] `TopicPostCellDelegate`:
      `didPressLink`, `didPressUser`, `didPressTopic`, `didPressImage`,
      `didPressReply`, `didPressReaction`, `didPressMoreReactions`,
      `didLongPressPost`, `didExpandQuote`.
      Delegate now covers link/user/topic/image/long-link, reply/reaction/
      more-reactions, long-post, and quote expansion.
- [x] **Safe adapter caches only.** Keep the cache at data/state boundaries,
      not at reusable View/block-instance boundaries. Current adapter has:
      - bounded parsed-body LRU keyed by `(post.id, cookedHtmlHash)`, passing
        cached `CookedPostBlock`s into `TopicPostCell` to avoid repeated Jsoup
        parsing;
      - bounded `PostBodyBlockState` LRU keyed by
        `(post.id, cookedHtmlHash, post.editCount)`, preserving `QuoteBlock`
        expanded HTML and `SpoilerBlock.revealed` across scroll recycle.

      Do **not** cache `List<PostBodyBlock>` / `totalHeightPx` in the adapter
      right now. Current block instances capture a concrete `host`, `delegate`,
      `PostBodyImagePool`, and `ImageReceiver` ownership; reusing them across
      recycled cells risks leaking old Views, invalidating the wrong cell,
      routing clicks through stale delegates, and breaking attach/detach for
      images. Telegram's comparable cache lives on `MessageObject` as layout
      data (`layoutCreated`, `textLayoutBlocks`, width/density guards), not as
      adapter-owned view/block instances. If long-thread profiling later shows
      layout work is still a bottleneck, add either:
      1. a cell-local reuse key in `TopicPostCell` for the same post/html/width
         while the same cell instance is bound; or
      2. a Telegram-style immutable `TopicPostLayoutState` render model that
         stores measured text/bounds and explicit width/density/theme keys, but
         **does not** hold `View`, `Context`, `ImageReceiver`, or delegate refs.
- [x] Reactions strip: NativeDo-original chip layout. Reads `post.reactions`
      / `post.currentUserReaction`. Draws emoji via `ImageReceiver` from
      `emojiCatalog`. Emoji URLs go through the same
      `NativeDoImageDownloader` bridge, so cookies are handled for free.

**Validate:** harness renders real JSON `Post` fixture rows from
[`fixtures/discourse/topic-detail.json`](../fixtures/discourse/topic-detail.json)
/
[`fixtures/discourse/topic-posts.json`](../fixtures/discourse/topic-posts.json)
plus cooked-HTML coverage rows from `fixtures/discourse/cooked-*.html` end-to-end.

### Phase 7 — TopicDetailFragment swap

Replace the Compose shell with the View shell. **Block on the parity
checklist below.**

- [x] Rewrite `TopicDetailFragment.createView` to return
      `SizeNotifierFrameLayout(RecyclerListView + ActionBar + (placeholder for
      floating views))`. A first View-shell implementation now binds real
      `TopicPostCell` rows and no longer hosts `TopicDetailScreen` through
      `ComposeView`; pull-refresh/floating views remain follow-up.
- [x] `TopicAdapter extends RecyclerListView.SelectionAdapter`. Physical cells may
      still be header / post / footer, but adapter input is an explicit row
      state machine: `InitialSkeleton`, `InitialError`, `Empty`, `Header`,
      `PostRow`, `AppendLoading`, `AppendError`, `EndOfList`. `getItemId(position)`
      returns `post.id` for post rows and stable synthetic ids for state rows.
      Current adapter remains scoped inside `TopicDetailFragment` while the
      rewrite is still in-flight, but it now uses the explicit row state
      machine and stable ids.
- [x] `DiffUtil.ItemCallback` with composite key:
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
      Implemented via `DiffUtil.Callback` over the explicit row list, with the
      same `PostDiffKey` content signature plus reply-count / reply-label
      changes for row contents.
- [x] Pagination via `RecyclerListView.OnScrollListener` (same threshold
      logic as `TopicListFragment`).
- [x] `recyclerListView.itemAnimator = null`. Canvas-drawn cells animate
      via `AnimatedFloat` themselves; the default `DefaultItemAnimator`
      cross-fade looks broken on `BaseCell`. Revisit if a real
      add/remove animation is needed later (use `DialogsItemAnimator` as
      `TopicListFragment` does, or write a custom one).
- [x] Pull-to-refresh: replicate the `PullForegroundDrawable` pattern from
      [`TopicListFragment`](../apps/android/app/src/main/java/io/github/baba983/nativedo/ui/topic/TopicListFragment.kt)
      lines ~857–933 (custom header view + manual `OnScrollListener` glue,
      ~80 LOC). `TopicDetailFragment` now wraps its `RecyclerListView` in a
      local `PullRefreshLayout` using the vendored drawable and calls
      `TopicViewModel.refresh()` on release.
- [x] `viewModel.uiState` collected via `lifecycleScope` +
      `Flow.collectLatest` → `TopicAdapter.submitList(...)`.
      Current implementation uses the fragment's `MainScope`, matching the
      existing `TopicListFragment` collector pattern; it is cancelled in
      `onFragmentDestroy()`.
- [x] Collapsed title: scroll listener detects first-post out-of-frame →
      `actionBar.setTitle(post.title)`. The View shell now clears the ActionBar
      title while the topic header/first post are visible and restores the topic
      title once the first post scrolls under the ActionBar.
- [x] Image viewer hand-off: `TopicPostCellDelegate.didPressImage` →
      existing `openImageViewer(...)` stub.
- [x] Reactions users sheet UI: Telegram `BottomSheet` /
      `BottomSheetWithRecyclerListView` container (not Material3
      `ModalBottomSheet`) with NativeDo content rows. The local `:telegram`
      now copies upstream `BottomSheet` and `BottomSheetWithRecyclerListView`
      verbatim; `TopicDetailFragment` subclasses that container for reaction
      tabs/chips, loading/empty states, user rows, selected reaction changes,
      and dismissal wired back to `TopicViewModel.dismissReactionSheet()`.
      Clicking an existing reaction chip opens this users sheet for that
      reaction; tabs are built from both the post's visible reactions and the
      loaded reaction-user groups, and tab glyphs use actual emoji images via
      `ImageReceiver` when `iconUrl` / `emojiCatalog` provides one.
- [x] Reaction picker UI: Telegram `BottomSheet` or compact anchored popup using
      Telegram reaction container geometry as a reference, but no
      `ReactionsLayoutInBubble` / TLRPC reaction model port. Current View path
      opens a `BottomSheetWithRecyclerListView` with available `validReactions`
      plus visible reaction ids, renders each row with a reaction glyph/image,
      subtitle, selected check mark, and divider, then calls
      `TopicViewModel.onReactionSelected()` on selection.
- [x] Replies-of-post / floor sheet UI: Telegram `BottomSheet` container +
      NativeDo `RecyclerListView` rows with fixed header, bounded list height,
      title/subtitle/preview, and dividers. Current View path reuses
      `TopicReplyTargetResolver.replySheetEntriesByPostNumber`, opens a
      `BottomSheetWithRecyclerListView` from the drawn reply footer, renders
      avatar-backed reply rows, and jumps to the selected reply row.
- [x] Delete the files listed under "Files deleted" above.
- [x] Remove the dev-only `TopicBodyHarnessFragment` entry.

**Parity checklist — passed before deleting the Compose topic files:**

| Area | Compose behavior | View target |
|---|---|---|
| Initial load | spinner + `TopicDetailSkeleton` | local post-shaped skeleton using Telegram `FlickerLoadingView` shimmer paint |
| Empty / error / retry | inline retry button | `TopicFooterCell` retry state |
| Pull-to-refresh | `PullToRefreshBox` | `PullForegroundDrawable` |
| Pagination | scroll near bottom → `loadMore` | `RecyclerListView.OnScrollListener` + one-row post skeleton while appending |
| Collapsed title | first-post off → `actionBar.setTitle` | scroll listener |
| Link / @user / #topic tap | `PostBodyDelegate` (in Compose) | `TopicPostCellDelegate` |
| Image tap | `onOpenImage(...)` → PhotoViewer stub | same delegate path, same stub |
| Quote collapsed → expanded | `fetchPostByNumber` callback | `QuoteBlock` + `delegate.didExpandQuote` |
| Reply target preview | `TopicPostMeta` shaping | `TopicReplyTargetResolver` |
| Reply jump highlight | `TopicJumpHighlight` state | one-shot event → `AnimatedFloat` on cell |
| Reaction toggle | optimistic `Post.toggledReaction` | same — moved into VM |
| Reaction picker | bottom sheet | Telegram `BottomSheet`, only for more/add reaction |
| Reaction users sheet | bottom sheet | Telegram `BottomSheet`, state in VM, existing reaction chip opens users |
| Replies-of-post count | footer row in cell | drawn footer in cell |

**Validate:** `:app:assembleDebug` + manual run through the checklist on a
real topic. Smoke-test on a long thread (≥100 posts) for fling smoothness.

### Phase 8 — text selection (separate effort)

Standalone, no longer blocks the rewrite.

- [x] Replace the no-op `TextSelectionHelper.ArticleTextSelectionHelper` stub
      with a trimmed NativeDo topic-detail port in `:telegram`. Manifest entry:
      `port_mode: native`, `forced_exact: [gesture, drawing]`. This is not a
      full exact upstream port.
- [x] Wire `TopicPostCell` through `fillTextLayoutBlocks(...)`, including nested
      `TextLayoutBlock`, `ListBlock`, `QuoteBlock`, and revealed
      `SpoilerBlock` text.
- [x] Long-press in `TopicPostCell` enters selection through the shared helper,
      uses the press coordinates to select the touched word, and shows a
      floating ActionMode with copy / select-all actions.
- [ ] Follow-up: port draggable handles, magnifier, translate action,
      multi-cell drag selection, RecyclerView auto-scroll, and full upstream
      `TextSelectionOverlay` chrome.

## Risks and open questions

- **Selection scope.** Current selection is a trimmed native port: the touched
  word is highlighted and copied through a floating ActionMode. Select-all is
  scoped to the current text block. Upstream-style draggable handles,
  cross-block drag, and overlay chrome remain the next parity step.
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
  upstream cell's measurement / drawing / gesture / animation. `OneboxBlock`
  requires attribution + manifest entry if it ports `ChatMessageCell` link
  preview algorithms. `SpoilerBlock` requires attribution + manifest entry if
  it ports Telegram spoiler span/effect/reveal algorithms. `PollBlock` remains
  NativeDo-original unless it copies Telegram poll percent / row-state /
  drawing / animation structure; copied helpers get their own provenance.
- Pure NativeDo product code (Discourse poll parsing/API models, reaction chip
  strip, reply footer, sheet content rows) gets neither header nor manifest
  entry unless actual Telegram source or branch structure was copied.
- Generic Telegram UI components such as `BottomSheet` and
  `BottomSheetWithRecyclerListView` should be copied into `:telegram` with
  upstream packages whenever the dependency surface is manageable. NativeDo-
  bound sheet contents stay in `:app` and talk in NativeDo-shaped models.
- Cell sub-area constants (`dp(...)`, animation durations, snap thresholds)
  match upstream when the corresponding subsystem is `forced_exact`.

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
  - From-scratch NativeDo code needs neither a header nor a manifest entry.
- Reuse `:telegram` infrastructure (`RecyclerListView`, `BaseCell`,
  `SizeNotifierFrameLayout`, `ImageReceiver`, `LinkSpanDrawable`, `LinkPath`,
  `AnimatedFloat`, `Theme`, `AndroidUtilities`, `BottomSheet`,
  `BottomSheetWithRecyclerListView`, ...).
- **Do not** introduce syntax highlighting in this rewrite (Compose path
  doesn't have it either — stay at parity).
- **Do not** port full `ReactionsLayoutInBubble` from upstream. Its dependency
  graph (TLRPC reaction objects, AnimatedEmoji set, ...) is too heavy for
  Discourse's `emoji_name + count + reacted` model. `TopicPostCell` keeps a
  NativeDo-bound `ReactionButton` subset, borrowing upstream field names,
  26dp button height, 20dp image slot, 4dp spacing, wrapping measurement, and
  reaction theme keys, plus a local subset of `recordDrawingState()` /
  `animateChange()` style update transitions.
- **Do not** treat `apps/android/telegram` stubs as the source of truth for
  non-trivial UI behavior. When you want Telegram behavior, diff against the
  pristine checkout (`../telegram`, see
  `scripts/vendor-setup.sh --print-dir`) and then either copy the component into
  `:telegram` or copy/port the algorithm into a NativeDo block.
- Telegram-derived blocks are split into three buckets:
  1. exact/native ports of `ArticleViewer` block geometry and rendering,
  2. selective native ports inspired by chat/webpage/poll/spoiler code,
  3. NativeDo-original blocks and sheets.

## Architecture target

```diagram
TopicDetailFragment (BaseFragment, no ComposeView)
└── TopicDetailHostLayout (SizeNotifierFrameLayout + AdjustPanLayoutHelper)
    ├── ActionBar
    ├── RecyclerListView (vertical, LinearLayoutManager)
    │   └── TopicAdapter (RecyclerListView.SelectionAdapter)
    │       ├── HEADER : TopicHeaderCell   (title + date line + load progress + tags)
    │       ├── DATE   : TopicDateCell     (ChatActionCell date-only native port)
    │       ├── POSTS  : TopicPostCell     (one per floor, BaseCell, drawn)
    │       └── FOOTER : TopicFooterCell   (loading / retry / end-of-list)
    ├── PullForegroundDrawable             (replicated pattern, ~80 LOC, see Phase 7)
    ├── ChatActivityEnterView-style persistent reply composer
    ├── ChatActivitySideControlsButtonsLayout page-down control
    └── TopicFloatingDateController + floating TopicDateCell
```

Inside each `TopicPostCell` (custom-drawn `View`, **no Compose**; most blocks
draw on the cell canvas, while top-level scrollable/media blocks may attach
Telegram-style child views for closer `ArticleViewer` structure):

```diagram
TopicPostCell  ── extends BaseCell, implements Theme.Colorable
                                            + TextSelectionHelper.ArticleSelectableView
│
├── avatar  ImageReceiver  (AvatarDrawable + ImageLocation, same as TopicCell)
├── header StaticLayouts: displayName · @username · floor#N · relativeTime
│
├── body: List<PostBodyBlock>   ← measured/drawn by the cell; selected blocks
│                                may expose embedded child Views
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
│          DetailsBlock     ← TL_pageBlockDetails / BlockDetailsCell
│          CalloutBlock     ← Discourse callout; shares ArticleViewer-shaped
│                              text/link/container pipeline
│          PollBlock        (NativeDo Discourse model; may borrow Telegram
│                              percent / result-animation behavior)
│
├── reactions row (drawn, `ReactionButton` subset over Discourse reactions)
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
    ├── TopicDetailHostLayout.java      ← ChatActivityFragmentView-shaped
    │                                      IME resize/pan transition host
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
        ├── ParagraphBlock.java HeadingBlock.java RuleBlock.java
        ├── ImageBlock.java     CollageBlock.java
        ├── OneboxBlock.java    QuoteBlock.java   CodeBlock.java
        ├── ListBlock.java      TableBlock.java
        ├── DetailsBlock.java   CalloutBlock.java
        ├── PollBlock.java
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
| Quote / code / list / divider | `ArticleViewer.BlockBlockquoteCell`, `ReplyMessageLine`, `BlockPreformattedCell`, `BlockListItemCell`, `BlockDividerCell` | native-port measurement / drawing |
| Image / collage | `ArticleViewer.BlockPhotoCell`, `BlockCollageCell` | native-port layout; collage uses an embedded inner `RecyclerListView`; NativeDo URL + `ImageReceiver` backend |
| Onebox | upstream `ChatMessageCell` link-preview code + `MessageObject` link description ideas | native-port layout / hit testing; NativeDo `OneboxPreview` model; no `TLRPC.WebPage` |
| Table | `ArticleViewer.BlockTableCell`, `TableLayout` | partial native-port; adapt algorithm to `CookedTableBlock` |
| Spoiler | `TextStyleSpan`, `SpoilerEffect`, `SpoilersTextView`, `SpoilersClickDetector` | trimmed NativeDo renderer; no `SpoilerEffect2` media/GL port |
| Poll | upstream `ChatMessageCell` poll button/result behavior + `Components/poll/buttons/PollButtonDrawable*` + `CheckBoxBase` | NativeDo Discourse model; native-port PollButton state, choice controls, result bars, voter-count/check drawable subsets, and 300ms result animation |
| Reaction picker/users/replies sheets | `BottomSheet`, `BottomSheetWithRecyclerListView`, reaction-user row/tab concepts | use/copy generic sheet components into `:telegram`; NativeDo sheet contents in `:app` |

Directly usable generic components belong in `:telegram` under `org.telegram.*`.
NativeDo-bound renderers belong in `:app` under `io.github.baba983.nativedo.*`
and get manifest entries when Telegram algorithms are copied or substantially
adapted.

## Image loading — single channel via ImageReceiver

The app already routes the upstream `ImageLoader` HTTP path through our OkHttp
client with the session cookie interceptor (`SessionCookieInterceptor`):

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
| `<img>` inside paragraph | `AsyncImage` inline span | `ReplacementSpan` backed by `ImageReceiver`, via `PostBodyTextBuilder` |
| Standalone `<img>` / `figure` | `CookedImageBlock` (AsyncImage) | `ImageBlock` (`ImageReceiver`) |
| Lightbox `<a class="lightbox">` group | `CookedCollageBlock` (AsyncImages) | `CollageBlock` (`ImageReceiver[]`) |
| `aside.quote` (collapsed) | `CookedQuoteBlock` + `fetchPostByNumber` | `QuoteBlock` + `delegate.expandQuote(topicId, postNumber)` |
| `<pre><code>` | `CookedCodeBlock` (horizontal scroll) | `CodeBlock` (horizontal scroll, monospace `StaticLayout`) |
| `<ul>` / `<ol>` | `CookedListBlock` | `ListBlock` |
| `<table>` | `CookedTableBlock` | `TableBlock` |
| `aside.onebox` | `CookedOneboxBlock` | `OneboxBlock` |
| `[poll]` widget | `CookedPollBlock` | `PollBlock` |
| `<details>` | `CookedPostBlock.Details` | `DetailsBlock` |
| `.spoiler` / `.spoiled` inline text | `TextStyleSpan.FLAG_STYLE_SPOILER` | shared `TextLayoutBlock` / `OneboxBlock` spoiler drawing + tap reveal |
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
  bridge as avatars, reaction icons, and post media. Rich text blocks build
  their `StaticLayout` before attaching inline image receivers, so
  replacement spans establish their final bounds first and receiver filters
  use the same dimensions that drawing and selection will use. If the host
  cell is already on screen, the handle is attached immediately after layout
  creation so inline emoji do not miss their first load after a recycled cell
  is measured. The local `Emoji` shim still draws system emoji instead of
  Telegram's atlas / animated emoji pipeline, but it keeps the upstream
  `replaceEmoji` overload shape, replacement-span metrics, baseline/bottom
  alignment, performance-class span limit, parser/per-span exception guards,
  and selection-bounds hook so malformed external Unicode content degrades
  instead of breaking topic text layout.
- [x] Implement `ParagraphBlock`, `HeadingBlock`, `RuleBlock`. Each owns a
      `StaticLayout` + (optional) `BuiltText.Handle`. `TextLayoutBlock`
      now mirrors ArticleViewer text-cell padding (`textY = dp(8)`,
      `height += dp(8 + 8)`) and `lineSpacingExtra = dp(4)`.
      `HeadingBlock` now also mirrors ArticleViewer's title/header/subheader
      paint split: h1 uses `dp(23)`, h2 uses `dp(20)`, h3-h6 use `dp(17)`,
      and all heading levels request Telegram's `fonts/mw_bold.ttf` typeface
      through `AndroidUtilities.getTypeface(...)`.
      Body text layouts that are not direct `TextLayoutBlock`s now route
      through the same `ArticleTextLayout` helper, so image captions, onebox
      title/body, details titles, poll rows, code, and table cells share
      ArticleViewer's `createLayoutForText` line spacing
      (`spacingMultiplier = 1.0`, `spacingAdd = dp(4)`, no font pad).
      `TopicPostCell` does not add a second inter-block gap before blocks that
      already own their ArticleViewer-style vertical rhythm:
      paragraph/header/divider, quote, details, code, and callout.
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
      TextLayoutBlock, ImageBlock captions, OneboxBlock rich text,
      DetailsBlock titles, and PollBlock option titles now share
      `ArticleLinkPressState`, which owns the `LinkSpanDrawable` path, release
      animation, long-press callback, and touch-slop cancellation instead of
      each block keeping a separate pressed-link implementation. When multiple
      link spans overlap the same touch point, hit testing now follows
      `ArticleViewer.checkLayoutForLinks` by preferring the wider covering span.
      Inline pill/tag/icon spans that expose custom highlight bounds are mixed
      with normal `StaticLayout` selection segments in the same pressed-link
      path, and those custom bounds participate in link hit-testing even when
      they cover only part of the link, so tag chips and icon+text links do not
      fall back to a plain rectangular text highlight.
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
      coordinates (`getLocationInWindow` + cell origin + block offset),
      first asking the active top-level body block for URL-keyed bounds so
      nested list/details/table media include their parent offsets,
      before calling `delegate.didPressImage(url, gallery, sourceBoundsInWindow,
      hostView, sourceBoundsProvider)`. `ImageBlock`, `CollageBlock`, and
      `OneboxBlock` also provide block-local bounds by gallery URL, so
      `PhotoViewer` can animate close back to the currently visible source image
      after swiping within the same block. `TopicDetailFragment.openImageViewer(...)`
      now delegates to the telegram-module `PhotoViewer` NativeDo port and passes
      source bounds through the provider for the opening/closing scale/fade animation. The source
      `TopicPostCell` hides the current viewer image, including images reached by
      horizontal gallery swipes, until PhotoViewer cleanup restores it;
      the viewer supports double-tap zoom, pinch zoom, bounded pan while zoomed,
      horizontal next/previous swipes at base scale with drag-follow and edge
      spring-back without replaying the source opening handoff animation for every
      gallery step, and vertical drag-to-dismiss
      with Telegram-style background fade plus close/spring-back release threshold.
- [x] `CollageBlock` — grid of `ImageReceiver`s, mirroring `BlockCollageCell`
      measurement. Same `imageBoundsInCell(index)` pattern per tile, plus
      URL-keyed source bounds for PhotoViewer open/close hand-off. It now owns
      `BlockCollageCell`-style internal spacing (`listTop = dp(8)`,
      `bottom = dp(16)`) instead of depending on `TopicPostCell` media spacing.
- [x] `OneboxBlock` — native port of upstream `ChatMessageCell` link-preview
      measurement / drawing / hit-testing, backed by a NativeDo
      `OneboxPreview` model. It now builds an immutable
      `OneboxPreviewLayout` containing thumbnail `ImageReceiver` bounds,
      site/title/description layouts, Telegram-style link-preview chrome
      bottom, total height, and separate hit regions for
      description/card/thumbnail. The whole preview is the link target,
      and its press feedback uses Telegram's `linkPreviewBounce` pattern
      (`ButtonBounce(..., 2.0f, 2.0f)` plus `getScale(0.01f)`) with the
      scale pivot centered on `linkPreviewY + linkPreviewHeight / 2`, and
      `linkPreviewSelector`-style `Theme.createRadSelectorDrawable` hotspot /
      state updates. The
      rounded preview background, left line, and link accent color now route
      through `ReplyMessageLine.TYPE_LINK`, matching the ChatMessageCell
      quote/link chrome entry point. Only the site/domain line uses that
      returned name color; title and description use the normal body text
      color like `ChatMessageCell` after its draw-time paint reset, and
      description inline links use `ReplyMessageLine.getColor()` like the
      upstream preview description draw path. Domain/title/description paints
      now clone Telegram's `Theme.chat_replyNamePaint` /
      `Theme.chat_replyTextPaint` metrics, including the upstream
      `(2 * SharedConfig.fontSize + 10) / 3` reply text sizing, then apply
      NativeDo's current body/link colors locally. Its
      top-level host inset is 18dp, then the preview rail/background apply
      `ChatMessageCell.drawLinkPreview`'s `linkX -= dp(1.33f)` internal shift
      while keeping the right edge fixed like the upstream `right - linkX`
      width calculation; the internal preview text keeps Telegram's extra 10dp
      offset from that shifted `linkX`.
      Short text-only/small-image previews now size the rounded background to
      the visible preview content plus Telegram's 20dp internal text allowance
      instead of always filling the whole topic body column; large-media
      previews still use the media width to fill the preview column. Hit
      testing uses the upstream `checkLinkPreviewMotionEvent` vertical span:
      it starts at `linkPreviewY` instead of making the rounded background's
      top padding clickable, and stops at `linkPreviewY + linkPreviewHeight +
      dp(8)` for the non-InstantView path instead of using the full local
      block tail/protrusion height. Horizontally, the hit region starts at the
      unshifted preview text column like `unmovedTextX`; the visual
      `linkX -= dp(1.33f)` rail/background extension is not a separate tap
      target. Once pressed, MOVE keeps the selector/bounce active anywhere
      inside this whole preview hit region and only cancels after leaving it,
      matching `ChatMessageCell`'s `instantPressed` / `linkPreviewSelector`
      motion branch instead of cancelling when crossing local
      card/thumbnail/description sub-targets.
      Its background/left rail follow ChatMessageCell's preview rect:
      top starts at `linkPreviewY - dp(6)`, bottom ends at
      `linkPreviewY + linkPreviewHeight`, with a separate `dp(6)` block tail
      outside that chrome; large-media previews also keep the upstream
      `dp(2.66f)` extra chrome tail when media follows text.
      Small-image previews keep the upstream `isSmallImageLinkPreviewType`
      whitelist, including `telegram_aicomposetone`, and preserve the
      `force_large_media` / `force_small_media` override order used by
      `ChatMessageCell`: force-large disables small media first, then
      force-small can force the 48dp side thumbnail path back on. They also keep the
      upstream RTL-title branch that subtracts the 48dp thumbnail width from
      the following description width, the draw-time RTL/right-aligned x-offset
      formula, and the `ChatMessageCell` small-image chrome rule of min 50dp
      then `-dp(8)` bottom trim. The
      description preserves source HTML and renders through
      `PostBodyTextBuilder`, so inline spoiler spans use the shared
      `TextStyleSpan` / `SpoilerEffect` draw/reveal path and persist revealed
      state through `PostBodyBlockState`. Hidden onebox thumbnails keep the
      media placeholder background while PhotoViewer suppresses the source
      `ImageReceiver` draw. Non-small site/domain rows now use Telegram's
      measured `ceil(paint.measureText(site_name) + 1)` layout width instead
      of occupying the whole preview text column and keep the upstream 0dp
      extra line spacing; small-image site/domain rows use the same
      `generateStaticLayout` path as the other wrapped preview text, including
      the vendored `StaticLayoutEx.createStaticLayout` final layout and
      `dp(1)` line spacing. It does
      **not** import `TLRPC.WebPage` or `MessageObject` into `:app` body code.

**Validate:** harness opens real JSON/cooked fixture rows with images / collage /
oneboxes; all load through OkHttp + cookies. Tapping image/collage routes through
the image-viewer delegate path, records `url` / gallery size / source window
bounds for device verification, and now opens the telegram-module `PhotoViewer`
NativeDo port. `PhotoViewerDebugActivity` also exposes `topicCellTap`,
`topicCellNestedImageTap`, `topicCellCollageTap`, and
`topicCellOneboxThumbnailTap` scenarios so plain, nested list/details/table,
collage, and onebox-thumbnail media hand-off rectangles can be checked
on-device. The same debug activity exposes `topicCellPollVote`,
`topicCellInlineSpoilerTap`, `topicCellReplyFooterTap`,
`topicCellReactionTap`, `topicCellMoreReactionTap`,
`topicCellPostLongPress`, `topicCellReplyFooterLongPress`, and
`topicCellReactionLongPress` smoke scenarios for poll option submission state,
inline `SpoilerEffect` reveal state, reply-footer delegate routing, reaction /
more-reaction delegate routing, and `BaseCell` long-press routing without adding
low-value style/pixel tests. The fragment-level `topicDetailInteraction`
scenario builds a minimal `TopicDetailFragment` host and invokes the production
post-options popup, reply action, root-bottom reply panel, and standalone
reaction rail paths directly, so popup/panel regressions can be checked from
logcat even when shell-level input injection is unavailable. The long-press and
fragment-level debug scenarios emit `passed=` and `failures=` fields so the
device run has an explicit pass/fail result instead of requiring manual log
interpretation.

### Phase 5 — remaining block types

Ported one type per commit. Each ships independently:

- [x] `QuoteBlock` (collapsed → expanded fetch flow).
      Visual QuoteBlock rendering follows ArticleViewer blockquote text
      geometry and chrome rather than ChatMessageCell reply-bubble chrome:
      18dp..20dp left line, 14dp text inset, 8dp vertical spacing,
      separate title/body `StaticLayout`s, link hit-testing, and both
      title and body exposed to `TextSelectionHelper`. Simple fallback quote
      text no longer inherits paragraph top/bottom padding; the quote container
      owns the upstream BlockBlockquoteCell-style 8dp top, 8dp title/body gap,
      and 8dp bottom spacing. Nested `blockquote` parsing now preserves
      ArticleViewer `TL_pageBlockBlockquote.level` semantics, with the recursive
      renderer translating those level offsets into parent-text-column local
      coordinates and drawing the upstream `level > 0` parent rail continuation
      alongside the current-level rail. Discourse `aside.quote` parsing now preserves
      `data-topic` / `data-post`; `QuoteBlock` requests expansion through
      `PostBodyDelegate.onQuoteExpand`, `TopicPostCellDelegate.didExpandQuote`,
      and `TopicDetailFragment` -> `TopicViewModel.expandQuote()`, then replaces
      the block body and remeasures the cell. Expanded quote children keep the
      pressed child target through MOVE/UP/CANCEL so nested link/spoiler/media
      state clears correctly. Top-level quotes now expose an embedded
      `ViewGroup`, so nested code/table/list/media children retain their real
      child-view drawing and scrolling path; quotes nested inside other
      canvas-only bodies keep the canvas fallback.
      **Expanded state lives on the block instance** (`var expanded: Boolean`
      + cached fetched HTML). The adapter-level layout cache in Phase 6
      preserves block instances across rebind, so expand state survives
      scroll-recycle (matches user expectation; the current Compose
      `remember{}` loses it). Cache eviction or `Post.editCount` /
      `updatedAt` change rebuilds the block from scratch — expected, since
      the quoted content may have changed.
- [x] `CodeBlock` — monospace, horizontal scroll on the block, no syntax
      highlighting. Geometry now follows ArticleViewer `BlockPreformattedCell`:
      rectangular background band from `dp(8)` to `height - dp(8)`, 16dp
      horizontal text margin, and 12dp child vertical margin plus 8dp scroll
      padding. The background color now follows Telegram's
      `Theme.key_windowBackgroundWhiteLinkSelection` alpha-20 preformatted
      paint, and the text container keeps the upstream
      `textWidth + dp(32)` trailing space, where `textWidth` is the measured
      longest code line rather than a viewport-filled minimum. When the block is rendered through
      the nested canvas fallback, it now keeps horizontal drag scrolling and
      selection geometry aligned to the same text offsets instead of degrading
      to static text.
- [x] `ListBlock` — ordered / unordered / nested list text. Marker column,
      first-item top padding, and marker/text spacing follow ArticleViewer list
      item cells; selected/copied text now preserves visible bullets or ordered
      numbers through `TextSelectionHelper` prefixes. Simple text items use
      list-row padding (`top = 0`, `bottom = dp(8)`) instead of normal paragraph
      padding, and list rows no longer add a local synthetic 6dp gap between
      items or nested blocks. The first-item 10dp top padding now follows
      ArticleViewer's `parent.level == 0` rule, so nested lists do not inherit
      the top-level list spacer. `ListBlock` now owns its ArticleViewer-style
      horizontal geometry instead of receiving `TopicPostCell`'s generic 18dp
      text inset, so marker/text X follows the upstream list-cell formula and
      the text column keeps the upstream trailing 18dp inset. Nested child touch
      streams keep the ACTION_DOWN target through MOVE/UP/CANCEL for correct
      pressed-state cleanup.
- [x] `TableBlock` — text table now uses intrinsic per-column measurement,
      horizontal overflow scrolling, grid drawing, and exposes table cell
      `StaticLayout`s to `TextSelectionHelper`. It now builds logical cells with
      parsed `colspan` / `rowspan`; standard text-only tables are mapped into
      `TL_pageTableCell` values and rendered by the vendored Telegram
      `TableLayout`. Mixed-content cells still use the NativeDo recursive
      fallback so parsed nested `CookedPostBlock` children, nested text
      selection layouts, child touch streams, and nested image source bounds /
      `ImageReceiver`s continue to work. Horizontal table scroll now sends
      `ACTION_CANCEL` into the active nested child before clearing it, so
      pressed link/spoiler/media state does not remain stuck after a scroll.
      The top-level block now also follows `BlockTableCell` outer geometry:
      `listY = dp(8)`, bottom `dp(8)`, scrollView horizontal padding `dp(18)`,
      `clipToPadding = false`, and scrollability measured against
      `viewport - dp(36)`.
      Full upstream `BlockTableCell` adapter-row parity is still not claimed
      because Discourse mixed cells are not backed by ArticleViewer page-block
      holders.
- [x] `DetailsBlock` — plain Discourse `<details>` now follows
      `TL_pageBlockDetails` / `ArticleViewer.BlockDetailsCell` structure:
      the summary is the details title row, `currentBlock.open` owns expansion
      state, the row uses Telegram's arrow/title/divider geometry, and child
      content is parsed as recursive blocks that appear only while open. Open
      children now approximate `TL_pageBlockDetailsChild level + 1`: a
      `dp(18)..dp(20)` level line is drawn with the same
      `Theme.key_chat_inReplyLine` color used by ArticleViewer quote/detail
      rails, text/media children start at `dp(32)`, and child blocks own their
      own vertical padding without an extra fixed details gap or the stale
      `BlockDetailsBottomCell` spacer whose insertion is commented out in the
      pinned `ArticleViewer`. Top-level
      details now expose an embedded
      `ViewGroup`, so expanded child code/table/list/media blocks can keep
      their embedded child-view drawing path; details nested inside
      quote/canvas-only bodies still use the canvas fallback. Child touch
      streams keep the pressed child target through MOVE/UP/CANCEL so nested
      link/spoiler/media state clears correctly.
      Inline text spoiler uses `TextStyleSpan` / `SpoilerEffect` with
      Telegram-style tap-to-reveal ripple; media
      `SpoilerEffect2` remains intentionally out of scope.
- [x] `CalloutBlock` — remains a NativeDo Discourse renderer rather than an
      exact upstream `ArticleViewer` cell, but now follows the same
      ArticleViewer-shaped text/link/container path as the other body blocks:
      title/body text use the shared `ArticleTextLayout` and
      `ArticleLinkPressState` pipeline, and top-level callouts expose an
      embedded `ViewGroup` so nested code/table/list/media children retain
      their real child-view drawing and scrolling path. Callouts nested inside
      quote/list/details canvas-only bodies keep the canvas fallback.
- [x] `PollBlock` — Java native port of Telegram `ChatMessageCell` poll button
      drawing / animation shape on top of the Discourse model. It keeps per-option
      button state, left-side single/multiple choice controls, percent labels,
      5dp result lines, `selectorDrawable` pressed/hotspot feedback, row-aware
      text selection, and a 300ms result animation. `PollButton` now keeps
      upstream-shaped
      `answer/title/x/y/titleX/titleY/count/percent/prevPercent/percentProgress/selectorDrawableColor/selectorDrawable/pollButtonDrawable/checkBox`
      fields while binding `answer` to Discourse `PollOption` instead of
      `TLRPC.PollAnswer`; result voter-count chrome goes through a
      NativeDo-scoped `PollButtonDrawable` subset, and option check state now
      uses the vendored `CheckBoxBase` setup from upstream `pollCheckBox[]`
      while media answers, recent voter avatars, and download progress remain omitted.
      Option-title link/spoiler press state is cleared when the rich-text stream
      returns unhandled, so a drag-cancelled spoiler/link cannot swallow the
      next vote tap. Poll option inline spoiler reveal now uses the shared
      inline state key even when the poll is nested under a details/list body
      whose structural poll state is intentionally not persisted. Option taps
      still submit `POST /polls/vote` with csrf,
      mark the poll locally submitted, and immediately request layout /
      invalidate so status text, submit-button removal, and result row heights
      update before the refreshed post arrives. Final percentages remain sourced
      from Discourse cooked HTML after the post is refreshed by `postNumber`.

**Validate after each:** matching fixture renders correctly in the harness.

### Phase 6 — TopicPostCell shell

The container that holds the body block list. `TopicPostCell` is now a Java
hot-path cell extending Telegram's `BaseCell`, so future ChatMessageCell /
ArticleViewer method-shape ports can be copied over with less Kotlin interop
friction.

- [x] `TopicPostCell extends BaseCell implements Theme.Colorable,
      TextSelectionHelper.ArticleSelectableView`.
      The Java cell extends Telegram `BaseCell`, implements `Theme.Colorable`,
      implements the local `TextSelectionHelper.ArticleSelectableView`, and uses
      `BaseCell` long-press handling. Real text selection block collection is
      wired in Phase 8; overlay chrome is a scoped NativeDo port with Telegram
      handle/menu behavior rather than a wholesale helper copy.
- [x] Owns: avatar `ImageReceiver` + `AvatarDrawable`; 1–2 header
      `StaticLayout`s; `List<PostBodyBlock>`; reactions strip; reply footer.
      The shell owns avatar `ImageReceiver` + `AvatarDrawable`, header
      `StaticLayout`s, `List<PostBodyBlock>`, a clickable reaction strip, and a
      lightweight reply footer (`N replies` + recent reply avatar stack). Reaction
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
      Long-post actions open a Telegram-style `ActionBarPopupWindow` /
      `ActionBarMenuSubItem` menu at the pressed cell coordinate. The popup is
      wrapped in a NativeDo `TopicPostScrimPopupContainerLayout` subset so the
      reaction rail and action menu are sibling surfaces like Telegram's
      `ChatScrimPopupContainerLayout + ReactionsContainerLayout + popupLayout`
      shape, with back/outside close handling, 220ms dismiss, and Telegram's
      default `dimBehind(0.2f)` scrim amount.
      Popup vertical clamping now follows Telegram's menu height budget by
      reserving `dp(48)` beyond the measured scrim content, giving the rail
      enter/overshoot transition the same bottom-edge breathing room as
      `ChatActivity`.
      The rail enter progress is also forwarded into
      `ActionBarPopupWindowLayout.setReactionsTransitionProgress(...)`, so the
      menu background/content collapses and fades with the reaction rail like
      Telegram's scrim container subset.
      The post menu and reaction rail now use Telegram's per-density
      `popup_fixed_alert4.9.png` background resource instead of the local
      transparent fallback, so popup padding, shadow, and rounded-edge chrome
      come from the same asset family as `ChatActivity`.
      Its enter draw path now uses Telegram `ReactionsContainerLayout`'s
      `CLIP_PROGRESS = 0.25f` reveal gate, clipping from the trailing side and
      reducing content translation after the initial reveal instead of simply
      sliding the whole rail across the popup background. The popup rail
      background also draws the same `drawBubbles`-style two-circle tail subset
      (`bigCircleRadius=dp(8)`, `smallCircleRadius=dp(4)`,
      `bigCircleOffset=dp(36)`) after the reveal gate, including Telegram's
      `reactions_bubble_shadow.9.png` bubble shadow resource and upstream
      shadow padding (`dp(3)` for the big bubble, `-dp(1)` for the mini bubble),
      while still omitting the full upstream custom emoji, star, and Lottie
      reaction container.
      `TopicDetailFragment` keeps one active post-options popup, dismisses any
      reaction-rail popup before showing it, and clears it on dismiss /
      fragment destroy like Telegram's `ChatActivity.createMenu(...)` lifecycle.
      While a post-options or standalone reaction popup is showing, the topic
      list is stopped and temporarily made non-scrollable, matching
      `ChatActivity`'s `chatListView.stopScroll()` /
      `setCanScrollVertically(false)` host behavior.
      The rail intentionally uses the local `ReactionPickerRailView` over
      NativeDo `emoji_name` / Discourse reaction ids; it is a compact
      transitional subset of Telegram's `ReactionsContainerLayout`, not a full
      `ReactionsLayoutInBubble` / TLRPC reaction port.
      The reply row now uses ChatActivity's `menu_reply` message-menu icon,
      and the copy-link row uses ChatActivity's `msg_link` icon rather than
      the generic copy glyph.
      Copy-link feedback uses `BulletinFactory.createCopyLinkBulletin().show(true)`
      like `ArticleViewer`'s web-page copy action. The add-bookmark row uses
      the same `msg_saved` icon as `WebActionBar.bookmark_item`, while the
      already-bookmarked remove action remains a Discourse extension with a
      saved/bookmarks icon. Bookmark toggles show Telegram-style
      `BulletinFactory` feedback; the local `Bulletin` port is a lightweight visible subset that
      preserves the factory/show/hide surface used by ArticleViewer and
      BottomSheet without porting Telegram's Lottie/spring/global-window
      implementation.
- [x] **Safe adapter caches only.** Keep the cache at data/state boundaries,
      not at reusable View/block-instance boundaries. Current adapter has:
      - bounded parsed-body LRU keyed by `(post.id, cookedHtmlHash)`, passing
        cached `CookedPostBlock`s into `TopicPostCell` to avoid repeated Jsoup
        parsing;
      - bounded `PostBodyBlockState` LRU keyed by
        `(post.id, cookedHtmlHash, post.editCount)`, preserving `QuoteBlock`
        expanded HTML, `DetailsBlock.currentBlock.open`, and inline text
        spoiler reveal state across scroll recycle / body block rebuilds.

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
- [x] Reactions strip: NativeDo-bound `ReactionsLayoutInBubble.ReactionButton`
      subset. Reads `post.reactions` / `post.currentUserReaction`, keeps
      upstream-shaped `reactionButtons`, `pressedReactionButton`,
      `key/countText/count/realCount/choosen/isSelected/x/y/top/width/height/bounce`,
      26dp button height, 20dp image slot, 4dp spacing, wrap measurement, and
      `Theme.key_chat_inReactionButton*` colors. Pressed feedback now uses the
      vendored `ButtonBounce` scale path like upstream reaction buttons. Draws emoji via
      `ImageReceiver` from the explicit reaction icon URL or `emojiCatalog`;
      emoji URLs go through the same `NativeDoImageDownloader` bridge, so
      cookies are handled for free. Missing URLs do not drop the reaction row:
      `PostReactionRendering` supplies the same fallback glyph/count text for
      the strip, reaction picker, reaction-users tabs, and optimistic/default
      post reaction updates.
      The strip renders every real `PostReaction` and intentionally omits the
      local `+` / `+N` aggregate chip; adding a reaction is routed through the
      long-press picker.
      Touch handling now follows the same button-state shape as
      `ChatMessageCell` comment/reaction areas: DOWN records the pressed
      button, MOVE outside clears it, UP only fires while the same button is
      still pressed, click sound is played on release, and CANCEL clears state.
      Long-pressing an existing reaction chip or the plus chip opens the compact
      reaction rail instead of the reaction-users sheet. Reaction updates now
      keep previous drawn button state by key, animate surviving chip position /
      width / text / background changes with `AnimatedFloat`, run a lightweight
      selected-chip pulse, animate count text with a local vertical old-out /
      new-in switch, and keep removed chips as local `outReactionButtons` so
      they fade out like Telegram's `outButtons` subset. This is still not a
      full `CounterDrawable` / `ReactionsEffectOverlay` port.
- [x] Reply jump highlight: `TopicUiState.jumpHighlight` is now part of the
      post-row diff key, passed into `TopicPostCell`, and rendered as a
      one-shot `AnimatedFloat` blue cell-background fade. Animation completion
      calls back to `TopicViewModel.clearJumpHighlight(nonce)`.
- [x] Reply footer pressed state uses Telegram selector/ripple path:
      `Theme.createSelectorDrawable(...)`, `setHotspot(x, y)`, pressed state on
      down, `StateSet.NOTHING` on up/cancel, and click sound on release. The
      hit target is now the measured reply-button rect rather than the whole
      content width, matching Telegram's `commentButtonRect` touch model. The
      footer also uses `ButtonBounce(this, 2.0f, 2.0f)` plus
      `getScale(0.0125f)`, matching `ChatMessageCell` reply chrome rather than
      a heavier local scale. The avatar stack follows Telegram comment-button geometry: 24dp avatars,
      first avatar inset by 2dp, and subsequent avatars stepped by 17dp. The
      stack now draws reply authors through `ImageReceiver`, using the same
      Discourse avatar URL resolution as the replies sheet and falling back to
      `AvatarDrawable` initials when an avatar URL is missing.
      Long-pressing the footer opens the post long-press menu, while tapping it
      still opens the replies-of-post sheet.

**Validate:** harness renders real JSON `Post` fixture rows from
[`fixtures/discourse/topic-detail.json`](../fixtures/discourse/topic-detail.json)
/
[`fixtures/discourse/topic-posts.json`](../fixtures/discourse/topic-posts.json)
plus cooked-HTML coverage rows from `fixtures/discourse/cooked-*.html` end-to-end.

### Phase 7 — TopicDetailFragment swap

Replace the Compose shell with the View shell. **Block on the parity
checklist below.**

- [x] Rewrite `TopicDetailFragment.createView` to return
      `TopicDetailHostLayout(RecyclerListView + ActionBar + overlay views)`.
      The View-shell implementation binds real `TopicPostCell` rows, no longer
      hosts `TopicDetailScreen` through `ComposeView`, and wraps the list in a
      Telegram-style `PullForegroundDrawable` pull-refresh container. The host
      now owns the 12.8.1 `AdjustPanLayoutHelper` lifecycle and callbacks like
      `ChatActivityFragmentView`: adjust-resize height changes temporarily
      translate the fragment hierarchy, the ActionBar receives the equal-and-opposite
      counter-translation, but TopicDetail is a normal top-down list so
      `contentPanTranslation` is **not** folded into list top padding (that would
      cancel the root pan and kill whole-page motion). Pan is disabled
      while an attached browser sheet is shown or `BottomSheetTabs` height is
      animating, so dismiss-into-tabs / reopen does not get mistaken for IME.
      `setCheckHierarchyHeight` stays off (Chat never enables it). Bottom-tab
      inset itself is owned only by `ActionBarLayout` margins +
      `AdjustPanLayoutHelper.getExpandedHeight()`, not double-counted in list
      bottom padding. `WindowInsetsStateHolder` still animates final bottom
      padding and input-island placement.
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
      `TopicViewModel.refresh()` on release. The wrapper is now the actual parent
      of the topic `RecyclerListView` (rather than an unused local class), keeps
      Telegram's `SNAP_HEIGHT` and `minPullingTime` gates, emits threshold haptic
      feedback, and yields while selection, popup scrim, pagination, keyboard,
      or the emoji panel owns the interaction.
- [x] Swipe-to-reply: the topic list is now a NativeDo-bound port of
      `ChatActivity`'s list-owned gesture subsystem rather than an
      `ItemTouchHelper` approximation. It preserves the pinned physical-left
      direction, `-80dp` clamp, `0.4cm` + 3:1 takeover gate, `50dp` reply/haptic
      threshold, three reply-button springs, upstream `filled_button_reply`
      asset, and 180ms decelerate return. `TopicPostCell` only owns the
      ChatMessageCell-shaped sliding-offset API; selection, popup/scrim,
      pull-refresh, locked scrolling, recycled post ids, and horizontally
      scrollable code/table content are explicitly arbitrated by the list host.
- [x] `viewModel.uiState` collected via `lifecycleScope` +
      `Flow.collectLatest` → `TopicAdapter.submitList(...)`.
      Current implementation uses the fragment's `MainScope`, matching the
      existing `TopicListFragment` collector pattern; it is cancelled in
      `onFragmentDestroy()`.
- [x] Collapsed title: scroll listener detects first-post out-of-frame →
      `actionBar.setTitle(post.title)`. The View shell now clears the ActionBar
      title while the topic header/first post are visible and restores the topic
      title once the first post scrolls under the ActionBar.
- [x] Initial topic identity: trusted topic-list/search/profile-topic rows pass a
      lightweight author snapshot into the detail fragment so the ActionBar can
      bind before the first post response. Post-search/profile-reply rows are
      excluded because their author is the matched reply author, not necessarily
      the topic author. List and ActionBar avatars share the 96-size URL and
      `50_50` filter so Telegram's global image cache can satisfy the first frame.
- [x] Image viewer hand-off: `TopicPostCellDelegate.didPressImage` →
      `openImageViewer(...)` → telegram-module `PhotoViewer` NativeDo port.
- [x] Reactions users sheet UI: app-side
      `FixedHeaderRecyclerBottomSheet` native port of pinned Telegram
      `StickersAlert` (not Material3 `ModalBottomSheet`) with NativeDo content
      rows. The shared shell owns Telegram-shaped natural-content measurement,
      rounded background/handle/status-bar drawing, and fixed-header scroll
      translation; reaction and poll sheets provide their own tabs, loading /
      empty states, and user rows. Their user-list geometry follows pinned
      `ReactedUsersListView` / `ReactedUserHolderView`: 50dp rows with a
      six-row maximum viewport. Loading uses the known reaction count when
      available, unknown counts fall back to six rows, and one-to-five-user
      results shrink to their natural height. The shared sheet caps larger
      result sets into a scrollable viewport. Failed reaction-user loads expose an
      in-sheet tap-to-retry state instead of being rendered as an empty result.
      Multi-reaction posts now expose a real All tab followed by reaction tabs
      in Telegram's fixed 44dp horizontal strip; the 32dp pills use the pinned
      24dp icon, 6dp spacing, short-number counter, reaction theme colors, and
      150ms `CubicBezierInterpolator.DEFAULT` selection/centering animation.
      Content now follows pinned `ChatActivity` with AndroidX `ViewPager`:
      every filter owns an independent cached `RecyclerListView + Adapter`,
      preserves its vertical scroll state, supports horizontal page swipes,
      and interpolates tab selection, tab centering, page height, and the two
      lists' sheet-top offsets during a swipe.
      All users are merged by username and use the post-level
      `reaction_users_count` rather than summing per-reaction counts. The
      discourse-reactions endpoint currently caps each group at 27 recent users
      and has no cursor/offset support, so truncated tabs show an explicit
      "recent N of total" footer instead of pretending the list is complete;
      true pagination remains blocked on a server-side cursor API.
      Dismissal is wired back to
      `TopicViewModel.dismissReactionSheet()`.
      Clicking an existing reaction chip opens this users sheet for that
      reaction; tabs are built from both the post's visible reactions and the
      loaded reaction-user groups, and tab glyphs use actual emoji images via
      `ImageReceiver` when `iconUrl` / `emojiCatalog` provides one.
- [x] Poll voter sheet pagination: each option keeps an independent page,
      loading/error/end state and scroll position, matching Telegram's cached
      per-filter list behavior while using Discourse poll semantics. The sheet
      now uses the same AndroidX `ViewPager` infrastructure as reaction users:
      each option owns a cached list/adapter, horizontal swipes interpolate the
      tab and sheet offset, and the 44dp single-line tab strip scrolls the
      selected 32dp option pill into the center. Tabs keep
      `PollOptionRef.votes` as the authoritative total; selecting an unloaded
      option lazily requests page 1 with `option_id`, and scrolling near the end
      appends 50-user pages with username de-duplication, load-more progress,
      retry, and stale-sheet request rejection. The server partitions page
      windows per option and returns no `has_more`, so exhaustion is derived
      from the real option vote count, a short page, or a missing option key.
      Number-poll voter responses are a different flat-array shape and remain
      intentionally unsupported until a separate DTO/UI path is added.
- [x] Reaction picker UI: compact `ActionBarPopupWindow` rail using Telegram
      context-menu chrome and reaction-container placement as the reference, but
      no full `ReactionsContainerLayout` / `ReactionsLayoutInBubble` / TLRPC
      reaction model port. Long-pressing a post opens the rail at the top of the
      post-options popup; long-pressing a reaction chip or tapping the plus chip
      opens the same rail as a standalone popup at the pressed coordinate, also
      dimming behind through `ActionBarPopupWindow.dimBehind(0.2f)`. The rail
      renders `currentUserReaction`, available `validReactions`, visible reaction
      ids, and fallback ids as Telegram-colored glyph/image buttons. The rail
      keeps the upstream 250ms overshoot container enter, and its buttons now
      add a 150ms `EASE_OUT_QUINT` staggered scale/alpha enter plus selected
      content scale like Telegram's `ReactionHolderView.play(delay)` subset.
      When the rail is attached above a post action menu, its transition
      progress drives `ActionBarPopupWindowLayout.setReactionsTransitionProgress(...)`
      so the menu surface participates in the same enter animation. The rail
      background now includes the upstream two-bubble tail subset that grows
      from the same clipped transition progress.
      Expanded picking uses a transparent full-screen `ActionBarPopupWindow`:
      the collapsed `fromRect` / `fromRadius` animates to a dp(8) panel over
      350ms. Its fixed linux.do grid is 5 columns × 2 rows with 36dp cells and
      30dp icons; the first row is visible immediately, the second row fades
      in, the expand affordance is redrawn inside the clip, and outside/back
      collapses the panel. Tall post menus retain ChatActivity's 240dp
      positioning budget.
      Holding a rail button past Android's long-press timeout now starts a
      Telegram-shaped `pressedProgress`: the pressed button scales toward
      `1 + 2 * progress`, neighbors shrink toward `1 - 0.15 * progress`, and
      neighboring buttons are translated with the same push-away formula as
      Telegram's `checkPressedProgressForOtherViews(...)`. Release/cancel
      restores them over 150ms. Entering this pressed state also
      performs Android `LONG_PRESS` haptic feedback like Telegram's
      `ReactionHolderView.longPressRunnable`.
      Selection now starts a lightweight local move overlay from the pressed
      rail button to the visible reaction-chip icon before calling
      `TopicViewModel.onReactionSelected()`, matching Telegram's
      `ReactionsEffectOverlay.ONLY_MOVE_ANIMATION` timing/easing at the
      user-visible level. The target chip now also fades in a static reaction
      copy after 70% progress, mirroring Telegram's target-side static image
      handoff while the moving reaction fades out. It still does not port the
      full `ReactionsEffectOverlay` animated-emoji/window/particle stack.
- [x] Replies-of-post / floor sheet UI: Telegram `BottomSheet` container +
      NativeDo `RecyclerListView` rows with fixed header, bounded list height,
      title/subtitle/preview, and dividers. The footer now uses Discourse's
      authoritative post-level `reply_count`; already-loaded topic posts are
      only an avatar preview fallback and no longer determine whether the
      footer/sheet exists. Clicking immediately opens a loading sheet backed by
      `GET /posts/{postId}/replies?after=...`, with retry, append pagination,
      de-duplication, authoritative header total, stale-request rejection, and
      dismissal state in `TopicViewModel`. Selecting an unloaded reply fetches
      it by topic/post number before emitting the normal jump/highlight event.
      This sheet intentionally represents direct replies; recursive reply-tree
      semantics would require `/posts/{id}/reply-ids.json` plus hierarchy UI.
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
| Image tap | `onOpenImage(...)` → NativeDo PhotoViewer port | same delegate path; opens a telegram-module ImageReceiver fullscreen URL gallery |
| Quote collapsed → expanded | `fetchPostByNumber` callback | `QuoteBlock` + `delegate.didExpandQuote` |
| Reply target preview | `TopicPostMeta` shaping | `TopicReplyTargetResolver` |
| Reply jump highlight | `TopicJumpHighlight` state | one-shot event → `AnimatedFloat` on `TopicPostCell` |
| Reply composer | generic BottomSheet form | NativeDo-scoped `ChatActivityEnterView`-shaped root-bottom panel, not a sheet: 44dp emoji/input/send row matching upstream `ChatActivityEnterView.DEFAULT_HEIGHT`, inline reply context, list bottom padding adjusted while visible, `WindowInsetsStateHolder + ChatInputViewsContainer + TopicDetailHostLayout/AdjustPanLayoutHelper` jointly drive final inset placement and the resize-transition pan, icon send/submitting state, and a bottom-attached emoji panel that uses the latest keyboard height or Telegram's `dp(200)` fallback while inserting Discourse `:emoji_name:` markup. The inline reply context now behaves closer to Telegram's `topView`: it animates height from 0 to the 46dp reply header instead of occupying space before fading in, uses the `ChatReplyContainer` 52dp icon/text/close columns without message-bubble quote-line chrome, uses the local Telegram `menu_reply` asset for the reply icon, and applies the 200ms show delay only while waiting for the keyboard to open. Its visibility animation now uses Telegram's `ChatListItemAnimator.DEFAULT_DURATION` 250ms timing and the same default cubic interpolator used by `animatorTopViewVisibility` / `animatorInputFieldHeight`, and the header translation follows the upstream `topView.setTranslationY(y - topViewHeight * visibility)` direction by sliding down behind the input field at visibility 0 and up into place at visibility 1. If the keyboard becomes visible while that delayed reply-header enter is pending, `TopicDetailHostLayout` forwards `onAdjustPanTransitionStart(keyboardVisible, contentHeight)` and flushes it, matching `ChatActivityEnterView`. The close icon now uses Telegram's copied `input_clear` asset with `Theme.createSelectorDrawable(..., 1, dp(19))` close button chrome, matching the upstream `replyCloseImageView` setup. The emoji button uses Telegram's 7.5dp padding plus `Theme.createInsetRoundRectDrawable(..., dp(19), dp(1), dp(3))` selector chrome. The send button now follows `ChatActivityEnterView.animateSendButton(show)`: initial state is `INVISIBLE` at alpha 0 / scale 0.1 like upstream construction, empty text stays hidden, entering text animates it to alpha 1 / scale 1 over 220ms with `CubicBezierInterpolator.EASE_OUT_QUINT`, the icon runs the upstream `SendButton.appear()`-style 320ms translate/scale/rotate entrance when first shown, the send hit/animation area is 100x44dp like upstream `sendButtonContainer`, and submitting keeps it visible while disabled with a Telegram-shaped loading state: `ButtonBounce` press feedback, 420ms loading visibility animation, icon fade/scale, and an infinite time-driven arc using the same fast-out-slow-in segment curve as upstream. Keyboard requests now follow Telegram's `openKeyboard()` fallback path by retrying focus/show-keyboard when the first `showKeyboard()` call fails, and composer open schedules a 100ms retry like upstream `openKeyboardInternal()`. The emoji button now follows `ChatActivityEnterView` toggle semantics: tap once hides the keyboard and opens emoji content below the composer; tap again while the emoji content is visible dismisses it and focuses the editor/keyboard; Back while the emoji panel is visible consumes both the fragment back path and the focused edit field's `KEYCODE_BACK/ACTION_UP` path, matching `ChatActivityEnterView.hidePopup(true)` plus `messageEditText.setOnKeyListener` when the keyboard is not visible; repeated taps during the local keyboard inset animation or the 200ms post-emoji keyboard-open wait are ignored like upstream `adjustPanLayoutHelper.animationInProgress()` / `waitingForKeyboardOpenAfterAnimation`. If the system keyboard becomes visible while the emoji panel is open, `WindowInsetsStateHolder` closes the panel immediately, matching Telegram's `keyboardVisible && isPopupShowing()` path. Its icon also switches between the local `SMILE` and `KEYBOARD` drawings like `ChatActivityEnterViewAnimatedIconView.State.SMILE/KEYBOARD`, with a lightweight scale/fade transition instead of Telegram's Lottie assets. The edit field now wires the upstream-shaped `EditTextBoldCursor.setWindowView(rootView)` on attach and reapplies handle tint on touch like Telegram's `fixHandlesColor()` path, while the full `EditTextEffects` stack remains omitted. Multiline field-height changes now feed list bottom padding through a 250ms `animatorInputFieldHeight`-style visible island height, matching the way `ChatActivityEnterView.getIslandTotalHeight()` drives `ChatActivity` bottom layout instead of jumping directly to the measured target height. Multiline scrolling now mirrors `ChatActivityEnterView.drawMessageEditText(...)` with animated top/bottom `DST_OUT` fade masks when the edit field can scroll. Emoji selection keeps the panel open so multiple emoji can be inserted like Telegram's `EmojiView`, though this remains a NativeDo reaction-rail subset rather than a full upstream `EmojiView` port. |
| Reaction toggle | optimistic `Post.toggledReaction` | same — moved into VM |
| Reaction picker | bottom sheet | Telegram-style `ActionBarPopupWindow` horizontal reaction rail in the long-press menu, plus the same rail for reaction-chip long-press / plus-chip tap; local `ReactionPickerRailView` now carries the upstream 250ms overshoot, `CLIP_PROGRESS = 0.25f` trailing-side reveal, and two-bubble rail tail subset with Telegram's `reactions_bubble_shadow.9.png` shadow bounds. Reaction selection also has a NativeDo `ReactionsEffectOverlay.ONLY_MOVE_ANIMATION` subset: a 34dp source icon moves toward the target chip over 350ms before the local chip pulse completes. This is still not the full upstream `ReactionsContainerLayout` / `ReactionsEffectOverlay` port. |

The reply composer is now a persistent 44dp bottom child of the topic host,
matching Telegram's long-lived `ChatActivityEnterView` lifecycle instead of
being recreated for every reply action. Its text and reply target have one
ViewModel state source and are persisted per normalized site, authenticated
username, and topic; refresh, keyboard dismissal, navigation, and process
recreation no longer discard a draft. Successful submission clears the stored
draft, while failure keeps it editable. Tapping the always-present field is the
general topic-reply entry; replying to a post only changes the same composer's
top reply context. The unsupported Attach and voice controls were removed
instead of presenting buttons that only reopened the keyboard. The Telegram
100x44dp send container, 50dp edit-field right inset, emoji/keyboard behavior,
and reply-header animation remain.

Long topics now use Telegram's exact `ChatActivitySideControlsButtonsLayout`,
`ChatActivityBlurredRoundPageDownButton`, round blur button, and `CounterView`
stack. NativeDo adapts the badge semantics from chat unread count to the number
of canonical `post_stream.stream` entries below the last visible post. The
button appears immediately when a topic opens above the latest post; after the
user has reached the bottom, Telegram's 100dp leave-bottom threshold prevents
it from flashing during small scroll corrections. It hides during text
selection, tracks the persistent composer/keyboard/emoji inset, and shows the
upstream loading state while one tail request loads the latest 20 stream IDs.
Tail and ordinary pagination results are merged by canonical stream order, so
jumping to the end no longer requires serially loading every intermediate page.
| Reaction users sheet | bottom sheet | Telegram `BottomSheet`, state in VM, existing reaction chip opens users |
| Replies-of-post count | footer row in cell | drawn footer in cell |

**Validate:** `:app:compileDebugKotlin`, targeted `TopicDetailFragmentTest` /
`TopicPostCellTest`, `:app:assembleDebug`, then manual run through the checklist
on a real topic. Smoke-test on a long thread (>=100 posts) for fling smoothness.
2026-06-20 status: compile, targeted tests, assemble, install, main-activity
launch, focused-window check, and logcat crash scan passed on device `449dd25d`.
Targeted tests now cover post-options popup positioning, menu action/reaction
ordering, reply-footer long-press, reaction-chip long-press, plus-chip tap, and
post-menu priority over first text-selection entry. Reply-panel keyboard inset
logic is covered by helper tests and by the local `SizeNotifierFrameLayout`
keyboard measurement subset. After the keyboard-inset fix, reinstalling the
debug APK and launching `MainActivity` on device `449dd25d` succeeded, the app
was the focused application, and a logcat crash scan found no NativeDo fatal
exception. Screenshot/manual interaction verification of the popup rail and
reply panel keyboard behavior is still pending because the device is currently
dozing behind `NotificationShade`, `adb input` is denied by `INJECT_EVENTS`, and
screenshots capture a black screen.

### Phase 8 — text selection (separate effort)

Standalone, no longer blocks the rewrite.

- [x] Replace the no-op `TextSelectionHelper.ArticleTextSelectionHelper` stub
      with a trimmed NativeDo topic-detail port in `:telegram`. Manifest entry:
      `port_mode: native`, `forced_exact: [gesture, drawing]`. This is not a
      full exact upstream port.
- [x] Wire `TopicPostCell` through `fillTextLayoutBlocks(...)`, including nested
      `TextLayoutBlock`, `ListBlock`, `QuoteBlock`, open
      `DetailsBlock` text, and visible `CodeBlock` / `OneboxBlock` / `PollBlock`
      labels. Image captions and horizontally scrollable table/code text are also
      exposed to the selection helper.
- [x] First long-press in `TopicPostCell` follows `ChatMessageCell` arbitration
      and opens the host post menu. The menu exposes an explicit **Select text**
      action that passes the original press coordinates to the shared helper,
      selects the touched word, and shows the floating ActionMode. The menu path
      intentionally does not start one-touch drag continuation because the
      initiating finger stream has already ended. Before the post menu opens,
      a body block that consumes `ACTION_DOWN` remains
      the active touch target for MOVE/UP/CANCEL, so nested pressed states are
      not dropped when the finger leaves the original block bounds. Rebind,
      detach, and body-context rebuild reset active body / reaction / reply
      pressed state so recycled cells do not carry stale touch state. Selection
      mode no longer makes `TopicPostCell` intercept every touch: only the
      still-down one-touch drag continuation is intercepted, while ordinary
      selection-mode drags fall through to RecyclerView scrolling.
- [x] Cross-post selection ownership now remains in adapter-position / child /
      offset endpoint tuples like Telegram's `ArticleTextSelectionHelper`.
      RecyclerView scrolling may detach or recycle the active cell without
      clearing the selection; visible endpoints are projected back into the
      current cell when they re-enter the viewport. The floating toolbar hides
      while the list moves and is refreshed when scrolling stops. Position-
      shifting adapter mutations clear the selection cache, while appends after
      the selected range preserve it.
- [x] Port draggable handles, magnifier, quote / translate actions, multi-cell drag
      selection, cursor haptic feedback, RecyclerView auto-scroll, and upstream-style
      `TextSelectionOverlay` chrome. Current implementation now keeps an
      upstream-shaped generic `TextSelectionHelper<Cell extends SelectableView>`
      core plus `ArticleTextSelectionHelper` subclass, uses Telegram's
      `CornerPath`/rounded selection geometry, `ScalablePath` line-rect shaping,
      handle-edge clipping, and handle silhouette, and now routes copy /
      select-all / translate through NativeDo `FloatingActionMode` /
      `FloatingToolbar` ports. The toolbar now has a Telegram-style main action
      row plus overflow panel, back-row chrome, 48dp action rows, and upstream
      animated-vector overflow/back arrows instead of a single flat button row.
      Copy completion is wired through `TextSelectionHelper.Callback` like
      `ArticleViewer`: after the helper writes to the clipboard,
      `TopicDetailFragment` shows a `BulletinFactory.createCopyBulletin(...)`
      with Telegram's `TextCopied` string instead of relying only on a platform
      clipboard toast.
      Like Telegram's floating toolbar, it now listens for parent layout changes
      while showing and recalculates its popup coordinates when the window size
      changes.
      The overflow panel now measures its width from visible action titles
      instead of using a fixed narrow value, so localized labels and larger font
      scales stay closer to Telegram's toolbar sizing behavior.
      Menu preparation now follows Telegram's dynamic action visibility: copy,
      quote, select-all, and translate are hidden when the current selection
      state cannot use them, and select-all is suppressed for cross-post or
      already-full-current-post selections. Translate now receives the current
      locale as the target language like Telegram's callback contract, while
      ML Kit source-language detection is not copied because that dependency is
      not otherwise present in NativeDo.
      The action-mode bridge follows Telegram's hide/moving/out-of-bounds/window-
      focus visibility state machine. Handle
      dragging now snaps to word boundaries and flips start/end roles when the
      dragged endpoint crosses the opposite side; handle drawing now uses the
      line-bottom anchor and RTL-aware left/right silhouette switching from
      Telegram. Same-row blocks such as table cells and poll labels/percentages
      now use Telegram's row-aware closest-layout hit testing, so X position
      chooses the nearest text layout within the touched visual row. Inline
      chip/icon/image spans that expose custom selection bounds now use those
      visual bounds for selectable-block hit testing and initial offset snapping,
      so their rounded selection geometry is not only drawn after selection but
      can also be the long-press/drag target. Empty-area taps exit selection only
      after the same short tap/slop check used by upstream. `TopicDetailFragment`
      dispatches touch events through the selection overlay before normal child
      dispatch like `ArticleViewer`: handle drags consume, empty-area short taps
      are converted into a downstream cancel after clearing selection, and
      non-handle movement continues to the list.

## Telegram parity follow-ups

The rewrite is functionally complete, but several rows are deliberately
"usable NativeDo implementation" rather than upstream Telegram parity:

- [x] `PhotoViewer`: `TopicDetailFragment.openImageViewer(...)` now opens the
      telegram-module `PhotoViewer` NativeDo port. The port keeps upstream's
      singleton/provider/`PageBlocksAdapter` API shape and hosts a fullscreen
      `ImageReceiver` URL gallery for Discourse images; it is not the full
      Telegram message/media/editor/stories viewer.
      It consumes provider source bounds for lightweight opening and closing
      animations, including per-gallery-image bounds when the current image still
      has a visible source tile/thumbnail in the tapped post, and converts those
      window bounds into the viewer container coordinate space before animating.
      The port now uses a Telegram-shaped `WindowManager` overlay host instead
      of a `Dialog`: `MATCH_PARENT` translucent `LAST_APPLICATION_WINDOW`,
      stable/fullscreen/hide-navigation layout UI flags, layout-in-screen/
      inset-decor/alt-focusable-IM/draw-system-bars window flags, resize soft-
      input mode, and short-edge display cutout layout on Android P+.
      It hides the current
      viewer image during hand-off and while swiping the gallery, routes overlay/back dismissal
      through provider cleanup, and supports the core Telegram image gestures:
      double-tap zoom, pinch zoom, bounded pan while zoomed, and base-scale
      horizontal next/previous swipes with drag-follow/edge spring-back and without
      replaying the source handoff animation for each gallery step, plus vertical drag-to-dismiss with
      Telegram-style background fade and release threshold.
- [x] Reply footer comment-button interaction parity: selector/ripple,
      button-rect hit testing, MOVE hotspot updates, release click sound, and
      cancel/up state clearing follow Telegram's `checkCommentButtonMotionEvent`
      shape. The avatar stack now follows Telegram's comment button geometry
      and drawing order: 24dp avatars, first avatar +2dp, subsequent +17dp,
      right-to-left draw order, and a dp(13) background mask circle between
      overlapping avatars. Because Discourse supplies `reply_count` without
      recent participant identities, the footer reserves `min(reply_count, 3)`
      neutral avatar circles and replaces them in place as reply participants
      become known. Replies fetched by the sheet are retained as footer previews
      without being inserted into the main topic stream.
      Remaining visual content is still NativeDo-shaped
      rather than upstream's exact `commentLayout` / unread-dot drawing.
- [x] `CodeBlock` / `TableBlock`: top-level blocks now use embedded
      `HorizontalScrollView` child views, matching the structural shape of
      `BlockPreformattedCell` / `BlockTableCell` more closely than the earlier
      hand-rolled canvas `scrollX`. Both keep a canvas fallback for nested HTML
      inside quote/list/details bodies, because `TopicPostCell` still owns one
      post row rather than splitting every InstantView block into adapter rows.
      `TableBlock` now also renders nested table-cell `CookedPostBlock` children
      instead of flattening cells to plain text, and its outer scroll geometry
      matches `BlockTableCell`'s dp(18) horizontal scroll padding plus dp(8)
      top/bottom table spacing.
- [x] `ListBlock`: top-level lists now use an embedded `ViewGroup`, matching
      the structural direction of `BlockListItemCell` / `BlockOrderedListItemCell`
      and allowing nested `CodeBlock` / `TableBlock` children to keep their real
      `HorizontalScrollView` child views. Simple text rows now follow the
      upstream list-item `textY` / `height += dp(8)` spacing instead of reusing
      paragraph cell padding. Markers are per-item `StaticLayout`s aligned by
      marker/text first-line ascent difference, matching the upstream
      `numLayout` / `numOffsetY` direction more closely than raw `drawText`
      baseline placement. Horizontal marker placement keeps the upstream split:
      unordered bullets right-align at `dp(15) + maxNumWidth + level * dp(12)`,
      while ordered markers right-align at `dp(18) + maxNumWidth + level * dp(20)`.
      Canvas fallback remains for lists nested inside
      quote/details bodies.
- [x] `QuoteBlock`: top-level quotes now use an embedded `ViewGroup`, matching
      the same ArticleViewer cell-holder direction used by details/list
      containers and allowing expanded quote children such as `CodeBlock` /
      `TableBlock` / `ListBlock` / media blocks to keep their real embedded
      child views. Nested quotes keep the canvas fallback because the current
      topic body still renders nested HTML inside a single parent block rather
      than splitting every nested page block into adapter rows; their rail
      geometry still follows `BlockBlockquoteCell`'s current-level rail and
      parent-rail continuation in recursive local coordinates.
- [x] `ImageBlock`: top-level images now use an embedded child `View` that
      reuses the same photo/caption draw and touch pipeline, moving closer to
      `BlockPhotoCell`'s real view-holder shape while preserving the canvas
      fallback for nested quote/list/details bodies. Unloaded or cross-fading
      images draw a non-interactive `RadialProgress2` chrome like `BlockPhotoCell`. Telegram
      `DownloadController` progress/cancel state is not faked because NativeDo
      loads Discourse URLs through `ImageReceiver`. Measurement now follows
      `BlockPhotoCell`'s self-contained `photoY` / `textY` spacing: non-first
      images get the internal 8dp top gap, captions are placed from the actual
      image bottom plus 8dp, and the block owns its 8dp bottom padding instead
      of depending on `TopicPostCell` media spacing. Discourse lightbox `href`
      values that point at image files are treated as gallery originals, not
      Telegram instant-view links; the `msg_instant_link` corner badge is kept
      only for non-image external image links.
- [x] `CollageBlock`: 5-12 image groups now choose a Telegram
      `GroupedMessages`-style optimal 2/3/4-line layout attempt instead of a
      fixed greedy row pass. It also follows `BlockCollageCell`'s internal
      8dp list top / 16dp bottom padding. Top-level collage blocks now render
      through an embedded `RecyclerListView` with a non-scrolling layout manager
      that places tile views at the grouped-media rects, while nested collage
      bodies keep the canvas fallback. Hidden source tiles keep the media
      placeholder background while the ImageReceiver draw is suppressed during
      PhotoViewer transitions. Tile gallery keys use the same image-like
      `href` rule as `ImageBlock`, so Discourse lightbox originals participate
      in PhotoViewer swiping while non-image wrapper links do not become fake
      gallery images. Full `BlockCollageCell` adapter parity remains incomplete
      because tiles are NativeDo views rather than ArticleViewer `BlockPhotoCell`
      / `BlockVideoCell` holders backed by `TLRPC.PageBlock`s.
- [x] `PollBlock`: cooked HTML result data is parsed into a Java native port of
      Telegram-style poll option state and result drawing. Each `PollButton`
      now owns a NativeDo-scoped `PollButtonDrawable` subset for upstream-shaped
      voter-count drawable lifecycle/drawing and a vendored `CheckBoxBase`
      for upstream-shaped checked-state animation. Vote submission is wired through
      `POST /polls/vote`; local submitted state immediately requests layout /
      invalidation for the optimistic UI transition, and after a successful vote
      the current post is fetched again so the block renders server-confirmed
      selected state and results.
- [x] `DetailsBlock`: replaces the previous self-drawn concealed details body
      with a `TL_pageBlockDetails`-shaped open/closed child block model.
      Expanded children now follow the upstream level+1 row direction with a
      details-level line, dp(32) text/media start, and no synthetic inter-child
      gap. Top-level details now use an embedded `ViewGroup` so child
      code/table/list/media blocks retain their embedded drawing structure;
      nested details keep the canvas fallback because `TopicPostCell` still
      owns a single post row instead of splitting every details child into
      adapter rows.
      Full `SpoilerEffect2` GL/media spoiler parity is intentionally not copied.
- [x] `CalloutBlock`: still NativeDo-specific because Discourse callouts do not
      map to a single upstream Instant View block, but top-level callouts now
      use an embedded `ViewGroup` so nested `CodeBlock` / `TableBlock` /
      `ListBlock` / media blocks keep the same real child-view structure used
      by their top-level renderers. Nested callouts keep the canvas fallback.
- [x] Text selection full overlay: handles, magnifier, translate, multi-cell
      drag, RecyclerView auto-scroll, and overlay chrome are wired for the
      topic-detail path. The `FloatingToolbar` port now mirrors Telegram's
      main-panel / overflow-panel interaction with back-row chrome and upstream
      animated-vector overflow/back arrows, uses localized back text, measures
      overflow width from menu labels, passes the current locale as translate
      target language, relayouts on parent layout changes, and `FloatingActionMode` now carries
      Telegram's visibility state machine for hide requests, moving selection
      rects, out-of-bounds content, and window focus. Full helper hierarchy
      parity is intentionally not copied wholesale.

## Risks and open questions

- **Selection scope.** Current selection is an upstream-shaped NativeDo port:
  `TextSelectionHelper<Cell extends SelectableView>` owns the core selection
  state/overlay contracts and `ArticleTextSelectionHelper` implements the
  Article/text-block mapping. The touched word enters selection, start/end
  handles can drag across visible cells, the initial long-press touch can keep
  dragging the active endpoint before finger-up, RecyclerView auto-scroll extends
  selection near edges, Android `Magnifier` is used where available, and the
  menu is backed by NativeDo `FloatingActionMode` / `FloatingToolbar`
  ports exposing dynamically visible copy / quote / select-all / translate,
  with a Telegram-style overflow panel, upstream animated-vector overflow/back
  arrows, localized back text, label-measured overflow width, and Telegram-style
  layout-change relayout plus action-mode visibility states. Translate uses the current locale as its target
  language; Telegram's ML Kit source-language detection remains intentionally
  outside this port.
  Handle dragging snaps to word
  boundaries, swaps start/end roles when an endpoint crosses the other side,
  anchors both handles to line bottoms, mirrors handle silhouettes for RTL text,
  picks the nearest layout by X within same-row table/poll text blocks, and exits
  selection on empty-area taps using Telegram's short-tap/slop guard.
- **Cell layout cache memory.** Current adapter caches are bounded LRU maps:
  parsed bodies and `PostBodyBlockState` each keep at most 64 post/html keys.
  No adapter-owned `PostBodyBlock` or measured layout objects are cached.
- **Reactions data shape.** `PostReactionRendering` now centralizes explicit
  icon URL / `emojiCatalog` fallback and glyph labels, so existing reactions,
  optimistic toggles, reaction picker buttons, and reaction-users tabs stay
  visible even when linux.do omits an emoji image URL. Device validation should
  still confirm the real `emojiCatalog` URL coverage and image loading quality.
- **AnimatedEmoji in reactions.** Out of scope for this rewrite. Reaction
  emoji are static `ImageReceiver`s for now.

## Conventions

- New Java cells (`TopicPostCell`, `TopicHeaderCell`, `TopicFooterCell`)
  carry SPDX header + one-line attribution to the upstream `BlockXxxCell` /
  `ChatMessageCell` they native-port from. Manifest entries:
  `port_mode: native`, `forced_exact` listing the subsystems whose constants
  / branch structure we keep (typically `measurement, drawing`; add
  `gesture` for cells with custom hit testing).
- New Java blocks under `body/` follow the same rule when they port an
  upstream cell's measurement / drawing / gesture / animation. `OneboxBlock`
  requires attribution + manifest entry if it ports `ChatMessageCell` link
  preview algorithms. `DetailsBlock` has a manifest entry for the
  `TL_pageBlockDetails` / `BlockDetailsCell` native port. `PollBlock.java` now has a manifest entry for the
  Telegram-style poll percent / row-state / drawing / animation structure while
  retaining NativeDo Discourse vote submission.
- Pure NativeDo product code (Discourse poll parsing/API models, reply footer,
  sheet content rows) gets neither header nor manifest
  entry unless actual Telegram source or branch structure was copied.
- Generic Telegram UI components such as `BottomSheet` and
  `BottomSheetWithRecyclerListView` should be copied into `:telegram` with
  upstream packages whenever the dependency surface is manageable. NativeDo-
  bound sheet contents stay in `:app` and talk in NativeDo-shaped models.
- Cell sub-area constants (`dp(...)`, animation durations, snap thresholds)
  match upstream when the corresponding subsystem is `forced_exact`.

# Vendor alignment decisions backlog

Human decisions needed. Agents should not auto-resolve these without product
input. Each item is left open after autonomous exact-alignment passes.

Global pin: `9b50143d8896` (Telegram Android 12.8.1, 2026-06-16).  
Local pristine checkout: `vendor/telegram-android` at that pin.  
`vendor-ff` dry-run: all eligible exact+unmodified files already at pin (`would_ff=0`).

## Recently landed (no decision needed)

| Commit | What |
|---|---|
| `9c32e06` | ArticleViewer B19: spoiler fields, `liveDrawingTexts`, spoiler draw/hit |
| `d70d193` | ArticleViewer B20a: search menu header height reset |
| *(this pass)* | ArticleViewer B20b: Block* `DrawingText.attach/detach` + EmbedPost/Slideshow a11y |
| *(this pass)* | ArticleViewer B21: pin-identical `MultiLayoutTypingAnimator` + DrawingText typing draw branch |
| `beab531` | `WindowInsetsStateHolder` restored byte-identical exact at pin |
| `d3439c0` | `CircularProgressDrawable`, `Blur3Utils` restored pin-identical |
| `9d8694b` | Decisions backlog doc |
| *(follow-up)* | `WindowAnimatedInsetsProvider` restored pin-identical |
| later | `HttpGetFileTask`, `AnimatedFloat`, `WindowVisibilityManager`, `UniversalRecyclerView` pin-identical; FlickerLoadingView/GraySectionCell partial re-align |

## ArticleViewer — epic closed at B21

Autonomous AV pin-alignment **stopped after B21**. Do not resume slice work
without a product ticket. Full plan: `docs/vendor-articleviewer-plan.md`.

### Landed (no decision needed)

| Id | Slice |
|---|---|
| A1 / B21 | `MultiLayoutTypingAnimator` vendored pin-identical; `DrawingText` Block + draw branch (IV leaves null) |
| A2 / B20b | Block* `DrawingText.attach/detach` lifecycle + EmbedPost/Slideshow a11y |
| A3 | `TLRPC.TL_textSpoiler` + `getText` → `TextStyleSpan(FLAG_STYLE_SPOILER)`; S1 map fixed |
| A4 | Partial: `setIsLocal` on transitions + `openWebpageUrl` local confirm; host bookmarks/restrict kept |
| A7 | `CodeHighlighting` + `codelng.gzip`; preformatted highlight; table long-press selection |
| B1–B20a | IArticleViewer, parent-driven text/paint/cells, spoiler surface, search header height |

### Parked host / S1 deltas (default: keep as-is)

| Id | Topic | Default |
|---|---|---|
| A5 | Embed/Collage/Slideshow static reshape | Leave non-static (matches pin) |
| A6 | Heading1–6 maps / `quoteLevels` | Keep S1 omission |

Re-open only when product needs pin behavior for a row above.

## Other exact+modified files — safe to leave, need product call to change

### B1. Import / annotation host wiring (keep unless you want pin purity)

| File | Delta | Risk if forced to pin |
|---|---|---|
| `AnimatedTextView.java` | Drop unused Checker Framework imports (classpath lacks checker) | Keep modified unless checker dep added |
| `SimpleTextView.java` | `DialogCell.FixedWidthSpan` → `FixedWidthSpan` | High if DialogCell not wanted |

### B2. Dependency substitutions (intentional)

| File | Delta | Decision |
|---|---|---|
| `SearchStateDrawable.java` | `Math.hypot` instead of zxing `MathUtils` | Add zxing dep? |
| `UniversalRecyclerView.java` | `getClassGuid()` → `0` | BaseFragment lacks classGuid? |
| `LayoutHelper.java` | Extra `createFrame(int,int)` for Kotlin | Keep host overload forever? |
| `WindowVisibilityManager.java` | Null listener guard + host header | Keep guard? |

### B3. Theme / image pipeline

| File | Delta | Decision |
|---|---|---|
| `ThemeColors.java` | Default wallpaper + service colors | Keep NativeDo defaults? |
| `ImageReceiver.java` | Behind legacy pin; Log swap; no AnimatedEmojiSpan interface; ImageLocation arity | Full re-sync to 12.8.1 image pipeline? |
| `ImageLocation.java` | Legacy pin / API shape | Re-sync with ImageReceiver as a pair? |
| `RestrictedDomainsList.java` | Legacy pin | Re-sync with ArticleViewer open-external path? |

### B4. Larger “other” exact+modified (classify primary=other)

Do not auto-overwrite. Re-sync only with a named product goal:

- `ArticleViewer.java` (epic closed at B21; stuck host/S1 by design)
- `RecyclerListView.java` (forced_exact gesture/recycling; heavy host wiring)
- `Utilities.java`, `AnimatedFloat.java`, `PullForegroundDrawable.java`, …
- Web stack: `WebInstantView`, `AddressBarList`, `BookmarksFragment`, `WebMetadataCache`, …
- Dialog cell SPI: `DialogsItemAnimator`, `AnimationProperties`

Full list: `scripts/vendor-classify.sh --port-mode exact`.

## Native / reference-only (default: do not overwrite)

~153 `:telegram` native + app native ports. `vendored_at` is historical. Re-check only `forced_exact` subsystems when product requires.

## Suggested default if you want zero more product risk

1. Keep all host-wiring tables above as-is.
2. **Do not continue ArticleViewer** unless a product ticket reopens a parked A3–A7 row.
3. Do not bump global pin until `scripts/vendor-status.sh --bump-report <new>` is empty of undecided exact+modified files.

## How to decide an item

For each row, answer:

1. Product needs pin behavior? (yes / no / later)
2. Accept new dependency? (jspecify, zxing, androidx.interpolator, TL_iv, …)
3. Accept host API break? (ImageLocation arity, BaseFragment classGuid, …)

Then either:

- re-sync file with host wiring re-applied, update compact manifest + lock, compile/tests; or
- leave `modified:true` / `stuck_reason` and mark “wontfix” here.

## Remaining exact+modified snapshot (after autonomous pass)

Still modified and intentionally not bulk-copied without product decisions:

- `ArticleViewer.java`, `IArticleViewer.java` (epic closed at B21; host-wired IV program parked)
- `ImageReceiver` / `ImageLocation` / `RestrictedDomainsList` (legacy pin, stuck)
- `Utilities.java` (native/no-JNI surface)
- `ThemeColors.java` (NativeDo default wallpaper/service colors)
- `LayoutHelper.java` (Kotlin `createFrame(int,int)` host overload)
- `RecyclerListView`, `DialogsItemAnimator`, `AnimationProperties`, `PullForegroundDrawable`, `RLottieImageView`, `NestedSizeNotifierLayout`, `ScrollSlidingTextTabStrip`
- Web stack: `AddressBarList`, `BookmarksFragment`, `WebInstantView`, `WebMetadataCache`
- `GraySectionCell` / `FragmentSearchField` (small remaining host deltas)
- `AnimatedTextView` (checker imports)
- `IMessageCell` (host-slim reactions types)

See earlier sections for decision prompts.

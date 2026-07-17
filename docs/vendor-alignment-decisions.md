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
| `beab531` | `WindowInsetsStateHolder` restored byte-identical exact at pin |
| `d3439c0` | `CircularProgressDrawable`, `Blur3Utils` restored pin-identical |
| `9d8694b` | Decisions backlog doc |
| *(follow-up)* | `WindowAnimatedInsetsProvider` restored pin-identical |

## ArticleViewer — still deferred

### A1. MultiLayoutTypingAnimator

- Pin `DrawingText.draw` uses typing fade via `MultiLayoutTypingAnimator`.
- Class is not vendored under `:telegram` (only lives in upstream rich-message stack).
- **Decision:** vendor a slim typing animator for IV, permanently OMIT, or wait for a product need?

### A2. Full Block*Cell `layout.attach(this)` call sites

- Pin attaches every `DrawingText` from nearly all Block cells after measure/bind.
- B19 only attaches when `createLayoutForText` sees `parentView.isAttachedToWindow()`.
- **Decision:** bulk-wire all cell attach/detach sites now (wide mechanical diff), or only when a spoiler/emoji leak is observed?

### A3. `TL_textSpoiler` / S1 spoiler content production

- Pin produces spoilers from `TL_iv.textSpoiler` → `TextStyleSpan(FLAG_STYLE_SPOILER)`.
- Local S1 `TLRPC.RichText` has no spoiler type; Discourse IV may never emit one.
- Lifecycle/hit path is ready; without content, spoilers stay empty in production.
- **Decision:** add `TL_textSpoiler` + `getText` branch, map from Discourse, or keep structural-only?

### A4. Sheet / WebActionBar broader chrome

Already host-wired on purpose; do **not** bulk-copy pin without choosing:

| Area | Local | Pin | Decision |
|---|---|---|---|
| Bookmarks | `WebBookmarks` + toast / `BookmarksFragment` | Saved Messages / MTProto `addBookmark` | Keep NativeDo store? |
| Open external | `RestrictedDomainsList` + custom dialog | `MessagesController` exception APIs + `AlertsCreator` | Keep RestrictedDomains path? |
| Open IV markdown file | omitted (`cached_page.local`) | opens local file | Need offline markdown open? |
| Sheet dismiss into tabs | ActionBarLayout overlay fallback + release guard | `LaunchActivity.instance` only | Keep host fallback? |
| Sheet preview bitmap | local `capturePreviewBitmap` | absent/different in pin head | Keep local preview capture? |
| Search UI chrome after menu | local search panel layout differs mid-file | pin continues with searchDownButton etc. | Full re-sync of search panel? |

### A5. Embed / Collage / Slideshow static reshape

- Still non-static nested cells with `ArticleViewer.this` helpers.
- Optional pin reshape; product risk if Table/media geometry drifts.
- **Decision:** force static parent reshape now or leave?

### A6. Heading1–6 resource maps / quoteLevels mask

- Local S1 intentionally omits pin maps and multi-gutter quote mask.
- **Decision:** adopt pin maps (needs TL/heading surface) or keep S1 omission?

### A7. CodeHighlighting / table selection long-press

- Still deferred under local S1 / host selection model.
- **Decision:** port, stub, or never?

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

- `ArticleViewer.java` (active slice program; stuck)
- `RecyclerListView.java` (forced_exact gesture/recycling; heavy host wiring)
- `Utilities.java`, `AnimatedFloat.java`, `PullForegroundDrawable.java`, …
- Web stack: `WebInstantView`, `AddressBarList`, `BookmarksFragment`, `WebMetadataCache`, …
- Dialog cell SPI: `DialogsItemAnimator`, `AnimationProperties`

Full list: `scripts/vendor-classify.sh --port-mode exact`.

## Native / reference-only (default: do not overwrite)

~153 `:telegram` native + app native ports. `vendored_at` is historical. Re-check only `forced_exact` subsystems when product requires.

## Suggested default if you want zero more product risk

1. Keep all host-wiring tables above as-is.
2. Continue ArticleViewer only when you pick A1–A7.
3. Do not bump global pin until `scripts/vendor-status.sh --bump-report <new>` is empty of undecided exact+modified files.

## How to decide an item

For each row, answer:

1. Product needs pin behavior? (yes / no / later)
2. Accept new dependency? (jspecify, zxing, androidx.interpolator, TL_iv, …)
3. Accept host API break? (ImageLocation arity, BaseFragment classGuid, …)

Then either:

- re-sync file with host wiring re-applied, update compact manifest + lock, compile/tests; or
- leave `modified:true` / `stuck_reason` and mark “wontfix” here.

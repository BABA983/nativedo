# Vendor bump 12.10.2 — landed

Pin `9bcf3d2769c6` (Telegram Android 12.9.0) → `163356809fa4bd81e64969700753e0a15664a288`
("update to 12.10.2 (7086)", 2026-09-17) — **landed**, this document is the record of *why*
each piece moved and what is left. The pin lives in [vendor-overlay.json](../vendor-overlay.json);
the workflow is [VENDOR.md](../VENDOR.md); provenance is derived (`scripts/vendor-index.sh`),
pre-flight is `scripts/vendor-guard.sh`. Prelude work landed before the pin moved, one verified
commit per step.

## What the pin diff contains

For the 496 paths we mirror, 12.10.2 touches 143 of them: **128 differ, 15 are deleted**
(12 `com.google.android.exoplayer2` files and 3 `androidx.media3`/recyclerview files that were
*renamed*, not dropped), and 353 are untouched.

Largest areas by change volume: `ui/Components` (49 files, ~5.9k lines), `messenger`
(29 files, ~2.8k), `ui` top level (16 files, ~2.3k), `ActionBar` (15 files, ~2.2k),
`ui/web` (4 files, ~810), `ui/Cells` (6 files, ~619), `tgnet` (3 files, ~1.9k).

Only three of those areas are decisions; the rest is drift we can leave alone:

| Topic | Decision |
|---|---|
| message-bubble theming (`Theme.MessageDrawable` → top-level class) | **follow** (landed, see below) |
| Instant View split into `org/telegram/ui/iv/` | **not following** |
| bot-webview bridge hardening (`BotWebViewContainer`) | **nothing to port** |
| emoji-pack refactor, gifts, stars, stories, proxy, bot keyboards | not product goals |
| zxing → Maven, ExoPlayer → media3 submodule | settled, see VENDOR.md "Third-party code" |

## Landed before the bump

| Step | What | Commit |
|---|---|---|
| 0 | resource/label verification: `native` for the 12 exoplayer stubs, resource groups resolved by bytes, `GIT_NO_LAZY_FETCH`, parallel prefetch | `285861f` |
| 1 | zxing from Maven (`com.google.zxing:core:3.5.4`), in-tree zxing deleted, Telegram's QR writer vendored with host wiring | `c0b61fd` |
| 2a | 12.10.2 package moves: `LinearSmoothScroller*`/`ChatListItemAnimator` → `ui/recyclerview/`, media3 stand-ins, `VideoPlayerHolderBase` + `HttpGetFileTask` | `baae8b6` |
| 2b | message-bubble theming: `MessageDrawable` extracted to `ui/ActionBar/` | `1d0ab15` |
| — | CI stops running the whole pipeline on every commit (path filters + job gating) | `07d8466` |
| 3 | the bump itself: pin, `ViewOutlineProviderImpl` adopted, move mappings dropped | `02b209c` |

## The bump

The overlay's `upstream` block is the only place the pin lives, so the bump is small: the pin,
the date, the subject, and dropping the three `derived_from` entries that only bridged the
recyclerview move (`LinearSmoothScrollerCustom`, `LinearSmoothScrollerEnd`,
`ChatListItemAnimator` — all three now sit at their upstream paths). Resource groups and the
`reference` block carry their own baselines (`vendored_at`, `reference.commit`) and do not move
with the main pin.

One file needed a real decision. `ViewOutlineProviderImpl.java` was a byte-clean copy of the
pin; 12.10.2 grows it from 67 to 174 lines (a `drawViewWithOutline` / `outlineToPath` helper
that clips a software canvas to a view's outline). Our copy carried no host edits, so the new
file was adopted byte-exact and `:TMessagesProj` compiles with it.

Twenty-one files have no upstream original at 12.10.2 and are therefore reported as shims:
the exoplayer2 stubs (upstream replaced that tree with the media3 submodule), the media3
stand-ins we added in 2a, and the GMS/guava shims. They are not scratch files: nine host files
still import the exoplayer types, so the stubs keep satisfying compile-time references, which
is what their `native` labels say. `MessageDrawable.java` was in that shim list before this
commit and is an ordinary copy now that the pin has it.

## Decision: follow the message-bubble theming move

At the pin `MessageDrawable` is a nested class inside `Theme`; 12.10.2 extracts it to
`org/telegram/ui/ActionBar/MessageDrawable.java`. The extraction is **pure code motion** —
diffing the pin's nested body against the new file leaves only the import block, `key_*` /
`ResourcesProvider` / `shouldDrawGradientIcons` qualification (`Theme.` prefixes), and one
redundant `MessageDrawable.PathDrawParams` qualifier.

What we did, matching upstream's own shape:

- vendored `MessageDrawable.java` byte-exact from 12.10.2 (869 lines, no non-Android deps);
- dropped the duplicated nested copy from our `Theme` and re-pointed the four references
  (`ActionBarLayout`, `CheckBoxBase`, `MediaActionDrawable`, `RadialProgress2`);
- gave `Theme` the two members the extracted class needs — a static `getCurrentColor(int)`
  and a `public static shouldDrawGradientIcons` — both of which are upstream's own 12.10.2
  declarations, not host inventions.

`MessageDrawable.java` has no counterpart in the pin tree (the class sat inside `Theme.java`
there), so the index reports it as a shim until the pin advances — it is byte-exact 12.10.2
code, not ours.

The forced-exact files above are touched only by that move, so the guard's class-move
allowance covers them: a moved class rewrites an import and a qualified reference to a simple
one, and neither can move a gesture/measurement/drawing number (`strip_type_qualifiers` in
`scripts/vendorlib.py`, `types=` on `comment_only_edit`). A real edit behind the move still
raises the prompt.

## The cell/theme content rebase — sliced and landed

The import closures originally recorded here (62 / 30 / 8 / 5 — `Stars*`, `Stories*`,
`Gift*`, `TL_keyboard`, `RichMessageLayout`, …) are the *whole-file* cost of replacing
these mirrors wholesale. Measured as a delta
(9bcf3d27 → 12.10.2) the same files are small — `ChatMessageCell` +395/−98,
`ChatActionCell` +7/−2, `Theme` +371/−882 (845 of them the `MessageDrawable` extraction
that landed in `1d0ab15`), `TextSelectionHelper` +55/−9 — and **no slice needed a new or
stubbed class**: every file is already a `native` entry, so the work is hand-porting
deltas, not buying closures.

Landed, one commit per slice:

| Slice | What | Commit |
|---|---|---|
| 1 | `TextSelectionHelper`: cached selection text is copied out of the live layout and drops `TextWatcher`/`SpanWatcher`/`NoCopySpan` spans; a null prefix clears its cache entry | `21239b8` |
| 2 | `Theme`/`ThemeColors`: the 16 table / divider / article code & details keys, the calculators that derive them from the bubble, the fallbacks, accent-exclusion and accent-tint pass, `getDefaultColor` following the fallback chain, `fillingPaint` skipping a no-op | `f229669` |
| 2b | The `:app` body blocks sample those keys: table surface and grid, preformatted background, details arrow and line, rule divider | `0a5b77c` |

Deliberately not taken, and why none of them is a product goal:

| Piece | Reason |
|---|---|
| `ChatMessageCell` / `ChatActionCell` content | both are 52- and 15-line stubs, so "adopting the content" means implementing Telegram's message cells (29.5k lines) *plus* their closures. The upstream delta does not intersect the cells we draw: no hunk's pre-image is present in `TopicPostCell` or `TopicDateCell` |
| the 30 `key_chat_msgIvButton*` keys, `IvButtonColors`, the `TL_keyboard` import | bot keyboards / Instant View buttons |
| `RLottieDrawable` constructor and layer names, `Utilities.blurBitmap(bitmap, 3)` | a coherence unit with its own files: our copies still carry the old signatures, so the call-site hunks belong to that pair's rebase |
| `ActionBarLayout` edge-to-edge insets (`EdgeToEdgeSupportMode`) | our tree has no such mode; taking it moves `BaseFragment`, the sheet containers and every container call site, and the result cannot be verified in an orb (no device, no emulator, and Paparazzi cannot see real insets). Revisit with a concrete insets bug and a device |

A product goal that needs one of these features should port it into the `:app` surface
that renders it, not re-copy an area.

Slice 2 also brings `key_chat_{in,out}ArticleCodeScrollbar[Background]`, which nothing
consumes yet: `CodeBlock` scrolls with the platform scrollbar. They stay because the
`applyCalculatedArticleCodeColors` pass that derives them is verbatim upstream code, and
dropping two keys would make that block diverge — which costs more on the next sync than
two unused ints. If the code block ever gains a themed scrollbar, the colours are there.

## Decision: not following the Instant View refactor

12.10.2 splits IV into `org/telegram/ui/iv/` (`Latex`, `RichMessageLayout`, `RichHtml`,
`RichTextStyle`, `QuoteCollapseButton`), slims `ArticleViewer`, makes `QuoteSpan` depend on
`QuoteCollapseButton`. `RichMessageLayout` alone is ~10k lines and needs `ru.noties.jlatexmath`;
`Latex` pulls the same library. Our article/browser stack is a host-wired native port with its
own epic already closed ([vendor-articleviewer-plan.md](vendor-articleviewer-plan.md)) —
adopting a second layout engine and a new dependency buys us nothing we need.

## Decision: bot-webview hardening — nothing to port

The `BotWebViewContainer` delta (+505/−296) wraps every `addJavascriptInterface` call in a
`WebRequestContext` that pins the *document generation* and the *trusted origin*
(`isBridgeRestrictedToOrigin`, `getTrustedOrigin`, `loadUrl(..., sameOrigin)`,
`replaceWebView(..., url, sameOrigin)`), and drops events from untrusted documents.

Our `BotWebViewContainer` is a `native` port that **exposes no JavaScript bridge at all**
(no `addJavascriptInterface`), and its `WebSettings` already disable file, content, file-URL
and universal-access-from-file-URL access. Without a bridge there is no origin to check, so
there is nothing to port. Revisit only if we ever add a JS bridge to that WebView.

## Remaining work

1. **Resolved — orb size, not the tests**: the deferred rebase needed a larger orb. On
   an `a1.medium` (4 vCPU / 8 GB) `:app:testDebugUnitTest` runs in 6m55s cold — 697
   tests, 0 failures — and the forced-exact classes the guard names run in seconds. The
   `a1.small` failure mode recorded here was the Kotlin test source set, never the tests
   themselves. `scripts/vendor-setup.sh` was also the only thing that checked
   `HEAD == pin`; `.agents/setup` and `.agents/resume` now do too (`6eb9df3`), because a
   snapshot taken before a pin bump otherwise keeps the old checkout on disk.
2. **Watch item**: 241 of the 520 tracked files differ from 12.10.2 (they were at 12.9.0
   before the pin moved). Nothing in that set is wrong — the pin advanced, the copies did
   not — but the next product-driven sync should take the delta for the files it touches
   instead of copying a whole area. The slices above are the model: `Theme` moved ~200
   lines of delta where the whole-file figure suggested an 8-import closure, and the two
   cell mirrors turned out to need nothing at all.

The deferred rebase has landed (slices 1, 2, 2b) and the dropped pieces are recorded
above with their reasons, so this document is now historical.

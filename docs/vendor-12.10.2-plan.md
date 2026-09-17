# Vendor bump 12.10.2 — plan and decisions

Pin `9bcf3d2769c6` (Telegram Android 12.9.0) → `163356809fa4bd81e64969700753e0a15664a288`
("update to 12.10.2 (7086)", 2026-09-17). The pin lives in [vendor-overlay.json](../vendor-overlay.json);
the workflow is [VENDOR.md](../VENDOR.md); provenance is derived (`scripts/vendor-index.sh`),
pre-flight is `scripts/vendor-guard.sh`. Prelude work lands before the pin moves so each step
can be verified and committed on its own.

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
| 0 | resource/label verification: `native` for the 12 exoplayer stubs, resource groups resolved by bytes, `GIT_NO_LAZY_FETCH`, parallel prefetch | `5642022` |
| 1 | zxing from Maven (`com.google.zxing:core:3.5.4`), in-tree zxing deleted, Telegram's QR writer vendored with host wiring | `567fa9f` |
| 2a | 12.10.2 package moves: `LinearSmoothScroller*`/`ChatListItemAnimator` → `ui/recyclerview/`, media3 stand-ins, `VideoPlayerHolderBase` + `HttpGetFileTask` | `1d92852` |
| 2b | message-bubble theming: `MessageDrawable` extracted to `ui/ActionBar/` | *(this step)* |
| — | CI stops running the whole pipeline on every commit (path filters + job gating) | `65b7c3c` |

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

## Deferred: the cell/theme *content* rebase

`ChatMessageCell`, `ChatActionCell`, `Theme`, `TextSelectionHelper` and `ActionBarLayout`
also gained 12.10.2 content, but adopting it is not a copy — it drags a wide closure of
classes for features we deliberately do not implement:

| File | Missing imports to satisfy |
|---|---|
| `ChatMessageCell.java` | 62 — `Stars*`, `Stories*`, `Gift*`, `Poll*`, `TL_stars`, `TL_stories`, `TL_payments`, `TL_iv`, `RichMessageLayout`, `Giveaway*`, … |
| `ChatActionCell.java` | 30 — the same families plus `ChatThemeController`, `Community*` |
| `Theme.java` | 8 — `TL_keyboard`, `BlurSettingsBottomSheet`, `ThemeActivity`, `ThemePreviewActivity`, … |
| `TextSelectionHelper.java` | 5 — `RichMessageLayout`, `RichTextCell`, `LanguageDetector`, … |

Their bot-keyboard additions (`TL_keyboard`, `key_chat_msgIvButton*`, `BotInlineKeyboard`)
are the new feature riding along. None of this is needed to ship the linux.do client: the
cells we actually draw are `:app` ports (`TopicCell`, `TopicPostCell`), and these mirrors are
`native` entries, not our product surface. Revisit when a product goal needs one of those
features — not as a bulk re-sync.

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

1. **Bump commit**: advance `upstream` in the overlay to 12.10.2, prune `reference`, drop the
   three `derived_from` entries that exist only to bridge the recyclerview move, resolve the
   shims, refresh the `resources` baselines, re-run `scripts/vendor-guard.sh --compile`.
2. **Deferred, needs a bigger orb**: the cell/theme content rebase, if a product goal appears.
   `:app:testDebugUnitTest` does not finish on an `a1.small` orb — the Kotlin test source set
   thrashes for half an hour without producing results (twice, with warm and cold daemons) —
   so the forced-exact test classes the guard names cannot be run here. What this orb *can*
   verify is compilation (`:TMessagesProj:compileDebugJavaWithJavac` ~25s,
   `:app:compileDebugJavaWithJavac` ~1.5min) and the guard's static checks.

Mark this file historical once the pin moves.

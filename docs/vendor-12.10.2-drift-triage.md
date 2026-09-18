# Vendor drift triage — 12.10.2

Snapshot of where the mirror stands at the 12.10.2 pin. The guard reports "241 of
520 tracked files differ from the pin", which is a number without a work item
behind it: most of that is our own host wiring, and much of the rest is upstream
feature work we deliberately do not take. This is the list that turns the number
into candidates.

## Method

- Local blob hash against the same path at `9bcf3d27` (12.9.0) and at the pin
  (`16335680`); hashing needs no blob fetches, so the table is cheap to redo:

  | State | Meaning |
  |---|---|
  | **synced** | byte-identical to the pin |
  | **pure drift** | byte-identical to 12.9.0 and upstream moved — a clean copy/delta |
  | **adapted** | differs from both — our port, or a `reference`-baseline file |
  | **shim** | no upstream counterpart |

- Upstream delta size and change families from `git diff 9bcf3d27 16335680`.
- *Consumed* means the class name appears in `:app` sources, or the file is the
  ancestor named by an `upstream_ports` entry in `vendor-overlay.json`.

## Where the 520 tracked files stand

| State | Files | Action |
|---|---|---|
| synced | 272 | none |
| pure drift | 29 | a clean copy when the surface is touched |
| adapted, upstream moved | 121 | a delta hand-port, with Theme / TextSelectionHelper as the model |
| adaptation only | 91 | none — upstream did not move |
| shim | 7 | none — no upstream counterpart |

The 150 drifted files collapse onto **133 upstream paths**: 70 are referenced
from `:app`, 14 are ancestors of our ports, and 38 sit on the historical
`reference` baseline (`009e97356f96`), which is not re-synced by policy.

## Tier 1 — review with the next product change to that surface

| File | Upstream Δ | What changed | Why it is on the list |
|---|---|---|---|
| `ui/Components/AnimatedArrowDrawable.java` | +33/−3 | width/height/stroke constructor (`dpf2`) | our details arrow uses the 2-arg constructor; this is upstream's own details arrow shape |
| `ui/Components/BottomSheetWithRecyclerListView.java` | +192/−17 | constructor overloads through `Params.Builder` (fragment, needFocus, hasFixedSize, resourcesProvider) | reply and participants sheets build on it |
| `ui/ActionBar/BottomSheet.java` | +59/−15 | `skipDismissAnimation()`, `doNotOverlayNavigationBar`, `ItemOptions.DimView` dim handling, nav-bar draw path | every sheet in the app |
| `ui/Components/TableLayout.java` | +182/−35 | natural horizontal geometry, `updateTextX/Y`, valign/padding helpers, `getEmojiOnlyCount` | post-body tables render through `TableLayout` |
| `ui/Components/EditTextBoldCursor.java` | +121/−16 | `setTransformHintToHeaderOnFocus`, hint-header visibility rule, `Choreographer60FpsContent` invalidation | search field and composer inputs |
| `ui/ActionBar/ActionBar.java` | +93/−11 | glass drawables become `BlurredBackgroundDrawable`, `alwaysApplyColorFilterToBackButton()`, width clamp | main-tabs chrome |
| `ui/Components/ReplyMessageLine.java` | +92/−93 | pattern/shader rewrite with a cached bitmap, `setSimpleColor` | reply and quote rails; the file is `forced_exact(drawing)` |
| `messenger/AndroidUtilities.java` | +96/−108 | drops the `USE_LEGACY_SYSTEM_INSETS` branch, adds `executeOnUIThread`, media3 `Consumer` | shared utility; the insets half belongs to the edge-to-edge question |
| `messenger/PhotoViewer.java` | +296/−302 | insets/media3/proxy touches on top of our port | our image viewer port |

## Reviewed against the product, not taken

Tier 1 is a "review when the next product change touches that surface" list;
these eight were read in full now, and none is worth taking today:

| File | Why not |
|---|---|
| `ui/Components/BottomSheetWithRecyclerListView.java` | it does have one consumer (`TopicDetailFragment`'s sheet subclass), but the delta is an internal `Params.Builder` refactor sitting on the edge-to-edge program the sheets are parked on; no behavior change for us |
| `ui/Components/AnimatedArrowDrawable.java` | the new constructor is geometry parity for upstream's details arrow (12.66/6.16/1.66dp); ours draws a 2dp stroke into a 13dp box, so the difference is cosmetic |
| `ui/Components/TableLayout.java` | the delta is IV-shaped: wide-table scaling (`setRenderWidth`), emoji-only cells, new padding defaults. Our tables scroll horizontally instead of scaling and never use `valign_bottom`, so only the padding change would be visible |
| `ui/ActionBar/BottomSheet.java` | the delta is the edge-to-edge program (`EdgeToEdge` V1/V2 + window insets) plus APIs nothing calls yet (`skipDismissAnimation()`, and `ItemOptions.DimView` measuring — `:app` never uses `ItemOptions`) |
| `ui/Components/EditTextBoldCursor.java` | makes the hint-to-header transform configurable and re-checks it; the default path is unchanged, so nothing is fixed for us |
| `ui/Components/ReplyMessageLine.java` | the drawing rewrite calls the new `native Utilities.drawReplyLinePattern`; our `Utilities` is deliberately no-JNI, and we consume only `check()` for colours, which the delta leaves alone |

## Landed since the snapshot

| Commit | What | Effect |
|---|---|---|
| `3d4c037` | `SimpleTextView` takes 12.10.2's wrap-width clamp (`setWidthWrapContent` could hand a negative width to `setMeasuredDimension`; the profile header, `DiscourseUserCell` and the topic title all measure through it) | six files byte-exact again; the guard's "differ" count 241 → 235 |
| `ce1876d` | `TextSettingsCell` takes 12.10.2's row density (icon gutter 21→16dp, value margin 71→58dp) | byte-exact; no production consumer yet |
| `4817a8a` | additive helpers: `Choreographer60FpsContent` once-callbacks, `DrawableUtils` `RectF`/int overloads, `FragmentSearchField.setHintText` | three files byte-exact |
| `cd6fc5b` | icon tints `MULTIPLY` → `SRC_IN`: 10 sites of upstream's 33, in `ActionBarMenuItem`, `ActionBarMenuSubItem` and `TextCell` | goldens do not move — pure-white icons are identical under both modes; the fix shows on icons that are not white |
| `8f107e4` | `EditTextBoldCursor`: `setTransformHintToHeaderOnFocus`, and selection handles keep one colour filter plus per-instance mutated drawables | the cursor-blink override stays out (`forced_exact(animation)`, no product reason) |
| `0084f59` | import-only cleanups: `HistoryFragment` (20 dead imports), `StaticLayoutEx` (4), `Blur3Utils` (a blank line) adopted verbatim; `Theme` (4) and `BookmarksFragment` (1) hand-cleaned | four files byte-exact; guard's "differ" count 231 → 228 |
| `236ca60` | the last two mechanical pure-drift deltas: `DefaultItemAnimator`'s `protected` widening, `SerializedData`'s `InputStream` constructor | both byte-exact; 228 → 226 |
| `ad73778` | `AnimatedArrowDrawable`'s additive custom-geometry constructor (`color, widthDp, heightDp, strokeWidthDp`) | byte-exact; the two-argument path our blocks use is unchanged, goldens confirm |
| `0ac809a` | `QuoteSpan` moves off the `reference` baseline onto the pin, with the collapse/expand toggle extracted into `QuoteCollapseButton` | the span keeps a `public QuoteCollapseButton collapseButton` and drops ~76 lines of inline animation; our editor compiles unchanged, and `verifyPaparazziDebug` is green — no golden moved |
| `d2fa294` | the wallpaper/AGSL group: `MotionBackgroundPaint` byte-exact (it wants the blur3 `BitmapChangeTracker` closure, which is in the tree), `MotionBackgroundDrawable` at the pin plus a 3-line wiring diff, and the `Utilities` surface they call — `generateGradient`'s `(bitmap, phase, progress, colors)` signature, plus new `applyAlphaInvert`/`expandAlphaToBlack` Java fallbacks modelled on the pinned `jni/image.cpp` | `MotionBackgroundPaint` is the first of the pair to be byte-exact; no golden moved, and that is the expected result, not a miss: nothing calls `setFastRenderAllowed()` so the AGSL path stays dormant, and `generateGradient` remains a host no-op, so the CPU path's gradient bitmaps are as blank after as before. The rewrite the pair brings (pattern+gift merge, legacy-bitmap path dropped, `drawBackground`/`drawPattern` collapsed into `draw`) is a device check |
| `d09ee77` | first hand-carry out of the pool: `ChatActivitySideControlsButtonsLayout` takes the pin's pending-button state (`setButtonCount`/`setButtonLoading`/`setButtonEnabled` only touch the view once the holder exists, and `getOrCreateButtonHolder` flushes it) | the file is now the pin plus its one local helper (`setButtonContentDescription`); the forced-exact trio passed, and the full suite plus `verifyPaparazziDebug` stayed green |
| `60a764c` | RLottie family shapes: the stub takes the pin's constructors (plus the dice `String` one upstream moved to `RLottieDiceDrawable`, and the `File` ones `ImageLoader` keeps), the 15 call sites drop the layer-name argument, and the 22 `.**` layer names become plain names (`Theme` ×20, `ThemeDescription` ×2) | `RLottieImageView` is byte-exact again (drift 217 → 216) and no golden moved. The three-argument constructor keeps its no-op body on purpose: assigning `resId` would arm the stub's composer-icon fallback and light up `ChatActivityEnterViewAnimatedIconView`, which the topic composer mounts — a device-visible change left as an open decision |

## Blocked, and by what

Adopting these wholesale was tried and fails at compile time, which is the
difference between "not worth it" and "cannot yet":

| File | Compile error | What has to land first |
|---|---|---|
| `ui/Components/BottomSheetWithRecyclerListView.java` | `cannot find symbol: EdgeToEdge` (in `Params`/`Params.Builder`) | `BottomSheet`'s `EdgeToEdge` enum and insets path — the parked edge-to-edge program |
| `ui/ActionBar/AlertDialog.java` | `package org.telegram.ui.Stars does not exist` (`BalanceCloud`, `StarsIntroActivity`) | the Stars family (non-goal), or a hand-port that keeps our stripped imports |
| `ui/ActionBar/BaseFragment.java` | `cannot find symbol: EdgeToEdgeSupportMode` (and the classes it drags) | the same edge-to-edge program |

`AnimatedArrowDrawable` looked like this list at first reading (its delta is a new
constructor) but compiles and is byte-exact now; only *using* the new geometry is a
visual decision.

## Revisit when the mirror is at the pin

The owner's direction: once every mirrored file sits at 12.10.2, the family
refactors become a later project — Discourse posts are rich text, so the
rich-text composition/rendering families are the plausible ones (`rich_message`
formatting, the composer/`EditTextBoldCursor` editing surface, message-cell text
rendering as a reference). The bot-keyboard half of `TL_keyboard` stays a
non-goal. Design is ours to make; this note only keeps the candidates from being
re-derived.

Read and left, for the record: `TextSelectionHelper`'s only remaining hunk is
`hideActionsMenu()`, whose single upstream caller is `iv/RichEditorListView` (the IV
refactor we do not follow) and whose body needs a `showActionsRunnable` field our
12.6-baseline copy never had; `AlertDialog`'s +64 is an opt-in four-button two-row
layout no screen of ours uses; `BaseFragment`'s +25 is a debug `Trace` wrapper and
`getEdgeToEdgeSupportMode()`.

`3d4c037` also removed the dead `com.google.android.exoplayer2.util.Log` import from
six files — 12.10.2 deletes it, nothing in the tree used it (the plan document's
"nine host files still import the exoplayer types" predates this). `0084f59` took
the rest: `ArticleViewer` → `AspectRatioFrameLayout` is the last real exoplayer
import (the stubs' own `Format` reference aside), so the `Log` and `Util` stubs
have no importers. The stub set stays for `AspectRatioFrameLayout` and `Format`;
pruning the other ten is a separate decision.

Read in full and left: `ChatActivityEnterViewAnimatedIconView` (byte-exact — it was the one
call site already on the pin's three-argument constructor), `ProfileActionsView` (its delta
is the RLottie constructor, which our port does not carry), `WindowInsetsStateHolder`
(display-cutout insets — device-bound), and the blur3 group — its closures landed, and the
one mirror that wanted them (`MotionBackgroundPaint`) is at the pin now.

One left *because upstream looks wrong*: `ExtendedGridLayoutManager` sets
`lastSpanCount = itemsCount` and then guards `checkLayout()` with
`lastSpanCount != getSpanCount()`, which is true whenever the item count and the
span count differ — i.e. almost always — so every `checkLayout()` recomputes the
layout. The file is used (`UniversalRecyclerView`), so our copy stays at 12.9.0
until upstream sets the field to the span count it compares against.

The hand-port pool, measured after the wallpaper group (`git apply --check` of the
12.9.0 → pin delta per moved upstream path, local root prefix substituted): of the
local files whose upstream moved, **five** take the delta verbatim — four of them are
still parked (`BottomSheet`, `BottomSheetWithRecyclerListView` and
`WindowInsetsStateHolder` on the edge-to-edge program, `ExtendedGridLayoutManager` on
the suspected upstream bug above) and the fifth, `RLottieImageView`, landed with the
family's shape change (`60a764c`). The
other 62 are `native` ports or mirrors whose host wiring overlaps the hunks; one of
them has landed by hand (`d09ee77`), and the rest are per-family hand-porting — RLottie's JNI
body (its shapes landed, `60a764c`), the media3 imports, and the `native` ports whose
upstream ancestors moved inside settled non-goals.

The `:app` ports' ancestors moved 12.9.0 → pin mostly inside settled non-goals:
`DialogCell` (+14/−17: rich-message drafts, RLottie layer names), `ChatActionCell`
(+7/−2: `TL_keyboard`, `emojiLoaded`, RLottie constructor), `ChatActivity`
(+743/−327) and `ChatMessageCell` (+395/−98) (stories, proxy, bot keyboards),
`ArticleViewer` (+39/−77) (the IV split). The landing-relevant deltas to read before
the next port change are `ProfileActivity` → `ProfileFragment` (+55/−5),
`SharedMediaLayout` → `ProfileContentLayout` (+59/−64), `DialogsActivity`
→ `TopicDialogsRecyclerView` (+19/−11) and `MainTabsActivity` (+140/−5) — all figures
are 12.9.0 → pin.

## Tier 2 — upstream moved, no product path: leave

| Family | Examples | Why not |
|---|---|---|
| bot keyboards / inline markups | `MessageObject`, `SendMessagesHelper`, `ChatActivity`, `ChatMessageCell` | settled non-goal |
| proxy | `ConnectionsManager`, `ProxySettings` users | settled non-goal |
| emoji packs | `Emoji`, `EmojiView`, `AnimatedEmoji*` | settled non-goal |
| localization | `LocaleController` (+ `org.telegram.localization`) | a new upstream subsystem; taking it drags a package we do not ship |
| RLottie layer names | `ThemeDescription`, `DialogCell`, `Theme` | the `.**` suffix and the stub's constructor shapes are aligned now (`60a764c`); what is left is `DialogCell`'s port and the JNI body of `RLottieDrawable`/`RLottieNative`, which this tree cannot carry |
| media3 / exoplayer | `VideoPlayer`, `AnimatedFileDrawable`, `AndroidUtilities` | settled when zxing and media3 were decided |
| stories / gifts / stars / community | `ChatActivity`, `MainTabsActivity`, `SharedMediaLayout` | settled non-goal |
| edge-to-edge insets | `ActionBarLayout`, `DrawerLayoutContainer`, `LaunchActivity`, `MainTabsActivity` | needs a device to verify; parked until a concrete insets bug |

## Tier 3 — not drift

- 272 synced, 91 adaptation-only, 7 shims.
- The 38 `reference`-baseline files (`009e97356f96`, several `:app` ports among
  them) are pinned to an older commit by policy; they are not re-synced.

## Reproducing

```bash
# per tracked file: hash it, and read the two upstream baselines' blob shas
git hash-object --stdin-paths < apps/android/...  # local content
git -C vendor/telegram-android ls-tree -r 9bcf3d2769c6 -- TMessagesProj/src/main/java
git -C vendor/telegram-android ls-tree -r 163356809fa4 -- TMessagesProj/src/main/java
# sizes and families
git -C vendor/telegram-android diff --numstat 9bcf3d2769c6 163356809fa4 -- <paths>
```

Both `ls-tree` calls read trees only, so a triage run costs seconds even on the
blob-filtered checkout.

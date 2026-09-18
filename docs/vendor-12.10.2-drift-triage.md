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
| `ui/Components/MotionBackgroundDrawable.java` + `MotionBackgroundPaint.java` | +170/−488, +242/−168 | gradient rendering moves to cached bitmaps and an AGSL/shader impl | theme wallpaper animation |
| `messenger/AndroidUtilities.java` | +96/−108 | drops the `USE_LEGACY_SYSTEM_INSETS` branch, adds `executeOnUIThread`, media3 `Consumer` | shared utility; the insets half belongs to the edge-to-edge question |
| `messenger/PhotoViewer.java` | +296/−302 | insets/media3/proxy touches on top of our port | our image viewer port |
| `ui/Components/QuoteSpan.java` | +17/−76 | slims the span in the `iv/` direction | used by quote content; check before taking — it leans on the IV refactor we skipped |

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
| `MotionBackgroundDrawable` + `MotionBackgroundPaint` | a bitmap/AGSL gradient rewrite on the wallpaper path, which is already on the device-validation list |
| `ui/Components/QuoteSpan.java` | slims the span toward the `iv/` refactor we skipped |

## Tier 2 — upstream moved, no product path: leave

| Family | Examples | Why not |
|---|---|---|
| bot keyboards / inline markups | `MessageObject`, `SendMessagesHelper`, `ChatActivity`, `ChatMessageCell` | settled non-goal |
| proxy | `ConnectionsManager`, `ProxySettings` users | settled non-goal |
| emoji packs | `Emoji`, `EmojiView`, `AnimatedEmoji*` | settled non-goal |
| localization | `LocaleController` (+ `org.telegram.localization`) | a new upstream subsystem; taking it drags a package we do not ship |
| RLottie layer names | `ThemeDescription`, `DialogCell`, `Theme` | coherence unit with `RLottieDrawable`; no product goal |
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

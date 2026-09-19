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
| `cc94b7f` | `Utilities.drawReplyLinePattern` becomes a Java port of the pinned `jni/image.cpp` (reference column with averaged transitions, 45° skew, the same validation and premultiplied output), which unblocks `ReplyMessageLine`'s delta: `drawLine`'s single- and multi-colour branches, `checkPatternBitmap` with its cache, `setSimpleColor`, and `drawBackground`'s `Theme.multAlpha` + `RadiiUtils.radiiAreSame` fast path | the loading branch and the pattern's scroll animation stay out (our port has no such state); the new tests sit in `MotionBackgroundDrawableContractTest` because adding a test class shifts the suite's order and flips order-sensitive goldens. Full run 704/0, no golden moved |
| `39372c2` | `BookmarksFragment` and `QRCodeBottomSheet` declare themselves `native`: both are Telegram files whose bodies were re-targeted at the Discourse models (bookmark rows, avatar-centred QR), so the drift report should stop treating their shape as drift | classification only, no code change |
| `6fda1ca` | the `isSingleChannel` tail: `TLRPC.TL_documentAttributeCustomEmoji` (our `TLRPC` is a shell, so it is the fields only), `MessageObject.getInputStickerSet`/`isTextColorEmoji`, `RLottieDrawable`'s 9-argument `File` constructor, and the `ImageLoader` call sites; `PullForegroundDrawable`, `DialogsItemAnimator` and `ArticleViewer` join `native` for the same reason as above | the custom-emoji branch compiles without the JNI body it would need on a device |
| `2e9f699` | the last two cosmetic-only mirror deltas: `SimpleItemAnimator`'s trailing blank line and `RectFMergeBounding`'s comment arrow | both byte-exact, 216 → 214. This closes the mechanical pool: a sweep of all 173 differing mirrors found no other file whose delta is whitespace or comments only, so every remaining difference is either a deliberate adaptation or a hand-carry |
| `6b83ef2` | `forced_exact` grows teeth: the guard's prompt is per file (`FORCED_EXACT_TESTS_BY_PATH`, 58 entries) instead of a fixed trio, unmapped files get name-matching candidate hints, and `scripts/tests/test_vendor_guard.py` fails when an entry names a renamed test or an un-promoted file. Promoted 31 mirrors that a test already pins — the five body blocks that lacked what their siblings had, the text cells, and the widgets with contract tests | 125 → 156 forced-exact files, no code change and no golden moved; the prompt was exercised on a mapped file (`TextCell` → `TextCellContractTest`) and an unmapped one (`BottomSheet` → trio + candidate hint) |
| `6a32c2a` | first hand-carry sweep over the `reference` set, using upstream's own baseline→pin patch per file: `UItem` takes the pin's additive factories (plus the `enabled` check in `itemContentEquals`, now a rebind instead of a no-op) and `GraySectionCell` takes back `createThemeDescriptions` | `GraySectionCell` byte-exact, `UItem` off the `reference` baseline (91 paths left); 213 differ. The sweep is also the measurement below — the mechanical pool is closed |
| `8c1e55f` | step 1 of the instant-view split: `org.telegram.tgnet.tl.TL_iv.pageTableCell` exists and `TableLayout` is the pin's file; the row's cell list, the two cell builders (`TableBlock`, `DetailsBlock`) and `WebInstantView`'s parser feed that type | the pin's name aliases onto our legacy `TLRPC.TL_pageTableCell` (the stub is a `native` entry), so `TableLayout` is byte-exact without the ~650-reference rename the full split needs — that seam is what a later slice widens. `:app:verifyPaparazziDebug` green, no golden moved; 524 tracked / 213 differ |
| `216ac1a` | first file of the cells family: `TextCell` becomes the pin's file (969 lines, up from our 279-line widget) with three premium features wired out (`FilterCreateActivity.NewSpan`, `PeerColorActivity.LevelLock`, `SettingsActivity.SettingCell.Background` — nothing in the tree calls them), the `AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable` stub extended to the API the cell calls, and `ThemeSettingsFragment`'s theme descriptions moved to the pin's field names | `UniversalAdapter`'s `VIEW_TYPE_TEXT` binding now matches the cell upstream binds; `TextCellContractTest` was rewritten for the adopted file's layout contract (the icon mirrors for RTL, the value side is text-metric dependent), and `:app:verifyPaparazziDebug` is green — no golden moved |
| `23e38e1` | the next two cell-family files: `TextCheckCell` and `TextDetailCell` are the pin's verbatim (so they leave `native` and the guard reports them byte-identical), with their dependencies `SpoilersTextView` and `AvatarSpan` copied from the pin, plus two forward-ports of our older components — `TextSelectionHelper.SimpleSelectabeleView` and `AvatarDrawable`'s account-aware `setInfo(int, …)` overloads. One wiring line: `AvatarSpan.setName` passes the id/name pair because `MessagesController.PeerColor` is not in our port | 526 tracked / 212 differ; the two contract tests follow the pin's structure (`getCheckBox()`, a `SpoilersTextView` title); `:app:verifyPaparazziDebug` green — no golden moved |
| `999800f` | the composer's formatting core: `MediaDataController.createNewSpan`/`addStyleToText` ported verbatim, `URLSpanReplacement` copied, and a hook on our `LaunchActivity` shim so the copied span stays the pin's file | two new contract tests in `EditTextEffectsContractTest` pin the split and clear semantics (`TextStyleSpan`, the run model and the `FLAG_STYLE_*` bits were already byte-exact). Adopting `EditTextCaption` itself is measured and deferred: it is the caption class of Telegram's style system, and it drags ML Kit language detection (`TranslateAlert3.setText` -> `LanguageDetector`), the style-toolbar UI (our `FloatingToolbar` port is 439 of the pin's 1610 lines), `AlertsCreator.createFormattedDatePickerDialog` and the `menu_*` ids |
| `88563fb` | the editing surface starts: `EditTextBoldCursor` becomes the pin's file (1383 lines, verbatim) and our composer follows it — `setDelegate`/`setAllowTextEntitiesIntersection` were our port's additions with no counterpart at the pin, so the two calls go and the pin's own `Choreographer60FpsContent` invalidation takes over | the guard map gains `EditTextBoldCursor` -> `EditTextEffectsContractTest` + `AlertDialogScreenshotTest`; no golden moved. Next in the family, measured: `EditTextCaption` needs `CopyUtilities`/`URLSpanReplacement`/`AlertDialogDecor`/`FormattedDateSpan` (four clean copies), `TLRPC.TL_messageEntityFormattedDate`, `FloatingToolbar.StyleDelegate` (our toolbar port is 439 of the pin's 1610 lines) and `AlertsCreator.createFormattedDatePickerDialog` |
| `c051fb0` | the near-pin wiring bucket closed: `GroupedPhotosListView` is byte-exact again (its one-line delta was an import the IV move left behind), and four more files drop the same stale import; the other eleven deltas stay, each with its reason recorded below | 526 tracked / 211 differ; imports only, so the full suite was the gate (Paparazzi skipped deliberately) |
| `2e7c61c` | the instant-view model moves: all 68 page/block/rich-text classes leave `TLRPC` for the pin's home in `TL_iv`, renamed to the pin's spelling (`TL_pageBlockPhoto` → `pageBlockPhoto`, `TL_textPlain` → `textPlain`; the eleven the pin still spells the legacy way keep theirs), as data holders with the field subset our ports read and write — and the references follow (`ArticleViewer` 569, `WebInstantView` 119, the app blocks 68, their tests 52, plus three files that only rename a declared type) | the seam alias (`TL_iv.pageTableCell extends TLRPC.TL_pageTableCell`) is now the real class, `TableLayout` stays the pin's file, and the field subsets are all that is left of the family: a later import of upstream's article-viewer code spells the same types. No golden moved |
| `e6bdc30` | the editing surface's main file lands: `EditTextCaption` is the pin's 1033-line file in the tree, and our composer's `EditText` is now `EditTextCaption(context, null)` — the same class Telegram's `ChatActivityEnterView` mounts. The cost was measured by copying the pin's file and compiling (31 errors, four gaps): `FloatingToolbar.StyleDelegate` (the pin's three-method interface, additive on our port), the eight style menu ids in `TMessagesProj`'s `ids.xml`, `TranslateAlert3.setText`/`setOnUse` and `AnimatedEmojiSpan.applyFontMetrics` on the stubs, and `AnimatedEmojiSpan` extending `CharacterStyle` so style spans can sit beside it | the adopted file keeps a 45-line delta for the two Telegram-only helpers it reaches — `makeSelectedDate()` is a no-op (formatted-date entities), `showInputDialog()`'s adaptive branch uses the plain `AlertDialog.Builder`, and HTML paste uses framework `Html.fromHtml` (see the blocked table for `AlertDialogDecor`/`CopyUtilities`). Full suite 707/0; `verifyPaparazziDebug` green and no golden moved, before the composer mounted it and after; the forced-exact trio ran for the additive interface in `FloatingToolbar` |
| `83eca05` | the composer's selection toolbar is upstream's now: `FloatingToolbar` (1610 lines, from our 439-line port) and `FloatingActionMode` (327, from 269) are byte-exact pin copies, plus the `GradientProtectionDrawable` helper the toolbar's overflow gradient reaches. Compiling them needed only `AndroidUtilities.allGlobalViews()` (the pin's body: `android.view.inspector` on Q+, `WindowManagerGlobal` reflection below) and a local mirror of androidx's `WindowInsetsCompat.Side`, which ships from core 1.16 while this module is pinned to 1.13.0. On the visible side, `extendActionMode` adds upstream's style items in upstream's order (`ChatActivity.fillActionModeMenu`: quote, spoiler, bold, italic, mono, strike, underline, link, regular) and `performMenuAction` routes those ids to the new `TopicReplyComposerMarkdown` | the actions edit markdown, not span runs: `TopicViewModel.submitReply` sends `replyDraft.raw`, which the composer feeds verbatim, so a span would be lost at send while a marker is what Discourse parses. Toggles are `**`, `_`, `~~`, backtick, `[u]`, `[spoiler]`, per-line `> ` and `[label](url)` through upstream's `showInputDialog`. Guard 529 tracked / 210 differ / 21 shims (three stale `native` entries dropped: the two toolbar files and `EditTextBoldCursor` are byte-clean); full suite 724/0 including 17 new markdown tests; `verifyPaparazziDebug` green, no golden moved; `:app:assembleDebug` green |
| `bcf5f08` | androidx core 1.13.1 → 1.16.0, and `:TMessagesProj` takes its own `core-ktx` edge: the pin's sources need 1.16 (`WindowInsetsCompat.Side`), and the library module was resolving 1.13.0 transitively. `GradientProtectionDrawable` drops the local `Side` mirror and is the pin's file again | the 1.16.0 AAR allows it (minCompileSdk 35, minAgpVersion 8.6.0, which the build has). Suite 724/0; `verifyPaparazziDebug` green with no golden moved; `assembleDebug` green; guard 529 tracked / 209 differ / 21 shims |
| `15f5bdb` | the composer goes WYSIWYG: the style items no longer edit markers, they run upstream's `EditTextCaption.performMenuAction` (style runs, `QuoteSpan` quotes, `URLSpanReplacement` links), and the new bridge `TopicReplyComposerMarkdown` serializes those spans into the markdown the draft store and `submitReply` carry, and parses a stored draft back into spans. Span-only edits are reported through `EditTextCaption`'s delegate because applying a span does not fire a `TextWatcher`; the panel remembers what it last reported so a state update cannot rewrite the composer | markdown cannot carry accent, strike-red, marked and sub/superscript, so those flags are dropped on the way out. 723/0 (16 bridge tests: nesting, abutting runs, links, quotes, round-trip stability); `verifyPaparazziDebug` green, no golden moved; `assembleDebug` green; guard OK, 529 tracked / 209 differ |
| `6b775d7` | the poll-votes sheet becomes Telegram's: `PollVotesAlert` (1475 lines) is the pin's file with four `// NativeDo:` deltas (no Telegram text runs/custom emoji on the question and option labels, no vote time because the site does not send one, and the voter tap routed to the host), and the model it needs comes with it — the pin's poll holders in `TLRPC` (`Poll`/`TL_poll`, `PollAnswer`, `PollResults`, `PollAnswerVoters`, `MessagePeerVote*`, `TL_messageMediaPoll`, `TL_messages_votesList`, `TL_messages_getPollVotes`, `UserStatus`, `TL_textWithEntities`), `TranslateController.PollText` + `Message.translatedPoll`, `LocaleController.formatDate`, `AndroidUtilities.isContextSafe`, a native `StatusBadgeComponent` shim (this tree has no verified/premium badges), and `PollVotesBridge` (native) which answers `messages.getPollVotes` from `polls/voters.json` through `ConnectionsManager` | the app rebuilds a Discourse poll as `TLRPC.Poll` (`TopicPollVotesAlert`) and opens the sheet from the existing "view votes" entry, so the 740-line `TopicPollVotersSheet`, its UI state and the ViewModel paging around it are deleted; `PollVotersSheetArgs` gains the poll type and voter count so the sheet's percentages and subtitle match the site. Full suite 753/0; `verifyPaparazziDebug` green with one new snapshot (`TopicUserSheetsScreenshotTest.pollVotesAlertRendersVoterSections`); guard 560 tracked / 215 differ |
| `78fa211` | the rich body goes hybrid: `PostRichMessageBuilder.isMappable` answers for a block *and its subtree*, `splitPostBodySegments` cuts the body into runs of mappable blocks plus the callout/onebox/poll blocks between them, and `RichMessageBody.blocks` routes the runs through the renderer and the rest through `PostBodyBlockFactory`, keyed by the block's index | with the switch on, a post mixing prose with a poll or a link card reads in order instead of dropping those blocks; 5 segment tests (order, grouping, container fallback, no block lost) plus the `richMessageHybridBody` cell snapshot; the flag-off and single-run goldens do not move; `TopicPostCell` stays untouched |
| `798ae84` | the post body speaks: `core/ui/PostAccessibility.kt` (`postBodyAccessibilityText`) builds one description from the cooked blocks — text, poll questions and counts, link cards, callouts, quote authors, visible details children — where every image contributes its `alt` (caption alongside when both exist, `图片` when neither), and the topic adapter sets the cell's `contentDescription` from it plus floor and author | upstream's shape (one description per message); nothing inside the forced-exact `TopicPostCell`/`ImageBlock` changes; 8 tests pin the spoken text, no golden moved |
| `b461063` | the rich body's switch becomes a persisted debug toggle: Settings → 调试 → 「富文本正文（实验）」 drives `RichMessageBody.setEnabled` (flag + `SharedPreferences`) and `RichMessageBody.loadPreference` runs from `NativeDoApp.onCreate` | 3 Robolectric tests (default off, round trip, off again) mean the device pass needs no rebuild; the default stays `false`, so the flip is still the "re-record the ten goldens" commit |
| `5fd8dc3` | S5, the selection engine: `TextSelectionHelper` is the pin's 4,091-line file (3,336 lines after three deltas — `ChatListTextSelectionHelper` omitted, an unused `RichTextCell` import dropped, and one `// NativeDo:` branch in `quoteText()` so a non-chat view reaches the quote hooks), and the pin's `TextLayoutBlock` shape (`Layout`-typed, `getSelectionBounds()` default, no prefix geometry, no clip rect) replaces ours across `PostSelectionLayoutBlock`, `RichMessageBody`, `TableBlock`, `ListBlock` and `CodeBlock`; `TopicPostTextSelectionHelper` (native) answers Quote on upstream's own hooks (`canShowQuote()`/`onQuoteClick(...)`, the shape `ChatActivity` uses) and carries the two things our port had grown — list-adapter observation and overlay teardown — plus native shims for `LanguageDetector`/`RestrictedLanguagesSelectActivity` | the span-level `TextSelectionBoundsProvider` is gone (`Emoji`, `InlineTextImages`, `PostBodyChipSpan` migrate; the engine derives span bounds from the layout); full suite 768/0; `verifyPaparazziDebug` green with no golden moved; guard 562 tracked / 217 differ. The dialogs-list half is the one omission, and it needs the chat text layout (plan: `vendor-selection-helper-plan.md`, P3) |
| `1c295dd` | the four ui/iv files that S5 unblocked: `RichBlockSelection` (the 42-line bridge that turns a non-text block's rectangle into the pin's `TextLayoutBlock`, with a cached 1-char placeholder layout and fresh bounds per call) and the three cells that build selection geometry with it — `RichDividerCell` (dp(12) line, block bleeding dp(12) past it), `RichDetailsEndCell` (the dp(6)+1 rule under a details block), `RichMathCell` (a scrollable LaTeX image; selection centred while it fits, full width while it scrolls). All four are the pin's blobs verbatim; `8e73eb2` lists them `forced_exact` (drawing; measurement on the two measuring cells and the math selection rect; gesture on the press hit test) mapped to the new `IvLeafCellsContractTest`, which pins the measured heights, the one block each cell reports, the handle bounds and the placeholder cache | guard 566 tracked / 217 differ; full suite 772/0; `:app:verifyPaparazziDebug` green — no golden moved (the files have no consumer yet, which is the point: they are the layer the iv cell family and `RichEditorListView` build on) |

## Hand-carry pool: measured

The pool is the `reference`-baseline set, and the measurement is upstream's own
patch per file, rewritten onto our path:

```bash
# per file: upstream's baseline -> pin patch, checked against our copy
git -C ../telegram diff <vendored_at> <pin> -- TMessagesProj/src/main/java/<upstream> \
  | sed -e 's#^--- a/#--- a/apps/android/<local>#' -e 's#^+++ b/#+++ b/apps/android/<local>#' \
  | git apply --check -p1
```

| Result | Files | What it means |
|---|---|---|
| no upstream delta | 11 | upstream's blob is identical at the reference commit and at the pin, so the file differs from the pin by our adaptation only — there is nothing to align, and the reference listing carries no information for it |
| applies cleanly | 1 | `UItem` (`6a32c2a`) — additive factories only; `asRadio2` had to stay out because our stripped `UniversalAdapter` lacks `VIEW_TYPE_RADIO_2`, so a clean apply is not yet a compiling one |
| conflicts | 75 | either our own rewrite (`Bulletin` 316 vs 1543 lines, `SizeNotifierFrameLayout` 236 vs 1230, `TextCell` 279 vs ~600, `NotificationCenter`, `FiltersView`, `CameraView`, …) or the delta is already in (`CheckBoxSquare`'s rounded checkbox, `ActionBarMenuSubItem`'s `SRC_IN`, `EditTextEmoji`'s observer move, `DiscourseProfileActionsView`'s RLottie constructor) |

So the mechanical pool is closed: what remains in the reference set is our own
design (leave it listed) or a family project. The families, with their
first-line blockers:

| Family | First line | Blocker |
|---|---|---|
| IV types | landed (`8c1e55f` + `2e7c61c`): the 68 classes live in `TL_iv` under the pin's names, `TableLayout` is the pin's file | what is left is deliberate: our classes are data holders (the pin's are generated with serializers) and carry only the fields our ports use; a future import of upstream's article-viewer code extends them field by field |
| RLottie | `RLottieDrawable` (a 12.6-based port) | the JNI body and `RLottieNative`; the shapes are already at the pin (`60a764c`) |
| cells / text | `TextCell` (`216ac1a`), `TextCheckCell` + `TextDetailCell` (`23e38e1`) are the pin's files | the rest of the family (`TextSettingsCell`, `TextInfoPrivacyCell`) is already a clean mirror; `TextCell` keeps 3 wiring points for premium features we do not ship, the other two are byte-exact |

Four deltas cannot be closed at all, and none of them is drift: `AnimatedTextView`
wants `org.checkerframework` imports (not on our classpath),
`NestedSizeNotifierLayout` wants an *unused* import of `CachedMediaLayout`
(1076 lines we do not ship), `ButtonWithCounterView` wants the unvendored
`R.drawable.mini_boost_button`, and `TelegramQRCodeWriter` deliberately drops the
Telegram QR logo.

### The wiring deltas that stay

Every near-pin-size file (ours within ±20% of the pin's line count) was swept the
same way as the cells — copy the pin's file, compile, read the errors — and the
one-to-sixty-line deltas stayed. Each has a reason:

| File | Δ | Why it stays |
|---|---|---|
| `GroupedPhotosListView` | 1 | it does not stay: the delta was an import the IV move left behind, and the file is byte-exact again (`c051fb0`) |
| `WebInstantView` | 12 | already the pin plus two wiring points: the `androidx` annotation substitution and a context-resolution fix |
| `RecyclerView` | 18 | the two host shims our Kotlin code calls (`getBindingAdapterPosition`, `getAbsoluteAdapterPosition`); dropping them is a `NoSuchMethodError` |
| `UniversalRecyclerView` | 27 | needs `RichEditor` (and `org.telegram.ui.iv`) — the rich-editor world |
| `SecureDocument` | 30 | needs `TLRPC.TL_inputFile`; encrypted media is not in the product |
| `CheckBoxCell` | 30 | needs `ContactsController` — Telegram's address book, absent by design |
| `CellFlickerDrawable` | 38 | the pin has a `GroupCallMiniTextureView` overload (voip) |
| `ThemesHorizontalListCell` | 42 | needs `ThemeActivity`/`EmojiThemes` — the theme editor and emoji packs |
| `PullForegroundDrawable` | 42 | the strips are Telegram-only: `TopicsFragment.TopicDialogCell`, `msg_filled_general`, the archive-avatar flight |
| `LiteMode` | 47 | needs the `TL_json*` TL types — the data-saver screen |
| `ProfileGalleryBlurView` | 64 | needs `ProfileSuggestionView`/`ProfileMusicView` |
| `ThemeDescription` | 66 | needs `EditTextCaption` (in the tree since `e6bdc30`), `LetterDrawable`, `ChatBigEmptyView`, `GroupCreateSpan` — the screen view types its descriptions reach |
| `EditTextCaption` | 45 | three wiring points on the pin's file: the date entity picker (`AlertsCreator.createFormattedDatePickerDialog` + `FormattedDateSpan` + `TLRPC.TL_messageEntityFormattedDate`), the adaptive dialog (`AlertDialogDecor`) and HTML paste (`CopyUtilities`) — both helpers are in the blocked table |

## Blocked, and by what

Adopting these wholesale was tried and fails at compile time, which is the
difference between "not worth it" and "cannot yet":

| File | Compile error | What has to land first |
|---|---|---|
| `ui/Components/BottomSheetWithRecyclerListView.java` | `cannot find symbol: EdgeToEdge` (in `Params`/`Params.Builder`) | `BottomSheet`'s `EdgeToEdge` enum and insets path — the parked edge-to-edge program |
| `ui/ActionBar/AlertDialog.java` | `package org.telegram.ui.Stars does not exist` (`BalanceCloud`, `StarsIntroActivity`) | the Stars family (non-goal), or a hand-port that keeps our stripped imports |
| `ui/ActionBar/BaseFragment.java` | `cannot find symbol: EdgeToEdgeSupportMode` (and the classes it drags) | the same edge-to-edge program |

| `messenger/utils/CopyUtilities.java` | `cannot find symbol: TLRPC.TL_messageEntityBold` (then Italic/Underline/Strike/Spoiler/Pre/CustomEmoji) | the entity half of our `TLRPC` shell — Telegram's `MessageEntity` wire model, which a markdown client has no use for |
| `ui/ActionBar/AlertDialogDecor.java` | `cannot find symbol: inflateContent(boolean)`, `super(alert)`, `createAlertDialog(...)` | our `AlertDialog` port (753 of the pin's 2045 lines) growing the pin's `Builder.createAlertDialog` factory plus `inflateContent`/`supportsNativeBlur` |

`AnimatedArrowDrawable` looked like this list at first reading (its delta is a new
constructor) but compiles and is byte-exact now; only *using* the new geometry is a
visual decision.

## Revisit when the mirror is at the pin

The owner's direction: once every mirrored file sits at 12.10.2, the family
refactors become a later project — Discourse posts are rich text, so the
rich-text composition/rendering families are the plausible ones (`rich_message`
formatting, the composer/`EditTextBoldCursor` editing surface, message-cell text
rendering as a reference). The bot-keyboard *wire* half of `TL_keyboard` (its
serializers and layer variants) waits for chat; the data holders it reads are in
with the rich-message work (`TLKeyboardHelper`, `ReplyMarkup`). Design is ours to
make; this note only keeps the candidates from being re-derived.

The render side of the same principle is analysed in
[`render-model-alignment.md`](render-model-alignment.md): make `TL_iv` the
post-body model (R1), then try lifting upstream's `ArticleViewer` cells out of
the monolith one at a time (R2/R3). Device-only checks accumulate in
[`device-verification.md`](device-verification.md).

Read and left, for the record: `TextSelectionHelper`'s 12.6 copy has been replaced
by the pin's file (`5fd8dc3`), so its `hideActionsMenu()` hunk is moot — with one
caveat, the dialogs-list class is omitted (`ChatListTextSelectionHelper` reads the
chat text layout, see `vendor-selection-helper-plan.md` P3); `AlertDialog`'s +64 is
an opt-in four-button two-row layout no screen of ours uses; `BaseFragment`'s +25 is
a debug `Trace` wrapper and `getEdgeToEdgeSupportMode()`.

**androidx core is at 1.16 now (`bcf5f08`).** `:TMessagesProj` compiled against 1.13.0 because it had no core dependency of its own and took appcompat's transitive one — the `{strictly 1.13.0}` in the dependency report is Gradle's consistent-resolution constraint between the module's compile and runtime classpaths, not a hand-written pin. The module now declares its own `core-ktx` edge at the catalog version, core resolves to 1.16.0 for it, and `GradientProtectionDrawable` is byte-exact again. A later vendored file that needs a newer androidx type declares the edge too instead of substituting a local mirror.
`3d4c037` also removed the dead `com.google.android.exoplayer2.util.Log` import from
six files — 12.10.2 deletes it, nothing in the tree used it (the plan document's
"nine host files still import the exoplayer types" predates this). `0084f59` took
the rest: `ArticleViewer` → `AspectRatioFrameLayout` is the last real exoplayer
import (the stubs' own `Format` reference aside), so the `Log` and `Util` stubs
have no importers. The stub set stays for `AspectRatioFrameLayout` and `Format`;
pruning the other ten is a separate decision.

**The gradient generator waits on the snapshot suite.** `Utilities.generateGradient`'s Java
body is written and unit-tested, but wiring it in makes every motion-gradient wallpaper
render real pixels for the first time, and the suite's wallpaper state is order-sensitive:
the wallpaper-derived `serviceMessageColor` leaks into later snapshots (measured: the
checkbox's unchecked circle goes from `0x28` over white to a wallpaper-tinted fill, and
`Theme.reloadWallpaper`/`applyTheme` per snapshot do not reset it because the state arrives
with the earlier tests' persisted app state). Every victim passes when its class is run
alone, so the full run is the only stable gate and it disagrees with the committed
snapshots. The ready-to-apply body is saved next to this document's workflow as
`.amp/in/artifacts/generateGradient-java.patch`; the next slice is a test-infrastructure
one — reset that state per snapshot, re-record what legitimately changes, then land it.

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
| bot keyboards / inline markups | `MessageObject`, `SendMessagesHelper`, `ChatActivity`, `ChatMessageCell` | deferred, not settled: the chat stack's prerequisite. Discourse has chat and we intend to support it, so these unlock with chat; the price list is in [`render-model-alignment.md`](render-model-alignment.md) |
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

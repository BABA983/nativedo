# Render-model alignment: Telegram's model in, translated at the boundary

The question (owner, 2026-09-18): the composer's rule — *edit in Telegram's
model, translate to Discourse's format at the boundary* — looks like it should
apply to rendering too (topic-detail post bodies, `ArticleViewer`, the instant
view stack). Would translating into Telegram's model at the parse boundary make
it easier to copy upstream's code, instead of porting and wiring it by hand?

Short answer: yes for the model half, and that half is cheap; the renderer half
is blocked by one structural fact with a concrete way around it. This document
records the measurements behind both claims, then the staged plan.

## What the surfaces translate today

| Surface | Boundary | State |
|---|---|---|
| Composer | markdown ↔ Telegram spans | done in `15f5bdb`: the composer is spans, `TopicReplyComposerMarkdown` serializes/parses |
| In-app browser / Instant View | fetched HTML → `TL_iv` blocks | already the mirror's own parser (`WebInstantView`, ours 1300 / pin 1298 lines) — the principle is already in force |
| **Topic-detail post bodies** | Discourse cooked HTML → **our own model** | `CookedPostBodyParser` (838) emits `CookedPostBlock` (128) with **HTML strings**; the renderers then re-parse those fragments |

So the post body is the one surface where the boundary translates into *our*
model, and the renderers are hand-derived ports of `ArticleViewer`'s cells:

- `ui/topic/body/` is 11,256 lines, and every block's header says
  "Derived in part from Telegram for Android's `ArticleViewer.BlockXCell`"
  (`QuoteBlock`, `TableBlock`, `DetailsBlock`, `ParagraphBlock`, `ListBlock`,
  `ImageBlock` (`BlockPhotoCell`), `CollageBlock`, `CodeBlock`
  (`BlockPreformattedCell`), …).
- They bind to **our** seam (`PostBodyBuildContext` + `PostBodyDelegate`) and
  take HTML fragments (`QuoteTextBlock(String html, …)`), so they re-implement
  Telegram's inline model in our own code.

## Why the renderers cannot simply be copied today

`ArticleViewer.java`: ours 16,215 / pin 16,469 lines, and the rebase plan marks
it "epic closed, full-file pin replace off-limits" (host wiring: `WebBookmarks`,
sheet tabs, `ActivityResult`). Its cells are **inner classes**, so "copy the
cell" means lifting code out by hand — which is exactly the drift-prone port
pattern we have.

But the cells are much closer to standalone than the monolith suggests:

- they take the host as the **top-level** interface `IArticleViewer`
  (`org/telegram/ui/IArticleViewer.java` exists as its own file at the pin),
- they call three **`public static`** helpers on `ArticleViewer`
  (`createLayoutForText` 3 overloads, `checkLayoutForLinks`,
  `buildAccessibilityText`),
- the second dependency, `WebpageAdapter` (inner, `RecyclerListView.SelectionAdapter`),
  is touched through only eleven members: `currentPage`, `isRtl`,
  `blocks`, `itemHeights`, `sumItemHeights`, `fullHeight`, `photoBlocks`,
  `channelBlock`, `getTypeForBlock`, `getItemCount`, `cleanup`.

That is a mechanical extraction, not a rewrite: same shape as the `TLRPC → TL_iv`
split (`2e7c61c`), where each extracted file becomes a tracked mirror whose
delta the guard reports.

The one real decision behind copying cells is **how the body is drawn**: the
pin's cells are `View`s hosted by a page adapter, while our body is drawn on a
canvas inside one post cell (StaticLayouts + hit-testing, the design
`docs/topic-detail-rewrite.md` chose for list performance). Copying cells
verbatim means accepting small Views inside the post cell. That is a perf/UX
call, not a vendor call.

The pin's newer `org/telegram/ui/iv/` package (`RichBlockCell`, `RichDetailsCell`,
`RichBlockChrome`, `RichCaptionController`, `RichContainer`, `RichEditText`, …)
is the modern version of the same idea: **standalone** rich-block cells instead
of monolith inner classes — 47 files / 27,247 lines, and its entry points are
tied to Telegram's message/editor world. It is the strategic alternative if the
body renderer is ever rebuilt, and a subsystem, not a slice. The triage
documents it as deferred (`UniversalRecyclerView` needs `RichEditor`).

## How upstream renders, and which renderer to copy

Measured at the pin. Nothing in Telegram renders HTML at runtime: each surface
renders from a Telegram model, and external formats are converted at the edges.

| Surface | Model | Renderer | Host | Size / shape |
|---|---|---|---|---|
| Classic chat message | `TLRPC.MessageEntity` list over plain text | `MessageObject.addEntitiesToText` → spans (`TextStyleSpan`, `URLSpan*`, `CodeHighlighting`, `AnimatedEmojiSpan`) | `ChatMessageCell`, `StaticLayout` drawn on canvas | cell-bound, ours already mirrors this shape |
| Rich message (Bot API rich messages) | `TLRPC.Message.rich_message` = `TL_iv.RichMessage` (blocks + `RichText`) | `org/telegram/messenger/RichMessageLayout.java` | `ChatMessageCell` delegates touch/selection to it and draws it | **10,066 lines, one file, canvas-drawn** |
| Instant view page | `TL_iv.Page` (blocks + `RichText`) | `ArticleViewer`'s inner `Block*Cell` Views | `ArticleViewer.WebpageAdapter` (`RecyclerListView`) | 16,469-line monolith, rebase closed |
| Rich message editing / other hosts | same blocks | `ui/iv/Rich*Cell` + `RichEditor`, `RichTextStyle`, `RichHtml`, `RichMessageConvert` | attach-alert rich layout, AI compose sheet | 47 files / 27,247 lines, editor-bound |

Conversions upstream already ships, i.e. the "assemble into Telegram's format"
half of the idea: `MarkdownParser.parse(String, ArrayList<TL_iv.PageBlock>)`
(markdown → blocks, for `.md` documents and bot content), `ui/iv/RichHtml`
(HTML ↔ rich model, for the editor's paste/copy), `RichMessageConvert`.
Both directions exist: `MessageObject.formatRichMessage/formatRichBlock` flatten
blocks back into a `CharSequence` for previews and copy.

Two consequences for our plan:

1. `RichMessageLayout` is the closest thing to our post body that upstream has:
   it draws blocks on a canvas inside a chat cell, which is exactly our shape
   (single canvas-drawn cell, `StaticLayout`s, hit-testing), and it is **one
   file** instead of a monolith of inner Views. Its `MessageObject` use is
   shallow - `messageOwner` (for the blocks), `isOutOwner`, `getDisplayRichMessage`,
   `getDialogId`, `isSponsored`/`sponsoredColor`, `isSpoilersRevealed`, link
   colors, `currentAccount` - which is why hosting it with a
   `MessageObject`-shaped holder is plausible: our `MessageObject` is already a
   364-line port and our `ChatMessageCell` is a 52-line stub that would have to
   grow the delegate/`TransitionParams` seam.
2. The blocks are the same blocks as instant view, so R1 (make `TL_iv` the
   post-body model) is the precondition for either renderer. Discourse-only
   blocks (polls, oneboxes, callouts) stay ours and would be an added
   block case in whichever renderer we adopt.

Revised R2, and then measured: `RichMessageLayout` was copied in verbatim and
compiled. It produces 337 errors, all inside the file, clustering into three
groups: host surface (`ChatMessageCell.TransitionParams` 10, `ChatMessageCellDelegate`
8, and `MessageObject` members it reads - `checked`/`checkbox`, `IvButtonColors`,
`holders`, `spoiler`, `collapsed`, `visibleHeight`, `textY`, `sponsoredColor`),
missing mirror classes (`SpoilerEffect2`, `UnsupportedBlockDrawable`,
`ForegroundColorSpanThemable`, `UnsupportedBlockDrawable`, `latex`/`Latex`), and
missing `TL_iv` classes (`RichMessage` itself, `textCustomEmoji`, `textMath`,
`textDate`, `textMentionName`, `pageBlockDocument`, `pageBlockMath`,
`pageBlockButtonRow`). Two of those drag documented non-goals: the button row
needs the bot-keyboard TL types (`TL_keyboard`, `TLKeyboardHelper`), and the
rich-text helpers come from `ui/iv` (`RichHtml`, `RichTextStyle`).

So the canvas shape is right but the host is not cheap: the rich-message path
assumes a chat message cell and two subsystems this product does not ship. R2
therefore starts with the **ArticleViewer-cell extraction** (one cell, the
eleven-member adapter, three public static helpers) and treats `RichMessageLayout`
as the destination to re-measure once the block model is `TL_iv` and the body
renderer is being replaced for real.

## What "extract into a mirror file" means, and why not just use ArticleViewer

Extraction, concretely: the pin keeps every block renderer as a class inside
`ArticleViewer.java` (31 `Block*Cell` classes). Extracting one means moving that
class body into its own file under the same package in `:TMessagesProj`, copying
it from the pin unchanged, and recording where it came from in
`vendor-overlay.json`'s `derived_from` (the same mechanism the overlay already
uses for files whose upstream source differs from their path). From then on the
guard tracks it like any other mirror: `vendor-diff.sh` shows how far it drifted,
and a re-sync is a per-file operation instead of an un-reviewable 16.5k-line
file. Extraction is mechanical here because the cells are already decoupled:
many are `public static`, the host is the top-level `IArticleViewer` interface
(89 lines, 11 methods), the adapter arrives as a constructor parameter, and the
whole cell range references `ArticleViewer.this` only five times - all of them
tab/sheet bookkeeping (`tryRemoveTabWith`, `tab.articleViewer = …`,
`activeSheets.add/remove`, one getter), never block drawing.

Why not use `ArticleViewer` itself, per post? Because it is a fragment-level
page viewer: its own window/chrome, one instance with its own state, tab
registration against `WebInstantView`, and `WebpageAdapter` is a **non-static**
inner class, so an adapter cannot exist without an `ArticleViewer` instance.
Two honest alternatives exist and neither is "the body renderer":

- Use it for the whole topic as an instant-view page (we have `TL_iv` and the
  pin's `WebInstantView`). That is an article-reading mode, a different product
  surface from the timeline of per-post cells with reactions, quotes and jumps.
- Instantiate an `ArticleViewer` inside the mirror purely as a host for its own
  cells. Possible, but it drags fragment and window state into a RecyclerView
  item, and the cells' five tab references show exactly what would have to be
  neutered.

ArticleViewer keeps its real place in this product: the in-app browser and
instant-view suite (`vendor-browser-iv-suite.md`), which already reuses it. The
post body is a separate surface, and whether it becomes View-based is the
decision R2 exists to inform.

## If chat is on the roadmap, the rich-message stack stops being a non-goal

Owner's point (2026-09-18): Discourse has chat, we will support it, and a topic
body and a chat message are the same shape upstream - `RichMessage` is blocks
plus `RichText`, the same blocks instant view uses. So the pieces
`RichMessageLayout` wants are not a cost the post body pays alone; they are the
prerequisites of Telegram's chat stack, and the earlier "bot keyboard = settled
non-goal" line should read "deferred until chat is scheduled".

The measured price (pin line counts, and how often `RichMessageLayout` touches
each): the missing pieces are shallow - most are one to seven call sites, and the
two with real substance are the button row (`TL_keyboard`, 21) and the style
mapping (`ui/iv/RichTextStyle`, 7).

| Piece | Lines | Uses | How |
|---|---|---|---|
| `tgnet/tl/TL_keyboard` | 1877 | 21 | the rich-message button row (inline keyboards) - chat prerequisite |
| `ui/iv/RichHtml` | 1427 | 5 | HTML to rich model, the editor's paste path |
| `ui/LinkManager` | 1570 | 2 | Telegram's link opener - wire out, we have our own |
| `ui/iv/RichTextStyle` | 608 | 7 | rich text style mapping |
| `components/spoilers/SpoilerEffect2` | 623 | few | spoiler rendering, additive |
| `PhoneFormat/PhoneFormat` | 423 | 3 | phone number formatting |
| `ui/Components/TornEdge` | 240 | 2 | unsupported/locked block decoration |
| `ui/Components/UnsupportedBlockDrawable` | 226 | 3 | unsupported block placeholder |
| `ui/iv/Latex` | 79 | 4 | math spans |
| `ui/Components/SquigglyLinesSpan` | 95 | 4 | spell/diff decoration |
| `ui/Components/ForegroundColorSpanThemable` | 42 | 3 | themed text colour span |
| `ui/Components/URLSpan{Mono,UserMention,BotCommand}` | 72/54/47 | few | URL span family, additive |
| `ui/Components/FormattedDateSpan` | 126 | few | date entities (we wire the picker out already) |
| `TLKeyboardHelper` | 48 | 2 | keyboard button helpers |
| `PremiumFeatureBottomSheet`, `PremiumPreviewFragment` | screens | 1 each | Telegram-only upsell - wire out |

Host surface, measured separately: `ChatMessageCellDelegate` and
`TransitionParams` are nested types of a 29,570-line `ChatMessageCell`, so
they are grown on our 52-line stub rather than copied; `MessageObject` (ours is
364 lines) only needs the members the layout reads, and the poll/inline-button
ones can start wired out.

Sequence, if chat is scheduled:

1. **S1 - landed.** The missing model classes are in: `RichMessage`, `textButton`,
   `textCustomEmoji`, `textMath`, `textDate`, `textDiff`, `textMentionName`,
   `pageBlockButtonRow`, `pageBlockDocument`, `pageBlockMath` joined `TL_iv`
   (data-holder style, like the rest of that `native` file), and the button row
   brought a minimal `TL_keyboard` host surface (`ButtonTypeProto`,
   `InlineButtonType`, `RichButtonStyle`, `PageButton`, the protocol as a marker
   interface). Measured and rejected on the way: adopting the pin's whole
   `TL_iv.java` (2,301 lines, 112 classes against our 69) verbatim - it needs the
   TL wire layer this tree does not carry (`TLObject.TLdeserialize`,
   `Vector`/`InputSerializedData`, `TLMethod`, `TLRPC.messages_Messages`,
   `TLRPC.InputUser`), which is why the file is `native` in the first place.
2. **S2 - landed.** The render-side leaves are in from the pin: `spoolers/SpoilerEffect2`
   (with the two spoiler shaders as raw resources), `TornEdge`, `SquigglyLinesSpan`,
   `FormattedDateSpan`, `ForegroundColorSpanThemable`, the `URLSpan{Mono,UserMention,BotCommand}`
   family, and `PhoneFormat` with its `CallingCodeInfo`/`PhoneRule`/`RuleSet` tables.
   Host additions they needed: `TLObject.hasFlag`/`setFlag` (the pin delegates to
   `me.vkryl.core.BitwiseUtils`, which is not in this tree, so they are inlined),
   `TLRPC.MessageEntity`'s `flags`/`language`, a `TL_messageEntityFormattedDate` data
   holder with a local flag table, a minimal `LocaleController.formatEntityFormattedDate`
   (the pin's relative/week/long/short composition lands with the date feature), and
   `TL_keyboard`'s `ButtonType` plus six button types. Left out on purpose, to be wired
   out when `RichMessageLayout` itself is patched in S3: `ui/iv/RichHtml` and
   `RichTextStyle` (they open the 27k-line editor package, below) and the
   Telegram-only `LinkManager`/premium sheets. Probe after S2: 337 -> 285 -> 229
   errors, the rest being the host surface and those wire-outs.
3. **S3 - in progress, three slices landed.**
   - `ui/iv/Latex` is in verbatim (79 lines) with the jar the pin itself uses,
     `ru.noties:jlatexmath-android:0.2.0`: a JAR-only AAR, so the math spans pay
     no JNI bill, and the wrapper stays byte-identical to upstream.
   - `TLKeyboardHelper` (48 lines, verbatim) plus the model it reads. `TL_keyboard`
     grew the pin's whole `KeyboardButtonProto` interface (including the `getUrl`/`getData`
     defaults), the remaining inline button types (`UrlAuth`, `SwitchInline`, `Buy`,
     `Callback`; the existing ones gained their pin constructor values),
     `KeyboardButtonStyle`, and the reply-keyboard row/button holders
     (`KeyboardButtonRow`, `KeyboardInlineButtonRow` + `TL_keyboardInlineButtonRow`,
     `KeyboardButton`, `KeyboardInlineButton`, `TL_keyboardInlineButton`).
     `TL_iv.textButton` learned the pin's `getType`/`getText` (that is what
     `RichMessageLayout`'s `RichButton` and `TLKeyboardHelper.isType` call), and
     `TLRPC` grew the reply-markup family as holders (`ReplyMarkup` +
     `TL_replyKeyboardMarkup`/`Hide`/`ForceReply`, `TL_replyInlineMarkup`). These are
     the data shapes upstream's composer and inline keyboards read; no screen in this
     product builds them yet.
   - `me.vkryl.android.util.ClickHelper` (227 lines, verbatim) - the tap helper
     `UnsupportedBlockDrawable` and the link spans use; `ViewUtils`/`BitwiseUtils` were
     already in the tree.
   - The model side of the renderer's closure also landed, as data holders: `TL_iv`'s
     remaining text/page nodes (`textMention`, `textHashtag`, `textCashtag`,
     `textBotCommand`, `textBankCard`, `textAutoUrl`, `textAutoPhone`, `textAutoEmail`,
     `pageBlockThinking` - all field-less markers upstream) and `TLRPC`'s
     `TL_messageEntity{Mention,Hashtag,Cashtag}` plus the `PeerColor` family
     (`PeerColor`, `TL_peerColor`, `TL_peerColorCollectible`).
   Left in S3, measured by re-probing `RichMessageLayout` + `RichTextStyle` +
   `RichInlineButtonSpan` + `UnsupportedBlockDrawable` together (**201 -> 183 errors**;
   178 of the 183 are inside `RichMessageLayout` itself). What the 183 name, by kind:
   the host surface (`ChatMessageCell.TransitionParams` 10, `ChatMessageCellDelegate` 8,
   `MessageObject` members: `checked`/`checkbox` 24, `IvButtonColors` 3+8, `holders`,
   `spoiler`, `collapsed`, `visibleHeight`, `textY`, `sponsoredColor`, `color`, `flags`,
   `from_id`, `namesOffset`, `childPosition`, `realId`, `isSpoilersRevealed`,
   `getText`/`getTextX`/`getTextY`/`getSize`/`getRealId`/`getFromChatId`/`isFromUser`/
   `isFromChannel`/`isPinnedTop`/`isTranslating`), the `ui/iv` HTML pair (`RichHtml` 1,
   `ReplaceCopyTextSpannable` 4 - the paste path, wire-out candidate), four drawables the
   button/unsupported rows reference (`mini_inline_{arrow,switch,profile,copy}_16`,
   `large_unsupported`), two accessibility strings (`AccDescrExpanded`/`Collapsed`), and
   the two documented wire-outs (`LinkManager`, `PremiumFeatureBottomSheet` +
   `PremiumPreviewFragment`). None of it is a new product surface.
4. **S4** - host it on a Discourse post (post blocks become a `TL_iv.RichMessage`)
   and compare goldens. This is the "topic body is a rich message" test.
5. **S5** - the interactive paths (spoilers, selection, button rows), scheduled
   with chat itself.

### The `ui/iv` project (approved 2026-09-18, copy rather than wire out)

`ui/iv` is the pin's 47-file / ~27k-line instant-view + rich-text package, and it is
two of our prerequisites at once:

- the **chat rich-text editor** (the counterpart of our WYSIWYG composer:
  `RichEditorListView`, `RichTextCell`, `RichCommand*`, `RichHtml`, `RichTextStyle`), and
- `RichMessageLayout`'s **HTML and style path** (`RichHtml`, `RichTextStyle`), whose
  call sites in S3 would otherwise be wire-outs.

Measured closure, from the `RichMessageLayout` probe: adding `RichHtml` alone costs 105
more symbols - `BlockRow` 55, `TableModel` 13, `RichInlineButtonSpan` 8,
`RichEditorListView` 5, `RichTextCell` 4, `ReplaceCopyTextSpannable` 4, `MediaUploadState`
3, `MathSpan` 3 - i.e. it opens the editor half of the package. That is why it is a
project with ordered slices rather than a wire-out:

1. The **non-editor leaves** - slice 1 landed (plus `Latex`, above):
   `RichBlockChrome`, `RichBlockInset`, `RichInsetCell`, `RichBlockCell`, `RichContainer`,
   `BlockRow`, `MathSpan`, `MediaUploadState` - the shared base of the iv cell family
   (`RichTextCell`, `RichMediaCell`, `RichTableCell`, `RichAudioCell`) and of `RichHtml`.
   The only host addition they needed was `TLObject`'s `FLAG_0`..`FLAG_31` table (the pin
   declares it there; `TableModel` alone uses 33 of them).
   Deferred on purpose, with the reason measured:
   `TableModel` (hard dep on `RichTextStyle.plainOf`/`toSpannable`/`fromSpannable`), and
   `RichTextStyle` + `RichInlineButtonSpan` (`RichTextStyle` spans an
   `org.telegram.ui.iv.RichInlineButtonSpan`, which calls `RichMessageLayout` - the three
   land together with the renderer in S3's remainder, which is also where
   `RichBlockSelection` waits: it implements the pin's `TextSelectionHelper.TextLayoutBlock`
   (`Layout getLayout()` plus `getPrefix`/`getSelectionBounds` defaults), while ours is
   the `reference`-pinned 12.6 shape (`StaticLayout getLayout()`, abstract `getPrefix`,
   `getPrefixLayout`), and rule 8 says that file is not moved to the pin.
   `RichCaptionHost` + `RichEditText` are the editor half and wait for slice 2.
2. `RichHtml` + the editor cells it drags (`RichEditorListView`, `RichTextCell`,
   `MediaUploadState`), with the commands/toolbar after.
3. Wire the editor into the composer as the upstream-shaped replacement for our
   span-only path, once chat/rich compose is scheduled.

Files we trim land in the overlay's `native` array; byte-identical copies do not need
bookkeeping. Nothing in this project is a new product surface by itself - the slices
exist because a single 10k-line `RichMessageLayout` and a 27k-line editor package are
not one reviewable change.

The `ArticleViewer`-cell extraction stays as the fallback: it serves the body
only, so it is the cheaper step if chat slips.

## Staged plan

**R1 — make `TL_iv` the post-body model (cheap, pixel-gated).**
Step 1 landed: `core/ui/InlineRichText.kt` cooks cooked HTML into
`TL_iv.RichText` (styles, links, spoilers, sub/superscript, marked) with the
Discourse-only constructs (emoji images, SVG icons, hashtag chips, `[size]`)
kept in the token form the model has no case for, plus nine tests.

Step 2 landed: `buildPostBodyText` walks the model instead of
`HtmlCompat.fromHtml` + marker spans. `prepareInlineContent`, the
`InlineSpoilerSpec`/`InlineTextStyleSpec` side-lists, the platform-span
collectors and `HtmlCompat` are gone (-116 lines net); `[size]` still rides
token pairs and becomes a `RelativeSizeSpan`, links still become our clickable
or chip spans, and the merged pass stays because one span per range carrying
flags plus size is the shape `TextStyleRun` describes. `normalizeInlineHtml`
and `htmlToPreviewText` now render from the model too (`toHtml`/`plainText`),
so bios and reply previews share it. The eight post-cell goldens did not move:
a boundary refactor should be pixel-neutral, and it was.

Step 3 is mechanical: the blocks carry `CookedInline` instead of HTML strings,
so the conversion happens once per post instead of once per bind.
The parser stops emitting HTML strings and emits `TL_iv` page blocks plus
`TL_iv.RichText` runs (`textBold`/`textItalic`/`textMono`/`textStrike`/
`textUnderline`/`textSpoiler`/`textMarked`/`textUrl`/`textMention`/…), and the
blocks read the mirror's fields. `TL_iv` is already real at the pin's names
(`8c1e55f`, `2e7c61c`) and `TableLayout` is already the pin's file, so nothing
new lands in the mirror. Payoff: one place for markup semantics, inline text
consumes Telegram's model instead of a home-grown one, and any later cell copy
becomes mechanical. Gate: the eight `TopicPostCellScreenshotTest` goldens plus
the suite — a pure refactor of the boundary should not move a pixel.

**R2 — renderer spike: an `ArticleViewer` cell (after measuring `RichMessageLayout`, below).**
Take `BlockBlockquoteCell` out of `ArticleViewer` into a mirror file and host it
with `IArticleViewer` plus a slim adapter carrying the eleven members its family
touches, then decide the View-vs-canvas question once, for all cells. The
`RichMessageLayout` measurement above is what moved it from the first slot to
the destination.

**R3 — if either spike holds, extend it block by block.**
Each adopted renderer becomes a mirror file with a reported delta instead of a
permanent hand port; what stays ours is what Discourse has and Telegram does not
(polls, oneboxes, callouts) plus the boundary parser. If both spikes fail,
record why here and keep the ports — R1 still pays for itself.

Explicit non-claim: R1 changes nothing visible; R2/R3 change how the body is
drawn, which is exactly why they need the goldens *and* a device pass
(`docs/device-verification.md`).

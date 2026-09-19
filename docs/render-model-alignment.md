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
   - The host surface for the renderer landed next (slice 3), and re-probing the four files
     now reports **183 -> 105 errors** (all 105 inside `RichMessageLayout`; `RichTextStyle`,
     `RichInlineButtonSpan` and `UnsupportedBlockDrawable` compile). What went in:
     `MessageObject` gained the members it reads (`isSpoilersRevealed`, `sponsoredColor`,
     `overrideLinkColor`/`overrideLinkPeerColor`, `translated`, `isSponsored`,
     `shouldDrawWithoutBackground`, `getFromChatId`/`getPeerId`, `getRealId`,
     `getDisplayRichMessage`, `isFromUser`/`isFromChannel`) plus the pin's
     `makeStaticLayout` and `capitalizeLanguage` verbatim; `TLRPC.Message` gained the pin's
     `post`/`realId`/`translatedRichMessage` and `rich_message` is now typed
     `TL_iv.RichMessage`; `Theme` gained the pin's `IvButtonColors` enum and its 30
     `key_chat_msgIvButton*` keys (appended after the last key, so no existing key moves);
     `SharedConfig.isAutoplayVideo`/`isAutoplayGifs`, `MessagesController.richEditorAllowed`
     (off: no rich-message-posting config in this product) and
     `TranslateController.isTranslating` are in; `AnimatedEmojiSpan` grew the pin's
     constructors/`cacheType`/`getDocumentId`/`setPreserveFontMetrics`/`setMinimumLineHeight`/
     `getSize` surface while staying a documented stub; and the renderer's resources landed -
     `mini_inline_{arrow,copy,profile,switch}_16`, `send_plane_26`, `large_unsupported`
     (declared as their own overlay drawable group) plus the 20 strings they label.
     Gate: guard OK, `verifyPaparazziDebug` green (732 tests, no golden moved).
   Left in S3, 105 errors, three groups - and two of them are decisions, not typing:
   the `ChatMessageCell` seam (`ChatMessageCellDelegate` 8, `TransitionParams` 10,
   `namesOffset`/`visibleHeight`/`childPosition`/`textY`/`isPinnedTop`/`getTextX`/`getTextY`/
     `drawButtonProgress`, `ChatActivity.PROGRESS_FULL_ARTICLE`,
     `ChatMessageCellDelegate.isProgressLoading`), which grows on our stub cell;
   the remaining model fields (`TL_iv`'s `checkbox`/`checked`, `collapsed`, `flags`,
     `value`/`start`/`reversed`, `compact`, `spoiler`, `fwd_from.from_id`, `chat.color`/
     `user.color`/`signature_profiles`, `DialogObject.getPeerDialogId`,
     `EmojiGroupedSpans.holders`, an `openForView` and a `shouldEncryptPhotoOrVideo`
     overload); and the wire-outs.
   **The selection-interface decision (recorded; superseded by `5fd8dc3`).** The pin's
   interface shape (`Layout getLayout()` + `getPrefix`/`getText`/`getSelectionBounds`
   defaults) was not ours at the time - the file was `reference`-pinned to 12.6,
   `native`, and `forced_exact` ['drawing','gesture'] - so the renderer's selection/copy
   sites were `// NativeDo:` wire-outs. That is no longer true: S5 put the pin's
   `TextSelectionHelper` and its interface in the tree
   (`docs/vendor-selection-helper-plan.md`), so `RichMessageLayout`'s two remaining
   sites are the copy-substitution marker (`ReplaceCopyTextSpannable`, which now
   exists) rather than a blocked decision, and the `getPrefix()` override at its
   `PaddedTextLayoutBlock` is what the pin's default already returns.
   **S3 is complete: the renderer closure compiles (0 errors).** The last pass (slice 4) landed
   the remaining model fields (`TL_iv`'s `pageBlockBlockquote.collapsed`, `pageBlockTable.compact`,
   `pageBlockOrderedList.flags/reversed/start/type`, `pageBlockPhoto`/`pageBlockVideo.spoiler`,
   `PageListItem`/`PageListOrderedItem`'s `flags`/`checkbox`/`checked`/`value`; `TLRPC`'s
   `MessageFwdHeader` with `fwd_from` retyped, `Chat`/`User.color`, `Chat.signature_profiles`;
   `DialogObject.getPeerDialogId`), the rest of the host surface
   (`MessageObject.shouldEncryptPhotoOrVideo()`, `Emoji.EmojiSpan`'s
   `setPreserveFontMetrics`/`setMinimumLineHeight`, `AnimatedEmojiSpan.setSize` and
   `EmojiGroupedSpans.holders`/`isEmpty`, the `AndroidUtilities.openForView(File, …)` overload,
   three more `ChatMessageCellDelegate` defaults), and the four mirror files themselves:
   `RichTextStyle`, `RichInlineButtonSpan` and `UnsupportedBlockDrawable` byte-exact,
   `RichMessageLayout` as a mirror with a reported delta, `RichHtml` as a documented `native` stub.
   That delta in `RichMessageLayout` is five `// NativeDo:` wire-outs, all at selection/copy or
   product-surface sites: the math span's copy substitution, `withReplacements`, the
   `TextLayoutBlock` shape (`StaticLayout getLayout()` plus a `getPrefix()` returning null in
   `Text` and in `PaddedTextLayoutBlock`), the Telegram web-app icon branch, and the premium
   rich-editor sheet. Probe trail for the record: 337 (raw) -> 229 (S2) -> 183 -> 105 -> **0**.
   Emoji caveat for S4: `AnimatedEmojiSpan` is a stub in this tree, so the renderer compiles
   against no-op emoji metrics - the emoji family (`AnimatedEmojiDrawable` and the span's real
   layout/draw) is its own alignment project, not part of S3; the device queue has the S4 pass.
4. **S4 - slice 1 landed: the pipeline is proven end to end.**
   `core/ui/PostRichMessage.kt` maps cooked post blocks to `TL_iv.RichMessage`
   (paragraph, headings 1-6, blockquote with the author as the caption and nested children as
   the `...Blocks` variant, preformatted, plain/ordered lists with the inline-item shape the
   parser's child blocks allow, details/toggle, tables with the header row marked, divider),
   and `PostRichMessageScreenshotTest` draws that model through upstream's own host view
   (`RichMessageLayout.PreviewView`) in both themes - the first pixels this renderer has ever
   produced in this tree: bold/italic/link runs, heading, bullets, the quote bar with its
   inline code run, the code block and the divider all come out as upstream draws them.
   `PostRichMessageLayoutProbeTest` guards the other half of the claim: every mapped block
   becomes exactly one laid-out block, so nothing is silently dropped between model and canvas.
   Two host rules the snapshot run made concrete, for the cell switch: the renderer paints **no
   message bubble** - `drawBackground` only draws quote/pullquote backgrounds, and the bubble is
   the cell's job (upstream's `PreviewView` leaves the canvas transparent, which is why the golden
   reads as a large white area around the content) - and the message must be built as an incoming
   one: `PreviewView.set` forces `out = true`, so its link/quote colours are the *out* variants, and
   a post body wants `out = false`.
   Not mapped yet, deliberately: callouts, oneboxes, polls and images/collages (the
   Discourse-only blocks plus media plumbing - Discourse URLs are not `TLRPC.Photo`s), so
   `pageBlock(...)` returns null for them and a post of only those blocks renders empty rather
   than wrong.
   **Slice 2 landed: the cell switch is in, opt-in.** `RichMessageBody.enabled` (default off)
   makes the top-level `PostBodyBlockFactory.build` return one `RichMessageBodyBlock` instead of
   the block tree, so the cooked blocks become the `TL_iv.RichMessage`, `RichMessageLayout` lays
   it out and draws into the cell's canvas, and the block forwards the renderer's text blocks to
   selection with the cell's offset. The cell keeps its own duties - background, column inset,
   touch - because the renderer draws no bubble, and the message is built incoming (`out` unset)
   so the body uses the body colours. The switch lives in the factory rather than the cell on
   purpose: `TopicPostCell` is `forced_exact` (drawing, gesture, measurement) and the guard
   refuses an edit there, while nested factory calls always carry a state prefix, so the switch
   can only fire at the top level. `TopicPostCellScreenshotTest.richMessageBody` renders that
   path (two new goldens) while every other golden stayed byte-identical - which is what
   "opt-in" means here.
   **Slice 3 landed for single images.** The mapping turns an `Image` block into a
   `pageBlockPhoto` carrying the absolute URL (`resolvePostImageUrl`), the declared pixel size and
   a `PageCaption` for its caption, and the renderer's photo block learned the URL case as a sixth
   `// NativeDo:` delta: with no `TLRPC.Photo` behind the block, `applyImage` loads
   `ImageLocation.getForPath(block.url)` - the mechanism the app's own image pool already uses, so
   `ImageLoader` fetches the URL directly - `getFileName`/`fileExists` answer for a URL instead of
   a FileLoader entry, and the size comes from the block instead of a photo size. The bitmap is
   device-only (a Paparazzi run has no network), so the snapshot proves the geometry: a 16:9 image
   box where the post asked for one. Collages followed: `Collage` maps to `pageBlockCollage` (one
   `pageBlockPhoto` per image, each with its URL and declared size - upstream's collage reads those
   for its aspect ratios), and `MediaCell`, the collage's per-cell helper, learned the same URL case
   (`webUrl`, aspect ratio from the block, `fileExists`/`getFileName`/`applyImage` answering for a
   URL). The caption path is exercised in the snapshots now: upstream's `RichCaptionBlock` renders
   under its image at 50% of the body text colour (probe: text `A captioned diagram.`, height 51).
   **Captions, aligned:** Telegram's model keeps **one caption per media group** (`pageBlockPhoto`
   carries its own; the renderer calls `emitCaption(collage.caption)` and never reads a member's), so
   the parser coalescing a captioned figure with adjacent images no longer drops anything: the mapping
   moves that caption onto the collage (several member captions join as paragraphs of the one caption,
   `joinedCaptions`), which is exactly what upstream's `RichCaptionBlock` then draws under the group.
   **The image tap, aligned:** a media tap reaches the host through `delegate.openArticlePhoto(cell,
   block)` (upstream's callback - `openForView` is *not* the image path; it is the document/file opener
   at `RichDocumentBlock.press`, and it stays a stub only until documents are mapped).
   `RichMessageBodyDelegate` answers it the way `ChatActivity` does upstream: `collectMediaBlocks` ->
   index of the tapped block -> `PhotoViewer.openPhoto(index, PageBlocksAdapter, provider)` - our
   ported entry point, which reads each block's URL through `photoItemForPageBlock`. One port delta was
   needed: `PhotoViewer.setParentActivity(Activity)` (our host is a view in a cell, not a fragment),
   factored so both overloads share the window setup. The adapter answers `getCaption` with **null**
   exactly like `ChatArticlePageBlocksAdapter` does - the message already draws the caption, so the
   viewer does not repeat it - and the place object for the zoom animation comes from
   `layout.findMediaImageReceiver(block, off)` plus the block's origin, which the block reads off the
   canvas matrix in `draw`.
   Still ours to do: slideshows (Discourse has none) and `openForView` when documents get mapped
   (the pin's mime dispatch over our own viewer).
   **Alt text speaks now.** A post body is canvas, so its only accessibility surface is the cell's
   description: `postBodyAccessibilityText` (core/ui) builds one from the cooked blocks - text,
   poll question and per-option counts, link cards, callouts, quote authors, the children of an
   open details - and every image contributes the one description a Discourse image has, its `alt`
   (with the caption next to it when both exist; a caption or the plain label `图片` stands in when
   alt is empty). The topic adapter sets the cell's `contentDescription` from it plus floor and
   author, which is upstream's shape - one description per message, walked block by block - and
   needed no change inside the forced-exact `TopicPostCell`/`ImageBlock`. 8 tests pin the spoken
   text; no golden moved.
   **The hybrid body landed: runs stay upstream's, the Discourse-only blocks stay ours.**
   `PostRichMessageBuilder.isMappable` answers for a block *and its subtree*, and
   `splitPostBodySegments` cuts the body into maximal runs of mappable blocks plus the
   callout/onebox/poll blocks between them; `RichMessageBody.blocks` builds one
   `RichMessageBodyBlock` per run and routes the rest through `PostBodyBlockFactory` (keyed by the
   block's index, so a poll's selection or a callout's collapse survives a rebind). A container
   whose subtree holds one of the three falls back as a whole rather than losing its child, which
   is why the predicate, not the renderer, decides. No cell change was needed - the factory already
   returned a list of blocks, so `TopicPostCell` (forced_exact) is untouched. 5 segment tests
   (order, grouping, container fallback, no block lost) plus the `richMessageHybridBody` cell
   snapshot; the flag-off and single-run goldens did not move.
   **The vertical rhythm, measured** (this is upstream's model, not ours to invent): paragraphs
   `setContentPadding(previousSiblingParagraph ? 0 : dp(5), dp(4.66f))`, headings `dp(10)/dp(6)`,
   media `padding.top + image + padding.bottom` with the caller's zero rect, captions the same, and
   `getGap()` between consecutive visible blocks is `0`; the first paragraph's top and the last
   block's bottom are trimmed by `layout()`. So upstream leaves a photo flush against whatever
   precedes it (measured in the snapshot: the quote's background ends at y=417 and the image box
   starts at y=418 - touching, never overlapping), which reads as one media message from a bot
   rather than as a reading surface. Seventh `// NativeDo:` delta: `mediaPadding()` gives a media
   block that follows something `dp(6)` of top padding. Eighth: `captionPadding()` gives the caption
   block `dp(4)` of top padding - upstream glues it to the image (measured: image bottom y=511,
   caption ink from y=521, ~3dp), and `dp(4)` is the renderer's own caption-to-credit gap, so the
   caption stays tied to its image while getting room to breathe.
   **The roll-out switch is a debug toggle now (`b461063`).** Settings -> 调试 ->
   "富文本正文（实验）" flips `RichMessageBody.enabled` and persists it (`RichMessageBody.setEnabled`,
   restored by `NativeDoApp.onCreate`), so the device pass in `docs/device-verification.md` §7 needs
   no rebuild. Measured while preparing the flip: with the default on, **10 goldens move** (five
   tests x two themes: `TopicThemePreviewScreenshotTest` x3, `TopicPostScrimHostLayoutScreenshotTest`,
   `TopicSwipeReplyRecyclerViewScreenshotTest`), all of them vertical-rhythm shifts (quote/list/code
   spacing and the rails that follow), not rendering errors - the flip commit is therefore
   "re-record those ten, nothing else".
5. **S5** - the interactive paths (spoilers, selection, button rows), scheduled
   with chat itself. **Landed (`5fd8dc3`): the selection engine is upstream's file.**
   `TextSelectionHelper` is the pin's, with `ChatListTextSelectionHelper` omitted and
   one `// NativeDo:` branch in `quoteText()`; the post body migrated to the pin's
   `TextLayoutBlock` shape and the app's Quote/list-observation/teardown behaviour
   lives in a `:app` subclass. Details, decision resolutions and what is left in
   `docs/vendor-selection-helper-plan.md`. **Second probe (2026-09-19): the file is
   ours after all - see `docs/vendor-selection-helper-plan.md`.** Of the 172 errors the adoption probe
   reports, **160 are inside `ChatListTextSelectionHelper`** (the dialogs-list engine
   that reads the un-ported chat text layout) and **`ArticleTextSelectionHelper` + the
   interfaces probe clean**; the residue is our own extras (`getPrefix*`/`getClipBounds`
   on `TextLayoutBlock`, `Emoji`'s `TextSelectionBoundsProvider`, `setOnQuote`/
   `setRecyclerView`/`destroy`) plus two `RichMessageLayout` `getLayout()` returns. So
   S5 is one file minus a block, not a chat-layout port. **First measurement, kept for
   the record:**
   adopting the pin's whole `TextSelectionHelper` (4,091 lines against our 2,347) was
   probed by overwriting the file and compiling: **172 errors, 168 of them inside the
   file**, all of them the engine reading the chat cell's layout state it lives inside -
   `messageObject.textLayoutBlocks` (35 references), `textLayout` (54),
   `getCaptionLayout`/`hasCaptionLayout`/`getFactCheckLayout`/`getDescriptionlayout`
   (32), `transitionParams`, `getCurrentMessagesGroup`, `textXOffset`,
   `charactersOffset`, `padTop`, `edit_date`, `quote`, `richLayout`. Those are products
   of upstream's chat text layout (`MessageObject.generateLayout` and the
   `ChatMessageCell` block lifecycle), which this tree never ported, so the file is
   **not standalone infrastructure**: it is the chat cell's selection engine, with the
   article/IV selection (`ArticleTextSelectionHelper`, what our `TopicPostCell` /
   `TopicDetailFragment` / `TextSelectionHelperTest` use) as one subclass. The other
   three holes are `LanguageDetector` and `RestrictedLanguagesSelectActivity`
   (translation UI we do not ship) and `ui/iv/RichTextCell` (the editor half).
   Cost on our side if adopted: 12+ `:app` body blocks implement our interface, and 13
   call sites use our extra members (`getPrefixLayout`/`getPrefixX`/`getPrefixY`/
   `getClipBounds`) that the pin's interface does not have, so they migrate too; and in
   `RichMessageLayout` the two `TextLayoutBlock` wire-outs plus the math-span copy
   wire-out would disappear. Decision (2026-09-18, owner): continue S4 first, align the
   selection helper with S5 - that is when `RichMessageLayout`'s own text blocks are
   the layout producer and the chat-shaped fields have something to be filled with.

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
   `BlockRow`, `MathSpan`, `MediaUploadState`, and (after S5 removed the interface
   blocker) `RichBlockSelection`, `RichDividerCell`, `RichDetailsEndCell`, `RichMathCell` -
   the shared base of the iv cell family
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
   `getPrefixLayout`), and rule 8 says that file is not moved to the pin. **That last
   blocker is gone: `5fd8dc3` put the pin's interface in the tree and `1c295dd`
   landed the file itself** (with `RichDividerCell`, `RichDetailsEndCell` and
   `RichMathCell`, its first consumers — see the triage document's landed table).
   `RichCaptionHost` + `RichEditText` are the editor half and wait for slice 2.
2. `RichHtml` + the editor cells it drags (`RichEditorListView`, `RichTextCell`,
   `MediaUploadState`), with the commands/toolbar after. **Measured 2026-09-19
   (`471a029`) by dropping the pin's whole 47-file package into the tree: 757
   compile errors, and the tail is the chat composer. So the slice is staged
   bottom-up:**
   - **Landed: the layer that needs nothing new** - `TableModel` (the editable
     table: span expansion into a solid grid with anchor maps, merge/unmerge,
     row/column edits, `normalizeForSend`), `RichMessageConvert` (the
     `TL_iv.RichMessage`/block ⇄ `CharSequence` bridge: line → paragraph,
     `CodeHighlighting.Span` line → preformatted with its language, `QuoteSpan`
     line → blockquote, plus `isLossy`), `RichMediaItem` and `RichMediaClipboard`.
     All four are the pin's blobs verbatim, pinned by
     `IvDocumentModelContractTest` (9 tests).
   - **Landed (`2e2e728`): the editor text view and the caption base** -
     `RichEditText` (706), `RichCaptionHost` (8), `RichCaptionController` (201),
     all byte-exact, plus the one stub they needed: a native
     `ChatAttachAlertRichLayout` with just `showEditLatexSheet` (the pin's sheet is
     `BottomSheet` + `EditTextCell` + `ButtonWithCounterView` preview, ~150 lines
     of editor UI, so it lands with that host). `IvEditorBaseContractTest` pins the
     style run API and the stub's "no sheet, no callback" contract.
   - **The cells are one host surface away.** With `RichEditText` in, the cell
     family (`RichTextCell`, `RichTableCell`/`Host`/`Grid`, `RichDetailsCell`,
     `RichQuoteAuthorCell`, `RichAudioCell`/`MapCell`/`DocumentCell`/`MediaCell`,
     `RichMediaCell`'s `LAYOUT_TYPE_*`) compiles once its own small host surface is
     there: the `ArticleHint*`/`ArticleTableTitleHint`/`ArticleAlignment` strings,
     the `iv_table_*`/`iv_media_*`/`arrows_select`/`msg_map`/`msg_spoiler*`
     drawables, an `AppGlobalConfig` shim (the `richMessage*` limits),
     `AnimatedEmojiSpan`'s `replaceFontMetrics`/`standard`, the media-upload TL
     shells (`TL_messages_uploadMedia`, `TL_inputMediaUploaded*`,
     `TL_inputPeerSelf`, `InputUser`, `TL_geoPoint*`), `TL_documentAttributeAudio`'s
     `title`/`performer`, `VideoEditedInfo`'s converter fields and
     `NotificationCenter.filePreparingStarted`/`MediaController.cancelVideoConvert`.
     `RichTextCell`, `RichButtonRowCell` and `RichAudioCell` also reach
     `RichEditor` (its nested drawables and `RichCommand`) - the editor core file,
     which is the chat-coupled tail below.
   - **The tail is chat.** `RichHtml` and `RichEditorListView` need the editor
     core (`RichEditor`, 229 errors) and the rest of `ChatAttachAlertRichLayout`
     (283) - the attach-alert editor host and the AI compose sheet. Those wait for
     chat, or take documented wire-outs like the LaTeX sheet; `RichHtml` itself is
     then three model fields away (`TLRPC.TL_geoPoint` + `access_hash`,
     `AnimatedEmojiSpan.standard`).
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

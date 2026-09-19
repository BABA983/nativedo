# Device verification queue

Work that only a device can settle. The orb has no KVM, so emulator and
instrumentation runs are impossible here; everything in this file shipped
verified by unit tests and (where relevant) Paparazzi goldens, and needs one
pass on real hardware. Add entries when a slice lands with a device-only
question; strike them out with a date + verdict once checked.

## 1. The reply composer's WYSIWYG editing (`15f5bdb`)

What changed: the composer's `EditText` is `EditTextCaption` and the style
toolbar items now run upstream's span actions (`TextStyleSpan` runs, `QuoteSpan`,
`URLSpanReplacement`). `TopicReplyComposerMarkdown` serializes those spans into
the markdown the draft store and the request carry, and parses a stored draft
back into spans.

To check:

1. Reply to a topic and select a few words. The floating toolbar should offer
   Quote, Spoiler, Bold, Italic, Mono, Strikethrough, Underline, Link, Regular.
2. Tap Bold. The selection should render **bold**, with no `**` in the field.
   Same for Italic, Mono, Strikethrough, Underline, Spoiler.
3. Tap Quote. The selection should become a quote block (Telegram's quote
   rendering, with the leading bar), not a `> ` prefix.
4. Type something, switch to another topic and back (or kill and reopen the
   app). The styled words should come back styled: the draft stores markdown and
   the parser rebuilds the spans.
5. Send. The post on Discourse should carry the styles, because the request body
   is the serialized markdown.
6. Type markdown by hand (`**x**`). It stays literal while typing and is sent
   as typed; a draft reload then shows it styled.
7. Edge cases worth poking: a style applied over a link, a style applied at a
   collapsed caret (bold with nothing selected applies to what you type next),
   Regular (clears the selection's styles), and paste of HTML from a browser
   (now framework `Html.fromHtml`, so it keeps bold/italic/links but drops
   Telegram-specific entities).

Known deltas that are expected, not bugs: accent, strike-red, marked and
sub/superscript have no markdown counterpart and are dropped on the way out.

## 2. The selection toolbar itself (`83eca05`)

What changed: `FloatingToolbar` and `FloatingActionMode` are the pin's files now
(1610 and 327 lines, byte-exact) instead of our 439-line port.

To check: select text anywhere (composer, the settings text fields, a message in
the timeline) and confirm the toolbar's position, overflow behaviour, animation
timing and blur still feel right, and that the style items appear only where a
selection exists. A regression here is hand-feel, not correctness, which is
exactly why it is on this list.

## 3. `EditTextCaption` in the composer (`e6bdc30`)

What to check: hint, cursor width/blink, selection handles, the "paste" popup in
the link dialog, copy/cut through the clipboard, and long-press selection. The
file is upstream's; the four local wires are recorded in the drift-triage table.

## 4. Wallpaper / AGSL group (`d2fa294`)

The rewrite (pattern + gift merge, the legacy-bitmap path dropped,
`drawBackground`/`drawPattern` collapsed into `draw`) is device-only: nothing in
the tree calls `setFastRenderAllowed()`, so the AGSL path stays dormant and
`generateGradient` remains a host no-op — the goldens cannot see it. Check the
motion background on a wallpaper-enabled account, both a static and a live one.

## 5. RLottie constructor shapes (`60a764c`)

The three-argument constructor keeps a no-op body on purpose: assigning `resId`
would arm the stub's composer-icon fallback and light up
`ChatActivityEnterViewAnimatedIconView`, which the topic composer mounts. If the
composer shows a sticker-icon animation where it should not, that is this
decision, not a regression.

## 6. androidx core 1.16 (`bcf5f08`)

No specific screen to check. The bump was pixel-neutral in the whole snapshot
suite, so treat any *new* inset/edge-to-edge oddity (status-bar overlap, sheet
height) as worth blaming on this first — it is a one-line revert.

## 7. The rich-message renderer (S3 complete, not yet hosted)

`RichMessageLayout` and its mirror dependencies compile (`docs/render-model-alignment.md`, S3),
and S4 slices 1-2 draw a mapped Discourse post through `RichMessageLayout`, both standalone
(`PostRichMessageScreenshotTest`) and inside the real `TopicPostCell` behind
`RichMessageBody.enabled` (off by default). What a device has to settle is that flag-on cell path:
turn it on, scroll a topic with long posts, and compare the body against the flag-off shape.
The switch is a debug toggle now (Settings -> 调试 -> "富文本正文（实验）", `b461063`); it persists
across restarts, so the pass needs no rebuild. With the default on, ten goldens move (see
`docs/render-model-alignment.md`, S4) - that is the expected shape of the flip, not part of this
checklist.

What to check once S4 lands:

1. A post body drawn by `RichMessageLayout` matches the current cell pixel-for-pixel (the
   S4 gate is the Paparazzi snapshot; a device pass confirms the animation/scroll feel).
2. Emoji: `AnimatedEmojiSpan` is a **stub** in this tree (no metrics, no drawable, no
   animation), so custom emoji in a post will be misplaced or invisible. That is the emoji
   family not being aligned yet, not an S4 bug.
3. The wire-outs are inert by design and should not be reachable from a timeline post:
   copy-as-HTML (`RichHtml` stub), the math span's copy substitution
   (`ReplaceCopyTextSpannable`), the Telegram web-app icon branch, and the premium rich-editor
   sheet. If a post can trigger any of them visibly, that is a bug in the wire-out.
4. Images: with the switch on, a post image should load (the bridge goes through
   `ImageLocation.getForPath`), keep its aspect ratio, and show a caption when the post has one.
   Tapping one should open our `PhotoViewer` at the tapped image, with swipe between the post's
   images and the zoom-from-the-tile animation (the place object is computed from
   `findMediaImageReceiver` plus the block's origin, so the geometry is worth eyeballing). A collage
   renders through the renderer too (its cells carry URLs the same way), and a caption under an image
   - or under a group, when a caption was folded onto a collage - should read at half the body text
   colour. The viewer itself shows no caption/title for these images, which is upstream's behaviour;
   if the product wants the author/time header we layer it on the viewer entry point.
5. Selection inside a rich message now runs upstream's engine (see §9): handles,
   magnifier, the article menu and the copy path are the pin's `TextSelectionHelper`, not
   our 12.6 port. The list-bullet/prefix highlighting our port drew is gone with the
   prefix geometry, which is upstream's behaviour.
6. The hybrid body (S4, `78fa211`): with the switch on, a post mixing prose with a poll, a link
   card or a callout should read in place - upstream's typography for the prose, our block for the
   three - and the seams (our blocks' own top/bottom margins against the run's trimmed edges) are
   worth eyeballing on a long post. A container holding one of the three (a details with a callout
   inside) must fall back to our block tree as a whole, never silently drop the child.
7. Accessibility (S4, `798ae84`): turn TalkBack on and focus a post. It should read the floor and
   author, then the body; an image with alt text must be spoken ("图片：..."), one with only a
   caption must read that caption, and a picture with neither must not fall silent. Check that a
   collapsed details block does not read its hidden children, and that the reaction rail below the
   cell keeps its own labels (the cell's description is additive, not a replacement for the child
   views).

## 8. The poll votes sheet is Telegram's `PollVotesAlert` (`6b775d7`)

What changed: the "view votes" entry on a poll opens the vendored
`PollVotesAlert` (the pin's file) fed by `TopicPollVotesAlert` — a Discourse poll
rebuilt as `TLRPC.Poll` — and `PollVotesBridge`, which answers
`messages.getPollVotes` from `polls/voters.json` instead of MTProto. The old
site-shaped `TopicPollVotersSheet` is gone, so the whole panel including its
paging now comes from upstream.

To check:

1. Open a public poll with votes and tap the entry: the sheet lists every option
   with its percentage and, per option, the first page of voters (50).
2. Scroll past the first page of an option: the sheet appends page 2 (upstream
   asks for it through `next_offset`, we translate it into the site's `page`).
   With more than 50 voters this is the pagination path; below that it must not
   offer a load-more row at all.
3. Avatars load from the site (`ImageLocation.getForUser`'s new URL branch);
   when a voter has no `avatar_template` the monogram circle stands in.
4. Tap a voter: the sheet dismisses and the site profile opens in the in-app
   browser, which is the behaviour our reaction sheets use. A voter without a
   username should do nothing.
5. Things Paparazzi cannot show: the section expand/collapse animation, the
   placeholder skeleton before the first page arrives, and the sheet's
   drag/scroll feel inside a topic.
6. The entry itself is still gated by `PollBlock.canOpenVotersList` (public poll
   with votes and results shown, or a voted state), and anonymous polls never
   offer it — that gate is unchanged.

## 9. The selection engine is upstream's (`5fd8dc3`)

What changed: `TextSelectionHelper` is the pin's file (the dialogs-list class
omitted, one `// NativeDo:` branch in `quoteText()` so a non-chat view reaches the
quote hooks, one dropped import), the post body implements the pin's
`TextLayoutBlock` (`Layout`-typed, `getSelectionBounds()`; our prefix geometry and
clip rect are gone), and `TopicPostTextSelectionHelper` carries what stayed
app-side — the Quote action on upstream's `canShowQuote()`/`onQuoteClick(...)`
hooks, the list-adapter observation and the overlay teardown.

To check, on a post body (long-press a text block, then drag):

1. Handles and magnifier behave like the upstream engine: selecting across
   paragraphs, dragging a handle to the cell edge (the list scrolls and the
   selection follows), and releasing into the floating toolbar's
   copy/quote/select-all/translate.
2. Quote still opens the composer with the same `[quote]` markup as before, and
   the action only appears where it did (a plain text selection without a
   Discourse author must not fail).
3. Code blocks, tables and lists still select and copy their text; a list bullet or
   a quote bar is **no longer** highlighted with its text (upstream draws prefixes
   through its own layout), and a table's rows must not smear the selection across
   cells.
4. Custom emoji, spoiler and chip spans: selection must not break into glyph
   garbage, and a copy must produce the same characters as before (the span-bounds
   provider is gone; the pin's engine derives span bounds from the layout).
5. Teardown: select something, then leave the topic (or rotate) and come back — no
   stray handles, no crash (this part is app-side).
6. TalkBack is unaffected: the cell's description is still the accessibility text
   (§7.7); selection is a separate gesture.

## 10. The block editor, and the composer's way in (`4ee2448`)

What changed: the reply composer grew the block editor's entry button (next to the
emoji one, upstream's `large_article` glyph and `ArticleEditor` label) and the sheet it
opens mounts the pin's editor page (`RichEditorHostLayout`) with our own ends - the draft
goes in as Discourse markdown and the send comes out the same way. Paparazzi pins the
rendering of one draft (`RichEditorSheetScreenshotTest`, both themes).

To check, in a topic:

1. Open: tapping the composer's entry button opens the editor with the draft the
   composer holds. Type text in the composer first and confirm it arrives as the
   document's text (the composer's own markers — `**bold**`, lists, quotes — arrive as
   their blocks).
2. Editing: the seeded heading row is the draft's first line; the toolbar's block button
   switches a row to heading/text/quote/code/list kinds (with indent/outdent in the list
   menu), the history pill enables as edits land, and undo/redo walk them back.
3. Selection: long-press then drag over a row — the bottom toolbar turns into the format
   panel (bold/italic/underline/strike/spoiler/mono/sub/sup, link, date, quote) and each
   button applies to the selection; the selection handles must survive scrolling.
4. Keyboard and insets: with the keyboard open, the toolbar's bottom row and the input
   must stay visible (the sheet pans), and with the keyboard closed the bottom row must
   sit above the navigation bar. The list must scroll clear of the toolbar (its bottom
   padding is `dp(8 + 44 + 8 + 50)`).
5. Send: pressing send posts through the reply path and closes the sheet; the composer's
   draft is cleared and the new post appears. The send button must be disabled while the
   page is empty.
6. Dismiss instead of send (back arrow in the sheet's top row, system back, swipe down,
   tap outside): the composer's draft now holds the editor's markdown — reopen the
   editor and the document must be identical (the page is a fixed point).
7. Known gaps in this slice, by design: the toolbar's attach button and a media row's
   "add" are inert (the app has no file picker, and `RichMediaUploadBridge` has no
   uploader installed yet — that is the next media slice), the emoji button does nothing,
   inline-button/block-button editing is not wired, and a site image (a photo block with
   only a URL, which is what the markdown reader produces) is not carried by the page -
   re-opening an existing post in the editor is a later flow (the edit-post surface), and
   it needs an import step before that gap is user-visible.

## 11. The editor's media: pick, upload, send (`fbe2772`, `990853c`)

What changed: the editor's attach button (and a media row's add) opens the system
picker for an image or a video; the picked file goes through
`POST /uploads.json` (multipart, `upload_type=composer`, synchronous) and the
site's URL is what the post embeds. The uploader is installed once by
`NativeDoApp` (`core/media/DiscourseMediaUploader`).

To check, in a topic with the editor open:

1. Tap the toolbar's attach button: the system picker opens (images and videos).
   Pick an image — a media block appears with the picked file previewed
   locally and a progress ring while it uploads, then no ring.
2. Pick a video: the block is a video, the preview comes from the local file,
   and the duration/size are the file's.
3. Send: the post appears with `![name](https://linux.do/uploads/...)` in its
   `raw` (edit the post on the web to see it), and the image renders in the
   topic.
4. Failure path: turn the network off and pick a file — the block must report the
   error (it is removed again) rather than hang; a row cancelled mid-upload must
   disappear without posting anything.
5. A media row's own "add" (a photo/video block that has no file yet) must put the
   picked file into that row, not into a new one.
6. Known gap: the composer's own inline reply box still has no attach path — media
   goes through the editor, and a post sent from the composer is text-only.

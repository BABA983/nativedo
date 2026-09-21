# Device verification queue

Work that only a device can settle. The orb has no KVM, so emulator and
instrumentation runs are impossible here; everything in this file shipped
verified by unit tests and (where relevant) Paparazzi goldens, and needs one
pass on real hardware. Add entries when a slice lands with a device-only
question; strike them out with a date + verdict once checked.

## 1. The reply composer's WYSIWYG editing (`15f5bdb`) — passed, one crash fixed (2026-09-20, `emulator-5554`)

What changed: the composer's `EditText` is `EditTextCaption` and the style
toolbar items now run upstream's span actions (`TextStyleSpan` runs, `QuoteSpan`,
`URLSpanReplacement`). `TopicReplyComposerMarkdown` serializes those spans into
the markdown the draft store and the request carry, and parses a stored draft
back into spans.

Verdict: the menu is complete (Quote in the primary row; Spoiler, Bold, Italic,
Mono, Strikethrough, Underline, Create Link, Regular behind the overflow). Bold and
Quote apply and render in the field with no markers in it, and the draft store picks
the markdown up (`111\n*222*` untouched → `**boldme**` → `> quoteme`). A draft stored
as markdown (`**bold** _italic_ `mono` ~~strike~~ [u]under[/u] [spoiler]spoiler[/spoiler]`
plus a `>` line) comes back fully styled after a restart, and hand-typed markdown stays
literal while typing (`*222*` stored verbatim). That reload was also where the crash
was: `parse()` built a `QuoteStyleSpan` without the back-reference upstream's
`putQuote`/`putQuoteToEditable` always assign, so the first measure threw
`NullPointerException ... QuoteSpan$QuoteStyleSpan.updateMeasureState`. Fixed in
`TopicReplyComposerMarkdown.parse`, with a regression test
(`parsedQuoteSpansMeasureAndDraw`).

Still open by hand: the Italic/Mono/Strikethrough/Underline/Spoiler *actions* (only
Bold and Quote were driven; their parse side renders), 1.5 (send — needs a reply
target), and 1.7 (Regular, collapsed-caret bold, a style over a link, HTML paste).

Known deltas that are expected, not bugs: accent, strike-red, marked and
sub/superscript have no markdown counterpart and are dropped on the way out.

## 2. The selection toolbar itself (`83eca05`) — partially seen (2026-09-20)

What changed: `FloatingToolbar` and `FloatingActionMode` are the pin's files now
(1610 and 327 lines, byte-exact) instead of our 439-line port.

Seen in the composer: the primary row (Cut/Copy/Paste/Quote + ⋮), the overflow panel
with its Back row, and its drag-to-reveal scrolling. The animation timing and the blur
were not judged by eye; also worth a second look on a long selection over a post body
(§9).

To check: select text anywhere (composer, the settings text fields, a message in
the timeline) and confirm the toolbar's position, overflow behaviour, animation
timing and blur still feel right, and that the style items appear only where a
selection exists. A regression here is hand-feel, not correctness, which is
exactly why it is on this list.

## 3. `EditTextCaption` in the composer (`e6bdc30`) — partially seen (2026-09-20)

Seen in the composer: the hint, the cursor and its blink, the selection handles and
long-press selection all behaved. Still to drive: the link dialog's paste popup,
clipboard cut/copy, and the four local wires' drift triage.

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

### Device verdict, first pass (2026-09-20, `emulator-5554`, topic 2790689 "发掘论坛的渲染极限")

With the switch on (`RichMessageLayout`) the same posts, against the flag-off cell:

- **Quotes lose their card.** The plain cell draws the app's quote card - leading bar, the quoted
  author's avatar, the referenced topic's title, its category chip and the collapse chevron; the
  rich body flattens `[quote]` to a tinted block with no bar, no header and no collapse, and the
  title line ends up inside the body text (post #1, the two quotes at the top).
- **A bullet's text overflows the column** and is clipped at the right edge ("开箱符号（Open Box）：
  ... 键符号）"), where the flag-off cell wraps it (post #7).
- **A code block is wider than the column**: its last line is clipped and a horizontal scrollbar
  strip is drawn inside the block; the flag-off cell wraps the same block (post #7).

Headings, plain lists, the divider and the spoiler bar render in place, so this is not a "nothing
draws" failure - it is the three shapes above. The switch is not ready to flip on as it stands;
it is off again in the emulator's `global.xml` after the pass. Not yet exercised on device: images
and collages (#7.4), selection inside a rich body (#7.5) and TalkBack (#7.7).

### Device verdict, second pass (2026-09-20, same emulator, after quotes stayed ours)

`isOurBlock` (the hybrid body's splitter) now keeps `CookedPostBlock.Quote` in our block tree, so
the flag-on body draws the card again: post #1's two quotes show the leading bar, the quoted author
(`星缘`) with the small avatar, the referenced topic's title, the `文档共建` / `开发调优` chips and
the collapse chevron - matching the flag-off cell. Re-read at native resolution, one first-pass item
was not what it looked like:

- The **`<kbd>` bullet** (post #7, "开箱符号（Open Box）：…") is not a column overflow: the line
  wraps, but the inline `<kbd>Space</kbd>` draws as a placeholder box and swallows the run right
  after it (「（空格」 is gone and 「键符号）」 opens the next line, with a stray corner glyph at the
  column's right edge). The flag-off cell resolves the same `<kbd>` through the sprite store, so
  this is the rich run's inline-image path, not the layout's wrapping.
- The **code block** keeps its horizontal scroll strip and clips the long line: that is what
  `RichMessageLayout` does with a long line (the flag-off cell wraps instead). A design difference
  to decide on, not a defect.

The switch is off again after the pass: the two shapes above plus the untested families (images and
collages #7.4, selection #7.5, TalkBack #7.7) keep it from flipping on.

### Device verdict, third pass (2026-09-20, media and selection)

- **An image in a rich run draws, and its viewer opens.** Topic 2790689 post #3's screenshot renders
  with its frame (the image block keeps its aspect ratio) and tapping it opens our `PhotoViewer` at
  that image - "Photo" header, no caption/title, which is upstream's behaviour (§7.4). The swipe
  between images is still unverified: that post carries a single image, so a multi-image post (or a
  collage) is the next fixture for it.
- **Selection**: a long-press on a text line raises the cell's message menu (Reply / Select text /
  Copy link / Add bookmark) and "Select text" enters selection mode - handles and the action toolbar
  draw. But the handles landed about a cell below the pressed line (pressed y ~1016 px, handles ~1610
  px on this 420 dpi screen), and a press ~20 px above an image opened the viewer instead. Both came
  from one bug: `RichMessageBodyBlock.onTouch` forwarded the *cell* event straight into
  `RichMessageLayout`, whose coordinate space starts at the block's origin, so every press landed a
  block-offset lower - a text press hit whatever media block sat below it. The block now moves the
  event into its own space first (the way the cell does for an embedded view).
  Verified with a temporary pref-gated probe (a fixed press inside the first text block of the first
  visible cell, `docs/device-verification.md` §7): before the fix the same press opened `PhotoViewer`
  (a black viewer screen, luminance ~70), after it the topic stays put (~223) and the selection's
  blue sits at the pressed line (~500 px, i.e. the probe's own y once the cell's window position is
  added) instead of ~590 px lower. The probe and its logs were removed again.

### Why the two bodies read differently (measured 2026-09-20, same emulator)

Both bodies are Telegram's, but from two different renderers: the flag-off block tree mirrors
`ArticleViewer` (the article reader) and the flag-on run is `RichMessageLayout` (the rich message).
They disagree on the reading rhythm, which is why flipping the flag visibly changes a post:

| | flag off (our blocks) | flag on (`RichMessageLayout`) |
| --- | --- | --- |
| line pitch, measured on topic 2790689 post #1 | ~60 px | ~49-50 px |
| within a paragraph | `ArticleTextLayout` sets `setLineSpacing(dp(4), 1f)`, and upstream's article text does the same (`new StaticLayout(..., 1.0f, dp(4), false)`) | `RichTextBlock` -> `makeStaticLayout(..., 1f, 0f, ...)`: no extra line spacing, exactly what `MessageObject` gives a chat message |
| between two paragraphs | 8 + 8 dp - every text block carries its own padding | 0 + 4.66 dp after another paragraph, 5 + 4.66 dp otherwise (`emitBlock`); headings 10 + 6 dp; first paragraph top and last block bottom are zeroed |

A Telegram *chat* paragraph is neither: its text also has `lineSpacingAdd = 0`, but its paragraph gap
is a full empty line. So "the rich body is Telegram's own typography" is only half true, and which
rhythm a post body should have is a product call - nothing in `RichMessageBodyBlock` can change it
today, because the constants live inside upstream's `emitBlock` and the line spacing is baked into
`RichTextBlock`'s `Text`.

### An inline image drew its token instead of its content (fixed 2026-09-20)

`InlineRichText` keeps an inline image, an inline icon, a hashtag chip and a `[size]` range as tokens
in the tree and resolves them to the readable form only in `plainText()`/`toHtml()`. The *block* path
is fine - `PostBodyInlineSpans` turns a token into a real span - but the rich run handed the tree
straight to the renderer, which draws a token literally: topic 2503776 post #1 showed
`__nd_inline_image_0__` where a custom emoji belongs. `flattenTokensForLayout()` now rewrites the tree
before the mapping hands it over, pinned by
`InlineRichTextTest.theLayoutTreeReplacesTokensWithTheirReadableForm` and confirmed on the device -
the same paragraph reads `:laughing:` now. The rich run still cannot *draw* what the block path draws
(an emoji image from the sprite store); the alt text is the honest fallback until it can.

What changed: `RichMessageLayout` and its mirror dependencies compile (`docs/render-model-alignment.md`, S3),
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
   inside) must fall back to our block tree as a whole, never silently drop the child. A fourth
   family is ours even though the mapper can express it: **quotes**, because only `QuoteBlock` draws
   the card (see the second pass above and `isOurBlock` in `PostRichMessage.kt`).
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

## 10. The block editor, and the composer's way in (`e4300b7`) — verified on device (2026-09-21)

What changed: the reply composer grew the block editor's entry button (next to the
emoji one, upstream's `large_article` glyph and `ArticleEditor` label) and the sheet it
opens mounts the pin's editor page (`RichEditorHostLayout`) with our own ends - the draft
goes in as Discourse markdown and the send comes out the same way. Paparazzi pins the
rendering of one draft (`RichEditorSheetScreenshotTest`, both themes).

Verdict: the entry button opens the sheet with the composer's draft as the document, a
tap in a row moves the caret and typing edits it, the history pill enables with the first
edit, and dismissing (the back arrow, or a tap outside the sheet) hands the edited
markdown back to the composer's draft (`hello andthere` in the draft store) - the round
trip is a fixed point. Two defects found and fixed on the way:

- the entry tap crashed with `NullPointerException: getResourceProvider(...) must not be
  null` (`showRichEditor`); `BaseFragment.getResourceProvider()` returns null and the
  fragment never overrode it. `TopicDetailFragment` now supplies the global theme as a
  provider (`AppThemeResourcesProvider`), with a regression test
  (`fragmentSuppliesTheResourcesProviderTheEditorSheetNeeds`).
- the sheet painted no background: it hands `BottomSheet` a `containerView`, and a
  container handed in must draw the sheet backdrop itself (the app's other sheets do).
  Found because the topic showed straight through the sheet and the dark-theme golden was
  white-on-white. The container now paints `shadowDrawable` like
  `FixedHeaderRecyclerBottomSheet`, and both goldens were re-recorded.

The host fix changes the sheet to `BottomSheet(context, true, ...)`, routes system back through
`RichEditorHostLayout.onBackPressed()` so selection is cleared before dismissal, and gives the
standalone page its own geometry. There is no ActionBar in this sheet: the toolbar starts at the
reported top inset and the first document row starts `dp(8 + 44 + 8)` below it. Keyboard and
emoji insets change the bottom only; the old attach-alert `2/5` or landscape `1/3.5` height
formula and the `firstItemTopRaw() - dp(8 + 44)` compensation are gone.

Device evidence was collected on `emulator-5554`, `sdk_gphone64_arm64`, Android 16/API 36,
1080x2400 at 420 dpi, font scale 1.0. The final empty state places the sheet container at
`[0,1603]-[1080,2337]`, the host at `[0,1649]-[1080,2316]`, the top controls at about
`y=1670-1786`, Title at `[42,1857]-[1038,1924]`, body at `[42,1957]-[1038,2014]`, and the
bottom editor toolbar at about `y=2179-2295`. The former roughly 800px top void is gone.

After tapping Title, UI XML reports a focused `org.telegram.ui.iv.RichEditText` with hint
`Title`. `dumpsys input_method` reports `mInputShown=true`, `mImeWindowVis=3`,
`mIsInputViewShown=true`, `mServedView=org.telegram.ui.iv.RichEditText`, and an
`ADJUST_RESIZE` focused window without `ALT_FOCUSABLE_IM`. The runner has a built-in hardware
keyboard, so Gboard renders its side toolbar instead of a full software keybed in the captured
frame even with `show_ime_with_hard_keyboard=1`; the IME service and served view are nevertheless
the expected ones. Full-keybed visual comparison needs a software-only emulator image.

Paparazzi covers the empty, content, keyboard-inset, format-panel, and undo states in both
themes. Telegram comparison was not possible: no Telegram package or APK is installed on this
emulator.

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
   button applies to the selection; the selection handles must survive scrolling. A tap
   inside either toolbar's own strip (the top row, the bottom panel) must not be eaten by
   the selection.
4. Keyboard and insets: with the keyboard open, the toolbar's bottom row and the input
   must stay visible (the sheet pans), and with the keyboard closed the bottom row must
   sit above the navigation bar. The list must scroll clear of the toolbar (its bottom
   padding is `dp(8 + 44 + 8 + 50)`), and the sheet container keeps `BottomSheet`'s own
   `dp(8)` bottom edge.
5. Send: pressing send posts through the reply path and closes the sheet; the composer's
   draft is cleared and the new post appears. The send button's enabled state is the
   editor's own limit check (the pin's `isWithinLimits`), so an empty page can look
   enabled - pressing it must do nothing until there is content. While media is still
   uploading the button shows the pin's loading arc, and the plane must stay the icon's own
   ~24dp inside its circle, never stretched to the 44dp mount.
6. Dismiss instead of send (back arrow in the sheet's top row, system back, swipe down,
   tap outside): the composer's draft now holds the editor's markdown — reopen the
   editor and the document must be identical (the page is a fixed point).
7. The media slice has since wired the toolbar's attach button and uploader. A site image,
   video, audio file or document that enters as a URL-only markdown block is imported into
   the editor's media model and must remain in the draft after dismiss/send. Remaining gaps:
   the AI key (both the block row's and the format panel's) and the emoji key are dimmed and
   unclickable - a tap must do nothing and must not open anything - and
   inline-button/block-button editing is not wired.

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

## 12. The composer's attach slot (`b9a967d`) — partially driven (2026-09-20)

What changed: the reply composer carries its own attach path. While the draft is empty the
send slot shows a paperclip instead of the send plane; a picked image or video is copied
into the cache, uploaded to `POST /uploads.json`, and its markdown lands in the draft on a
line of its own. With text already in the draft the same action is in the send button's
long-press menu (send is the other item there).

Verdict: with an empty draft the slot is the paperclip; with text in it, long-pressing the
send plane opens "Add image or video" above "Send", and that entry opens the system photo
picker. Still to drive: 12.1's insert/caret placement, the failure path (12.4), and the
send that proves the site renders the upload (12.5) - the emulator's picker has no photos,
and the send needs a reply target.

To check, in a topic:

1. Empty reply: the slot at the bottom right shows the paperclip, not the send plane. Tap
   it, pick an image — the slot must spin (and ignore further taps) until the upload
   answers, then `![name](https://linux.do/uploads/...)` appears at the caret, on its own
   line, and the slot becomes the send plane (the draft is no longer empty).
2. Type a few characters first: the slot is the send plane, and long-pressing it offers
   "Add image or video" above "Send".
3. Insert with the caret mid-line: the image must start on a new line, and the caret must
   end on the line *after* the markdown, so typing continues below it.
4. Failure path: turn the network off and attach — the composer's red error line must
   report it and the draft must stay as it was (no broken markdown inserted).
5. Send the post: the site must render the image (edit the post on the web to see the
   `raw`), which is the same wire form the editor produces.
6. Known gaps: only one file at a time, no caption, and no upload progress beyond the
   spinning slot; the composer has no screenshot golden, so this swap is device-only.

## 13. Cold start can fail with "Session changed while request was in flight" (new, 2026-09-20)

Seen three times on the emulator and once on the phone in the same pass: when
`linux.do` rotates `_t` on the first response of a launch, `SessionCookieInterceptor`
fences the concurrent startup requests (`saveIfCurrent` returns false → `accepted=false`),
every one of them throws `IOException("Session changed while request was in flight")`,
and the home screen shows "Couldn't load topics" with a Retry. Retry reloads the feed and
a restart re-verifies the session, so the session itself is never lost.

The rotation is expected (the site rotates `_t` for a session it considers stale, and this
is exactly the state an injected browser cookie starts in); the emulator's proxy also
rotates its exit IP between requests, which may make the rotation likelier than it is on a
phone VPN. What needs deciding is what a fenced *read* response (the feed, a topic) should
do: today it fails the whole startup, where keeping the response and letting the next
request carry the new token would show the feed.

Fixed (2026-09-20, seen again on the phone after the re-login): the fence drops the stale
cookie *write* but returns a plain success, so a read that lost the rotation race keeps its
payload; only a negative answer (not successful, or classified as anything but `Other`) still
fails the request, which is what keeps a stale response from reporting a logout or a challenge.
`fenced_read_returned` is logged when a read is returned that way. Test:
`fencedReadIsReturnedWhenARotationWonTheRace` (fails without the change). The next cold start
after a rotation should stop showing "Couldn't load topics / Session changed while request was
in flight".

## 14. `_t` rotation vs. the two cookie jars: a rejected token must reconcile, not log out (fixed 2026-09-20)

Symptom on the phone (`449dd25d`): the app often asked for a login on start. `NativeDoSession`
shows why: `/session/current.json` answered `404 evidence=LoggedOut ... setsToken=true
deletesToken=true`, then `event=expired_ServerLoggedOut`, which clears the native jar, the
cached identity *and* the WebView cookies.

Root cause (Discourse source): `_t` is a per-device `UserAuthToken` rotated roughly every ten
minutes, and the rotation hands the new value to whichever caller made that request. The
previous value lives for exactly one more request, after which it resolves to no user and the
server deletes the cookie (`refresh_session`: `cookie_jar.delete(TOKEN_COOKIE) if !user`). So a
rejected `_t` proves only that *that* token is stale - while the browser jar (in-app browser,
login WebView, challenge page) can hold the rotation the native jar missed. The pass made it
acute because one browser `_t` was shared by the browser, the phone app and the emulator app, so
each rotation logged out whoever still held the previous value.

Fixed: `SessionCoordinator` reconciles before clearing anything. On `SessionLoggedOutException`
it adopts the browser jar for the current revision (`CookieSyncBridge.syncFromWebViewIfCurrent`)
and verifies once more; only a second rejection clears both jars and the identity. A login
completion whose import produced no `_t` no longer runs a native verify that would clear the
browser's fresh login - it keeps both jars and reports recovery. Contract updated
(`contracts/discourse/auth-flow.md`, "`_t` rotates, so a rejected token is not a logout"), with
tests in `SessionCoordinatorTest`. New diagnostics: `webview_session_adopted`,
`webview_session_unavailable`, `webview_login_unreadable`.

Still to check on device, and it needs a live `_t` (a fresh in-app login): browse linux.do in the
in-app browser, wait out a rotation, cold-start - the log should show `webview_session_adopted`
instead of `expired_ServerLoggedOut`.

## 15. `cf_clearance` only arrives through the WebView (new, 2026-09-20)

Seen on the phone after the re-login: the jar holds `_t` and `_forum_session` but no
`cf_clearance` (every `native_response` logs `hasClearance=false`), and native reads answer
`403 evidence=Challenge` in bursts. `cf_clearance` is bound to the TLS/IP identity that solved
the challenge, so only the WebView can obtain it; the app imports it when a WebView login
completes (`CompleteLoginUseCase` stage 2) and the profile's challenge cell offers that WebView
as its action.

To check: with an authenticated but clearance-less jar, tap the challenge action (Profile →
the cell's button), let the page settle, and confirm the next native requests log
`hasClearance=true` and the `403 Challenge` bursts stop. A VPN node change invalidates the
clearance again - that part is expected.

## 16. A hand-injected session must stay host-only, or the WebView gets two variants (2026-09-20)

Injected browser cookies were written into `nativedo.cookie_store.xml` with a `domain=linux.do`
attribute, which makes them *domain* cookies for OkHttp (and `Cookie.toString()` then keeps
`domain=`, unlike for host-only cookies). Discourse sets `_t`/`_forum_session` host-only, so
`syncToWebView` wrote the domain copy next to the site's own one: the emulator's WebView jar held
`.linux.do | _forum_session | 12` (the injected value) *and* `linux.do | _forum_session | 460`
(the session the site had just issued). With two `_forum_session` variants the server resolves
the wrong session, and the GitHub OAuth callback answers `csrf_detected` - rendered as "Sorry, the
authorization timed out, or you have switched browsers" (`login.omniauth_error.csrf_detected` in
Discourse's `server.en.yml`). The flow never completes, and back then pops the login screen.

The product path is clean: the phone's jar, from a real in-app GitHub login, holds a single
host-only `_forum_session`/`_t`. So: inject host-only values (no `domain=`), or clear the app's
data between an injection and a WebView login. Verified by clearing the emulator's data and
finishing a Turnstile: the jar then holds only the site's own cookies.

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
  **Fixed:** the rich run's inline images now go through the same registry the block path uses
  (`DiscourseInlineImages` -> `AnimatedEmojiSpan`): the icon draws its sprite when the captured
  markup has it and its alt text when it does not, and the span reserves the square it draws in, so
  no run is swallowed. Device re-check: a `<kbd>`/site icon must show the sprite (or its alt text)
  and the text after it must stay on the line.
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
`__nd_inline_image_0__` where a custom emoji belongs. The mapping now carries an inline image as
upstream's own `textCustomEmoji` run (`InlineRichText.toLayoutTree`), pinned by
`InlineRichTextTest.theLayoutTreeCarriesInlineImagesAsEmojiRuns`, so the layout never sees a token.
That first pass flattened the token into the alt text (`:laughing:`, the honest fallback); the
registry below then made the run draw the real image.

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
2. Emoji and inline icons: a custom emoji must draw as the site's image (a `:name:` text or an
   empty box means the registry or the image load is broken), and a `<kbd>`/site icon must draw its
   sprite from the captured markup - or its alt text when the markup is missing - with the text after
   it still on the line. The span reserves the square it draws in, so a line whose emoji is invisible
   but whose spacing looks right is the fallback, not a crash.
3. Code blocks: a fenced block with a language (`lang-kotlin`) must come out highlighted, and the
   copy/label path must name the language; `lang-auto` must stay plain and unnamed.
4. The wire-outs are inert by design and should not be reachable from a timeline post:
   copy-as-HTML (`RichHtml` stub), the math span's copy substitution
   (`ReplaceCopyTextSpannable`), the Telegram web-app icon branch, and the premium rich-editor
   sheet. If a post can trigger any of them visibly, that is a bug in the wire-out.
5. Images: with the switch on, a post image should load (the bridge goes through
   `ImageLocation.getForPath`), keep its aspect ratio, and show a caption when the post has one.
   Tapping one should open our `PhotoViewer` at the tapped image, with swipe between the post's
   images and the zoom-from-the-tile animation (the place object is computed from
   `findMediaImageReceiver` plus the block's origin, so the geometry is worth eyeballing). A collage
   renders through the renderer too (its cells carry URLs the same way), and a caption under an image
   - or under a group, when a caption was folded onto a collage - should read at half the body text
   colour. The viewer itself shows no caption/title for these images, which is upstream's behaviour;
   if the product wants the author/time header we layer it on the viewer entry point.
6. Selection inside a rich message now runs upstream's engine (see §9): handles,
   magnifier, the article menu and the copy path are the pin's `TextSelectionHelper`, not
   our 12.6 port. The list-bullet/prefix highlighting our port drew is gone with the
   prefix geometry, which is upstream's behaviour.
7. The hybrid body (S4, `78fa211`): with the switch on, a post mixing prose with a poll, a link
   card or a callout should read in place - upstream's typography for the prose, our block for the
   three - and the seams (our blocks' own top/bottom margins against the run's trimmed edges) are
   worth eyeballing on a long post. A container holding one of the three (a details with a callout
   inside) must fall back to our block tree as a whole, never silently drop the child. A fourth
   family is ours even though the mapper can express it: **quotes**, because only `QuoteBlock` draws
   the card (see the second pass above and `isOurBlock` in `PostRichMessage.kt`).
8. Accessibility (S4, `798ae84`): turn TalkBack on and focus a post. It should read the floor and
   author, then the body; an image with alt text must be spoken ("图片：..."), one with only a
   caption must read that caption, and a picture with neither must not fall silent. Check that a
   collapsed details block does not read its hidden children, and that the reaction rail below the
   cell keeps its own labels (the cell's description is additive, not a replacement for the child
   views).

### The Discourse-only inline constructs (`2026-09-25`)

The rich run (flag-on body) now carries the four constructs the model has no upstream case for, so a
flag-on post reads like the flag-off cell. All four ride the runs the block path already emits, and
`TopicPostCellScreenshotTest.richMessageInlineCases` is the retained golden (both themes); the device
pass is what settles the parts a snapshot cannot:

- **Hashtag chip**: a cooked `hashtag-cooked` anchor is registered as a chip
  (`DiscourseInlineImages.registerChip`) and draws through the block path's own
  `DiscourseTagChipSpan` drawable, inside upstream's `textCustomEmoji` run. On device: the chip must
  show the tag's pill with its colour (not the tag name as plain text), keep its size when the theme
  flips, and **tapping it must open that tag's page** - a tap that does nothing, or one that opens the
  wrong tag, is the bug to look for. The first device pass found exactly that dead tap (the press went
  to the renderer's emoji branch, which needs a `ChatMessageCell` this host has not got); the fix is
  the `textUrl` run around the chip's drawing run (`docs/render-model-alignment.md`), and a device
  re-check is queued.
- **`[size=150]`**: the cooker's size range becomes a `RelativeSizeSpan` on the run
  (`RichMessageLayout.formatText`'s `applyDiscourseSizeMarkers`). On device: the sized text must be
  visibly larger/smaller and *stay* so through selection and copy (the markers must not leak into
  the copied text), and a nested size inside bold/italic must not shift the run's other spans.
- **Task lists**: a `[ ]`/`[x]` item is carried as `PageListItem.checkbox/checked` (the parser drops
  the `input[type=checkbox]` element and reads `checked`). On device: a checked item must draw
  upstream's checked box and an unchecked one its empty box, both without a bullet in front, and a
  *plain* list item must keep its bullet (a checkbox on every item would mean the flag was read
  from the wrong node).
- **Custom emoji / inline icons**: the registry's images (see the fixed section above). On device:
  an emoji image must load from the post's base URL and an icon without captured markup must fall
  back to its alt text rather than an empty square.
- **Inline checkboxes**: the site cooks `[x]`/`[ ]` into an `<input type="checkbox">` anywhere - a
  web-authored post renders `Inline ☑ control` on the site itself, while the app used to show
  `Inline  control` (the element dropped). On device: the same post must now draw a checked and an
  open box inline, with the same look as the task-list rows; a *list* item the site cooked keeps its
  box and must not gain a bullet.

#### Device passes (2026-09-26, runner `emulator-5554`, AVD `Medium_Phone_API_36.0`)

Two rounds, same session (`@example_user`), the patched debug build installed over the existing data (the
session and the debug switch survived) - and the runner's Mac cannot reach `linux.do` over TLS at all
(`LibreSSL SSL_ERROR_SYSCALL` on every A-record, pre-outage included), so the site's health has to be
read from the app's own traffic rather than from `curl` on that host.

**Round 1 - blocked by an outage.** `linux.do` answered Cloudflare **521** ("Web server is down", Ray
IDs `a40bf7d19be18efe` / `a40bf9945e6c8ef8`) on `/latest.json` and on every topic's `.json`, so no post
body rendered in either switch state and no chip existed to tap. The Emoji key was a silent no-op for
the same reason: the panel is built from the detail response's `emojiCategories`/`emojiCatalog`, and
the composer's key has the identical `categories.isEmpty()` guard - which also means the round could
not separate "blocked by the outage" from "broken".

**Round 2 - the site was up, and the parts a snapshot cannot reach were checked:**

- **Custom emoji images: PASS.** The same post renders its emoji images identically with the switch
  off and on (`71-posted-render-closeup.png` vs `73-posted-render-flagON.png`), and the reply the
  round posted (topic 2790689, `RENDER :grinning_face:`) renders as an image in both states - so the
  registry's URL load works on a device, not only in the golden.
- **Switch on/off on the same post: PASS** (`43-post4-flagON.png` vs `45-topic-flagOFF-open.png`).
- **`[size]`, task lists and hashtag chips: NOT REACHED.** The site had no reachable post carrying
  them (the Posts search hits for `[size=150]` and `[x]` are code/markdown posts - `77`..`80`), and
  linux.do's tags are non-ASCII, so one cannot be authored from `adb` input either. Their rendering
  is what `richMessageInlineCases` pins; the **chip tap** (`didPressAnimatedEmoji` -> `onLinkClick`)
  stays unverified on a device, and it is the one construct whose failure a golden cannot show.
- **Keyboard-dependent halves: unobservable on this AVD.** The soft IME never shows (`dumpsys
  input_method` -> `mImeWindowVis=0`, `mInputShown=false`; the `InputMethod` window is `GONE`), and
  `ime reset` / `ime set` / `show_ime_with_hard_keyboard` did not bring it back, so "the keyboard
  comes back" never happens and the sheet's keyboard rule (§10.8) has no precondition here.

Screenshots and the full command/verdict table: `slice-a-device/` on the runner (round 1: `00`..`21`,
round 2: `30`..`88`, round 3: `90`..`102`, plus `evidence-log.md`). One observation left unresolved, not
a defect claim: topic 2790689's post #4 reads `disguised_face:` as literal text in *both* switch
states, which is what the site cooked (an unknown `:name:` stays text), not a rich-body difference.

**Round 3 - the private test bed was blocked, and one check got its answer.** The emulator
(`emulator-5554`, AVD `Medium_Phone_API_36.0`) was restarted (process restarted 12:04, resumed from a
snapshot) and its IME now *connects* (`mCurId=...LatinIME`, `mImeWindowVis=3`, `mInputShown=true`) -
but Gboard paints nothing and contributes no usable bottom inset, so the app's layout never leaves the
panel-open position and the keyboard rule is still **inconclusive** (the environment cannot evaluate
it, not the patch failing). The only other device is a physical Xiaomi `24129PN74C` (`449dd25d`),
where MIUI refuses `input` injection (`INJECT_EVENTS`), so it cannot be driven; there is no second
emulator (one `qemu-system-aarch64` process, and `Pixel_Tablet_car` has a stale lock and no process).

The round's premise - driving the *in-app browser* - does not hold on this setup: it is logged out,
because the app's session is hand-injected and (per §16) the product path is a real in-app login; a
hand-injected session is not the WebView's. So the public test reply could not be deleted from the
browser and no self-PM could be created there - both need a hand (or a WebView login) on the web side.
The selection check got its answer for `adb` input: a press-hold-drag (`input swipe x y x+70 y 1500`)
produced no selection at all, and the pick then inserted a second emoji at the caret
(`ALPHAB :grinning_face: :grinning_face: ETA`) - the correct behaviour for a caret, so the replace
branch is still unexercised. Leftover, account-only: the reply composer for the Premium giveaway topic
keeps the private draft `ALPHAB :grinning_face: ETA`.

**Round 4 - the private test bed worked, and it found a real bug.** The owner created a self-PM
(topic 2953635); anonymity confirms it stays invisible (`/t/topic/2953635.json` -> 404 to a
logged-out request, while the public control answers 200). In the app the PM renders as a normal topic
- title, author headers, quote card, composer (`110`) - the first device confirmation of that. On a
reply posted into it from the app's own composer: `[size=150]` draws visibly larger, `#linux` `#api`
`#vps` draw as pills (`#dev` stays text - no such tag exists), `:grinning_face:` draws as an image, and
no `__nd_inline_size_` marker leaks - all in both switch states. Two findings:

- **The chip was a dead tap** (three taps, three positions, nothing moved, while a control tap on the
  composer bar focused it - `122`/`123`). Cause: the renderer dispatches an emoji press only when it
  has a `ChatMessageCell`, and this host is a plain view, so `didPressAnimatedEmoji` was never called.
  Fixed after the pass by carrying the chip as a `textUrl` run around its drawing run - the anchor the
  markup actually is - which routes the tap through upstream's link span and its cell-less fallback
  into `Browser.openUrl` (`docs/render-model-alignment.md`). A device re-check is queued.
- **The task list cannot be authored from this app**: a reply typed as `- [x] task alpha` cooks to a
  plain bullet, and even `inline [x] control` loses the `[x]` (`Inline  control`) - the app's composer
  does send the marker (the stored draft carries it verbatim, rounds 5-6 below), so what is lost is
  the *drawing* of what the server cooks from it; the editor's list-style button offers no checklist
  either (`121`). See the `[x]` section below for the resolved version.

**Rounds 5-8 - the chip's link run, and two traps the device pass walked into.** The chip tap stayed
dead on the device with the `textUrl` fix installed, while the same chip opened the tag page in the
flag-off block path (round 5, five pixel-verified taps; the drawing itself was unchanged, no
underline). A local probe of the shipped layout settled what the model looks like: for a chip
paragraph the rich layout holds `URLSpanReplacement [2,5] url=https://linux.do/tag/dev` **and** the
emoji span over the same range, and `getOffsetForHorizontal` across the pill's pixels maps back
inside that clickable - so the run, the span and the hit test are all right. Two later rounds then
found the *reason* the taps had been dead all along in that session, and it was neither the app's link
code nor the browser:

- **A stuck text selection swallows every body tap.** `TopicPostCell.onTouchEvent` starts with
  `if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) { ... return false; }`,
  so while the cell is in selection mode no post body touch reaches any block - chip, link, emoji,
  both flag states - while the composer (another view) still focuses, the debug "Open URL" entry still
  opens the in-app browser (`180`), and logcat stays empty (`183`). The pass's own long-press and drag
  experiments (`139`) started that selection. **Device passes must clear the selection (back) before
  tap checks, and must not long-press/drag on a post body they intend to tap afterwards.**
- **The browser was never broken**: Settings -> 调试 -> Open URL -> `https://example.com` loaded in the
  in-app browser, and Settings -> 浏览器设置 shows in-app browsing on (`180`, `181`).

**The rich run's link and chip taps were dead - fixed (`2026-09-26`).** Round 9 got the clean A/B the
earlier rounds could not: on one post, one session, single taps, with the selection cleared, the
flag-off link (native topic) and chip (in-app browser) both dispatch and the flag-on pair does nothing
(`191`..`199`). The cause is the pin's guard in `dispatchLinkClick` - it calls `didPressUrl` only with a
`ChatMessageCell`, which this host has not got - so the press fell into the cell-less `ClickableSpan`
branch and died. The fix is a `// NativeDo:` delta plus `RichMessageBodyDelegate.didPressUrl` ->
`onLinkClick`, the entry the block path uses; a cell-level probe over the shipped cell shows the tap
landing on the link's URL after it. A device re-check of the same five taps is queued (`210`+). **Round 10's re-check did not settle it**:
with the fix installed the flag-off link worked, but the flag-on pair still behaved differently (one
chip tap opened an unrelated topic) and the flag-off chip that worked in round 9 was dead in the same
session - the instance was flaky again in exactly the rounds-6-8 way. The tap question is therefore
closed on local evidence instead: a cell-level probe over a hybrid post maps the same routes as the
block path (link line -> `topic:1`, chip line -> the tag URL, no shift from the quote card in front),
which is the code path the device would exercise.
**The `[x]` answer, and what the app does with it now (`2026-09-26`).** The site cooks `[x]`/`[ ]`
into an `<input type="checkbox">` anywhere in a post (the web UI draws the same element as a box), and
the app now paints it with Telegram's own task-list box: `DiscourseCheckboxDrawable`/`DiscourseCheckboxSpan`
in the block path, the registry's emoji resolver in the rich one, and a list item's own input stays the
block parser's `checked`. On device the same web-authored post must therefore read `Inline ☑ control`
instead of `Inline  control`, with the list rows unchanged - a screenshot of it is queued (`200`).
**The `[x]` question, answered by the site itself.** The owner wrote `Inline [x] control` plus a
`- [x] done item` / `- [ ] todo item` list into the private PM **from the web**, and the web UI renders
`Inline ☑ control`, `☑ done item`, `☐ todo item` - so the server cooks both into checkboxes, and the
app is the side that does not draw them. On the device the same post reads `Inline  control` (the
inline checkbox element dropped, the two spaces left behind) and the list draws **plain bullets in
both flag states** (`170`, `171`) - which contradicts the retained golden, so the real cooked
structure differs from the fixture in a way the snapshot cannot show. What the app *does* send is
clean: the stored draft (read out of the app's own prefs) is `Inline [x] control` verbatim, and the
composer's serializer, the editor's markdown bridge and the cooked-HTML pipeline all keep the marker
in JVM probes. The missing piece is the post's cooked HTML, which the runner cannot fetch (no TLS
client on the emulator, the Mac's own egress to linux.do is blocked) - the owner has been asked to
paste it, and the fix lands with it.

#### A private test bed: a personal message to yourself

Discourse personal messages are readable only by their participants (`Guardian#can_see_topic?`), and a
message whose only recipient is yourself is allowed server-side - the web composer only shows a "did
you forget to add recipients?" hint, which is dismissible, and there is no "cannot message yourself"
validation. So a self-PM is a private place to post real server-cooked HTML for a device pass: create
it in the web UI (recipient = your own username), open it in the app by topic id (Settings -> 调试 ->
打开话题), and every reply the app posts there is cooked by the server and visible only to you. This
client ignores `archetype` and `TopicDetail` needs no category, so a PM should render as a normal
topic (not yet device-verified). Two dead ends, recorded so nobody re-treads them: unlisting is
staff/TL4-only *and* not private (anyone with the link can read it), and Discourse has no server-side
"cook this markdown" endpoint - the composer preview is client-side markdown-it, so a post has to
exist before its cooked HTML does.

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
   the two AI keys (the block row's and the format panel's) are dimmed and unclickable - a tap
   must do nothing and must not open anything - and inline-button/block-button editing is not
   wired.
8. The emoji key (`2026-09-25`): it is live now and opens the composer's own panel
   (`TopicReplyComposerEmojiPanel`, the same view the reply island shows) at the sheet's bottom
   edge, and the key flips to a keyboard glyph while the panel covers the bottom (the pin's
   `setEmojiOpened`). **Driven on the runner's emulator (2026-09-26, round 2, `slice-a-device/`):**
   the sheet opens from the composer's ArticleEditor entry (`50`, `56`); the panel appears at the
   sheet's bottom edge with the toolbar riding above it and `uiautomator`'s content description
   flipping `Emoji` -> `Keyboard` (`59-editor-emoji-panel.png`); a pick with the caret mid-word
   landed *at the caret* - the body read `ALPHAB :grinning_face: ETA`
   (`60-emoji-inserted-at-caret.png`); tapping the key again closed the panel and flipped the key
   back (`62`); dismiss and reopen kept the markup (`65`, `66`); and the sent reply posted and
   rendered (`69`..`73`). Still unverified on a device, with the reason:
   - **A selection replaced by the pick**: `adb` input could not create a text selection in the
     editor - long-press and double-tap both left a bare caret (`82`, `86`), and a press-hold-drag
     (`input swipe x y x+70 y 1500`) selected nothing either (`100`) - so the replace branch of
     `topicReplyComposerEmojiInsertionText` was never reached; the pick inserted at the caret
     (`ALPHAB :grinning_face: :grinning_face: ETA`, `101`), which is what a caret asks for.
   - **The keyboard coming back closing the panel** (the composer's own
     `topicReplyComposerShouldHideEmojiPanelForKeyboard`): this AVD's soft IME never shows
     (`dumpsys input_method` -> `mImeWindowVis=0`, `mInputShown=false`, the `InputMethod` window
     `GONE`; `ime reset` / `ime set` / `show_ime_with_hard_keyboard` did not revive it), so the
     precondition never occurred - the panel stayed up after a tap in the text (`61`). After the
     emulator was restarted the IME connects but paints nothing and supplies no usable inset, so the
     check stays inconclusive (`99`, and §7's round 3). The rule itself is the composer's, already
     exercised on the island.
   To check by hand, in a topic with the editor open:
   - Tap the emoji key: the panel slides in over the page's bottom, the keyboard goes away, the
     toolbar (format pill and send) rides above the panel, and the key shows a keyboard.
     Tapping the key again, or dismissing the sheet, takes the panel away and gives the keyboard
     back. Tapping into the text with the panel up brings the keyboard back and closes the panel
     (the composer's own `topicReplyComposerShouldHideEmojiPanelForKeyboard`), and the key opens it
     again from there.
   - The key is inert while the topic's emoji categories have not loaded: both this panel and the
     composer's island are built from the detail response's `emojiCategories`/`emojiCatalog`, and
     the composer's `showReplyComposerPopup` has the same `categories.isEmpty()` guard - a topic
     opened during an outage leaves both keys doing nothing, which is the expected shape, not a
     wiring gap.
   - Put the caret in the middle of a word and pick an emoji: the site's `:name:` markup lands
     *at the caret* (`topicReplyComposerEmojiInsertionText`, the composer's own insertion rule),
     not appended at the end - and with a selection, the pick replaces the selection.
   - Pick, then send: the post's `raw` carries the `:name:` form and the topic renders the emoji
     image (the panel's own search/category strip is the composer's view, unchanged).
   - The panel's height is the composer's `topicReplyComposerEmojiPanelHeightPx` with no keyboard
     inset, so on a tall page the page's top inset must not change (only the bottom moves).

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

## 12. The composer's attach slot (`b9a967d`) — partially driven (2026-09-20; the file route added `2026-09-26`)

What changed: the reply composer carries its own attach path. While the draft is empty the
send slot shows a paperclip instead of the send plane; a picked image or video is copied
into the cache, uploaded to `POST /uploads.json`, and its markdown lands in the draft on a
line of its own. With text already in the draft the same action is in the send button's
long-press menu (send is the other item there). Since `2026-09-26` the menu also carries
**Add file** - any other type, uploaded the same way, inserted as the site's own attachment
markdown (`[name|attachment](url) (12.3 KB)`), and the paperclip's *long press* opens that
menu so an empty draft can reach the file route too.

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
   "Add image or video" above "Add file" above "Send".
3. Insert with the caret mid-line: the image must start on a new line, and the caret must
   end on the line *after* the markdown, so typing continues below it.
4. Failure path: turn the network off and attach — the composer's red error line must
   report it and the draft must stay as it was (no broken markdown inserted).
5. Send the post: the site must render the image (edit the post on the web to see the
   `raw`), which is the same wire form the editor produces.
6. The file route: long-press the paperclip with an *empty* draft, choose "Add file", pick a
   `.zip` or a `.pdf` (the picker must be the document picker, not the photo picker), and the
   draft must gain `[name|attachment](https://linux.do/uploads/...) (12.3 KB)` - the size from
   the upload's `human_filesize`. Send it: the post must draw as the attachment row (the
   document golden's shape) and a tap on it must open the in-app browser at the uploads URL.
   This is the route the attachment-row device pass was blocked on (round 14, 2026-09-26: the
   photo picker was the only entry, so a `.zip` could not be attached at all).
7. Known gaps: only one file at a time, no caption, and no upload progress beyond the
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
instead of `expired_ServerLoggedOut`. Superseded 2026-10-04 (#37): the two-jar reconcile this
waited for was deleted; the one shared jar has its own verdict there.

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

## 17. The Discourse attachment row (`494f5d5`) — passed (2026-09-26, `emulator-5554`)

What changed: a cooked paragraph that is exactly one `a.attachment` anchor (plus the site's
` (12.3 KB)` text) is no longer a link run - it maps to the pin's `pageBlockDocument`, so the
flag-on body draws the document row: a circular file glyph, the file name in bold and the size
under it. The row's press goes through `didPressUrl`, the same delegate entry a link run uses,
which routes an uploads URL to the app's link entry (the in-app browser) - the site serves a PDF
inline and downloads everything else.

Verdict (2026-09-26, fifth pass, app built from the patch): the whole loop is closed on post #10,
which the app itself posted into the private test bed.
1. The composer's document route inserts `[1790436268563-test_file.zip|attachment](https://linuxdo-uploads.s3.ldstatic.com/original/...zip) (219 Bytes)`
   - absolute, not the protocol-relative form the site answered with - and the site cooks that into
   its attachment anchor (the cell's content description reads `10F，example_user。1790436268563-test_file.zip (219 Bytes)`).
2. The flag-on body draws the row; the flag-ON tap on the glyph centre opens the in-app browser at
   the CDN URL (blank body - a `.zip` has nothing to render), and the flag-OFF tap on the link line
   opens the same URL. The two states agree on the *action*.
3. The hold opens the app's link popup (URL chip + Share/Copy) and does **not** open the browser;
   Copy yields exactly the absolute URL.
4. The row shows no download ring (nothing is tracked through `FileLoader`), and no document
   options sheet appears.

Still open, and it is a product fork rather than a defect: the two flag states draw *different
shapes* for the same post - the rich body draws Telegram's file row, the block path draws the
site's own link line with the size text. The row was only ever mapped for the rich model; the site
itself draws a link with a paperclip icon, so the block path matches the site and the rich path
matches this app's Telegram machinery. Decide once and make the other path follow.

**The fork is settled: the row in both bodies (`74b1e28`, 2026-09-26).** The owner chose the row, so
the detection moved to the parser (`CookedPostBlock.Attachment`, emitted for a paragraph that is
exactly one `a.attachment` plus the site's optional size text) and `PostBodyBlockFactory` builds
`AttachmentBlock`, which mirrors `RichMessageLayout.RichDocumentBlock`'s URL-case geometry and
colours. The rich golden did not move in that refactor, and `TopicPostCellScreenshotTest.attachment`
is the block-path golden for the same fixture.

Verdict for the block-path row (2026-09-26, `emulator-5554`, app from `74b1e28`): post #10 draws the
row in **both** states now (the flag-off block grew 293px -> 381px and shows the disc, the bold name
and `219 Bytes`), the flag-off tap on the glyph centre opens the in-app browser at the CDN URL, and
the flag-off hold opens the link popup and copies the exact absolute URL without opening the browser.
Post #9, posted before the URL fix, also gained the row (its stored markdown keeps the old `//host`
href; the renderer resolves that form - proven by probe - so no separate pass is needed for it).

**Alignment follow-up (`7c8e492`, 2026-09-27).** The row used to be indented relative to the
paragraphs around it: the block path put the glyph at dp(16) against a dp(18) text column, and the
rich body put it at dp(16) *inside* its block padding (41px right of the column). Both now start at
the text column. Device verdict: the disc's left edge is x=48 in **both** states, with the body's
link ink at x=50/51, a list checkbox at x=49 and a paragraph's `I` at 52 in the same screenshots -
2-3px of ink edge, no indent, and the two bodies agree. The macOS goldens were re-recorded for the
pair (`attachment` and `richMessageAttachment`).

Notes for the next device pass on this surface: the row's press band is `y >= dp(10) && y <= dp(53)`
inside the block (a tap a few pixels above it is swallowed by the cell with an empty logcat - that
is what made post #9 look like a dead row), and the paperclip's long press can need a force-stop
after many rounds (a wedged process, not the gesture).

## 18. The checklist markup is the site's, not GitHub's (`de01924`) — passed (2026-09-26, `emulator-5554`)

What changed: the parser no longer looks for `<input type="checkbox">` in `li.task-list-item` (a
shape Discourse never emits - its core allowlister strips a raw `<input>`, and the checklist is a
bundled plugin). It reads the plugin's real output: an empty `span.chcklst-box`, with `checked` /
`checked permanent` as the state and `data-chk-src` as the toggle locator, in a paragraph, a list
item, a table cell or a blockquote alike; a box only counts as a list item's marker when it leads the
item or its first paragraph (the position the site's own client-side `li.has-checkbox` rule removes
the bullet in).

This is the item the device pass kept failing to settle. Rounds 9-10 recorded, on post #7 of the
private test bed (2953635), `Inline  control` - the double space where the box should be - and plain
bullets, with the checkbox code in the build; the cooked HTML was never the problem and neither was
the app's rendering, only the element it looked for.

Verdict (2026-09-26, `emulator-5554`, app built from the patch): post #7 now reads `Inline ☑ control`
with the filled box where the double space was, its first item is a checked box plus "done item" and
its second an empty box plus "todo item", both with **no bullet** - and the two flag states are
identical (`223-post7-8-flagON.png` / `221-post7-flagOFF-checklist.png`). Checks 1, 2 and 4 pass;
the mid-item case (3) and the no-toggle case (5) are covered by the retained golden rather than by
this pass.

To check, on a post with checklists (post #7 of 2953635 is the sample) and with the rich body flag on:
1. An inline `[x]`/`[ ]` draws as a box in the sentence - filled for checked, empty for open - with
   no leftover double space.
2. A list item that *leads* with a box draws as a checklist row: a box and the text, no bullet.
3. A box later in an item stays inline next to the bullet (the site's own client-side rule only
   removes the bullet for a leading box).
4. Both flag states agree; the block path and the rich path read the same parser.
5. Nothing here toggles: the box is display-only (`data-chk-src` is the site's toggle locator, and
   this client has no toggle call yet) - tapping it must not change the post.

## 19. Inline math renders with the tree's LaTeX renderer (`6c8f1b7`) — passed (2026-09-26, `emulator-5554`)

What changed: a cooked `<span class="math">x^2</span>` (the site's math plugin output - bare TeX, the
`$` delimiters are stripped at cook time) is no longer read as its text. The block path paints it
with `DiscourseMathSpan` (a bitmap from `org.telegram.ui.iv.Latex`, falling back to the TeX text when
a formula cannot be parsed) and the rich path emits the model's `textMath` run, which upstream's
renderer draws through the same renderer. Display math (`div.math`) still reads as its text.

Verdict (2026-09-26, `emulator-5554`): `$x^2 + y^2$` posted into the private test bed from the app's
composer (it became post #8) renders as a formula - italic x and y with raised `2`s, no caret - in
both flag states (`222-math-post-flagOFF.png` / `223-post7-8-flagON.png`), so checks 1 and 4 pass.
The cooked JSON could not be inspected from the device (the in-app browser has no session there and
the shell has no TLS client), so the markup shape rests on the plugin's own specs; the copy check (3)
and the unparsable-formula fallback (5) are covered by the renderer's code path rather than by this
pass. Display math is still open.

To check, on a post with math (`$x^2 + y^2$` on its own line, posted into the private test bed) and
in both flag states:
1. The formula draws as math - italic variables, the `2` raised as a superscript - not as the
   characters `x^2 + y^2`.
2. It sits on the text baseline (no clipping of a tall formula, no gap under a fraction); a formula
   the renderer cannot parse shows the TeX text instead of nothing.
3. Copy the line: the clipboard must carry the TeX source (`x^2 + y^2`), which is what the restored
   `ReplaceCopyTextSpannable` is for.
4. The same post with the rich body flag off must look the same - both paths share the parser and
   the renderer.
5. A formula that fails to parse must not leave a hole in the line: the fallback is the source text.

## 20. Display math renders too (`739300a`) — passed (2026-09-26, `emulator-5554`)

What changed: a cooked `div.math` (the site's `$$x^2$$` form, bare TeX between newlines) is no
longer read as its text. The parser emits `CookedPostBlock.Math`, the rich body maps it to the pin's
`pageBlockMath` (`RichMathBlock`), and the block path draws the new `MathBlock` - same size
(fontSize+4), same dp(8) padding, centred in the body, tinted with the body's text colour, and the
TeX as text when the renderer cannot parse it. `RichMathBlock`'s centring moved from the layout's
minimum width to the viewport so the two bodies agree.

Verdict (2026-09-26, `emulator-5554`, app from `739300a`): `$$x^2 + y^2$$` posted into the private
test bed from the app's composer became post #11, whose cell description reads `11F，example_user。x^2 + y^2`
with no `$` left - the `div.math` reading is right for the multi-line block, no plain-text fallback.
The formula's ink is 57px tall at x 461..616 in **both** states (viewport centre 540, formula centre
538.5) with ~58px and ~63px of space above and below, and the block is 319px tall in both; for scale,
post #8's *inline* `$x^2+y^2$` is 44px of ink at x 57..180. A tap on the formula's centre produced
nothing at all - empty logcat, no browser, and a before/after pixel diff whose only band is the
app-bar title marquee - so it is inert as intended (a bitmap: no link, no selection, no scroll).
Checks 1, 2 and 4 pass; the wide-formula scaling (3) and the font-size re-render (5) rest on the code
path rather than this pass. The macOS goldens agree with the Linux ones to the pixel (the formula at
x 394..548, centre 471, in both bodies).

To check, on a post with display math (`$$x^2 + y^2$$` on its own line, posted into the private test
bed) in both flag states:
1. The formula draws as math, centred in the body, larger than the inline form, with space above and
   below it (dp(8) each side).
2. The two states put it at the same place and the same size - a difference is a bug, not a taste.
3. A formula wider than the body scales down to fit here (the rich body would let it scroll); a
   formula the renderer cannot parse shows the TeX text instead of a hole.
4. Tapping the formula does nothing (it is a bitmap: no link, no selection, no scroll).
5. Changing the app's font size re-renders it at the new size; a theme switch only re-tints it.

## 21. The image viewer's start rect (`f78e2b3`) — passed (2026-09-27, `emulator-5554`)

What changed: the rich body's `PhotoViewer` place object handed `viewX/viewY` as
`host + layout origin + block offset - receiver.imageX`, but PhotoViewer adds the receiver's own draw
region to those itself (`fillClippingAnimationValues`: `viewX + drawRegion.left`), and the renderer's
`findMediaImageReceiver` hands back the *block's* offset while `imageX/imageY` are the image's offset
inside that block. The extra subtraction pulled the start rect left by twice the offset, so every
image but the first of a group animated in from off-screen left. A probe over a two-image collage
showed the second tile's place as `(-450, 0)` where its cell sits at x=450. `viewX/viewY` are now the
block's window position; the block path's own provider was already correct (it starts from the
image's window rect and subtracts the in-block offset, which PhotoViewer re-adds).

Verdict (2026-09-27, `emulator-5554`): a two-image collage posted from the app (post #12, red tile
x 54..539, blue tile x 546..1037) opens from the **second** tile with the viewer at "2 of 2". From a
screen recording, the first captured animation frame has the rect's right edge already at the tile's
right edge (1062 vs 1037) with the left edge expanding leftward from the tile's 546 - it grows out of
the tile, and no frame shows the rect starting at `x ≈ -546` (which is what the old formula would
have produced). Closing converges back onto the tile (f130 x 416..1048 towards 546..1037) and the
topic is restored with the collage intact; the first image still opens as before ("1 of 2"). Caveat
recorded by the pass: the emulator's encoder dropped frames (79 in ~4.4s), so the exact first frame
is not in the file - the evidence is the resting frame and the first animation frame that bracket it.

Still open, and a candidate rather than a defect: the place object's `parentView` is the cell here,
where upstream's article viewer (`ArticleViewer.PageBlocksPhotoViewerProvider`) passes the scrolling
list view and sets `clipTopAddition` to its header height. That affects only how the flying image is
clipped during the transition; nothing has been changed for it.

## 22. The rich table's border, the hard break, and the selection tint (`7dca3f3`, `782504d`, `de8cea2`) — two pass, one re-opened and fixed (2026-09-27, `emulator-5554`)

Three claims from one device round, each against the number the round before it recorded.

**The rich table's outer border** (`7dca3f3`) — passed. What changed: `RichTableBlock` draws both
horizontal-scroll `GradientClip` fades unconditionally, and each fade's opaque end sits on the
table's own edge (they are `DST_OUT`, so they erase). Telegram's bubble padding keeps most of that
overlap off the table; this host draws the layout at the column's origin with `padLeft = 0`, so the
left fade erased the left border outright and the right one erased the right border whenever it fell
inside the 12dp band. Both call sites now fade a side only while that side is actually clipped.

Verdict (2026-09-27, topic 2140307 post #1, `公益站列表`, y=1500 inside a data row): flag ON at rest
draws the left stroke at x 48..49 (224,224,224) with the internal dividers at x 384..385 and 953..954
(226) and **no** right stroke, because that side is clipped and the table overflows the column
(separators stop at x≈1020); dragged fully right, the right stroke appears at x 1030..1031 (224/233),
the dividers travel with the scroll (x 101..102, 313..314) and the left stroke is gone — that side is
clipped now. So each side's stroke exists exactly while that side is not clipped, which is the rule
the fix implements. Round 19's "no stroke at x=65" is falsified for the left (it is at x 48..49, the
table's edge) and explained for the right. Flag OFF is unchanged: left stroke 48..49 (224), cell fill
from x 50 (239..241), dividers 4px at 382..385 and 976..979, separators to the screen edge, right
border off-screen at rest.

**The hard break** (`782504d`) — passed in both states. Post 2933846/20: line 1 `我是RN` ink x=50 and
line 2 `有2台…` ink x=50 with the flag ON and OFF (round 19 measured 50 and 60). The cell description
lost the leading space too (`我是RN\n有2台…`, was `…\n 有2台…`), so the collapse is in the parsed
text, not only in the draw.

**The selection tint** — failed, then fixed (`de8cea2`). Round 20 measured exactly what round 19 had:
the handle pair on the pressed word (x 242..299 and 720..777) with the background inside the
selection at 252.7 against 253.9 outside, in both bodies. The loop over the cell's text layout blocks
(`782504d`) was only half of it: `ArticleTextSelectionHelper.draw` paints in the *layout's* own
coordinates — upstream's `ArticleViewer` cells translate the canvas to their text layout first — and
`TopicPostCell` called it at the cell's origin, so the tint was drawn over the author row, a whole
cell above the pressed paragraph. The handles looked right because the overlay draws them from the
block's own x/y. `dispatchDraw` now translates to each block's `getX()/getY()` before offering it,
the same origin the overlay uses.

The retained artifact is the new golden `TopicPostCellScreenshotTest.selectionHighlight` (both
themes): it selects offsets 4..21 of the second paragraph of a two-paragraph post, and the tint sits
on exactly those words. The golden pins the fix rather than restating it — with the translate removed
the same test fails by 1.15% and the rendered frame shows the blue rect over `Alice Chen`.

Verdict (2026-09-27, `emulator-5554`, app from `de8cea2`): passed, and the "before" is visible in the
previous round's own screenshots — files 346/349 of the round-20 pass carry a solid 49px band of the
tint colour at x 252..671, y 290..338, i.e. over the author row, while the handles sit at y 681..738
on the pressed line. (That pass sampled only the pressed line, so it reported the words as untinted
and missed the rect 390px above them; the verdict was right, the scan was blind.) After the fix, in a
paragraph that is not the first: flag ON gives a tint rect x 375..743, y 789..845 in the pure colour
(206,228,246) with the samples left (253.2) / inside (214.6,232.4,247.0) / right (253.3) and the
handles at x 356..413 and 658..715, y 844..901; flag OFF gives x 248..583, y 541..591 with left
(252.8,252.6,249.4) / inside (204.8,226.8,244.8) / right (254.9,254.3,252.5). Round 20 measured the
inside at 252.7/252.6, so the tint is now 40-48 levels darker with a blue cast (B−R +32 and +40), and
it is the same pure colour the golden carries. The author-row band (y 380..440) averages
(246.2,246.6,247.7) ON and (246.1,246.4,247.4) OFF — neutral, no stray tint — and a per-pixel scan for
the exact tint colour in y 300..440 finds 25 antialiasing pixels ON and none in the row band OFF.

The macOS set for the two new tests (`selectionHighlight`, `richMessageTable`, both themes) and the
moved `richMessageInlineCases` pair were recorded on the same run and agree with the Linux ones.

To check, on the same posts:
1. A table post (topic 2140307 post #1) with the flag ON: the outer left border draws at rest, the
   outer right border draws when the table is dragged to its end, and each one disappears only when
   its own side is clipped. A missing left border at rest is the defect this section fixed.
2. A post written with hard breaks (topic 2933846 post #20): every line starts at the same ink x,
   and the cell's spoken text has no leading space after the break.
3. Select text in a paragraph that is not the first: the tint covers the selected words, and nothing
   is tinted over the author row.

## 23. The rich-flag sweep (`de8cea2`) — first pass: spoiler, nested list and emoji clean, the rest pending (2026-09-27, `emulator-5554`)

The owner asked for a sweep of everything the experimental flag renders wrong, so the pass is: read
the same post with the flag ON and OFF and report only differences that are **not** by design. A
difference is a defect when content is missing, clipped, overlapping, mis-coloured, or when a
construct draws in one body and not the other; it is not a defect when it is one of the four accepted
ones — upstream's article rhythm (the owner's 2026-09-26 decision), the callout/onebox/poll/quote
cards staying in our own block tree and being stacked in place, the rich table being stretched to the
column width (upstream's `resolveWidth`), and the rich table's border fading only on a clipped side
(§22). The local half of the sweep is in `docs/render-model-alignment.md`: for the two long posts
(2790689 #1, 1779614 #1) the text each body actually draws is identical.

**A1 — spoiler (2215753, posts #1 and #3) — clean.** The cell descriptions are byte-identical between
the two states (`1F wya …`, `3F nikiski …`), the spoiler's black bar is y 584..1091 (h=508) in both
screenshots, post #3's image matches at dy=0 with a pixel difference of 0.0, and the full-frame row
profile differs only at the app-bar title marquee (y 88..130), the clock (y 36) and the composer
(y 2196..2287) — the content band y 200..2100 has a mean difference of 0.008/255.

**A2 — list nested pic (2214964, post #1) — clean, rhythm only.** Both links, both headings, every
paragraph and all five list items draw in both states with the same colours; nothing is missing,
clipped or overlapping in the visible portion. The differences are the accepted ones: bigger and
bolder 前言/技术栈 headings, tighter list rows (one more item fits the viewport) and looser
paragraphs.

**Emoji — images in both bodies.** The risk the local sweep named (a shortcode drawn as text where the
image should be) does not reproduce on the device: the table topic's opening post carries standard
and custom emoji, and the same text cropped from the flag-OFF and flag-ON captures shows 😅/🐶/🎉 and
the tieba/bili images drawn as images in both, with no shortcode rendered as text in either state.

**Not reached in this pass:** the rest of A (投票 #2229268 and #2537621, emoji title #2503776, 很多回复
#1993635, 又来修一下 CC #2336669, topic list #2310531), B (the visual half of the two long posts) and C.
The second pass is navigation-bound, not discovery-bound: the Settings → 渲染测试帖 rows and the
capture recipe are recorded in the pass's log.

### Second pass (2026-09-27, same emulator) — five more A topics clean, 2790689 #1 clean

**投票 #2229268 and #2537621 — clean.** Both polls draw in both bodies with the same bars,
percentages, counts and controls (72% 计算机技术岗 18 / 8% 计算机售前岗 2 / 20% 非计算机相关专业 5, 查看投票,
then the second poll at 21% 完全不给AI做 4; and 84% Codex 142, 8% Claude Code 14, 1% Opencode 2, 3% Pi 5,
0% Aider, 0% Cursor 1, 4% 其他 6 with the checkmark on 其他 and 已投票/查看投票/撤销投票), and the cell
descriptions are identical between the states. The poll stays in our block tree in both bodies, so
this is the expected result rather than a surprise.

**emoji title #2503776 — clean.** Both states draw the two custom photos, the text with the 😂 as an
image, the 原文 label and the same link card; descriptions identical. Observation, not a defect by the
sweep's criteria: the rich body draws those two images ~10% smaller, scaled to the column - the same
width class as the table and the code block below, nothing cropped.

**又来修一下 CC #2336669 — clean.** The same code block with its `Bash(` language label, the same
wrapped line and `└ Waiting…` continuation in both; descriptions identical. Observation: the rich code
block is wider (full column), and the long line clips at the panel edge the same way in both.

**topic list #2310531 — clean.** Descriptions identical (an image post and a text post); the pixel
difference is a scroll offset, no construct at risk.

**B — 2790689 #1 (four ON screens, five OFF screens, covering 节目 4/5/6) — nothing clipped,
overlapping or missing.** Code blocks draw in both with the grey panel, monospace text and the
language label, and the same long line clips at the panel edge in both; the scrollable container and
its rendered text are present in both; footnotes draw in both (heading, inline marker and the
`1. 这是脚注的详细说明内容` block); headings and lists are present in both with the accepted rhythm.
Gap left in the pair: the *expanded* details widget was only captured with the flag ON (the OFF
screens landed on 节目 5/6); its code block is in both.

**Still not reached:** 很多回复 #1993635 and 1779614 #1 (the 15 KB post), plus the OFF capture of that
expanded details widget. They are queued as the sweep's tail.

### Third pass, the tail (2026-09-27, same emulator) — the sweep reads as finished

**很多回复 #1993635 — clean.** Read at the topic's end (posts #683-688): the descriptions overlap
exactly between the states (684F 卋罖 …, 685F http …, 688F Jasonlin …), plain text, no construct at risk.

**1779614 #1 — clean, four screens per state.** The headings (3s内报403…, 基础要点, 什么花都可以改出来…,
必须要用 Sonnet/Opus 4+…), the quote card, the related-topics list, the Claude Code image, the
`~/.claude.json` and `~/.claude/settings.json` code blocks with their grey panels and monospace text,
the ✓ blocks, the `DISABLE_*`/`HTTP_PROXY`/`CLAUDE_*` lists and the `settings.anyrouter.json` block all
draw in both bodies; nothing clipped, overlapping or missing an image or caption in either. The BBCode
colour/size ranges are dropped in both bodies, as recorded above.

**The expanded details widget — same widget in both bodies.** The OFF capture has it at y≈1120..1180
and pairs with the ON one: both draw `⌄ 这是HTML5原生的details折叠标题` with the same chevron and text,
the OFF title at x≈130 and the ON at x≈190 — indent plus rhythm, i.e. the accepted set.

**The sweep's verdict:** across rounds 22-24, every targeted post of all 13 topics in the 渲染测试帖 list
read the same in both bodies apart from the four accepted difference classes; no construct is missing,
clipped, overlapping or mis-coloured, and no image or caption draws in one body and not the other. The
two observations the pass raised are the width model rather than defects - the rich body scales an
image to the column (~10% smaller in 2503776, whole frame, nothing cropped) and its code block is
full-column wide (2336669, the long line clips identically in both) - the same class as the table's
`resolveWidth`. Making media or code pixel-identical across the two bodies would be a product decision,
not a flag bug.

What is left for the flag is therefore not a rendering defect but the switch itself: the rich body is
still off by default, and the sweep was the last open item on the flag's roll-out list.

## 24. The selection action bar (`7081fbf`) — items are upstream's, and the bar comes up from the menu too (2026-09-27, `emulator-5554`)

The owner asked whether the bar that pops up after selecting text matches Telegram. It is upstream's
own widget rather than a port: `ArticleTextSelectionHelper.showActions()` builds Telegram's
`FloatingToolbar` + `FloatingActionMode` (`STYLE_THEME`) on API ≥ M, hosted on the upstream
`TextSelectionOverlay` the fragment mounts over the list, and the items come from upstream's
`textSelectActionCallback` (Copy, Quote, Translate, Cut, Paste, Select all, with per-item visibility).

What the device reads, and what is by design:

- **Copy / Quote / Translate** is the correct item set. Quote is visible because the helper answers
  `canShowQuote()` through upstream's hook (the pin's `quoteText()` carries one `// NativeDo:` line so
  a non-chat view reaches `onQuoteClick`); Translate because the fragment wires `setOnTranslate` to
  Google Translate with the selected text; Select all is hidden because the article helper sets
  `multiselect = true` and upstream's rule hides it in that mode (Telegram's instant view has none
  either); Cut/Paste stay hidden because a post body is not editable. The first read of this bar
  expected Translate hidden and Select all shown — that expectation was wrong, not the device.
- **Style**: a white rounded pill above the selection with 12sp theme-coloured labels; no measurable
  shadow on a white page, which is what Telegram's floating toolbar looks like there.

**The defect it found, and the fix.** The bar was reachable only by long-press-and-drag: the cell
menu's *Select text* produced the tint and the handles with no bar. The cause is upstream's one-touch
selection — `startSelectionRunnable` ends with `movingHandle = true`, and `showActions()` refuses to
show while a handle is moving because a gesture is expected to end it, which a menu-driven selection
never does. `finishOneTouchSelection()` is upstream's own API for exactly that, and upstream's editor
calls it right after its double-tap one-touch selection (`RichEditorListView`). `TopicPostCell
.startTextSelectionAt` now calls it when the selection started.

Verdict after the fix (`7081fbf`, post 1779614 #1): the bar appears immediately from the menu route in
both flag states (label rows measured: Copy 79px, Quote 90-93px, Translate 138-142px, the same three
items in the same order, x differing only by where the selected word sits in each body); Copy puts
exactly the selected text on the clipboard; Quote opens the composer with the reply-to chip and
`[quote="Haleclipse, post:1, topic:1779614"]你就等[/quote]`, i.e. `discourseQuoteMarkup` of the
selection; tapping outside dismisses the bar and the selection; the flag-ON read is clean (the first
attempt ended in the app's black state, the re-run did not).

One item is still open: whether the bar *follows* a handle drag. One read moved the selection
(`tint 540..632 -> 500..632`) while the bar stayed at the same y — but the code repositions through
`FloatingActionMode.invalidateContentRect()` on every drag MOVE and re-shows on ACTION_UP, so the
ambiguous read is likelier a synthetic-gesture artifact than a defect; a clean touch-hold-then-slow-drag
read is queued to settle it.

That clean read (round 27, flag OFF, post 1779614 #1, a 0.9s hold then slow MOVEs with a capture
between each) settled three of the four points the code predicts: the bar disappears on handle-down
with the magnifier over the handle, it comes back on release, and its **x follows the selection's x
range** (its centre moved +194px against the selection's +191px). The fourth reads negative: dragging
the *start* handle down one line (heading y≈555..595 -> the `L站都快成Any牌路由器反馈地了` line at
y≈660..698) left the bar's label row at y 432, identical within 1px — where the code says the rect is
anchored to the start line, so it should have followed. The read could not separate "the bar is
block-anchored" from "the y is not recomputed after a drag" (a fresh selection low in the post was
never captured: one attempt opened the image viewer, another landed where the long-press menu did not
come up), so the control read is queued: a fresh selection low in the post, then a start-handle drag
from it, then an end-handle drag from it, with the pairs (bar label-row y, selected line y) recorded.
If the control shows a fresh low selection also lands at y≈430, the bar is pinned and the cause is in
this host's wiring rather than upstream's geometry.

### The control read (round 28) — two bars, not one

The control came back with something better than a y verdict: the bar that appears for a *paragraph*
selection is not the same bar as the one for a *heading* selection.

| case | selected line y | bar label row y | gap | items |
|---|---|---|---|---|
| fresh selection, heading line | 555..595 | 424 | 131px | Copy / Quote / Translate |
| fresh selection, paragraph line | 512..600 | ~100 (over the app bar) | ~412px | Copy / Translate / Select all |
| start-handle drag down one line (from the heading) | 660..698 | 432 | 228px | Copy / Quote / Translate |
| that paragraph selection after a 200px scroll | moved with the scroll | ~100 (unchanged) | — | Copy / Translate / Select all |

So the y does not track the start line, the scroll, or a constant gap for the paragraph case — and the
item set differs too. The one factor that separates the two cases in every capture is the state of the
reply composer: every paragraph-case capture had the composer's field focused (keyboard up, a reply chip
in the draft), every heading-case capture had it collapsed. The first read of the paragraph bar also
called its labels *larger* than the heading bar's; a same-scale side-by-side of the two "Copy" labels
(482-label-compare) shows they match in size and weight, so that observation was an artefact of a
resized crop and is withdrawn — the font is not the tell.

What is *ours* in the paragraph case is the selection itself: the tint over `早期版本` is exactly
(206,228,246) — Telegram's `key_chat_inTextSelectionHighlight` (0x5062A9E3, 31% alpha) over white —
and its ends are rounded (the helper's `CornerPathEffect`, dp(6)), where a platform highlight would be
square. Our helper cannot produce Copy / Translate / Select all, though: its constructor sets
`multiselect = true` and upstream hides Select all in that mode, while `canShowQuote()` is true for a
`TopicPostCell`. A local probe also rules out the other candidate cause: `fillTextLayoutBlocks` hands
the helper correct per-block x/y (42 blocks on that post, e.g. block[38] at y=10295 for the line that
was selected), so the geometry is not what puts the paragraph bar at the top.

The read that settles which bar is which is queued: for the paragraph case and the heading case in the
same session, the label-row height, whether the droplets are visible, whether the tint's end is rounded,
and whether the composer's field is focused — plus the same on a paragraph of a different post, and a
re-selection of the heading after a paragraph selection to separate "position" from "session order".
Round 29 answered the label-height half (the two are the same size, so no tell there) and could not
land the decisive case — a paragraph selection with the composer *unfocused* — because every attempt to
unfocus either navigated away or cleared the selection first. The clean recipe for it (fresh launch,
never touch the composer, scroll to a paragraph mid-post, long-press there) is queued as the last read
on this bar. If the paragraph case then gives Copy/Quote/Translate with droplets, both bars are ours and
the difference is environmental; if it still gives Copy/Translate/Select all, there are two bars and the
second one is somewhere in this host's wiring.

Round 30 ran that recipe and answered it: with the composer untouched and collapsed throughout, a
*heading* selection still gives Copy/Quote/Translate anchored above the line while a *paragraph/list
item* selection (post #6) gives Copy/Translate/Select all pinned at the screen top (labels y≈76..170
against a selected line at y 1107..1219) — and in both cases the tint's caps are rounded and the
droplets are there, so the selection itself is the helper's either way. So it is one helper behaving
differently by block, not two widgets, and the composer's focus is not the factor.

**The cause is a missing hop of upstream's host contract, and it is fixed (`dc2b18c`).** The article
helper resolves the selected cell through `layoutManager`: `ArticleViewer` sets
`textSelectionHelper.layoutManager = pages[0].layoutManager` and `RichEditorListView` does the same, and
this host never did. Without it, `pickStartView()`/`pickEndView()` take the `parentView.getChildAt`
branch, which indexes *attached children* with an *adapter* position; for a selection in a cell whose
adapter position is past the attached child count the pick returns null. Everything the device read
follows from that one null: `canShowQuote()` false (no Quote item), the Select-all guard skipped
because it is gated on `selectedView != null` (so Select all keeps its default and shows), and
`onGetContentRect` answering y=1 (so the bar anchors at the top of the screen instead of above the
selected line). A probe over a scrolled list reproduced the resolution exactly — null without the layout
manager, the target cell with it — and the wiring now happens in
`TopicPostTextSelectionHelper.setParentView`. The retained gate is
`TopicPostTextSelectionHelperTest`: `aSelectionResolvesToItsOwnCellWhenTheAdapterPositionIsPastTheChildCount`
and `withoutTheLayoutManagerTheSameSelectionResolvesToNothing`. The device re-read of the bar's items
and anchor after the fix is queued for the next device pass.

**The fix reads as passing on device (`dc2b18c`, post #6 with the flag OFF — the case that failed in
round 30):** the menu is Copy / Quote / Translate with the bar just above the selected line (bar
y 826..950, tint y 958..1006, an 8px gap), the tint is (206,228,246) against white outside with rounded
caps, and the droplets are at both ends. Two items the same read turned up, both open:

- **Flag ON: Select text painted nothing in that read** — the menu opened, but no tint, droplets or bar
  appeared at 0.6s, 1.5s or 3s, and Back then left the app in the black/frozen frame first seen in
  round 25 (its 424). Caveat before this is treated as a defect: the emulator's installed build changed
  under the pass (a parallel session installed `0.1.0-debug-20260928-092909` at 09:30:07 over the build
  the pass was using), and round 26 had read the ON case working on `7081fbf`, so the ON failure needs a
  clean re-read on a *recorded* build id before it is chased. The successor runner is queued for exactly
  that read (plus a logcat scan for an exception).
- **A one-touch selection inside a code block put the droplets at the block's bottom corners** rather
  than at the selected word's ends (503a; measured by the successor runner: x 21..78 and 1032..1079,
  y 1876..1933 — 303px below the tint, 1262px from the word). **Cause found and fixed (`2b435ae`):**
  `ArticleTextSelectionHelper` reads a layout block's `selectionBounds` in exactly two places, both in
  the overlay that draws the handles, where the handle goes to `selectionBounds.right/left` and
  `.bottom` instead of the selection's own line — the tint ignores the field entirely. `CodeBlock` was
  passing the *block's* rect (`x, y, x + viewportWidth, y + measuredHeight`), and `TableBlock` the same
  block-wide rect, so a selection in a table cell put its handles at the table's corners too. Both now
  pass none; upstream's article table passes a *cell's* bounds, and our layout blocks already carry
  cell-local x/y, so null is the same thing. The device re-read of both is queued with the ON-body
  selection round.

**The ON-body selection failure, as far as it is pinned:** a long press on post #1's body paragraph in
the ON body opens the post menu, but *Select text* then paints nothing (two reproductions), while the
same post and coordinates in the OFF body select normally; and a long press on the same post's *heading*
in the ON body does not even open the menu. Later single-paragraph posts are fine in the ON body, so the
case is "ON + this multi-block opening post", not the flag as a whole. A local probe rules out the
geometry: building that post's cell in both bodies and calling `startTextSelectionAt(x, y)` at the same
coordinates starts a selection in both (a valid `maybeTextIndex`, a plausible character offset), so the
failure lives in the long-press → menu → *Select text* path rather than in the hit test. The runner's
next round carries a three-line diagnostic build (a log at the long press, two around `trySelect`) and
will report the coordinates, `isSelectable` and the resulting selection state for the ON case, the OFF
control and the heading case.

## 25. A press in the rich body resolved to the wrong block (`c502d72`) — root cause found, fix in, device re-read queued (2026-09-28)

The ON-body failure §24 parked ("Select text paints nothing" on post 1779614 #1's paragraph, while the
same post's heading and every single-paragraph reply were fine) is a hit test, not a drawing bug, and the
diagnostic instrumented build that settled it is `sel-diagnostic2` (temporary logs only, never committed:
`fillTextLayoutBlocks`' listing plus the helper's `startViewChildPosition`/offsets, logged at select time
and at draw time).

What the device and a local `Robolectric` probe both read (topic 1779614 #1, cell-local press at (200, 385),
which is inside the first paragraph's text block at y=356, len=82):

- **the listing is stable** — 63 blocks at select time and at draw time with the same per-index `y`/`textLen`
  (`0=197/20`, `1=356/82`, `52=12129/155`); only the wrapper objects' identity changes, which the helper never
  reads;
- **the offsets fit their block** (child 52 is 155 chars long and carries 3..11), so nothing was clamped away;
- **the resolved child is the wrong one**: the helper created the selection on child **52** — a block 11.7k px
  further down the same cell — so the tint, the droplets and the bar were all drawn off-screen. The flag-off
  body resolves the same press to child 1 and selects `L站都快成Any牌路由器反馈地了`.

**Cause.** `RichMessageLayout.reposition()` places a top-level block's text with
`placeTexts(x, y, i)` — **`i` is that block's index** — and every `Text` keeps it in `row`
(`Text.setRow(row)`). `ArticleTextSelectionHelper.findClosestLayoutIndex` reads any `row != 0` as
"several text blocks beside each other in one row" and, when the press is within `dp(24)` of the nearest
block, replaces its answer with the index of the last block in that row whose x-range covers the press x.
In a single-column body that row is a *group id*: a local reproduction of the same post shows the blocks'
rows are `0, 2, 3, 5, 6, 7 …` and that child 1 shares row 2 with children 17, 46 and 52 — the helper's own
resolver returns 52 for the press, reproducibly, with the only containing block being child 1. Flag-off
bodies are unaffected because our own `TextLayoutBlock.getRow()` answers 0.

**Fix.** The host's wrapper (`RichMessageBody.OffsetTextLayoutBlock`) no longer forwards the renderer's row
stamp: it keeps a row only where the blocks sharing it really are side by side — i.e. when no two of them
overlap horizontally, which is a rich table's row of cells and is exactly the case the x-pick exists for —
and answers 0 otherwise. With it, the same probe resolves the paragraph press to child 1 and selects the
pressed line's text; flag-off behaviour is untouched.

This also explains the "two bars" cluster §24 parked (rounds 28-30): a paragraph/list selection and a
heading selection produced different item sets and different anchors because only the heading's block (row
0) was resolved correctly, so the paragraph cases fed the bar a bogus selection.

**Verdict on the device (`c502d72`, 2026-09-28, `emulator-5554`, post 1779614 #1).** The ON body's
paragraph now reads exactly like the flag-off control: pressing the paragraph's first line tints that line
(tint y 647..700 against the line's ink 655..698, x 48..644), the droplets sit at the selection's two ends
(x 0..47 and 645..702 at y 701..758, i.e. beyond the tint's own ends at 48/644) and the bar comes up 3px
above the line with Copy 80px / Quote 94px / Translate 143px — the same three items, order and widths as
flag-off (which measured tint y 668..724, droplets 0..47/645..702, the same labels). The heading read is
unchanged, and a second ON read of the same paragraph reproduced the first. The off-screen selection the
diagnostic reported (child 52 at y≈12129 for a press on child 1 at y=356) is gone.

**The code-block droplets (`2b435ae`) also pass on the same build:** a press inside the
`settings.aliyun.json` block now tints its line (y 833..880, x 151..904) with the droplets at x 151..208 and
847..904 — the tint's own ends — where the pre-fix read had them at the block's corners (x 21..78 and
1032..1079, y 1082..1139).

**Queued:** a table cell's selection, because the host keeps the renderer's row stamps for blocks that are
genuinely side by side (a rich table's row of cells) and that path has no device read yet. The topic the
table read used before (2140307) now opens as prose — its author moved the list to a new topic, which its
opening post links to — so the read follows that link.

**Still open, unrelated to this fix:** backing out of an ON-body selection put the app in the black/frozen
frame once on `c502d72` (the §24 round-25 symptom: the related-topics strip floating on black); a second
Back recovered and navigation stayed normal. Seen once, not chased.

## 26. A rich table's cells cannot be selected (`c502d72`) — one pass, one open gap (2026-09-28, `emulator-5554`)

The §25 fix left one read queued ("the host keeps the renderer's row stamps for blocks that are genuinely
beside each other - a rich table's row of cells - and that path has no device read yet"). It is now read in
both flag states, on the topic the table reads have used all along (2140307, whose opening post does carry
its table: the round that reported it gone had probed for one cell fill colour and missed the others).

**Flag off (our `TableBlock`) — passed.** A press on the `Any Router` cell of the 公益站名 column
(x 48..364, ink y 1722..1757) opens the cell menu as on any other body, and *Select text* tints that cell
only (y 1713..1761, x 107..299 — no smear into the 地址 column) with the droplets at the tint's own ends
(x 88..145 and 264..321). That is also the device confirmation `2b435ae` was still owed for tables: before
it, the droplets went to the *table's* corners because the block passed its own rect as
`selectionBounds`, the one field the handle placement reads.

**Flag on (the rich table) — the cells cannot be selected at all, and the cause is the renderer's cell
geometry.** Two long presses (1.5s, 2.5s) on the same cell did nothing: no menu, no tint, no droplets, the
table not scrolling either. A local probe of the same shape (a two-row rich table, first body block) names
the mechanism:

- the three cells of a row all report **the same y-band** and the renderer stamps the table's cells with the
  block index, which for a table that is the body's *first* block is `0` — the value
  `ArticleTextSelectionHelper.findClosestLayoutIndex` reads as "not a row". It scans its listing from the
  end and breaks on the first block whose y-range contains the press, so a press in `Any Router` resolves to
  the row's **last** cell (`maybeTextIndex=5`, `可用`, for a press on cell 3): the selection is created on a
  different cell than the finger's, and with a two-character target it does not even start;
- and a row stamp would not be enough by itself: every rich table cell's layout block reports the **table's**
  width (x-ranges 30..636, 64..670, 111..717 all contain the press x), so the helper's by-x pick returns the
  rightmost cell whatever the row says. That geometry comes from upstream's `RichTableBlock`, in the vendored
  file, not from this host's wiring.

**Why it is not this round's fix.** §25's rule (drop the renderer's row where the blocks are stacked, keep it
where they are beside each other) is what the text case needed and it changes nothing for these cells. Making
a rich table selectable means giving its cells a usable row *and* a cell-sized x-range, i.e. a deliberate
change in the vendored renderer under the overlay/forced-exact workflow, not a host wiring tweak. Recorded as
an open gap for the owner to schedule; the flag-off table selects correctly today.

## 27. The viewer opens the un-resized file, and a loading image can be cancelled (`e53a295`, `23e24a7`) — passed (2026-09-28, `emulator-5554`)

Both changes come from the owner's read of the app's images ("打开后还是很模糊", "看不到 telegram 那样的 loading 按钮").

**The viewer's file (`e53a295`) — passed, with the cache as the proof.** A Discourse `<img>` carries
the display-sized resize in `src` and the un-resized copy as the second `srcset` candidate
(`…/original/4X/<hash>.jpeg` beside `…/optimized/…_2_690x326.jpeg`), and the viewer used to be handed
`src`, so opening an image could never be sharper than the body. `resolvePostImageViewerUrl` now prefers
the widest `srcset` candidate, then an anchor that stays on the file's own host (an off-site anchor must
not hijack the body's own upload - a cell-level test pins that), then `src`; the gallery, `ImageBlock`
and the rich body's `pageBlockPhoto` all use it, and the body itself keeps downloading `src`.

The device pass opened topic 2790689's third post (the 690x326 screenshot with no anchor - the case that
was worst before) and read the app's own image cache afterwards: **two files, byte-for-byte the ones the
URLs serve** - `…jpeg` at **96,856 B / 994x471** (what the viewer now fetches) beside
**49,677 B / 690x326** (what the body still fetches). At 1x the image region's Laplacian variance rises
641.6 -> 846.7 (+32%). The honest limit of the read: at the PhotoViewer's maximum double-tap zoom the
before/after frames are identical (that zoom is past both files' native resolution, and the emulator
cannot be given a two-finger gesture, so no intermediate 2x was read); the un-zoomed and modest-zoom
cases are where the change lives, and the side-by-side of the two cache files shows it directly.
Flag-on draws the same image full-column and opens the same viewer, with no jank; the rich body's own
loading state was not captured (the file was already in memory), which stays a small open gap.

**The loading button (`23e24a7`) — passed, all four steps.** A loading body image used to draw nothing in
its progress ring (`ICON_EMPTY` with a binary progress). It now carries Telegram's own media-button
states: the cross in the ring while the file is coming in, the download arrow after the reader
cancels, a tap on the button cancelling (`ImageReceiver.cancelLoadImage`) or retrying, and a tap
anywhere else still opening the viewer. Read under `adb emu network speed gsm` so the window was
catchable (restored to `full` and airplane mode off afterwards):

1. a throttled image shows the grey disc with a **white cross** and a moving dot - indeterminate, as
   expected for a URL load (Telegram's `ImageLoader` reports no percentage for those);
2. tapping the cross turns it into the **download arrow** immediately, and three seconds later it is
   still the arrow with the placeholder unmoved - nothing resumed behind the reader's back;
3. tapping the arrow brings the cross back and the image then loads normally;
4. tapping the image away from the button (200, 1600) still opens the viewer, so the cancel logic does
   not eat the block's own gesture.

The opening sequence has no black frame in either build (mean 212 -> 19: the viewer's black background
plus a dark screenshot), so nothing regressed there.

## 28. Detail content passes under the chrome again (`70a1d96`) — passed (2026-09-28, `emulator-5554`)

The owner read the topic action bar as carrying a background layer Telegram does not have. The layer was
`3d97b24`'s `clipBounds` on the list ("protect detail content from fixed chrome"): rows were cut flat at
the chrome line and the strip behind the bar painted the page colour instead of the messages. The
fragment's own comment for this chrome says the opposite - ChatActivity's glass ActionBar occupies the
status bar and content draws underneath the entire top chrome - and the list already carries
`clipToPadding = false` plus the fade zone at the chrome line, so the clip was the only thing hiding it.
`70a1d96` removes the clip (the two call sites, the host layout's clip machinery and its test) and leaves
the glass bar and the fade untouched.

The device read used identical positioning for both builds (topic 2790689 scrolled so post 2's cell top
sits at screen y 105; the uiautomator dumps agree in both: `[0,105][1080,620] 2F …`):

1. **The row under the chrome** - before: cut flat at the chrome's bottom edge (the avatar's lower rim and
   `@scottalight · #2 · 08-21 23:05` sliced, page colour above); after: the row passes under the bar and
   fades out through the glass, no cut. Side by side in the pass's `2004-compare`.
2. **The title and back chips stay legible** over the content in every frame; the body is only a faint
   ghost behind the glass.
3. **Keyboard pan** - the composer's focus state (`mImeWindowVis=3`, `mInputShown=true`) scrolls the list
   as before with content still passing under the chrome. This AVD's Gboard never renders (known since the
   project's round 2), so only the transition's end state could be judged, and it is no worse than with
   the clip.
4. **Page-down buttons and the floating date** - the two FABs are whole at mid-scroll, near the top only
   "go to bottom" shows, both show at the end, no clipping artefacts; the date capsule is drawn below the
   chrome and was never inside the clip's scope.

**One judgement call handed to the owner:** while scrolling *fast*, a dense text block or a dark image
sweeping through the chip band reads slightly busy for a frame or two. The pass does not recommend
bringing the clip back; the fallback if the owner minds it is to clip only while the IME pan runs, which
keeps the resting state as it is now.

### §26 follow-up: the rows and the boxes (`4b8a4a5`) — fixed, device read queued (2026-09-28)

The gap §26 named ("the cells cannot be selected at all") needed two changes, and the owner's question
about the two `createTextLayout` calls is what pinned the first one: `TableLayout.java` is **byte-clean
against the pin** (`vendor-diff`), so both calls are upstream's own two-pass measure. Pass one builds
every cell at a provisional `maxCellWidth` (`width / 1.5`, or half the table for two columns - the 606 px
the probe read for a three-column table); pass two re-builds at the exact cell width but only for cells
that came out multi-line, or single-line and centred/right-aligned. A single-line left-aligned cell keeps
the wide box - upstream never needed it narrowed because the glyphs render identically - which is exactly
why the helper's by-x pick could not tell the columns apart.

- **`TableLayout`** now also runs the exact pass when the box is not the cell's own width. The guard
  flags it as forced-exact `[drawing, measurement]`; the named gate (`:app:verifyPaparazziDebug`) is green
  and every table golden is unchanged, i.e. the drawing output is byte-identical - the only visible
  difference is that a long single-line cell now wraps instead of being clipped.
- **`RichMessageBody`** derives the helper's row stamps from the geometry (same renderer stamp group, same
  y-band, no horizontal overlap - a table's row of cells) rather than forwarding the renderer's top-level
  block index. The cells previously answered 0, so the helper's by-x pick never ran at all; the §25 case
  (stacked blocks sharing a stamp) still answers 0 by construction, and the goldens for it are unchanged.

Local probe, same coordinates as §26's: a press on the first column resolved to the third column
(`maybeTextIndex=5`, `可用`) before; it now resolves to the pressed cell (`3`) and selects `Any Router`.
**Queued:** the device read on this build - press a rich table's first column and confirm the tint lands
there, a long cell wraps rather than clipping, and the table's own look is unchanged next to the flag-off
body's table.

### §26 follow-up, the device read (`bc1eceb`): the geometry half is in, the touch routing is the other half

The read on `4b8a4a5`+`bc1eceb` says two things, and the second one is the point:

- **Nothing regressed.** The rich table's geometry is identical to the pre-fix build (row separators at
  1348/1442/1538/1634/1730/1826/1965/2104 and column separators at 48/384/953 in both), no cell is
  clipped that was not before, and the flag-off body's own table still selects its left column exactly as
  §26 measured it (tint y 1713..1761, x 107..299; droplets x 88..145 / 264..321).
- **But the cells still cannot be reached by a long press**: presses inside the rich table - left column,
  header, another row, and a long-press-and-drag - produce no menu and no tint, while a press just left of
  the table (x 20) and a press in the paragraph above it both open the menu. So the blocker the geometry
  fix does not touch is **touch routing**: `TopicPostCell.onTouchEvent` dispatches the DOWN into the body
  block first, and when the block consumes it (`RichMessageBody.onTouch` -> the rich renderer, which owns
  the table's own gestures) the cell calls `cancelCheckLongPress()` - the menu never opens and the
  selection overlay never sees a landable point. The same read shows a selection started in the paragraph
  above cannot be dragged into the table: its block carries no reachable text position.

The geometry itself is proven locally (a press on the first column resolved to the third column before,
to the pressed cell after - see the §26 follow-up above); what remains is to let the cell keep its long
press when the rich body consumes the DOWN, i.e. to give the rich body the same "the cell owns the long
press" treatment the paragraph/heading blocks already have, and re-read the three columns on the device.

Also noted, **pre-existing and not from this change**: in the flag-off body the address column renders
empty for the rows sampled (the same in §26's older captures), which is a separate lookup to make another
day.

**Device verdict (`f18e6cc`, 2026-09-28, `emulator-5554`):** the geometry and the routing together make the rich
table selectable, and nothing else moved.

- **Cells**: a long press on each column of a data row tints that column's own cell against the separators
  at x 48/384/953 - left `x 112..295`, middle `379..537`, right `948..1060`, all at `y 1465..1517` - with
  the droplets at the tint's ends. The three header cells select the same way (`y 1369..1420`). The menu
  that comes up first is the post menu (Reply / Select text / Copy link / Add bookmark), i.e. the cell's own
  long press, and *Select text* then lands the selection in that cell - which is the same two-step the
  flag-off body has always had, so the item set was not compared item-by-item against the pre-fix build.
- **Regressions**: a drag across the table still scrolls it horizontally (the x 953 separator moved to 583,
  Δ370, both directions); tapping a URL in the address column still opens it (Custom Tab, anyrouter.top
  /login); the paragraph above the table and the margin to its left still open the menu and still start a
  selection.
- **Bonus**: a selection started in the paragraph above can now be dragged into the table (its end handle
  pulls the tint onto the table's rows), which the previous build could not do at all.

The flag-off address column note from the previous read still stands (empty for the rows sampled, older than
this work).

## 29. A loading rich image shows its button (`f96e975`) — passed (2026-09-28, `emulator-5554`)

The renderer keeps its own `RadialProgress2` for chat-shaped photos (a `TLRPC.Photo` with file progress) and
never draws it for a photo that came from a URL, so a rich body that was still fetching an image showed a
plain grey rectangle - no ring and no way to stop it - while the block path had both (§27). `RichMessageBody`
now draws the same button over every media block whose receiver has no bitmap yet: the disc with a white
cross while the file is coming in, the download arrow after a cancel, a tap cancelling
(`ImageReceiver.cancelLoadImage`) or restarting the renderer's own URL load, and a tap anywhere else still
reaching the renderer.

**Placement, and the probe that got it right.** `ImageReceiver`'s own coordinates are relative to its block
(`RichMessageLayout` calls `setImageCoords(0, 0, …)` for a photo, and a collage cell's are relative to the
collage), so the button goes at `findMediaImageReceiver`'s block origin plus those coordinates. Drawing them
at the receiver coordinates alone put three buttons on one placeholder, which the golden caught before
anything was committed.

**Reads.** The golden (Paparazzi has no network, so every image is loading) shows one button centred on the
captioned image and one in each half of the collage, none floating in text; both `richMessageBody` goldens
were re-recorded for Linux and the macOS pair was recorded on the runner and verified there
(`verifyPaparazziDebug` green). On the device (topic 2790689, the third post's image): the button appears
while loading, a tap turns it into the white download arrow and three seconds later the frame is
pixel-identical (nothing resumes behind the reader's back), a tap on the arrow brings the cross back and the
image then loads, and a tap elsewhere still opens the viewer. The post's whole layout - text, code block,
carousel, reaction pills - is unchanged, and the flag-off body is unaffected.

The little dot at the disc's edge is the indeterminate spinner's own start marker, the same one the block
path's reading called a dot travelling around the disc (§27); it is Telegram's look for a load without a
reported percentage, not an artefact.

**The one frame §27 left open, captured (2026-09-29).** The rich body's own loading state - the case §27 could
not photograph because the file was already in memory - was read again on a feed post's image under
`adb emu network speed umts`: the grey disc with the white cross over the light-grey placeholder block, the
spinner's start dot on the disc's rim, and the photo drawing in over it a few frames later. Same three-state
button this section verified on 2790689, so both bodies now have a photographed loading state.

## 30. A post body's photos reach the column's edges (`466d0b7`) — passed; a collage's cells do not yet (2026-09-28)

The owner read the rich body's images as rounded and inset where Telegram's read edge to edge. Both come from
the chat bubble in the renderer - the media's bleed is `root.padLeft/padRight` minus the block's own padding,
the corners come from `SharedConfig.bubbleRadius` - and neither is ours to change globally (the radius is a
chat setting; the paddings also feed the layout's centring maths). `RichMessageLayout` therefore gained two
host hooks, `0`/`false` meaning upstream's shape exactly: `mediaBleed` (how far a media block may draw past
its padding box) and `squareMedia` (both `updateRoundRadius` variants answer 0 when it is set; a quote keeps
its own 8dp). `RichMessageBody` sets the inset it already gives its column and `true`.

**Reads.** Golden (Linux and macOS, both re-recorded and verified on their own platform): the single photo's
placeholder moves from x 23..369 (the text inset) to 0..392 and its corners become the fill; the collage row
is byte-identical. Device (topic 2790689, flag on): the photo spans **x 0..1079** - the screen - with square
corners, while the paragraph's text stays at x 50..1010, so it is visibly wider than the body; the loading
button stays centred on the block (disc centre 539 against the block's 539.5); tapping away from the button
still opens the viewer; the flag-off body is unchanged (same block height, same full width it always had).

**Open: a collage's cells.** Their rects are the collage's own layout maths, not a bleed, so this change only
squared their corners - the cells stay inside the text inset (measured: the collage row unchanged at x
23..374). The device pass could not photograph a collage at all: the host had no route to linux.do during the
read, so only the two cached fixture topics were available and neither has side-by-side images. It did
photograph a list-nested image reaching x 48..1079, i.e. bleeding right but keeping the list's own 48px
indent, which is the same shape of gap. The runner's read of the look: a group of images reads as one media
unit, so it would bleed collages too; the owner took that call - see §32.

## 31. A photo narrower than the column keeps its declared size (`5d83086`) — fixed; device re-read queued (2026-09-28)

The owner read a 287x500 portrait in topic 2964229's seventh post as flush left. The device pass measured
both bodies and both were wrong, each for its own reason:

- **rich body (flag on): x 0..812 of the frame's 1079** - left margin 0, right 267. The renderer's `fit`
  branch (an image narrower than the column) drew it in a box spanning the full available width and, with
  aspect-fit, sat the bitmap against that box's left edge. The box is now the width the site declared
  (capped by the available width) and centred; the change is gated on the `mediaBleed` host hook (§30) so a
  chat bubble keeps upstream's shape. The centring helper upstream already used for the media button
  (`getImageLeft()`) is the same rule.
- **block body (flag off): the full frame**, and about 1.1-1.2x larger than the rich one. `ImageBlock.measure`
  scaled every photo to the column and capped only the height, so the portrait became 909x1583 - wider than
  the file it came from and blurrier than it needs to be. It now stops at the declared width, and
  `imageRect`'s existing centring gives it equal margins.

Two new goldens (the narrow image in both bodies) pin what a snapshot without a network can see - the panel
and its button, since the loading placeholder is the renderer's full-width backdrop by design; the photo's
own place is what the device read measures, and the before numbers above are its other half.

**Device re-read (`4729e03`, build `…-103333`, topic 2964229 post 7, flag on).** The declared-width cap is
in: the portrait draws **382x499** where it used to be 812x1249 - so the *size* half of this section is
fixed in the rich body too. The *place* half was not: it still sat at **x 0..382** with 697px of slack on its
right, where the block body's equivalent photo is centred (396..683). A diagnostic build was prepared to log
the geometry, and then the cause turned out to be readable: the centring ran inside the photo block's
`availWidth()`, which in a body is the layout's *intrinsic* width, not the column - see §33, where the fix
and its golden read are recorded.

**③ (a collage's second image jittering) - not reproduced.** The runner opened the first cell, went back,
opened the second, and recorded at 44fps VFR: both opens are frame-for-frame the same shape (the photo
reaches about 95% of the frame within the first frame and 0..1079 within ~4 frames), with no frame that
lands somewhere else first. The one observation worth keeping: the first frame is already nearly full
screen, so an open reads as a near-full-screen fade-in rather than as growth out of the tapped cell - but
that is the same for the first and the second image, so it is not the reported jitter.

**Also from this round.** ④ (a quote's background flickering when a selection starts) was
recorded at 44fps VFR over a long press into *Select text* on 2790689's tenth post and **did not reproduce**:
270 frames with the card's fill constant apart from the long-press highlight and the menu, so that case is
closed as not-reproduced rather than fixed. The network itself was diagnosed by the pass: the host's direct
DNS answers linux.do with a Dropbox address and every A record fails, while the proxy returns Cloudflare's
challenge; the emulator was pointed at the host's Clash (`http_proxy 10.0.2.2:7890`, revertible) so the app
could reach the site again, which is how the measurements above were taken.

## 32. A collage reaches the column's edges too (`4550ff2`) — golden-verified; a device read is queued (2026-09-29)

§30 left a collage's cells inside the text inset and called the look the owner's call. He made it - a group of
images reads as one media unit - so the collage now takes the same `mediaBleed` host hook the single photo
does: `RichCollageBlock.onDraw` computes `padL`/`padR` as `mediaBleed` when the host set it, and as upstream's
`padLeft - 2dp` otherwise. Upstream's stretch was already in that method (`k = (maxWidth + padL + padR) /
maxWidth`, applied to every cell's x and width), so this only changes which value feeds it. A chat bubble
(`mediaBleed == 0`) and a quote (`inQuote`, which keeps its inset *and* its 8dp radius) are untouched.

**Reads.** Linux golden, both themes re-recorded and verified (`runUnitAndScreenshotTests`, exit 0): in the
393px fixture frame the two-cell collage row moves from **x 22..375 to x 0..392**, i.e. to exactly the span the
single photo takes in the same frame; the text above (its caption lines) and the row's own height do not move
(the difference is bounded to y 668..945); the gutter between the cells goes 2px -> 3px because `k` scales
every cell rect - the same thing upstream's stretch already does to a gutter. `pageBlockSlideshow` has the
same `padLeft - 2dp` stretch and is deliberately left alone: nothing in the linux.do parser produces one, so a
change there would be unverifiable.

**Still owed.** A device pass - the runner's host lost its route to linux.do in §31, and no cached topic has
side-by-side images, so this change is golden-evidence only so far - and the macOS half of these two goldens
(`richMessageBody[Light|Dark]`), which the runner re-records once its host is back on the network.

**Both halves landed (2026-09-29).** macOS: the runner re-recorded and verified; the two `richMessageBody`
goldens are committed with the four narrow-panel goldens the macOS set had never had (92 files against
Linux's 96, now 96 each - only the runner's uncommitted recordings were keeping its own verify green).
Device (build `…-103333`, flag on, topic **847468** post #1): **what reached x 0..1079 there was the
placeholder/backdrop, not a collage.**  A later read measured the two screenshots against each other pixel by
pixel (same post, newer build): the four images render as four **stacked standalone photos** (bands y
460..809, 1006..1353, 1550..1900, 2097..2399), each photo **689-690 wide at x 195..884** - the width the site
declared for it, centred - in *both* builds, while the grey backdrop behind them is full-width in the older
one and gone in the newer one (that narrowing is §33's fix). Tapping each of the four photos opens the
viewer (`1 of 4` … `4 of 4` in the post's gallery); the two that did not respond in the first read had simply
not loaded yet. So this read is about photo placeholders: a live collage has still not been found on the
site, and the collage's own cells (this section's `k` stretch) remain **golden-verified only** - as does the
question of whether a collage's photos fill their stretched cells or letterbox inside them, which is what the
golden's backdrop-only pixels cannot answer.

## 33. A body's photo box is the block's width, so a narrow photo centres (`27fa448`) — golden-verified; device re-read queued (2026-09-29)

§31 capped the photo at the size the site declared but the place stayed wrong, and §31's re-read showed it:
382x499 instead of 812x1249, still at x 0..382 of a 1079 frame with 697px of slack on the right, while the
block body centred the same photo (396..683). The root cause is the quantity the centring used:
`availWidth()` is `root.getMinWidth()` - the *intrinsic* max over the blocks' `getMinWidth()` - minus the
block's padding. In a chat bubble that equals the bubble's content width (a lone photo block's
`getMinWidth()` is the bubble's whole width, which is why upstream's rule works there); in a post body the
layout's max width is the column but `root.getMinWidth()` is whatever the widest *intrinsic* line happens to
be, so the box the photo - and its placeholder and blur backdrop - filled was narrower than the frame and
started at the column's left edge. That is also why the earlier "centre it in `availWidth()`" fix was
invisible on the device: it centred the photo inside a box that was itself flush left.

The fix is in a body to use the block's own content width (`RichBlock.maxWidth`) as the box: the photo keeps
the declared size, sits centred in the column, and the placeholder and the blur cover that same box, so
nothing wider or further left than the photo is painted. `getImageLeft()` follows it - the tap mapping, the
accessibility rect and the loading button all ride on that - and everything is behind the `mediaBleed` host
hook, so a chat bubble and a quote keep upstream's geometry to the pixel.

**Reads.** Linux golden, both themes: the narrow-panel placeholder moves from **x 0..615 to x 329..615** -
287 wide, equidistant from both edges, i.e. the exact box the block body's own golden has always had (the
two bodies now agree pixel for pixel on where the photo's panel sits). No other golden moved, and
`runUnitAndScreenshotTests` is green. The block body's own two goldens re-recorded a 19-pixel difference
(12x10 at 446,569, ~6 grey levels on a soft edge) which is the same anti-aliasing flake class as
`PollInstantButton` and `richMessageBody[Dark]`; both were restored. macOS still needs the two
`narrow…DeclaredSize` goldens re-recorded, and the device re-read (2964229 post 7, flag on: expect the
photo's block to be ~287 wide with equal margins, i.e. what the block body now reads as 396..683) is queued
with the runner.

**Both landed (2026-09-29).** Device (build at `27fa448`): the portrait's block now reads **x 396..682, width
286, margins 396/397** - the same screen carries the block body's equivalent at 396..683, so the two bodies
agree on the photo's place, not just on its panel. (The screen happened to hold a second portrait at 390..689,
width 299 - a *different* image with a different declared size, which is the point: each keeps its own.) macOS:
the two `narrow…DeclaredSize` goldens re-recorded and verified on the recording host, and the panel sits at
**x 329..615** there too - the same box Linux renders, which is what a cross-platform golden is for.
`richMessageBody` on macOS showed the usual zero-pixel anti-aliasing jitter and was restored.

**And the backdrop follows it - read off two builds (2026-09-29).** The same post 847468's first post, same
scroll position, old build (`…-103333`) against the fixed one: the photo pixels are **byte-identical** between
the two (e.g. (243,247,252) at x 200 and (245,249,253) at x 540 on the same row), while the grey backdrop
behind them - (222,223,225) at x 5/30/60, (219,222,229) at x 900/1050 - is **full width (x 0..1079) in the old
build and simply not there** in the new one, where the non-white pixels stop at the photo's own box
(x 195..884). That is this section's "the placeholder and the blur cover the same box" sentence, measured.

## 34. ④ reproduces: the pressed post dims for ~80ms when the selection starts (`2963790` post 2) — open (2026-09-29)

The owner reproduced ④ on a specific post (topic 2963790 post 2: "long-press turns the whole thing grey for a
moment, then Select text turns it grey again and then it is normal"), so the earlier *not reproduced* in §31 is
superseded - that read used 2790689's tenth post, and the owner notes it does not happen on every post.

**Reads (build `…-103333`, 44fps VFR, band means).**

- *Menu steady state (flag on).* The pressed post's band reads 255/252 while a list row reads 202-204
  (= 0.8 x 255, the scrim's 0.2 black). So the scrim dims the list and the foreground pass keeps the pressed
  post bright once the menu is up: the steady state is right.
- *Long press (47 frames).* The post's band never dips (250 -> 252) while the list goes 253 -> 202. The encoder
  emitted no frames between t=0.436 and t=1.352 (a static screen), so a dip shorter than that window cannot be
  excluded - but nothing was seen.
- *Select text (363 frames, flag on).* Frames 20-22 (t=1.388/1.409/1.470) drop the post's band **252 ->
  221.4/221.4/187.3** (~80ms) *while the scrim is still up* (the list stays 202); frame 23 (t=1.504) is when the
  scrim lifts (253) and the selection's own pixels appear (18,356 then 18,701). So the post darkens for three
  frames before the scrim goes away - that is the flash the owner sees.
- *Flag off (345 frames).* The same gesture dips the band too (250.8 -> 234/224/229/231/231/231 over ~220ms,
  list 208 -> 230, then 246 plus the tint), so **the flash is not rich-body specific**.

**Where that leaves it.** The steady state, the scrim and the foreground pass are all doing what they should, so
the dip is in the transition: something draws the pressed post darker (187 is darker than both the bright post
and the dimmed list) for a few frames while the scrim is still up. The band means cannot say whether the band's
*pixels* changed colour or whether the cell's content moved under a fixed band, so the next read is the frames
themselves (and a per-frame selection-pixel count) rather than another band average.

**Root cause and fix (`6e65240`, 2026-09-29).** The frames settled the first half (no shift at all: the best
cross-correlation between every neighbouring pair is 0px; b20 and b21 are byte-identical; in the dip the pressed
post's own rect - full width, y 750..1105 - reads 188,190,190 while the list reads 202,202,202 and the popup
stays 253; the blue selection tint arrives only in the *next* frame). A probe build (tag `SCRIMMPROBE`) then
settled the second: the pressed cell is deliberately left out of the list's own pass
(`TopicSwipeReplyRecyclerView.drawChild` returns false for the scrimmed child) and is drawn by the host above
the scrim - but that pass skipped the cell's background (the `scrimForegroundDrawing` flag), and for a *clean*
view `View.draw` never runs `onDraw`. The log caught exactly those frames (t=02.680..02.691, immediately after
`menu: Select text tapped`): the flag was set and cleared, `onDraw` never ran, and neither pass painted the
post, so its rect showed the host's wallpaper through the scrim - 235ish x 0.8 = 188,190,190, the numbers the
frames read. The steady state looked right only because in those frames the cell did draw (`cell.onDraw ...
scrimFg=true` once per frame).

The background is now painted in every pass that draws the cell, as a rect bounded to it: `drawRect(0, 0,
getWidth(), getHeight())` instead of `drawColor(...)`, because the host's direct fallback has no child-bounds
clip and a colour fill would cover the whole viewport - which is the reason the flag existed at all. It is also
upstream's model: `ChatActivity` draws the selected message's background above the scrim (`drawBackground`).
The two scrim-host goldens recorded the intentional difference on Linux (their fixture text now says what the
golden pins: the background is bounded to the post, nothing spilled).

That also answers the owner's "it seems not every post does this": the hole needs the foreground pass to land
on a frame where the cell is *clean* (nothing about the post changed), so whether the dip appears at all
depends on the frames Android happens to hand the host - the same gesture on the same post can show it or not.

**Device verdict (`6e65240`, build `…-20260929-115411`, 44fps VFR, both bodies).**

- *Flag on (204 frames).* The pressed post's two blank bands - both inside the post's own y 746..1105 and to the
  right of the popup - read **253.0 in every frame, minimum 253.0**: the 188/190/221 dips are gone. The list
  dims 253 -> 201.8 when the scrim comes up and returns to 252.7 after the tap, and the selection's own pixels
  land exactly as before (996 at the tap, 19,578 by the settle frame) - only the dip changed.
- *Flag off (356 frames).* The same two bands read 253.0 in all 356 frames: the flag-off body's seven-frame dip
  had the same cause (it is the same cell code) and is gone with it.
- Menu steady states were photographed for both bodies: the pressed post bright with its own background, the
  other posts behind the dim.

macOS: the two scrim-host goldens re-recorded and verified on the recording host (hashes re-checked after
download). The other five that moved in that run (narrow…DeclaredSize[Light], richMessageBody[Light|Dark],
PollInstantButton[Light|Dark]) were the usual 1-25px anti-aliasing jitter and were restored rather than
committed.

## 35. The copied post link carries the topic's own slug (`c1cb3f1`) — fix in; device check queued (2026-09-29)

The owner pasted a link the app had copied: `https://linux.do/t/-/2964174/1`. The `-` was ours, not the
site's: `postLink` hardcoded it, so *every* copied post link carried the placeholder where Discourse puts the
title slug, while the site's own share button gives `/t/<slug>/<id>/<n>`. Discourse does use `-` for a topic
whose title produces no slug (an emoji-only title, for instance), so the placeholder stays as the fallback.

`TopicResponseDto`, `TopicDetail` and `TopicUiState` now carry the `slug` the topic JSON already sends, and
`postLink` builds `$baseUrl/t/$slug/$topicId/$postNumber`, falling back to `-` only when the slug is blank.
`runUnitAndScreenshotTests` is green (511 tests, no golden moved). The device read - copy a post's link,
paste it, and compare with what the site itself copies - is with the runner, together with the owner's other
report from the same message (an image-layout oddity in topic 2964174's first post).

## 36. Copied link matches the site; first real collage read (2026-09-29)

**§35's copy link — verified on device** (build `0.1.0-debug-20260929-145523` = `25a38e9`, flag ON). Copying
post 1 of topic 2964174 puts `https://linux.do/t/topic/2964174/1` on the clipboard (pasted into the app's own
Open-topic field to dump it), and the app's toast shows the same string. The slug is literally `topic` —
Discourse's fallback for a Chinese title — so our output now has the site's shape. (The site's own page could
not be read back as a cross-check: its anonymous view is "Oops! That page doesn't exist or is private." and an
authenticated `curl` of `/t/2964174.json` eats a Cloudflare challenge, since `cf_clearance` is bound to the
app's TLS fingerprint.)

**Topic 2964174 post 1 — 29 images, and the first real collage rows.** The post stacks single images (one per
paragraph) and contains two grouped rows: a 3-cell row (cells x 0..385 / 393..682 / 689..1079, i.e. 386/290/391
wide, gaps 7/6 px, row height 678) and a 2-cell row (x 0..381 / 389..1079 = 382/691, gap 7 px, height 670).
The cells **fill** — the photos are centre-cropped (~20-25 % of the width each side, circles still round, so not
stretched) rather than letterboxed — and each cell opens the viewer at its own index (17/18/19 and 23/24 of 29).
That closes the item §32 left open ("do real collage cells still bleed after `27fa448`?"): yes, they reach the
edges, gaps are ~6 px (= `dp(2)` at this density), and the crop-to-fill matches Telegram's grouped media.

**Single images still follow the declared size 1:1, and that is the remaining oddity.** Measured: 4:3 → 666x500
at x 207..872 (62 % of the 1080 px column, white margins both sides), 3:4 → 374x500, 9:16 → 281x500, near-square
→ 394x390, and one wide upload → 1080x744 (full bleed). The site sends these numbers as **CSS px** — Discourse
generates them for a mobile viewport (~411 CSS px wide) and its CSS caps images at `max-width: 100 %` — so on a
phone all of these except the smallest fill the column, while we read 666 as 666 *device* px. The wide upload
next to a 62 %-wide one is what makes the inconsistency visible.

Open question for the owner (A vs B), no change made yet:

- **(A) keep 1:1** — a declared 666 stays 666 device px (62 %), 287 stays 287 (27 %).
- **(B) read the declaration as CSS px**: `min(declared x density, column)` — 666 → full column (like the site),
  287 → ~753 px (~70 %), 394 → full. Same rule in both bodies (the OFF `ImageBlock` must match), new goldens,
  and a device re-read.

Also open: whether the collage should keep cropping to fill (Telegram's grouped-media behaviour, what it does
today) or letterbox the cells.

## 37. One shared cookie jar: CookieManager is the store (`f100aa7e`) — passed (2026-10-04, `emulator-5554`)

What changed: the native cookie snapshot is gone. `SharedCookieJar` is OkHttp's side of the one
`CookieManager` (read `getCookie` per exchange, write each response `Set-Cookie` value verbatim,
linux.do hosts only); `LegacyCookieMigration` reads the retired `nativedo.cookie_store` once
into an empty jar and deletes it; `SessionCoordinator` verifies the jar's `_t` at startup, so an
account created in the in-app browser while the app was closed is the normal cold-start session.
The in-flight fence compares the `_t` the request carried with the jar's current one: a response
that lost a rotation may not write or delete auth cookies, and a logout-shaped answer that lost
it fails its own request instead of clearing the session. The two-jar reconcile machinery (#14)
is deleted.

Verdict (build SHA-256 `f4d0f829328b8a6a311608f84c698a2291a8e0c776ca069c6e7e6e813bb61442`):
before installing, the device's WebView `Cookies` DB and `nativedo.*` prefs were backed up to
`.amp/in/session-backup/`. The state under test was exactly the design's target - native
SessionStore empty, WebView jar holding a fresh `_t`. Cold start verified it natively
(`route=session status=200`, `hasToken=true`) and Settings showed `已登录 @example_user`. `_t` rotated
twice during the pass (`setsToken=true` at 13:43:19 and 13:53:17); the in-app browser, opened
from a post link, stayed signed in across both, and two force-stop cold starts plus browser<->app
round trips stayed signed in. `expired_*` appeared zero times and the crash buffer was empty. A
reaction tap exercised the write path (csrf 200, one `403 evidence=Challenge`, retry 200) without
ending the session; the chip's selected state could not be confirmed from the screenshot.

Suite: 32 classes / 94 tests green (new `SharedCookieJarTest` pins cold-start recovery, the
in-flight rotation fence, the stale logout answer, real logout's host isolation, and migration).
Evidence: `.amp/in/artifacts/cookie-single-jar/` (screenshots `01`-`19`, `logcat-session.txt`).

Post-pass note: at 14:09 the parallel `nativedo-settings-tab` worktree (based on `cdcc23ad`,
still carrying the native cookie snapshot) installed and ran its build against the same app data.
At 14:21 the single-jar build's first cold start found the server had deleted `_t`
(`404 evidence=LoggedOut deletesToken=true`) - the session had been consumed by that second
client with its own stale copy. The single-jar build cleared the site's cookies and stopped at
the login gate (screenshot `20`) with an empty crash buffer; per the device rules no login was
attempted. The verdict above (13:43-13:54) stands for the pass itself.

## Profile redesign pass: capsule flight, action row, tabs (2026-10-05, `emulator-5554`, `nativedo-profile-redesign`, signed in @example_user)

Opening from the topic header's capsule: the capsule avatar's X in the profile's own coordinate
space is measured with `ViewPositionWatcher.computeXCoordinateInParent` (the same call upstream
makes at `ProfileActivity.java:10080`) and carried in the fragment's arguments; logcat confirms
`present fragment ProfileFragment args=Bundle[{avatarX=204.0, username=neo, displayName=Neo}]`.
The screen plays upstream's `playProfileAnimation == 2` (250 ms, `CubicBezierInterpolator.DEFAULT`,
the 50 ms start delay) with the avatar flying from the capsule's 42 dp picture to the header's
96 dp rest size (`02-profile-from-capsule`, the round avatar, name and `@handle` landed centered;
`harness/profile-open-from-capsule.png` is the settled render, and `ProfileRedesignTest` asserts
the seeded X, the 0 -> 1 progress and the landed user). The AVD's `screenrecord` stayed frozen for
a second pass (2 frames in `profile-open2.mp4`, 1 in `profile-open.mp4`), the same limitation the
earlier passes recorded; the two mp4s and the still sequence are kept as evidence.

Action row: on Neo's profile the row shows Message / 取消关注 / 分享 (the account already follows
Neo, so the site's `is_followed` puts the unfollow label on the button) - `04-profile-scrolled2`
and `02-profile-from-capsule`; on @example_user's own profile it shows 书签 / 设置 / 分享
(`15-own-profile`). The four actions are offered only when `/u/{u}.json` says they exist
(`can_send_private_message_to_user`, `can_follow`/`is_followed`, `can_mute_user`/`muted`); the
follow and mute writes were exercised against `FakeDiscourseSite` only (no writes to real accounts
from the device).

Sections and stats: real linux.do numbers for Neo - 话题 649, 帖子 21254, 获赞 620454, 送出赞 3629,
访问天数 993, 阅读帖子 1631688, 阅读时长 3155 小时, 浏览 383640, 徽章 63, 得分 783474, 关注者 12456
(`04-profile-scrolled2`) - from `/u/{u}/summary.json` joined with the profile endpoint.

Tabs: 话题 / 回复 / 点赞 / 徽章 for another reader (书签 additionally on one's own profile), the
strip pinned at the list's top with the page's rows scrolling under it. 话题 shows the site's
topics with `user_actions.json?filter=4` (`16-final-tab-topics`; the last APK also strips the
cooked HTML some excerpts carry), 徽章 shows `/user-badges/{u}.json` grants with dates
(`14-tab-badges`).

One device-only defect was found and fixed in the pass: a summary arriving after the tab area was
built rebuilt the info rows without the tab row, which made the tabs unreachable (the list ended
at 被采纳回复); `buildRows` now re-appends the tab row whenever the tab view exists, and the
harness tab test serves the summary fixture so the ordering is pinned. The first two holds ran the
pre-fix APK; the final hold reinstalled the fixed build (APK sha256 `9f7f3cb6…`, matching the
device's `base.apk`) and re-verified the tab pages.

Suite: 43 classes / 153 tests green. Evidence: `.amp/in/artifacts/profile-redesign/`
(screenshots `00`-`16`, both mp4 attempts, `ui*.xml`, `harness/`).

## Composer / shell / system-bar integration pass — device verification (2026-10-06, `emulator-5554`, signed in @example_user)

Scope: the previous wave's shell/system-bar/capsule build, `apps/android/nativedo/build/intermediates/apk/debug/nativedo-debug.apk`,
SHA-256 `14eceb294eb342f60bb9f8c273ed2bffb091026cdc1d08b3e8dd734226b3114d`. Verification only — no app source edited,
no Gradle, no commits. `adb install -r -t` over the existing data (session and drafts preserved); the device `pm path`
base.apk pulled back matched the build hash. Emulator `emulator-5554`, AVD `Medium_Phone_API_36.0` (Android 16 / API 36,
arm64, 1080x2400 @ 420 dpi, 1 core / 2 GB).

Checks against the native **NewTopic** screen (no topic opened — `TopicReadReporter` would post `/topics/timings`):

- **Keyboard focus / hide** — focusing the title or body sets `mInputShown=true` and BACK clears it (`mInputShown=false`,
  IME inset `visible=false`); composer returns to its resting bounds (`05`,`07`,`25`,`26`).
- **Composer above IME, no double inset** — the composer's bottom sits **28 px above whichever bottom inset is current**:
  with only the nav bar, composer `[151,2202][939,2309]` vs `navigationBars [0,2337][1080,2400]`; with Gboard up,
  composer `[151,1384][939,1491]` vs `ime [0,1519][1080,2400]` (881 px). A double inset would grow that 28 px gap by the
  nav-bar height when the IME is up; it does not. Composer keeps its 107 px height and is not overlapped (`25-newtopic-ime-real.png`).
- **System gesture handle / 3-button nav** — the gesture pill is visible and coherent in both themes (`15-gesture-handle-crop`,
  `27-gesture-handle-crop`); with the `threebutton` overlay the back/home/recents buttons appear and the nav band samples
  `#1d2733`, identical to the app background (no mismatched band) (`17b-home-3button-dark.png`).
- **Light/dark switches** — the home options-menu switch recolours home (header `#1d2733`, no stale white band) and the
  reopened NewTopic screen is coherent in dark; switching back gives coherent light home (`15`,`16`,`27`).
- **Return / back and editor Apply back-to-origin** — the NewTopic action-bar pencil opens the upstream editor; **Apply**
  ("完成并返回") returns to NewTopic with the draft `raw` updated to `BodyLine`; BACK from the editor returns to NewTopic and
  BACK from NewTopic returns home (`09`–`13b`).
- **Last-Activity recreation** — a `font_scale` config change (not in the manifest's `configChanges`) recreated the Activity
  in the same process; the shell re-verified the session (`route=session status=200 hasToken=true`) and re-presented
  `NativeDoMainTabsActivity`, restoring the selected **我的** tab (`19-after-fontscale-recreate.png`).
- **Database / socket observations** — no `cache4.db`, crash buffer empty; app-uid sockets go only to the configured
  proxy `10.0.2.2:7890` (`29-sideeffect-checks.txt`). The destination behind that proxy was not inspected, so this
  device observation does not prove the absence of Telegram network traffic. The harness separately checks tgnet calls.

**IME environment finding (recorded for the harness):** the AVD ships `hw.keyboard = yes`, so Gboard runs its
`AccessoryKeyboard` at zero height (`keyboardType: accessory`, IME inset `[0,2400][1080,2400]`) — the same limitation earlier
passes saw. Only Gboard is installed (its service class *is* `com.android.inputmethod.latin.LatinIME`); no AOSP LatinIME exists.
To get a real keyboard the AVD was cold-booted once with `hw.keyboard = no` (config backed up), which gave the full QWERTY
kernel used for the inset measurement, then restored to `hw.keyboard = yes` (config diff identical) and cold-booted back.

**Observed startup issue:** the first app start after the cold boot raised a one-off ANR
("NativeDoApplication isn't responding", `Reason: … failed to complete startup`, `Load: 25.52`, CPU pressure 97 % on the
1-core AVD); tapping **Wait** let it finish and it did not recur. CPU pressure is a possible explanation, not a verified
cause, and an app defect has not been ruled out. Cold start is slow (~19.7 s).

**Restored:** gestural nav, light theme, Gboard default IME, `config.ini` identical to the pre-pass backup, drafts back to
the pristine snapshot (test `new|…` draft removed), app on the 我的 tab, APK `14eceb29…` still installed.
Evidence: `.amp/in/artifacts/composer-next-wave/device/` (`device-notes.md` per-image map; logs redact the session cookie —
no tokens/cookies/credentials captured).

## Composer emoji-panel crash fix + mention host — device verification (2026-10-06, `emulator-5554`, signed in @example_user)

Scope: the fixed build `apps/android/nativedo/build/intermediates/apk/debug/nativedo-debug.apk`,
SHA-256 `cdad87aad11631538b7b386dd7391829c734e2910b5f612580db0ec53f10dd09`. Verification only — no app source edited,
no Gradle, no commit, no push, no login/logout. The verified main pass opened no real topic (`TopicReadReporter` would
POST `/topics/timings`); a post-pass re-capture accidentally did — see the disclosed incident below.
`adb install -r -t` over the existing data (session and drafts preserved); the device `pm path` base.apk pulled back after
install matched the build hash (`cdad87…`). It replaced the previous pass's crashing build (`c1e1bc…`). Emulator
`emulator-5554`, Android 16 / API 36, arm64, 1080×2400 @ 420 dpi. Device lock `/tmp/nativedo-device.lock.d` held for the
whole pass and released at the end.

**The emoji-panel crash is fixed.** The prior build died in `DiscourseEmojiPicker.requestImage` →
`GridAdapter.submit` (`notifyDataSetChanged` while the `RecyclerView` was computing layout, `DiscourseEmojiPicker.kt:492/559`).
The fixed build runs the same sequence — warm catalog, `show()` → `applyGroups()` → first grid layout binds a tile →
`requestImage` — in **both light and dark** and completes with `grep -c "Cannot call this method while RecyclerView"` = 0,
an empty crash buffer and no ANR in the full 24,878-line pass logcat. Verified: cold open → NewTopic → smile (no crash),
reopen, toggle to keyboard and back, category switch (`Smileys / Emotion` → `People / Body`), search (`grin` → 5 cells),
and exact shortcode insertion (`hello world` → `hello world:grin:`, surrounding draft kept). Back closes the panel and
releases the composer lift (`[27,2197]`) while staying on NewTopic.

**Mention host.** The `@` panel renders full-height above the composer with the **first row fully visible** and ≥4 live
candidates (`@Dua`, `@duanzero`, `@duansheng`, `@duansix`), scrolls (6 candidates after a swipe) and selects
(`@duanzero ` inserted, panel closed). Touching above the band reaches the form: tapping the title field focuses it
(`focused=true`) with the body unfocused, so the full-height panel does not block the form. Dark panel is coherent.

**Two honest limitations remain (not crashes).** (1) **Emoji images never render** — the catalog entries carry
root-relative image paths (`/image…`); `DiscourseReactionEmoji` feeds them to okhttp, which throws
`Expected URL scheme 'http' or 'https' but no scheme was found for /image…` (172×), so every tile falls back to its
human-readable **name as text** (the shortcode is still on the cell, `content-desc=":grin:"`, and insertion inserts the
shortcode, so only the asset fetch is broken — a URL-resolution gap). (2) **Focusing the emoji search field brings up the
IME over the grid**, so results are only visible/tappable after dismissing the keyboard (ESC/Back); searching works, you
just can't tap a result while the keyboard is up. Cosmetic: the mention panel's 4th row is clipped by the composer (the
first row — the fix target — is intact), and Back needs two presses when the IME is up (first dismisses the keyboard).

**Observations:** no fatal exception and no `am_anr` across the main pass (04:24–04:37); `databases/` contains no
`cache4.db`; at rest the app uid (`u0_a212` = 10212) owns TCP sockets only to `10.0.2.2:7890` (the configured proxy),
which — as before — cannot prove the absence of Telegram traffic behind the proxy. `grep -c posts.json` = 0 (no post
created).

**Disclosed incident (post-pass re-capture, not part of the verified pass):** after the pass, a too-broad `rm` glob
deleted the uiautomator scratch dumps, so a re-capture was attempted. The app then sat on the **我的** tab, where the
reused compose-FAB coordinate lands on a topic row; it **opened a real topic** (`TopicFragment topic_id=2919125`) at
04:39:49 and the following keystrokes went into that topic's reply composer. `TopicReadReporter` fired
`POST /topics/timings` twice (04:39:50, 04:40:49), each answered `403 … evidence=Challenge` — **no server write
succeeded**. It was stopped immediately; the real reply draft (`2919125.raw`) was removed, the draft store was restored
byte-identically, and the app was relaunched onto the 我的 tab. The four re-captured dumps from the topic screen were
discarded. The main pass opened no topic (its logcat shows only `NewTopicFragment`/`DiscourseRichEditor`), so the headline
findings stand.

**Restored:** light theme (via the app's own 夜间模式/日间模式 toggle), `cmd uimode night` = no, `navigation_mode` = 2,
`font_scale` = 1.0, Gboard default IME; session preserved (`@example_user`); `nativedo.topic_reply_drafts.xml` byte-identical to
the pre-pass snapshot (test `new|…` keys and the accidental `2919125` draft removed); `shared_prefs/` file set unchanged;
app left on the **我的** tab; APK `cdad87…` still installed. Evidence:
`.amp/in/artifacts/composer-next-wave/fixed-device/` (`verdict.md`, screenshots `00`–`33`, `99-final-state.txt`,
`logcat-all.txt`, `ui-final.xml`; logs redact the session cookie — no tokens/cookies/credentials captured).

## Composer emoji picker fixes — final device retest (2026-10-06, `emulator-5554`, signed in @example_user)

Scope: the build `apps/android/nativedo/build/intermediates/apk/debug/nativedo-debug.apk`, SHA-256
`71fa82693d274fa5fe4083bc46dbce2dff373a66a6ba4243e5f0ecf32d97e4f9` (the `emoji-live-fixes` source). Verification
only — no source/test edited, no Gradle, no commit, no push, no login/logout. `adb install -r -t` over the existing
data (session, drafts and prefs preserved); the device `pm path` base.apk pulled back after install and again at the
end both matched the build hash. It replaced the previous pass's fixed build (`cdad87…`). Emulator `emulator-5554`,
Android 16 / API 36, arm64, 1080×2400 @ 420 dpi, `hw.keyboard = yes`. Device lock `/tmp/nativedo-device.lock.d`
held (mkdir) for the whole pass and released at the end. No real topic was opened; the prior pass's incident was
avoided by dump-confirming the active home feed and the actual FAB bounds before every tap.

**Both fixes verified. (1) The emoji grid draws the site's images** — the catalog carries root-relative paths
(`/images/emoji/twemoji/grin.png?v=15`), which this build resolves against `baseUrl`; the grid shows real images (no
shortcode text) in **both light and dark**, with `grep -c "no scheme was found"` = 0 (was 172×), `IllegalArgumentException`
= 0 and no non-200 image status. **(2) Focusing the emoji search field grows the in-app panel above the real IME** —
with the field focused (`mInputShown=true`, `keyboardType=prime`, full Gboard QWERTY visible) the search field and the
`grin` result tiles sit above the keyboard (field `[142,692][959,797]`, results `y 821–947`), and a result tile is
tappable while the IME is up (the `:grin:` tile at `[270,821][405,947]` inserted the shortcode). Also verified: exact
`:grin:` insertion keeping the surrounding text (`hello world` → `hello world:grin:`, draft store updated); Back hides
the IME then closes the panel with the form kept and the capsule back at `[27,2197]`; reopening the panel does not
crash; the `@` panel's **first row is fully visible** with 4 candidates (`@Dua` `[242,1058][346,1111]`, `@duanlab`,
`@duanzero`, `@duansheng`); tapping the title with the panel open focuses the title (`focused=true`) while the body
stays unfocused.

**Observations:** no fatal exception and no `am_anr` across the pass; `databases/` contains no `cache4.db`;
`grep -c posts.json` = 0, `grep -c timings` = 0, no `TopicReadReporter`, and `present fragment` shows only
`NativeDoMainTabsActivity` + `NewTopicFragment` (×2) — no real topic opened. The AVD's
`show_ime_with_hard_keyboard` was already `1` and was not touched (reads `1` at the end); no AVD config changed and
no cold boot.

**Restored:** light theme (`theme = Blue`, via the app's own toggle), `cmd uimode night` = no, `navigation_mode` = 2,
`font_scale` = 1.0, Gboard default IME; session preserved (`@example_user`); `nativedo.topic_reply_drafts.xml` byte-identical
to the pre-pass snapshot (SHA-256 `8444fa95…`, test `new|…` keys removed, mode `660`); `shared_prefs/` file set
unchanged; app left on the **我的** tab; APK `71fa8269…` still installed. Evidence:
`.amp/in/artifacts/composer-next-wave/final-device/` (`verdict.md`, `00`–`30` captures, `99-final-state.txt`,
`logcat-main.txt`, `ui-final.xml`; logs redact the session cookie — no tokens/cookies/credentials captured).

## The home category strip: shown from the first frame, its capsule travels (2026-10-06), initial harness pass

What changed: two things the earlier pass left wrong. (1) The strip was hidden until `/categories.json`
answered — `publishCategories` only ran `buildTabs()` for a non-empty list, and `buildTabs()` set
`stripHasTabs = !categories.isEmpty()` — so with an empty cache the first frames carried no strip at
all. The all-topics folder is now a tab of the strip itself: `publishCategories` always builds,
`stripHasTabs = true`, and the site's answer only adds tabs (a site that answers none leaves just the
全部 tab, with the list still readable). (2) The selected capsule had no travel:
`FilterTabsView.onMeasure` calls `updateTabsWidths()` — the only source of `positionToX/Width/Count` —
only when `findDefaultTab() != null`, and the capsule is drawn from
`indicatorWidth = lerp(prevW, newW, progress)`. With the tables empty `indicatorWidth == 0`, so
`drawSelector` skipped the capsule entirely while it moved: it vanished on the tap and reappeared at
the destination. The 全部 tab was added with `isDefault=false` (to dodge upstream renaming the default
tab to "All chats"), which disabled exactly that path; it now carries `isDefault=true`, and
`FilterAllChats`/`FilterAllChatsShort` are overridden to 全部 in the module's `strings.xml`. Finally,
upstream's zero-delay animation repost now goes through `NativeDoHooks.repostIndicatorAnimation`, which
holds it to the frame clock (a device already paces it through the traversal barrier; a host without one
ran the whole 320 ms animation inside a single drain).

Verdict: harness only (the fake site). `HomeCategoryPagerTest` 8/8, including the two new tests: while
`/categories.json` is held the strip is `VISIBLE` and offers only the 全部 tab, and a mid-slide frame
draws the capsule at a `centerX` strictly between the two tabs. Evidence:
`.amp/in/artifacts/folders-reactions-wave/tabs/renders/` — `home-folders-initial-loading.png` (strip up,
全部 only, categories pending but feed loaded) and `home-folders-selector-before/mid/after.png` (capsule on 全部, mid-travel,
settled on 开发调优). No real topic was opened, no real API written, no data cleared.

Still to settle on hardware: the capsule's hand-feel through a real tap (the harness steps the clock by
hand).

## Folders, reactions, search and sharing (2026-10-06), combined verification

The final arm64 APK was assembled and installed with `adb install -r -t` on `emulator-5554`
(API 36, 1080×2400, 420 dpi). SHA-256:
`e508b5cf59e6f8bf5b8de89440b5a467f7ff4a0a886c6ea13d3eccc459d5ca48`.
Session and app data were preserved; no uninstall, clear-data, logout or draft edit was performed.
The device lock was held during the pass and released afterward.

**Home taps:** from 全部, tap 开发调优 → 国产替代 → 全部 → 资源荟萃 → 全部, waiting one second
between taps. The recording contains frames at approximately 16 ms intervals during the slide.
The inspected `first-slide-60fps.png` shows the selected blue capsule moving and widening from
全部 to 开发调优 through intermediate positions while the text positions stay fixed. It stays drawn
throughout, so this verifies the selector itself rather than confusing it with strip scrolling.
Evidence: `.amp/in/artifacts/folders-reactions-wave/device/tabs-travel.mp4`,
`first-slide-60fps.png`, `selector-travel.gif`, and before/after hierarchy captures.
The first attempted recording was static because recording and input were separate tool calls;
it was replaced by a single ordered recording/input command. Low-rate video summaries missed the
short transition; the verdict above comes from inspecting its full-rate frame extraction.

The app was left on 我的 in the light theme; `navigation_mode=2`, night mode `no`. The collected
flow log reports only `NativeDoMainTabsActivity`, with no topic fragment, fatal exception or ANR
in that retained excerpt. No real topic was opened or real reaction submitted during the pass.

**Fake-site topic flows:** the retained production-host tests verify the finite upstream reaction
picker, chip toggle versus user-list long press, retry/empty states and dismissal before a late
user response; topic-scoped search with floor navigation, retry, paging and deduplication; system
share intents with unequal post id and post number; anonymous/signed-out deep-link gating and
exact-floor navigation after login. Inspected screenshots show the picker and loaded/error/empty
users sheets, plus search results. The loading-sheet static JVM capture does not show a spinner,
so it is not retained as proof of a visible loading animation. Topic flows were not driven on the
real site to avoid writes and read-timing reports.

**Combined checks:** the parent's final `:nativedo:testDebugUnitTest --quiet` run passed
251 tests, zero failures/errors/skips. The arm64 assemble, Telegram hook guard and vendor quick
guard passed. Logs and XML are retained in
`.amp/in/artifacts/folders-reactions-wave/final-check/` (`parent-full.log`, `xml-parent/`).
Earlier repeated runs exposed timing-sensitive failures. Fixed the category-tap settlement wait,
the fake server's live-list iteration race, asynchronous feed assertions, and the new selector
test's sampling: its 48 ms sample now uses bounded `idleFor` instead of draining future tasks.
Other timing-sensitive full-suite failures are retained honestly; this pass is not a guarantee
of deterministic stability or physical-device GPU/keyboard performance.

All changes remain local and uncommitted; the APK is installed only on this emulator.

## Topic body fidelity and delayed avatars (2026-10-06), host UI only

This pass used FakeDiscourseSite and UpstreamUiHarness against the production TopicFragment and
upstream ChatMessageCell on macOS. It did not run on a device, use adb, open a real topic or write
to linux.do. No APK was installed by this unit. The changes remain local and uncommitted.

The retained pre-fix failures show lost root text, 14 top-level blocks where the nested body needs
4, and a delayed avatar resetting the first visible position from 8 to 0. A real details tap also
exposed a missing host forceUpdate callback: its model opened, but its cell stayed at the collapsed
height. A separate Discourse aside/title case failed before quote titles were moved into cite.

The final combined run passed 15 tests with zero failures, errors or skips: TopicBodyFidelityTest
(5), AvatarRenderTest (4), PollTest (3), OneboxTest (1), TopicImageTest (1), TopicPagingTest (1).
Each Gradle run held the shared `/tmp/nativedo-T01a10c8f-gradle.lock` with ownerPID and an EXIT trap.
Production-file `git diff --check` passed. The body checks reported zero tgnet native calls,
initialisations or Telegram database opens after harness boot.

Inspected renders and assertions show nested ordered/unordered lists, quote authors and details
children in the correct containers; a Discourse quote retains its downloaded photo and author
without title controls leaking into the body. Actual taps open and close details, grow and restore
the cell height, and preserve cell identity and top offset. The delayed avatar changes initials
to the downloaded magenta picture while retaining position 8, top 143 px and cell identity.
The synthetic 40-paragraph body's END OF LONG BODY is visible after scrolling (rich height
5505 px, cell bottom 2562 px in a 2742 px list viewport).

Evidence: `.amp/in/artifacts/topic-fidelity-wave/body/` contains the failure XML/logs,
`final-check.log`, `xml-final/`, the body/quote/details/long-tail/avatar PNGs, and a README with
the exact repeat command and limits. Inline emoji now keeps its alt/title shortcode as readable
text; this is not a claim that the original emoji image is rendered.

This does not reproduce topic 2963407: its original JSON was unavailable, and the synthetic long
body already passed before the fix. The 900 dp cap is upstream's partial-message behavior and
was not changed. Physical-device animation/gesture behavior and arbitrary plugin markup remain
unverified; the conversion rebuilds the rows upstream's importer emits, not content that importer
already flattened inside a list item.

## Topic original photos and viewer source geometry (2026-10-07 CST), host UI only

This pass used FakeDiscourseSite and UpstreamUiHarness through the production TopicFragment,
ChatMessageCell, ImageReceiver and PhotoViewer on macOS. It did not use adb, install an APK,
clear app data, open a real topic or write to linux.do. All edits remain local and uncommitted.
Only the photo bridge/viewer, TopicFragment's two viewer calls and TopicImageTest changed in
this unit; the shared checkout's existing body, avatar, composer and other changes were preserved.
No upstream animation, drawing or gesture code was edited.

Tests were written and run before production changes. The retained red results show that the
lightbox original was never requested, all conflicting download/srcset hints resolved to src,
and the viewer opened without a source PlaceProviderObject. The final combined run passed
25 tests, zero failures/errors/skips: TopicImageTest (3), DiscourseCookedHtmlTest (3),
PostCellRenderTest (6), PostVideoTest (7), TopicBodyFidelityTest (5), TopicPagingTest (1).
The arm64 :nativedo:assembleDebug build and scoped git diff --check also passed. Gradle runs
held /tmp/nativedo-T01a10c8f-gradle.lock with ownerPID and an EXIT trap; the lock was released.

The fake preview is magenta, 320×180; its original is cyan, 1600×900. The original request carried
_t, _forum_session and cf_clearance. The stock viewer's actual adapter file decoded as 1600×900,
matched the original fixture byte-for-byte, and the full-screen receiver/render drew cyan pixels.
The photo model kept the cooked 320×180 display aspect. Explicit data-download-href on the image
or lightbox wins, then lightbox href, then an original-path/widest srcset candidate, then src.
An ordinary linked picture does not use its navigation href; emoji and small icons stay inline.

The visible source was (210,419) px with 180/180 px list clip additions. Its receiver, bitmap and
coordinates matched the drawn rich block. Actual upstream opening and closing animator progress
was sampled between 0.4 and 0.6, with separate-window captures. Closing preserved cell identity
and top offset 180 px. After scrolling the same long cell, the image was outside the viewport;
the current provider returned null and upstream's no-source close completed. Invalid gallery
indices also returned null. The photo checks reported zero tgnet requests/native initialisations
and Telegram database opens after harness boot.

Evidence is retained in .amp/in/artifacts/topic-fidelity-wave/photo/: red XML/logs, xml-final/,
final-check.log, assemble.log, preview-fixture.png, original-fixture.png,
viewer-downloaded-original.png, photo-original-viewer.png, photo-open-mid.png,
photo-close-mid.png and photo-close-offscreen-mid.png. These captures were inspected with
view_media. The README there records the repeat commands and the host rendering limits.

This verifies host production wiring, file contents and sampled animation states, not physical
device GPU/gesture behavior. The existing eager image cache now fetches the selected original,
so page-load bandwidth can increase; no separate preview/original lazy-download lifecycle was
introduced. Stock importer behavior for ordinary inline images and nested media was not changed.

## Topic header metadata and avatar-to-profile flight (2026-10-07 CST), host UI only

This pass used FakeDiscourseSite and UpstreamUiHarness through the active nativedo/full Telegram
build on macOS. No adb, installation, data clearing, real topic request or linux.do write was
performed. Changes remain in the shared local main checkout, uncommitted. This unit touched
TopicListFragment's presentTopic entry, TopicFragment's header/profile opening, ProfileFragment,
TopicHeaderAvatarTest and ProfileTransitionTest. Existing body/photo/composer/reaction/reply and
other uncommitted work was preserved; no upstream source was edited.

The topic entry now carries the row's reply count and category ID. CategoryStore's current cache
supplies the name synchronously. With topic detail and a category refresh both held, the first
header paints `开发调优 · 17 回复`; detail corrects it to `运营反馈 · 3 回复` while the refresh still
waits. An unknown category is not guessed. The stock SimpleTextView title scrolling is enabled;
the test measures its actual Canvas layoutX moving left across 160 timed draws, not a flag.

Profile now follows pristine ProfileActivity's 8623-8690 full-width photo target, retaining its
250ms DEFAULT interpolation and 50ms delay. The source's actual X/Y, drawn size and ImageReceiver
are handed over. The test moves the source 19px and scales it to 90%, opens while the profile
response is held, then releases a changed avatar template in mid-flight. Actual AnimatorSets
are sampled at opening 0/125/250ms and closing 125/249ms, then ended through the real host
callbacks. Avatar and title rects agree across the opening handover within 2px; the final visible
closing frame returns to the source rect within 2px, the real TopicFragment returns and its
bar is restored. Cancellation at 100ms also restores the bar and produces a returned-topic frame.
The real cached source photo remains sufficient for expansion while the destination download
is pending, fixing a reproduced race that otherwise collapsed the photo to x=410 at handover.

Tests preceded the production changes. Retained red evidence covers empty initial subtitle,
immobile title, wrong source rect and the async-avatar collapse. Final combined verification
passed 22 tests with zero failures/errors/skips: ProfileTransitionTest (4), TopicHeaderAvatarTest
(5), ProfileScreenTest (3), ProfileRedesignTest (5), ProfileStatusBarTest (1), HomeFeedTest (3),
TopicPagingTest (1). Arm64 :nativedo:assembleDebug and scoped git diff --check passed. Gradle
runs held /tmp/nativedo-T01a10c8f-gradle.lock with ownerPID/EXIT cleanup; the lock was released.

Evidence is retained in .amp/in/artifacts/topic-fidelity-wave/header-profile/: xml-red/,
xml-final/, final-check.log, assemble.log, pending/corrected subtitle, marquee before/after,
opening start/mid/end, closing mid, normal/cancelled return PNGs and a README with repeat commands.
The final captures were directly inspected with view_media after its objective-based service
returned intermittent errors. The photo is a flat magenta fixture at 96×96 and 720×720, not a
missing network picture. Pale action capsules/glyphs in the host capture are not accepted here
as proof of action-row visual fidelity.

This is host production-wiring and sampled-animation verification, not a device smoothness pass.
GPU blur, physical frame pacing, real avatar latency, landscape/tablet and screen-reader animation
behavior, and a completely empty category cache were not verified. Long category labels keep the
existing width policy: the reply count is appended only when the composed subtitle fits.

## Topic replies, mention scrolling and long-press reactions (2026-10-07 CST), combined host verification

The reply composer hosts Telegram's ChatActivityEnterTopView and ChatReplyContainer above the
capsule. Selecting a target preserves the existing draft instead of replacing it with @username;
cancelling clears only the target. A restored target outside the loaded window is read by floor
number and names the strip when it arrives. Incoming text, onebox and poll bubbles carry the
reply on the first bubble only, with the target's author and excerpt in the stock reply preview.
Taps navigate by post number, not post ID. One target read updates all current replies to it;
generation checks reject results belonging to a replaced window or a destroyed screen.

The long-press menu now hosts ReactionsContainerLayout directly, without a second sheet step.
Tests scroll the production popup to its tenth permitted reaction and verify the selected site's
ID in the fake toggle request. LayoutHelper receives dp rather than already converted pixels,
fixing a reproduced oversized rail. A dismissed rail ignores late emoji completion. The mention
host is bounded while twenty candidates scroll, so it does not grow over the entire topic. Both
light and dark renders retain the header and post above the candidate band.

Tests preceded the corresponding fixes. Retained red results cover draft replacement, missing
reply previews, multiple answers to an unloaded target, a target later in the same window,
onebox-first replies, restored targets, the oversized rail and late updates after dismissal.
Inspected screenshots show different reply authors (Baba and Dora), empty/drafted/multiline
reply strips, cancellation, the menu rail, and mention scrolling. The rail screenshot uses
generated fallback tiles because that fixture supplies no emoji catalog; it is not evidence of
real site emoji artwork. The multiline review PNG is a resized copy of the executed host render.

The parent's final fresh :nativedo:testDebugUnitTest run passed 273 tests across 67 classes,
zero failures/errors/skips. The arm64 assemble, Telegram hook guard, vendor quick guard and
git diff --check passed. The final APK SHA-256 is
b39c0b8d2161dec816fb859af66cc7e1e4a50957c11b94c92c2d21b0e0497e74.
Gradle commands ran serially under the mkdir lock, which was released. The temporary Gradle
wrapper was removed after use. No upstream component implementation changed in this wave.

This was not consistently green: the parent's first fresh full run timed out in
AvatarRenderTest.aListAvatarArrivingAfterAnotherRowsPhotoIsDrawnStillAppears; an isolated
AvatarRenderTest run passed that case but timed out in
theAvatarSpotIsNeverEmptyWhileThePictureIsNotDrawn. No tests were disabled or expectations
weakened. Those logs/XML and the final clean run remain under
.amp/in/artifacts/topic-fidelity-wave/final-check/. Earlier subagent attempts also reported
order/timing-sensitive failures. The passing run does not establish deterministic suite stability.

Interaction evidence and repeat notes are under
.amp/in/artifacts/topic-fidelity-wave/interactions/. All topic flows used FakeDiscourseSite;
there was no real topic read/timing write, reaction, reply, adb pass or data clearing. The final
APK was built but not installed in this wave; code remains local and uncommitted. Reply/window
generation guards were reviewed but do not have dedicated deterministic late-window/cancel
tests. Native IME/GPU frame pacing and physical-device hand-feel remain unverified. Topic 2963407
was not reproduced because its original payload was unavailable. Inline cooked emoji currently
retains its readable label, not the original custom emoji image; arbitrary plugin HTML and
content already flattened by the stock importer are not claimed to have full fidelity.

## Profile, message menus, session states and direct replies (2026-10-07 CST), host verification

This pass used serial subagents in the shared `spike/full-telegram-subtree` checkout, followed
by parent source review, screenshot inspection and combined validation. It is not a device pass.
All topic and account flows used FakeDiscourseSite through the production NativeDoActivity and
upstream widgets. No real topic timings, replies, reactions or account changes were sent.

The topic action-bar profile flight now lands on the normal round header avatar, not the expanded
full-width photo. Deliberate photo expansion remains available. Pushed profiles are edge-to-edge,
and the upstream ProfileActionsView receives the collapsed expansion state so its four permitted
actions have dark text on light pills and light text on dark pills. Source coordinates and scale,
reverse flight and cancellation restoration are retained. Both other-user action-row themes were
rendered and inspected, rather than inferring four-action contrast from the own-account row.

The message menu now uses ChatScrimPopupContainerLayout with ReactionsContainerLayout above the
action list, instead of placing the rail inside ItemOptions. The pressed message is redrawn above
a flat dim layer. The rail is populated before measurement and uses upstream's enter animation;
ten permitted IDs remain scrollable and each fake-site toggle carries the selected ID. Fallback
artwork is transparent rather than invented colored rectangles, and a warm menu retains an
already registered site icon. Inspection found decoded static chips at scale zero after a late
icon rebind. The production callback now resets the rail's visible-holder play bookkeeping after
setMessage; the painted-last-chip test only waits and draws, with no test-side repair. Failed
topic pages use Telegram Bulletin above the composer, preserving the loaded rows and retry path.

Notifications, Profile and Settings now observe session state and show explicit login, expiry,
verification, forbidden and retry states. Ordinary network failures and plain 403s are not called
logout. Cancelled WebView recovery does not refresh or report success. Confirmed recovery refreshes
reads, including same-account challenge recovery when coordinator identity did not change. Own
Profile follows an account change; epochs reject late profile, notification and activity results.
Pushed profiles keep their viewed target. Appearance and help remain accessible in Settings.

The source comparison with local main found a deliberate cookie-design difference: main uses
CookieStore plus CookieSyncBridge, whereas this branch already shares CookieManager between the
WebView and native exchanges. This pass did not revert that design. Native validation remains
startup plus explicit recovery, not every foreground event or tab selection. Authentication does
not prove Cloudflare accepts native reads or writes, and no write is automatically replayed.

Each floor's last bubble can now carry the stock ChatMessageCell reply/comment footer. Its total
is the server's reply_count; faces come only from loaded direct replies and are deduplicated by
author. Opening the footer reads the existing /posts/{id}/replies endpoint lazily. The sheet scroll
and status taps drive pagination/retry, overlapping pages are deduplicated by post ID, and a tapped
reply jumps by positive post number. Dismissed sheets ignore late results. An empty page says no
replies are currently visible without replacing the server's total. No new endpoint was needed.

Parent validation artifacts are in .amp/in/artifacts/topic-corrections-final/. The final fresh
full suite ran 320 tests across 71 classes with three failures: HomeTabsTest's keyboard clearance,
TopicFadeBandTest's theme-switch timeout, and PostVideoTest's in-flight progress assertion.
The requested feature classes contributed 83 passing tests and no failures in that same run.
The parent's earlier full run ran 319 tests and failed HomeCategoryPagerTest, TopicFadeBandTest
and PostVideoTest. Each of these four classes passed in separate parent invocations. This is
evidence of order/timing sensitivity, not proof the entire suite is stable or that a regression
has been excluded. No tests were disabled or assertions weakened to obtain those results.

The final arm64 assemble, Telegram hook guard, vendor quick guard and git diff --check passed.
APK SHA-256: 5d3ecd97b277631b68c2f88a2ec8fd7eb0499923c11a5524bcb86d353ef72c1a.
The APK was not installed. Work is local and uncommitted; nothing was pushed or deployed.

Review images and repeat evidence are grouped in .amp/in/artifacts/topic-corrections-final/REVIEW.md
and the profile-correction, menu-correction, session-correction and replies-correction directories.
These are host renders, not real-site screenshots or a hardware frame-pacing verification.
Physical gesture/three-button bar behavior, GPU blur, keyboard animation and real emoji latency
remain unverified in this pass. The menu uses a non-blurred dim layer; synthetic emoji fixtures
verify decoding and composition, not the exact production catalog artwork. Stock reply-count and
composer labels still include English. Full-suite instability remains an open verification issue.

### 2026-10-08 — Profile, post menu and discussion-page correction (host only)

This follow-up supersedes the replies-sheet behavior recorded above. The active build remains
`:nativedo` under `apps/android/telegram` on `spike/full-telegram-subtree`. No upstream file was
changed in this follow-up. All checks used FakeDiscourseSite and the production UI harness, not
linux.do. No device was connected during the parent check; no APK was installed and no real
reaction, reply or read-timing write was sent.

The topic header now uses the ordinary Profile transition's clock and palette rather than the
expanded-photo transition. The avatar still travels from the actual capsule to the round header;
opening and closing avoid layout requests when its frame dimensions are unchanged. The reverse
captures the actual starting corner radius, preserving both round-header and expanded-photo
returns. Transition tests exposed the expanded action/palette state, redundant layout requests
and round-to-square first-close frame before those corrections.

The long-press menu retains ChatScrimPopupContainerLayout and ReactionsContainerLayout, adds the
action-content width floor, and uses the cell-local press point with composer-aware placement.
The permitted ten reactions remain horizontally reachable and retain their server IDs. The
width floor also applies to rows because the upstream scrim measures the menu's inner content;
this is a host adaptation, not a claim of byte-identical ChatActivity wiring. The backdrop still
uses the previously recorded flat dim with the pressed cell redrawn, not Telegram's global blur.

The comment footer now pushes TopicFragment in discussion mode: original rich post, direct reply
chat cells and the same capsule composer. It no longer opens an author/excerpt BottomSheet.
Paging uses post ID for the endpoint and post number for the cursor, deduplicates overlapping
pages, and ends on empty or non-advancing pages. Thread drafts are isolated from the topic and
other root posts. A default send targets the root at send time without a redundant reply strip;
explicit targets and failed-send text are retained. Reaction/reply refresh stays in the discussion,
reads the root's authoritative count, and rejects stale callbacks. Sparse discussion reads never
advance the full-topic read reporter. The original page's scroll and draft survive back navigation.

The self-styled loading strip was replaced by ChatActivityBlurredRoundPageDownButton, including
its loading indicator and remaining-count badge. Retry uses the existing session-aware path and
Bulletin; empty replies use ChatActionCell. A positive server count with an empty endpoint says
that no replies are currently visible, not that none exist. Default, paged, loading, retry and
empty light/dark renders were inspected. These are host renders, not GPU or frame-pacing proof.

Parent fresh full run: 338 tests, 2 failures (PostVideoTest's in-flight progress assertion and
TopicFadeBandTest's theme-switch timeout). The selected Profile/menu/reaction/thread/reply/draft/
read/paging classes contributed 108 passes and no failures in that same run. Both failing classes
passed in a subsequent isolated invocation. This demonstrates order/timing sensitivity, not
proof that they are unrelated regressions. No failing checks were disabled.

The final arm64 assemble, telegram-hook-guard, vendor-guard --quick and git diff --check passed.
Full XML results and logs are retained in .amp/in/artifacts/comments-thread-v2/parent-full-results
and parent-*.log. Review captures are grouped in .amp/in/artifacts/topic-corrections-v2/REVIEW.html.
Work remains local, uncommitted and unpushed. Hardware smoothness, GPU blur and real-site artwork
remain unverified.

### 2026-10-08 — Resumed-topic history and home folder settle (host only)

The active build is still `:nativedo` under `apps/android/telegram`, on
`spike/full-telegram-subtree`. Serial subagents implemented the changes, followed by parent
integration and additional failure reproductions. This pass changed no Telegram subtree files.
A physical device was visible to adb, but it was not driven or updated. No APK was installed and
no real-site topic was opened: all topic tests use FakeDiscourseSite so read-timing writes stay
on the fake site rather than altering the reader's linux.do history.

Ordinary topics now prefetch both sides of the stream window. Earlier rows are inserted rather
than rebinding the whole list, retaining the visible floor's pixel offset and unaffected rich
body objects. A reply-preview rebuild restores the anchor by post identity and segment ordinal
when its old message object no longer exists. The details test invokes the upstream details
block's touch handler, then verifies expanded content survives an unrelated prepend; it does
not inject a flag, but it bypasses the list-cell coordinate hit test. An empty successful slice
advances the stream cursor; a failed slice retains it. Media callbacks resolve their message's
current row rather than using a captured row index. Window/head/page generations reject stale
answers. A held replacement-window test also caught old rows starting pagination with the new
generation; stream prefetch now waits until that window finishes or fails.

The existing ChatActivityBlurredRoundPageDownButton now works for ordinary topics as well as
discussion pages. Its badge counts actual stream posts below the viewport, not a subtraction of
floor numbers that would include deleted floors. A tap targets the first unread floor after the
reader's monotonic position when it is below the viewport, otherwise the newest floor. Loaded
targets smooth-scroll; unloaded targets show the stock spinner and refuse duplicate requests.
Only the actual viewport advances reading state. Failed jumps retain rows and position, restore
the button, and show the arrow-retry Bulletin. The parent's geometry test exposed the Bulletin
covering the retry button; the Bulletin delegate now reserves the measured button band above
the composer. The final banner and button rectangles do not intersect. Discussion paging and
its sparse read-report isolation remain separate.

The home still uses FilterTabsView, the correct Telegram folder component. Comparison with
DialogsActivity.ViewPage.setTranslationX found the missing release-settle hook: finger MOVE
updated the indicator, but the page AnimatorSet after UP did not. The upstream per-frame update
is restored while tabsAnimationInProgress and only for the outgoing/front page. Profile's
existing pager-driven strip was already continuous and was not changed. DispatchTouchEvent
tests reproduce the indicator freezing during a commit, remaining half-selected after a cancel,
and losing alignment when a settling pair is grabbed again. They also verify the tab-tap clock
stays separate and a held category-feed response does not block the settle. Retained release
frames show progress 0.393, then 0.643, then 1.000; cancel frames start on the feedback category
and return to that category, with progress 0.066 then 0.000.

Parent combined feature check: 68 tests, zero failures. Final fresh full check: 367 tests across
73 classes, two failures, PostVideoTest's in-flight progress assertion and TopicFadeBandTest's
theme-switch timeout. A prior parent full run also failed DiscourseMentionsTest's watcher
cleanup assertion. All three classes then passed alongside the relevant feature classes in the
final selected run: 85 tests across ten classes, zero failures/errors. These results demonstrate
order/timing sensitivity, not proof that the failures are pre-existing or harmless. No failing
checks were disabled or their assertions weakened.

Final arm64 assemble, telegram-hook-guard, vendor-guard --quick and git diff --check passed.
APK SHA-256: 3224a13f13d5aab9496d107b0b404e4055e7ec7a33333de371cdf3130d40b8c5.
Logs and XML are under .amp/in/artifacts/topic-history/parent-final-*; the inspected topic states
and home settle frames are grouped in .amp/in/artifacts/topic-history/REVIEW.html. Reproduction
tests are TopicHistoryPagingTest, TopicPageDownTest and HomeCategoryPagerTest. These are host
renders and simulated-clock checks; physical frame pacing, GPU blur and real network latency
remain unverified. Work is local, uncommitted and unpushed.

### 2026-10-08 — Ordinary-topic repeated paging failure (host only)

A serial subagent reproduced the repeated failure hint in the production TopicFragment with
FakeDiscourseSite. Before the fix, small near-edge scrolls made seven requests for the same
failed stream slice, both forward and backward. The failure cleared the busy flag without
advancing the cursor or blocking another automatic attempt. After the fix, each direction
makes one request per failed edge visit. Layout callbacks and small nudges do not re-ask;
actual scrolling away from that threshold re-arms it. Successful pages still load normally.

The stock Telegram Bulletin now carries an explicit retry button. The failed direction's
rows, cursor and reading anchor stay. Each hint captures its own failure, window and cursor,
rather than overwriting the full-screen retry. Challenge/login failures use the existing
WebView recovery. Parent tests reproduced two additional defects: a stale challenge action
opened login after a window switch, and an old hint requested the next slice after the failed
page had already arrived. Window/cursor/in-flight guards now reject both before any recovery
navigation and again after recovery. The queued-challenge test captures the real Bulletin
button callback to exercise the timing boundary; it does not click an already detached button
and mistake upstream's no-op for an application guard.

Parent combined check: 63 tests across five classes, zero failures. Fresh full check: 374 tests
across 73 classes, five failures: TopicPageDownTest's Bulletin/button-bounds nonintersection,
two DiscourseMentionsTest cases (paused-query and avatar arrival), PostVideoTest's in-flight
progress, and TopicFadeBandTest's theme switch. Rechecking those classes with the affected
classes produced 80 tests across eight classes, one failure: the same Bulletin/button-bounds
assertion, with a 10-pixel intersection. Its capture was inspected: the visible badge and arrow
are not obscured, but the existing bounds contract still fails. It was not weakened or disabled.
Both ordinary stream-paging classes passed all 22 cases in every parent combined/full run.
These differing results do not prove the remaining failures are pre-existing or harmless.

Arm64 assemble, telegram-hook-guard, vendor-guard --quick and git diff --check passed.
APK SHA-256: 257cc325c060404fbe1d76c0e53edd935bf5e07da04a75fb2bb6ab16a97314ac.
Reproduction tests are TopicPagingTest and TopicHistoryPagingTest. Parent logs and XML are
retained under .amp/in/artifacts/topic-scroll-failure/parent-*; inspected failure and recovery
captures are grouped in .amp/in/artifacts/topic-scroll-failure/REVIEW.html. This pass changed
no Telegram subtree files. No APK was installed and no real-site topic or reading history was
touched. The user's original transport/session failure and physical-device behavior remain
unverified. Work remains local, uncommitted and unpushed.

### 2026-10-08 — Rich-message whitespace and inline emoji (host only)

Serial subagents reproduced extra blank lines and indented line starts through the production
TopicFragment. Jsoup's pretty-printer inserted whitespace after a cooked `<br>`, and Telegram's
RichHtml importer preserved it in addition to the explicit break. Both HTML serializers now use
non-pretty output. The cooked boundary collapses ordinary whitespace across inline bold/link
elements and trims around breaks while preserving code/pre, NBSP, fullwidth spaces and explicit
consecutive breaks. Tests written before these whitespace changes compare independent canonical
text, block origins and drawn height with a direct stock RichHtml import. Telegram's layout
constants and paragraph-spacing implementation were not changed.

Cooked `img.emoji` had been replaced by its alt/title shortcode, so the importer never received
artwork. The bridge now downloads the site's picture using the existing session image client and
maps decodable cached images to upstream `animated-emoji`/AnimatedEmojiSpan documents. Documents
are seeded in memory before attachment; host tests assert no Telegram native send, init or database
open. Standard and custom pictures remain distinct, and warm reopen performs no redownload. Raw
shortcodes resolve only against the loaded site's catalog; unknown names and code literals remain
text. Missing/unsupported URLs, refused requests and non-picture responses keep readable labels.
Static thumbnails are supported; SVG and Lottie/tgs remain labels, not animation support.

Parent tests added before further corrections caught two real regressions: artwork arrival moved
the reader from an attached floor 4008 to 4006, and a missing src discarded its label. A subsequent
real-cell details tap caught the same arrival closing expanded content. The emoji refresh now
preserves the first visible row's pixel offset and the floor's details states. The final test starts
at position 8 / offset -37 and identifies the visible cell through LayoutManager, rather than
assuming the first attached child is visible. Only missing artwork is queued, deduplicated by id;
an already-cached emoji cannot trigger an unrelated failed label's rebuild.

Final combined check: 82 tests across nine classes, zero failures. Separate DiscourseMentionsTest
recheck: nine tests, zero failures. Full check: 380 tests across 74 classes, three failures:
PostVideoTest's in-flight progress, TopicFadeBandTest's theme switch, and DiscourseMentionsTest's
paused query. The prior full run also failed TopicPageDownTest's Bulletin/button-bounds contract.
These order/timing differences do not establish that failures are pre-existing or harmless.
No failing assertion was disabled or weakened. Original worker emoji checks were written after
implementation and demonstrated a disabled-gate failure, not an original-code red run; the
parent's actual regression red XML is retained separately.

Arm64 assemble, telegram-hook-guard, vendor-guard --quick and git diff --check passed.
APK: `apps/android/nativedo/build/intermediates/apk/debug/nativedo-debug.apk`.
SHA-256: `d9d846510caee270a3c5e7141880b3ff74fb02f359000a951200509c6497c7f0`.
Light/dark whitespace, emoji, loading/upgrade, expanded-details, fallback and literal-code captures
were inspected and grouped in `.amp/in/artifacts/rich-message-layout/REVIEW.html`; parent logs/XML
are alongside it. Emoji captures use synthetic color images to verify decoding and layout, not
real linux.do artwork. No Telegram subtree file changed in this pass. No APK was installed, no
real-site reading history was written, and physical frame pacing/network behavior remain unverified.
Changes are local, uncommitted and unpushed.

### 2026-10-08 — Commit preparation checkpoint (macOS host only)

The accumulated work is grouped into two local commits: pinned dependency/build tooling and CI,
then application features, upstream host hooks, contracts and their verification records. This
checkpoint supersedes the earlier waves' delivery statuses; their test results remain historical.
The organization pass changes no application behavior. Root build instructions now distinguish
the active `apps/android/telegram` / `:nativedo` build from the legacy `:app` stack.

The print-only CategorySelectorDiagTest was moved out of test discovery, without deleting it.
Loose root recordings and the `frames/` directory are retained under
`.amp/in/artifacts/precommit/retained/`; the diagnostic source and keyboard notes are under
`.amp/in/scratch/precommit/`. These local artifacts are excluded from Git, not bundled with code.

Fresh full `:nativedo:cleanTestDebugUnitTest :nativedo:testDebugUnitTest --quiet`:
379 tests across 73 classes, three failures, zero errors/skips. Failures were PostVideoTest's
in-flight progress assertion, TopicFadeBandTest's theme-switch timeout, and TopicPageDownTest's
Bulletin/button-bounds nonintersection assertion. The latter reported
`Rect(0, 2358 - 1233, 2550)` against `Rect(1065, 2370 - 1233, 2562)`.
Rechecking those three classes together with DiscourseMentionsTest passed all 28 tests.
The selected pass does not turn the full-suite result green or prove those failures harmless.
Neither assertions nor gates were disabled. Both runs' XML and the original full report are
retained under `.amp/in/artifacts/precommit/`.

All 85 Python script tests passed, including the 40 dependency/hook-tool cases. Both guards
passed via an explicit `.githooks/pre-commit` invocation: this checkout's configured `.git/hooks`
directory has no installed pre-commit hook. Shell syntax, CI YAML parsing and `git diff --check`
passed. `scripts/telegram-deps.sh --verify` failed on all 15 existing dependency trees because
they have no pinned-content stamps. Their source provenance remains unverified; no stamps were
manufactured and no existing dependency tree was replaced. Linux orb setup and fresh-clone CI
were not executed on this macOS runner.

The documented arm64 assemble command passed; inspecting the APK confirmed that both native
libraries are under `lib/arm64-v8a/`, with no other ABI packaged. A preceding default-ABI assemble
also completed. Logs are `app-arm64-assemble.log` and `app-assemble.log` in the checkpoint artifacts.
APK: `apps/android/nativedo/build/intermediates/apk/debug/nativedo-debug.apk`.
SHA-256: `0e9f397a70cd197668810c27c047edf67f9bda5eab1066d7b5f5c95bea25fd37`.

No APK was installed, no real-site reading history was written, and no commit was pushed.

### 2026-10-08 — Notifications, own posts, private messages and offline reads (Linux orb only)

Continued the four-feature plan on `spike/full-telegram-subtree`, using separate worker worktrees
and integrating their changes into the main checkout. Builds and verification ran sequentially.
The active target is `apps/android/telegram` / `:nativedo`, not the legacy `:app`. No Telegram
subtree files changed; upstream cells, menus, dialogs, Bulletins and composer/editor components
remain the UI infrastructure, with tgnet and Telegram's database disabled.

Implemented scope:

- Notifications: server-confirmed single/all mark-read, navigation after success, readable retry,
  server count readback without restarting MessageBus; topic notification levels 0–3 and home
  new/unread feeds.
- Own posts: raw edit loading, existing composer/rich editor, update/delete/recover according to
  server permissions, retained drafts on refusal, delete readback without replaying the write,
  and stale-account/window callback guards. Bodyless DELETE 204 uses Retrofit's `Unit` path.
- Native PMs: inbox/sent/unread routes, settings/profile entrances, existing composer/uploads,
  site/account-scoped drafts and session guards. Sent messages retain unread replies. The header
  names the active box; the flow switches inbox → sent → unread and checks the drawn rows.
- Offline reads: network-first wire-payload cache for public feeds/topics/post slices, scoped to
  site, reader and exact read request. Only transport failure may fall back; refusal, challenge,
  parse error and session change may not. Logout clears the cache and rejects late writers under
  the same lock. Cached permissions stay read-only, including mixed cached/live pages; drafts
  survive, and persistent markers plus explicit retry remain until a live replacement arrives.
  PM and notification lists are not cached.

**Not implemented: OS-level background push.** Foreground MessageBus is not background delivery.
No native push bridge/gateway is configured; this work does not add a battery-heavy polling service
or claim reliable delivery after the app is suspended or killed.

Final targeted command (from repository root):

```bash
./apps/android/telegram/gradlew -p apps/android/telegram \
  -Dorg.gradle.jvmargs='-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
  --init-script /tmp/nativedo-verification.gradle :nativedo:testDebugUnitTest \
  --tests '*NotificationsActionsTest' --tests '*TopicNotificationLevelTest' \
  --tests '*HomeFeedModesTest' --tests '*OwnPostManagementTest' \
  --tests '*PrivateMessagesTest' --tests '*PrivateMessageComposerTest' \
  --tests '*OfflineReadingTest' --tests '*OfflineCacheIdentityTest' \
  --tests '*MessageBusSessionTest' --quiet
```

Result: **65 tests / nine classes, zero failures/errors/skips**, after the final FAB-aware Bulletin
offset and PM-header fixes. XML and log are retained in
`.amp/in/artifacts/nativedo/verification/targeted-final/` and `targeted-final.log`.
Regression red XML is retained as `mixed-cache-red.xml`, `offline-overlap-red.xml` and
`pm-box-header-red.xml`; the last reproduced a missing active-box subtitle before its fix.

An ordinary full run was OOM-killed; the test JVM reached roughly 18 GiB RSS and kernel evidence
confirmed the kill (`full-oom.log`). Bounded verification used the temporary init script below,
plus the daemon flags above. Recreate this script to repeat the command; it is not a production
build setting. The full command is the targeted command with all `--tests` filters removed.

```groovy
gradle.projectsEvaluated {
    allprojects {
        tasks.withType(Test).configureEach {
            maxParallelForks = 1
            forkEvery = 5
            maxHeapSize = '4g'
        }
    }
}
```

The retained bounded full run, before the last two UI fixes, is **439 tests / 81 classes, five
failures**, zero errors/skips (`verification/full-final/`, `full-final.log`):

- `DiscourseEmojiRenderTest.cookedStandardAndCustomEmojiRenderAsArtworkBesideTextRuns`: null cell cast.
- `HomeTabsTest.theSettingsTabOpensOurOwnSettingsScreen`: expected two feed reads, got three.
- `TopicRepliesThreadTest.anOverlappingPageThenAnEmptyPageEndsTheThreadWithoutDuplicating`:
  terminal reading affordance still visible.
- `TopicRepliesThreadTest.theComposerSendsToTheRootFloorByDefaultAndHonoursAnExplicitTarget`:
  explicit reply target expected floor 15, got 12.
- `ComposerCapsuleTest.systemInsetsMoveTheIslandOnceAndKeepTheSendButtonAboveTheKeyboard`:
  expected 2646, got 2715.

Rechecking those four classes produced **41 tests, four failures** (`failures-recheck/` and log):
the emoji failure and both thread failures remained; HomeTabs' keyboard-hide inset assertion
failed instead (expected 2678, got 2715). Its settings-read assertion and ComposerCapsule passed.
There is no unmodified baseline establishing whether these are pre-existing. Neither assertions
nor gates were weakened, and the selected green run does **not** make the full gate green.

Arm64 assemble passed with:

```bash
./apps/android/telegram/gradlew -p apps/android/telegram \
  -Dorg.gradle.jvmargs='-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
  :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a --max-workers=2 --quiet
```

APK: `apps/android/nativedo/build/intermediates/apk/debug/nativedo-debug.apk` (62 MiB).
SHA-256: `2c0462bbc486bff9bd4dff8ab211b7d8e0bd6afc00a1f610fa769b7b97efb726`.
ZIP inspection found only `lib/arm64-v8a/libtmessages.49.so` and
`lib/arm64-v8a/liblanguage_id_l2c_jni.so`. Log: `verification/arm64-assemble.log`.
All 85 Python script tests passed with
`python3 -m unittest discover -s scripts/tests -t scripts/tests -p 'test_*.py'`.
`telegram-hook-guard.sh`, `vendor-guard.sh --quick`, `telegram-deps.sh --verify` (15 pins, zero
problems), and both staged/unstaged `git diff --check` passed.

Inspected host renders under `.amp/in/artifacts/nativedo/` include notifications and mark-read
success/refusal, notification-level selection, new/unread feeds, own-post edit/delete/recover
and refusals, PM boxes/menu/composer/refusal, and cached home/topic light/night states. Final
review surfaces:

- Notifications: [all read](../.amp/in/artifacts/nativedo/notifications-all-read.png),
  [notification levels](../.amp/in/artifacts/nativedo/topic-notification-level-menu.png).
- Own posts: [edit](../.amp/in/artifacts/nativedo/own-post-edit.png),
  [delete](../.amp/in/artifacts/nativedo/own-post-deleted.png),
  [recover](../.amp/in/artifacts/nativedo/own-post-recovered.png).
- PM: [inbox](../.amp/in/artifacts/nativedo/private-messages-inbox.png),
  [sent](../.amp/in/artifacts/nativedo/private-messages-sent.png),
  [unread](../.amp/in/artifacts/nativedo/private-messages-unread.png),
  [menu](../.amp/in/artifacts/nativedo/private-messages-menu.png),
  [composer](../.amp/in/artifacts/nativedo/private-message-composer.png),
  [refusal](../.amp/in/artifacts/nativedo/private-message-refused.png).
- Offline: [home light](../.amp/in/artifacts/nativedo/home-offline-cached.png),
  [home night](../.amp/in/artifacts/nativedo/home-offline-cached-night.png),
  [topic light](../.amp/in/artifacts/nativedo/topic-offline-cached.png),
  [topic night](../.amp/in/artifacts/nativedo/topic-offline-cached-night.png).

The final light home capture shows the persistent offline marker and retry clear of the tabs;
the test also checks retry/FAB bounds. The FAB is not drawn in this capture, so the image alone
does not prove FAB clearance. The night home capture retains the marker after the transient
Bulletin expires. Topic-night renders still have white wallpaper although the header, bubbles,
composer and marker recolor; complete night appearance remains unresolved. Synthetic avatars
are intentional fixtures. Dialog/menu captures are harness composites, not proof of physical
window placement or GPU rendering. Screenshots are retained review artifacts, not committed
Paparazzi goldens or evidence of real-site writes.

No device/emulator was available, no APK was installed, and no real-site mutation was performed.
Physical IME/insets, background delivery, Cloudflare/session recovery against linux.do and real
network behavior remain device/site checks. Changes are local, uncommitted and unpushed; the
full regression gate and complete night appearance are not cleared for release.

### Telegram-style PM, public chat, inline emoji and app links (2026-10-08, Linux orb)

Continued the previous feature wave in the same checkout, with separately owned subagent work
and serial Gradle integration. Public channels mean **linux.do Discourse Chat**, not Telegram
accounts or network channels. Site enablement and account permissions have not been checked
against linux.do; an unavailable/refused API has an explicit retry/recovery state.

Delivered locally:

- PM uses Telegram DialogCell rows, inbox/sent/unread tabs, an inset-aware compose FAB and an
  overflow entry to public channels; PM and chat lists/conversation recolor for day/night.
- Public channels support listing, joining, text conversation/reply targeting, sending and
  readback, older history and manual refresh. Initial reads use the latest window. Accepted
  message readback omits `direction=past`, because that exclusive cursor drops the accepted id.
  A failed readback never resends the accepted write. Channel upload/video interaction remains
  unimplemented; MessageBus attachment depends on the current foreground poller, with manual
  refresh as fallback. This is not background OS push.
- Draft ownership is captured rather than read from the current account at save time. Session
  loss saves the owner's draft, clears/hides the field and fences stale callbacks; join/send/read
  abort if the session changes during CSRF acquisition. Red chat/send/session/CSRF results are
  retained under `telegram-style/verification/` before their production fixes.
- Inline body emoji artwork fits its surrounding font metrics and follows larger fonts. Only
  the one-line host call was added to upstream RichMessageLayout; button emoji and MTProto mode
  keep upstream sizes. Test screenshots use controlled solid PNGs, not real laugh artwork.
- Debug settings restore curated render topics and validated ID/link/floor jumps. App-owned
  `nativedo://topic/<id>[/<floor>]` and `linuxdo://topic/<id>[/<floor>]` routes work alongside
  same-origin HTTPS topics. Merged-manifest tests reject tg/tgb/tonsite and Telegram domains;
  cold/warm entry, login-pending navigation and recreation are covered.

The final selected regression is **125 tests / 16 classes, zero failures/errors/skips**. It
covers notifications, topic notification levels, feed modes, own-post management, PM list and
composer, offline reading/cache identity, MessageBusSession, public channels/conversation,
emoji, comment threads, debug tools, app routing and URL validation. Reproduce from repository
root with the temporary init script and daemon flags recorded immediately above:

```bash
./apps/android/telegram/gradlew -p apps/android/telegram \
  -Dorg.gradle.jvmargs='-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8' \
  --init-script /tmp/nativedo-verification.gradle :nativedo:testDebugUnitTest \
  --tests '*NotificationsActionsTest' --tests '*TopicNotificationLevelTest' \
  --tests '*HomeFeedModesTest' --tests '*OwnPostManagementTest' \
  --tests '*PrivateMessagesTest' --tests '*PrivateMessageComposerTest' \
  --tests '*OfflineReadingTest' --tests '*OfflineCacheIdentityTest' \
  --tests '*MessageBusSessionTest' --tests '*ChatChannelTest' --tests '*PublicChannelsTest' \
  --tests '*DiscourseEmojiRenderTest' --tests '*TopicRepliesThreadTest' \
  --tests '*DebugMenuTest' --tests '*DeeplinkRoutingTest' --tests '*DiscourseLinksTest' --quiet
```

Log and XML: `.amp/in/artifacts/telegram-style/verification/targeted.log` and `targeted/`.
The temporary init script was removed after verification; its content above is retained to
make the run repeatable, not added to the production build.

Final visual review found the debug fixture sheet's bottom-aligned child obscured its first row.
A first-row/title bounds assertion reproduced this (`debug-sheet-red.xml`); top alignment fixes
it. The debug class was rerun separately after this last change: **three tests, zero failures**
(`debug-sheet-final.log` / `.xml`). The new sheet capture shows the first row and the rest of the
fixture list fully below its title; the APK below was rebuilt after that correction. The 125-test
selected run preceded this final debug-only alignment change.

The retained full run is **468 tests / 86 classes, three failures**, zero errors/skips
(`telegram-style/verification/full.log` and `full/`). This ran before the final cached-emoji
reopen wait and debug screenshot-scroll corrections:

- `DiscourseEmojiRenderTest.cookedStandardAndCustomEmojiRenderAsArtworkBesideTextRuns`: reopened
  topic had no loaded cell when the artwork wait dereferenced it. The wait now first requires
  a loaded cell; the final six-test emoji class passes, with unchanged artwork/text/cache assertions.
- `HomeTabsTest.theSettingsTabOpensOurOwnSettingsScreen`: expected one feed read, got two.
- `ComposerCapsuleTest.systemInsetsMoveTheIslandOnceAndKeepTheSendButtonAboveTheKeyboard`:
  expected 2646, got 2715.

The two comment-thread failures recorded in the previous wave now pass after awaiting the
processed terminal page / accepted-reply reload, not merely request arrival or a row count that
was already true. Assertions were not weakened. The user's intermittent large blank comment
line was **not reproduced**: no paragraph breaks, reply-preview gaps or comment footers were
removed on conjecture. A failing topic ID/floor and screenshot remain needed. Ordinary topic
night renders still show white wallpaper; PM and public chat night surfaces are fixed. The
selected green result is **not** a green full-suite gate or device acceptance.

Arm64 assemble passed using the command in the previous record. Retained APK:
`.amp/in/artifacts/nativedo/nativedo-debug-arm64.apk` (66 MiB), SHA-256
`fdeffe479346b92c546eca9196131707b50a3f17ce4edc8811a0fd5d625a6e76`.
Only `lib/arm64-v8a/libtmessages.49.so` and `liblanguage_id_l2c_jni.so` are packaged.
Log: `telegram-style/verification/assemble.log`. All 85 Python script tests, hook guard,
vendor guard, 15 dependency pins and staged/unstaged whitespace checks passed.

Inspected final host renders (synthetic fixture content; not physical IME/GPU evidence):

- PM: [inbox light](../.amp/in/artifacts/nativedo/pm-telegram-light.png),
  [night](../.amp/in/artifacts/nativedo/pm-telegram-night.png),
  [sent](../.amp/in/artifacts/nativedo/pm-telegram-sent.png),
  [unread](../.amp/in/artifacts/nativedo/pm-telegram-unread.png),
  [overflow](../.amp/in/artifacts/nativedo/pm-telegram-menu.png).
- Chat: [conversation light](../.amp/in/artifacts/nativedo/chat-conversation-light.png),
  [night](../.amp/in/artifacts/nativedo/chat-conversation-night.png),
  [channel list light](../.amp/in/artifacts/nativedo/chat-channels-light.png),
  [channel list night](../.amp/in/artifacts/nativedo/chat-channels-night.png),
  [joined](../.amp/in/artifacts/nativedo/chat-join.png),
  [accepted send](../.amp/in/artifacts/nativedo/chat-send.png),
  [refusal](../.amp/in/artifacts/nativedo/chat-channel-error.png).
- Emoji: [light](../.amp/in/artifacts/nativedo/render-fixes-emoji-light.png),
  [night](../.amp/in/artifacts/nativedo/render-fixes-emoji-night.png).
- Debug: [section](../.amp/in/artifacts/nativedo/debug-menu-section.png),
  [fixture sheet](../.amp/in/artifacts/nativedo/debug-menu-fixtures.png),
  [validated input](../.amp/in/artifacts/nativedo/debug-menu-input.png).

No device was available, APK installed, real-site write performed or OS push claimed. All
changes remain local, uncommitted and unpushed. The full gate, white topic-night wallpaper and
comment reproduction remain open; this is a review/debug build, not release clearance.

## 2026-10-09 — topic 2998428 heading-photo regression (Linux host)

The public HTML of `https://linux.do/t/topic/2998428` places its first lightbox inside an
`h2` after a bold label and a line break. Telegram's `RichHtml` imports that heading as text
only, dropping the nested photo. A production-wired `TopicImageTest` reproduced the missing
photo before the fix; the retained red log is
`.amp/in/artifacts/topic-2998428/verification/heading-red.log`.

The NativeDo image bridge now splits text wrappers around prepared lightboxes, preserving
text order/styles and omitting empty fragments. Ordinary inline images retain their existing
behavior; Telegram's subtree is unchanged. The regression serves a cyan 1600×900 original and
a magenta preview: it checks that the original is drawn, a tap opens the upstream viewer, and
the viewer decodes the original's dimensions and pixels. The inspected
[final render](../.amp/in/artifacts/topic-2998428/heading-image-fixed.png) uses this controlled
image, not the real post's artwork; surrounding text is visible without an extra empty heading.

Reproduce the selected gate from the repository root:

```bash
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:testDebugUnitTest \
  --tests '*TopicImageTest' --tests '*DiscourseCookedHtmlTest' \
  --tests '*TopicBodyFidelityTest' --tests '*IncomingReplyTest' \
  --tests '*TopicRepliesThreadTest' --quiet
./apps/android/telegram/gradlew -p apps/android/telegram :nativedo:assembleDebug \
  -Pandroid.injected.build.abi=arm64-v8a --quiet
```

The selected gate passed **46 tests / five classes, zero failures/errors/skips**; XML and log
are retained under `.amp/in/artifacts/topic-2998428/verification/`. Arm64 assemble exited zero.
This is not a new full-suite result or a physical-device acceptance pass.
The arm64-only APK is `.amp/in/artifacts/topic-2998428/nativedo-heading-image-debug-arm64.apk`,
SHA-256 `b581a9872cdf2a6a634c868bc6b5ef64081128b73400eed45739da8d9b34dbeb`.

The user's two comment screenshots show a blank reply-preview slot while the child's author,
body and timestamp are already drawn; the later screenshot fills the same slot. The ordinary
host comment render does not reproduce that quote-only delay. An initial-frame probe instead
observed a whole-row insertion fade, which is a different symptom and was removed rather than
used to justify disabling animations. No comment-animation change is claimed by this fix.
The separate Mac runner's read-only diagnosis found only an emulator, with an older ordinary
APK and a separate audit-modified review APK; neither proves behavior at this orb's HEAD.
The parent-floor public JSON request was blocked by Cloudflare, so there is no actual payload
or matching-version failure-frame capture. See the imported
[read-only diagnostic](../.amp/in/artifacts/topic-2998428/comment-delay-diagnostic.md).
The next reproduction needs a matching APK plus parent/reply payloads and failure-frame
layout/draw state; missing-target network delay and upstream drawing gates remain hypotheses,
not established causes.
This was the initial narrow pass; the broader rendering and delivery status is recorded below.

## 2026-10-09 — cooked HTML structure and semantics regression (Linux host)

The broader pass preserves media and text order in headings and paragraphs, ordinary content
images and linked-image navigation, generic wrapper block boundaries, and media nested inside
lists, quotes and details. Adjacent direct-text `div`s remain separate paragraphs rather than
being concatenated. Telegram's subtree, measurement and drawing code are unchanged.

Discourse inline/block spoilers now retain covered text and media without flattening their
containers. Hidden table-media fallback links and hidden attachment links stay covered.
Fenced `lang-*` / `language-*` metadata reaches the upstream code block; indentation and inner
line breaks are retained. Upstream's current rich-code style does not draw a separate language
header; it uses the language for highlighting and the accessibility label.

The rich model supports inline text, not media blocks, inside table cells. Images/videos there
therefore remain at their authored position as source links. Unsupported `audio`, `iframe`,
`embed` and `object` content gets a readable HTTP(S)/site-relative link or a no-source fallback;
unsafe embed schemes stay non-clickable text. This is compatibility with cooked Discourse
content, not a claim of arbitrary browser HTML/CSS parity or embedded audio playback.

The final selected gate passed **74 tests / 12 classes, zero failures/errors/skips** after the
linked-image, nested-spoiler and adjacent-div corrections. Retained log and XML:
`.amp/in/artifacts/cooked-content/verification/selected.log` and `selected-xml/`.
Failing-before-fix evidence is retained in that directory as `structure-red.log`,
`linked-image-red.log`, `table-spoiler-red.log`, `nested-spoiler-red.log` and
`block-boundary-red.log`. The tests drive production topic fragments, API parsing and upstream
cells against `FakeDiscourseSite`, including photo-viewer original decoding, spoiler reveal and
horizontal table dragging; they are host integration checks, not physical-device passes.

For a conservative rerun, create this temporary init script outside the repository:

```groovy
// /tmp/nativedo-test-limits.gradle
gradle.projectsEvaluated {
    allprojects {
        tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
            maxHeapSize = '2g'
            maxParallelForks = 1
            forkEvery = 4
        }
    }
}
```

Run Gradle sequentially from the repository root:

```bash
./apps/android/telegram/gradlew -p apps/android/telegram \
  -I /tmp/nativedo-test-limits.gradle \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' --no-daemon --max-workers=2 \
  :nativedo:testDebugUnitTest --tests '*TopicImageTest' --tests '*TopicBodyFidelityTest' \
  --tests '*TopicCookedSemanticsTest' --tests '*DiscourseCookedHtmlTest' \
  --tests '*PostVideoTest' --tests '*AttachmentRenderTest' --tests '*DiscourseEmojiRenderTest' \
  --tests '*WideTableLayoutTest' --tests '*PollTest' --tests '*OneboxTest' \
  --tests '*IncomingReplyTest' --tests '*TopicRepliesThreadTest' --quiet
# Omit the --tests filters for the full suite, keeping the same limits.
./apps/android/telegram/gradlew -p apps/android/telegram \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' --no-daemon --max-workers=2 \
  :nativedo:assembleDebug -Pandroid.injected.build.abi=arm64-v8a --quiet
```

Two unsupervised full-run attempts ended with a disappeared Gradle daemon amid executor
disconnects; neither is a full-suite result, and an OOM/whole-orb restart was not established.
The final full run and sequential arm64 build were moved to the ad-hoc supervised
`cooked-verification` service so client reconnection does not own their lifetime. The full run
completed with **478 tests / 87 classes, two failures, zero errors/skips**. Log and XML are
retained in `cooked-content/verification/full.log` and `full-xml/`:

- `HomeTabsTest.theSettingsTabOpensOurOwnSettingsScreen`: expected two feed reads, got three.
  The earlier full run also failed this test, then expecting one and getting two.
- `HomeTabsTest.systemInsetsAndKeyboardLeaveTheTabsAndReplyReachable`: "the reply stays above
  the keyboard" failed. This is not the earlier `ComposerCapsuleTest` inset failure, and is not
  described as a proven pre-existing failure or attributed to a new root cause.

All other tests passed, including this rendering wave. Neither failure was hidden or fixed
outside this scope; the full-suite gate remains red.

Arm64 assemble exited zero. The retained 68,354,491-byte APK is
`.amp/in/artifacts/cooked-content/nativedo-cooked-debug-arm64.apk`, SHA-256
`95ffa9ad0a4a14965497282ccfe2fec6bd159134f5f2dcc7af4e8b18294ce579`.
Only `lib/arm64-v8a/libtmessages.49.so` and `liblanguage_id_l2c_jni.so` are packaged.
The build log is `cooked-content/verification/assemble.log`. The temporary verification
service was stopped after completion; its script and the invocation-only init script were
removed, leaving the production Gradle configuration unchanged.

Inspected host captures (controlled fixture artwork, not the real post's images):

- [Wrapped heading photo](../.amp/in/artifacts/topic-fidelity-wave/photo/photo-wrapped-heading-cell.png)
  and [bare image/video/document](../.amp/in/artifacts/topic-fidelity-wave/photo/photo-bare-media-cell.png).
- [Generic wrapper and distinct paragraphs](../.amp/in/artifacts/rich-message-layout/body-generic-wrapper-table.png)
  and [nested list/quote/details media](../.amp/in/artifacts/rich-message-layout/body-nested-media.png).
  The nested-media capture reaches the viewport bottom; the remaining body is scrollable.
  `list-omega` continues the same list item after its image, so it intentionally has no new bullet.
- Table source-link [initial](../.amp/in/artifacts/rich-message-layout/body-table-cell-source-link.png)
  and [after horizontal drag](../.amp/in/artifacts/rich-message-layout/body-table-cell-source-link-scrolled.png);
  the trailing `after-cell` column is visible in the latter.
- Spoiler [covered](../.amp/in/artifacts/nativedo/cooked-spoiler-hidden.png) and
  [revealed](../.amp/in/artifacts/nativedo/cooked-spoiler-revealed.png),
  [code indentation](../.amp/in/artifacts/nativedo/cooked-code-language.png), and
  [embed fallbacks](../.amp/in/artifacts/nativedo/cooked-embed-fallbacks.png).

Some fixture captures include an unrelated pagination retry banner; no pagination fix is
claimed here. The user's intermittent quote-only blank preview remains unverified and is not
claimed fixed by these HTML changes. No device install or real-site write was performed.

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

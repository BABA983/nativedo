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

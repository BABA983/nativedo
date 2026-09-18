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
and S4 slice 1 draws a mapped Discourse post through `RichMessageLayout.PreviewView` in the
snapshot suite - so the pipeline is verified in pixels, but **no app screen uses it yet**. What a
device has to settle is the cell switch: the post body becoming this renderer behind a flag.

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
4. Selection inside a rich message uses our 12.6 `TextSelectionHelper.TextLayoutBlock` shape;
   handles/prefixes are therefore expected to behave like the current text blocks, not like
   upstream 12.10.2's selection.

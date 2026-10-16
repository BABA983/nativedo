# Content rendering and composition

## Two representations, one server authority

Discourse stores editable Markdown as `raw` and renders it as `cooked` HTML.
Read `cooked`; edit the server's `raw`; write Markdown and render the returned
cooked result. Reconstructing raw from rendered HTML loses information, and
writing HTML as raw breaks web-composer compatibility. Wire details remain in
the [endpoint](../contracts/discourse/endpoints.md) and [model](../contracts/discourse/models.md) contracts.

```diagram
┌─────────────────┐    ┌──────────────────┐    ┌──────────────────────┐
│ Discourse cooked│───▶│ HTML/model bridge│───▶│ Upstream message UI  │
└─────────────────┘    └──────────────────┘    └──────────────────────┘
┌─────────────────┐    ┌──────────────────┐    ┌──────────────────────┐
│ Discourse raw   │◀──▶│ Markdown bridge  │◀──▶│ Composer / RichEditor│
└─────────────────┘    └──────────────────┘    └──────────────────────┘
```

## Reading uses upstream models

[DiscourseCookedHtml](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscourseCookedHtml.java)
normalizes HTML for upstream `RichHtml` / `TL_iv`. Presentational wrappers must
not hide their children from the parser; semantic containers must not be flattened
indiscriminately. [DiscoursePostBody](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscoursePostBody.kt)
splits polls and handles Discourse-specific markup. Link previews (oneboxes) stay
inside the post's rich bubble, between its surrounding paragraphs: site name,
thumbnail, linked title and description. Only the matching bare-link placeholder
is removed; ordinary links and other previews remain in authored order. A preview
is not a second Discourse post. Polls still need separate bubbles because upstream
cannot draw a poll as a rich-text block. [DiscourseRows](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscourseRows.java)
then adapts posts into the presentation objects upstream `ChatMessageCell` draws.

Discourse quote asides retain their author and quoted blocks, including lists and
media; they are not converted to decorative pull-quotes. An existing attribution
link is preserved. When it is absent, positive `data-topic` / `data-post` values
make the attribution a link to that topic and floor (not a post ID). `<details>`
uses upstream's expandable details block and remeasures the retained message when
toggled. These are separate semantics: `data-full` identifies a whole-post quote,
not a collapsed one. Automatic three-line quote folding and fetching an expanded
original for a truncated Discourse quote are not implemented; the native reader
currently shows the quoted content supplied in `cooked`.

Media bridges supply the local paths and stable identities upstream loaders expect,
with Discourse-aware downloads. Photos, document blocks and PhotoViewer use those
paths, not fake Telegram downloads. A site's smaller photo preview can appear while
its original downloads; the original replaces it in the retained bubble and open
PhotoViewer. The HTTP transfer drives upstream progress, cancel and tap-to-retry
controls. Failed, cancelled or non-image responses never become a completed photo
cache entry; duplicate consumers share the transfer and its outcome. Fetching video
bytes remains a separate action from rendering its cover. Never retain recycled
view/block instances as a data cache.

PhotoViewer groups the media of one rich message into upstream's thumbnail strip;
single-media messages have no strip, and neighbouring posts are not combined.
Thumbnail receivers use downloaded local previews or originals, never synthetic
Telegram datacenter locations. Preview completion is published under the preview's
own file identity. Newly available files rebind the strip's existing receivers
without clearing its current picture or gesture state. Upstream still owns the
image backdrop, progress overlay, zoom, swiping and transitions.

GIF post images use upstream's animated-file loader in the bubble and PhotoViewer;
a static optimized preview stays static while the GIF original downloads. Avatars
prefer the site's `animated_avatar` asset over its sized still template, and a GIF
template is animated too. Static assets keep the ordinary bitmap path. Both kinds
are downloaded by the session-aware client; upstream ImageReceiver owns playback
and detach/recycling, not a second player or Telegram download service.

Polls and reactions are site resources despite their Telegram-shaped controls.
Respect server permissions, topic `valid_reactions`, hidden results and incomplete
voter/user lists. Topic posts and comment replies resolve reaction IDs through the
same site emoji catalog even when the replies endpoint omits individual icon URLs.
A drawn upstream affordance is not proof its Telegram-account
action is supported by this host; rank-based polls and richer voter UI need their
own product wiring rather than pretending a tap supplies the missing semantics.

## Writing owns text and account identity

Reply, new-topic and private-message composition share upstream
`ChatActivityEnterView` and the rich editor. [DiscourseRichText](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscourseRichText.kt)
bridges raw to editor blocks and back; upload handles remain literal Discourse
Markdown, not Telegram media IDs. Unsupported Telegram-only controls must not
silently discard text or create unusable content.

`composer` is the host's name for this writing area, not a replacement component:
the field is Telegram's `ChatActivityEnterView`. Its send lifecycle hides the top
reply/edit panel before calling the host. Unlike Telegram's failed-message retry
row, a rejected Discourse write retains the text in the field, so the host also
restores the target/edit panel and its cancel action. Cancel-edit and cancel-reply
have distinct accessibility labels. Offline-suppressed edits follow the same rule.

Public Discourse Chat sends text, reply targets and recorded-audio uploads.
Its upload/message path is distinct from forum attachment insertion; sharing the
upstream input component does not grant Chat the forum's rich editor or poll form.
Channel/session permissions gate the whole input host, not its mic/send children:
upstream owns their visibility transitions, including while a channel is loading.

[DiscourseComposerTools](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscourseComposerTools.kt)
owns mention/emoji lifecycle. Mentions merge local authors and server-ranked site
candidates, replacing only the caret token; emoji inserts site shortcodes rather
than Telegram packs. Category tabs show each site's group-first emoji image, the
[Discourse picker rule](https://github.com/discourse/discourse/blob/main/frontend/discourse/app/components/emoji-picker/content.gjs),
including custom groups; category names remain available to screen readers.
The panel retains the upstream tab strip's selection and uses the same image cache
as its tiles, rather than Telegram's account-backed `EmojiView` catalog.
Pause/destroy prevents late results from reopening a panel.
System keyboard, emoji panel and composer height share inset ownership.

### Site emoji panel

The panel gives the site's own catalog the pinned `EmojiView`'s shape, not
Telegram's account-backed one: compact group-first icon tabs over one continuous
grid, where every category is a full-width header above its tiles. Columns
adapt to the panel's width by dividing it by 45dp (60dp on a tablet), the
pinned view's own span formula. Tapping a tab smooth-scrolls to its section;
scrolling the grid instead selects the tab of the section at the viewport, and
clearing the search restores the selected category. The bottom bar holds search
and backspace: backspace removes a whole `:name:` shortcode at the caret before
deleting a single character, and holding it repeats. The panel rides the
existing inset lifecycle rather than standing up a second keyboard host, and it
offers no sticker, GIF, premium or skin-tone controls the site catalog does not
have. Host tests cover scrolling, search restoration, shortcode deletion/repeat
and the inset requests; real IME, GPU and gesture pacing still needs a device.

### Drafts

Drafts outlive screens but not their identity: topic/thread/channel and site/account
keys keep unrelated compositions separate. A failed send retains the draft; an
accepted write clears the submitted draft. An account switch must not transfer an
open field to the new reader. The [reading/state design](reading-state.md) owns
session fences, recovery and accepted-write readback rules.

### Attachment sheet and recorded audio

The forum's paperclip opens upstream's own `ChatAttachAlert`, not a lookalike: the
real bottom-sheet drag, glass tab capsule, tab `RecyclerListView`, pane transitions,
gallery grid, document browser and rich article pane, hosted by a
`DiscourseAttachSheet` subclass that only changes what the tabs mean. Its four tabs
are the device gallery, the device files browser, the article editor and the site's
poll form. The gallery and files panes are upstream's own inline panes, not a handoff
to a system picker: the gallery Done hands the selected device images to the screen's
own URI upload path, and the files pane lists the device's files so any file type is
selectable (upstream's images-only rule covers a caption-less Telegram attach, which
this sheet is not). Only the files pane's own system-file row leaves for Android's SAF
picker, whose result the screen already uploads; because neither that pane nor SAF
needs a media grant, the hosted sheet answers upstream's storage-permission question
with "not needed". A selection becomes site Markdown through the normal upload path -
never a Telegram media object, and never a Telegram draft or message. The article tab
is upstream's own rich editor pane, seeded from the composer's Markdown and writing it
back through the [DiscourseRichText](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscourseRichText.kt)
bridge on Apply; Telegram's article send, send preview and premium lock never run. The
poll tab keeps its question, options and multiple-choice setting inline in the same
sheet. Its question is a leading Markdown heading inside `[poll]`, as the Discourse poll
plugin parses it. The form requires two options and caps at the plugin's default of 20;
the server's configurable limit remains authoritative. The sheet's own chrome is three
separate things. The top handle is the bottom sheet's grabber at its rounded top;
it fades as the ActionBar appears, rather than remaining a universal full-screen
header. The sheet container owns dragging, not a separate handle component. The
bottom tab capsule is the alert's glass Gallery/File/Article/Poll strip along the
sheet's foot. The ActionBar is the alert's own top bar - a pane's title and its back
arrow - and each pane claims it with `needsActionBar()`. The gallery, files and site
poll panes all use upstream's attachment-pane geometry: opening padding gives the pane
a partial-height start with the ActionBar out of the way and the tab capsule still
showing, and scrolling the pane's own list up raises the sheet under its ActionBar
while the capsule gives way to it (`shouldHideBottomButtons()`). The gallery is the
default pane and opens at that half height. The upstream layout manager fills the
trailing row while excluding the opening spacer, so even the short poll form can scroll
to that expanded position; field focus and keyboard insets still belong to the same
sheet. None of these panes opens another full-screen page. Back on the gallery, files
or poll pane closes the whole sheet to the composer - the field's draft is left exactly
as it was, and an unpicked gallery selection is never uploaded - rather than returning
to another tab. The files pane walks up one directory first: back inside a folder
returns to the parent listing, named by the pane's own title, and only at the root does
back close the sheet. A poll question or option entered but not created requires
discard confirmation; cancelling that confirmation keeps the form, and switching tabs
retains it without closing the sheet. Create inserts Markdown, never posts a poll.
Reply, new-topic and PM forms
share the sheet without sharing draft identity. Cancelling gallery/files/poll leaves
the field unchanged; leaving the article pane writes supported content back into
the draft, while unsupported blocks keep the pane open with an explanation. Unlike
the three ActionBar panes, the article uses upstream's own floating rich-editor
toolbar. NativeDo shows its existing round back control and routes it through the
same draft-preserving sheet-back path; pure Telegram attachment instances retain
their hidden back control. The hosted article reserves space for these top controls
without inserting an extra Markdown block. When article content hides the attachment
tabs, their settled visibility is reapplied on entry so they cannot cover the format
toolbar. The article retains upstream's content-dependent scroll range and keyboard
geometry: a short article need not have enough content to expand by scrolling alone.
It is not forced into a second ActionBar or a new full-screen page. Like Telegram's
chat host, the owning screen keeps the sheet through pause/resume. A
visible article saves before the screen persists its draft; a hidden article cannot
overwrite later edits. Apply is labelled as completion, not as sending a post.

Not advertised because the site has no path for them: Telegram's location, contact,
audio, sticker, emoji, quick-reply and to-do panes (so the tab set is only the four
above), Telegram's article send/preview and premium locks, and any Telegram RPC or
draft write.

Hold-to-record is implemented on the reply, new-topic and private-message screens
through the shared `ComposerVoiceController` behind `DiscourseComposerTools`.
The microphone gesture, live recording controls, timer, waveform/trim editor,
playback, delete action and transitions are upstream's `ChatActivityEnterView`
and `RecordedAudioPlayerView`, not a second review band. The fragment supplies the
same overlay inset/layout and glass factory that `ChatActivity` normally supplies;
page navigation stays out of the recording controls' way.

Recorder operations, permission requests, local editing and Send cross the
instance-bound `ChatActivityEnterViewDelegate` seam. A real Telegram composer's
delegate keeps its own recorder. NativeDo captures private AAC `.m4a` segments
with Android's recorder and samples its peak amplitude for the waveform. Release
opens review rather than sending; sliding left cancels. Locked recording can pause
into review and resume into another segment. The selected region is applied before
resume or upload, and a later pause joins the retained audio and continuation.

Telegram's Opus-only recording/editing natives cannot edit these AAC files, so
`VoiceRecordingEditor` uses the subtree's mp4parser to copy whole AAC samples
inside the selection and concatenate compatible segments without re-encoding.
Durations come from the file's sample tables. File edits run off the UI thread;
failed edits preserve the inputs and offer retry rather than silently sending an
unedited or partial take. Cancel and stale async results delete their private output.
Ownership is captured when recording starts, never reacquired on resume; a take
from an old session/account cannot be inserted or uploaded for a new one.

Send uploads the selected audio on the normal site path (`upload_type=composer`)
and inserts the site's `![name|audio](upload://...)` Markdown into the field,
never a posted topic or Telegram voice RPC. A refused upload keeps the take and
offers retry above the recording overlay without covering its controls; cancel or
an accepted upload deletes it. Takes are screen-local,
not durable Telegram voice drafts. Backgrounding stops active capture and playback;
destroying the screen removes its private take.

Chat retains its separate review/upload flow; it is not migrated to this editor.
Chat uses `upload_type=chat-composer` then `upload_ids[]` on its message create.
An explicit retry after a message-write failure reuses the accepted upload ID;
accepted messages are read back, never silently resent. Received audio uses a
labeled playable link with session-aware download, not Telegram voice bubbles.
Telegram video-note mode remains off.

Production-screen host checks drive the shipped flow on the real fragments - the
sheet's own tab list and its gallery/files/article action paths (and their upload
wire round-trips), the poll form's raw, panel expansion, insertion, and record
cancellation/permission/error paths. Real AAC fixtures verify sample ordering and
crop boundaries; the voice host drives pause/resume and inspects the uploaded
selected clip. Rendered states and accessibility bounds check the editor and IME
inset hosting, not actual keyboard motion.
Device-only, not provable by the host renderer: actual microphone encoding and
playback, drag feel, IME transitions and the on-device gallery grid; animated
attachment/delete icons need native Lottie, whose methods the renderer stubs.
Site audio extensions and permissions can still reject a recording; linux.do acceptance is not established by a
fake-site run. No live uploads or posts are part of host checks.

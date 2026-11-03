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

A post's own `user_title` is drawn where a chat draws a sender's tag: the title goes
into the message's `from_rank`, and the stock cell lays it out at the end of the name
line - the slot a group's admin rank uses, so the bubble's metrics stay upstream's. A title
longer than the tag's 100 dp is ellipsized to one line before it is written, so a long
site title cannot wrap over the body or take the whole name width; the author's name
keeps upstream's own ellipsis for whatever is left. The wire carries only the text;
linux.do's theme, not the answer, decides which titles are special. Its holographic rule names `种子用户`, `破界者` and `premium`, and those are
painted with the same animated gradient; every other title keeps the plain muted tag.
The gradient is a `CharacterStyle` span on the tag that reads a shared phase on every
draw, so the cell's layout is built once; a frame loop in the topic screen advances the
phase only while a row with such a title is visible, and stops when it scrolls away.
A title the site adds to that rule later still needs its name added here - the payload
has no class to follow. The [title test](../apps/android/nativedo/src/test/java/io/github/baba983/nativedo/topic/PostUserTitleTest.kt)
drives a holographic and a plain title, the drawn tag text, the shader and the moving
phase.

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

Channel messages use the same image viewer, attachment cache and video-cover path as
topic posts. Media taps open the selected picture in its message's gallery or fetch a
video on demand. A completed video fetch may open a viewer or external player only
while its originating screen is still visible. Ordinary channel links use the native
topic/profile destinations or the browser; received voice links retain local playback.
External video playback receives a URL, not the app's cookies, so it cannot guarantee
playback of authenticated uploads. Codec support and GPU playback require device checks.

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
A poll's answers are not capped by the site the way Telegram caps its own (12): the
cell sizes its answer controls from that cap and then indexes them per answer, so
the site's own count has to reach the cell before it is built - a poll with more
options than the cap otherwise throws while the row attaches.

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

Public Discourse Chat sends text, reply targets, recorded-audio uploads and picked
files. Its upload/message path is distinct from forum attachment insertion;
sharing the upstream input component does not grant Chat the forum's rich editor or
poll form.
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
offers no sticker, premium or skin-tone controls the site catalog does not have.
Its `GIF` control opens the sibling site GIF panel rather than a Telegram
sticker/GIF tab. Host tests cover scrolling, search restoration, shortcode
deletion/repeat and the inset requests; real IME, GPU and gesture pacing still
needs a device.

### Site GIF picker

The emoji panel's `GIF` control switches the same in-app keyboard bubble to
[DiscourseGifPicker](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscourseGifPicker.kt),
not to a fifth emoji tab or to Telegram's sticker sets. The panel is built from
upstream input components (a `RecyclerListView` with a `GridLayoutManager`, a
`SearchField` and an `EmptyTextProgressView`), but its data is the site's own GIF
proxy. Opening reads `GET /gifs/categories.json`; a category tile is a search term
with the provider's banner, not an insertable GIF, so tapping one starts that
search. A query of two characters or more reads `GET /gifs/search.json?q=&pos=`
after a short debounce, and scrolling to the end asks the provider's `next`
cursor for the following page. A result's format follows the site's own choice:
webp first, then gif, then any format that carries a URL. Selecting a result
inserts `![title|WxH](url)` at the caret - replacing the selection, escaping the
title for the Markdown label and dropping the size when the provider gave no
usable dimensions - then closes the panel and returns focus to the field. It
never sends the post and never writes HTML. The panel rides the same in-app
keyboard inset life cycle as the emoji panel and expands for its search field
while the IME is up.

Capability belongs to the site, not to the panel: GIFs switched off or the
feature absent is the site's own `404`, which the panel names instead of drawing a
pretend empty wall; a `403` (no provider key, or a permission) and a rate limit
stay retryable, and a session answer goes to the existing verification or login
states. Provider thumbnails are fetched through a client that carries the image
headers but no cookie interceptor, so a CDN request cannot forward the reader's
session; the provider key stays on the server and no Telegram sticker or GIF RPC
is involved. Category and result reads are fenced by session and panel
generation, so a late answer cannot paint a closed panel or another reader's grid,
and a tile's decoded bitmap is cached per result and released with the panel.

### Drafts

Drafts outlive screens but not their identity: topic/thread/channel and site/account
keys keep unrelated compositions separate. A failed send retains the draft; an
accepted write clears the submitted draft. An account switch must not transfer an
open field to the new reader. The [reading/state design](reading-state.md) owns
session fences, recovery and accepted-write readback rules.

### IME and clipboard media

Upstream's composer hands the app media the reader's keyboard commits
(`InputConnection.commitContent`, the path a keyboard uses to insert a GIF or
sticker) and an image pasted from the clipboard. On Telegram those become a
Telegram document or photo; here they reach the screen through the composer's own
delegate: `NativeDoHooks.ComposerMediaInputHost`, implemented by the shared
`DiscourseComposerDelegate`. A real Telegram composer's delegate does not
implement it and keeps upstream's branch; the base `DiscourseComposerDelegate`
answers by releasing the keyboard's read grant without attaching anything, so a
hosted screen with no upload path can neither leak the grant nor reach Telegram's
send or photo-edit path.

The receiving screen copies the URI into its own cache before returning the
grant, and the copy is what the upload reads. Reply, new-topic and private-message
composers insert it through their existing attachment upload path - the same
`upload_type=composer` Markdown insertion a gallery pick uses, not a Telegram
media object. A chat channel stages it for its next message (below). The grant is
released exactly once: after the copy exists, after the copy fails, or when the
screen can no longer take the file.

### Channel attachments

A chat channel takes files, not an article or a poll, so its paperclip is the
system document picker (`ACTION_OPEN_DOCUMENT`) rather than the forum attachment
sheet. The picker's answer and keyboard/paste media go through one staging step in
[ChatLoader](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/chat/ChatLoader.kt):
the URI is copied once into the app's private cache, the temporary read grant is
released, and the copy is the only file the upload reads. At most four files are
staged for one message; a fifth is refused with an explanation. Staging is fenced
to the session generation and owner that started the copy, so a copy that lands
after an account switch is deleted rather than attached.

Staging is not sending. Each staged file is uploaded on its own as
`upload_type=chat-composer` so the message write can reference it, but no message
exists until the composer's own Send. Send uploads whatever the site has not
accepted yet, waits for each id, then writes the text, the reply target and
`upload_ids[]`; a message may be empty when files are its content, which is also
why the field offers its Send button for a staged file. A refused message write
keeps the text and the staged files, and an id the site already accepted is reused
instead of uploading the same bytes twice; an accepted write spends every copy
before the readback, so a failed readback cannot leave them staged for a second
send. The band above the field
([ComposerAttachmentStrip](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/chat/ComposerAttachmentStrip.kt))
is a host preview - one chip per file with its name, its size or upload
progress/failure and its remove action - not upstream's attach alert, and it has
no gallery, article or poll pane.

A staged file is screen state, never a persisted draft: removing a chip deletes
its copy, an account change or screen destroy discards the whole staging, and an
upload id alone sends nothing. A take and a staged file are different writes, so
the composer refuses to start or send either one while the other is pending.

### Typing presence

[TypingPresenceController](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/core/presence/TypingPresenceController.kt)
binds one screen, presence channel and reader to Discourse's core presence backend,
not to a chat endpoint. Reading others is `GET /presence/get?channels[]={name}`,
seeded into the `/presence{name}` message-bus channel; announcing is
`POST /presence/update` with a random per-binding client id. A skipped bus message
id, or a payload it cannot decode, re-reads the snapshot instead of guessing who
entered or left; a `null` channel state (the site will not show it to this reader)
draws nothing; and the visible list excludes the reader by bound username, by the
session's own username and by user id, because the site's own list includes them.
Timings follow the site: a present client re-announces inside the server's 60 s
presence expiry, updates are throttled, a failed update backs off, and a failed
leave is retried a few times before that expiry is the backstop.

Announcing is deliberately conservative. It happens only while the composer holds
non-whitespace text and the channel still accepts posts, only after
`current_user.user_option.hide_presence` read `false`, and only while the bound
message-bus client is the running one. An
unanswered or failed privacy read, a `true` value, a different signed-in account
and a session/bus change all mean "read but do not announce"; none of them may
inherit a previous announcement. Typing stops being announced after the last
input even if the text stays in the box - which leaves the draft itself
untouched - and leaving the screen sends a leave. Neither request carries the
composer's text, and nothing here broadcasts the reader globally through
`/chat/online`.

Two screens share the controller, each naming the site's own channel. A chat
channel binds `/chat-reply/{channelId}` (the chat plugin's name) and shows the
other typers in its header capsule's status line, with Telegram's own
`TypingDotsDrawable` before the line: `正在输入：name`, or
`正在输入：name 等 N 人`, five seconds after the last input. The status verb leads
so a long name is what the capsule's edge ellipsizes, not the verb. The topic
screen binds `/discourse-presence/reply/{topicId}` (the discourse-presence
plugin's name) and shows them in the same capsule line as `正在回复：name` or
`正在回复：name 等 N 人`, restoring the topic's category/count line when the last
replier leaves; its composer keeps the reader announced for the plugin's own 10 s
`KEEP_ALIVE`. A post being edited is never announced as a reply - the plugin has a
separate edit channel, and this client does not claim one yet.

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

Completed gallery/files handoffs close without asking to discard the selection that
was just handed to the uploader. Screen destruction also closes the sheet without a
confirmation dialog. The multipart filename preserves the picker's display name rather
than the cache copy's timestamp prefix; the site's returned filename still owns markup.
Edited stills use the editor's output filename because the URI does not carry the
original name alongside it.

The local gallery's upstream mode supports still-image crop/rotate/mirror and, in
the hosted sheet only, upstream's own paint and filter editors; every edit uploads
the resulting `PhotoEntry.imagePath`, leaving the source file intact. The two
tools are shown only for the hosted sheet's local gallery still: videos, GIF
animations, live photos and the document browser keep upstream's hidden state, and
a real Telegram alert is untouched. A hosted paint editor is bound to the alert
that opened it, so Telegram's sticker tab, its sticker/GIF search and its
account-backed photo widget stay out of reach, and its text emoji panel is built
without the animated-emoji packs; draw, text and the local editor itself are
unchanged. Post images remain read-only. Video edits are still not transcoded by
this host: a selection containing a trimmed, cropped, painted, filtered or muted
video is refused before any file is uploaded, with an explanation and the
selection/draft intact. Unedited videos upload as their original bytes. A
quality-only compression preference does not trigger conversion; original encoding
is retained.
Host tests exercise file handoff and refusal, not real crop or paint encoding,
editor export, gallery grants, hardware video playback or live-site upload acceptance.

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
Chat uses `upload_type=chat-composer` then `upload_ids[]` on its message create,
for a take and for a staged file alike.
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
inset hosting, not actual keyboard motion. Host-side coverage is scoped to the
IME/paste intake seam, a chat file staged, uploaded and sent as `upload_ids[]`
(including a refused write that keeps its accepted id), the staging discarded on
an account change, the typing controller against a fake site and the real bus, the
chat header's typing line and its dots, the topic screen's replier line and its own
announce/leave writes, and the GIF panel's category, search, paging and insert
paths.
Device-only, not provable by the host renderer: actual microphone encoding and
playback, a real keyboard's `commitContent` and clipboard handoff, drag feel, IME
transitions and the on-device gallery grid; animated
attachment/delete icons need native Lottie, whose methods the renderer stubs.
Site audio extensions and permissions can still reject a recording; the site's
GIF setting and provider key decide whether the panel can answer; presence is only
as visible as the site allows. linux.do acceptance is not established by a
fake-site run. No live uploads or posts are part of host checks.

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

Media bridges supply the local paths and stable identities upstream loaders expect,
with Discourse-aware downloads. Photos, document blocks and PhotoViewer use those
paths, not fake Telegram downloads. A site's smaller photo preview can appear while
its original downloads; the original replaces it in the retained bubble and open
PhotoViewer. The HTTP transfer drives upstream progress, cancel and tap-to-retry
controls. Failed, cancelled or non-image responses never become a completed photo
cache entry; duplicate consumers share the transfer and its outcome. Fetching video
bytes remains a separate action from rendering its cover. Never retain recycled
view/block instances as a data cache.

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

The forum's paperclip opens a Telegram-shaped attachment sheet that expands by
dragging, using upstream `BottomSheet`, `RecyclerListView` and `GlassTabView`.
Gallery and files still use Android's content picker and the session-aware upload
path; rich editing and poll composition produce Discourse raw, not Telegram media.
The poll tab keeps its question, options and multiple-choice setting inline in the
expanded sheet. Its question is a leading Markdown heading inside `[poll]`, as the
Discourse poll plugin parses it. The form requires two options and caps at the
plugin's default of 20; the server's configurable limit remains authoritative.
Reply, new-topic and PM forms share the sheet without sharing draft identity.
This is not the full account-backed `ChatAttachAlert`: gallery/files hand off to
the system picker rather than an inline media grid, and location/contact message
types without a site send path are not advertised.

Hold-to-record is implemented on the reply, new-topic and private-message screens
through the shared `ComposerVoiceController` behind `DiscourseComposerTools`. The
empty composer offers its own microphone, bound to the composer's own
`DiscourseComposerDelegate` so upstream's Telegram recorder and video note are never
reached; a hold records a private `.m4a` with this client's `android.media`
recorder (permission, cancellation and pause/destroy cleanup included), a review
band plays the take back, and Send uploads it on the normal site path
(`upload_type=composer`) and inserts the site's own `![name|audio](upload://...)`
markdown into the field - never a posted topic, and never Telegram's voice RPC. A
refused upload keeps the take and offers a retry; a cancel or an accepted upload
deletes it. Ownership is captured when recording starts, not when it finishes;
a take from an old session/account cannot be inserted or uploaded for a new one.
Release opens review rather than sending immediately; sliding left cancels.
Chat uses `upload_type=chat-composer` then `upload_ids[]` on its message create.
An explicit retry after a message-write failure reuses the accepted upload ID;
accepted messages are read back, never silently resent. Received audio uses a
labeled playable link with session-aware download, not Telegram voice bubbles.
Telegram video-note mode remains off.

Production-screen host checks exercise panel expansion, insertion,
record cancellation/permission/error paths and upload wire round-trips. Rendered
host states check layout; actual microphone encoding, drag feel and IME transitions
still require an Android device. Animated attachment icons depend on native Lottie
and are not proved by the host renderer's stubbed native methods. Site audio
extensions and permissions can still reject a recording; linux.do acceptance is
not established by a fake-site run. No live uploads or posts are part of host checks.

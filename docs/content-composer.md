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
splits polls/oneboxes and handles Discourse-specific markup. [DiscourseRows](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscourseRows.java)
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

Public Discourse Chat currently sends text and reply targets, not channel attachment
uploads. Sharing the upstream input component does not grant the topic composer's
upload or rich-editing capabilities to Chat.

[DiscourseComposerTools](../apps/android/nativedo/src/main/java/io/github/baba983/nativedo/topic/DiscourseComposerTools.kt)
owns mention/emoji lifecycle. Mentions merge local authors and server-ranked site
candidates, replacing only the caret token; emoji inserts site shortcodes rather
than Telegram packs. Pause/destroy prevents late results from reopening a panel.
System keyboard, emoji panel and composer height share inset ownership.

Drafts outlive screens but not their identity: topic/thread/channel and site/account
keys keep unrelated compositions separate. A failed send retains the draft; an
accepted write clears the submitted draft. An account switch must not transfer an
open field to the new reader. The [reading/state design](reading-state.md) owns
session fences, recovery and accepted-write readback rules.

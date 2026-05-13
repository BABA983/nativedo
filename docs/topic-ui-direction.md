# Topic UI Direction

Topic pages are NativeDo-owned Discourse content hosted inside Telegram-derived navigation.

## Ownership

- `ui/topic/*Fragment` owns Telegram host wiring: `ActionBar`, `BaseFragment`, stack pushes, browser sheets, and page-scoped `ViewModel` creation.
- `feature/topic/ui` owns topic detail content, post cells, metadata labels, reply/reaction presentation, and cooked-body placement.
- Compose content emits callbacks only. It does not call Telegram navigation APIs directly.

## Detail Surface

- Topic detail uses a continuous reader surface, not one floating card per post.
- The topic header is calm: title, tags, loaded/total posts, and views.
- The `ActionBar` title is shown only after the content header scrolls away.
- Post avatars live in the header row only. The post body starts full-width at the cell inset, aligned with the avatar edge.
- The original poster badge text is `OP`.
- Replies/reactions are quiet footer content and should appear only when backed by current data.
- Quotes, reply previews, code blocks, images, and sheets may keep their own contained surfaces.

## First Slice

The first implementation slice is presentation-only:

- Keep current `TopicViewModel`, repository, endpoints, session behavior, `HtmlPostBodyView`, pull-to-refresh, pagination, reply/reaction hooks, and link callbacks.
- Replace the active post presentation with compact reader cells.
- Do not add reply composer, search, new APIs, browser changes, or a parallel card/cell feature flag.

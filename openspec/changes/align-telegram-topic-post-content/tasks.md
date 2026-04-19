## 1. Topic card structure

- [x] 1.1 Split `TopicPostCard` into explicit header, optional reply preview, body, and footer sections while keeping avatar usage inside the header only.
- [x] 1.2 Implement `TopicReplyPreview` as a compact two-line relationship summary that can jump to the replied-to post.
- [x] 1.3 Keep `HtmlPostBodyView` as the body renderer in topic posts and verify the body still uses the full post width after the card restructure.

## 2. Interaction footer

- [x] 2.1 Add a `TopicPostFooter` that renders a replies entry and a like/reaction chip below the post body.
- [x] 2.2 Wire footer rendering to the minimum topic interaction data model, including graceful fallback when reply participant preview or like data is missing.
- [x] 2.3 Validate footer layout and tap targets against topic posts with and without reply/like data.

## 3. Replies bottom sheet

- [x] 3.1 Add a modal bottom sheet surface for topic replies that opens from the footer replies entry at partial height.
- [x] 3.2 Implement `ReplySheetItem` with author identity, relative time, bounded summary content, and a "view original reply" action.
- [x] 3.3 Wire reply sheet actions so opening an original reply restores topic context and dismisses or yields focus from the sheet.

## 4. Quote hierarchy refinement

- [x] 4.1 Adjust body quote rendering so normal quote blocks feel lighter than the current forum-style embedded card treatment.
- [x] 4.2 Add a collapsible long-quote presentation using a structure-based threshold and an expand/collapse affordance.
- [x] 4.3 Verify reply preview and body quotes remain visually and behaviorally distinct in topic posts.

## 5. Reader boundary verification

- [x] 5.1 Ensure reader / Instant View flows do not render topic-only reply preview, social footer, replies sheet entry, or reaction chip structures.
- [x] 5.2 If shared content render entry points gain a variant or boundary flag, thread that boundary through reader callers without regressing reader layout.
- [ ] 5.3 Manually verify topic and reader surfaces still present distinct interaction models after the topic content alignment changes.

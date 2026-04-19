## Stage 1: Restructure topic post card

**Goal**: Split topic posts into explicit header, reply preview, body, and footer layers without changing the existing body renderer.
**Success Criteria**: Topic posts render with the new structure; avatar stays in header; reply preview is visually separate from body quotes; body still uses full width.
**Tests**: Open a topic with replies and verify header, reply preview, and body layout on the golden path.
**Status**: Complete

## Stage 2: Add Telegram-style interaction surfaces

**Goal**: Add a footer with replies and like affordances plus a modal bottom sheet for reply discovery.
**Success Criteria**: Footer renders below the body; replies entry opens a half-height bottom sheet; sheet items show bounded reply summaries and can jump to the original reply.
**Tests**: Open and dismiss the replies sheet; verify summary items and jump-to-reply behavior.
**Status**: Complete

## Stage 3: Refine quote hierarchy

**Goal**: Differentiate reply preview from body quote styling and add collapsible handling for long body quotes.
**Success Criteria**: Normal quotes feel lighter than forum cards; long quotes collapse and expand per-post; reply preview remains visually distinct.
**Tests**: Verify posts with short and long quotes and ensure reply preview never looks like a quote block.
**Status**: Complete

## Stage 4: Preserve reader separation and validate

**Goal**: Keep reader / Instant View free of topic-only social chrome and validate the final topic-vs-reader interaction model.
**Success Criteria**: Reader scenes render without reply preview/footer/reaction chip; topic and reader remain visually distinct.
**Tests**: Open both a topic and reader scene and verify they use different interaction models.
**Status**: In Progress

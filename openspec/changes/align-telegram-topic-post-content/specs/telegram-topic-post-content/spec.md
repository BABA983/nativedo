## ADDED Requirements

### Requirement: Topic posts SHALL use a Telegram-style layered card structure
The topic screen SHALL render each post using a layered card structure with a header, an optional reply preview, a body, and an interaction footer. The header SHALL keep avatar usage inside the header row, and the post body SHALL continue to use the full available width instead of reserving a permanent avatar column.

#### Scenario: Render a normal topic post
- **WHEN** a topic post has no reply target metadata
- **THEN** the topic screen SHALL render the post header followed directly by the body and interaction footer
- **AND** the body SHALL use the full post content width

#### Scenario: Render a reply topic post
- **WHEN** a topic post has reply target metadata
- **THEN** the topic screen SHALL render the post header, then a reply preview, then the body, and then the interaction footer
- **AND** the reply preview SHALL be visually distinct from body quote blocks

### Requirement: Topic reply preview SHALL represent reply metadata rather than body content
The system SHALL render reply preview as a compact relationship summary above the body content. The reply preview SHALL identify the replied-to target and a short summary, and it SHALL NOT be rendered using the same visual treatment as a body quote block.

#### Scenario: Reply preview is present
- **WHEN** the current post replies to another post with available target metadata
- **THEN** the topic card SHALL show a compact reply preview above the body content
- **AND** the reply preview SHALL remain limited to relationship-oriented summary content

#### Scenario: Reply preview is clicked
- **WHEN** the user taps the reply preview
- **THEN** the system SHALL navigate to or focus the replied-to post target

### Requirement: Topic posts SHALL expose an interaction footer for reply discovery and reactions
The system SHALL render a footer below the post body for topic interactions. The footer SHALL provide a replies entry and a reaction-style like chip without pushing those interactions into the post header.

#### Scenario: Post has replies and likes
- **WHEN** reply count and like count are available for a post
- **THEN** the footer SHALL show a replies entry and a like chip below the body
- **AND** the replies entry SHALL remain the primary affordance for discovering second-level replies to that post

#### Scenario: Interaction data is partially unavailable
- **WHEN** a post is missing reply participant preview data or like count detail
- **THEN** the footer SHALL degrade gracefully without breaking the post layout
- **AND** the system SHALL still preserve the layered card structure

### Requirement: Long quote blocks SHALL support collapsible presentation
The system SHALL preserve normal quote blocks inside the post body, and it SHALL allow long quote blocks to render in a collapsible presentation so that oversized quoted content does not dominate the visible post height.

#### Scenario: Quote stays uncollapsed
- **WHEN** a body quote block does not exceed the long-quote threshold
- **THEN** the quote SHALL render inline as a normal body quote block
- **AND** it SHALL remain visually distinct from reply preview content

#### Scenario: Quote becomes collapsible
- **WHEN** a body quote block exceeds the long-quote threshold
- **THEN** the post body SHALL render that quote in a collapsed presentation with an affordance to expand it
- **AND** expanding or collapsing the quote SHALL affect only the current post instance

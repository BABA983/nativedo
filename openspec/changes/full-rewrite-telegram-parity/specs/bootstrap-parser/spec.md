## ADDED Requirements

### Requirement: Homepage HTML parsing
The system SHALL provide a `BootstrapParser` that fetches and parses the `https://linux.do/` homepage HTML to extract: (1) current user identity (username, avatar), (2) CSRF token from meta tags, (3) site categories from preloaded store data.

#### Scenario: Parse logged-in user
- **WHEN** the homepage HTML contains `<meta name="current-user-username" content="alice">`
- **THEN** `bootstrapParser.parse(html).currentUser` returns `BootstrapUser(username = "alice", ...)`

#### Scenario: Parse CSRF token
- **WHEN** the homepage HTML contains `<meta name="csrf-token" content="abc123">`
- **THEN** `bootstrapParser.parse(html).csrfToken` returns "abc123"

#### Scenario: Parse not logged in
- **WHEN** the homepage HTML does not contain user meta tags
- **THEN** `bootstrapParser.parse(html).currentUser` returns null

### Requirement: Category data extraction
The system SHALL extract category data (id, name, slug, color, text_color, parent_category_id) from the preloaded store JSON embedded in the homepage HTML.

#### Scenario: Extract categories
- **WHEN** the homepage HTML contains preloaded store with 15 categories
- **THEN** `bootstrapParser.parse(html).categories` returns a list of 15 `CategorySummary` objects with colors

### Requirement: Jsoup-based parsing
The system SHALL use Jsoup for HTML parsing. The parser SHALL be resilient to HTML structure changes and SHALL NOT crash on missing elements (return null/empty instead).

#### Scenario: Missing meta tag
- **WHEN** the homepage HTML is missing the csrf-token meta tag
- **THEN** `bootstrapParser.parse(html).csrfToken` returns null (no crash)

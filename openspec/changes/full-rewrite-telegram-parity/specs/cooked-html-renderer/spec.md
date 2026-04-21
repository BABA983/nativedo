## ADDED Requirements

### Requirement: HTML to RenderBlock conversion
The system SHALL provide a `CookedHtmlRenderer` that parses Discourse cooked HTML (via Jsoup) and converts it into a `List<RenderBlock>` sequence. Each HTML element type SHALL map to a specific RenderBlock variant.

#### Scenario: Simple paragraph
- **WHEN** `<p>Hello <strong>world</strong></p>` is parsed
- **THEN** it produces `RenderBlock.Paragraph` backed by text content plus inline entities for normal and bold text

#### Scenario: Code block
- **WHEN** `<pre><code class="lang-kotlin">val x = 1</code></pre>` is parsed
- **THEN** it produces `RenderBlock.CodeBlock(code="val x = 1", language="kotlin")`

#### Scenario: Block quote
- **WHEN** `<blockquote><p>quoted text</p></blockquote>` is parsed
- **THEN** it produces `RenderBlock.Blockquote` containing a child `RenderBlock.Paragraph`

### Requirement: Supported block-level HTML elements
The system SHALL support conversion of block-level cooked HTML structures including: `<p>`, `<h1>`-`<h6>`, `<pre><code>`, `<blockquote>`, `<img>`, `<ul>/<ol>/<li>`, `<table>`, `<hr>`, `<details>/<summary>`, Discourse oneboxes (`<aside class="onebox">`, `a.onebox`, `a.inline-onebox`), Discourse quotes (`<aside class="quote">`), Discourse lightbox images (`div.lightbox-wrapper`, `a.lightbox`), Discourse image grids (`div.d-image-grid`), Discourse spoilers (`<div class="spoiler">`), and Discourse polls (`<div class="poll">`).

#### Scenario: Discourse onebox
- **WHEN** an `<aside class="onebox">` element is parsed
- **THEN** it produces `RenderBlock.Onebox` with extracted title, description, and thumbnail

#### Scenario: Details block
- **WHEN** a `<details>` element with a `<summary>` child is parsed
- **THEN** it produces a dedicated expandable block with summary text and parsed child blocks

#### Scenario: Spoiler block
- **WHEN** a `<div class="spoiler">` element is parsed
- **THEN** it produces a dedicated spoiler block whose children are parsed as normal cooked HTML blocks and hidden by default until revealed

#### Scenario: Discourse quote metadata
- **WHEN** an `<aside class="quote" data-topic="482293" data-post="1" data-username="neo">` element is parsed
- **THEN** it preserves source topic/post metadata and quote author metadata in the produced block

#### Scenario: Lightbox image metadata
- **WHEN** an `a.lightbox` element contains an image and a `.meta` block
- **THEN** it preserves the preview image URL, original image URL, filename, and size text in the produced block

#### Scenario: Image grid block
- **WHEN** a `div.d-image-grid` element contains multiple images or lightbox anchors
- **THEN** it produces a dedicated image-grid block with parsed child images and preserved column count

#### Scenario: Inline onebox link
- **WHEN** an `a.inline-onebox` element is parsed
- **THEN** it produces a link-preview style block instead of plain anchor text

#### Scenario: Poll block
- **WHEN** a `<div class="poll">` element is parsed
- **THEN** it produces a read-only poll block containing options and visible poll status metadata

#### Scenario: Markdown-style callout lead
- **WHEN** a blockquote starts with a marker such as `[!warning]`
- **THEN** the parser treats it as a callout-style block rather than a plain blockquote

### Requirement: Supported inline entities
The system SHALL parse inline cooked HTML semantics into inline entities within text blocks instead of promoting them to standalone RenderBlocks. This includes links, mentions, hashtags, emoji, and inline code.

#### Scenario: Discourse mention
- **WHEN** `<a class="mention" href="/u/alice">@alice</a>` is parsed inside a paragraph
- **THEN** it produces an inline mention entity with mention-specific styling metadata

#### Scenario: Discourse emoji
- **WHEN** `<img class="emoji" title=":innocent:" alt=":innocent:">` is parsed inside a paragraph
- **THEN** it produces an inline emoji entity instead of a standalone block

#### Scenario: Hashtag link
- **WHEN** `<a class="hashtag-cooked" href="/tag/example">Example</a>` is parsed inside a paragraph
- **THEN** it produces an inline hashtag entity that is visually distinct from a normal hyperlink

#### Scenario: Inline code
- **WHEN** `<code>value</code>` is parsed inside a paragraph
- **THEN** it produces an inline code entity instead of a code-block RenderBlock

### Requirement: Baseline parity with the current parser
The rewrite SHALL not regress below the set of cooked HTML structures already handled by the current implementation in [PostBodyBlocks.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlocks.kt) and [PostBodyBlockParser.kt](/Users/baba/Documents/workspace_baba/linuxdo-native/apps/android/app/src/main/java/io/github/baba983/nativedo/core/ui/PostBodyBlockParser.kt).

#### Scenario: Existing supported structures remain supported
- **WHEN** a cooked HTML sample exercises details, callouts, Discourse quote metadata, lightbox images, or image grids
- **THEN** the new renderer still maps those structures to dedicated typed blocks rather than dropping them to unknown/raw HTML

### Requirement: Unknown HTML fallback
The system SHALL convert any unrecognized HTML elements into `RenderBlock.RawHtml`, which will be rendered via a small WebView as a fallback.

#### Scenario: Unknown plugin HTML
- **WHEN** `<div class="custom-plugin-widget">...</div>` is parsed
- **THEN** it produces `RenderBlock.RawHtml(html="<div class='custom-plugin-widget'>...</div>")`

### Requirement: Theme-aware rendering
Each RenderBlock type SHALL be rendered using theme colors from `NdTheme`: body text uses `key_post_body`, code blocks use `key_post_codeBlock`/`key_post_codeBlockBackground`, blockquotes use `key_post_blockquote`/`key_post_blockquoteBorder`, links use `key_post_link`.

#### Scenario: Dark theme code block
- **WHEN** a code block is rendered with the Dark theme active
- **THEN** it uses the dark theme's code background and text colors

### Requirement: Image loading in rendered blocks
Image RenderBlocks SHALL load images via the `ImageReceiver`/Coil pipeline, displaying a placeholder rectangle during loading and fading in the image once loaded.

#### Scenario: Post image loading
- **WHEN** a RenderBlock.Image is displayed in a post
- **THEN** a placeholder with the image's aspect ratio is shown, then the image fades in

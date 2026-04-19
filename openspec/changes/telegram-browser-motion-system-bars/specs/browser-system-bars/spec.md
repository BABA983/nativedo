## ADDED Requirements

### Requirement: Browser scene shall own its system bar style
The browser scene SHALL apply its own status bar and navigation bar style while active rather than relying exclusively on the root default system bar configuration.

#### Scenario: Browser scene becomes active
- **WHEN** the app browser scene becomes the active visible scene
- **THEN** the browser SHALL apply a browser-specific system bar style
- **AND** the root scene system bar style SHALL NOT remain visible as the active style

#### Scenario: Browser scene is dismissed
- **WHEN** the browser scene is dismissed
- **THEN** the previous scene's system bar style SHALL be restored
- **AND** browser-specific colors or icon policies SHALL NOT leak into the restored scene

### Requirement: Browser chrome colors shall respond to page identity
The browser SHALL resolve a page-aware chrome color model that can blend between the NativeDo fallback palette and a page-provided theme color.

#### Scenario: Page exposes a theme color
- **WHEN** the current page provides a valid theme color
- **THEN** the browser top bar and navigation bar presentation SHALL animate toward the resolved page color
- **AND** the color transition SHALL remain smooth across page changes

#### Scenario: Page does not expose a theme color
- **WHEN** the current page does not expose a usable theme color
- **THEN** the browser SHALL fall back to the NativeDo browser palette
- **AND** the browser SHALL remain fully usable without waiting for page-provided color metadata

### Requirement: Browser system bar icon contrast shall follow resolved luminance
The browser SHALL derive status bar and navigation bar icon appearance from the resolved browser chrome luminance so icon contrast remains legible.

#### Scenario: Resolved chrome color is light
- **WHEN** the resolved browser chrome color is above the configured luminance threshold for light surfaces
- **THEN** the browser SHALL use dark system bar icons where the platform supports them

#### Scenario: Resolved chrome color is dark
- **WHEN** the resolved browser chrome color is at or below the configured luminance threshold for light surfaces
- **THEN** the browser SHALL use light system bar icons where the platform supports them

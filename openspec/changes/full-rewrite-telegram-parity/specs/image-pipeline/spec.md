## ADDED Requirements

### Requirement: ImageReceiver wrapper for Canvas drawing
The system SHALL provide an `ImageReceiver` class that wraps Coil 3's image loading and provides a Canvas-compatible drawing API. ImageReceiver SHALL manage its own lifecycle (attach/detach from views) and handle placeholder → loaded image transitions.

#### Scenario: Load image for Canvas cell
- **WHEN** `imageReceiver.setImageUrl(url)` is called from a BaseCell
- **THEN** the image is loaded via Coil 3 and `imageReceiver.draw(canvas)` renders it at the configured bounds

#### Scenario: Placeholder during loading
- **WHEN** `imageReceiver.draw(canvas)` is called before the image has loaded
- **THEN** a placeholder (color/drawable) is drawn at the configured bounds

### Requirement: Round/clipping transforms
The system SHALL support circular clipping (`setRoundRadius`) for avatar rendering and rounded rectangle clipping for thumbnail rendering, applied during Canvas draw without creating a new Bitmap.

#### Scenario: Circular avatar drawing
- **WHEN** `imageReceiver.setRoundRadius(dp(20))` is set and the image is drawn
- **THEN** the image is clipped to a circle with 20dp radius during draw

### Requirement: View attach/detach lifecycle
The system SHALL call `onAttachedToWindow()` and `onDetachedFromWindow()` on ImageReceiver to start and cancel image loading respectively. This prevents loading images for off-screen cells.

#### Scenario: Cell recycled
- **WHEN** a BaseCell containing an ImageReceiver is recycled by RecyclerView
- **THEN** `imageReceiver.onDetachedFromWindow()` cancels any pending image load

#### Scenario: Cell rebound
- **WHEN** a recycled BaseCell is rebound with a new image URL
- **THEN** `imageReceiver.setImageUrl(newUrl)` triggers a new load and the old image is discarded

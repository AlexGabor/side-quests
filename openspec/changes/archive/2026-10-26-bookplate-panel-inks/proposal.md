## Why

Books now print in their own Riso inks in Player, Home and the mini player. The system media panel doesn't follow:
a book without a cover sends no artwork, so the panel falls back to the system's default colors. The panel's colors
can't be set directly. Since Android 13 (the app's minimum) the media controls build their palette from the artwork
and ignore the notification's own color, and iOS Now Playing works the same way. So the panel can only follow the
book's inks through artwork printed in them.

## What Changes

- A book without a cover gets generated artwork for the media panel, printed in its inks, on Android and iOS. It
  matches the library placeholder: the title's first letter in its accent, over the book's accent variant at 40 %
  opacity, framed in its accent. The system then colors the panel from it.
- The placeholders follow the same look: the letter of Home's placeholder turns to the accent, and Player's
  placeholder, which was plain, becomes the same as Home's.
- A book with a cover keeps showing its cover, and the system colors the panel from the cover, as it does today.
- `PlayerBook` carries the book's inks. The engine follows the loaded book's ink changes, so a swatch tap in Player
  recolors the panel without reloading the book.

## Capabilities

### New Capabilities

### Modified Capabilities
- `bookplate-player`: the media panel shows ink artwork for coverless books; `PlaybackState`'s book carries its inks
  and follows changes to them; Player's cover placeholder matches Home's.
- `bookplate-home`: the placeholder's letter is printed in the book's accent.

## Impact

- `bookplate/core/player/api`: `PlayerBook.inks`.
- `bookplate/core/player/impl`:
  - common: `PanelInfo.inks`, and the `PanelInks` colors with the `PanelInkColors` lookup;
  - `EngineBookPlayer` follows the loaded book's inks;
  - Android: `PanelPlayer` sends PNG artwork rendered with `android.graphics`;
  - iOS: `NowPlayingPanel` sends artwork rendered with UIKit.
- `bookplate/core/player/test`: `FakeBookPlayer` carries and follows the inks.
- `bookplate/feature/player`: builds `PlayerBook` with its inks; `BookCover` placeholder.
- `bookplate/feature/home`: `BookCover` placeholder letter.
- `bookplate/sharedApp`: binds the Riso-backed `PanelInkColors`.
- No new dependencies.

## 1. Inks through the player

- [x] 1.1 Add `inks: BookInks` to `PlayerBook` and set it wherever a `PlayerBook` is built (engine, fake, `PlayerScreenState`, tests)
- [x] 1.2 `EngineBookPlayer` follows the loaded book's inks in the library; tests that it updates state without touching playback
- [x] 1.3 `FakeBookPlayer` follows them the same way
- [x] 1.4 Add `PanelInfo.inks`, `PanelInks` and `PanelInkColors`; test that `panelInfo()` carries the inks and that `PanelUpdates` pushes an ink change

## 2. Artwork

- [x] 2.1 Android: render the coverless artwork as PNG (cached per letter and inks) and send it from `PanelPlayer`; inject `PanelInkColors` into `PlaybackService` and include inks in its label check
- [x] 2.2 iOS: render the artwork with UIKit in `NowPlayingPanel` (cached), passing `PanelInkColors` from `playerModule`

## 3. App binding and docs

- [x] 3.1 Bind the Riso-backed `PanelInkColors` in `sharedApp`, with a test of the name lookup and fallback
- [x] 3.2 Update `bookplate/CLAUDE.md` (media panel) and the `bookplate/README.md` feature list

## 4. Verification

- [x] 4.1 Run `./gradlew :bookplate:core:player:api:jvmTest :bookplate:core:player:impl:jvmTest :bookplate:core:player:test:jvmTest :bookplate:feature:player:jvmTest :bookplate:feature:home:jvmTest :bookplate:sharedApp:jvmTest`, and compile the iOS target
- [x] 4.2 Android, the user's choice: play `Single`, check that the notification and its colors follow its inks, tap the swatch, and check that they change; `Sample Book` keeps its cover
- [x] 4.3 iOS, the user's choice: the same on the simulator's lock screen Now Playing

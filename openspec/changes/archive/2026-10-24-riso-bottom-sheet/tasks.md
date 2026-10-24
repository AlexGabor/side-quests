## 1. Backdrop

- [x] 1.1 Add the internal backdrop state and `LocalRisoBackdrop`; wrap `RisoTheme` content in the backdrop (`risoFade` with effects on, paper veil with effects off)
- [x] 1.2 Tests: backdrop fade from `shown`, no change with no sheet, effects-off veil pixel

## 2. Bottom sheet

- [x] 2.1 `components/BottomSheet.kt`: `BottomSheet`, `BottomSheetScope.dismiss()`, `BottomSheetDefaults.BackdropFade`, built on Material's `ModalBottomSheet` with no scrim, Riso paper, inked edge, drag handle and insets
- [x] 2.2 Report the sheet's on-screen share to the backdrop and unregister on leave
- [x] 2.3 Compose UI tests: content shown, handle present, tap outside dismisses and blocks the page, `dismiss()` calls `onDismissRequest` once
- [x] 2.4 README and docs (`riso-paper-and-ink.md`, `design/riso/CLAUDE.md`) updated

## 3. Demo

- [x] 3.1 Split `design/risoDemo` into `androidApp` and `sharedApp`; move `DemoPages` and `ComponentsDemo` into `sharedApp`
- [x] 3.2 Add `SheetsDemo`: a short `Track` sheet and a long scrollable list sheet, over an inked page with a button behind
- [x] 3.3 Add `design/risoDemo/iosApp` (Xcode project) hosting `MainViewController`

## 4. Verification

- [x] 4.1 `:design:riso` tests and all targets build; Pacer and Bookplate build and their tests pass
- [x] 4.2 Android emulator: open both sheets, drag to dismiss, back (incl. predictive back), tap outside
- [x] 4.3 iOS simulator: open both sheets, drag/swipe to dismiss, tap outside

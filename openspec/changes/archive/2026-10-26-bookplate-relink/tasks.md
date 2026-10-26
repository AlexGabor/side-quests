## 1. Document access fake

- [x] 1.1 `FakeDocumentAccess(followsRenames)`: renaming a granted root moves it under its old reference
- [x] 1.2 Count reachability checks, track the peak running at once, optional `checkDelay`
- [x] 1.3 Tests for Android-like and iOS-like renames and for concurrent checks

## 2. Library api and storage

- [x] 2.1 `Availability`, `BookAvailability`, `BookRelinker`, `RelinkResult`; `AudioFile.isMissing`
- [x] 2.2 `AudioFileEntity.missing`, schema v2 with `Migration(1, 2)`, `2.json` exported, migration test
- [x] 2.3 DAO: other-book lookups by source and fingerprint, and a relink transaction

## 3. Detection and relink in `core/library/impl`

- [x] 3.1 `BookAvailabilityImpl`: root first, then the files not left out; `checkAll` bounded to 4 on IO, deduplicated
- [x] 3.2 `RelinkMatcher`: path, name, size and duration passes with tie-breaks, durations read lazily
- [x] 3.3 `BookRelinkerImpl`: ordered checks, confirmation, unchanged, apply, re-check
- [x] 3.4 Bind both in `libraryModule`
- [x] 3.5 Fakes in `core/library/test`: `FakeBookAvailability`, `FakeBookRelinker`, bound in `fakeLibraryModule`

## 4. Player

- [x] 4.1 `BookTimeline` leaves out missing files and maps gap positions to the next file
- [x] 4.2 `EngineBookPlayer`: check on play, reload changed files, check after a failure
- [x] 4.3 `FakeBookPlayer`: the same checks and `failPlayback()`
- [x] 4.4 Android and iOS `playerModule`, and `fakePlayerModule`, pass `BookAvailability`

## 5. Home

- [x] 5.1 `HomeState` takes `BookAvailability`, checks all books on creation, rows carry the unavailable line
- [x] 5.2 `MiniPlayerState`: unavailable state, no play
- [x] 5.3 UI: the row line in accent, and "Unavailable" in the mini player

## 6. Player screen

- [x] 6.1 `PlayerScreenState` takes `BookAvailability` and `BookRelinker`: checks on open, the unavailable state,
  "Check again", and the relink status machine (running, confirmation, result)
- [x] 6.2 UI: the unavailable panel, the relink status, the confirmation buttons, the picker by kind, and
  Chapters/Speed hidden while unavailable

## 7. Integration, docs, flows

- [x] 7.1 `sharedApp` jvmTest: end to end through the real library and FakeDocumentAccess
- [x] 7.2 README feature list, `bookplate/CLAUDE.md` (relink flows, the driver script)
- [x] 7.3 Maestro relink step flows and `scripts/relink-flows.sh`, run on the Android emulator and the iOS simulator
- [x] 7.4 Run every JVM test, the iOS simulator tests, every existing Maestro flow on both platforms, and the Pacer build
- [x] 7.5 `openspec validate bookplate-relink --strict`

## 8. Test matrix (requested scenarios → tests)

Levels: **M** = `RelinkMatcherTest` (pure matcher), **L** = library on Room + `FakeDocumentAccess`
(`BookRelinkerTest`, `BookAvailabilityTest`, `LibraryMigrationTest`), **P** = player (`EngineBookPlayerTest`,
`BookTimelineTest`, `FakeBookPlayerTest`), **S** = state holders (`HomeStateTest`, `MiniPlayerStateTest`,
`PlayerRelinkTest`), **E** = end to end on the JVM (`RelinkEndToEndTest` for state, `RelinkUiTest` for Compose UI),
and **D** = device: `scripts/relink-flows.sh <scenario>`, Maestro on the Android emulator (A) and the iOS simulator (I).

| Scenario | Tests |
|---|---|
| Folder moved or renamed: full match; position, speed, chapters kept | M `wholeFolderMovedMatchesEveryFileByPathWithoutReadingDurations`; L `wholeFolderMovedIsAFullMatchKeepingPositionSpeedChaptersCoverAndTimes`, `wholeFolderRenamedInPlaceIsAFullMatch`; E `movedFolderIsUnavailableThenRelinkedAndResumesAtTheSamePosition`, `aMovedFolderIsMarkedOnHomeAndRelinkedFromPlayer`; D `moved-folder` (A, I) |
| One file renamed inside, one deleted: partial; confirm and cancel | M `aRenamedFileIsMatchedBySizeAndDuration`, `aRenamedAndADeletedFile`; L `oneFileRenamedAndOneDeletedAsksForConfirmationAndChangesNothing`, `aConfirmedPartialMatchLeavesTheMissingFileOutAndKeepsTheTimeline`, `cancellingAPartialMatchLeavesTheBookMissing`, `aConfirmationOnlyCoversTheFilesItNames`, `aRenamedFileInsideTheFolderIsFoundBySizeAndDuration`; S `aPartialMatchAsksAndRelinksAnywayWhenConfirmed`, `cancellingAPartialMatchChangesNothing`; E `partialMatchConfirmedLeavesTheMissingFileOutAndPlays`, `partialMatchCancelledLeavesTheBookMissing`, `aPartialMatchIsConfirmedFromPlayer`, `aCancelledPartialMatchKeepsTheBookMissing`; D `renamed-file`, `partial` (A, I) |
| Nested `Disc` folders moved together; a sub-folder renamed | M `nestedDiscsMovedTogetherMatchByPath`, `aRenamedSubFolderMatchesByName`, `pathMatchesComeFirstSoANameMatchCantTakeThem`; L `nestedDiscsMovedTogetherAreAFullMatch`, `aRenamedSubFolderIsMatchedByName` |
| Single-file book moved, and renamed | M `aSingleFileMovedMatchesByItsNameAndARenamedOneBySizeAndDuration`; L `aMovedSingleFileIsAFullMatch`, `aRenamedSingleFileIsMatchedBySizeAndDuration`, `anotherSingleFileIsNoMatch` |
| Wrong folder: no match, nothing changed | L `theWrongFolderIsNoMatchAndChangesNothing`; S `theWrongFolderSaysNoneOfTheFilesAreThere` |
| Folder for a single-file book, and the reverse | L `aFolderForASingleFileBookIsTheWrongKind`, `aFileForAFolderBookIsTheWrongKind`; S `theWrongKindSaysWhatToPick`, `aSingleFileBookRelinksWithAFile` |
| Same-name files with other sizes or durations: right one, or refused when ambiguous | M `sameNameDifferentSizePicksTheOneOfItsSize`, `sameNameAndSizeDifferentDurationPicksTheOneOfItsDuration`, `sameNameSizeAndDurationFallsBackToTheParentFolderName`, `anAmbiguousNameIsNotGuessed`, `anAmbiguousSizeAndDurationIsNotGuessed`, `sizeAndDurationNeedsAKnownSizeAndASameDuration`, `aCandidateIsUsedOnce`, `namesMatchIgnoringCase`, `aPathMatchNeedsACompatibleSize`, `unknownSizesAreCompatible`, `eachCandidatesDurationIsReadAtMostOnce`; L `sameNamedFilesOfDifferentSizesPickTheRightOne`, `sameNamedFilesThatCantBeToldApartAreNotGuessed` |
| Another book's source: refused | L `anotherBooksFolderIsRefused`, `anotherBooksFolderPickedAgainIsRefusedByItsFingerprint`, `theNewPlaceIsTheBooksSourceForDuplicates`; S `anotherBooksFolderNamesThatBook`; E `anotherBooksFolderIsRefused` |
| Permission lost: reported as such; relinking the same place restores | L `aRevokedGrantIsAccessLostNotMissing`, `accessLostWinsOverMissing`, `accessLostIsRestoredByPickingTheSameFolderAgain`; S `anAccessLostBookSaysSo`, `anAccessLostBooksRowSaysSo`; E `accessLostIsRestoredByRelinkingToTheSameFolder` |
| Files reappear: available again without a relink | L `aBookWhoseFilesComeBackIsAvailableAgainWithoutARelink`; S `checkingAgainAfterTheFilesCameBackShowsTheBook`, `aBookThatIsAvailableAgainLosesItsLine`, `aBookAvailableAgainGetsItsButtonBack`; E `filesPutBackAreFoundWithoutARelink`; D `files-come-back` (A, I) |
| Detection on library load | L `checkingTheLibraryChecksEveryBookAtMostFourAtATimeOnIo`, `aSecondCheckOfTheLibraryWhileOneRunsDoesNothing`, `aMovedFolderIsMissing`, `aDeletedFileIsMissing`, `aFileRenamedInsideTheFolderIsMissing`; S `creatingHomeChecksEveryBookOnce`, `rowsShowAtOnceAndChangeAsChecksComeBack`; E `homeMarksAMissingBookWhenItLoads`; D the first steps of `moved-folder`, `renamed-file`, `partial` (after a relaunch) |
| Detection on playback start | P `playingAnUnavailableBookStaysPausedAndPlaysNothing`, `accessLostAlsoStaysPaused`, `anAvailableBookIsCheckedAndPlays`; P(fake) `refusesToPlayAnUnavailableBook`; S `nothingPlaysOrMovesWhileUnavailable`, `aBookFoundUnavailableWhenPlayedFromTheMiniPlayerTurnsUnavailable`; D `play-missing` (A, I) |
| A file going missing during playback | P `aPlayingBookIsCheckedEveryThirtySecondsAndStopsWhenAFileWentMissing`, `aFileGoingMissingDuringPlaybackStopsWithTheBookUnavailable`, `aFailureIsCheckedOnceNotOnEveryChange`, `aPanelPlayOfAMissingBookEndsPausedAndUnavailable`; P(fake) `aPlayingBookFoundMissingIsPaused`, `aFailedPlaybackStopsSavesAndChecksTheBook`; S `aFileGoingMissingDuringPlaybackShowsFilesMissing`; E `aFileGoingMissingDuringPlaybackStopsWithFilesMissing`; D `stops-playing` (A, I) |
| Home and Player show unavailable, relink states and results | S `aMissingBooksRowSaysSoAndOffersRelink`, `searchKeepsTheUnavailableLine`, `anUnavailableLastPlayedBookShowsUnavailableAndDoesNotPlay`, `openingChecksTheBook`, `aMissingBookShowsFilesMissingInsteadOfTheControls`, `relinkingAMovedFolderShowsTheBookWhereItWas`, `aRelinkedBookPlaysAgain`, `severalLeftOutFilesAreCounted`, `unchangedAndFailedResults`, `aCancelledPickChangesNothing`, `picksAreIgnoredWhileARelinkRuns`, `closingAResultClearsIt`; E `RelinkUiTest` (all) |
| Relink is idempotent | L `relinkingAnAvailableBookToItsOwnFolderIsANoOp`; S `unchangedAndFailedResults` |
| iOS: the picked folder renamed stays available | L `aPickedFolderRenamedWhereBookmarksFollowStaysAvailable`, `aDeletedAndCopiedFolderOnIosIsRelinkedToTheCopy`; E `aFileRenamedInsideTheFolderOnIosIsRelinkedToTheSameFolder`; D `followed-rename` (I) |
| Left-out files: skipped, found again later, not asked twice | P `filesLeftOutByAPartialRelinkAreNotGivenToTheOutput`, `BookTimelineTest.filesLeftOutByAPartialRelinkAreNotPlayedAndKeepTheOthersPlaces`, `aPositionInALeftOutFilePlaysFromTheNextFile`, `aPositionAfterTheLastPlayedFileIsItsEnd`, `aLeftOutFirstFilePlaysFromTheSecond`; L `aLeftOutFileFoundLaterIsBroughtBack`, `aFileAlreadyLeftOutIsNotAskedAboutAgain`, `filesLeftOutByAPartialRelinkAreNotChecked` |
| A relink after load reloads the files; schema v2 | P `playingAfterARelinkLoadsTheFilesFromTheirNewPlace`; L `LibraryMigrationTest.version1MigratesKeepingEveryBookWithNoFileLeftOut` |
| Only reads, closes what it opens | L `relinkingOnlyReadsAndClosesWhatItOpens` |

## 9. Spec scenario → test map

### bookplate-library
- Book availability: Folder moved → `BookAvailabilityTest.aMovedFolderIsMissing`; One file deleted →
  `aDeletedFileIsMissing`; Grant revoked → `aRevokedGrantIsAccessLostNotMissing`; Picked item renamed on iOS →
  `aPickedFolderRenamedWhereBookmarksFollowStaysAvailable`; Files come back →
  `aBookWhoseFilesComeBackIsAvailableAgainWithoutARelink`; Left-out files are not checked →
  `filesLeftOutByAPartialRelinkAreNotChecked`; Checking the library →
  `checkingTheLibraryChecksEveryBookAtMostFourAtATimeOnIo`.
- Relinking a book (`BookRelinkerTest`): Whole folder moved →
  `wholeFolderMovedIsAFullMatchKeepingPositionSpeedChaptersCoverAndTimes`; Partial match asks first →
  `oneFileRenamedAndOneDeletedAsksForConfirmationAndChangesNothing`; Partial match confirmed →
  `aConfirmedPartialMatchLeavesTheMissingFileOutAndKeepsTheTimeline`; Partial match cancelled →
  `cancellingAPartialMatchLeavesTheBookMissing`; Wrong folder → `theWrongFolderIsNoMatchAndChangesNothing`; Folder
  for a single-file book → `aFolderForASingleFileBookIsTheWrongKind`; File for a folder book →
  `aFileForAFolderBookIsTheWrongKind`; Another book's folder → `anotherBooksFolderIsRefused`; Access restored by
  picking the same folder → `accessLostIsRestoredByPickingTheSameFolderAgain`; Relinking an available book to where
  it is → `relinkingAnAvailableBookToItsOwnFolderIsANoOp`; Unreachable pick → `aPickThatIsGoneFails`.
- Matching files when relinking (`RelinkMatcherTest`): Renamed file inside the folder →
  `aRenamedFileIsMatchedBySizeAndDuration`; Nested sections moved together → `nestedDiscsMovedTogetherMatchByPath`;
  Sub-folder renamed → `aRenamedSubFolderMatchesByName`; Same name, different size →
  `sameNameDifferentSizePicksTheOneOfItsSize`; Same name and size, different duration →
  `sameNameAndSizeDifferentDurationPicksTheOneOfItsDuration`; Ambiguous name → `anAmbiguousNameIsNotGuessed`;
  Single file renamed → `BookRelinkerTest.aRenamedSingleFileIsMatchedBySizeAndDuration`; Different single file →
  `BookRelinkerTest.anotherSingleFileIsNoMatch`.
- Files left out by a partial relink: Found again later → `aLeftOutFileFoundLaterIsBroughtBack`; No second
  confirmation → `aFileAlreadyLeftOutIsNotAskedAboutAgain`.
- Stored books: Read back after import, New book defaults → `BookImporterTest` (unchanged, plus `isMissing` false
  in `aConfirmedPartialMatch…`'s before state); Migration from version 1 →
  `LibraryMigrationTest.version1MigratesKeepingEveryBookWithNoFileLeftOut`.
- Test doubles: Scripted import → `FakeLibraryTest.scriptedResults`; Scripted availability →
  `FakeLibraryTest.scriptedAvailability` (and `scriptedRelinks`).

### bookplate-home
- Unavailable books: Missing book → `HomeStateTest.aMissingBooksRowSaysSoAndOffersRelink`; Access lost →
  `anAccessLostBooksRowSaysSo`; Checked when Home opens → `creatingHomeChecksEveryBookOnce`; Back to normal →
  `aBookThatIsAvailableAgainLosesItsLine`; Tapping opens Player → `RelinkUiTest.aMovedFolderIsMarkedOnHomeAndRelinkedFromPlayer`
  (and Maestro `2-relink.yaml`).
- Mini player: existing scenarios → `MiniPlayerStateTest` (unchanged); Unavailable last-played book →
  `anUnavailableLastPlayedBookShowsUnavailableAndDoesNotPlay`.

### bookplate-player
- Playback checks the book: Play a missing book → `EngineBookPlayerTest.playingAnUnavailableBookStaysPausedAndPlaysNothing`;
  File disappears during playback → `aFileGoingMissingDuringPlaybackStopsWithTheBookUnavailable`; Buffered when the
  file disappears → `aPlayingBookIsCheckedEveryThirtySecondsAndStopsWhenAFileWentMissing`; Play after a relink →
  `playingAfterARelinkLoadsTheFilesFromTheirNewPlace`.
- Unavailable book in Player (`PlayerRelinkTest`): Missing book → `aMissingBookShowsFilesMissingInsteadOfTheControls`
  (and `RelinkUiTest`, which checks that "Play" and "Chapters" are gone); Access lost → `anAccessLostBookSaysSo`;
  Files came back → `checkingAgainAfterTheFilesCameBackShowsTheBook`; Stopped during playback →
  `aFileGoingMissingDuringPlaybackShowsFilesMissing`.
- Relinking from Player: Relink a moved folder → `PlayerRelinkTest.relinkingAMovedFolderShowsTheBookWhereItWas`;
  Folder or file picker → `aSingleFileBookRelinksWithAFile` with `RelinkUiTest` (the folder pick is requested);
  Confirm a partial match → `aPartialMatchAsksAndRelinksAnywayWhenConfirmed`; Cancel a partial match →
  `cancellingAPartialMatchChangesNothing`; Wrong folder → `theWrongFolderSaysNoneOfTheFilesAreThere`; Another
  book's folder → `anotherBooksFolderNamesThatBook`.
- One timeline across a book's files: Left-out file is skipped →
  `EngineBookPlayerTest.filesLeftOutByAPartialRelinkAreNotGivenToTheOutput` and
  `BookTimelineTest.aPositionInALeftOutFilePlaysFromTheNextFile`; other scenarios unchanged.
- Fake player for the UI: Fake refuses an unavailable book → `FakeBookPlayerTest.refusesToPlayAnUnavailableBook`;
  bindings → `PlayerBindingTest` (unchanged).

### lib-document-access
- Test double: Picking and revoking in a test → existing `FakeDocumentAccessTest`; Rename on Android →
  `renamingAPickedFolderLeavesItsReferencesMissingByDefault`; Rename followed like iOS →
  `renamingAPickedFolderIsFollowedLikeIos`; Rename inside a followed folder →
  `renamingAFileInsideAFollowedFolderLeavesItMissing`; Concurrent checks → `countsChecksAndThePeakRunningAtOnce`.

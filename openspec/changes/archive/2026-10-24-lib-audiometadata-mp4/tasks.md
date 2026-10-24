## 1. Detection

- [x] 1.1 `AudioFormat.Mp4`, the `ftyp` brand check before the MPEG check, and the `Mp4Parser` branch in `AudioMetadataReader`
- [x] 1.2 Move image MIME sniffing to an internal `Images` helper shared by `Id3Frames` and MP4

## 2. MP4 parser

- [x] 2.1 `Atoms`: header-only walker (32/64-bit, size 0, truncation flag, per-level cap) and bounded payload reads
- [x] 2.2 `Mp4Tracks`: `tkhd`, `mdhd`, `hdlr`, `tref/chap` per track, `stbl` location only
- [x] 2.3 `Mp4Tags`: `ilst` items, text types, numbers, `trkn`/`disk`, `gnre`, `©nrt` and freeform narrator, `covr`
- [x] 2.4 `Mp4Duration`: `mvhd` → `mdhd` → `tkhd`, version 0 and 1
- [x] 2.5 `Mp4Chapters`: QuickTime chapter track (`stts`, `stsc`, `stsz`, `stco`/`co64`, coalesced sample reads), Nero `chpl`, preference and end times
- [x] 2.6 `Mp4Parser`: guarded steps, start after leading ID3v2 tags

## 3. Fixtures and tests

- [x] 3.1 Extend `scripts/generate-fixtures.sh` with the MP4 set (both / QuickTime / Nero chapters, tags with cover and narrator, ALAC, plain MP4, five-hour 300-chapter M4B header) and generate `src/jvmTest/resources/fixtures/mp4/`
- [x] 3.2 `commonTest` `Mp4Builder`
- [x] 3.3 Tests: detection brands; atom walker (64-bit, size 0, QuickTime `meta`, skipped `mdat`); every tag incl. freeform narrator, `gnre`, UTF-16, JPEG/PNG cover; duration v0/v1 and both fallbacks; QuickTime, Nero, both, `co64`, multi-sample chunks, unreferenced text track; bounded reads on a 500 MB virtual M4B
- [x] 3.4 Malformed MP4: truncated `moov`, cut `ilst` item, tiny/huge sizes, broken chapter track falling back to `chpl`, hostile sample counts, fuzz — partial results, no throw
- [x] 3.5 `jvmTest` fixture tests for each MP4 fixture, and a timed parse of the five-hour M4B with its byte count

## 4. Verification

Platforms, as given for this milestone: JVM tests, the module's tests on the iOS simulator, compiling Android, and the Bookplate and Pacer builds.

- [x] 4.1 `./gradlew :lib:audiometadata:jvmTest` passes
- [x] 4.2 `./gradlew :lib:audiometadata:iosSimulatorArm64Test` passes
- [x] 4.3 `./gradlew :lib:audiometadata:compileAndroidMain` (and the iOS device target compiles)
- [x] 4.4 Bookplate and Pacer still build: `:bookplate:androidApp:assembleDebug`, `:bookplate:sharedApp:jvmTest`, `:pacer:androidApp:assembleDebug`

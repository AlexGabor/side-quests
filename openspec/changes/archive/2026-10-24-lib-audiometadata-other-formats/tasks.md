## 1. Shared pieces

- [x] 1.1 `AudioFormat` entries `Flac`, `Vorbis`, `Opus`, `Wav`, `Aac`; detection order and parser branches in `AudioMetadataReader`
- [x] 1.2 `FlatChapters` helper (sort, drop duplicate starts, ends, cap)
- [x] 1.3 `Text` helpers: little-endian reads, clean-UTF-8-else-Windows-1252 decoding, UTF-16 with or without BOM
- [x] 1.4 `vorbis` package: comment list parser, field mapping, base64 decoder, FLAC picture decoder, `METADATA_BLOCK_PICTURE`/`COVERART`, `CHAPTERnnn`

## 2. Formats

- [x] 2.1 `flac`: block walker, STREAMINFO, VORBIS_COMMENT, PICTURE, CUESHEET block, leading ID3v2 fill-in, chapter preference
- [x] 2.2 `ogg`: page reader with CRC, packet reassembly across pages, Vorbis/Opus headers, last-page search from the end
- [x] 2.3 `wav`: RIFF chunk walker, `fmt `/`data` duration, `LIST/INFO`, `id3 ` chunk through the ID3v2 parser
- [x] 2.4 `aac`: ADTS header, detection with a second sync, bounded scan with exact and estimated durations
- [x] 2.5 `cue`: public `CueSheet` (`parse`, `read`, `chapters`) with decoding, quoting, FILE/TRACK/INDEX and file matching; FLAC uses it for `CUESHEET` comments

## 3. Fixtures and tests

- [x] 3.1 Extend `scripts/generate-fixtures.sh` with the FLAC, Ogg Vorbis, Opus, WAV, ADTS and cue fixtures, and generate them
- [x] 3.2 `commonTest` builders: `FlacBuilder`, `OggBuilder` (CRC, custom lacing), `WavBuilder`, ADTS frames, Vorbis comments
- [x] 3.3 Tests: detection of every format and its negative cases; Vorbis mapping, repeated keys, narrator rules, covers (picture block, legacy, base64 with whitespace), chapter comments
- [x] 3.4 Tests: FLAC (STREAMINFO, unknown total, PICTURE preference, ID3 fill-in, CUESHEET with and without titles, index 1, preference); Ogg (Vorbis and Opus durations, spanning packet, fake page, multiplexed, bounded reads on 100 MB); WAV (durations, unfinished `data`, INFO, `id3 ` chunk, odd padding); ADTS (exact, estimate within 1 %, bounded reads)
- [x] 3.5 Cue tests: single file, several files and matching, missing INDEX 01, track without index, quoting, Windows-1252, UTF-8 BOM, UTF-16 LE/BE, not a cue
- [x] 3.6 Malformed input for every new format: truncation at each structure, hostile sizes, random fuzz; partial results, no throw
- [x] 3.7 `jvmTest` fixture tests for each new fixture

## 4. Verification

Platforms, as given for this milestone: JVM tests, the module's tests on the iOS simulator, compiling Android, and the Bookplate and Pacer builds.

- [x] 4.1 `./gradlew :lib:audiometadata:jvmTest` passes
- [x] 4.2 `./gradlew :lib:audiometadata:iosSimulatorArm64Test` passes
- [x] 4.3 `./gradlew :lib:audiometadata:compileAndroidMain` (and the iOS device target compiles)
- [x] 4.4 Bookplate and Pacer still build: `:bookplate:androidApp:assembleDebug`, `:bookplate:sharedApp:jvmTest`, `:pacer:androidApp:assembleDebug`

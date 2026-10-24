## Why

Bookplate builds books from the user's audio files, so it needs their tags, duration, cover art and chapters. We chose to parse these ourselves in common Kotlin instead of using platform APIs, so Android and iOS give the same results and the parsers can be tested on the JVM against fixture files. This change lays the foundation that the MP4 (M5) and FLAC/Ogg/WAV/AAC/cue (M6) parsers build on, and ships the first format, MP3.

## What Changes

- New module `lib/audiometadata` (app agnostic, common Kotlin only, `sidequests.mobile.library`: Android, iOS and `jvm()` for tests).
- A `ByteSource` interface for random-access reads (size, read at an offset). The platform layer (M7, `lib/documentaccess`) implements it over Android SAF and iOS security-scoped URLs. An in-memory `ByteArraySource` ships for tests and small inputs.
- `AudioMetadataReader`: detects the format from magic bytes (not the file extension) and dispatches to a per-format parser. Unrecognised input gives `null`.
- Result model `AudioMetadata`: format, tags (title, album, artist, album artist, narrator, composer, genre, year, track and disc number and total), duration, embedded cover (bytes + MIME type), and chapters (title, start, end) that nest for sections.
- MP3:
  - ID3v1 and v1.1;
  - ID3v2.2, 2.3 and 2.4: text frames in every encoding, `TXXX` narrator, `APIC`/`PIC`, `CHAP` and `CTOC` (with nested tables of contents), unsynchronisation and the extended header;
  - duration from the Xing/Info or VBRI header, or a CBR estimate when neither is present.
- Malformed or truncated input gives a partial result and never throws. Nothing is logged.
- Fixtures: a script that uses `ffmpeg` to generate short silent clips with tags, chapters and a cover. The generated clips are committed. Cases ffmpeg can't produce (ID3v2.2, VBRI, nested `CTOC`, UTF-16BE, unsynchronisation, malformed frames) are built byte by byte in the tests.

## Capabilities

### New Capabilities
- `lib-audiometadata`: reads audio file metadata through a random-access byte source. Covers format detection, the result model and the formats it supports, starting with MP3. M5 and M6 add requirements to it.

### Modified Capabilities
_None._

## Impact

- New: `lib/audiometadata` and its `settings.gradle.kts` include. No new third-party dependencies: it uses only the Kotlin stdlib, plus `kotlin-test` for tests.
- No changes to Bookplate, Pacer or other `lib/` modules. Nothing depends on the new module yet: `lib/documentaccess` (M7) and `bookplate/core/library` (M8) will.
- Pacer's public link parameters are unaffected.

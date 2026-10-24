## Context

Bookplate (M8) assembles books from audio files. For each file it needs tags, duration, cover and chapters. The roadmap settles that these come from our own parsers, written in common Kotlin and tested on the JVM against fixtures. Files are reached through SAF on Android and security-scoped URLs on iOS (M7, `lib/documentaccess`). Neither gives a `java.io.File`, and audiobooks can be gigabytes, so the parsers only ever seek and read small ranges.

This is the first of three parser milestones: M4 covers the foundation and MP3, M5 MP4/M4B, and M6 FLAC, Ogg, WAV, ADTS AAC and `.cue`. The shapes chosen here have to take those formats in without being reworked.

`lib/audiometa/` and `lib/documents/` in the working tree hold only stale `build/` output and are not touched.

## Goals / Non-Goals

**Goals:**
- One app-agnostic module, `lib/audiometadata`, in pure common Kotlin with no dependencies beyond the stdlib.
- A small, stable public API: `ByteSource`, `AudioMetadataReader`, `AudioFormat`, `AudioMetadata`, `AudioTags`, `Cover`, `Chapter`.
- Complete MP3 support as listed in the spec, tolerant of real-world breakage.
- Fast JVM tests, using ffmpeg fixtures plus byte-level builders for the cases ffmpeg can't produce.

**Non-Goals:**
- Writing tags, or any other change to files.
- Other formats (M5, M6), platform file access (M7), and assembling a book from several files (M8).
- Exact gapless duration (LAME encoder delay and padding), and seek tables.
- Compressed or encrypted ID3v2 frames, which are skipped. Lyrics, comments, ratings and other frames the model doesn't hold.

## Decisions

### 1. A single module, no api/impl split
`lib/audiometadata` is one Gradle project with the package `com.alexgabor.lib.audiometadata`. The api/impl split in `modules.md` exists to hide platform code behind an interface and bind it in `sharedApp`. This module has no platform code: the same pure function runs everywhere, and its only platform-facing seam, `ByteSource`, is an interface that callers implement. A split would add a Koin binding and a fake for no benefit. Tests in M8 can run the real parser on fixtures, which is more faithful than a fake. If a fake is ever needed, `AudioMetadataReader` can become an interface without breaking callers of `read`. This is not a deviation: `modules.md` says lib modules *may* be split.

### 2. `ByteSource` is blocking
```kotlin
interface ByteSource {
    val size: Long
    fun read(position: Long, length: Int): ByteArray   // ≤ length bytes; fewer at the end
}
```
- Blocking rather than `suspend`. The architecture rule is that blocking disk work is moved to `CoroutineDispatchers.IO` by the caller (`withContext`). Both M7 back ends (`ParcelFileDescriptor`/`FileChannel`, `NSFileHandle`) are blocking anyway. Keeping it blocking means the parsers are plain functions, with no coroutine dependency and simpler tests.
- Returning a fresh `ByteArray` instead of filling a caller buffer keeps parser code simple. Reads are few and small (tens of KB, apart from the tag body).
- Implementations may throw on I/O errors. The reader treats an exception as the end of data (see 6).
- An internal `Reader` wraps the source with bounds-checked helpers (`bytes(pos, n)`, `u8`, `u32`, …), so no parser does offset arithmetic against `size` itself.
- `ByteArraySource` is public: M5, M6 and M8 tests and in-memory callers need it.

### 3. Detection, then dispatch, through one internal seam
```kotlin
enum class AudioFormat { Mp3 }                     // M5: Mp4, M6: Flac, Ogg, Wav, Aac
object AudioMetadataReader {
    fun detect(source: ByteSource): AudioFormat?
    fun read(source: ByteSource): AudioMetadata?
}
internal fun interface FormatParser { fun parse(source: Reader, into: MetadataBuilder) }
```
- `detect` reads the first 10 bytes. On `ID3` it skips each ID3v2 tag by its declared size (plus the footer), then reads a 64-byte probe. Signatures are checked in a fixed order against the probe, so M5 adds `ftyp` at offset 4, and M6 adds `fLaC`, `OggS`, `RIFF…WAVE`, and the ADTS sync (layer bits `00`, which is how it differs from MPEG audio). MP3 is the MPEG audio header check. The "ID3 but nothing recognisable" fallback to MP3 comes last, so formats that can carry a leading ID3 tag (ADTS, sometimes FLAC) still win.
- Where MP3 audio does not start right after the tag (padding, or junk the encoder left), the MP3 parser itself scans for the first frame. Detection does not.
- `read` maps the format to a `FormatParser` with an exhaustive `when`. A new format is an enum entry, a probe check and a parser. The result model doesn't change.
- `null` means "not a supported audio file", so M8 can skip non-audio files in a folder. A recognised but broken file gives a partial `AudioMetadata`, never `null`.
- An `object` rather than a class, because it holds no state or configuration.

### 4. Result model
```kotlin
data class AudioMetadata(val format: AudioFormat, val tags: AudioTags, val duration: Duration?,
                         val cover: Cover?, val chapters: List<Chapter>)
data class AudioTags(title, album, artist, albumArtist, narrator, composer, genre: String?,
                     year, trackNumber, trackTotal, discNumber, discTotal: Int?)
class Cover(val data: ByteArray, val mimeType: String)   // content-based equals/hashCode
data class Chapter(val title: String?, val start: Duration, val end: Duration,
                   val children: List<Chapter> = emptyList())
```
- Sections are chapters with children, not a separate type. A `CTOC` inside a `CTOC` maps to this directly, and so will M5 and M6 sources (a cue sheet is flat; QuickTime and Nero chapters are flat). M8's folder-based sections can build the same tree. Only leaf chapters are guaranteed to be playable ranges. A section's start and end span its children.
- `narrator` and `composer` are separate fields. Audiobook taggers use both, and the rule for which one to show as the author belongs to M8, not to the parser.
- `kotlin.time.Duration`, not milliseconds, so units can't be mixed up.
- `Cover` is a plain class, because a data class would compare `ByteArray` by reference.
- `AudioTags` is its own type so M5 and M6 can fill the same fields from `ilst` and Vorbis comments.

### 5. MP3 parser layout
Internal files in `mp3/` and `id3/`:
- `Id3v2` reads a tag into a list of raw frames (`id`, `data`). It handles versions, the extended header, unsynchronisation, frame flags and the non-syncsafe size quirk. Frame IDs from 2.2 are mapped to their 2.3 equivalents (`TT2`→`TIT2`, `PIC`→`APIC` with a converted body, `TXX`→`TXXX`, …), so the next stage sees one vocabulary.
- `Id3Frames` decodes text (all four encodings, implemented by hand because common Kotlin has no charset API except UTF-8), pictures, `CHAP` and `CTOC`. `CHAP` and `CTOC` sub-frames go back through the same frame reader with the tag's version.
- `Id3Chapters` builds the tree (root selection, cycle guard via a visited set, sorting unordered entries, fixing end times).
- `Id3v1` reads the 128-byte tag. `Genres` holds the ID3v1 list (0–191).
- `MpegFrame` decodes the 4-byte header. `Mp3Duration` handles Xing/Info (offset by version and channel mode), VBRI (header + 32) and the CBR estimate.
- Scanning for the first frame looks at most 64 KB past the tags. A candidate is accepted when the next frame, at the computed length, is also a valid header, or when the candidate sits right at the end of the tags. This avoids false syncs inside tag padding or junk.

Non-syncsafe 2.4 sizes (an iTunes bug): if any size byte has its top bit set, read the size as a plain integer. Otherwise use syncsafe, unless the syncsafe size lands on a non-frame and the plain one lands on a valid frame ID, padding or the end of the tag.

### 6. Partial results and safety
- Everything a parser finds goes into a `MetadataBuilder` as soon as it is decoded. `read` runs the parser inside `try { … } catch (e: Exception)` and returns `builder.build()` either way. Within MP3, the ID3v2, ID3v1 and duration steps are each guarded on their own, so a broken tag doesn't lose the duration, and the reverse.
- Each frame is decoded in its own guard, so one bad `APIC` doesn't stop the frames after it. A frame whose size overruns the tag ends that tag, because the next offset can't be trusted.
- Allocation is bounded: a tag body read is `min(declared, source.size − pos, 64 MB)`. Every inner length is checked against the slice it lives in. `Reader.bytes` never allocates more than what is left in the source.
- `Error`s (e.g. `OutOfMemoryError`) are not caught. The bounds above mean hostile sizes can't cause them.
- Nothing is logged. There is no logger in `lib/`, and partial results are expected.

### 7. Fixtures
- `lib/audiometadata/scripts/generate-fixtures.sh` uses `ffmpeg` to write `src/jvmTest/resources/fixtures/mp3/`. Clips are 1–3 s of silence from `anullsrc` at 8–22 kHz mono and low bitrate, with `-bitexact` so there is no encoder version string. Each file is a few KB, together under 100 KB. The set: v2.3 and v2.4 with full tags, UTF-16 text, cover and chapters; v2.3 + ID3v1; ID3v1 only; no tags; VBR (Xing); CBR (Info); CBR without Xing (`-write_xing 0`). An 8×8 PNG cover is generated in the same script.
- ffmpeg can't write ID3v2.2, UTF-16BE, unsynchronisation, extended headers, nested `CTOC`, VBRI, non-syncsafe sizes or broken frames. A test-only `Id3Builder` and an `MpegFrames` helper build these byte by byte in `commonTest`. Expected bytes sit next to each test, which reads better than opaque binary files.
- Fixture tests live in `jvmTest`, because they need classpath resources. Builder tests live in `commonTest`, so they also run on iOS when someone runs those tests. Only `jvmTest` is part of verification.
- A `CountingSource` test wrapper with a virtual size (a real header followed by up to 100 MB of synthetic zeros) proves the "reads only what it needs" requirement without large files.

## Risks / Trade-offs

- [Xing frame counts include or exclude the info frame, depending on the encoder, and gapless padding is ignored] → durations can be off by about 50 ms. That is negligible for audiobooks. Tests allow one frame plus the gapless window of tolerance against ffprobe's values.
- [VBR files with no header get the CBR estimate, which can be wrong] → accepted. Counting frames would mean reading the whole file, which the byte-source rule forbids. M8 can't do better without decoding.
- [Hand-rolled text decoding] → covered by a test per encoding, including UTF-16 surrogate pairs and missing BOMs (falls back to little-endian, as most writers do).
- [Compressed ID3v2.3/2.4 frames are skipped] → rare in practice. Adding zlib to common Kotlin isn't worth it now. The spec says so.
- [Detection falls back to MP3 for ID3 + unknown] → a non-MP3 file wrapped in ID3 is reported as MP3 with tags and no duration. That is the most useful answer for a truncated file, and the more specific M5/M6 signatures are checked first.

## Open Questions

_None._ How `narrator` vs `composer` vs `artist` map to a book's author is left to M8.

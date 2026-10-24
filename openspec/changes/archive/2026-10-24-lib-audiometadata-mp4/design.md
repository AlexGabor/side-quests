## Context

M4 built `lib/audiometadata`: a blocking `ByteSource`, a bounds-checked internal `Reader`, detection that probes 64 bytes after any ID3v2 tags, one `FormatParser` per `AudioFormat`, and a first-value-wins `MetadataBuilder` that keeps partial results. M5 adds MP4, the container most audiobooks come in (`.m4b`), plugged in the same way as MP3. M6 adds the remaining formats; M8 assembles books from the results.

MP4 differs from MP3 in two ways that shape this design:
- The interesting data sits in `moov`, which may come before or after `mdat`. `mdat` is the audio itself, often gigabytes, sometimes with a 64-bit size.
- A long book's `moov` is big too: a 20-hour AAC track at 44.1 kHz has about 3.1 million samples, so its `stsz` table alone is about 12 MB. Reading `moov` whole would break the "only the regions it needs" rule. Only a few small atoms are needed: `mvhd`, per track `tkhd`/`mdhd`/`hdlr`/`tref`, `ilst`, `chpl`, and the chapter track's sample tables.

What ffmpeg 7.1 writes, checked while planning: the `ipod` (M4A/M4B) and `mp4` muxers write *both* a QuickTime chapter track (a `text` track referenced by the sound track's `tref/chap`, samples in `mdat`) and a Nero `chpl` in `moov/udta`. `chpl` holds at most 255 chapters (8-bit count). `©wrt` is written for `composer`; there is no metadata key for `©nrt`.

## Goals / Non-Goals

**Goals:**
- `AudioFormat.Mp4` with the spec's tags, cover, duration and both chapter styles, through the existing seams.
- Reading a multi-hour M4B touches a few KB of the file, regardless of its size or `moov` layout.
- Same damage tolerance as MP3: partial results, no throws, bounded allocation.

**Non-Goals:**
- Writing tags. Fragmented MP4 (`moof`), DRM (Audible `.aax`), video tracks, edit lists (`elst`) and gapless offsets.
- Nested chapters: MP4 has no standard for sections.
- QuickTime `.mov` brands (`qt  `), 3GP and the `keys`/`mdta` metadata style.

## Decisions

### 1. One `Mp4` format, detected from `ftyp` brands
`AudioFormat` gains `Mp4`; M4A, M4B and MP4 are the same container and the result model has no use for telling them apart. Detection checks the probe for `ftyp` at offset 4 and accepts it when the major brand or any compatible brand (as far as the 64-byte probe reaches) is in the spec's list. The check runs before the MPEG frame check, so the `ftyp` size bytes can never be mistaken for a frame sync. It sits after the ID3v2 skip, so an M4A that some tagger prefixed with ID3 still works; the parser then starts its walk at the end of those tags.

The brand list is the audio-capable ISO/Apple brands. A plain video `.mp4` (`isom`, `mp42`) is also detected as `Mp4`; its audio track's tags and duration are still correct, and deciding whether to import video files belongs to M8, which sees the file list. `heic`/`mif1` (images), `qt  ` and 3GP are not accepted. Alternative considered: requiring a sound track before saying `Mp4`. That needs walking `moov`, which `detect`'s small-probe contract doesn't allow.

No change to the shared model or detection structure beyond the enum entry and one `when` branch.

### 2. Atom walker reads headers, not atoms
`mp4/Atoms.kt` holds:
```kotlin
internal class Atom(val type: String, val start: Long, val payload: Long, val end: Long, val truncated: Boolean)
internal fun Reader.atoms(from: Long, to: Long): List<Atom>   // one level, headers only
```
- Each header is one 16-byte read (8 for the size and type, 8 more only used for a 64-bit size). Size 1 → 64-bit, size 0 → to `to`, size < header → stop the level. `end` is clamped to `to` and the file size; `truncated` says it was clamped.
- A level is capped at 4096 atoms, so a hostile file of millions of 8-byte atoms can't turn into millions of reads.
- `Reader.payload(atom, max)` reads a leaf atom's payload, bounded by `max` and by what is left in the source. Callers skip `truncated` leaves (spec: leaves that run past their parent are dropped), but walk truncated containers.
- The parser descends only along known paths (`moov/trak/mdia/minf/stbl`, `moov/udta/meta/ilst`, `trak/tref`), so recursion depth is fixed and unknown atoms are never opened.
- The `ilst` and the chapter track's sample tables are read whole into memory (bounded: 32 MB for `ilst`, which holds the cover; 4 MB per sample table) and walked with the same `atoms` function over a `Reader(ByteArraySource(bytes))`. One walker serves both the file and in-memory parsing, so the size rules are the same everywhere.

Alternative considered: reading `moov` whole and parsing in memory. Simpler, but a 20-hour book's `moov` is 10+ MB, mostly the sound track's `stsz`/`stco`, which nothing here needs.

### 3. Parser layout
Internal files in `mp4/`:
- `Mp4Parser` (`FormatParser`): finds `moov` and `udta`, then runs three guarded steps (tags, duration, chapters), mirroring `Mp3Parser`.
- `Mp4Tracks`: per `trak`, reads only `tkhd` (id, duration), `mdhd` (timescale, duration), `hdlr` (handler type), `tref/chap` ids, and remembers where `stbl` is without reading it.
- `Mp4Tags`: `ilst` items → `MetadataBuilder`, including `covr`.
- `Mp4Duration`: `mvhd` → sound track `mdhd` → sound track `tkhd`, version 0 and 1. Converts `value / timescale` as whole seconds plus a remainder, so 64-bit values can't overflow.
- `Mp4Chapters`: QuickTime chapter track and `chpl`, the preference rule, and end times.

Text decoding reuses `Id3Text.decode` (UTF-8 and UTF-16 with or without BOM), and the genre table reuses `Genres`. Image-type sniffing moves from `Id3Frames` to an internal `Images.sniffMimeType` in the root package so `covr` items of unknown type can use it; behaviour for MP3 is unchanged.

### 4. Narrator mapping
There is no standard MP4 narrator atom. The mapping, by preference:
1. `©nrt`: written by Mp3tag, OpenAudible and Audible-derived tools.
2. A freeform `----` item named `NARRATOR` or `NARRATEDBY` (case and non-letters ignored, so `Narrated By` matches), under any `mean`: this is how foobar2000, beets and older Mp3tag versions store it.

`©wrt` stays `composer`, as `TCOM` does for MP3. Some taggers put the narrator there, but others put the author there, and M4 already decided that choosing among artist, composer and narrator for a book belongs to M8, which has both fields.

### 5. Chapters
- **QuickTime**: the referencing track is any track with `tref/chap`; the first referenced id whose track has handler `text` or `sbtl` is the chapter track. Its tables are decoded into: sample count and sizes (`stsz`, uniform or table), sample durations (`stts`), chunk offsets (`stco` or `co64`) and the chunk → samples-per-chunk runs (`stsc`). Samples are located by walking chunks in order. Where a chunk's samples are contiguous and together at most 64 KB (ffmpeg puts all chapter samples in one chunk), they are read in one call; otherwise each sample is read alone, at most 1 KB. Sample text: 16-bit length, then UTF-8, or UTF-16 if it starts with a BOM; anything after (`encd` atoms) is ignored.
- **Nero `chpl`**: parsed as the spec says, mirroring ffmpeg's reader (version 1 has 4 reserved bytes; version 0 doesn't). Times in 100 ns units. ffmpeg caps the count at 255, so for long books only the QuickTime track is complete, one more reason to prefer it.
- **Preference**: QuickTime if it produced any chapter with a readable sample, else `chpl`. Each source is guarded on its own, so a broken chapter track falls back to `chpl`.
- **End times** are computed once both are built, after the duration is known. The cap of 10 000 chapters bounds memory for hostile sample counts.

### 6. Damage and allocation bounds
- All reads go through `Reader`, which never allocates more than the source has left. Each sample-table entry count is checked against its payload's real length before use.
- An `ilst` item that runs past the `ilst` (e.g. a cut-off file) is dropped and the walk of `ilst` stops there; the items before it are kept.
- Each `ilst` item is decoded in its own guard, as ID3 frames are.

### 7. Fixtures
`scripts/generate-fixtures.sh` gains an MP4 section writing `src/jvmTest/resources/fixtures/mp4/`, from 8 kHz mono silence (AAC at 8 kbit/s, or ALAC), with `-bitexact`:
- `tags-both.m4b`: full tags, PNG cover, three chapters, as ffmpeg writes them (QuickTime track and `chpl`). The `chpl` titles are patched (`Opening` → `OPENING`, same length) so a test can tell which source won. `composer` is written as `©wrt`; the grouping `©grp` is renamed to `©nrt` in place (same length), since ffmpeg has no narrator key.
- `chapters-quicktime.m4a`: the `chpl` atom renamed to `free`.
- `chapters-nero.m4a`: the `tref` atom renamed to `free`, so the text track is unreferenced.
- `alac.m4a`: ALAC, tags, no chapters. `plain.mp4`: `-f mp4` (brand `isom`), no tags.
- `long-header.m4b`: 5 h 0 m 1.92 s of ALAC silence with 300 one-minute chapters and `+faststart`, so `moov` comes first. The length is a whole number of 4096-sample ALAC frames, so every frame has the same size and `stsz` is 20 bytes. The file is cut to 16 KB: `moov`, the chapter samples and the start of an `mdat` that declares far more than is there. A test serves it through `CountingSource`, padded back to the declared size.

Binary patches use `perl` (present on macOS), with the offsets found by searching, not hard-coded. Cases ffmpeg can't write are built in `commonTest` by an `Mp4Builder` (atoms by type and payload, 64-bit headers, size 0, version 1 boxes, `co64`, multiple samples per chunk, freeform items, `gnre`, JPEG covers, broken atoms, and a virtual 500 MB file with a 4 MB `stsz`).

## Risks / Trade-offs

- [Edit lists are ignored] → a chapter track with an `elst` offset would shift chapters. ffmpeg, iTunes and Audible-derived files don't use one on chapter tracks; noted as a non-goal.
- [Video MP4s are detected as `Mp4`] → M8 decides which files to import; tags and duration are still those of the file.
- [Chapter sample durations vs. movie duration] → the last chapter ends at the movie duration, which can be slightly longer than the text track. That matches what players show.
- [`©wrt` is not used for the narrator] → books tagged that way show the narrator as composer; M8 can still use it.
- [Per-sample reads when chapter samples are scattered] → up to 10 000 reads of ≤ 1 KB. Real files keep them in one or a few chunks, which are coalesced.

## Open Questions

_None._

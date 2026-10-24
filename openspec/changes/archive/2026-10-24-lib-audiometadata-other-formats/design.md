## Context

M4 and M5 built `lib/audiometadata`. It has:
- a blocking `ByteSource` and a bounds-checked internal `Reader`;
- detection over a 64-byte probe, taken after any leading ID3v2 tags;
- one `FormatParser` per `AudioFormat`, each reading in separate guards;
- a first-value-wins `MetadataBuilder`, which keeps partial results.

MP3 and MP4 plug in that way. M6 adds the remaining formats the same way, so after it M8 only has to pick which results make a book.

What ffmpeg 7.1 writes, checked while planning:

| Muxer | Tags | Cover | Chapters | Other |
|---|---|---|---|---|
| `flac` | VORBIS_COMMENT, with `album_artist` → `ALBUMARTIST`, `track` → `TRACKNUMBER` (as `3/12`), `disc` → `DISCNUMBER` | PICTURE (type from the stream comment) | none | 8 KB PADDING block |
| `ogg` (Vorbis, Opus) | comments, including any custom key | can't attach a picture stream; a `METADATA_BLOCK_PICTURE` or `COVERART` key passed with `-metadata` is written as given | `CHAPTERxxx` / `CHAPTERxxxNAME` | — |
| `wav` | `LIST/INFO` (`INAM`, `IART`, `IPRD`, `IGNR`, `ICRD`, `IPRT`, …) | none | none | writes no `id3 ` chunk |
| `adts` | none | none | none | bare frames |

## Goals / Non-Goals

**Goals:**
- `Flac`, `Vorbis`, `Opus`, `Wav` and `Aac` in `AudioFormat`, each with the tags, cover, chapters and duration the spec lists, plugged in through the existing seams.
- A public cue sheet parser whose chapters M8 can attach to one audio file.
- Reads stay small, whatever the file size: metadata blocks and chunks are read by header, and the end of an Ogg file is read from the end.
- Same damage tolerance as MP3/MP4: partial results, no throws, bounded allocation.

**Non-Goals:**
- Writing tags.
- Ogg FLAC, Speex and Theora streams; chained Ogg (several logical streams in sequence).
- RF64/BW64 WAV larger than 4 GB, and the `bext`/`iXML` chunks.
- APE tags, and tags in raw AAC files. AAC is duration only, as the roadmap says.
- Finding sidecar `.cue` files and pairing them with audio files: that is M8's job. M8 passes the audio file's name to `CueSheet`.
- CD-TEXT, `CATALOG`, `ISRC`, `FLAGS`, `PREGAP` and `POSTGAP` in cue sheets.

## Decisions

### 1. Formats and detection
`AudioFormat` gains `Flac`, `Vorbis` (Ogg Vorbis), `Opus` (Ogg Opus), `Wav` and `Aac` (ADTS). Vorbis and Opus are separate entries: their durations are computed differently, and M8 may want to tell them apart. Detection looks at the probe after the ID3v2 skip, in this order:
1. `ftyp`: MP4, as before.
2. `fLaC`: FLAC.
3. `OggS`, a page with the beginning-of-stream flag, and its first packet starts with `\x01vorbis` or `OpusHead`: Vorbis or Opus. Another Ogg codec gives `null`.
4. `RIFF` with `WAVE` at offset 8: WAV.
5. An MPEG audio frame: MP3, as before.
6. An ADTS header: a 12-bit sync, layer `00`, a sample-rate index below 13 and a frame length of at least the header size. A frame that ends before the file does must be followed by a second sync. Layer `00` is reserved in MPEG audio, so an ADTS header can never pass as an MP3 frame, nor the reverse. The second-header check keeps stray `FF Fx` bytes from being taken for AAC. It reads a few bytes past the probe.
7. A leading ID3v2 tag followed by nothing recognisable: MP3, as before.

Alternative considered: one `Ogg` entry with a codec field. Rejected because the result model has no codec field, and adding one only for Ogg would be lopsided.

### 2. Shared Vorbis comments (`vorbis` package)
`VorbisComments.parse(bytes, start)` reads the little-endian vendor string and the `KEY=value` list. Every length is checked against the bytes actually there, and the count is capped by the bytes left. Keys are compared case-insensitively.

`VorbisComments.apply(comments, into)` maps the fields as follows. Where a key appears more than once, the values are joined with `; `, as ID3 multi-values are.

| Field | Keys, by preference |
|---|---|
| title | `TITLE` |
| album | `ALBUM` |
| artist | `ARTIST` |
| albumArtist | `ALBUMARTIST`, `ALBUM ARTIST`, `ALBUM_ARTIST` |
| narrator | `NARRATOR`, `NARRATEDBY` (any key whose letters spell one of these, so `NARRATED BY` matches), else `PERFORMER` |
| composer | `COMPOSER` |
| genre | `GENRE` |
| year | first four digits of `DATE`, else `YEAR`, else `ORIGINALDATE` |
| track number / total | `TRACKNUMBER` as `n` or `n/m`; total also from `TRACKTOTAL`, `TOTALTRACKS` |
| disc number / total | `DISCNUMBER` as `n` or `n/m`; total also from `DISCTOTAL`, `TOTALDISCS` |

Why the narrator maps this way:
- `NARRATOR` is what the audiobook taggers write: Mp3tag's audiobook profile, beets, and the m4b-tool and OpenAudible exports.
- The Vorbis comment spec defines `PERFORMER` as "the artist(s) who performed the work". For a spoken recording that is the reader, so it is the fallback.
- `COMPOSER` stays `composer`, as `TCOM` and `©wrt` do for MP3 and MP4: M8 decides which of artist, composer and narrator names the author.

Covers:
- `METADATA_BLOCK_PICTURE` is a base64 FLAC picture block. It goes through the same decoder as FLAC's PICTURE (decision 3) and prefers the front cover.
- The legacy `COVERART` is base64 image bytes, with an optional `COVERARTMIME`. Its MIME type is sniffed when `COVERARTMIME` is missing, and it is not marked as a front cover.
- Base64 is decoded by a small hand-written decoder that skips whitespace and other non-alphabet characters, and doesn't need padding. Taggers wrap long values, and Kotlin's `Base64` rejects that.

Chapters: `CHAPTERnnn=HH:MM:SS.fff` (also `MM:SS.fff`, and any number of digits in `nnn`) with an optional `CHAPTERnnnNAME`. Entries are sorted by start. A `CHAPTERnnnNAME` without its time is ignored.

### 3. FLAC (`flac` package)
- The parser starts after any ID3v2 tags. It walks the metadata blocks: a 1-byte header (last flag and type) and a 24-bit size. It stops at the last-block flag, at a block that runs past the file, or after 1024 blocks.
- STREAMINFO: the sample rate (20 bits) and total samples (36 bits) give the duration. Total samples of 0 means unknown, so the duration is `null`.
- VORBIS_COMMENT goes through decision 2. PICTURE blocks hold type, MIME, description, width, height, depth, colours and data; the MIME is sniffed when it is empty, and type 3 is the front cover. Each block is bounded by its 24-bit size, so at most 16 MB.
- A leading ID3v2 tag is read with the existing `Id3v2` and `Id3Frames`, after the FLAC blocks. Since the builder keeps first values, the Vorbis comments win and the ID3 tag only fills gaps. Chapters from that tag are ignored, because FLAC has its own.
- Chapter sources, by preference:
  1. `CHAPTERnnn` comments, which have titles.
  2. The CUESHEET block. Each track except the lead-out (170 for a CD, 255 otherwise) is a chapter. It starts at the track offset plus its index 1 offset, or its first index when it has no index 1, in samples ÷ STREAMINFO's rate. The block has no titles, so they come from a `CUESHEET` Vorbis comment (embedded cue text, as foobar2000 writes it) when it has the same track number, else they are absent. The last chapter ends at the lead-out offset.
  3. A `CUESHEET` comment on its own, parsed as a cue sheet (decision 6), using its first FILE.

### 4. Ogg (`ogg` package)
- `OggPages` reads one page header (27 bytes plus the lacing table) at a time and hands back segments. Packets are reassembled across pages from the lacing values: a 255 continues the packet, anything less ends it. Only pages of the first beginning-of-stream page's serial number are used, so a multiplexed file still works.
- The parser reads the first two packets of that stream: the identification header, then the comment header. A comment header is bounded to 32 MB, because covers make it large. Reading stops after the comment header, or after 1024 pages, whichever comes first.
- Vorbis: the identification header gives the sample rate. The comment header is `\x03vorbis` + comments; the framing bit is ignored.
- Opus: `OpusHead` gives the pre-skip at offset 10. The comment header is `OpusTags` + comments.
- Duration: the granule position of the last page of the stream. It is found by reading back from the end:
  - Read the last 16 KB and scan it backwards for `OggS`.
  - Take the first candidate that is a whole page with a valid CRC, the stream's serial number and a granule position other than −1.
  - If no window holds such a page, retry with 64 KB, then 256 KB.
  - The CRC check (the Ogg polynomial `0x04C11DB7`, table-driven) keeps an `OggS` inside audio data from being taken for a page. A page is at most 65 307 bytes, so 256 KB always holds a whole one.
  - Vorbis duration is granule ÷ rate. Opus duration is (granule − pre-skip) ÷ 48 000, whatever the input rate.

Alternative considered: scanning forward through every page. Rejected because a 20-hour Opus book has about 50 000 pages.

### 5. WAV (`wav` package) and ADTS (`aac` package)
- WAV walks the RIFF chunks after `RIFF....WAVE`:
  - Each chunk is a little-endian size followed by its payload, padded to an even length.
  - The walk stops after 4096 chunks or at a chunk that runs past the file. `data` is the exception: its size is clamped to the bytes that are there, and its payload is never read.
  - `fmt ` gives the byte rate, and duration = `data` size ÷ byte rate. A `data` size of `0xFFFFFFFF`, or one larger than the file (as streaming writers leave it), is replaced by the bytes left in the file.
  - `LIST` of type `INFO` gives tags: `INAM` title, `IPRD` album, `IART` artist, `IGNR` genre, `ICRD` year (first four digits), `IPRT` or else `ITRK` track (`n` or `n/m`). Values are NUL-terminated, and decoded as UTF-8 when valid, else Windows-1252.
  - An `id3 ` or `ID3 ` chunk (Mp3tag and foobar2000 write it) is read with the existing ID3v2 parser, through a `Reader` over that chunk. It is applied before INFO, so its richer values win and INFO fills gaps. Its `APIC` gives the cover and its `CHAP`/`CTOC` give chapters, as for MP3.
- ADTS scans frames from the first header:
  - Each frame's 13-bit length leads to the next header. Each frame holds (raw blocks + 1) × 1024 samples at the header's sample rate.
  - Reads are 64 KB chunks, and the scan stops after 256 KB of frames. If the scan reaches the end of the audio, the duration is exact.
  - Otherwise it is an estimate, scanned duration × (audio bytes ÷ scanned bytes), which assumes the rest of the file averages the same bitrate as its start. A trailing ID3v1 tag is not counted as audio.
  - Losing sync stops the scan and also gives an estimate. A file with no valid frame has no duration.

### 6. Cue sheets (public `CueSheet`, internals in `cue`)
```kotlin
class CueSheet(val title: String?, val performer: String?, val files: List<File>) {
    class File(val name: String?, val tracks: List<Track>)
    class Track(val number: Int, val title: String?, val performer: String?, val start: Duration?)
    /** Chapters for one audio file, matched by name. */
    fun chapters(audioFileName: String?, duration: Duration?): List<Chapter>
    companion object {
        fun parse(bytes: ByteArray): CueSheet?
        fun read(source: ByteSource): CueSheet?   // reads at most 1 MB
    }
}
```
- Decoding:
  - A UTF-8 BOM is dropped.
  - UTF-16 is detected by its BOM, or by NULs in alternate bytes.
  - Otherwise the text is UTF-8 when it decodes cleanly, else Windows-1252. EAC and other Windows rippers write the system code page, and Windows-1252 also covers every Latin-1 letter.
- Lines: commands are case-insensitive. Values may be quoted (`"…"`, with embedded spaces) or bare. `REM` and unknown commands are ignored.
  - `TITLE` and `PERFORMER` before the first `TRACK` belong to the sheet, and after it to that track.
  - `FILE` starts a new file entry. Tracks that come before any `FILE` belong to an entry with no name.
  - `INDEX nn mm:ss:ff` uses 75 frames per second. A track starts at `INDEX 01`, else at its lowest index.
  - A track with no index at all has no start, and gives no chapter.
- `parse` returns `null` when the text has no `TRACK`. That is how M8 tells a cue sheet from junk.
- `chapters(name, duration)`:
  - Picks the FILE entry whose name equals `audioFileName`, comparing case-insensitively, ignoring any directory part, and falling back to the name without its extension. The fallback is there because cue sheets often name the `.wav` that was later encoded to `.flac`.
  - When the sheet has exactly one entry, that entry is used whatever its name.
  - Several entries and no match give no chapters. In the picked entry, each track with a start becomes a chapter, sorted by start.
- Chapter end times use the shared rule (decision 7).

Alternative considered: hiding cue parsing behind `AudioMetadataReader.read(audio, cue)`. Rejected: M8 has to know which `.cue` belongs to which file and may show cue chapters for a multi-file book, so a plain parser is the simpler seam.

### 7. Flat chapter ends (shared helper)
The new sources all produce flat lists: Vorbis `CHAPTERnnn`, the FLAC CUESHEET and cue sheets. `FlatChapters.build(starts, duration, lastEnd)` sorts them by start and gives each one an end:
- where the next chapter starts;
- for the last one, at `lastEnd` (the CUESHEET lead-out) when that is after its start, else at the duration when that is after its start, else at its start.

It drops exact duplicate starts, keeping the first, and caps the list at 10 000 entries, as MP4 does. MP4 and ID3 keep their own builders: their rules differ (QuickTime sample durations, CHAP's own end times), and they are tested as they are.

### 8. Damage and allocation bounds
- Every new parser goes through `Reader`, which never allocates more than the source has left. Lengths inside in-memory blocks (Vorbis comment lengths, picture fields, cue lines) are checked against the array before slicing.
- Each parser runs tags, cover, chapters and duration in separate guards, so a broken comment packet still leaves the duration.
- A Vorbis comment list that is cut off keeps the entries before the cut. An Ogg comment packet cut off by the end of the file is parsed as far as it goes.

### 9. Fixtures
`scripts/generate-fixtures.sh` gains sections writing `fixtures/{flac,ogg,wav,aac,cue}/`. They use 8 kHz mono silence and `-bitexact`, reuse `cover.png` and the three-chapter metadata file, and use perl for every binary patch:
- `flac/tags.flac`: full tags, front-cover PICTURE, a CUESHEET block with three tracks, and a `CUESHEET` comment that gives the titles. The perl helper rebuilds the metadata blocks: it drops ffmpeg's 8 KB PADDING, adds the CUESHEET block, and fixes the last-block flag.
- `flac/id3.flac`: an ID3v2.4 tag (cut from an ffmpeg MP3, as the ID3v1-only MP3 fixture is) in front of a FLAC whose only comment is `TITLE`.
- `ogg/tags.ogg`: Vorbis, full tags, `CHAPTERnnn` chapters, legacy `COVERART` cover.
- `ogg/tags.opus`: Opus, full tags, chapters, `METADATA_BLOCK_PICTURE` front cover.
- `wav/info.wav`: 8-bit PCM, `LIST/INFO` tags.
- `wav/id3.wav`: as `info.wav`, plus an `id3 ` chunk holding an ID3v2.4 tag with cover and chapters. The RIFF size is fixed up.
- `aac/plain.aac`: three seconds of ADTS.
- `cue/book.cue`: a two-file, Windows-1252 sheet with REM lines, a missing `INDEX 01` and a track without an index.

`commonTest` builders cover what ffmpeg can't: `FlacBuilder` (blocks, CUESHEET, damaged sizes), `OggBuilder` (pages with CRCs, custom lacing so packets span pages, a bad CRC, a multiplexed stream), `WavBuilder` (chunks, odd padding, oversized `data`), ADTS frames for the exact and estimated paths, and cue texts in each encoding.

## Risks / Trade-offs

- [ADTS estimate on VBR files] → a variable-bitrate file whose first 256 KB differ from the rest gets a skewed duration. Raw AAC audiobooks are rare, and the estimate is documented in the spec. M11's player reports the real duration once it plays.
- [`PERFORMER` as the narrator] → a music file tagged with a performer shows it as narrator. That is harmless for audiobooks, and an explicit `NARRATOR` wins.
- [Windows-1252 fallback] → a cue sheet in another code page (Cyrillic, Japanese Shift-JIS) decodes to the wrong letters. Detecting those reliably would need a charset-guessing library. UTF-8 and UTF-16 are always right.
- [Opus files whose first granule isn't 0] → a stream cut from a longer one has a non-zero starting granule. Its duration is overstated by that offset. Doing better would need the first audio page as well; it is rare in audiobooks.
- [ID3 tag before FLAC] → most decoders dislike it, but some taggers write it. We read it only to fill gaps.

## Migration Plan

None: a library addition with no persisted data.

## Open Questions

_None._

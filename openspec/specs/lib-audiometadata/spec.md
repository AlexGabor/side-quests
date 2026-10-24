# lib-audiometadata Specification

## Purpose
App-agnostic reading of audio file metadata (tags, duration, embedded cover, nested chapters) in common Kotlin, through a random-access byte source, with the format told from content rather than file name. Supported formats: MP3 (ID3v1, ID3v2.2–2.4) and MP4 (M4A, M4B, MP4 with `ilst` tags, QuickTime and Nero chapters). Implemented in `lib/audiometadata`.

## Requirements
### Requirement: Random-access byte source
`lib/audiometadata` SHALL read input only through a `ByteSource` that reports its `size` in bytes and returns up to `length` bytes starting at a given position. A read that reaches the end of the source returns fewer bytes. A read at or past the end returns none. Parsers SHALL seek to and read only the regions they need (tag headers and bodies, the first audio frame, the end of the file, atom headers and the atoms they decode), and never the whole file. The module SHALL ship an in-memory `ByteArraySource`.

#### Scenario: Large file, small reads
- **WHEN** an MP3 source of 100 MB, with a 1 KB ID3v2 tag, is read
- **THEN** the total number of bytes requested from the source is under 64 KB

#### Scenario: Large MP4, small reads
- **WHEN** an M4B source of 500 MB holds a 300-chapter QuickTime chapter track and an audio track whose `stsz` table is 4 MB
- **THEN** the audio track's sample tables and `mdat` are not read, and the total number of bytes requested from the source is under 64 KB

#### Scenario: Short read at the end
- **WHEN** a `ByteArraySource` of 10 bytes is asked for 8 bytes at position 6
- **THEN** it returns the 4 bytes from position 6 to 9

### Requirement: Format detection by content
`AudioMetadataReader` SHALL detect the format from the bytes, never from a file name or extension. It SHALL skip a leading ID3v2 tag before looking for a format signature. Signatures are checked in this order:
1. An `ftyp` atom whose major or a compatible brand is one of `M4A `, `M4B `, `M4P `, `F4A `, `F4B `, `mp41`, `mp42`, `isom` or `iso2`–`iso9` means MP4.
2. An MPEG audio frame header (Layer I, II or III) means MP3.
3. A leading ID3v2 tag followed by nothing recognisable also means MP3, so a truncated file still yields its tags.

When no supported format is recognised, `detect` and `read` SHALL return `null`. Formats SHALL be listed in the `AudioFormat` enum; this change supports `Mp3` and `Mp4`. M4A, M4B and MP4 files are all `Mp4`.

#### Scenario: MP3 without ID3
- **WHEN** a source starts with a valid MPEG-1 Layer III frame header
- **THEN** the detected format is `Mp3`

#### Scenario: Unrecognised bytes
- **WHEN** a source starts with `%PDF-1.7` or is empty
- **THEN** `detect` and `read` return `null`

#### Scenario: Not an MPEG frame
- **WHEN** a source starts with a frame sync but a reserved version, layer, bitrate index or sample rate
- **THEN** it is not detected as MP3

#### Scenario: Audiobook brand
- **WHEN** a source starts with an `ftyp` atom whose major brand is `M4B `
- **THEN** the detected format is `Mp4`

#### Scenario: Compatible brand only
- **WHEN** an `ftyp` atom has the major brand `XXXX` and lists `isom` among its compatible brands
- **THEN** the detected format is `Mp4`

#### Scenario: Unknown brand
- **WHEN** an `ftyp` atom lists only the brands `heic` and `mif1`
- **THEN** `detect` returns `null`

### Requirement: Result model
`read` SHALL return an `AudioMetadata` with:
- the detected `format`;
- `tags`: `title`, `album`, `artist`, `albumArtist`, `narrator`, `composer`, `genre`, `year`, `trackNumber`, `trackTotal`, `discNumber`, `discTotal`, each `null` when absent;
- `duration` (`kotlin.time.Duration`), `null` when unknown;
- `cover`: `null`, or the embedded image bytes and MIME type;
- `chapters`: a list of `Chapter(title, start, end, children)`. A chapter with children is a section, and its start and end span its children. The list is empty when the file has no chapters.

Text values SHALL be trimmed. Empty values SHALL be treated as absent.

#### Scenario: File with nothing but audio
- **WHEN** an MP3 without tags is read
- **THEN** every tag is `null`, `cover` is `null`, `chapters` is empty, and `duration` is set

### Requirement: Never throws
`read` and `detect` SHALL NOT throw for any input, including malformed, truncated or hostile data and a source whose reads throw. They SHALL return whatever was parsed before the problem and SHALL NOT log. Declared sizes SHALL be bounded by what the source actually holds before anything is allocated.

#### Scenario: Truncated tag
- **WHEN** an ID3v2 tag declares 1 MB but the source ends after its first two frames
- **THEN** the result holds those two frames' values

#### Scenario: Corrupt frame in the middle
- **WHEN** a frame declares a size past the end of the tag
- **THEN** the frames before it are kept, and parsing of that tag stops there

#### Scenario: Failing source
- **WHEN** the source throws on every read
- **THEN** `read` returns `null` and does not throw

### Requirement: ID3v1
The MP3 parser SHALL read an ID3v1 tag (`TAG` in the last 128 bytes): title, artist, album, year and genre. It SHALL read the track number when the tag is ID3v1.1 (comment byte 28 is zero and byte 29 is not). Fields are ISO-8859-1, or UTF-8 when the bytes are valid UTF-8 (as ffmpeg writes them), with trailing NULs and spaces removed. The genre index SHALL map to its name: the 80 standard genres plus the Winamp extensions. ID3v2 values SHALL take precedence; ID3v1 fills only the fields ID3v2 lacks.

#### Scenario: ID3v1.1 only
- **WHEN** a file has only an ID3v1.1 tag with title `Old`, track 7 and genre 101
- **THEN** title is `Old`, track number is 7, and genre is `Speech`

#### Scenario: Both versions
- **WHEN** ID3v2 has title `New` and ID3v1 has title `Old` and album `Album`
- **THEN** title is `New` and album is `Album`

### Requirement: ID3v2 tag structure
The MP3 parser SHALL read ID3v2.2, 2.3 and 2.4 tags at the start of the file. It SHALL:
- skip an extended header (2.3: size excludes itself; 2.4: syncsafe size includes itself);
- reverse unsynchronisation for the whole tag (2.2, 2.3) or per frame (2.4: frame flag, or the tag flag);
- for 2.4 frames, skip the group byte and data length indicator, and accept frame sizes written as plain integers instead of syncsafe ones;
- skip compressed or encrypted frames;
- stop at padding;
- skip tags with an unknown major version, using the declared size.

Several consecutive ID3v2 tags SHALL be read, the first taking precedence.

#### Scenario: Each version
- **WHEN** tags of version 2.2 (`TT2`), 2.3 and 2.4 (`TIT2`) each hold title `T`
- **THEN** title is `T` for each

#### Scenario: Unsynchronised tag
- **WHEN** a 2.3 tag with the unsynchronisation flag holds an `APIC` whose image contains `FF 00` sequences inserted by unsynchronisation
- **THEN** the cover bytes equal the original image

#### Scenario: Extended header
- **WHEN** a 2.3 or 2.4 tag has an extended header
- **THEN** the frames after it are read

### Requirement: ID3v2 text frames
Text frames SHALL be decoded in each ID3v2 encoding: ISO-8859-1 (0), UTF-16 with BOM (1), UTF-16BE (2) and UTF-8 (3). Several values in one frame (NUL-separated) are joined with `; `. Frames map to the result as follows (2.2 IDs in brackets):

| Field | Frames |
|---|---|
| title | `TIT2` (`TT2`) |
| album | `TALB` (`TAL`) |
| artist | `TPE1` (`TP1`) |
| albumArtist | `TPE2` (`TP2`) |
| composer | `TCOM` (`TCM`) |
| narrator | `TXXX` (`TXX`) with description `narrator` or `narratedby` (case and non-letters ignored) |
| genre | `TCON` (`TCO`); `(n)`, `n` and `(n)Refinement` forms resolve to the ID3v1 genre name or the refinement |
| year | first four digits of `TDRC`, else `TYER` (`TYE`), else `TDRL`, else `TDOR`/`TORY` (`TOR`) |
| track number / total | `TRCK` (`TRK`), as `n` or `n/m` |
| disc number / total | `TPOS` (`TPA`), as `n` or `n/m` |

#### Scenario: Every encoding
- **WHEN** a title frame holds `Ünïcødé ★` in UTF-16 with BOM, UTF-16BE or UTF-8, or `Café` in ISO-8859-1
- **THEN** title decodes to that text

#### Scenario: Track and disc
- **WHEN** `TRCK` is `3/12` and `TPOS` is `1`
- **THEN** track number is 3, track total is 12, disc number is 1, and disc total is `null`

#### Scenario: Numeric genre
- **WHEN** `TCON` is `(101)`
- **THEN** genre is `Speech`

### Requirement: Embedded cover
The parser SHALL read `APIC` (2.3/2.4) and `PIC` (2.2) frames. It SHALL prefer the front cover (picture type 3) and otherwise use the first picture. The MIME type comes from the frame: `image/jpg` is normalised to `image/jpeg`; `PIC`'s `JPG`/`PNG` map to `image/jpeg`/`image/png`. When the frame has no MIME type, it is sniffed from the image's magic bytes. Linked images (`-->`) and empty images SHALL be ignored.

#### Scenario: Front cover wins
- **WHEN** a tag has a back-cover `APIC` followed by a front-cover `APIC`
- **THEN** cover is the front cover

#### Scenario: Version 2.2 picture
- **WHEN** a 2.2 tag has a `PIC` with format `PNG`
- **THEN** cover MIME type is `image/png` and its bytes are the image

### Requirement: ID3v2 chapters
The parser SHALL read `CHAP` frames (element ID, start and end in milliseconds, embedded `TIT2` title) and `CTOC` frames (element ID, top-level and ordered flags, child IDs, embedded `TIT2` title). Chapters are built from the top-level `CTOC`, or else from a `CTOC` that no other `CTOC` references. A child `CTOC` becomes a section whose children are its entries. When a `CTOC` is not ordered, its entries are sorted by start time. Unknown child IDs and reference cycles SHALL be skipped. Without a usable `CTOC`, all `CHAP` frames are listed flat, sorted by start. A chapter whose end is not after its start ends where the next chapter starts, or else at the duration.

#### Scenario: Flat chapters
- **WHEN** a file has a top-level `CTOC` listing `CHAP`s `c1` (0–10 s, `One`) and `c2` (10–20 s, `Two`)
- **THEN** chapters are `One` 0–10 s and `Two` 10–20 s, with no children

#### Scenario: Nested sections
- **WHEN** the top-level `CTOC` lists `CTOC` `p1` (`Part 1`: `c1`, `c2`) and `CTOC` `p2` (`Part 2`: `c3`)
- **THEN** chapters are `Part 1` (spanning `c1`–`c2`, children `c1`, `c2`) and `Part 2` (spanning `c3`, child `c3`)

#### Scenario: Cycle
- **WHEN** `CTOC` `a` lists `b` and `b` lists `a`
- **THEN** reading finishes and does not throw

### Requirement: MP3 duration
The parser SHALL find the first MPEG audio frame after the ID3v2 tags and compute the duration from it:
1. from a Xing or Info header's frame count;
2. else from a VBRI header's frame count;
3. else as a CBR estimate: audio bytes (excluding ID3v2 and ID3v1) × 8 ÷ the first frame's bitrate.

Frame count × samples per frame ÷ sample rate gives the duration for 1 and 2. Duration SHALL be `null` when no frame header is found or the bitrate is free-format.

#### Scenario: Xing (VBR)
- **WHEN** a VBR file's first frame has a `Xing` header with a frame count
- **THEN** duration is frames × samples per frame ÷ sample rate

#### Scenario: Info (CBR)
- **WHEN** a CBR file has an `Info` header
- **THEN** duration comes from its frame count

#### Scenario: VBRI
- **WHEN** the first frame has a `VBRI` header 32 bytes after the frame header
- **THEN** duration comes from its frame count

#### Scenario: No header
- **WHEN** a CBR file has no Xing, Info or VBRI header
- **THEN** duration is estimated from the audio size and bitrate

### Requirement: MP4 atom structure
The MP4 parser SHALL walk the atom tree from the start of the file (after any leading ID3v2 tag), reading only atom headers until it reaches an atom it decodes. It SHALL:
- read 32-bit sizes, and 64-bit sizes when the 32-bit size is 1;
- treat a size of 0 as "extends to the end of the enclosing atom, or of the file";
- skip `mdat`, `free`, `skip` and every atom it does not decode by its header alone, whatever their size;
- descend only into `moov`, `trak`, `mdia`, `minf`, `stbl`, `tref`, `udta`, `meta` and `ilst`, and treat `meta` as a full box (4 bytes of version and flags) unless its payload starts directly with an atom header (`hdlr`);
- stop walking a level at an atom whose size is smaller than its header;
- walk a container whose size runs past its parent or the file as far as the bytes go, and skip a leaf atom whose size runs past them;
- read the sample tables (`stts`, `stsc`, `stsz`, `stco`, `co64`) of the chapter track only, and never those of a sound track.

#### Scenario: `moov` after `mdat`
- **WHEN** `ftyp`, then a 2 GB `mdat` with a 64-bit size, then `moov` follow each other
- **THEN** the tags and duration in `moov` are read without reading `mdat`

#### Scenario: Size 0 at the end
- **WHEN** the last top-level atom is `moov` with size 0
- **THEN** it is read up to the end of the file

#### Scenario: QuickTime-style `meta`
- **WHEN** `udta/meta` has no version and flags before its `hdlr`
- **THEN** its `ilst` is still read

### Requirement: MP4 tags
The MP4 parser SHALL read the items of `moov/udta/meta/ilst`. Each item's value is in its first `data` atom: type 1 is UTF-8 and type 2 is UTF-16BE text; type 0 or 21 is a big-endian integer. Items map to the result as follows:

| Field | Items |
|---|---|
| title | `©nam` |
| album | `©alb` |
| artist | `©ART` |
| albumArtist | `aART` |
| composer | `©wrt` |
| narrator | `©nrt`, else a freeform `----` item whose `name` is `narrator` or `narratedby` (case and non-letters ignored, any `mean`) |
| genre | `©gen`, else `gnre`, whose value n is ID3v1 genre n − 1 |
| year | first four digits of `©day` |
| track number / total | `trkn`: number at bytes 2–3, total at bytes 4–5 of the value |
| disc number / total | `disk`: same layout as `trkn` |

The composer is never used as the narrator; choosing which one names a book's author is left to the importer. A zero number or total is absent.

#### Scenario: Full tags
- **WHEN** `ilst` holds `©nam` `T`, `©alb` `A`, `©ART` `R`, `aART` `B`, `©wrt` `C`, `©nrt` `N`, `©gen` `Audiobook`, `©day` `2021-05-01`, `trkn` 3 of 12 and `disk` 1 of 2
- **THEN** the tags are title `T`, album `A`, artist `R`, album artist `B`, composer `C`, narrator `N`, genre `Audiobook`, year 2021, track 3 of 12 and disc 1 of 2

#### Scenario: Freeform narrator
- **WHEN** `ilst` holds no `©nrt` and a `----` item with mean `com.apple.iTunes`, name `NARRATOR` and value `Jane Doe`
- **THEN** narrator is `Jane Doe`

#### Scenario: Numeric genre
- **WHEN** `ilst` holds `gnre` with value 102 and no `©gen`
- **THEN** genre is `Speech`

#### Scenario: UTF-16 text
- **WHEN** `©nam` has a type-2 `data` atom holding `Ünïcødé ★` in UTF-16BE
- **THEN** title is `Ünïcødé ★`

### Requirement: MP4 cover
The MP4 parser SHALL use the first image of the `covr` item as the cover. Data type 13 is `image/jpeg`, 14 is `image/png` and 27 is `image/bmp`; any other type is sniffed from the image's magic bytes. Empty images SHALL be ignored.

#### Scenario: PNG cover
- **WHEN** `covr` holds one type-14 `data` atom
- **THEN** cover is its bytes with MIME type `image/png`

#### Scenario: Several images
- **WHEN** `covr` holds a JPEG followed by a PNG
- **THEN** cover is the JPEG

### Requirement: MP4 duration
The MP4 parser SHALL take the duration from `mvhd` (duration ÷ timescale). When `mvhd` is missing or its duration is 0 or all ones, it SHALL use the first sound track's `mdhd` (its own timescale), and else that track's `tkhd` duration in the `mvhd` timescale. It SHALL read both version 0 (32-bit times) and version 1 (64-bit times) of each box.

#### Scenario: Version 0 `mvhd`
- **WHEN** `mvhd` version 0 has timescale 1000 and duration 3000
- **THEN** duration is 3 s

#### Scenario: Version 1 `mvhd`
- **WHEN** `mvhd` version 1 has timescale 44100 and a duration of 30 hours (more than 32 bits hold)
- **THEN** duration is 30 hours

#### Scenario: Fallback to `mdhd`
- **WHEN** `mvhd` has duration 0 and the sound track's `mdhd` has timescale 8000 and duration 16000
- **THEN** duration is 2 s

#### Scenario: Fallback to `tkhd`
- **WHEN** neither `mvhd` nor `mdhd` has a duration, `mvhd` has timescale 600, and the sound track's `tkhd` has duration 1200
- **THEN** duration is 2 s

### Requirement: MP4 chapters
The MP4 parser SHALL read chapters from two sources:
- **QuickTime chapter track**: the text track (handler `text` or `sbtl`) referenced by a track's `tref/chap`. Each sample is one chapter. Its start is the sum of the earlier samples' `stts` durations in that track's `mdhd` timescale. Its position comes from `stsc`, `stsz` and `stco` or `co64`. Its title is a 16-bit length followed by that many bytes of UTF-8, or UTF-16 when they start with a byte-order mark. Only the text samples SHALL be read, at most 1 KB of each. A sample that can't be read has no title; a track none of whose samples can be read yields no chapters.
- **Nero `chpl`** in `moov/udta`: version and flags, 4 reserved bytes in version 1, an 8-bit count, then per chapter a 64-bit start in 100 ns units and an 8-bit-length UTF-8 title. Entries are sorted by start.

When both sources yield chapters, the QuickTime chapters SHALL be used. MP4 chapters are flat. Each chapter SHALL end where the next starts; the last ends at the duration, or, when the duration is unknown or not after its start, at its start plus its own sample duration (QuickTime) or its start (Nero). An empty title is absent. At most 10 000 chapters are read.

#### Scenario: QuickTime chapters
- **WHEN** an M4B's sound track has `tref/chap` pointing at a text track with samples `One`, `Two` and `Three`, each lasting 60 s, and the duration is 170 s
- **THEN** chapters are `One` 0–60 s, `Two` 60–120 s and `Three` 120–170 s

#### Scenario: Nero chapters
- **WHEN** a file has only a `chpl` with `One` at 0 and `Two` at 1.5 s, and the duration is 3 s
- **THEN** chapters are `One` 0–1.5 s and `Two` 1.5–3 s

#### Scenario: Both present
- **WHEN** a file has a QuickTime chapter track titled `Quick` and a `chpl` titled `Nero`
- **THEN** chapters come from the QuickTime track

#### Scenario: 64-bit chunk offsets
- **WHEN** the chapter track uses `co64` and several samples per chunk
- **THEN** each chapter title is read from its sample's position

#### Scenario: Unreferenced text track
- **WHEN** a file has a text track that no `tref/chap` points at, and no `chpl`
- **THEN** chapters are empty

### Requirement: MP4 damaged input
For MP4, the "never throws" rules SHALL hold as for MP3: the tags, duration and each chapter source are read in separate guards, so one broken part doesn't lose the others, and an `ilst` item that is cut off is dropped while the items before it are kept.

#### Scenario: Truncated `moov`
- **WHEN** a file ends partway through its `ilst`, after `mvhd` and the first two items
- **THEN** the result has the duration and those two items

#### Scenario: Broken chapter track
- **WHEN** the chapter track's `stco` points past the end of the file and the file also has a `chpl`
- **THEN** chapters come from the `chpl`

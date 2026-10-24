## MODIFIED Requirements

### Requirement: Random-access byte source
`lib/audiometadata` SHALL read input only through a `ByteSource` that reports its `size` in bytes and returns up to `length` bytes starting at a given position. A read that reaches the end of the source returns fewer bytes. A read at or past the end returns none. Parsers SHALL seek to and read only the regions they need, and never the whole file. Those regions are:
- tag headers and bodies;
- the first audio frame, or a bounded run of frames;
- the end of the file;
- atom, chunk and block headers, and the atoms, chunks and blocks they decode;
- the first pages of an Ogg stream.

The module SHALL ship an in-memory `ByteArraySource`.

#### Scenario: Large file, small reads
- **WHEN** an MP3 source of 100 MB, with a 1 KB ID3v2 tag, is read
- **THEN** the total number of bytes requested from the source is under 64 KB

#### Scenario: Large MP4, small reads
- **WHEN** an M4B source of 500 MB holds a 300-chapter QuickTime chapter track and an audio track whose `stsz` table is 4 MB
- **THEN** the audio track's sample tables and `mdat` are not read, and the total number of bytes requested from the source is under 64 KB

#### Scenario: Large Ogg, small reads
- **WHEN** an Opus source of 100 MB has a 2 KB comment header and 4 KB pages
- **THEN** the total number of bytes requested from the source is under 64 KB

#### Scenario: Large FLAC and WAV, small reads
- **WHEN** a FLAC or WAV source of 500 MB has 4 KB of metadata before its audio
- **THEN** the total number of bytes requested from the source is under 64 KB

#### Scenario: Short read at the end
- **WHEN** a `ByteArraySource` of 10 bytes is asked for 8 bytes at position 6
- **THEN** it returns the 4 bytes from position 6 to 9

### Requirement: Format detection by content
`AudioMetadataReader` SHALL detect the format from the bytes, never from a file name or extension. It SHALL skip a leading ID3v2 tag before looking for a format signature. Signatures are checked in this order:
1. An `ftyp` atom whose major or a compatible brand is one of `M4A `, `M4B `, `M4P `, `F4A `, `F4B `, `mp41`, `mp42`, `isom` or `iso2`–`iso9` means MP4.
2. `fLaC` means FLAC.
3. An Ogg page (`OggS`, version 0) with the beginning-of-stream flag means Vorbis when its first packet starts with `\x01vorbis`, and Opus when it starts with `OpusHead`. An Ogg stream of any other codec is not recognised.
4. `RIFF` followed, at offset 8, by `WAVE` means WAV.
5. An MPEG audio frame header (Layer I, II or III) means MP3.
6. An ADTS header means AAC. It has a 12-bit sync, layer `00`, a sample-rate index below 13, and a frame length of at least its header size. When that frame ends before the source does, another ADTS sync SHALL follow it.
7. A leading ID3v2 tag followed by nothing recognisable also means MP3, so a truncated file still yields its tags.

When no supported format is recognised, `detect` and `read` SHALL return `null`. Formats SHALL be listed in the `AudioFormat` enum: `Mp3`, `Mp4`, `Flac`, `Vorbis`, `Opus`, `Wav` and `Aac`. M4A, M4B and MP4 files are all `Mp4`.

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

#### Scenario: Each new format
- **WHEN** sources start with `fLaC`, an Ogg Vorbis page, an Ogg Opus page, `RIFF....WAVE`, or two consecutive ADTS frames
- **THEN** the detected formats are `Flac`, `Vorbis`, `Opus`, `Wav` and `Aac`

#### Scenario: FLAC after ID3v2
- **WHEN** a source starts with an ID3v2 tag followed by `fLaC`
- **THEN** the detected format is `Flac`

#### Scenario: Other Ogg codec
- **WHEN** an Ogg stream's first packet starts with `\x7FFLAC` or `Speex`
- **THEN** `detect` returns `null`

#### Scenario: Lone ADTS sync
- **WHEN** a source starts with a valid-looking ADTS header but the bytes where its frame ends are not another sync
- **THEN** it is not detected as `Aac`

## ADDED Requirements

### Requirement: Supported formats
The module SHALL read the following from each format:

| Format | Tags | Cover | Chapters | Duration |
|---|---|---|---|---|
| MP3 | ID3v2.2–2.4, ID3v1 | `APIC`/`PIC` | `CHAP`/`CTOC`, nested | Xing/Info, VBRI, CBR estimate |
| MP4 (M4A, M4B, MP4) | `ilst` | `covr` | QuickTime chapter track, Nero `chpl` | `mvhd`, `mdhd`, `tkhd` |
| FLAC | Vorbis comments; a leading ID3v2 tag fills gaps | PICTURE, `METADATA_BLOCK_PICTURE` | `CHAPTERnnn` comments, CUESHEET block, `CUESHEET` comment | STREAMINFO |
| Ogg Vorbis | Vorbis comments | `METADATA_BLOCK_PICTURE`, `COVERART` | `CHAPTERnnn` comments | last granule ÷ sample rate |
| Ogg Opus | Vorbis comments | `METADATA_BLOCK_PICTURE`, `COVERART` | `CHAPTERnnn` comments | (last granule − pre-skip) ÷ 48 kHz |
| WAV | `LIST/INFO`; an `id3 ` chunk wins | `id3 ` chunk | `id3 ` chunk | `data` size ÷ byte rate |
| AAC (ADTS) | — | — | — | frame scan, estimated past 256 KB |

A standalone `.cue` sheet SHALL be readable as a chapter source for one audio file (see "Cue sheets").

#### Scenario: Every format yields a result
- **WHEN** one fixture of each format in the table is read
- **THEN** each result has its format and the fields the table lists for it

### Requirement: Vorbis comments
FLAC and Ogg SHALL read Vorbis comments in the same way. The structure is:
- a little-endian 32-bit vendor length and the vendor string;
- a 32-bit count;
- per comment, a 32-bit length and `KEY=value` in UTF-8.

Keys SHALL be compared case-insensitively. A key that appears more than once has its values joined with `; `. Fields map as follows:

| Field | Keys |
|---|---|
| title | `TITLE` |
| album | `ALBUM` |
| artist | `ARTIST` |
| albumArtist | `ALBUMARTIST`, else `ALBUM ARTIST`, else `ALBUM_ARTIST` |
| narrator | a key whose letters spell `NARRATOR` or `NARRATEDBY`, else `PERFORMER` |
| composer | `COMPOSER` |
| genre | `GENRE` |
| year | first four digits of `DATE`, else `YEAR`, else `ORIGINALDATE` |
| track number / total | `TRACKNUMBER` as `n` or `n/m`; the total also from `TRACKTOTAL`, else `TOTALTRACKS` |
| disc number / total | `DISCNUMBER` as `n` or `n/m`; the total also from `DISCTOTAL`, else `TOTALDISCS` |

The composer is never used as the narrator.

#### Scenario: Full tags
- **WHEN** the comments are `TITLE=T`, `ALBUM=A`, `ARTIST=R`, `ALBUMARTIST=B`, `COMPOSER=C`, `NARRATOR=N`, `GENRE=Audiobook`, `DATE=2021-05-01`, `TRACKNUMBER=3/12` and `DISCNUMBER=1`, `DISCTOTAL=2`
- **THEN** the tags are title `T`, album `A`, artist `R`, album artist `B`, composer `C`, narrator `N`, genre `Audiobook`, year 2021, track 3 of 12 and disc 1 of 2

#### Scenario: Performer as narrator
- **WHEN** the comments hold `PERFORMER=Jane Doe` and no narrator key
- **THEN** narrator is `Jane Doe`

#### Scenario: Narrator wins over performer
- **WHEN** the comments hold `performer=P` and `Narrated By=N`
- **THEN** narrator is `N`

#### Scenario: Repeated key
- **WHEN** the comments hold `ARTIST=One` and `ARTIST=Two`
- **THEN** artist is `One; Two`

#### Scenario: Cut-off list
- **WHEN** a comment declares a length past the end of the block
- **THEN** the comments before it are kept

### Requirement: Vorbis comment covers and chapters
A `METADATA_BLOCK_PICTURE` comment SHALL be decoded from base64 as a FLAC picture block, and the front cover (type 3) is preferred. A legacy `COVERART` comment SHALL be decoded from base64 as image bytes, with its MIME type from `COVERARTMIME`, else sniffed from the bytes. Base64 decoding SHALL ignore whitespace and other characters outside the alphabet, and accept missing padding.

`CHAPTERnnn=[HH:]MM:SS[.fff]` comments SHALL become chapters, titled by `CHAPTERnnnNAME` when present. Chapters are sorted by start. A `CHAPTERnnn` whose time doesn't parse is skipped.

Flat chapters from every source in this change end as follows. Each chapter ends where the next one starts. The last ends at the source's own end when one is given (the CUESHEET lead-out); otherwise at the duration when that is after its start; otherwise at its start. Of chapters with the same start, only the first is kept. At most 10 000 chapters are read.

#### Scenario: Picture comment
- **WHEN** an Opus comment header holds a `METADATA_BLOCK_PICTURE` with a back cover and another with a front-cover PNG
- **THEN** cover is the PNG with MIME type `image/png`

#### Scenario: Legacy cover
- **WHEN** a Vorbis comment header holds `COVERART` with base64 JPEG bytes wrapped over several lines and no `COVERARTMIME`
- **THEN** cover is those bytes with MIME type `image/jpeg`

#### Scenario: Chapter comments
- **WHEN** the comments are `CHAPTER001=00:00:00.000`, `CHAPTER001NAME=One`, `CHAPTER002=00:01:30.500`, `CHAPTER002NAME=Two`, and the duration is 3 minutes
- **THEN** chapters are `One` 0–90.5 s and `Two` 90.5–180 s

### Requirement: FLAC
The FLAC parser SHALL start after any leading ID3v2 tags. It SHALL walk the metadata blocks after `fLaC`: each has a 1-byte header (last-block flag, 7-bit type) and a 24-bit big-endian size. The walk stops at the last-block flag, at a block that runs past the source, or after 1024 blocks. Audio frames SHALL NOT be read.
- **STREAMINFO** (type 0): duration is total samples (36 bits) ÷ sample rate (20 bits). Total samples of 0 means the duration is unknown.
- **VORBIS_COMMENT** (type 4): tags, covers and chapters as for Vorbis comments.
- **PICTURE** (type 6): picture type, MIME type, description, four 32-bit image properties, then the image data. The front cover (type 3) is preferred, otherwise the first picture is used. An empty MIME type is sniffed from the bytes, and an empty image is ignored.
- **Leading ID3v2 tag**: read as for MP3, after the FLAC blocks, so it only fills fields the Vorbis comments and PICTURE blocks lack. Its chapters are not used.

#### Scenario: Tags, cover and duration
- **WHEN** a FLAC at 8 kHz has STREAMINFO with 24 000 total samples, a VORBIS_COMMENT with `TITLE=T`, and a back-cover PICTURE followed by a front-cover PICTURE
- **THEN** duration is 3 s, title is `T`, and cover is the front cover

#### Scenario: Unknown total
- **WHEN** STREAMINFO's total samples are 0
- **THEN** duration is `null` and the tags are still read

#### Scenario: Leading ID3v2
- **WHEN** a FLAC starts with an ID3v2 tag holding `TIT2=Old` and `TALB=Album`, and its Vorbis comments hold `TITLE=New`
- **THEN** title is `New` and album is `Album`

### Requirement: FLAC chapters
FLAC chapters SHALL come from the first source that yields any:
1. `CHAPTERnnn` Vorbis comments.
2. The CUESHEET block (type 5). Its layout is:
   - a 128-byte catalog number;
   - 64-bit lead-in samples;
   - a 1-byte CD flag and 258 reserved bytes;
   - an 8-bit track count;
   - per track: a 64-bit offset in samples, an 8-bit number, a 12-byte ISRC, 1 byte of flags, 13 reserved bytes, an 8-bit index count, and per index a 64-bit offset (relative to the track), an 8-bit number and 3 reserved bytes.

   Every track except the lead-out (number 170 for a CD, 255 otherwise) is a chapter. It starts at the track offset plus its index 1 offset, or its first index's offset when it has no index 1, divided by STREAMINFO's sample rate. The lead-out offset is the last chapter's end. Titles come from the track of the same number in a `CUESHEET` Vorbis comment, when there is one; otherwise they are absent.
3. A `CUESHEET` Vorbis comment, parsed as a cue sheet (its first FILE entry).

#### Scenario: CUESHEET block with titles
- **WHEN** a FLAC at 44.1 kHz has a CUESHEET block with tracks 1 (offset 0), 2 (offset 441 000), 3 (offset 882 000) and lead-out 170 (offset 1 323 000), and a `CUESHEET` comment titling tracks 1–3 `A`, `B` and `C`
- **THEN** chapters are `A` 0–10 s, `B` 10–20 s and `C` 20–30 s

#### Scenario: CUESHEET block without titles
- **WHEN** the same CUESHEET block has no `CUESHEET` comment
- **THEN** there are three chapters at the same times, with no titles

#### Scenario: Index 1 offset
- **WHEN** a track at offset 441 000 has index 0 at 0 and index 1 at 44 100
- **THEN** that chapter starts at 11 s

#### Scenario: Chapter comments win
- **WHEN** a FLAC has both `CHAPTERnnn` comments and a CUESHEET block
- **THEN** chapters come from the comments

### Requirement: Ogg Vorbis and Opus
The Ogg parser SHALL read pages (`OggS`, version 0, 27-byte header, lacing table) of the logical stream started by the first beginning-of-stream page, ignoring pages of other serial numbers. It SHALL reassemble packets from lacing values: a value of 255 continues the packet, a smaller one ends it, and a packet may continue on the next pages. It SHALL read only the first two packets:
- **Identification header**:
  - Vorbis: `\x01vorbis`, with the sample rate as a 32-bit little-endian value at offset 12.
  - Opus: `OpusHead`, with the pre-skip as a 16-bit little-endian value at offset 10.
- **Comment header**: Vorbis comments after `\x03vorbis` (Vorbis) or `OpusTags` (Opus). It is at most 32 MB, and when the source ends partway through it, the comments are parsed as far as they go.

Duration SHALL come from the granule position of the stream's last page, found by reading back from the end of the source:
- Windows of 16 KB, then 64 KB, then 256 KB are read from the end.
- In each window, `OggS` candidates are tried from the last one back. The first that is a complete page with a valid CRC, the stream's serial number and a granule position other than −1 gives the granule.
- The pages between the first packets and that page SHALL NOT be read.
- Vorbis duration is granule ÷ sample rate.
- Opus duration is (granule − pre-skip) ÷ 48 000, whatever the input sample rate.
- Duration is `null` when no such page is found.

#### Scenario: Vorbis duration
- **WHEN** a Vorbis stream at 8 kHz ends with a page whose granule position is 24 000
- **THEN** duration is 3 s

#### Scenario: Opus duration
- **WHEN** an Opus stream with a pre-skip of 312 ends with a page whose granule position is 144 312
- **THEN** duration is 3 s

#### Scenario: Comment packet spanning pages
- **WHEN** a comment header with a 100 KB picture spans three pages
- **THEN** its tags and cover are read

#### Scenario: Fake page in audio
- **WHEN** the last 16 KB contain `OggS` bytes inside audio data that don't form a page with a valid CRC, before the real last page
- **THEN** the duration comes from the real last page

#### Scenario: Multiplexed stream
- **WHEN** pages of another serial number are interleaved with the Vorbis stream's, including the last page of the file
- **THEN** tags come from the Vorbis stream and the duration from its own last page

### Requirement: WAV
The WAV parser SHALL walk the RIFF chunks after `RIFF....WAVE`. Each chunk has a 4-byte ID, a 32-bit little-endian size and a payload padded to an even length. The walk stops after 4096 chunks or at a chunk other than `data` that runs past the source. The `data` payload SHALL NOT be read.
- **Duration**: `data` size ÷ the `fmt ` byte rate (32-bit, at offset 8). When the `data` size is `0xFFFFFFFF` or more than the source holds, the bytes left in the source are used instead. Duration is `null` without `fmt ` or `data`, or when the byte rate is 0.
- **`LIST` of type `INFO`**: `INAM` title, `IPRD` album, `IART` artist, `IGNR` genre, `ICRD` year (first four digits), `IPRT`, else `ITRK`, track number (`n` or `n/m`). Values are NUL-terminated, and are UTF-8 when they decode cleanly, otherwise Windows-1252.
- **`id3 ` or `ID3 ` chunk**: an ID3v2 tag, read as for MP3, including its cover and chapters. Its values take precedence over `INFO`.

#### Scenario: PCM duration
- **WHEN** a WAV has `fmt ` with byte rate 16 000 and a `data` chunk of 48 000 bytes
- **THEN** duration is 3 s

#### Scenario: Unfinished `data` size
- **WHEN** the `data` size is `0xFFFFFFFF` and 32 000 bytes follow its header, at byte rate 16 000
- **THEN** duration is 2 s

#### Scenario: INFO tags
- **WHEN** `LIST/INFO` holds `INAM` `Title`, `IART` `Artist`, `IPRD` `Album`, `ICRD` `2019-01-01` and `ITRK` `4`
- **THEN** title, artist, album, year 2019 and track number 4 are read

#### Scenario: ID3 chunk
- **WHEN** a WAV has `INFO` with `INAM` `Info` and an `id3 ` chunk with `TIT2` `Id3`, an `APIC` and two `CHAP`s
- **THEN** title is `Id3`, cover is the `APIC` image, and the chapters are the two `CHAP`s

#### Scenario: Odd-sized chunk
- **WHEN** a chunk with an odd size is followed by one pad byte and then `data`
- **THEN** `data` is found

### Requirement: AAC (ADTS)
For raw AAC, the parser SHALL read only the duration. Starting at the first ADTS frame after any ID3v2 tags, it follows each frame's 13-bit length to the next header. Each frame holds (raw data blocks + 1) × 1024 samples at the header's sample rate. The scan reads 64 KB at a time and stops after 256 KB of frames, at the end of the audio (the source size less a trailing ID3v1 tag), or at a position that is not a valid header.
- When the scan reaches the end of the audio, the duration is the exact sum of the frames.
- Otherwise the duration is an estimate: the scanned frames' duration × (audio bytes from the first frame ÷ scanned bytes). It assumes the rest of the file has the same average bitrate as the part that was scanned.
- Duration is `null` when no frame is found.

#### Scenario: Short file, exact
- **WHEN** an ADTS file at 8 kHz holds 30 frames of one raw block each
- **THEN** duration is 30 × 1024 ÷ 8000 s

#### Scenario: Large file, estimated
- **WHEN** an ADTS source of 10 MB holds identical 400-byte frames at 44.1 kHz
- **THEN** duration is within 1 % of 10 MB ÷ 400 × 1024 ÷ 44 100 s, and under 512 KB are requested from the source

### Requirement: Cue sheets
`CueSheet.parse(bytes)` SHALL parse standalone cue sheet text. `CueSheet.read(source)` SHALL do the same over a `ByteSource`, reading at most its first 1 MB. Neither throws.

Text SHALL be decoded as:
- UTF-8 when it starts with a UTF-8 BOM, which is dropped;
- UTF-16 when it starts with a UTF-16 BOM, or has NULs in every other byte of its first line;
- otherwise UTF-8 when the bytes are valid UTF-8, else Windows-1252.

Lines end at CR, LF or CRLF. Commands SHALL be case-insensitive. Values are quoted (`"…"`, which may contain spaces) or bare words. `REM` and unknown commands are ignored. The parser SHALL read:
- `TITLE` and `PERFORMER`: for the sheet before the first `TRACK`, and for the current track after it;
- `FILE "name" type`: starts a new file entry. Tracks before any `FILE` belong to an entry with no name;
- `TRACK nn type`: starts a track in the current file;
- `INDEX nn mm:ss:ff`: minutes, seconds and frames, at 75 frames per second. Minutes may exceed 99.

A track's start is its `INDEX 01`, else its lowest-numbered index. A track without any index has no start. `parse` SHALL return `null` when the text holds no `TRACK`.

`chapters(audioFileName, duration)` SHALL give the chapters for one audio file:
- The file entry is the one whose name matches `audioFileName`, ignoring case and any directory part, or else matches it without both extensions.
- When the sheet has exactly one file entry, that entry is used whatever its name.
- Each track of the entry that has a start becomes a chapter, titled by the track's `TITLE`.
- Ends follow the flat-chapter rule, with the duration closing the last chapter.
- No matching entry gives an empty list.

#### Scenario: Single file
- **WHEN** a sheet has `FILE "book.wav" WAVE` and tracks `One` at `00:00:00`, `Two` at `01:30:37`, and the audio is `book.flac` of 3 minutes
- **THEN** chapters are `One` 0–90.493 s and `Two` 90.493–180 s

#### Scenario: Several files
- **WHEN** a sheet has `FILE "part1.mp3"` with two tracks and `FILE "part2.mp3"` with one, and chapters are asked for `part2.mp3`
- **THEN** there is one chapter, from `part2.mp3`'s track

#### Scenario: Missing INDEX 01
- **WHEN** a track has only `INDEX 00 00:10:00` and the next track has no index
- **THEN** the first starts at 10 s and the second gives no chapter

#### Scenario: Windows-1252
- **WHEN** a sheet's bytes hold `TITLE "Caf\xE9 \x96 Br\xFCcke"` and are not valid UTF-8
- **THEN** the title is `Café – Brücke`

#### Scenario: UTF-16
- **WHEN** a sheet is UTF-16LE with a BOM and holds `TITLE "Ünïcødé ★"`
- **THEN** the title is `Ünïcødé ★`

#### Scenario: Not a cue sheet
- **WHEN** the bytes are a PNG image or text without `TRACK`
- **THEN** `parse` returns `null`

### Requirement: Damaged input in other formats
For FLAC, Ogg, WAV, AAC and cue sheets, the "never throws" rules SHALL hold as for MP3 and MP4. Tags, cover, chapters and duration are read in separate guards, so one broken part doesn't lose the others. Declared sizes SHALL be bounded by what the source or block actually holds before anything is allocated.

#### Scenario: Truncated FLAC
- **WHEN** a FLAC ends partway through its VORBIS_COMMENT block, after STREAMINFO and two comments
- **THEN** the result has the duration and those two comments' values

#### Scenario: Truncated Ogg
- **WHEN** an Opus file ends partway through its comment header
- **THEN** the comments before the cut are read, and duration is `null`

#### Scenario: Hostile sizes
- **WHEN** a Vorbis comment count, a PICTURE length, a WAV chunk size or an ADTS frame length claims more than the source holds
- **THEN** reading finishes without throwing and keeps what was read before

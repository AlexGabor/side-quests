## Why

MP4 (`.m4b`, `.m4a`, `.mp4`) is the most common audiobook container, and the one where embedded chapters matter most: a single M4B usually holds a whole book. Bookplate (M8) builds books from `lib/audiometadata` results, so the parser added in M4 has to read MP4 tags, duration, cover and chapters before import can handle real libraries.

## What Changes

- `AudioFormat.Mp4`: M4A, M4B and MP4 audio (AAC, ALAC) recognised from the `ftyp` atom and its brands, never from the extension. A leading ID3v2 tag before `ftyp` is skipped, as for MP3.
- An atom walker over the `ByteSource`: 32- and 64-bit sizes, size-0 "to the end" atoms, and `mdat` or any other large atom skipped by its header without reading its contents.
- Tags from `moov/udta/meta/ilst`: title, album, artist, album artist, composer, narrator (`©nrt`, or a freeform `----` atom named narrator), genre (`©gen`, or numeric `gnre`), year (`©day`), track and disc numbers (`trkn`, `disk`), and the `covr` cover (JPEG, PNG).
- Duration from `mvhd`, falling back to the sound track's `mdhd`, then its `tkhd`; version 0 and 1 boxes.
- Chapters from Nero `chpl` and from QuickTime chapter text tracks (`tref/chap`, read through `stts`, `stsc`, `stsz`, `stco`/`co64`, reading only the text samples). QuickTime chapters win when both are present. Each chapter ends where the next starts, the last at the file duration.
- Malformed or truncated MP4 input gives a partial result and never throws, as for MP3.
- Fixtures: `scripts/generate-fixtures.sh` also writes small MP4/M4A/M4B clips (each chapter style, tags, cover, ALAC) and a five-hour M4B with 300 chapters, cut down to its header and a few KB of audio. Cases ffmpeg can't write (64-bit atoms, version 1 boxes, `co64`, freeform narrator, `gnre`, broken atoms) are built byte by byte in `commonTest`.

## Capabilities

### New Capabilities
_None._

### Modified Capabilities
- `lib-audiometadata`: format detection gains MP4; the byte-source requirement gains an MP4 scenario; new requirements for MP4 atom structure, `ilst` tags, duration and chapters.

## Impact

- `lib/audiometadata` only: new `mp4` package, an `Mp4` entry in `AudioFormat`, its `ftyp` check in detection, and the image-type sniffing moved out of `id3` so both parsers share it. No new dependencies.
- `AudioFormat` gains an entry, so an exhaustive `when` over it outside the module would need a new branch. Nothing outside the module uses it yet.
- No changes to Bookplate, Pacer or other `lib/` modules. No Pacer link parameters are affected.

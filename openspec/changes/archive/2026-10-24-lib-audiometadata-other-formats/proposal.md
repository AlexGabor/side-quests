## Why

Audiobooks also come as FLAC, Ogg Vorbis, Opus, WAV and raw AAC, often split into one file per chapter or paired with a `.cue` sheet. `lib/audiometadata` reads only MP3 and MP4 so far. M8's import builds books from these results, so the parser must handle the remaining popular formats and cue sheets first. This completes the parsing work.

## What Changes

- New `AudioFormat` entries `Flac`, `Vorbis`, `Opus`, `Wav` and `Aac`, each detected from content, never from the file name. A leading ID3v2 tag is skipped before detection, as for MP3 and MP4.
- **Vorbis comments**, shared by FLAC and Ogg: one documented field mapping (title, album, artist, album artist, narrator, composer, genre, year, track and disc numbers including `n/total`), `METADATA_BLOCK_PICTURE` and legacy `COVERART` covers, and `CHAPTERxxx` / `CHAPTERxxxNAME` chapters.
- **FLAC**:
  - duration from STREAMINFO;
  - VORBIS_COMMENT tags;
  - PICTURE blocks, preferring the front cover;
  - the CUESHEET block as a chapter source;
  - files that start with an ID3v2 tag are accepted, and that tag fills fields the Vorbis comments lack.
- **Ogg Vorbis and Opus**:
  - the comment header, reassembled when it spans pages;
  - covers and chapters from the comments;
  - duration from the granule position of the last page, found by reading back from the end of the file. Opus subtracts its pre-skip and uses 48 kHz.
- **WAV**: `fmt ` and `data` chunks give the duration. `LIST/INFO` chunks give tags. An embedded `id3 ` chunk is read with the existing ID3v2 parser, including its cover and chapters.
- **Raw AAC (ADTS)**: duration only. A bounded frame scan gives an exact count for small files and an estimate for large ones.
- **`.cue` sheets**: a new public `CueSheet` parser for standalone cue text. It reads TITLE, PERFORMER, FILE, TRACK, INDEX and mm:ss:ff times, and handles UTF-8, UTF-16 and Windows-1252. Its chapters can be taken for one audio file. The FLAC CUESHEET block produces the same chapter model.
- Malformed or truncated input in any new format gives a partial result and never throws.
- Fixtures: `scripts/generate-fixtures.sh` also writes small FLAC, Ogg Vorbis, Opus, WAV and ADTS clips and a sample `.cue`. Cases ffmpeg can't write are patched in with perl or built byte by byte in `commonTest`: the CUESHEET block, the `id3 ` chunk, a comment packet spanning pages, damaged pages and chunks, and the ADTS estimate.

## Capabilities

### New Capabilities
_None._

### Modified Capabilities
- `lib-audiometadata`:
  - the purpose names every supported format;
  - format detection gains FLAC, Ogg Vorbis, Opus, WAV and ADTS;
  - the byte-source requirement gains a large-Ogg scenario;
  - new requirements for Vorbis comments, FLAC, Ogg, WAV, ADTS and cue sheets;
  - a supported-formats summary lists what is read from each format.

## Impact

- Only `lib/audiometadata` changes:
  - new `vorbis`, `flac`, `ogg`, `wav`, `aac` and `cue` packages;
  - new `AudioFormat` entries and their checks in `AudioMetadataReader`;
  - a public `CueSheet` API;
  - a small shared helper that gives flat chapters their end times.
- No new dependencies.
- `AudioFormat` gains entries, so any exhaustive `when` over it outside the module would need new branches. Nothing outside the module uses it yet.
- Bookplate, Pacer and the other `lib/` modules don't change, and no Pacer link parameters are affected.

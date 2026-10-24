#!/usr/bin/env bash
# Regenerates the ffmpeg fixtures in src/jvmTest/resources/fixtures. Each clip is a few seconds of
# silence at a low bitrate, so the whole set stays small enough to commit. Cases ffmpeg can't write
# (ID3v2.2, VBRI, nested CTOC, unsynchronisation, broken frames, …) are built byte by byte in the
# tests instead.
#
# Usage: lib/audiometadata/scripts/generate-fixtures.sh [path/to/ffmpeg]
set -euo pipefail

FFMPEG="${1:-${FFMPEG:-ffmpeg}}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/jvmTest/resources/fixtures/mp3"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"

ff() { "$FFMPEG" -hide_banner -loglevel error -y "$@"; }

# Mono 22.05 kHz silence; MPEG-2 Layer III, 576 samples per frame.
silence() { echo "-f lavfi -t $1 -i anullsrc=r=22050:cl=mono"; }
cbr="-c:a libmp3lame -b:a 32k"

# An 8×8 PNG for the embedded cover. Kept next to the clips so tests can compare bytes.
ff -f lavfi -i color=c=red:s=8x8 -frames:v 1 -bitexact "$OUT/cover.png"

cat > "$WORK/chapters.txt" <<'EOF'
;FFMETADATA1
[CHAPTER]
TIMEBASE=1/1000
START=0
END=1000
title=Opening
[CHAPTER]
TIMEBASE=1/1000
START=1000
END=2000
title=Middle
[CHAPTER]
TIMEBASE=1/1000
START=2000
END=3000
title=Ending
EOF

tags=(
  -metadata title="Bookplate Fixture"
  -metadata album="Fixture Album"
  -metadata artist="Fixture Artist"
  -metadata album_artist="Fixture Author"
  -metadata composer="Fixture Composer"
  -metadata narrator="Fixture Narrator"
  -metadata genre="Audiobook"
  -metadata date="2021"
  -metadata track="3/12"
  -metadata disc="1/2"
)
cover=(-map 1:v -c:v copy -metadata:s:v title="Cover" -metadata:s:v comment="Cover (front)")

# ID3v2.4 (UTF-8 text), full tags, front cover, three flat chapters, CBR with an Info header.
# shellcheck disable=SC2046
ff $(silence 3) -i "$OUT/cover.png" -i "$WORK/chapters.txt" \
  -map 0:a $cbr "${cover[@]}" -map_metadata 2 -map_chapters 2 "${tags[@]}" \
  -id3v2_version 4 -write_id3v1 0 -bitexact "$OUT/tags-v24.mp3"

# ID3v2.3 (non-Latin-1 text is written as UTF-16 with BOM) plus an ID3v1 tag, same cover and chapters.
# shellcheck disable=SC2046
ff $(silence 3) -i "$OUT/cover.png" -i "$WORK/chapters.txt" \
  -map 0:a $cbr "${cover[@]}" -map_metadata 2 -map_chapters 2 "${tags[@]}" \
  -metadata title="Ünïcødé ★ Title" \
  -id3v2_version 3 -write_id3v1 1 -bitexact "$OUT/tags-v23-id3v1.mp3"

# Only an ID3v1.1 tag. ffmpeg writes no ID3v1 when ID3v2 is off, so write both and cut the ID3v2
# tag (10-byte header plus its syncsafe size) off the front.
# shellcheck disable=SC2046
ff $(silence 1) $cbr -map_metadata -1 \
  -metadata title="Old Title" -metadata artist="Old Artist" -metadata album="Old Album" \
  -metadata date="1999" -metadata track="7" -metadata genre="Speech" \
  -id3v2_version 3 -write_id3v1 1 -bitexact "$WORK/id3v1.mp3"
read -r s0 s1 s2 s3 < <(od -An -tu1 -j6 -N4 "$WORK/id3v1.mp3")
tail -c +$(( 10 + (s0 << 21 | s1 << 14 | s2 << 7 | s3) + 1 )) "$WORK/id3v1.mp3" > "$OUT/id3v1-only.mp3"

# VBR with a Xing header, no tags. Silence would be encoded at one bitrate and get an Info header
# instead, so this one is quiet noise with a fixed seed.
ff -f lavfi -t 2 -i "anoisesrc=a=0.05:r=22050:seed=1" -ac 1 -c:a libmp3lame -q:a 9 -map_metadata -1 \
  -id3v2_version 0 -write_id3v1 0 -bitexact "$OUT/vbr-xing.mp3"

# CBR with no Xing/Info header and no tags at all: the duration has to be estimated.
# shellcheck disable=SC2046
ff $(silence 2) $cbr -map_metadata -1 \
  -id3v2_version 0 -write_id3v1 0 -write_xing 0 -bitexact "$OUT/cbr-no-header.mp3"

ls -l "$OUT"

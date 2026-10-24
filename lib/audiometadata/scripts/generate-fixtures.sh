#!/usr/bin/env bash
# Regenerates the ffmpeg fixtures in src/jvmTest/resources/fixtures. Each clip is a few seconds of
# silence at a low bitrate, so the whole set stays small enough to commit. Cases ffmpeg can't write
# (ID3v2.2, VBRI, nested CTOC, unsynchronisation, 64-bit atoms, co64, broken frames, …) are built byte
# by byte in the tests instead. MP4 patches use perl, which macOS ships.
#
# Usage: lib/audiometadata/scripts/generate-fixtures.sh [path/to/ffmpeg]
set -euo pipefail

FFMPEG="${1:-${FFMPEG:-ffmpeg}}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/jvmTest/resources/fixtures/mp3"
OUT4="$ROOT/src/jvmTest/resources/fixtures/mp4"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT" "$OUT4"

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

# --- MP4 ---------------------------------------------------------------------------------------
# Chapter titles are chapter metadata, so -map_metadata must keep the chapters file: without titles ffmpeg
# writes no chapter track. ffmpeg's ipod and mp4 muxers write every chapter twice: as a QuickTime chapter track (a text track the
# sound track points at with tref/chap) and as a Nero chpl atom. The single-style files rename the other
# atom to `free`, which keeps every size and offset valid.

# Mono 8 kHz silence; AAC at 8 kbit/s.
silence4() { echo "-f lavfi -t $1 -i anullsrc=r=8000:cl=mono"; }
aac="-c:a aac -b:a 8k"

# Replaces bytes in place with a same-length perl substitution.
patch() { perl -0777 -pi -e "$1" "$2"; }

# ffmpeg has no metadata key for a narrator atom, so write the grouping (©grp) and rename it to ©nrt.
mp4tags=("${tags[@]}" -metadata grouping="Fixture Narrator")
mp4cover=(-map 1:v -c:v copy -disposition:v attached_pic)

# Full tags, PNG cover, three chapters in both styles. The chpl titles are upper-cased so a test can
# tell which style was used.
# shellcheck disable=SC2046
ff $(silence4 3) -i "$OUT/cover.png" -i "$WORK/chapters.txt" \
  -map 0:a $aac "${mp4cover[@]}" -map_metadata 2 -map_chapters 2 "${mp4tags[@]}" \
  -f ipod -brand "M4B " -bitexact "$OUT4/tags-both.m4b"
patch 's/\xa9grp/\xa9nrt/' "$OUT4/tags-both.m4b"
patch 's/chpl(.*)/my $x = $1; $x =~ s{(Opening|Middle|Ending)}{uc $1}ge; "chpl$x"/se' "$OUT4/tags-both.m4b"

# QuickTime chapters only: chpl becomes free.
# shellcheck disable=SC2046
ff $(silence4 3) -i "$WORK/chapters.txt" -map 0:a $aac -map_chapters 1 -map_metadata 1 \
  -metadata title="QuickTime Chapters" -f ipod -bitexact "$WORK/qt.m4a"
patch 's/chpl/free/' "$WORK/qt.m4a"
cp "$WORK/qt.m4a" "$OUT4/chapters-quicktime.m4a"

# Nero chapters only: tref becomes free, so the text track is still there but nothing points at it.
# shellcheck disable=SC2046
ff $(silence4 3) -i "$WORK/chapters.txt" -map 0:a $aac -map_chapters 1 -map_metadata 1 \
  -metadata title="Nero Chapters" -f ipod -bitexact "$WORK/nero.m4a"
patch 's/tref/free/' "$WORK/nero.m4a"
cp "$WORK/nero.m4a" "$OUT4/chapters-nero.m4a"

# ALAC with a few tags and no chapters.
# shellcheck disable=SC2046
ff $(silence4 1) -c:a alac -map_metadata -1 -metadata title="Lossless" -metadata artist="Fixture Artist" \
  -metadata genre="Speech" -metadata track="1" -f ipod -bitexact "$OUT4/alac.m4a"

# Plain MP4 (brand isom), no tags, no chapters.
# shellcheck disable=SC2046
ff $(silence4 2) $aac -map_metadata -1 -f mp4 -bitexact "$OUT4/plain.mp4"

# Five hours of ALAC with 300 one-minute chapters, moov first. 18001.92 s is a whole number of
# 4096-sample frames, so every frame has the same size and stsz has no table. The file is then cut to
# 16 KB: ftyp, moov, the chapter text samples and the start of an mdat that declares the rest.
{
  echo ';FFMETADATA1'
  for i in $(seq 0 299); do
    printf '[CHAPTER]\nTIMEBASE=1/1000\nSTART=%d\nEND=%d\ntitle=Chapter %d\n' $((i * 60000)) $(((i + 1) * 60000)) $((i + 1))
  done
} > "$WORK/long.txt"
ff -f lavfi -t 18001.92 -i anullsrc=r=8000:cl=mono -i "$WORK/long.txt" -map 0:a -c:a alac -map_chapters 1 \
  -map_metadata 1 -metadata title="Long Book" -movflags +faststart -f ipod -brand "M4B " -bitexact "$WORK/long.m4b"
head -c 16384 "$WORK/long.m4b" > "$OUT4/long-header.m4b"

ls -l "$OUT" "$OUT4"

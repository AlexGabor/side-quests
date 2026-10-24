#!/usr/bin/env bash
# Regenerates the ffmpeg fixtures in src/jvmTest/resources/fixtures. Each clip is a few seconds of
# silence at a low bitrate, so the whole set stays small enough to commit. Cases ffmpeg can't write
# (ID3v2.2, VBRI, nested CTOC, unsynchronisation, 64-bit atoms, co64, Ogg pages spanning packets,
# broken frames, …) are built byte by byte in the tests instead. Binary patches use perl, which macOS ships.
#
# Usage: lib/audiometadata/scripts/generate-fixtures.sh [path/to/ffmpeg]
set -euo pipefail

FFMPEG="${1:-${FFMPEG:-ffmpeg}}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/jvmTest/resources/fixtures/mp3"
OUT4="$ROOT/src/jvmTest/resources/fixtures/mp4"
FIX="$ROOT/src/jvmTest/resources/fixtures"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT" "$OUT4" "$FIX/flac" "$FIX/ogg" "$FIX/wav" "$FIX/aac" "$FIX/cue"

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

# --- FLAC, Ogg, WAV, AAC, cue ------------------------------------------------------------------
# All from 8 kHz mono silence (4 kHz 8-bit for WAV, to keep the PCM small).

# An ID3v2.4 tag on its own: written in front of a short MP3, then cut off at its declared size.
# Usage: id3tag <out> [ffmpeg metadata args…]
id3tag() {
  local out="$1"; shift
  # shellcheck disable=SC2046
  ff $(silence 0.1) -i "$OUT/cover.png" -i "$WORK/chapters.txt" -map 0:a $cbr "${cover[@]}" \
    -map_metadata 2 -map_chapters 2 "$@" -id3v2_version 4 -write_id3v1 0 -bitexact "$WORK/id3tag.mp3"
  read -r s0 s1 s2 s3 < <(od -An -tu1 -j6 -N4 "$WORK/id3tag.mp3")
  head -c $(( 10 + (s0 << 21 | s1 << 14 | s2 << 7 | s3) )) "$WORK/id3tag.mp3" > "$out"
}

# Rewrites a FLAC's metadata blocks: drops PADDING (ffmpeg writes 8 KB of it), optionally adds a
# CUESHEET block after STREAMINFO, and sets the last-block flag on the new last block.
# Usage: flacblocks <file> [cuesheet]
flacblocks() {
  perl -e '
    my ($file, $cue) = @ARGV;
    open my $in, "<:raw", $file or die; local $/; my $d = <$in>; close $in;
    my $p = 4; my @blocks; my $last = 0;
    until ($last) {
      my ($h, $len) = unpack("C a3", substr($d, $p, 4)); $len = unpack("N", "\0$len");
      $last = $h & 0x80; my $type = $h & 0x7F;
      push @blocks, [$type, substr($d, $p + 4, $len)] unless $type == 1;
      $p += 4 + $len;
    }
    if ($cue) {
      # Non-CD sheet at 8 kHz: tracks 1-3 at 0, 1 and 2 s, each with index 1 at 0; lead-out 255 at 3 s.
      my $track = sub { my ($off, $n, $idx) = @_;
        pack("Q> C a12 C a13 C", $off, $n, "", 0, "", $idx ? 1 : 0) . ($idx ? pack("Q> C a3", 0, 1, "") : "") };
      my $b = pack("a128 Q> C a258 C", "", 0, 0, "", 4)
        . $track->(0, 1, 1) . $track->(8000, 2, 1) . $track->(16000, 3, 1) . $track->(24000, 255, 0);
      splice @blocks, 1, 0, [5, $b];
    }
    my $o = "fLaC";
    for my $i (0 .. $#blocks) {
      my ($type, $body) = @{$blocks[$i]};
      $o .= pack("C", $type | ($i == $#blocks ? 0x80 : 0)) . substr(pack("N", length $body), 1) . $body;
    }
    $o .= substr($d, $p);
    open my $out, ">:raw", $file or die; print $out $o;
  ' "$@"
}

# A FLAC picture block (front cover) of a PNG, base64 encoded, as METADATA_BLOCK_PICTURE holds it.
picture64() {
  perl -MMIME::Base64 -e '
    open my $in, "<:raw", $ARGV[0] or die; local $/; my $d = <$in>;
    print encode_base64(pack("N N/a* N/a* N N N N N/a*", 3, "image/png", "", 8, 8, 24, 0, $d), "");
  ' "$1"
}

cuesheet='REM GENRE Audiobook
TITLE "Bookplate Fixture"
FILE "tags.flac" WAVE
  TRACK 01 AUDIO
    TITLE "Opening"
    INDEX 01 00:00:00
  TRACK 02 AUDIO
    TITLE "Middle"
    INDEX 01 00:01:00
  TRACK 03 AUDIO
    TITLE "Ending"
    INDEX 01 00:02:00'
flaccover=(-map 1:v -c:v copy -disposition:v attached_pic -metadata:s:v comment="Cover (front)")

# FLAC: full tags, front-cover PICTURE, a CUESHEET block and a CUESHEET comment that titles it.
# shellcheck disable=SC2046
ff $(silence4 3) -i "$OUT/cover.png" -map 0:a -c:a flac "${flaccover[@]}" -map_metadata -1 "${tags[@]}" \
  -metadata cuesheet="$cuesheet" -bitexact "$FIX/flac/tags.flac"
flacblocks "$FIX/flac/tags.flac" cuesheet

# FLAC behind an ID3v2 tag: the FLAC has only a title; the ID3 tag adds an album, which fills the gap.
id3tag "$WORK/flac-id3.tag" -metadata title="Id3 Title" -metadata album="Id3 Album"
# shellcheck disable=SC2046
ff $(silence4 1) -c:a flac -map_metadata -1 -metadata title="Flac Title" -bitexact "$WORK/plain.flac"
flacblocks "$WORK/plain.flac"
cat "$WORK/flac-id3.tag" "$WORK/plain.flac" > "$FIX/flac/id3.flac"

# Ogg Vorbis: full tags, CHAPTERnnn comments, and the legacy COVERART cover. ffmpeg writes chapter
# comments for Opus but not for Vorbis, so these are plain comments.
# shellcheck disable=SC2046
ff $(silence4 3) -map 0:a -c:a libvorbis -q:a 0 -map_metadata -1 "${tags[@]}" \
  -metadata CHAPTER001=00:00:00.000 -metadata CHAPTER001NAME=Opening -metadata CHAPTER002=00:00:01.000 \
  -metadata CHAPTER002NAME=Middle -metadata CHAPTER003=00:00:02.000 -metadata CHAPTER003NAME=Ending \
  -metadata COVERART="$(base64 < "$OUT/cover.png" | tr -d '\n')" -metadata COVERARTMIME="image/png" \
  -bitexact "$FIX/ogg/tags.ogg"

# Ogg Opus: full tags, chapters, and a METADATA_BLOCK_PICTURE front cover.
# shellcheck disable=SC2046
ff $(silence4 3) -i "$WORK/chapters.txt" -map 0:a -c:a libopus -b:a 6k -map_metadata 1 -map_chapters 1 \
  "${tags[@]}" -metadata METADATA_BLOCK_PICTURE="$(picture64 "$OUT/cover.png")" -bitexact "$FIX/ogg/tags.opus"

# WAV with LIST/INFO tags.
wavtags=(-metadata title="Info Title" -metadata artist="Fixture Artist" -metadata album="Fixture Album"
  -metadata genre="Audiobook" -metadata date="2019-01-01" -metadata track="4")
ff -f lavfi -t 1 -i anullsrc=r=4000:cl=mono -c:a pcm_u8 -map_metadata -1 "${wavtags[@]}" -bitexact "$FIX/wav/info.wav"

# The same plus an `id3 ` chunk (ffmpeg doesn't write one) with title, cover and chapters. The ID3 title
# wins over INAM. The chunk is appended after `data` and the RIFF size fixed up.
id3tag "$WORK/wav-id3.tag" -metadata title="Id3 Title" -metadata album_artist="Fixture Author"
perl -e '
  my ($wav, $tag, $out) = @ARGV; local $/;
  open my $a, "<:raw", $wav or die; my $d = <$a>;
  open my $b, "<:raw", $tag or die; my $t = <$b>;
  $d .= "id3 " . pack("V", length $t) . $t . (length($t) % 2 ? "\0" : "");
  substr($d, 4, 4) = pack("V", length($d) - 8);
  open my $o, ">:raw", $out or die; print $o $d;
' "$FIX/wav/info.wav" "$WORK/wav-id3.tag" "$FIX/wav/id3.wav"

# Raw AAC in ADTS frames, bare and behind an ID3v2 tag.
# shellcheck disable=SC2046
ff $(silence4 3) $aac -map_metadata -1 -f adts -bitexact "$FIX/aac/plain.aac"
id3tag "$WORK/aac-id3.tag" -metadata title="Id3 Title"
cat "$WORK/aac-id3.tag" "$FIX/aac/plain.aac" > "$FIX/aac/id3.aac"

# A Windows-1252 cue sheet for two files, with REM lines, a track that has only INDEX 00 and one that
# has no index at all.
printf '%s\r\n' 'REM GENRE Audiobook' 'REM COMMENT "ExactAudioCopy v1.0"' 'PERFORMER "Fixture Author"' \
  $'TITLE "Caf\xe9 Stories"' 'FILE "Part 1.wav" WAVE' '  TRACK 01 AUDIO' '    TITLE "Opening"' \
  '    INDEX 01 00:00:00' '  TRACK 02 AUDIO' $'    TITLE "Br\xfccke \x96 Middle"' '    PERFORMER "Guest Reader"' \
  '    INDEX 00 00:59:70' '    INDEX 01 01:00:00' 'FILE "Part 2.wav" WAVE' '  TRACK 03 AUDIO' '    TITLE "Ending"' \
  '    INDEX 00 00:00:00' '  TRACK 04 AUDIO' '    TITLE "No Index"' > "$FIX/cue/book.cue"

ls -l "$OUT" "$OUT4" "$FIX/flac" "$FIX/ogg" "$FIX/wav" "$FIX/aac" "$FIX/cue"

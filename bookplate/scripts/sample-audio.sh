#!/usr/bin/env bash
# Generates a small sample library with ffmpeg and puts it where the system pickers can see it.
#
#   bookplate/scripts/sample-audio.sh android   # adb push to /sdcard/Audiobooks on the running emulator
#   bookplate/scripts/sample-audio.sh ios       # copy into "On My iPhone" on the booted simulator
#
# The library (tones, so durations are real and files stay tiny):
#   Sample Book/01 - Opening.mp3        1:05
#   Sample Book/Disc 2/02 - Middle.mp3  2:05
#   Sample Book/notes.txt               not audio, skipped by the app
#   Single.m4b                          0:42
set -euo pipefail

FFMPEG="${FFMPEG:-$(command -v ffmpeg || echo /opt/homebrew/bin/ffmpeg)}"
OUT="${SAMPLE_AUDIO_DIR:-${TMPDIR:-/tmp}/bookplate-sample-audio}"

generate() {
  rm -rf "$OUT" && mkdir -p "$OUT/Sample Book/Disc 2"
  tone() { "$FFMPEG" -loglevel error -y -f lavfi -i "sine=frequency=$1:duration=$2" -ac 1 "${@:3}"; }
  tone 440 65 -b:a 32k -metadata title="Opening" -metadata album="Sample Book" "$OUT/Sample Book/01 - Opening.mp3"
  tone 550 125 -b:a 32k -metadata title="Middle" -metadata album="Sample Book" "$OUT/Sample Book/Disc 2/02 - Middle.mp3"
  echo "Not audio." > "$OUT/Sample Book/notes.txt"
  tone 660 42 -c:a aac -b:a 32k -metadata title="Single" -f mp4 "$OUT/Single.m4b"
}

android() {
  adb shell mkdir -p /sdcard/Audiobooks
  adb push "$OUT/." /sdcard/Audiobooks/ >/dev/null
  # So the files also show under the picker's Audio / Recent roots, not only when browsing storage.
  adb shell 'find /sdcard/Audiobooks -type f' | while read -r f; do
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://${f// /%20}" >/dev/null
  done
  echo "Pushed to /sdcard/Audiobooks"
}

# "On My iPhone" in the Files app is the LocalStorage file provider's app-group container.
ios_files() {
  local device groups
  device="$(xcrun simctl list devices booted | grep -Eo '[0-9A-F-]{36}' | head -1)"
  [ -n "$device" ] || { echo "No booted simulator" >&2; exit 1; }
  groups="$HOME/Library/Developer/CoreSimulator/Devices/$device/data/Containers/Shared/AppGroup"
  for group in "$groups"/*; do
    if plutil -p "$group/.com.apple.mobile_container_manager.metadata.plist" 2>/dev/null \
      | grep -q 'group.com.apple.FileProvider.LocalStorage'; then
      echo "$group/File Provider Storage"
      return
    fi
  done
  echo "Open the Files app on the simulator once, then retry" >&2
  exit 1
}

ios() {
  local files
  files="$(ios_files)"
  mkdir -p "$files/Audiobooks"
  cp -R "$OUT/." "$files/Audiobooks/"
  echo "Copied to On My iPhone/Audiobooks ($files/Audiobooks)"
}

case "${1:-}" in
  android) generate; android ;;
  ios) generate; ios ;;
  ios-path) ios_files ;;
  *) echo "usage: $0 android|ios|ios-path" >&2; exit 2 ;;
esac

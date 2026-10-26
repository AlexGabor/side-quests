#!/usr/bin/env bash
# Runs the relink Maestro flows (bookplate/maestro/relink), which need the book's files changed from the host
# between their steps: Maestro can't move or delete files itself.
#
#   bookplate/scripts/relink-flows.sh android [scenario...]   # the debug app on the running emulator
#   bookplate/scripts/relink-flows.sh ios [scenario...]       # the Debug app on the booted simulator
#
# With no scenario, runs them all. Each starts from a fresh copy of the sample library (scripts/sample-audio.sh)
# and puts it back afterwards. Install the app first (see bookplate/CLAUDE.md). Scenarios:
#
#   moved-folder       the book's folder moved to "Moved Book", relinked there, resumes at 0:40
#                      (iOS: copied, and the original deleted, since a bookmark follows a plain move)
#   renamed-file       "01 - Opening.mp3" renamed to "01 - Intro.mp3" inside the folder, relinked to the same folder
#   partial            the folder moved without "Disc 2/02 - Middle.mp3": cancelled once, then relinked anyway
#   files-come-back    "01 - Opening.mp3" moved out of the folder and back: "Check again" finds it, no relink
#   play-missing       the folder moved away while Player shows the book: "Play" finds it missing, plays nothing
#   stops-playing      the second file deleted while the first plays: Player shows the book missing
#   followed-rename    iOS only: the picked folder renamed in place; its bookmark follows, so the book stays available
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
FLOWS="$HERE/../maestro/relink"
PLATFORM="${1:-}"
shift || true
ALL=(moved-folder renamed-file partial files-come-back play-missing stops-playing)
[ "$PLATFORM" = ios ] && ALL+=(followed-rename)
if [ $# -gt 0 ]; then SCENARIOS=("$@"); else SCENARIOS=("${ALL[@]}"); fi

case "$PLATFORM" in
  android)
    APP_ID=com.alexgabor.bookplate.debug
    DEVICE="${ANDROID_SERIAL:-emulator-5554}"
    ;;
  ios)
    APP_ID=com.alexgabor.bookplate
    DEVICE="$(xcrun simctl list devices booted | grep -Eo '[0-9A-F-]{36}' | head -1)"
    LIBRARY="$("$HERE/sample-audio.sh" ios-path)/Audiobooks"
    ;;
  *) echo "usage: $0 android|ios [scenario...]" >&2; exit 2 ;;
esac

# The sample library's folder, as a path on the device (Android) or the host (iOS).
book() { if [ "$PLATFORM" = android ]; then echo "/sdcard/Audiobooks/$1"; else echo "$LIBRARY/$1"; fi; }

on_device() {
  if [ "$PLATFORM" = android ]; then
    local quoted=""
    for arg in "$@"; do quoted+=" '${arg//\'/\'\\\'\'}'"; done
    adb -s "$DEVICE" shell "$quoted"
  else
    "$@"
  fi
}

reset_library() {
  on_device rm -rf "$(book "Sample Book")" "$(book "Moved Book")" "$(book "01 - Opening.mp3")"
  if [ "$PLATFORM" = android ]; then ANDROID_SERIAL="$DEVICE" "$HERE/sample-audio.sh" android >/dev/null; else "$HERE/sample-audio.sh" ios >/dev/null; fi
}

# Moves the book's folder so its references are missing on this platform.
move_book() {
  if [ "$PLATFORM" = android ]; then
    on_device mv "$(book "Sample Book")" "$(book "Moved Book")"
  else
    # A plain move is followed by the bookmark; a copy under a new name, then the original deleted, is not.
    cp -R "$(book "Sample Book")" "$(book "Moved Book")"
    rm -rf "$(book "Sample Book")"
  fi
}

flow() {
  local file="$1"
  shift
  maestro --device "$DEVICE" test -e APP_ID="$APP_ID" "$@" "$FLOWS/$file"
}

run() {
  case "$1" in
    moved-folder)
      flow 1-setup.yaml
      move_book
      flow 2-relink.yaml -e FOLDER="Moved Book"
      ;;
    renamed-file)
      flow 1-setup.yaml
      on_device mv "$(book "Sample Book")/01 - Opening.mp3" "$(book "Sample Book")/01 - Intro.mp3"
      flow 2-relink.yaml -e FOLDER="Sample Book"
      ;;
    partial)
      flow 1-setup.yaml
      move_book
      on_device rm "$(book "Moved Book")/Disc 2/02 - Middle.mp3"
      flow 2-relink-partial.yaml -e FOLDER="Moved Book"
      ;;
    files-come-back)
      # A file inside the folder, which is addressed by its path on both platforms.
      flow 1-setup.yaml
      on_device mv "$(book "Sample Book")/01 - Opening.mp3" "$(book "01 - Opening.mp3")"
      flow 2-missing.yaml
      on_device mv "$(book "01 - Opening.mp3")" "$(book "Sample Book")/01 - Opening.mp3"
      flow 3-check-again.yaml
      ;;
    play-missing)
      flow 1-setup.yaml
      flow 2-open-player.yaml
      move_book
      flow 3-play-missing.yaml
      ;;
    stops-playing)
      flow 1-setup-playing.yaml
      on_device rm "$(book "Sample Book")/Disc 2/02 - Middle.mp3"
      flow 2-stops-while-playing.yaml
      ;;
    followed-rename)
      [ "$PLATFORM" = ios ] || { echo "followed-rename is iOS only: on Android any rename makes the book missing" >&2; return 2; }
      flow 1-setup.yaml
      mv "$(book "Sample Book")" "$(book "Moved Book")"
      flow 2-still-available.yaml
      ;;
    *) echo "Unknown scenario $1" >&2; return 2 ;;
  esac
}

failed=()
for scenario in "${SCENARIOS[@]}"; do
  echo "== $PLATFORM: $scenario"
  reset_library
  if run "$scenario"; then echo "== $scenario: passed"; else echo "== $scenario: FAILED"; failed+=("$scenario"); fi
done
reset_library

if [ ${#failed[@]} -gt 0 ]; then
  echo "Failed on $PLATFORM: ${failed[*]}"
  exit 1
fi
echo "All relink scenarios passed on $PLATFORM: ${SCENARIOS[*]}"

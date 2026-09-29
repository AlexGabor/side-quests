#!/bin/sh
# Bundles each page into build/ as one file, so the pages also open straight from disk.
set -e
cd "$(dirname "$0")"
mkdir -p build
for page in main snapshot; do
  npx esbuild src/$page.tsx --bundle --format=iife --jsx=automatic --loader:.png=dataurl --outfile=build/$page.js --log-level=warning
done
# Riso's own font, inlined: a font file next to the page won't load from file://.
FONT=$(base64 < ../riso/src/commonMain/composeResources/font/FiraCode_VF.ttf | tr -d '\n')
for page in main snapshot; do
cat > build/$page.html <<HTML
<!doctype html>
<html><head><meta charset="utf-8"><title>Riso React</title>
<style>@font-face{font-family:"Fira Code";src:url(data:font/ttf;base64,$FONT) format("truetype");font-weight:300 700}
body{margin:0}</style></head>
<body><div id="root"></div><script src="$page.js"></script></body></html>
HTML
done
# The Compose reference for the side by side, if it has been rendered.
SNAPSHOT=../risoRecorder/build/snapshots/desktop.png
if [ -f "$SNAPSHOT" ]; then cp "$SNAPSHOT" build/compose.png; fi

# Usage

Run the app on a device, tap `Export all`, then:

```
adb pull /sdcard/Download/stamp
cp -R stamp/drawable-* pacer/androidApp/src/main/res/
cp stamp/AppIcon.appiconset/AppIcon.png pacer/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/
cp stamp/playstore/ic_launcher-playstore.png pacer/androidApp/src/main/
```

Then derive the web icons from the print you just copied:

```
python3 tools/stamp/favicon.py
```

The three `drawable-*` layers are Android's adaptive icon, which a launcher composites itself.
`AppIcon.png` is the same mark printed flat and opaque, which is all an asset catalog takes.
`ic_launcher-playstore.png` is that print again at 512 px, full-bleed, for the Play listing — upload
it as is; Play rounds the corners and adds the shadow itself.

`favicon.py` reads `AppIcon.png` back and writes `favicon.ico` (16, 32 and 48 px) and a 180 px
`apple-touch-icon.png` into the web app's resources. The tab icon is cropped to the mark's own
bounds first, because a browser already pads a favicon and the icon grid's safe area only makes the
stopwatch smaller; the touch icon keeps the safe area, since a home screen treats it as an app icon.

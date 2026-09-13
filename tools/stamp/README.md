# Usage

Run the app on a device, tap `Export all`, then:

```
adb pull /sdcard/Download/stamp
cp -R stamp/drawable-* pacer/androidApp/src/main/res/
cp stamp/AppIcon.appiconset/AppIcon.png pacer/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/
cp stamp/playstore/ic_launcher-playstore.png pacer/androidApp/src/main/
```

The three `drawable-*` layers are Android's adaptive icon, which a launcher composites itself.
`AppIcon.png` is the same mark printed flat and opaque, which is all an asset catalog takes.
`ic_launcher-playstore.png` is that print again at 512 px, full-bleed, for the Play listing — upload
it as is; Play rounds the corners and adds the shadow itself.

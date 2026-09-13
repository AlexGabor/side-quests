package com.alexgabor.stamp.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.alexgabor.stamp.IconExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the prints into `Download/stamp/`, one directory per density bucket, one for the asset
 * catalog and one for the Play listing, so that the pulled tree can be copied straight over an app's
 * resources:
 *
 * ```
 * adb pull /sdcard/Download/stamp
 * cp -R stamp/drawable-* pacer/androidApp/src/main/res/
 * cp stamp/AppIcon.appiconset/AppIcon.png pacer/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/
 * cp stamp/playstore/ic_launcher-playstore.png pacer/androidApp/src/main/
 * ```
 *
 * `MediaStore` rather than a share sheet or the app's own external directory: an app needs no
 * permission to insert its own rows, and what it inserts is visible in Files and reachable over adb
 * without digging into a sandbox.
 */
class MediaStoreIconExporter(private val context: Context) : IconExporter {

    override val destination: String = "$DOWNLOADS/$ROOT"

    override suspend fun write(
        dir: String,
        fileName: String,
        opaque: Boolean,
        image: ImageBitmap,
    ): Unit = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$ROOT/$dir"

        // MediaStore does not overwrite: a second export would otherwise leave the first one in
        // place and land beside it as "ic_launcher_foreground (1).png", which is exactly the file
        // nobody wants to find in a res folder.
        resolver.delete(
            collection,
            "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf("$relativePath/", fileName),
        )

        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            // Holds the row back from other apps until the bytes are actually in it.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending)
            ?: error("MediaStore refused $relativePath/$fileName")

        try {
            val bitmap = image.asAndroidBitmap().software(mutable = opaque).apply {
                // Skia encodes a PNG from the bitmap's alpha type, so declaring the pixels opaque is
                // what makes it write a color type carrying no alpha channel at all — which is what
                // an iOS asset catalog requires of an app icon. Sound only because a print marked
                // opaque is one laid on stock that covers the canvas: nothing is discarded.
                if (opaque) setHasAlpha(false)
            }
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("Could not encode $fileName")
                }
            } ?: error("Could not open $relativePath/$fileName")
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
    }
}

/**
 * A copy that can be read back pixel by pixel, and written to if [mutable].
 *
 * `GraphicsLayer.toImageBitmap` renders through a `HardwareRenderer` on API 28 and up — which is the
 * only reason the print shaders run at all — and hands back a bitmap that lives on the GPU.
 *
 * [mutable] is for the callers that go on to change something about the copy. `setHasAlpha` happens
 * not to be checked against mutability today, which is a thin thing to encode a file format on.
 */
private fun Bitmap.software(mutable: Boolean = false): Bitmap =
    if (config == Bitmap.Config.HARDWARE || (mutable && !isMutable)) {
        copy(Bitmap.Config.ARGB_8888, mutable)!!
    } else {
        this
    }

private const val DOWNLOADS = "Download"
private const val ROOT = "stamp"

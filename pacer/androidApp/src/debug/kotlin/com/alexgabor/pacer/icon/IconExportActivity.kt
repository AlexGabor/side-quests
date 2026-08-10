package com.alexgabor.pacer.icon

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.alexgabor.design.riso.print.bakeRisoPrint
import java.io.File
import androidx.core.graphics.createBitmap

/**
 * Prints the launcher icon and writes it out, so that `res/` can hold the result of a real press run
 * rather than a hand-drawn imitation of one.
 *
 * A launcher icon has to be a static drawable, and [com.alexgabor.design.riso.print.risoPrint] is a
 * `RuntimeShader` that only runs on a GPU — so the icon is baked here, on a device, and the PNGs are
 * pulled into the project. Debug-only: it exists to regenerate the asset when the press or the mark
 * changes, and never ships.
 *
 * ```
 * ./gradlew :pacer:androidApp:installDebug
 * adb shell am start -n com.alexgabor.pacer/.icon.IconExportActivity
 * adb pull /sdcard/Android/data/com.alexgabor.pacer/files/icon/ pacer/androidApp/src/main/res/drawable-xxxhdpi/
 * ```
 */
class IconExportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = File(getExternalFilesDir(null), "icon").apply { mkdirs() }

        // The sheet, unprinted. It is a layer of its own so that the launcher can shift it against
        // the artwork, which is the only reason the mark is not simply printed onto it.
        write(target, "ic_launcher_background.png") {
            bakeRisoPrint(SIDE, SIDE, DENSITY, PacerIconPrint) { }
        }

        // The artwork, on a stock that is not painted. The front colour keeps its RGB — that is the
        // white point the separation works against, so the ink comes off the press the same colour
        // it would on the sheet above — and only loses its alpha, which is all that decides whether
        // the sheet itself is printed.
        write(target, "ic_launcher_foreground.png") {
            val stock = PacerIconPaper.copy(
                colorFront = PacerIconPaper.colorFront.copy(alpha = 0f),
                colorBack = Color.Transparent,
            )
            bakeRisoPrint(SIDE, SIDE, DENSITY, PacerIconPrint.copy(paper = stock)) {
                drawPacerStopwatch()
            }
        }

        // The themed layer never goes near the press: the system tints it from its alpha, and grain
        // would read as holes in the silhouette rather than as ink.
        write(target, "ic_launcher_monochrome.png") {
            drawToBitmap(SIDE, SIDE, DENSITY) { drawPacerSilhouette() }
        }

        Log.i(TAG, "wrote the icon to $target")
        finish()
    }

    private fun write(directory: File, name: String, bake: () -> Bitmap) {
        val file = File(directory, name)
        file.outputStream().use { bake().compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "${file.name}: ${file.length()} bytes")
    }

    private companion object {
        const val TAG = "IconExport"

        /** 108 dp — an adaptive icon layer — at four pixels to the dp. */
        const val SIDE = 432

        /**
         * Six pixels to the dp, not four.
         *
         * Every length in the print is authored in dp, so this is what decides how large the grain
         * and the registration error come out *physically*, not how large the bitmap is. A launcher
         * shows the 72 dp middle of the layer at roughly the icon's own size, so all 108 dp of it
         * land in about 72 dp of screen: 432 px across 72 dp is six. Baking at four would make the
         * whole texture a third too fine once the launcher scales it down.
         */
        const val DENSITY = 6f
    }
}

/** The same drawing, off the press — for a layer that has to stay flat. */
private fun drawToBitmap(width: Int, height: Int, density: Float, content: DrawScope.() -> Unit) =
    createBitmap(width, height).also { bitmap ->
        CanvasDrawScope().draw(
            density = Density(density),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(android.graphics.Canvas(bitmap)),
            size = Size(width.toFloat(), height.toFloat()),
            block = content,
        )
    }

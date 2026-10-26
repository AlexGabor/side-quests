package com.alexgabor.bookplate.core.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.alexgabor.bookplate.core.library.BookInks
import java.io.ByteArrayOutputStream

/**
 * The [PanelArtwork] of a book without a cover, as PNG, for the media session. Made again only when the letter or
 * the inks change.
 */
internal class PanelArtworkPng(private val colors: PanelInkColors) {

    private var key: Pair<String, BookInks>? = null
    private var png: ByteArray? = null

    fun of(info: PanelInfo): ByteArray {
        val wanted = info.artworkLetter to info.inks
        png?.takeIf { key == wanted }?.let { return it }
        return render(info.artworkLetter, colors.of(info.inks)).also {
            key = wanted
            png = it
        }
    }
}

private fun render(letter: String, inks: PanelInks): ByteArray {
    val size = PanelArtwork.SIZE_PX.toFloat()
    val bitmap = Bitmap.createBitmap(PanelArtwork.SIZE_PX, PanelArtwork.SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(inks.paper)

    val inset = size * PanelArtwork.BORDER_INSET
    val box = RectF(inset, inset, size - inset, size - inset)
    val corner = size * PanelArtwork.CORNER_RADIUS
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = inks.accentVariant.withAlpha(PanelArtwork.WASH_ALPHA)
    canvas.drawRoundRect(box, corner, corner, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = size * PanelArtwork.BORDER_WIDTH
    paint.color = inks.accent
    canvas.drawRoundRect(box, corner, corner, paint)

    paint.style = Paint.Style.FILL
    paint.color = inks.content
    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    paint.textSize = size * PanelArtwork.LETTER_SIZE
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(letter, size / 2, size / 2 - (paint.ascent() + paint.descent()) / 2, paint)

    return ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        stream.toByteArray()
    }
}

private fun Int.withAlpha(alpha: Float): Int = ((alpha * 255).toInt() shl 24) or (this and 0xFFFFFF)

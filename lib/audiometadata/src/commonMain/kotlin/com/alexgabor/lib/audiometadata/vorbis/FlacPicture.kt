package com.alexgabor.lib.audiometadata.vorbis

import com.alexgabor.lib.audiometadata.Cover
import com.alexgabor.lib.audiometadata.Images
import com.alexgabor.lib.audiometadata.u32

/**
 * A FLAC picture block, as FLAC's PICTURE and Ogg's `METADATA_BLOCK_PICTURE` hold it: type, MIME,
 * description, four image properties, then the data. All big-endian.
 */
internal class FlacPicture(val cover: Cover, val isFront: Boolean) {

    companion object {
        /** `null` for a linked (`-->`), empty or cut-off picture. */
        fun parse(b: ByteArray): FlacPicture? {
            if (b.size < 8) return null
            val type = b.u32(0)
            val mimeLength = b.u32(4)
            if (mimeLength > b.size - 8) return null
            var p = 8 + mimeLength.toInt()
            val mime = b.decodeToString(8, p).trim().lowercase()
            if (mime == "-->" || p + 4 > b.size) return null
            val descriptionLength = b.u32(p)
            if (descriptionLength > b.size - p - 4) return null
            p += 4 + descriptionLength.toInt() + 16
            if (p + 4 > b.size) return null
            // A declared length past the block keeps what is there.
            val length = minOf(b.u32(p), (b.size - p - 4).toLong()).toInt()
            if (length <= 0) return null
            val image = b.copyOfRange(p + 4, p + 4 + length)
            return FlacPicture(Cover(image, mimeType(mime, image)), isFront = type == FRONT_COVER)
        }

        private fun mimeType(mime: String, image: ByteArray): String = when {
            mime == "image/jpg" -> "image/jpeg"
            '/' in mime -> mime
            else -> Images.sniffMimeType(image)
        }

        private const val FRONT_COVER = 3L
    }
}

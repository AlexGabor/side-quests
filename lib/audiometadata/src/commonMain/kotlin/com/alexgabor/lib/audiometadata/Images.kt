package com.alexgabor.lib.audiometadata

/** Tells an embedded picture's type from its first bytes, for tags that don't say. */
internal object Images {

    fun sniffMimeType(image: ByteArray): String = when {
        image.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        image.startsWith(0x89, 'P'.code, 'N'.code, 'G'.code) -> "image/png"
        image.startsWith('G'.code, 'I'.code, 'F'.code, '8'.code) -> "image/gif"
        image.startsWith('B'.code, 'M'.code) -> "image/bmp"
        image.size >= 12 && image.startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) &&
            image.startsWith("WEBP", at = 8) -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean =
        size >= bytes.size && bytes.indices.all { u8(it) == bytes[it] }
}

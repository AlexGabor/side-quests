package com.alexgabor.bookplate.core.library

/** Telling the files of a picked folder apart by name, before any is opened. */
internal object FileKinds {

    private val audio = setOf("mp3", "m4a", "m4b", "mp4", "aac", "flac", "ogg", "oga", "opus", "wav")
    private val images = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun isAudio(name: String): Boolean = extension(name) in audio

    fun isCueSheet(name: String): Boolean = extension(name) == "cue"

    /** `cover.*` before `folder.*`; `null` for anything else. */
    fun folderImageRank(name: String): Int? {
        if (extension(name) !in images) return null
        return when (stem(name).lowercase()) {
            "cover" -> 0
            "folder" -> 1
            else -> null
        }
    }

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun stem(name: String): String = if ('.' in name) name.substringBeforeLast('.') else name

    /** What a cover in [mimeType] is saved as. */
    fun imageExtension(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        else -> "img"
    }
}

/**
 * What tells two picks of the same book apart from others when their references differ (a new iOS
 * bookmark each pick): the picked name and every audio file's path and size, as a 64-bit FNV-1a hash.
 */
internal object Fingerprint {

    fun of(pickedName: String, audio: List<Pair<String, Long?>>): String {
        var hash = OFFSET_BASIS
        fun add(text: String) {
            for (byte in text.encodeToByteArray()) {
                hash = (hash xor (byte.toLong() and 0xFF)) * PRIME
            }
        }
        add(pickedName)
        audio.forEach { (path, size) -> add("\n$path\t${size ?: -1}") }
        return hash.toULong().toString(16).padStart(16, '0')
    }

    private const val OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val PRIME = 0x100000001b3L
}

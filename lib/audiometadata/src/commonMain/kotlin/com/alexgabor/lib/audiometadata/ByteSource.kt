package com.alexgabor.lib.audiometadata

/**
 * Random-access, read-only bytes of one file. Platforms implement it over whatever handle they have
 * (a SAF file descriptor, an `NSFileHandle`, …), so parsers never need a path or the whole file.
 *
 * Reads are blocking; callers move them off the main thread.
 */
interface ByteSource {
    /** Total length in bytes. */
    val size: Long

    /**
     * Up to [length] bytes starting at [position]. Fewer when the end of the source is reached, none
     * at or past it. May throw on I/O errors; [AudioMetadataReader] treats that as the end of data.
     */
    fun read(position: Long, length: Int): ByteArray
}

/** A [ByteSource] over bytes already in memory. */
class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, length: Int): ByteArray {
        if (position < 0 || length <= 0 || position >= bytes.size) return ByteArray(0)
        val start = position.toInt()
        val end = minOf(bytes.size.toLong(), start.toLong() + length).toInt()
        return bytes.copyOfRange(start, end)
    }
}

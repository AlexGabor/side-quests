package com.alexgabor.lib.audiometadata

import java.io.File
import java.io.RandomAccessFile

/** Reads through the file like a platform source would, rather than loading it whole. */
class FileSource(file: File) : ByteSource, AutoCloseable {
    private val raf = RandomAccessFile(file, "r")
    override val size: Long = raf.length()
    override fun read(position: Long, length: Int): ByteArray {
        if (position >= size) return ByteArray(0)
        val out = ByteArray(minOf(length.toLong(), size - position).toInt())
        raf.seek(position)
        raf.readFully(out)
        return out
    }
    override fun close() = raf.close()
}

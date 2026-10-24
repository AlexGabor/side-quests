package com.alexgabor.lib.audiometadata.mp4

import com.alexgabor.lib.audiometadata.ByteArraySource
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.ascii
import com.alexgabor.lib.audiometadata.u32

/**
 * One atom (box) of an MP4 file: its [type], where it starts, where its [payload] starts and where it
 * [end]s. [truncated] means its declared size ran past its parent or the file, and [end] was cut there.
 */
internal class Atom(val type: String, val start: Long, val payload: Long, val end: Long, val truncated: Boolean) {
    val payloadSize: Long get() = end - payload
}

/** Walks atom headers without reading what they hold, so `mdat` and other large atoms cost one read. */
internal object Atoms {

    /**
     * The atoms between [from] and [to], from their headers alone. Size 1 means a 64-bit size follows,
     * size 0 means "up to [to]". The walk stops at a size smaller than its header, and after an atom
     * that runs past [to] or the end of the source.
     */
    fun children(reader: Reader, from: Long, to: Long): List<Atom> {
        val limit = minOf(to, reader.size)
        val atoms = mutableListOf<Atom>()
        var position = from
        while (position + HEADER <= limit && atoms.size < MAX_PER_LEVEL) {
            val header = reader.bytes(position, LARGE_HEADER)
            if (header.size < HEADER) break
            val type = header.ascii(4, 4)
            var headerSize = HEADER
            val size = when (val small = header.u32(0)) {
                1L -> {
                    if (header.size < LARGE_HEADER) break
                    headerSize = LARGE_HEADER
                    header.u64(8)
                }
                0L -> limit - position
                else -> small
            }
            // A negative 64-bit size is above 2^63 and as broken as one smaller than its header.
            if (size < headerSize || position + headerSize > limit) break
            val declaredEnd = position + size
            val truncated = declaredEnd > limit || declaredEnd < 0
            val end = if (truncated) limit else declaredEnd
            atoms += Atom(type, position, position + headerSize, end, truncated)
            if (truncated) break
            position = declaredEnd
        }
        return atoms
    }

    /** The children of [atom], or none when it was cut off before its payload. */
    fun children(reader: Reader, atom: Atom): List<Atom> = children(reader, atom.payload, atom.end)

    /** Up to [max] bytes of [atom]'s payload, never more than the source holds. */
    fun payload(reader: Reader, atom: Atom, max: Int): ByteArray =
        reader.bytes(atom.payload, minOf(atom.payloadSize, max.toLong()).toInt())

    /** A [Reader] over bytes already read, so in-memory atoms are walked by the same rules. */
    fun inMemory(bytes: ByteArray): Reader = Reader(ByteArraySource(bytes))

    private const val HEADER = 8
    private const val LARGE_HEADER = 16
    private const val MAX_PER_LEVEL = 4096
}

/** The first complete atom of [type]. */
internal fun List<Atom>.leaf(type: String): Atom? = firstOrNull { it.type == type && !it.truncated }

/** The first atom of [type], cut off or not: a container is walked as far as its bytes go. */
internal fun List<Atom>.container(type: String): Atom? = firstOrNull { it.type == type }

/** A big-endian 64-bit value; negative when the top bit is set. */
internal fun ByteArray.u64(i: Int): Long = (u32(i) shl 32) or u32(i + 4)

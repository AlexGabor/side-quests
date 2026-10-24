package com.alexgabor.lib.audiometadata.mp4

import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.ascii
import com.alexgabor.lib.audiometadata.u32
import com.alexgabor.lib.audiometadata.u64
import com.alexgabor.lib.audiometadata.u8

/**
 * What the parser needs from one `trak`. Its sample tables aren't read here, only located in [stbl], so a
 * long audio track's tables are never read at all.
 */
internal class Track(
    val id: Long,
    /** `hdlr` handler type: `soun`, `text`, … */
    val handler: String?,
    val media: TimeHeader?,
    /** `tkhd` duration, in the movie's timescale. */
    val headerDuration: Long?,
    /** Track IDs listed in `tref/chap`. */
    val chapterRefs: List<Long>,
    val stbl: Atom?,
)

/** An `mvhd` or `mdhd`: the timescale and the duration in it, `null` when unknown. */
internal class TimeHeader(val timescale: Long, val duration: Long?)

internal object Mp4Tracks {

    fun read(reader: Reader, trak: Atom): Track {
        val atoms = Atoms.children(reader, trak)
        var id = 0L
        var headerDuration: Long? = null
        atoms.leaf("tkhd")?.let { Atoms.payload(reader, it, BOX_HEADER_MAX) }?.let { b ->
            // Version 1: 64-bit creation and modification times, and duration.
            if (b.isNotEmpty() && b.u8(0) == 1) {
                if (b.size >= 36) { id = b.u32(20); headerDuration = known(b.u64(28), wide = true) }
            } else if (b.size >= 24) {
                id = b.u32(12); headerDuration = known(b.u32(20), wide = false)
            }
        }
        val chapterRefs = atoms.container("tref")
            ?.let { Atoms.children(reader, it).leaf("chap") }
            ?.let { Atoms.payload(reader, it, MAX_REFS * 4) }
            ?.let { b -> List(b.size / 4) { b.u32(it * 4) } }
            .orEmpty()
        val mdia = atoms.container("mdia")?.let { Atoms.children(reader, it) }.orEmpty()
        val media = mdia.leaf("mdhd")?.let { timeHeader(Atoms.payload(reader, it, BOX_HEADER_MAX)) }
        val handler = mdia.leaf("hdlr")?.let { Atoms.payload(reader, it, 12) }?.takeIf { it.size >= 12 }?.ascii(8, 4)
        val stbl = mdia.container("minf")?.let { Atoms.children(reader, it).container("stbl") }
        return Track(id, handler, media, headerDuration, chapterRefs, stbl)
    }

    /** `mvhd` and `mdhd` share this layout up to the duration. */
    fun timeHeader(b: ByteArray): TimeHeader? = when {
        b.isEmpty() -> null
        b.u8(0) == 1 -> if (b.size >= 32) TimeHeader(b.u32(20), known(b.u64(24), wide = true)) else null
        b.size >= 20 -> TimeHeader(b.u32(12), known(b.u32(16), wide = false))
        else -> null
    }

    /** 0 and all ones both mean "unknown". */
    private fun known(value: Long, wide: Boolean): Long? =
        value.takeIf { it > 0 && (wide || it != 0xFFFFFFFFL) }

    const val BOX_HEADER_MAX = 64
    private const val MAX_REFS = 64
}

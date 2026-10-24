package com.alexgabor.lib.audiometadata.mp4

import com.alexgabor.lib.audiometadata.FormatParser
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.ascii
import com.alexgabor.lib.audiometadata.id3.Id3v2
import com.alexgabor.lib.audiometadata.startsWith
import com.alexgabor.lib.audiometadata.u32

/**
 * MP4 (M4A, M4B, MP4): walks atom headers to `moov` wherever it is, then reads tags, duration and
 * chapters, each in its own guard. `mdat` and the audio track's sample tables are never read.
 */
internal object Mp4Parser : FormatParser {

    /** An `ftyp` atom at the start of [probe] with an audio-capable major or compatible brand. */
    fun isMp4(probe: ByteArray): Boolean {
        if (probe.size < 12 || !probe.startsWith("ftyp", 4)) return false
        if (probe.ascii(8, 4) in BRANDS) return true
        // Compatible brands follow the minor version, as far as the atom and the probe reach.
        val end = probe.u32(0).coerceIn(16, probe.size.toLong()).toInt()
        return (16 until end - 3 step 4).any { probe.ascii(it, 4) in BRANDS }
    }

    override fun parse(reader: Reader, into: MetadataBuilder) {
        val start = guarded { Id3v2.skipTags(reader).end } ?: 0L
        val moov = Atoms.children(reader, start, reader.size).container("moov") ?: return
        val atoms = Atoms.children(reader, moov)
        val udta = guarded { atoms.container("udta")?.let { Atoms.children(reader, it) } }.orEmpty()
        guarded { Mp4Tags.read(reader, udta, into) }
        val tracks = atoms.filter { it.type == "trak" }.mapNotNull { guarded { Mp4Tracks.read(reader, it) } }
        guarded { into.duration = Mp4Duration.of(reader, atoms, tracks) }
        guarded { into.chapters = Mp4Chapters.read(reader, tracks, udta, into.duration) }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    private val BRANDS = setOf(
        "M4A ", "M4B ", "M4P ", "F4A ", "F4B ", "mp41", "mp42",
        "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
    )
}

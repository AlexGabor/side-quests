package com.alexgabor.lib.audiometadata.mp3

import com.alexgabor.lib.audiometadata.FormatParser
import com.alexgabor.lib.audiometadata.MetadataBuilder
import com.alexgabor.lib.audiometadata.Reader
import com.alexgabor.lib.audiometadata.id3.Id3Chapters
import com.alexgabor.lib.audiometadata.id3.Id3Frames
import com.alexgabor.lib.audiometadata.id3.Id3v1
import com.alexgabor.lib.audiometadata.id3.Id3v2

/**
 * MP3: ID3v2 tags at the start, an optional ID3v1 tag at the end, and the duration from the first
 * frame. Each step is guarded on its own, so a broken tag doesn't cost the duration, nor the reverse.
 */
internal object Mp3Parser : FormatParser {

    override fun parse(reader: Reader, into: MetadataBuilder) {
        val chapters = Id3Chapters()
        val audioStart = guarded {
            Id3v2.readTags(reader) { major, frames -> Id3Frames.apply(frames, major, into, chapters) }
        } ?: guarded { Id3v2.skipTags(reader).end } ?: 0L
        val hasId3v1 = guarded { Id3v1.isPresent(reader) } == true
        if (hasId3v1) guarded { Id3v1.read(reader, into) }
        val audioEnd = reader.size - if (hasId3v1) Id3v1.SIZE else 0
        guarded { into.duration = Mp3Duration.of(reader, audioStart, audioEnd) }
        guarded { into.chapters = chapters.build(into.duration) }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: Exception) {
        null
    }
}

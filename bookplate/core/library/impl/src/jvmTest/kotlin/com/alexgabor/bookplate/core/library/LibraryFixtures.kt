package com.alexgabor.bookplate.core.library

import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Instant
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

/** Bytes of a `lib/audiometadata` fixture, e.g. `mp3/tags-v24.mp3`. */
internal fun fixture(path: String): ByteArray =
    checkNotNull(LibraryFixtures::class.java.getResourceAsStream("/fixtures/$path")) { "No fixture $path" }
        .use { it.readBytes() }

/**
 * The one-second `mp3/id3v1-only.mp3` with its ID3v1 title, artist and album replaced, so a test can
 * make as many distinct tagged files as it needs. `null` leaves a field empty.
 */
internal fun taggedMp3(title: String?, artist: String? = null, album: String? = null): ByteArray {
    val bytes = fixture("mp3/id3v1-only.mp3")
    val tag = bytes.size - 128
    check(bytes.decodeToString(tag, tag + 3) == "TAG")
    fun field(offset: Int, value: String?) {
        val text = (value ?: "").toByteArray(Charsets.ISO_8859_1) // ID3v1 is Latin-1
        for (i in 0 until 30) bytes[tag + offset + i] = text.getOrElse(i) { 0 }
    }
    field(3, title)
    field(33, artist)
    field(63, album)
    return bytes
}

/** Two seconds of MP3 with no tags at all. */
internal fun untaggedMp3(): ByteArray = fixture("mp3/cbr-no-header.mp3")

internal class FixedClock(var now: Instant = Instant.fromEpochMilliseconds(1_000_000)) : Clock {
    override fun now(): Instant = now
}

internal fun temporaryDirectory(): Path = Files.createTempDirectory("bookplate-covers").toOkioPath()

internal fun deleteDirectory(path: Path) = FileSystem.SYSTEM.deleteRecursively(path)

private object LibraryFixtures

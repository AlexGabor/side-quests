package com.alexgabor.bookplate.core.library

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import okio.FileSystem
import okio.Path

/** Cover images copied into the app's own storage, named so a book row only keeps the file name. */
internal class CoverStore(
    private val fileSystem: FileSystem,
    private val directory: Path,
) {

    /** Writes [bytes] under a new name ending in [extension] and returns that name. */
    @OptIn(ExperimentalUuidApi::class)
    fun write(bytes: ByteArray, extension: String): String {
        fileSystem.createDirectories(directory)
        val name = "${Uuid.random()}.$extension"
        fileSystem.write(directory / name) { write(bytes) }
        return name
    }

    fun delete(name: String) {
        fileSystem.delete(directory / name, mustExist = false)
    }

    fun pathOf(name: String): String = (directory / name).toString()
}

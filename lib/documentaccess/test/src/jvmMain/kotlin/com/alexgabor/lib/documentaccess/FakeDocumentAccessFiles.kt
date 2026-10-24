package com.alexgabor.lib.documentaccess

import java.io.File

/**
 * Copies [directory] and everything below it into the tree at [at], keeping last-modified times, so a
 * test can import fixture folders from disk.
 */
fun FakeDocumentAccess.addDirectory(directory: File, at: String = directory.name) {
    require(directory.isDirectory) { "Not a directory: $directory" }
    addFolder(at)
    directory.walkTopDown().filter { it.isFile }.forEach { file ->
        val relative = file.relativeTo(directory).invariantSeparatorsPath
        addFile("${at.trim('/')}/$relative", file.readBytes(), file.lastModified())
    }
}

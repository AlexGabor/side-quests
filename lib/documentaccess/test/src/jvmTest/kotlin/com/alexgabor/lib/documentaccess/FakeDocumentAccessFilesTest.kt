package com.alexgabor.lib.documentaccess

import com.alexgabor.lib.audiometadata.AudioFormat
import com.alexgabor.lib.audiometadata.AudioMetadataReader
import com.alexgabor.lib.coroutine.TestScopeRule
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FakeDocumentAccessFilesTest {

    private val rule = TestScopeRule()

    // The metadata library's own fixtures, so a parse goes through a listed reference end to end.
    private val fixtures = File("../../audiometadata/src/jvmTest/resources/fixtures/mp3")

    @BeforeTest
    fun setUp() = rule.install()

    @AfterTest
    fun tearDown() = rule.reset()

    @Test
    fun aDirectoryLoadsWithItsFilesAndTimes() = rule.testScope.runTest {
        val access = FakeDocumentAccess()
        access.addDirectory(fixtures, at = "Books/Fixtures")

        val entries = access.list(access.grant("Books/Fixtures"))

        val onDisk = fixtures.listFiles()!!.filter { it.isFile && !it.name.startsWith(".") }.sortedBy { it.name }
        assertEquals(onDisk.map { it.name }, entries.map { it.relativePath })
        assertEquals(onDisk.map { it.length() }, entries.map { it.size })
        assertEquals(onDisk.map { it.lastModified() }, entries.map { it.lastModified })
    }

    @Test
    fun listedAudioParsesThroughTheSource() = rule.testScope.runTest {
        val access = FakeDocumentAccess()
        access.addDirectory(fixtures)
        val entry = access.list(access.grant(fixtures.name)).first { it.name == "vbr-xing.mp3" }

        val metadata = access.read(entry.ref) { AudioMetadataReader.read(it) }

        assertEquals(AudioFormat.Mp3, assertNotNull(metadata).format)
        assertTrue(assertNotNull(metadata.duration).isPositive())
        assertEquals(0, access.openSources)
    }
}

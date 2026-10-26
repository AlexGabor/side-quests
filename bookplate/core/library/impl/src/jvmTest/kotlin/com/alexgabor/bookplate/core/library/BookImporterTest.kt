package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.coroutine.TestScopeRule
import com.alexgabor.lib.documentaccess.FakeDocumentAccess
import com.alexgabor.lib.documentaccess.PickedDocument
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem

class BookImporterTest {

    private val rule = TestScopeRule()
    private val documents = FakeDocumentAccess()
    private val clock = FixedClock()
    private val coversDirectory = temporaryDirectory()
    private val covers = CoverStore(FileSystem.SYSTEM, coversDirectory)
    private lateinit var database: LibraryDatabase
    private lateinit var importer: BookImporterImpl
    private lateinit var repository: LibraryRepositoryImpl

    @BeforeTest
    fun setUp() {
        rule.install()
        // Built after the rule, so Room's queries run on the test IO dispatcher.
        database = inMemoryLibraryDatabase()
        importer = BookImporterImpl(database.dao(), covers, documents, clock, FakeBookInkPicker())
        repository = LibraryRepositoryImpl(database.dao(), covers)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        rule.reset()
        deleteDirectory(coversDirectory)
    }

    @Test
    fun singleM4bWithChapters() = rule.testScope.runTest {
        documents.addFile("Books/Fixture.m4b", fixture("mp4/tags-both.m4b"))

        val details = importDetails(pick("Books/Fixture.m4b"))

        with(details.book) {
            assertEquals("Fixture Album", title)
            assertEquals("Fixture Author", author)
            assertEquals("Fixture Narrator", narrator)
            assertEquals(clock.now, importedAt)
            assertEquals(Duration.ZERO, position)
            assertEquals(1f, speed)
            assertNull(lastPlayedAt)
            // The picker's first pair.
            assertEquals(BookInks(accent = "Teal", accentVariant = "Yellow"), inks)
        }
        assertEquals(listOf("Fixture.m4b"), details.files.map { it.relativePath })
        // QuickTime chapters are preferred over the upper-cased Nero ones.
        assertEquals(listOf("Opening", "Middle", "Ending"), details.chapters.map { it.title })
        assertEquals(listOf(0.seconds, 1.seconds, 2.seconds), details.chapters.map { it.start })
        assertEquals(listOf(1.seconds, 2.seconds, 3.seconds), details.chapters.map { it.end })
        assertTrue(details.chapters.none { it.isSection })
        // The embedded PNG, copied into app storage.
        val cover = assertNotNull(details.book.coverPath)
        assertTrue(cover.startsWith(coversDirectory.toString()) && cover.endsWith(".png"), cover)
        assertContentEquals(fixture("mp3/cover.png"), File(cover).readBytes())
    }

    @Test
    fun multiFileFolderInNaturalOrderWithOneChapterPerFile() = rule.testScope.runTest {
        documents.addFile("Books/Tagged/10.mp3", taggedMp3("Ten", artist = "Old Artist", album = "Old Album"))
        documents.addFile("Books/Tagged/2.mp3", taggedMp3("Two", artist = "Old Artist", album = "Old Album"))
        documents.addFile("Books/Tagged/1.mp3", taggedMp3("One", artist = "Old Artist", album = "Old Album"))
        documents.addFile("Books/Tagged/notes.txt", "not audio".encodeToByteArray())

        val details = importDetails(pick("Books/Tagged"))

        assertEquals("Old Album", details.book.title)
        assertEquals("Old Artist", details.book.author)
        assertEquals(listOf("1.mp3", "2.mp3", "10.mp3"), details.files.map { it.relativePath })
        assertEquals(listOf(0, 1, 2), details.files.map { it.index })
        assertEquals(listOf("One", "Two", "Ten"), details.chapters.map { it.title })
        assertTimeline(details)
        assertEquals(details.files.sumOf { it.duration.inWholeMilliseconds }, details.book.duration.inWholeMilliseconds)
        assertEquals(details.files.map { it.start }, details.chapters.map { it.start })
        assertTrue(details.files.all { it.duration > Duration.ZERO })
        assertNull(details.book.coverPath)
    }

    @Test
    fun embeddedChaptersAreOffsetByTheirFilesStart() = rule.testScope.runTest {
        documents.addFile("Books/Mixed/1.mp3", taggedMp3("Prologue"))
        documents.addFile("Books/Mixed/2.mp3", fixture("mp3/tags-v24.mp3"))

        val details = importDetails(pick("Books/Mixed"))

        val second = details.files[1].start
        assertEquals(listOf("Prologue", "Opening", "Middle", "Ending"), details.chapters.map { it.title })
        assertEquals(listOf(Duration.ZERO, second, second + 1.seconds, second + 2.seconds), details.chapters.map { it.start })
    }

    @Test
    fun subFoldersBecomeSections() = rule.testScope.runTest {
        documents.addFile("Books/Discs/Disc 2/01.mp3", taggedMp3("Three"))
        documents.addFile("Books/Discs/Disc 1/02.mp3", taggedMp3("Two"))
        documents.addFile("Books/Discs/Disc 1/01.mp3", taggedMp3("One"))

        val details = importDetails(pick("Books/Discs"))

        assertEquals(listOf("Disc 1/01.mp3", "Disc 1/02.mp3", "Disc 2/01.mp3"), details.files.map { it.relativePath })
        assertEquals(listOf("Disc 1", "Disc 2"), details.chapters.map { it.title })
        assertTrue(details.chapters.all { it.isSection })
        val (disc1, disc2) = details.chapters
        assertEquals(listOf("One", "Two"), disc1.children.map { it.title })
        assertEquals(listOf("Three"), disc2.children.map { it.title })
        assertEquals(disc1.children.first().start, disc1.start)
        assertEquals(disc1.children.last().end, disc1.end)
        assertEquals(disc1.end, disc2.start)
        assertEquals(listOf("One", "Two", "Three"), details.chapters.playable().map { it.title })
    }

    @Test
    fun foldersSharedByAllFilesAreNotSections() = rule.testScope.runTest {
        documents.addFile("Books/Wrapper/The Book/01.mp3", taggedMp3("One"))
        documents.addFile("Books/Wrapper/The Book/02.mp3", taggedMp3("Two"))

        val details = importDetails(pick("Books/Wrapper"))

        assertEquals(listOf("One", "Two"), details.chapters.map { it.title })
        assertTrue(details.chapters.none { it.isSection })
    }

    @Test
    fun untaggedFolderFallsBackToNames() = rule.testScope.runTest {
        documents.addFile("Books/My Book/b side.mp3", fixture("mp3/vbr-xing.mp3"))
        documents.addFile("Books/My Book/a side.mp3", untaggedMp3())

        val details = importDetails(pick("Books/My Book"))

        assertEquals("My Book", details.book.title)
        assertNull(details.book.author)
        assertNull(details.book.narrator)
        assertEquals(listOf("a side", "b side"), details.chapters.map { it.title })
        assertTimeline(details)
    }

    @Test
    fun untaggedSingleFileFallsBackToItsName() = rule.testScope.runTest {
        documents.addFile("Books/Story.mp3", untaggedMp3())

        val details = importDetails(pick("Books/Story.mp3"))

        assertEquals("Story", details.book.title)
        assertEquals(listOf("Story"), details.chapters.map { it.title })
    }

    @Test
    fun cueSidecarNamedLikeItsAudio() = rule.testScope.runTest {
        documents.addFile("Books/Cue/book.mp3", untaggedMp3())
        documents.addFile("Books/Cue/book.cue", cueSheet(fileName = "book.mp3"))

        val details = importDetails(pick("Books/Cue"))

        assertEquals("Cue Book", details.book.title)
        assertEquals("Cue Reader", details.book.author)
        assertEquals(listOf("First", "Second"), details.chapters.map { it.title })
        assertEquals(listOf(Duration.ZERO, 1.seconds), details.chapters.map { it.start })
        assertEquals(details.book.duration, details.chapters.last().end)
    }

    @Test
    fun cueSidecarNamingItsAudioInAFileEntry() = rule.testScope.runTest {
        documents.addFile("Books/Cue/audio.mp3", untaggedMp3())
        documents.addFile("Books/Cue/other.mp3", untaggedMp3())
        documents.addFile("Books/Cue/sheet.cue", cueSheet(fileName = "audio.wav"))

        val details = importDetails(pick("Books/Cue"))

        // Matched without the extension, as sheets often name the file they were ripped to.
        assertEquals(listOf("First", "Second", "other"), details.chapters.map { it.title })
    }

    @Test
    fun coverFromFolderImage() = rule.testScope.runTest {
        documents.addFile("Books/Covered/01.mp3", taggedMp3("One"))
        documents.addFile("Books/Covered/folder.jpg", fixture("mp3/cover.png"))
        documents.addFile("Books/Covered/other.jpg", byteArrayOf(1, 2, 3))

        val details = importDetails(pick("Books/Covered"))

        val cover = assertNotNull(details.book.coverPath)
        assertTrue(cover.endsWith(".jpg"), cover)
        assertContentEquals(fixture("mp3/cover.png"), File(cover).readBytes())
    }

    @Test
    fun embeddedArtWinsOverFolderImage() = rule.testScope.runTest {
        documents.addFile("Books/Both/01.mp3", fixture("mp3/tags-v24.mp3"))
        documents.addFile("Books/Both/cover.jpg", byteArrayOf(1, 2, 3))

        val details = importDetails(pick("Books/Both"))

        assertContentEquals(fixture("mp3/cover.png"), File(assertNotNull(details.book.coverPath)).readBytes())
    }

    @Test
    fun sameFolderTwiceIsRefused() = rule.testScope.runTest {
        documents.addFile("Books/Tagged/01.mp3", taggedMp3("One"))
        val first = assertIs<ImportResult.Success>(importer.import(pick("Books/Tagged")))
        val before = repository.book(first.bookId).first()
        val coversBefore = FileSystem.SYSTEM.list(coversDirectory.also { FileSystem.SYSTEM.createDirectories(it) })

        clock.now = clock.now.plus(60.seconds)
        assertEquals(ImportResult.AlreadyInLibrary(first.bookId), importer.import(pick("Books/Tagged")))

        assertEquals(1, repository.books().first().size)
        assertEquals(before, repository.book(first.bookId).first())
        assertEquals(coversBefore, FileSystem.SYSTEM.list(coversDirectory))
    }

    @Test
    fun sameBookUnderAnotherReferenceIsRefused() = rule.testScope.runTest {
        documents.addFile("Books/Tagged/01.mp3", taggedMp3("One"))
        documents.addFile("Copy/Tagged/01.mp3", taggedMp3("One"))
        val first = assertIs<ImportResult.Success>(importer.import(pick("Books/Tagged")))

        // Same picked name and files, different reference: how a re-pick looks on iOS.
        assertEquals(ImportResult.AlreadyInLibrary(first.bookId), importer.import(pick("Copy/Tagged")))
        assertEquals(1, repository.books().first().size)
    }

    @Test
    fun folderWithoutAudio() = rule.testScope.runTest {
        documents.addFile("Books/Empty/notes.txt", "notes".encodeToByteArray())
        documents.addFile("Books/Empty/cover.jpg", fixture("mp3/cover.png"))
        // Named like audio, but it isn't.
        documents.addFile("Books/Empty/fake.mp3", "not audio".encodeToByteArray())

        assertEquals(ImportResult.NoAudioFound, importer.import(pick("Books/Empty")))
        assertEquals(emptyList(), repository.books().first())
    }

    @Test
    fun missingSource() = rule.testScope.runTest {
        documents.addFile("Books/Gone/01.mp3", taggedMp3("One"))
        val picked = pick("Books/Gone")
        documents.delete("Books/Gone")

        val result = assertIs<ImportResult.Failed>(importer.import(picked))
        assertEquals(FailureReason.SourceMissing, result.reason)
        assertEquals(emptyList(), repository.books().first())
    }

    @Test
    fun accessLost() = rule.testScope.runTest {
        documents.addFile("Books/Revoked/01.mp3", taggedMp3("One"))
        val picked = pick("Books/Revoked")
        documents.revoke("Books/Revoked")

        assertEquals(FailureReason.AccessLost, assertIs<ImportResult.Failed>(importer.import(picked)).reason)
    }

    @Test
    fun reportsProgress() = rule.testScope.runTest {
        listOf("1", "2", "3").forEach { documents.addFile("Books/Three/$it.mp3", taggedMp3(it)) }
        val progress = mutableListOf<ImportProgress>()

        assertIs<ImportResult.Success>(importer.import(pick("Books/Three")) { progress += it })

        assertEquals(
            listOf(
                ImportProgress.Listing,
                ImportProgress.Reading(0, 3),
                ImportProgress.Reading(1, 3),
                ImportProgress.Reading(2, 3),
                ImportProgress.Reading(3, 3),
                ImportProgress.Saving,
            ),
            progress,
        )
    }

    @Test
    fun onlyReadsTheUsersFiles() = rule.testScope.runTest {
        documents.addFile("Books/Covered/01.mp3", fixture("mp3/tags-v24.mp3"))
        documents.addFile("Books/Covered/book.cue", cueSheet(fileName = "01.mp3"))
        documents.addFile("Books/Covered/folder.jpg", fixture("mp3/cover.png"))
        val picked = pick("Books/Covered")
        val before = documents.list(picked.ref)

        assertIs<ImportResult.Success>(importer.import(picked))

        assertEquals(before, documents.list(picked.ref))
        assertEquals(0, documents.openSources)
    }

    private fun pick(path: String) = PickedDocument(documents.grant(path), path.substringAfterLast('/'))

    private suspend fun importDetails(document: PickedDocument): BookDetails {
        val result = assertIs<ImportResult.Success>(importer.import(document))
        return assertNotNull(repository.book(result.bookId).first())
    }

    /** Each file starts where the one before it ends. */
    private fun assertTimeline(details: BookDetails) {
        details.files.zipWithNext { a, b -> assertEquals(a.start + a.duration, b.start) }
        assertEquals(Duration.ZERO, details.files.first().start)
    }

    private fun cueSheet(fileName: String) = """
        TITLE "Cue Book"
        PERFORMER "Cue Reader"
        FILE "$fileName" MP3
          TRACK 01 AUDIO
            TITLE "First"
            INDEX 01 00:00:00
          TRACK 02 AUDIO
            TITLE "Second"
            INDEX 01 00:01:00
    """.trimIndent().encodeToByteArray()
}

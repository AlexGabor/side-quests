package com.alexgabor.lib.documentaccess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentRefTest {

    private fun assertRoundTrips(ref: DocumentRef) = assertEquals(ref, DocumentRef.parse(ref.asString()))

    @Test
    fun rootReferencesRoundTrip() {
        assertRoundTrips(DocumentRef("content://tree/primary%3ABooks", null, isFolder = true))
        assertRoundTrips(DocumentRef("content://document/audio%3A12", null, isFolder = false))
    }

    @Test
    fun childReferencesRoundTrip() {
        assertRoundTrips(
            DocumentRef(
                root = "content://com.android.externalstorage.documents/tree/primary%3ABooks",
                child = "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2F01.mp3",
                isFolder = false,
            ),
        )
        assertRoundTrips(DocumentRef("Ym9va21hcms=", "Disc 2/02 – Intro.mp3", isFolder = false))
    }

    @Test
    fun separatorsAndEscapesInsidePartsRoundTrip() {
        assertRoundTrips(DocumentRef("a;b%3Bc%25", "x;;y%", isFolder = false))
        assertRoundTrips(DocumentRef("", "", isFolder = true))
    }

    @Test
    fun anEmptyChildIsNotTheSameAsNoChild() {
        val none = DocumentRef("root", null, isFolder = true)
        val empty = DocumentRef("root", "", isFolder = true)

        assertEquals(none, DocumentRef.parse(none.asString()))
        assertEquals(empty, DocumentRef.parse(empty.asString()))
    }

    @Test
    fun theFormIsStable() {
        // Stored strings must keep parsing, so the form is pinned.
        assertEquals("docref1;F;r%3Bx;a%25b", DocumentRef("r;x", "a%b", isFolder = false).asString())
        assertEquals(DocumentRef("r", null, isFolder = true), DocumentRef.parse("docref1;D;r"))
    }

    @Test
    fun otherStringsAreNotReferences() {
        listOf(
            "",
            "hello",
            "docref1",
            "docref1;D",
            "docref2;D;root",
            "docref1;X;root",
            "docref1;F;root;child;extra",
            "docref1;F;bad%2Fescape",
            "docref1;F;truncated%2",
        ).forEach { assertNull(DocumentRef.parse(it), it) }
    }
}

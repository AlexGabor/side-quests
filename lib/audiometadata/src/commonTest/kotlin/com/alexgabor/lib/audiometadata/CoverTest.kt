package com.alexgabor.lib.audiometadata

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverTest {

    private fun cover(tag: ByteArray) = readMp3(tag + Mpeg.frames(2)).cover

    @Test
    fun apicInEachVersion() {
        for (major in listOf(3, 4)) {
            val c = cover(Id3Builder(major).frame("APIC", apicBody("image/png", 3, PNG, "Front")).build())
            assertEquals(Cover(PNG, "image/png"), c, "v2.$major")
        }
    }

    @Test
    fun picIn22() {
        assertEquals(Cover(PNG, "image/png"), cover(Id3Builder(2).frame("PIC", picBody("PNG", 3, PNG)).build()))
        assertEquals(Cover(JPEG, "image/jpeg"), cover(Id3Builder(2).frame("PIC", picBody("JPG", 0, JPEG)).build()))
    }

    @Test
    fun frontCoverWinsOverAnEarlierPicture() {
        val tag = Id3Builder(3)
            .frame("APIC", apicBody("image/jpeg", 4, JPEG))
            .frame("APIC", apicBody("image/png", 3, PNG))
            .frame("APIC", apicBody("image/jpeg", 3, JPEG))
            .build()
        assertEquals(Cover(PNG, "image/png"), cover(tag))
    }

    @Test
    fun firstPictureWithoutAFrontCover() {
        val tag = Id3Builder(3)
            .frame("APIC", apicBody("image/jpeg", 0, JPEG))
            .frame("APIC", apicBody("image/png", 4, PNG))
            .build()
        assertEquals(Cover(JPEG, "image/jpeg"), cover(tag))
    }

    @Test
    fun descriptionInUtf16IsSkippedCorrectly() {
        val tag = Id3Builder(3).frame("APIC", apicBody("image/png", 3, PNG, "Ünï", encoding = 1)).build()
        assertContentEquals(PNG, cover(tag)?.data)
    }

    @Test
    fun mimeTypesAreNormalisedOrSniffed() {
        assertEquals("image/jpeg", cover(Id3Builder(3).frame("APIC", apicBody("image/jpg", 3, JPEG)).build())?.mimeType)
        assertEquals("image/png", cover(Id3Builder(3).frame("APIC", apicBody("PNG", 3, PNG)).build())?.mimeType)
        assertEquals("image/png", cover(Id3Builder(3).frame("APIC", apicBody("", 3, PNG)).build())?.mimeType)
        assertEquals("image/jpeg", cover(Id3Builder(3).frame("APIC", apicBody("", 3, JPEG)).build())?.mimeType)
        assertEquals("image/webp", cover(Id3Builder(3).frame("APIC", apicBody("image/WebP", 3, PNG)).build())?.mimeType)
    }

    @Test
    fun linksAndEmptyImagesAreIgnored() {
        assertNull(cover(Id3Builder(3).frame("APIC", apicBody("-->", 3, latin1("http://x/y.jpg"))).build()))
        assertNull(cover(Id3Builder(3).frame("APIC", apicBody("image/png", 3, ByteArray(0))).build()))
    }
}

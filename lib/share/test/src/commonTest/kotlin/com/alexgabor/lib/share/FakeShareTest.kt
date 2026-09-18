package com.alexgabor.lib.share

import kotlin.test.Test
import kotlin.test.assertEquals

class FakeShareTest {

    @Test
    fun recordsSharesInOrder() {
        val share = FakeShare()

        share.text("a")
        share.text("b")

        assertEquals(listOf("a", "b"), share.shared)
    }
}

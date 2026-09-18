package com.alexgabor.lib.share

/** A [Share] that remembers what was shared through it, for a test to read back. */
class FakeShare(override val isAvailable: Boolean = true) : Share {

    /** Every text shared, in order. */
    val shared: List<String>
        field = mutableListOf<String>()

    override fun text(text: String) {
        shared += text
    }
}

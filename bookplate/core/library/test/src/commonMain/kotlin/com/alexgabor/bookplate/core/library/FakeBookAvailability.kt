package com.alexgabor.bookplate.core.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [BookAvailability] whose answers a test sets with [set]: a check publishes and returns the book's set answer,
 * [Availability.Available] when none is set. Every check is recorded in [checks], and [checkAll] counts in
 * [checkAllCount] and checks every book that has an answer set.
 */
class FakeBookAvailability : BookAvailability {

    private val answers = mutableMapOf<BookId, Availability>()
    private val mutableAvailability = MutableStateFlow<Map<BookId, Availability>>(emptyMap())
    override val availability: StateFlow<Map<BookId, Availability>> = mutableAvailability.asStateFlow()

    /** Every book checked by [check], in order. */
    val checks: List<BookId>
        field = mutableListOf<BookId>()

    var checkAllCount: Int = 0
        private set

    /** What checking [id] answers from now on. With [publish], the answer is also published at once, as a check would. */
    fun set(id: BookId, availability: Availability, publish: Boolean = false) {
        answers[id] = availability
        if (publish) mutableAvailability.update { it + (id to availability) }
    }

    override suspend fun check(id: BookId): Availability {
        checks += id
        val answer = answers[id] ?: Availability.Available
        mutableAvailability.update { it + (id to answer) }
        return answer
    }

    override suspend fun checkAll() {
        checkAllCount++
        answers.keys.toList().forEach { check(it) }
    }
}

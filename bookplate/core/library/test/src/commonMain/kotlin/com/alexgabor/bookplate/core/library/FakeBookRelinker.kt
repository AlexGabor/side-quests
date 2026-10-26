package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.PickedDocument

/** [BookRelinker] that returns queued results, in order, and records each call in [calls]. */
class FakeBookRelinker : BookRelinker {

    data class Call(val id: BookId, val pick: PickedDocument, val confirmMissing: Set<String>)

    private val queue = ArrayDeque<RelinkResult>()

    val calls: List<Call>
        field = mutableListOf<Call>()

    /** The next relink returns [result]; [onRelink] runs first, so a test can change the library as a relink would. */
    fun enqueue(result: RelinkResult, onRelink: () -> Unit = {}) {
        queue.addLast(result)
        actions.addLast(onRelink)
    }

    private val actions = ArrayDeque<() -> Unit>()

    override suspend fun relink(id: BookId, pick: PickedDocument, confirmMissing: Set<String>): RelinkResult {
        calls += Call(id, pick, confirmMissing)
        val result = queue.removeFirstOrNull() ?: error("No relink result queued for ${pick.name}")
        actions.removeFirst().invoke()
        return result
    }
}

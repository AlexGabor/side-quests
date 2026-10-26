package com.alexgabor.bookplate.core.library

import com.alexgabor.lib.documentaccess.DocumentEntry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A book's file as the matcher sees it. [index] is its place in the book. */
internal data class StoredFile(
    val index: Int,
    val relativePath: String,
    val size: Long?,
    val duration: Duration,
)

/** Which rule matched a file. */
internal enum class MatchRule { RelativePath, Name, SizeAndDuration }

/**
 * @property links each matched file's index to the candidate it matched, with the rule that matched it.
 * @property unmatched the files no rule matched, in book order.
 */
internal data class Matching(
    val links: Map<Int, Pair<DocumentEntry, MatchRule>>,
    val unmatched: List<StoredFile>,
)

/**
 * Matches a book's stored files to the files of a new location, in three passes over the files in book order, each
 * on what is still unmatched; a candidate is used once:
 *
 * 1. the same relative path, with a compatible size;
 * 2. the same name (ignoring case), with a compatible size; several are narrowed to those with the same duration,
 *    then to those in a folder named like the stored file's; exactly one must be left;
 * 3. a known, equal size and the same duration; exactly one must be left.
 *
 * Sizes are compatible when equal or when either is unknown; durations are the same when both are known and within
 * [DurationTolerance]. [durationOf] is only asked when a rule needs a candidate's duration, at most once each. Pure
 * apart from that, so it is tested without storage.
 */
internal object RelinkMatcher {

    val DurationTolerance: Duration = 500.milliseconds

    suspend fun match(
        stored: List<StoredFile>,
        candidates: List<DocumentEntry>,
        durationOf: suspend (DocumentEntry) -> Duration?,
    ): Matching {
        val durations = mutableMapOf<DocumentEntry, Duration?>()
        suspend fun duration(entry: DocumentEntry): Duration? =
            if (entry in durations) durations[entry] else durationOf(entry).also { durations[entry] = it }

        val links = mutableMapOf<Int, Pair<DocumentEntry, MatchRule>>()
        val used = mutableSetOf<DocumentEntry>()
        fun free() = candidates.filter { it !in used }
        fun link(file: StoredFile, entry: DocumentEntry, rule: MatchRule) {
            links[file.index] = entry to rule
            used += entry
        }

        for (file in stored) {
            free().firstOrNull { it.relativePath == file.relativePath && compatible(file.size, it.size) }
                ?.let { link(file, it, MatchRule.RelativePath) }
        }

        for (file in stored.filter { it.index !in links }) {
            var found = free().filter { it.name.equals(file.name, ignoreCase = true) && compatible(file.size, it.size) }
            if (found.size > 1) found = found.filter { sameDuration(file.duration, duration(it)) }
            if (found.size > 1) found = found.filter { it.relativePath.parentName().equals(file.relativePath.parentName(), ignoreCase = true) }
            found.singleOrNull()?.let { link(file, it, MatchRule.Name) }
        }

        for (file in stored.filter { it.index !in links }) {
            if (file.size == null || !file.duration.isPositive()) continue
            val found = free().filter { it.size == file.size }.filter { sameDuration(file.duration, duration(it)) }
            found.singleOrNull()?.let { link(file, it, MatchRule.SizeAndDuration) }
        }

        return Matching(links, stored.filter { it.index !in links })
    }

    private fun compatible(a: Long?, b: Long?): Boolean = a == null || b == null || a == b

    private fun sameDuration(stored: Duration, candidate: Duration?): Boolean =
        stored.isPositive() && candidate != null && candidate.isPositive() &&
            (stored - candidate).absoluteValue <= DurationTolerance

    private val StoredFile.name: String get() = relativePath.substringAfterLast('/')

    private fun String.parentName(): String = substringBeforeLast('/', "").substringAfterLast('/')
}

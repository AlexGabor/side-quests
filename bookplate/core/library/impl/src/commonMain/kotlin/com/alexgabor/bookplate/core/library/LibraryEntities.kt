package com.alexgabor.bookplate.core.library

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

// Times are epoch milliseconds and durations milliseconds, so no column converters are needed.

@Entity(
    tableName = "books",
    indices = [Index(value = ["source_ref"], unique = true), Index(value = ["fingerprint"])],
)
internal data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** `DocumentRef.asString()` of the picked file or folder. */
    @ColumnInfo(name = "source_ref") val sourceRef: String,
    /** Hash of the picked name and the audio files' paths and sizes; see [Fingerprint]. */
    val fingerprint: String,
    val title: String,
    val author: String?,
    val narrator: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    /** File name under the covers directory, never an absolute path: iOS moves the app container. */
    @ColumnInfo(name = "cover_file") val coverFile: String?,
    @ColumnInfo(name = "position_ms", defaultValue = "0") val positionMs: Long = 0,
    @ColumnInfo(defaultValue = "1.0") val speed: Float = 1f,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
)

@Entity(
    tableName = "audio_files",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["book_id", "ordinal"], unique = true)],
)
internal data class AudioFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    val ordinal: Int,
    @ColumnInfo(name = "source_ref") val sourceRef: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    val size: Long?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    /** Where the file starts in the book's timeline. */
    @ColumnInfo(name = "start_ms") val startMs: Long,
    /** A confirmed partial relink didn't find it: left out of playback until a relink does. Since version 2. */
    @ColumnInfo(defaultValue = "0") val missing: Boolean = false,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["book_id", "ordinal"]), Index(value = ["parent_id"])],
)
internal data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Long,
    /** The section holding this chapter, `null` at the top level. */
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    /** Pre-order position across the whole book, so siblings sort by it. */
    val ordinal: Int,
    val title: String?,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
)

internal data class BookWithContents(
    @Embedded val book: BookEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["book_id"])
    val files: List<AudioFileEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["book_id"])
    val chapters: List<ChapterEntity>,
)

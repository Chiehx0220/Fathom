package io.github.aedev.flow.sync2.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

/** When this device last wrote a snapshot of each link, and how many batch bytes it merged since. */
@Entity(tableName = "snapshots")
data class SnapshotRow(
    @PrimaryKey val link: String,
    val writtenMs: Long,
    val mergedBytes: Long = 0,
)

@Dao
interface SnapshotDao {
    /** Counts merged or uploaded batch bytes toward the next snapshot; the first call starts the link's age. */
    @Query(
        "INSERT INTO snapshots (link, writtenMs, mergedBytes) VALUES (:link, :nowMs, :bytes) " +
            "ON CONFLICT (link) DO UPDATE SET mergedBytes = mergedBytes + excluded.mergedBytes",
    )
    suspend fun noteMerged(
        link: String,
        bytes: Long,
        nowMs: Long,
    )

    @Query("SELECT * FROM snapshots WHERE link = :link")
    suspend fun forLink(link: String): SnapshotRow?

    /** After this device removed another, its next sync writes a snapshot under the new epoch. */
    @Query("UPDATE snapshots SET writtenMs = 0")
    suspend fun snapshotSoon()

    @Query("UPDATE snapshots SET writtenMs = :nowMs, mergedBytes = 0 WHERE link = :link")
    suspend fun written(
        link: String,
        nowMs: Long,
    )
}

package io.github.aedev.flow.sync2.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Each device's latest ack: its version vector as canonical JSON. */
@Entity(tableName = "acks")
data class AckRow(
    @PrimaryKey val did: String,
    val vv: String,
)

/** A snapshot manifest seen in a log, kept until garbage collection deletes it. */
@Entity(tableName = "manifests", primaryKeys = ["did", "seq"])
data class ManifestRow(
    val did: String,
    val seq: Long,
    val manifest: String,
)

/** A link that batches or manifests name, as JSON (`null` for `profiles`); each needs a snapshot before collection. */
@Entity(tableName = "links")
data class LinkRow(
    @PrimaryKey val link: String,
)

/** How far each collectable log has been deleted. */
@Entity(tableName = "collected")
data class CollectedRow(
    @PrimaryKey val did: String,
    val through: Long,
)

@Dao
interface CollectionDao {
    @Upsert
    suspend fun saveAcks(rows: List<AckRow>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addManifests(rows: List<ManifestRow>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLinks(rows: List<LinkRow>)

    @Query("SELECT * FROM acks")
    suspend fun acks(): List<AckRow>

    @Query("SELECT * FROM manifests")
    suspend fun manifests(): List<ManifestRow>

    @Query("SELECT * FROM links")
    suspend fun links(): List<LinkRow>

    @Query("SELECT * FROM collected")
    suspend fun collected(): List<CollectedRow>

    @Query("DELETE FROM manifests WHERE did = :did AND seq = :seq")
    suspend fun deleteManifest(
        did: String,
        seq: Long,
    )

    @Upsert
    suspend fun setCollected(row: CollectedRow)
}

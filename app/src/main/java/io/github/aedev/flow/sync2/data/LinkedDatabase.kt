package io.github.aedev.flow.sync2.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** The hash of a record as last uploaded or received, so a device sends only what changed. */
@Entity(tableName = "sent", primaryKeys = ["collection", "recordKey"])
data class SentRecord(
    val collection: String,
    val recordKey: String,
    val hash: String,
    val sentAt: Long,
)

@Dao
interface SentDao {
    @Query("SELECT * FROM sent WHERE collection = :collection")
    suspend fun forCollection(collection: String): List<SentRecord>

    @Upsert
    suspend fun upsert(records: List<SentRecord>)
}

/** Linked-devices state, kept apart from the app's database. Only Default syncs. */
@Database(
    entities = [
        SentRecord::class,
        GroupRecord::class,
        RosterRow::class,
        StreamRow::class,
        SnapshotRow::class,
        AckRow::class,
        ManifestRow::class,
        LinkRow::class,
        CollectedRow::class,
        HeldRow::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3, spec = HoldObjects::class)],
)
abstract class LinkedDatabase : RoomDatabase() {
    abstract fun sent(): SentDao

    abstract fun groups(): GroupDao

    abstract fun snapshots(): SnapshotDao

    abstract fun collection(): CollectionDao

    abstract fun held(): HeldDao
}

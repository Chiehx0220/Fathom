package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `serviceId` (NewPipe ServiceList id, 0 = YouTube) so rows record their extractor service.
 *
 * Starts at upstream's version 28 and lands in the fork's reserved 10001+ block (see AppDatabase's
 * FORK VERSIONING POLICY). Idempotent: devices that ran it under an earlier numbering already have
 * the columns, and never ran upstream's AutoMigration(26, 27), so `notes` is ensured too.
 */
class Migration28To10001 : Migration(28, 10001) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureNotesTable()
        db.addColumnIfMissing("videos", "serviceId", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("playlists", "serviceId", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("watch_history", "serviceId", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("downloads", "serviceId", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("home_feed_cache", "serviceId", "INTEGER NOT NULL DEFAULT 0")
    }
}

package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `serviceId` (NewPipe ServiceList id, 0 = YouTube) to record extractor service.
 *
 * From upstream v28 into fork's reserved 10001+ block (AppDatabase FORK VERSIONING POLICY).
 * Idempotent: earlier-numbered runs already have the columns; upstream AutoMigration(26, 27) never ran, so `notes` ensured too.
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

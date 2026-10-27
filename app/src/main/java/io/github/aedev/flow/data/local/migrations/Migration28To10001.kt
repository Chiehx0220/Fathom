package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds `serviceId` (0 = YouTube): upstream v28 → fork's 10001+ block. Idempotent; ensures `notes`. */
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

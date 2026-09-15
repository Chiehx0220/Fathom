package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `serviceId` (org.schabi.newpipe.extractor.ServiceList id, 0 = YouTube) so rows can
 * record which extractor service they came from now that Bilibili is a second option.
 *
 * Numbered 27->28 rather than 26->27 because upstream's own AutoMigration(26, 27) (adds notes)
 * already claims that version transition - this picks up right after it instead of colliding.
 * A device that ran this migration under its old 26->27 number already has these columns, so
 * each ADD COLUMN is guarded rather than assumed safe (see MigrationColumns.kt). Such a device
 * also never ran upstream's AutoMigration(26, 27), which is where the `notes` table normally
 * comes from - ensureNotesTable() covers that gap too.
 */
class Migration27To28 : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureNotesTable()
        db.addColumnIfMissing("videos", "serviceId", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("playlists", "serviceId", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("watch_history", "serviceId", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("downloads", "serviceId", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("home_feed_cache", "serviceId", "INTEGER NOT NULL DEFAULT 0")
    }
}

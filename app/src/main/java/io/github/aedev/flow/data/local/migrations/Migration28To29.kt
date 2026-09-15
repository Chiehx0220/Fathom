package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `serviceId` to the subscription feed cache, missed by Migration27To28 - a Bilibili
 * subscription's uploads now flow through this cache too, once its feed refresh no longer
 * assumes YouTube.
 *
 * A device that ran this migration under its old 27->28 number already has the column, so the
 * ADD COLUMN is guarded rather than assumed safe (see MigrationColumns.kt) - this is exactly the
 * version this fork's own migrations collided with itself over, not a hypothetical. Such a device
 * also never ran upstream's AutoMigration(26, 27), which is where the `notes` table normally
 * comes from - ensureNotesTable() covers that gap too.
 */
class Migration28To29 : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureNotesTable()
        db.addColumnIfMissing("subscription_feed_cache", "serviceId", "INTEGER NOT NULL DEFAULT 0")
    }
}

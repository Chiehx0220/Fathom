package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `serviceId` to the subscription feed cache, so non-YouTube uploads flow through it.
 * Idempotent for the same reasons as [Migration28To10001].
 */
class Migration10001To10002 : Migration(10001, 10002) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.ensureNotesTable()
        db.addColumnIfMissing("subscription_feed_cache", "serviceId", "INTEGER NOT NULL DEFAULT 0")
    }
}

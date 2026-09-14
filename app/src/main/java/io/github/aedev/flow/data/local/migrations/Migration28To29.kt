package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `serviceId` to the subscription feed cache, missed by Migration27To28 - a Bilibili
 * subscription's uploads now flow through this cache too, once its feed refresh no longer
 * assumes YouTube.
 */
class Migration28To29 : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscription_feed_cache ADD COLUMN serviceId INTEGER NOT NULL DEFAULT 0")
    }
}

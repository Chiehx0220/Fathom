package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Carries upstream AutoMigration 32→33 (Room walks forward only): feed-cache exact-upload-time flag.
 * Idempotent, as [Migration10003To10004].
 */
class Migration10004To10005 : Migration(10004, 10005) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("subscription_feed_cache", "timestampIsExact", "INTEGER NOT NULL DEFAULT 0")
    }
}

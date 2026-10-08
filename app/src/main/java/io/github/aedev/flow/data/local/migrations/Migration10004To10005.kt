package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Carries upstream's AutoMigration 32 to 33, which a fork device never runs since Room only walks
 * forward from its own version: the feed cache's flag for whether an upload time is exact.
 * Idempotent like [Migration10003To10004].
 */
class Migration10004To10005 : Migration(10004, 10005) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("subscription_feed_cache", "timestampIsExact", "INTEGER NOT NULL DEFAULT 0")
    }
}

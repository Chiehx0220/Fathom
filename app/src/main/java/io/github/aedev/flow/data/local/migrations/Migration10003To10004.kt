package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Carries upstream AutoMigrations 29-32 (Room walks forward only). Idempotent. */
class Migration10003To10004 : Migration(10003, 10004) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("notes", "title", "TEXT")
        db.addColumnIfMissing("notes", "channelName", "TEXT")
        db.addColumnIfMissing("notes", "channelId", "TEXT")
        db.addColumnIfMissing("notes", "thumbnailUrl", "TEXT")
        db.addColumnIfMissing("notes", "durationSeconds", "INTEGER")
        db.addColumnIfMissing("subscription_feed_cache", "feedChannelId", "TEXT NOT NULL DEFAULT ''")
        db.addColumnIfMissing("subscription_feed_cache", "collaboratorsJson", "TEXT NOT NULL DEFAULT ''")
        db.addColumnIfMissing("notes", "channelAvatarUrl", "TEXT")
        db.addColumnIfMissing("notes", "channelHandle", "TEXT")
        db.addColumnIfMissing("notes", "subscriberCountText", "TEXT")
        db.addColumnIfMissing("notes", "position", "INTEGER")
    }
}

package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `serviceId` (org.schabi.newpipe.extractor.ServiceList id, 0 = YouTube) so rows can
 * record which extractor service they came from now that Bilibili is a second option.
 *
 * Numbered 27->28 rather than 26->27 because upstream's own AutoMigration(26, 27) (adds notes)
 * already claims that version transition - this picks up right after it instead of colliding.
 */
class Migration27To28 : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN serviceId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN serviceId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE watch_history ADD COLUMN serviceId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE downloads ADD COLUMN serviceId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE home_feed_cache ADD COLUMN serviceId INTEGER NOT NULL DEFAULT 0")
    }
}

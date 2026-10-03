package io.github.aedev.flow.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Carries upstream's AutoMigration(28, 29): the playlist `position` column. Idempotent like [Migration28To10001]. */
class Migration10002To10003 : Migration(10002, 10003) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("playlists", "position", "INTEGER NOT NULL DEFAULT 0")
    }
}

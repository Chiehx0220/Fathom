package io.github.aedev.flow.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * [table] already has [column].
 *
 * Earlier-numbered fork migrations (Migration28To10001, Migration10001To10002) may have added it;
 * blind ALTER TABLE → "duplicate column name", database unopenable.
 */
internal fun SupportSQLiteDatabase.hasColumn(
    table: String,
    column: String,
): Boolean =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return@use false
        var found = false
        while (!found && cursor.moveToNext()) {
            found = cursor.getString(nameIndex).equals(column, ignoreCase = true)
        }
        found
    }

/** Adds [column] to [table] via [columnDef] if absent. */
internal fun SupportSQLiteDatabase.addColumnIfMissing(
    table: String,
    column: String,
    columnDef: String,
) {
    if (!hasColumn(table, column)) {
        execSQL("ALTER TABLE $table ADD COLUMN $column $columnDef")
    }
}

/**
 * Creates `notes` if missing.
 *
 * Upstream AutoMigration(26, 27) never runs past fork's old v27 (Room walks forward only).
 * Called first by both fork migrations; no-op if present.
 */
internal fun SupportSQLiteDatabase.ensureNotesTable() {
    execSQL(
        "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `targetId` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `text` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )
}

package io.github.aedev.flow.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * True if [table] already has a column named [column].
 *
 * Devices that ran this fork's migrations under an earlier numbering (see Migration28To10001,
 * Migration10001To10002) may already have the column; a blind ALTER TABLE would fail with
 * "duplicate column name" and leave the database unopenable.
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

/** Adds [column] to [table] via [columnDef] only if it is not already there. */
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
 * Creates the `notes` table if it is missing.
 *
 * Normally created by upstream's AutoMigration(26, 27), which a device already past this fork's
 * old version 27 never runs (Room only walks forward). Both fork migrations call this first;
 * it is a no-op once the table exists.
 */
internal fun SupportSQLiteDatabase.ensureNotesTable() {
    execSQL(
        "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `targetId` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `text` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )
}

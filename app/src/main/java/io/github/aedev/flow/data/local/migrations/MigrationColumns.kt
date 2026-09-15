package io.github.aedev.flow.data.local.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * True if [table] already has a column named [column].
 *
 * A device that ran this fork before its migrations were renumbered to make room for upstream's
 * own use of the same version integers (see Migration27To28, Migration28To29) may already have
 * reached the schema a migration below wants to produce, under a different version number than
 * the one that migration now runs at. Re-running its ALTER TABLE against such a device would
 * fail with "duplicate column name" and brick the database open - checking first makes every
 * ADD COLUMN migration safe to run whether or not it already happened.
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
 * It's normally created by upstream's own AutoMigration(26, 27) - but a device that already
 * reached this fork's old (pre-renumbering) version 27+ jumps straight to Migration27To28 or
 * Migration28To29 instead, since Room only walks forward from a device's current version and
 * never revisits an edge behind it. Both of those call this first so the table exists either way;
 * whichever runs first on a given device creates it, and it's a no-op after that.
 */
internal fun SupportSQLiteDatabase.ensureNotesTable() {
    execSQL(
        "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `targetId` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `text` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )
}

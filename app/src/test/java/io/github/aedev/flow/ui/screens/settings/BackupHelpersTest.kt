package io.github.aedev.flow.ui.screens.settings

import io.github.aedev.flow.data.backup.ExportKind
import io.github.aedev.flow.data.local.LocalDataManager.AutoBackupFrequency
import io.github.aedev.flow.ui.screens.settings.backup.folderDisplayName
import io.github.aedev.flow.ui.screens.settings.backup.periodDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BackupHelpersTest {
    @Test
    fun `schedule periods match the old worker intervals`() {
        assertNull(AutoBackupFrequency.NONE.periodDays())
        assertEquals(1L, AutoBackupFrequency.DAILY.periodDays())
        assertEquals(7L, AutoBackupFrequency.WEEKLY.periodDays())
        assertEquals(30L, AutoBackupFrequency.MONTHLY.periodDays())
    }

    @Test
    fun `folder names drop the storage volume`() {
        assertEquals("Documents/Backups", folderDisplayName("primary:Documents/Backups"))
        assertEquals("Flow", folderDisplayName("1A2B-3C4D:Flow/"))
        assertNull(folderDisplayName("primary:"))
        assertNull(folderDisplayName(null))
    }

    @Test
    fun `export file names keep their historical shapes`() {
        val now = Instant.ofEpochMilli(1_700_000_000_000)
        assertEquals("flow_master_backup_1700000000000.zip", ExportKind.MASTER.fileName(now))
        assertEquals("flow-watch-history.json", ExportKind.WATCH_HISTORY.fileName(now))
    }
}

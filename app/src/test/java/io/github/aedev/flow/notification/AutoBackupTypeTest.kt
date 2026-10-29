package io.github.aedev.flow.notification

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.LocalDataManager.AutoBackupType
import org.junit.Test

class AutoBackupTypeTest {
    @Test
    fun `one profile backs up what was chosen`() {
        AutoBackupType.entries.forEach { assertThat(AutoBackupWorker.effectiveType(it, profileCount = 1)).isEqualTo(it) }
    }

    @Test
    fun `two or more profiles always back up every profile in a master backup`() {
        AutoBackupType.entries.forEach { assertThat(AutoBackupWorker.effectiveType(it, profileCount = 2)).isEqualTo(AutoBackupType.MASTER) }
    }
}

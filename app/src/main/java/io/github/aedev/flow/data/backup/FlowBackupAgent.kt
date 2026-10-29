package io.github.aedev.flow.data.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.profile.ProfileFolders
import kotlinx.coroutines.runBlocking

/**
 * Auto Backup with extra steps after a restore, before the app first runs: install-only files an older
 * backup still carries are deleted, the app reopens in Default, and the folders of profiles the
 * restored registry doesn't list are removed. With fullBackupOnly the key-value calls never run.
 */
class FlowBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestoreFinished() {
        val deleted = RestoredDataCleanup.deleteInstallOnlyFiles(filesDir)
        if (deleted.isNotEmpty()) Log.i(TAG, "Removed restored install-only files: $deleted")
        val registry = runBlocking { ActiveProfile.registry(this@FlowBackupAgent).reopenInDefault() }
        val unlisted =
            RestoredDataCleanup.deleteUnlistedProfiles(
                folders = ProfileFolders.of(this),
                profileIds = registry.profiles.mapTo(HashSet()) { it.id },
            )
        if (unlisted.isNotEmpty()) Log.i(TAG, "Removed profiles the restored registry doesn't list: $unlisted")
    }

    private companion object {
        const val TAG = "FlowBackupAgent"
    }
}

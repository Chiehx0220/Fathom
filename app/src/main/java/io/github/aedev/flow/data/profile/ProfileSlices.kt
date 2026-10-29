package io.github.aedev.flow.data.profile

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.BackupRepository
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.stats.RecapBackup
import io.github.aedev.flow.data.stats.VideoStatsRecorder
import io.github.aedev.flow.sync2.service.LinkedReset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A profile in the single-profile master backup format: app data, the video and music engines, recap and favourite
 * artists. The master backup and its restore, sync rollback and a new profile's seed all write and read it here.
 */
@Singleton
class ProfileSlices
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: BackupRepository,
        private val musicBrain: MusicBrainEngine,
        private val videoStats: VideoStatsRecorder,
        private val favouriteArtists: FavouriteArtistsStore,
        private val linkedReset: LinkedReset,
    ) {
        /** The whole running profile. */
        suspend fun export(uri: Uri): Result<Unit> =
            measured("export") { repository.exportMasterBackup(uri, musicBrainBytes(), recapBytes(), favouriteArtists.export()) }

        suspend fun export(file: File): Result<Unit> = export(Uri.fromFile(file)).also { logSize(file) }

        /**
         * Every profile: the running one exported now, the others from the slice each wrote when it was left. Returns
         * the names of profiles that have no slice yet, which the backup can't hold.
         */
        suspend fun exportMaster(uri: Uri): Result<List<String>> =
            withMaster { write ->
                val out =
                    context.contentResolver.openOutputStream(uri, "wt")
                        ?: return@withMaster Result.failure(IOException("Could not open $uri"))
                out.use(write)
                Result.success(Unit)
            }

        suspend fun exportMasterToFolder(folder: Uri): Result<List<String>> =
            withMaster { write -> repository.writeToFolder(folder, MASTER_FILE_NAME, MASTER_MIME_TYPE, write) }

        /** Keeps the running profile's data for the master backup while it is closed: a closed profile can't change. */
        suspend fun writeSwitchSlice() {
            val target = sliceFile(ActiveProfile.get(context))
            val partial = File(target.parentFile, "$SLICE_NAME.partial")
            target.parentFile?.mkdirs()
            export(partial)
                .onSuccess { if (!partial.renameTo(target)) partial.delete() }
                .onFailure {
                    partial.delete()
                    Log.e(TAG, "Could not keep this profile's slice for the master backup", it)
                }
        }

        private suspend fun withMaster(save: suspend ((OutputStream) -> Unit) -> Result<Unit>): Result<List<String>> =
            withContext(Dispatchers.IO) {
                val running = ActiveProfile.get(context)
                val registry = ActiveProfile.registry(context).state.first()
                val defaultId = registry.defaultId ?: running.id
                val live = File(context.cacheDir, "master_live_slice.zip")
                try {
                    export(live).getOrElse { return@withContext Result.failure(it) }
                    val slices =
                        registry.profiles
                            .mapNotNull { profile ->
                                val file =
                                    if (profile.id ==
                                        running.id
                                    ) {
                                        live
                                    } else {
                                        sliceFile(RunningProfile(profile.id, profile.id == defaultId))
                                    }
                                file.takeIf(File::isFile)?.let { profile.id to it }
                            }.toMap()
                    val avatars =
                        registry.profiles
                            .filter { it.avatar.kind == ProfileAvatar.PHOTO }
                            .mapNotNull { profile ->
                                ProfileAvatarPhotos.fileFor(context, profile.id).takeIf(File::isFile)?.let {
                                    profile.id to
                                        it
                                }
                            }.toMap()
                    val manifest = MasterManifest(defaultId = defaultId, profiles = registry.profiles, included = slices.keys.toList())
                    measured("master") {
                        save { out -> MasterBackupZip.write(out, manifest, slices[defaultId], slices - defaultId, avatars) }
                    }.map { registry.profiles.filterNot { it.id in slices }.map { it.displayName(context) } }
                } finally {
                    live.delete()
                }
            }

        private fun sliceFile(profile: RunningProfile) = File(ProfileFiles.noBackupRoot(context.noBackupFilesDir, profile), SLICE_NAME)

        /** Writes [parts] of the running profile as [profileId]'s seed, applied when that profile first opens. */
        suspend fun writeSeed(
            profileId: String,
            parts: Set<SlicePart>,
        ): Result<Unit> {
            val file = PendingSlice.file(ProfileFiles.noBackupRoot(context.noBackupFilesDir, RunningProfile(profileId, isDefault = false)))
            file.parentFile?.mkdirs()
            val engine = SlicePart.RECOMMENDATIONS in parts
            return measured("seed") {
                repository.exportMasterBackup(
                    Uri.fromFile(file),
                    musicBrain = if (engine) musicBrainBytes() else null,
                    favouriteArtists = if (engine) favouriteArtists.export() else null,
                    seed = parts,
                )
            }.onSuccess { logSize(file) }.onFailure { file.delete() }
        }

        suspend fun import(uri: Uri): Result<Unit> =
            repository.importMasterBackup(
                uri,
                onMusicBrain = { bytes -> musicBrain.importBrainFromStream(bytes.inputStream()) },
                onRecap = ::restoreRecap,
                onFavouriteArtists = favouriteArtists::restore,
            )

        /** A seed or restore that waited for the running profile; see [PendingSlice]. */
        suspend fun applyPending(file: File) {
            measured("pending import") { import(Uri.fromFile(file)) }
                .onSuccess { linkedReset.dataReplaced() }
                .onFailure { Log.e(TAG, "Could not apply the pending slice", it) }
        }

        /**
         * The profile an old master backup, from before profiles, would restore into when there is more than one to
         * choose from; null when the file holds every profile or there is only one, so it restores without asking.
         */
        suspend fun oldMasterTarget(uri: Uri): String? =
            withContext(Dispatchers.IO) {
                val registry = ActiveProfile.registry(context).state.first()
                if (registry.profiles.size < 2) return@withContext null
                withCopy(uri) { copy ->
                    if (MasterBackupZip.readManifest(copy) != null) {
                        null
                    } else {
                        registry.profiles.firstOrNull { it.id == ActiveProfile.get(context).id }?.displayName(context)
                    }
                }
            }

        /**
         * Restores a master backup. The backup's Default lands in this device's Default and every other profile under
         * its own id, added to the registry when missing; the running profile is restored now and the others wait
         * until they are next opened. Refused before anything changes when the result would exceed the limit.
         */
        suspend fun restoreMaster(uri: Uri): Result<MasterRestored> =
            withContext(Dispatchers.IO) {
                withCopy(uri) { copy -> restoreMasterFrom(copy) }
            }

        private suspend fun restoreMasterFrom(copy: File): Result<MasterRestored> {
            val running = ActiveProfile.get(context)
            val manifest =
                MasterBackupZip.readManifest(copy)
                    ?: return import(Uri.fromFile(copy)).map { MasterRestored(restoredNow = true, waiting = emptyList()) }
            val registry = ActiveProfile.registry(context)
            val localDefault = registry.state.first().defaultId ?: running.id
            val incoming = manifest.profiles.filter { it.id in manifest.included }
            val targetOf = { profile: Profile -> if (profile.id == manifest.defaultId) localDefault else profile.id }
            val others = incoming.filter { it.id != manifest.defaultId }
            if (!registry.addRestored(others)) return Result.failure(TooManyProfiles())
            var restoredNow = false
            val waiting = mutableListOf<String>()
            incoming.forEach { profile ->
                val target = targetOf(profile)
                val fromFolder = profile.id.takeUnless { it == manifest.defaultId }
                val local = RunningProfile(target, isDefault = target == localDefault)
                if (target != localDefault &&
                    MasterBackupZip.extractAvatar(copy, profile.id, ProfileAvatarPhotos.fileFor(context, target))
                ) {
                    registry.setAppearance(target, profile.colorKey, profile.avatar)
                }
                if (target == running.id) {
                    val now = File(context.cacheDir, "master_restore_now.zip")
                    try {
                        if (MasterBackupZip.extractSlice(copy, fromFolder, now)) {
                            import(Uri.fromFile(now)).getOrElse { return Result.failure(it) }
                            restoredNow = true
                        }
                    } finally {
                        now.delete()
                    }
                } else if (MasterBackupZip.extractSlice(
                        copy,
                        fromFolder,
                        PendingSlice.file(ProfileFiles.noBackupRoot(context.noBackupFilesDir, local)),
                    )
                ) {
                    waiting += profile.displayName(context)
                }
            }
            return Result.success(MasterRestored(restoredNow, waiting))
        }

        private suspend fun <T> withCopy(
            uri: Uri,
            read: suspend (File) -> T,
        ): T {
            val copy = File(context.cacheDir, "master_restore.zip")
            try {
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not read $uri")
                input.use { source -> copy.outputStream().use { source.copyTo(it) } }
                return read(copy)
            } finally {
                copy.delete()
            }
        }

        private suspend fun musicBrainBytes(): ByteArray? =
            runCatching { ByteArrayOutputStream().also { musicBrain.exportBrainToStream(it) }.toByteArray() }.getOrNull()

        private suspend fun recapBytes(): ByteArray? =
            runCatching { RecapBackup(video = videoStats.snapshot(), music = musicBrain.listeningStats()).encode() }.getOrNull()

        private suspend fun restoreRecap(bytes: ByteArray) {
            val backup = RecapBackup.decode(bytes) ?: return
            backup.video?.let { videoStats.restore(it) }
            backup.music?.let { musicBrain.restoreListeningStats(it) }
        }

        // History can be large; these lines are how a slice's cost is measured on a real data set.
        private inline fun <T> measured(
            what: String,
            block: () -> Result<T>,
        ): Result<T> {
            val start = SystemClock.elapsedRealtime()
            return block().also { Log.i(TAG, "Slice $what took ${SystemClock.elapsedRealtime() - start} ms, ok=${it.isSuccess}") }
        }

        private fun logSize(file: File) {
            if (file.exists()) Log.i(TAG, "Slice ${file.name} is ${file.length()} bytes")
        }

        private companion object {
            const val TAG = "ProfileSlices"
            const val SLICE_NAME = "slice.zip"
            const val MASTER_FILE_NAME = "flow_master_backup.zip"
            const val MASTER_MIME_TYPE = "application/zip"
        }
    }

/** What a master restore did: the running profile restored now, and the profiles that will be when next opened. */
data class MasterRestored(
    val restoredNow: Boolean,
    val waiting: List<String>,
)

/** A master backup that would take this device past the profile limit. */
class TooManyProfiles : IllegalStateException("A master backup can't add profiles past the limit")

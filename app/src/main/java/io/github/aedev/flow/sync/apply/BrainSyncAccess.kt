package io.github.aedev.flow.sync.apply

import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicBrainStorage
import io.github.aedev.flow.sync.canonical.CanonicalMusicBrain
import io.github.aedev.flow.sync.identity.Hlc
import io.github.aedev.flow.sync.mapping.MusicBrainMapper
import io.github.aedev.flow.sync.merge.CounterOwnership
import io.github.aedev.flow.sync.merge.MusicBrainCrdtState
import io.github.aedev.flow.sync.merge.MusicBrainCrdtStore
import io.github.aedev.flow.sync.merge.MusicBrainMerger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Music brain collection: CRDT sidecar of the engine file. Local counter growth is credited to the HLC node id
 * ([myNode]) on send and receive; the full-device-id entry written by earlier builds stays frozen and counts as local.
 */
@Singleton
class BrainSyncAccess
    @Inject
    constructor(
        private val musicBrainCrdtStore: MusicBrainCrdtStore,
        private val musicBrainEngine: MusicBrainEngine,
    ) {
        suspend fun exportMusicBrain(
            myNode: String,
            hlc: String,
        ): CanonicalMusicBrain {
            val local = exportLocalMusicBrain()
            val sidecar = attributeLocalMusic(musicBrainCrdtStore.load(), myNode, local, hlc)
            musicBrainCrdtStore.save(sidecar)
            return MusicBrainMapper.toCanonical(local, myNode, hlc, sidecar)
        }

        suspend fun mergeMusicBrain(
            remote: CanonicalMusicBrain,
            senderId: String,
            myNode: String,
            myDeviceId: String,
            hlc: String,
        ) {
            val local = exportLocalMusicBrain()
            val sidecar = attributeLocalMusic(musicBrainCrdtStore.load(), myNode, local, hlc)
            val own = MusicBrainMapper.toCanonical(local, myNode, hlc, sidecar)
            val merged =
                CounterOwnership.music(
                    merged = MusicBrainMerger.merge(own, remote),
                    local = own,
                    remote = remote,
                    own = setOf(myNode, myDeviceId),
                    sender = setOf(senderId, Hlc.nodeFromDeviceId(senderId)),
                )
            val mergedBrain = MusicBrainMapper.writeBack(merged, local)
            musicBrainEngine.importBrainFromStream(ByteArrayInputStream(MusicBrainMapper.serialize(mergedBrain)))
            musicBrainCrdtStore.save(MusicBrainCrdtState.afterMerge(merged))
        }

        private suspend fun exportLocalMusicBrain(): MusicBrainStorage.SerializableMusicBrain {
            val bytes =
                ByteArrayOutputStream().use { bos ->
                    musicBrainEngine.exportBrainToStream(bos)
                    bos.toByteArray()
                }
            return runCatching { MusicBrainMapper.parse(bytes) }
                .getOrElse { throw IllegalStateException("the local music brain could not be parsed", it) }
        }

        private fun attributeLocalMusic(
            state: MusicBrainCrdtState,
            myNode: String,
            brain: MusicBrainStorage.SerializableMusicBrain,
            hlc: String,
        ): MusicBrainCrdtState =
            MusicBrainCrdtState.attributeLocal(
                state = state,
                myDevice = myNode,
                totalPlaysScalar = brain.totalPlays.toLong(),
                artistPlayScalars = brain.artistAffinity.mapValues { it.value.plays.toLong() },
                artistScores = brain.artistAffinity.mapValues { it.value.score },
                seenArtists = brain.seenArtists.toSet(),
                blockedArtists = brain.blockedArtists.toSet(),
                dislikedArtists = brain.dislikedArtists,
                appetite = brain.discoveryAppetite,
                hlc = hlc,
            )
    }

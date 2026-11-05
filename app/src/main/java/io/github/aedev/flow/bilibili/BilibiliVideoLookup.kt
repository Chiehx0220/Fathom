package io.github.aedev.flow.bilibili

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Video details by id; null for a non-Bilibili id (no request) or a failed lookup. */
@Singleton
class BilibiliVideoLookup
    @Inject
    constructor(
        private val api: BilibiliApi,
    ) {
        suspend fun video(id: String): Video? {
            val ref = BilibiliRef.ofVideoId(id) as? BilibiliRef.Video ?: return null
            return try {
                BilibiliVideoMapper.videoFromInfo(id, api.videoInfo(ref.bvid, ref.part))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

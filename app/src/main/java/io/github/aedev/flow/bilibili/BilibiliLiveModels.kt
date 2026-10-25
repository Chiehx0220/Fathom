package io.github.aedev.flow.bilibili

// Live results; wire: BilibiliWireLive.kt.

enum class BilibiliLiveStatus { OFFLINE, LIVE, REBROADCAST }

/** Room page: uploader, title, start time. */
data class BilibiliLiveRoom(
    /** Long room number (link may carry a short alias). */
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val uploader: BilibiliUploader,
    val viewerCount: Long,
    /** Stream start, epoch seconds; 0 unknown. */
    val startedAtSec: Long,
    val tags: List<String>,
    val status: BilibiliLiveStatus,
)

/** Room playback source: HLS master playlist (all qualities) or single FLV. */
data class BilibiliLiveStreams(
    val hlsMasterUrl: String?,
    val flvUrl: String?,
)

data class BilibiliLivePlayback(
    val room: BilibiliLiveRoom,
    val streams: BilibiliLiveStreams,
)

/** Room list item: recommendations, search. */
data class BilibiliLiveItem(
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String,
    val viewerCount: Long,
    val startedAtSec: Long = 0,
)

/** Chat socket endpoint + auth. */
data class BilibiliLiveChatAccess(
    val roomId: Long,
    val token: String,
    /** "wss://host:port/sub", best first. */
    val endpoints: List<String>,
    val buvid: String?,
)

/** Chat event. */
sealed interface BilibiliLiveMessage {
    data class Chat(
        val text: String,
        val argbColor: Int,
        val position: BilibiliDanmakuPosition,
        val author: String,
        val avatarUrl: String?,
    ) : BilibiliLiveMessage

    data class SuperChat(
        val message: String,
        val priceYuan: Int,
        val argbColor: Int,
        val headerArgbColor: Int,
        val author: String,
        val avatarUrl: String?,
    ) : BilibiliLiveMessage {
        /** Danmaku layer form. */
        val text: String get() = "(¥$priceYuan) $message"
    }
}

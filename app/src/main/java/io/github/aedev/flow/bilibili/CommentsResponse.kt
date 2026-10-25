package io.github.aedev.flow.bilibili

// Comment endpoint JSON.

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CommentsResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val replies: List<Reply>? = null,
        @SerialName("top_replies") val topReplies: List<Reply>? = null,
        val cursor: Cursor = Cursor(),
    )

    @Serializable
    data class Cursor(
        @SerialName("is_end") val isEnd: Boolean = true,
        @SerialName("pagination_reply") val paginationReply: PaginationReply = PaginationReply(),
    )

    @Serializable
    data class PaginationReply(
        @SerialName("next_offset") val nextOffset: String = "",
    )

    @Serializable
    data class Reply(
        @SerialName("rpid_str") val rpidStr: String = "",
        val mid: Long = 0,
        val like: Long = 0,
        val ctime: Long = 0,
        val rcount: Int = 0,
        val member: Member = Member(),
        val content: Content = Content(),
        @SerialName("up_action") val upAction: UpAction = UpAction(),
    )

    @Serializable
    data class Member(
        val uname: String = "",
        val avatar: String = "",
    )

    @Serializable
    data class Content(
        val message: String = "",
    )

    @Serializable
    data class UpAction(
        val like: Boolean = false,
    )
}

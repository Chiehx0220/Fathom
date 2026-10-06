package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class BilibiliLiveRoomTest {
    private val session: BilibiliSession = mockk()
    private val live = BilibiliLive(session, Json { ignoreUnknownKeys = true })

    private fun answer(vararg rooms: Pair<String, Long>) {
        val body =
            rooms.joinToString(",") { (key, roomId) ->
                """"$key":{"room_id":$roomId,"uid":9,"uname":"up","title":"t","cover":"http://c","live_status":1,"live_time":"0000-00-00 00:00:00"}"""
            }
        coEvery { session.headers(any()) } returns LinkedHashMap()
        coEvery { session.get(any(), any()) } returns """{"code":0,"message":"OK","data":{"by_room_ids":{$body}}}"""
    }

    @Test
    fun `a room asked for by its long number is found under it`() =
        runTest {
            answer("545068" to 545068L)

            assertThat(live.room(545068L).roomId).isEqualTo(545068L)
        }

    @Test
    fun `a room asked for by a short number comes back under the long one and is still found`() =
        runTest {
            answer("545068" to 545068L)

            val room = live.room(7777L)

            assertThat(room.roomId).isEqualTo(545068L)
            assertThat(room.status).isEqualTo(BilibiliLiveStatus.LIVE)
        }

    @Test
    fun `an answer with no room is not available`() =
        runTest {
            coEvery { session.headers(any()) } returns LinkedHashMap()
            coEvery { session.get(any(), any()) } returns """{"code":0,"message":"OK","data":{"by_room_ids":{}}}"""

            val failure = runCatching { live.room(7777L) }.exceptionOrNull()

            assertThat(failure).isInstanceOf(BilibiliContentNotAvailableException::class.java)
        }
}

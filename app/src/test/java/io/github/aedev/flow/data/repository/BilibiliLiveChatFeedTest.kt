package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BilibiliDanmakuPosition
import io.github.aedev.flow.bilibili.BilibiliLiveMessage
import io.github.aedev.flow.data.model.LiveChatMessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BilibiliLiveChatFeedTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val chat = MutableSharedFlow<BilibiliLiveMessage>(extraBufferCapacity = 64)
    private val opened = mutableListOf<Long>()
    private var live = 0

    private val feed =
        BilibiliLiveChatFeed(
            source = { room ->
                opened += room
                callbackFlow {
                    live++
                    val relay = launch { chat.collect { send(it) } }
                    awaitClose {
                        live--
                        relay.cancel()
                    }
                }
            },
            scope = scope,
        )

    private fun say(
        text: String,
        author: String = "u",
    ) = BilibiliLiveMessage.Chat(text, 0xFFFFFFFF.toInt(), BilibiliDanmakuPosition.SCROLL, author, "https://i0.hdslb.com/$author.jpg")

    private fun superChat() =
        BilibiliLiveMessage.SuperChat(
            message = "thanks",
            priceYuan = 30,
            argbColor = 0xFF2A60B2.toInt(),
            headerArgbColor = 0xFF3171D2.toInt(),
            author = "fan",
            avatarUrl = "https://i0.hdslb.com/face.jpg",
        )

    private fun feedTest(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `only live room ids are claimed`() =
        feedTest {
            assertThat(feed.seed("live:21452505")).isNotNull()
            assertThat(feed.seed("BV1xx411c7mD")).isNull()
            assertThat(feed.seed("dQw4w9WgXcQ")).isNull()
            assertThat(feed.poll("CjoKGQoLZFF3NHc5V2dYY1E")).isNull()
        }

    @Test
    fun `a poll returns what arrived since the last one`() =
        feedTest {
            val token = feed.seed("live:7")!!
            assertThat(feed.poll(token)!!.messages).isEmpty()
            runCurrent()

            chat.emit(say("one"))
            chat.emit(say("two"))
            runCurrent()

            val page = feed.poll(token)!!
            assertThat(page.messages.map { it.message }).containsExactly("one", "two").inOrder()
            assertThat(page.nextContinuation).isEqualTo(token)
            assertThat(feed.poll(token)!!.messages).isEmpty()
        }

    @Test
    fun `chat and super chat map to the list fields`() =
        feedTest {
            val token = feed.seed("live:7")!!
            feed.poll(token)
            runCurrent()

            chat.emit(say("hello", author = "alice"))
            chat.emit(superChat())
            runCurrent()

            val (text, paid) = feed.poll(token)!!.messages
            assertThat(text.author).isEqualTo("alice")
            assertThat(text.authorPhotoUrl).isEqualTo("https://i0.hdslb.com/alice.jpg")
            assertThat(text.type).isEqualTo(LiveChatMessageType.TEXT)
            assertThat(paid.type).isEqualTo(LiveChatMessageType.SUPER_CHAT)
            assertThat(paid.author).isEqualTo("fan")
            assertThat(paid.authorPhotoUrl).isEqualTo("https://i0.hdslb.com/face.jpg")
            assertThat(paid.message).isEqualTo("thanks")
            assertThat(paid.superChatAmount).isEqualTo("¥30")
            assertThat(paid.superChatArgb).isEqualTo(0xFF2A60B2.toInt())
            assertThat(paid.superChatHeaderArgb).isEqualTo(0xFF3171D2.toInt())
        }

    @Test
    fun `message ids are unique across identical lines`() =
        feedTest {
            val token = feed.seed("live:7")!!
            feed.poll(token)
            runCurrent()

            repeat(3) { chat.emit(say("666")) }
            runCurrent()

            assertThat(
                feed
                    .poll(token)!!
                    .messages
                    .map { it.id }
                    .toSet(),
            ).hasSize(3)
        }

    @Test
    fun `the inbox keeps only the newest messages`() =
        feedTest {
            val token = feed.seed("live:7")!!
            feed.poll(token)
            runCurrent()

            repeat(300) {
                chat.emit(say("m$it"))
                runCurrent()
            }

            val messages = feed.poll(token)!!.messages
            assertThat(messages).hasSize(200)
            assertThat(messages.last().message).isEqualTo("m299")
        }

    @Test
    fun `the reader closes after polling stops and reopens on the next poll`() =
        feedTest {
            val token = feed.seed("live:7")!!
            feed.poll(token)
            runCurrent()
            assertThat(live).isEqualTo(1)

            advanceTimeBy(15_001)
            runCurrent()
            assertThat(live).isEqualTo(0)

            feed.poll(token)
            runCurrent()
            assertThat(live).isEqualTo(1)
            assertThat(opened).containsExactly(7L, 7L)
        }

    @Test
    fun `polling keeps the reader open`() =
        feedTest {
            val token = feed.seed("live:7")!!
            repeat(40) {
                feed.poll(token)
                advanceTimeBy(1_000)
            }
            assertThat(live).isEqualTo(1)
            assertThat(opened).containsExactly(7L)
        }

    @Test
    fun `a different room replaces the reader and drops pending messages`() =
        feedTest {
            feed.poll(feed.seed("live:7")!!)
            runCurrent()
            chat.emit(say("from seven"))
            runCurrent()

            val page = feed.poll(feed.seed("live:8")!!)!!
            runCurrent()

            assertThat(page.messages).isEmpty()
            assertThat(opened).containsExactly(7L, 8L).inOrder()
            assertThat(live).isEqualTo(1)
        }
}

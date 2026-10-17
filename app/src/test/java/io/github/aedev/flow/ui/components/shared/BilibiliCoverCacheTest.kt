package io.github.aedev.flow.ui.components.shared

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BilibiliCoverCacheTest {
    private var clock = 0L
    private val calls = AtomicInteger()

    private fun cache(
        timeoutMs: Long = BilibiliCoverCache.TIMEOUT_MS,
        fetch: suspend (String) -> String,
    ) = BilibiliCoverCache(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        now = { clock },
        timeoutMs = timeoutMs,
    ) { _, bvid ->
        calls.incrementAndGet()
        fetch(bvid)
    }

    @Test
    fun `a cover is asked of Bilibili once however many cards want it`() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val cache =
                cache {
                    release.await()
                    "https://i0.hdslb.com/$it.jpg"
                }

            val answers = (1..5).map { async(Dispatchers.Default) { cache.coverOf(mockk(), "BV1xx411c7mD") } }
            delay(50)
            release.complete(Unit)

            assertThat(answers.awaitAll().toSet()).containsExactly("https://i0.hdslb.com/BV1xx411c7mD.jpg")
            assertThat(cache.coverOf(mockk(), "BV1xx411c7mD")).isEqualTo("https://i0.hdslb.com/BV1xx411c7mD.jpg")
            assertThat(calls.get()).isEqualTo(1)
        }

    @Test
    fun `a video Bilibili could not answer is left alone for a while, then asked again`() =
        runBlocking {
            val cache = cache { error("offline") }

            assertThat(cache.coverOf(mockk(), "BV1xx411c7mD")).isNull()
            assertThat(cache.coverOf(mockk(), "BV1xx411c7mD")).isNull()
            assertThat(calls.get()).isEqualTo(1)

            clock += BilibiliCoverCache.RETRY_MS
            assertThat(cache.coverOf(mockk(), "BV1xx411c7mD")).isNull()
            assertThat(calls.get()).isEqualTo(2)
        }

    @Test
    fun `one video failing does not hold back another`() =
        runBlocking {
            val cache = cache { if (it == "BV1xx411c7mD") error("gone") else "cover-$it" }

            assertThat(cache.coverOf(mockk(), "BV1xx411c7mD")).isNull()
            assertThat(cache.coverOf(mockk(), "BV1yy411c7mE")).isEqualTo("cover-BV1yy411c7mE")
        }

    @Test
    fun `a blank answer counts as no cover`() =
        runBlocking {
            val cache = cache { "" }

            assertThat(cache.coverOf(mockk(), "BV1xx411c7mD")).isNull()
        }

    @Test
    fun `a request that hangs gives up`() =
        runBlocking {
            val cache = cache(timeoutMs = 20) { delay(10_000).let { "late" } }

            assertThat(cache.coverOf(mockk(), "BV1xx411c7mD")).isNull()
        }
}

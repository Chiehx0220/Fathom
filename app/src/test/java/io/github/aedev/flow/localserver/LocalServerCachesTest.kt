package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamExtractor

class LocalServerCachesTest {
    private val hour = 3_600_000L

    @Test
    fun `a stream url is found until it expires`() {
        val cache = TtlCache<String>(maxItems = 60)
        cache.put("live", "https://cdn/a", hour)
        cache.put("gone", "https://cdn/b", -1L)

        assertThat(cache.get("live")).isEqualTo("https://cdn/a")
        assertThat(cache.get("gone")).isNull()
        assertThat(cache.get("never-stored")).isNull()
    }

    @Test
    fun `removing and clearing forget entries`() {
        val cache = TtlCache<String>(maxItems = 60)
        cache.put("a", "1", hour)
        cache.put("b", "2", hour)

        cache.remove("a")
        assertThat(cache.get("a")).isNull()
        assertThat(cache.get("b")).isEqualTo("2")

        cache.clear()
        assertThat(cache.get("b")).isNull()
    }

    @Test
    fun `it keeps only the most recently used entries`() {
        val cache = TtlCache<String>(maxItems = 60)
        repeat(60) { cache.put("k$it", "v$it", hour) }
        cache.get("k0")

        cache.put("k60", "v60", hour)

        assertThat(cache.get("k0")).isEqualTo("v0")
        assertThat(cache.get("k1")).isNull()
        assertThat(cache.get("k60")).isEqualTo("v60")
    }

    @Test
    fun `it holds any value type and drops the expired`() {
        val cache = TtlCache<StreamExtractor>(maxItems = 15)
        val extractors = List(16) { mockk<StreamExtractor>() }
        extractors.take(15).forEachIndexed { i, e -> cache.put("k$i", e, hour) }

        cache.put("k15", extractors[15], hour)

        assertThat(cache.get("k0")).isNull()
        assertThat(cache.get("k15")).isSameInstanceAs(extractors[15])

        cache.put("stale", extractors[0], -1L)
        assertThat(cache.get("stale")).isNull()
    }
}

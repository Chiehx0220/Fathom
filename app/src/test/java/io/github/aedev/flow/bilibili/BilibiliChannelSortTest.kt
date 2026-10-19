package io.github.aedev.flow.bilibili

import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliChannelSortTest {
    @Test
    fun `sort orders are the API values`() {
        assertEquals("pubdate", BilibiliChannelSort.LATEST.order)
        assertEquals("click", BilibiliChannelSort.POPULAR.order)
    }

    @Test
    fun `popular skips the search API that ignores it`() {
        assertEquals(
            BilibiliUserSpace.MODE_APP,
            BilibiliUserSpace.apiFor(BilibiliUserSpace.MODE_SEARCH, BilibiliChannelSort.POPULAR),
        )
    }

    @Test
    fun `every other combination keeps the current API`() {
        for (mode in listOf(BilibiliUserSpace.MODE_WEB, BilibiliUserSpace.MODE_SEARCH, BilibiliUserSpace.MODE_APP)) {
            assertEquals(mode, BilibiliUserSpace.apiFor(mode, BilibiliChannelSort.LATEST))
        }
        assertEquals(BilibiliUserSpace.MODE_WEB, BilibiliUserSpace.apiFor(BilibiliUserSpace.MODE_WEB, BilibiliChannelSort.POPULAR))
        assertEquals(BilibiliUserSpace.MODE_APP, BilibiliUserSpace.apiFor(BilibiliUserSpace.MODE_APP, BilibiliChannelSort.POPULAR))
    }

    @Test
    fun `a page key defaults to latest`() {
        assertEquals(BilibiliChannelSort.LATEST, BilibiliChannelPageKey(1, 0L).sort)
    }
}

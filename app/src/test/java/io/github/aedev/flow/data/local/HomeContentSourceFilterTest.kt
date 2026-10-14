package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeContentSourceFilterTest {
    @Test
    fun `an unset or unknown choice means YouTube`() {
        assertThat(HomeContentSourceFilter.fromStoredName(null)).isEqualTo(HomeContentSourceFilter.YOUTUBE)
        assertThat(HomeContentSourceFilter.fromStoredName("gone")).isEqualTo(HomeContentSourceFilter.YOUTUBE)
    }

    @Test
    fun `a stored choice is kept`() {
        HomeContentSourceFilter.entries.forEach {
            assertThat(HomeContentSourceFilter.fromStoredName(it.name)).isEqualTo(it)
        }
    }
}

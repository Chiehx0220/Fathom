package io.github.aedev.flow.localserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ByteRangeTest {
    @Test
    fun `a closed range is taken as asked`() {
        assertEquals(10L..19L, parseByteRange("bytes=10-19", 100))
    }

    @Test
    fun `an open end runs to the last byte`() {
        assertEquals(90L..99L, parseByteRange("bytes=90-", 100))
    }

    @Test
    fun `an end past the file is cut to the file`() {
        assertEquals(0L..99L, parseByteRange("bytes=0-5000", 100))
    }

    @Test
    fun `a suffix asks for the last bytes`() {
        assertEquals(90L..99L, parseByteRange("bytes=-10", 100))
        assertEquals(0L..99L, parseByteRange("bytes=-500", 100))
    }

    @Test
    fun `only the first of several ranges is served`() {
        assertEquals(0L..4L, parseByteRange("bytes=0-4, 10-14", 100))
    }

    @Test
    fun `a range the file does not have is refused`() {
        assertNull(parseByteRange("bytes=100-", 100))
        assertNull(parseByteRange("bytes=50-10", 100))
        assertNull(parseByteRange("bytes=-0", 100))
        assertNull(parseByteRange("items=0-1", 100))
        assertNull(parseByteRange(null, 100))
        assertNull(parseByteRange("bytes=0-1", 0))
    }
}

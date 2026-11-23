package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BilibiliVideoshotTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(body: String) = json.decodeFromString<VideoshotResponse>(body).toFrameset()

    private fun response(
        code: Int = 0,
        image: String = """["//bimp.hdslb.com/videoshotpvhdboss/a-0001.jpg"]""",
        index: String = "[0,0,5,10,16,21,27,32,38,43,49,54,60,65]",
        columns: Int = 10,
        rows: Int = 10,
    ) =
        """{"code":$code,"data":{"pvdata":"x.bin","img_x_len":$columns,"img_y_len":$rows,"img_x_size":480,"img_y_size":270,"image":$image,"index":$index}}"""

    @Test
    fun `a leading placeholder is dropped and frames are spread evenly`() {
        val shot = parse(response())!!
        assertEquals(13, shot.totalCount)
        assertEquals(5417, shot.durationPerFrame)
        assertEquals(10, shot.framesPerPageX)
        assertEquals(480, shot.frameWidth)
        assertEquals(270, shot.frameHeight)
    }

    @Test
    fun `protocol-relative sheet urls become https`() {
        assertEquals(listOf("https://bimp.hdslb.com/videoshotpvhdboss/a-0001.jpg"), parse(response())!!.urls)
    }

    @Test
    fun `an absolute sheet url is kept`() {
        val shot = parse(response(image = """["https://i0.hdslb.com/bfs/videoshot/b.jpg"]"""))!!
        assertEquals(listOf("https://i0.hdslb.com/bfs/videoshot/b.jpg"), shot.urls)
    }

    @Test
    fun `frames beyond the sheets are ignored`() {
        val shot = parse(response(columns = 2, rows = 2, index = "[0,0,1,2,3,4,5,6]"))!!
        assertEquals(4, shot.totalCount)
    }

    @Test
    fun `an error code has no previews`() {
        assertNull(parse(response(code = -400)))
    }

    @Test
    fun `no sheets means no previews`() {
        assertNull(parse(response(image = "[]")))
    }

    @Test
    fun `fewer than two frames cannot be spaced`() {
        assertNull(parse(response(index = "[0,0]")))
    }

    @Test
    fun `a missing data object has no previews`() {
        assertNull(parse("""{"code":0}"""))
    }

    @Test
    fun `an empty sheet grid has no previews`() {
        assertNull(parse(response(columns = 0)))
    }
}

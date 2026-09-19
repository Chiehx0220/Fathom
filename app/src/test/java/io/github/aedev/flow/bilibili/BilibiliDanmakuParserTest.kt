package io.github.aedev.flow.bilibili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class BilibiliDanmakuParserTest {
    private val xml =
        """<?xml version="1.0" encoding="UTF-8"?><i><state>0</state>
        <d p="12.5,1,25,16777215,1700000000,0,abc,1">hello &amp; bye</d>
        <d p="3,5,36,255,1700000000,0,abc,2">top</d>
        <d p="4,4,18,0,1700000000,0,abc,3">bottom</d>
        <d p="5,1,25,0,1700000000,3,abc,4">vote</d>
        <d p="6,7,25,0,1700000000,0,abc,5">[1,1,"1-1",4,"advanced text"]</d></i>"""

    @Test
    fun parsesFieldsAndSkipsVotes() {
        val list = BilibiliDanmakuParser.parse(xml)
        assertEquals(listOf("hello & bye", "top", "bottom", "advanced text"), list.map { it.text })
        assertEquals(12_500L + 2_500L, list[0].timeMs)
        assertEquals(0xFFFFFFFF.toInt(), list[0].argbColor)
        assertEquals(BilibiliDanmakuPosition.TOP, list[1].position)
        assertEquals(0.7f, list[1].relativeFontSize)
        assertEquals(BilibiliDanmakuPosition.BOTTOM, list[2].position)
        assertEquals(0.5f, list[2].relativeFontSize)
    }

    @Test
    fun stateOneMeansNoDanmaku() {
        assertTrue(BilibiliDanmakuParser.parse("<i><state>1</state></i>").isEmpty())
    }

    @Test
    fun decodesRawDeflateAndPlainBodies() {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(xml.toByteArray())
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        assertEquals(xml, BilibiliDanmakuParser.decodeBody(out.toByteArray()))
        assertEquals(xml, BilibiliDanmakuParser.decodeBody(xml.toByteArray()))
    }
}

/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * extractor/src/main/java/org/schabi/newpipe/extractor/services/bilibili/DeviceForger.java
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import java.util.Base64
import java.util.Locale
import kotlin.random.Random

/**
 * The browser fingerprint Bilibili's risk control sees: a frozen-UA modern Chrome on Windows plus a
 * WebGL vendor/renderer pair and a window size. One is forged per process and reused, so every
 * request in a session presents the same device.
 */
object DeviceForger {
    class Device(
        val userAgent: String,
        val webGlVersion: String,
        val webGlRendererInfo: String,
        val innerWidth: Int,
        val innerHeight: Int,
    ) {
        val webGlVersionBase64: String = encodeToBase64SubString(webGlVersion)
        val webGlRendererInfoBase64: String = encodeToBase64SubString(webGlRendererInfo)
    }

    private class GraphicCard(val vendor: String, val model: String)

    private const val USER_AGENT_TEMPLATE =
        "Mozilla/5.0 (%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%d.0.0.0 Safari/537.36"
    private const val DEFAULT_CHROMIUM_WEBGL_VERSION = "WebGL 1.0 (OpenGL ES 2.0 Chromium)"
    private const val ANGLE_RENDERER_INFO_TEMPLATE = "ANGLE (%s, %s Direct3D11 vs_5_0 ps_5_0, D3D11)Google Inc. (%s)"

    private val cards =
        arrayOf(
        GraphicCard("AMD", "AMD Radeon 780M Graphics (0x000015BF)"),
        GraphicCard("AMD", "AMD Radeon RX 5700 (0x0000731F)"),
        GraphicCard("AMD", "AMD Radeon RX 6500 XT (0x0000743F)"),
        GraphicCard("AMD", "AMD Radeon RX 6600 (0x000073FF)"),
        GraphicCard("AMD", "AMD Radeon RX 6750 GRE 10GB (0x000073FF)"),
        GraphicCard("AMD", "AMD Radeon RX 6750 GRE 12GB (0x000073DF)"),
        GraphicCard("AMD", "AMD Radeon RX 6750 XT (0x000073DF)"),
        GraphicCard("AMD", "AMD Radeon RX 6800 XT (0x000073BF)"),
        GraphicCard("AMD", "AMD Radeon RX 7600S (0x00007480)"),
        GraphicCard("AMD", "AMD Radeon(TM) Graphics (0x00001636)"),
        GraphicCard("AMD", "AMD Radeon(TM) Graphics (0x00001638)"),
        GraphicCard("AMD", "AMD Radeon(TM) Graphics (0x00001681)"),
        GraphicCard("AMD", "AMD Radeon(TM) Vega 6 Graphics (0x000015DD)"),
        GraphicCard("Intel", "Intel(R) Arc(TM) A750 Graphics (0x000056A1)"),
        GraphicCard("Intel", "Intel(R) Arc(TM) Graphics (0x00007D55)"),
        GraphicCard("Intel", "Intel(R) HD Graphics (0x000022B1)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 4400 (0x0000041E)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 510 (0x00001902)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 520 (0x00001916)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 530 (0x00001912)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 530 (0x0000191B)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 5500 (0x00001616)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 6000 (0x00001626)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 610 (0x00005902)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 620 (0x00005916)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 630 (0x00005912)"),
        GraphicCard("Intel", "Intel(R) HD Graphics 630 (0x0000591B)"),
        GraphicCard("Intel", "Intel(R) Iris(R) Plus Graphics 640 (0x00005926)"),
        GraphicCard("Intel", "Intel(R) Iris(R) Xe Graphics (0x000046A6)"),
        GraphicCard("Intel", "Intel(R) Iris(R) Xe Graphics (0x000046A8)"),
        GraphicCard("Intel", "Intel(R) Iris(R) Xe Graphics (0x0000A7A1)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics (0x000046A3)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009A60)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009B41)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009BA4)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009BC4)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics (0x0000A720)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics (0x0000A721)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics (0x0000A78B)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 610 (0x00009BA8)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 620 (0x00003EA0)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 620 (0x00005917)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 630 (0x00003E91)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 630 (0x00003E92)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 630 (0x00003E98)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 630 (0x00009BC5)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 730 (0x00004682)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 730 (0x00004692)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 750 (0x00004C8A)"),
        GraphicCard("Intel", "Intel(R) UHD Graphics 770 (0x00004680)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GT 710 (0x0000128B)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GT 730 (0x00000F02)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GT 730 (0x00001287)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GT 1010 (0x00001D02)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 650 (0x00000FC6)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 750 (0x00001381)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 750 Ti (0x00001380)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 960 (0x00001401)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1050 (0x00001C81)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1050 Ti (0x00001C82)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1060 6GB (0x00001C03)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1070 (0x00001B81)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1650 Ti (0x00001F95)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1660 (0x00002184)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1660 SUPER (0x000021C4)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce GTX 965M (0x00001427)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 2060 (0x00001F08)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 2070 SUPER (0x00001E84)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3050 (0x00002584)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 (0x00002504)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 (0x00002544)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 Laptop GPU (0x00002520)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 Laptop GPU (0x00002560)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 Ti (0x00002414)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 Ti (0x00002489)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 Ti (0x000024C9)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3070 (0x00002484)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3070 (0x00002488)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3080 (0x00002206)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3080 Ti (0x00002208)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4050 Laptop GPU (0x000028E1)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4060 Laptop GPU (0x000028A0)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4060 Laptop GPU (0x000028E0)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4060 Ti (0x00002803)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4070 (0x00002786)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4070 Laptop GPU (0x00002820)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4070 SUPER (0x00002783)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4080 (0x00002704)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4080 SUPER (0x00002702)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4090 (0x00002684)"),
        GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4090 D (0x00002685)"),
        GraphicCard("NVIDIA", "NVIDIA Quadro P2000 (0x00001C30)"),
        )

    /** Base64 with the trailing "==" cut off, the way the web client sends dm_img_str. */
    internal fun encodeToBase64SubString(raw: String): String {
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
        return encoded.substring(0, encoded.length - 2)
    }

    internal fun forgeDevice(random: Random): Device {
        // Modern Chrome (frozen UA) on Windows 10/11 x64 only.
        val chromiumVersion = random.nextInt(8) + 130
        val card = cards[random.nextInt(cards.size)]
        return Device(
            userAgent = String.format(Locale.ROOT, USER_AGENT_TEMPLATE, "Windows NT 10.0; Win64; x64", chromiumVersion),
            webGlVersion = DEFAULT_CHROMIUM_WEBGL_VERSION,
            webGlRendererInfo =
                String.format(Locale.ROOT, ANGLE_RENDERER_INFO_TEMPLATE, card.vendor, card.model, card.vendor),
            innerWidth = 1920 - 60 - random.nextInt(60),
            innerHeight = 1080 - 90 - random.nextInt(60),
        )
    }

    @Volatile
    private var current: Device? = null

    fun requireRandomDevice(): Device =
        current ?: synchronized(this) { current ?: forgeDevice(Random.Default).also { current = it } }

    fun regenerateRandomDevice() {
        synchronized(this) { current = forgeDevice(Random.Default) }
    }
}

package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import org.schabi.newpipe.extractor.ServiceList

/**
 * Whether [serviceId] is one Flow's own danmaku overlay (DanmakuLayer, the settings toggle, Local
 * Server's /danmaku route) actually understands.
 *
 * This is deliberately a service-id check, not `ServiceInfo.MediaCapability.BULLET_COMMENTS` -
 * that capability is also declared by YouTube, but `YoutubeBulletCommentsExtractor` is a stateful
 * live-chat *poller* (`getInitialPage()` schedules a background fetch loop and returns null; a
 * fresh instance throws unless a YouTube-specific, differently-shaped `WatchDataCache` was already
 * primed by an unrelated caller), nothing like Bilibili's one-shot "list every comment" extractor
 * this overlay was actually built against. Flow already has its own, more capable pipeline for
 * YouTube live chat (`LiveChatRepository`) that doesn't go through this at all. Gating on the
 * capability would silently show a Danmaku toggle on YouTube videos that can never show anything.
 */
fun serviceSupportsBulletComments(serviceId: Int): Boolean = serviceId == BILIBILI_SERVICE_ID

package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID

/**
 * [serviceId] understood by Flow's danmaku overlay (DanmakuLayer, settings toggle, Local Server /danmaku route).
 *
 * Service-id check, not `ServiceInfo.MediaCapability.BULLET_COMMENTS`: YouTube declares it too, but `YoutubeBulletCommentsExtractor` is a stateful
 * live-chat poller (`getInitialPage()` schedules a fetch loop, returns null; fresh instance throws without a primed YouTube `WatchDataCache`),
 * unlike Bilibili's one-shot list extractor. YouTube live chat: `LiveChatRepository`. Capability gating → Danmaku toggle on YouTube that never shows anything.
 */
fun serviceSupportsBulletComments(serviceId: Int): Boolean = serviceId == BILIBILI_SERVICE_ID

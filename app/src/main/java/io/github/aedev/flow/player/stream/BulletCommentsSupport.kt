package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID

/**
 * Service understood by danmaku overlay (layer, settings toggle, /danmaku). Service-id check, not `BULLET_COMMENTS`: YouTube declares it
 * but is a live-chat poller (`LiveChatRepository`).
 */
fun serviceSupportsBulletComments(serviceId: Int): Boolean = serviceId == BILIBILI_SERVICE_ID

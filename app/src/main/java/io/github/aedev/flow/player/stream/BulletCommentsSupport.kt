package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID

/** Service understood by danmaku overlay. Service-id check, not `BULLET_COMMENTS` (YouTube declares it, live-chat poller). */
fun serviceSupportsBulletComments(serviceId: Int): Boolean = serviceId == BILIBILI_SERVICE_ID

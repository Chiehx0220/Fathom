package io.github.aedev.flow.localserver

import java.io.OutputStream

// Bilibili danmaku, async from the watch page's player. Only Bilibili has a BulletCommentsExtractor (null elsewhere): empty result.
@Throws(Exception::class)
internal fun RequestContext.handleDanmaku(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val danmaku = LocalServerSource.danmaku(dbHelper.appContext, serviceId, params.required("id"))
    reply.sendJson(DanmakuList(danmaku))
}

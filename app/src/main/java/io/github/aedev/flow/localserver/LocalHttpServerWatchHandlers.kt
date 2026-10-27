package io.github.aedev.flow.localserver

// Bilibili danmaku, async from watch page; BulletCommentsExtractor exists only for Bilibili: else empty.
internal fun RequestContext.handleDanmaku(params: Query) = DanmakuList(source.danmaku(getServiceId(params), params.required("id")))

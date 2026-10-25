package io.github.aedev.flow.localserver

// Bilibili danmaku, async from the watch page's player. Only Bilibili has a BulletCommentsExtractor (null elsewhere): empty result.
internal fun RequestContext.handleDanmaku(params: Query) = DanmakuList(source.danmaku(getServiceId(params), params.required("id")))

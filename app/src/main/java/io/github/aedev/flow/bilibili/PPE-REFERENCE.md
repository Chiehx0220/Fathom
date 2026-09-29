# PPE reference

This package ports the Bilibili service of PipePipeExtractor (PPE, GPL-3.0). Flow no longer depends
on PPE (it uses NewPipeExtractor, like upstream); the port is kept in step by hand. The copy it was
ported from is Chiehx0220/PipePipeExtractor at commit `aef9726d5b1172213066f60bc338eb4278651d61`.
Upstream (InfinityLoop1308/PipePipeExtractor) has been ported up to `c68e10e2` (v5.4.0), live rooms included.

When Bilibili changes its risk control, diff the PPE files on the right against the files on the
left, re-port only what changed, and update the commit above. PPE paths are under
`extractor/src/main/java/org/schabi/newpipe/extractor/services/bilibili/`.

| Here | PPE | Holds |
|---|---|---|
| `BilibiliCdn.kt` | `BilibiliService.java` (`isBiliBiliDownloadUrl`, `isMcdnUrl`, `pickStableStreamUrl`) | CDN hosts, required headers, M-CDN filtering |
| `BilibiliLive.kt` | `extractors/BillibiliStreamExtractor.java` (live branch), `BilibiliRecommendLiveInfoItemExtractor.java`, `BilibiliChannelExtractor.java` (live room) | Room info, HLS/FLV addresses, recommended rooms, an uploader's live room, chat token |
| `BilibiliLiveChat.kt`, `BilibiliLivePacket.kt` | `BilibiliWebSocketClient.java`, `extractors/BilibiliBulletCommentsExtractor.java`, `BilibiliLiveBulletCommentsInfoItemExtractor.java`, `BilibiliSuperChatInfoItemExtractor.java` | Chat socket, packet format, chat and super chat messages |
| `DeviceForger.kt` | `DeviceForger.java` | Forged Chrome user agent, WebGL strings, window size |
| `BilibiliSigning.kt` | `utils.java` | av/bv ids, WBI signature, app signature, dm_img telemetry |
| `BilibiliSession.kt` | `BilibiliService.java`, `utils.java` | Anonymous cookies, headers, daily WBI key |
| `BilibiliApi.kt` (video, related) | `extractors/BillibiliStreamExtractor.java`, `BilibiliRelatedInfoItemExtractor.java` | View and playurl requests, DASH streams, related videos |
| `BilibiliApi.kt` (search), `BilibiliSearchParser.kt` | `extractors/BilibiliSearchExtractor.java`, `BilibiliStreamInfoItemExtractor.java`, `BilibiliSearchResultChannelInfoItemExtractor.java` | Search rows for videos and users |
| `BilibiliDanmaku.kt` | `extractors/BilibiliBulletCommentsExtractor.java`, `BilibiliBulletCommentsInfoItemExtractor.java`, `utils.decompress` | Danmaku list, deflate XML, 2.5 s sync offset |
| `BilibiliUserSpace.kt` | `extractors/BilibiliChannelExtractor.java`, `BilibiliChannelTabExtractor.java`, `BilibiliPlaylistExtractor.java`, `BilibiliChannelInfoItem*APIExtractor.java` | Channel profile, video list in three API modes, series and seasons |
| `BilibiliComments.kt` | `extractors/BilibiliCommentExtractor.java`, `BilibiliCommentsInfoItemExtractor.java`, `linkHandler/BilibiliCommentsLinkHandlerFactory.java` | Hot-ordered comments, pinned first, and replies |

## Not ported
- Rebroadcast rooms (`live_status` 2, PPE's "round play"), bangumi and other paid content, partition playlists.
- Subtitles, login.
- Chapters (`BilibiliApi.chapters`) read the web player's own endpoint, and the popular list
  (`BilibiliApi.popular`) is Bilibili's public `web-interface/popular`; neither is in PPE.

## Differences from PPE
- Live chat runs on OkHttp's WebSocket and coroutines, not a second WebSocket library. Its heartbeat is a
  coroutine tied to the flow (PPE's loops without a delay), packets are parsed by their headers rather than by
  splitting the JSON on control characters, the auth packet asks for protover 2 (zlib) so no brotli decoder is
  needed, and it carries the anonymous session's `buvid3`. The socket is opened only while the danmaku layer is on
  screen and playing.
- The chat host comes from `getDanmuInfo`'s host list (PPE hardcodes `broadcastlv.chat.bilibili.com`, kept as the
  fallback), and that request is WBI-signed.
- Backups are filtered by the same M-CDN rule as the primary (PPE does so for audio only), so a stream's mirror
  group never contains an M-CDN node unless every candidate is one.
- Requests go through Flow's OkHttp client, not PPE's downloader, so the default user agent of the one
  request sent with an empty Cookie header (`wbi/view`) is OkHttp's. Compare this first if that request fails.
- A blocked video list moves to the next API in the same call. PPE moves on for the next call.

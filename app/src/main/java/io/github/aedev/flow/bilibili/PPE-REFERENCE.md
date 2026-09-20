# PPE reference

This package ports the Bilibili service of PipePipeExtractor (PPE, GPL-3.0). Flow no longer depends
on PPE (it uses NewPipeExtractor, like upstream); the port is kept in step by hand. The copy it was
ported from is Chiehx0220/PipePipeExtractor at commit `aef9726d5b1172213066f60bc338eb4278651d61`.

When Bilibili changes its risk control, diff the PPE files on the right against the files on the
left, re-port only what changed, and update the commit above. PPE paths are under
`extractor/src/main/java/org/schabi/newpipe/extractor/services/bilibili/`.

| Here | PPE | Holds |
|---|---|---|
| `DeviceForger.kt` | `DeviceForger.java` | Forged Chrome user agent, WebGL strings, window size |
| `BilibiliSigning.kt` | `utils.java` | av/bv ids, WBI signature, app signature, dm_img telemetry |
| `BilibiliSession.kt` | `BilibiliService.java`, `utils.java` | Anonymous cookies, headers, daily WBI key |
| `BilibiliApi.kt` (video, related) | `extractors/BillibiliStreamExtractor.java`, `BilibiliRelatedInfoItemExtractor.java` | View and playurl requests, DASH streams, related videos |
| `BilibiliApi.kt` (search), `BilibiliSearchParser.kt` | `extractors/BilibiliSearchExtractor.java`, `BilibiliStreamInfoItemExtractor.java`, `BilibiliSearchResultChannelInfoItemExtractor.java` | Search rows for videos and users |
| `BilibiliDanmaku.kt` | `extractors/BilibiliBulletCommentsExtractor.java`, `BilibiliBulletCommentsInfoItemExtractor.java`, `utils.decompress` | Danmaku list, deflate XML, 2.5 s sync offset |
| `BilibiliUserSpace.kt` | `extractors/BilibiliChannelExtractor.java`, `BilibiliChannelTabExtractor.java`, `BilibiliPlaylistExtractor.java`, `BilibiliChannelInfoItem*APIExtractor.java` | Channel profile, video list in three API modes, series and seasons |
| `BilibiliComments.kt` | `extractors/BilibiliCommentExtractor.java`, `BilibiliCommentsInfoItemExtractor.java`, `linkHandler/BilibiliCommentsLinkHandlerFactory.java` | Hot-ordered comments, pinned first, and replies |

## Not ported
- Live streams and live danmaku (WebSocket), bangumi and other paid content, partition playlists.
- Subtitles, login.
- Chapters (`BilibiliApi.chapters`) read the web player's own endpoint, and the popular list
  (`BilibiliApi.popular`) is Bilibili's public `web-interface/popular`; neither is in PPE.

## Differences from PPE
- Requests go through Flow's OkHttp client, not PPE's downloader, so the default user agent of the one
  request sent with an empty Cookie header (`wbi/view`) is OkHttp's. Compare this first if that request fails.
- A blocked video list moves to the next API in the same call. PPE moves on for the next call.

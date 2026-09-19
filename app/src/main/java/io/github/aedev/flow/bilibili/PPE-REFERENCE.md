# PPE reference map

This package is a Kotlin port of the Bilibili service in PipePipeExtractor (GPL-3.0,
InfinityLoop1308/PipePipeExtractor). Flow's copy of it lives at Chiehx0220/PipePipeExtractor.
Ported at commit `aef9726d5b1172213066f60bc338eb4278651d61`.

When Bilibili changes its risk control, diff the PPE file on the right against the file on the left,
and re-port only what changed. Bump the commit above when done.

| Here | PPE file | What it holds |
|---|---|---|
| `DeviceForger.kt` | `services/bilibili/DeviceForger.java` | Forged Chrome UA, WebGL strings, window size |
| `BilibiliSigning.kt` | `services/bilibili/utils.java` | av/bv codec, WBI mixin key + signature, dm_img_* telemetry |
| `BilibiliSession.kt` | `services/bilibili/BilibiliService.java`, `utils.java` (encWbi) | Anonymous cookies (spi, bili_ticket, buvid_fp), headers, daily mixin-key fetch |
| `BilibiliApi.kt` | `services/bilibili/extractors/BillibiliStreamExtractor.java` | view + playurl request, DASH stream parsing |

## Not ported yet
Live, bangumi/premium (`pgc/...`), search, channel, comments, danmaku, subtitles, app-signed endpoints
(`APP_KEY` / `encAppSign`), the login-cookie feature flags.

## Deliberate differences
- HTTP goes through Flow's OkHttp client, not PPE's Downloader, so the default User-Agent on the one
  request PPE sends with only an empty Cookie header (`wbi/view`) is OkHttp's, not PPE's downloader's.
  If that request starts failing, this is the first thing to compare.
- Nothing here has been run against the live Bilibili API yet.

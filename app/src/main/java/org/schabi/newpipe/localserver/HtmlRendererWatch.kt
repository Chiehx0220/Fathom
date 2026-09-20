package org.schabi.newpipe.localserver

import io.github.aedev.flow.player.stream.serviceSupportsBulletComments
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.extractor.stream.VideoStream

object HtmlRendererWatch {

    // Related-item data shared by the video and audio pages; their card sizes differ.
    private data class RelatedItemDisplay(
        val uploaderEscaped: String,
        val nameEscaped: String,
        val thumbUrl: String,
        val metaText: String,
    )

    private fun prepareRelatedItem(related: InfoItem): RelatedItemDisplay {
        var uploader: String?
        var metaText = ""
        if (related is StreamInfoItem) {
            uploader = related.uploaderName
            val viewsText = if (related.viewCount >= 0) "${HtmlRendererCommon.formatCount(related.viewCount)} views" else "Live"
            metaText = "$viewsText • ${HtmlRendererCommon.formatUploadDate(related.uploadDate, related.textualUploadDate ?: "")}"
        } else {
            uploader = related.name
        }
        if (uploader == null) uploader = ""
        return RelatedItemDisplay(
            uploaderEscaped = HtmlRendererCommon.escapeHtml(uploader),
            nameEscaped = HtmlRendererCommon.escapeHtml(related.name),
            thumbUrl = HtmlRendererCommon.getThumbnailUrl(related.thumbnailUrl),
            metaText = metaText,
        )
    }

    private fun formatChapterTimestamp(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    @JvmStatic
    fun renderWatchSkeleton(serviceId: Int, mediaUrl: String?, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, ""))
        sb.append("  <div id=\"watch-container-loader\" style=\"text-align: center; padding: 100px 0; font-family: inherit;\">\n")
          .append("    <div style=\"display: inline-block; width: 50px; height: 50px; border: 4px solid var(--search-input-bg); border-top: 4px solid var(--logo-color); border-radius: 50%; animation: spin 0.8s linear infinite;\"></div>\n")
          .append("    <div style=\"margin-top: 24px; font-size: 16px; font-weight: 500; color: var(--text-color);\">Loading video streams...</div>\n")
          .append("  </div>\n")
          .append("  <div id=\"watch-content\" style=\"display: none;\"></div>\n")
          .append("<style>\n")
          .append("  @keyframes spin {\n")
          .append("    0% { transform: rotate(0deg); }\n")
          .append("    100% { transform: rotate(360deg); }\n")
          .append("  }\n")
          .append("</style>\n")
          .append("<script>\n")
          .append("  function loadWatchContent(url) {\n")
          .append("      const loader = document.getElementById('watch-container-loader');\n")
          .append("      const content = document.getElementById('watch-content');\n")
          .append("      if (loader) loader.style.display = 'block';\n")
          .append("      if (content) content.style.display = 'none';\n")
          .append("      \n")
          // Forward serviceId, or the server falls back to YouTube for non-YouTube links.
          .append("      fetchText('/watch-content?serviceId=' + encodeURIComponent(new URLSearchParams(window.location.search).get('serviceId') || '0') + '&id=' + encodeURIComponent(url),\n")
          .append("          html => {\n")
          .append("              if (loader) loader.style.display = 'none';\n")
          .append("              if (content) {\n")
          .append("                  content.style.display = 'block';\n")
          .append("                  content.innerHTML = html;\n")
          .append("                  \n")
          .append("                  // Execute scripts inside the loaded content\n")
          .append("                  content.querySelectorAll('script').forEach(oldScript => {\n")
          .append("                      const newScript = document.createElement('script');\n")
          .append("                      Array.from(oldScript.attributes).forEach(attr => newScript.setAttribute(attr.name, attr.value));\n")
          .append("                      newScript.appendChild(document.createTextNode(oldScript.innerHTML));\n")
          .append("                      oldScript.parentNode.replaceChild(newScript, oldScript);\n")
          .append("                  });\n")
          .append("              }\n")
          .append("          },\n")
          .append("          err => {\n")
          .append("              if (loader) loader.style.display = 'none';\n")
          .append("              if (content) {\n")
          .append("                  content.style.display = 'block';\n")
          .append("                  content.innerHTML = '<div class=\"loading-placeholder\" style=\"color: #ff4b5c; border-color: rgba(255, 75, 92, 0.2);\">Failed to load video: ' + err.message + '</div>';\n")
          .append("              }\n")
          .append("          });\n")
          .append("  }\n")
          .append("  \n")
          .append("  window.loadNewVideo = function(url) {\n")
          .append("      history.pushState(null, '', '/watch?serviceId=0&id=' + encodeURIComponent(url));\n")
          .append("      loadWatchContent(url);\n")
          .append("  };\n")
          .append("  \n")
          .append("  document.addEventListener('DOMContentLoaded', () => {\n")
          .append("      const urlParams = new URLSearchParams(window.location.search);\n")
          .append("      const id = urlParams.get('id');\n")
          .append("      if (id) loadWatchContent(id);\n")
          .append("  });\n")
          .append("</script>\n")

        return HtmlRendererCommon.wrapInTemplate("Loading video...", sb.toString(), isTv, true)
    }

    /** The description as it goes into the box: plain text is escaped, and stray blank lines at the ends dropped. */
    private fun descriptionHtml(info: StreamInfo): String {
        val description = info.description ?: return "No description provided."
        val content = description.content?.trim().orEmpty().ifEmpty { return "No description provided." }
        return if (description.type == Description.PLAIN_TEXT) HtmlRendererCommon.escapeHtml(content) else content
    }

    @JvmStatic
    fun renderWatchContent(serviceId: Int, info: StreamInfo, isSubscribed: Boolean, isWatchLater: Boolean, likeState: String?, isTv: Boolean, targetQuality: String?, duration: Long): String {
        val sb = StringBuilder()
        var nextVideoUrl = ""
        var nextVideoTitle = ""
        var nextVideoThumb = ""
        if (!info.relatedItems.isNullOrEmpty()) {
            val nextItem = info.relatedItems[0]
            nextVideoUrl = "/watch?serviceId=$serviceId&id=${nextItem.url}"
            nextVideoTitle = nextItem.name ?: ""
            nextVideoThumb = HtmlRendererCommon.getThumbnailUrl(nextItem.thumbnailUrl)
        }

        val nextVideoUrlJs = HtmlRendererCommon.escapeJs(nextVideoUrl)
        val nextVideoTitleJs = HtmlRendererCommon.escapeJs(nextVideoTitle)
        val nextVideoThumbJs = HtmlRendererCommon.escapeJs(nextVideoThumb)

        val progressUrlEncoded = HtmlRendererCommon.encodeUrl(info.url)

        // SponsorBlockClient returns milliseconds; converted to seconds for initSponsorBlockMarkers(). Only skippable segments are requested.
        val sponsorSegments = SponsorBlockClient.fetchSegments(LocalHttpServer.getVideoId(info.url))
        val sponsorItemsJs = StringBuilder()
        for (seg in sponsorSegments) {
            val label = when (seg.category) {
                "sponsor" -> "贊助內容"
                "intro" -> "片頭"
                "outro" -> "片尾"
                "interaction" -> "訂閱/按讚提醒"
                "selfpromo" -> "業配置入"
                "music_offtopic" -> "非音樂片段"
                else -> continue
            }
            if (sponsorItemsJs.isNotEmpty()) sponsorItemsJs.append(",")
            sponsorItemsJs.append("{s:").append(seg.startMs / 1000.0)
                .append(",e:").append(seg.endMs / 1000.0)
                .append(",c:\"").append(seg.category).append("\"")
                .append(",l:\"").append(label).append("\"}")
        }

        // Chapter data for initChapterMarkers().
        val chapters: List<StreamSegment> = info.streamSegments ?: emptyList()
        val chaptersItemsJs = StringBuilder()
        for (chapter in chapters) {
            if (chaptersItemsJs.isNotEmpty()) chaptersItemsJs.append(",")
            chaptersItemsJs.append("{s:").append(chapter.startTimeSeconds)
                .append(",t:\"").append(HtmlRendererCommon.escapeJs(chapter.title)).append("\"}")
        }

        val danmakuUrlJs = if (!serviceSupportsBulletComments(serviceId)) {
            ""
        } else {
            "/danmaku?serviceId=$serviceId&id=${HtmlRendererCommon.encodeUrl(info.url)}"
        }

        val infoNameJs = HtmlRendererCommon.escapeJs(info.name)
        sb.append("<script>document.title = \"$infoNameJs - Fathom\";</script>\n")
        sb.append("<div class=\"container\">\n")
          .append("  <div class=\"player-container\">\n")
          .append("    <div class=\"player-layout\">\n")
          .append("      <div class=\"main-content\">\n")

        val hasVideo = info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty() || !info.hlsUrl.isNullOrEmpty()
        if (hasVideo) {
            // "Preferred Video Quality": matched by height, since an extractor resolution can carry a frame-rate suffix ("1080p60").
            val defaultQualityHeight = LocalHttpServer.getResolutionHeight(targetQuality)

            val subtitles = info.subtitles
            val trackTags = StringBuilder()
            if (subtitles != null) {
                for (sub in subtitles) {
                    val lang = sub.languageTag
                    val labelJs = HtmlRendererCommon.escapeJs(sub.displayLanguageName)
                    val isAuto = sub.isAutoGenerated
                    val subUrlEncoded = HtmlRendererCommon.encodeUrl(info.url)
                    trackTags.append("            <track kind=\"captions\" src=\"/subtitles?serviceId=$serviceId&id=$subUrlEncoded&lang=$lang&auto=$isAuto\" srclang=\"$lang\" label=\"$labelJs\">\n")
                }
            }

            val infoUrlEncoded = HtmlRendererCommon.encodeUrl(info.url)

            val playerTitleEscaped = HtmlRendererCommon.escapeHtml(info.name)
            run {
                // .player-wrapper is the <media-player> itself; the mini-player logic toggles position:fixed on it.
                sb.append("        <media-player id=\"player\" class=\"player-wrapper\" title=\"$playerTitleEscaped\" artist=\"${HtmlRendererCommon.escapeHtml(info.uploaderName)}\" storage=\"fathom-player\" key-target=\"document\" crossorigin playsinline>\n")
                  .append("          <media-provider>\n")
                  .append(trackTags.toString())
                  .append("          </media-provider>\n")
                  // Cover until the first frame; served same-origin because the player loads it in CORS mode (LocalHttpServerThumbnails.kt).
                  .append(sameOriginImageUrl(HtmlRendererCommon.getThumbnailUrl(info.thumbnails))?.let { "          <media-poster class=\"vds-poster\" src=\"${HtmlRendererCommon.escapeHtml(it)}\" alt=\"\"></media-poster>\n" } ?: "")
                  .append(
                      if (info.previewFrames.bestForSeekBar() != null) {
                          "          <media-video-layout thumbnails=\"/thumbnails?serviceId=$serviceId&id=$infoUrlEncoded\"></media-video-layout>\n"
                      } else {
                          "          <media-video-layout></media-video-layout>\n"
                      },
                  )
                  .append(if (serviceSupportsBulletComments(serviceId)) "          <div class=\"danmaku-layer\" id=\"danmaku-layer\"></div>\n" else "")
                  .append("          <div class=\"double-tap-indicator left\" id=\"double-tap-left\">\n")
                  .append("            <svg viewBox=\"0 0 24 24\"><path d=\"M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z\"/></svg>\n")
                  .append("            <div class=\"double-tap-text\">-10s</div>\n")
                  .append("          </div>\n")
                  .append("          <div class=\"double-tap-indicator right\" id=\"double-tap-right\">\n")
                  .append("            <svg viewBox=\"0 0 24 24\"><path d=\"M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z\"/></svg>\n")
                  .append("            <div class=\"double-tap-text\">+10s</div>\n")
                  .append("          </div>\n")
                  .append("          <div class=\"volume-hud\" id=\"volume-hud-indicator\">\n")
                  .append("            <span id=\"volume-hud-icon\">🔊</span>\n")
                  .append("            <span id=\"volume-hud-text\">100%</span>\n")
                  .append("          </div>\n")
                  .append("          <div class=\"up-next-overlay\" id=\"autoplay-overlay\">\n")
                  .append("            <div class=\"up-next-title\">Up Next</div>\n")
                  .append("            <div class=\"up-next-name\" id=\"autoplay-next-title\"></div>\n")
                  .append("            <img class=\"up-next-thumb\" id=\"autoplay-next-thumb\" src=\"\" alt=\"\">\n")
                  .append("            <div class=\"up-next-circle\">\n")
                  .append("              <svg width=\"56\" height=\"56\">\n")
                  .append("                <circle cx=\"28\" cy=\"28\" r=\"22\" class=\"up-next-circle-bg\" />\n")
                  .append("                <circle cx=\"28\" cy=\"28\" r=\"22\" class=\"up-next-circle-val\" id=\"autoplay-progress-circle\" />\n")
                  .append("              </svg>\n")
                  .append("            </div>\n")
                  .append("            <div class=\"up-next-btn-row\">\n")
                  .append("              <button class=\"up-next-btn up-next-btn-play\" id=\"autoplay-play-now\">Play Now</button>\n")
                  .append("              <button class=\"up-next-btn up-next-btn-cancel\" id=\"autoplay-cancel\">Cancel</button>\n")
                  .append("            </div>\n")
                  .append("          </div>\n")
                  .append("          <button class=\"sponsor-skip-btn\" id=\"sponsor-skip-btn\"><span class=\"material-symbols-rounded\" style=\"font-size:18px;\">fast_forward</span><span id=\"sponsor-skip-label\"></span></button>\n")
                  .append("          <div class=\"mini-player-drag-handle\" id=\"mini-player-drag-handle\" title=\"Drag to move\"><span class=\"material-symbols-rounded\">open_with</span></div>\n")
                  .append("          <button class=\"mini-player-close-btn\" id=\"mini-player-close-btn\" title=\"Close\"><span class=\"material-symbols-rounded\">close</span></button>\n")
                  .append("        </media-player>\n")
                  // Keeps the layout height when .player-wrapper becomes fixed.
                  .append("        <div id=\"player-space-holder\" style=\"height:0;\"></div>\n")
                  // Static observation target for the mini-player: the wrapper itself moves, which would retrigger the observer.
                  .append("        <div id=\"mini-player-sentinel\" style=\"height:1px;\"></div>\n")

                // Only the danmaku toggle lives here; quality and audio language come from Vidstack's settings menu.
                if (serviceSupportsBulletComments(serviceId)) {
                    sb.append("        <div class=\"player-controls-row\">\n")
                      .append("          <button type=\"button\" class=\"action-pill-btn danmaku-toggle-btn\" id=\"danmaku-toggle-btn\" title=\"彈幕開關\"><span class=\"material-symbols-rounded\" style=\"font-size:18px;\">chat_bubble</span>Danmaku</button>\n")
                      .append("        </div>\n")
                }

                if (chapters.isNotEmpty()) {
                    val fallbackChapterThumb = HtmlRendererCommon.getThumbnailUrl(info.thumbnails)
                    val firstChapterTitleEscaped = HtmlRendererCommon.escapeHtml(chapters[0].title)
                    // Collapsed by default; toggleChaptersSection() opens the full list.
                    sb.append("        <div class=\"chapters-section\">\n")
                      .append("          <div class=\"chapters-header\" onclick=\"toggleChaptersSection()\">\n")
                      .append("            <h3 class=\"comment-count\">Chapters</h3>\n")
                      .append("            <div class=\"chapters-header-right\">\n")
                      .append("              <span id=\"current-chapter-label\">$firstChapterTitleEscaped</span>\n")
                      .append("              <span class=\"material-symbols-rounded chapters-toggle-icon\" id=\"chapters-toggle-icon\">expand_more</span>\n")
                      .append("            </div>\n")
                      .append("          </div>\n")
                      .append("          <div class=\"chapters-list\" id=\"chapters-list\" style=\"display:none;\">\n")
                    for (chapter in chapters) {
                        val thumb = HtmlRendererCommon.getThumbnailUrl(
                            chapter.previewUrl?.takeIf { it.isNotBlank() } ?: fallbackChapterThumb,
                        )
                        val titleEscaped = HtmlRendererCommon.escapeHtml(chapter.title)
                        sb.append("            <div class=\"chapter-item\" onclick=\"seekToChapter(${chapter.startTimeSeconds})\">\n")
                          .append("              <img class=\"chapter-thumb\" src=\"$thumb\" loading=\"lazy\">\n")
                          .append("              <div class=\"chapter-item-body\">\n")
                          .append("                <span class=\"chapter-item-time\">${formatChapterTimestamp(chapter.startTimeSeconds)}</span>\n")
                          .append("                <span class=\"chapter-item-title\">$titleEscaped</span>\n")
                          .append("              </div>\n")
                          .append("            </div>\n")
                    }
                    sb.append("          </div>\n")
                      .append("        </div>\n")
                }

                sb.append("        <script>\n")
                  .append("            (function() {\n")
                  .append("                const player = document.getElementById('player');\n")
                  .append("                window.videoPlayer = player;\n")
                  // fullscreen-change detail is a boolean.
                  .append("                player.addEventListener('fullscreen-change', (e) => {\n")
                  .append("                    if (e.detail) {\n")
                  .append("                        if (screen.orientation && screen.orientation.lock) {\n")
                  .append("                            screen.orientation.lock('landscape').catch(e => {});\n")
                  .append("                        }\n")
                  .append("                    } else {\n")
                  .append("                        if (screen.orientation && screen.orientation.unlock) {\n")
                  .append("                            screen.orientation.unlock();\n")
                  .append("                        }\n")
                  .append("                    }\n")
                  .append("                });\n")
                  // Buffer ahead of dash.js's default so a stall on a slow link does not drain it; keep played data for small rewinds.
                  .append("                player.addEventListener('provider-change', (event) => {\n")
                  .append("                    const provider = event.detail;\n")
                  .append("                    if (provider && provider.type === 'dash') {\n")
                  .append("                        provider.config = { streaming: { buffer: {\n")
                  .append("                            stableBufferTime: 60,\n")
                  .append("                            bufferTimeAtTopQuality: 90,\n")
                  .append("                            bufferTimeAtTopQualityLongForm: 120,\n")
                  .append("                            bufferToKeep: 30,\n")
                  .append("                            avoidCurrentTimeRangePruning: true\n")
                  .append("                        } } };\n")
                  .append("                    }\n")
                  .append("                });\n")
                  .append("                player.src = { src: '/manifest?serviceId=$serviceId&id=$infoUrlEncoded', type: 'application/dash+xml' };\n")
                  .append("                player.addEventListener('can-play', () => {\n")
                  .append("                    player.play().catch(err => console.error(err));\n")
                  .append("                }, { once: true });\n")
                  .append("                \n")
                  .append("                const streamDuration = $duration;\n")
                  .append("                \n")
                  .append("                // Extract initial start time if available\n")
                  .append("                const urlParams = new URLSearchParams(window.location.search);\n")
                  .append("                let initialStartTime = parseFloat(urlParams.get('start_time')) || 0;\n")
                  .append("                if (initialStartTime > 0) {\n")
                  .append("                    player.addEventListener('can-play', () => {\n")
                  .append("                        player.currentTime = initialStartTime;\n")
                  .append("                    }, { once: true });\n")
                  .append("                }\n")
                  .append("                \n")
                  // currentTime/duration are direct properties of <media-player>.
                  .append("                window.seekVideo = (delta) => {\n")
                  .append("                    const targetTime = Math.max(0, Math.min(streamDuration || player.duration || 0, player.currentTime + delta));\n")
                  .append("                    player.currentTime = targetTime;\n")
                  .append("                };\n")
                  .append("                \n")
                  // Apply "Preferred Video Quality" once dash.js has filled player.qualities. Setting an individual quality's .selected is the working API; player.qualities.selected is a no-op.
                  .append("                const preferredQualityHeight = $defaultQualityHeight;\n")
                  .append("                if (preferredQualityHeight > 0) {\n")
                  .append("                    let appliedDefaultQuality = false;\n")
                  .append("                    player.addEventListener('qualities-change', () => {\n")
                  .append("                        if (appliedDefaultQuality) return;\n")
                  .append("                        const match = Array.from(player.qualities).find(q => q.height === preferredQualityHeight);\n")
                  .append("                        if (match) {\n")
                  .append("                            match.selected = true;\n")
                  .append("                            appliedDefaultQuality = true;\n")
                  .append("                        }\n")
                  .append("                    });\n")
                  .append("                }\n")
                  .append("                \n")
                  .append("                initAdvancedPlayerControls(player, \"$nextVideoUrlJs\", \"$nextVideoTitleJs\", \"$nextVideoThumbJs\");\n")
                  .append(if (sponsorItemsJs.isEmpty()) "" else "                initSponsorBlockMarkers(player, [$sponsorItemsJs]);\n")
                  .append(if (chaptersItemsJs.isEmpty()) "" else "                initChapterMarkers(player, [$chaptersItemsJs]);\n")
                  .append("                initMiniPlayer(player);\n")
                  .append(if (danmakuUrlJs.isEmpty()) "" else "                initDanmakuOverlay(player, \"$danmakuUrlJs\");\n")
                  .append("                initFullscreenLetterbox(player);\n")
                  .append("                initWatchProgressReporting(player, \"$progressUrlEncoded\", $serviceId);\n")
                  .append(if (serviceId == 0) "                initDownloadButton(\"$progressUrlEncoded\", $serviceId);\n" else "")
                  .append("            })();\n")
                  .append("        </script>\n")
            }
        } else {
            var audioMime = "audio/mpeg"
            if (info.audioStreams.isNotEmpty()) {
                val format = info.audioStreams[0].format
                if (format != null) {
                    audioMime = format.mimeType
                }
            }
            val thumbUrl = HtmlRendererCommon.getThumbnailUrl(info.thumbnails)
            val infoUrlEncoded = HtmlRendererCommon.encodeUrl(info.url)
            sb.append("        <div class=\"media-info\">\n")
              .append("          <img src=\"$thumbUrl\" style=\"width:100%; max-height:300px; object-fit:contain; border-radius:8px; background:#000;\">\n")
              .append("        </div>\n")
              .append("        <audio id=\"audio-player\" controls autoplay class=\"native-audio\">\n")
              .append("          <source src=\"/stream?serviceId=$serviceId&id=$infoUrlEncoded\" type=\"$audioMime\">\n")
              .append("          Your browser does not support the HTML5 audio tag.\n")
              .append("        </audio>\n")
              .append("        <script>\n")
              .append("            (function() {\n")
              .append("                const audio = document.getElementById('audio-player');\n")
              .append("                const streamDuration = $duration;\n")
              .append("                if (audio && streamDuration > 0) {\n")
              .append("                    const setDuration = () => {\n")
              .append("                        if (Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'duration')) {\n")
              .append("                           try {\n")
              .append("                               Object.defineProperty(audio, 'duration', { value: streamDuration, configurable: true });\n")
              .append("                               audio.dispatchEvent(new Event('durationchange'));\n")
              .append("                           } catch(e) { console.error('Failed to override audio duration:', e); }\n")
              .append("                        }\n")
              .append("                    };\n")
              .append("                    audio.addEventListener('loadedmetadata', setDuration);\n")
              .append("                    if (audio.readyState >= 1) setDuration();\n")
              .append("                }\n")
              .append("            })();\n")
              .append("        </script>\n")
        }

        val formattedViews = if (info.viewCount >= 0) "${HtmlRendererCommon.formatCount(info.viewCount)} views" else "Unknown views"
        val uploadDate = HtmlRendererCommon.formatUploadDate(info.uploadDate, info.textualUploadDate ?: "Unknown date")
        val subsText = if (info.uploaderSubscriberCount >= 0) "${HtmlRendererCommon.formatCount(info.uploaderSubscriberCount)} subscribers" else ""
        val infoNameEscaped = HtmlRendererCommon.escapeHtml(info.name)
        val uploaderAvatar = HtmlRendererCommon.getThumbnailUrl(info.uploaderAvatars)
        val uploaderNameEscaped = HtmlRendererCommon.escapeHtml(info.uploaderName)
        val uploaderUrlEncoded = HtmlRendererCommon.encodeUrl(info.uploaderUrl)

        sb.append("        <div class=\"media-info\">\n")
          .append("          <h1 class=\"media-title\">$infoNameEscaped</h1>\n")

        sb.append("          <div class=\"uploader-profile\">\n")
          .append("            <div class=\"uploader-main\">\n")
          .append("              <img class=\"uploader-avatar\" src=\"$uploaderAvatar\">\n")
          .append("              <div class=\"uploader-info\">\n")
          .append("                <a href=\"/channel?serviceId=$serviceId&id=$uploaderUrlEncoded\" class=\"uploader-name\">$uploaderNameEscaped</a>\n")
          .append("                <span class=\"uploader-subs\">$subsText</span>\n")
          .append("              </div>\n")

        val infoUrlEncodedForSub = HtmlRendererCommon.encodeUrl(info.url)
        sb.append(HtmlRendererCommon.renderSubscribeButton(info.uploaderUrl, info.uploaderName, uploaderAvatar, infoUrlEncodedForSub, isSubscribed))
        sb.append("            </div>\n")

        sb.append("            <div class=\"action-buttons-group\">\n")
          .append("              ${HtmlRendererCommon.renderLikeDislikePill(info, likeState)}")
          .append("              <button type=\"button\" onclick=\"if (window.NewPipeApp &amp;&amp; window.NewPipeApp.enterPip) { window.NewPipeApp.enterPip(); } else if (document.pictureInPictureEnabled &amp;&amp; document.querySelector('video')) { document.querySelector('video').requestPictureInPicture(); }\" class=\"action-pill-btn\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"16\" height=\"16\" style=\"margin-right:6px;\"><path d=\"M19 11h-8v6h8v-6zm4-8H1c-.55 0-1 .45-1 1v16c0 .55.45 1 1 1h22c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1zm-2 16H3V5h18v14z\"/></svg>Pop-up</button>\n")
          .append("              <a href=\"/audio?serviceId=$serviceId&id=$infoUrlEncodedForSub\" class=\"action-pill-btn\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"16\" height=\"16\" style=\"margin-right:6px;\"><path d=\"M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z\"/></svg>Audio Only</a>\n")
          .append("              <button type=\"button\" onclick=\"shareLink('${HtmlRendererCommon.escapeJs(info.url)}', '$infoNameJs')\" class=\"action-pill-btn\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"16\" height=\"16\" style=\"margin-right:6px;\"><path d=\"M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z\"/></svg>Share</button>\n")
        // Hands off to Flow's downloader (YouTube only: it has no header handling for other CDNs); initDownloadButton() fills in the state.
        if (serviceId == 0) {
            sb.append("              <button type=\"button\" id=\"download-btn\" class=\"action-pill-btn\"><span class=\"material-symbols-rounded\" style=\"font-size:18px;\">download</span>Download</button>\n")
        }
        sb.append("              ${HtmlRendererCommon.renderWatchLaterButton(info, serviceId, isWatchLater)}")

        sb.append("            </div>\n")
        sb.append("          </div>\n")

        // No whitespace between tags and text: the box is white-space:pre-wrap.
        sb.append("          <div class=\"media-description\">")
          .append("<div style=\"font-weight:700; font-size:13.5px; margin-bottom:8px; color:var(--text-color);\">$formattedViews &nbsp;•&nbsp; $uploadDate</div>")
          .append(descriptionHtml(info))
          .append("</div>\n")
          .append("        </div>\n")

        sb.append("        <div class=\"comments-section\">\n")
          .append("          <h3 class=\"comment-count\">💬 Comments</h3>\n")
          .append("          <div id=\"comments-loader\" style=\"text-align:center; padding:24px 0;\">\n")
          .append("            <div style=\"display:inline-block; width:28px; height:28px; border:3px solid var(--search-input-bg); border-top:3px solid var(--logo-color); border-radius:50%; animation:comments-spin 0.8s linear infinite;\"></div>\n")
          .append("          </div>\n")
          .append("          <div id=\"comments-list\" style=\"display:none;\"></div>\n")
          .append("          <style>@keyframes comments-spin { 0% { transform:rotate(0deg); } 100% { transform:rotate(360deg); } }</style>\n")
          .append("          <script>\n")
          // btn.parentElement instead of an id: this markup repeats per reply thread.
          .append("            window.loadMoreComments = function(btn, nextPage, svcId, videoUrl, isReplies) {\n")
          .append("                const wrapper = btn.parentElement;\n")
          .append("                btn.textContent = 'Loading...';\n")
          .append("                btn.style.pointerEvents = 'none';\n")
          .append("                const ctx = isReplies ? '&context=replies' : '';\n")
          .append("                fetchText('/comments?serviceId=' + svcId + '&id=' + encodeURIComponent(videoUrl) + '&nextPage=' + encodeURIComponent(nextPage) + ctx,\n")
          .append("                    html => { if (wrapper) wrapper.outerHTML = html; },\n")
          .append("                    () => { btn.textContent = 'Failed to load. Tap to retry'; btn.style.pointerEvents = 'auto'; });\n")
          .append("            };\n")
          // Replies reuse /comments; fetched once per thread (dataset.loaded), later clicks toggle the "expanded" class.
          .append("            window.toggleReplies = function(btn, repliesPage, svcId, videoUrl) {\n")
          .append("                const container = btn.nextElementSibling;\n")
          .append("                const expanded = btn.classList.toggle('expanded');\n")
          .append("                if (!expanded) { container.classList.remove('expanded'); return; }\n")
          .append("                container.classList.add('expanded');\n")
          .append("                if (container.dataset.loaded === '1') return;\n")
          .append("                fetchText('/comments?serviceId=' + svcId + '&id=' + encodeURIComponent(videoUrl) + '&nextPage=' + encodeURIComponent(repliesPage) + '&context=replies',\n")
          .append("                    html => { container.innerHTML = html; container.dataset.loaded = '1'; },\n")
          .append("                    () => { container.innerHTML = '<div class=\"loading-placeholder\">Failed to load replies.</div>'; });\n")
          .append("            };\n")
          .append("            (function() {\n")
          .append("                fetchText('/comments?serviceId=$serviceId&id=$infoUrlEncodedForSub',\n")
          .append("                    html => {\n")
          .append("                        const loader = document.getElementById('comments-loader');\n")
          .append("                        const list = document.getElementById('comments-list');\n")
          .append("                        if (list) { list.innerHTML = html; list.style.display = 'block'; }\n")
          .append("                        if (loader) loader.style.display = 'none';\n")
          .append("                    },\n")
          .append("                    () => {\n")
          .append("                        const loader = document.getElementById('comments-loader');\n")
          .append("                        if (loader) loader.innerHTML = '<div class=\"loading-placeholder\">Failed to load comments.</div>';\n")
          .append("                    });\n")
          .append("            })();\n")
          .append("          </script>\n")
          .append("        </div>\n")
          .append("      </div>\n")

        sb.append("      <div class=\"sidebar\">\n")
          .append("        <h3 style=\"font-size: 16px; font-weight: 700; margin-bottom: 16px;\">Related Content</h3>\n")
        for (related in info.relatedItems) {
            val item = prepareRelatedItem(related)

            sb.append("        <div class=\"card\" style=\"margin-bottom:8px; flex-direction:row; gap:8px; height:94px; background:transparent; border:none; box-shadow:none; min-width:0; overflow:hidden;\">\n")
              .append("          <a href=\"/watch?serviceId=$serviceId&id=${related.url}\" style=\"flex-shrink:0; width:168px; height:94px; border-radius:8px; overflow:hidden; background:var(--card-thumbnail-bg);\">\n")
              .append("            <img src=\"${item.thumbUrl}\" style=\"width:100%; height:100%; object-fit:cover; flex-shrink:0;\">\n")
              .append("          </a>\n")
              .append("          <div class=\"card-details\" style=\"padding:0; display:flex; flex-direction:column; justify-content:flex-start; min-width:0; flex-grow:1; overflow:hidden;\">\n")
              .append("            <a href=\"/watch?serviceId=$serviceId&id=${related.url}\" class=\"card-title\" style=\"font-size:14px; font-weight:500; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; line-height:1.2; margin-bottom:4px; word-break:break-word; overflow-wrap:break-word;\">${item.nameEscaped}</a>\n")
              .append("            <span class=\"card-meta\" style=\"font-size:12px; line-height:1.4;\">\n")
              .append("              <span class=\"card-uploader\">${item.uploaderEscaped}</span>\n")
            if (item.metaText.isNotEmpty()) {
                sb.append("              <span>${item.metaText}</span>\n")
            }
            sb.append("            </span>\n")
              .append("          </div>\n")
              .append("        </div>\n")
        }
        sb.append("      </div>\n")
        sb.append("    </div>\n")
          .append("  </div>\n")
          .append("</div>\n")

        return sb.toString()
    }

    @JvmStatic
    fun renderAudioWatch(serviceId: Int, info: StreamInfo, isSubscribed: Boolean, isWatchLater: Boolean, likeState: String?, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, "", "audio"))

        val formattedViews = if (info.viewCount >= 0) "${HtmlRendererCommon.formatCount(info.viewCount)} views" else "Unknown views"
        val uploadDate = HtmlRendererCommon.formatUploadDate(info.uploadDate, info.textualUploadDate ?: "Unknown date")
        val infoNameEscaped = HtmlRendererCommon.escapeHtml(info.name)
        val infoNameJs = HtmlRendererCommon.escapeJs(info.name)
        val infoUrlJs = HtmlRendererCommon.escapeJs(info.url)
        val infoUrlEncoded = HtmlRendererCommon.encodeUrl(info.url)
        val uploaderUrlEncoded = HtmlRendererCommon.encodeUrl(info.uploaderUrl)
        val uploaderNameEscaped = HtmlRendererCommon.escapeHtml(info.uploaderName)
        val uploaderNameJs = HtmlRendererCommon.escapeJs(info.uploaderName)

        val posterUrl = HtmlRendererCommon.getThumbnailUrl(info.thumbnails)
        var audioMime = "audio/mpeg"
        val audioStreamsForMime = info.audioStreams
        if (!audioStreamsForMime.isNullOrEmpty()) {
            val format = audioStreamsForMime[0].format
            if (format != null) {
                audioMime = format.mimeType
            }
        }

        sb.append("<div class=\"container\">\n")
          .append("  <div class=\"player-container\">\n")
          .append("    <div class=\"player-layout\">\n")
          .append("      <div class=\"main-content\">\n")
          .append("        <div class=\"audio-player-card\" style=\"display:flex; flex-direction:column; align-items:center; background:var(--card-bg); border-radius:24px; padding:32px 24px; border:1px solid var(--card-border); box-shadow:0 8px 24px rgba(0,0,0,0.12); text-align:center;\">\n")
          .append("          <div style=\"position:relative; width:240px; height:240px; margin-bottom:24px;\">\n")
          .append("            <img id=\"audio-cover\" src=\"$posterUrl\" style=\"width:100%; height:100%; border-radius:20px; object-fit:cover; box-shadow:0 8px 20px rgba(0,0,0,0.3); transition:transform 0.5s ease;\">\n")
          .append("            <div style=\"position:absolute; bottom:12px; right:12px; background:rgba(0,0,0,0.7); color:#fff; padding:4px 10px; border-radius:20px; font-size:12px; font-weight:600;\">🎵 Audio Only</div>\n")
          .append("          </div>\n")
          .append("          <h1 class=\"media-title\" style=\"font-size:22px; font-weight:700; margin-bottom:8px;\">$infoNameEscaped</h1>\n")
          .append("          <a href=\"/channel?serviceId=$serviceId&id=$uploaderUrlEncoded\" style=\"font-size:15px; color:var(--logo-color, #6750A4); font-weight:600; margin-bottom:20px;\">$uploaderNameEscaped</a>\n")
          // Vidstack audio layout. The element keeps id="audio-player" and the media API the native-app sync below uses.
          .append("          <media-player id=\"audio-player\" class=\"audio-player\" view-type=\"audio\" title=\"$infoNameEscaped\" artist=\"$uploaderNameEscaped\" storage=\"fathom-player\" key-target=\"document\" crossorigin playsinline>\n")
          .append("            <media-provider></media-provider>\n")
          .append("            <media-audio-layout></media-audio-layout>\n")
          .append("          </media-player>\n")
          .append("          <script>\n")
          .append("            (function() {\n")
          .append("              const audio = document.getElementById('audio-player');\n")
          .append("              const cover = document.getElementById('audio-cover');\n")
          // The type is given explicitly: Vidstack cannot infer it from the extension-less URL.
          .append("              audio.src = { src: '/stream?serviceId=$serviceId&id=$infoUrlEncoded', type: '$audioMime' };\n")
          .append("              audio.addEventListener('can-play', () => audio.play().catch(() => {}), { once: true });\n")
          .append(sameOriginImageUrl(posterUrl)?.let { "              audio.poster = '${HtmlRendererCommon.escapeJs(it)}';\n" } ?: "")
          .append("              const audioStreamUrl = window.location.origin + '/stream?serviceId=$serviceId&id=' + encodeURIComponent('$infoUrlJs');\n")
          .append("              const titleText = '$infoNameJs';\n")
          .append("              const artistText = '$uploaderNameJs';\n")
          .append("              if (audio) {\n")
          .append("                audio.addEventListener('play', () => {\n")
          .append("                  if(cover) cover.style.transform = 'scale(1.04)';\n")
          .append("                  if (window.NewPipeApp && window.NewPipeApp.resumeNativeAudio) {\n")
          .append("                    window.NewPipeApp.resumeNativeAudio();\n")
          .append("                  }\n")
          .append("                });\n")
          .append("                audio.addEventListener('pause', () => {\n")
          .append("                  if(cover) cover.style.transform = 'scale(1)';\n")
          .append("                  if (window.NewPipeApp && window.NewPipeApp.pauseNativeAudio) {\n")
          .append("                    window.NewPipeApp.pauseNativeAudio();\n")
          .append("                  }\n")
          .append("                });\n")
          .append("                audio.addEventListener('seeked', () => {\n")
          .append("                  if (window.NewPipeApp && window.NewPipeApp.seekNativeAudio) {\n")
          .append("                    window.NewPipeApp.seekNativeAudio(Math.floor(audio.currentTime * 1000));\n")
          .append("                  }\n")
          .append("                });\n")
          .append("              }\n")
          .append("              if (window.NewPipeApp && window.NewPipeApp.playNativeAudio) {\n")
          .append("                if (audio) {\n")
          .append("                  audio.muted = true;\n")
          .append("                  setInterval(() => {\n")
          .append("                    if (window.NewPipeApp.getNativeAudioPosition) {\n")
          .append("                      const nativePosSec = window.NewPipeApp.getNativeAudioPosition() / 1000.0;\n")
          .append("                      if (nativePosSec > 0 && Math.abs(audio.currentTime - nativePosSec) > 1.5) {\n")
          .append("                        audio.currentTime = nativePosSec;\n")
          .append("                      }\n")
          .append("                    }\n")
          .append("                  }, 1000);\n")
          .append("                }\n")
          .append("                window.NewPipeApp.playNativeAudio(audioStreamUrl, titleText, artistText);\n")
          .append("              }\n")
          .append("            })();\n")
          .append("          </script>\n")
          .append("          <div class=\"action-buttons-group\" style=\"justify-content:center; flex-wrap:wrap; gap:10px;\">\n")
          .append("            <a href=\"/watch?serviceId=$serviceId&id=$infoUrlEncoded&amp;force_video=true\" class=\"action-pill-btn\" style=\"background-color:var(--logo-color, #6750A4); color:#fff;\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"16\" height=\"16\" style=\"margin-right:6px;\"><path d=\"M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14zM9 8l7 4-7 4V8z\"/></svg>📺 Video Mode</a>\n")
          .append("            ${HtmlRendererCommon.renderLikeDislikePill(info, likeState, includeDislike = false)}")

        val uploaderAvatar = HtmlRendererCommon.getThumbnailUrl(info.uploaderAvatars)
        val audioBackUrl = HtmlRendererCommon.encodeUrl("/audio?serviceId=$serviceId&id=${info.url}")
        sb.append(HtmlRendererCommon.renderSubscribeButton(info.uploaderUrl, info.uploaderName, uploaderAvatar, audioBackUrl, isSubscribed))
        sb.append("            ${HtmlRendererCommon.renderWatchLaterButton(info, serviceId, isWatchLater)}")

        sb.append("          </div>\n")
        sb.append("        </div>\n")

        sb.append("        <div class=\"media-description\" style=\"margin-top:20px;\">")
          .append("<div style=\"font-weight:700; font-size:13.5px; margin-bottom:8px; color:var(--text-color);\">$formattedViews &nbsp;•&nbsp; $uploadDate</div>")
          .append(descriptionHtml(info))
          .append("</div>\n")
          .append("      </div>\n")

        sb.append("      <div class=\"sidebar\">\n")
          .append("        <h3 style=\"font-size:16px; font-weight:700; margin-bottom:16px;\">Up Next</h3>\n")
        for (related in info.relatedItems) {
            val item = prepareRelatedItem(related)

            sb.append("        <div class=\"card\" style=\"margin-bottom:8px; flex-direction:row; gap:8px; height:94px; background:transparent; border:none; box-shadow:none;\">\n")
              .append("          <a href=\"/audio?serviceId=$serviceId&id=${related.url}\" style=\"flex-shrink:0; width:120px; height:80px; border-radius:12px; overflow:hidden; background:var(--card-thumbnail-bg);\">\n")
              .append("            <img src=\"${item.thumbUrl}\" style=\"width:100%; height:100%; object-fit:cover;\">\n")
              .append("          </a>\n")
              .append("          <div class=\"card-details\" style=\"padding:0; display:flex; flex-direction:column; justify-content:flex-start; min-width:0; flex-grow:1;\">\n")
              .append("            <a href=\"/audio?serviceId=$serviceId&id=${related.url}\" class=\"card-title\" style=\"font-size:14px; font-weight:500; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; line-height:1.2; margin-bottom:4px;\">${item.nameEscaped}</a>\n")
              .append("            <span class=\"card-meta\" style=\"font-size:12px; line-height:1.4;\">\n")
              .append("              <span class=\"card-uploader\">${item.uploaderEscaped}</span>\n")
            if (item.metaText.isNotEmpty()) {
                sb.append("              <span>${item.metaText}</span>\n")
            }
            sb.append("            </span>\n")
              .append("          </div>\n")
              .append("        </div>\n")
        }
        sb.append("      </div>\n")
        sb.append("    </div>\n")
        sb.append("  </div>\n")
        sb.append("</div>\n")

        return HtmlRendererCommon.wrapInTemplate("Audio: ${info.name}", sb.toString(), isTv, true)
    }

    // Bare HTML fragment injected via innerHTML, for pagination and reply threads. The load-more wrapper is a class because it repeats per thread.
    @JvmStatic
    fun renderComments(serviceId: Int, videoUrl: String, items: List<CommentsInfoItem>, nextPage: Page?, isTv: Boolean, isReplies: Boolean = false): String {
        val sb = StringBuilder()

        // Always ends with the load-more wrapper, the anchor loadMoreComments() replaces.
        if (items.isEmpty()) {
            sb.append("<div class=\"loading-placeholder\">No comments yet.</div>\n")
        }

        val videoUrlJs = HtmlRendererCommon.escapeJs(videoUrl)

        for (item in items) {
            val authorEscaped = HtmlRendererCommon.escapeHtml(item.uploaderName)
            // Description.content is HTML; rendered unescaped.
            val commentTextHtml = item.commentText.content ?: ""
            val timeText = HtmlRendererCommon.formatUploadDate(item.uploadDate, item.textualUploadDate ?: "")
            val likeCountText = if (item.likeCount > 0) HtmlRendererCommon.formatCount(item.likeCount.toLong()) else ""

            sb.append("<div class=\"comment\">\n")
            if (HtmlRendererCommon.hasThumbnail(item.uploaderAvatarUrl)) {
                val avatarUrl = HtmlRendererCommon.getThumbnailUrl(item.uploaderAvatarUrl)
                sb.append("  <img class=\"comment-avatar\" src=\"$avatarUrl\">\n")
            } else {
                // Extractor limitation: fall back to a colored initial.
                val avatarBg = HtmlRendererCommon.avatarColorFor(item.uploaderName ?: "")
                val initial = HtmlRendererCommon.avatarInitial(item.uploaderName)
                sb.append("  <div class=\"comment-avatar\" style=\"display:flex; background-color:$avatarBg; color:#ffffff; font-weight:700; align-items:center; justify-content:center;\">$initial</div>\n")
            }
            sb.append("  <div class=\"comment-details\">\n")
              .append("    <div class=\"comment-header\">\n")
              .append("      <span class=\"comment-author\">$authorEscaped")
            if (item.isUploaderVerified) {
                sb.append("<span class=\"material-symbols-rounded comment-verified\" title=\"Verified\">check_circle</span>")
            }
            sb.append("</span>\n")
              .append("      <span class=\"comment-time\">$timeText</span>\n")
              .append("    </div>\n")

            if (item.isPinned) {
                sb.append("    <div class=\"comment-pinned\"><span class=\"material-symbols-rounded\">push_pin</span>Pinned</div>\n")
            }

            sb.append("    <div class=\"comment-text\">$commentTextHtml</div>\n")

            if (likeCountText.isNotEmpty() || item.isHeartedByUploader) {
                sb.append("    <div class=\"comment-meta\">\n")
                if (likeCountText.isNotEmpty()) {
                    sb.append("      <span class=\"comment-like\"><span class=\"material-symbols-rounded\">thumb_up</span>$likeCountText</span>\n")
                }
                if (item.isHeartedByUploader) {
                    sb.append("      <span class=\"comment-hearted\" title=\"Hearted by creator\"><span class=\"material-symbols-rounded\">favorite</span></span>\n")
                }
                sb.append("    </div>\n")
            }

            val replies = item.replies
            if (item.replyCount > 0 && replies != null) {
                val repliesJs = HtmlRendererCommon.serializePageJs(replies)
                if (repliesJs != null) {
                    sb.append("    <a href=\"#\" class=\"comment-replies-toggle\" onclick=\"toggleReplies(this, '$repliesJs', $serviceId, '$videoUrlJs'); return false;\">")
                      .append("<span class=\"material-symbols-rounded reply-chevron\">expand_more</span>${item.replyCount} replies</a>\n")
                      .append("    <div class=\"comment-replies\"></div>\n")
                }
            }

            sb.append("  </div>\n")
              .append("</div>\n")
        }

        sb.append("<div class=\"comments-load-more-wrapper\">\n")
        val nextPageJs = HtmlRendererCommon.serializePageJs(nextPage)
        if (nextPageJs != null) {
            val repliesFlag = if (isReplies) 1 else 0
            val loadMoreLabel = if (isReplies) "Load More Replies" else "Load More Comments"
            sb.append("  <a href=\"#\" class=\"btn-page\" onclick=\"loadMoreComments(this, '$nextPageJs', $serviceId, '$videoUrlJs', $repliesFlag); return false;\">$loadMoreLabel</a>\n")
        }
        sb.append("</div>\n")

        return sb.toString()
    }
}

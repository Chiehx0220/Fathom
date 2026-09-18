package org.schabi.newpipe.localserver

import io.github.aedev.flow.player.stream.serviceSupportsBulletComments
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

// Video watch page and audio-only watch page rendering, split out of the former monolithic
// HtmlRenderer.java. renderWatchContent is the largest single chunk (~700 lines) from that file.
object HtmlRendererWatch {

    // The "Related Content"/"Up Next" sidebar list's per-item data prep - identical between
    // renderWatchContent() and renderAudioWatch(), which otherwise render the card in visually
    // different sizes (94px vs 80px thumbnails) so aren't collapsed into one shared markup
    // function. This is the part a future extractor-shape change would actually need to touch.
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
          // serviceId must be forwarded: without it the server falls back to YouTube and fails to
          // resolve links from any other service (e.g. Bilibili). This was harmless while localtube
          // was YouTube-only, since the server ignored the parameter entirely.
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
        val advancedJs =
            "                (function() {\n" +
            "                    const nextUrl = \"$nextVideoUrlJs\";\n" +
            "                    const nextTitle = \"$nextVideoTitleJs\";\n" +
            "                    const nextThumb = \"$nextVideoThumbJs\";\n" +
            "                    \n" +
            "                    const wrapper = player.el();\n" +
            "                    player.ready(() => {\n" +
            "                        const el = player.el();\n" +
            "                        const leftTap = document.getElementById(\"double-tap-left\");\n" +
            "                        const rightTap = document.getElementById(\"double-tap-right\");\n" +
            "                        const volumeHud = document.getElementById(\"volume-hud-indicator\");\n" +
            "                        const autoplayOverlay = document.getElementById(\"autoplay-overlay\");\n" +
            "                        if (leftTap) el.appendChild(leftTap);\n" +
            "                        if (rightTap) el.appendChild(rightTap);\n" +
            "                        if (volumeHud) el.appendChild(volumeHud);\n" +
            "                        if (autoplayOverlay) el.appendChild(autoplayOverlay);\n" +
            "                    });\n" +
            "                    \n" +
            "                    // Double tap & Double click to seek\n" +
            "                    if (wrapper) {\n" +
            "                        let lastTap = 0;\n" +
            "                        wrapper.addEventListener(\"touchstart\", function(e) {\n" +
            "                            const now = Date.now();\n" +
            "                            const DOUBLE_PRESS_DELAY = 300;\n" +
            "                            if (now - lastTap < DOUBLE_PRESS_DELAY) {\n" +
            "                                e.preventDefault();\n" +
            "                                const rect = wrapper.getBoundingClientRect();\n" +
            "                                const touchX = e.touches[0].clientX - rect.left;\n" +
            "                                const isLeft = touchX < rect.width * 0.4;\n" +
            "                                const isRight = touchX > rect.width * 0.6;\n" +
            "                                if (isLeft) {\n" +
            "                                    window.seekVideo(-10);\n" +
            "                                    showDoubleTapRipple(\"left\");\n" +
            "                                } else if (isRight) {\n" +
            "                                    window.seekVideo(10);\n" +
            "                                    showDoubleTapRipple(\"right\");\n" +
            "                                }\n" +
            "                            }\n" +
            "                            lastTap = now;\n" +
            "                        }, { passive: false });\n" +
            "                        \n" +
            "                        wrapper.addEventListener(\"dblclick\", function(e) {\n" +
            "                            e.preventDefault();\n" +
            "                            const rect = wrapper.getBoundingClientRect();\n" +
            "                            const clickX = e.clientX - rect.left;\n" +
            "                            const isLeft = clickX < rect.width * 0.4;\n" +
            "                            const isRight = clickX > rect.width * 0.6;\n" +
            "                            if (isLeft) {\n" +
            "                                window.seekVideo(-10);\n" +
            "                                showDoubleTapRipple(\"left\");\n" +
            "                            } else if (isRight) {\n" +
            "                                window.seekVideo(10);\n" +
            "                                showDoubleTapRipple(\"right\");\n" +
            "                            }\n" +
            "                        });\n" +
            "                    }\n" +
            "                    \n" +
            "                    function showDoubleTapRipple(side) {\n" +
            "                        const ind = document.getElementById(\"double-tap-\" + side);\n" +
            "                        if (ind) {\n" +
            "                            ind.classList.add(\"show\");\n" +
            "                            setTimeout(() => ind.classList.remove(\"show\"), 650);\n" +
            "                        }\n" +
            "                    }\n" +
            "                    \n" +
            "                    // Swipe vertically on right side to adjust volume\n" +
            "                    if (wrapper) {\n" +
            "                        let touchStartY = 0;\n" +
            "                        let initialVolume = 1;\n" +
            "                        let isSwipeActive = false;\n" +
            "                        \n" +
            "                        wrapper.addEventListener(\"touchstart\", function(e) {\n" +
            "                            if (e.touches.length === 1) {\n" +
            "                                const rect = wrapper.getBoundingClientRect();\n" +
            "                                const touchX = e.touches[0].clientX - rect.left;\n" +
            "                                if (touchX > rect.width * 0.5) {\n" +
            "                                    touchStartY = e.touches[0].clientY;\n" +
            "                                    initialVolume = player.volume();\n" +
            "                                    isSwipeActive = true;\n" +
            "                                }\n" +
            "                            }\n" +
            "                        }, { passive: true });\n" +
            "                        \n" +
            "                        wrapper.addEventListener(\"touchmove\", function(e) {\n" +
            "                            if (isSwipeActive && e.touches.length === 1) {\n" +
            "                                e.preventDefault();\n" +
            "                                const deltaY = touchStartY - e.touches[0].clientY;\n" +
            "                                const rect = wrapper.getBoundingClientRect();\n" +
            "                                const volumeChange = deltaY / (rect.height * 0.8);\n" +
            "                                const newVolume = Math.max(0, Math.min(1, initialVolume + volumeChange));\n" +
            "                                player.volume(newVolume);\n" +
            "                                showVolumeHUD(Math.round(newVolume * 100));\n" +
            "                            }\n" +
            "                        }, { passive: false });\n" +
            "                        \n" +
            "                        wrapper.addEventListener(\"touchend\", function() {\n" +
            "                            isSwipeActive = false;\n" +
            "                        });\n" +
            "                    }\n" +
            "                    \n" +
            "                    let volumeHudTimeout = null;\n" +
            "                    function showVolumeHUD(volumePercent) {\n" +
            "                        const hud = document.getElementById(\"volume-hud-indicator\");\n" +
            "                        const text = document.getElementById(\"volume-hud-text\");\n" +
            "                        const icon = document.getElementById(\"volume-hud-icon\");\n" +
            "                        if (hud && text && icon) {\n" +
            "                            text.innerText = volumePercent + \"%\";\n" +
            "                            if (volumePercent === 0) icon.innerText = \"🔇\";\n" +
            "                            else if (volumePercent < 30) icon.innerText = \"🔈\";\n" +
            "                            else if (volumePercent < 70) icon.innerText = \"🔉\";\n" +
            "                            else icon.innerText = \"🔊\";\n" +
            "                            hud.classList.add(\"show\");\n" +
            "                            clearTimeout(volumeHudTimeout);\n" +
            "                            volumeHudTimeout = setTimeout(() => hud.classList.remove(\"show\"), 1000);\n" +
            "                        }\n" +
            "                    }\n" +
            "                    \n" +
            "                    // Autoplay Queue\n" +
            "                    let autoplayTimer = null;\n" +
            "                    let autoplayInterval = null;\n" +
            "                    player.on(\"ended\", function() {\n" +
            "                        if (!nextUrl) return;\n" +
            "                        const overlay = document.getElementById(\"autoplay-overlay\");\n" +
            "                        const titleEl = document.getElementById(\"autoplay-next-title\");\n" +
            "                        const thumbEl = document.getElementById(\"autoplay-next-thumb\");\n" +
            "                        const progressCircle = document.getElementById(\"autoplay-progress-circle\");\n" +
            "                        \n" +
            "                        if (overlay && titleEl && thumbEl && progressCircle) {\n" +
            "                            titleEl.innerText = nextTitle;\n" +
            "                            thumbEl.src = nextThumb;\n" +
            "                            overlay.classList.add(\"show\");\n" +
            "                            \n" +
            "                            const totalDash = 138;\n" +
            "                            progressCircle.style.strokeDashoffset = 0;\n" +
            "                            \n" +
            "                            autoplayTimer = setTimeout(() => {\n" +
            "                                window.location.href = nextUrl;\n" +
            "                            }, 5000);\n" +
            "                            \n" +
            "                            let elapsed = 0;\n" +
            "                            autoplayInterval = setInterval(() => {\n" +
            "                                elapsed += 100;\n" +
            "                                const progress = elapsed / 5000;\n" +
            "                                progressCircle.style.strokeDashoffset = totalDash * progress;\n" +
            "                            }, 100);\n" +
            "                        }\n" +
            "                    });\n" +
            "                    \n" +
            "                    function clearAutoplay() {\n" +
            "                        clearTimeout(autoplayTimer);\n" +
            "                        clearInterval(autoplayInterval);\n" +
            "                        const overlay = document.getElementById(\"autoplay-overlay\");\n" +
            "                        if (overlay) overlay.classList.remove(\"show\");\n" +
            "                    }\n" +
            "                    \n" +
            "                    const cancelBtn = document.getElementById(\"autoplay-cancel\");\n" +
            "                    if (cancelBtn) cancelBtn.addEventListener(\"click\", clearAutoplay);\n" +
            "                    \n" +
            "                    const playNowBtn = document.getElementById(\"autoplay-play-now\");\n" +
            "                    if (playNowBtn) {\n" +
            "                        playNowBtn.addEventListener(\"click\", () => {\n" +
            "                            if (nextUrl) window.location.href = nextUrl;\n" +
            "                        });\n" +
            "                    }\n" +
            "                    \n" +
            "                    // Keyboard Shortcuts\n" +
            "                    document.addEventListener(\"keydown\", (e) => {\n" +
            "                        const active = document.activeElement;\n" +
            "                        if (active && (active.tagName === \"INPUT\" || active.tagName === \"SELECT\" || active.tagName === \"TEXTAREA\" || active.isContentEditable)) {\n" +
            "                            return;\n" +
            "                        }\n" +
            "                        if (e.key === \" \" || e.key === \"k\" || e.key === \"K\") {\n" +
            "                            e.preventDefault();\n" +
            "                            if (player.paused()) player.play().catch(e => {}); else player.pause();\n" +
            "                        } else if (e.key === \"j\" || e.key === \"J\") {\n" +
            "                            e.preventDefault();\n" +
            "                            window.seekVideo(-10);\n" +
            "                            showDoubleTapRipple(\"left\");\n" +
            "                        } else if (e.key === \"l\" || e.key === \"L\") {\n" +
            "                            e.preventDefault();\n" +
            "                            window.seekVideo(10);\n" +
            "                            showDoubleTapRipple(\"right\");\n" +
            "                        } else if (e.key === \"m\" || e.key === \"M\") {\n" +
            "                            e.preventDefault();\n" +
            "                            player.muted(!player.muted());\n" +
            "                            showVolumeHUD(player.muted() ? 0 : Math.round(player.volume() * 100));\n" +
            "                        }\n" +
            "                    });\n" +
            "                    \n" +
            "                    // Picture-in-Picture Control\n" +
            "                    try {\n" +
            "                        const Button = videojs.getComponent(\"Button\");\n" +
            "                        const PipButton = videojs.extend(Button, {\n" +
            "                            constructor: function() {\n" +
            "                                Button.apply(this, arguments);\n" +
            "                                this.controlText(\"Picture-in-Picture\");\n" +
            "                            },\n" +
            "                            createEl: function() {\n" +
            "                                return videojs.dom.createEl(\"button\", {\n" +
            "                                    className: \"vjs-pip-control vjs-control vjs-button\",\n" +
            "                                    innerHTML: '<span aria-hidden=\"true\" class=\"vjs-icon-placeholder\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" style=\"width:18px;height:18px;vertical-align:middle;margin-top:6px;\"><path d=\"M19 11h-8v6h8v-6zm4 8V4.98C23 3.88 22.1 3 21 3H3c-1.1 0-2 .88-2 1.98V19c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2zm-2 .02H3V4.97h18v14.05z\"/></svg></span>',\n" +
            "                                    type: \"button\"\n" +
            "                                });\n" +
            "                            },\n" +
            "                            handleClick: function() {\n" +
            "                                const video = document.querySelector(\"#player_html5_api\") || document.querySelector(\"video\");\n" +
            "                                if (video) {\n" +
            "                                    if (document.pictureInPictureElement) {\n" +
            "                                        document.exitPictureInPicture().catch(e => {});\n" +
            "                                    } else {\n" +
            "                                        video.requestPictureInPicture().catch(e => {});\n" +
            "                                    }\n" +
            "                                }\n" +
            "                            }\n" +
            "                        });\n" +
            "                        videojs.registerComponent(\"PipButton\", PipButton);\n" +
            "                        player.ready(() => {\n" +
            "                            player.getChild(\"controlBar\").addChild(\"PipButton\", {}, player.getChild(\"controlBar\").children().length - 1);\n" +
            "                        });\n" +
            "                    } catch(e) { console.error(e); }\n" +
            "                })();\n"

        // CSS object-fit:contain (tried first, in HtmlStyles.kt) was expected to letterbox the
        // picture in fullscreen regardless of the device screen's aspect ratio, and it does when
        // simulated in a desktop browser - but the user still saw the picture stretched/cropped in
        // real fullscreen on their phone. Since that couldn't be reproduced or diagnosed further
        // without live access to whatever the real mobile browser is doing differently, this computes
        // the correct letterboxed size directly in JS instead of depending on object-fit being
        // honored at all - same end result (whole frame always visible, black bars fill the gap),
        // but by setting explicit pixel dimensions on the inner <video> tag (not the .video-js
        // wrapper - that stays CDN's forced 100%/100% so the control bar still spans the full
        // screen width, only the picture itself gets boxed) with setProperty(...,'important'), which
        // wins the cascade regardless of what other rule or quirk was defeating the CSS-only version.
        val fullscreenLetterboxJs =
            "                (function() {\n" +
            "                    function fitVideoLetterbox() {\n" +
            "                        var videoTag = player.el().querySelector(\"video\");\n" +
            "                        if (!videoTag) return;\n" +
            "                        if (!player.isFullscreen()) {\n" +
            "                            videoTag.style.removeProperty(\"width\");\n" +
            "                            videoTag.style.removeProperty(\"height\");\n" +
            "                            videoTag.style.removeProperty(\"position\");\n" +
            "                            videoTag.style.removeProperty(\"top\");\n" +
            "                            videoTag.style.removeProperty(\"left\");\n" +
            "                            return;\n" +
            "                        }\n" +
            "                        var vw = player.videoWidth() || 16;\n" +
            "                        var vh = player.videoHeight() || 9;\n" +
            "                        var availW = window.innerWidth;\n" +
            "                        var availH = window.innerHeight;\n" +
            "                        var targetW, targetH;\n" +
            "                        if (vw / vh > availW / availH) {\n" +
            "                            targetW = availW;\n" +
            "                            targetH = availW * vh / vw;\n" +
            "                        } else {\n" +
            "                            targetH = availH;\n" +
            "                            targetW = availH * vw / vh;\n" +
            "                        }\n" +
            "                        videoTag.style.setProperty(\"width\", targetW + \"px\", \"important\");\n" +
            "                        videoTag.style.setProperty(\"height\", targetH + \"px\", \"important\");\n" +
            "                        videoTag.style.setProperty(\"position\", \"absolute\", \"important\");\n" +
            "                        videoTag.style.setProperty(\"top\", ((availH - targetH) / 2) + \"px\", \"important\");\n" +
            "                        videoTag.style.setProperty(\"left\", ((availW - targetW) / 2) + \"px\", \"important\");\n" +
            "                    }\n" +
            "                    player.on(\"fullscreenchange\", fitVideoLetterbox);\n" +
            "                    player.on(\"loadedmetadata\", fitVideoLetterbox);\n" +
            "                    window.addEventListener(\"resize\", fitVideoLetterbox);\n" +
            "                    window.addEventListener(\"orientationchange\", fitVideoLetterbox);\n" +
            "                })();\n"

        // Fathom<->Flow Stage 2: reports playback progress to the new /api/v1/watch_progress
        // endpoint (ApiRenderer.kt / LocalHttpServer.handleApiWatchProgress), so a future Flow
        // client hitting the same server sees the same "how far did I get" state this desktop
        // HTML player itself produces. Recomputes the encoded URL locally rather than reusing the
        // per-branch "infoUrlEncoded" val below - this block sits above where the isCached/DASH
        // branches split, before that val exists in scope, and info.url itself is already
        // available this high up (info is this function's own parameter).
        val progressUrlEncoded = HtmlRendererCommon.encodeUrl(info.url)
        val watchProgressJs =
            "                (function() {\n" +
            "                    var lastReported = -1;\n" +
            "                    function reportProgress() {\n" +
            "                        var dur = player.duration();\n" +
            "                        if (!dur) return;\n" +
            "                        var percent = Math.round((player.currentTime() / dur) * 100);\n" +
            "                        if (percent === lastReported) return;\n" +
            "                        lastReported = percent;\n" +
            "                        var url = \"/api/v1/watch_progress?id=$progressUrlEncoded&serviceId=$serviceId&percent=\" + percent + \"&durationSeconds=\" + Math.round(dur);\n" +
            "                        fetch(url, { keepalive: true }).catch(function() {});\n" +
            "                    }\n" +
            "                    var progressTimer = setInterval(reportProgress, 15000);\n" +
            "                    player.on(\"pause\", reportProgress);\n" +
            "                    window.addEventListener(\"pagehide\", reportProgress);\n" +
            "                })();\n"

        // Times come back from SponsorBlockClient in milliseconds; dividing by 1000 here once
        // means the JS side just compares against player.currentTime() (seconds) directly with
        // no unit conversion scattered through it. actionType=skip is already filtered server-side
        // by SponsorBlockClient, so every segment here is skippable by construction. Flow's native
        // SponsorBlockRepository (which SponsorBlockClient now bridges to) only fetches these 6
        // categories - no preview/filler.
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
        val sponsorSegmentsJs = if (sponsorItemsJs.isEmpty()) {
            ""
        } else {
            "                (function() {\n" +
            "                    var segments = [$sponsorItemsJs];\n" +
            "                    var skipBtn = document.getElementById(\"sponsor-skip-btn\");\n" +
            "                    var skipLabel = document.getElementById(\"sponsor-skip-label\");\n" +
            "                    var current = null;\n" +
            "                    function placeMarkers() {\n" +
            "                        var holder = player.el().querySelector(\".vjs-progress-holder\");\n" +
            "                        var dur = player.duration();\n" +
            "                        if (!holder || !dur) return;\n" +
            "                        segments.forEach(function(seg) {\n" +
            "                            var marker = document.createElement(\"div\");\n" +
            "                            marker.className = \"sponsor-segment-marker cat-\" + seg.c;\n" +
            "                            marker.style.left = (seg.s / dur * 100) + \"%\";\n" +
            "                            marker.style.width = (Math.max(seg.e - seg.s, 0) / dur * 100) + \"%\";\n" +
            // A segment's true proportional width can be a fraction of a pixel on a long video
            // with a narrow mobile progress bar (confirmed live: a 7s segment in a 33-minute video
            // computed to 0.16px on a 375px-wide phone, where the whole progress-holder itself is
            // only 44px - the rest of that width goes to the other control-bar buttons). At that
            // size the marker is both invisible and physically untappable, which is what made the
            // "flashes and disappears" report - see [[project_localtube_sponsorblock_feature]] -
            // and "no button after seeking to it" reports look like a logic bug when the underlying
            // skip-button timing (driven by the real start/end times, unaffected by this) was
            // actually correct the whole time. min-width floors the visual marker at a legible size
            // without touching those real times, same accommodation the actual SponsorBlock browser
            // extension makes for short segments.\n" +
            "                            marker.style.minWidth = \"3px\";\n" +
            "                            holder.appendChild(marker);\n" +
            "                        });\n" +
            "                    }\n" +
            "                    player.one(\"loadedmetadata\", placeMarkers);\n" +
            "                    if (player.duration()) placeMarkers();\n" +
            "                    player.on(\"timeupdate\", function() {\n" +
            "                        var t = player.currentTime();\n" +
            "                        var seg = null;\n" +
            "                        for (var i = 0; i < segments.length; i++) {\n" +
            "                            if (t >= segments[i].s && t < segments[i].e) { seg = segments[i]; break; }\n" +
            "                        }\n" +
            "                        if (seg) {\n" +
            "                            if (current !== seg) {\n" +
            "                                current = seg;\n" +
            "                                if (skipLabel) skipLabel.textContent = \"跳過 \" + seg.l;\n" +
            "                                if (skipBtn) skipBtn.classList.add(\"visible\");\n" +
            "                            }\n" +
            "                        } else if (current) {\n" +
            "                            current = null;\n" +
            "                            if (skipBtn) skipBtn.classList.remove(\"visible\");\n" +
            "                        }\n" +
            "                    });\n" +
            "                    if (skipBtn) skipBtn.addEventListener(\"click\", function() {\n" +
            "                        if (current) {\n" +
            "                            player.currentTime(current.e);\n" +
            "                            skipBtn.classList.remove(\"visible\");\n" +
            "                            current = null;\n" +
            "                        }\n" +
            "                    });\n" +
            "                })();\n"
        }

        // Bilibili danmaku ("bullet comments") overlay. Fetched async from /danmaku (same
        // rationale as the comments fragment below it - a video can carry thousands of these, so
        // it shouldn't block the initial page). Scroll-type comments animate via a CSS `transform`
        // transition (not `animation`/@keyframes) since the travel distance depends on the
        // player's actual pixel width and each comment's own measured text width, neither known
        // until render time. Top/bottom comments are simple fixed-position fades instead, since
        // those don't move horizontally. BulletCommentsInfoItem.getLastingTime() always reports -1
        // (an extractor-library bug, not this app's), so on-screen duration is hardcoded below to
        // Bilibili's own typical defaults instead of coming from the API.
        val danmakuJs = if (!serviceSupportsBulletComments(serviceId)) {
            ""
        } else {
            val danmakuUrlJs = "/danmaku?serviceId=$serviceId&id=${HtmlRendererCommon.encodeUrl(info.url)}"
            "                (function() {\n" +
            "                    var layer = document.getElementById(\"danmaku-layer\");\n" +
            "                    var toggleBtn = document.getElementById(\"danmaku-toggle-btn\");\n" +
            "                    if (!layer) return;\n" +
            // video.js's real fullscreen target is player.el() (a wrapper div it creates around
            // the actual <video> tech element - see fitVideoLetterbox()'s own use of
            // player.el().querySelector("video") above), NOT the .vjs-player-wrapper div this
            // layer and the other overlays (double-tap indicators, volume HUD, up-next overlay,
            // sponsor-skip button) live in as plain siblings of <video>. The Fullscreen API only
            // renders the fullscreened element and its descendants, so anything outside player.el()
            // - this layer included - simply isn't part of the screen while fullscreen is active.
            // Moving it inside on fullscreenchange (and back out again on exit) fixes that; the
            // other overlays have the same gap but are out of scope for this fix.
            "                    var danmakuHome = layer.parentNode, danmakuNextSibling = layer.nextSibling;\n" +
            "                    player.on(\"fullscreenchange\", function() {\n" +
            "                        if (player.isFullscreen()) {\n" +
            "                            player.el().appendChild(layer);\n" +
            "                        } else if (danmakuHome) {\n" +
            "                            danmakuHome.insertBefore(layer, danmakuNextSibling);\n" +
            "                        }\n" +
            "                    });\n" +
            "                    var SCROLL_DURATION = 8, FIXED_DURATION = 4;\n" +
            "                    var comments = [], nextIndex = 0, enabled = true;\n" +
            "                    var scrollLaneUntil = new Array(14).fill(0);\n" +
            "                    var topLaneUntil = new Array(4).fill(0);\n" +
            "                    var bottomLaneUntil = new Array(4).fill(0);\n" +
            "                    function pickLane(untilArr, lanes, now, dur) {\n" +
            "                        for (var i = 0; i < lanes; i++) {\n" +
            "                            if (untilArr[i] <= now) { untilArr[i] = now + dur; return i; }\n" +
            "                        }\n" +
            "                        var idx = 0;\n" +
            "                        for (var i = 1; i < lanes; i++) { if (untilArr[i] < untilArr[idx]) idx = i; }\n" +
            "                        untilArr[idx] = now + dur;\n" +
            "                        return idx;\n" +
            "                    }\n" +
            "                    function spawn(item) {\n" +
            "                        var h = layer.clientHeight || 200;\n" +
            "                        var w = layer.clientWidth || 800;\n" +
            "                        var el = document.createElement(\"div\");\n" +
            "                        el.className = \"danmaku-item \" + item.position;\n" +
            "                        el.textContent = item.text;\n" +
            // Bilibili's own comment sizes are meant as a small, fairly constant font relative to
            // the player's WIDTH (not height) - the first attempt scaled by height and produced
            // comments 50-60px tall on a normal-width video, covering most of the frame.
            "                        el.style.fontSize = Math.max(14, Math.min(30, Math.round(w * item.size * 0.028))) + \"px\";\n" +
            "                        el.style.color = item.color;\n" +
            "                        var laneH = parseFloat(el.style.fontSize) * 1.5;\n" +
            "                        var now = player.currentTime();\n" +
            "                        if (item.position === \"top\" || item.position === \"bottom\") {\n" +
            "                            var lanes = Math.max(1, Math.min(4, Math.floor(h * 0.35 / laneH)));\n" +
            "                            var untilArr = item.position === \"top\" ? topLaneUntil : bottomLaneUntil;\n" +
            "                            var lane = pickLane(untilArr, lanes, now, FIXED_DURATION);\n" +
            "                            el.style[item.position] = (8 + lane * laneH) + \"px\";\n" +
            "                            el.style.animation = \"danmaku-fade \" + FIXED_DURATION + \"s linear\";\n" +
            "                            el.addEventListener(\"animationend\", function() { el.remove(); });\n" +
            "                            layer.appendChild(el);\n" +
            "                        } else {\n" +
            "                            var lanes = Math.max(1, Math.floor(h / laneH));\n" +
            "                            var lane = pickLane(scrollLaneUntil, Math.min(14, lanes), now, SCROLL_DURATION);\n" +
            "                            el.style.top = (lane * laneH) + \"px\";\n" +
            "                            var startX = layer.clientWidth;\n" +
            "                            el.style.transform = \"translateX(\" + startX + \"px)\";\n" +
            "                            layer.appendChild(el);\n" +
            "                            var endX = -el.offsetWidth;\n" +
            "                            el.dataset.startX = startX; el.dataset.endX = endX; el.dataset.duration = SCROLL_DURATION;\n" +
            "                            requestAnimationFrame(function() {\n" +
            "                                el.style.transition = \"transform \" + SCROLL_DURATION + \"s linear\";\n" +
            "                                el.style.transform = \"translateX(\" + endX + \"px)\";\n" +
            "                            });\n" +
            "                            el.addEventListener(\"transitionend\", function() { el.remove(); });\n" +
            "                        }\n" +
            "                    }\n" +
            "                    function tick() {\n" +
            "                        if (!enabled || !comments.length) return;\n" +
            "                        var t = player.currentTime();\n" +
            "                        while (nextIndex < comments.length && comments[nextIndex].time <= t) {\n" +
            "                            if (t - comments[nextIndex].time < 1.2) spawn(comments[nextIndex]);\n" +
            "                            nextIndex++;\n" +
            "                        }\n" +
            "                    }\n" +
            "                    function resync() {\n" +
            "                        layer.innerHTML = \"\";\n" +
            "                        scrollLaneUntil.fill(0); topLaneUntil.fill(0); bottomLaneUntil.fill(0);\n" +
            "                        var t = player.currentTime();\n" +
            "                        nextIndex = 0;\n" +
            "                        while (nextIndex < comments.length && comments[nextIndex].time < t) nextIndex++;\n" +
            "                    }\n" +
            "                    player.on(\"timeupdate\", tick);\n" +
            "                    player.on(\"seeking\", resync);\n" +
            "                    player.on(\"pause\", function() {\n" +
            "                        layer.classList.add(\"video-paused\");\n" +
            "                        layer.querySelectorAll(\".danmaku-item.scroll\").forEach(function(el) {\n" +
            "                            var m = new DOMMatrixReadOnly(getComputedStyle(el).transform);\n" +
            "                            el.style.transition = \"none\";\n" +
            "                            el.style.transform = \"translateX(\" + m.m41 + \"px)\";\n" +
            "                            el.dataset.pausedX = m.m41;\n" +
            "                        });\n" +
            "                    });\n" +
            "                    player.on(\"play\", function() {\n" +
            "                        layer.classList.remove(\"video-paused\");\n" +
            "                        layer.querySelectorAll(\".danmaku-item.scroll\").forEach(function(el) {\n" +
            "                            if (el.dataset.pausedX === undefined) return;\n" +
            "                            var startX = parseFloat(el.dataset.pausedX);\n" +
            "                            var endX = parseFloat(el.dataset.endX);\n" +
            "                            var totalDist = parseFloat(el.dataset.startX) - endX;\n" +
            "                            var remainDist = startX - endX;\n" +
            "                            var remainDur = totalDist > 0 ? parseFloat(el.dataset.duration) * (remainDist / totalDist) : 0;\n" +
            "                            delete el.dataset.pausedX;\n" +
            "                            if (remainDur <= 0) { el.remove(); return; }\n" +
            "                            requestAnimationFrame(function() {\n" +
            "                                el.style.transition = \"transform \" + remainDur + \"s linear\";\n" +
            "                                el.style.transform = \"translateX(\" + endX + \"px)\";\n" +
            "                            });\n" +
            "                        });\n" +
            "                    });\n" +
            "                    if (toggleBtn) {\n" +
            "                        toggleBtn.addEventListener(\"click\", function() {\n" +
            "                            enabled = !enabled;\n" +
            "                            toggleBtn.classList.toggle(\"off\", !enabled);\n" +
            "                            if (!enabled) layer.innerHTML = \"\";\n" +
            "                        });\n" +
            "                    }\n" +
            "                    fetch(\"$danmakuUrlJs\")\n" +
            "                        .then(function(res) { return res.json(); })\n" +
            "                        .then(function(data) {\n" +
            "                            comments = (data.danmaku || []).sort(function(a, b) { return a.time - b.time; });\n" +
            "                            resync();\n" +
            "                        })\n" +
            "                        .catch(function() {});\n" +
            "                })();\n"
        }

        val infoNameJs = HtmlRendererCommon.escapeJs(info.name)
        sb.append("<script>document.title = \"$infoNameJs - Fathom\";</script>\n")
        sb.append("<div class=\"container\">\n")
          .append("  <div class=\"player-container\">\n")
          .append("    <div class=\"player-layout\">\n")
          .append("      <div class=\"main-content\">\n")

        val hasVideo = info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty() || !info.hlsUrl.isNullOrEmpty()
        if (hasVideo) {
            @Suppress("UNUSED_VARIABLE")
            val defaultQuality = targetQuality ?: "720p"

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

            run {
                sb.append("        <div class=\"vjs-player-wrapper\">\n")
                  .append("          <video id=\"player\" class=\"video-js vjs-default-skin vjs-big-play-centered\" controls autoplay preload=\"auto\" style=\"width:100%; height:auto; aspect-ratio:16/9; display:block;\">\n")
                  .append("            <source src=\"/manifest?serviceId=$serviceId&id=$infoUrlEncoded\" type=\"application/dash+xml\">\n")
                  .append(trackTags.toString())
                  .append("            Your browser does not support HTML5 video.\n")
                  .append("          </video>\n")
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
                  .append("        </div>\n")

                // Serialize available audio tracks for JavaScript dropdown rendering
                var defaultAudio: AudioStream? = null
                val audioStreams = info.audioStreams
                if (!audioStreams.isNullOrEmpty()) {
                    var m4aStreams: MutableList<AudioStream> = audioStreams.filterTo(ArrayList()) { it.format == MediaFormat.M4A }
                    if (m4aStreams.isEmpty()) {
                        m4aStreams = ArrayList(audioStreams)
                    }
                    java.util.Collections.sort(m4aStreams, LocalHttpServer.audioTrackPriorityComparator())
                    defaultAudio = m4aStreams[0]
                }

                val defaultTrackId = defaultAudio?.audioTrackId ?: ""

                val tracksJson = StringBuilder("[")
                if (audioStreams != null) {
                    var first = true
                    val processedTrackIds = java.util.HashSet<String>()
                    for (stream in audioStreams) {
                        var trackId = stream.audioTrackId
                        if (trackId == null) trackId = ""
                        if (processedTrackIds.contains(trackId)) {
                            continue
                        }
                        processedTrackIds.add(trackId)

                        var label = stream.audioTrackName
                        if (label.isNullOrEmpty()) {
                            val locale = stream.audioLocale
                            label = locale?.let { java.util.Locale.forLanguageTag(it.replace('_', '-')).displayName } ?: "Audio Track"
                        }

                        if (!first) tracksJson.append(",")
                        first = false
                        val trackIdJs = HtmlRendererCommon.escapeJs(trackId)
                        val trackLabelJs = HtmlRendererCommon.escapeJs(label)
                        tracksJson.append("{\"id\":\"$trackIdJs\",\"label\":\"$trackLabelJs\"}")
                    }
                }
                tracksJson.append("]")

                // Generate quality selector and audio track selector HTML options
                sb.append("        <div class=\"player-controls-row\" style=\"display: flex; gap: 15px; margin-top: 10px; margin-bottom: 15px; align-items: center; justify-content: flex-start; flex-wrap: wrap;\">\n")
                  .append("          <div style=\"display: flex; align-items: center; gap: 8px;\">\n")
                  .append("            <label for=\"quality-select\" style=\"font-size: 13px; font-weight: 500; color: var(--text-color); opacity: 0.8;\">Quality:</label>\n")
                  .append("            <div class=\"md-select-wrap\">\n")
                  .append("            <select id=\"quality-select\" class=\"md-select\">\n")
                  .append("              <option value=\"auto\" selected>Auto</option>\n")

                // List available qualities
                val addedQualities = java.util.HashSet<String>()
                // Check video-only streams (HD)
                val videoOnlyStreams = info.videoOnlyStreams
                if (videoOnlyStreams != null) {
                    for (vs in videoOnlyStreams) {
                        val res = vs.resolution
                        if (res != null && !addedQualities.contains(res)) {
                            addedQualities.add(res)
                            sb.append("              <option value=\"$res\">$res</option>\n")
                        }
                    }
                }
                // Check progressive streams (SD)
                val videoStreams = info.videoStreams
                if (videoStreams != null) {
                    for (vs in videoStreams) {
                        val res = vs.resolution
                        if (res != null && !addedQualities.contains(res)) {
                            addedQualities.add(res)
                            sb.append("              <option value=\"$res\">$res</option>\n")
                        }
                    }
                }
                sb.append("            </select>\n")
                  .append("            <span class=\"material-symbols-rounded md-select-arrow\">expand_more</span>\n")
                  .append("            </div>\n")
                  .append("          </div>\n")
                  .append("          <div id=\"audio-track-container\" style=\"display: flex; align-items: center; gap: 8px;\">\n")
                  .append("            <label for=\"audio-track-select\" style=\"font-size: 13px; font-weight: 500; color: var(--text-color); opacity: 0.8;\">Audio Language:</label>\n")
                  .append("            <div class=\"md-select-wrap\">\n")
                  .append("            <select id=\"audio-track-select\" class=\"md-select\">\n")
                  .append("            </select>\n")
                  .append("            <span class=\"material-symbols-rounded md-select-arrow\">expand_more</span>\n")
                  .append("            </div>\n")
                  .append("          </div>\n")
                  .append(
                      if (serviceSupportsBulletComments(serviceId)) {
                          "          <button class=\"danmaku-toggle-btn\" id=\"danmaku-toggle-btn\" title=\"彈幕開關\"><span class=\"material-symbols-rounded\">chat_bubble</span></button>\n"
                      } else {
                          ""
                      },
                  )
                  .append("        </div>\n")

                // Script for player quality switching and remote commands
                sb.append("        <script>\n")
                  .append("            window.availableAudioTracks = $tracksJson;\n")
                  .append("            window.defaultAudioTrackId = '${HtmlRendererCommon.escapeJs(defaultTrackId)}';\n")
                  .append("            (function() {\n")
                  .append("                const player = videojs('player', {\n")
                  .append("                    playbackRates: [0.5, 1, 1.25, 1.5, 2],\n")
                  .append("                    controlBar: { audioTrackButton: false }\n")
                  .append("                });\n")
                  .append("                window.videoPlayer = player;\n")
                  .append("                player.on('fullscreenchange', () => {\n")
                  .append("                    if (player.isFullscreen()) {\n")
                  .append("                        if (screen.orientation && screen.orientation.lock) {\n")
                  .append("                            screen.orientation.lock('landscape').catch(e => {});\n")
                  .append("                        }\n")
                  .append("                    } else {\n")
                  .append("                        if (screen.orientation && screen.orientation.unlock) {\n")
                  .append("                            screen.orientation.unlock();\n")
                  .append("                        }\n")
                  .append("                    }\n")
                  .append("                });\n")
                  .append("                player.ready(() => {\n")
                  .append("                    player.play().catch(err => console.error(err));\n")
                  .append("                });\n")
                  .append("                \n")
                  .append("                const selector = document.getElementById('quality-select');\n")
                  .append("                const streamDuration = $duration;\n")
                  .append("                \n")
                  .append("                // Extract initial start time if available\n")
                  .append("                const urlParams = new URLSearchParams(window.location.search);\n")
                  .append("                let initialStartTime = parseFloat(urlParams.get('start_time')) || 0;\n")
                  .append("                if (initialStartTime > 0) {\n")
                  .append("                    player.ready(() => {\n")
                  .append("                        player.currentTime(initialStartTime);\n")
                  .append("                    });\n")
                  .append("                }\n")
                  .append("                \n")
                  .append("                window.seekVideo = (delta) => {\n")
                  .append("                    const targetTime = Math.max(0, Math.min(streamDuration || player.duration() || 0, player.currentTime() + delta));\n")
                  .append("                    player.currentTime(targetTime);\n")
                  .append("                };\n")
                  .append("                \n")
                  .append("                if (selector) {\n")
                  .append("                    selector.addEventListener('change', () => {\n")
                  .append("                        const targetQuality = selector.value;\n")
                  .append("                        const qualityLevels = player.qualityLevels();\n")
                  .append("                        if (!qualityLevels) return;\n")
                  .append("                        \n")
                  .append("                        if (targetQuality === 'auto') {\n")
                  .append("                            for (let i = 0; i < qualityLevels.length; i++) {\n")
                  .append("                                qualityLevels[i].enabled = true;\n")
                  .append("                            }\n")
                  .append("                        } else {\n")
                  .append("                            const targetHeight = parseInt(targetQuality);\n")
                  .append("                            for (let i = 0; i < qualityLevels.length; i++) {\n")
                  .append("                                const level = qualityLevels[i];\n")
                  .append("                                if (level.height === targetHeight) {\n")
                  .append("                                    level.enabled = true;\n")
                  .append("                                } else {\n")
                  .append("                                    level.enabled = false;\n")
                  .append("                                }\n")
                  .append("                            }\n")
                  .append("                        }\n")
                  .append("                    });\n")
                  .append("                }\n")
                  .append("                \n")
                  .append("                const audioSelect = document.getElementById('audio-track-select');\n")
                  .append("                if (audioSelect && window.availableAudioTracks) {\n")
                  .append("                    audioSelect.innerHTML = '';\n")
                  .append("                    const urlParams = new URLSearchParams(window.location.search);\n")
                  .append("                    let currentAudioTrack = urlParams.get('audio_track');\n")
                  .append("                    if (currentAudioTrack === null) {\n")
                  .append("                        currentAudioTrack = window.defaultAudioTrackId || '';\n")
                  .append("                    }\n")
                  .append("                    window.availableAudioTracks.forEach(track => {\n")
                  .append("                        const option = document.createElement('option');\n")
                  .append("                        option.value = track.id;\n")
                  .append("                        option.text = track.label;\n")
                  .append("                        option.selected = (track.id === currentAudioTrack);\n")
                  .append("                        audioSelect.appendChild(option);\n")
                  .append("                    });\n")
                  .append("                    audioSelect.addEventListener('change', () => {\n")
                  .append("                        const selectedTrackId = audioSelect.value;\n")
                  .append("                        const currUrl = new URL(window.location.href);\n")
                  .append("                        currUrl.searchParams.set('audio_track', selectedTrackId);\n")
                  .append("                        window.history.replaceState({}, '', currUrl);\n")
                  .append("                        const currentTime = player.currentTime();\n")
                  .append("                        const isPaused = player.paused();\n")
                  .append("                        const manifestUrl = '/manifest?serviceId=$serviceId&id=$infoUrlEncoded&audio_track=' + encodeURIComponent(selectedTrackId);\n")
                  .append("                        player.src({ src: manifestUrl, type: 'application/dash+xml' });\n")
                  .append("                        player.ready(() => {\n")
                  .append("                            setTimeout(() => {\n")
                  .append("                                player.currentTime(currentTime);\n")
                  .append("                                if (!isPaused) {\n")
                  .append("                                    player.play().catch(e => {});\n")
                  .append("                                }\n")
                  .append("                            }, 150);\n")
                  .append("                        });\n")
                  .append("                    });\n")
                  .append("                }\n")
                  .append("                \n")
                  .append("                const cacheBtn = document.getElementById('cache-offline-btn');\n")
                  .append("                if (cacheBtn) {\n")
                  .append("                    cacheBtn.addEventListener('click', (e) => {\n")
                  .append("                        e.preventDefault();\n")
                  .append("                        const qualitySelect = document.getElementById('quality-select');\n")
                  .append("                        const audioSelectEl = document.getElementById('audio-track-select');\n")
                  .append("                        const quality = qualitySelect ? qualitySelect.value : 'auto';\n")
                  .append("                        const audioTrack = audioSelectEl ? audioSelectEl.value : '';\n")
                  .append("                        const url = new URL(cacheBtn.href, window.location.origin);\n")
                  .append("                        url.searchParams.set('quality', quality);\n")
                  .append("                        url.searchParams.set('audio_track', audioTrack);\n")
                  .append("                        window.location.href = url.toString();\n")
                  .append("                    });\n")
                  .append("                }\n")
                  .append(advancedJs)
                  .append(sponsorSegmentsJs)
                  .append(danmakuJs)
                  .append(fullscreenLetterboxJs)
                  .append(watchProgressJs)
                  .append("                if ('mediaSession' in navigator) {\n")
                  .append("                    navigator.mediaSession.metadata = new MediaMetadata({\n")
                  .append("                        title: '$infoNameJs',\n")
                  .append("                        artist: '${HtmlRendererCommon.escapeJs(info.uploaderName)}'\n")
                  .append("                    });\n")
                  .append("                }\n")
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
          .append("              ${HtmlRendererCommon.renderWatchLaterButton(info, serviceId, isWatchLater)}")

        sb.append("            </div>\n")
        sb.append("          </div>\n")

        sb.append("          <div class=\"media-description\">\n")
          .append("            <div style=\"font-weight:700; font-size:13.5px; margin-bottom:8px; color:var(--text-color);\">$formattedViews &nbsp;•&nbsp; $uploadDate</div>\n")
          .append(info.description?.content ?: "No description provided.")
          .append("          </div>\n")
          .append("        </div>\n")

        sb.append("        <div class=\"comments-section\">\n")
          .append("          <h3 class=\"comment-count\">💬 Comments</h3>\n")
          .append("          <div id=\"comments-loader\" style=\"text-align:center; padding:24px 0;\">\n")
          .append("            <div style=\"display:inline-block; width:28px; height:28px; border:3px solid var(--search-input-bg); border-top:3px solid var(--logo-color); border-radius:50%; animation:comments-spin 0.8s linear infinite;\"></div>\n")
          .append("          </div>\n")
          .append("          <div id=\"comments-list\" style=\"display:none;\"></div>\n")
          .append("          <style>@keyframes comments-spin { 0% { transform:rotate(0deg); } 100% { transform:rotate(360deg); } }</style>\n")
          .append("          <script>\n")
          // btn.parentElement rather than a global id lookup: this same wrapper markup appears
          // once for the top-level "Load More Comments" and once per expanded reply thread, so a
          // fixed id would collide the moment more than one is on the page at once.
          .append("            window.loadMoreComments = function(btn, nextPage, svcId, videoUrl, isReplies) {\n")
          .append("                const wrapper = btn.parentElement;\n")
          .append("                btn.textContent = 'Loading...';\n")
          .append("                btn.style.pointerEvents = 'none';\n")
          .append("                const ctx = isReplies ? '&context=replies' : '';\n")
          .append("                fetchText('/comments?serviceId=' + svcId + '&id=' + encodeURIComponent(videoUrl) + '&nextPage=' + encodeURIComponent(nextPage) + ctx,\n")
          .append("                    html => { if (wrapper) wrapper.outerHTML = html; },\n")
          .append("                    () => { btn.textContent = 'Failed to load. Tap to retry'; btn.style.pointerEvents = 'auto'; });\n")
          .append("            };\n")
          // Replies reuse the exact same /comments endpoint and CommentsInfoItem.getReplies()'s
          // Page - the extractor treats a reply thread as just another paginated comment list, so
          // no separate backend route was needed. Fetched once per thread (dataset.loaded guards
          // re-fetching); after that, re-clicking just toggles the "expanded" class on the toggle
          // link (rotates the chevron via CSS) and its sibling container (show/hide via CSS).
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
          .append("          <audio id=\"audio-player\" controls autoplay style=\"width:100%; max-width:540px; height:48px; border-radius:24px; margin-bottom:20px;\">\n")
          .append("            <source src=\"/stream?serviceId=$serviceId&id=$infoUrlEncoded\" type=\"$audioMime\">\n")
          .append("            Your browser does not support the HTML5 audio element.\n")
          .append("          </audio>\n")
          .append("          <script>\n")
          .append("            (function() {\n")
          .append("              const audio = document.getElementById('audio-player');\n")
          .append("              const cover = document.getElementById('audio-cover');\n")
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
          .append("              if ('mediaSession' in navigator) {\n")
          .append("                navigator.mediaSession.metadata = new MediaMetadata({\n")
          .append("                  title: titleText,\n")
          .append("                  artist: artistText,\n")
          .append("                  artwork: [{ src: '${HtmlRendererCommon.escapeJs(posterUrl)}', sizes: '512x512', type: 'image/png' }]\n")
          .append("                });\n")
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

        sb.append("        <div class=\"media-description\" style=\"margin-top:20px;\">\n")
          .append("          <div style=\"font-weight:700; font-size:13.5px; margin-bottom:8px; color:var(--text-color);\">$formattedViews &nbsp;•&nbsp; $uploadDate</div>\n")
          .append(info.description?.content ?: "No description provided.")
          .append("        </div>\n")
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

        return HtmlRendererCommon.wrapInTemplate("Audio: ${info.name}", sb.toString(), isTv)
    }

    // Bare HTML fragment (no wrapInTemplate) - injected via innerHTML by the comments-loading
    // <script> in renderWatchContent(), and re-fetched as-is both for top-level pagination and
    // (with isReplies=true) for a reply thread, which is just another Page from the same
    // extractor. .comments-load-more-wrapper is the seam: a "Load More" click replaces
    // btn.parentElement's outerHTML with a fresh batch of comments plus a new wrapper, so
    // repeated pagination keeps appending in place. Deliberately a class, not an id - this
    // markup appears once per expanded reply thread as well as once for the top-level list, and
    // window.loadMoreComments finds its target via btn.parentElement rather than a lookup that
    // would collide the moment more than one instance exists on the page.
    @JvmStatic
    fun renderComments(serviceId: Int, videoUrl: String, items: List<CommentsInfoItem>, nextPage: Page?, isTv: Boolean, isReplies: Boolean = false): String {
        val sb = StringBuilder()

        // Always ends with a .comments-load-more-wrapper div (populated or empty), even on this
        // empty-items branch, rather than returning early - loadMoreComments() replaces
        // btn.parentElement's outerHTML with whatever comes back from a "Load More" click, so
        // every response (first load or a later page) needs that same anchor to stay replaceable.
        if (items.isEmpty()) {
            sb.append("<div class=\"loading-placeholder\">No comments yet.</div>\n")
        }

        val videoUrlJs = HtmlRendererCommon.escapeJs(videoUrl)

        for (item in items) {
            val authorEscaped = HtmlRendererCommon.escapeHtml(item.uploaderName)
            // Description.content is real HTML (<br>, <a href>), same as .media-description above
            // - not plain text, so it's rendered unescaped rather than double-escaped.
            val commentTextHtml = item.commentText.content ?: ""
            val timeText = HtmlRendererCommon.formatUploadDate(item.uploadDate, item.textualUploadDate ?: "")
            val likeCountText = if (item.likeCount > 0) HtmlRendererCommon.formatCount(item.likeCount.toLong()) else ""

            sb.append("<div class=\"comment\">\n")
            if (HtmlRendererCommon.hasThumbnail(item.uploaderAvatarUrl)) {
                val avatarUrl = HtmlRendererCommon.getThumbnailUrl(item.uploaderAvatarUrl)
                sb.append("  <img class=\"comment-avatar\" src=\"$avatarUrl\">\n")
            } else {
                // Extractor limitation, not fixable here - fall back to a colored initial instead
                // of the generic placeholder photo.
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

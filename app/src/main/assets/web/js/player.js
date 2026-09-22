// The watch page: player controls, chapters, SponsorBlock, mini-player, danmaku, download.

// Sets up a watch page from the facts the server sent (#watch-config): the stream, the chapters, SponsorBlock segments, what plays next.
function startWatchPage(config) {
    const player = document.getElementById('player');
    if (!player) return;
    document.title = config.title + ' - Fathom';
    window.videoPlayer = player;

    // Landscape while fullscreen, where the browser allows it.
    player.addEventListener('fullscreen-change', (e) => {
        if (!screen.orientation) return;
        if (e.detail) {
            if (screen.orientation.lock) screen.orientation.lock('landscape').catch(() => {});
        } else if (screen.orientation.unlock) {
            screen.orientation.unlock();
        }
    });

    // Buffer further ahead than dash.js's default, so a stall on a slow link does not drain it, and keep played data for small rewinds.
    player.addEventListener('provider-change', (event) => {
        const provider = event.detail;
        if (provider && provider.type === 'dash') {
            provider.config = { streaming: { buffer: {
                stableBufferTime: 60,
                bufferTimeAtTopQuality: 90,
                bufferTimeAtTopQualityLongForm: 120,
                bufferToKeep: 30,
                avoidCurrentTimeRangePruning: true,
            } } };
        }
    });

    player.src = { src: config.src, type: 'application/dash+xml' };
    player.addEventListener('can-play', () => { player.play().catch((err) => console.error(err)); }, { once: true });

    const startAt = parseFloat(new URLSearchParams(window.location.search).get('start_time')) || 0;
    if (startAt > 0) player.addEventListener('can-play', () => { player.currentTime = startAt; }, { once: true });

    // currentTime and duration are direct properties of <media-player>.
    window.seekVideo = (delta) => {
        player.currentTime = Math.max(0, Math.min(config.duration || player.duration || 0, player.currentTime + delta));
    };

    const next = config.next || {};
    initAdvancedPlayerControls(player, next.url || '', next.title || '', next.thumb || '');
    if (config.sponsor.length) initSponsorBlockMarkers(player, config.sponsor);
    // The phone remote's chapter keys read this; cleared so a video without chapters does not inherit the last one's.
    window.remoteChapters = null;
    if (config.chapters.length) initChapterMarkers(player, config.chapters);
    initMiniPlayer(player);
    if (config.danmaku) initDanmakuOverlay(player, config.danmaku);
    initFullscreenLetterbox(player);
    initWatchProgressReporting(player, config.video, config.service);
    if (config.download) initDownloadButton(config.video, config.service);
}

// The audio page: the same player in its audio layout, and, inside the app, kept in step with the app's own audio playback.
function startAudioPage(config) {
    const audio = document.getElementById('audio-player');
    const cover = document.querySelector('.audio-cover');
    if (!audio) return;
    // The type is given explicitly: Vidstack cannot infer it from an address with no file extension.
    audio.src = { src: config.src, type: config.mime };
    audio.addEventListener('can-play', () => audio.play().catch(() => {}), { once: true });
    if (config.poster) audio.poster = config.poster;

    const app = window.NewPipeApp;
    audio.addEventListener('play', () => {
        if (cover) cover.classList.add('is-playing');
        if (app && app.resumeNativeAudio) app.resumeNativeAudio();
    });
    audio.addEventListener('pause', () => {
        if (cover) cover.classList.remove('is-playing');
        if (app && app.pauseNativeAudio) app.pauseNativeAudio();
    });
    audio.addEventListener('seeked', () => {
        if (app && app.seekNativeAudio) app.seekNativeAudio(Math.floor(audio.currentTime * 1000));
    });
    if (app && app.playNativeAudio) {
        // The app plays the sound; this player stays silent and follows the app's position.
        audio.muted = true;
        setInterval(() => {
            if (!app.getNativeAudioPosition) return;
            const seconds = app.getNativeAudioPosition() / 1000.0;
            if (seconds > 0 && Math.abs(audio.currentTime - seconds) > 1.5) audio.currentTime = seconds;
        }, 1000);
        app.playNativeAudio(window.location.origin + config.src, config.title, config.artist);
    }
}

// The player is started once its page is in place (see loader.js) and its element has been defined by Vidstack.
function bootWatchPage(scope) {
    const holder = (scope || document).querySelector('#watch-config');
    if (!holder) return;
    let config;
    try { config = JSON.parse(holder.textContent); } catch (e) { return; }
    customElements.whenDefined('media-player').then(() => startWatchPage(config));
}
document.addEventListener('fathom:content', (e) => bootWatchPage(e.target));
document.addEventListener('DOMContentLoaded', () => {
    const holder = document.getElementById('audio-config');
    if (!holder) return;
    customElements.whenDefined('media-player').then(() => startAudioPage(JSON.parse(holder.textContent)));
});

// Tabs under the video (About, Chapters, Comments, Related): one panel shows at a time.
function activateWatchTab(name) {
    document.querySelectorAll('.watch-tab').forEach(function(tab) {
        tab.classList.toggle('active', tab.dataset.panel === name);
    });
    document.querySelectorAll('.tab-panel').forEach(function(panel) {
        panel.classList.toggle('active', panel.dataset.panel === name);
    });
}
// Delegated, so it also covers a watch page injected after load (playing the next video in place).
document.addEventListener('click', function(e) {
    var tab = e.target.closest && e.target.closest('.watch-tab');
    if (tab) activateWatchTab(tab.dataset.panel);
});

// Player behaviors shared by every watch page. Per-request data is passed as call arguments, e.g. initChapterMarkers(player, [{s:12,t:"Intro"}]).
function initAdvancedPlayerControls(player, nextUrl, nextTitle, nextThumb) {
    const wrapper = player;
    
    
    function showDoubleTapRipple(side) {
        const ind = document.getElementById("double-tap-" + side);
        if (ind) {
            ind.classList.add("show");
            setTimeout(() => ind.classList.remove("show"), 650);
        }
    }
    
    // Swipe vertically on right side to adjust volume
    if (wrapper) {
        let touchStartY = 0;
        let initialVolume = 1;
        let isSwipeActive = false;
        
        wrapper.addEventListener("touchstart", function(e) {
            if (e.touches.length === 1) {
                const rect = wrapper.getBoundingClientRect();
                const touchX = e.touches[0].clientX - rect.left;
                if (touchX > rect.width * 0.5) {
                    touchStartY = e.touches[0].clientY;
                    initialVolume = player.volume;
                    isSwipeActive = true;
                }
            }
        }, { passive: true });
        
        wrapper.addEventListener("touchmove", function(e) {
            if (isSwipeActive && e.touches.length === 1) {
                e.preventDefault();
                const deltaY = touchStartY - e.touches[0].clientY;
                const rect = wrapper.getBoundingClientRect();
                const volumeChange = deltaY / (rect.height * 0.8);
                const newVolume = Math.max(0, Math.min(1, initialVolume + volumeChange));
                player.volume = newVolume;
                showVolumeHUD(Math.round(newVolume * 100));
            }
        }, { passive: false });
        
        wrapper.addEventListener("touchend", function() {
            isSwipeActive = false;
        });
    }
    
    let volumeHudTimeout = null;
    function showVolumeHUD(volumePercent) {
        const hud = document.getElementById("volume-hud-indicator");
        const text = document.getElementById("volume-hud-text");
        const icon = document.getElementById("volume-hud-icon");
        if (hud && text && icon) {
            text.innerText = volumePercent + "%";
            icon.textContent = volumePercent === 0 ? "volume_off" : volumePercent < 30 ? "volume_mute" : volumePercent < 70 ? "volume_down" : "volume_up";
            hud.classList.add("show");
            clearTimeout(volumeHudTimeout);
            volumeHudTimeout = setTimeout(() => hud.classList.remove("show"), 1000);
        }
    }
    
    // Double-tap seek and the k/j/l/m/f/c/i shortcuts are Vidstack's; this only drives the ripple and volume HUD. Arrow keys stay with the global handler (also TV focus navigation), so they are removed from Vidstack's set.
    player.keyShortcuts = { togglePaused: "k Space", toggleMuted: "m", toggleFullscreen: "f", togglePictureInPicture: "i", toggleCaptions: "c", seekBackward: "j J", seekForward: "l L", volumeUp: "", volumeDown: "" };
    player.addEventListener("media-seek-request", function(e) {
        if (typeof e.detail === "number") showDoubleTapRipple(e.detail < player.currentTime ? "left" : "right");
    });
    let lastMuted = player.muted;
    player.addEventListener("volume-change", function(e) {
        const muted = !!(e.detail && e.detail.muted);
        if (muted === lastMuted) return;
        lastMuted = muted;
        showVolumeHUD(muted ? 0 : Math.round(player.volume * 100));
    });
    
    // Autoplay Queue
    let autoplayTimer = null;
    let autoplayInterval = null;
    player.addEventListener("ended", function() {
        if (!nextUrl) return;
        const overlay = document.getElementById("autoplay-overlay");
        const titleEl = document.getElementById("autoplay-next-title");
        const thumbEl = document.getElementById("autoplay-next-thumb");
        const progressCircle = document.getElementById("autoplay-progress-circle");
        
        if (overlay && titleEl && thumbEl && progressCircle) {
            titleEl.innerText = nextTitle;
            thumbEl.src = nextThumb;
            overlay.classList.add("show");
            
            const totalDash = 138;
            progressCircle.style.strokeDashoffset = 0;
            
            autoplayTimer = setTimeout(() => {
                window.location.href = nextUrl;
            }, 5000);
            
            let elapsed = 0;
            autoplayInterval = setInterval(() => {
                elapsed += 100;
                const progress = elapsed / 5000;
                progressCircle.style.strokeDashoffset = totalDash * progress;
            }, 100);
        }
    });
    
    function clearAutoplay() {
        clearTimeout(autoplayTimer);
        clearInterval(autoplayInterval);
        const overlay = document.getElementById("autoplay-overlay");
        if (overlay) overlay.classList.remove("show");
    }
    
    const cancelBtn = document.getElementById("autoplay-cancel");
    if (cancelBtn) cancelBtn.addEventListener("click", clearAutoplay);
    
    const playNowBtn = document.getElementById("autoplay-play-now");
    if (playNowBtn) {
        playNowBtn.addEventListener("click", () => {
            if (nextUrl) window.location.href = nextUrl;
        });
    }
    
    
// Picture-in-Picture: provided by the default layout.
}

// Computes the letterboxed size in JS: CSS object-fit:contain alone still stretched the picture in mobile fullscreen.
function initFullscreenLetterbox(player) {
    function fitVideoLetterbox() {
        var videoTag = player.querySelector("video");
        if (!videoTag) return;
        if (!player.state.fullscreen) {
            videoTag.style.removeProperty("width");
            videoTag.style.removeProperty("height");
            videoTag.style.removeProperty("position");
            videoTag.style.removeProperty("top");
            videoTag.style.removeProperty("left");
            return;
        }
        // Read from the native <video>, the element being resized.
        var vw = videoTag.videoWidth || 16;
        var vh = videoTag.videoHeight || 9;
        var availW = window.innerWidth;
        var availH = window.innerHeight;
        var targetW, targetH;
        if (vw / vh > availW / availH) {
            targetW = availW;
            targetH = availW * vh / vw;
        } else {
            targetH = availH;
            targetW = availH * vw / vh;
        }
        videoTag.style.setProperty("width", targetW + "px", "important");
        videoTag.style.setProperty("height", targetH + "px", "important");
        videoTag.style.setProperty("position", "absolute", "important");
        videoTag.style.setProperty("top", ((availH - targetH) / 2) + "px", "important");
        videoTag.style.setProperty("left", ((availW - targetW) / 2) + "px", "important");
    }
    player.addEventListener("fullscreen-change", fitVideoLetterbox);
    player.addEventListener("loaded-metadata", fitVideoLetterbox);
    window.addEventListener("resize", fitVideoLetterbox);
    window.addEventListener("orientationchange", fitVideoLetterbox);
}

// videoId is already URL-encoded server-side; do not encode it again.
function initWatchProgressReporting(player, videoId, serviceId) {
    var lastReported = -1;
    function reportProgress() {
        var dur = player.duration;
        if (!dur) return;
        var percent = Math.round((player.currentTime / dur) * 100);
        if (percent === lastReported) return;
        lastReported = percent;
        var url = "/api/v1/watch_progress?id=" + videoId + "&serviceId=" + serviceId + "&percent=" + percent + "&durationSeconds=" + Math.round(dur);
        fetch(url, { keepalive: true }).catch(function() {});
    }
    var progressTimer = setInterval(reportProgress, 15000);
    player.addEventListener("pause", reportProgress);
    window.addEventListener("pagehide", reportProgress);
}

// Download button backed by /api/v1/download. The server is the source of truth; the button polls while a download is active, so it also reflects downloads started in the app.
function initDownloadButton(videoId, serviceId) {
    var btn = document.getElementById("download-btn");
    if (!btn) return;
    var state = "none", timer = null;
    function call(action) {
        return fetch("/api/v1/download?action=" + action + "&serviceId=" + serviceId + "&id=" + videoId)
            .then(function(res) { return res.json().then(function(body) { return { ok: res.ok, body: body }; }); });
    }
    function icon(name) { return '<span class="material-symbols-rounded">' + name + '</span>'; }
    function render(s) {
        state = s.state;
        btn.classList.remove("btn-filled", "btn-danger");
        btn.title = "";
        if (state === "pending" || state === "downloading") {
            btn.innerHTML = icon("downloading") + "Downloading " + s.progress + "%";
            btn.title = "Click to cancel";
        } else if (state === "paused") {
            btn.innerHTML = icon("pause_circle") + "Paused " + s.progress + "%";
            btn.title = "Click to cancel";
        } else if (state === "completed") {
            btn.classList.add("btn-filled");
            btn.innerHTML = icon("download_done") + "Downloaded";
            btn.title = "Click to delete the downloaded file";
        } else if (state === "failed") {
            btn.classList.add("btn-danger");
            btn.innerHTML = icon("error") + "Retry download";
        } else {
            btn.innerHTML = icon("download") + "Download";
        }
        var active = state === "pending" || state === "downloading" || state === "paused";
        if (active && !timer) timer = setInterval(refresh, 2000);
        if (!active && timer) { clearInterval(timer); timer = null; }
    }
    function refresh() {
        call("status").then(function(r) { if (r.ok) render(r.body); }).catch(function() {});
    }
    btn.addEventListener("click", function() {
        var action;
        if (state === "none" || state === "failed") action = "start";
        else if (state === "completed") { if (!confirm("Delete the downloaded file?")) return; action = "delete"; }
        else action = "cancel";
        call(action).then(function(r) {
            if (r.ok) render(r.body); else alert(r.body.error || "Download failed");
        }).catch(function() { alert("Could not reach the server"); });
    });
    refresh();
}

function initSponsorBlockMarkers(player, segments) {
    var skipBtn = document.getElementById("sponsor-skip-btn");
    var skipLabel = document.getElementById("sponsor-skip-label");
    var current = null;
    function placeMarkers() {
        var holder = player.querySelector("media-time-slider .vds-slider-track") || player.querySelector("media-time-slider");
        var dur = player.duration;
        if (!holder || !dur) return;
        segments.forEach(function(seg) {
            var marker = document.createElement("div");
            marker.className = "sponsor-segment-marker cat-" + seg.c;
            marker.style.left = (seg.s / dur * 100) + "%";
            marker.style.width = (Math.max(seg.e - seg.s, 0) / dur * 100) + "%";
            // min-width keeps very short segments visible and tappable on narrow screens without changing the real start/end times.
            marker.style.minWidth = "3px";
            holder.appendChild(marker);
        });
    }
    player.addEventListener("loaded-metadata", placeMarkers, { once: true });
    if (player.duration) placeMarkers();
    player.addEventListener("time-update", function() {
        var t = player.currentTime;
        var seg = null;
        for (var i = 0; i < segments.length; i++) {
            if (t >= segments[i].s && t < segments[i].e) { seg = segments[i]; break; }
        }
        if (seg) {
            if (current !== seg) {
                current = seg;
                if (skipLabel) skipLabel.textContent = "Skip " + seg.l;
                if (skipBtn) skipBtn.classList.add("visible");
            }
        } else if (current) {
            current = null;
            if (skipBtn) skipBtn.classList.remove("visible");
        }
    });
    if (skipBtn) skipBtn.addEventListener("click", function() {
        if (current) {
            player.currentTime = current.e;
            skipBtn.classList.remove("visible");
            current = null;
        }
    });
}

function initChapterMarkers(player, chapters) {
    var listEl = document.getElementById("chapters-list");
    var activeIndex = -1;
    // Vidstack draws chapter gaps and the chapter title from a chapters text track, built here from the chapter list.
    function pad(n, len) { var s = String(n); while (s.length < len) s = "0" + s; return s; }
    function vttTime(sec) {
        var ms = Math.round(sec * 1000);
        return pad(Math.floor(ms / 3600000), 2) + ":" + pad(Math.floor(ms / 60000) % 60, 2) + ":" + pad(Math.floor(ms / 1000) % 60, 2) + "." + pad(ms % 1000, 3);
    }
    var trackAdded = false;
    function addChapterTrack() {
        var dur = player.duration;
        if (trackAdded || !dur || !isFinite(dur)) return;
        trackAdded = true;
        var vtt = "WEBVTT\n\n";
        chapters.forEach(function(ch, i) {
            var end = i + 1 < chapters.length ? chapters[i + 1].s : dur;
            if (end <= ch.s) return;
            vtt += vttTime(ch.s) + " --> " + vttTime(end) + "\n" + ch.t.replace(/[\r\n]+/g, " ") + "\n\n";
        });
        player.textTracks.add({ kind: "chapters", label: "Chapters", language: "en-US", type: "vtt", default: true, content: vtt });
    }
    player.addEventListener("loaded-metadata", addChapterTrack, { once: true });
    player.addEventListener("duration-change", addChapterTrack);
    if (player.duration) addChapterTrack();
    player.addEventListener("time-update", function() {
        var t = player.currentTime;
        var idx = 0;
        for (var i = 0; i < chapters.length; i++) {
            if (t >= chapters[i].s) idx = i; else break;
        }
        if (idx === activeIndex) return;
        activeIndex = idx;
        if (listEl) {
            listEl.querySelectorAll(".chapter-item").forEach(function(el, i) {
                el.classList.toggle("active", i === idx);
            });
        }
    });
    window.seekToChapter = function(seconds) {
        player.currentTime = seconds;
        document.querySelector(".player-wrapper").scrollIntoView({ behavior: "smooth", block: "start" });
        // A remote picked the chapter from the list: hand the keys back to the player.
        if (window.returnToPlayer) window.returnToPlayer();
    };
    // For the phone remote's previous/next chapter keys. Previous restarts the chapter first, and only goes back a chapter when it has just begun.
    window.remoteChapters = {
        items: chapters,
        index: function() { return Math.max(0, activeIndex); },
        step: function(direction) {
            var idx = Math.max(0, activeIndex);
            if (direction > 0) {
                if (idx + 1 < chapters.length) player.currentTime = chapters[idx + 1].s;
            } else if (idx > 0 && player.currentTime - chapters[idx].s <= 3) {
                player.currentTime = chapters[idx - 1].s;
            } else {
                player.currentTime = chapters[idx].s;
            }
        }
    };
}

// Mini-player: shrinks .player-wrapper to a corner box once the sentinel is scrolled out. #player-space-holder keeps the layout height. Suspended in fullscreen; the close button pauses and disconnects the observer.
function initMiniPlayer(player) {
    var wrapper = document.querySelector(".player-wrapper");
    var holder = document.getElementById("player-space-holder");
    var sentinel = document.getElementById("mini-player-sentinel");
    var closeBtn = document.getElementById("mini-player-close-btn");
    var dragHandle = document.getElementById("mini-player-drag-handle");
    if (!wrapper || !holder || !sentinel) return;
    var isMini = false, dismissed = false;
    // A dragged position is stored in localStorage and clamped on every apply so it cannot end up off-screen after a resize.
    var POS_KEY = "miniPlayerPos";
    function applyPos(x, y) {
        var maxX = Math.max(0, window.innerWidth - wrapper.offsetWidth);
        var maxY = Math.max(0, window.innerHeight - wrapper.offsetHeight);
        wrapper.style.left = Math.min(Math.max(0, x), maxX) + "px";
        wrapper.style.top = Math.min(Math.max(0, y), maxY) + "px";
        wrapper.style.right = "auto";
        wrapper.style.bottom = "auto";
    }
    function clearPos() {
        wrapper.style.left = ""; wrapper.style.top = "";
        wrapper.style.right = ""; wrapper.style.bottom = "";
    }
    function restorePos() {
        try {
            var saved = JSON.parse(localStorage.getItem(POS_KEY));
            if (saved && typeof saved.x === "number" && typeof saved.y === "number") applyPos(saved.x, saved.y);
        } catch (e) {}
    }
    function enterMini() {
        if (isMini || dismissed || player.state.fullscreen) return;
        isMini = true;
        holder.style.height = wrapper.offsetHeight + "px";
        wrapper.classList.add("mini-player");
        restorePos();
    }
    function exitMini() {
        if (!isMini) return;
        isMini = false;
        wrapper.classList.remove("mini-player");
        clearPos();
        holder.style.height = "0px";
    }
    // Drag handle instead of the whole box, which Vidstack's own controls cover; pointer capture keeps the drag tracking.
    if (dragHandle) {
        var dragging = false, offX = 0, offY = 0;
        dragHandle.addEventListener("pointerdown", function(e) {
            var r = wrapper.getBoundingClientRect();
            dragging = true;
            offX = e.clientX - r.left;
            offY = e.clientY - r.top;
            dragHandle.setPointerCapture(e.pointerId);
            e.preventDefault();
        });
        dragHandle.addEventListener("pointermove", function(e) {
            if (dragging) applyPos(e.clientX - offX, e.clientY - offY);
        });
        function endDrag(e) {
            if (!dragging) return;
            dragging = false;
            try { dragHandle.releasePointerCapture(e.pointerId); } catch (x) {}
            var r = wrapper.getBoundingClientRect();
            try { localStorage.setItem(POS_KEY, JSON.stringify({ x: r.left, y: r.top })); } catch (x) {}
        }
        dragHandle.addEventListener("pointerup", endDrag);
        dragHandle.addEventListener("pointercancel", endDrag);
    }
    window.addEventListener("resize", function() {
        if (isMini && wrapper.style.left) applyPos(parseFloat(wrapper.style.left), parseFloat(wrapper.style.top));
    });
    // Observes the sentinel, not the wrapper: the wrapper moves when the mini-player toggles, which would retrigger the observer.
    var observer = new IntersectionObserver(function(entries) {
        if (entries[0].isIntersecting) exitMini(); else enterMini();
    }, { threshold: 0 });
    observer.observe(sentinel);
    player.addEventListener("fullscreen-change", function() {
        if (player.state.fullscreen) exitMini();
    });
    if (closeBtn) closeBtn.addEventListener("click", function() {
        player.pause();
        dismissed = true;
        exitMini();
        observer.disconnect();
    });
}

// Bilibili danmaku overlay. Scroll comments animate via transform (distance needs the measured player width); top/bottom comments are fixed fades. The extractor's lasting time is unreliable, so durations use Bilibili's defaults.
function initDanmakuOverlay(player, danmakuUrl) {
    var layer = document.getElementById("danmaku-layer");
    var toggleBtn = document.getElementById("danmaku-toggle-btn");
    if (!layer) return;
    // Defensive re-append into <media-player>, the fullscreen target.
    var danmakuHome = layer.parentNode, danmakuNextSibling = layer.nextSibling;
    player.addEventListener("fullscreen-change", function() {
        if (player.state.fullscreen) {
            player.appendChild(layer);
        } else if (danmakuHome) {
            danmakuHome.insertBefore(layer, danmakuNextSibling);
        }
    });
    var SCROLL_DURATION = 8, FIXED_DURATION = 4;
    var comments = [], nextIndex = 0, enabled = true;
    var scrollLaneUntil = new Array(14).fill(0);
    var topLaneUntil = new Array(4).fill(0);
    var bottomLaneUntil = new Array(4).fill(0);
    function pickLane(untilArr, lanes, now, dur) {
        for (var i = 0; i < lanes; i++) {
            if (untilArr[i] <= now) { untilArr[i] = now + dur; return i; }
        }
        var idx = 0;
        for (var i = 1; i < lanes; i++) { if (untilArr[i] < untilArr[idx]) idx = i; }
        untilArr[idx] = now + dur;
        return idx;
    }
    function spawn(item) {
        var h = layer.clientHeight || 200;
        var w = layer.clientWidth || 800;
        var el = document.createElement("div");
        el.className = "danmaku-item " + item.position;
        el.textContent = item.text;
        // Scaled by player width; scaling by height made comments cover the frame.
        el.style.fontSize = Math.max(14, Math.min(30, Math.round(w * item.size * 0.028))) + "px";
        el.style.color = item.color;
        var laneH = parseFloat(el.style.fontSize) * 1.5;
        var now = player.currentTime;
        if (item.position === "top" || item.position === "bottom") {
            var lanes = Math.max(1, Math.min(4, Math.floor(h * 0.35 / laneH)));
            var untilArr = item.position === "top" ? topLaneUntil : bottomLaneUntil;
            var lane = pickLane(untilArr, lanes, now, FIXED_DURATION);
            el.style[item.position] = (8 + lane * laneH) + "px";
            el.style.animation = "danmaku-fade " + FIXED_DURATION + "s linear";
            el.addEventListener("animationend", function() { el.remove(); });
            layer.appendChild(el);
        } else {
            var lanes = Math.max(1, Math.floor(h / laneH));
            var lane = pickLane(scrollLaneUntil, Math.min(14, lanes), now, SCROLL_DURATION);
            el.style.top = (lane * laneH) + "px";
            var startX = layer.clientWidth;
            el.style.transform = "translateX(" + startX + "px)";
            layer.appendChild(el);
            var endX = -el.offsetWidth;
            el.dataset.startX = startX; el.dataset.endX = endX; el.dataset.duration = SCROLL_DURATION;
            requestAnimationFrame(function() {
                el.style.transition = "transform " + SCROLL_DURATION + "s linear";
                el.style.transform = "translateX(" + endX + "px)";
            });
            el.addEventListener("transitionend", function() { el.remove(); });
        }
    }
    function tick() {
        if (!enabled || !comments.length) return;
        var t = player.currentTime;
        while (nextIndex < comments.length && comments[nextIndex].time <= t) {
            if (t - comments[nextIndex].time < 1.2) spawn(comments[nextIndex]);
            nextIndex++;
        }
    }
    function resync() {
        layer.innerHTML = "";
        scrollLaneUntil.fill(0); topLaneUntil.fill(0); bottomLaneUntil.fill(0);
        var t = player.currentTime;
        nextIndex = 0;
        while (nextIndex < comments.length && comments[nextIndex].time < t) nextIndex++;
    }
    player.addEventListener("time-update", tick);
    player.addEventListener("seeking", resync);
    player.addEventListener("pause", function() {
        layer.classList.add("video-paused");
        layer.querySelectorAll(".danmaku-item.scroll").forEach(function(el) {
            var m = new DOMMatrixReadOnly(getComputedStyle(el).transform);
            el.style.transition = "none";
            el.style.transform = "translateX(" + m.m41 + "px)";
            el.dataset.pausedX = m.m41;
        });
    });
    player.addEventListener("play", function() {
        layer.classList.remove("video-paused");
        layer.querySelectorAll(".danmaku-item.scroll").forEach(function(el) {
            if (el.dataset.pausedX === undefined) return;
            var startX = parseFloat(el.dataset.pausedX);
            var endX = parseFloat(el.dataset.endX);
            var totalDist = parseFloat(el.dataset.startX) - endX;
            var remainDist = startX - endX;
            var remainDur = totalDist > 0 ? parseFloat(el.dataset.duration) * (remainDist / totalDist) : 0;
            delete el.dataset.pausedX;
            if (remainDur <= 0) { el.remove(); return; }
            requestAnimationFrame(function() {
                el.style.transition = "transform " + remainDur + "s linear";
                el.style.transform = "translateX(" + endX + "px)";
            });
        });
    });
    if (toggleBtn) {
        toggleBtn.addEventListener("click", function() {
            enabled = !enabled;
            toggleBtn.classList.toggle("off", !enabled);
            if (!enabled) layer.innerHTML = "";
        });
    }
    fetch(danmakuUrl)
        .then(function(res) { return res.json(); })
        .then(function(data) {
            comments = (data.danmaku || []).sort(function(a, b) { return a.time - b.time; });
            resync();
        })
        .catch(function() {});
}

// The one player. It is created once and lives for the whole visit, so a video keeps playing (and stays fullscreen) while the
// screens around it change. Screens only ask it to open a video, dock it (full, mini, off) or do something to it.
(() => {
const h = FT.h;
const stageEl = () => FT.$('#stage');
const playerEl = () => FT.$('#player');

let current = null; // { service, url, info, chapters, segments }
let dockMode = 'off';
let danmaku = null;
let nextTimer = 0;
let lastReported = -1;
let hudTimer = 0;

const sizeStage = () => {
    // The picture is as wide as the window, but never taller than most of it.
    const height = Math.min(innerHeight * 0.64, (innerWidth * 9) / 16);
    document.documentElement.style.setProperty('--stage-h', Math.round(height) + 'px');
};
sizeStage();
addEventListener('resize', FT.frame(sizeStage));

const hud = (icon, text) => {
    const box = FT.$('#hud');
    if (!box) return;
    box.replaceChildren(FT.icon(icon), text);
    box.hidden = false;
    clearTimeout(hudTimer);
    hudTimer = setTimeout(() => { box.hidden = true; }, 1100);
};

const isFullscreen = () => {
    const p = playerEl();
    return document.body.classList.contains('remote-fs') || !!document.fullscreenElement || !!document.webkitFullscreenElement || !!(p && p.state && p.state.fullscreen);
};

// ---- Chapters, SponsorBlock and the comment overlay ----

const vttTime = (sec) => {
    const ms = Math.round(sec * 1000);
    const pad = (n, len) => String(n).padStart(len, '0');
    return `${pad(Math.floor(ms / 3600000), 2)}:${pad(Math.floor(ms / 60000) % 60, 2)}:${pad(Math.floor(ms / 1000) % 60, 2)}.${pad(ms % 1000, 3)}`;
};

// Vidstack draws chapter gaps and the chapter name on the seek bar from a chapters text track built from the list.
const addChapterTrack = () => {
    const p = playerEl();
    if (!current || !current.chapters.length || current.chapterTrack || !p.duration || !isFinite(p.duration)) return;
    current.chapterTrack = true;
    const list = current.chapters;
    let vtt = 'WEBVTT\n\n';
    list.forEach((ch, i) => {
        const end = i + 1 < list.length ? list[i + 1].s : p.duration;
        if (end > ch.s) vtt += `${vttTime(ch.s)} --> ${vttTime(end)}\n${ch.t.replace(/[\r\n]+/g, ' ')}\n\n`;
    });
    try { p.textTracks.add({ kind: 'chapters', label: 'Chapters', language: 'en-US', type: 'vtt', default: true, content: vtt }); } catch (e) {}
};

const placeMarks = () => {
    const p = playerEl();
    if (!current || !current.segments.length || !p.duration) return;
    const track = FT.$('media-time-slider .vds-slider-track', p) || FT.$('media-time-slider', p);
    if (!track) return;
    FT.$$('.sb-mark', p).forEach((m) => m.remove());
    for (const seg of current.segments) {
        track.append(h('div', { class: 'sb-mark ' + seg.c, style: `left:${(seg.s / p.duration) * 100}%;width:${(Math.max(seg.e - seg.s, 0) / p.duration) * 100}%` }));
    }
};

let skipTarget = null;
const watchSegments = () => {
    const p = playerEl();
    const btn = FT.$('#skip-btn');
    if (!current || !btn) return;
    const t = p.currentTime;
    const seg = current.segments.find((s) => t >= s.s && t < s.e) || null;
    if (seg === skipTarget) return;
    skipTarget = seg;
    btn.hidden = !seg;
    if (seg) btn.textContent = 'Skip ' + seg.l;
};

const chapterIndex = () => {
    const p = playerEl();
    if (!current || !current.chapters.length) return -1;
    let index = 0;
    current.chapters.forEach((ch, i) => { if (p.currentTime >= ch.s) index = i; });
    return index;
};

// Comments flying across the picture (Bilibili). Each one is a CSS animation, so pausing the video pauses them too.
const startDanmaku = (url) => {
    const p = playerEl();
    stopDanmaku();
    const layer = h('div', { class: 'danmaku-layer' });
    p.append(layer);
    const state = { layer, items: [], next: 0, on: true, lanes: new Array(14).fill(0) };
    danmaku = state;
    const spawn = (item) => {
        const width = layer.clientWidth || 800;
        const size = Math.max(14, Math.min(30, Math.round(width * (item.size || 1) * 0.028)));
        const el = h('div', { class: 'danmaku-item ' + (item.position || 'scroll') }, item.text);
        el.style.fontSize = size + 'px';
        el.style.color = item.color || '#fff';
        const lane = Math.min(state.lanes.length - 1, state.lanes.findIndex((until) => until <= p.currentTime) < 0 ? Math.floor(Math.random() * 6) : state.lanes.findIndex((until) => until <= p.currentTime));
        state.lanes[lane] = p.currentTime + 6;
        const laneHeight = size * 1.5;
        if (item.position === 'top') el.style.top = 8 + (lane % 4) * laneHeight + 'px';
        else if (item.position === 'bottom') el.style.bottom = 8 + (lane % 4) * laneHeight + 'px';
        else {
            el.style.top = lane * laneHeight + 'px';
            el.style.setProperty('--from', width + 'px');
            el.style.setProperty('--to', '-100%');
            el.style.setProperty('--dur', '8s');
        }
        el.addEventListener('animationend', () => el.remove());
        layer.append(el);
    };
    state.tick = () => {
        if (!state.on) return;
        const t = p.currentTime;
        while (state.next < state.items.length && state.items[state.next].time <= t) {
            if (t - state.items[state.next].time < 1.2) spawn(state.items[state.next]);
            state.next++;
        }
    };
    state.resync = () => {
        layer.replaceChildren();
        state.lanes.fill(0);
        state.next = state.items.findIndex((c) => c.time >= p.currentTime);
        if (state.next < 0) state.next = state.items.length;
    };
    state.pause = () => layer.classList.add('paused');
    state.play = () => layer.classList.remove('paused');
    p.addEventListener('time-update', state.tick);
    p.addEventListener('seeking', state.resync);
    p.addEventListener('pause', state.pause);
    p.addEventListener('play', state.play);
    fetch(url).then((res) => res.json()).then((data) => {
        state.items = (data.danmaku || []).sort((a, b) => a.time - b.time);
        state.resync();
    }).catch(() => {});
};

const stopDanmaku = () => {
    if (!danmaku) return;
    const p = playerEl();
    p.removeEventListener('time-update', danmaku.tick);
    p.removeEventListener('seeking', danmaku.resync);
    p.removeEventListener('pause', danmaku.pause);
    p.removeEventListener('play', danmaku.play);
    danmaku.layer.remove();
    danmaku = null;
};

// ---- Progress and what comes next ----

const reportProgress = () => {
    const p = playerEl();
    if (!current || !p.duration) return;
    const percent = Math.round((p.currentTime / p.duration) * 100);
    if (percent === lastReported) return;
    lastReported = percent;
    FT.api.progress(current.url, current.service, percent, Math.round(p.duration));
};
setInterval(reportProgress, 15000);
addEventListener('pagehide', reportProgress);

const cancelNext = () => {
    clearTimeout(nextTimer);
    nextTimer = 0;
    const box = FT.$('#hud');
    if (box && !box.hidden && box.dataset.next) box.hidden = true;
};

const scheduleNext = () => {
    const next = current && current.info.relatedVideos && current.info.relatedVideos[0];
    if (!next) return;
    const box = FT.$('#hud');
    box.dataset.next = '1';
    box.replaceChildren(FT.icon('skip_next'), 'Up next in 6 s: ' + next.title + '  (Back to cancel)');
    box.hidden = false;
    nextTimer = setTimeout(() => {
        box.hidden = true;
        delete box.dataset.next;
        location.hash = FT.link.watch(next);
    }, 6000);
};

// ---- The public side ----

FT.player = {
    get current() { return current; },
    isFullscreen,

    // Loads a video from /api/v1/video's answer. Opening the one that is already loaded changes nothing.
    async open(service, info) {
        if (current && current.url === info.url) return;
        await customElements.whenDefined('media-player');
        const p = playerEl();
        cancelNext();
        stopDanmaku();
        reportProgress();
        lastReported = -1;
        current = { service, url: info.url, info, chapters: info.chapters || [], segments: [], chapterTrack: false };
        skipTarget = null;
        FT.$('#skip-btn').hidden = true;
        FT.$$('.sb-mark', p).forEach((m) => m.remove());
        try { Array.from(p.textTracks).forEach((t) => p.textTracks.remove(t)); } catch (e) {}
        p.title = info.title;
        // Video streams come as a DASH manifest; anything else (audio only) is one stream whose type the player works out itself.
        p.src = info.playback.isDash ? { src: info.playback.manifestUrl, type: 'application/dash+xml' } : info.playback.streamUrl;
        for (const sub of info.subtitles || []) {
            try { p.textTracks.add({ src: sub.url, kind: 'subtitles', label: sub.displayName + (sub.isAutoGenerated ? ' (auto)' : ''), language: sub.languageTag || 'en', type: 'vtt' }); } catch (e) {}
        }
        p.addEventListener('can-play', () => { p.play().catch(() => {}); }, { once: true });
        FT.api.sponsor(service, info.url).then((body) => {
            if (!current || current.url !== info.url) return;
            current.segments = body.segments || [];
            placeMarks();
        }).catch(() => {});
        if (service === 5) startDanmaku(`/danmaku?serviceId=${service}&id=${FT.enc(info.url)}`);
        FT.player.dock(dockMode === 'off' ? 'full' : dockMode);
    },

    dock(mode) {
        dockMode = current ? mode : 'off';
        const stage = stageEl();
        stage.hidden = dockMode === 'off';
        stage.classList.toggle('mini', dockMode === 'mini');
        FT.$('#mini-close').hidden = dockMode !== 'mini';
    },

    close() {
        const p = playerEl();
        cancelNext();
        stopDanmaku();
        reportProgress();
        try { p.pause(); } catch (e) {}
        p.src = '';
        current = null;
        document.body.classList.remove('remote-fs');
        FT.player.dock('off');
    },

    get danmaku() { return danmaku; },
    toggleDanmaku() {
        if (!danmaku) return false;
        danmaku.on = !danmaku.on;
        if (!danmaku.on) danmaku.layer.replaceChildren();
        else danmaku.resync();
        return danmaku.on;
    },

    // Keys and the phone remote.
    cancelNext,
    toggle() {
        const p = playerEl();
        if (!current) return;
        if (p.paused) p.play().catch(() => {}); else p.pause();
        hud(p.paused ? 'play_arrow' : 'pause', p.paused ? 'Play' : 'Pause');
    },
    seek(delta) {
        const p = playerEl();
        if (!current) return;
        const limit = current.info.duration || p.duration || 0;
        p.currentTime = Math.max(0, Math.min(limit || Infinity, p.currentTime + delta));
        hud(delta > 0 ? 'fast_forward' : 'fast_rewind', (delta > 0 ? '+' : '') + delta + ' s');
    },
    seekTo(sec) {
        const p = playerEl();
        if (current && isFinite(sec)) p.currentTime = Math.max(0, sec);
    },
    volume(delta) {
        const p = playerEl();
        p.muted = false;
        p.volume = Math.max(0, Math.min(1, (p.volume || 0) + delta));
        hud(p.volume === 0 ? 'volume_off' : p.volume < 0.5 ? 'volume_down' : 'volume_up', Math.round(p.volume * 100) + '%');
    },
    mute() {
        const p = playerEl();
        p.muted = !p.muted;
        hud(p.muted ? 'volume_off' : 'volume_up', p.muted ? 'Muted' : Math.round(p.volume * 100) + '%');
    },
    async fullscreen(want) {
        const p = playerEl();
        if (!current) return;
        const on = isFullscreen();
        const enter = want === undefined ? !on : want;
        if (!enter) {
            document.body.classList.remove('remote-fs');
            if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(() => {});
            try { const left = p.exitFullscreen && p.exitFullscreen(); if (left && left.catch) left.catch(() => {}); } catch (e) {}
            return;
        }
        // A browser only grants real fullscreen right after a click on the page itself, which a remote never makes; if it is refused, fill the window.
        try { const asked = p.enterFullscreen && p.enterFullscreen(); if (asked && asked.catch) asked.catch(() => {}); } catch (e) {}
        setTimeout(() => { if (!isFullscreen()) document.body.classList.add('remote-fs'); }, 300);
    },
    stepChapter(direction) {
        const p = playerEl();
        const list = current ? current.chapters : [];
        if (!list.length) return;
        const i = Math.max(0, chapterIndex());
        if (direction > 0) { if (i + 1 < list.length) p.currentTime = list[i + 1].s; }
        else if (i > 0 && p.currentTime - list[i].s <= 3) p.currentTime = list[i - 1].s;
        else p.currentTime = list[i].s;
    },

    // What the phone remote shows.
    snapshot() {
        const p = playerEl();
        const i = chapterIndex();
        return {
            open: !!current, title: current ? current.info.title : '', t: p.currentTime || 0, d: p.duration || 0, paused: !!p.paused,
            vol: p.volume == null ? 1 : p.volume, muted: !!p.muted, fs: isFullscreen(),
            chap: i >= 0 ? current.chapters[i].t : '', chapn: current ? current.chapters.length : 0,
        };
    },
    skip() {
        const btn = FT.$('#skip-btn');
        if (!skipTarget) return false;
        playerEl().currentTime = skipTarget.e;
        btn.hidden = true;
        skipTarget = null;
        return true;
    },
    hasSkip: () => !!skipTarget,
    chapterIndex,
};

// Wiring that only needs the element to exist.
const wire = () => {
    const p = playerEl();
    if (!p) return;
    p.addEventListener('loaded-metadata', () => { addChapterTrack(); placeMarks(); });
    p.addEventListener('duration-change', addChapterTrack);
    p.addEventListener('time-update', watchSegments);
    p.addEventListener('pause', reportProgress);
    p.addEventListener('ended', scheduleNext);
    p.addEventListener('fullscreen-change', (e) => {
        if (!screen.orientation) return;
        if (e.detail) { if (screen.orientation.lock) screen.orientation.lock('landscape').catch(() => {}); }
        else if (screen.orientation.unlock) screen.orientation.unlock();
    });
    // A longer buffer than dash.js's default, so a stall on a slow link does not drain it.
    p.addEventListener('provider-change', (event) => {
        const provider = event.detail;
        if (provider && provider.type === 'dash') {
            provider.config = { streaming: { buffer: { stableBufferTime: 60, bufferTimeAtTopQuality: 90, bufferTimeAtTopQualityLongForm: 120, bufferToKeep: 30, avoidCurrentTimeRangePruning: true } } };
        }
    });
    FT.$('#skip-btn').addEventListener('click', () => FT.player.skip());
    FT.$('#mini-close').addEventListener('click', () => FT.player.close());
    FT.$('#stage').addEventListener('click', (e) => { if (stageEl().classList.contains('mini') && !e.target.closest('#mini-close') && current) location.hash = FT.link.watch({ url: current.url, serviceId: current.service }); });
};
wire();
})();

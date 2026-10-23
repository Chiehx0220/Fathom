// The one player. It is created once and lives for the whole visit, so a video keeps playing (and stays fullscreen) while the
// screens around it change. Screens only ask it to open a video, dock it (full, mini, off) or do something to it.
(() => {
const h = FT.h;
const stageEl = () => FT.$('#stage');

// The player of the open video is built per video (build() below): a Video.js player, its skin and the media component that fits the stream,
// all inside #player. playerEl() is the media element, which has the HTMLMediaElement API (paused, currentTime, play(), events);
// tree.host is the <video-player>, whose store carries fullscreen, renditions and tracks. Before anything is open there is only IDLE.
let tree = null; // { host, skin, media, live, chapterUrl }
const IDLE = {
    paused: true, currentTime: 0, duration: 0, volume: 1, muted: false,
    play: () => Promise.resolve(), pause() {}, addEventListener() {}, removeEventListener() {},
};
const playerEl = () => (tree ? tree.media : IDLE);

let current = null; // { service, url, info, chapters, segments }
let dockMode = 'off';
let miniDismissed = false; // closed from the corner on the watch page: paused, back in place when scrolled to the top
let danmaku = null;
let upNext = null; // the open "Up next" card: { el, timer, endAt, ring, count }
let lastReported = -1;
let hudTimer = 0;
let audioOnly = false;

// ---- Mini player: drag-to-reposition, tap-to-expand, and its own controls (media-video-layout
// is hidden at this size, so close/play/expand are the only interactive surface it has). Every
// control shares the .mini-ctrl class, so drag suppression and click dispatch use one selector
// each rather than one-off wiring per button. ----
const MINI_CTRL = '.mini-ctrl';

let miniPos = null; // {x, y} pixel override past the CSS corner default; session-lived, not persisted
let drag = null; // {pointerId, startX, startY, originX, originY, moved}

const clampMiniPos = (x, y) => {
    const stage = stageEl();
    return {
        x: Math.max(8, Math.min(innerWidth - stage.offsetWidth - 8, x)),
        y: Math.max(8, Math.min(innerHeight - stage.offsetHeight - 8, y)),
    };
};

const placeMini = () => {
    const stage = stageEl();
    if (!stage.classList.contains('mini') || !miniPos) { stage.style.left = stage.style.top = stage.style.right = stage.style.bottom = ''; return; }
    stage.style.right = stage.style.bottom = 'auto';
    stage.style.left = miniPos.x + 'px';
    stage.style.top = miniPos.y + 'px';
};

// The hash of this video's own watch page does not change, so there the way back is the top of the page.
const openFull = () => {
    if (!current) return;
    if (FT.route.name === 'watch' && FT.route.params.u === current.url) scrollTo({ top: 0, behavior: 'smooth' });
    else location.hash = FT.link.watch({ url: current.url, serviceId: current.service });
};

const onMiniPointerDown = (e) => {
    const stage = stageEl();
    if (!stage.classList.contains('mini') || e.target.closest(MINI_CTRL)) return;
    const rect = stage.getBoundingClientRect();
    drag = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, originX: rect.left, originY: rect.top, moved: false };
    try { stage.setPointerCapture(e.pointerId); } catch (err) {}
};
const onMiniPointerMove = (e) => {
    if (!drag || drag.pointerId !== e.pointerId) return;
    const dx = e.clientX - drag.startX, dy = e.clientY - drag.startY;
    if (!drag.moved && Math.hypot(dx, dy) < 6) return;
    if (!drag.moved) { drag.moved = true; stageEl().classList.add('dragging'); }
    e.preventDefault();
    miniPos = clampMiniPos(drag.originX + dx, drag.originY + dy);
    placeMini();
};
const onMiniPointerUp = (e) => {
    if (!drag || drag.pointerId !== e.pointerId) return;
    const stage = stageEl();
    try { stage.releasePointerCapture(e.pointerId); } catch (err) {}
    stage.classList.remove('dragging');
    if (!drag.moved) drag = null; // else left set, for onMiniClick to swallow the trailing click
};
// Single dispatch point for taps on the mini player: a completed drag is swallowed, #mini-play
// toggles playback, #mini-close closes, everything else (including #mini-expand) opens the full page.
const onMiniClick = (e) => {
    if (drag && drag.moved) { drag = null; return; }
    if (!stageEl().classList.contains('mini')) return;
    if (e.target.closest('#mini-play')) FT.player.toggle();
    else if (e.target.closest('#mini-close')) FT.player.dismissMini();
    else openFull();
};

const sizeStage = () => {
    // The picture is as wide as the window, but never taller than most of it.
    const height = Math.min(innerHeight * 0.64, (innerWidth * 9) / 16);
    document.documentElement.style.setProperty('--stage-h', Math.round(height) + 'px');
};
sizeStage();
addEventListener('resize', FT.frame(() => { sizeStage(); if (miniPos) { miniPos = clampMiniPos(miniPos.x, miniPos.y); placeMini(); } }));

const hud = (icon, text) => {
    const box = FT.$('#hud');
    if (!box) return;
    box.replaceChildren(FT.icon(icon), text);
    box.hidden = false;
    clearTimeout(hudTimer);
    hudTimer = setTimeout(() => { box.hidden = true; }, 1100);
};

const paintMiniPlay = () => {
    const btn = FT.$('#mini-play');
    if (!btn) return;
    const paused = playerEl().paused;
    btn.replaceChildren(FT.icon(paused ? 'play_arrow' : 'pause'));
    btn.setAttribute('aria-label', FT.t(paused ? 'play' : 'pause'));
};

const isFullscreen = () => document.body.classList.contains('remote-fs') || !!document.fullscreenElement || !!document.webkitFullscreenElement
    || !!(tree && tree.host.store && tree.host.store.isFullscreen);

// ---- Chapters, SponsorBlock and the comment overlay ----

const vttTime = (sec) => {
    const ms = Math.round(sec * 1000);
    const pad = (n, len) => String(n).padStart(len, '0');
    return `${pad(Math.floor(ms / 3600000), 2)}:${pad(Math.floor(ms / 60000) % 60, 2)}:${pad(Math.floor(ms / 1000) % 60, 2)}.${pad(ms % 1000, 3)}`;
};

// The skin draws chapter gaps and the chapter name on the seek bar from a chapters text track built from the list.
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
    if (!tree) return;
    tree.chapterUrl = URL.createObjectURL(new Blob([vtt], { type: 'text/vtt' }));
    p.append(track('chapters', FT.t('chapters'), 'en', tree.chapterUrl, true));
};

// The marks go into the skin's time slider, which lives in the skin's open shadow root (their rules are in SKIN_STYLE). That is inside the
// packaged skin, which Video.js does not promise to keep, so a new Video.js version means checking this still finds the slider.
const placeMarks = () => {
    const p = playerEl();
    if (!tree || !current || !current.segments.length || !p.duration) return;
    const slider = tree.skin.shadowRoot && tree.skin.shadowRoot.querySelector('media-time-slider');
    if (!slider) return;
    slider.querySelectorAll('.sb-mark').forEach((m) => m.remove());
    for (const seg of current.segments) {
        slider.append(h('div', { class: 'sb-mark ' + seg.c, style: `left:${(seg.s / p.duration) * 100}%;width:${(Math.max(seg.e - seg.s, 0) / p.duration) * 100}%` }));
    }
};

let skipTarget = null;
// The rendition id the viewer picked, or 'auto'; the player itself only says which one is playing.
let chosenQuality = 'auto';

// The qualities the remote can choose between, tallest first and one per label; fewer than two is nothing to choose.
const qualityLabel = (r) => (r.height ? r.height + 'p' + (r.frameRate > 30 ? Math.round(r.frameRate) : '') : '');
const qualitySnapshot = () => {
    const store = tree && tree.host.store;
    const list = (store && store.videoRenditionList) || [];
    const seen = new Map();
    list.forEach((r) => {
        const label = qualityLabel(r);
        if (label && (!seen.has(label) || (r.bitrate || 0) > (seen.get(label).bitrate || 0))) seen.set(label, r);
    });
    const options = [...seen.entries()]
        .sort((a, b) => (b[1].height || 0) - (a[1].height || 0) || (b[1].frameRate || 0) - (a[1].frameRate || 0))
        .map(([label, r]) => [String(r.id), label]);
    const active = store && store.activeVideoRendition;
    return {
        qualities: options.length > 1 ? options : [],
        quality: active ? qualityLabel(active) : '',
        qchosen: chosenQuality,
    };
};
// Server labels are English; translated via segment_* keys when present.
const segmentName = (label) => FT.t('segment_' + String(label).replace(/[ -]/g, '_'), null, label);

const watchSegments = () => {
    const p = playerEl();
    const btn = FT.$('#skip-btn');
    if (!current || !btn) return;
    const t = p.currentTime;
    const seg = current.segments.find((s) => t >= s.s && t < s.e) || null;
    if (seg === skipTarget) return;
    skipTarget = seg;
    btn.hidden = !seg;
    if (seg) btn.textContent = FT.t('skip', { label: segmentName(seg.l) });
};

const chapterIndex = () => {
    const p = playerEl();
    if (!current || !current.chapters.length) return -1;
    let index = 0;
    current.chapters.forEach((ch, i) => { if (p.currentTime >= ch.s) index = i; });
    return index;
};

// Rows appended to the skin's settings menu (open shadow root), cloned from a native row for styling.
const DANMAKU_ROW = 'danmaku-row';
const SHORTCUTS_ROW = 'shortcuts-row';
const CHAT_ICON = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z"/></svg>';
const KEYBOARD_ICON = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M20 5H4c-1.1 0-1.99.9-1.99 2L2 17c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm-9 3h2v2h-2V8zm0 3h2v2h-2v-2zM8 8h2v2H8V8zm0 3h2v2H8v-2zm-1 2H5v-2h2v2zm0-3H5V8h2v2zm9 7H8v-2h8v2zm0-4h-2v-2h2v2zm0-3h-2V8h2v2zm3 3h-2v-2h2v2zm0-3h-2V8h2v2z"/></svg>';
const DANMAKU_HINT_KEY = 'fathom-hint-danmaku';
let menuRowsWatch = null;

// `valued`: a switch row keeping the native value slot (On/Off); otherwise a plain action row.
const buildMenuRow = (content, { id, svg, label, valued, onSelect }) => {
    const model = content.querySelector(':scope > media-menu-item');
    if (!model) return null;
    const row = model.cloneNode(true);
    ['commandfor', 'aria-haspopup', 'aria-expanded', 'data-has-submenu', 'data-highlighted', 'data-availability', 'disabled', 'aria-disabled', 'hidden']
        .forEach((name) => row.removeAttribute(name));
    row.id = id;
    const text = row.querySelector('media-text');
    text.removeAttribute('token');
    text.textContent = label;
    const chevron = row.querySelector('media-icon[name="chevron"]');
    if (chevron) chevron.remove();
    if (!valued) {
        const hint = row.querySelector('.media-menu-hint');
        if (hint) hint.remove();
    }
    // <media-icon> resolves icons by name and has none of these, so a same-class element carries the SVG.
    const icon = row.querySelector('media-icon');
    if (icon) {
        const glyph = h('span', { class: icon.className });
        glyph.innerHTML = svg;
        icon.replaceWith(glyph);
    }
    row.addEventListener('select', onSelect);
    return row;
};

const paintMenuRows = () => {
    const root = tree && tree.skin.shadowRoot;
    const content = root && root.querySelector('media-menu-content');
    if (!content) return;

    let shortcuts = root.getElementById(SHORTCUTS_ROW);
    if (!shortcuts) {
        shortcuts = buildMenuRow(content, { id: SHORTCUTS_ROW, svg: KEYBOARD_ICON, label: FT.t('shortcuts_title'), onSelect: () => FT.shortcuts.open() });
        if (shortcuts) content.append(shortcuts);
    }

    let row = root.getElementById(DANMAKU_ROW);
    if (!danmaku) { if (row) row.remove(); return; }
    if (!row) {
        // Cancelling `select` keeps the menu open, so the switch flips in place.
        row = buildMenuRow(content, {
            id: DANMAKU_ROW, svg: CHAT_ICON, label: FT.t('danmaku'), valued: true,
            onSelect: (e) => { e.preventDefault(); FT.player.toggleDanmaku(); },
        });
        if (!row) return;
        content.insertBefore(row, shortcuts || null);
    }
    row.setAttribute('aria-checked', String(danmaku.on));
    const value = row.querySelector('[data-part="value"]');
    const text = FT.t(danmaku.on ? 'state_on' : 'state_off');
    if (value && value.textContent !== text) value.textContent = text;
};

// The skin renders the menu lazily and may re-render it; re-assert the rows on every mutation.
const watchMenuRows = () => {
    if (menuRowsWatch) menuRowsWatch.disconnect();
    menuRowsWatch = null;
    const root = tree && tree.skin.shadowRoot;
    if (!root) return;
    menuRowsWatch = new MutationObserver(paintMenuRows);
    menuRowsWatch.observe(root, { childList: true, subtree: true });
    paintMenuRows();
};

// One-time callout (persisted in localStorage) pointing at the menu.
const hintDanmaku = () => {
    let seen = true;
    try { seen = !!localStorage.getItem(DANMAKU_HINT_KEY); } catch (e) {}
    if (seen || !tree) return;
    try { localStorage.setItem(DANMAKU_HINT_KEY, '1'); } catch (e) {}
    const tip = h('div', { class: 'dm-hint', role: 'status' }, FT.icon('chat'), FT.t('danmaku_hint'));
    tip.addEventListener('click', () => tip.remove());
    tree.skin.append(tip);
    setTimeout(() => tip.remove(), 8000);
};

// Bilibili danmaku overlay: one CSS animation per comment, so pausing the media pauses them.
const startDanmaku = (url, live = false) => {
    const p = playerEl();
    stopDanmaku();
    const layer = h('div', { class: 'danmaku-layer' });
    (tree ? tree.skin : stageEl()).append(layer);
    const state = { layer, media: p, items: [], next: 0, on: true, lanes: new Array(14).fill(0) };
    danmaku = state;
    paintMenuRows();
    hintDanmaku();
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
    p.addEventListener('timeupdate', state.tick);
    p.addEventListener('seeking', state.resync);
    p.addEventListener('pause', state.pause);
    p.addEventListener('play', state.play);
    if (live) {
        // A live room has no timeline: its chat is polled, and each message is shown as it arrives.
        let since = null;
        let timer = 0;
        const poll = async () => {
            try {
                const data = await (await fetch(url + (since === null ? '' : '&since=' + since))).json();
                since = data.seq;
                if (state.on && !p.paused) (data.items || []).slice(-30).forEach(spawn);
            } catch (e) {}
            if (danmaku === state) timer = setTimeout(poll, 1500);
        };
        state.stop = () => clearTimeout(timer);
        poll();
        return;
    }
    fetch(url).then((res) => res.json()).then((data) => {
        state.items = (data.danmaku || []).sort((a, b) => a.time - b.time);
        state.resync();
    }).catch(() => {});
};

const stopDanmaku = () => {
    if (!danmaku) return;
    if (danmaku.stop) danmaku.stop();
    const p = danmaku.media;
    p.removeEventListener('timeupdate', danmaku.tick);
    p.removeEventListener('seeking', danmaku.resync);
    p.removeEventListener('pause', danmaku.pause);
    p.removeEventListener('play', danmaku.play);
    danmaku.layer.remove();
    danmaku = null;
    paintMenuRows();
};

// ---- Progress and what comes next ----

// Timer reports are silent; pause, close and switch notify history subscribers.
const reportProgress = (quiet) => {
    const p = playerEl();
    if (!current || !p.duration || !isFinite(p.duration)) return;
    const percent = Math.round((p.currentTime / p.duration) * 100);
    if (percent === lastReported) return;
    lastReported = percent;
    FT.api.progress(current.url, current.service, percent, Math.round(p.duration), { notify: quiet !== true });
};
setInterval(() => reportProgress(true), 15000);
addEventListener('pagehide', reportProgress);

// ---- Up next: the card shown when a video ends ----

const UP_NEXT_SECONDS = 8;
const RING = 88; // circumference of the countdown ring's circle (r = 14)
const svgEl = (tag, attrs) => {
    const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
    return el;
};

const autoplayOn = () => loadPrefs().autoplay !== false;
const setAutoplay = (on) => {
    try { localStorage.setItem(PREFS_KEY, JSON.stringify({ ...loadPrefs(), autoplay: on })); } catch (e) {}
};

// Closes the card, stops its countdown and gives the keys back. Safe to call when there is none.
const cancelNext = () => {
    if (!upNext) return;
    clearInterval(upNext.timer);
    upNext.el.remove();
    upNext = null;
    FT.focus.trap(null);
    FT.focus.clear();
};

const playNext = (next) => {
    cancelNext();
    location.hash = FT.link.watch(next);
};

const tickUpNext = (next) => {
    if (!upNext) return;
    const left = Math.max(0, upNext.endAt - performance.now());
    upNext.count.textContent = String(Math.ceil(left / 1000));
    upNext.ring.style.strokeDashoffset = String(RING * (1 - left / (UP_NEXT_SECONDS * 1000)));
    if (left <= 0) playNext(next);
};

const scheduleNext = () => {
    const next = current && current.info.relatedVideos && current.info.relatedVideos[0];
    // A video that ends in the mini player, with the viewer elsewhere on the site, does not pull the page away to the next one.
    if (!next || upNext || !tree || dockMode !== 'full') return;
    const counting = autoplayOn();
    const ring = svgEl('circle', { cx: 17, cy: 17, r: 14, fill: 'none', 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-dasharray': RING, class: 'upnext-bar' });
    const count = h('b', {}, String(UP_NEXT_SECONDS));
    const dial = svgEl('svg', { viewBox: '0 0 34 34' });
    dial.append(svgEl('circle', { cx: 17, cy: 17, r: 14, fill: 'none', 'stroke-width': 3, class: 'upnext-track' }), ring);
    const play = h('button', { type: 'button', class: 'upnext-btn primary', data: { f: '', key: 'upnext-play' }, onclick: () => playNext(next) }, FT.t(counting ? 'play_now' : 'play_next'));
    const cancel = h('button', { type: 'button', class: 'upnext-btn', data: { f: '', key: 'upnext-cancel' }, onclick: cancelNext }, FT.t('cancel'));
    const dialBox = h('div', { class: 'upnext-ring' }, dial, count);
    dialBox.hidden = !counting;
    // switch: on restarts the countdown, off stops it
    const auto = h('button', { type: 'button', class: 'upnext-auto' + (counting ? ' on' : ''), role: 'switch', 'aria-checked': String(counting), data: { f: '', key: 'upnext-auto' } },
        h('span', {}, FT.t('autoplay')), h('span', { class: 'upnext-switch' }));
    auto.addEventListener('click', () => {
        const on = !autoplayOn();
        setAutoplay(on);
        auto.classList.toggle('on', on);
        auto.setAttribute('aria-checked', String(on));
        play.textContent = FT.t(on ? 'play_now' : 'play_next');
        dialBox.hidden = !on;
        if (!upNext) return;
        clearInterval(upNext.timer);
        upNext.timer = 0;
        if (on) {
            upNext.endAt = performance.now() + UP_NEXT_SECONDS * 1000;
            upNext.timer = setInterval(() => tickUpNext(next), 200);
        }
    });
    const el = h('div', { class: 'upnext', role: 'dialog', 'aria-label': FT.t('up_next') },
        h('div', { class: 'upnext-top' },
            h('div', { class: 'upnext-thumb' },
                next.thumbnailUrl ? h('img', { src: next.thumbnailUrl, alt: '' }) : null,
                next.duration > 0 ? h('span', { class: 'dur' }, FT.duration(next.duration)) : null),
            h('div', { class: 'upnext-text' },
                h('div', { class: 'upnext-kicker' }, FT.t('up_next')),
                h('div', { class: 'upnext-title' }, next.title),
                h('div', { class: 'upnext-channel' }, next.channelName || '')),
            dialBox),
        h('div', { class: 'upnext-actions' }, play, cancel, auto));
    tree.skin.append(el);
    upNext = { el, ring, count, endAt: performance.now() + UP_NEXT_SECONDS * 1000, timer: 0 };
    if (counting) upNext.timer = setInterval(() => tickUpNext(next), 200);
    // The remote's keys stay on the card until it is gone: OK plays now, left and right pick a button, Back cancels.
    FT.focus.trap(el);
    FT.focus.select(play, { scroll: false });
};

// Back (or Escape) closes the card without leaving the page; every other key is the focus engine's. True when it was used.
const upNextBack = (name) => {
    if (!upNext) return false;
    if (name !== 'back') return false;
    cancelNext();
    return true;
};

// YouTube's 1440p and 4K come as AV1. If this browser can decode that smoothly, the playlist is asked for the tallest ladder (up to 4K);
// otherwise it gets the H.264 one, which every device plays, up to 1080p. The answer does not change, so it is asked once.
// decodingInfo() is only a hint; MediaSource.isTypeSupported() is what the player enforces.
let highestQuality = null;
const canPlayHighest = () => highestQuality || (highestQuality = (async () => {
    try {
        if (!(window.MediaSource && MediaSource.isTypeSupported('video/mp4; codecs="av01.0.12M.08"'))) return false;
        const info = await navigator.mediaCapabilities.decodingInfo({
            type: 'media-source',
            video: { contentType: 'video/mp4; codecs="av01.0.12M.08"', width: 2560, height: 1440, bitrate: 12000000, framerate: 30 },
        });
        return info.supported && info.smooth;
    } catch (e) { return false; }
})());

// ---- Building the player ----

// The player's modules come with the first video that opens, not with the page: the player, the live player and the media components are
// 1.8 MB between them, and a visit that only browses needs none of it. The page's version goes on the entry files, as for /app.js.
const APP_VERSION = new URL(document.currentScript.src).searchParams.get('v') || '';
const vendor = (path) => `/vendor/videojs/${path}?v=${APP_VERSION}`;
const loadPlayer = (way) => Promise.all([
    import(vendor(way.live ? 'live-video.js' : 'video.js')),
    way.tag === 'video' ? null : import(vendor(`media/${way.tag}.js`)),
]);

// Rules for the skin's shadow root, which the page's CSS cannot reach: the mini player has no controls of its own (its buttons are the .mini-ctrl
// ones in the page), the skin's cast button (this page casts through the phone remote in the top bar), and the SponsorBlock marks on the seek bar.
const SKIN_STYLE = `
:host([data-mini]) media-controls, :host([data-mini]) media-title { display: none !important; }
media-cast-button { display: none !important; }
.sb-mark { position: absolute; top: 50%; height: 4px; transform: translateY(-50%); min-width: 3px; border-radius: 2px; pointer-events: none; background: #00D400; opacity: 0.9; }
.sb-mark.intro, .sb-mark.outro { background: #00FFFF; }
.sb-mark.interaction { background: #CC00FF; }
.sb-mark.selfpromo { background: #FFFF00; }
.sb-mark.music_offtopic { background: #FF9900; }`;

// hls.js settings. A longer buffer than its default (30 s), so a stall on a slow link does not drain it; and a high first rung (bits/s) instead
// of the lowest one, which the estimate corrects within a few segments if the link cannot hold it.
const HLS_SETTINGS = { maxBufferLength: 60, maxMaxBufferLength: 120, backBufferLength: 30, abrEwmaDefaultEstimate: 6000000 };

// Which media component plays what /api/v1/video describes, and whether the live skin (a Live button, no time slider) fits it. A live room and
// every video with a quality ladder is an HLS playlist, and anything else (audio only) is one stream the browser plays as it is.
// `local=1`: play the saved file; on a media error fall back to the stream
const planFor = async (info, service, allowLocal = FT.route.params.local === '1') => {
    const pb = info.playback;
    if (allowLocal && !pb.liveRoom && !info.isLive) return { live: false, tag: 'video', src: `/downloadfile?serviceId=${service}&id=${FT.enc(info.url)}`, local: true };
    if (pb.hlsUrl) return { live: !!pb.liveRoom, tag: 'hlsjs-video', src: pb.hlsUrl };
    if (pb.isAdaptive) return { live: false, tag: 'hlsjs-video', src: pb.playlistUrl + ((await canPlayHighest()) ? '&prefer=hd' : '') };
    return { live: false, tag: 'video', src: pb.streamUrl };
};

const track = (kind, label, lang, src, isDefault) => {
    const el = document.createElement('track');
    el.kind = kind;
    el.label = label;
    el.srclang = lang;
    el.src = src;
    el.default = !!isDefault;
    return el;
};

// Video.js keeps nothing between visits, so the volume, mute and speed the viewer chose are kept here.
const PREFS_KEY = 'fathom-player';
const loadPrefs = () => { try { return JSON.parse(localStorage.getItem(PREFS_KEY)) || {}; } catch (e) { return {}; } };
const savePrefs = (media) => {
    try { localStorage.setItem(PREFS_KEY, JSON.stringify({ ...loadPrefs(), volume: media.volume, muted: media.muted, rate: media.playbackRate })); } catch (e) {}
};
const restorePrefs = (media) => {
    const prefs = loadPrefs();
    if (typeof prefs.volume === 'number') media.volume = prefs.volume;
    if (typeof prefs.muted === 'boolean') media.muted = prefs.muted;
    if (typeof prefs.rate === 'number') media.playbackRate = prefs.rate;
};

// Video.js does not set the browser's media session, which is what lock screens and keyboard media keys show.
const publishMediaSession = (info) => {
    if (!('mediaSession' in navigator) || typeof MediaMetadata === 'undefined') return;
    try {
        navigator.mediaSession.metadata = new MediaMetadata({
            title: info.title || '', artist: info.channelName || '', artwork: info.thumbnailUrl ? [{ src: info.thumbnailUrl }] : [],
        });
    } catch (e) {}
};

const bindMedia = (media) => {
    let restored = false;
    media.addEventListener('loadedmetadata', () => { addChapterTrack(); placeMarks(); });
    media.addEventListener('loadedmetadata', () => { restorePrefs(media); restored = true; }, { once: true });
    media.addEventListener('durationchange', addChapterTrack);
    media.addEventListener('timeupdate', watchSegments);
    media.addEventListener('pause', reportProgress);
    media.addEventListener('ended', scheduleNext);
    media.addEventListener('play', paintMiniPlay);
    media.addEventListener('pause', paintMiniPlay);
    media.addEventListener('volumechange', () => { if (restored) savePrefs(media); });
    media.addEventListener('ratechange', () => { if (restored) savePrefs(media); });
    media.addEventListener('canplay', () => { media.play().catch(() => {}); }, { once: true });
};

const teardown = () => {
    const holder = FT.$('#overlay-holder');
    if (holder) holder.append(FT.$('#skip-btn'), FT.$('#hud'));
    if (menuRowsWatch) { menuRowsWatch.disconnect(); menuRowsWatch = null; }
    if (tree) {
        // A removed media element keeps playing unless it is stopped first.
        try { tree.media.pause(); tree.media.removeAttribute('src'); } catch (e) {}
        if (tree.chapterUrl) URL.revokeObjectURL(tree.chapterUrl);
        tree.host.remove();
        tree = null;
    }
    FT.$('#player').replaceChildren();
};

// The skin builds its shadow root when it is connected; the rules for it go in once that exists.
const skinReady = (skin) => new Promise((resolve) => {
    const started = performance.now();
    const check = () => ((skin.shadowRoot && skin.shadowRoot.firstElementChild) || performance.now() - started > 3000 ? resolve() : requestAnimationFrame(check));
    check();
});

// Builds the player for one video, replacing the last. The media component is chosen by the stream, so it is part of what is built. Returns
// null when another video was opened while this one waited for its components.
const build = async (info, way, owner) => {
    teardown();
    const [playerTag, skinTag] = way.live ? ['live-video-player', 'live-video-skin'] : ['video-player', 'video-skin'];
    await loadPlayer(way);
    await Promise.all([playerTag, skinTag, ...(way.tag === 'video' ? [] : [way.tag])].map((tag) => customElements.whenDefined(tag)));
    if (current !== owner) return null;
    const host = document.createElement(playerTag);
    host.setAttribute('content-title', info.title || '');
    const skin = document.createElement(skinTag);
    skin.toggleAttribute('data-mini', dockMode === 'mini');
    const media = document.createElement(way.tag);
    media.setAttribute('playsinline', '');
    media.setAttribute('crossorigin', '');
    skin.append(media, FT.$('#skip-btn'), FT.$('#hud'));
    host.append(skin);
    tree = { host, skin, media, live: way.live, chapterUrl: '' };
    FT.$('#player').replaceChildren(host);
    await skinReady(skin);
    if (skin.shadowRoot) {
        const style = document.createElement('style');
        style.textContent = SKIN_STYLE;
        skin.shadowRoot.append(style);
    }
    watchMenuRows();
    bindMedia(media);
    return tree;
};

// A video that was left part-way starts where it was left (the history keeps a percentage; "Continue watching" on the home page offers the same).
// Reports of the new position follow from the usual timer, so a visit that only opens the video does not overwrite the saved progress with 0.
const sameVideo = (a, b) => a === b || (new URL(a, location.href).searchParams.get('v') || a) === (new URL(b, location.href).searchParams.get('v') || b);
// One-shot start position from `play_video_at`, ahead of the history position.
const startFromPhone = (owner, media, info) => {
    const at = FT.startAt;
    FT.startAt = null;
    if (!at || !sameVideo(at.url, info.url)) return false;
    const seek = () => { if (current === owner) media.currentTime = at.sec; };
    if (media.readyState >= 1) seek(); else media.addEventListener('loadedmetadata', seek, { once: true });
    return true;
};

const resumeFromHistory = (owner, media, info, service) => {
    if (info.isLive || info.playback.liveRoom) return;
    if (startFromPhone(owner, media, info)) return;
    FT.api.history().then((hist) => {
        const saved = (hist.videos || []).find((v) => v.url === info.url && (v.serviceId ?? 0) === service);
        if (!saved || !(saved.progress > 2 && saved.progress < 95)) return;
        // Some files start their timeline past 0, so "has not played yet" is judged from where the buffer begins, not from 0.
        const seek = (fresh) => {
            if (current !== owner || !isFinite(media.duration)) return;
            const start = media.buffered.length ? media.buffered.start(0) : 0;
            if (!fresh && media.currentTime - start > 5) return;
            media.currentTime = (saved.progress / 100) * media.duration;
        };
        if (media.readyState >= 1) seek(false);
        else media.addEventListener('loadedmetadata', () => seek(true), { once: true });
    }).catch(() => {});
};

// ---- The public side ----

FT.player = {
    get current() { return current; },
    isFullscreen,
    expand: openFull,

    // Loads a video from /api/v1/video's answer. Opening the one that is already loaded changes nothing.
    async open(service, info) {
        if (current && current.url === info.url) return;
        cancelNext();
        stopDanmaku();
        reportProgress();
        lastReported = -1;
        const owner = { service, url: info.url, info, chapters: info.chapters || [], segments: [], chapterTrack: false };
        current = owner;
        audioOnly = false;
        stageEl().classList.remove('audio-only');
        skipTarget = null;
        chosenQuality = 'auto';
        FT.$('#skip-btn').hidden = true;
        // null when another video was opened meanwhile
        const start = async (way) => {
            if (current !== owner || !(await build(info, way, owner))) return null;
            const p = playerEl();
            for (const sub of info.subtitles || []) {
                p.append(track('subtitles', sub.isAutoGenerated ? FT.t('subtitle_auto', { name: sub.displayName }) : sub.displayName, sub.languageTag || 'en', sub.url, false));
            }
            // seek-bar preview: WebVTT storyboard track (404 when absent)
            if (!info.playback.liveRoom && !info.isLive) p.append(track('metadata', 'thumbnails', 'en', `/thumbnails?serviceId=${service}&id=${FT.enc(info.url)}`, true));
            if (way.tag === 'hlsjs-video' && !way.live) p.source = { src: way.src, engine: { hlsJs: HLS_SETTINGS } };
            else p.src = way.src;
            return p;
        };
        const way = await planFor(info, service);
        const p = await start(way);
        if (!p) return;
        if (way.local) {
            const media = tree.media;
            media.addEventListener('error', async () => {
                if (current !== owner || tree.media !== media) return;
                FT.toast(FT.t('download_not_playable'));
                const replay = await start(await planFor(info, service, false));
                if (replay) resumeFromHistory(owner, replay, info, service);
            }, { once: true });
        }
        publishMediaSession(info);
        resumeFromHistory(owner, p, info, service);
        FT.api.sponsor(service, info.url).then((body) => {
            if (!current || current.url !== info.url) return;
            current.segments = body.segments || [];
            placeMarks();
        }).catch(() => {});
        if (info.playback.liveRoom) startDanmaku('/live_chat?room=' + info.playback.liveRoom, true);
        else if (service === 5) startDanmaku(`/danmaku?serviceId=${service}&id=${FT.enc(info.url)}`);
        FT.player.dock(dockMode === 'off' ? 'full' : dockMode);
    },

    dock(mode) {
        dockMode = current ? mode : 'off';
        if (dockMode === 'mini' && miniDismissed && FT.route.name !== 'watch') return FT.player.close();
        if (dockMode !== 'mini') miniDismissed = false;
        if (dockMode !== 'full') cancelNext();
        const stage = stageEl();
        stage.hidden = dockMode === 'off' || (dockMode === 'mini' && miniDismissed);
        stage.classList.toggle('mini', dockMode === 'mini');
        if (tree) tree.skin.toggleAttribute('data-mini', dockMode === 'mini');
        FT.$$(MINI_CTRL).forEach((btn) => { btn.hidden = dockMode !== 'mini'; });
        if (dockMode === 'mini') paintMiniPlay();
        placeMini();
    },

    // On the watch page the video keeps its place at the top, so the corner is only emptied and the video paused.
    dismissMini() {
        if (FT.route.name !== 'watch') return FT.player.close();
        playerEl().pause();
        miniDismissed = true;
        reportProgress();
        FT.player.dock('mini');
    },

    close() {
        cancelNext();
        stopDanmaku();
        reportProgress();
        teardown();
        current = null;
        audioOnly = false;
        stageEl().classList.remove('audio-only');
        document.body.classList.remove('remote-fs');
        FT.player.dock('off');
    },

    // Visual-only: hides the video surface behind an artwork card while the same stream keeps
    // playing, for audio-focused listening without a separate audio-only fetch.
    toggleAudioOnly() {
        if (!current) return false;
        audioOnly = !audioOnly;
        stageEl().classList.toggle('audio-only', audioOnly);
        FT.$('#audio-art-title').textContent = audioOnly ? current.info.title : '';
        return audioOnly;
    },

    get danmaku() { return danmaku; },
    setDanmaku(on) {
        if (!danmaku) return false;
        danmaku.on = on;
        if (!on) danmaku.layer.replaceChildren();
        else danmaku.resync();
        paintMenuRows();
        hud('chat', FT.t('hud_danmaku', { state: FT.t(on ? 'state_on' : 'state_off') }));
        return on;
    },
    toggleDanmaku() {
        return !!danmaku && FT.player.setDanmaku(!danmaku.on);
    },

    // Keys and the phone remote.
    cancelNext,
    get upNextActive() { return !!upNext; },
    upNextBack,
    toggle() {
        const p = playerEl();
        if (!current) return;
        if (p.paused) p.play().catch(() => {}); else p.pause();
        hud(p.paused ? 'play_arrow' : 'pause', FT.t(p.paused ? 'play' : 'pause'));
    },
    seek(delta) {
        const p = playerEl();
        if (!current) return;
        const limit = current.info.duration || p.duration || 0;
        p.currentTime = Math.max(0, Math.min(limit || Infinity, p.currentTime + delta));
        hud(delta > 0 ? 'fast_forward' : 'fast_rewind', FT.t('hud_seconds', { value: (delta > 0 ? '+' : '') + delta }));
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
        hud(p.muted ? 'volume_off' : 'volume_up', p.muted ? FT.t('muted') : Math.round(p.volume * 100) + '%');
    },
    async fullscreen(want) {
        if (!current) return;
        const on = isFullscreen();
        const enter = want === undefined ? !on : want;
        const store = tree && tree.host.store;
        if (!enter) {
            document.body.classList.remove('remote-fs');
            if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(() => {});
            try { const left = store && store.isFullscreen && store.exitFullscreen(); if (left && left.catch) left.catch(() => {}); } catch (e) {}
            return;
        }
        // A browser only grants real fullscreen right after a click on the page itself, which a remote never makes; if it is refused, fill the window.
        try { const asked = store && store.requestFullscreen(); if (asked && asked.catch) asked.catch(() => {}); } catch (e) {}
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
    // Direct jump by chapter index, for the remote's chapter picker (stepChapter only moves ±1).
    jumpChapter(index) {
        const p = playerEl();
        const list = current ? current.chapters : [];
        const target = list[index];
        if (target) p.currentTime = target.s;
    },

    // Remote-control state snapshot: title/time/transport plus chapters (titles + active index)
    // and the active SponsorBlock label, if any.
    snapshot() {
        const p = playerEl();
        return {
            open: !!current, title: current ? current.info.title : '', t: p.currentTime || 0, d: p.duration || 0, paused: !!p.paused,
            vol: p.volume == null ? 1 : p.volume, muted: !!p.muted, fs: isFullscreen(),
            chapters: current ? current.chapters.map((c) => c.t) : [], chapi: chapterIndex(),
            skip: skipTarget ? segmentName(skipTarget.l) : '',
            rate: p.playbackRate || 1,
            danmaku: danmaku ? danmaku.on : null,
            ...qualitySnapshot(),
        };
    },
    setQuality(id) {
        const store = tree && tree.host.store;
        if (!store || typeof store.selectVideoRendition !== 'function') return;
        chosenQuality = id === 'auto' ? 'auto' : String(id);
        try { store.selectVideoRendition(chosenQuality); } catch (e) {}
    },
    setRate(rate) {
        const value = parseFloat(rate);
        if (!(value >= 0.25 && value <= 4)) return;
        const p = playerEl();
        p.playbackRate = value;
        hud('speed', value + 'x');
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

// Wiring that does not depend on the video: the media's own events are bound as each player is built (bindMedia).
const wire = () => {
    // Fullscreen: the phone's browsers turn to landscape for it, as the player this replaced did.
    document.addEventListener('fullscreenchange', () => {
        if (!current || !screen.orientation) return;
        if (document.fullscreenElement) { if (screen.orientation.lock) screen.orientation.lock('landscape').catch(() => {}); }
        else if (screen.orientation.unlock) screen.orientation.unlock();
    });
    FT.$('#skip-btn').addEventListener('click', () => FT.player.skip());
    FT.$('#stage').addEventListener('pointerdown', onMiniPointerDown);
    FT.$('#stage').addEventListener('pointermove', onMiniPointerMove);
    FT.$('#stage').addEventListener('pointerup', onMiniPointerUp);
    FT.$('#stage').addEventListener('pointercancel', onMiniPointerUp);
    FT.$('#stage').addEventListener('click', onMiniClick);
};
wire();
})();

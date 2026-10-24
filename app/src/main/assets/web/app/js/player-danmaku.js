// Bilibili danmaku overlay: one CSS animation per comment, so pausing the media pauses them.
(() => {
const P = FT.playerCore;
const h = FT.h;
let danmaku = null; // { layer, media, items, next, on, lanes, tick, resync, pause, play, stop? }

const stop = () => {
    if (!danmaku) return;
    if (danmaku.stop) danmaku.stop();
    const p = danmaku.media;
    p.removeEventListener('timeupdate', danmaku.tick);
    p.removeEventListener('seeking', danmaku.resync);
    p.removeEventListener('pause', danmaku.pause);
    p.removeEventListener('play', danmaku.play);
    danmaku.layer.remove();
    danmaku = null;
    P.menu.paint();
};

const start = (url, live = false) => {
    const p = P.media();
    stop();
    const layer = h('div', { class: 'danmaku-layer' });
    (P.tree ? P.tree.skin : P.stage()).append(layer);
    const state = { layer, media: p, items: [], next: 0, on: true, lanes: new Array(14).fill(0) };
    danmaku = state;
    P.menu.paint();
    P.menu.hintDanmaku();
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
        // A live room has no timeline: chat is polled and each message shown as it arrives.
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

const set = (on) => {
    if (!danmaku) return false;
    danmaku.on = on;
    if (!on) danmaku.layer.replaceChildren();
    else danmaku.resync();
    P.menu.paint();
    P.hud('chat', FT.t('hud_danmaku', { state: FT.t(on ? 'state_on' : 'state_off') }));
    return on;
};

FT.playerCore.danmaku = {
    start,
    stop,
    set,
    toggle: () => !!danmaku && set(!danmaku.on),
    current: () => danmaku,
};
})();

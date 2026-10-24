// Up-next card on ended.
(() => {
const P = FT.playerCore;
const h = FT.h;
const UP_NEXT_SECONDS = 8;
const RING = 88; // ring circumference (r = 14)
let upNext = null; // { el, timer, endAt, ring, count }

const svgEl = (tag, attrs) => {
    const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
    return el;
};

// Idempotent: close card, stop countdown, release focus trap.
const cancel = () => {
    if (!upNext) return;
    clearInterval(upNext.timer);
    upNext.el.remove();
    upNext = null;
    FT.focus.trap(null);
    FT.focus.clear();
};

const playNext = (next) => {
    cancel();
    location.hash = FT.link.watch(next);
};

const tick = (next) => {
    if (!upNext) return;
    const left = Math.max(0, upNext.endAt - performance.now());
    upNext.count.textContent = String(Math.ceil(left / 1000));
    upNext.ring.style.strokeDashoffset = String(RING * (1 - left / (UP_NEXT_SECONDS * 1000)));
    if (left <= 0) playNext(next);
};

const schedule = () => {
    const next = P.current && P.current.info.relatedVideos && P.current.info.relatedVideos[0];
    // Skip unless full dock (ended in mini: no navigation).
    if (!next || upNext || !P.tree || P.dockMode !== 'full') return;
    const counting = P.prefs.autoplay();
    const ring = svgEl('circle', { cx: 17, cy: 17, r: 14, fill: 'none', 'stroke-width': 3, 'stroke-linecap': 'round', 'stroke-dasharray': RING, class: 'upnext-bar' });
    const count = h('b', {}, String(UP_NEXT_SECONDS));
    const dial = svgEl('svg', { viewBox: '0 0 34 34' });
    dial.append(svgEl('circle', { cx: 17, cy: 17, r: 14, fill: 'none', 'stroke-width': 3, class: 'upnext-track' }), ring);
    const play = h('button', { type: 'button', class: 'upnext-btn primary', data: { f: '', key: 'upnext-play' }, onclick: () => playNext(next) }, FT.t(counting ? 'play_now' : 'play_next'));
    const cancelBtn = h('button', { type: 'button', class: 'upnext-btn', data: { f: '', key: 'upnext-cancel' }, onclick: cancel }, FT.t('cancel'));
    const dialBox = h('div', { class: 'upnext-ring' }, dial, count);
    dialBox.hidden = !counting;
    // switch: on restarts the countdown, off stops it
    const auto = h('button', { type: 'button', class: 'upnext-auto' + (counting ? ' on' : ''), role: 'switch', 'aria-checked': String(counting), data: { f: '', key: 'upnext-auto' } },
        h('span', {}, FT.t('autoplay')), h('span', { class: 'upnext-switch' }));
    auto.addEventListener('click', () => {
        const on = !P.prefs.autoplay();
        P.prefs.setAutoplay(on);
        auto.classList.toggle('on', on);
        auto.setAttribute('aria-checked', String(on));
        play.textContent = FT.t(on ? 'play_now' : 'play_next');
        dialBox.hidden = !on;
        if (!upNext) return;
        clearInterval(upNext.timer);
        upNext.timer = 0;
        if (on) {
            upNext.endAt = performance.now() + UP_NEXT_SECONDS * 1000;
            upNext.timer = setInterval(() => tick(next), 200);
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
        h('div', { class: 'upnext-actions' }, play, cancelBtn, auto));
    P.tree.skin.append(el);
    upNext = { el, ring, count, endAt: performance.now() + UP_NEXT_SECONDS * 1000, timer: 0 };
    if (counting) upNext.timer = setInterval(() => tick(next), 200);
    // Focus trapped: OK play, left/right buttons, Back cancel.
    FT.focus.trap(el);
    FT.focus.select(play, { scroll: false });
};

FT.playerCore.upNext = {
    schedule,
    cancel,
    active: () => !!upNext,
    // Back/Escape cancels card in place; true if consumed.
    back(name) {
        if (!upNext || name !== 'back') return false;
        cancel();
        return true;
    },
};
})();

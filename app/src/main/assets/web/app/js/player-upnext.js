// Up-next card on ended.
(() => {
const P = FT.playerCore;
const h = FT.h;
const UP_NEXT_SECONDS = 8;
let upNext = null; // { el, fill, secs, timer, endAt }

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
    upNext.secs.textContent = String(Math.ceil(left / 1000));
    upNext.fill.style.width = `${100 * (1 - left / (UP_NEXT_SECONDS * 1000))}%`;
    if (left <= 0) playNext(next);
};

const schedule = () => {
    const next = FT.related(P.current ? P.current.info : {})[0];
    // Skip unless full dock (ended in mini: no navigation).
    if (!next || upNext || !P.tree || P.dockMode !== 'full') return;
    const counting = P.prefs.autoplay();
    const fill = h('span', { class: 'upnext-fill' });
    const secs = h('b', {}, String(UP_NEXT_SECONDS));
    const count = h('span', { class: 'upnext-n' }, h('span', { class: 'upnext-dot' }, ' · '), secs);
    count.hidden = !counting;
    const text = h('span', { class: 'upnext-play-text' }, FT.t(counting ? 'play_now' : 'play_next'));
    const play = h('button', { type: 'button', class: 'upnext-btn primary upnext-play', 'aria-label': FT.t(counting ? 'play_now' : 'play_next'), data: { f: '', key: 'upnext-play' }, onclick: () => playNext(next) },
        fill, FT.icon('play_arrow'), text, count);
    const cancelBtn = h('button', { type: 'button', class: 'upnext-btn upnext-cancel', 'aria-label': FT.t('cancel'), data: { f: '', key: 'upnext-cancel' }, onclick: cancel },
        FT.icon('close'), h('span', { class: 'upnext-cancel-text' }, FT.t('cancel')));
    // switch: on restarts the countdown, off stops it
    const auto = h('button', { type: 'button', class: 'upnext-auto' + (counting ? ' on' : ''), role: 'switch', 'aria-checked': String(counting), data: { f: '', key: 'upnext-auto' } },
        h('span', {}, FT.t('autoplay')), h('span', { class: 'upnext-switch' }));
    auto.addEventListener('click', () => {
        const on = !P.prefs.autoplay();
        P.prefs.setAutoplay(on);
        auto.classList.toggle('on', on);
        auto.setAttribute('aria-checked', String(on));
        text.textContent = FT.t(on ? 'play_now' : 'play_next');
        play.setAttribute('aria-label', text.textContent);
        count.hidden = !on;
        fill.style.width = '0';
        if (!upNext) return;
        clearInterval(upNext.timer);
        upNext.timer = 0;
        if (on) {
            upNext.endAt = performance.now() + UP_NEXT_SECONDS * 1000;
            upNext.timer = setInterval(() => tick(next), 200);
        }
    });
    const byline = [next.channelName, FT.views(next.viewCount)].filter(Boolean).join(' · ');
    const el = h('div', { class: 'upnext', role: 'dialog', 'aria-label': FT.t('up_next') },
        h('div', { class: 'upnext-head' }, h('span', { class: 'upnext-kicker' }, FT.t('up_next')), auto),
        h('div', { class: 'upnext-top' },
            h('div', { class: 'upnext-thumb' },
                next.thumbnailUrl ? h('img', { src: next.thumbnailUrl, alt: '' }) : null,
                next.duration > 0 ? h('span', { class: 'dur' }, FT.duration(next.duration)) : null),
            h('div', { class: 'upnext-text' },
                h('div', { class: 'upnext-title' }, next.title),
                h('div', { class: 'upnext-channel' }, byline))),
        h('div', { class: 'upnext-actions' }, play, cancelBtn));
    P.tree.skin.append(el);
    upNext = { el, fill, secs, endAt: performance.now() + UP_NEXT_SECONDS * 1000, timer: 0 };
    if (counting) upNext.timer = setInterval(() => tick(next), 200);
    // Focus trapped: OK play, left/right buttons, Back cancels.
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

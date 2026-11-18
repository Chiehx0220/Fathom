// Playback queue: play all, shuffle, loop; per tab (sessionStorage).
(() => {
const h = FT.h;
const KEY = 'fathom-queue';
const LOOPS = ['off', 'all', 'one'];
const KEEP = ['url', 'title', 'thumbnailUrl', 'channelName', 'channelId', 'serviceId', 'duration', 'viewCount', 'uploadDate', 'local'];

let state = null;
try { state = JSON.parse(sessionStorage.getItem(KEY)); } catch (e) { state = null; }
if (!state || !Array.isArray(state.items) || !Array.isArray(state.order)) state = null;

const save = () => {
    try { if (state) sessionStorage.setItem(KEY, JSON.stringify(state)); else sessionStorage.removeItem(KEY); } catch (e) {}
    dispatchEvent(new Event('ft:queue'));
};

const shuffled = (list) => {
    const out = list.slice();
    for (let i = out.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [out[i], out[j]] = [out[j], out[i]];
    }
    return out;
};

const at = (pos) => (state && pos >= 0 && pos < state.order.length ? state.items[state.order[pos]] : null);

// Shuffle: `first`, rest random; else list order from `first`.
const arrange = (count, first, shuffle) => {
    const all = [...Array(count).keys()];
    return shuffle ? { order: [first, ...shuffled(all.filter((i) => i !== first))], pos: 0 } : { order: all, pos: first };
};

const clip = (video) => Object.fromEntries(KEEP.filter((k) => video[k] !== undefined).map((k) => [k, video[k]]));

FT.queue = {
    active: () => !!state && !!at(state.pos),
    loop: () => (state ? state.loop : 'off'),
    shuffle: () => !!state && state.shuffle,
    position: () => (state ? { at: state.pos + 1, of: state.order.length } : null),

    // Start `videos` (url-deduped) at `from`.
    start(videos, { shuffle = false, from = 0 } = {}) {
        const seen = new Set();
        const items = videos.filter((v) => v && v.url && !seen.has(v.url) && seen.add(v.url)).map(clip);
        if (!items.length) return;
        const first = Math.min(from, items.length - 1);
        state = { items, ...arrange(items.length, first, shuffle), loop: state ? state.loop : 'off', shuffle };
        save();
        location.hash = FT.link.watch(items[first]);
    },

    // Watch page opened `url`: advance position if queued.
    visit(url) {
        if (!state) return;
        const pos = state.order.findIndex((i) => state.items[i].url === url);
        if (pos < 0 || pos === state.pos) return;
        state.pos = pos;
        save();
    },

    // Next video; null at end unless looping.
    next() {
        if (!state) return null;
        if (state.pos + 1 < state.order.length) return at(state.pos + 1);
        if (state.loop === 'all') return at(0);
        return null;
    },
    previous: () => (state && state.pos > 0 ? at(state.pos - 1) : null),
    current: () => (state ? at(state.pos) : null),
    upcoming: () => (state ? state.order.slice(state.pos + 1).map((i) => state.items[i]) : []),

    setLoop(mode) {
        if (!state || !LOOPS.includes(mode)) return;
        state.loop = mode;
        save();
    },
    toggleShuffle() {
        if (!state) return;
        state.shuffle = !state.shuffle;
        Object.assign(state, arrange(state.items.length, state.order[state.pos], state.shuffle));
        save();
    },
    clear() {
        state = null;
        save();
    },

    // Watch-page controls and upcoming list; null without queue.
    panel() {
        const holder = h('div', { class: 'queue' });
        const paint = () => {
            if (!FT.queue.active()) { holder.replaceChildren(); return; }
            const place = FT.queue.position();
            const go = (video) => () => { location.hash = FT.link.watch(video); };
            const prev = FT.queue.previous();
            const next = FT.queue.next();
            const loop = FT.queue.loop();
            const controls = h('div', { class: 'pills' },
                h('span', { class: 'queue-place' }, FT.t('queue_position', { at: place.at, of: place.of })),
                FT.pill(FT.t('queue_previous'), null, false, prev ? go(prev) : () => {}, 'queue-prev'),
                FT.pill(FT.t('queue_next'), null, false, next ? go(next) : () => {}, 'queue-next'),
                FT.pill(FT.t('queue_shuffle'), null, FT.queue.shuffle(), () => FT.queue.toggleShuffle(), 'queue-shuffle'),
                FT.pill(FT.t(loop === 'one' ? 'queue_loop_one' : loop === 'all' ? 'queue_loop_all' : 'queue_loop_off'), null, loop !== 'off',
                    () => FT.queue.setLoop(LOOPS[(LOOPS.indexOf(loop) + 1) % LOOPS.length]), 'queue-loop'),
                FT.pill(FT.t('queue_close'), null, false, () => FT.queue.clear(), 'queue-close'));
            const upcoming = FT.queue.upcoming();
            holder.replaceChildren(h('div', { class: 'queue-head' }, h('h2', {}, FT.t('queue')), controls),
                upcoming.length ? FT.scroller(h('div', { class: 'row' }, upcoming.slice(0, 20).map((v) => FT.videoCard(v, 'q:' + v.url)))) : null);
        };
        paint();
        holder.stop = FT.on('ft:queue', paint);
        return holder;
    },

    // Play all / Shuffle buttons.
    buttons(getVideos) {
        return h('div', { class: 'btns queue-start' },
            FT.button(FT.t('play_all'), 'play_arrow', () => FT.queue.start(getVideos()), 'primary', 'play-all'),
            FT.button(FT.t('queue_shuffle'), null, () => { const list = getVideos(); FT.queue.start(list, { shuffle: true, from: Math.floor(Math.random() * list.length) }); }, '', 'shuffle-all'));
    },
};
})();

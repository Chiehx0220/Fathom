// Home: a featured video, then shelves. The quick shelves show at once; the slow ones (subscriptions) fill in when they arrive.
(() => {
const h = FT.h;
// Per-shelf IntersectionObservers (see shelfOf); disconnected together on leave().
let activeObservers = [];

// Cards rendered per chunk as a shelf scrolls into view.
const SHELF_CHUNK = 14;

// options.loadMore: () => Promise<{items, hasMore}>, fetches a deeper server round once the local
// list is exhausted, appended to the same row.
// options.expand: hash link to the shelf's full contents as a list.js view, for keyboard/mouse
// navigation (a row's overflow is otherwise only reachable by dragging it).
const shelfOf = (title, sub, items, options = {}) => {
    const row = h('div', { class: 'row' });
    let shown = 0;
    let io = null;
    let loading = false;
    let hasMore = !!options.loadMore;
    const step = () => {
        for (const v of items.slice(shown, shown + SHELF_CHUNK)) {
            const card = FT.videoCard(v, title + ':' + v.url);
            if (options.later) card.inWatchLater = true;
            row.append(card);
        }
        shown = Math.min(shown + SHELF_CHUNK, items.length);
        if (shown >= items.length && !hasMore) { if (io) io.disconnect(); return; }
        const sentinel = h('div', { style: 'flex:none;width:1px' });
        row.append(sentinel);
        if (!io) { io = new IntersectionObserver(([entry]) => { if (entry.isIntersecting) reach(sentinel); }, { root: row, rootMargin: '600px' }); activeObservers.push(io); }
        io.observe(sentinel);
    };
    // Sentinel intersected: show the next local chunk, else (if loadMore was given) fetch one.
    const reach = (sentinel) => {
        sentinel.remove();
        if (shown < items.length) { step(); return; }
        if (!hasMore || loading) return;
        loading = true;
        options.loadMore().then((more) => {
            hasMore = more.hasMore;
            items.push(...more.items);
            step();
        }).catch(() => { hasMore = false; if (io) io.disconnect(); }).finally(() => { loading = false; });
    };
    step();
    const head = options.expand
        ? h('a', { class: 'shelf-head expand', href: options.expand, data: { f: '', key: 'shelf:' + title } },
            h('h2', {}, title), sub ? h('span', {}, sub) : null, h('span', { class: 'shelf-more' }, 'See all', FT.icon('chevron_right')))
        : h('div', { class: 'shelf-head' }, h('h2', {}, title), sub ? h('span', {}, sub) : null);
    return h('section', { class: 'shelf' }, head, row);
};

const hero = (v, resuming) => h('div', { class: 'hero' },
    v.thumbnailUrl ? h('img', { src: v.thumbnailUrl, alt: '' }) : null,
    h('div', { class: 'hero-cap' },
        h('div', { class: 'hero-kicker' }, resuming ? `Continue watching · ${100 - v.progress}% left` : 'Featured'),
        h('h1', {}, v.title),
        h('div', { class: 'card-meta' }, [FT.meta(v), v.duration > 0 ? FT.duration(v.duration) : ''].filter(Boolean).join(' · ')),
        h('div', { class: 'btns' },
            FT.button(resuming ? 'Resume' : 'Play', 'play_arrow', () => { location.hash = FT.link.watch(v); }, 'primary', 'hero-play'),
            FT.button('Watch later', 'schedule', () => FT.api.watchLater(v, true).then(() => FT.toast('Saved to Watch later')).catch(() => FT.toast('Could not save that')), '', 'hero-later'))));

const render = async (root, params, isCurrent) => {
    const service = FT.store.service;
    const [recs, hist] = await Promise.all([
        FT.api.recommendations(service).catch(() => FT.api.home(service)),
        FT.api.history().catch(() => ({ videos: [] })),
    ]);
    if (!isCurrent()) return;

    const seen = (hist.videos || []).filter((v) => v.serviceId === service && v.progress > 2 && v.progress < 95);
    const picks = (recs.videos || []).filter((v) => !v.isShort);
    const lead = seen[0] || picks[0];
    if (!lead) { root.replaceChildren(FT.empty('video_library', 'Nothing to show yet', 'Search for something to get started.')); return; }

    // Slots for the shelves that arrive later, so they land in the right place whenever they do.
    const slotSubs = h('div', { hidden: true });
    const slotLater = h('div', { hidden: true });

    // Deeper discovery rounds (handleApiRecommendations's more=1) feed the Recommended shelf directly.
    const known = new Set(picks.map((v) => v.url));
    const loadMoreRecs = recs.hasMore === false ? null : () => FT.api.recommendations(service, true).then((body) => {
        if (!isCurrent()) throw new Error('left the home screen');
        const fresh = (body.videos || []).filter((v) => !v.isShort && !known.has(v.url));
        fresh.forEach((v) => known.add(v.url));
        return { items: fresh, hasMore: body.hasMore !== false };
    });

    const page = [
        hero(lead, seen[0] === lead),
        seen.length ? shelfOf('Continue watching', 'From your history', seen, { expand: '#/history?filter=continuing' }) : null,
        picks.length ? shelfOf('Recommended', 'For you', picks, { ...(loadMoreRecs ? { loadMore: loadMoreRecs } : {}), expand: '#/recommended' }) : null,
        slotSubs,
        slotLater,
    ];
    root.replaceChildren(...page.filter(Boolean));

    FT.api.feed(service).then((body) => {
        if (!isCurrent()) return;
        const items = (body.videos || []).filter((v) => v.serviceId === service);
        if (!items.length) return;
        slotSubs.replaceChildren(shelfOf('From your subscriptions', 'Newest first', items, { expand: '#/library?tab=feed' }));
        slotSubs.hidden = false;
    }).catch(() => {});

    FT.api.library(service).then((lib) => {
        if (!isCurrent()) return;
        const items = (lib.watchLater || []).filter((v) => v.serviceId === service);
        if (!items.length) return;
        slotLater.replaceChildren(shelfOf('Watch later', 'Saved by you', items, { later: true, expand: '#/library?tab=later' }));
        slotLater.hidden = false;
    }).catch(() => {});
};

FT.register('home', {
    render,
    leave() { activeObservers.forEach((o) => o.disconnect()); activeObservers = []; },
});
})();

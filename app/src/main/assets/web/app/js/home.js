// Home: a featured video, then shelves. The quick shelves show at once; the slow ones (subscriptions) fill in when they arrive.
(() => {
const h = FT.h;
// Per-shelf IntersectionObservers (see shelfOf); disconnected together on leave().
let activeObservers = [];
// Unsubscribes the resume row from history changes.
let stopHistory = null;

// Cards rendered per chunk as a shelf scrolls into view.
const SHELF_CHUNK = 14;

// options.loadMore: () => Promise<{items, hasMore}>, fetches a deeper server round once the local
// list is exhausted, appended to the same row.
// options.expand: hash link to the shelf's full contents as a list.js view, for keyboard/mouse
// navigation (a row's overflow is otherwise only reachable by dragging it).
// options.rows: grid layout preview rows for a shelf without loadMore.
// Grid layout: the shelf is a grid and its sentinel watches the viewport.
const shelfOf = (title, sub, items, options = {}) => {
    const wide = FT.layout.current() === 'grid';
    const preview = wide && !options.loadMore;
    const row = h('div', { class: wide ? 'grid' : 'row' });
    const tail = wide ? h('div') : row;
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
        if (preview || (shown >= items.length && !hasMore)) { if (io) io.disconnect(); return; }
        const sentinel = h('div', { style: wide ? 'height:1px' : 'flex:none;width:1px' });
        tail.append(sentinel);
        if (!io) { io = new IntersectionObserver(([entry]) => { if (entry.isIntersecting) reach(sentinel); }, { root: wide ? null : row, rootMargin: '600px' }); activeObservers.push(io); }
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
    if (preview) {
        const rows = options.rows || 1;
        const fit = () => {
            const columns = getComputedStyle(row).gridTemplateColumns;
            if (columns === 'none') return;
            const count = columns.split(' ').length * rows;
            [...row.children].forEach((card, i) => { card.hidden = i >= count; });
        };
        const resize = new ResizeObserver(fit);
        resize.observe(row);
        activeObservers.push(resize);
    }
    const head = options.expand
        ? h('a', { class: 'shelf-head expand', href: options.expand, 'aria-label': FT.t('see_all_named', { title }), data: { f: '', key: 'shelf:' + title } },
            h('h2', {}, title), FT.icon('chevron_right', 'shelf-chev'), sub ? h('span', { class: 'shelf-sub' }, sub) : null, h('em', { class: 'shelf-all' }, FT.t('see_all')))
        : h('div', { class: 'shelf-head' }, h('h2', {}, title), sub ? h('span', {}, sub) : null);
    return h('section', { class: 'shelf' }, head, wide ? row : FT.scroller(row), wide ? tail : null);
};

const hero = (v, resuming) => h('div', { class: 'hero' },
    v.thumbnailUrl ? h('img', { src: v.thumbnailUrl, alt: '' }) : null,
    h('div', { class: 'hero-cap' },
        h('div', { class: 'hero-kicker' }, resuming ? FT.t('continue_left', { percent: 100 - v.progress }) : FT.t('featured')),
        h('h1', {}, v.title),
        h('div', { class: 'card-meta' }, [FT.meta(v), v.duration > 0 ? FT.duration(v.duration) : ''].filter(Boolean).join(' · ')),
        h('div', { class: 'btns' },
            FT.button(FT.t(resuming ? 'resume' : 'play'), 'play_arrow', () => { location.hash = FT.link.watch(v); }, 'primary', 'hero-play'),
            FT.button(FT.t('watch_later'), 'schedule', () => FT.api.watchLater(v, true).then(() => FT.toast(FT.t('saved_to_later'))).catch(() => FT.toast(FT.t('could_not_save'))), '', 'hero-later'))));

const render = async (root, params, isCurrent) => {
    const service = FT.store.service;
    const wide = FT.layout.current() === 'grid';
    const [recs, hist] = await Promise.all([
        FT.api.recommendations(service).catch(() => FT.api.home(service)),
        FT.api.history().catch(() => ({ videos: [] })),
    ]);
    if (!isCurrent()) return;

    const resumable = (history) => (history.videos || []).filter((v) => v.serviceId === service && v.progress > 2 && v.progress < 95);
    const seen = resumable(hist);
    const picks = (recs.videos || []).filter((v) => !v.isShort);
    const lead = seen[0] || picks[0];
    if (!lead) { root.replaceChildren(FT.empty('video_library', FT.t('nothing_yet'), FT.t('nothing_yet_hint'))); return; }

    // Slots for the shelves that arrive later, so they land in the right place whenever they do.
    const slotHero = h('div', { style: 'display:contents' });
    const slotSeen = h('div', { style: 'display:contents' });
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

    const paintResume = (resuming) => {
        const first = resuming[0] || picks[0];
        slotHero.replaceChildren(...(first && !wide ? [hero(first, resuming[0] === first)] : []));
        slotSeen.replaceChildren(...(resuming.length
            ? [shelfOf(FT.t('continue_watching'), FT.t('from_history'), resuming, { expand: '#/history?filter=continuing', rows: 1 })] : []));
    };
    paintResume(seen);

    const page = [
        slotHero,
        slotSeen,
        picks.length ? shelfOf(FT.t('recommended'), FT.t('for_you'), picks, { ...(loadMoreRecs ? { loadMore: loadMoreRecs } : {}), expand: '#/recommended', rows: 3 }) : null,
        slotSubs,
        slotLater,
    ];
    root.replaceChildren(...page.filter(Boolean));

    if (stopHistory) stopHistory();
    stopHistory = FT.api.onHistoryChange(() => FT.api.history().then((fresh) => {
        if (!isCurrent()) return;
        const selected = FT.focus.el;
        const key = selected && (slotHero.contains(selected) || slotSeen.contains(selected)) ? selected.dataset.key : null;
        paintResume(resumable(fresh));
        if (key) FT.focus.select(FT.focus.stops().find((el) => el.dataset.key === key) || FT.focus.stops()[0]);
    }).catch(() => {}));

    FT.api.feed(service).then((body) => {
        if (!isCurrent()) return;
        const items = (body.videos || []).filter((v) => v.serviceId === service);
        if (!items.length) return;
        slotSubs.replaceChildren(shelfOf(FT.t('from_subscriptions'), FT.t('newest_first'), items, { expand: '#/subscriptions?tab=feed', rows: 2 }));
        slotSubs.hidden = false;
    }).catch(() => {});

    FT.api.library(service).then((lib) => {
        if (!isCurrent()) return;
        const items = (lib.watchLater || []).filter((v) => v.serviceId === service);
        if (!items.length) return;
        slotLater.replaceChildren(shelfOf(FT.t('watch_later'), FT.t('saved_by_you'), items, { later: true, expand: '#/library?tab=later', rows: 1 }));
        slotLater.hidden = false;
    }).catch(() => {});
};

FT.register('home', {
    render,
    leave() {
        activeObservers.forEach((o) => o.disconnect());
        activeObservers = [];
        if (stopHistory) stopHistory();
        stopHistory = null;
    },
});
})();

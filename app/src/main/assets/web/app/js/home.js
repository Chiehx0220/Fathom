// Home: a featured video, then shelves. The quick shelves show at once; the slow ones (subscriptions) fill in when they arrive.
(() => {
const h = FT.h;
let observer = null;

const shelfOf = (title, sub, items, options = {}) => {
    const cards = items.map((v) => {
        const card = FT.videoCard(v, title + ':' + v.url);
        if (options.later) card.inWatchLater = true;
        return card;
    });
    return FT.shelf(title, sub, cards);
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

    const seen = (hist.videos || []).filter((v) => v.serviceId === service && v.progress > 2 && v.progress < 95).slice(0, 14);
    const picks = (recs.videos || []).filter((v) => !v.isShort);
    const lead = seen[0] || picks[0];
    if (!lead) { root.replaceChildren(FT.empty('video_library', 'Nothing to show yet', 'Search for something to get started.')); return; }

    // Slots for the shelves that arrive later, so they land in the right place whenever they do.
    const slotSubs = h('div', { hidden: true });
    const slotLater = h('div', { hidden: true });
    // Grows as later discovery rounds come in (see the infinite-scroll block below) - the home
    // feed used to stop dead at whatever the first ranked batch happened to contain.
    const slotMore = h('div', {});
    const sentinel = h('div', { style: 'height:1px' });
    const page = [
        hero(lead, seen[0] === lead),
        seen.length ? shelfOf('Continue watching', 'From your history', seen) : null,
        picks.length ? shelfOf('Recommended', 'For you', picks.slice(0, 14)) : null,
        slotSubs,
        slotLater,
        // The whole first batch, not just the first 30 of it - what used to be thrown away.
        picks.length > 14 ? shelfOf('More to watch', '', picks.slice(14)) : null,
        slotMore,
        sentinel,
    ];
    root.replaceChildren(...page.filter(Boolean));

    FT.api.feed(service).then((body) => {
        if (!isCurrent()) return;
        const items = (body.videos || []).filter((v) => v.serviceId === service).slice(0, 14);
        if (!items.length) return;
        slotSubs.replaceChildren(shelfOf('From your subscriptions', 'Newest first', items));
        slotSubs.hidden = false;
    }).catch(() => {});

    FT.api.library(service).then((lib) => {
        if (!isCurrent()) return;
        const items = (lib.watchLater || []).filter((v) => v.serviceId === service).slice(0, 14);
        if (!items.length) return;
        slotLater.replaceChildren(shelfOf('Watch later', 'Saved by you', items, { later: true }));
        slotLater.hidden = false;
    }).catch(() => {});

    // Infinite scroll: reaching the bottom asks the server for a deeper discovery round
    // (handleApiRecommendations's `more=1`) instead of leaving the feed capped.
    let hasMore = recs.hasMore !== false;
    let loading = false;
    const known = new Set(picks.map((v) => v.url));
    if (observer) observer.disconnect();
    observer = new IntersectionObserver(([entry]) => {
        if (!entry.isIntersecting || loading || !hasMore) return;
        loading = true;
        FT.api.recommendations(service, true).then((body) => {
            if (!isCurrent()) { observer.disconnect(); return; }
            hasMore = body.hasMore !== false;
            const fresh = (body.videos || []).filter((v) => !v.isShort && !known.has(v.url));
            fresh.forEach((v) => known.add(v.url));
            if (fresh.length) slotMore.append(shelfOf('More recommendations', '', fresh));
            if (!hasMore) observer.disconnect();
        }).catch(() => { hasMore = false; observer.disconnect(); }).finally(() => { loading = false; });
    }, { rootMargin: '800px' });
    observer.observe(sentinel);
};

FT.register('home', {
    render,
    leave() { if (observer) { observer.disconnect(); observer = null; } },
});
})();

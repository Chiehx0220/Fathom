// Screens that are lists of videos: search, subscriptions, library, playlists. One list view serves them all:
// rows on the left, the selected one drawn large on the right, and "load more" that also runs by itself near the end.
(() => {
const h = FT.h;

// The detail pane for the selected row.
const paneFor = (el) => {
    const v = el.item;
    return [
        h('div', { class: 'thumb' }, v.thumbnailUrl ? h('img', { src: v.thumbnailUrl, alt: '' }) : null, v.duration > 0 ? h('span', { class: 'dur' }, FT.duration(v.duration)) : null),
        h('h2', {}, v.title),
        h('p', {}, FT.meta(v)),
        h('div', { class: 'btns' },
            FT.button(FT.t('play'), 'play_arrow', () => { location.hash = FT.link.watch(v); }, 'primary', 'pane-play'),
            FT.button(FT.t(el.inWatchLater ? 'saved' : 'watch_later'), 'schedule', () => FT.api.watchLater(v, !el.inWatchLater).then(() => { el.inWatchLater = !el.inWatchLater; FT.toast(FT.t(el.inWatchLater ? 'saved_to_later' : 'removed_from_later')); }).catch(() => FT.toast(FT.t('could_not_save'))), '', 'pane-later'),
            v.channelId ? FT.button(FT.t('channel'), 'account_circle', () => { location.hash = FT.link.channel(v.channelId, v.serviceId); }, '', 'pane-channel') : null),
    ];
};

// options: { fetch(cursor) -> { items, next }, decorate(el), empty: { icon, title, text }, before: [elements] }
// Grid layout: card grid without the detail pane; the next page loads when `more` nears the viewport.
FT.listView = async (root, options, isCurrent) => {
    const wide = FT.layout.current() === 'grid';
    const itemSelector = wide ? '.card' : '.row-item';
    const list = h('div', { class: wide ? 'grid' : 'list' });
    const pane = h('aside', { class: 'pane' });
    const more = h('div', { class: 'more', hidden: true });
    let cursor = null;
    let loading = false;
    let done = false;

    const showPane = (el) => { if (el && el.item && el.kind === 'video') pane.replaceChildren(...paneFor(el)); };
    FT.screenSelect = (el) => {
        if (!list.contains(el)) return;
        showPane(el);
        const rows = FT.$$(itemSelector, list);
        if (!done && rows.length - rows.indexOf(el) <= 3) loadMore();
    };

    const remove = (el) => {
        const rows = FT.$$(itemSelector, list);
        const at = rows.indexOf(el);
        const neighbour = rows[at + 1] || rows[at - 1];
        el.remove();
        if (neighbour) FT.focus.select(neighbour); else FT.focus.first();
        if (!FT.$(itemSelector, list)) root.replaceChildren(...(options.before || []), FT.empty(options.empty.icon, options.empty.title, options.empty.text));
    };

    const append = (items) => {
        for (const item of items) {
            const row = wide ? FT.videoCard(item) : FT.videoRow(item);
            if (options.decorate) options.decorate(row);
            row.onRemoved = () => remove(row);
            list.append(row);
        }
    };

    async function loadMore() {
        if (loading || done) return;
        loading = true;
        try {
            const page = await options.fetch(cursor);
            if (!isCurrent()) return;
            append(page.items);
            cursor = page.next || null;
            done = !cursor;
            more.hidden = done;
        } catch (error) {
            if (isCurrent()) FT.toast(FT.t('could_not_load_more'));
        } finally {
            loading = false;
        }
    }

    more.append(FT.button(FT.t('load_more'), 'expand_more', loadMore, '', 'load-more'));
    if (wide) {
        const nearEnd = new IntersectionObserver(([entry]) => { if (entry.isIntersecting) loadMore(); }, { rootMargin: '600px' });
        nearEnd.observe(more);
        activeEnds.push(nearEnd);
    }
    const first = await options.fetch(null);
    if (!isCurrent()) return;
    if (!first.items.length && !(first.extra && first.extra.length)) {
        root.replaceChildren(...(options.before || []), FT.empty(options.empty.icon, options.empty.title, options.empty.text));
        return;
    }
    append(first.items);
    cursor = first.next || null;
    done = !cursor;
    more.hidden = done;
    const body = wide ? h('div', {}, list, more) : h('div', { class: 'split' }, h('div', {}, list, more), pane);
    root.replaceChildren(...[...(options.before || []), ...(first.extra || []), first.items.length ? body : null].filter(Boolean));
    const firstRow = FT.$(itemSelector, list);
    if (firstRow && !wide) showPane(firstRow);
};

// A history list redraws on history changes (scroll and selection kept by FT.reload).
let stopHistory = null;
// Grid layout end observers; disconnected on leave.
const activeEnds = [];
const cleanup = () => {
    activeEnds.splice(0).forEach((o) => o.disconnect());
    FT.screenSelect = null;
    if (stopHistory) stopHistory();
    stopHistory = null;
};

// ---- Search ----

const searchField = (value) => {
    const input = h('input', { type: 'search', placeholder: FT.t('search_placeholder'), value: value || '', enterkeyhint: 'search', autocomplete: 'off' });
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && input.value.trim()) { e.preventDefault(); input.blur(); location.hash = '#/search?q=' + FT.enc(input.value.trim()); }
    });
    return h('div', { class: 'searchbar' }, h('div', { class: 'field', data: { f: '', key: 'search-field' }, onclick: () => input.focus() }, FT.icon('search'), input));
};

const suggestions = (history, onChange) => {
    if (!history.length) return h('div', {});
    return h('div', { class: 'suggest' }, history.map((q) => {
        const row = h('div', { class: 'suggest-row' },
            h('button', { type: 'button', class: 'suggest-item', data: { f: '', key: 'q:' + q }, onclick: () => { location.hash = '#/search?q=' + FT.enc(q); } }, FT.icon('history'), h('span', {}, q)),
            h('button', { type: 'button', class: 'suggest-del', 'aria-label': FT.t('remove_query', { query: q }), data: { f: '', key: 'del:' + q }, onclick: () => FT.api.forgetSearch(q).then(() => { row.remove(); onChange(); }) }, FT.icon('close')));
        return row;
    }));
};

FT.register('search', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        const q = (params.q || '').trim();
        const service = FT.store.service;
        const field = searchField(q);
        if (!q) {
            const history = await FT.api.searchHistory().catch(() => []);
            if (!isCurrent()) return;
            root.replaceChildren(...[field, suggestions(history, () => {}), history.length ? null : FT.empty('search', FT.t('search_service', { name: FT.store.serviceName(service) }), FT.t('search_empty_hint'))].filter(Boolean));
            return;
        }
        // The first page decides which kinds of result exist; videos come first, channels and playlists are one press away.
        const first = await FT.api.search(service, q, null);
        if (!isCurrent()) return;
        const kinds = [['videos', FT.t('videos'), 'play_circle', first.videos], ['channels', FT.t('channels'), 'group', first.channels], ['playlists', FT.t('playlists'), 'playlist_play', first.playlists]]
            .filter(([id, , , list]) => id === 'videos' || (list && list.length));
        const firstKind = kinds.find((k) => k[3] && k[3].length);
        const type = kinds.some((k) => k[0] === params.type) ? params.type : firstKind ? firstKind[0] : 'videos';
        const pills = kinds.length > 1 ? h('div', { class: 'pills' }, kinds.map(([id, label, icon]) => FT.pill(label, icon, id === type,
            () => { location.hash = '#/search?q=' + FT.enc(q) + (id === 'videos' ? '' : '&type=' + id); }, 'kind-' + id))) : null;
        const before = [field, pills].filter(Boolean);
        if (type !== 'videos') {
            const cards = (type === 'channels' ? first.channels : first.playlists).map(type === 'channels' ? FT.channelCard : FT.playlistCard);
            root.replaceChildren(...before, h('div', { class: 'grid' }, cards));
            return;
        }
        let served = false;
        await FT.listView(root, {
            before,
            empty: { icon: 'search_off', title: FT.t('no_results'), text: FT.t('no_results_hint', { query: q }) },
            async fetch(cursor) {
                if (!cursor && !served) { served = true; return { items: first.videos || [], next: first.nextPage }; }
                const body = await FT.api.search(service, q, cursor);
                return { items: body.videos || [], next: body.nextPage };
            },
        }, isCurrent);
    },
});

// ---- History ----

// history list; `continuing`: unfinished only (Home shelf)
const historyList = (root, { before, continuing }, isCurrent) => {
    const service = FT.store.service;
    if (stopHistory) stopHistory();
    stopHistory = FT.api.onHistoryChange(() => { if (isCurrent()) FT.reload(); });
    return FT.listView(root, {
        before,
        empty: continuing
            ? { icon: 'play_circle', title: FT.t('nothing_in_progress'), text: FT.t('nothing_in_progress_hint') }
            : { icon: 'history', title: FT.t('nothing_watched'), text: FT.t('nothing_watched_hint') },
        decorate: (row) => { row.fromHistory = true; },
        fetch: async () => {
            const items = (await FT.api.history()).videos || [];
            return { items: continuing ? items.filter((v) => v.serviceId === service && v.progress > 2 && v.progress < 95) : items, next: null };
        },
    }, isCurrent);
};

FT.register('history', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        // full history is in Library; route kept for ?filter=continuing and old links
        if (params.filter !== 'continuing') { location.replace('#/library?tab=history'); return; }
        await historyList(root, { before: [h('div', { class: 'head' }, h('h1', {}, FT.t('continue_watching')))], continuing: true }, isCurrent);
    },
});

// ---- Recommended (Home's "Recommended" shelf, opened out into a full list) ----

FT.register('recommended', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        const service = FT.store.service;
        const known = new Set();
        const head = h('div', { class: 'head' }, h('h1', {}, FT.t('recommended')));
        let served = false;
        await FT.listView(root, {
            before: [head],
            empty: { icon: 'video_library', title: FT.t('nothing_yet'), text: FT.t('nothing_yet_hint') },
            async fetch(cursor) {
                const body = !cursor && !served ? await FT.api.recommendations(service) : await FT.api.recommendations(service, true);
                served = true;
                const items = (body.videos || []).filter((v) => !v.isShort && !known.has(v.url));
                items.forEach((v) => known.add(v.url));
                return { items, next: body.hasMore !== false ? '1' : null };
            },
        }, isCurrent);
    },
});

// ---- Subscriptions: feed, channels ----

// tab pills; selecting re-renders the route with ?tab=
const tabPills = (route, tabs, active) => h('div', { class: 'pills' }, tabs.map(([id, label, icon]) => FT.pill(label, icon, id === active, () => { location.hash = `#/${route}?tab=${id}`; }, 'pill-' + id)));
const ofService = (service) => (item) => item.serviceId === undefined || item.serviceId === service;

const SUBSCRIPTIONS_TABS = [['feed', FT.t('tab_feed'), 'dynamic_feed'], ['channels', FT.t('channels'), 'group']];
const SOURCE_KEY = 'fathom-subs-source';

// Source filter of the subscriptions screen: 'all' or a service id, independent of the app-wide source.
// `used`: the services that have a subscription.
const subsSource = (params, used) => {
    let saved = null;
    try { saved = localStorage.getItem(SOURCE_KEY); } catch (e) {}
    const raw = params.src ?? saved;
    return used.some((s) => String(s.id) === raw) ? raw : 'all';
};

const subsPills = (tab, src, used) => {
    const go = (nextTab, nextSrc) => () => {
        try { localStorage.setItem(SOURCE_KEY, nextSrc); } catch (e) {}
        location.hash = `#/subscriptions?tab=${nextTab}&src=${nextSrc}`;
    };
    const sources = used.length > 1
        ? [h('span', { class: 'sep' }), FT.pill(FT.t('source_mix'), null, src === 'all', go(tab, 'all'), 'src-all'),
            ...used.map((s) => FT.pill(s.name, null, src === String(s.id), go(tab, String(s.id)), 'src-' + s.id))]
        : [];
    return h('div', { class: 'pills' }, [...SUBSCRIPTIONS_TABS.map(([id, label, icon]) => FT.pill(label, icon, id === tab, go(id, src), 'pill-' + id)), ...sources]);
};

FT.register('subscriptions', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        const tab = SUBSCRIPTIONS_TABS.some((t) => t[0] === params.tab) ? params.tab : 'feed';
        const lib = await FT.api.library(FT.store.service);
        if (!isCurrent()) return;
        const serviceOf = (item) => item.serviceId ?? FT.services[0].id;
        const used = FT.services.filter((s) => (lib.channels || []).some((c) => serviceOf(c) === s.id));
        const src = subsSource(params, used);
        const pills = subsPills(tab, src, used);
        const head = h('div', { class: 'head' }, h('h1', {}, FT.t('tab_subscriptions')));
        const wanted = (item) => src === 'all' || String(serviceOf(item)) === src;

        if (tab === 'feed') {
            await FT.listView(root, {
                before: [head, pills],
                empty: { icon: 'dynamic_feed', title: FT.t('no_recent_uploads'), text: FT.t('no_recent_uploads_hint') },
                fetch: async () => ({ items: ((await FT.api.feed(FT.store.service)).videos || []).filter(wanted), next: null }),
            }, isCurrent);
            return;
        }

        const channels = (lib.channels || []).filter(wanted);
        if (!channels.length) {
            root.replaceChildren(head, pills, FT.empty('group', FT.t('no_subscriptions'), FT.t('no_subscriptions_hint')));
            return;
        }
        const titled = src === 'all' && used.length > 1;
        const sections = used.map((service) => {
            const own = channels.filter((c) => serviceOf(c) === service.id);
            if (!own.length) return null;
            const grid = h('div', { class: 'grid channels' }, own.map((item) => {
                const card = FT.channelCard(item);
                card.onRemoved = () => { card.remove(); if (!grid.children.length) section.remove(); FT.focus.first(); };
                return card;
            }));
            const section = h('section', { class: 'subs-group' }, titled ? h('h2', {}, service.name) : null, grid);
            return section;
        });
        root.replaceChildren(head, pills, ...sections.filter(Boolean));
    },
});

// ---- Library: history, watch later, playlists, downloads ----

const LIBRARY_TABS = [['history', FT.t('tab_history'), 'history'], ['later', FT.t('watch_later'), 'schedule'], ['playlists', FT.t('playlists'), 'playlist_play'], ['downloads', FT.t('tab_downloads'), 'download']];

FT.register('library', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        // legacy ?tab=feed|channels redirect
        if (params.tab === 'feed' || params.tab === 'channels') { location.replace('#/subscriptions?tab=' + params.tab); return; }
        const tab = LIBRARY_TABS.some((t) => t[0] === params.tab) ? params.tab : 'history';
        const service = FT.store.service;
        const pills = tabPills('library', LIBRARY_TABS, tab);
        const head = h('div', { class: 'head' }, h('h1', {}, FT.t('tab_library')));
        const sameService = ofService(service);
        const before = [head, pills];

        if (tab === 'history') {
            await historyList(root, { before, continuing: false }, isCurrent);
            return;
        }

        if (tab === 'later' || tab === 'downloads') {
            await FT.listView(root, {
                before,
                empty: tab === 'later'
                    ? { icon: 'schedule', title: FT.t('nothing_saved'), text: FT.t('nothing_saved_hint') }
                    : { icon: 'download', title: FT.t('no_downloads'), text: FT.t('no_downloads_hint') },
                decorate: (row) => { if (tab === 'later') row.inWatchLater = true; },
                fetch: async () => {
                    const items = tab === 'later' ? (await FT.api.library(service)).watchLater : (await FT.api.downloads()).videos;
                    return { items: (items || []).filter(sameService), next: null };
                },
            }, isCurrent);
            return;
        }

        const lib = await FT.api.library(service);
        if (!isCurrent()) return;
        const cards = (lib.playlists || []).filter(sameService).map((item) => {
            const card = FT.playlistCard(item);
            card.onRemoved = () => { card.remove(); FT.focus.first(); };
            return card;
        });
        root.replaceChildren(...before, cards.length
            ? h('div', { class: 'grid' }, cards)
            : FT.empty('playlist_play', FT.t('no_bookmarks'), FT.t('no_bookmarks_hint')));
    },
});

// ---- A playlist ----

FT.register('playlist', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        const service = parseInt(params.s, 10);
        const id = params.u;
        let meta = null;
        await FT.listView(root, {
            before: [],
            empty: { icon: 'playlist_remove', title: FT.t('playlist_empty'), text: FT.t('playlist_empty_hint') },
            fetch: async (cursor) => {
                const body = await FT.api.playlist(service, id, cursor);
                if (!meta) {
                    meta = body.playlist;
                    const bookmark = FT.button(FT.t(meta.isBookmarked ? 'bookmarked' : 'bookmark'), 'star', () => {
                        const on = !meta.isBookmarked;
                        FT.api.bookmarkPlaylist({ id, name: meta.name }, on).then(() => {
                            meta.isBookmarked = on;
                            FT.toast(FT.t(on ? 'playlist_bookmarked' : 'bookmark_removed'));
                            bookmark.classList.toggle('on', on);
                        }).catch(() => FT.toast(FT.t('could_not_change')));
                    }, meta.isBookmarked ? 'on' : '', 'bookmark');
                    return { items: body.videos, next: body.nextPage, extra: [h('div', { class: 'head' }, h('h1', {}, meta.name), h('span', { class: 'card-meta' }, [meta.uploaderName, meta.videoCount >= 0 ? meta.videoCount + ' videos' : ''].filter(Boolean).join(' · ')), h('span', { class: 'grow' }), bookmark)] };
                }
                return { items: body.videos, next: body.nextPage };
            },
        }, isCurrent);
    },
});
})();

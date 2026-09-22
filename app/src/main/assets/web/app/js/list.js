// Screens that are lists of videos: search, history, the library and playlists. One list view serves them all:
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
            FT.button('Play', 'play_arrow', () => { location.hash = FT.link.watch(v); }, 'primary', 'pane-play'),
            FT.button(el.inWatchLater ? 'Saved' : 'Watch later', 'schedule', () => FT.api.watchLater(v, !el.inWatchLater).then(() => { el.inWatchLater = !el.inWatchLater; FT.toast(el.inWatchLater ? 'Saved to Watch later' : 'Removed from Watch later'); }).catch(() => FT.toast('Could not save that')), '', 'pane-later'),
            v.channelId ? FT.button('Channel', 'account_circle', () => { location.hash = FT.link.channel(v.channelId, v.serviceId); }, '', 'pane-channel') : null),
    ];
};

// options: { fetch(cursor) -> { items, next }, decorate(el), empty: { icon, title, text }, before: [elements] }
FT.listView = async (root, options, isCurrent) => {
    const list = h('div', { class: 'list' });
    const pane = h('aside', { class: 'pane' });
    const more = h('div', { class: 'more', hidden: true });
    let cursor = null;
    let loading = false;
    let done = false;

    const showPane = (el) => { if (el && el.item && el.kind === 'video') pane.replaceChildren(...paneFor(el)); };
    FT.screenSelect = (el) => {
        if (!list.contains(el)) return;
        showPane(el);
        const rows = FT.$$('.row-item', list);
        if (!done && rows.length - rows.indexOf(el) <= 3) loadMore();
    };

    const remove = (el) => {
        const rows = FT.$$('.row-item', list);
        const at = rows.indexOf(el);
        const neighbour = rows[at + 1] || rows[at - 1];
        el.remove();
        if (neighbour) FT.focus.select(neighbour); else FT.focus.first();
        if (!FT.$('.row-item', list)) root.replaceChildren(...(options.before || []), FT.empty(options.empty.icon, options.empty.title, options.empty.text));
    };

    const append = (items) => {
        for (const item of items) {
            const row = FT.videoRow(item);
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
            if (isCurrent()) FT.toast('Could not load more');
        } finally {
            loading = false;
        }
    }

    more.append(FT.button('Load more', 'expand_more', loadMore, '', 'load-more'));
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
    root.replaceChildren(...[...(options.before || []), ...(first.extra || []), first.items.length ? h('div', { class: 'split' }, h('div', {}, list, more), pane) : null].filter(Boolean));
    const firstRow = FT.$('.row-item', list);
    if (firstRow) showPane(firstRow);
};

const cleanup = () => { FT.screenSelect = null; };

// ---- Search ----

const searchField = (value) => {
    const input = h('input', { type: 'search', placeholder: 'Search videos, channels, playlists', value: value || '', enterkeyhint: 'search', autocomplete: 'off' });
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
            h('button', { type: 'button', class: 'suggest-del', 'aria-label': 'Remove ' + q, data: { f: '', key: 'del:' + q }, onclick: () => FT.api.forgetSearch(q).then(() => { row.remove(); onChange(); }) }, FT.icon('close')));
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
            root.replaceChildren(...[field, suggestions(history, () => {}), history.length ? null : FT.empty('search', 'Search ' + FT.store.serviceName(service), 'Type here, or type on your phone when the remote is connected.')].filter(Boolean));
            return;
        }
        // The first page decides which kinds of result exist; videos come first, channels and playlists are one press away.
        const first = await FT.api.search(service, q, null);
        if (!isCurrent()) return;
        const kinds = [['videos', 'Videos', 'play_circle', first.videos], ['channels', 'Channels', 'group', first.channels], ['playlists', 'Playlists', 'playlist_play', first.playlists]]
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
            empty: { icon: 'search_off', title: 'No results', text: 'Nothing found for "' + q + '".' },
            async fetch(cursor) {
                if (!cursor && !served) { served = true; return { items: first.videos || [], next: first.nextPage }; }
                const body = await FT.api.search(service, q, cursor);
                return { items: body.videos || [], next: body.nextPage };
            },
        }, isCurrent);
    },
});

// ---- History ----

FT.register('history', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        // ?filter=continuing: Home's "Continue watching" shelf, same source and filter, as a full list.
        const continuing = params.filter === 'continuing';
        const service = FT.store.service;
        const head = h('div', { class: 'head' }, h('h1', {}, continuing ? 'Continue watching' : 'History'));
        await FT.listView(root, {
            before: [head],
            empty: continuing
                ? { icon: 'play_circle', title: 'Nothing in progress', text: "Videos you start but don't finish show up here." }
                : { icon: 'history', title: 'Nothing watched yet', text: 'Videos you watch show up here.' },
            decorate: (row) => { row.fromHistory = true; },
            fetch: async () => {
                const items = (await FT.api.history()).videos || [];
                return { items: continuing ? items.filter((v) => v.serviceId === service && v.progress > 2 && v.progress < 95) : items, next: null };
            },
        }, isCurrent);
    },
});

// ---- Recommended (Home's "Recommended" shelf, opened out into a full list) ----

FT.register('recommended', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        const service = FT.store.service;
        const known = new Set();
        const head = h('div', { class: 'head' }, h('h1', {}, 'Recommended'));
        let served = false;
        await FT.listView(root, {
            before: [head],
            empty: { icon: 'video_library', title: 'Nothing to show yet', text: 'Search for something to get started.' },
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

// ---- Library: subscriptions feed, channels, playlists, watch later ----

const TABS = [['feed', 'Feed', 'dynamic_feed'], ['channels', 'Channels', 'group'], ['playlists', 'Playlists', 'playlist_play'], ['later', 'Watch later', 'schedule']];

FT.register('library', {
    leave: cleanup,
    async render(root, params, isCurrent) {
        const tab = TABS.some((t) => t[0] === params.tab) ? params.tab : 'feed';
        const service = FT.store.service;
        const pills = h('div', { class: 'pills' }, TABS.map(([id, label, icon]) => FT.pill(label, icon, id === tab, () => { location.hash = '#/library?tab=' + id; }, 'pill-' + id)));
        const head = h('div', { class: 'head' }, h('h1', {}, 'Subscriptions'));
        const sameService = (item) => item.serviceId === undefined || item.serviceId === service;

        if (tab === 'feed' || tab === 'later') {
            await FT.listView(root, {
                before: [head, pills],
                empty: tab === 'feed'
                    ? { icon: 'dynamic_feed', title: 'No recent uploads', text: 'Subscribe to channels and their newest videos appear here.' }
                    : { icon: 'schedule', title: 'Nothing saved', text: 'Use Options on any video to save it for later.' },
                decorate: (row) => { if (tab === 'later') { row.inWatchLater = true; } },
                fetch: async () => {
                    if (tab === 'feed') return { items: ((await FT.api.feed(service)).videos || []).filter(sameService), next: null };
                    return { items: ((await FT.api.library(service)).watchLater || []).filter(sameService), next: null };
                },
            }, isCurrent);
            return;
        }

        const lib = await FT.api.library(service);
        if (!isCurrent()) return;
        const items = (tab === 'channels' ? lib.channels : lib.playlists).filter(sameService);
        const cards = items.map((item) => {
            const card = tab === 'channels' ? FT.channelCard(item) : FT.playlistCard(item);
            card.onRemoved = () => { card.remove(); FT.focus.first(); };
            return card;
        });
        root.replaceChildren(head, pills, cards.length
            ? h('div', { class: 'grid' }, cards)
            : FT.empty(tab === 'channels' ? 'group' : 'playlist_play', tab === 'channels' ? 'No subscriptions yet' : 'No bookmarked playlists', tab === 'channels' ? 'Subscribe to a channel and it shows up here.' : 'Bookmark a playlist to keep it here.'));
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
            empty: { icon: 'playlist_remove', title: 'Empty playlist', text: 'There are no videos here.' },
            fetch: async (cursor) => {
                const body = await FT.api.playlist(service, id, cursor);
                if (!meta) {
                    meta = body.playlist;
                    const bookmark = FT.button(meta.isBookmarked ? 'Bookmarked' : 'Bookmark', 'star', () => {
                        const on = !meta.isBookmarked;
                        FT.api.bookmarkPlaylist({ id, name: meta.name }, on).then(() => {
                            meta.isBookmarked = on;
                            FT.toast(on ? 'Playlist bookmarked' : 'Bookmark removed');
                            bookmark.classList.toggle('on', on);
                        }).catch(() => FT.toast('Could not change that'));
                    }, meta.isBookmarked ? 'on' : '', 'bookmark');
                    return { items: body.videos, next: body.nextPage, extra: [h('div', { class: 'head' }, h('h1', {}, meta.name), h('span', { class: 'card-meta' }, [meta.uploaderName, meta.videoCount + ' videos'].filter(Boolean).join(' · ')), h('span', { class: 'grow' }), bookmark)] };
                }
                return { items: body.videos, next: body.nextPage };
            },
        }, isCurrent);
    },
});
})();

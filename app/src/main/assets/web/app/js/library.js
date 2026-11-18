// Library tabs and the continue-watching list.
(() => {
const h = FT.h;

// `continuing`: unfinished only (Home shelf).
const historyList = (root, { before, continuing }, scope) => {
    const service = FT.store.service;
    scope.subscribe(() => FT.api.onHistoryChange(() => { if (scope.live()) FT.reload(); }));
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
    }, scope);
};

FT.register('history', {
    async render(root, params, scope) {
        // Kept for ?filter=continuing and old links.
        if (params.filter !== 'continuing') { location.replace('#/library?tab=history'); return; }
        await historyList(root, { before: [h('div', { class: 'head' }, h('h1', {}, FT.t('continue_watching')))], continuing: true }, scope);
    },
});

// Selecting re-renders with ?tab=.
const tabPills = (route, tabs, active) => h('div', { class: 'pills' }, tabs.map(([id, label, icon]) => FT.pill(label, icon, id === active, () => { location.hash = `#/${route}?tab=${id}`; }, 'pill-' + id)));
const ofService = (service) => (item) => item.serviceId === undefined || item.serviceId === service;

const LIBRARY_TABS = [['history', FT.t('tab_history'), 'history'], ['later', FT.t('watch_later'), 'schedule'], ['liked', FT.t('liked_videos'), 'thumb_up'], ['playlists', FT.t('playlists'), 'playlist_play'], ['downloads', FT.t('tab_downloads'), 'download']];

// Tabs that are one plain video list.
const LIBRARY_LISTS = {
    later: {
        empty: { icon: 'schedule', title: FT.t('nothing_saved'), text: FT.t('nothing_saved_hint') },
        load: async (service) => (await FT.api.library(service)).watchLater,
        decorate: (row) => { row.inWatchLater = true; },
    },
    liked: {
        empty: { icon: 'thumb_up', title: FT.t('no_liked'), text: FT.t('no_liked_hint') },
        load: async () => (await FT.api.liked()).videos,
        decorate: (row) => { row.fromLiked = true; },
    },
    downloads: {
        empty: { icon: 'download', title: FT.t('no_downloads'), text: FT.t('no_downloads_hint') },
        load: async () => (await FT.api.downloads()).videos,
    },
};

FT.register('library', {
    async render(root, params, scope) {
        // Legacy ?tab=feed|channels.
        if (params.tab === 'feed' || params.tab === 'channels') { location.replace('#/subscriptions?tab=' + params.tab); return; }
        const tab = LIBRARY_TABS.some((t) => t[0] === params.tab) ? params.tab : 'history';
        const service = FT.store.service;
        const pills = tabPills('library', LIBRARY_TABS, tab);
        const head = h('div', { class: 'head' }, h('h1', {}, FT.t('tab_library')));
        const sameService = ofService(service);
        const before = [head, pills];

        if (tab === 'history') {
            await historyList(root, { before, continuing: false }, scope);
            return;
        }

        const source = LIBRARY_LISTS[tab];
        if (source) {
            await FT.listView(root, {
                before,
                queue: true,
                empty: source.empty,
                decorate: source.decorate,
                fetch: async () => ({ items: (await source.load(service)).filter(sameService), next: null }),
            }, scope);
            return;
        }

        const lib = await FT.api.library(service);
        if (!scope.live()) return;
        const cards = [...(lib.mine || []), ...(lib.playlists || []).filter(sameService)].map((item) => {
            const card = FT.playlistCard(item);
            card.onRemoved = () => { card.remove(); FT.focus.first(); };
            return card;
        });
        root.replaceChildren(...before, cards.length
            ? h('div', { class: 'grid' }, cards)
            : FT.empty('playlist_play', FT.t('no_bookmarks'), FT.t('no_bookmarks_hint')));
    },
});
})();

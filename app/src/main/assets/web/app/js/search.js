// Search: suggestions, result kinds, video list.
(() => {
const h = FT.h;

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
    keepFor: () => FT.KEEP_MS,
    async render(root, params, scope) {
        const q = (params.q || '').trim();
        const service = FT.store.service;
        const field = searchField(q);
        if (!q) {
            const history = await FT.api.searchHistory().catch(() => []);
            if (!scope.live()) return;
            root.replaceChildren(...[field, suggestions(history, () => {}), history.length ? null : FT.empty('search', FT.t('search_service', { name: FT.store.serviceName(service) }), FT.t('search_empty_hint'))].filter(Boolean));
            return;
        }
        root.replaceChildren(field, FT.ghost.quiet(h('div', { class: 'pills' }, [['videos', 'play_circle'], ['channels', 'group'], ['playlists', 'playlist_play']]
            .map(([id, icon], i) => FT.pill(FT.t(id), icon, i === 0, null, 'ghost-' + id)))), FT.ghostBody(FT.layout.current() === 'grid'));
        // Kinds from the first page; videos first.
        const first = await FT.api.search(service, q, null);
        if (!scope.live()) return;
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
        }, scope);
    },
});
})();

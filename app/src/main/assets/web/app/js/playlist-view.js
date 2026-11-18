// Own and followed playlists.
(() => {
const h = FT.h;

const ownPlaylistHead = (id, meta) => {
    const title = h('h1', {}, meta.name);
    const rename = FT.button(FT.t('rename'), 'title', () => {
        const name = (prompt(FT.t('playlist_name'), meta.name) || '').trim();
        if (!name || name === meta.name) return;
        FT.api.playlistRename(id, name)
            .then(() => { meta.name = name; title.textContent = name; FT.toast(FT.t('playlist_renamed')); })
            .catch(() => FT.toast(FT.t('could_not_change')));
    }, '', 'rename');
    const remove = FT.button(FT.t('delete_playlist'), 'delete', () => {
        if (!confirm(FT.t('confirm_delete_playlist', { name: meta.name }))) return;
        FT.api.playlistDelete(id)
            .then(() => { FT.toast(FT.t('playlist_deleted')); location.hash = '#/library?tab=playlists'; })
            .catch(() => FT.toast(FT.t('could_not_remove')));
    }, '', 'delete-playlist');
    return h('div', { class: 'head' }, title, h('span', { class: 'card-meta' }, FT.tn('count_videos', meta.videoCount)), h('span', { class: 'grow' }), rename, remove);
};

FT.register('mylist', {
    async render(root, params, scope) {
        const id = params.u;
        await FT.listView(root, {
            before: [],
            queue: true,
            empty: { icon: 'playlist_remove', title: FT.t('playlist_empty'), text: FT.t('playlist_empty_hint') },
            decorate: (row) => { row.fromPlaylist = id; },
            fetch: async () => {
                const body = await FT.api.myList(id);
                return { items: body.videos, next: null, extra: [ownPlaylistHead(id, body.playlist)] };
            },
        }, scope);
    },
});

FT.register('playlist', {
    keepFor: () => FT.KEEP_MS,
    async render(root, params, scope) {
        const service = parseInt(params.s, 10);
        const id = params.u;
        let meta = null;
        await FT.listView(root, {
            before: [],
            queue: true,
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
        }, scope);
    },
});
})();

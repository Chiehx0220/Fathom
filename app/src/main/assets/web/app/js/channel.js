// Channel: header, Subscribe, video list.
(() => {
const h = FT.h;

FT.register('channel', {
    keepFor: () => FT.KEEP_MS,
    async render(root, params, scope) {
        const service = parseInt(params.s, 10) || 0;
        const id = params.u;
        let header = null;
        await FT.listView(root, {
            before: [],
            empty: { icon: 'video_library', title: FT.t('no_videos'), text: FT.t('channel_empty') },
            fetch: async (cursor) => {
                const body = await FT.api.channel(service, id, cursor);
                if (header) return { items: body.videos || [], next: body.nextPage };
                const c = body.channel;
                header = c;
                const subscribe = FT.subscribeButton({ id: c.id, name: c.name, thumbnailUrl: c.thumbnailUrl });
                subscribe.set({ subscribed: c.isSubscribed, notify: c.isNotifying });
                FT.followState(scope, () => {
                    const since = performance.now();
                    return FT.api.state('', c.id).then((s) => subscribe.set(s, since));
                }, { topics: ['subscriptions'], covered: true });
                const top = h('div', { class: 'channel-head' },
                    FT.avatar(c.name, c.thumbnailUrl, 'lg'),
                    h('div', { style: 'flex:1;min-width:200px' }, h('h1', {}, c.name), h('p', {}, c.subscriberCount >= 0 ? FT.t('count_subscribers', { count: FT.count(c.subscriberCount) }) : '')),
                    subscribe.el);
                return { items: body.videos || [], next: body.nextPage, extra: [top] };
            },
        }, scope);
    },
});
})();

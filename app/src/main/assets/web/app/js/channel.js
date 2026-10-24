// Channel: header, Subscribe, video list.
(() => {
const h = FT.h;

FT.register('channel', {
    leave() { FT.screenSelect = null; },
    async render(root, params, isCurrent) {
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
                let subscribed = !!c.isSubscribed;
                const button = FT.button(FT.t(subscribed ? 'subscribed' : 'subscribe'), subscribed ? 'notifications_active' : 'notifications', () => {
                    const on = !subscribed;
                    subscribed = on;
                    paint();
                    FT.api.subscribe({ id: c.id, name: c.name, thumbnailUrl: c.thumbnailUrl }, on).catch(() => { subscribed = !on; paint(); FT.toast(FT.t('action_failed')); });
                }, subscribed ? '' : 'primary', 'subscribe');
                const paint = () => {
                    button.replaceChildren(FT.icon(subscribed ? 'notifications_active' : 'notifications'), FT.t(subscribed ? 'subscribed' : 'subscribe'));
                    button.classList.toggle('primary', !subscribed);
                };
                const top = h('div', { class: 'channel-head' },
                    FT.avatar(c.name, c.thumbnailUrl, 'lg'),
                    h('div', { style: 'flex:1;min-width:200px' }, h('h1', {}, c.name), h('p', {}, c.subscriberCount >= 0 ? FT.t('count_subscribers', { count: FT.count(c.subscriberCount) }) : '')),
                    button);
                return { items: body.videos || [], next: body.nextPage, extra: [top] };
            },
        }, isCurrent);
    },
});
})();

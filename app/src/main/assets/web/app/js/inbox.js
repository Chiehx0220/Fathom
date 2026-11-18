// Notification inbox: top-bar bell, unread count, upload list.
(() => {
const h = FT.h;
let unread = 0;

const when = new Intl.RelativeTimeFormat(document.documentElement.lang || undefined, { numeric: 'auto' });
const ago = (timestamp) => {
    const minutes = Math.round((timestamp - Date.now()) / 60000);
    if (Math.abs(minutes) < 60) return when.format(minutes, 'minute');
    const hours = Math.round(minutes / 60);
    if (Math.abs(hours) < 48) return when.format(hours, 'hour');
    return when.format(Math.round(hours / 24), 'day');
};

const refresh = async () => {
    try { unread = (await FT.api.notifications({ count: 'true' })).unread; } catch (e) { return; }
    // Pre-open count.
    if (FT.route.name === 'notifications') unread = 0;
    FT.renderTop();
};

FT.inbox = {
    button(active) {
        const badge = unread > 0 ? h('span', { class: 'badge' }, unread > 99 ? '99+' : String(unread)) : null;
        return h('a', { class: 'rbtn bell' + (active ? ' on' : ''), href: '#/notifications', 'aria-label': FT.t('notifications'), data: { f: '', key: 'notifications' } },
            FT.icon(unread > 0 ? 'notifications_active' : 'notifications'), badge);
    },
    refresh,
};

document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
FT.events.on('inbox', () => { if (!document.hidden) refresh(); });

FT.register('notifications', {
    async render(root, params, scope) {
        const body = await FT.api.notifications();
        if (!scope.live()) return;
        unread = 0;
        FT.renderTop();
        const clear = () => FT.api.notifications({ clear: 'true' }).then(() => FT.reload()).catch(() => FT.toast(FT.t('could_not_save')));
        const head = h('div', { class: 'head' }, h('h1', {}, FT.t('notifications')),
            body.items.length ? FT.button(FT.t('clear_all'), 'delete', clear, '', 'clear-all') : null);
        await FT.listView(root, {
            before: [head],
            empty: { icon: 'notifications', title: FT.t('inbox_empty'), text: FT.t('inbox_empty_hint') },
            fetch: async () => ({ items: body.items.map((entry) => ({ ...entry.video, uploadDate: ago(entry.timestamp), isNew: entry.isNew })), next: null }),
            decorate: (row) => { if (row.item.isNew) row.classList.add('fresh'); },
        }, scope);
    },
});
})();

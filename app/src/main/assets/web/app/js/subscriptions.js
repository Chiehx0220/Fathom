// Subscriptions: feed and channel tabs, service and group filters.
(() => {
const h = FT.h;

const SUBSCRIPTIONS_TABS = [['feed', FT.t('tab_feed'), 'dynamic_feed'], ['channels', FT.t('channels'), 'group']];
const SOURCE_KEY = 'fathom-subs-source';
const GROUP_KEY = 'fathom-subs-group';

// 'all' | service id; `used`: services with subscriptions.
const subsSource = (params, used) => {
    let saved = null;
    try { saved = localStorage.getItem(SOURCE_KEY); } catch (e) {}
    const raw = params.src ?? saved;
    return used.some((s) => String(s.id) === raw) ? raw : 'all';
};

// Phone group name; '' = all.
const subsGroup = (params, groups) => {
    let saved = null;
    try { saved = localStorage.getItem(GROUP_KEY); } catch (e) {}
    const name = params.grp ?? saved;
    return groups.some((g) => g.name === name) ? name : '';
};

const subsPills = (tab, src, used, grp, groups) => {
    const go = (nextTab, nextSrc, nextGrp = grp) => () => {
        try { localStorage.setItem(SOURCE_KEY, nextSrc); localStorage.setItem(GROUP_KEY, nextGrp); } catch (e) {}
        location.hash = `#/subscriptions?tab=${nextTab}&src=${nextSrc}&grp=${FT.enc(nextGrp)}`;
    };
    const sources = used.length > 1
        ? [h('span', { class: 'sep' }), FT.pill(FT.t('source_mix'), null, src === 'all', go(tab, 'all'), 'src-all'),
            ...used.map((s) => FT.pill(s.name, null, src === String(s.id), go(tab, String(s.id)), 'src-' + s.id))]
        : [];
    const row = h('div', { class: 'pills' }, [...SUBSCRIPTIONS_TABS.map(([id, label, icon]) => FT.pill(label, icon, id === tab, go(id, src), 'pill-' + id)), ...sources]);
    if (!groups.length) return row;
    const named = h('div', { class: 'pills' }, [FT.pill(FT.t('groups_all'), null, !grp, go(tab, src, ''), 'grp-all'),
        ...groups.map((g) => FT.pill(g.name, null, g.name === grp, go(tab, src, g.name), 'grp:' + g.name))]);
    return h('div', { class: 'pill-rows' }, row, named);
};

FT.register('subscriptions', {
    // Feed memo lifetime (api.js); channels change from the watch page.
    keepFor: (params) => (params.tab === 'channels' ? 0 : 5 * 60 * 1000),
    async render(root, params, scope) {
        const tab = SUBSCRIPTIONS_TABS.some((t) => t[0] === params.tab) ? params.tab : 'feed';
        const [lib, groupList] = await Promise.all([FT.api.library(FT.store.service), FT.api.groups().catch(() => ({ groups: [] }))]);
        if (!scope.live()) return;
        const groups = groupList.groups || [];
        const grp = subsGroup(params, groups);
        const members = grp ? new Set(groups.find((g) => g.name === grp).channels) : null;
        const serviceOf = (item) => item.serviceId ?? FT.services[0].id;
        const used = FT.services.filter((s) => (lib.channels || []).some((c) => serviceOf(c) === s.id));
        const src = subsSource(params, used);
        const pills = subsPills(tab, src, used, grp, groups);
        const head = h('div', { class: 'head' }, h('h1', {}, FT.t('tab_subscriptions')));
        const wanted = (item) => src === 'all' || String(serviceOf(item)) === src;

        if (tab === 'feed') {
            await FT.listView(root, {
                before: [head, pills],
                empty: { icon: 'dynamic_feed', title: FT.t('no_recent_uploads'), text: FT.t('no_recent_uploads_hint') },
                fetch: async () => ({ items: ((await FT.api.feed(FT.store.service, grp)).videos || []).filter(wanted), next: null }),
            }, scope);
            return;
        }

        const channels = (lib.channels || []).filter(wanted).filter((c) => !members || members.has(c.id));
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
})();

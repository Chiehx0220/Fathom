// Moving between screens without reloading the page. A screen is `{ render(root, params, isCurrent) }`; the router draws the top bar,
// asks the screen to fill the page, docks the player to suit, and hands the selection back to where it was.
(() => {
const h = FT.h;
const screens = {};
let active = null;
let token = 0;
const scrolls = new Map();

FT.register = (name, screen) => { screens[name] = screen; };
FT.route = { name: '', params: {}, key: '' };

const parse = () => {
    const hash = location.hash.replace(/^#\/?/, '');
    const [name, query = ''] = hash.split('?');
    return { name: name || 'home', params: Object.fromEntries(new URLSearchParams(query)) };
};

const TABS = [
    { name: 'home', label: FT.t('tab_home'), icon: 'home', match: ['home'] },
    { name: 'subscriptions', label: FT.t('tab_subscriptions'), icon: 'subscriptions', match: ['subscriptions', 'channel'] },
    { name: 'library', label: FT.t('tab_library'), icon: 'video_library', match: ['library', 'playlist', 'mylist', 'history'] },
    { name: 'settings', label: FT.t('tab_settings'), icon: 'settings', match: ['settings'] },
];

// ---- The top bar ----

const chooseService = (anchor) => {
    FT.menu.open(FT.services.map((s) => ({
        icon: s.id === FT.store.service ? 'radio_button_checked' : 'radio_button_unchecked', label: s.name, run: () => FT.setService(s.id),
    })), { title: FT.t('source'), anchor });
};

// the tab the watch page was opened from stays active
let origin = 'home';

const renderTop = () => {
    const route = FT.route.name === 'watch' ? origin : FT.route.name;
    const top = FT.$('#top');
    const searching = route === 'search';
    top.replaceChildren(
        h('a', { class: 'brand', href: '#/home', 'aria-label': FT.t('brand_home') }, FT.brandMark(), h('span', {}, 'Fathom')),
        h('nav', { class: 'tabs' }, TABS.map((tab) => h('a', {
            class: 'tab' + (tab.match.includes(route) ? ' on' : ''), href: '#/' + tab.name, data: { f: '', key: 'tab-' + tab.name },
        }, FT.icon(tab.icon, tab.match.includes(route) ? 'fill' : ''), h('span', {}, tab.label)))),
        h('div', { class: 'tools' },
            h('button', { type: 'button', class: 'chip', data: { f: '', key: 'source' }, onclick: (e) => chooseService(e.currentTarget) },
                h('span', {}, FT.store.serviceName(FT.store.service)), FT.icon('expand_more')),
            FT.deepFlow.active ? h('a', { class: 'dfbadge', href: '#/settings', title: FT.deepFlow.status(), 'aria-label': FT.t('deep_flow_badge_label'), data: { f: '', key: 'deepflow' } },
                FT.deepFlow.icon(), h('span', {}, FT.t('deep_flow_badge'))) : null,
            h('a', { class: 'rbtn' + (searching ? ' on' : ''), href: '#/search', 'aria-label': FT.t('search'), data: { f: '', key: 'search' } }, FT.icon('search'))));
};
FT.renderTop = renderTop;

// ---- Showing a screen ----

const show = async () => {
    const { name, params } = parse();
    const screen = screens[name] || screens.home;
    if (active) {
        FT.focus.remember();
        scrolls.set(FT.route.key, scrollY);
        if (active.leave) active.leave();
    }
    if (FT.menu.isOpen()) FT.menu.close();
    FT.screenSelect = null;
    const mine = ++token;
    const key = name + '?' + Object.keys(params).sort().map((k) => k + '=' + params[k]).join('&');
    FT.route = { name, params, key };
    if (name !== 'watch') origin = name;
    document.body.dataset.route = name;
    if (FT.player) FT.player.dock(name === 'watch' ? 'full' : 'mini');
    active = screen;
    renderTop();
    const root = FT.$('#screen');
    root.replaceChildren(FT.loading());
    FT.focus.clear();
    const isCurrent = () => mine === token;
    try {
        await screen.render(root, params, isCurrent);
    } catch (error) {
        if (isCurrent()) root.replaceChildren(FT.failure(error.message, show));
    }
    if (!isCurrent()) return;
    FT.focus.restore(key);
    scrollTo({ top: scrolls.get(key) || 0, behavior: 'instant' });
    if (screen.ready) screen.ready(root, params);
};
FT.reload = show;

// Every selection passes through here: the watch screen leaves player mode when a control is picked, and a screen may listen too.
FT.onSelect = (el) => {
    if (FT.route.name === 'watch' && FT.watchMode && FT.watchMode.onPlayer && el) {
        FT.watchMode.onPlayer = false;
        document.body.classList.remove('player-mode');
    }
    if (FT.screenSelect) FT.screenSelect(el);
};

FT.setService = (id) => {
    if (id === FT.store.service) return;
    FT.store.setService(id);
    FT.toast(FT.t('source_changed', { name: FT.store.serviceName(id) }));
    const name = FT.route.name;
    // A channel or playlist belongs to one source, so switching leaves it; screens that follow the source start over; the rest only need the top bar.
    if (name === 'channel' || name === 'playlist') location.hash = '#/home';
    else if (['home', 'subscriptions', 'library', 'search'].includes(name)) show();
    else renderTop();
};

// Back: close a menu, leave fullscreen, else go to the previous screen.
FT.back = () => {
    if (FT.menu.isOpen()) { FT.menu.close(); return; }
    if (FT.player && FT.player.isFullscreen()) { FT.player.fullscreen(false); return; }
    if (FT.route.name === 'home') return;
    if (history.length > 1) history.back();
    else location.hash = '#/home';
};

FT.start = () => {
    if (!location.hash) history.replaceState(null, '', '#/home');
    addEventListener('hashchange', show);
    show();
};
})();

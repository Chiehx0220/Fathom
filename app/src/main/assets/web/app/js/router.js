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
    { name: 'home', label: 'Home', icon: 'home', match: ['home', 'watch'] },
    { name: 'library', label: 'Subscriptions', icon: 'subscriptions', match: ['library', 'channel', 'playlist'] },
    { name: 'history', label: 'History', icon: 'history', match: ['history'] },
    { name: 'settings', label: 'Settings', icon: 'settings', match: ['settings'] },
];

// ---- The top bar ----

const chooseService = (anchor) => {
    FT.menu.open(FT.services.map((s) => ({
        icon: s.id === FT.store.service ? 'radio_button_checked' : 'radio_button_unchecked', label: s.name, run: () => FT.setService(s.id),
    })), { title: 'Source', anchor });
};

const renderTop = () => {
    const route = FT.route.name;
    const top = FT.$('#top');
    const searching = route === 'search';
    top.replaceChildren(
        h('a', { class: 'brand', href: '#/home', 'aria-label': 'Fathom home' }, h('span', { class: 'brand-mark' }, FT.icon('straighten')), h('span', {}, 'Fathom')),
        h('nav', { class: 'tabs' }, TABS.map((tab) => h('a', {
            class: 'tab' + (tab.match.includes(route) ? ' on' : ''), href: '#/' + tab.name, data: { f: '', key: 'tab-' + tab.name },
        }, FT.icon(tab.icon, tab.match.includes(route) ? 'fill' : ''), h('span', {}, tab.label)))),
        h('div', { class: 'tools' },
            h('button', { type: 'button', class: 'chip', data: { f: '', key: 'source' }, onclick: (e) => chooseService(e.currentTarget) },
                h('span', {}, FT.store.serviceName(FT.store.service)), FT.icon('expand_more')),
            h('a', { class: 'rbtn' + (searching ? ' on' : ''), href: '#/search', 'aria-label': 'Search', data: { f: '', key: 'search' } }, FT.icon('search')),
            h('button', { type: 'button', class: 'rbtn' + (FT.remote.active ? ' on' : ''), id: 'cast-btn', 'aria-label': 'Connect phone remote', data: { f: '', key: 'cast' }, onclick: () => FT.remote.toggle() },
                FT.icon(FT.remote.active ? 'cast_connected' : 'cast'))));
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
    FT.toast('Source: ' + FT.store.serviceName(id));
    const name = FT.route.name;
    // A channel or playlist belongs to one source, so switching leaves it; screens that follow the source start over; the rest only need the top bar.
    if (name === 'channel' || name === 'playlist') location.hash = '#/home';
    else if (['home', 'library', 'search'].includes(name)) show();
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

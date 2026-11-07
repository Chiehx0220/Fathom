// Screen router (no reload): screen = { render(root, params, scope), keepFor?(params) → ms, ready? }.
(() => {
const h = FT.h;
const screens = {};
const scrolls = new Map();
// Finished screens that opt in (keepFor) stay detached here; back restores them as left.
const kept = new Map();
const KEPT_MAX = 4;
// How long a kept page may be restored after leaving it.
FT.KEEP_MS = 30 * 60 * 1000;
let current = null;
let fresh = false;

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

// watch page keeps origin tab active
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

// Per drawn screen: teardown (dispose), external feeds (subscribe: stopped while kept), selection and resume hooks.
const lifeOf = () => {
    const life = { alive: true, disposers: [], subs: [], resumes: [], select: null };
    life.scope = {
        live: () => life.alive,
        dispose: (fn) => { life.disposers.push(fn); },
        subscribe: (start) => { if (life.alive) life.subs.push({ start, stop: start() }); },
        onSelect: (fn) => { life.select = fn; FT.screenSelect = fn; },
        onResume: (fn) => { life.resumes.push(fn); },
    };
    return life;
};
const pause = (life) => life.subs.forEach((sub) => { if (sub.stop) sub.stop(); sub.stop = null; });
const unpause = (life, age) => {
    life.subs.forEach((sub) => { sub.stop = sub.start(); });
    life.resumes.forEach((fn) => fn(age));
};
const end = (life) => {
    life.alive = false;
    pause(life);
    life.disposers.splice(0).forEach((fn) => fn());
};

const dropKept = (key) => {
    const entry = kept.get(key);
    if (!entry) return;
    kept.delete(key);
    end(entry.life);
};

const leaveCurrent = () => {
    const { screen, params, key, life, done } = current;
    const ttl = screen.keepFor ? screen.keepFor(params) : 0;
    if (!fresh && done && ttl > 0) {
        kept.set(key, { life, ttl, nodes: [...FT.$('#screen').childNodes], at: Date.now() });
        pause(life);
        if (kept.size > KEPT_MAX) dropKept(kept.keys().next().value);
        return;
    }
    end(life);
};

const show = async () => {
    const reload = fresh;
    const { name, params } = parse();
    const screen = screens[name] || screens.home;
    const key = name + '?' + Object.keys(params).sort().map((k) => k + '=' + params[k]).join('&');
    if (current) {
        FT.focus.remember();
        scrolls.set(FT.route.key, scrollY);
        leaveCurrent();
    }
    fresh = false;
    if (FT.menu.isOpen()) FT.menu.close();
    FT.screenSelect = null;
    FT.route = { name, params, key };
    if (name !== 'watch') origin = name;
    document.body.dataset.route = name;
    if (FT.player) FT.player.dock(name === 'watch' ? 'full' : 'mini');
    renderTop();
    const root = FT.$('#screen');
    FT.focus.clear();
    const back = reload ? null : kept.get(key);
    if (back && Date.now() - back.at <= back.ttl) {
        kept.delete(key);
        current = { screen, params, key, life: back.life, done: true };
        root.replaceChildren(...back.nodes);
        FT.screenSelect = back.life.select;
        FT.focus.restore(key);
        scrollTo({ top: scrolls.get(key) || 0, behavior: 'instant' });
        unpause(back.life, Date.now() - back.at);
        return;
    }
    dropKept(key);
    const life = lifeOf();
    current = { screen, params, key, life, done: false };
    root.replaceChildren(FT.loading());
    try {
        await screen.render(root, params, life.scope);
    } catch (error) {
        if (life.alive) root.replaceChildren(FT.failure(error.message, FT.reload));
    }
    if (!life.alive) return;
    current.done = true;
    FT.focus.restore(key);
    scrollTo({ top: scrolls.get(key) || 0, behavior: 'instant' });
    if (screen.ready) screen.ready(root, params);
};
// Redraw the current screen from scratch (scroll and selection kept); refresh also returns to the top.
FT.reload = () => { fresh = true; return show(); };
FT.refresh = () => { scrolls.delete(FT.route.key); return FT.reload(); };
// Data behind kept screens changed (source switch): none may be restored.
FT.forgetScreens = () => [...kept.keys()].forEach(dropKept);

// All selections pass here: watch screen leaves player mode; screens may listen.
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
    FT.forgetScreens();
    FT.toast(FT.t('source_changed', { name: FT.store.serviceName(id) }));
    const name = FT.route.name;
    // Channel/playlist is per source: switching leaves it; source-following screens restart; rest redraw top bar.
    if (name === 'channel' || name === 'playlist') location.hash = '#/home';
    else if (['home', 'subscriptions', 'library', 'search'].includes(name)) FT.reload();
    else renderTop();
};

// Back: close menu, leave fullscreen, else previous screen.
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

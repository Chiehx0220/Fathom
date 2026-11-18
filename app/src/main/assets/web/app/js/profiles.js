// Profile menu; switch restarts the app, cover until reconnect.
(() => {
const h = FT.h;
const POLL_MS = 1000;
const POLL_TIMEOUT_MS = 3000;
// After this, old profile + not switching = rolled back.
const SETTLE_MS = 4000;
const GIVE_UP_MS = 90000;
const refusal = (reason) => {
    if (reason === 'car_connected') return FT.t('profile_refused_car');
    if (reason === 'sync_running') return FT.t('profile_refused_sync');
    if (reason === 'backup_running') return FT.t('profile_refused_backup');
    return FT.t('profile_switch_failed');
};

let list = [];
let running = '';
let shown = true;
let cover = null;

const initial = (name) => Array.from(name.trim())[0] || '?';

// Contrast text colour.
const textOn = (hex) => {
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
    return 0.299 * r + 0.587 * g + 0.114 * b > 160 ? '#111' : '#fff';
};

const avatar = (profile) => h('span', { class: 'pavatar', style: profile.color ? `--pa:${profile.color};--pc:${textOn(profile.color)}` : '' }, profile.emoji || initial(profile.name).toUpperCase());

const current = () => list.find((profile) => profile.id === running) || list[0];

const timeout = (promise) => Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), POLL_TIMEOUT_MS))]);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ---- Cover ----

const show = (...content) => {
    if (!cover) {
        cover = h('div', { class: 'switching', role: 'alertdialog', 'aria-live': 'polite' });
        document.body.append(cover);
        FT.focus.trap(cover);
    }
    cover.replaceChildren(...content);
};

const hide = () => {
    if (!cover) return;
    cover.remove();
    cover = null;
    FT.focus.trap(null);
    FT.focus.clear();
};

const lost = () => {
    const retry = h('button', { type: 'button', class: 'btn primary', data: { f: '', key: 'profile-reload' }, onclick: () => location.reload() }, FT.t('profile_reload'));
    show(h('strong', {}, FT.t('profile_lost')), h('p', { class: 'hint' }, FT.t('profile_lost_hint')), retry);
    FT.focus.select(retry, { scroll: false });
};

// Drop state of the old profile.
const arrived = () => {
    FT.api.forget();
    FT.profiles.reopen();
};

const wait = async (target, from) => {
    const started = Date.now();
    while (Date.now() - started < GIVE_UP_MS) {
        await sleep(POLL_MS);
        let body = null;
        try { body = await timeout(FT.api.profiles()); } catch (e) { continue; }
        if (body.running === target.id) return arrived();
        if (!body.switching && body.running === from && Date.now() - started > SETTLE_MS) {
            hide();
            FT.toast(FT.t('profile_switch_failed'));
            return;
        }
    }
    lost();
};

const switchTo = async (target) => {
    const from = running;
    show(h('div', { class: 'spinner' }), h('strong', {}, FT.t('profile_switching', { name: target.name })), h('p', { class: 'hint' }, FT.t('profile_switching_hint')));
    let body;
    try {
        body = await FT.api.profiles(target.id);
    } catch (e) {
        hide();
        FT.toast(FT.t('profile_switch_failed'));
        return;
    }
    if (body.refused) {
        hide();
        FT.toast(refusal(body.refused));
        return;
    }
    wait(target, from);
};

// ---- Top bar ----

const choose = (anchor) => {
    FT.menu.open(list.map((profile) => ({
        lead: avatar(profile), label: profile.name, on: profile.id === running, run: () => { if (profile.id !== running) switchTo(profile); },
    })), { title: FT.t('profile_menu_title'), anchor });
};

FT.profiles = {
    reopen() {
        location.hash = '#/home';
        location.reload();
    },

    button() {
        if (!shown || !list.length) return null;
        return h('button', { type: 'button', class: 'rbtn pbtn', 'aria-label': FT.t('profile_switch'), data: { f: '', key: 'profile' }, onclick: (e) => choose(e.currentTarget) }, avatar(current()));
    },

    async load() {
        try {
            const body = await FT.api.profiles();
            list = body.profiles;
            running = body.running;
            shown = body.showInTopBar !== false;
        } catch (e) {
            list = [];
        }
        FT.renderTop();
    },
};

// Profile list / top-bar setting changes.
document.addEventListener('visibilitychange', () => { if (!document.hidden && !cover) FT.profiles.load(); });
for (const topic of ['profiles', 'settings']) FT.events.on(topic, () => { if (!document.hidden && !cover) FT.profiles.load(); });
})();

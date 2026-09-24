// Settings: the theme, what the home feed shows, and the phone remote. Theme changes apply at once; the rest is saved on the phone.
(() => {
const h = FT.h;

const FEED_MODES = [['mix', 'Mix', 'Recommendations and subscriptions'], ['subs', 'Subscriptions', 'Only your channels'], ['recs', 'Recommendations', 'Only suggested videos']];

// A palette's swatch is drawn by the stylesheet itself: it carries the same attributes as <html>, so it shows the palette in the current mode.
const syncArt = (art) => {
    const root = document.documentElement;
    art.dataset.scheme = root.dataset.scheme;
    if (root.hasAttribute('data-oled')) art.setAttribute('data-oled', ''); else art.removeAttribute('data-oled');
};

const swatch = (palette) => {
    const art = h('div', { class: 'swatch-art', data: { palette: palette.id } }, h('i', {}), h('i', {}));
    syncArt(art);
    const el = h('button', { type: 'button', class: 'swatch' + (FT.theme.palette() === palette.id ? ' on' : ''), data: { f: '', key: 'palette-' + palette.id },
        onclick: () => {
            FT.theme.setPalette(palette.id);
            FT.$$('.swatch').forEach((s) => s.classList.toggle('on', s === el));
        } },
        art, h('b', {}, palette.name), h('span', {}, palette.note));
    return el;
};

const modePills = () => {
    const row = h('div', { class: 'seg' }, FT.modes.map(([id, label]) => {
        const pill = FT.pill(label, null, FT.theme.mode() === id, () => {
            FT.theme.setMode(id);
            FT.$$('.pill', row).forEach((p) => p.classList.toggle('on', p === pill));
            FT.$$('.swatch-art').forEach(syncArt);
        }, 'mode-theme-' + id);
        return pill;
    }));
    return row;
};

const toggle = (title, hint, on, save, key) => {
    const sw = h('button', { type: 'button', class: 'switch' + (on ? ' on' : ''), role: 'switch', 'aria-checked': String(on), data: { f: '', key }, onclick: () => {
        on = !on;
        sw.classList.toggle('on', on);
        sw.setAttribute('aria-checked', String(on));
        save(on).then(() => { FT.api.forget(); }).catch(() => { on = !on; sw.classList.toggle('on', on); FT.toast('Could not save that'); });
    } });
    return h('div', { class: 'setting' }, h('div', {}, h('b', {}, title), h('span', { class: 'hint' }, hint)), sw);
};

FT.register('settings', {
    async render(root, params, isCurrent) {
        const values = await FT.api.settings().catch(() => null);
        if (!isCurrent()) return;

        const modes = h('div', { class: 'seg' }, FEED_MODES.map(([id, label]) => {
            const pill = FT.pill(label, null, values && values.homeFeedMode === id, () => {
                FT.api.settings({ homeFeedMode: id }).then(() => {
                    FT.$$('.pill', modes).forEach((p) => p.classList.toggle('on', p === pill));
                    FT.api.forget();
                    FT.toast('Home feed: ' + label);
                }).catch(() => FT.toast('Could not save that'));
            }, 'mode-' + id);
            return pill;
        }));

        const sections = [
            h('section', {}, h('h2', {}, 'Theme'), h('p', { class: 'hint' }, 'Applies to this browser only.'),
                h('div', { style: 'margin-top:12px' }, modePills()), h('div', { class: 'swatches' }, FT.palettes.map(swatch))),
        ];
        if (values) {
            sections.push(h('section', {}, h('h2', {}, 'Home feed'), h('p', { class: 'hint' }, 'What the recommendations shelf is made of.'), h('div', { style: 'margin-top:12px' }, modes)));
            sections.push(h('section', { style: 'display:flex;flex-direction:column;gap:10px' }, h('h2', {}, 'Filters'),
                toggle('Hide watched videos', 'Leave videos you have already watched out of lists.', !!values.hideWatched, (on) => FT.api.settings({ hideWatched: on }), 'hide-watched'),
                toggle('Hide Shorts', 'Leave out vertical videos shorter than two minutes.', !!values.hideShorts, (on) => FT.api.settings({ hideShorts: on }), 'hide-shorts')));
        }
        sections.push(h('section', {}, h('h2', {}, 'Phone remote'),
            h('p', { class: 'hint' }, FT.remote.active ? 'Connected. Open the remote on your phone to control this screen.' : 'Use your phone as a remote: press connect here, then open the remote on the phone.'),
            h('div', { class: 'btns', style: 'margin-top:12px' },
                FT.button(FT.remote.active ? 'Disconnect' : 'Connect phone remote', FT.remote.active ? 'cast_connected' : 'cast', () => { FT.remote.toggle(); setTimeout(() => FT.reload(), 600); }, 'primary', 'remote'))));
        sections.push(h('section', {}, h('h2', {}, 'Full screen'),
            h('p', { class: 'hint' }, 'A remote cannot start real full screen: the browser only allows it from a click on the page. Press F11 once in your browser (or use its full screen option) and it stays on as you move between videos.')));

        root.replaceChildren(h('div', { class: 'head' }, h('h1', {}, 'Settings')), h('div', { class: 'settings' }, sections));
    },
});
})();

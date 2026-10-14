// Settings: the theme, what the home feed shows, and the phone remote. Theme changes apply at once; the rest is saved on the phone.
(() => {
const h = FT.h;

const FEED_MODES = [['mix', FT.t('feed_mix'), FT.t('feed_mix_note')], ['subs', FT.t('tab_subscriptions'), FT.t('feed_subs_note')], ['recs', FT.t('recommendations'), FT.t('feed_recs_note')]];

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
        save(on).then(() => { FT.api.forget(); }).catch(() => { on = !on; sw.classList.toggle('on', on); FT.toast(FT.t('could_not_save')); });
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
                    FT.toast(FT.t('feed_changed', { name: label }));
                }).catch(() => FT.toast(FT.t('could_not_save')));
            }, 'mode-' + id);
            return pill;
        }));

        const sections = [
            h('section', {}, h('h2', {}, FT.t('settings_theme')), h('p', { class: 'hint' }, FT.t('settings_theme_hint')),
                h('div', { style: 'margin-top:12px' }, modePills()), h('div', { class: 'swatches' }, FT.palettes.map(swatch))),
        ];
        if (values) {
            sections.push(h('section', {}, h('h2', {}, FT.t('settings_feed')), h('p', { class: 'hint' }, FT.t('settings_feed_hint')), h('div', { style: 'margin-top:12px' }, modes)));
            sections.push(h('section', { style: 'display:flex;flex-direction:column;gap:10px' }, h('h2', {}, FT.t('settings_filters')),
                toggle(FT.t('filter_hide_watched'), FT.t('filter_hide_watched_hint'), !!values.hideWatched, (on) => FT.api.settings({ hideWatched: on }), 'hide-watched'),
                toggle(FT.t('filter_hide_shorts'), FT.t('filter_hide_shorts_hint'), !!values.hideShorts, (on) => FT.api.settings({ hideShorts: on }), 'hide-shorts')));
        }
        sections.push(h('section', {}, h('h2', {}, FT.t('settings_remote')),
            h('p', { class: 'hint' }, FT.t(FT.remote.active ? 'remote_hint_connected' : 'remote_hint_idle')),
            h('div', { class: 'btns', style: 'margin-top:12px' },
                FT.button(FT.t(FT.remote.active ? 'disconnect' : 'connect_remote'), 'cast', () => { FT.remote.toggle(); setTimeout(() => FT.reload(), 600); }, 'primary', 'remote'))));
        sections.push(h('section', {}, h('h2', {}, FT.t('settings_fullscreen')),
            h('p', { class: 'hint' }, FT.t('settings_fullscreen_hint'))));

        root.replaceChildren(h('div', { class: 'head' }, h('h1', {}, FT.t('tab_settings'))), h('div', { class: 'settings' }, sections));
    },
});
})();

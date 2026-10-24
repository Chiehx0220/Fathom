// Settings: the theme, list filters, and the phone remote. Theme changes apply at once; the rest is saved on the phone.
(() => {
const h = FT.h;

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

// Text with an {icon} placeholder replaced by the glyph.
const withIcon = (text, icon) => {
    const [before, after] = text.split('{icon}');
    return after === undefined ? [text] : [before, FT.icon(icon), after];
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
        on = !sw.classList.contains('on');
        sw.classList.toggle('on', on);
        sw.setAttribute('aria-checked', String(on));
        save(on).then(() => { FT.api.forget(); }).catch(() => { on = !on; sw.classList.toggle('on', on); FT.toast(FT.t('could_not_save')); });
    } });
    return h('div', { class: 'setting' }, h('div', {}, h('b', {}, title), h('span', { class: 'hint' }, hint)), sw);
};

// Keeps the Deep Flow switch in step with the phone while Settings is open.
let stopDeepFlow = null;

FT.register('settings', {
    leave() {
        if (stopDeepFlow) stopDeepFlow();
        stopDeepFlow = null;
    },
    async render(root, params, isCurrent) {
        let status = null;
        const values = await FT.api.settings().catch(() => null);
        if (!isCurrent()) return;

        const sections = [
            h('section', {}, h('h2', {}, FT.t('settings_theme')), h('p', { class: 'hint' }, FT.t('settings_theme_hint')),
                h('div', { style: 'margin-top:12px' }, modePills()), h('div', { class: 'swatches' }, FT.palettes.map(swatch))),
        ];
        if (values) {
            FT.deepFlow.adopt(values);
            status = h('span', { class: 'hint' }, FT.deepFlow.status());
            sections.push(h('section', { style: 'display:flex;flex-direction:column;gap:10px' }, h('h2', {}, 'Deep Flow'),
                toggle(FT.t('settings_deep_flow'), FT.t('settings_deep_flow_hint'), !!values.deepFlow,
                    (on) => FT.deepFlow.set(on).then(() => { status.textContent = FT.deepFlow.status(); }), 'deep-flow'),
                status));
            sections.push(h('section', { style: 'display:flex;flex-direction:column;gap:10px' }, h('h2', {}, FT.t('settings_filters')),
                toggle(FT.t('filter_hide_watched'), FT.t('filter_hide_watched_hint'), !!values.hideWatched, (on) => FT.api.settings({ hideWatched: on }), 'hide-watched'),
                toggle(FT.t('filter_hide_shorts'), FT.t('filter_hide_shorts_hint'), !!values.hideShorts, (on) => FT.api.settings({ hideShorts: on }), 'hide-shorts')));
        }
        sections.push(h('section', {}, h('h2', {}, FT.t('settings_remote')),
            h('p', { class: 'hint' }, ...withIcon(FT.t(FT.remote.active ? 'remote_hint_connected' : 'remote_hint_idle'), 'cast')),
            h('div', { class: 'btns', style: 'margin-top:12px' },
                FT.button(FT.t(FT.remote.active ? 'disconnect' : 'connect_remote'), 'smartphone', () => { FT.remote.toggle(); setTimeout(() => FT.reload(), 600); }, 'primary', 'remote'))));
        sections.push(h('section', {}, h('h2', {}, FT.t('settings_fullscreen')),
            h('p', { class: 'hint' }, FT.t('settings_fullscreen_hint'))));

        root.replaceChildren(h('div', { class: 'head' }, h('h1', {}, FT.t('tab_settings'))), h('div', { class: 'settings' }, sections));

        const sw = FT.$('[data-key="deep-flow"]', root);
        if (sw && status) {
            if (stopDeepFlow) stopDeepFlow();
            stopDeepFlow = FT.deepFlow.onChange(() => {
                sw.classList.toggle('on', FT.deepFlow.active);
                sw.setAttribute('aria-checked', String(FT.deepFlow.active));
                status.textContent = FT.deepFlow.status();
            });
        }
    },
});
})();

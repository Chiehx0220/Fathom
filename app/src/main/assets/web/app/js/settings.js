// Settings: theme (immediate), list filters, phone remote (saved on phone).
(() => {
const h = FT.h;

// Palette swatch drawn by stylesheet (same attributes as <html>).
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

// Text with {icon} placeholder → glyph.
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

const layoutPills = () => {
    const row = h('div', { class: 'seg' }, FT.layouts.map(([id, label]) => {
        const pill = FT.pill(label, null, FT.layout.mode() === id, () => {
            FT.layout.setMode(id);
            FT.$$('.pill', row).forEach((p) => p.classList.toggle('on', p === pill));
        }, 'layout-' + id);
        return pill;
    }));
    return row;
};

// `row.sync(value, since)`: apply a phone read.
FT.toggleRow = (title, hint, on, save, key) => {
    const guard = FT.editGuard();
    const sw = h('button', { type: 'button', class: 'switch', role: 'switch', data: { f: '', key } });
    const paint = () => { sw.classList.toggle('on', on); sw.setAttribute('aria-checked', String(on)); };
    sw.onclick = () => {
        on = !on;
        paint();
        guard.run(save(on).then(() => { FT.api.forget(); })).catch(() => { on = !on; paint(); FT.toast(FT.t('could_not_save')); });
    };
    paint();
    const row = h('div', { class: 'setting' }, h('div', {}, h('b', {}, title), h('span', { class: 'hint' }, hint)), sw);
    row.sync = (value, since) => { if (guard.stale(since)) return; on = !!value; paint(); };
    return row;
};

FT.register('settings', {
    async render(root, params, scope) {
        let status = null;
        const values = await FT.api.settings().catch(() => null);
        if (!scope.live()) return;

        const sections = [
            h('section', {}, h('h2', {}, FT.t('settings_theme')), h('p', { class: 'hint' }, FT.t('settings_theme_hint')),
                h('div', { style: 'margin-top:12px' }, modePills()), h('div', { class: 'swatches' }, FT.palettes.map(swatch))),
            h('section', {}, h('h2', {}, FT.t('settings_layout')), h('p', { class: 'hint' }, FT.t('settings_layout_hint')),
                h('div', { style: 'margin-top:12px' }, layoutPills())),
        ];
        if (values) {
            FT.deepFlow.adopt(values);
            status = h('span', { class: 'hint' }, FT.deepFlow.status());
            sections.push(h('section', { style: 'display:flex;flex-direction:column;gap:10px' }, h('h2', {}, 'Deep Flow'),
                FT.toggleRow(FT.t('settings_deep_flow'), FT.t('settings_deep_flow_hint'), !!values.deepFlow,
                    (on) => FT.deepFlow.set(on).then(() => { status.textContent = FT.deepFlow.status(); }), 'deep-flow'),
                status));
            const hideWatched = FT.toggleRow(FT.t('filter_hide_watched'), FT.t('filter_hide_watched_hint'), !!values.hideWatched, (on) => FT.api.settings({ hideWatched: on }), 'hide-watched');
            const hideShorts = FT.toggleRow(FT.t('filter_hide_shorts'), FT.t('filter_hide_shorts_hint'), !!values.hideShorts, (on) => FT.api.settings({ hideShorts: on }), 'hide-shorts');
            const filters = h('section', { style: 'display:flex;flex-direction:column;gap:10px' }, h('h2', {}, FT.t('settings_filters')), hideWatched, hideShorts);
            filters.refresh = async () => {
                const since = performance.now();
                const next = await FT.api.settings();
                hideWatched.sync(next.hideWatched, since);
                hideShorts.sync(next.hideShorts, since);
            };
            filters.pushed = true;
            sections.push(filters);
        }
        sections.push(...await FT.settingsExtra.sections());
        if (!scope.live()) return;
        sections.push(h('section', {}, h('h2', {}, FT.t('settings_remote')),
            h('p', { class: 'hint' }, ...withIcon(FT.t(FT.remote.active ? 'remote_hint_connected' : 'remote_hint_idle'), 'cast')),
            h('div', { class: 'btns', style: 'margin-top:12px' },
                FT.button(FT.t(FT.remote.active ? 'disconnect' : 'connect_remote'), 'smartphone', () => { FT.remote.toggle(); setTimeout(() => FT.reload(), 600); }, 'primary', 'remote'))));
        sections.push(h('section', {}, h('h2', {}, FT.t('settings_fullscreen')),
            h('p', { class: 'hint' }, FT.t('settings_fullscreen_hint'))));

        root.replaceChildren(h('div', { class: 'head' }, h('h1', {}, FT.t('tab_settings'))), h('div', { class: 'settings' }, sections));

        const live = sections.filter((section) => section.refresh);
        const refreshAll = (list) => () => Promise.all(list.map((section) => section.refresh().catch(() => {})));
        FT.followState(scope, refreshAll(live.filter((section) => section.pushed)), { topics: ['settings'], covered: true });
        FT.followState(scope, refreshAll(live.filter((section) => !section.pushed)));

        const sw = FT.$('[data-key="deep-flow"]', root);
        if (sw && status) {
            scope.subscribe(() => FT.deepFlow.onChange(() => {
                sw.classList.toggle('on', FT.deepFlow.active);
                sw.setAttribute('aria-checked', String(FT.deepFlow.active));
                status.textContent = FT.deepFlow.status();
            }));
        }
    },
});
})();

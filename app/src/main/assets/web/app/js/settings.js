// Settings: five panels behind a side nav; theme applies at once, the rest saves on the phone.
(() => {
const h = FT.h;

const PANEL_ICONS = { appearance: 'palette', content: 'filter_list', playback: 'play_circle', focus: 'forest', device: 'smartphone' };
let lastPanel = null;

// Swatch art mirrors <html> theme attributes.
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

// `{icon}` placeholder → glyph.
const withIcon = (text, icon) => {
    const [before, after] = text.split('{icon}');
    return after === undefined ? [text] : [before, FT.icon(icon), after];
};

const pillRow = (options, current, apply, key) => {
    const row = h('div', { class: 'seg' }, options.map(([id, label]) => {
        const pill = FT.pill(label, null, current() === id, () => {
            apply(id);
            FT.$$('.pill', row).forEach((p) => p.classList.toggle('on', p === pill));
        }, key + id);
        return pill;
    }));
    return row;
};

const stack = (...kids) => h('div', { class: 'setting stack' }, ...kids);

// Titled stack of rows (`el.card`); `note` (text or node) under it.
FT.settingsGroup = (title, note, ...rows) => {
    const card = h('div', { class: 'group' }, rows);
    const el = h('section', {}, h('h2', {}, title), card, note ? h('p', { class: 'note' }, note) : null);
    el.card = card;
    return el;
};

// `row.sync(value, since)`: apply a phone read; `row.onpaint(on)`: follow the switch.
FT.toggleRow = (title, hint, on, save, key) => {
    const guard = FT.editGuard();
    const sw = h('button', { type: 'button', class: 'switch', role: 'switch', data: { f: '', key } });
    const paint = () => { sw.classList.toggle('on', on); sw.setAttribute('aria-checked', String(on)); if (row.onpaint) row.onpaint(on); };
    sw.onclick = () => {
        on = !on;
        paint();
        guard.run(save(on).then(() => { FT.api.forget(); })).catch(() => { on = !on; paint(); FT.toast(FT.t('could_not_save')); });
    };
    const row = h('div', { class: 'setting' }, h('div', {}, h('b', {}, title), h('span', { class: 'hint' }, hint)), sw);
    row.sync = (value, since) => { if (guard.stale(since)) return; on = !!value; paint(); };
    paint();
    return row;
};

const appearanceSections = () => [
    FT.settingsGroup(FT.t('settings_theme'), FT.t('settings_theme_hint'),
        stack(pillRow(FT.modes, FT.theme.mode, (id) => { FT.theme.setMode(id); FT.$$('.swatch-art').forEach(syncArt); }, 'mode-theme-')),
        stack(h('div', { class: 'swatches' }, FT.palettes.map(swatch)))),
    FT.settingsGroup(FT.t('settings_layout'), FT.t('settings_layout_hint'), stack(pillRow(FT.layouts, FT.layout.mode, FT.layout.setMode, 'layout-'))),
];

const filtersSection = (values) => {
    const hideWatched = FT.toggleRow(FT.t('filter_hide_watched'), FT.t('filter_hide_watched_hint'), !!values.hideWatched, (on) => FT.api.settings({ hideWatched: on }), 'hide-watched');
    const hideShorts = FT.toggleRow(FT.t('filter_hide_shorts'), FT.t('filter_hide_shorts_hint'), !!values.hideShorts, (on) => FT.api.settings({ hideShorts: on }), 'hide-shorts');
    const el = FT.settingsGroup(FT.t('settings_filters'), null, hideWatched, hideShorts);
    el.refresh = async () => {
        const since = performance.now();
        const next = await FT.api.settings();
        hideWatched.sync(next.hideWatched, since);
        hideShorts.sync(next.hideShorts, since);
    };
    el.pushed = true;
    return el;
};

// `el.follow()`: mirror phone-side changes; returns stop.
const deepFlowSection = (values) => {
    FT.deepFlow.adopt(values);
    const status = h('span', {}, FT.deepFlow.status());
    const row = FT.toggleRow(FT.t('settings_deep_flow'), FT.t('settings_deep_flow_hint'), !!values.deepFlow,
        (on) => FT.deepFlow.set(on).then(() => { status.textContent = FT.deepFlow.status(); }), 'deep-flow');
    const sw = FT.$('.switch', row);
    const el = FT.settingsGroup('Deep Flow', status, row);
    el.follow = () => FT.deepFlow.onChange(() => {
        sw.classList.toggle('on', FT.deepFlow.active);
        sw.setAttribute('aria-checked', String(FT.deepFlow.active));
        status.textContent = FT.deepFlow.status();
    });
    return el;
};

const deviceSections = () => {
    const active = FT.remote.active;
    const action = FT.button(FT.t(active ? 'disconnect' : 'connect_remote'), 'smartphone', () => { FT.remote.toggle(); setTimeout(() => FT.reload(), 600); }, active ? '' : 'primary', 'remote');
    return [FT.settingsGroup(FT.t('settings_remote'), FT.t('settings_fullscreen_hint'),
        h('div', { class: 'setting' },
            h('div', {}, h('b', {}, FT.t(active ? 'sum_connected' : 'sum_not_connected')), h('span', { class: 'hint' }, ...withIcon(FT.t(active ? 'remote_hint_connected' : 'remote_hint_idle'), 'cast'))),
            action))];
};

// One tab per panel; returns { nav, select }.
const panelNav = (panels) => {
    const select = (id) => {
        lastPanel = id;
        for (const panel of panels) {
            const current = panel.id === id;
            panel.el.hidden = !current;
            panel.tab.classList.toggle('on', current);
            if (current) panel.tab.setAttribute('aria-current', 'page'); else panel.tab.removeAttribute('aria-current');
        }
    };
    const nav = h('nav', { class: 'set-nav' }, panels.map((panel) => {
        panel.sum = h('small', { class: 'set-sum' });
        panel.tab = h('button', { type: 'button', class: 'set-tab', data: { f: '', key: 'panel-' + panel.id }, onclick: () => select(panel.id) },
            FT.icon(PANEL_ICONS[panel.id]), h('span', { class: 'set-text' }, h('span', {}, FT.t('settings_group_' + panel.id)), panel.sum));
        return panel.tab;
    }));
    select(panels.some((panel) => panel.id === lastPanel) ? lastPanel : panels[0].id);
    return nav;
};

// Nav captions read row state from the DOM, so pushes, polls and local edits all show; stops with the scope.
const followCaptions = (scope, root, panels, { sponsorBlock, blocked }) => {
    const on = (key) => !!FT.$(`[data-key="${key}"]`, root)?.classList.contains('on');
    const captions = {
        appearance: () => [FT.modes.find(([id]) => id === FT.theme.mode())?.[1], FT.palettes.find((p) => p.id === FT.theme.palette())?.name],
        content: () => {
            const filtered = ['hide-watched', 'hide-shorts'].filter(on).length;
            const blockedCount = blocked ? FT.$$('[data-id]', blocked.card).length : 0;
            return [filtered ? FT.t('sum_filters', { count: filtered }) : null, blockedCount ? FT.t('sum_blocked', { count: blockedCount }) : null];
        },
        playback: () => [on('sponsorblock') ? FT.t('sum_sb_on', { count: FT.$$('.fold-value.on', sponsorBlock.card).length }) : FT.t('sum_sb_off')],
        focus: () => [on('deep-flow') ? FT.t('settings_deep_flow') : null, on('break') ? FT.t('settings_break') + ' ' + (FT.$('[data-key="break-every"] .fold-value', root)?.textContent || '') : null],
        device: () => [FT.t(FT.remote.active ? 'sum_connected' : 'sum_not_connected')],
    };
    const caption = () => { for (const panel of panels) panel.sum.textContent = captions[panel.id]().filter(Boolean).join(' · ') || FT.t('sum_none'); };
    caption();
    scope.subscribe(() => {
        let queued = false;
        const observer = new MutationObserver(() => { if (!queued) { queued = true; queueMicrotask(() => { queued = false; caption(); }); } });
        observer.observe(FT.$('.panels', root), { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] });
        return () => observer.disconnect();
    });
};

FT.register('settings', {
    async render(root, params, scope) {
        const values = await FT.api.settings().catch(() => null);
        if (!scope.live()) return;
        const extra = await FT.settingsExtra.sections();
        if (!scope.live()) return;

        const filters = values && filtersSection(values);
        const deep = values && deepFlowSection(values);
        const panels = [
            ['appearance', appearanceSections()],
            ['content', [filters, extra.blocked]],
            ['playback', [extra.sponsorBlock]],
            ['focus', [deep, extra.wellbeing]],
            ['device', deviceSections()],
        ].map(([id, sections]) => ({ id, sections: sections.filter(Boolean) }))
            .filter((panel) => panel.sections.length)
            .map((panel) => ({ ...panel, el: h('div', { class: 'panel' }, panel.sections) }));

        root.replaceChildren(h('div', { class: 'head' }, h('h1', {}, FT.t('tab_settings'))),
            h('div', { class: 'settings' }, panelNav(panels), h('div', { class: 'panels' }, panels.map((panel) => panel.el))));
        followCaptions(scope, root, panels, extra);

        const live = panels.flatMap((panel) => panel.sections).filter((section) => section.refresh);
        const refreshAll = (list) => () => Promise.all(list.map((section) => section.refresh().catch(() => {})));
        FT.followState(scope, refreshAll(live.filter((section) => section.pushed)), { topics: ['settings'], covered: true });
        FT.followState(scope, refreshAll(live.filter((section) => !section.pushed)));
        if (deep) scope.subscribe(deep.follow);
    },
});
})();

// Phone-backed settings sections: SponsorBlock, wellbeing, blocked channels; dropped on endpoint failure.
(() => {
const h = FT.h;

const section = (title, ...rows) => h('section', { style: 'display:flex;flex-direction:column;gap:10px' }, h('h2', {}, title), ...rows);

// Folded choice row; `choose(id)` saves, returns selected id; `row.sync(id, since)` applies a phone read.
const choice = (title, hint, options, current, choose, key) => {
    const guard = FT.editGuard();
    let selected = current;
    const pills = h('div', { class: 'pills' });
    const value = h('span', { class: 'fold-value' });
    const paint = () => {
        value.textContent = options.find(([id]) => id === selected)[1];
        pills.replaceChildren(...options.map(([id, label]) => FT.pill(label, null, id === selected, () => {
            guard.run(choose(id).then((next) => { selected = next; paint(); })).catch(() => FT.toast(FT.t('could_not_save')));
        }, `${key}-${id}`)));
    };
    const row = h('div', { class: 'setting fold' });
    const head = h('button', { type: 'button', class: 'fold-head', 'aria-expanded': 'false', data: { f: '', key }, onclick: () => head.setAttribute('aria-expanded', String(row.classList.toggle('open'))) },
        h('div', {}, h('b', {}, title), hint ? h('span', { class: 'hint' }, hint) : null), value, FT.icon('expand_more'));
    paint();
    row.append(head, pills);
    row.sync = (id, since) => { if (guard.stale(since) || id === selected) return; selected = id; paint(); };
    return row;
};

const categoryName = (id) => {
    if (id === 'interaction') return FT.t('segment_reminder');
    if (id === 'selfpromo') return FT.t('segment_self_promotion');
    if (id === 'music_offtopic') return FT.t('segment_non_music_part');
    if (id === 'sponsor') return FT.t('segment_sponsor');
    if (id === 'intro') return FT.t('segment_intro');
    return FT.t('segment_outro');
};

const actionLabel = (name) => {
    if (name === 'SKIP') return FT.t('sb_skip');
    if (name === 'SKIP_BUTTON') return FT.t('sb_button');
    if (name === 'IGNORE') return FT.t('sb_show');
    return FT.t('sb_off');
};

// `refresh()` re-reads in place; `pushed`: covered by the settings topic.
const sponsorBlock = async () => {
    const body = await FT.api.sponsorBlock();
    const master = FT.toggleRow(FT.t('settings_sponsorblock'), FT.t('settings_sponsorblock_hint'), body.enabled, (on) => FT.api.sponsorBlock({ enabled: on }), 'sponsorblock');
    const rows = new Map(body.categories.map((c) => [c.id, choice(categoryName(c.id), null, body.actions.map((a) => [a, actionLabel(a)]), c.action,
        (action) => FT.api.sponsorBlock({ category: c.id, action }).then(() => { FT.api.forget(); return action; }), 'sb-' + c.id)]));
    const el = section(FT.t('settings_sponsorblock'), master, ...rows.values());
    el.refresh = async () => {
        const since = performance.now();
        const next = await FT.api.sponsorBlock();
        master.sync(next.enabled, since);
        next.categories.forEach((c) => { if (rows.has(c.id)) rows.get(c.id).sync(c.action, since); });
    };
    el.pushed = true;
    return el;
};

const wellbeing = async () => {
    const body = await FT.api.wellbeing();
    const reminder = FT.toggleRow(FT.t('settings_break'), FT.t('settings_break_hint'), body.breakEnabled, (on) => FT.api.wellbeing({ breakEnabled: on }), 'break');
    const every = choice(FT.t('settings_break_every'), FT.t('settings_wellbeing_note'), body.breakOptions.map((m) => [m, FT.t('minutes_short', { count: m })]), body.breakMinutes,
        (minutes) => FT.api.wellbeing({ breakMinutes: minutes }).then((next) => next.breakMinutes), 'break-every');
    const el = section(FT.t('settings_wellbeing'), reminder, every);
    el.refresh = async () => {
        const since = performance.now();
        const next = await FT.api.wellbeing();
        reminder.sync(next.breakEnabled, since);
        every.sync(next.breakMinutes, since);
    };
    el.pushed = true;
    return el;
};

const nameOf = (channel) => channel.name || decodeURIComponent(channel.id.replace(/\/+$/, '').split('/').pop() || channel.id);

const blocked = async () => {
    const body = await FT.api.blockedChannels();
    const guard = FT.editGuard();
    const list = h('div', { style: 'display:flex;flex-direction:column;gap:10px' });
    const empty = h('p', { class: 'hint' }, FT.t('no_blocked'));
    const show = (channels) => {
        list.replaceChildren(...channels.map((channel) => {
            const row = h('div', { class: 'setting' }, h('div', {}, h('b', {}, nameOf(channel))),
                FT.button(FT.t('unblock'), 'person_remove', () => guard.run(FT.api.block(channel.id, false).then(() => { row.remove(); empty.hidden = list.children.length > 0; FT.toast(FT.t('unblocked')); })).catch(() => FT.toast(FT.t('could_not_save'))), '', 'unblock:' + channel.id));
            row.dataset.id = channel.id;
            return row;
        }));
        empty.hidden = channels.length > 0;
    };
    show(body.channels);
    const el = section(FT.t('settings_blocked'), h('p', { class: 'hint' }, FT.t('settings_blocked_hint')), list, empty);
    el.refresh = async () => {
        const since = performance.now();
        const next = await FT.api.blockedChannels();
        const same = next.channels.length === list.children.length && next.channels.every((c, i) => list.children[i].dataset.id === c.id);
        if (!guard.stale(since) && !same) show(next.channels);
    };
    return el;
};

FT.settingsExtra = {
    // Page order; failed sections omitted.
    async sections() {
        const built = await Promise.all([sponsorBlock(), wellbeing(), blocked()].map((p) => p.catch(() => null)));
        return built.filter(Boolean);
    },
};
})();

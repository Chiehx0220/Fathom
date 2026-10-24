// Cast menu: DLNA devices via phone; Continue on phone. Opened from skin cast button, watch page.
(() => {
const h = FT.h;
const POLL_MS = 2000;

const row = (icon, label, key, run, on) => h('button', {
    type: 'button', class: 'menu-item' + (on ? ' on' : ''), data: { f: '', key }, onclick: run,
}, FT.icon(icon, on ? 'fill' : ''), label);

const start = async (info, device) => {
    FT.menu.close();
    FT.toast(FT.t('cast_connecting', { name: device.name }));
    const now = FT.player.snapshot();
    try {
        await FT.api.castStart(device.usn, info.url, now.t, info.title);
        if (!now.paused) FT.player.toggle();
        FT.toast(FT.t('cast_started', { name: device.name }));
    } catch (e) { FT.toast(FT.t('cast_failed')); }
};

const toPhone = async (info) => {
    FT.menu.close();
    const now = FT.player.snapshot();
    try {
        await FT.api.handoff(info.url, now.t, now.d, info.title);
        if (!now.paused) FT.player.toggle();
        FT.toast(FT.t('handoff_sent'));
    } catch (e) { FT.toast(FT.t('action_failed')); }
};

FT.cast = {
    // info: video (url, title).
    open(info) {
        const body = h('div', { class: 'cast-list' });
        let shown = '';
        const paint = (state) => {
            const sig = JSON.stringify(state);
            if (sig === shown) return;
            shown = sig;
            const rows = state.devices.map((d) => row('cast', d.name, 'cast-' + d.usn, () => start(info, d), state.casting === d.usn));
            if (!rows.length) rows.push(h('div', { class: 'menu-title' }, FT.t(state.discovering ? 'cast_searching' : 'cast_none')));
            if (state.casting) rows.push(row('close', FT.t('cast_stop'), 'cast-stop', () => { FT.menu.close(); FT.api.castStop().catch(() => {}); }));
            if (!FT.onPhone()) rows.push(row('play_circle', FT.t('continue_on_phone'), 'cast-phone', () => toPhone(info)));
            // Keep focus on the current device row, else the first row.
            const kept = FT.focus.el && body.contains(FT.focus.el) ? FT.focus.el.dataset.key : '';
            body.replaceChildren(...rows);
            const same = kept.startsWith('cast-') && !['cast-phone', 'cast-stop'].includes(kept) ? FT.$('[data-key="' + CSS.escape(kept) + '"]', body) : null;
            FT.focus.select(same || FT.$('[data-f]', body), { scroll: false });
        };
        const poll = async (scan) => {
            try { paint(await FT.api.castDevices(scan)); } catch (e) { paint({ devices: [], discovering: false, casting: null }); }
            if (body.isConnected) setTimeout(() => poll(false), POLL_MS);
        };
        paint({ devices: [], discovering: true, casting: null });
        FT.menu.panel(FT.t('cast_to'), body);
        poll(true);
    },
};
})();

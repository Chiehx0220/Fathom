// The options menu: a small list of what can be done with the selected card. Opened by the Menu key, `m`, a long press on the
// phone's wheel, or the card's own button. While it is open it holds the focus.
(() => {
const h = FT.h;
let opener = null;

const root = () => FT.$('#menu');

const place = (panel, anchor) => {
    const r = anchor ? anchor.getBoundingClientRect() : { left: innerWidth / 2 - 135, right: innerWidth / 2 + 135, top: innerHeight / 3, bottom: innerHeight / 3 };
    const w = 270;
    const height = panel.offsetHeight || 240;
    let left = r.right + 14;
    if (left + w > innerWidth - 12) left = Math.max(12, r.left - w - 14);
    if (left + w > innerWidth - 12) left = Math.max(12, Math.min(r.left, innerWidth - w - 12));
    const top = Math.max(12, Math.min(r.top, innerHeight - height - 12));
    panel.style.left = left + 'px';
    panel.style.top = top + 'px';
};

FT.menu = {
    isOpen: () => !root().hidden,

    // items: [{ icon, label, run, danger }]
    open(items, options = {}) {
        if (!items.length) return;
        opener = FT.focus.el;
        const menu = root();
        const list = items.map((item, i) => h('button', {
            type: 'button', class: 'menu-item' + (item.danger ? ' danger' : ''), data: { f: '', key: 'menu-' + i },
            onclick: () => { FT.menu.close(); item.run(); },
        }, FT.icon(item.icon), item.label));
        const panel = h('div', { class: 'menu-panel', role: 'menu' }, options.title ? h('div', { class: 'menu-title' }, options.title) : null, list);
        menu.replaceChildren(h('div', { class: 'menu-scrim', onclick: () => FT.menu.close() }), panel);
        menu.hidden = false;
        place(panel, options.anchor);
        FT.focus.trap(panel);
        FT.focus.select(FT.$('[data-f]', panel), { scroll: false });
    },

    close() {
        if (!FT.menu.isOpen()) return;
        root().hidden = true;
        root().replaceChildren();
        FT.focus.trap(null);
        if (opener && opener.isConnected) FT.focus.select(opener, { scroll: false });
        opener = null;
    },

    // The menu of whatever is selected, if it has one.
    forSelected() {
        if (FT.menu.isOpen()) { FT.menu.close(); return; }
        const el = FT.focus.el;
        if (!el || !el.item) return;
        const items = FT.menu.itemsFor(el);
        FT.menu.open(items, { title: el.item.title || el.item.name, anchor: el });
    },

    itemsFor(el) {
        const it = el.item;
        const items = [{ icon: 'open_in_new', label: 'Open', run: () => el.click() }];
        if (el.kind === 'video') {
            const later = !!el.inWatchLater;
            items.push({
                icon: later ? 'bookmark_remove' : 'bookmark_add', label: later ? 'Remove from Watch later' : 'Save to Watch later',
                run: () => FT.api.watchLater(it, !later).then(() => {
                    el.inWatchLater = !later;
                    FT.toast(later ? 'Removed from Watch later' : 'Saved to Watch later');
                    if (later && el.onRemoved) el.onRemoved();
                }).catch(() => FT.toast('Could not save that')),
            });
            if (it.channelId) items.push({ icon: 'account_circle', label: 'Go to channel', run: () => { location.hash = FT.link.channel(it.channelId, it.serviceId); } });
            if (el.onRemoved && el.fromHistory) {
                items.push({
                    icon: 'delete', label: 'Remove from history', danger: true,
                    run: () => FT.api.removeFromHistory(it).then(() => { FT.toast('Removed from history'); el.onRemoved(); }).catch(() => FT.toast('Could not remove that')),
                });
            }
        } else if (el.kind === 'channel') {
            items.push({
                icon: 'person_remove', label: 'Unsubscribe', danger: true,
                run: () => FT.api.subscribe(it, false).then(() => { FT.toast('Unsubscribed'); if (el.onRemoved) el.onRemoved(); }).catch(() => FT.toast('Could not unsubscribe')),
            });
        } else if (el.kind === 'playlist') {
            items.push({
                icon: 'star_border', label: 'Remove bookmark', danger: true,
                run: () => FT.api.bookmarkPlaylist(it, false).then(() => { FT.toast('Bookmark removed'); if (el.onRemoved) el.onRemoved(); }).catch(() => FT.toast('Could not remove that')),
            });
        }
        return items;
    },
};
})();

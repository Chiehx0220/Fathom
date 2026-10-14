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

// Shows `panel` over a scrim and traps focus in it; `position` runs once the panel is in the document.
const present = (panel, focus, position) => {
    opener = FT.focus.el;
    const menu = root();
    menu.replaceChildren(h('div', { class: 'menu-scrim', onclick: () => FT.menu.close() }), panel);
    menu.hidden = false;
    if (position) position();
    FT.focus.trap(panel);
    FT.focus.select(focus, { scroll: false });
};

FT.menu = {
    isOpen: () => !root().hidden,

    // items: [{ icon, label, run, danger }]
    open(items, options = {}) {
        if (!items.length) return;
        const list = items.map((item, i) => h('button', {
            type: 'button', class: 'menu-item' + (item.danger ? ' danger' : ''), data: { f: '', key: 'menu-' + i },
            onclick: () => { FT.menu.close(); item.run(); },
        }, FT.icon(item.icon), item.label));
        const panel = h('div', { class: 'menu-panel', role: 'menu' }, options.title ? h('div', { class: 'menu-title' }, options.title) : null, list);
        present(panel, FT.$('[data-f]', panel), () => place(panel, options.anchor));
    },

    // A dialog with the caller's content, closed like a menu (scrim, Back, the button).
    panel(title, body) {
        const close = h('button', { type: 'button', class: 'menu-item', data: { f: '', key: 'panel-close' }, onclick: () => FT.menu.close() }, FT.icon('close'), FT.t('close'));
        present(h('div', { class: 'menu-panel wide', role: 'dialog', 'aria-label': title }, h('div', { class: 'menu-title' }, title), body, close), close);
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
        const items = [{ icon: 'open_in_new', label: FT.t('open'), run: () => el.click() }];
        if (el.kind === 'video') {
            const later = !!el.inWatchLater;
            items.push({
                icon: later ? 'bookmark_remove' : 'bookmark_add', label: FT.t(later ? 'remove_from_later' : 'save_to_later'),
                run: () => FT.api.watchLater(it, !later).then(() => {
                    el.inWatchLater = !later;
                    FT.toast(FT.t(later ? 'removed_from_later' : 'saved_to_later'));
                    if (later && el.onRemoved) el.onRemoved();
                }).catch(() => FT.toast(FT.t('could_not_save'))),
            });
            if (it.channelId) items.push({ icon: 'account_circle', label: FT.t('go_to_channel'), run: () => { location.hash = FT.link.channel(it.channelId, it.serviceId); } });
            if (el.onRemoved && el.fromHistory) {
                items.push({
                    icon: 'delete', label: FT.t('remove_from_history'), danger: true,
                    run: () => FT.api.removeFromHistory(it).then(() => { FT.toast(FT.t('removed_from_history')); el.onRemoved(); }).catch(() => FT.toast(FT.t('could_not_remove'))),
                });
            }
        } else if (el.kind === 'channel') {
            items.push({
                icon: 'person_remove', label: FT.t('unsubscribe'), danger: true,
                run: () => FT.api.subscribe(it, false).then(() => { FT.toast(FT.t('unsubscribed')); if (el.onRemoved) el.onRemoved(); }).catch(() => FT.toast(FT.t('could_not_unsubscribe'))),
            });
        } else if (el.kind === 'playlist') {
            items.push({
                icon: 'star_border', label: FT.t('remove_bookmark'), danger: true,
                run: () => FT.api.bookmarkPlaylist(it, false).then(() => { FT.toast(FT.t('bookmark_removed')); if (el.onRemoved) el.onRemoved(); }).catch(() => FT.toast(FT.t('could_not_remove'))),
            });
        }
        return items;
    },
};
})();

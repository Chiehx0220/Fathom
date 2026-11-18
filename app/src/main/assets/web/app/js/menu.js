// Options menu of the selected card: Menu key, `m`, phone wheel long press, card button; holds focus while open.
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

// Panel over scrim, focus trapped; `position` runs after insert.
const present = (panel, focus, position) => {
    opener = FT.focus.el;
    const menu = root();
    menu.replaceChildren(h('div', { class: 'menu-scrim', onclick: () => FT.menu.close() }), panel);
    menu.hidden = false;
    if (position) position();
    FT.focus.trap(panel);
    FT.focus.select(focus, { scroll: false });
};

// Watched, not interested (feeds only), block channel: the phone's video menu.
const feedbackItems = (el, drop) => {
    const it = el.item;
    const watched = (it.progress || 0) >= 100;
    const items = [{
        icon: watched ? 'undo' : 'check', label: FT.t(watched ? 'mark_unwatched' : 'mark_watched'),
        run: () => FT.api.feedback(it, watched ? 'unwatched' : 'watched').then(() => {
            it.progress = watched ? 0 : 100;
            const bar = FT.$('.prog', el);
            if (bar) bar.remove();
            FT.toast(FT.t(watched ? 'marked_unwatched' : 'marked_watched'));
        }).catch(() => FT.toast(FT.t('could_not_save'))),
    }];
    if (!el.fromHistory && !el.fromLiked && !el.fromPlaylist) {
        items.push({
            icon: 'not_interested', label: FT.t('not_interested'),
            run: () => FT.api.feedback(it, 'not_interested').then(() => { FT.toast(FT.t('not_interested_done')); drop(el); }).catch(() => FT.toast(FT.t('could_not_save'))),
        });
    }
    if (it.channelId) {
        items.push({
            icon: 'block', label: FT.t('block_channel'), danger: true,
            run: () => FT.setBlocked({ id: it.channelId, name: it.channelName || '' }, true).then(() => {
                FT.$$('.card, .row-item').filter((c) => c !== el && c.kind === 'video' && c.item.channelId === it.channelId).forEach((c) => c.remove());
                drop(el);
            }).catch(() => FT.toast(FT.t('could_not_save'))),
        });
    }
    return items;
};

FT.menu = {
    isOpen: () => !root().hidden,

    // items: [{ icon, label, run, danger }]
    open(items, options = {}) {
        if (!items.length) return;
        const list = items.map((item, i) => h('button', {
            type: 'button', class: 'menu-item' + (item.danger ? ' danger' : '') + (item.on ? ' on' : ''), data: { f: '', key: 'menu-' + i },
            onclick: () => { FT.menu.close(); item.run(); },
        }, item.lead || FT.icon(item.icon), item.label));
        const panel = h('div', { class: 'menu-panel', role: 'menu' }, options.title ? h('div', { class: 'menu-title' }, options.title) : null, list);
        present(panel, FT.$('[data-f]', panel), () => place(panel, options.anchor));
    },

    // Dialog with caller content; closes like menu (scrim, Back, button).
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

    // Menu of selection, if any.
    forSelected() {
        if (FT.menu.isOpen()) { FT.menu.close(); return; }
        const el = FT.focus.el;
        if (!el || !el.item) return;
        const items = FT.menu.itemsFor(el);
        FT.menu.open(items, { title: el.item.title || el.item.name, anchor: el });
    },

    itemsFor(el) {
        const drop = (card) => { if (card.onRemoved) card.onRemoved(); else { card.remove(); FT.focus.first(); } };
        const removal = (icon, label, done, request) => ({
            icon, label, danger: true,
            run: () => request().then(() => { FT.toast(done); el.onRemoved(); }).catch(() => FT.toast(FT.t('could_not_remove'))),
        });
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
            items.push({ icon: 'playlist_play', label: FT.t('save_to_playlist'), run: () => FT.playlistPicker(it) });
            if (el.fromLiked) items.push(removal('thumb_down_off', FT.t('remove_like'), FT.t('removed_like'), () => FT.api.rate(it, 'remove')));
            if (el.fromPlaylist) items.push(removal('delete', FT.t('remove_from_playlist'), FT.t('removed_from_playlist'), () => FT.api.playlistSet(el.fromPlaylist, it, false)));
            if (it.channelId) items.push({ icon: 'account_circle', label: FT.t('go_to_channel'), run: () => { location.hash = FT.link.channel(it.channelId, it.serviceId); } });
            items.push(...feedbackItems(el, drop));
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
        } else if (el.kind === 'playlist' && !it.isLocal) {
            items.push({
                icon: 'star_border', label: FT.t('remove_bookmark'), danger: true,
                run: () => FT.api.bookmarkPlaylist(it, false).then(() => { FT.toast(FT.t('bookmark_removed')); if (el.onRemoved) el.onRemoved(); }).catch(() => FT.toast(FT.t('could_not_remove'))),
            });
        }
        return items;
    },
};
})();

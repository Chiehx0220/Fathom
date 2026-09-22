// The pieces screens are built from: cards, list rows, shelves, buttons. Each returns an element; the item it shows
// is kept on the element (`el.item`) so the options menu and the detail pane can find it again.
(() => {
const h = FT.h;

// Where opening a video, channel or playlist goes. Links are real anchors, so the mouse gets them natively.
FT.link = {
    watch: (video) => `#/watch?s=${video.serviceId ?? FT.serviceOfUrl(video.url)}&u=${FT.enc(video.url)}`,
    channel: (url, service) => `#/channel?s=${service ?? FT.serviceOfUrl(url)}&u=${FT.enc(url)}`,
    playlist: (item) => `#/playlist?s=${item.serviceId ?? FT.store.service}&u=${FT.enc(item.id)}`,
};

const meta = (v) => [v.channelName, FT.views(v.viewCount), v.uploadDate].filter(Boolean).join(' · ');
FT.meta = meta;

const thumb = (v, badge) => {
    const box = h('div', { class: 'thumb' }, v.thumbnailUrl ? h('img', { src: v.thumbnailUrl, alt: '', loading: 'lazy' }) : null);
    if (v.isLive) box.append(h('span', { class: 'dur live' }, 'LIVE'));
    else if (v.duration > 0) box.append(h('span', { class: 'dur' }, FT.duration(v.duration)));
    if (v.progress > 0 && v.progress < 100) box.append(h('div', { class: 'prog' }, h('i', { style: `width:${v.progress}%` })));
    if (badge !== false) box.append(h('span', { class: 'opt' }, FT.icon('menu'), 'Options'));
    return box;
};

// A video as a card, for shelves and grids.
FT.videoCard = (v, key) => {
    const el = h('a', { class: 'card', href: FT.link.watch(v), tabindex: '-1', data: { f: '', key: key || v.url } },
        thumb(v), h('div', { class: 'card-title' }, v.title), h('div', { class: 'card-meta' }, meta(v)));
    el.item = v;
    el.kind = 'video';
    return el;
};

// A video as a list row; `onSelect` is called with the item when the row becomes the selected one.
FT.videoRow = (v, key) => {
    const el = h('a', { class: 'row-item', href: FT.link.watch(v), tabindex: '-1', data: { f: '', key: key || v.url } },
        thumb(v, false), h('div', { class: 'row-text' }, h('div', { class: 'card-title' }, v.title), h('div', { class: 'card-meta' }, meta(v))));
    el.item = v;
    el.kind = 'video';
    return el;
};

FT.channelCard = (c) => {
    const box = h('div', { class: 'thumb' }, c.thumbnailUrl ? h('img', { src: c.thumbnailUrl, alt: '', loading: 'lazy' }) : null);
    const el = h('a', { class: 'card channel', href: FT.link.channel(c.id, c.serviceId), tabindex: '-1', data: { f: '', key: c.id } },
        box, h('div', { class: 'card-title' }, c.name), h('div', { class: 'card-meta' }, c.subscriberCount >= 0 ? FT.count(c.subscriberCount) + ' subscribers' : ''));
    el.item = c;
    el.kind = 'channel';
    return el;
};

FT.playlistCard = (p) => {
    const box = h('div', { class: 'thumb' }, p.thumbnailUrl ? h('img', { src: p.thumbnailUrl, alt: '', loading: 'lazy' }) : null, p.videoCount ? h('span', { class: 'dur' }, p.videoCount + ' videos') : null);
    const el = h('a', { class: 'card', href: FT.link.playlist(p), tabindex: '-1', data: { f: '', key: p.id } }, box, h('div', { class: 'card-title' }, p.name));
    el.item = p;
    el.kind = 'playlist';
    return el;
};

// One card for whatever an API list held.
FT.itemCard = (item) => (item.subscriberCount !== undefined && item.name ? FT.channelCard(item) : item.videoCount !== undefined && item.name ? FT.playlistCard(item) : FT.videoCard(item));

FT.shelf = (title, sub, cards, extra = '') => h('section', { class: 'shelf' },
    h('div', { class: 'shelf-head' }, h('h2', {}, title), sub ? h('span', {}, sub) : null),
    h('div', { class: ('row ' + extra).trim() }, cards));

// A button. `onclick` is the action; `variant` is 'primary', 'round' or ''. Stops are found by the focus engine through data-f.
FT.button = (label, icon, onclick, variant = '', key) => {
    const el = h('button', { type: 'button', class: ('btn ' + variant).trim(), data: { f: '', key: key || label }, onclick, 'aria-label': label },
        icon ? FT.icon(icon) : null, variant.includes('round') ? null : label);
    return el;
};

FT.pill = (label, icon, on, onclick, key) => h('button', { type: 'button', class: 'pill' + (on ? ' on' : ''), data: { f: '', key: key || label }, onclick },
    icon ? FT.icon(icon) : null, label);

FT.loading = (text) => h('div', { class: 'loading' }, h('div', { class: 'spinner' }), text ? h('div', { class: 'card-meta', style: 'margin-top:14px' }, text) : null);

FT.empty = (icon, title, text) => h('div', { class: 'empty' }, FT.icon(icon), h('h2', {}, title), text ? h('p', {}, text) : null);

FT.failure = (message, retry) => h('div', { class: 'empty' }, FT.icon('cloud_off'), h('h2', {}, 'Could not load this'), h('p', {}, message || ''),
    retry ? FT.button('Try again', 'refresh', retry, 'primary') : null);
})();

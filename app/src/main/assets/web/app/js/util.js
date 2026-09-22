// Small helpers used everywhere. Everything the app shares hangs off one object, FT.
const FT = (window.FT = window.FT || {});

FT.$ = (selector, root = document) => root.querySelector(selector);
FT.$$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

// Builds an element. Text is always added as text (never parsed), so titles from the network cannot inject markup.
//   FT.h('a', { class: 'card', href: '#/x', onclick: fn, data: { key: 'k' } }, child, 'text', [more])
FT.h = (tag, props, ...kids) => {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(props || {})) {
        if (value === undefined || value === null || value === false) continue;
        if (key === 'class') el.className = value;
        else if (key === 'data') Object.assign(el.dataset, value);
        else if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
        else el.setAttribute(key, value === true ? '' : value);
    }
    for (const kid of kids.flat(Infinity)) {
        if (kid === undefined || kid === null || kid === false) continue;
        el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return el;
};

// A Material icon by name; the glyph comes from the bundled font.
FT.icon = (name, extra = '') => FT.h('span', { class: ('i ' + extra).trim(), 'aria-hidden': 'true' }, name);

FT.enc = encodeURIComponent;

FT.duration = (seconds) => {
    const s = Math.max(0, Math.round(seconds || 0));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = String(s % 60).padStart(2, '0');
    return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
};

FT.count = (n) => {
    if (n === undefined || n === null || n < 0) return '';
    if (n >= 1e9) return (n / 1e9).toFixed(1).replace(/\.0$/, '') + 'B';
    if (n >= 1e6) return (n / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
    if (n >= 1e3) return Math.round(n / 1e3) + 'K';
    return String(n);
};

// A count of zero is what the phone reports when the source gave no number, so it is left out rather than shown as "0 views".
FT.views = (n) => (n > 0 ? FT.count(n) + ' views' : '');

// The service a video or channel address belongs to (the list of services comes from the server).
FT.serviceOfUrl = (url) => (/bilibili\.com|^BV/i.test(url || '') ? 5 : 0);

let toastTimer = 0;
FT.toast = (message) => {
    const el = FT.$('#toast');
    if (!el) return;
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 2200);
};

// Runs fn at most once per animation frame.
FT.frame = (fn) => {
    let queued = false;
    return (...args) => {
        if (queued) return;
        queued = true;
        requestAnimationFrame(() => { queued = false; fn(...args); });
    };
};

// A video description. The extractor gives YouTube's as HTML (links, line breaks) but Bilibili's
// as plain text (see ApiRenderer.kt's descriptionType) - only the HTML one is set as markup, so a
// plain description containing '<' is never mistaken for a tag. External links open in a new tab
// so following one doesn't navigate this remote-controlled app away from itself.
FT.description = (text, type) => {
    const box = FT.h('div');
    if (type === 'html' && text) {
        box.innerHTML = text;
        FT.$$('a', box).forEach((a) => { a.target = '_blank'; a.rel = 'noopener noreferrer'; });
    } else {
        box.textContent = text || 'No description.';
    }
    return box;
};

// An avatar: the picture over a coloured initial, so a missing or broken picture still looks intentional.
FT.avatar = (name, src, extra = '') => {
    const img = src ? FT.h('img', { src, alt: '', loading: 'lazy' }) : null;
    const box = FT.h('span', { class: ('avatar ' + extra).trim() }, (name || '?').trim().charAt(0).toUpperCase(), img);
    if (img) img.addEventListener('error', () => box.classList.add('failed'));
    return box;
};

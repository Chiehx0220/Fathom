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

// The Fathom mark: the page's own icon (the <link rel="icon"> in index.html), so the header and the browser tab always match.
FT.brandMark = () => FT.h('img', { class: 'brand-mark', src: document.querySelector('link[rel="icon"]').href, alt: '', width: 32, height: 32 });

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

// Copies text to the clipboard, true on success. navigator.clipboard requires a secure context
// (this app is plain HTTP) and a trusted user gesture; execCommand needs only the latter, so it
// stays as a real fallback rather than dead code.
FT.copyText = async (text) => {
    try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(text);
            return true;
        }
    } catch (e) { /* fall through */ }
    try {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.setAttribute('readonly', '');
        ta.style.cssText = 'position:fixed;top:-9999px;left:-9999px';
        document.body.append(ta);
        ta.select();
        ta.setSelectionRange(0, text.length);
        const ok = document.execCommand('copy');
        ta.remove();
        if (ok) return true;
    } catch (e) { /* fall through */ }
    return false;
};

// Description/comment text: HTML (YouTube) vs plain text (Bilibili) per ApiRenderer.kt's type
// field, so a plain-text '<' is never rendered as markup. Links open in a new tab.
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
    // A comment author's name is often a "@handle" - the initial is the first letter of the handle, not "@".
    const initial = (name || '?').trim().replace(/^@/, '').charAt(0).toUpperCase() || '?';
    const box = FT.h('span', { class: ('avatar ' + extra).trim() }, initial, img);
    if (img) img.addEventListener('error', () => box.classList.add('failed'));
    return box;
};

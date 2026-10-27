// Shared helpers; everything hangs off FT.
const FT = (window.FT = window.FT || {});

FT.$ = (selector, root = document) => root.querySelector(selector);
FT.$$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

// Window event subscription; returns the unsubscribe function.
FT.on = (type, handler) => {
    addEventListener(type, handler);
    return () => removeEventListener(type, handler);
};

// Element builder; text always as text (no markup injection from network titles).
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

// Material icon by name (bundled font).
FT.icon = (name, extra = '') => FT.h('span', { class: ('i ' + extra).trim(), 'aria-hidden': 'true' }, name);

// Fathom mark from <link rel=icon>: header = tab.
FT.brandMark = () => FT.h('img', { class: 'brand-mark', src: document.querySelector('link[rel="icon"]').href, alt: '', width: 32, height: 32 });

FT.enc = encodeURIComponent;

// Card picture: YouTube hq720 → hqdefault (480x360, cover-cropped, ~90% fewer bytes); other hosts unchanged.
FT.cardThumb = (url) => (url || '').replace(/^(https:\/\/i\.ytimg\.com\/vi\/[\w-]+\/)hq720\.jpg$/, '$1hqdefault.jpg');

FT.duration = (seconds) => {
    const s = Math.max(0, Math.round(seconds || 0));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = String(s % 60).padStart(2, '0');
    return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
};

// ---- i18n ----
// window.FT_STRINGS: { <language code>: { key: text } } from strings/*.json; `en` is the fallback.
const STRINGS = window.FT_STRINGS || {};

// BCP 47 tag -> file code: zh-TW -> zh_Hant, zh-CN -> zh_Hans, pt-BR -> pt_BR, pt.
const codesFor = (tag) => {
    const parts = String(tag).split('-');
    const base = parts[0].toLowerCase();
    if (base === 'zh') return [/hant|tw|hk|mo/i.test(tag) ? 'zh_Hant' : 'zh_Hans'];
    return [parts.join('_'), base];
};
const pickLang = () => {
    const tags = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || 'en'];
    for (const tag of tags) {
        const found = codesFor(tag).find((code) => STRINGS[code]);
        if (found) return found;
    }
    return 'en';
};
FT.lang = pickLang();
document.documentElement.lang = FT.lang.replace('_', '-');
const WORDS = STRINGS[FT.lang] || {};
const BASE = STRINGS.en || {};
const BCP47 = FT.lang.replace('_', '-');
const fill = (text, values) => (values ? text.replace(/\{(\w+)\}/g, (all, name) => (name in values ? values[name] : all)) : text);

FT.hasT = (key) => key in WORDS || key in BASE;
// Text for key with {name} filled; missing → fallback, else key.
FT.t = (key, values, fallback) => fill(WORDS[key] ?? BASE[key] ?? fallback ?? key, values);
// Plural by CLDR category: key_one, key_other, ...; {count} filled.
const plurals = new Intl.PluralRules(BCP47);
FT.tn = (key, n, values) => {
    const form = `${key}_${plurals.select(n)}`;
    return FT.t(FT.hasT(form) ? form : `${key}_other`, { count: n, ...values });
};

const compact = new Intl.NumberFormat(BCP47, { notation: 'compact', maximumFractionDigits: 1 });
FT.count = (n) => (n === undefined || n === null || n < 0 ? '' : compact.format(n));

// 0 means the source gave no count.
FT.views = (n) => (n > 0 ? FT.t('count_views', { count: FT.count(n) }) : '');

// Service of a video/channel address (list from server).
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

// Clipboard copy, true on success; navigator.clipboard needs a secure context (plain HTTP here), execCommand fallback.
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

// Description/comment text: HTML (YouTube) or plain (Bilibili) per ApiRenderer type field; plain '<' never markup; links open in a new tab.
FT.description = (text, type) => {
    const box = FT.h('div');
    if (type === 'html' && text) {
        box.innerHTML = text;
        FT.$$('a', box).forEach((a) => { a.target = '_blank'; a.rel = 'noopener noreferrer'; });
    } else {
        box.textContent = text || FT.t('no_description');
    }
    return box;
};

// Avatar: picture over coloured initial (missing picture still looks intentional).
FT.avatar = (name, src, extra = '') => {
    const img = src ? FT.h('img', { src, alt: '', loading: 'lazy' }) : null;
    // '@handle' authors: initial = first letter after @.
    const initial = (name || '?').trim().replace(/^@/, '').charAt(0).toUpperCase() || '?';
    const box = FT.h('span', { class: ('avatar ' + extra).trim() }, initial, img);
    if (img) img.addEventListener('error', () => box.classList.add('failed'));
    return box;
};

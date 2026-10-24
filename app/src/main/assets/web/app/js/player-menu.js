// Rows added to the skin's settings menu (open shadow root), cloned from a native row for styling.
(() => {
const P = FT.playerCore;
const h = FT.h;
const DANMAKU_ROW = 'danmaku-row';
const SHORTCUTS_ROW = 'shortcuts-row';
const DANMAKU_HINT_KEY = 'fathom-hint-danmaku';
const CHAT_ICON = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z"/></svg>';
const KEYBOARD_ICON = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M20 5H4c-1.1 0-1.99.9-1.99 2L2 17c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm-9 3h2v2h-2V8zm0 3h2v2h-2v-2zM8 8h2v2H8V8zm0 3h2v2H8v-2zm-1 2H5v-2h2v2zm0-3H5V8h2v2zm9 7H8v-2h8v2zm0-4h-2v-2h2v2zm0-3h-2V8h2v2zm3 3h-2v-2h2v2zm0-3h-2V8h2v2z"/></svg>';
let observer = null;

// `valued`: a switch row keeping the native value slot (On/Off); otherwise a plain action row.
const buildRow = (content, { id, svg, label, valued, onSelect }) => {
    const model = content.querySelector(':scope > media-menu-item');
    if (!model) return null;
    const row = model.cloneNode(true);
    ['commandfor', 'aria-haspopup', 'aria-expanded', 'data-has-submenu', 'data-highlighted', 'data-availability', 'disabled', 'aria-disabled', 'hidden']
        .forEach((name) => row.removeAttribute(name));
    row.id = id;
    const text = row.querySelector('media-text');
    text.removeAttribute('token');
    text.textContent = label;
    const chevron = row.querySelector('media-icon[name="chevron"]');
    if (chevron) chevron.remove();
    if (!valued) {
        const hint = row.querySelector('.media-menu-hint');
        if (hint) hint.remove();
    }
    // <media-icon> resolves icons by name and has none of these: a same-class element carries the SVG.
    const icon = row.querySelector('media-icon');
    if (icon) {
        const glyph = h('span', { class: icon.className });
        glyph.innerHTML = svg;
        icon.replaceWith(glyph);
    }
    row.addEventListener('select', onSelect);
    return row;
};

const paint = () => {
    const root = P.tree && P.tree.skin.shadowRoot;
    const content = root && root.querySelector('media-menu-content');
    if (!content) return;

    let shortcuts = root.getElementById(SHORTCUTS_ROW);
    if (!shortcuts) {
        shortcuts = buildRow(content, { id: SHORTCUTS_ROW, svg: KEYBOARD_ICON, label: FT.t('shortcuts_title'), onSelect: () => FT.shortcuts.open() });
        if (shortcuts) content.append(shortcuts);
    }

    const danmaku = P.danmaku.current();
    let row = root.getElementById(DANMAKU_ROW);
    if (!danmaku) { if (row) row.remove(); return; }
    if (!row) {
        // Cancelling `select` keeps the menu open, so the switch flips in place.
        row = buildRow(content, {
            id: DANMAKU_ROW, svg: CHAT_ICON, label: FT.t('danmaku'), valued: true,
            onSelect: (e) => { e.preventDefault(); P.danmaku.toggle(); },
        });
        if (!row) return;
        content.insertBefore(row, shortcuts || null);
    }
    row.setAttribute('aria-checked', String(danmaku.on));
    const value = row.querySelector('[data-part="value"]');
    const text = FT.t(danmaku.on ? 'state_on' : 'state_off');
    if (value && value.textContent !== text) value.textContent = text;
};

FT.playerCore.menu = {
    paint,
    // The skin renders the menu lazily and may re-render it: rows are re-asserted on every mutation.
    watch() {
        if (observer) observer.disconnect();
        observer = null;
        const root = P.tree && P.tree.skin.shadowRoot;
        if (!root) return;
        observer = new MutationObserver(paint);
        observer.observe(root, { childList: true, subtree: true });
        paint();
    },
    stop() {
        if (observer) observer.disconnect();
        observer = null;
    },
    // One-time callout (localStorage) pointing at the menu.
    hintDanmaku() {
        let seen = true;
        try { seen = !!localStorage.getItem(DANMAKU_HINT_KEY); } catch (e) {}
        if (seen || !P.tree) return;
        try { localStorage.setItem(DANMAKU_HINT_KEY, '1'); } catch (e) {}
        const tip = h('div', { class: 'dm-hint', role: 'status' }, FT.icon('chat'), FT.t('danmaku_hint'));
        tip.addEventListener('click', () => tip.remove());
        P.tree.skin.append(tip);
        setTimeout(() => tip.remove(), 8000);
    },
};
})();

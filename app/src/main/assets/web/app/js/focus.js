// The focus engine: which control the keys (or the phone remote) are on, and where an arrow goes next.
// Screens only mark their controls with `data-f`; they never handle arrow keys themselves. Where a control sits on
// screen decides where each arrow leads, so shelves, lists, grids and the top bar all behave the same way.
(() => {
const state = { el: null, trap: null, memory: new Map(), routeKey: '' };

const visible = (el) => {
    if (el.closest('[hidden]')) return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
};

// The controls that can be selected right now: an open menu or dialog holds them all, otherwise the top bar and the screen.
const stops = () => {
    const scope = state.trap ? [state.trap] : [FT.$('#top'), FT.$('#screen')];
    return scope.flatMap((root) => FT.$$('[data-f]', root)).filter(visible);
};

const box = (el) => {
    const r = el.getBoundingClientRect();
    return { l: r.left, r: r.right, t: r.top, b: r.bottom, cx: r.left + r.width / 2, cy: r.top + r.height / 2, w: r.width, h: r.height };
};

// Best control in a direction: nearest along it, with a strong preference for staying lined up (same row or column).
const neighbour = (from, all, dir) => {
    const c = box(from);
    const horizontal = dir === 'left' || dir === 'right';
    const sign = dir === 'right' || dir === 'down' ? 1 : -1;
    let best = null;
    let bestScore = Infinity;
    for (const el of all) {
        if (el === from) continue;
        const r = box(el);
        const along = (horizontal ? r.cx - c.cx : r.cy - c.cy) * sign;
        if (along <= 4) continue;
        const across = Math.abs(horizontal ? r.cy - c.cy : r.cx - c.cx);
        // Sideways moves stay on the row they are on; a control in another row is not "to the left".
        if (horizontal && across > Math.max(c.h, r.h) * 0.6) continue;
        // Up and down prefer what is directly above or below (the two overlap side to side); only when there is
        // nothing there does the nearest control off to the side count.
        const lined = Math.min(r.r, c.r) - Math.max(r.l, c.l) > 8;
        const score = horizontal ? along + across * 3 : lined ? along : along + across * 2 + 100000;
        if (score < bestScore) { bestScore = score; best = el; }
    }
    return best;
};

const inTop = (el) => !!el && !!el.closest('#top');

FT.focus = {
    get el() { return state.el; },
    stops,

    select(el, options = {}) {
        if (state.el && state.el !== el) state.el.classList.remove('sel');
        state.el = el || null;
        if (!el) return;
        el.classList.add('sel');
        if (options.scroll !== false) el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        if (FT.onSelect) FT.onSelect(el);
    },

    clear() {
        if (state.el) state.el.classList.remove('sel');
        state.el = null;
    },

    // Confine the controls to one element (an open menu); pass null to release.
    trap(el) { state.trap = el || null; },

    first() {
        const all = stops();
        // A screen opens on its first control; when that is a search box and there are results, on the first result instead.
        const content = all.filter((el) => !inTop(el));
        const lead = content[0];
        const pick = lead && lead.matches('.field') ? content.find((el) => el.matches('.row-item, .card')) || lead : lead || all[0];
        if (pick) FT.focus.select(pick);
        return pick || null;
    },

    // Remember where the selection was on this screen, so coming back to it lands on the same control.
    remember() {
        if (state.routeKey && state.el && state.el.dataset.key && !inTop(state.el)) state.memory.set(state.routeKey, state.el.dataset.key);
    },

    // Called when a screen has drawn itself: reselect what was selected last time here, else the first control.
    restore(routeKey) {
        state.routeKey = routeKey;
        FT.focus.clear();
        const wanted = state.memory.get(routeKey);
        const all = stops();
        const match = wanted && all.find((el) => el.dataset.key === wanted);
        if (match) FT.focus.select(match);
        else FT.focus.first();
    },

    // Where a downward move from the top bar should land.
    below() {
        const wanted = state.memory.get(state.routeKey);
        const all = stops().filter((el) => !inTop(el));
        return (wanted && all.find((el) => el.dataset.key === wanted)) || all[0] || null;
    },

    move(dir) {
        const all = stops();
        const from = state.el && all.includes(state.el) ? state.el : null;
        if (!from) { FT.focus.first(); return; }
        if (dir === 'down' && inTop(from)) {
            const target = FT.focus.below();
            if (target) FT.focus.select(target);
            return;
        }
        const next = neighbour(from, all, dir);
        if (next) FT.focus.select(next);
    },

    activate() {
        const el = state.el;
        if (!el) return;
        const field = FT.$('input', el);
        if (field) field.focus();
        else el.click();
    },
};

// ---- Keys and the input mode ----

const setInput = (mode) => { if (document.body.dataset.input !== mode) document.body.dataset.input = mode; };
FT.input = setInput;

const DIRECTIONS = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' };

// One entry point for the keyboard and the phone remote: a command name in, the same behaviour out.
FT.command = (name) => {
    setInput('keys');
    if (FT.command.hook && FT.command.hook(name)) return;
    if (name === 'ok') FT.focus.activate();
    else if (name === 'menu') FT.menu.forSelected();
    else if (name === 'back') FT.back();
    else FT.focus.move(name);
};

document.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    const inField = e.target && e.target.matches && e.target.matches('input, textarea, select');
    const dir = DIRECTIONS[e.key];
    if (inField) {
        // A text field keeps its own keys, except that up and down leave it and Escape gives it up.
        if (e.key === 'Escape') { e.target.blur(); FT.focus.select(e.target.closest('[data-f]')); e.preventDefault(); }
        else if (dir === 'up' || dir === 'down') { e.target.blur(); FT.command(dir); e.preventDefault(); }
        return;
    }
    if (dir) { FT.command(dir); e.preventDefault(); }
    else if (e.key === 'Enter') { if (state.el) { FT.command('ok'); e.preventDefault(); } }
    else if (e.key === 'Escape' || e.key === 'Backspace' || e.key === 'BrowserBack') { FT.command('back'); e.preventDefault(); }
    else if (e.key === 'ContextMenu' || e.key === 'Menu' || e.key === 'm' || e.key === 'M') { FT.command('menu'); e.preventDefault(); }
    else if (e.key === 'Tab') { FT.command(e.shiftKey ? 'left' : 'right'); e.preventDefault(); }
});

// The mouse takes over again the moment it really moves; a hovered control becomes the selected one, so the keys continue from it.
let lastX = -1;
let lastY = -1;
document.addEventListener('mousemove', (e) => {
    if (e.clientX === lastX && e.clientY === lastY) return;
    lastX = e.clientX;
    lastY = e.clientY;
    setInput('pointer');
});
document.addEventListener('mouseover', (e) => {
    // Content sliding under a still mouse (keys scrolling the page) also raises mouseover; only a moving mouse counts.
    if (document.body.dataset.input !== 'pointer') return;
    const stop = e.target.closest && e.target.closest('[data-f]');
    if (stop && stop !== state.el && (!state.trap || state.trap.contains(stop))) FT.focus.select(stop, { scroll: false });
});
})();

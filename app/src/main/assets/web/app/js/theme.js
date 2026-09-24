// The theme: a palette (which colours) and a mode (how light). Both are attributes on <html>, set by FT_setTheme in
// index.html; what each palette and scheme looks like is in tokens.css. The choice is kept in this browser.
(() => {

const save = (key, value) => { try { localStorage.setItem(key, value); } catch (e) {} };
const root = document.documentElement;

FT.palettes = [
    { id: 'fathom', name: 'Fathom', note: 'Ivory, steel blue, deep navy' },
    { id: 'graphite', name: 'Graphite', note: 'Neutral, no colour' },
    { id: 'ember', name: 'Ember', note: 'Terracotta, espresso' },
    { id: 'moss', name: 'Moss', note: 'Sage, forest' },
    { id: 'lagoon', name: 'Lagoon', note: 'Seafoam, deep teal' },
    { id: 'orchid', name: 'Orchid', note: 'Lilac, aubergine' },
    { id: 'rose', name: 'Rose', note: 'Blush, wine' },
    { id: 'citrus', name: 'Citrus', note: 'Lime cream, olive' },
    { id: 'device', name: 'Phone colours', note: 'Follows the phone' },
];

FT.modes = [['light', 'Light'], ['dark', 'Dark'], ['oled', 'OLED'], ['auto', 'Auto']];

FT.theme = {
    palette: () => root.dataset.palette,
    mode: () => root.dataset.mode,
    setPalette(id) {
        if (!FT.palettes.some((p) => p.id === id)) return;
        window.FT_setTheme(id, FT.theme.mode());
        save('fathom-palette', id);
    },
    setMode(id) {
        if (!FT.modes.some(([mode]) => mode === id)) return;
        window.FT_setTheme(FT.theme.palette(), id);
        save('fathom-mode', id);
    },
};

// Auto follows the browser's own light or dark setting as it changes.
const preference = window.matchMedia && matchMedia('(prefers-color-scheme: dark)');
if (preference && preference.addEventListener) {
    preference.addEventListener('change', () => { if (FT.theme.mode() === 'auto') window.FT_setTheme(FT.theme.palette(), 'auto'); });
}
})();

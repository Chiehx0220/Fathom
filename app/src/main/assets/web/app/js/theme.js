// Theme = palette + mode, attributes on <html> (FT_setTheme, index.html); styles in tokens.css; per-browser.
(() => {

const save = (key, value) => { try { localStorage.setItem(key, value); } catch (e) {} };
const root = document.documentElement;

FT.palettes = [
    { id: 'fathom', name: FT.t('palette_fathom_name'), note: FT.t('palette_fathom_note') },
    { id: 'graphite', name: FT.t('palette_graphite_name'), note: FT.t('palette_graphite_note') },
    { id: 'ember', name: FT.t('palette_ember_name'), note: FT.t('palette_ember_note') },
    { id: 'moss', name: FT.t('palette_moss_name'), note: FT.t('palette_moss_note') },
    { id: 'lagoon', name: FT.t('palette_lagoon_name'), note: FT.t('palette_lagoon_note') },
    { id: 'orchid', name: FT.t('palette_orchid_name'), note: FT.t('palette_orchid_note') },
    { id: 'rose', name: FT.t('palette_rose_name'), note: FT.t('palette_rose_note') },
    { id: 'citrus', name: FT.t('palette_citrus_name'), note: FT.t('palette_citrus_note') },
    { id: 'device', name: FT.t('palette_device_name'), note: FT.t('palette_device_note') },
];

FT.modes = [['light', FT.t('mode_light')], ['dark', FT.t('mode_dark')], ['oled', FT.t('mode_oled')], ['auto', FT.t('mode_auto')]];

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

// Auto follows browser scheme changes.
const preference = window.matchMedia && matchMedia('(prefers-color-scheme: dark)');
if (preference && preference.addEventListener) {
    preference.addEventListener('change', () => { if (FT.theme.mode() === 'auto') window.FT_setTheme(FT.theme.palette(), 'auto'); });
}
})();

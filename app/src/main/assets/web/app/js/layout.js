// Layout: "tv" = shelves + detail pane, "grid" = grids; "auto" follows (hover: hover) and (pointer: fine). Persisted per browser.
(() => {

const KEY = 'fathom-layout';
const pointer = window.matchMedia && matchMedia('(hover: hover) and (pointer: fine)');

FT.layouts = [['auto', FT.t('layout_auto')], ['grid', FT.t('layout_grid')], ['tv', FT.t('layout_tv')]];

const stored = () => { try { return localStorage.getItem(KEY); } catch (e) { return null; } };

FT.layout = {
    mode: () => (FT.layouts.some(([id]) => id === stored()) ? stored() : 'auto'),
    current() {
        const mode = FT.layout.mode();
        if (mode !== 'auto') return mode;
        return pointer && pointer.matches ? 'grid' : 'tv';
    },
    setMode(id) {
        if (!FT.layouts.some(([mode]) => mode === id)) return;
        try { localStorage.setItem(KEY, id); } catch (e) {}
    },
};
})();

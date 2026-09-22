// What the app remembers between screens and visits: which source is chosen, and the theme.
(() => {

const save = (key, value) => { try { localStorage.setItem(key, value); } catch (e) {} };
const load = (key) => { try { return localStorage.getItem(key); } catch (e) { return null; } };

const services = (window.FT_CONFIG && window.FT_CONFIG.services) || [[0, 'YouTube']];
FT.services = services.map(([id, name]) => ({ id, name }));

FT.store = {
    service: (() => {
        const stored = parseInt(load('fathom-service'), 10);
        return FT.services.some((s) => s.id === stored) ? stored : FT.services[0].id;
    })(),
    setService(id) {
        if (!FT.services.some((s) => s.id === id)) return;
        FT.store.service = id;
        save('fathom-service', String(id));
    },
    serviceName: (id) => (FT.services.find((s) => s.id === id) || {}).name || '',
};

// Themes are only values for the colour roles in tokens.css. `art` is the three colours drawn in the picker.
FT.themes = [
    { id: 'fathom', name: 'Fathom', note: 'Deep navy, gold', art: ['#0B1524', '#1F3454', '#E8C468'] },
    { id: 'graphite', name: 'Graphite', note: 'Neutral dark, blue', art: ['#121316', '#2E3238', '#8AB4F8'] },
    { id: 'oled', name: 'Midnight', note: 'True black, teal', art: ['#000000', '#232428', '#4DD6C1'] },
    { id: 'ember', name: 'Ember', note: 'Warm dark, orange', art: ['#17110D', '#3A2A1D', '#FF9E5E'] },
    { id: 'forest', name: 'Forest', note: 'Deep green, lime', art: ['#0C1611', '#253F32', '#A8D86E'] },
    { id: 'orchid', name: 'Orchid', note: 'Dusk purple, pink', art: ['#150F1F', '#3A2C5A', '#F08BC0'] },
    { id: 'paper', name: 'Paper', note: 'Warm light, amber', art: ['#F6F1E6', '#D6CBB2', '#8F6200'] },
    { id: 'daylight', name: 'Daylight', note: 'Cool light, blue', art: ['#F3F6FB', '#CBD8EC', '#1B62D6'] },
    { id: 'device', name: 'Phone colours', note: 'Follows the phone', art: ['#10131A', '#272A32', '#A8C8FF'] },
];

FT.theme = {
    current: () => document.documentElement.dataset.theme,
    apply(id) {
        if (!FT.themes.some((t) => t.id === id)) return;
        document.documentElement.dataset.theme = id;
        save('fathom-theme', id);
    },
};

// Whether the phone remote is connected here; the remote layer keeps it up to date.
FT.remote = { active: false };
})();

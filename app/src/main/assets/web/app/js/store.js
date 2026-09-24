// What the app remembers between screens and visits: which source is chosen.
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

// Whether the phone remote is connected here; the remote layer keeps it up to date.
FT.remote = { active: false };
})();

// Server push: /events topics; polling fallback while down.
(() => {
const RETRY_MS = [1000, 2000, 5000, 10000];
const listeners = new Map();
let socket = null;
let up = false;
let attempt = 0;
let lost = false;

const connect = () => {
    if (socket || document.hidden) return;
    const ws = new WebSocket('ws://' + location.host + '/events');
    socket = ws;
    // Re-read after reconnect, not on first connect.
    ws.onopen = () => {
        up = true;
        attempt = 0;
        if (lost) listeners.forEach((set) => set.forEach((fn) => fn()));
        lost = false;
    };
    ws.onmessage = (event) => {
        FT.api.fresh();
        (listeners.get(String(event.data)) || []).forEach((fn) => fn());
    };
    ws.onclose = () => {
        up = false;
        lost = true;
        if (socket === ws) socket = null;
        setTimeout(connect, RETRY_MS[Math.min(attempt++, RETRY_MS.length - 1)]);
    };
};

FT.events = {
    connect,
    connected: () => up,
    // Returns unsubscribe.
    on(topic, fn) {
        if (!listeners.has(topic)) listeners.set(topic, new Set());
        listeners.get(topic).add(fn);
        return () => listeners.get(topic).delete(fn);
    },
};
document.addEventListener('visibilitychange', connect);
})();

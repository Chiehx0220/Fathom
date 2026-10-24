// FT.playerCore: cross-module state + helpers.
// Only multi-module state here; rest private to owner.
// Order: WebAssets APP_JS_FILES.
(() => {
// Placeholder media while idle.
const IDLE = {
    paused: true, currentTime: 0, duration: 0, volume: 1, muted: false,
    play: () => Promise.resolve(), pause() {}, addEventListener() {}, removeEventListener() {},
};

let hudTimer = 0;

const P = FT.playerCore = {
    // { host, skin, media, live, chapterUrl }; host.store: fullscreen, renditions, tracks
    tree: null,
    // { service, url, info, chapters, segments, chapterTrack }
    current: null,
    // 'off' | 'full' | 'mini'; written only by P.dock.set
    dockMode: 'off',

    stage: () => FT.$('#stage'),
    media: () => (P.tree ? P.tree.media : IDLE),

    hud(icon, text) {
        const box = FT.$('#hud');
        if (!box) return;
        box.replaceChildren(FT.icon(icon), text);
        box.hidden = false;
        clearTimeout(hudTimer);
        hudTimer = setTimeout(() => { box.hidden = true; }, 1100);
    },

    isFullscreen: () => document.body.classList.contains('remote-fs') || !!document.fullscreenElement || !!document.webkitFullscreenElement
        || !!(P.tree && P.tree.host.store && P.tree.host.store.isFullscreen),

    track(kind, label, lang, src, isDefault) {
        const el = document.createElement('track');
        el.kind = kind;
        el.label = label;
        el.srclang = lang;
        el.src = src;
        el.default = !!isDefault;
        return el;
    },
};
})();

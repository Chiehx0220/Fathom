// Shared state of the one player (FT.playerCore), plus helpers every player-*.js module needs.
// Only state read by several modules lives here; the rest is private to the module that owns it.
// Load order (WebAssets): state, prefs, progress, segments, stream, menu, danmaku, upnext, dock, controls, build, player.
(() => {
// Stands in for the media element while nothing is open.
const IDLE = {
    paused: true, currentTime: 0, duration: 0, volume: 1, muted: false,
    play: () => Promise.resolve(), pause() {}, addEventListener() {}, removeEventListener() {},
};

let hudTimer = 0;

const P = FT.playerCore = {
    // { host, skin, media, live, chapterUrl }: the Video.js player of the open video. host.store carries fullscreen, renditions, tracks.
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

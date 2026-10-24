// Per-video player build: modules, skin, media component, bindings.
(() => {
const P = FT.playerCore;

// Lazy-load player modules on first open (1.8 MB); version tag = /app.js ?v=.
const APP_VERSION = new URL(document.currentScript.src).searchParams.get('v') || '';
const vendor = (path) => `/vendor/videojs/${path}?v=${APP_VERSION}`;
const loadPlayer = (way) => Promise.all([
    import(vendor(way.live ? 'live-video.js' : 'video.js')),
    way.tag === 'video' ? null : import(vendor(`media/${way.tag}.js`)),
]);

// Skin shadow-root CSS (unreachable from page CSS): hide controls in mini, force-enable cast button (FT.cast), SponsorBlock marks.
const SKIN_STYLE = `
:host([data-mini]) media-controls, :host([data-mini]) media-title { display: none !important; }
media-cast-button[hidden] { display: inline-flex !important; }
media-cast-button, media-cast-button[data-disabled], media-cast-button[aria-disabled] { opacity: 1 !important; pointer-events: auto !important; cursor: pointer; }
.sb-mark { position: absolute; top: 50%; height: 4px; transform: translateY(-50%); min-width: 3px; border-radius: 2px; pointer-events: none; background: #00D400; opacity: 0.9; }
.sb-mark.intro, .sb-mark.outro { background: #00FFFF; }
.sb-mark.interaction { background: #CC00FF; }
.sb-mark.selfpromo { background: #FFFF00; }
.sb-mark.music_offtopic { background: #FF9900; }`;

// Media Session (lock screen, media keys); not set by Video.js.
const publishMediaSession = (info) => {
    if (!('mediaSession' in navigator) || typeof MediaMetadata === 'undefined') return;
    try {
        navigator.mediaSession.metadata = new MediaMetadata({
            title: info.title || '', artist: info.channelName || '', artwork: info.thumbnailUrl ? [{ src: info.thumbnailUrl }] : [],
        });
    } catch (e) {}
};

const bindMedia = (media) => {
    let restored = false;
    media.addEventListener('loadedmetadata', () => { P.segments.addTrack(); P.segments.placeMarks(); });
    media.addEventListener('loadedmetadata', () => { P.prefs.restore(media); restored = true; }, { once: true });
    media.addEventListener('durationchange', P.segments.addTrack);
    media.addEventListener('timeupdate', P.segments.watch);
    media.addEventListener('pause', P.progress.report);
    media.addEventListener('ended', P.upNext.schedule);
    media.addEventListener('play', P.dock.paintPlay);
    media.addEventListener('pause', P.dock.paintPlay);
    media.addEventListener('volumechange', () => { if (restored) P.prefs.save(media); });
    media.addEventListener('ratechange', () => { if (restored) P.prefs.save(media); });
    media.addEventListener('canplay', () => { media.play().catch(() => {}); }, { once: true });
};

const teardown = () => {
    const holder = FT.$('#overlay-holder');
    if (holder) holder.append(FT.$('#skip-btn'), FT.$('#hud'));
    P.menu.stop();
    if (P.tree) {
        // Stop before remove, else audio continues.
        try { P.tree.media.pause(); P.tree.media.removeAttribute('src'); } catch (e) {}
        if (P.tree.chapterUrl) URL.revokeObjectURL(P.tree.chapterUrl);
        P.tree.host.remove();
        P.tree = null;
    }
    FT.$('#player').replaceChildren();
};

// Shadow root exists after connect; inject rules then.
const skinReady = (skin) => new Promise((resolve) => {
    const started = performance.now();
    const check = () => ((skin.shadowRoot && skin.shadowRoot.firstElementChild) || performance.now() - started > 3000 ? resolve() : requestAnimationFrame(check));
    check();
});

// Replaces previous player; null if superseded while loading.
const build = async (info, way, owner) => {
    teardown();
    const [playerTag, skinTag] = way.live ? ['live-video-player', 'live-video-skin'] : ['video-player', 'video-skin'];
    await loadPlayer(way);
    await Promise.all([playerTag, skinTag, ...(way.tag === 'video' ? [] : [way.tag])].map((tag) => customElements.whenDefined(tag)));
    if (P.current !== owner) return null;
    const host = document.createElement(playerTag);
    host.setAttribute('content-title', info.title || '');
    const skin = document.createElement(skinTag);
    skin.toggleAttribute('data-mini', P.dockMode === 'mini');
    const media = document.createElement(way.tag);
    media.setAttribute('playsinline', '');
    media.setAttribute('crossorigin', '');
    skin.append(media, FT.$('#skip-btn'), FT.$('#hud'));
    host.append(skin);
    P.tree = { host, skin, media, live: way.live, chapterUrl: '' };
    FT.$('#player').replaceChildren(host);
    await skinReady(skin);
    if (skin.shadowRoot) {
        const style = document.createElement('style');
        style.textContent = SKIN_STYLE;
        skin.shadowRoot.append(style);
        const castButton = skin.shadowRoot.querySelector('media-cast-button');
        if (castButton) {
            const open = (e) => { e.preventDefault(); e.stopImmediatePropagation(); FT.cast.open(info); };
            castButton.addEventListener('click', open, true);
            castButton.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') open(e); }, true);
        }
    }
    P.menu.watch();
    bindMedia(media);
    return P.tree;
};

FT.playerCore.build = { build, teardown, publishMediaSession };
})();

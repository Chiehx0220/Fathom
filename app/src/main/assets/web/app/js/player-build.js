// Builds the Video.js player of one video: modules, skin, media component, and the page-side bindings.
(() => {
const P = FT.playerCore;

// Player modules come with the first video that opens, not with the page: player, live player and media components are 1.8 MB together.
// The page's version goes on the entry files, as for /app.js.
const APP_VERSION = new URL(document.currentScript.src).searchParams.get('v') || '';
const vendor = (path) => `/vendor/videojs/${path}?v=${APP_VERSION}`;
const loadPlayer = (way) => Promise.all([
    import(vendor(way.live ? 'live-video.js' : 'video.js')),
    way.tag === 'video' ? null : import(vendor(`media/${way.tag}.js`)),
]);

// Rules for the skin's shadow root, out of reach of the page's CSS: no skin controls in the mini player (its buttons are the page's .mini-ctrl),
// the skin's cast button (disabled without the Cast SDK; FT.cast takes it over), SponsorBlock marks on the seek bar.
const SKIN_STYLE = `
:host([data-mini]) media-controls, :host([data-mini]) media-title { display: none !important; }
media-cast-button[hidden] { display: inline-flex !important; }
media-cast-button, media-cast-button[data-disabled], media-cast-button[aria-disabled] { opacity: 1 !important; pointer-events: auto !important; cursor: pointer; }
.sb-mark { position: absolute; top: 50%; height: 4px; transform: translateY(-50%); min-width: 3px; border-radius: 2px; pointer-events: none; background: #00D400; opacity: 0.9; }
.sb-mark.intro, .sb-mark.outro { background: #00FFFF; }
.sb-mark.interaction { background: #CC00FF; }
.sb-mark.selfpromo { background: #FFFF00; }
.sb-mark.music_offtopic { background: #FF9900; }`;

// Video.js does not set the media session shown on lock screens and by media keys.
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
        // A removed media element keeps playing unless stopped first.
        try { P.tree.media.pause(); P.tree.media.removeAttribute('src'); } catch (e) {}
        if (P.tree.chapterUrl) URL.revokeObjectURL(P.tree.chapterUrl);
        P.tree.host.remove();
        P.tree = null;
    }
    FT.$('#player').replaceChildren();
};

// The skin builds its shadow root when connected; its rules go in once that exists.
const skinReady = (skin) => new Promise((resolve) => {
    const started = performance.now();
    const check = () => ((skin.shadowRoot && skin.shadowRoot.firstElementChild) || performance.now() - started > 3000 ? resolve() : requestAnimationFrame(check));
    check();
});

// Replaces the last player. The media component follows from the stream, so it is part of what is built.
// Returns null when another video was opened while this one waited for its components.
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

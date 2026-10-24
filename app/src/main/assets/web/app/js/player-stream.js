// Which stream plays (plan) and which rendition of it (quality).
(() => {
const P = FT.playerCore;

// hls.js: 60 s buffer (default 30) so a stall on a slow link does not drain it; high first rung (bits/s), corrected by the estimate within a few segments.
const HLS_SETTINGS = { maxBufferLength: 60, maxMaxBufferLength: 120, backBufferLength: 30, abrEwmaDefaultEstimate: 6000000 };

// YouTube 1440p/4K are AV1. Smooth decode here → playlist asks for the tallest ladder (4K); else H.264, up to 1080p. Asked once.
// decodingInfo() is a hint; MediaSource.isTypeSupported() is what the player enforces.
let highestQuality = null;
const canPlayHighest = () => highestQuality || (highestQuality = (async () => {
    try {
        if (!(window.MediaSource && MediaSource.isTypeSupported('video/mp4; codecs="av01.0.12M.08"'))) return false;
        const info = await navigator.mediaCapabilities.decodingInfo({
            type: 'media-source',
            video: { contentType: 'video/mp4; codecs="av01.0.12M.08"', width: 2560, height: 1440, bitrate: 12000000, framerate: 30 },
        });
        return info.supported && info.smooth;
    } catch (e) { return false; }
})());

// Media component and skin for what /api/v1/video describes. Live room and every quality ladder is HLS; anything else (audio only) is one plain stream.
// `local=1`: play the saved file; on a media error the caller falls back to the stream.
const plan = async (info, service, allowLocal = FT.route.params.local === '1') => {
    const pb = info.playback;
    if (allowLocal && !pb.liveRoom && !info.isLive) return { live: false, tag: 'video', src: `/downloadfile?serviceId=${service}&id=${FT.enc(info.url)}`, local: true };
    if (pb.hlsUrl) return { live: !!pb.liveRoom, tag: 'hlsjs-video', src: pb.hlsUrl };
    if (pb.isAdaptive) return { live: false, tag: 'hlsjs-video', src: pb.playlistUrl + ((await canPlayHighest()) ? '&prefer=hd' : '') };
    return { live: false, tag: 'video', src: pb.streamUrl };
};

// Rendition id the viewer picked, or 'auto'; the player only reports which one is playing.
let chosenQuality = 'auto';

const qualityLabel = (r) => (r.height ? r.height + 'p' + (r.frameRate > 30 ? Math.round(r.frameRate) : '') : '');

// Choices for the remote: tallest first, one per label; fewer than two is nothing to choose.
const snapshot = () => {
    const store = P.tree && P.tree.host.store;
    const list = (store && store.videoRenditionList) || [];
    const seen = new Map();
    list.forEach((r) => {
        const label = qualityLabel(r);
        if (label && (!seen.has(label) || (r.bitrate || 0) > (seen.get(label).bitrate || 0))) seen.set(label, r);
    });
    const options = [...seen.entries()]
        .sort((a, b) => (b[1].height || 0) - (a[1].height || 0) || (b[1].frameRate || 0) - (a[1].frameRate || 0))
        .map(([label, r]) => [String(r.id), label]);
    const active = store && store.activeVideoRendition;
    return {
        qualities: options.length > 1 ? options : [],
        quality: active ? qualityLabel(active) : '',
        qchosen: chosenQuality,
    };
};

FT.playerCore.stream = {
    HLS_SETTINGS,
    plan,
    snapshot,
    reset: () => { chosenQuality = 'auto'; },
    setQuality(id) {
        const store = P.tree && P.tree.host.store;
        if (!store || typeof store.selectVideoRendition !== 'function') return;
        chosenQuality = id === 'auto' ? 'auto' : String(id);
        try { store.selectVideoRendition(chosenQuality); } catch (e) {}
    },
};
})();

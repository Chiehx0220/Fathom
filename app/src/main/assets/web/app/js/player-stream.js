// Stream plan + rendition selection.
(() => {
const P = FT.playerCore;

// hls.js: 60 s buffer; first rung 6 Mbit/s (ABR corrects).
const HLS_SETTINGS = { maxBufferLength: 60, maxMaxBufferLength: 120, backBufferLength: 30, abrEwmaDefaultEstimate: 6000000 };

// 1440p/4K = AV1: smooth decode → &prefer=hd, else H.264 ≤1080p. Cached.
// decodingInfo() advisory; isTypeSupported() enforced.
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

// Plan per /api/v1/video playback: HLS (live, adaptive) or single stream; local=1 saved file, caller falls back on error.
const plan = async (info, service, allowLocal = FT.route.params.local === '1') => {
    const pb = info.playback;
    if (allowLocal && !pb.liveRoom && !info.isLive) return { live: false, tag: 'video', src: `/downloadfile?serviceId=${service}&id=${FT.enc(info.url)}`, local: true };
    if (pb.hlsUrl) return { live: !!pb.liveRoom, tag: 'hlsjs-video', src: pb.hlsUrl };
    if (pb.isAdaptive) return { live: false, tag: 'hlsjs-video', src: pb.playlistUrl + ((await canPlayHighest()) ? '&prefer=hd' : '') };
    return { live: false, tag: 'video', src: pb.streamUrl };
};

// Selected rendition id or 'auto'.
let chosenQuality = 'auto';

const qualityLabel = (r) => (r.height ? r.height + 'p' + (r.frameRate > 30 ? Math.round(r.frameRate) : '') : '');

// Remote choices: one per label, tallest first; <2 → none.
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

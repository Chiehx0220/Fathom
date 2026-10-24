// Progress reporting, resume position.
(() => {
const P = FT.playerCore;
let lastReported = -1;

// Timer silent; pause/close/switch notify history.
const report = (quiet) => {
    const p = P.media();
    if (!P.current || !p.duration || !isFinite(p.duration)) return;
    const percent = Math.round((p.currentTime / p.duration) * 100);
    if (percent === lastReported) return;
    lastReported = percent;
    FT.api.progress(P.current.url, P.current.service, percent, Math.round(p.duration), { notify: quiet !== true });
};
setInterval(() => report(true), 15000);
addEventListener('pagehide', report);

const sameVideo = (a, b) => a === b || (new URL(a, location.href).searchParams.get('v') || a) === (new URL(b, location.href).searchParams.get('v') || b);

// play_video_at start position; precedes history.
const startFromPhone = (owner, media, info) => {
    const at = FT.startAt;
    FT.startAt = null;
    if (!at || !sameVideo(at.url, info.url)) return false;
    const seek = () => { if (P.current === owner) media.currentTime = at.sec; };
    if (media.readyState >= 1) seek(); else media.addEventListener('loadedmetadata', seek, { once: true });
    return true;
};

// Resume from history; no report before playback (no 0% overwrite).
const resume = (owner, media, info, service) => {
    if (info.isLive || info.playback.liveRoom) return;
    if (startFromPhone(owner, media, info)) return;
    FT.api.history().then((hist) => {
        const saved = (hist.videos || []).find((v) => v.url === info.url && (v.serviceId ?? 0) === service);
        if (!saved || !(saved.progress > 2 && saved.progress < 95)) return;
        // Timeline may start >0: 'unplayed' = relative to buffer start.
        const seek = (fresh) => {
            if (P.current !== owner || !isFinite(media.duration)) return;
            const start = media.buffered.length ? media.buffered.start(0) : 0;
            if (!fresh && media.currentTime - start > 5) return;
            media.currentTime = (saved.progress / 100) * media.duration;
        };
        if (media.readyState >= 1) seek(false);
        else media.addEventListener('loadedmetadata', () => seek(true), { once: true });
    }).catch(() => {});
};

FT.playerCore.progress = { report, resume, reset: () => { lastReported = -1; } };
})();

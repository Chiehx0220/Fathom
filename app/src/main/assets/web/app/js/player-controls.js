// Transport controls for keys and the phone remote, and the state snapshot the remote reads.
(() => {
const P = FT.playerCore;

FT.playerCore.controls = {
    toggle() {
        const p = P.media();
        if (!P.current) return;
        if (p.paused) p.play().catch(() => {}); else p.pause();
        P.hud(p.paused ? 'play_arrow' : 'pause', FT.t(p.paused ? 'play' : 'pause'));
    },
    seek(delta) {
        const p = P.media();
        if (!P.current) return;
        const limit = P.current.info.duration || p.duration || 0;
        p.currentTime = Math.max(0, Math.min(limit || Infinity, p.currentTime + delta));
        P.hud(delta > 0 ? 'fast_forward' : 'fast_rewind', FT.t('hud_seconds', { value: (delta > 0 ? '+' : '') + delta }));
    },
    seekTo(sec) {
        if (P.current && isFinite(sec)) P.media().currentTime = Math.max(0, sec);
    },
    volume(delta) {
        const p = P.media();
        p.muted = false;
        p.volume = Math.max(0, Math.min(1, (p.volume || 0) + delta));
        P.hud(p.volume === 0 ? 'volume_off' : p.volume < 0.5 ? 'volume_down' : 'volume_up', Math.round(p.volume * 100) + '%');
    },
    mute() {
        const p = P.media();
        p.muted = !p.muted;
        P.hud(p.muted ? 'volume_off' : 'volume_up', p.muted ? FT.t('muted') : Math.round(p.volume * 100) + '%');
    },
    setRate(rate) {
        const value = parseFloat(rate);
        if (!(value >= 0.25 && value <= 4)) return;
        P.media().playbackRate = value;
        P.hud('speed', value + 'x');
    },
    async fullscreen(want) {
        if (!P.current) return;
        const enter = want === undefined ? !P.isFullscreen() : want;
        const store = P.tree && P.tree.host.store;
        if (!enter) {
            document.body.classList.remove('remote-fs');
            if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(() => {});
            try { const left = store && store.isFullscreen && store.exitFullscreen(); if (left && left.catch) left.catch(() => {}); } catch (e) {}
            return;
        }
        // A browser grants real fullscreen only right after a click on the page itself, which a remote never makes: if refused, fill the window.
        try { const asked = store && store.requestFullscreen(); if (asked && asked.catch) asked.catch(() => {}); } catch (e) {}
        setTimeout(() => { if (!P.isFullscreen()) document.body.classList.add('remote-fs'); }, 300);
    },
    snapshot() {
        const p = P.media();
        const current = P.current;
        const danmaku = P.danmaku.current();
        return {
            open: !!current, title: current ? current.info.title : '', t: p.currentTime || 0, d: p.duration || 0, paused: !!p.paused,
            vol: p.volume == null ? 1 : p.volume, muted: !!p.muted, fs: P.isFullscreen(),
            chapters: current ? current.chapters.map((c) => c.t) : [], chapi: P.segments.index(),
            skip: P.segments.skipLabel(),
            rate: p.playbackRate || 1,
            danmaku: danmaku ? danmaku.on : null,
            ...P.stream.snapshot(),
        };
    },
};
})();

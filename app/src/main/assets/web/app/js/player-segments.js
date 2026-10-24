// Chapters, SponsorBlock segments and the skip button.
(() => {
const P = FT.playerCore;
const h = FT.h;
let skipTarget = null;

const vttTime = (sec) => {
    const ms = Math.round(sec * 1000);
    const pad = (n, len) => String(n).padStart(len, '0');
    return `${pad(Math.floor(ms / 3600000), 2)}:${pad(Math.floor(ms / 60000) % 60, 2)}:${pad(Math.floor(ms / 1000) % 60, 2)}.${pad(ms % 1000, 3)}`;
};

// The skin draws chapter gaps and names on the seek bar from a chapters text track.
const addTrack = () => {
    const p = P.media();
    const current = P.current;
    if (!current || !current.chapters.length || current.chapterTrack || !p.duration || !isFinite(p.duration)) return;
    current.chapterTrack = true;
    const list = current.chapters;
    let vtt = 'WEBVTT\n\n';
    list.forEach((ch, i) => {
        const end = i + 1 < list.length ? list[i + 1].s : p.duration;
        if (end > ch.s) vtt += `${vttTime(ch.s)} --> ${vttTime(end)}\n${ch.t.replace(/[\r\n]+/g, ' ')}\n\n`;
    });
    if (!P.tree) return;
    P.tree.chapterUrl = URL.createObjectURL(new Blob([vtt], { type: 'text/vtt' }));
    p.append(P.track('chapters', FT.t('chapters'), 'en', P.tree.chapterUrl, true));
};

// Marks go into the skin's time slider, in the skin's open shadow root (rules in player-build.js SKIN_STYLE).
// Packaged skin internals: re-check that the slider is still found after a Video.js upgrade.
const placeMarks = () => {
    const p = P.media();
    if (!P.tree || !P.current || !P.current.segments.length || !p.duration) return;
    const slider = P.tree.skin.shadowRoot && P.tree.skin.shadowRoot.querySelector('media-time-slider');
    if (!slider) return;
    slider.querySelectorAll('.sb-mark').forEach((m) => m.remove());
    for (const seg of P.current.segments) {
        slider.append(h('div', { class: 'sb-mark ' + seg.c, style: `left:${(seg.s / p.duration) * 100}%;width:${(Math.max(seg.e - seg.s, 0) / p.duration) * 100}%` }));
    }
};

// Server labels are English; translated via segment_* keys when present.
const segmentName = (label) => FT.t('segment_' + String(label).replace(/[ -]/g, '_'), null, label);

const watch = () => {
    const btn = FT.$('#skip-btn');
    if (!P.current || !btn) return;
    const t = P.media().currentTime;
    const seg = P.current.segments.find((s) => t >= s.s && t < s.e) || null;
    if (seg === skipTarget) return;
    skipTarget = seg;
    btn.hidden = !seg;
    if (seg) btn.textContent = FT.t('skip', { label: segmentName(seg.l) });
};

const index = () => {
    const p = P.media();
    if (!P.current || !P.current.chapters.length) return -1;
    let found = 0;
    P.current.chapters.forEach((ch, i) => { if (p.currentTime >= ch.s) found = i; });
    return found;
};

FT.playerCore.segments = {
    addTrack,
    placeMarks,
    watch,
    index,
    reset() {
        skipTarget = null;
        FT.$('#skip-btn').hidden = true;
    },
    load(service, info) {
        FT.api.sponsor(service, info.url).then((body) => {
            if (!P.current || P.current.url !== info.url) return;
            P.current.segments = body.segments || [];
            placeMarks();
        }).catch(() => {});
    },
    skip() {
        if (!skipTarget) return false;
        P.media().currentTime = skipTarget.e;
        FT.$('#skip-btn').hidden = true;
        skipTarget = null;
        return true;
    },
    hasSkip: () => !!skipTarget,
    skipLabel: () => (skipTarget ? segmentName(skipTarget.l) : ''),
    step(direction) {
        const p = P.media();
        const list = P.current ? P.current.chapters : [];
        if (!list.length) return;
        const i = Math.max(0, index());
        if (direction > 0) { if (i + 1 < list.length) p.currentTime = list[i + 1].s; }
        else if (i > 0 && p.currentTime - list[i].s <= 3) p.currentTime = list[i - 1].s;
        else p.currentTime = list[i].s;
    },
    // By chapter index, for the remote's picker (step moves ±1).
    jump(i) {
        const target = P.current ? P.current.chapters[i] : null;
        if (target) P.media().currentTime = target.s;
    },
};
})();

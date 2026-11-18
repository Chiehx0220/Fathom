// DeArrow: community titles and thumbnails on YouTube cards; looked up by the phone.
(() => {
const YOUTUBE_ID = /^[\w-]{11}$/;

// id → { title?, thumbnailUrl? } | null
const items = FT.phoneLookup(async (entries) => {
    const body = await FT.api.dearrow({ videos: entries.map(([id, duration]) => ({ id, duration })) });
    return { active: body.enabled, answers: body.items };
});

const eligible = (v) => !!v && YOUTUBE_ID.test(v.id || '') && (v.serviceId ?? 0) === 0 && !v.isLive && !v.local;

// Original stays in the tooltip and on the card's item (saves and feedback use it).
const retitle = (el, title) => {
    if (!title || el.textContent === title) return;
    el.title = el.textContent;
    el.textContent = title;
};

FT.dearrow = {
    card(el) {
        if (!eligible(el.item)) return;
        items.get(el.item.id, el.item.duration || 0).then((item) => {
            if (!item) return;
            retitle(FT.$('.card-title', el), item.title);
            const img = FT.$('.thumb img', el);
            if (item.thumbnailUrl && img) {
                const original = img.src;
                img.addEventListener('error', () => { img.src = original; }, { once: true });
                img.src = item.thumbnailUrl;
            }
        });
    },
    title(el, video) {
        if (eligible(video)) items.get(video.id, video.duration || 0).then((item) => item && retitle(el, item.title));
    },
    // Switched here or on the phone: forget answers; what is on screen stays.
    reset: items.reset,
};

FT.events.on('settings', () => FT.dearrow.reset());
})();

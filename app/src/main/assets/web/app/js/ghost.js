// Placeholders from the real builders on ghost items.
(() => {
const h = FT.h;

const lines = (box, count) => box.replaceChildren(...Array.from({ length: count }, () => h('i', { class: 'gh' })));

// Subtree without focus stops, links or screen-reader content.
const quiet = (el) => {
    el.setAttribute('aria-hidden', 'true');
    el.inert = true;
    for (const node of [el, ...el.querySelectorAll('[data-f], [href], [tabindex]')]) {
        node.removeAttribute('href');
        node.removeAttribute('tabindex');
        delete node.dataset.f;
    }
    return el;
};

FT.ghost = {
    quiet,
    // Items that make the builders return placeholders.
    items: (count) => Array.from({ length: count }, (_, i) => ({ ghost: true, url: 'ghost:' + i, title: '', serviceId: 0 })),
    // Picture, badges and text become bars.
    card: (el) => {
        el.classList.add('ghost');
        el.querySelectorAll('img, .dur, .opt, .prog').forEach((node) => node.remove());
        lines(el.querySelector('.card-title'), 2);
        const meta = el.querySelector('.card-meta');
        if (meta) lines(meta, 1);
        return quiet(el);
    },
    line: (cls) => h('i', { class: 'gh ' + (cls || '') }),
};
// Loaded-page body around ghost items.
FT.ghostBody = (wide) => (wide
    ? h('div', {}, h('div', { class: 'grid' }, FT.ghost.items(12).map((v) => FT.videoCard(v))))
    : h('div', { class: 'split' }, h('div', {}, h('div', { class: 'list' }, FT.ghost.items(6).map((v) => FT.videoRow(v)))), h('aside', { class: 'pane' })));

// Watch page: title block, action row, description card, related shelf; `below` follows.
FT.watchSkeleton = (...below) => {
    const tool = (label, icon, extra) => h('button', { type: 'button', class: 'btn tool ' + extra }, FT.icon(icon), h('span', { class: 'lbl' }, label));
    const about = h('div', { class: 'about' }, h('b', {}, FT.ghost.line('short')), h('div', { class: 'about-text' }, FT.ghost.line(), FT.ghost.line('short')));
    return h('div', { class: 'loading skel' },
        quiet(h('div', { class: 'watch-info' },
            h('h1', {}, FT.ghost.line()),
            h('div', { class: 'who-row' },
                h('span', { class: 'who-link' }, h('span', { class: 'avatar' }), h('div', { class: 'grow', style: 'width:9em' }, h('div', { class: 'who' }, FT.ghost.line()))),
                h('span', { class: 'subscribe' }, FT.button(FT.t('subscribe'), null, null, 'primary')), FT.button('', 'more_vert', null, 'round quiet')),
            h('div', { class: 'watch-actions' },
                h('div', { class: 'rate', role: 'group' }, FT.button(FT.t('like'), 'thumb_up', null), FT.button(FT.t('dislike'), 'thumb_down', null, 'round')),
                tool(FT.t('watch_later'), 'schedule', ''), tool(FT.t('playlist'), 'playlist_play', 'tier-mid'), h('span', { class: 'rule' }),
                tool(FT.t('share'), 'share', 'tier-mid compact'), tool(FT.t('audio_only'), 'music_note', 'wide-only compact'),
                tool(FT.t('cast'), 'cast', 'wide-only compact'), tool(FT.t('download'), 'download', 'wide-only compact'),
                tool(FT.t('more'), 'more_vert', 'narrow-only')))),
        quiet(about),
        quiet(FT.shelf(FT.t('related'), FT.t('related_hint'), FT.ghost.items(4).map((v) => FT.videoCard(v)))),
        ...below);
};

// Tail placeholders while a page loads, shown after PENDING_MS: each loaded item cross-fades in its placeholder's slot, surplus ones fade out.
const PENDING_MS = 300;
const FADE_MS = 600;
const fade = (el, cls) => {
    const gone = () => el.remove();
    el.classList.add(cls);
    el.addEventListener('animationend', gone, { once: true });
    setTimeout(gone, FADE_MS);
};
FT.pending = (host, count, rows) => {
    const bones = FT.ghost.items(count).map((item) => (rows ? FT.videoRow(item) : FT.videoCard(item)));
    const leaving = new Set();
    const timer = setTimeout(() => host.append(...bones), PENDING_MS);
    return {
        cancel: () => { clearTimeout(timer); bones.forEach((bone) => { if (!leaving.has(bone)) bone.remove(); }); },
        // `items` already sit in the host, in placeholder order.
        settle: (items) => {
            clearTimeout(timer);
            bones.forEach((bone, i) => {
                leaving.add(bone);
                if (!bone.isConnected) return;
                if (!items[i]) { fade(bone, 'sk-gone'); return; }
                bone.before(items[i]);
                items[i].append(bone);
                fade(bone, 'sk-out');
            });
        },
    };
};
})();

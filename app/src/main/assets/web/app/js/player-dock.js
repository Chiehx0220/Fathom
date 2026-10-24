// Dock state: off | full | mini. P.dock.set is sole writer of P.dockMode.
(() => {
const P = FT.playerCore;
// Shared selector: drag suppression, click dispatch.
const MINI_CTRL = '.mini-ctrl';
let miniPos = null; // {x, y} over CSS default; not persisted
let drag = null; // {pointerId, startX, startY, originX, originY, moved}
let miniDismissed = false; // dismissed from mini on watch page: paused, restored at scroll top

const clampMiniPos = (x, y) => {
    const stage = P.stage();
    return {
        x: Math.max(8, Math.min(innerWidth - stage.offsetWidth - 8, x)),
        y: Math.max(8, Math.min(innerHeight - stage.offsetHeight - 8, y)),
    };
};

const place = () => {
    const stage = P.stage();
    if (!stage.classList.contains('mini') || !miniPos) { stage.style.left = stage.style.top = stage.style.right = stage.style.bottom = ''; return; }
    stage.style.right = stage.style.bottom = 'auto';
    stage.style.left = miniPos.x + 'px';
    stage.style.top = miniPos.y + 'px';
};

const paintPlay = () => {
    const btn = FT.$('#mini-play');
    if (!btn) return;
    const paused = P.media().paused;
    btn.replaceChildren(FT.icon(paused ? 'play_arrow' : 'pause'));
    btn.setAttribute('aria-label', FT.t(paused ? 'play' : 'pause'));
};

// Same-video watch route: hash unchanged, scroll to top.
const expand = () => {
    if (!P.current) return;
    if (FT.route.name === 'watch' && FT.route.params.u === P.current.url) scrollTo({ top: 0, behavior: 'smooth' });
    else location.hash = FT.link.watch({ url: P.current.url, serviceId: P.current.service });
};

const onPointerDown = (e) => {
    const stage = P.stage();
    if (!stage.classList.contains('mini') || e.target.closest(MINI_CTRL)) return;
    const rect = stage.getBoundingClientRect();
    drag = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, originX: rect.left, originY: rect.top, moved: false };
    try { stage.setPointerCapture(e.pointerId); } catch (err) {}
};
const onPointerMove = (e) => {
    if (!drag || drag.pointerId !== e.pointerId) return;
    const dx = e.clientX - drag.startX, dy = e.clientY - drag.startY;
    if (!drag.moved && Math.hypot(dx, dy) < 6) return;
    if (!drag.moved) { drag.moved = true; P.stage().classList.add('dragging'); }
    e.preventDefault();
    miniPos = clampMiniPos(drag.originX + dx, drag.originY + dy);
    place();
};
const onPointerUp = (e) => {
    if (!drag || drag.pointerId !== e.pointerId) return;
    const stage = P.stage();
    try { stage.releasePointerCapture(e.pointerId); } catch (err) {}
    stage.classList.remove('dragging');
    if (!drag.moved) drag = null; // kept: onClick swallows trailing click
};
// Mini tap dispatch: drag swallowed; #mini-play toggle; #mini-close dismiss; else expand.
const onClick = (e) => {
    if (drag && drag.moved) { drag = null; return; }
    if (!P.stage().classList.contains('mini')) return;
    if (e.target.closest('#mini-play')) FT.player.toggle();
    else if (e.target.closest('#mini-close')) FT.player.dismissMini();
    else expand();
};

// Stage height: min(64% viewport, 16:9 of width).
const sizeStage = () => {
    const height = Math.min(innerHeight * 0.64, (innerWidth * 9) / 16);
    document.documentElement.style.setProperty('--stage-h', Math.round(height) + 'px');
};
sizeStage();
addEventListener('resize', FT.frame(() => { sizeStage(); if (miniPos) { miniPos = clampMiniPos(miniPos.x, miniPos.y); place(); } }));

const surface = P.stage();
surface.addEventListener('pointerdown', onPointerDown);
surface.addEventListener('pointermove', onPointerMove);
surface.addEventListener('pointerup', onPointerUp);
surface.addEventListener('pointercancel', onPointerUp);
surface.addEventListener('click', onClick);

FT.playerCore.dock = {
    expand,
    paintPlay,
    set(mode) {
        P.dockMode = P.current ? mode : 'off';
        if (P.dockMode === 'mini' && miniDismissed && FT.route.name !== 'watch') return FT.player.close();
        if (P.dockMode !== 'mini') miniDismissed = false;
        if (P.dockMode !== 'full') P.upNext.cancel();
        const el = P.stage();
        el.hidden = P.dockMode === 'off' || (P.dockMode === 'mini' && miniDismissed);
        el.classList.toggle('mini', P.dockMode === 'mini');
        if (P.tree) P.tree.skin.toggleAttribute('data-mini', P.dockMode === 'mini');
        FT.$$(MINI_CTRL).forEach((btn) => { btn.hidden = P.dockMode !== 'mini'; });
        if (P.dockMode === 'mini') paintPlay();
        place();
    },
    // Watch route: stage stays in place; mini corner emptied, media paused.
    dismiss() {
        if (FT.route.name !== 'watch') return FT.player.close();
        P.media().pause();
        miniDismissed = true;
        P.progress.report();
        P.dock.set('mini');
    },
};
})();

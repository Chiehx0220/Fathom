// The phone remote, on the screen it controls: pairing, receiving the phone's commands, and telling the phone how things are going.
// The pairing code is kept under the same name the classic pages use, so switching between the two interfaces stays connected.
(() => {
const KEY = 'server_play_release_code';
let socket = null;
let reportTimer = 0;
let idleTimer = 0;
let pointerIdleTimer = 0;
let pointerX = innerWidth / 2;
let pointerY = innerHeight / 2;

const code = () => { try { return localStorage.getItem(KEY); } catch (e) { return null; } };
const setCode = (value) => { try { if (value) localStorage.setItem(KEY, value); else localStorage.removeItem(KEY); } catch (e) {} };

// ---- The banner, the pointer and the key hints ----

const HINTS = [['open_with', 'Move'], ['OK', 'Open'], ['menu', 'Options'], ['undo', 'Back']];

const paintState = () => {
    const on = FT.remote.active;
    document.body.classList.toggle('remote-active', on);
    const banner = FT.$('#remote-banner');
    banner.hidden = !on;
    if (on) banner.replaceChildren(FT.icon('cast_connected'), 'Remote connected', FT.h('button', { type: 'button', onclick: () => FT.remote.release() }, 'Disconnect'));
    const hints = FT.$('#keyhints');
    hints.hidden = !on;
    if (on) {
        hints.replaceChildren(...HINTS.map(([key, label]) => FT.h('span', {},
            FT.h('span', { class: 'k' }, key.length > 2 ? FT.icon(key) : key), label)));
    }
    if (!on) { clearTimeout(pointerIdleTimer); FT.$('#vptr').hidden = true; }
    if (FT.renderTop) FT.renderTop();
};

// Idle-fade for the key hints; any command resets the timer.
const wake = () => {
    const hints = FT.$('#keyhints');
    hints.classList.remove('idle');
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => hints.classList.add('idle'), 4000);
};

// The connection banner hides for fullscreen video instead of idle-fading - that is exactly when
// it would sit on top of the picture. isFullscreen() also covers the manual "remote-fs" fallback
// for a browser that refused real fullscreen (see player.js's fullscreen()).
const updateBannerFullscreen = () => {
    FT.$('#remote-banner').classList.toggle('fs-hidden', FT.player.isFullscreen());
};
document.addEventListener('fullscreenchange', updateBannerFullscreen);
new MutationObserver(updateBannerFullscreen).observe(document.body, { attributes: true, attributeFilter: ['class'] });

// Idle-fade for the pointer dot; any pointer activity resets the timer.
const showPointer = () => {
    const p = FT.$('#vptr');
    p.hidden = false;
    p.classList.remove('idle');
    p.style.left = pointerX + 'px';
    p.style.top = pointerY + 'px';
    clearTimeout(pointerIdleTimer);
    pointerIdleTimer = setTimeout(() => p.classList.add('idle'), 2500);
};
const movePointer = (dx, dy) => {
    pointerX = Math.max(0, Math.min(innerWidth, pointerX + dx));
    pointerY = Math.max(0, Math.min(innerHeight, pointerY + dy));
    showPointer();
};
const clickPointer = () => {
    const p = FT.$('#vptr');
    p.classList.add('press');
    setTimeout(() => p.classList.remove('press'), 150);
    const target = document.elementFromPoint(pointerX, pointerY);
    if (target && target !== p) target.click();
};

// ---- Commands from the phone ----

const KEYS = { up: 'up', down: 'down', left: 'left', right: 'right', ok: 'ok', menu: 'menu' };
const PAGES = { home: '#/home', subscriptions: '#/library', history: '#/history' };

const handle = (cmd) => {
    wake();
    if (cmd.startsWith('play_video:')) {
        const url = cmd.slice('play_video:'.length);
        location.hash = FT.link.watch({ url, serviceId: FT.serviceOfUrl(url) });
    } else if (cmd.startsWith('pointer_move:')) {
        const [dx, dy] = cmd.slice('pointer_move:'.length).split(',').map((n) => parseFloat(n) || 0);
        FT.input('pointer');
        movePointer(dx, dy);
    } else if (cmd === 'pointer_click') {
        showPointer();
        clickPointer();
    } else if (cmd.startsWith('pointer_scroll:')) {
        scrollBy({ top: parseFloat(cmd.slice('pointer_scroll:'.length)) || 0, behavior: 'instant' });
    } else if (cmd === 'back') {
        FT.command('back');
    } else if (cmd === 'play_pause') {
        FT.player.toggle();
    } else if (cmd === 'forward') {
        FT.player.seek(10);
    } else if (cmd === 'rewind') {
        FT.player.seek(-10);
    } else if (cmd.startsWith('key:')) {
        const name = KEYS[cmd.slice('key:'.length)];
        if (name) FT.command(name);
    } else if (cmd === 'chapter:next' || cmd === 'chapter:prev') {
        FT.player.stepChapter(cmd === 'chapter:next' ? 1 : -1);
    } else if (cmd.startsWith('chapter:jump:')) {
        const index = parseInt(cmd.slice('chapter:jump:'.length), 10);
        if (!isNaN(index)) FT.player.jumpChapter(index);
    } else if (cmd === 'skip') {
        FT.player.skip();
    } else if (cmd.startsWith('search:')) {
        const query = cmd.slice('search:'.length).trim();
        if (query) location.hash = '#/search?q=' + FT.enc(query);
    } else if (cmd.startsWith('service:')) {
        FT.setService(parseInt(cmd.slice('service:'.length), 10));
    } else if (cmd.startsWith('goto:')) {
        // Named screens only, never an address taken from the command.
        const target = PAGES[cmd.slice('goto:'.length)];
        if (target) location.hash = target;
    } else if (cmd.startsWith('seek_to:')) {
        FT.player.seekTo(parseFloat(cmd.slice('seek_to:'.length)));
    } else if (cmd.startsWith('volume:')) {
        FT.player.volume(parseFloat(cmd.slice('volume:'.length)) || 0);
    } else if (cmd === 'mute') {
        FT.player.mute();
    } else if (cmd === 'fullscreen') {
        FT.player.fullscreen();
    } else if (cmd === 'expand') {
        FT.player.expand();
    }
    // Tell the phone how the page ended up now, not at the next once-a-second report.
    if (!cmd.startsWith('pointer_')) setTimeout(report, 100);
};

// ---- Reporting back to the phone ----

const report = () => {
    const lock = code();
    if (!lock) return;
    const snap = FT.player.snapshot();
    const watching = FT.route.name === 'watch' && snap.open;
    // A video shrunk to the mini player is still playing and still needs its keys, even though the page has moved on.
    const q = new URLSearchParams({
        release_code: lock, watching: watching ? 1 : 0, mini: snap.open && !watching ? 1 : 0, fs: snap.fs ? 1 : 0,
        svc: FT.store.service, svcs: FT.services.map((s) => s.id + ':' + s.name).join(','),
    });
    if (snap.open) {
        q.set('title', snap.title);
        q.set('t', snap.t);
        q.set('d', snap.d);
        q.set('paused', snap.paused ? 1 : 0);
        q.set('vol', snap.vol);
        q.set('muted', snap.muted ? 1 : 0);
        if (snap.chapters.length) { q.set('chapters', JSON.stringify(snap.chapters)); q.set('chapi', snap.chapi); }
        if (snap.skip) q.set('skip', snap.skip);
    }
    fetch('/remote-state?' + q).then((res) => res.json()).then((body) => {
        // The phone no longer recognises this lock (released there): behave as if disconnected here too.
        if (body.status === 'error') FT.remote.end();
    }).catch(() => {});
};

const connect = () => {
    if (socket) return;
    socket = new WebSocket('ws://' + location.hostname + ':8081');
    socket.onmessage = (event) => handle(String(event.data));
    socket.onclose = () => {
        socket = null;
        // The server may have restarted: try again while we still hold a lock.
        setTimeout(() => { if (code()) connect(); }, 1500);
    };
    clearInterval(reportTimer);
    reportTimer = setInterval(report, 1000);
};

Object.assign(FT.remote, {
    // Pairs with the phone (or, if already paired, does nothing new).
    async pair() {
        try {
            const res = await fetch('/send-link?' + new URLSearchParams({ id: '__connect_only__', release_code: code() || '', title: document.title }));
            const body = await res.json();
            if (body.status === 'success') {
                setCode(body.release_code);
                FT.remote.active = true;
                paintState();
                connect();
                wake();
                FT.toast('Remote connected. Open the remote on your phone.');
            } else if (body.status === 'busy') {
                FT.toast(body.message || 'Another device is connected');
            } else {
                FT.toast(body.message || 'Could not connect');
            }
        } catch (e) {
            FT.toast('Could not reach the phone');
        }
    },

    toggle() { if (FT.remote.active) FT.remote.release(); else FT.remote.pair(); },

    // Ends the session here and tells the phone.
    async release() {
        const lock = code();
        if (lock) { try { await fetch('/release-lock?release_code=' + FT.enc(lock)); } catch (e) {} }
        FT.remote.end();
    },

    // Forget the lock, hide everything that shows a connection, stop reporting.
    end() {
        setCode(null);
        FT.remote.active = false;
        clearInterval(reportTimer);
        reportTimer = 0;
        clearTimeout(idleTimer);
        if (socket) { const s = socket; socket = null; s.onclose = null; s.close(); }
        paintState();
    },

    // On page load: pick the session back up if this browser still holds a lock.
    resume() {
        if (!code()) return;
        FT.remote.active = true;
        paintState();
        connect();
        wake();
    },
});
})();

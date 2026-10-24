// Phone-remote endpoint: pairing, commands (WebSocket), state reports (HTTP). Pairing code per tab (sessionStorage); new tab takes over.
(() => {
const KEY = 'server_play_release_code';
// Explicit disconnect; suppresses auto-pair until a manual connect.
const OPT_OUT = 'fathom_remote_opt_out';
let socket = null;
let pairing = false;
let reportTimer = 0;
let idleTimer = 0;
let pointerIdleTimer = 0;
let pointerX = innerWidth / 2;
let pointerY = innerHeight / 2;

const code = () => { try { return sessionStorage.getItem(KEY); } catch (e) { return null; } };
const setCode = (value) => { try { if (value) sessionStorage.setItem(KEY, value); else sessionStorage.removeItem(KEY); } catch (e) {} };
const optedOut = () => { try { return localStorage.getItem(OPT_OUT) === '1'; } catch (e) { return false; } };
const setOptOut = (value) => { try { if (value) localStorage.setItem(OPT_OUT, '1'); else localStorage.removeItem(OPT_OUT); } catch (e) {} };

// ---- Pointer and key hints ----

const HINTS = [['open_with', FT.t('hint_move')], ['OK', FT.t('open')], ['menu', FT.t('options')], ['undo', FT.t('hint_back')]];

const paintState = () => {
    const on = FT.remote.active;
    document.body.classList.toggle('remote-active', on);
    const hints = FT.$('#keyhints');
    hints.hidden = !on;
    if (on) {
        hints.replaceChildren(...HINTS.map(([key, label]) => FT.h('span', {},
            FT.h('span', { class: 'k' }, key.length > 2 ? FT.icon(key) : key), label)));
    }
    if (!on) { clearTimeout(pointerIdleTimer); FT.$('#vptr').hidden = true; }
};

// Idle-fade for the key hints; any command resets the timer.
const wake = () => {
    const hints = FT.$('#keyhints');
    hints.classList.remove('idle');
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => hints.classList.add('idle'), 4000);
};

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
const PAGES = { home: '#/home', subscriptions: '#/subscriptions', library: '#/library', history: '#/library?tab=history' };

const handle = (cmd) => {
    wake();
    if (cmd.startsWith('play_video_at:')) {
        // play_video_at:<seconds>:<url>
        const rest = cmd.slice('play_video_at:'.length);
        const split = rest.indexOf(':');
        const url = rest.slice(split + 1);
        const at = { url, sec: parseFloat(rest.slice(0, split)) || 0 };
        if (FT.player.current && FT.player.current.url === url) {
            FT.player.seekTo(at.sec);
            if (FT.player.snapshot().paused) FT.player.toggle();
        } else {
            FT.startAt = at;
            setTimeout(() => { if (FT.startAt === at) FT.startAt = null; }, 15000);
            location.hash = FT.link.watch({ url, serviceId: FT.serviceOfUrl(url) });
        }
    } else if (cmd.startsWith('play_video:')) {
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
        // Whitelisted routes only.
        const target = PAGES[cmd.slice('goto:'.length)];
        if (target) location.hash = target;
    } else if (cmd.startsWith('seek_to:')) {
        FT.player.seekTo(parseFloat(cmd.slice('seek_to:'.length)));
    } else if (cmd.startsWith('volume:')) {
        FT.player.volume(parseFloat(cmd.slice('volume:'.length)) || 0);
    } else if (cmd.startsWith('quality:')) {
        FT.player.setQuality(cmd.slice('quality:'.length));
    } else if (cmd.startsWith('rate:')) {
        FT.player.setRate(cmd.slice('rate:'.length));
    } else if (cmd.startsWith('danmaku:')) {
        FT.player.setDanmaku(cmd.endsWith(':1'));
    } else if (cmd === 'like' || cmd === 'dislike') {
        if (FT.rating) FT.rating.toggle(cmd);
    } else if (cmd === 'mute') {
        FT.player.mute();
    } else if (cmd === 'fullscreen') {
        FT.player.fullscreen();
    } else if (cmd === 'expand') {
        FT.player.expand();
    }
    // Report immediately instead of waiting for the periodic report.
    if (!cmd.startsWith('pointer_')) setTimeout(report, 100);
};

// ---- Reporting back to the phone ----

const report = () => {
    const lock = code();
    if (!lock) return;
    const snap = FT.player.snapshot();
    const watching = FT.route.name === 'watch' && snap.open;
    // A mini-player video is still controllable.
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
        q.set('rate', snap.rate);
        if (FT.rating) q.set('like', FT.rating.state());
        if (snap.danmaku !== null) q.set('danmaku', snap.danmaku ? 1 : 0);
        if (snap.qualities.length) {
            q.set('qualities', JSON.stringify(snap.qualities));
            q.set('quality', snap.quality);
            q.set('qchosen', snap.qchosen);
        }
    }
    fetch('/remote-state?' + q).then((res) => res.json()).then((body) => {
        // Lock gone on the phone: end here; next wake re-pairs.
        if (body.status === 'error') FT.remote.end();
    }).catch(() => {});
};

// Report cadence: 1 s playing, 5 s idle, 8 s hidden (< PAGE_ALIVE_MS).
const reportDelay = () => {
    const snap = FT.player.snapshot();
    if (snap.open && !snap.paused) return 1000;
    return document.hidden ? 8000 : 5000;
};
const tick = () => {
    report();
    reportTimer = setTimeout(tick, reportDelay());
};
const restartReports = () => {
    clearTimeout(reportTimer);
    tick();
};

const connect = () => {
    if (socket) return;
    socket = new WebSocket('ws://' + location.hostname + ':8081');
    socket.onmessage = (event) => handle(String(event.data));
    socket.onclose = (event) => {
        socket = null;
        if (event.reason === 'taken_over') {
            FT.remote.end();
            FT.toast(FT.t('remote_taken_over'));
            return;
        }
        // Server may have restarted: retry while the lock is held.
        setTimeout(() => { if (code()) connect(); }, 1500);
    };
    restartReports();
};

Object.assign(FT.remote, {
    // takeover: replace the lock holder; otherwise busy.
    async pair({ silent = false, takeover = !silent } = {}) {
        if (!silent) setOptOut(false);
        if (pairing) return;
        pairing = true;
        try {
            const res = await fetch('/send-link?' + new URLSearchParams({ id: '__connect_only__', release_code: code() || '', title: document.title, takeover: takeover ? 1 : 0 }));
            const body = await res.json();
            if (body.status === 'success') {
                const fresh = !FT.remote.active;
                setCode(body.release_code);
                FT.remote.active = true;
                if (fresh) { paintState(); wake(); }
                connect();
                if (!silent) FT.toast(FT.t('remote_connected_toast'));
            } else if (body.status === 'busy') {
                // Busy: drop the local session.
                if (FT.remote.active) FT.remote.end();
                if (!silent) FT.toast(body.message || FT.t('remote_other_device'));
            } else if (!silent) {
                FT.toast(body.message || FT.t('remote_could_not_connect'));
            }
        } catch (e) {
            if (!silent) FT.toast(FT.t('remote_unreachable'));
        } finally {
            pairing = false;
        }
    },

    toggle() { if (FT.remote.active) FT.remote.release(); else FT.remote.pair(); },

    // Ends the session here and tells the phone.
    async release() {
        setOptOut(true);
        const lock = code();
        if (lock) { try { await fetch('/release-lock?release_code=' + FT.enc(lock)); } catch (e) {} }
        FT.remote.end();
    },

    // Forget the lock, hide everything that shows a connection, stop reporting.
    end() {
        setCode(null);
        FT.remote.active = false;
        clearTimeout(reportTimer);
        reportTimer = 0;
        clearTimeout(idleTimer);
        if (socket) { const s = socket; socket = null; s.onclose = null; s.close(); }
        paintState();
    },

    // Pairs unless opted out or on the phone.
    auto({ takeover = false } = {}) {
        if (optedOut() || FT.onPhone()) return;
        FT.remote.pair({ silent: true, takeover });
    },

    // Load: pair with takeover (newest page wins).
    resume() {
        if (code()) {
            FT.remote.active = true;
            paintState();
            wake();
            // No auto-pair: reconnect the socket for the held lock.
            if (optedOut() || FT.onPhone()) connect();
        }
        FT.remote.auto({ takeover: true });
    },
});

// Wake: reclaim a released lock; no-op while held.
document.addEventListener('visibilitychange', () => {
    if (document.hidden) return;
    FT.remote.auto();
    if (FT.remote.active) restartReports();
});
addEventListener('online', () => FT.remote.auto());
addEventListener('hashchange', () => { if (FT.remote.active) setTimeout(report, 300); });
})();

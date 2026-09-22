// Remote control session on the TV/computer side: pairing, the command receiver, and reporting playback back to the phone.

function updateTVLockBanner() {
    const banner = document.getElementById('tv-lock-banner');
    if (!banner) return;
    const code = localStorage.getItem('server_play_release_code');
    if (code) {
        banner.hidden = false;
        document.body.classList.add('has-banner', 'remote-active');
    } else {
        banner.hidden = true;
        document.body.classList.remove('has-banner', 'remote-active');
    }
}

function playOnTV(videoUrl, title) {
    const isVideoPage = location.pathname.startsWith('/watch') || location.pathname.startsWith('/audio');
    const idToSend = isVideoPage ? videoUrl : '__connect_only__';
    const code = localStorage.getItem('server_play_release_code') || '';
    const url = '/send-link?id=' + encodeURIComponent(idToSend) + 
                '&release_code=' + encodeURIComponent(code) +
                '&title=' + encodeURIComponent(title);
    
    fetch(url)
        .then(res => res.json())
        .then(data => {
            if (data.status === 'success') {
                localStorage.setItem('server_play_release_code', data.release_code);
                updateTVLockBanner();
                startCommandPolling();
                alert('Successfully connected remote!');
            } else if (data.status === 'busy') {
                alert('Server is busy: ' + data.message);
            } else {
                alert('Error casting video: ' + (data.message || 'Unknown error'));
            }
        })
        .catch(err => {
            alert('Connection error: ' + err);
        });
}

// The source the page is showing, read from its own switcher (falls back to YouTube).
function currentServiceId() {
    const current = document.querySelector('.service-switcher a.active');
    return (current && new URL(current.href, location.href).searchParams.get('serviceId')) || '0';
}

function startCommandPolling() {
    if (window.wsConnection) return;
    
    const remotePlayer = () => {
        const media = document.getElementById('player') || document.getElementById('audio-player');
        return media && media.isConnected ? media : null;
    };
    // Either the browser's own fullscreen or the fill-the-window fallback (body.remote-fs).
    const isRemoteFullscreen = () => {
        const media = remotePlayer();
        return document.body.classList.contains('remote-fs') || !!document.fullscreenElement || !!document.webkitFullscreenElement
            || !!(media && media.state && media.state.fullscreen);
    };
    const leaveRemoteFullscreen = () => {
        document.body.classList.remove('remote-fs');
        if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(() => {});
        else if (document.webkitFullscreenElement && document.webkitExitFullscreen) document.webkitExitFullscreen();
        const media = remotePlayer();
        try {
            const left = media && typeof media.exitFullscreen === 'function' ? media.exitFullscreen() : null;
            if (left && left.catch) left.catch(() => {});
        } catch (e) {}
    };
    
    // Virtual cursor state
    let vptrX = window.innerWidth / 2, vptrY = window.innerHeight / 2;
    const vptr = document.getElementById('vptr');
    function showVptr() {
        if (vptr) { vptr.hidden = false; vptr.style.left = vptrX + 'px'; vptr.style.top = vptrY + 'px'; }
    }
    function moveVptr(dx, dy) {
        vptrX = Math.max(0, Math.min(window.innerWidth, vptrX + dx));
        vptrY = Math.max(0, Math.min(window.innerHeight, vptrY + dy));
        if (vptr) { vptr.style.left = vptrX + 'px'; vptr.style.top = vptrY + 'px'; }
        const el = document.elementFromPoint(vptrX, vptrY);
        if (el) {
            const isOverPlayer = el.closest('#video-container') !== null;
            const controls = document.getElementById('video-controls');
            if (controls) {
                controls.style.opacity = isOverPlayer ? '1' : '0';
                controls.style.pointerEvents = isOverPlayer ? 'auto' : 'none';
            }
        }
    }
    function clickVptr() {
        if (vptr) vptr.classList.add('press');
        setTimeout(() => { if (vptr) vptr.classList.remove('press'); }, 150);
        const el = document.elementFromPoint(vptrX, vptrY);
        if (!el || el === vptr) return;
        el.click();
        const anchor = el.tagName === 'A' ? el : el.closest('a');
        if (anchor && anchor.href && !anchor.href.startsWith('javascript')) {
            window.location.href = anchor.href;
        }
    }
    
    // A video sent from the phone plays in place: the page's content is swapped for the watch page, loaded the way any region is.
    window.playVideoSPA = function(url) {
        const service = new URLSearchParams(window.location.search).get('serviceId') || '0';
        const container = document.querySelector('.container');
        const watchUrl = '/watch?serviceId=' + encodeURIComponent(service) + '&id=' + encodeURIComponent(url);
        if (!container) {
            window.location.href = watchUrl;
            return;
        }
        history.pushState(null, '', watchUrl);
        const page = document.createElement('main');
        page.className = 'container';
        const region = document.createElement('div');
        region.className = 'async';
        region.dataset.fill = '/watch-content?serviceId=' + encodeURIComponent(service) + '&id=' + encodeURIComponent(url);
        region.innerHTML = '<div class="loading"><div class="spinner"></div><div class="loading-text">Loading video streams...</div></div>';
        page.appendChild(region);
        container.replaceWith(page);
        hydrateRegions(page);
    };

    window.wsConnection = new WebSocket('ws://' + location.hostname + ':8081');
    window.wsConnection.onmessage = function(event) {
        const cmd = event.data;
        if (cmd.startsWith('play_video:')) {
            const url = cmd.substring('play_video:'.length);
            window.playVideoSPA(url);
        } else if (cmd.startsWith('pointer_move:')) {
            const parts = cmd.substring('pointer_move:'.length).split(',');
            const dx = parseFloat(parts[0]) || 0;
            const dy = parseFloat(parts[1]) || 0;
            showVptr();
            moveVptr(dx, dy);
        } else if (cmd === 'pointer_click') {
            showVptr();
            clickVptr();
        } else if (cmd.startsWith('pointer_scroll:')) {
            const dy = parseFloat(cmd.substring('pointer_scroll:'.length)) || 0;
            window.scrollBy({ top: dy, behavior: 'smooth' });
        } else if (cmd === 'back') {
            if (window.remoteMenu && window.remoteMenu.isOpen()) window.remoteMenu.close();
            else if (isRemoteFullscreen()) leaveRemoteFullscreen();
            else if (window.history.length > 1) window.history.back();
        } else if (cmd === 'play_pause') {
            if (window.videoPlayer) {
                if (window.videoPlayer.paused) {
                    window.videoPlayer.play().catch(e => {});
                } else {
                    window.videoPlayer.pause();
                }
            } else {
                const media = document.getElementById('player') || document.getElementById('audio-player');
                if (media) {
                    if (media.readyState < 2) {
                        const loader = document.getElementById('video-loader');
                        if (loader) loader.style.display = 'block';
                        media.play().catch(e => {});
                    } else {
                        if (media.paused) media.play().catch(e => {}); else media.pause();
                    }
                }
            }
        } else if (cmd === 'forward') {
            if (typeof window.seekVideo === 'function') window.seekVideo(10);
            else {
                const media = document.getElementById('player') || document.getElementById('audio-player');
                if (media) media.currentTime += 10;
            }
        } else if (cmd === 'rewind') {
            if (typeof window.seekVideo === 'function') window.seekVideo(-10);
            else {
                const media = document.getElementById('player') || document.getElementById('audio-player');
                if (media) media.currentTime -= 10;
            }
        } else if (cmd.startsWith('key:')) {
            // The phone's direction pad: replayed as the arrow/Enter keys the page already navigates with.
            const keys = { up: 'ArrowUp', down: 'ArrowDown', left: 'ArrowLeft', right: 'ArrowRight', ok: 'Enter', menu: 'ContextMenu' };
            const key = keys[cmd.substring('key:'.length)];
            if (key) document.dispatchEvent(new KeyboardEvent('keydown', { key: key, bubbles: true, cancelable: true }));
        } else if (cmd === 'chapter:next' || cmd === 'chapter:prev') {
            if (window.remoteChapters) window.remoteChapters.step(cmd === 'chapter:next' ? 1 : -1);
        } else if (cmd.startsWith('search:')) {
            // Typed on the phone: run it as a search in the source the page is on.
            const query = cmd.substring('search:'.length).trim();
            if (query) window.location.href = '/search?serviceId=' + encodeURIComponent(currentServiceId()) + '&q=' + encodeURIComponent(query);
        } else if (cmd.startsWith('service:')) {
            // The phone's YouTube / Bilibili switch: follows the page's own switcher link for that service.
            const wanted = cmd.substring('service:'.length);
            const link = Array.from(document.querySelectorAll('.service-switcher a')).find(a => new URL(a.href, location.href).searchParams.get('serviceId') === wanted);
            if (link) window.location.href = link.href;
        } else if (cmd.startsWith('goto:')) {
            // The phone's shortcut keys: named pages only, never an address from the command.
            // The page stays in the source (YouTube or Bilibili) it is showing now.
            const pages = { home: '/', subscriptions: '/subscriptions', history: '/history' };
            const target = pages[cmd.substring('goto:'.length)];
            if (target) window.location.href = target + '?serviceId=' + encodeURIComponent(currentServiceId());
        } else if (cmd.startsWith('seek_to:')) {
            const media = remotePlayer();
            const sec = parseFloat(cmd.substring('seek_to:'.length));
            if (media && isFinite(sec)) media.currentTime = Math.max(0, sec);
        } else if (cmd.startsWith('volume:')) {
            const media = remotePlayer();
            const delta = parseFloat(cmd.substring('volume:'.length)) || 0;
            if (media) { media.muted = false; media.volume = Math.max(0, Math.min(1, (media.volume || 0) + delta)); }
        } else if (cmd === 'mute') {
            const media = remotePlayer();
            if (media) media.muted = !media.muted;
        } else if (cmd === 'fullscreen') {
            const media = remotePlayer();
            if (!media) return;
            if (isRemoteFullscreen()) {
                leaveRemoteFullscreen();
            } else {
                // Browsers only grant real fullscreen right after a tap on the page itself, which a remote never makes; if it is refused, fill the window instead.
                try {
                    const asked = typeof media.enterFullscreen === 'function' ? media.enterFullscreen() : null;
                    if (asked && asked.catch) asked.catch(() => {});
                } catch (e) {}
                setTimeout(() => { if (!isRemoteFullscreen()) document.body.classList.add('remote-fs'); }, 300);
            }
        }
    };
    // Tell the server how playback is going, so the phone can show progress and switch between browsing and playing.
    if (window.remoteStateTimer) clearInterval(window.remoteStateTimer);
    window.remoteStateTimer = setInterval(() => {
        const code = localStorage.getItem('server_play_release_code');
        if (!code) return;
        const media = remotePlayer();
        if (!media) document.body.classList.remove('remote-fs');
        let query = 'release_code=' + encodeURIComponent(code) + '&watching=' + (media ? 1 : 0) + '&fs=' + (isRemoteFullscreen() ? 1 : 0);
        const links = Array.from(document.querySelectorAll('.service-switcher a'));
        const serviceOf = (a) => new URL(a.href, location.href).searchParams.get('serviceId');
        const current = links.find(a => a.classList.contains('active'));
        query += '&svc=' + (current ? serviceOf(current) : '') + '&svcs=' + encodeURIComponent(links.map(a => serviceOf(a) + ':' + a.textContent.trim()).join(','));
        if (media) {
            query += '&title=' + encodeURIComponent(media.getAttribute('title') || document.title || '')
                + '&t=' + (media.currentTime || 0) + '&d=' + (media.duration || 0)
                + '&paused=' + (media.paused ? 1 : 0) + '&vol=' + (media.volume == null ? 1 : media.volume)
                + '&muted=' + (media.muted ? 1 : 0);
            const chapters = window.remoteChapters;
            if (chapters && chapters.items.length > 0) {
                query += '&chap=' + encodeURIComponent(chapters.items[chapters.index()].t) + '&chapn=' + chapters.items.length;
            }
        }
        fetch('/remote-state?' + query).then(res => res.json()).then(data => {
            // The server no longer recognises this page's lock (released from the phone): behave as if disconnected here too.
            if (data.status === 'error') endRemoteSession();
        }).catch(() => {});
    }, 1000);
    window.wsConnection.onclose = function() {
        window.wsConnection = null;
        setTimeout(() => {
            if (localStorage.getItem('server_play_release_code')) {
                startCommandPolling();
            }
        }, 1000);
    };
}

// Everything that ends a remote session on this page: forget the lock, hide the banner and pointer, stop reporting.
function endRemoteSession() {
    localStorage.removeItem('server_play_release_code');
    updateTVLockBanner();
    const pointer = document.getElementById('vptr');
    if (pointer) pointer.hidden = true;
    if (window.remoteStateTimer) {
        clearInterval(window.remoteStateTimer);
        window.remoteStateTimer = null;
    }
    if (window.wsConnection) {
        window.wsConnection.close();
        window.wsConnection = null;
    }
}

function releaseTVLock() {
    const code = localStorage.getItem('server_play_release_code');
    if (!code) return;
    
    fetch('/release-lock?release_code=' + encodeURIComponent(code))
        .then(res => res.json())
        .then(data => endRemoteSession())
        .catch(err => {
            endRemoteSession();
            alert('Connection error/released locally: ' + err);
        });
}

document.addEventListener('DOMContentLoaded', () => {
    updateTVLockBanner();

    // Resume command polling if we are currently connected/locked
    if (localStorage.getItem('server_play_release_code')) {
        startCommandPolling();
    }
});

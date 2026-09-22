// Everything a viewer can press that changes something. The markup declares it (`data-action="..."` plus the data it
// needs), and this is the only place it is handled: no page wires a handler inline.

// A two-state button (subscribed, saved, blocked): flip it at once, ask the server, and flip back if the request fails.
function flipToggle(button, request) {
    const wasOn = button.classList.contains('is-on');
    button.classList.toggle('is-on', !wasOn);
    fetch(request(wasOn)).catch(() => button.classList.toggle('is-on', wasOn));
}

// Like and dislike are one pill: liking clears a dislike and the other way round; pressing the lit one clears it.
function rateVideo(button, kind) {
    const pill = button.closest('.like-dislike-pill');
    const like = pill.querySelector('.like-btn');
    const dislike = pill.querySelector('.dislike-btn');
    const wasLiked = !!like && like.classList.contains('active');
    const wasDisliked = !!dislike && dislike.classList.contains('active');
    const wasActive = button.classList.contains('active');
    const show = (liked, disliked) => {
        if (like) like.classList.toggle('active', liked);
        if (dislike) dislike.classList.toggle('active', disliked);
    };
    show(!wasActive && kind === 'like', !wasActive && kind === 'dislike');
    const d = button.dataset;
    fetch('/rate_video?action=' + (wasActive ? 'remove' : kind) + sharedVideoParamsQs(d.url, d.title, d.uploader, d.thumbnail, d.uploaderUrl) + '&back=ajax')
        .catch(() => show(wasLiked, wasDisliked));
}

function updateHistorySelectCount() {
    const count = document.querySelectorAll('.card-select-checkbox:checked').length;
    const label = document.getElementById('history-select-count');
    if (label) label.textContent = count + ' selected';
}

const ACTIONS = {
    subscribe(button) {
        const d = button.dataset;
        flipToggle(button, (wasOn) => '/subscribe?action=' + (wasOn ? 'unsubscribe' : 'subscribe') +
            '&id=' + encodeURIComponent(d.id) + '&name=' + encodeURIComponent(d.name) + '&avatar=' + encodeURIComponent(d.avatar) + '&back=ajax');
    },

    block(button) {
        flipToggle(button, (wasOn) => '/block_channel?action=' + (wasOn ? 'unblock' : 'block') + '&id=' + encodeURIComponent(button.dataset.id) + '&back=ajax');
    },

    'watch-later'(button) {
        const d = button.dataset;
        flipToggle(button, (wasOn) => '/watch_later_action?action=' + (wasOn ? 'remove' : 'add') +
            sharedVideoParamsQs(d.url, d.title, d.uploader, d.thumbnail, d.uploaderUrl) + '&type=video&serviceId=' + d.service + '&back=ajax');
    },

    like(button) { rateVideo(button, 'like'); },
    dislike(button) { rateVideo(button, 'dislike'); },

    // "Load more": the response replaces the whole button row, and carries the next one.
    'load-more'(button) {
        const row = button.closest('.pagination');
        button.disabled = true;
        button.textContent = 'Loading...';
        fetchText(button.dataset.url,
            (html) => { if (row) row.outerHTML = html; },
            () => { button.disabled = false; button.textContent = 'Failed to load. Tap to retry'; });
    },

    share(button) { openShareModal(button.dataset.url, button.dataset.title); },
    'share-close'() { closeShareModal(); },
    'share-copy'() { copyShareLink(); },

    // The watch page: jump to a chapter, float the video in a small window, expand a comment's replies.
    'seek-chapter'(item) { seekToChapter(parseFloat(item.dataset.seconds)); },

    popup() {
        if (window.NewPipeApp && window.NewPipeApp.enterPip) {
            window.NewPipeApp.enterPip();
        } else if (document.pictureInPictureEnabled && document.querySelector('video')) {
            document.querySelector('video').requestPictureInPicture();
        }
    },

    'toggle-replies'(button) {
        const replies = button.nextElementSibling;
        const open = button.classList.toggle('expanded');
        replies.classList.toggle('expanded', open);
        if (!open || replies.dataset.loaded === '1') return;
        fetchText(button.dataset.url,
            (html) => { replies.innerHTML = html; replies.dataset.loaded = '1'; },
            () => { replies.innerHTML = '<div class="notice notice-error">Failed to load replies.</div>'; });
    },

    'connect-remote'() { playOnTV(window.location.href, document.title); },
    'disconnect-remote'() { releaseTVLock(); },

    'history-remove'(button) {
        button.disabled = true;
        fetch('/history_action?action=remove&url=' + encodeURIComponent(button.dataset.url) + '&serviceId=' + button.dataset.service + '&back=ajax')
            .then(() => {
                const card = button.closest('.card');
                if (!card) return;
                card.classList.add('is-removing');
                setTimeout(() => card.remove(), 300);
            })
            .catch(() => { button.disabled = false; });
    },

    'history-select-mode'() {
        const on = document.body.classList.toggle('history-select-mode');
        const bar = document.getElementById('history-select-bar');
        if (bar) bar.hidden = !on;
        if (!on) {
            document.querySelectorAll('.card-select-checkbox').forEach((box) => { box.checked = false; });
            const all = document.getElementById('history-select-all');
            if (all) all.checked = false;
        }
        updateHistorySelectCount();
    },

    'history-delete-selected'(button) {
        const chosen = Array.from(document.querySelectorAll('.card-select-checkbox:checked'));
        if (chosen.length === 0 || !confirm('Delete ' + chosen.length + ' selected item(s) from history?')) return;
        const service = button.dataset.service;
        Promise.all(chosen.map((box) => fetch('/history_action?action=remove&url=' + encodeURIComponent(box.dataset.url) + '&serviceId=' + service + '&back=ajax')))
            .then(() => location.reload(), () => location.reload());
    },
};

document.addEventListener('click', (e) => {
    const control = e.target.closest && e.target.closest('[data-action]');
    if (!control) return;
    const handler = ACTIONS[control.dataset.action];
    if (!handler) return;
    // Links keep an href for when scripts are off; with scripts on, the action replaces the navigation.
    e.preventDefault();
    handler(control, e);
});

document.addEventListener('change', (e) => {
    const target = e.target;
    if (target.id === 'history-select-all') {
        document.querySelectorAll('.card-select-checkbox').forEach((box) => { box.checked = target.checked; });
        updateHistorySelectCount();
    } else if (target.classList && target.classList.contains('card-select-checkbox')) {
        updateHistorySelectCount();
    }
});

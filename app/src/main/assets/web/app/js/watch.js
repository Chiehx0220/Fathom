// The watch screen. The picture is the persistent player, docked over the space this screen leaves at the top; below it are the
// video's details, chapters, related videos and comments. With a remote, the keys start on the player (left and right seek,
// OK pauses) and Down moves into the details; Up from the first row returns to the player.
(() => {
const h = FT.h;
let observer = null;
let downloadTimer = 0;
const mode = { onPlayer: true };
FT.watchMode = mode;

const comment = (c, service, url) => {
    const box = h('div', { class: 'comment', tabindex: '-1', data: { f: '', key: 'c:' + c.id } },
        FT.avatar(c.author, c.authorThumbnail),
        h('div', { class: 'comment-body' },
            h('div', { class: 'comment-head' }, h('b', {}, c.author), h('span', {}, c.publishedTime), c.isPinned ? FT.icon('push_pin') : null),
            h('div', { class: 'comment-text' }, FT.description(c.text, c.textType)),
            h('div', { class: 'comment-foot' },
                c.likeCount > 0 ? h('span', {}, FT.icon('thumb_up'), ' ' + FT.count(c.likeCount)) : null,
                c.replyCount > 0 && c.continuationToken ? h('span', {}, FT.icon('expand_more'), ' ' + c.replyCount + (c.replyCount === 1 ? ' reply' : ' replies')) : null)));
    if (c.replyCount > 0 && c.continuationToken) {
        let opened = false;
        const replies = h('div', { class: 'replies' });
        box.querySelector('.comment-body').append(replies);
        box.addEventListener('click', async () => {
            opened = !opened;
            replies.hidden = !opened;
            if (opened && !replies.childElementCount) {
                try {
                    const body = await FT.api.comments(service, url, c.continuationToken);
                    replies.replaceChildren(...(body.comments || []).map((r) => comment(r, service, url)));
                } catch (e) { FT.toast('Could not load replies'); opened = false; replies.hidden = true; }
            }
        });
    }
    return box;
};

const loadComments = async (holder, service, url, isCurrent) => {
    let cursor = null;
    const list = h('div', {});
    const more = h('div', { class: 'more', hidden: true });
    const page = async () => {
        const body = await FT.api.comments(service, url, cursor);
        if (!isCurrent()) return;
        if (body.commentsDisabled) { holder.replaceChildren(h('h2', {}, 'Comments'), h('p', { class: 'card-meta' }, 'Comments are turned off for this video.')); return; }
        list.append(...(body.comments || []).map((c) => comment(c, service, url)));
        cursor = body.nextPage || null;
        more.hidden = !cursor;
    };
    more.append(FT.button('Load more comments', 'expand_more', () => page().catch(() => FT.toast('Could not load more')), '', 'more-comments'));
    try {
        await page();
        if (isCurrent() && !holder.querySelector('p')) holder.replaceChildren(h('h2', {}, 'Comments'), list, more);
    } catch (e) {
        if (isCurrent()) holder.replaceChildren(h('h2', {}, 'Comments'), h('p', { class: 'card-meta' }, 'Could not load comments.'));
    }
};

// Download button: the phone is the source of truth; the button polls while a download runs.
const downloadButton = (service, info) => {
    let state = 'none';
    const label = h('span', {}, 'Download');
    const icon = h('span', {});
    const btn = h('button', { type: 'button', class: 'btn', data: { f: '', key: 'download', first: '1' } }, icon, label);
    const paint = (s) => {
        state = s.state;
        btn.classList.toggle('primary', state === 'completed');
        const map = {
            pending: ['downloading', 'Downloading ' + (s.progress || 0) + '%'], downloading: ['downloading', 'Downloading ' + (s.progress || 0) + '%'],
            paused: ['pause_circle', 'Paused ' + (s.progress || 0) + '%'], completed: ['download_done', 'Downloaded'], failed: ['error', 'Retry download'],
        };
        const [ic, text] = map[state] || ['download', 'Download'];
        icon.replaceChildren(FT.icon(ic));
        label.textContent = text;
        const active = state === 'pending' || state === 'downloading' || state === 'paused';
        if (active && !downloadTimer) downloadTimer = setInterval(refresh, 2000);
        if (!active && downloadTimer) { clearInterval(downloadTimer); downloadTimer = 0; }
    };
    const call = (action) => FT.api.download(action, service, info.url);
    const refresh = () => call('status').then(paint).catch(() => {});
    btn.addEventListener('click', () => {
        const action = state === 'none' || state === 'failed' ? 'start' : state === 'completed' ? 'delete' : 'cancel';
        if (action === 'delete' && !confirm('Delete the downloaded file?')) return;
        call(action).then(paint).catch((e) => FT.toast(e.message || 'Download failed'));
    });
    paint({ state: 'none' });
    refresh();
    return btn;
};

const actionRow = (service, info) => {
    const video = { url: info.url, title: info.title, channelName: info.channelName, thumbnailUrl: info.thumbnailUrl, channelId: info.channelId, serviceId: service };
    const channel = { id: info.channelId, name: info.channelName, thumbnailUrl: info.channelThumbnailUrl };
    const st = { subscribed: false, later: false, like: null };

    const subscribe = FT.button('Subscribe', 'notifications', null, 'primary', 'subscribe');
    const like = FT.button(info.likeCount > 0 ? FT.count(info.likeCount) : 'Like', 'thumb_up', null, '', 'like');
    const dislike = FT.button('Dislike', 'thumb_down', null, 'round', 'dislike');
    const later = FT.button('Watch later', 'schedule', null, '', 'later');
    const paint = () => {
        subscribe.replaceChildren(FT.icon(st.subscribed ? 'notifications_active' : 'notifications'), st.subscribed ? 'Subscribed' : 'Subscribe');
        subscribe.classList.toggle('primary', !st.subscribed);
        like.classList.toggle('on', st.like === 'LIKED' || st.like === 'like');
        dislike.classList.toggle('on', st.like === 'DISLIKED' || st.like === 'dislike');
        later.replaceChildren(FT.icon(st.later ? 'bookmark_added' : 'schedule'), st.later ? 'Saved' : 'Watch later');
        later.classList.toggle('on', st.later);
    };
    const flip = (key, request) => { const was = st[key]; st[key] = !was; paint(); request(!was).catch(() => { st[key] = was; paint(); FT.toast('That did not go through'); }); };
    subscribe.onclick = () => { if (channel.id) flip('subscribed', (on) => FT.api.subscribe(channel, on)); };
    later.onclick = () => flip('later', (on) => FT.api.watchLater(video, on));
    const rate = (kind) => {
        const wasLiked = st.like === 'LIKED' || st.like === 'like';
        const wasDisliked = st.like === 'DISLIKED' || st.like === 'dislike';
        const was = st.like;
        const active = kind === 'like' ? wasLiked : wasDisliked;
        st.like = active ? null : kind;
        paint();
        FT.api.rate(video, active ? 'remove' : kind).catch(() => { st.like = was; paint(); FT.toast('That did not go through'); });
    };
    like.onclick = () => rate('like');
    dislike.onclick = () => rate('dislike');

    // navigator.share() rejects without a trusted user gesture (a phone-remote press is a WebSocket
    // message, not a click); falls through to FT.copyText so that press still does something.
    const share = FT.button('Share', 'share', async () => {
        const link = info.url;
        if (navigator.share) {
            try { await navigator.share({ title: info.title, url: link }); return; } catch (e) { if (e && e.name === 'AbortError') return; }
        }
        FT.toast(await FT.copyText(link) ? 'Link copied' : link);
    }, '', 'share');
    const audio = FT.button('Audio only', 'music_note', () => audio.classList.toggle('on', FT.player.toggleAudioOnly()), '', 'audio');
    const buttons = [subscribe, like, dislike, later, share, audio];
    if (service === 0) buttons.push(downloadButton(service, info));
    if (FT.player.danmaku) {
        const overlay = FT.button('Comments overlay', 'chat', () => overlay.classList.toggle('on', FT.player.toggleDanmaku()), 'on', 'overlay');
        buttons.push(overlay);
    }
    buttons.forEach((b, i) => { if (i < 4) b.dataset.first = '1'; });
    paint();

    FT.api.state(info.url, info.channelId).then((s) => {
        st.subscribed = !!s.subscribed;
        st.later = !!s.watchLater;
        st.like = s.like || null;
        paint();
    }).catch(() => {});
    return h('div', { class: 'btns' }, buttons);
};

const chapterCards = (info) => info.chapters.map((c, i) => {
    const card = h('div', { class: 'card', tabindex: '-1', data: { f: '', key: 'ch' + i }, onclick: () => { FT.player.seekTo(c.s); mode.onPlayer = true; FT.focus.clear(); scrollTo({ top: 0 }); } },
        h('div', { class: 'thumb' }, (c.thumb || info.thumbnailUrl) ? h('img', { src: c.thumb || info.thumbnailUrl, alt: '', loading: 'lazy' }) : null),
        h('div', { class: 'card-title' }, h('span', { class: 'tt' }, FT.duration(c.s)), ' ' + c.t));
    return card;
});

const render = async (root, params, isCurrent) => {
    const service = parseInt(params.s, 10) || 0;
    const url = params.u;
    mode.onPlayer = true;
    const slot = h('div', { class: 'watch-slot' });
    root.replaceChildren(slot, FT.loading());
    FT.player.dock('full');

    const info = await FT.api.video(service, url);
    if (!isCurrent()) return;
    await FT.player.open(service, info);
    if (!isCurrent()) return;
    FT.player.dock('full');

    const about = h('div', { class: 'about' }, h('b', {}, [FT.views(info.viewCount), info.uploadDate].filter(Boolean).join(' · ') || 'About'), FT.description(info.description, info.descriptionType));
    const commentsHolder = h('section', { class: 'comments' }, h('h2', {}, 'Comments'), FT.loading());
    const related = (info.relatedVideos || []).filter((v) => !v.isShort);
    const page = h('div', {},
        h('div', { class: 'watch-info' },
            h('h1', {}, info.title),
            h('a', { class: 'who-link', href: info.channelId ? FT.link.channel(info.channelId, service) : '#', tabindex: '-1', data: { f: '', key: 'channel', first: '1' } },
                FT.avatar(info.channelName, info.channelThumbnailUrl), h('div', { class: 'grow' }, h('div', { class: 'who' }, info.channelName))),
            actionRow(service, info)),
        info.chapters.length ? FT.shelf('Chapters', 'Left and right to jump, OK to play from there', chapterCards(info), 'chapters') : null,
        about,
        related.length ? FT.shelf('Related', 'More like this', related.slice(0, 16).map((v) => FT.videoCard(v))) : null,
        commentsHolder);
    root.replaceChildren(slot, page);
    loadComments(commentsHolder, service, info.url, isCurrent);

    // The picture shrinks into the corner once it has scrolled out of sight, and comes back when it is in view again.
    if (observer) observer.disconnect();
    observer = new IntersectionObserver(([entry]) => {
        if (FT.route.name !== 'watch' || FT.player.isFullscreen()) return;
        FT.player.dock(entry.intersectionRatio < 0.3 ? 'mini' : 'full');
    }, { threshold: [0, 0.3, 1] });
    observer.observe(slot);
};

FT.register('watch', {
    render,
    // The keys start on the player, not on a control.
    ready() { mode.onPlayer = true; FT.focus.clear(); document.body.classList.add('player-mode'); },
    leave() {
        if (observer) { observer.disconnect(); observer = null; }
        if (downloadTimer) { clearInterval(downloadTimer); downloadTimer = 0; }
        document.body.classList.remove('player-mode');
    },
});

// While the player has the keys: left and right seek, OK pauses (or skips a sponsor segment), Menu toggles fullscreen.
FT.command.hook = (name) => {
    FT.player.cancelNext();
    if (FT.menu.isOpen() || FT.route.name !== 'watch') return false;
    const el = FT.focus.el;
    if (mode.onPlayer) {
        if (name === 'left') FT.player.seek(-10);
        else if (name === 'right') FT.player.seek(10);
        else if (name === 'ok') { if (FT.player.hasSkip()) FT.player.skip(); else FT.player.toggle(); }
        else if (name === 'menu') FT.player.fullscreen();
        else if (name === 'down') {
            mode.onPlayer = false;
            document.body.classList.remove('player-mode');
            const first = FT.$$('[data-first]', FT.$('#screen')).find((c) => c.getBoundingClientRect().width > 0) || FT.focus.below();
            if (first) FT.focus.select(first);
        } else if (name === 'up') {
            if (FT.remote.active) return true;
            const tab = FT.$('#top .tab.on') || FT.$('#top [data-f]');
            mode.onPlayer = false;
            document.body.classList.remove('player-mode');
            if (tab) FT.focus.select(tab);
        } else return false;
        return true;
    }
    // From the first row of details, Up hands the keys back to the player.
    if (name === 'up' && el && el.dataset.first) {
        mode.onPlayer = true;
        document.body.classList.add('player-mode');
        FT.focus.clear();
        scrollTo({ top: 0 });
        return true;
    }
    // Leaving the top bar downward, or picking a control by any means, ends player mode.
    return false;
};

// Keyboard shortcuts for the video, on this screen only and never while typing.
document.addEventListener('keydown', (e) => {
    if (FT.route.name !== 'watch' || e.ctrlKey || e.metaKey || e.altKey || FT.menu.isOpen()) return;
    if (e.target && e.target.matches && e.target.matches('input, textarea, select')) return;
    const key = e.key.toLowerCase();
    if (key === ' ' || key === 'k') FT.player.toggle();
    else if (key === 'f') FT.player.fullscreen();
    else if (key === 'j') FT.player.seek(-10);
    else if (key === 'l') FT.player.seek(10);
    else if (key === '+' || key === '=') FT.player.volume(0.1);
    else if (key === '-') FT.player.volume(-0.1);
    else return;
    e.preventDefault();
});
})();

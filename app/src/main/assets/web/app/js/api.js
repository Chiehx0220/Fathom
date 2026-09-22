// Everything the app asks the phone for, and everything it tells it. Nothing else in the app calls fetch().
(() => {

const getJson = async (path, params) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params || {})) {
        if (value !== undefined && value !== null && value !== '') query.set(key, value);
    }
    const qs = query.toString();
    const res = await fetch(qs ? `${path}?${qs}` : path);
    const body = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(body.error || `HTTP ${res.status}`);
    return body;
};

// A short memory for lists that are slow to build, so going back to a screen does not fetch it all again.
const memo = new Map();
const remembered = (key, ttlMs, load) => {
    const hit = memo.get(key);
    if (hit && Date.now() - hit.at < ttlMs) return hit.value;
    const value = load().catch((error) => { memo.delete(key); throw error; });
    memo.set(key, { at: Date.now(), value });
    return value;
};

// A video the server needs to know about to save, like or list it.
const videoQuery = (v) => ({
    url: v.url, title: v.title, uploader: v.channelName || '', thumbnail: v.thumbnailUrl || '', uploaderUrl: v.channelId || '',
});
// These actions answer with a redirect or a page; only whether they went through matters.
const act = async (path, params) => {
    const query = new URLSearchParams({ ...params, back: 'ajax' });
    const res = await fetch(`${path}?${query}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
};

FT.api = {
    forget: () => memo.clear(),

    // more: true asks for another discovery round for infinite-scroll on Home - never memoized,
    // each call is a fresh server round (see handleApiRecommendations's `more=1`).
    recommendations: (service, more) => more
        ? getJson('/api/v1/recommendations', { serviceId: service, more: 1 })
        : remembered(`rec:${service}`, 120000, () => getJson('/api/v1/recommendations', { serviceId: service })),
    home: (service) => remembered(`home:${service}`, 120000, () => getJson('/api/v1/home', { serviceId: service })),
    feed: (service) => remembered(`feed:${service}`, 300000, () => getJson('/api/v1/feed', { serviceId: service })),
    history: () => getJson('/api/v1/history'),
    library: (service) => getJson('/api/v1/library', { serviceId: service }),
    search: (service, q, nextPage) => getJson('/api/v1/search', { serviceId: service, q, nextPage }),
    searchHistory: () => getJson('/search-history'),
    forgetSearch: (q) => getJson('/search-history', { delete: q }),
    channel: (service, id, nextPage) => getJson('/api/v1/channel', { serviceId: service, id, nextPage }),
    playlist: (service, id, nextPage) => getJson('/api/v1/playlist', { serviceId: service, id, nextPage }),
    video: (service, id) => getJson('/api/v1/video', { serviceId: service, id }),
    comments: (service, id, nextPage) => getJson('/api/v1/comments', { serviceId: service, id, nextPage }),
    state: (id, channel) => getJson('/api/v1/state', { id, channel }),
    sponsor: (service, id) => getJson('/api/v1/sponsor', { serviceId: service, id }),
    settings: (changes) => getJson('/api/v1/settings', changes),
    download: (action, service, id) => getJson('/api/v1/download', { action, serviceId: service, id }),

    progress: (id, service, percent, duration) => fetch('/api/v1/watch_progress?' + new URLSearchParams({ id, serviceId: service, percent, durationSeconds: duration }), { keepalive: true }).catch(() => {}),

    subscribe: (channel, on) => act('/subscribe', { action: on ? 'subscribe' : 'unsubscribe', id: channel.id, name: channel.name || '', avatar: channel.thumbnailUrl || '' }),
    block: (channelUrl, on) => act('/block_channel', { action: on ? 'block' : 'unblock', id: channelUrl }),
    watchLater: (video, on) => act('/watch_later_action', { action: on ? 'add' : 'remove', ...videoQuery(video), type: 'video', serviceId: video.serviceId ?? 0 }),
    rate: (video, action) => act('/rate_video', { action, ...videoQuery(video), serviceId: video.serviceId ?? 0 }),
    removeFromHistory: (video) => act('/history_action', { action: 'remove', url: video.url, serviceId: video.serviceId ?? 0 }),
    bookmarkPlaylist: (playlist, on) => act('/bookmark_playlist', { action: on ? 'bookmark' : 'unbookmark', id: playlist.id, name: playlist.name || '' }),
};
})();

// Sole fetch() caller: all phone requests.
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

// TTL memo for slow lists.
const memo = new Map();
const remembered = (key, ttlMs, load) => {
    const hit = memo.get(key);
    if (hit && Date.now() - hit.at < ttlMs) return hit.value;
    const value = load().catch((error) => { memo.delete(key); throw error; });
    memo.set(key, { at: Date.now(), value });
    return value;
};

// Video fields the server needs to save/like/list.
const videoQuery = (v) => ({
    url: v.url, title: v.title, uploader: v.channelName || '', thumbnail: v.thumbnailUrl || '', uploaderUrl: v.channelId || '',
});
// Redirect/page answers: only ok matters.
const act = async (path, params) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) if (value !== undefined && value !== null) query.set(key, value);
    query.set('back', 'ajax');
    const res = await fetch(`${path}?${query}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
};

const playlistAction = (params) => getJson('/playlist_action', params);

FT.api = {
    forget: () => memo.clear(),

    // more: fresh discovery round (infinite scroll), never memoized (handleApiRecommendations more=1).
    recommendations: (service, more) => more
        ? getJson('/api/v1/recommendations', { serviceId: service, more: 1 })
        : remembered(`rec:${service}`, 120000, () => getJson('/api/v1/recommendations', { serviceId: service })),
    home: (service) => remembered(`home:${service}`, 120000, () => getJson('/api/v1/home', { serviceId: service })),
    feed: (service, group) => remembered(`feed:${service}:${group || ''}`, 300000, () => getJson('/api/v1/feed', { serviceId: service, group })),
    groups: () => getJson('/api/v1/groups'),
    // options: count (unread only), clear; listing marks read.
    notifications: (options) => getJson('/api/v1/notifications', options),
    history: () => getJson('/api/v1/history'),
    library: (service) => getJson('/api/v1/library', { serviceId: service }),
    downloads: () => getJson('/api/v1/downloads'),
    search: (service, q, nextPage) => getJson('/api/v1/search', { serviceId: service, q, nextPage }),
    searchHistory: () => getJson('/search-history'),
    forgetSearch: (q) => getJson('/search-history', { delete: q }),
    channel: (service, id, nextPage) => getJson('/api/v1/channel', { serviceId: service, id, nextPage }),
    liked: () => getJson('/api/v1/liked'),
    playlists: (videoUrl) => getJson('/api/v1/playlists', videoUrl ? { id: videoUrl } : {}),
    myList: (id) => getJson('/api/v1/mylist', { id }),
    playlist: (service, id, nextPage) => getJson('/api/v1/playlist', { serviceId: service, id, nextPage }),
    video: (service, id) => getJson('/api/v1/video', { serviceId: service, id }),
    comments: (service, id, nextPage) => getJson('/api/v1/comments', { serviceId: service, id, nextPage }),
    state: (id, channel) => getJson('/api/v1/state', { id, channel }),
    sponsor: (service, id) => getJson('/api/v1/sponsor', { serviceId: service, id }),
    settings: (changes) => getJson('/api/v1/settings', changes),
    // switchTo: profile id to switch to (the app restarts).
    sponsorBlock: (changes) => getJson('/api/v1/sponsorblock', changes),
    wellbeing: (changes) => getJson('/api/v1/wellbeing', changes),
    blockedChannels: () => getJson('/api/v1/blocked'),
    profiles: (switchTo) => getJson('/api/v1/profiles', switchTo ? { switch: switchTo } : {}),
    download: (action, service, id, choice = {}) => getJson('/api/v1/download', { action, serviceId: service, id, ...choice }),

    // notify: playback settled (pause/close/switch) → history subscribers refresh; timer reports silent.
    progress: (id, service, percent, duration, { notify = false } = {}) => fetch('/api/v1/watch_progress?' + new URLSearchParams({ id, serviceId: service, percent, durationSeconds: duration }), { keepalive: true })
        .then(() => { if (notify) dispatchEvent(new Event('ft:history')); }).catch(() => {}),
    onHistoryChange: (handler) => FT.on('ft:history', handler),

    subscribe: (channel, on) => act('/subscribe', { action: on ? 'subscribe' : 'unsubscribe', id: channel.id, name: channel.name || '', avatar: channel.thumbnailUrl || '' }),
    notify: (channel, on) => act('/subscribe', { action: on ? 'notify_on' : 'notify_off', id: channel.id }),
    block: (channelUrl, on, name) => act('/block_channel', { action: on ? 'block' : 'unblock', id: channelUrl, name }),
    // action: not_interested | watched | unwatched
    feedback: (video, action) => act('/video_feedback', { action, ...videoQuery(video), duration: video.duration || 0 }),
    // { videos: [{ id, duration }], enabled } → { enabled, items: { id: { title?, thumbnailUrl? } } }
    dearrow: ({ videos, enabled } = {}) => getJson('/api/v1/dearrow', { ids: videos && videos.map((v) => `${v.id}:${v.duration || 0}`).join(','), enabled }),
    // { id: video url, enabled } → { enabled, dislikes?, likes? }
    dislikes: ({ id, enabled } = {}) => getJson('/api/v1/dislikes', { id, enabled }),
    // { channels, block, warn, allow, name, disallow } → { block, warn, channelCount?, updatedAt, allowed, verdicts }
    aiList: ({ channels, block, warn, allow, name, disallow } = {}) => getJson('/api/v1/ailist', { channels: channels && channels.join(','), block, warn, allow, name, disallow }),
    handoff: (url, t, d, title) => act('/handoff', { id: url, t, d, title }),
    castDevices: (scan) => getJson('/api/v1/cast/devices', scan ? { scan: 1 } : {}),
    castStart: (usn, url, t, title) => act('/api/v1/cast/start', { usn, id: url, t, title, serviceId: FT.serviceOfUrl(url) }),
    castStop: () => act('/api/v1/cast/stop', {}),
    watchLater: (video, on) => act('/watch_later_action', { action: on ? 'add' : 'remove', ...videoQuery(video), type: 'video', serviceId: video.serviceId ?? 0 }),
    rate: (video, action) => act('/rate_video', { action, ...videoQuery(video), serviceId: video.serviceId ?? 0 }),
    removeFromHistory: (video) => act('/history_action', { action: 'remove', url: video.url, serviceId: video.serviceId ?? 0 }),
    playlistSet: (playlist, video, on) => playlistAction({ action: on ? 'add' : 'remove', playlist, ...videoQuery(video) }),
    playlistCreate: (name, video) => playlistAction({ action: 'create', name, ...(video ? videoQuery(video) : {}) }),
    playlistRename: (playlist, name) => playlistAction({ action: 'rename', playlist, name }),
    playlistDelete: (playlist) => playlistAction({ action: 'delete', playlist }),
    bookmarkPlaylist: (playlist, on) => act('/bookmark_playlist', { action: on ? 'bookmark' : 'unbookmark', id: playlist.id, name: playlist.name || '' }),
};
})();

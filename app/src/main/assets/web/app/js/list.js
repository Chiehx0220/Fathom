// Shared video-list view: rows left, selection large right, auto load-more near the end.
(() => {
const h = FT.h;

// Detail pane of selected row.
const paneFor = (el) => {
    const v = el.item;
    return [
        h('div', { class: 'thumb' }, v.thumbnailUrl ? h('img', { src: v.thumbnailUrl, alt: '' }) : null, v.duration > 0 ? h('span', { class: 'dur' }, FT.duration(v.duration)) : null),
        h('h2', {}, v.title),
        h('p', {}, FT.meta(v)),
        h('div', { class: 'btns' },
            FT.button(FT.t('play'), 'play_arrow', () => { location.hash = FT.link.watch(v); }, 'primary', 'pane-play'),
            FT.button(FT.t(el.inWatchLater ? 'saved' : 'watch_later'), 'schedule', () => FT.api.watchLater(v, !el.inWatchLater).then(() => { el.inWatchLater = !el.inWatchLater; FT.toast(FT.t(el.inWatchLater ? 'saved_to_later' : 'removed_from_later')); }).catch(() => FT.toast(FT.t('could_not_save'))), '', 'pane-later'),
            v.channelId ? FT.button(FT.t('channel'), 'account_circle', () => { location.hash = FT.link.channel(v.channelId, v.serviceId); }, '', 'pane-channel') : null),
    ];
};

// options: { fetch(cursor) -> { items, next }, decorate(el), empty: { icon, title, text }, before: [elements], queue: Play all / Shuffle }
// Grid layout: card grid without the detail pane; the next page loads when `more` nears the viewport.
FT.listView = async (root, options, scope) => {
    const wide = FT.layout.current() === 'grid';
    const itemSelector = wide ? '.card' : '.row-item';
    const list = h('div', { class: wide ? 'grid' : 'list' });
    const pane = h('aside', { class: 'pane' });
    const more = h('div', { class: 'more', hidden: true });
    const loaded = [];
    let cursor = null;
    let loading = false;
    let done = false;

    const showPane = (el) => { if (el && el.item && el.kind === 'video') pane.replaceChildren(...paneFor(el)); };
    scope.onSelect((el) => {
        if (!list.contains(el)) return;
        showPane(el);
        const rows = FT.$$(itemSelector, list);
        if (!done && rows.length - rows.indexOf(el) <= 3) loadMore();
    });

    const remove = (el) => {
        const rows = FT.$$(itemSelector, list);
        const at = rows.indexOf(el);
        const neighbour = rows[at + 1] || rows[at - 1];
        el.remove();
        if (neighbour) FT.focus.select(neighbour); else FT.focus.first();
        if (!FT.$(itemSelector, list)) root.replaceChildren(...(options.before || []), FT.empty(options.empty.icon, options.empty.title, options.empty.text));
    };

    const append = (items) => {
        for (const item of items) {
            loaded.push(item);
            const row = wide ? FT.videoCard(item) : FT.videoRow(item);
            if (options.decorate) options.decorate(row);
            row.onRemoved = () => remove(row);
            list.append(row);
        }
    };

    async function loadMore() {
        if (loading || done) return;
        loading = true;
        try {
            const page = await options.fetch(cursor);
            if (!scope.live()) return;
            append(page.items);
            cursor = page.next || null;
            done = !cursor;
            more.hidden = done;
        } catch (error) {
            if (scope.live()) FT.toast(FT.t('could_not_load_more'));
        } finally {
            loading = false;
        }
    }

    more.append(FT.button(FT.t('load_more'), 'expand_more', loadMore, '', 'load-more'));
    if (wide) {
        const nearEnd = new IntersectionObserver(([entry]) => { if (entry.isIntersecting) loadMore(); }, { rootMargin: '600px' });
        nearEnd.observe(more);
        scope.dispose(() => nearEnd.disconnect());
    }
    const first = await options.fetch(null);
    if (!scope.live()) return;
    if (!first.items.length && !(first.extra && first.extra.length)) {
        root.replaceChildren(...(options.before || []), FT.empty(options.empty.icon, options.empty.title, options.empty.text));
        return;
    }
    append(first.items);
    cursor = first.next || null;
    done = !cursor;
    more.hidden = done;
    const body = wide ? h('div', {}, list, more) : h('div', { class: 'split' }, h('div', {}, list, more), pane);
    const start = options.queue && first.items.length ? FT.queue.buttons(() => loaded.filter((v) => v.kind !== 'channel')) : null;
    root.replaceChildren(...[...(options.before || []), ...(first.extra || []), start, first.items.length ? body : null].filter(Boolean));
    const firstRow = FT.$(itemSelector, list);
    if (firstRow && !wide) showPane(firstRow);
};
})();

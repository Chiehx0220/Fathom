const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll, video } = require('./dom-harness');

afterEach(closeAll);

const home = { '/api/v1/recommendations': { videos: [video(1)], hasMore: false }, '/api/v1/history': { videos: [] } };
const entry = (n, isNew) => ({ id: n, isNew, timestamp: Date.now() - 3600e3, video: video(n) });
const inbox = (items, unread = items.filter((e) => e.isNew).length) => (params) => (params.count ? { unread, items: [] } : { unread, items });

test('the bell shows the unread count and none when everything is read', async () => {
  const unread = await open({ routes: { ...home, '/api/v1/notifications': inbox([entry(1, true), entry(2, true)]) } });
  assert.strictEqual(unread.window.document.querySelector('#top .bell .badge').textContent, '2');
  const read = await open({ routes: { ...home, '/api/v1/notifications': inbox([entry(1, false)], 0) } });
  assert.strictEqual(read.window.document.querySelector('#top .bell .badge'), null);
});

test('an old server without the inbox keeps the bell without a badge', async () => {
  const page = await open({ routes: home });
  assert.ok(page.window.document.querySelector('#top .bell'));
  assert.strictEqual(page.window.document.querySelector('#top .bell .badge'), null);
});

test('the inbox lists uploads, flags the new ones and clears the badge', async () => {
  const page = await open({ routes: { ...home, '/api/v1/notifications': inbox([entry(1, true), entry(2, false)]) }, hash: '#/notifications' });
  const { document } = page.window;
  const rows = [...document.querySelectorAll('#screen .row-item, #screen .card')];
  assert.strictEqual(rows.length, 2);
  assert.deepStrictEqual(rows.map((row) => row.classList.contains('fresh')), [true, false]);
  assert.strictEqual(document.querySelector('#top .bell .badge'), null);
  assert.ok(page.requests.some((r) => r.path === '/api/v1/notifications' && !r.params.count));
});

test('an empty inbox says so, and Clear all asks the phone to clear', async () => {
  const empty = await open({ routes: { ...home, '/api/v1/notifications': inbox([]) }, hash: '#/notifications' });
  assert.match(empty.window.document.querySelector('#screen .empty').textContent, /caught up/);
  const full = await open({ routes: { ...home, '/api/v1/notifications': inbox([entry(1, false)], 0) }, hash: '#/notifications' });
  full.window.document.querySelector('[data-key="clear-all"]').click();
  await full.settle();
  assert.ok(full.requests.some((r) => r.params.clear === 'true'));
});

const subs = {
  ...home,
  '/api/v1/library': { channels: [{ id: 'https://www.youtube.com/channel/UC1', serviceId: 0, name: 'One', thumbnailUrl: '', subscriberCount: 1, url: 'x' }], playlists: [], watchLater: [], mine: [] },
  '/api/v1/feed': { videos: [video(1)] },
  '/api/v1/groups': { groups: [{ name: 'News', channels: ['https://www.youtube.com/channel/UC1'] }, { name: 'Music', channels: [] }] },
};

test('subscription groups from the phone filter the feed', async () => {
  const page = await open({ routes: subs, hash: '#/subscriptions?tab=feed' });
  const { document } = page.window;
  const pills = [...document.querySelectorAll('#screen .pill')].map((p) => p.textContent);
  assert.deepStrictEqual(pills.slice(-3), ['All', 'News', 'Music']);
  assert.ok(page.requests.some((r) => r.path === '/api/v1/feed' && !r.params.group));
  document.querySelector('[data-key="grp:News"]').click();
  await page.settle();
  assert.ok(page.requests.some((r) => r.path === '/api/v1/feed' && r.params.group === 'News'));
});

test('without groups the subscriptions page is unchanged', async () => {
  const page = await open({ routes: { ...subs, '/api/v1/groups': { groups: [] } }, hash: '#/subscriptions?tab=feed' });
  assert.strictEqual(page.window.document.querySelector('[data-key="grp-all"]'), null);
});

test('a pushed inbox change refreshes the bell count', async () => {
  const { sockets, requests, settle } = await open({ routes: { ...home, '/api/v1/notifications': { unread: 2, items: [] } } });
  const before = requests.filter((r) => r.path === '/api/v1/notifications').length;
  sockets[0].message('inbox');
  await settle();
  assert.strictEqual(requests.filter((r) => r.path === '/api/v1/notifications').length, before + 1);
});

const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll, video } = require('./dom-harness');

afterEach(closeAll);

const picks = [video(1), video(2, { channelId: 'UC1' }), video(3), video(4)];
const routes = (extra = {}) => ({
  '/api/v1/recommendations': { videos: picks, hasMore: false },
  '/api/v1/history': { videos: [] },
  '/video_feedback': {},
  '/block_channel': {},
  ...extra,
});
const key = (window, name) => window.document.dispatchEvent(new window.KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
const labels = (document) => [...document.querySelectorAll('#menu .menu-item')].map((item) => item.textContent.replace(/^[a-z_]+(?=[A-Z])/, ''));

// Opens the options menu of the nth grid card.
const menuOf = async (page, n) => {
  const cards = page.window.document.querySelectorAll('#screen .card');
  page.FT.focus.select(cards[n]);
  key(page.window, 'm');
  return cards[n];
};
const choose = async (page, label) => {
  const item = [...page.window.document.querySelectorAll('#menu .menu-item')].find((el) => el.textContent.endsWith(label));
  item.click();
  await page.settle();
};

test('the video menu offers watched, not interested and block', async () => {
  const page = await open({ routes: routes() });
  await menuOf(page, 0);
  assert.deepStrictEqual(labels(page.window.document).slice(-3), ['Mark as watched', 'Not interested', 'Block channel']);
});

test('not interested tells the phone and drops the card', async () => {
  const page = await open({ routes: routes() });
  const { document } = page.window;
  const before = document.querySelectorAll('#screen .card').length;
  await menuOf(page, 0);
  await choose(page, 'Not interested');
  const call = page.requests.find((r) => r.path === '/video_feedback');
  assert.strictEqual(call.params.action, 'not_interested');
  assert.strictEqual(call.params.url, picks[0].url);
  assert.strictEqual(call.params.duration, String(picks[0].duration));
  assert.strictEqual(document.querySelectorAll('#screen .card').length, before - 1);
});

test('mark as watched keeps the card and flips the entry to unwatched', async () => {
  const page = await open({ routes: routes() });
  const { document } = page.window;
  const card = await menuOf(page, 0);
  await choose(page, 'Mark as watched');
  assert.strictEqual(page.requests.find((r) => r.path === '/video_feedback').params.action, 'watched');
  assert.ok(card.isConnected);
  assert.strictEqual(card.item.progress, 100);
  assert.strictEqual(document.querySelector('#toast').textContent, 'Marked as watched');
  key(page.window, 'm');
  assert.ok(labels(document).includes('Mark as unwatched'));
});

test('block channel hides that channel everywhere on the page and offers undo', async () => {
  const page = await open({ routes: routes() });
  const { document } = page.window;
  await menuOf(page, 0);
  await choose(page, 'Block channel');
  const call = page.requests.find((r) => r.path === '/block_channel');
  assert.strictEqual(call.params.action, 'block');
  assert.strictEqual(call.params.id, 'UC1');
  assert.strictEqual(call.params.name, 'Channel 1');
  const remaining = [...document.querySelectorAll('#screen .card')].map((card) => card.item.channelId);
  assert.ok(!remaining.includes('UC1'));
  const toast = document.querySelector('#toast');
  assert.ok(toast.textContent.startsWith('Blocked Channel 1'));
  toast.querySelector('.toast-undo').click();
  await page.settle();
  const undo = page.requests.filter((r) => r.path === '/block_channel').pop();
  assert.strictEqual(undo.params.action, 'unblock');
});

test('a failed feedback request says so and keeps the card', async () => {
  const page = await open({ routes: routes({ '/video_feedback': { status: 500, body: {} } }) });
  const { document } = page.window;
  const before = document.querySelectorAll('#screen .card').length;
  await menuOf(page, 0);
  await choose(page, 'Not interested');
  assert.strictEqual(document.querySelector('#toast').textContent, 'Could not save that');
  assert.strictEqual(document.querySelectorAll('#screen .card').length, before);
});

test('feedback in history and liked lists leaves out "not interested"', async () => {
  const page = await open({ routes: routes({ '/api/v1/history': { videos: [video(7, { progress: 30 })] } }), hash: '#/history' });
  const card = page.window.document.querySelector('#screen .row-item, #screen .card');
  page.FT.focus.select(card);
  key(page.window, 'm');
  assert.ok(!labels(page.window.document).includes('Not interested'));
  assert.ok(labels(page.window.document).includes('Mark as watched'));
});

test('the channel page can block and unblock the channel', async () => {
  const channel = { id: 'UC9', name: 'Nine', thumbnailUrl: '', subscriberCount: 5, isSubscribed: false, isNotifying: false };
  const page = await open({
    routes: routes({
      '/api/v1/channel': { channel, videos: [], nextPage: null },
      '/api/v1/state': { subscribed: false, notify: false, blocked: false, watchLater: false, like: null },
    }),
    hash: '#/channel?s=0&u=UC9',
  });
  const { document } = page.window;
  document.querySelector('[data-key="channel-menu"]').click();
  assert.deepStrictEqual(labels(document), ['Block channel']);
  await choose(page, 'Block channel');
  assert.strictEqual(page.requests.filter((r) => r.path === '/block_channel').pop().params.action, 'block');
  document.querySelector('[data-key="channel-menu"]').click();
  assert.deepStrictEqual(labels(document), ['Unblock channel']);
});

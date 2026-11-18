const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll } = require('./dom-harness');

afterEach(closeAll);

const channel = { id: 'https://www.youtube.com/channel/UC1', name: 'Chan', thumbnailUrl: '' };
const actions = (requests) => requests.filter((r) => r.path === '/subscribe').map((r) => r.params.action);
const bellOf = (sub) => sub.el.querySelector('.notify-bell');

test('the bell appears once subscribed and switches notifications in one tap', async () => {
  const { FT, window, requests, settle } = await open({ routes: { '/subscribe': {}, '/api/v1/home': {} } });
  const sub = FT.subscribeButton(channel);
  window.document.body.append(sub.el);
  assert.ok(sub.button.classList.contains('primary'));
  assert.strictEqual(bellOf(sub), null);

  sub.button.click();
  await settle();
  assert.deepStrictEqual(actions(requests), ['subscribe']);
  assert.ok(sub.button.classList.contains('quiet'));
  assert.strictEqual(bellOf(sub).getAttribute('aria-pressed'), 'false');

  bellOf(sub).click();
  await settle();
  assert.deepStrictEqual(actions(requests), ['subscribe', 'notify_on']);
  assert.strictEqual(bellOf(sub).getAttribute('aria-pressed'), 'true');
  assert.ok(bellOf(sub).classList.contains('on'));

  bellOf(sub).click();
  await settle();
  assert.deepStrictEqual(actions(requests), ['subscribe', 'notify_on', 'notify_off']);
  assert.ok(!bellOf(sub).classList.contains('on'));
});

test('unsubscribing drops the bell and its state', async () => {
  const { FT, window, requests, settle } = await open({ routes: { '/subscribe': {}, '/api/v1/home': {} } });
  const sub = FT.subscribeButton(channel);
  window.document.body.append(sub.el);
  sub.set({ subscribed: true, notify: true });
  assert.ok(bellOf(sub).classList.contains('on'));
  sub.button.click();
  await settle();
  assert.deepStrictEqual(actions(requests), ['unsubscribe']);
  assert.strictEqual(bellOf(sub), null);
  assert.ok(sub.button.classList.contains('primary'));
});

test('a failed bell change rolls back', async () => {
  const { FT, window, settle } = await open({ routes: { '/subscribe': { status: 500, body: {} }, '/api/v1/home': {} } });
  const sub = FT.subscribeButton(channel);
  window.document.body.append(sub.el);
  sub.set({ subscribed: true, notify: false });
  bellOf(sub).click();
  await settle();
  assert.strictEqual(bellOf(sub).getAttribute('aria-pressed'), 'false');
});

test('a read that began before a local change does not overwrite it', async () => {
  const { FT, window, settle } = await open({ routes: { '/subscribe': {}, '/api/v1/home': {} } });
  const sub = FT.subscribeButton(channel);
  window.document.body.append(sub.el);
  const since = window.performance.now();
  sub.button.click();
  sub.set({ subscribed: false, notify: false }, since);
  assert.ok(sub.button.classList.contains('quiet'));
  await settle();
  sub.set({ subscribed: false, notify: false }, since);
  assert.ok(sub.button.classList.contains('quiet'));
  sub.set({ subscribed: false, notify: false }, window.performance.now());
  assert.ok(sub.button.classList.contains('primary'));
});

test('followState refreshes when the page becomes visible and stops with its scope', async () => {
  const { FT, window } = await open({ routes: { '/api/v1/home': {} } });
  let live = true;
  let disposed = null;
  let calls = 0;
  FT.followState({ live: () => live, dispose: (fn) => { disposed = fn; } }, () => { calls += 1; return Promise.resolve(); });
  window.document.dispatchEvent(new window.Event('visibilitychange'));
  assert.strictEqual(calls, 1);
  live = false;
  window.document.dispatchEvent(new window.Event('visibilitychange'));
  assert.strictEqual(calls, 1);
  disposed();
  live = true;
  window.document.dispatchEvent(new window.Event('visibilitychange'));
  assert.strictEqual(calls, 1);
});

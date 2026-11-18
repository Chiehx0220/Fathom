const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll } = require('./dom-harness');

afterEach(closeAll);

const routes = { '/api/v1/home': {}, '/api/v1/settings': { hideWatched: false, hideShorts: false, deepFlow: false, deepFlowEndsAt: 0 } };
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

test('the page opens the events socket and tells listeners which topic changed', async () => {
  const { FT, sockets } = await open({ routes });
  assert.strictEqual(sockets.length, 1);
  assert.ok(sockets[0].url.endsWith('/events'));
  assert.strictEqual(FT.events.connected(), true);
  const seen = [];
  FT.events.on('settings', () => seen.push('settings'));
  FT.events.on('subscriptions', () => seen.push('subscriptions'));
  sockets[0].message('subscriptions');
  sockets[0].message('unknown');
  assert.deepStrictEqual(seen, ['subscriptions']);
});

test('an unsubscribed listener hears nothing', async () => {
  const { FT, sockets } = await open({ routes });
  let calls = 0;
  const off = FT.events.on('settings', () => { calls += 1; });
  sockets[0].message('settings');
  off();
  sockets[0].message('settings');
  assert.strictEqual(calls, 1);
});

test('a lost socket reconnects and every listener re-reads once', async () => {
  const { FT, sockets } = await open({ routes });
  let calls = 0;
  FT.events.on('settings', () => { calls += 1; });
  sockets[0].close();
  assert.strictEqual(FT.events.connected(), false);
  await sleep(1100);
  assert.strictEqual(sockets.length, 2);
  assert.strictEqual(FT.events.connected(), true);
  assert.strictEqual(calls, 1);
});

test('a pushed settings change syncs Deep Flow without waiting for a poll', async () => {
  const { sockets, requests, settle } = await open({ routes });
  const before = requests.filter((r) => r.path === '/api/v1/settings').length;
  sockets[0].message('settings');
  await settle();
  assert.strictEqual(requests.filter((r) => r.path === '/api/v1/settings').length, before + 1);
});

test('followState re-reads on its topics only', async () => {
  const { FT, sockets } = await open({ routes });
  let calls = 0;
  FT.followState({ live: () => true, dispose() {} }, () => { calls += 1; return Promise.resolve(); }, { topics: ['subscriptions'], covered: true });
  sockets[0].message('settings');
  assert.strictEqual(calls, 0);
  sockets[0].message('subscriptions');
  assert.strictEqual(calls, 1);
});

const count = (requests, path) => requests.filter((r) => r.path === path).length;

test('pushed profile and settings changes reload the profile list', async () => {
  const profiles = { running: 'a', profiles: [{ id: 'a', name: 'Mum' }], switching: false, refused: null, showInTopBar: true };
  const { sockets, requests, settle } = await open({ routes: { ...routes, '/api/v1/profiles': profiles } });
  const before = count(requests, '/api/v1/profiles');
  sockets[0].message('profiles');
  sockets[0].message('settings');
  await settle();
  assert.strictEqual(count(requests, '/api/v1/profiles'), before + 2);
});

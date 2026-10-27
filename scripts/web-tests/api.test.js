const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { APP, serverRoutes, load } = require('./harness');

const recording = (respond = () => ({ ok: true, status: 200, body: {} })) => {
  const urls = [];
  const fetch = async (url) => {
    urls.push(String(url));
    const answer = respond(String(url));
    return { ok: answer.ok, status: answer.status, json: async () => answer.body };
  };
  return { urls, fetch };
};

const api = (respond) => {
  const net = recording(respond);
  const { FT } = load({ fetch: net.fetch });
  return { FT, urls: net.urls };
};

test('requests drop empty parameters', async () => {
  const { FT, urls } = api();
  await FT.api.search(0, 'cats', undefined);
  await FT.api.channel(5, '', null);
  assert.deepStrictEqual(urls, ['/api/v1/search?serviceId=0&q=cats', '/api/v1/channel?serviceId=5']);
});

test('a failing response throws the server message, else the status', async () => {
  const withBody = api(() => ({ ok: false, status: 400, body: { error: 'bad id' } }));
  await assert.rejects(withBody.FT.api.video(0, 'x'), /bad id/);
  const bare = api(() => ({ ok: false, status: 500, body: {} }));
  await assert.rejects(bare.FT.api.video(0, 'x'), /HTTP 500/);
});

test('slow lists are remembered, forgotten on request, and not kept after a failure', async () => {
  let fail = false;
  const { FT, urls } = api(() => (fail ? { ok: false, status: 500, body: {} } : { ok: true, status: 200, body: { items: [] } }));
  await FT.api.home(0);
  await FT.api.home(0);
  assert.strictEqual(urls.length, 1);
  await FT.api.home(5);
  assert.strictEqual(urls.length, 2);
  FT.api.forget();
  await FT.api.home(0);
  assert.strictEqual(urls.length, 3);

  fail = true;
  await assert.rejects(FT.api.feed(0));
  fail = false;
  await FT.api.feed(0);
  assert.strictEqual(urls.filter((u) => u.startsWith('/api/v1/feed')).length, 2);
});

test('loading more recommendations is never remembered', async () => {
  const { FT, urls } = api();
  await FT.api.recommendations(0, true);
  await FT.api.recommendations(0, true);
  assert.deepStrictEqual(urls, ['/api/v1/recommendations?serviceId=0&more=1', '/api/v1/recommendations?serviceId=0&more=1']);
});

test('actions carry back=ajax and fail on a bad status', async () => {
  const ok = api();
  await ok.FT.api.subscribe({ id: 'UC1', name: 'N', thumbnailUrl: 'u' }, true);
  assert.strictEqual(ok.urls[0], '/subscribe?action=subscribe&id=UC1&name=N&avatar=u&back=ajax');
  const bad = api(() => ({ ok: false, status: 403, body: {} }));
  await assert.rejects(bad.FT.api.block('https://x', true), /HTTP 403/);
});

test('video actions send the fields the server saves', async () => {
  const { FT, urls } = api();
  await FT.api.watchLater({ url: 'U', title: 'T', channelName: 'C', thumbnailUrl: 'th', channelId: 'ch', serviceId: 5 }, true);
  const query = new URLSearchParams(urls[0].split('?')[1]);
  assert.strictEqual(urls[0].split('?')[0], '/watch_later_action');
  assert.deepStrictEqual(Object.fromEntries(query), {
    action: 'add', url: 'U', title: 'T', uploader: 'C', thumbnail: 'th', uploaderUrl: 'ch', type: 'video', serviceId: '5', back: 'ajax',
  });
});

test('progress is reported without waiting, and tells history listeners only when asked', async () => {
  const { FT, urls } = api();
  let notified = 0;
  FT.api.onHistoryChange(() => { notified++; });
  await FT.api.progress('v', 0, 50, 120);
  assert.strictEqual(notified, 0);
  await FT.api.progress('v', 0, 100, 120, { notify: true });
  assert.strictEqual(notified, 1);
  assert.match(urls[0], /^\/api\/v1\/watch_progress\?id=v&serviceId=0&percent=50&durationSeconds=120$/);
});

// Contract: every server path the scripts call must be a route the server serves.
test('every endpoint the scripts call exists in the server routes', () => {
  const routes = serverRoutes();
  const dir = path.join(APP, 'js');
  const used = new Map();
  for (const name of fs.readdirSync(dir).filter((n) => n.endsWith('.js'))) {
    const text = fs.readFileSync(path.join(dir, name), 'utf8');
    for (const m of text.matchAll(/\b(?:fetch|getJson|act|playlistAction)\(\s*['`](\/[A-Za-z0-9_\-/]+)/g)) used.set(m[1], name);
    for (const m of text.matchAll(/['`](\/api\/v1\/[a-z_/]+)/g)) used.set(m[1], name);
  }
  assert.ok(used.size > 20, `found only ${used.size} endpoints, the pattern is probably stale`);
  for (const [endpoint, file] of used) assert.ok(routes.has(endpoint), `${file} calls ${endpoint}, which the server does not serve`);
});

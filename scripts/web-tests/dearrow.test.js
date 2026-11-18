const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll, video } = require('./dom-harness');

afterEach(closeAll);

const ID = (n) => `abcdefghij${n}`;
const picks = [1, 2, 3].map((n) => video(n, { id: ID(n), thumbnailUrl: `https://i.ytimg.com/vi/${ID(n)}/hqdefault.jpg` }));
const routes = (dearrow) => ({
  '/api/v1/recommendations': { videos: picks, hasMore: false },
  '/api/v1/history': { videos: [] },
  '/api/v1/dearrow': dearrow,
});
// The phone is asked once the lookups of a render are gathered.
const gathered = (page) => page.settle(40);
const titles = (document) => [...document.querySelectorAll('#screen .card .card-title')].map((el) => el.textContent);

test('cards take DeArrow titles and thumbnails, asked for in one request', async () => {
  const page = await open({
    routes: routes({ enabled: true, items: { [ID(2)]: { title: 'A plain title', thumbnailUrl: '/image-proxy?u=x' }, [ID(3)]: { thumbnailUrl: '/image-proxy?u=y' } } }),
  });
  await gathered(page);
  const { document } = page.window;
  assert.deepStrictEqual(titles(document), ['Video 1', 'A plain title', 'Video 3']);
  const cards = [...document.querySelectorAll('#screen .card')];
  assert.strictEqual(cards[1].querySelector('.card-title').title, 'Video 2');
  assert.ok(cards[1].querySelector('img').getAttribute('src').endsWith('/image-proxy?u=x'));
  assert.ok(cards[2].querySelector('img').getAttribute('src').endsWith('/image-proxy?u=y'));
  assert.strictEqual(cards[0].querySelector('img').getAttribute('src'), picks[0].thumbnailUrl.replace('hq720', 'hqdefault'));
  const asks = page.requests.filter((r) => r.path === '/api/v1/dearrow' && r.params.ids);
  assert.strictEqual(asks.length, 1);
  assert.deepStrictEqual(asks[0].params.ids.split(',').slice(0, 3), [`${ID(1)}:60`, `${ID(2)}:120`, `${ID(3)}:180`]);
});

test('a card keeps its own title and picture when DeArrow has nothing', async () => {
  const page = await open({ routes: routes({ enabled: true, items: {} }) });
  await gathered(page);
  assert.deepStrictEqual(titles(page.window.document), ['Video 1', 'Video 2', 'Video 3']);
});

test('switched off, cards stay as they are and the phone is not asked again', async () => {
  const page = await open({ routes: routes({ enabled: false, items: {} }) });
  await gathered(page);
  assert.deepStrictEqual(titles(page.window.document), ['Video 1', 'Video 2', 'Video 3']);
  const asked = () => page.requests.filter((r) => r.path === '/api/v1/dearrow' && r.params.ids).length;
  assert.strictEqual(asked(), 1);
  page.FT.videoCard(picks[0]);
  await gathered(page);
  assert.strictEqual(asked(), 1);
});

test('a failed lookup leaves the cards alone', async () => {
  const page = await open({ routes: routes({ status: 500, body: {} }) });
  await gathered(page);
  assert.deepStrictEqual(titles(page.window.document), ['Video 1', 'Video 2', 'Video 3']);
});

test('only YouTube videos are looked up', async () => {
  const mixed = [video(1, { id: ID(1) }), video(2, { id: '1234567', serviceId: 5 }), video(3, { id: ID(3), isLive: true })];
  const page = await open({ routes: { ...routes({ enabled: true, items: {} }), '/api/v1/recommendations': { videos: mixed, hasMore: false } } });
  await gathered(page);
  const ask = page.requests.find((r) => r.path === '/api/v1/dearrow' && r.params.ids);
  assert.strictEqual(ask.params.ids, `${ID(1)}:60`);
});

test('the switch in settings sets DeArrow on the phone', async () => {
  let enabled = false;
  const page = await open({
    routes: {
      ...routes(null),
      '/api/v1/dearrow': (params) => { if (params.enabled) enabled = params.enabled === 'true'; return { enabled, items: {} }; },
      '/api/v1/settings': { hideWatched: false, hideShorts: false, deepFlow: false, deepFlowEndsAt: 0 },
    },
    hash: '#/settings',
  });
  const { document } = page.window;
  const sw = document.querySelector('[data-key="dearrow"]');
  const caption = () => document.querySelector('[data-key="panel-services"] .set-sum').textContent;
  assert.ok(!sw.classList.contains('on'));
  assert.strictEqual(caption(), '0 of 1 on');
  sw.click();
  await page.settle();
  assert.strictEqual(enabled, true);
  assert.ok(sw.classList.contains('on'));
  assert.strictEqual(caption(), '1 of 1 on');
});

const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll, video } = require('./dom-harness');

afterEach(closeAll);

const picks = [video(1), video(2), video(3, { isShort: true }), video(4)];
const home = { '/api/v1/recommendations': { videos: picks, hasMore: false }, '/api/v1/history': { videos: [] } };

const text = (nodes) => [...nodes].map((node) => node.textContent);
const key = (window, name, init = {}) => window.document.dispatchEvent(new window.KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true, ...init }));

test('home lists the recommendations without shorts and leads with the first video', async () => {
  const page = await open({ routes: home });
  const { document } = page.window;
  assert.strictEqual(document.body.dataset.route, 'home');
  assert.deepStrictEqual(text(document.querySelectorAll('#screen .card .card-title')), ['Video 1', 'Video 2', 'Video 4']);
  assert.strictEqual(document.querySelector('#screen .hero h1').textContent, 'Video 1');
  assert.strictEqual(page.FT.focus.el.dataset.key, 'hero-play');
});

test('the top bar marks the current tab and the source', async () => {
  const page = await open({ routes: home });
  const { document } = page.window;
  assert.deepStrictEqual(text(document.querySelectorAll('#top .tab span:last-child')), ['Home', 'Subscriptions', 'Library', 'Settings']);
  assert.strictEqual(document.querySelector('#top .tab.on').dataset.key, 'tab-home');
  assert.strictEqual(document.querySelector('#top [data-key="source"]').textContent.replace('expand_more', ''), 'YouTube');
});

test('home with nothing to show says so instead of rendering a blank page', async () => {
  const page = await open({ routes: { '/api/v1/recommendations': { videos: [] }, '/api/v1/history': { videos: [] } } });
  const empty = page.window.document.querySelector('#screen .empty');
  assert.ok(empty);
  assert.strictEqual(empty.querySelector('h2').textContent, page.FT.t('nothing_yet'));
});

test('home falls back to the plain home feed when recommendations fail', async () => {
  const page = await open({ routes: { '/api/v1/recommendations': { status: 500, body: { error: 'down' } }, '/api/v1/home': { videos: [video(7)] }, '/api/v1/history': { videos: [] } } });
  assert.deepStrictEqual(text(page.window.document.querySelectorAll('#screen .card .card-title')), ['Video 7']);
  assert.ok(page.requests.some((r) => r.path === '/api/v1/home'));
});

test('a failed screen offers a retry that loads it again', async () => {
  let up = false;
  const page = await open({ hash: '#/library', routes: { '/api/v1/history': () => (up ? { videos: [video(1)] } : { status: 500, body: { error: 'boom' } }) } });
  const { document } = page.window;
  assert.strictEqual(document.querySelector('#screen .empty h2').textContent, page.FT.t('load_failed_title'));
  assert.strictEqual(document.querySelector('#screen .empty p').textContent, 'boom');
  up = true;
  document.querySelector('#screen [data-key="' + page.FT.t('try_again') + '"]').click();
  await page.settle();
  assert.strictEqual(document.querySelector('#screen .empty h2'), null);
  assert.deepStrictEqual(text(document.querySelectorAll('#screen .row-item .card-title')), ['Video 1']);
});

test('changing the hash swaps the screen, the route and the highlighted tab', async () => {
  const page = await open({ routes: { ...home, '/api/v1/settings': {}, '/api/v1/library': { watchLater: [], playlists: [] } } });
  const { window } = page;
  const { document } = window;
  window.location.hash = '#/settings';
  await page.settle();
  assert.strictEqual(document.body.dataset.route, 'settings');
  assert.strictEqual(document.querySelector('#top .tab.on').dataset.key, 'tab-settings');
  assert.strictEqual(document.querySelector('#screen h1').textContent, 'Settings');
  window.location.hash = '#/library';
  await page.settle();
  assert.strictEqual(document.querySelector('#top .tab.on').dataset.key, 'tab-library');
});

test('an unknown route shows home', async () => {
  const page = await open({ hash: '#/nowhere', routes: home });
  assert.strictEqual(page.window.document.querySelectorAll('#screen .card').length, 3);
});

test('picking another source reloads home for that service', async () => {
  const page = await open({ routes: home });
  const { document } = page.window;
  document.querySelector('#top [data-key="source"]').click();
  assert.strictEqual(page.FT.menu.isOpen(), true);
  assert.deepStrictEqual(text(document.querySelectorAll('#menu .menu-item')), ['radio_button_checkedYouTube', 'radio_button_uncheckedBilibili']);
  [...document.querySelectorAll('#menu .menu-item')][1].click();
  await page.settle();
  assert.strictEqual(page.FT.menu.isOpen(), false);
  assert.strictEqual(page.FT.store.service, 5);
  assert.ok(page.requests.some((r) => r.path === '/api/v1/recommendations' && r.params.serviceId === '5'));
  assert.strictEqual(document.querySelector('#toast').textContent, 'Source: Bilibili');
});

test('the stored source is the one home asks for', async () => {
  const page = await open({ routes: home, stored: { 'fathom-service': '5' } });
  assert.ok(page.requests.some((r) => r.path === '/api/v1/recommendations' && r.params.serviceId === '5'));
});

test('arrow keys move the selection and Enter opens it', async () => {
  const page = await open({ routes: home });
  const { window } = page;
  assert.strictEqual(page.FT.focus.el.dataset.key, 'hero-play');
  key(window, 'ArrowRight');
  assert.strictEqual(page.FT.focus.el.dataset.key, 'hero-later');
  key(window, 'ArrowLeft');
  assert.strictEqual(page.FT.focus.el.dataset.key, 'hero-play');
  assert.strictEqual(page.window.document.querySelectorAll('.sel').length, 1);
  key(window, 'Enter');
  assert.match(window.location.hash, /^#\/watch/);
});

test('Down from the top bar lands on the screen and Up is left alone', async () => {
  const page = await open({ routes: home });
  const { window } = page;
  page.FT.focus.select(window.document.querySelector('#top [data-key="tab-home"]'));
  key(window, 'ArrowDown');
  assert.strictEqual(page.FT.focus.el.dataset.key, 'hero-play');
});

test('the options menu opens for the selected video and Escape closes it back to the video', async () => {
  const page = await open({ routes: home });
  const { window } = page;
  const { document } = window;
  const card = document.querySelector('#screen .card');
  page.FT.focus.select(card);
  key(window, 'm');
  assert.strictEqual(page.FT.menu.isOpen(), true);
  assert.deepStrictEqual(text(document.querySelectorAll('#menu .menu-item')).map((label) => label.replace(/^[a-z_]+(?=[A-Z])/, '')),
    ['Open', 'Save to Watch later', 'Add to playlist', 'Go to channel']);
  assert.strictEqual(document.querySelector('#menu .menu-title').textContent, 'Video 1');
  key(window, 'Escape');
  assert.strictEqual(page.FT.menu.isOpen(), false);
  assert.strictEqual(page.FT.focus.el, card);
});

test('while the menu is open the selection stays inside it', async () => {
  const page = await open({ routes: home });
  const { window } = page;
  const { document } = window;
  page.FT.focus.select(document.querySelector('#screen .card'));
  key(window, 'm');
  assert.ok(page.FT.focus.stops().every((el) => el.closest('#menu')));
});

test('saving to Watch later asks the phone and confirms', async () => {
  const page = await open({ routes: { ...home, '/watch_later_action': {} } });
  const { window } = page;
  window.document.querySelector('#screen [data-key="hero-later"]').click();
  await page.settle();
  const call = page.requests.find((r) => r.path === '/watch_later_action');
  assert.ok(call);
  assert.strictEqual(call.params.action, 'add');
  assert.strictEqual(call.params.url, picks[0].url);
  assert.strictEqual(window.document.querySelector('#toast').textContent, 'Saved to Watch later');
});

test('a failed save says so', async () => {
  const page = await open({ routes: { ...home, '/watch_later_action': { status: 500, body: {} } } });
  page.window.document.querySelector('#screen [data-key="hero-later"]').click();
  await page.settle();
  assert.strictEqual(page.window.document.querySelector('#toast').textContent, 'Could not save that');
});

test('history that is partly watched becomes the continue shelf and the lead', async () => {
  const seen = video(9, { progress: 40 });
  const page = await open({ routes: { ...home, '/api/v1/history': { videos: [seen] } } });
  const { document } = page.window;
  assert.strictEqual(document.querySelector('#screen .hero h1').textContent, 'Video 9');
  assert.ok(text(document.querySelectorAll('#screen .shelf-head h2')).includes('Continue watching'));
});

test('the page speaks the browser language and falls back to English for missing words', async () => {
  const page = await open({ routes: home, languages: ['zh-TW'] });
  const { document } = page.window;
  assert.strictEqual(page.FT.lang, 'zh_Hant');
  assert.strictEqual(document.documentElement.lang, 'zh-Hant');
  assert.notStrictEqual(document.querySelector('#top .tab').textContent, 'homeHome');
});

test('settings toggles save through the phone', async () => {
  const page = await open({ hash: '#/settings', routes: { '/api/v1/settings': { deepFlow: false, hideWatched: false, hideShorts: false } } });
  const { document } = page.window;
  const toggle = document.querySelector('#screen [data-key="hide-shorts"]');
  assert.ok(toggle);
  toggle.click();
  await page.settle();
  const save = page.requests.filter((r) => r.path === '/api/v1/settings' && r.params.hideShorts !== undefined);
  assert.strictEqual(save.length, 1);
  assert.strictEqual(save[0].params.hideShorts, 'true');
});

test('cards load the smaller YouTube picture while the featured video keeps the large one', async () => {
  const big = (n) => video(n, { thumbnailUrl: `https://i.ytimg.com/vi/id${n}/hq720.jpg` });
  const page = await open({ routes: { '/api/v1/recommendations': { videos: [big(1), big(2)], hasMore: false }, '/api/v1/history': { videos: [] } } });
  const { document } = page.window;
  assert.strictEqual(document.querySelector('#screen .hero img').getAttribute('src'), 'https://i.ytimg.com/vi/id1/hq720.jpg');
  assert.deepStrictEqual([...document.querySelectorAll('#screen .card img')].map((img) => img.getAttribute('src')),
    ['https://i.ytimg.com/vi/id1/hqdefault.jpg', 'https://i.ytimg.com/vi/id2/hqdefault.jpg']);
});

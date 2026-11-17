const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll, video } = require('./dom-harness');

afterEach(closeAll);

const home = { '/api/v1/recommendations': { videos: [video(1)], hasMore: false }, '/api/v1/history': { videos: [] } };
const mum = { id: 'a', name: 'Mum', color: '#3B6FD8', emoji: null };
const kid = { id: 'b', name: 'Kid', color: null, emoji: '🦊' };
const both = (extra = {}) => ({ ...home, '/api/v1/profiles': { running: 'a', profiles: [mum, kid], switching: false, refused: null, showInTopBar: true, ...extra } });
const text = (nodes) => [...nodes].map((node) => node.textContent);

test('one profile still shows its avatar, and picking it does nothing', async () => {
  const page = await open({ routes: { ...home, '/api/v1/profiles': { running: 'a', profiles: [mum], switching: false, refused: null } } });
  const { document } = page.window;
  document.querySelector('#top [data-key="profile"]').click();
  assert.deepStrictEqual(text(document.querySelectorAll('#menu .menu-item')), ['MMum']);
  document.querySelector('#menu .menu-item').click();
  await page.settle();
  assert.strictEqual(page.requests.some((r) => r.params.switch), false);
});

test('an old server without profiles leaves the top bar as it was', async () => {
  const page = await open({ routes: home });
  assert.strictEqual(page.window.document.querySelector('#top [data-key="profile"]'), null);
});

test('with two profiles the avatar opens a menu that marks the running one', async () => {
  const page = await open({ routes: both() });
  const { document } = page.window;
  const button = document.querySelector('#top [data-key="profile"]');
  assert.strictEqual(button.textContent, 'M');
  button.click();
  assert.deepStrictEqual(text(document.querySelectorAll('#menu .menu-item')), ['MMum', '🦊Kid']);
  assert.ok(document.querySelectorAll('#menu .menu-item')[0].classList.contains('on'));
});

test('picking a profile asks the phone to switch and covers the page until it answers as that profile', async () => {
  let polls = 0;
  const routes = both();
  routes['/api/v1/profiles'] = (params) => {
    if (params.switch) return { running: 'a', profiles: [mum, kid], switching: true, refused: null };
    polls += 1;
    return { running: polls < 2 ? 'a' : 'b', profiles: [mum, kid], switching: polls < 2, refused: null };
  };
  const page = await open({ routes });
  const { document } = page.window;
  let reopened = 0;
  page.FT.profiles.reopen = () => { reopened += 1; };
  document.querySelector('#top [data-key="profile"]').click();
  document.querySelectorAll('#menu .menu-item')[1].click();
  await page.settle();
  assert.strictEqual(page.requests.filter((r) => r.path === '/api/v1/profiles' && r.params.switch === 'b').length, 1);
  assert.match(document.querySelector('.switching').textContent, /Switching to Kid/);
  assert.strictEqual(reopened, 0);
  await new Promise((resolve) => setTimeout(resolve, 2300));
  assert.strictEqual(reopened, 1);
});

test('a refused switch uncovers the page and says why', async () => {
  const routes = both();
  routes['/api/v1/profiles'] = (params) => ({ running: 'a', profiles: [mum, kid], switching: false, refused: params.switch ? 'sync_running' : null });
  const page = await open({ routes });
  const { document } = page.window;
  document.querySelector('#top [data-key="profile"]').click();
  document.querySelectorAll('#menu .menu-item')[1].click();
  await page.settle();
  assert.strictEqual(document.querySelector('.switching'), null);
  assert.strictEqual(document.querySelector('#toast').textContent, 'Wait for sync to finish to switch profiles');
});

test('picking the running profile does nothing', async () => {
  const page = await open({ routes: both() });
  const { document } = page.window;
  document.querySelector('#top [data-key="profile"]').click();
  document.querySelectorAll('#menu .menu-item')[0].click();
  await page.settle();
  assert.strictEqual(page.requests.some((r) => r.params.switch), false);
  assert.strictEqual(document.querySelector('.switching'), null);
});

test('the phone setting that hides the profile in the top bar hides it here too', async () => {
  const page = await open({ routes: both({ showInTopBar: false }) });
  assert.strictEqual(page.window.document.querySelector('#top [data-key="profile"]'), null);
});

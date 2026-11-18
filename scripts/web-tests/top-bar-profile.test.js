const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll } = require('./dom-harness');

afterEach(closeAll);

const mum = { id: 'a', name: 'Mum', color: '#3366CC', emoji: null };
const phone = (state) => ({
  '/api/v1/settings': (params) => {
    if (params.profileInTopBar) state.shown = params.profileInTopBar === 'true';
    return { hideWatched: false, hideShorts: false, deepFlow: false, deepFlowEndsAt: 0, profileInTopBar: state.shown };
  },
  '/api/v1/profiles': () => ({ running: 'a', profiles: [mum, { ...mum, id: 'b', name: 'Kid' }], switching: false, refused: null, showInTopBar: state.shown }),
});
const topBarProfile = (document) => document.querySelector('[data-key="top-bar-profile"]');

test('the switch in settings shows or hides the profile in the top bar on the phone', async () => {
  const state = { shown: true };
  const page = await open({ routes: phone(state), hash: '#/settings' });
  const sw = topBarProfile(page.window.document);
  assert.ok(sw.classList.contains('on'));
  sw.click();
  await page.settle();
  assert.strictEqual(state.shown, false);
  assert.ok(!sw.classList.contains('on'));
  assert.ok(page.requests.some((r) => r.path === '/api/v1/settings' && r.params.profileInTopBar === 'false'));
});

test('a change made on the phone moves the switch', async () => {
  const state = { shown: true };
  const page = await open({ routes: phone(state), hash: '#/settings' });
  state.shown = false;
  page.sockets[0].message('settings');
  await page.settle(80);
  assert.ok(!topBarProfile(page.window.document).classList.contains('on'));
});

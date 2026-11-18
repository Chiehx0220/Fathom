const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll, video } = require('./dom-harness');

afterEach(closeAll);

const home = { '/api/v1/recommendations': { videos: [video(1)], hasMore: false }, '/api/v1/history': { videos: [] } };
const text = (nodes) => [...nodes].map((node) => node.textContent);
const CATEGORIES = ['sponsor', 'intro', 'outro', 'selfpromo', 'interaction', 'music_offtopic'];

const routes = () => {
  const state = { sb: { enabled: false, categories: CATEGORIES.map((id) => ({ id, action: 'SKIP' })), actions: ['SKIP', 'SKIP_BUTTON', 'IGNORE', 'DISABLED'] }, wellbeing: { breakEnabled: false, breakMinutes: 30, breakOptions: [15, 30, 60] } };
  return {
    state,
    routes: {
      ...home,
      '/api/v1/settings': { hideWatched: false, hideShorts: false, deepFlow: false, deepFlowEndsAt: 0 },
      '/api/v1/sponsorblock': (params) => {
        if (params.enabled) state.sb.enabled = params.enabled === 'true';
        if (params.category) state.sb.categories.find((c) => c.id === params.category).action = params.action;
        return state.sb;
      },
      '/api/v1/wellbeing': (params) => {
        if (params.breakEnabled) state.wellbeing.breakEnabled = params.breakEnabled === 'true';
        if (params.breakMinutes) state.wellbeing.breakMinutes = Number(params.breakMinutes);
        return state.wellbeing;
      },
      '/api/v1/blocked': { channels: [{ id: 'https://www.youtube.com/channel/UC1', name: 'Noisy' }, { id: 'https://www.youtube.com/@quiet', name: '' }] },
      '/block_channel': {},
    },
  };
};

test('settings lists SponsorBlock, wellbeing and blocked channels from the phone', async () => {
  const page = await open({ routes: routes().routes, hash: '#/settings' });
  const headings = text(page.window.document.querySelectorAll('#screen h2'));
  assert.ok(['SponsorBlock', 'Wellbeing', 'Blocked channels'].every((h) => headings.includes(h)), headings.join('|'));
});

test('a SponsorBlock category action is saved and marked', async () => {
  const { routes: r, state } = routes();
  const page = await open({ routes: r, hash: '#/settings' });
  const { document } = page.window;
  document.querySelector('[data-key="sb-intro-IGNORE"]').click();
  await page.settle();
  assert.strictEqual(state.sb.categories.find((c) => c.id === 'intro').action, 'IGNORE');
  assert.ok(document.querySelector('[data-key="sb-intro-IGNORE"]').classList.contains('on'));
  assert.ok(document.querySelector('[data-key="sb-sponsor-SKIP"]').classList.contains('on'));
});

test('the break reminder saves its switch and its interval', async () => {
  const { routes: r, state } = routes();
  const page = await open({ routes: r, hash: '#/settings' });
  const { document } = page.window;
  document.querySelector('[data-key="break"]').click();
  document.querySelector('[data-key="break-every-60"]').click();
  await page.settle();
  assert.strictEqual(state.wellbeing.breakEnabled, true);
  assert.strictEqual(state.wellbeing.breakMinutes, 60);
});

test('a blocked channel is named, falls back to its handle, and unblocks', async () => {
  const page = await open({ routes: routes().routes, hash: '#/settings' });
  const { document } = page.window;
  const rows = text(document.querySelectorAll('[data-key^="unblock:"]')).length;
  assert.strictEqual(rows, 2);
  const names = text(document.querySelectorAll('.setting b'));
  assert.ok(names.includes('Noisy'));
  assert.ok(names.includes('@quiet'));
  document.querySelector('[data-key="unblock:https://www.youtube.com/channel/UC1"]').click();
  await page.settle();
  assert.ok(page.requests.some((r) => r.path === '/block_channel' && r.params.action === 'unblock'));
  assert.strictEqual(document.querySelectorAll('[data-key^="unblock:"]').length, 1);
});

test('a section whose endpoint is missing is left out and the page still renders', async () => {
  const { routes: r } = routes();
  delete r['/api/v1/wellbeing'];
  const page = await open({ routes: r, hash: '#/settings' });
  const headings = text(page.window.document.querySelectorAll('#screen h2'));
  assert.ok(headings.includes('SponsorBlock'));
  assert.ok(!headings.includes('Wellbeing'));
});

const segments = async (list, time) => {
  const page = await open({ routes: home });
  const P = page.FT.playerCore;
  const media = { currentTime: time };
  P.media = () => media;
  P.current = { segments: list };
  return { P, media, document: page.window.document };
};
const seg = (a) => ({ s: 4, e: 10, c: 'sponsor', l: 'sponsor', a });

test('a segment set to skip jumps past itself', async () => {
  const { P, media } = await segments([seg('skip')], 5);
  P.segments.watch();
  assert.strictEqual(media.currentTime, 10);
});

test('a segment set to skip once is skipped the first time only', async () => {
  const { P, media } = await segments([seg('once')], 5);
  P.segments.watch();
  assert.strictEqual(media.currentTime, 10);
  media.currentTime = 5;
  P.segments.watch();
  assert.strictEqual(media.currentTime, 5);
});

test('button segments show the skip button, mark-only and old servers behave as before', async () => {
  const button = await segments([seg('button')], 5);
  button.P.segments.watch();
  assert.strictEqual(button.document.querySelector('#skip-btn').hidden, false);
  const show = await segments([seg('show')], 5);
  show.P.segments.watch();
  assert.strictEqual(show.document.querySelector('#skip-btn').hidden, true);
  assert.strictEqual(show.media.currentTime, 5);
  const plain = await segments([{ s: 4, e: 10, c: 'sponsor', l: 'sponsor' }], 5);
  plain.P.segments.watch();
  assert.strictEqual(plain.document.querySelector('#skip-btn').hidden, false);
});

test('a choice row is folded to its current value until opened', async () => {
  const { window } = await open({ routes: routes().routes, hash: '#/settings' });
  const document = window.document;
  const head = document.querySelector('[data-key="sb-intro"]');
  const row = head.closest('.fold');
  assert.ok(!row.classList.contains('open'));
  assert.strictEqual(head.getAttribute('aria-expanded'), 'false');
  assert.ok(head.textContent.includes('Skip'));
  head.click();
  assert.ok(row.classList.contains('open'));
  assert.strictEqual(head.getAttribute('aria-expanded'), 'true');
});

test('changes made on the phone show on the open settings page', async () => {
  const { routes: r, state } = routes();
  const page = await open({ routes: r, hash: '#/settings' });
  const { document } = page.window;
  const on = (key) => document.querySelector(`[data-key="${key}"]`).classList.contains('on');
  assert.strictEqual(on('sponsorblock'), false);
  state.sb.enabled = true;
  state.sb.categories.find((c) => c.id === 'intro').action = 'DISABLED';
  state.wellbeing.breakEnabled = true;
  state.wellbeing.breakMinutes = 60;
  document.dispatchEvent(new page.window.Event('visibilitychange'));
  await page.settle();
  assert.strictEqual(on('sponsorblock'), true);
  assert.strictEqual(on('break'), true);
  assert.ok(on('sb-intro-DISABLED'));
  assert.ok(on('break-every-60'));
  assert.ok(document.querySelector('[data-key="sb-intro"]').textContent.includes('Off'));
});

test('a refresh does not rebuild unchanged rows', async () => {
  const { routes: r } = routes();
  const page = await open({ routes: r, hash: '#/settings' });
  const { document } = page.window;
  const before = document.querySelector('[data-key="sb-intro-SKIP"]');
  document.dispatchEvent(new page.window.Event('visibilitychange'));
  await page.settle();
  assert.strictEqual(document.querySelector('[data-key="sb-intro-SKIP"]'), before);
});

test('the blocked list follows the phone', async () => {
  const { routes: r } = routes();
  let channels = [{ id: 'https://www.youtube.com/channel/UC1', name: 'Noisy' }];
  r['/api/v1/blocked'] = () => ({ channels });
  const page = await open({ routes: r, hash: '#/settings' });
  const { document } = page.window;
  assert.strictEqual(document.querySelectorAll('[data-key^="unblock:"]').length, 1);
  channels = [...channels, { id: 'https://www.youtube.com/channel/UC2', name: 'Loud' }];
  document.dispatchEvent(new page.window.Event('visibilitychange'));
  await page.settle();
  assert.strictEqual(document.querySelectorAll('[data-key^="unblock:"]').length, 2);
  channels = [];
  document.dispatchEvent(new page.window.Event('visibilitychange'));
  await page.settle();
  assert.strictEqual(document.querySelectorAll('[data-key^="unblock:"]').length, 0);
});

const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll, video } = require('./dom-harness');

afterEach(closeAll);

const key = (window, name) => window.document.dispatchEvent(new window.KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
const gathered = (page) => page.settle(40);
const picks = [video(1, { channelId: 'https://www.youtube.com/channel/UCai' }), video(2, { channelId: 'https://www.youtube.com/channel/UCok' })];
const base = (extra = {}) => ({
  '/api/v1/recommendations': { videos: picks, hasMore: false },
  '/api/v1/history': { videos: [] },
  ...extra,
});

test('cards of flagged channels carry a label, others do not; one request asks for all', async () => {
  const verdicts = { 'https://www.youtube.com/channel/UCai': 'warn' };
  const page = await open({ routes: base({ '/api/v1/ailist': { block: false, warn: true, allowed: [], verdicts } }) });
  await gathered(page);
  const tags = [...page.window.document.querySelectorAll('#screen .card')].map((card) => !!card.querySelector('.ai-tag'));
  assert.deepStrictEqual(tags, [true, false]);
  const asks = page.requests.filter((r) => r.path === '/api/v1/ailist' && r.params.channels);
  assert.strictEqual(asks.length, 1);
  assert.deepStrictEqual(asks[0].params.channels.split(',').slice(0, 2), ['https://www.youtube.com/channel/UCai', 'https://www.youtube.com/channel/UCok']);
});

test('with both lists off, nothing is labelled and the phone is not asked again', async () => {
  const page = await open({ routes: base({ '/api/v1/ailist': { block: false, warn: false, allowed: [], verdicts: {} } }) });
  await gathered(page);
  const asked = () => page.requests.filter((r) => r.path === '/api/v1/ailist' && r.params.channels).length;
  assert.strictEqual(page.window.document.querySelectorAll('.ai-tag').length, 0);
  assert.strictEqual(asked(), 1);
  page.FT.videoCard(picks[0]);
  await gathered(page);
  assert.strictEqual(asked(), 1);
});

test('a flagged channel page says so and can allow the channel', async () => {
  const channel = { id: 'https://www.youtube.com/channel/UCai', name: 'Robo', thumbnailUrl: '', subscriberCount: 3, isSubscribed: false, isNotifying: false };
  const calls = [];
  const page = await open({
    routes: base({
      '/api/v1/channel': { channel, videos: [], nextPage: null },
      '/api/v1/state': { subscribed: false, notify: false, blocked: false, watchLater: false, like: null },
      '/api/v1/ailist': (params) => { calls.push(params); return { block: true, warn: true, allowed: [], verdicts: params.channels ? { [channel.id]: 'block' } : {} }; },
    }),
    hash: '#/channel?s=0&u=UCai',
  });
  await gathered(page);
  const { document } = page.window;
  const note = document.querySelector('.ai-note');
  assert.ok(note && !note.hidden);
  document.querySelector('[data-key="ai-allow"]').click();
  await page.settle();
  const allow = calls.find((params) => params.allow);
  assert.strictEqual(allow.allow, channel.id);
  assert.strictEqual(allow.name, 'Robo');
  assert.ok(note.hidden);
});

const buttons = (page, likeCount) => {
  const like = page.FT.button(likeCount > 0 ? page.FT.count(likeCount) : 'Like', 'thumb_up', null, '', 'like');
  const dislike = page.FT.button('Dislike', 'thumb_down', null, 'round', 'dislike');
  return { like, dislike };
};
const info = (likeCount) => ({ url: 'https://www.youtube.com/watch?v=abcdefghij1', likeCount });

test('the dislike count and the missing likes come from the phone', async () => {
  const page = await open({ routes: base({ '/api/v1/dislikes': { enabled: true, dislikes: 1234, likes: 56789 } }) });
  const { like, dislike } = buttons(page, -1);
  await page.FT.dislikeCounts(info(-1), like, dislike);
  assert.strictEqual(dislike.querySelector('.count').textContent, page.FT.count(1234));
  assert.ok(like.textContent.includes(page.FT.count(56789)));
  assert.strictEqual(page.requests.find((r) => r.path === '/api/v1/dislikes').params.id, info(-1).url);
});

test('the page keeps its own like count when it has one', async () => {
  const page = await open({ routes: base({ '/api/v1/dislikes': { enabled: true, dislikes: 10, likes: 99 } }) });
  const { like, dislike } = buttons(page, 500);
  await page.FT.dislikeCounts(info(500), like, dislike);
  assert.ok(like.textContent.includes(page.FT.count(500)));
  assert.ok(!like.textContent.includes(page.FT.count(99)));
});

test('switched off or unanswered, the buttons stay plain', async () => {
  for (const answer of [{ enabled: false }, { status: 500, body: {} }]) {
    const page = await open({ routes: base({ '/api/v1/dislikes': answer }) });
    const { like, dislike } = buttons(page, 5);
    await page.FT.dislikeCounts(info(5), like, dislike);
    assert.ok(!dislike.querySelector('.count'));
  }
});

test('settings switch Return YouTube Dislike and the AiSList lists on the phone, and list allowed channels', async () => {
  const state = { dislikes: true, block: false, warn: false, allowed: [{ id: 'https://www.youtube.com/channel/UCa', name: 'Allowed One' }] };
  const page = await open({
    routes: {
      ...base(),
      '/api/v1/settings': { hideWatched: false, hideShorts: false, deepFlow: false, deepFlowEndsAt: 0 },
      '/api/v1/dislikes': (params) => { if (params.enabled) state.dislikes = params.enabled === 'true'; return { enabled: state.dislikes }; },
      '/api/v1/ailist': (params) => {
        if (params.block) state.block = params.block === 'true';
        if (params.warn) state.warn = params.warn === 'true';
        if (params.disallow) state.allowed = state.allowed.filter((c) => c.id !== params.disallow);
        return { block: state.block, warn: state.warn, channelCount: 1234, updatedAt: 0, allowed: state.allowed, verdicts: {} };
      },
    },
    hash: '#/settings',
  });
  const { document } = page.window;
  const on = (name) => document.querySelector(`[data-key="${name}"]`).classList.contains('on');
  assert.ok(on('rytd'));
  assert.ok(!on('ai-block'));
  document.querySelector('[data-key="rytd"]').click();
  document.querySelector('[data-key="ai-block"]').click();
  await page.settle();
  assert.strictEqual(state.dislikes, false);
  assert.strictEqual(state.block, true);
  assert.ok(document.querySelector('[data-key="ai-block"]').classList.contains('on'));
  assert.ok(document.body.textContent.includes('1,234 channels, updated'));
  assert.ok(document.body.textContent.includes('Allowed One'));
  document.querySelector('[data-key="ai-flag:https://www.youtube.com/channel/UCa"]').click();
  await page.settle();
  assert.deepStrictEqual(state.allowed, []);
  assert.ok(!document.body.textContent.includes('Allowed One'));
  key(page.window, 'Escape');
});

const { test, afterEach } = require('node:test');
const assert = require('node:assert');
const { open, closeAll, video } = require('./dom-harness');

afterEach(closeAll);

const home = { '/api/v1/recommendations': { videos: [video(1)], hasMore: false }, '/api/v1/history': { videos: [] } };
const list = [1, 2, 3, 4].map((n) => video(n));
const titles = (videos) => Array.from(videos, (v) => v.title);
const plain = (value) => JSON.parse(JSON.stringify(value));

test('starting a queue dedupes, opens the first video and reports the position', async () => {
  const { FT, window } = await open({ routes: home });
  FT.queue.start([...list, list[0]]);
  assert.ok(window.location.hash.includes(encodeURIComponent(list[0].url)));
  assert.strictEqual(FT.queue.active(), true);
  assert.deepStrictEqual(plain(FT.queue.position()), { at: 1, of: 4 });
  assert.deepStrictEqual(titles(FT.queue.upcoming()), ['Video 2', 'Video 3', 'Video 4']);
});

test('next and previous follow the list; the end is null unless looping all', async () => {
  const { FT } = await open({ routes: home });
  FT.queue.start(list, { from: 2 });
  assert.strictEqual(FT.queue.current().title, 'Video 3');
  assert.strictEqual(FT.queue.previous().title, 'Video 2');
  assert.strictEqual(FT.queue.next().title, 'Video 4');
  FT.queue.visit(list[3].url);
  assert.strictEqual(FT.queue.next(), null);
  FT.queue.setLoop('all');
  assert.strictEqual(FT.queue.next().title, 'Video 1');
});

test('visiting a video of the queue moves the position, an unrelated video leaves it alone', async () => {
  const { FT } = await open({ routes: home });
  FT.queue.start(list);
  FT.queue.visit(list[2].url);
  assert.strictEqual(FT.queue.position().at, 3);
  FT.queue.visit('https://www.youtube.com/watch?v=other');
  assert.strictEqual(FT.queue.position().at, 3);
});

test('shuffle keeps the current video first and every video once, and turning it off restores list order', async () => {
  const { FT } = await open({ routes: home });
  FT.queue.start(list, { shuffle: true, from: 1 });
  assert.strictEqual(FT.queue.current().title, 'Video 2');
  assert.deepStrictEqual(titles([FT.queue.current(), ...FT.queue.upcoming()]).sort(), ['Video 1', 'Video 2', 'Video 3', 'Video 4']);
  FT.queue.toggleShuffle();
  assert.strictEqual(FT.queue.shuffle(), false);
  assert.strictEqual(FT.queue.current().title, 'Video 2');
  assert.deepStrictEqual(titles(FT.queue.upcoming()), ['Video 3', 'Video 4']);
  assert.strictEqual(FT.queue.previous().title, 'Video 1');
});

test('clearing the queue ends it', async () => {
  const { FT } = await open({ routes: home });
  FT.queue.start(list);
  FT.queue.clear();
  assert.strictEqual(FT.queue.active(), false);
  assert.strictEqual(FT.queue.next(), null);
});

test('Play all on a saved list starts a queue with its videos', async () => {
  const page = await open({ routes: { ...home, '/api/v1/library': { channels: [], playlists: [], mine: [], watchLater: list } }, hash: '#/library?tab=later' });
  page.window.document.querySelector('[data-key="play-all"]').click();
  assert.strictEqual(page.FT.queue.active(), true);
  assert.strictEqual(page.FT.queue.position().of, 4);
  page.window.document.querySelector('[data-key="shuffle-all"]');
});

const ended = async (loop, queueList = list) => {
  const page = await open({ routes: home });
  const { FT, window } = page;
  FT.queue.start(queueList);
  if (loop) FT.queue.setLoop(loop);
  const calls = [];
  FT.player.seekTo = (t) => calls.push(['seek', t]);
  FT.player.snapshot = () => ({ paused: true });
  FT.player.toggle = () => calls.push(['toggle']);
  const P = FT.playerCore;
  P.current = { info: { related: [video(9)] } };
  P.tree = { skin: window.document.createElement('div') };
  P.dockMode = 'full';
  return { FT, P, calls };
};

test('when a video ends, the queue decides what comes next', async () => {
  const { P } = await ended();
  P.upNext.schedule();
  assert.strictEqual(P.tree.skin.querySelector('.upnext-title').textContent, 'Video 2');
  P.upNext.cancel();
});

test('loop one replays the same video instead of offering another', async () => {
  const { P, calls } = await ended('one');
  P.upNext.schedule();
  assert.deepStrictEqual(calls, [['seek', 0], ['toggle']]);
  assert.strictEqual(P.tree.skin.querySelector('.upnext'), null);
});

test('the end of an unlooped queue offers nothing, not a related video', async () => {
  const { FT, P } = await ended(null, [list[0]]);
  assert.strictEqual(FT.queue.next(), null);
  P.upNext.schedule();
  assert.strictEqual(P.tree.skin.querySelector('.upnext'), null);
});

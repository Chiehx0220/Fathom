const test = require('node:test');
const assert = require('node:assert');
const { load } = require('./harness');

const STRINGS = {
  en: { hello: 'Hello {name}', only_en: 'English only', item_one: '{count} item', item_other: '{count} items', count_views: '{count} views' },
  zh_Hant: { hello: '哈囉 {name}' },
  zh_Hans: { hello: '你好 {name}' },
  pt_BR: { hello: 'Olá {name}' },
  pt: { hello: 'Oi {name}' },
};

const ft = (languages = ['en'], strings = STRINGS) => load({ languages, strings }).FT;

test('duration formats seconds as m:ss and h:mm:ss', () => {
  const { duration } = ft();
  assert.strictEqual(duration(0), '0:00');
  assert.strictEqual(duration(65), '1:05');
  assert.strictEqual(duration(3661), '1:01:01');
  assert.strictEqual(duration(-5), '0:00');
  assert.strictEqual(duration(undefined), '0:00');
  assert.strictEqual(duration(59.6), '1:00');
});

test('serviceOfUrl recognises Bilibili links and ids', () => {
  const { serviceOfUrl } = ft();
  assert.strictEqual(serviceOfUrl('https://www.bilibili.com/video/BV1GJ411x7h7'), 5);
  assert.strictEqual(serviceOfUrl('https://space.bilibili.com/546195'), 5);
  assert.strictEqual(serviceOfUrl('BV1GJ411x7h7'), 5);
  assert.strictEqual(serviceOfUrl('https://www.youtube.com/watch?v=dQw4w9WgXcQ'), 0);
  assert.strictEqual(serviceOfUrl(''), 0);
  assert.strictEqual(serviceOfUrl(undefined), 0);
});

test('t fills placeholders and falls back to English, then to the key', () => {
  const { t } = ft(['zh-TW']);
  assert.strictEqual(t('hello', { name: 'A' }), '哈囉 A');
  assert.strictEqual(t('only_en'), 'English only');
  assert.strictEqual(t('missing_key'), 'missing_key');
  assert.strictEqual(t('missing_key', undefined, 'fallback'), 'fallback');
  assert.strictEqual(t('hello', { other: 1 }), '哈囉 {name}');
});

test('tn picks the plural form and fills count', () => {
  const { tn } = ft();
  assert.strictEqual(tn('item', 1), '1 item');
  assert.strictEqual(tn('item', 3), '3 items');
});

test('language comes from the first browser language the strings cover', () => {
  assert.strictEqual(ft(['zh-TW']).lang, 'zh_Hant');
  assert.strictEqual(ft(['zh-HK']).lang, 'zh_Hant');
  assert.strictEqual(ft(['zh-CN']).lang, 'zh_Hans');
  assert.strictEqual(ft(['pt-BR']).lang, 'pt_BR');
  assert.strictEqual(ft(['pt-PT']).lang, 'pt');
  assert.strictEqual(ft(['xx', 'zh-TW']).lang, 'zh_Hant');
  assert.strictEqual(ft(['xx']).lang, 'en');
});

test('count and views leave out unknown counts', () => {
  const { count, views } = ft();
  assert.strictEqual(count(1500), '1.5K');
  assert.strictEqual(count(undefined), '');
  assert.strictEqual(count(-1), '');
  assert.strictEqual(views(0), '');
  assert.strictEqual(views(1500), '1.5K views');
});

test('h builds elements: text as text, props by kind, empty values skipped', () => {
  const { h } = ft();
  const el = h('a', { class: 'card', href: '#/x', data: { key: 'k' }, hidden: true, skip: null, off: false, onclick: () => {} },
    'a < b', 0, null, [['nested']]);
  assert.strictEqual(el.className, 'card');
  assert.strictEqual(el.attrs.href, '#/x');
  assert.strictEqual(el.attrs.hidden, '');
  assert.ok(!('skip' in el.attrs) && !('off' in el.attrs));
  assert.strictEqual(el.dataset.key, 'k');
  assert.strictEqual(el.listeners.click.length, 1);
  assert.deepStrictEqual(el.children.map((kid) => kid.text), ['a < b', '0', 'nested']);
});

test('a card picture asks YouTube for hqdefault instead of hq720 and leaves other addresses alone', () => {
  const { FT } = load({});
  assert.strictEqual(FT.cardThumb('https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg'), 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg');
  assert.strictEqual(FT.cardThumb('https://i.ytimg.com/vi/a-b_C/hq720.jpg'), 'https://i.ytimg.com/vi/a-b_C/hqdefault.jpg');
  for (const same of ['https://i.ytimg.com/vi/x/maxresdefault.jpg', 'https://i.ytimg.com/vi/x/hq720.jpg?sqp=1', 'https://i0.hdslb.com/bfs/archive/x.jpg', '']) {
    assert.strictEqual(FT.cardThumb(same), same);
  }
  assert.strictEqual(FT.cardThumb(undefined), '');
});

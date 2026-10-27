// jsdom page of the real shell (index.html) + the whole script bundle: `open(options)` → { window, FT, requests, settle }; `closeAll()` after each test.

const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');
const { ROOT, bundleFiles } = require('./harness');

const WEB = path.join(ROOT, 'app/src/main/assets/web');
const read = (file) => fs.readFileSync(path.join(WEB, file), 'utf8');

const strings = () =>
  '{' +
  fs.readdirSync(path.join(WEB, 'app/strings')).filter((name) => name.endsWith('.json')).sort()
    .map((name) => `"${name.replace(/\.json$/, '')}":${read('app/strings/' + name).trim()}`).join(',') +
  '}';

const shell = (services) =>
  read('app/index.html')
    .replace(/\{\{version\}\}/g, 'test')
    .replace('{{deviceColors}}', '')
    .replace('{{services}}', JSON.stringify(services))
    .replace('{{strings}}', strings());

const bundle = () => bundleFiles().map(read).join('\n');

// routes: { '/api/v1/home': body | (params) => body | { status, body } }; unknown paths answer 404.
const respond = (routes, requests) => async (input) => {
  const url = new URL(String(input), 'http://localhost');
  const params = Object.fromEntries(url.searchParams);
  requests.push({ path: url.pathname, params });
  let answer = routes[url.pathname];
  if (typeof answer === 'function') answer = answer(params);
  const failed = answer && answer.status;
  const status = failed ? answer.status : answer === undefined ? 404 : 200;
  const body = failed ? answer.body : answer;
  return { ok: status < 400, status, json: async () => body || {} };
};

// jsdom has no layout: every element gets a box, `[data-f]` controls laid out left to right in document order.
const layout = (window) => {
  window.Element.prototype.getBoundingClientRect = function () {
    const stops = Array.from(this.ownerDocument.querySelectorAll('[data-f]'));
    const index = stops.indexOf(this);
    const left = index < 0 ? 0 : index * 200;
    return { left, right: left + 180, top: 100, bottom: 160, width: 180, height: 60, x: left, y: 100 };
  };
};

const opened = [];
const closeAll = () => { while (opened.length) opened.pop().close(); };

const open = async ({
  routes = {},
  hash = '#/home',
  services = [[0, 'YouTube'], [5, 'Bilibili']],
  stored = {},
  languages = ['en'],
  pointer = false,
} = {}) => {
  const requests = [];
  const dom = new JSDOM(shell(services), {
    url: 'http://localhost/' + hash,
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    beforeParse(window) {
      Object.defineProperty(window.navigator, 'languages', { value: languages });
      Object.defineProperty(window.navigator, 'language', { value: languages[0] });
      for (const [key, value] of Object.entries(stored)) window.localStorage.setItem(key, value);
      window.fetch = respond(routes, requests);
      window.matchMedia = (query) => ({ matches: pointer && query.includes('hover: hover'), media: query, addEventListener() {}, removeEventListener() {} });
      window.scrollTo = () => {};
      window.Element.prototype.scrollIntoView = () => {};
      window.Element.prototype.scrollBy = () => {};
      for (const name of ['IntersectionObserver', 'ResizeObserver']) {
        window[name] = class { observe() {} unobserve() {} disconnect() {} };
      }
      layout(window);
    },
  });
  const { window } = dom;
  // The bundle reads its own `?v=` from the script tag that loads it.
  Object.defineProperty(window.document, 'currentScript', { value: { src: 'http://localhost/app.js?v=test' }, configurable: true });
  window.eval(bundle());
  // Pending fetches and the renders they unlock.
  const settle = async (rounds = 12) => { for (let i = 0; i < rounds; i++) await new Promise((resolve) => setTimeout(resolve, 2)); };
  opened.push(window);
  await settle();
  return { window, FT: window.FT, requests, settle };
};

const video = (n, extra = {}) => ({
  url: `https://www.youtube.com/watch?v=id${n}`,
  title: `Video ${n}`,
  channelName: `Channel ${n}`,
  channelId: `UC${n}`,
  serviceId: 0,
  duration: 60 * n,
  viewCount: 1000 * n,
  thumbnailUrl: '',
  ...extra,
});

module.exports = { open, closeAll, video };

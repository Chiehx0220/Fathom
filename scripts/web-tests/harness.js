// Loads the web app's plain scripts into a bare vm context (no DOM library): `load(options)` returns { FT, window, calls }.

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.join(__dirname, '..', '..');
const APP = path.join(ROOT, 'app/src/main/assets/web/app');
const WEB_ASSETS = path.join(ROOT, 'app/src/main/java/io/github/aedev/flow/localserver/WebAssets.kt');
const ROUTES = path.join(ROOT, 'app/src/main/java/io/github/aedev/flow/localserver/LocalHttpRoutes.kt');

// Bundle order, read from the server's own list.
const bundleFiles = () => {
  const source = fs.readFileSync(WEB_ASSETS, 'utf8');
  const list = source.match(/APP_JS_FILES\s*=\s*listOf\(([\s\S]*?)\)/);
  return [...list[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
};

const serverRoutes = () => {
  const source = fs.readFileSync(ROUTES, 'utf8');
  return new Set([...source.matchAll(/\b(?:page|api)\("(\/[^"]*)"\)/g)].map((m) => m[1]));
};

const element = (tag) => ({
  tag,
  nodeType: 1,
  className: '',
  dataset: {},
  attrs: {},
  children: [],
  listeners: {},
  setAttribute(name, value) { this.attrs[name] = value; },
  addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); },
  append(...kids) { this.children.push(...kids); },
});

// Scripts loaded by default: the ones that run without a page.
const DEFAULT_SCRIPTS = ['app/js/util.js', 'app/js/store.js', 'app/js/api.js'];

const load = ({
  scripts = DEFAULT_SCRIPTS,
  languages = ['en'],
  strings = { en: {} },
  config,
  stored = {},
  fetch,
} = {}) => {
  const calls = [];
  const storage = { ...stored };
  const listeners = {};
  const sandbox = {
    FT_STRINGS: strings,
    FT_CONFIG: config,
    navigator: { languages, language: languages[0] },
    document: {
      documentElement: { lang: '' },
      createElement: element,
      createTextNode: (text) => ({ nodeType: 3, text: String(text) }),
      querySelector: () => null,
    },
    localStorage: {
      getItem: (key) => (key in storage ? storage[key] : null),
      setItem: (key, value) => { storage[key] = String(value); },
    },
    location: { hostname: 'localhost' },
    fetch: fetch || (async (url) => { calls.push(String(url)); return { ok: true, json: async () => ({}) }; }),
    addEventListener: (type, fn) => { (listeners[type] ||= []).push(fn); },
    removeEventListener: () => {},
    dispatchEvent: (event) => { (listeners[event.type] || []).forEach((fn) => fn(event)); },
    Event: class { constructor(type) { this.type = type; } },
    Intl, URLSearchParams, Date, Promise, Object, Array, Math, JSON, String, Number, Map, Set, Error,
    setTimeout, clearTimeout, parseInt, encodeURIComponent,
  };
  sandbox.window = sandbox;
  const context = vm.createContext(sandbox);
  for (const file of scripts) {
    vm.runInContext(fs.readFileSync(path.join(APP, file.replace(/^app\//, '')), 'utf8'), context, { filename: file });
  }
  return { FT: sandbox.FT, window: sandbox, calls, storage };
};

module.exports = { ROOT, APP, bundleFiles, serverRoutes, load };

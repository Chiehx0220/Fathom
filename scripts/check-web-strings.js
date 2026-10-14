#!/usr/bin/env node
// Checks the words of the local web UI (app/src/main/assets/web/app/strings/<language>.json).
//
//   node scripts/check-web-strings.js           exit 1 on a problem
//   node scripts/check-web-strings.js --report  also list what each language has not translated
//
// en.json is the base: every key the page asks for (FT.t / FT.tn / data-t / data-t-label) must be there, every key there must
// be asked for, and the other languages may only hold keys en.json has, with the same {values} in each. A plural key is
// written name_one, name_other (and the forms a language needs); the page asks for the name.

const fs = require('fs');
const path = require('path');

const APP = path.join(__dirname, '..', 'app/src/main/assets/web/app');
const STRINGS = path.join(APP, 'strings');
const PLURAL = /_(zero|one|two|few|many|other)$/;

const read = (file) => JSON.parse(fs.readFileSync(file, 'utf8'));
const files = fs.readdirSync(STRINGS).filter((name) => name.endsWith('.json')).sort();
const languages = Object.fromEntries(files.map((name) => [name.replace(/\.json$/, ''), read(path.join(STRINGS, name))]));
const base = languages.en;
if (!base) { console.error('strings/en.json is missing'); process.exit(1); }

const problems = [];
const values = (text) => [...String(text).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',');
const plural = (key) => key.replace(PLURAL, '');

// ---- What the page asks for ----
const literal = new Set();   // 'a_key'
const prefixes = new Set();  // 'segment_' + ...
const sources = fs.readdirSync(path.join(APP, 'js')).filter((name) => name.endsWith('.js')).map((name) => path.join(APP, 'js', name));
for (const file of sources) {
  const text = fs.readFileSync(file, 'utf8');
  for (const call of text.matchAll(/\bFT\.tn?\(([^)]*)\)/g)) {
    // Both branches of a condition, and the first word of a key built from a variable.
    const args = call[1].replace(/[!=]==?\s*'[^']*'/g, ''); // a word compared with, as in dir === 'left', is no key
    for (const token of args.matchAll(/'([a-z0-9_]+)'(\s*\+)?/g)) {
      if (token[2]) prefixes.add(token[1]); else literal.add(token[1]);
    }
  }
}
const html = fs.readFileSync(path.join(APP, 'index.html'), 'utf8');
for (const m of html.matchAll(/data-t(?:-label)?="([a-z0-9_]+)"/g)) literal.add(m[1]);
const keysOfBase = new Set(Object.keys(base).map(plural));

for (const key of literal) {
  if (!keysOfBase.has(key) && !(key in base)) problems.push(`the page asks for "${key}", which en.json does not have`);
}
for (const key of Object.keys(base)) {
  const name = plural(key);
  const asked = literal.has(key) || literal.has(name) || [...prefixes].some((p) => key.startsWith(p));
  if (!asked) problems.push(`en.json has "${key}", which nothing asks for`);
}

// ---- The other languages ----
for (const [code, words] of Object.entries(languages)) {
  if (code === 'en') continue;
  for (const [key, text] of Object.entries(words)) {
    if (!(key in base) && !keysOfBase.has(plural(key))) { problems.push(`${code}: "${key}" is not in en.json`); continue; }
    const model = key in base ? base[key] : base[`${plural(key)}_other`] || base[`${plural(key)}_one`];
    if (values(text) !== values(model)) problems.push(`${code}: "${key}" has {${values(text)}}, en.json has {${values(model)}}`);
  }
}

if (process.argv.includes('--report')) {
  for (const [code, words] of Object.entries(languages)) {
    if (code === 'en') continue;
    const need = new Set(Object.keys(base).map(plural));
    const have = new Set(Object.keys(words).map(plural));
    const missing = [...need].filter((key) => !have.has(key));
    console.log(`${code}: ${have.size}/${need.size} translated${missing.length ? ', missing: ' + missing.join(', ') : ''}`);
  }
}

if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
console.log(`Web strings OK: ${Object.keys(base).length} keys in ${files.length} language files.`);

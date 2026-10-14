#!/usr/bin/env node
// Validates app/src/main/assets/web/app/strings/<language>.json against the web UI.
//
//   node scripts/check-web-strings.js           exit 1 on a problem
//   node scripts/check-web-strings.js --report  also list untranslated keys per language
//
// en.json is the base. Every key referenced via FT.t / FT.tn / data-t-label must exist in it, every key in it must be
// referenced, and other languages may only contain en.json keys with identical {placeholders}. Plural keys use CLDR
// suffixes (_one, _other, ...) and are referenced by their stem.

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

// ---- References in the UI ----
const literal = new Set();   // FT.t('key')
const prefixes = new Set();  // FT.t('prefix_' + x)
const sources = fs.readdirSync(path.join(APP, 'js')).filter((name) => name.endsWith('.js')).map((name) => path.join(APP, 'js', name));
for (const file of sources) {
  const text = fs.readFileSync(file, 'utf8');
  for (const call of text.matchAll(/\bFT\.tn?\(([^)]*)\)/g)) {
    // Drop comparison operands (x === 'left'); both branches of a ternary count as references.
    const args = call[1].replace(/[!=]==?\s*'[^']*'/g, '');
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

// ---- Other languages ----
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

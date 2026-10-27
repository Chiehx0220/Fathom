const test = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { APP } = require('./harness');

const css = fs.readFileSync(path.join(APP, 'css/fonts.css'), 'utf8');
const referenced = [...css.matchAll(/url\(\/fonts\/([^)]+)\)/g)].map((m) => m[1]);
const onDisk = fs.readdirSync(path.join(APP, 'fonts'));

test('every font face points at a file that exists', () => {
  assert.ok(referenced.length >= 4);
  for (const name of referenced) assert.ok(onDisk.includes(name), `${name} is missing from assets/web/app/fonts`);
});

test('no font file is left unused', () => {
  assert.deepStrictEqual([...onDisk].sort(), [...new Set(referenced)].sort());
});

test('a font file name carries the hash of its content, so a changed font is a new URL', () => {
  for (const name of onDisk) {
    const bytes = fs.readFileSync(path.join(APP, 'fonts', name));
    assert.strictEqual(bytes.subarray(0, 4).toString('latin1'), 'wOF2', `${name} is not WOFF2`);
    const hash = crypto.createHash('sha256').update(bytes).digest('hex').slice(0, 8);
    assert.match(name, new RegExp(`\\.${hash}\\.woff2$`), `${name} does not match its content hash ${hash}`);
  }
});

test('the stylesheet carries no embedded font data', () => {
  assert.ok(!/data:font/.test(css));
});

test('the preloaded fonts of the page shell are files that exist', () => {
  const shell = fs.readFileSync(path.join(APP, 'index.html'), 'utf8');
  const preloaded = [...shell.matchAll(/<link rel="preload" href="\/fonts\/([^"]+)"/g)].map((m) => m[1]);
  assert.ok(preloaded.length >= 1);
  for (const name of preloaded) assert.ok(onDisk.includes(name), `${name} is preloaded but missing`);
});

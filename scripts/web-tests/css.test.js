const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { APP } = require('./harness');

const sheets = fs.readdirSync(path.join(APP, 'css')).filter((name) => name.endsWith('.css'));
const read = (name) => fs.readFileSync(path.join(APP, 'css', name), 'utf8');

// A class name defined as a component base (`.name {`) in two sheets reshapes both users of it.
test('no component base class is defined in more than one stylesheet', () => {
  const owners = new Map();
  for (const name of sheets) {
    for (const [, cls] of read(name).matchAll(/^\.([a-z][\w-]*)\s*\{/gm)) {
      owners.set(cls, [...(owners.get(cls) || []), name]);
    }
  }
  const shared = [...owners].filter(([, files]) => new Set(files).size > 1).map(([cls, files]) => `.${cls}: ${[...new Set(files)].join(', ')}`);
  assert.deepStrictEqual(shared, []);
});

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { APP, bundleFiles } = require('./harness');

const onDisk = () => fs.readdirSync(path.join(APP, 'js')).filter((name) => name.endsWith('.js')).map((name) => `app/js/${name}`);

test('every bundled script exists', () => {
  for (const file of bundleFiles()) {
    assert.ok(fs.existsSync(path.join(APP, file.replace(/^app\//, ''))), `${file} is listed but missing`);
  }
});

test('every script on disk is in the bundle', () => {
  const listed = new Set(bundleFiles());
  for (const file of onDisk()) assert.ok(listed.has(file), `${file} is not in APP_JS_FILES, so the page never loads it`);
});

test('the bundle has no duplicates', () => {
  const files = bundleFiles();
  assert.strictEqual(new Set(files).size, files.length);
});

test('the joined bundle parses', () => {
  const source = bundleFiles().map((file) => fs.readFileSync(path.join(APP, file.replace(/^app\//, '')), 'utf8')).join('\n');
  assert.doesNotThrow(() => new vm.Script(source, { filename: 'app.js' }));
});

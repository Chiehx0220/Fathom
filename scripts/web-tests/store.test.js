const test = require('node:test');
const assert = require('node:assert');
const { load } = require('./harness');

const SERVICES = [[0, 'YouTube'], [5, 'Bilibili']];

test('the stored service is used when the page offers it', () => {
  const { FT } = load({ config: { services: SERVICES }, stored: { 'fathom-service': '5' } });
  assert.strictEqual(FT.store.service, 5);
  assert.strictEqual(FT.store.serviceName(5), 'Bilibili');
});

test('an unknown or missing stored service falls back to the first', () => {
  assert.strictEqual(load({ config: { services: SERVICES }, stored: { 'fathom-service': '9' } }).FT.store.service, 0);
  assert.strictEqual(load({ config: { services: SERVICES } }).FT.store.service, 0);
  assert.strictEqual(load({ config: { services: SERVICES }, stored: { 'fathom-service': 'x' } }).FT.store.service, 0);
});

test('with no config the only service is YouTube', () => {
  const { FT } = load({});
  assert.deepStrictEqual(JSON.parse(JSON.stringify(FT.services)), [{ id: 0, name: 'YouTube' }]);
});

test('setService persists a known id and ignores an unknown one', () => {
  const { FT, storage } = load({ config: { services: SERVICES } });
  FT.store.setService(5);
  assert.strictEqual(FT.store.service, 5);
  assert.strictEqual(storage['fathom-service'], '5');
  FT.store.setService(9);
  assert.strictEqual(FT.store.service, 5);
  assert.strictEqual(storage['fathom-service'], '5');
});

test('onPhone is true only for the phone itself', () => {
  assert.strictEqual(load({}).FT.onPhone(), true);
});

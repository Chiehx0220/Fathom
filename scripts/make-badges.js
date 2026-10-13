#!/usr/bin/env node
// Writes the README badges to Assets/badges as light and dark SVGs, with the text drawn as paths so they
// look the same everywhere. The release and downloads values come from GitHub; run it again after a release.
//
//   node scripts/make-badges.js                              read the values with `gh`
//   node scripts/make-badges.js --release v0.1.5 --downloads 1200   use these values instead
//
// glyphs.json is Roboto Bold (Apache-2.0) and icons.json is Material Symbols Rounded (Apache-2.0) plus the
// GitHub mark from Simple Icons (CC0), both cut down to what the badges use.

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const ROOT = path.join(__dirname, '..');
const OUT = path.join(ROOT, 'Assets', 'badges');
const font = JSON.parse(fs.readFileSync(path.join(__dirname, 'badges', 'glyphs.json'), 'utf8'));
const icons = JSON.parse(fs.readFileSync(path.join(__dirname, 'badges', 'icons.json'), 'utf8'));
const REPO = process.env.FATHOM_REPO || 'Chiehx0220/Fathom';

const THEMES = {
  light: { primary: '#3F6188', onPrimary: '#F7F9FF', container: '#AFD2FF', onContainer: '#23476D' },
  dark: { primary: '#AFC9EB', onPrimary: '#28425E', container: '#3B5472', onContainer: '#D3E5FF' },
};

const num = (v) => String(Math.round(v * 100) / 100);

function textWidth(text, size) {
  let units = 0;
  for (const ch of text) units += (font.glyphs[ch] || font.glyphs['?'] || { adv: 0 }).adv;
  return (units * size) / font.unitsPerEm;
}

// Text as one path group whose baseline sits at (x, y).
function text(str, x, y, size, fill) {
  const scale = size / font.unitsPerEm;
  let pen = 0;
  const parts = [];
  for (const ch of str) {
    const glyph = font.glyphs[ch];
    if (!glyph) throw new Error(`No glyph for "${ch}"`);
    if (glyph.d) parts.push(`<path transform="translate(${pen} 0)" d="${glyph.d}"/>`);
    pen += glyph.adv;
  }
  return `<g fill="${fill}" transform="translate(${num(x)} ${num(y)}) scale(${scale.toFixed(6)} ${(-scale).toFixed(6)})">${parts.join('')}</g>`;
}

function icon(name, x, y, size, fill) {
  const { viewBox, d } = icons[name];
  const [vx, vy, vw] = viewBox.split(' ').map(Number);
  const scale = size / vw;
  return `<path fill="${fill}" transform="translate(${num(x)} ${num(y)}) scale(${scale}) translate(${-vx} ${-vy})" d="${d}"/>`;
}

function svg(width, height, label, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${num(width)}" height="${height}" viewBox="0 0 ${num(width)} ${height}" role="img" aria-label="${label}"><title>${label}</title>${body}</svg>\n`;
}

// A label on the primary colour and a value on the container colour, 32 px high.
function split({ label, value, icon: iconName }, t) {
  const H = 32;
  const size = 13.5;
  const baseline = (H + (font.capHeight * size) / font.unitsPerEm) / 2;
  const left = 10 + 18 + 8 + textWidth(label, size) + 10;
  const right = 12 + textWidth(value, size) + 12;
  const x0 = left + 3;
  const width = x0 + right;
  const leftPath = `M16 0H${num(left - 5)}A5 5 0 0 1 ${num(left)} 5V27A5 5 0 0 1 ${num(left - 5)} 32H16A16 16 0 0 1 0 16V16A16 16 0 0 1 16 0Z`;
  const rightPath = `M${num(x0 + 5)} 0H${num(width - 16)}A16 16 0 0 1 ${num(width)} 16V16A16 16 0 0 1 ${num(width - 16)} 32H${num(x0 + 5)}A5 5 0 0 1 ${num(x0)} 27V5A5 5 0 0 1 ${num(x0 + 5)} 0Z`;
  return svg(width, H, `${label}: ${value}`, [
    `<path fill="${t.primary}" d="${leftPath}"/>`,
    `<path fill="${t.container}" d="${rightPath}"/>`,
    icon(iconName, 10, 7, 18, t.onPrimary),
    text(label, 36, baseline, size, t.onPrimary),
    text(value, x0 + 12, baseline, size, t.onContainer),
  ].join(''));
}

// A two-line call to action in a pill, 60 px high.
function pill({ small, big, icon: iconName }, t) {
  const H = 60;
  const smallSize = 12;
  const bigSize = 23;
  const textX = 64;
  const width = textX + Math.max(textWidth(small, smallSize), textWidth(big, bigSize)) + 26;
  return svg(width, H, `${small} ${big}`, [
    `<rect width="${num(width)}" height="${H}" rx="30" fill="${t.container}"/>`,
    icon(iconName, 17, 12, 36, t.primary),
    text(small, textX, 24, smallSize, t.onContainer),
    text(big, textX, 47, bigSize, t.onContainer),
  ].join(''));
}

function flag(name) {
  const at = process.argv.indexOf(`--${name}`);
  return at > 0 ? process.argv[at + 1] : undefined;
}

function githubValues() {
  const json = execFileSync('gh', ['api', `repos/${REPO}/releases?per_page=100`], { encoding: 'utf8' });
  const releases = JSON.parse(json).filter((r) => !r.draft);
  const downloads = releases.reduce((sum, r) => sum + r.assets.reduce((n, a) => n + a.download_count, 0), 0);
  return { release: releases[0]?.tag_name, downloads };
}

function compact(n) {
  if (n < 10) return String(n);
  if (n < 1000) return `${Math.floor(n / 10) * 10}+`;
  if (n < 10000) return `${Math.floor(n / 100) / 10}k+`;
  return `${Math.floor(n / 1000)}k+`;
}

const given = { release: flag('release'), downloads: flag('downloads') };
const values = given.release && given.downloads ? { release: given.release, downloads: Number(given.downloads) } : githubValues();

const BADGES = [
  { kind: 'pill', file: 'get-github', spec: { small: 'Get it on', big: 'GitHub', icon: 'github' } },
  { kind: 'split', file: 'android', spec: { label: 'Android', value: '9+', icon: 'android' } },
  { kind: 'split', file: 'release', spec: { label: 'Release', value: values.release, icon: 'rocket_launch' } },
  { kind: 'split', file: 'downloads', spec: { label: 'Downloads', value: compact(values.downloads), icon: 'download' } },
  { kind: 'split', file: 'license', spec: { label: 'License', value: 'GPL-3.0', icon: 'balance' } },
  { kind: 'split', file: 'tracking', spec: { label: 'Tracking', value: 'none', icon: 'visibility_off' } },
  { kind: 'split', file: 'fork', spec: { label: 'Fork of', value: 'Flow', icon: 'fork_right' } },
];

for (const { kind, file, spec } of BADGES) {
  fs.mkdirSync(path.join(OUT, kind), { recursive: true });
  for (const [name, theme] of Object.entries(THEMES)) {
    const body = kind === 'pill' ? pill(spec, theme) : split(spec, theme);
    fs.writeFileSync(path.join(OUT, kind, `${file}-${name}.svg`), body);
  }
}
console.log(`Wrote ${BADGES.length * 2} badges to ${path.relative(ROOT, OUT)} (release ${values.release}, ${compact(values.downloads)} downloads)`);

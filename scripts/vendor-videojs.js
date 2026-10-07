#!/usr/bin/env node
// Copies the parts of Video.js 10's CDN build that the web UI uses into app/src/main/assets/web/vendor/videojs,
// so the page loads the player from the phone and needs no outside host.
//
//   node scripts/vendor-videojs.js            vendor the version in VERSION below
//   node scripts/vendor-videojs.js 10.0.2     vendor another version
//
// The CDN entry files import shared, content-hashed chunks, so a file cannot be copied alone: this follows every
// import from the entries and copies exactly what they reach. Source maps, development builds, type files, locale
// packs and the media components the UI does not use stay behind. Run it again to upgrade, and commit the result.

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const VERSION = process.argv[2] || '10.0.1';
const ROOT = path.join(__dirname, '..');
const DEST = path.join(ROOT, 'app/src/main/assets/web/vendor/videojs');

// video.js / live-video.js: the player and its skin (VOD, and live without a time slider).
// media/hlsjs-video.js: HLS through hls.js (live rooms and on-demand video alike).
const ENTRIES = ['video.js', 'live-video.js', 'media/hlsjs-video.js'];
const EXTRA = ['global.css', 'LICENSE', 'package.json'];

const IMPORT = /(?:\bfrom|\bimport)\s*["'](\.{1,2}\/[^"']+)["']|import\(\s*["'](\.{1,2}\/[^"']+)["']\s*\)/g;

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'videojs-cdn-'));
execFileSync('npm', ['pack', `@videojs/cdn@${VERSION}`, '--silent'], { cwd: work, stdio: ['ignore', 'pipe', 'inherit'] });
const tgz = fs.readdirSync(work).find((f) => f.endsWith('.tgz'));
execFileSync('tar', ['xzf', tgz], { cwd: work });
const pkg = path.join(work, 'package');

const reached = new Set();
const queue = [...ENTRIES];
while (queue.length) {
  const file = queue.pop();
  if (reached.has(file)) continue;
  const full = path.join(pkg, file);
  if (!fs.existsSync(full)) throw new Error(`${file} is not in @videojs/cdn@${VERSION}`);
  reached.add(file);
  const source = fs.readFileSync(full, 'utf8');
  for (const match of source.matchAll(IMPORT)) {
    queue.push(path.normalize(path.join(path.dirname(file), match[1] || match[2])));
  }
}

fs.rmSync(DEST, { recursive: true, force: true });
for (const file of [...reached, ...EXTRA]) {
  const target = path.join(DEST, file);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.copyFileSync(path.join(pkg, file), target);
}
fs.writeFileSync(path.join(DEST, 'VERSION'), `${VERSION}\n`);
fs.rmSync(work, { recursive: true, force: true });

const bytes = [...reached].reduce((sum, file) => sum + fs.statSync(path.join(DEST, file)).size, 0);
console.log(`@videojs/cdn@${VERSION}: ${reached.size} files, ${Math.round(bytes / 1024)} KB -> ${path.relative(ROOT, DEST)}`);
